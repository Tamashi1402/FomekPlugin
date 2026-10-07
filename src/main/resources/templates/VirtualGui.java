package net.tamashi.fomekcore.api.guisystems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.common.NeoForge;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * VirtualGui — Virtual GUI system with nesting, actions, and checks.
 *
 * Uses begin/end pattern for panels:
 *   VirtualGui.beginPanel("panel1", x1, y1, x2, y2, type);
 *     // children, actions, checks go here
 *   VirtualGui.endPanel();
 *
 * Actions are triggered via callAction(id) and consumed during render.
 * Checks store results during render, retrievable via getCheckResult(id).
 *
 * Buttons and sliders are added as children of the current panel.
 * Screen boundaries constrain panel dragging within a region.
 *
 * The render trigger fires EVERY frame on EVERY screen. User procedures
 * call beginMenu + build elements inside that trigger. beginMenu is
 * idempotent (same menu = no clear), and beginPanel/addButton/addSlider
 * skip duplicates so state (drag positions, slider values) persists.
 */
public class VirtualGui {

    // ── Registry ───────────────────────────────────────────────────────────────

    private static final Map<String, VirtualGui> REGISTERED = new HashMap<>();
    private static String currentOpenId = null;
    private static VirtualGui current = null;

    // ── Persistent GUI state ───────────────────────────────────────────────────
    private String persistentStateId = null;
    private CompoundTag persistentStateTag = null;

    private final String id;
    private final Map<String, VirtualGuiElement> elements = new HashMap<>();
    private final List<String> elementOrder = new ArrayList<>();

    // ── Panel stack (for nesting) ────────────────────────────────────────────────

    private static final Deque<VirtualGuiElement> panelStack = new ArrayDeque<>();
    private static String currentParentId = null;
    private static String currentRenderElementId = null;

    // Scissor stack: tracks whether scissor is enabled at each panel nesting level.
    // Pushed in beginPanel/beginScrollView, popped in endPanel.
    private static final Deque<Boolean> scissorStack = new ArrayDeque<>();
    // Tracks whether a panel's scissor was set independently (reparented panel
    // whose procedure parent's scissor was disabled to avoid intersection).
    // Popped in endPanel to know whether to re-enable the parent's scissor.
    private static final Deque<Boolean> reparentedScissorStack = new ArrayDeque<>();

    // Panels that have had children reparented out of them (drag-out feature).
    // Their scissor is permanently skipped so the reparented element's content
    // (still rendered in this panel's procedure scope) remains visible outside.

    // ── Screen dimensions (set every render frame, used for boundary clamping) ───

    private static float screenWidth = 0f;
    private static float screenHeight = 0f;
    private static float prevScreenWidth = 0f;
    private static float prevScreenHeight = 0f;
    private static boolean resizeJustHappened = false;

    /** Returns the root-level ancestor of an element (the top of its parent
     *  chain), or the element itself if it has no parent. */
    private static VirtualGuiElement getRootAncestor(VirtualGuiElement el, VirtualGui gui) {
        VirtualGuiElement root = el;
        String pid = root.getParentId();
        while (pid != null) {
            VirtualGuiElement parent = gui.elements.get(pid);
            if (parent == null) break;
            root = parent;
            pid = root.getParentId();
        }
        return root;
    }
    // ── Screen boundaries (applied to panels created within the scope) ───────────

    private static boolean boundariesActive = false;
    private static float boundsX1, boundsY1, boundsX2, boundsY2;
    // Previous frame's boundary (for proportional repositioning on resize).
    // When the procedure recomputes boundaries from the screen size every frame
    // (e.g. screenWidth/2 - 200), the boundary corner moves non-proportionally
    // to the screen. Scaling panel positions by the screen ratio would drift
    // them off the boundary corner. Instead, we scale by the boundary ratio.
    private static boolean prevBoundsActive = false;
    private static float prevBoundsX1, prevBoundsY1, prevBoundsX2, prevBoundsY2;

    // Mouse position during render phase (for beginPanel decoration rendering)
    private static float renderMouseX, renderMouseY;
    private static float lastPartialTick;
    private static GuiGraphics lastGuiGraphics;
    private static String currentPartId = null;

    /** Current mouse X from the last render frame (for scroll event handler). */
    public static float getRenderMouseX() { return renderMouseX; }
    /** Current mouse Y from the last render frame (for scroll event handler). */
    public static float getRenderMouseY() { return renderMouseY; }

    // ── Actions ──────────────────────────────────────────────────────────────────

    private static final CopyOnWriteArrayList<ActionEntry> triggeredActions = new CopyOnWriteArrayList<>();

    private static class ActionEntry {
        final String id;
        final String reason;
        final String elementId;
        ActionEntry(String id, String reason, String elementId) {
            this.id = id; this.reason = reason; this.elementId = elementId;
        }
    }

    public static void callAction(String actionId) {
        if (actionId == null || actionId.isEmpty()) return;
        triggeredActions.add(new ActionEntry(actionId, currentActionReason, currentActionElementId));
    }

    /** Consume first action matching id with ANY reason except "update". */
    public static boolean consumeAction(String actionId) {
        for (ActionEntry e : triggeredActions) {
            if (e.id.equals(actionId) && !"update".equals(e.reason)) {
                currentActionElementId = e.elementId;
                return triggeredActions.remove(e);
            }
        }
        return false;
    }

    /** Consume first action matching both id and reason. */
    public static boolean consumeAction(String actionId, String reason) {
        for (ActionEntry e : triggeredActions) {
            if (e.id.equals(actionId) && (reason == null || reason.equals(e.reason))) {
                currentActionElementId = e.elementId;
                return triggeredActions.remove(e);
            }
        }
        return false;
    }

    // ── Checks ──────────────────────────────────────────────────────────────────

    private static final Map<String, Boolean> checkResults = new HashMap<>();
    private static String currentCheckId = null;
    private static boolean currentCheckResult = true;

    public static void beginCheck(String checkId) {
        currentCheckId = checkId;
        currentCheckResult = true;
    }

    public static void setCheckReturn(boolean value) {
        currentCheckResult = value;
    }

    public static void endCheck() {
        if (currentCheckId != null) {
            checkResults.put(currentCheckId, currentCheckResult);
            currentCheckId = null;
        }
    }

    public static boolean getCheckResult(String checkId) {
        return checkResults.getOrDefault(checkId, false);
    }

    // ── Action reason & cancel system ──────────────────────────────────────────
    // When an event fires (button click, slider change, grid add/remove, drag
    // in/out/start), the reason is recorded. During the next render frame, checks
    // can use the "Is" block to test the reason, and "Cancel Action" to prevent
    // the action body from running.
    //
    // The ID of the object (button/slider/panel element) that triggered the
    // action is recorded alongside the reason, so the "Action" body can use
    // "Is object id" to tell which specific object called it — useful when
    // many prefab copies of the same object share one action id.

    private static String currentActionReason = null;
    private static String currentActionElementId = null;
    private static String currentActionManagerId = null;
    private static boolean actionCancelled = false;
    private static String droppedElementId = null;
    private static String dropTargetId = null;
    private static float currentActionMouseX = 0f;
    private static float currentActionMouseY = 0f;

    public static void setActionReason(String reason) {
        currentActionReason = reason;
    }

    public static String getActionReason() {
        return currentActionReason;
    }

    public static boolean isActionReason(String reason) {
        return reason != null && reason.equals(currentActionReason);
    }

    /** Records the id of the object (element) that triggered the current action. */
    public static void setActionElementId(String elementId) {
        currentActionElementId = elementId;
    }

    /** The id of the object that triggered the action currently being handled. */
    public static String getActionElementId() {
        return currentActionElementId;
    }

    public static boolean isActionElementId(String elementId) {
        return elementId != null && elementId.equals(currentActionElementId);
    }

    /** Records the mouse X position when the current action was triggered. */
    public static void setActionMouseX(float x) { currentActionMouseX = x; }

    /** The mouse X position when the current action was triggered (screen coords). */
    public static float getActionMouseX() { return currentActionMouseX; }

    /** Records the mouse Y position when the current action was triggered. */
    public static void setActionMouseY(float y) { currentActionMouseY = y; }

    /** The mouse Y position when the current action was triggered (screen coords). */
    public static float getActionMouseY() { return currentActionMouseY; }

    /** Records the id of the InputManager that triggered the current action. */
    public static void setActionManagerId(String managerId) {
        currentActionManagerId = managerId;
    }

    /** The id of the InputManager that triggered the action currently being handled. */
    public static String getActionManagerId() {
        return currentActionManagerId;
    }

    public static boolean isActionManagerId(String managerId) {
        return managerId != null && managerId.equals(currentActionManagerId);
    }

    public static void cancelAction() {
        actionCancelled = true;
    }

    public static boolean isActionCancelled() {
        return actionCancelled;
    }

    public static void clearActionCancel() {
        actionCancelled = false;
        currentActionReason = null;
        currentActionElementId = null;
        currentActionManagerId = null;
        droppedElementId = null;
        dropTargetId = null;
    }

    /** The element that was dropped onto a drop target (the dragged file). */
    public static String getDroppedElementId() { return droppedElementId; }

    /** The drop-target element that received the drop (the trashbin). */
    public static String getDropTargetId() { return dropTargetId; }

    // ── Menu management ──────────────────────────────────────────────────────────

    /** Declaration frame counter — bumped by beginMenu so per-frame
     *  declarations (book page panels) can detect staleness. */
    private static int declFrame = 0;
    public static int currentDeclFrame() { return declFrame; }

    /** Hide a book page side panel that was NOT re-declared this frame
     *  (its page did not render — flipped away or the book closed).
     *  Hiding the panel hides its whole content subtree with it. */
    public static void hideStalePagePanel(String panelId) {
        if (current == null || panelId == null) return;
        VirtualGuiElement el = current.elements.get(panelId);
        if (el != null && el.getLastDeclaredFrame() != declFrame) {
            el.setVisible(false);
        }
    }

    public static void beginMenu(String menuId) {
        declFrame++;
        // If same menu is already current, just reset the panel stack for rebuilding.
        // Don't clear elements — preserves drag positions, slider values, visibility, etc.
        if (menuId.equals(currentOpenId) && current != null) {
            panelStack.clear();
            scissorStack.clear();
            reparentedScissorStack.clear();
            currentParentId = null;
            currentRenderElementId = null;
            boundariesActive = false;
            // Reset backgroundRendered + decorationsRendered so panels re-draw
            // in beginPanel during the procedure phase
            for (String id : current.elementOrder) {
                VirtualGuiElement el = current.elements.get(id);
                if (el != null) {
                    el.resetDecorationsRendered();
                    el.clearPendingFills();
                }
            }
            return;
        }

        // Different menu or first open — create fresh
        REGISTERED.computeIfAbsent(menuId, VirtualGui::new);
        currentOpenId = menuId;
        current = REGISTERED.get(menuId);
        current.elements.clear();
        current.elementOrder.clear();
        panelStack.clear();
        scissorStack.clear();
        reparentedScissorStack.clear();
        currentParentId = null;
        currentRenderElementId = null;
        boundariesActive = false;
    }


    // ── Box convenience overloads ──────────────────────────────────────────────

    /**
     * Recursively scales an element's position and size by (sx, sy), and does
     * the same for ALL descendants — not just direct children. This prevents
     * grandchildren (e.g. a button inside a sub-panel) from staying at their
     * original size when the root panel is resized via boundary-ratio scaling.
     */
    private static void scaleDescendants(VirtualGuiElement el, float sx, float sy) {
        for (VirtualGuiElement child : el.getChildren()) {
            if (!child.isVisible()) continue;
            // Skip children of scaled-content panels — they stay in design space
            // and are scaled at render time via getContentScaleX/Y instead.
            if (el.getScaledContent()) continue;
            child.setX(child.getX() * sx);
            child.setY(child.getY() * sy);
            child.setWidth(child.getWidth() * sx);
            child.setHeight(child.getHeight() * sy);
            // Recurse into grandchildren
            scaleDescendants(child, sx, sy);
        }
    }

    public static void beginPanel(String id, Box box, PanelType type) {
        beginPanel(id, box.x1(), box.y1(), box.x2(), box.y2(), type, false, false);
    }

    public static void beginPanel(String id, Box box, PanelType type, boolean shouldStick) {
        beginPanel(id, box.x1(), box.y1(), box.x2(), box.y2(), type, shouldStick, false);
    }

    public static void beginPanel(String id, Box box, PanelType type, boolean shouldStick, boolean collision) {
        beginPanel(id, box.x1(), box.y1(), box.x2(), box.y2(), type, shouldStick, collision);
    }

    /**
     * Panel creation with size + position decoupled: `box` (x1,y1,x2,y2) only
     * supplies the SIZE (width = x2-x1, height = y2-y1) — its own x1/y1 corner
     * is ignored as a position. `atX`/`atY` is the actual screen position, and
     * `pivot` decides which point of the box lands on (atX, atY):
     *   top-left (default) — box's top-left corner = (atX, atY)
     *   top                — box's top-center = (atX, atY)
     *   bottom             — box's bottom-center = (atX, atY)
     *   left               — box's left-center = (atX, atY)
     *   right              — box's right-center = (atX, atY)
     *   center             — box's center = (atX, atY)
     * This only matters at panel CREATION — on rebuild (existing panel), the
     * position is preserved so drag/resize state survives across frames.
     */
    public static void beginPanel(String id, Box box, float atX, float atY, PanelType type, String pivot) {
        beginPanel(id, box, atX, atY, type, pivot, false, false);
    }

    public static void beginPanel(String id, Box box, float atX, float atY, PanelType type, String pivot, boolean shouldStick) {
        beginPanel(id, box, atX, atY, type, pivot, shouldStick, false);
    }

    public static void beginPanel(String id, Box box, float atX, float atY, PanelType type, String pivot, boolean shouldStick, boolean collision) {
        beginPanel(id, box, atX, atY, type, pivot, shouldStick, collision, false);
    }

    public static void beginPanel(String id, Box box, float atX, float atY, PanelType type, String pivot, boolean shouldStick, boolean collision, boolean scaledContent) {
        float w = box.x2() - box.x1();
        float h = box.y2() - box.y1();
        float[] resolved = resolvePivot(atX, atY, w, h, pivot);
        beginPanel(id, resolved[0], resolved[1], resolved[2], resolved[3], type, shouldStick, collision, scaledContent);
    }

    /**
     * Resolves a pivot-anchored box into absolute (x1,y1,x2,y2), given the
     * anchor point (atX, atY) and the box's width/height. See beginPanel(..., pivot)
     * for the pivot semantics.
     */
    private static float[] resolvePivot(float atX, float atY, float w, float h, String pivot) {
        float x1, y1;
        if (pivot == null) pivot = "top-left";
        switch (pivot) {
            case "top":           x1 = atX - w / 2f; y1 = atY;           break;
            case "top-right":     x1 = atX - w;      y1 = atY;           break;
            case "right":         x1 = atX - w;      y1 = atY - h / 2f;  break;
            case "bottom-right":  x1 = atX - w;      y1 = atY - h;       break;
            case "bottom":        x1 = atX - w / 2f; y1 = atY - h;       break;
            case "bottom-left":   x1 = atX;          y1 = atY - h;       break;
            case "left":          x1 = atX;          y1 = atY - h / 2f;  break;
            case "center":        x1 = atX - w / 2f; y1 = atY - h / 2f;  break;
            case "top-left":
            default:              x1 = atX;          y1 = atY;           break;
        }
        return new float[]{x1, y1, x1 + w, y1 + h};
    }

    public static void setBoundaries(Box box) {
        setBoundaries(box.x1(), box.y1(), box.x2(), box.y2());
    }

    // ── Scroll view (begin/end pattern, like Panel but with clipping + culling) ─

    public static void beginScrollView(String id, Box box, float scrollValue) {
        beginScrollView(id, box.x1(), box.y1(), box.x2(), box.y2(), scrollValue, false);
    }

    public static void beginScrollView(String id, Box box, float scrollValue, boolean shouldStick) {
        beginScrollView(id, box.x1(), box.y1(), box.x2(), box.y2(), scrollValue, shouldStick, false);
    }

    public static void beginScrollView(String id, Box box, float scrollValue, boolean shouldStick, boolean collision) {
        beginScrollView(id, box.x1(), box.y1(), box.x2(), box.y2(), scrollValue, shouldStick, collision);
    }

    public static void beginScrollView(String id, Box box, float scrollValue, boolean shouldStick, boolean collision, String anchor) {
        beginScrollView(id, box.x1(), box.y1(), box.x2(), box.y2(), scrollValue, shouldStick, collision, anchor);
    }

    public static void beginScrollView(String id, float x1, float y1, float x2, float y2, float scrollValue) {
        beginScrollView(id, x1, y1, x2, y2, scrollValue, false, false);
    }

    public static void beginScrollView(String id, float x1, float y1, float x2, float y2, float scrollValue, boolean shouldStick) {
        beginScrollView(id, x1, y1, x2, y2, scrollValue, shouldStick, false);
    }

    public static void beginScrollView(String id, float x1, float y1, float x2, float y2, float scrollValue, boolean shouldStick, boolean collision) {
        beginScrollView(id, x1, y1, x2, y2, scrollValue, shouldStick, collision, "top");
    }

    public static void beginScrollView(String id, float x1, float y1, float x2, float y2, float scrollValue, boolean shouldStick, boolean collision, String anchor) {
        if (current == null) return;
        boolean anchorBottom = "bottom".equalsIgnoreCase(anchor);

        VirtualGuiElement existing = current.elements.get(id);
        if (existing != null) {
            // DON'T overwrite position/size from the procedure's literal params —
            // same pattern as beginPanel/beginRenderElement. Preserves window-resize
            // scaling (otherwise the literal x1/y1/x2/y2 would reset the scroll
            // view's size every frame and undo the scaling).
            // Always update scroll offset from the parameter.
            // If value <= 1.0, treat as a fraction of the scrollable range.
            // If value > 1.0, treat as raw pixels.
            float viewportH = existing.getHeight();
            float maxScroll = Math.max(0f, existing.getContentHeight() - viewportH);
            float newOffset;
            if (scrollValue <= 1.0f) {
                newOffset = scrollValue * maxScroll;
            } else {
                newOffset = scrollValue;
            }
            existing.setScrollOffsetY(newOffset);
            existing.setScrollAnchorBottom(anchorBottom);
            // Clamp scroll offset to valid range
            clampScrollOffset(existing);
            if (boundariesActive) {
                existing.setBounds(boundsX1, boundsY1, boundsX2, boundsY2);
            } else {
                existing.clearBounds();
            }
            // Reposition root-level panels proportionally to the BOUNDARY, not
            // the screen. The procedure recomputes boundaries from the screen
            // every frame (e.g. screenWidth/2 - 200), which is NOT proportional
            // to the screen — so scaling panel positions by the screen ratio
            // (what the resize handler does) would drift panels off the boundary
            // corner. Instead, we scale by the boundary-size ratio: if the
            // boundary was 400x200 and is now 400x200 (centered, same size), the
            // panel stays at the same size and just moves to the new corner.
            // Only applies when bounds changed (resize) and the panel is
            // root-level (no parent) — children are handled by their parent's
            // resize logic.
            if (resizeJustHappened && existing.getParentId() == null) {
                // Determine old and new reference boxes
                float oldRX1, oldRY1, oldRX2, oldRY2;
                float newRX1, newRY1, newRX2, newRY2;
                if (prevBoundsActive && boundariesActive) {
                    oldRX1 = prevBoundsX1; oldRY1 = prevBoundsY1;
                    oldRX2 = prevBoundsX2; oldRY2 = prevBoundsY2;
                    newRX1 = boundsX1; newRY1 = boundsY1;
                    newRX2 = boundsX2; newRY2 = boundsY2;
                } else if (prevBoundsActive && !boundariesActive) {
                    // Bounds cleared — old was boundary, new is full screen
                    oldRX1 = prevBoundsX1; oldRY1 = prevBoundsY1;
                    oldRX2 = prevBoundsX2; oldRY2 = prevBoundsY2;
                    newRX1 = 0; newRY1 = 0;
                    newRX2 = screenWidth; newRY2 = screenHeight;
                } else if (!prevBoundsActive && boundariesActive) {
                    // Bounds just set — old was full screen, new is boundary
                    oldRX1 = 0; oldRY1 = 0;
                    oldRX2 = prevScreenWidth > 0 ? prevScreenWidth : screenWidth;
                    oldRY2 = prevScreenHeight > 0 ? prevScreenHeight : screenHeight;
                    newRX1 = boundsX1; newRY1 = boundsY1;
                    newRX2 = boundsX2; newRY2 = boundsY2;
                } else {
                    // No bounds before or after — use screen ratio (same as resize handler)
                    oldRX1 = 0; oldRY1 = 0;
                    oldRX2 = prevScreenWidth > 0 ? prevScreenWidth : screenWidth;
                    oldRY2 = prevScreenHeight > 0 ? prevScreenHeight : screenHeight;
                    newRX1 = 0; newRY1 = 0;
                    newRX2 = screenWidth; newRY2 = screenHeight;
                }
                float oldW = oldRX2 - oldRX1;
                float oldH = oldRY2 - oldRY1;
                float newW = newRX2 - newRX1;
                float newH = newRY2 - newRY1;
                if (oldW > 0 && oldH > 0) {
                    float sx = newW / oldW;
                    float sy = newH / oldH;
                    // Scale position relative to the reference box
                    float newX = newRX1 + (existing.getX() - oldRX1) * sx;
                    float newY = newRY1 + (existing.getY() - oldRY1) * sy;
                    float newWidth = existing.getWidth() * sx;
                    float newHeight = existing.getHeight() * sy;
                    existing.setX(newX);
                    existing.setY(newY);
                    existing.setWidth(newWidth);
                    existing.setHeight(newHeight);
                    // Re-snap grid children after repositioning (same as resize handler)
                    if (existing.hasGrid()) {
                        existing.reSnapAllChildren(current);
                    }
                    // If the scroll view's size changed, scale children AND
                    // all descendants recursively (grandchildren included).
                    if (Math.abs(sx - 1.0f) > 0.001f || Math.abs(sy - 1.0f) > 0.001f) {
                        scaleDescendants(existing, sx, sy);
                        if (existing.hasGrid()) {
                            existing.reSnapAllChildren(current);
                        }
                    }
                }
            }
            existing.setShouldStick(shouldStick);
            existing.setCollision(collision);
            existing.clearSolidRenderBoxes();
            existing.clearGrid();
            panelStack.push(existing);
            currentParentId = id;
        } else {
            float w = x2 - x1;
            float h = y2 - y1;
            VirtualGuiElement sv = new VirtualGuiElement(id, "scroll_view", x1, y1, w, h, -1);
            applyPersistentState(sv);
            sv.setScrollView(true);
            sv.setScrollAnchorBottom(anchorBottom);
            // On first frame, contentHeight is 0, so just store the raw value.
            // Next frame the existing-block will convert it properly.
            sv.setScrollOffsetY(scrollValue);

            if (currentParentId != null) {
                sv.setParentId(currentParentId);
                sv.setHomeParentId(currentParentId);
                VirtualGuiElement parent = current.elements.get(currentParentId);
                if (parent != null) parent.addChild(sv);
            }

            sv.setShouldStick(shouldStick);
            sv.setCollision(collision);
            sv.clearSolidRenderBoxes();
            sv.clearGrid();
            current.elements.put(id, sv);
            current.elementOrder.add(id);
            panelStack.push(sv);
            currentParentId = id;
        }

        // Render background + enable scissoring (same pattern as beginPanel).
        VirtualGuiElement sv = current.elements.get(id);
        String svProcedureParent = sv.getParentId(); // actual parent (may differ from procedure nesting)
        boolean svReparented = !java.util.Objects.equals(svProcedureParent, 
            panelStack.isEmpty() ? null : panelStack.peek().getId());
        GuiGraphics gui = MenuRenderHelper.getGuiGraphics();
        sv.clearPendingFills();
        if (gui != null) {
            // Background is now rendered during renderElements (in render())
            // to ensure correct z-ordering across panels.
            if (svReparented && !scissorStack.isEmpty() && scissorStack.peek()) {
                gui.disableScissor();
            }
            int svx = sv.getRenderX(current);
            int svy = sv.getRenderY(current);
            int svw = sv.getRenderWidth(current);
            int svh = sv.getRenderHeight(current);
            gui.enableScissor(svx, svy, svx + svw, svy + svh);
        }
        scissorStack.push(true);
        reparentedScissorStack.push(svReparented);
    }

    public static void endScrollView() {
        // Compute content height from all children before ending the panel.
        // This is used for scroll clamping (can't scroll past content).
        if (current != null && !panelStack.isEmpty()) {
            VirtualGuiElement sv = panelStack.peek();
            if (sv != null && sv.isScrollView()) {
                float maxBottom = 0f;
                // Tracked children
                for (VirtualGuiElement child : sv.getChildren()) {
                    if (!child.isVisible()) continue;
                    float childBottom = child.getY() + child.getEffectiveHeight(current);
                    if (childBottom > maxBottom) maxBottom = childBottom;
                }
                // Solid render-call boxes (local coordinates)
                for (float[] box : sv.getSolidRenderBoxes()) {
                    float boxBottom = box[3];
                    if (boxBottom > maxBottom) maxBottom = boxBottom;
                }
                sv.setContentHeight(maxBottom);
                // Re-clamp scroll offset with updated content height
                clampScrollOffset(sv);
            }
        }
        endPanel();
    }

    /** Clamps a scroll view's scroll offset to [0, contentHeight - viewportHeight]. */
    private static void clampScrollOffset(VirtualGuiElement sv) {
        float viewportH = sv.getHeight();
        float maxScroll = Math.max(0f, sv.getContentHeight() - viewportH);
        float offset = sv.getScrollOffsetY();
        if (offset < 0f) offset = 0f;
        if (offset > maxScroll) offset = maxScroll;
        sv.setScrollOffsetY(offset);
    }

    // ── Mouse wheel scroll handling ─────────────────────────────────────────────

    /**
     * Called when the mouse wheel is scrolled. Finds the topmost scroll view
     * under the mouse cursor and scrolls it. Returns true if a scroll view
     * consumed the event (so the caller can cancel the vanilla scroll).
     */
    public static boolean handleMouseScroll(double scrollDelta, float mouseX, float mouseY) {
        if (current == null) return false;

        // Find the topmost scroll view under the mouse
        VirtualGuiElement target = findTopmostScrollViewAt(mouseX, mouseY);
        if (target == null) return false;

        // Each wheel notch scrolls by ~16 pixels (one line height)
        float scrollAmount = (float) (scrollDelta * 16.0);
        float newOffset = target.getScrollOffsetY() - scrollAmount;
        target.setScrollOffsetY(newOffset);
        clampScrollOffset(target);
        return true;
    }

    /**
     * Finds the frontmost scroll view that contains the mouse point.
     * Walks the element tree from front to back, descending into children.
     */
    private static VirtualGuiElement findTopmostScrollViewAt(float mx, float my) {
        if (current == null) return null;
        for (int i = current.elementOrder.size() - 1; i >= 0; i--) {
            VirtualGuiElement el = current.elements.get(current.elementOrder.get(i));
            if (el == null || el.getParentId() != null || !el.isVisible()) continue;
            VirtualGuiElement found = descendScrollView(el, mx, my);
            if (found != null) return found;
        }
        return null;
    }

    /** Recursively descends into children to find the frontmost scroll view. */
    private static VirtualGuiElement descendScrollView(VirtualGuiElement el, float mx, float my) {
        java.util.List<VirtualGuiElement> kids = el.getChildren();
        for (int i = kids.size() - 1; i >= 0; i--) {
            VirtualGuiElement child = kids.get(i);
            if (child == null || !child.isVisible()) continue;
            if (!containsPoint(child, mx, my)) continue;
            VirtualGuiElement found = descendScrollView(child, mx, my);
            if (found != null) return found;
        }
        // No child scroll view found — check if this element itself is a scroll view
        if (el.isScrollView() && containsPoint(el, mx, my)) return el;
        return null;
    }

    public static void closeMenu() {
        // Auto-save persistent state before closing
        savePersistentState();
        currentOpenId = null;
        current = null;
        panelStack.clear();
        scissorStack.clear();
        reparentedScissorStack.clear();
        currentParentId = null;
        boundariesActive = false;
        pendingClickElementId = null;
        draggingElementId = null;
        draggingSliderId = null;
        resizingElementId = null;
        resizeDirection = 0;
        triggeredActions.clear();
    }

    /** Alias for closeMenu() — called by MenuEventHandler on screen close. */
    public static void close() { closeMenu(); }

    // ── Persistent GUI state ───────────────────────────────────────────────────

    /**
     * Load persistent GUI state from MenuData storage.  Call this right after
     * Initialize menu — before creating any panels.  When panels are created
     * via beginPanel, the saved position/size/parent/visibility/scroll will be
     * applied automatically if a saved state exists for that element id.
     *
     * @param stateId The MenuData storage id (usually same as menu id)
     */
    public static void loadPersistentState(String stateId) {
        if (current == null) return;
        current.persistentStateId = stateId;
        MenuData data = MenuData.load(stateId);
        current.persistentStateTag = data.getTag();
    }

    /**
     * Save all element positions, sizes, parents, visibility, and scroll
     * offsets to MenuData storage.  Called automatically on screen close if
     * persistent state was loaded.  Can also be called manually (e.g. after
     * a drag or resize operation) to force-save.
     */
    public static void savePersistentState() {
        if (current == null || current.persistentStateId == null) return;

        // Load existing MenuData so we don't wipe user variables
        MenuData data = MenuData.load(current.persistentStateId);
        CompoundTag guiState = new CompoundTag();

        for (String elId : current.elementOrder) {
            VirtualGuiElement el = current.elements.get(elId);
            if (el == null) continue;
            CompoundTag elTag = new CompoundTag();
            elTag.putFloat("x", el.getX());
            elTag.putFloat("y", el.getY());
            elTag.putFloat("width", el.getWidth());
            elTag.putFloat("height", el.getHeight());
            if (el.getParentId() != null) elTag.putString("parentId", el.getParentId());
            elTag.putBoolean("visible", el.isVisible());
            elTag.putFloat("scrollOffsetY", el.getScrollOffsetY());
            guiState.put(elId, elTag);
        }

        // Store under reserved __gui_state__ key — coexists with user variables
        data.setCompound("__gui_state__", guiState);
        MenuData.save(current.persistentStateId, data);

        // Update cached tag so subsequent saves within the same session work
        current.persistentStateTag = data.getTag();
    }

    /**
     * Apply saved persistent state to a newly created element, if available.
     * Called automatically after creating panels, scroll views, buttons, sliders, etc.
     */
    private static void applyPersistentState(VirtualGuiElement el) {
        if (current == null || current.persistentStateTag == null) return;
        CompoundTag guiState = current.persistentStateTag.getCompound("__gui_state__");
        if (!guiState.contains(el.getId())) return;
        CompoundTag saved = guiState.getCompound(el.getId());
        el.setX(saved.getFloat("x"));
        el.setY(saved.getFloat("y"));
        el.setWidth(saved.getFloat("width"));
        el.setHeight(saved.getFloat("height"));
        if (saved.contains("parentId")) el.setParentId(saved.getString("parentId"));
        if (saved.contains("visible")) el.setVisible(saved.getBoolean("visible"));
        if (saved.contains("scrollOffsetY")) el.setScrollOffsetY(saved.getFloat("scrollOffsetY"));
    }


    public static boolean isOpen(String id) { return id != null && id.equals(currentOpenId); }
    public static boolean isAnyOpen() { return currentOpenId != null; }
    public static String getCurrentId() { return currentOpenId; }

    // ── Scroll offset (block API) ─────────────────────────────────────────────────

    /** Set the scroll offset of a scroll view by element ID. Clamped to [0, max]. */
    public static void setScrollOffset(String elementId, float value) {
        if (current == null) return;
        VirtualGuiElement el = current.elements.get(elementId);
        if (el != null && el.isScrollView()) {
            el.setScrollOffsetY(value);
            clampScrollOffset(el);
        }
    }

    /** Get the current scroll offset of a scroll view by element ID. */
    public static float getScrollOffset(String elementId) {
        if (current == null) return 0f;
        VirtualGuiElement el = current.elements.get(elementId);
        if (el != null && el.isScrollView()) {
            return el.getScrollOffsetY();
        }
        return 0f;
    }
    public static VirtualGui getCurrent() { return current; }

    /** Moves an element to the end of elementOrder so it renders last (on top). */
    public static void bringToFront(String id) {
        if (current == null || id == null) return;
        current.elementOrder.remove(id);
        current.elementOrder.add(id);
    }

    /**
     * Returns true if the panel/element with the given id is the frontmost
     * (topmost in z-order) root-level element — i.e. it was last brought to
     * front, or is the only element. Useful for input routing: only move
     * the snake when the snake_game panel is focused.
     */
    public static boolean isPanelFocused(String id) {
        if (current == null || id == null || current.elementOrder.isEmpty()) return false;
        // Only consider root-level elements (parentId == null)
        for (int i = current.elementOrder.size() - 1; i >= 0; i--) {
            String eid = current.elementOrder.get(i);
            VirtualGuiElement el = current.elements.get(eid);
            if (el == null || el.getParentId() != null) continue;
            // First root-level element from the top is the focused one
            return eid.equals(id);
        }
        return false;
    }

    /** Returns the current parent panel ID (for scissor bypass checks). */
    public static String getCurrentParentId() { return currentParentId; }

    /** Re-enables scissor for the current panel (used after per-item scissor bypass). */
    public static void reenableCurrentPanelScissor() {
        if (current == null || currentParentId == null) return;
        VirtualGuiElement panel = current.elements.get(currentParentId);
        if (panel == null) return;
        GuiGraphics gui = MenuRenderHelper.getGuiGraphics();
        if (gui == null) return;
        int sx = panel.getRenderX(current);
        int sy = panel.getRenderY(current);
        int sw = panel.getRenderWidth(current);
        int sh = panel.getRenderHeight(current);
        gui.enableScissor(sx, sy, sx + sw, sy + sh);
    }

    // ── Panel creation (begin/end pattern) ────────────────────────────────────────

    /**
     * Book page panel (called by the Page block inside a Book): a
     * ROOT-level panel whose position is BOOK-DRIVEN, not user-dragged.
     *
     * Unlike beginPanel, the position is (re)applied EVERY frame from
     * the book's live geometry, so the page content follows the book
     * art exactly: the closed-cover slide (+/- pageWidth/2 with
     * "offset to center") and the flip animations move the panel with
     * the book. A plain beginPanel would freeze the panel at the
     * position of the FIRST frame it rendered — for openable books
     * that is mid-animation, and the content would sit off the page
     * art forever after.
     *
     * Also note beginPanel(id, box, atX, atY, ...) resolves the
     * position from the atX/atY ANCHOR args and discards the box's
     * own x1/y1 — the Page block used to call it with 0f, 0f, which
     * parked every page panel at the top-left screen corner with all
     * its content rendered "outside the page" (the 0,0 anchor bug).
     */
    public static void beginPagePanel(String panelId, float x1, float y1,
                                      float x2, float y2, boolean collision) {
        if (current == null) return;
        VirtualGuiElement panel = current.elements.get(panelId);
        if (panel == null) {
            float w = x2 - x1;
            float h = y2 - y1;
            panel = new VirtualGuiElement(panelId, "panel", x1, y1, w, h, -1);
            panel.setPanelType(new PanelType.Empty());
            panel.setShouldStick(false);
            panel.setCollision(collision);
            panel.setScaledContent(false);
            panel.setDesignSize(w, h);
            panel.clearSolidRenderBoxes();
            panel.clearGrid();
            current.elements.put(panelId, panel);
            current.elementOrder.add(panelId);
        } else {
            // Book-driven: always follow the live book geometry.
            panel.setX(x1);
            panel.setY(y1);
            panel.setWidth(x2 - x1);
            panel.setHeight(y2 - y1);
            panel.setCollision(collision);
            panel.setVisible(true);
            panel.clearSolidRenderBoxes();
            panel.clearGrid();
        }
        panel.setLastDeclaredFrame(declFrame);
        panelStack.push(panel);
        currentParentId = panelId;
        GuiGraphics gui = MenuRenderHelper.getGuiGraphics();
        if (gui != null) {
            int sx = panel.getRenderX(current);
            int sy = panel.getRenderY(current);
            int ew = panel.getRenderWidth(current);
            int eh = panel.getRenderHeight(current);
            gui.enableScissor(sx, sy, sx + ew, sy + eh);
        }
        scissorStack.push(true);
        reparentedScissorStack.push(false);
    }

    public static void beginPanel(String panelId, float x1, float y1, float x2, float y2, PanelType type) {
        beginPanel(panelId, x1, y1, x2, y2, type, false, false);
    }

    public static void beginPanel(String panelId, float x1, float y1, float x2, float y2, PanelType type, boolean shouldStick, boolean collision) {
        beginPanel(panelId, x1, y1, x2, y2, type, shouldStick, collision, false);
    }

    public static void beginPanel(String panelId, float x1, float y1, float x2, float y2, PanelType type, boolean shouldStick, boolean collision, boolean scaledContent) {
        if (current == null) return;

        // If element already exists (rebuilt every frame in render trigger), just
        // push to stack for nesting — don't recreate, preserves position/state.
        VirtualGuiElement existing = current.elements.get(panelId);
        if (existing != null) {
            // Re-apply panel type so it stays in sync with the user's blocks.
            // Position/size are intentionally NOT updated — preserves drag/resize state.
            existing.setPanelType(type);
            // Refresh the clamp region every frame. Bounds are recomputed by the
            // user's own procedure each frame from the CURRENT screen size — if we
            // only captured them once at creation, they go stale on window resize
            // / fullscreen toggle and the panel gets clamped into an old region
            // that no longer matches the screen ("stuck where it was on init").
            if (boundariesActive) {
                existing.setBounds(boundsX1, boundsY1, boundsX2, boundsY2);
            } else {
                existing.clearBounds();
            }
            // Reposition root-level panels proportionally to the BOUNDARY, not
            // the screen. The procedure recomputes boundaries from the screen
            // every frame (e.g. screenWidth/2 - 200), which is NOT proportional
            // to the screen — so scaling panel positions by the screen ratio
            // (what the resize handler does) would drift panels off the boundary
            // corner. Instead, we scale by the boundary-size ratio: if the
            // boundary was 400x200 and is now 400x200 (centered, same size), the
            // panel stays at the same size and just moves to the new corner.
            // Only applies when bounds changed (resize) and the panel is
            // root-level (no parent) — children are handled by their parent's
            // resize logic.
            if (resizeJustHappened && existing.getParentId() == null) {
                // Determine old and new reference boxes
                float oldRX1, oldRY1, oldRX2, oldRY2;
                float newRX1, newRY1, newRX2, newRY2;
                if (prevBoundsActive && boundariesActive) {
                    oldRX1 = prevBoundsX1; oldRY1 = prevBoundsY1;
                    oldRX2 = prevBoundsX2; oldRY2 = prevBoundsY2;
                    newRX1 = boundsX1; newRY1 = boundsY1;
                    newRX2 = boundsX2; newRY2 = boundsY2;
                } else if (prevBoundsActive && !boundariesActive) {
                    // Bounds cleared — old was boundary, new is full screen
                    oldRX1 = prevBoundsX1; oldRY1 = prevBoundsY1;
                    oldRX2 = prevBoundsX2; oldRY2 = prevBoundsY2;
                    newRX1 = 0; newRY1 = 0;
                    newRX2 = screenWidth; newRY2 = screenHeight;
                } else if (!prevBoundsActive && boundariesActive) {
                    // Bounds just set — old was full screen, new is boundary
                    oldRX1 = 0; oldRY1 = 0;
                    oldRX2 = prevScreenWidth > 0 ? prevScreenWidth : screenWidth;
                    oldRY2 = prevScreenHeight > 0 ? prevScreenHeight : screenHeight;
                    newRX1 = boundsX1; newRY1 = boundsY1;
                    newRX2 = boundsX2; newRY2 = boundsY2;
                } else {
                    // No bounds before or after — use screen ratio (same as resize handler)
                    oldRX1 = 0; oldRY1 = 0;
                    oldRX2 = prevScreenWidth > 0 ? prevScreenWidth : screenWidth;
                    oldRY2 = prevScreenHeight > 0 ? prevScreenHeight : screenHeight;
                    newRX1 = 0; newRY1 = 0;
                    newRX2 = screenWidth; newRY2 = screenHeight;
                }
                float oldW = oldRX2 - oldRX1;
                float oldH = oldRY2 - oldRY1;
                float newW = newRX2 - newRX1;
                float newH = newRY2 - newRY1;
                if (oldW > 0 && oldH > 0) {
                    float sx = newW / oldW;
                    float sy = newH / oldH;
                    // Scale position relative to the reference box
                    float newX = newRX1 + (existing.getX() - oldRX1) * sx;
                    float newY = newRY1 + (existing.getY() - oldRY1) * sy;
                    float newWidth = existing.getWidth() * sx;
                    float newHeight = existing.getHeight() * sy;
                    existing.setX(newX);
                    existing.setY(newY);
                    existing.setWidth(newWidth);
                    existing.setHeight(newHeight);
                    // Re-snap grid children after repositioning (same as resize handler)
                    if (existing.hasGrid()) {
                        existing.reSnapAllChildren(current);
                    }
                    // If the panel's size changed (boundary ratio != 1.0), scale
                    // children AND all descendants recursively (grandchildren
                    // included) — they were NOT scaled by the resize handler
                    // (we skip children of bounded roots), so we do it here
                    // using the boundary ratio instead of the screen ratio.
                    if (Math.abs(sx - 1.0f) > 0.001f || Math.abs(sy - 1.0f) > 0.001f) {
                        scaleDescendants(existing, sx, sy);
                        // Re-snap grid children after child scaling
                        if (existing.hasGrid()) {
                            existing.reSnapAllChildren(current);
                        }
                    }
                }
            }
            existing.setShouldStick(shouldStick);
            existing.setCollision(collision);
            existing.setScaledContent(scaledContent);
            if (scaledContent && existing.getDesignWidth() == 0f) {
                existing.setDesignSize(x2 - x1, y2 - y1);
            }
            existing.clearSolidRenderBoxes();
            existing.clearGrid();
            // Capture the PROCEDURE's parent (what currentParentId is NOW, before
            // we set it to this panel). This is the nesting in the user's code,
            // which may differ from the actual parent if the panel was reparented
            // by dragging.
            String procedureParentId = currentParentId;
            panelStack.push(existing);
            currentParentId = panelId;

            GuiGraphics gui = MenuRenderHelper.getGuiGraphics();
            // Detect if this panel has been reparented away from the procedure's
            // nesting. The procedure always calls beginPanel("panelB") inside
            // beginPanel("panelA"), but panelB might have been dragged out and
            // reparented to root or another panel. If so, panelA's scissor is
            // still active and would INTERSECT with panelB's scissor, clipping
            // panelB's children to the overlap area ("scissored in half").
            // Fix: disable the procedure parent's scissor before enabling
            // panelB's own scissor, and re-enable it in endPanel.
            boolean reparented = !java.util.Objects.equals(procedureParentId, existing.getParentId());
            existing.clearPendingFills();
            if (gui != null) {
                // Background is now rendered during renderElements (in render())
                // to ensure correct z-ordering across panels.
                // If reparented, pop the procedure parent's scissor so panelB's
                // scissor is NOT intersected with the old parent's bounds.
                if (reparented && !scissorStack.isEmpty() && scissorStack.peek()) {
                    gui.disableScissor();
                }
                int sx = existing.getRenderX(current);
                int sy = existing.getRenderY(current);
                int ew = existing.getRenderWidth(current);
                int eh = existing.getRenderHeight(current);
                gui.enableScissor(sx, sy, sx + ew, sy + eh);
            }
            scissorStack.push(true);
            reparentedScissorStack.push(reparented);
            return;
        }

        // For NEW top-level panels: snap the box inside the active clamp region.
        // Prefer the explicit bounds (setBoundaries scope) over the raw screen —
        // if the caller set up a bounds region, a panel created inside that scope
        // is meant to live inside it, not just anywhere on screen.
        if (currentParentId == null) {
            if (boundariesActive) {
                float[] snapped = snapBoxInto(x1, y1, x2, y2, boundsX1, boundsY1, boundsX2, boundsY2);
                x1 = snapped[0]; y1 = snapped[1]; x2 = snapped[2]; y2 = snapped[3];
            } else if (screenWidth > 0 && screenHeight > 0) {
                float[] snapped = snapBoxInto(x1, y1, x2, y2, 0, 0, screenWidth, screenHeight);
                x1 = snapped[0]; y1 = snapped[1]; x2 = snapped[2]; y2 = snapped[3];
            }
        }

        float w = x2 - x1;
        float h = y2 - y1;
        VirtualGuiElement panel = new VirtualGuiElement(panelId, "panel", x1, y1, w, h, -1);
        applyPersistentState(panel);
        panel.setPanelType(type);

        if (currentParentId != null) {
            panel.setParentId(currentParentId);
            panel.setHomeParentId(currentParentId);
            VirtualGuiElement parent = current.elements.get(currentParentId);
            if (parent != null) parent.addChild(panel);
        }

        if (boundariesActive) {
            panel.setBounds(boundsX1, boundsY1, boundsX2, boundsY2);
        }

        panel.setShouldStick(shouldStick);
        panel.setCollision(collision);
        panel.setScaledContent(scaledContent);
        panel.setDesignSize(w, h);
        panel.clearSolidRenderBoxes();
        panel.clearGrid();
        current.elements.put(panelId, panel);
        current.elementOrder.add(panelId);
        String procedureParentId = currentParentId;
        panelStack.push(panel);
        currentParentId = panelId;

        // Render background + enable scissor for new panels too.
        GuiGraphics gui = MenuRenderHelper.getGuiGraphics();
        // New panels are never reparented (just created), but compute for
        // consistency with endPanel's scissor re-enable logic.
        boolean reparented = !java.util.Objects.equals(procedureParentId, panel.getParentId());
        panel.clearPendingFills();
        if (gui != null) {
            // Background is now rendered during renderElements (in render())
            // to ensure correct z-ordering across panels.
            // If reparented (shouldn't happen for new panels, but for safety),
            // pop the procedure parent's scissor first.
            if (reparented && !scissorStack.isEmpty() && scissorStack.peek()) {
                gui.disableScissor();
            }
            int sx = panel.getRenderX(current);
            int sy = panel.getRenderY(current);
            int ew = panel.getRenderWidth(current);
            int eh = panel.getRenderHeight(current);
            gui.enableScissor(sx, sy, sx + ew, sy + eh);
        }
        scissorStack.push(true);
        reparentedScissorStack.push(reparented);
    }

    /**
     * Snaps a box (x1,y1,x2,y2) so it fits inside the given region
     * (limX1,limY1,limX2,limY2), preserving its width/height where possible.
     * Only snaps if the box actually falls outside the region.
     * Returns {x1, y1, x2, y2}.
     */
    private static float[] snapBoxInto(float x1, float y1, float x2, float y2,
                                        float limX1, float limY1, float limX2, float limY2) {
        if (x1 >= limX1 && x2 <= limX2 && y1 >= limY1 && y2 <= limY2) {
            return new float[]{x1, y1, x2, y2};
        }
        float boxW = x2 - x1;
        float boxH = y2 - y1;
        float regionW = Math.max(0, limX2 - limX1);
        float regionH = Math.max(0, limY2 - limY1);
        // Clamp size to region first
        if (boxW > regionW) boxW = regionW;
        if (boxH > regionH) boxH = regionH;
        // Snap right/bottom edge inside, then clamp left/top inside too
        float newX1 = Math.max(limX1, Math.min(x1, limX2 - boxW));
        float newY1 = Math.max(limY1, Math.min(y1, limY2 - boxH));
        return new float[]{newX1, newY1, newX1 + boxW, newY1 + boxH};
    }

    public static void endPanel() {
        // Disable scissor that was enabled in beginPanel/beginScrollView.
        // Only disable if the scissor was actually enabled for this panel level
        // (drag-out panels skip scissor). Must happen BEFORE popping the stack
        // so the scissor region matches the panel that enabled it.
        GuiGraphics gui = MenuRenderHelper.getGuiGraphics();
        boolean wasReparented = !reparentedScissorStack.isEmpty() && reparentedScissorStack.pop();
        if (!scissorStack.isEmpty()) {
            boolean wasEnabled = scissorStack.pop();
            if (gui != null && wasEnabled) {
                gui.disableScissor();
            }
        }
        if (!panelStack.isEmpty()) {
            panelStack.pop();
            currentParentId = panelStack.isEmpty() ? null : panelStack.peek().getId();
        }
        // If this panel was reparented (its procedure parent's scissor was
        // disabled in beginPanel), re-enable the parent's scissor now.
        if (wasReparented && gui != null && !panelStack.isEmpty()) {
            VirtualGuiElement parent = panelStack.peek();
            int psx = parent.getRenderX(current);
            int psy = parent.getRenderY(current);
            int pew = parent.getRenderWidth(current);
            int peh = parent.getRenderHeight(current);
            gui.enableScissor(psx, psy, psx + pew, psy + peh);
        }
    }

    public static VirtualGuiElement getCurrentElement() {
        if (currentRenderElementId != null && current != null) {
            VirtualGuiElement el = current.elements.get(currentRenderElementId);
            if (el != null) return el;
        }
        return panelStack.isEmpty() ? null : panelStack.peek();
    }

    /** Returns true if we're currently inside a beginPanel/endPanel scope. */
    public static boolean isInsidePanel() {
        return current != null && currentParentId != null;
    }

    /**
     * Width to use for "fill the whole parent" purposes (e.g. the "Whole" box
     * block). Returns the DESIGN width when the current element has scaled
     * content enabled (children live in 0..designWidth space), otherwise the
     * element's actual on-screen width.
     */
    public static float getCurrentElementWholeWidth() {
        VirtualGuiElement el = getCurrentElement();
        if (el == null) return 0f;
        return el.getScaledContent() ? el.getDesignWidth() : el.getWidth();
    }

    public static float getCurrentElementWholeHeight() {
        VirtualGuiElement el = getCurrentElement();
        if (el == null) return 0f;
        return el.getScaledContent() ? el.getDesignHeight() : el.getHeight();
    }

    /** Returns the current VirtualGui state object (for MenuRenderHelper access). */
    public static VirtualGui getCurrentGui() {
        return current;
    }

    /** Returns the active render element (if inside beginRenderElement/endRenderElement scope). */
    public static VirtualGuiElement getCurrentRenderElement() {
        if (current == null || currentRenderElementId == null) return null;
        return current.elements.get(currentRenderElementId);
    }

    /** Sets the menu object on the current render element (for "Render menu object" with interactions). */
    public static void setCurrentMenuObject(MenuObject mo, String pivot) {
        if (current == null || currentRenderElementId == null) return;
        VirtualGuiElement el = current.elements.get(currentRenderElementId);
        if (el != null) {
            // Store a COPY so each render element gets its own independent snapshot.
            // Without this, reusing the same MenuObject across multiple elements
            // (e.g. in a loop) would cause all elements to share the same mutable
            // object — a changeText call on one would affect all of them.
            el.setMenuObject(mo.copy());
            el.setMenuObjectPivot(pivot);
        }
    }

    // ── Render elements (items/textures with interactions) ──────────────────────

    /**
     * Begins a render element scope. When a render call (renderItem/renderTexture)
     * has interactions (attrs) added via the gear mutator, the FTL wraps the call
     * in beginRenderElement/endRenderElement. This creates a tracked
     * VirtualGuiElement for the render call so it can be resized, dragged, etc.
     *
     * On first frame: creates the element with the given position/size.
     * On subsequent frames: retrieves the existing element (preserves drag/resize state).
     *
     * "render_item" type: corner-only resize, maintain aspect ratio (scale proportionally).
     * "render_texture" type: edge resize, free stretch.
     * "render_rect" / "render_rect_outline" type: edge resize, free stretch.
     */
    public static void beginRenderElement(String id, String type, float x, float y,
                                           float w, float h, boolean shouldStick) {
        beginRenderElement(id, type, x, y, w, h, shouldStick, false);
    }

    /**
     * Pivot-aware render element creation. Resolves the pivot to compute the
     * element's top-left corner, then delegates to the standard beginRenderElement.
     * Used by render_rect and render_rect_outline which have a pivot parameter.
     */
    public static void beginRenderElement(String id, String type, float x, float y,
                                           float w, float h, boolean shouldStick, boolean collision, String pivot) {
        float[] resolved = resolvePivot(x, y, w, h, pivot);
        beginRenderElement(id, type, resolved[0], resolved[1], w, h, shouldStick, collision);
    }

    public static void beginRenderElement(String id, String type, float x, float y,
                                           float w, float h, boolean shouldStick, boolean collision) {
        if (current == null) return;

        VirtualGuiElement existing = current.elements.get(id);
        if (existing != null) {
            // Don't update position/size — preserves drag/resize state.
            existing.setShouldStick(shouldStick);
            existing.setCollision(collision);
            // Refresh the clamp region every frame — same as beginPanel.
            if (boundariesActive) {
                existing.setBounds(boundsX1, boundsY1, boundsX2, boundsY2);
            } else {
                existing.clearBounds();
            }
            existing.clearSolidRenderBoxes();
            existing.clearGrid();
            currentRenderElementId = id;
        } else {
            VirtualGuiElement el = new VirtualGuiElement(id, type, x, y, w, h, -1);
            applyPersistentState(el);
            el.setShouldStick(shouldStick);
            el.setCollision(collision);
            // Resize behavior depends on element type:
            // - render_item, menu_object: corner-only resize, maintain aspect ratio (1:1)
            // - render_texture: edge resize, free stretch
            // - render_rect, render_rect_outline: edge resize, free stretch
            if ("render_item".equals(type) || "menu_object".equals(type)) {
                el.setCornerResizeOnly(true);
                el.setMaintainAspectRatio(true);
            } else {
                el.setCornerResizeOnly(false);
                el.setMaintainAspectRatio(false);
            }

            if (currentParentId != null) {
                el.setParentId(currentParentId);
                el.setHomeParentId(currentParentId);
                VirtualGuiElement parent = current.elements.get(currentParentId);
                if (parent != null) parent.addChild(el);
                // Auto-snap to parent's grid on first creation so new elements
                // start centered in their grid cell instead of at raw position.
                if (parent.hasGrid()) {
                    parent.snapChildToGrid(el, current, false);
                }
            }

            if (boundariesActive) {
                el.setBounds(boundsX1, boundsY1, boundsX2, boundsY2);
            }

            current.elements.put(id, el);
            current.elementOrder.add(id);
            currentRenderElementId = id;
        }
    }

    /** Ends a render element scope. */
    public static void endRenderElement() {
        if (currentRenderElementId == null || current == null) return;
        // Decorations are now rendered at the end of render() (after children
        // and items) so highlights appear on top of scissored content.
        currentRenderElementId = null;
    }

    // ── Screen boundaries ───────────────────────────────────────────────────────

    public static void setBoundaries(float x1, float y1, float x2, float y2) {
        // Always store previous frame's boundary values. The procedure calls
        // setBoundaries every frame, so prevBoundsActive is always true here.
        // clearBoundaries() at end of frame sets boundariesActive=false, but
        // we must NOT let that leak into prevBoundsActive — otherwise next
        // frame's setBoundaries would think bounds just appeared and scale
        // from screen to boundary (shrinking every resize).
        prevBoundsActive = true;
        prevBoundsX1 = boundsX1; prevBoundsY1 = boundsY1;
        prevBoundsX2 = boundsX2; prevBoundsY2 = boundsY2;
        boundariesActive = true;
        boundsX1 = x1; boundsY1 = y1;
        boundsX2 = x2; boundsY2 = y2;
    }

    public static void clearBoundaries() {
        boundariesActive = false;
    }

    public static float getScreenWidth() { return screenWidth; }
    public static float getScreenHeight() { return screenHeight; }

    /** Returns the absolute X of the current parent panel (for render offset). */
    public static float getCurrentParentAbsX() {
        if (current == null || currentParentId == null) return 0f;
        VirtualGuiElement parent = current.elements.get(currentParentId);
        return parent != null ? parent.getAbsoluteX(current) : 0f;
    }

    /** Returns the absolute Y of the current parent panel (for render offset). */
    public static float getCurrentParentAbsY() {
        if (current == null || currentParentId == null) return 0f;
        VirtualGuiElement parent = current.elements.get(currentParentId);
        return parent != null ? parent.getAbsoluteY(current) : 0f;
    }

    /** Returns the scale factor of the current parent panel (for render scaling). */
    public static float getCurrentParentScaleX() {
        if (current == null || currentParentId == null) return 1f;
        VirtualGuiElement parent = current.elements.get(currentParentId);
        return parent != null ? parent.getScaleX() : 1f;
    }

    public static float getCurrentParentScaleY() {
        if (current == null || currentParentId == null) return 1f;
        VirtualGuiElement parent = current.elements.get(currentParentId);
        return parent != null ? parent.getScaleY() : 1f;
    }

    /** Returns the current parent panel's accumulated resize delta X (for render calls). */
    public static float getCurrentParentResizeDeltaX() {
        if (current == null || currentParentId == null) return 0f;
        VirtualGuiElement parent = current.elements.get(currentParentId);
        return parent != null ? parent.getResizeDeltaX() : 0f;
    }

    /** Returns the current parent panel's accumulated resize delta Y (for render calls). */
    public static float getCurrentParentResizeDeltaY() {
        if (current == null || currentParentId == null) return 0f;
        VirtualGuiElement parent = current.elements.get(currentParentId);
        return parent != null ? parent.getResizeDeltaY() : 0f;
    }

    /** Returns the current parent panel's integer render X (for lockstep rendering). */
    public static int getCurrentParentRenderX() {
        if (current == null || currentParentId == null) return 0;
        VirtualGuiElement parent = current.elements.get(currentParentId);
        return parent != null ? parent.getRenderX(current) : 0;
    }

    /** Returns the current parent panel's integer render Y (for lockstep rendering). */
    public static int getCurrentParentRenderY() {
        if (current == null || currentParentId == null) return 0;
        VirtualGuiElement parent = current.elements.get(currentParentId);
        return parent != null ? parent.getRenderY(current) : 0;
    }

    /** Returns the current parent panel's content scale X (for render-call scaling). */
    public static float getCurrentContentScaleX() {
        if (current == null || currentParentId == null) return 1.0f;
        VirtualGuiElement parent = current.elements.get(currentParentId);
        return parent != null ? parent.getContentScaleX() : 1.0f;
    }

    /** Returns the current parent panel's content scale Y (for render-call scaling). */
    public static float getCurrentContentScaleY() {
        if (current == null || currentParentId == null) return 1.0f;
        VirtualGuiElement parent = current.elements.get(currentParentId);
        return parent != null ? parent.getContentScaleY() : 1.0f;
    }


    /**
     * Registers a collision (collision=true) render call's LOCAL box against the
     * current parent panel, so its resize can be clamped to never clip it.
     * Called by MenuRenderHelper for renderRect/renderTexture/renderItem/etc.
     * when their collision flag is true. No-op if there's no current parent panel.
     */
    public static void registerSolidBox(float x1, float y1, float x2, float y2) {
        if (current == null || currentParentId == null) return;
        VirtualGuiElement parent = current.elements.get(currentParentId);
        if (parent != null) parent.addSolidRenderBox(x1, y1, x2, y2);
    }

    // ── Button creation ───────────────────────────────────────────────────────────

    public static void addButton(String id, float x, float y, float w, float h, int color,
                                  String actionId, String[] checkIds) {
        addButton(id, x, y, w, h, color, actionId, checkIds, false, false, "top-left");
    }

    public static void addButton(String id, float x, float y, float w, float h, int color,
                                  String actionId, String[] checkIds, boolean shouldStick) {
        addButton(id, x, y, w, h, color, actionId, checkIds, shouldStick, false, "top-left");
    }

    public static void addButton(String id, float x, float y, float w, float h, int color,
                                  String actionId, String[] checkIds, boolean shouldStick, boolean collision) {
        addButton(id, x, y, w, h, color, actionId, checkIds, shouldStick, collision, "top-left");
    }

    public static void addButton(String id, float x, float y, float w, float h, int color,
                                  String actionId, String[] checkIds, boolean shouldStick, boolean collision,
                                  String pivot) {
        if (current == null) return;

        // If element already exists, update config but DON'T touch position/size —
        // same pattern as beginPanel/beginRenderElement. Preserves window-resize
        // scaling and drag/resize state, which would otherwise be wiped out every
        // frame by the procedure re-running with its original literal x/y/w/h.
        if (current.elements.containsKey(id)) {
            VirtualGuiElement existing = current.elements.get(id);
            existing.setColor(color);
            existing.setActionId(actionId);
            existing.setShouldStick(shouldStick);
            existing.setCollision(collision);
            if (checkIds != null) existing.setCheckIds(checkIds);
            if (boundariesActive) {
                existing.setBounds(boundsX1, boundsY1, boundsX2, boundsY2);
            } else {
                existing.clearBounds();
            }
            return;
        }

        // Apply pivot offset — same logic as MenuRenderHelper.renderRect.
        // (x, y) is the anchor; pivot determines how the w×h box is positioned
        // relative to that anchor. "top-left" = no offset (default).
        float actualX = x;
        float actualY = y;
        if (pivot == null) pivot = "top-left";
        switch (pivot) {
            case "top": case "center": case "bottom":
                actualX -= w / 2; break;
            case "top-right": case "right": case "bottom-right":
                actualX -= w; break;
        }
        switch (pivot) {
            case "left": case "center": case "right":
                actualY -= h / 2; break;
            case "bottom-left": case "bottom": case "bottom-right":
                actualY -= h; break;
        }

        VirtualGuiElement button = new VirtualGuiElement(id, "button", actualX, actualY, w, h, color);
        applyPersistentState(button);
        button.setShouldStick(shouldStick);
        button.setCollision(collision);
        button.setActionId(actionId);
        if (checkIds != null) button.setCheckIds(checkIds);

        if (currentParentId != null) {
            button.setParentId(currentParentId);
            VirtualGuiElement parent = current.elements.get(currentParentId);
            if (parent != null) parent.addChild(button);
        }

        current.elements.put(id, button);
        current.elementOrder.add(id);
    }

    // ── Slider creation ──────────────────────────────────────────────────────────

    public static void addSlider(String id, float x, float y, float w, float h, String direction, String sliderType,
                                  float min, float max, float step, String actionId, String[] checkIds) {
        addSlider(id, x, y, w, h, direction, sliderType, min, max, step, actionId, checkIds, false);
    }

    public static void addSlider(String id, float x, float y, float w, float h, String direction, String sliderType,
                                  float min, float max, float step, String actionId, String[] checkIds, boolean shouldStick) {
        addSlider(id, x, y, w, h, direction, sliderType, min, max, step, actionId, checkIds, shouldStick, false);
    }

    public static void addSlider(String id, float x, float y, float w, float h, String direction, String sliderType,
                                  float min, float max, float step, String actionId, String[] checkIds, boolean shouldStick, boolean collision) {
        addSlider(id, x, y, w, h, direction, sliderType, min, max, step, actionId, checkIds, shouldStick, collision, false);
    }

    public static void addSlider(String id, float x, float y, float w, float h, String direction, String sliderType,
                                  float min, float max, float step, String actionId, String[] checkIds, boolean shouldStick, boolean collision,
                                  boolean reverseDefault) {
        if (current == null) return;

        // If element already exists, update config but DON'T touch position/size —
        // same pattern as beginPanel/beginRenderElement. Preserves window-resize
        // scaling; otherwise the procedure's literal x/y/w/h would overwrite the
        // scaled size every frame and the slider would look wrong/hidden after
        // a fullscreen <-> windowed toggle.
        if (current.elements.containsKey(id)) {
            VirtualGuiElement existing = current.elements.get(id);
            if (boundariesActive) {
                existing.setBounds(boundsX1, boundsY1, boundsX2, boundsY2);
            } else {
                existing.clearBounds();
            }
            existing.setSliderDirection(direction);
            existing.setSliderType(sliderType);
            existing.setSliderMin(min);
            existing.setSliderMax(max);
            existing.setStepSize(step);
            existing.setActionId(actionId);
            existing.setShouldStick(shouldStick);
            existing.setCollision(collision);
            existing.setReverseDefault(reverseDefault);
            if (checkIds != null) existing.setCheckIds(checkIds);
            return;
        }

        VirtualGuiElement slider = new VirtualGuiElement(id, "slider", x, y, w, h, -1);
        applyPersistentState(slider);
        slider.setShouldStick(shouldStick);
        slider.setSliderDirection(direction);
        slider.setSliderType(sliderType);
        slider.setSliderMin(min);
        slider.setSliderMax(max);
        slider.setSliderValue(reverseDefault ? max : min);
        slider.setStepSize(step);
        slider.setReverseDefault(reverseDefault);
        slider.setColor(0xFF4A90D9);
        slider.setActionId(actionId);
        if (checkIds != null) slider.setCheckIds(checkIds);

        if (currentParentId != null) {
            slider.setParentId(currentParentId);
            VirtualGuiElement parent = current.elements.get(currentParentId);
            if (parent != null) parent.addChild(slider);
        }

        current.elements.put(id, slider);
        current.elementOrder.add(id);
    }

    // ── Visibility ───────────────────────────────────────────────────────────────

    public static void hideElement(String id) {
        if (current == null) return;
        VirtualGuiElement el = current.elements.get(id);
        if (el != null) el.setVisible(false);
    }

    public static void showElement(String id) {
        if (current == null) return;
        VirtualGuiElement el = current.elements.get(id);
        if (el != null) el.setVisible(true);
    }

    public static boolean isElementVisible(String id) {
        if (current == null) return false;
        VirtualGuiElement el = current.elements.get(id);
        return el != null && el.isVisible();
    }

    // ── Position / Size ──────────────────────────────────────────────────────────

    public static void setElementPosition(String id, float x, float y) {
        if (current == null) return;
        VirtualGuiElement el = current.elements.get(id);
        if (el != null) { el.setX(x); el.setY(y); }
    }

    public static void setElementSize(String id, float w, float h) {
        if (current == null) return;
        VirtualGuiElement el = current.elements.get(id);
        if (el != null) { el.setWidth(w); el.setHeight(h); }
    }

    public static float getElementAbsoluteX(String id) {
        if (current == null) return 0f;
        VirtualGuiElement el = current.elements.get(id);
        return el != null ? el.getAbsoluteX(current) : 0f;
    }

    public static float getElementAbsoluteY(String id) {
        if (current == null) return 0f;
        VirtualGuiElement el = current.elements.get(id);
        return el != null ? el.getAbsoluteY(current) : 0f;
    }

    public static float getElementRelativeX(String id) {
        if (current == null) return 0f;
        VirtualGuiElement el = current.elements.get(id);
        return el != null ? el.getX() : 0f;
    }

    public static float getElementRelativeY(String id) {
        if (current == null) return 0f;
        VirtualGuiElement el = current.elements.get(id);
        return el != null ? el.getY() : 0f;
    }

    public static float getElementWidth(String id) {
        if (current == null) return 0f;
        VirtualGuiElement el = current.elements.get(id);
        return el != null ? el.getWidth() : 0f;
    }

    public static float getElementHeight(String id) {
        if (current == null) return 0f;
        VirtualGuiElement el = current.elements.get(id);
        return el != null ? el.getHeight() : 0f;
    }

    public static float getElementContentScaleX(String id) {
        if (current == null) return 1.0f;
        VirtualGuiElement el = current.elements.get(id);
        return el != null ? el.getContentScaleX() : 1.0f;
    }

    public static float getElementContentScaleY(String id) {
        if (current == null) return 1.0f;
        VirtualGuiElement el = current.elements.get(id);
        return el != null ? el.getContentScaleY() : 1.0f;
    }

    // ── Hit testing ──────────────────────────────────────────────────────────────

    public static boolean isElementHovered(String id, float mx, float my) {
        if (current == null) return false;
        VirtualGuiElement el = current.elements.get(id);
        if (el == null || !el.isVisible()) return false;
        return el.isHovered(current, mx, my);
    }

    // ── Slider ──────────────────────────────────────────────────────────────────

    public static void setSliderValue(String id, float value) {
        if (current == null) return;
        VirtualGuiElement el = current.elements.get(id);
        if (el != null) el.setSliderValue(value);
    }

    public static float getSliderValue(String id) {
        if (current == null) return 0f;
        VirtualGuiElement el = current.elements.get(id);
        return el != null ? el.getSliderValue() : 0f;
    }

    // ── Drag system ──────────────────────────────────────────────────────────────

    private static String draggingElementId = null;
    private static String preDragParentId = null;
    private static float preDragAbsX = 0f;
    private static float preDragAbsY = 0f;
    private static float dragOffsetX = 0f;
    private static float dragOffsetY = 0f;

    // Slider drag state
    private static String draggingSliderId = null;

    // Pending click for button+draggable disambiguation
    // When an element has BOTH Button and Draggable attributes, a click in the
    // drag zone becomes "pending" — if the mouse moves beyond DRAG_THRESHOLD,
    // it converts to a drag; if released without moving, it fires the button action.
    private static String pendingClickElementId = null;
    private static float pendingClickStartX = 0f;
    private static float pendingClickStartY = 0f;
    private static final float DRAG_THRESHOLD = 3f;

    // Double-click detection
    private static long lastClickTime = 0;
    private static String lastClickElementId = null;
    private static float lastClickX = 0f;
    private static float lastClickY = 0f;
    private static final long DOUBLE_CLICK_INTERVAL_MS = 500;

    // Resize state (priority over drag)
    private static String resizingElementId = null;
    private static int resizeDirection = 0; // bitmask of RESIZE_LEFT/RIGHT/TOP/BOTTOM
    private static float resizeStartMouseX = 0f;
    private static float resizeStartMouseY = 0f;
    private static float resizeStartAbsX = 0f;
    private static float resizeStartAbsY = 0f;
    private static float resizeStartW = 0f;
    private static float resizeStartH = 0f;
    private static final float MIN_RESIZE_SIZE = 10f;

    public static boolean handleClick(float mx, float my, int button) {
        if (button != 0 || !isAnyOpen() || current == null) return false;

        // Store mouse position for action context
        setActionMouseX(mx);
        setActionMouseY(my);

        // Determine what's actually VISUALLY topmost at this exact point, like a
        // real OS window manager. A panel with no button/drag zone at this spot
        // still has a solid background that blocks clicks from reaching whatever
        // is rendered behind it (e.g. an item in a different, lower panel).
        // Elements may only be interacted with if they OWN this point — i.e. they
        // ARE the topmost hit, or they're an ANCESTOR of it (a panel's own
        // resize/drag furniture isn't blocked by its own children sitting on
        // top of it — only by unrelated elements from a different branch).
        VirtualGuiElement topmostAtPoint = getTopmostElementAt(mx, my);

        // Priority: resize → slider → button → panel drag
        // Resize comes FIRST so that when a panel is shrunk so small that child
        // elements (sliders, buttons) cover the resize edges, the user can still
        // grab an edge to resize the panel back.

        // Resize check (highest priority)
        for (int i = current.elementOrder.size() - 1; i >= 0; i--) {
            VirtualGuiElement el = current.elements.get(current.elementOrder.get(i));
            if (el == null || !el.isVisible()) continue;
            if (!ownsPoint(el, topmostAtPoint)) continue;
            int edge = el.getResizeEdge(current, mx, my);
            if (edge != VirtualGuiElement.RESIZE_NONE) {
                bringToFront(el.getId());
                resizingElementId = el.getId();
                resizeDirection = edge;
                resizeStartMouseX = mx;
                resizeStartMouseY = my;
                resizeStartAbsX = el.getAbsoluteX(current);
                resizeStartAbsY = el.getAbsoluteY(current);
                resizeStartW = el.getWidth();
                resizeStartH = el.getHeight();
                return true;
            }
        }

        // Slider click — start slider drag
        for (int i = current.elementOrder.size() - 1; i >= 0; i--) {
            VirtualGuiElement el = current.elements.get(current.elementOrder.get(i));
            if (el == null || !el.isVisible()) continue;
            if (!ownsPoint(el, topmostAtPoint)) continue;

            if ("slider".equals(el.getType()) && el.canInteract() && el.isHovered(current, mx, my)) {
                draggingSliderId = el.getId();
                updateSliderFromMouse(el, mx, my);
                return true;
            }

            // Button click — fire action if checks pass
            if ("button".equals(el.getType()) && el.canInteract() && el.isHovered(current, mx, my)) {
                el.setActive(true);
                // Check for double-click on render elements with doubleClickActionId
                long now = System.currentTimeMillis();
                if (el.getDoubleClickActionId() != null && !el.getDoubleClickActionId().isEmpty()
                        && now - lastClickTime < DOUBLE_CLICK_INTERVAL_MS
                        && el.getId().equals(lastClickElementId)
                        && Math.abs(mx - lastClickX) < DRAG_THRESHOLD * 2
                        && Math.abs(my - lastClickY) < DRAG_THRESHOLD * 2) {
                    setActionReason("double_click");
                    setActionElementId(el.getId());
                    callAction(el.getDoubleClickActionId());
                    lastClickTime = 0; // Reset so triple-click doesn't fire again
                    return true;
                }
                lastClickTime = now;
                lastClickElementId = el.getId();
                lastClickX = mx;
                lastClickY = my;
                if (el.getActionId() != null && !el.getActionId().isEmpty()) {
                    setActionReason("button_clicked");
                    setActionElementId(el.getId());
                    callAction(el.getActionId());
                }
                return true;
            }
        }

        // Panel button attribute + drag (coordinate to avoid conflicts)
        for (int i = current.elementOrder.size() - 1; i >= 0; i--) {
            VirtualGuiElement el = current.elements.get(current.elementOrder.get(i));
            if (el == null || !el.isVisible()) continue;
            if (!ownsPoint(el, topmostAtPoint)) continue;

            boolean inDragZone = el.isInDragZone(current, mx, my);
            boolean hasButtonAttr = el.hasButtonAttribute() && el.canButtonInteract()
                                    && el.isHovered(current, mx, my);

            if (hasButtonAttr && inDragZone) {
                // Both button and draggable — pending click (resolve on drag or release)
                bringToFront(el.getId());
                pendingClickElementId = el.getId();
                pendingClickStartX = mx;
                pendingClickStartY = my;
                return true;
            }

            // Double-click on draggable-only elements (no button attr but has doubleClickActionId)
            if (el.getDoubleClickActionId() != null && !el.getDoubleClickActionId().isEmpty()
                    && el.isHovered(current, mx, my)) {
                long now = System.currentTimeMillis();
                if (now - lastClickTime < DOUBLE_CLICK_INTERVAL_MS
                        && el.getId().equals(lastClickElementId)
                        && Math.abs(mx - lastClickX) < DRAG_THRESHOLD * 2
                        && Math.abs(my - lastClickY) < DRAG_THRESHOLD * 2) {
                    setActionReason("double_click");
                    setActionElementId(el.getId());
                    callAction(el.getDoubleClickActionId());
                    lastClickTime = 0;
                    // Cancel any pending drag from this click
                    pendingClickElementId = null;
                    return true;
                }
                lastClickTime = now;
                lastClickElementId = el.getId();
                lastClickX = mx;
                lastClickY = my;
            }

            if (hasButtonAttr && !inDragZone) {
                // Button only (not in drag zone) — fire immediately
                long now = System.currentTimeMillis();
                if (el.getDoubleClickActionId() != null && !el.getDoubleClickActionId().isEmpty()
                        && now - lastClickTime < DOUBLE_CLICK_INTERVAL_MS
                        && el.getId().equals(lastClickElementId)
                        && Math.abs(mx - lastClickX) < DRAG_THRESHOLD * 2
                        && Math.abs(my - lastClickY) < DRAG_THRESHOLD * 2) {
                    setActionReason("double_click");
                    setActionElementId(el.getId());
                    callAction(el.getDoubleClickActionId());
                    lastClickTime = 0;
                    return true;
                }
                lastClickTime = now;
                lastClickElementId = el.getId();
                lastClickX = mx;
                lastClickY = my;
                if (el.getButtonActionId() != null && !el.getButtonActionId().isEmpty()) {
                    setActionReason("button_clicked");
                    setActionElementId(el.getId());
                    callAction(el.getButtonActionId());
                }
                return true;
            }

            if (inDragZone && !hasButtonAttr) {
                // Drag only — start drag immediately
                setActionReason("drag_start");
                setActionElementId(el.getId());
                bringToFront(el.getId());
                draggingElementId = el.getId();
                preDragParentId = el.getParentId();
                preDragAbsX = el.getAbsoluteX(current);
                preDragAbsY = el.getAbsoluteY(current);
                dragOffsetX = mx - el.getAbsoluteX(current);
                dragOffsetY = my - el.getAbsoluteY(current);
                callAction(el.getActionId());
                callAction(el.getButtonActionId());
                return true;
            }
        }
        return false;
    }

    /**
     * Handles right-click (button == 1). Finds the topmost element at the click
     * position that has a rightClickActionId and fires its action.
     */
    public static boolean handleRightClick(float mx, float my) {
        if (!isAnyOpen() || current == null) return false;

        setActionMouseX(mx);
        setActionMouseY(my);

        VirtualGuiElement topmostAtPoint = getTopmostElementAt(mx, my);

        // Find the topmost element with a right-click action at this position
        for (int i = current.elementOrder.size() - 1; i >= 0; i--) {
            VirtualGuiElement el = current.elements.get(current.elementOrder.get(i));
            if (el == null || !el.isVisible()) continue;
            if (!ownsPoint(el, topmostAtPoint)) continue;
            if (el.getRightClickActionId() == null || el.getRightClickActionId().isEmpty()) continue;
            if (!el.isHovered(current, mx, my)) continue;

            setActionReason("right_click");
            setActionElementId(el.getId());
            callAction(el.getRightClickActionId());
            return true;
        }

        return false;
    }

    // ── Topmost hit-testing (click-through prevention) ──────────────────────────
    // Mirrors the actual render order used by renderElements()/render(): root
    // elements are visited in REVERSE elementOrder (bringToFront moves an id to
    // the end = rendered last = frontmost), and children are visited in REVERSE
    // insertion order of the `children` list (render() draws them forward, so
    // the last-added child is drawn last = on top). Whatever is found this way
    // is what's ACTUALLY visible at that pixel — nothing behind it should react
    // to a click there, even if it has no button/drag zone of its own (a plain
    // panel background is still opaque and blocks clicks like a real window).


    /**
     * Final boundary enforcement using RENDER coordinates (not absolute).
     * Catches cases where content-scale mismatch lets the visual area
     * escape the boundary even though the absolute coordinates are clamped.
     * Adjusts the element's local position to bring render bounds inside.
     */
    private static void enforceRenderBounds(VirtualGuiElement el) {
        if (current == null || el == null || !el.hasBounds()) return;
        int rx = el.getRenderX(current);
        int ry = el.getRenderY(current);
        int rw = el.getRenderWidth(current);
        int rh = el.getRenderHeight(current);
        float bx1 = el.getBoundsX1();
        float by1 = el.getBoundsY1();
        float bx2 = el.getBoundsX2();
        float by2 = el.getBoundsY2();

        // Compute needed adjustment in render space
        float deltaX = 0, deltaY = 0;
        if (rx < bx1) deltaX = bx1 - rx;
        if (ry < by1) deltaY = by1 - ry;
        if (rx + rw > bx2) deltaX = Math.min(deltaX, -(rx + rw - bx2));
        if (ry + rh > by2) deltaY = Math.min(deltaY, -(ry + rh - by2));
        if (deltaX == 0 && deltaY == 0) return;

        // Convert render-space delta to local-space delta.
        // For children of scaledContent panels, render = parent.render + local*scale,
        // so local_delta = render_delta / scale.
        float scaleX = 1f, scaleY = 1f;
        if (el.getParentId() != null) {
            VirtualGuiElement parent = current.elements.get(el.getParentId());
            if (parent != null) {
                scaleX = parent.getEffectiveContentScaleX(current);
                scaleY = parent.getEffectiveContentScaleY(current);
            }
        }
        if (scaleX == 0) scaleX = 1f;
        if (scaleY == 0) scaleY = 1f;
        el.setX(el.getX() + deltaX / scaleX);
        el.setY(el.getY() + deltaY / scaleY);
    }

    private static boolean containsPoint(VirtualGuiElement el, float mx, float my) {
        // Use render coordinates (getRenderX/Y/Width/Height) which apply the
        // parent's content scale — same coordinate system as the visual rendering.
        // Using getAbsoluteX/Y + getEffectiveWidth/Height does NOT apply content
        // scale, causing hit-test mismatch when a scaledContent panel is resized.
        int ix = el.getRenderX(current);
        int iy = el.getRenderY(current);
        int iw = el.getRenderWidth(current);
        int ih = el.getRenderHeight(current);
        return mx >= ix && mx < ix + iw && my >= iy && my < iy + ih;
    }

    /** Recursively finds the frontmost descendant of `el` under (mx,my); falls back to `el` itself. */
    private static VirtualGuiElement descendTopmost(VirtualGuiElement el, float mx, float my) {
        java.util.List<VirtualGuiElement> kids = el.getChildren();
        for (int i = kids.size() - 1; i >= 0; i--) {
            VirtualGuiElement child = kids.get(i);
            if (child == null || !child.isVisible()) continue;
            if (!containsPoint(child, mx, my)) continue;
            return descendTopmost(child, mx, my);
        }
        return el;
    }

    /** Finds the frontmost VISIBLE element (root or nested) under (mx,my), or null if nothing is there. */
    public static VirtualGuiElement getTopmostElementAt(float mx, float my) {
        if (current == null) return null;
        for (int i = current.elementOrder.size() - 1; i >= 0; i--) {
            VirtualGuiElement el = current.elements.get(current.elementOrder.get(i));
            if (el == null || el.getParentId() != null || !el.isVisible()) continue;
            if (!containsPoint(el, mx, my)) continue;
            return descendTopmost(el, mx, my);
        }
        return null;
    }

    /**
     * True if `el` IS the topmost-hit element, or an ANCESTOR of it. A panel's
     * own resize edges / drag zone are still grabbable even when one of its
     * own children happens to be the frontmost thing at that pixel — only an
     * UNRELATED element (from a different branch, rendered on top) blocks it.
     */
    public static boolean ownsPoint(VirtualGuiElement el, VirtualGuiElement topmost) {
        if (el == null || topmost == null) return false;
        VirtualGuiElement cur = topmost;
        while (cur != null) {
            if (cur.getId().equals(el.getId())) return true;
            String pid = cur.getParentId();
            cur = pid != null ? current.elements.get(pid) : null;
        }
        return false;
    }

    private static void updateSliderFromMouse(VirtualGuiElement slider, float mx, float my) {
        float pct;
        float ew = slider.getEffectiveWidth(current);
        float eh = slider.getEffectiveHeight(current);
        if ("vertical".equals(slider.getSliderDirection())) {
            float ay = slider.getAbsoluteY(current);
            float relY = my - ay;
            pct = eh > 0 ? relY / eh : 0f;
        } else {
            float ax = slider.getAbsoluteX(current);
            float relX = mx - ax;
            pct = ew > 0 ? relX / ew : 0f;
        }
        pct = Math.max(0f, Math.min(1f, pct));

        float min = slider.getSliderMin();
        float max = slider.getSliderMax();
        float value = min + pct * (max - min);

        if ("step".equals(slider.getSliderType())) {
            float stepSize = slider.getStepSize();
            if (stepSize <= 0) stepSize = (max - min) <= 10 ? 0.1f : 1f;
            // Snap value to nearest step
            value = min + Math.round((value - min) / stepSize) * stepSize;
            // Clamp to range
            value = Math.max(min, Math.min(max, value));
        }

        float oldVal = slider.getSliderValue();
        slider.setSliderValue(value);

        if (slider.getActionId() != null && !slider.getActionId().isEmpty() && value != oldVal) {
            setActionReason("slider_changed");
            setActionElementId(slider.getId());
            callAction(slider.getActionId());
        }
    }

    public static void handleDrag(float mx, float my) {
        // Resize takes priority over everything
        if (resizingElementId != null && current != null) {
            VirtualGuiElement el = current.elements.get(resizingElementId);
            if (el == null) { resizingElementId = null; }
            else {
                float dx = mx - resizeStartMouseX;
                float dy = my - resizeStartMouseY;

                // Effective clamp region = screen ∩ the element's own explicit
                // bounds (if any). Using ONLY the raw screen here was the bug —
                // a panel inside a setBoundaries() scope could be resized clean
                // out of its container. Same region drag already respects.
                float clampMinX = 0f;
                float clampMaxX = (screenWidth > 0) ? screenWidth : Float.MAX_VALUE;
                float clampMinY = 0f;
                float clampMaxY = (screenHeight > 0) ? screenHeight : Float.MAX_VALUE;
                if (el.hasBounds()) {
                    clampMinX = Math.max(clampMinX, el.getBoundsX1());
                    clampMaxX = Math.min(clampMaxX, el.getBoundsX2());
                    clampMinY = Math.max(clampMinY, el.getBoundsY1());
                    clampMaxY = Math.min(clampMaxY, el.getBoundsY2());
                }
                // ResizeBounds: tighter than screen/boundary bounds —
                // the panel's edges cannot extend beyond this box.
                if (el.hasResizeBounds()) {
                    clampMinX = Math.max(clampMinX, el.getResizeBoundsX1());
                    clampMaxX = Math.min(clampMaxX, el.getResizeBoundsX2());
                    clampMinY = Math.max(clampMinY, el.getResizeBoundsY1());
                    clampMaxY = Math.min(clampMaxY, el.getResizeBoundsY2());
                }

                // Fixed-edge approach: the edge being NOT dragged stays fixed.
                // The dragged edge is clamped to the region above + min size.
                // This prevents "resizes wrong side" at boundaries.

                float newAbsX = resizeStartAbsX;
                float newAbsY = resizeStartAbsY;
                float newW = resizeStartW;
                float newH = resizeStartH;

                // Element's min/max resize constraints (from gear mutator, -1 = default)
                float minW = el.getMinResizeX() > 0 ? el.getMinResizeX() : MIN_RESIZE_SIZE;
                float minH = el.getMinResizeY() > 0 ? el.getMinResizeY() : MIN_RESIZE_SIZE;
                float maxW = el.getMaxResizeX() > 0 ? el.getMaxResizeX() : Float.MAX_VALUE;
                float maxH = el.getMaxResizeY() > 0 ? el.getMaxResizeY() : Float.MAX_VALUE;

                // Solid render-call boxes (renderRect/renderTexture/renderItem with
                // collision=true) act as a fixed collision boundary: the panel can
                // never be resized past them. Tracked children with collision=true
                // are handled separately (pushed, not blocking).
                float[] solidBounds = el.getRenderBoxUnionBounds(current);

                // Aspect-ratio (proportional) corner resize for render items.
                // When maintainAspectRatio is true and both axes are active,
                // derive width from BOTH horizontal and vertical mouse movement,
                // then pick whichever produces the larger size change — so the
                // element responds to dragging the corner in ANY direction (not
                // just horizontally). The opposite corner stays fixed.
                if (el.isMaintainAspectRatio() &&
                    (resizeDirection & (VirtualGuiElement.RESIZE_LEFT | VirtualGuiElement.RESIZE_RIGHT)) != 0 &&
                    (resizeDirection & (VirtualGuiElement.RESIZE_TOP | VirtualGuiElement.RESIZE_BOTTOM)) != 0) {

                    float aspect = resizeStartW / resizeStartH;

                    // Compute new width from HORIZONTAL drag (existing approach)
                    float newW_fromDx;
                    float newAbsX_fromDx;
                    if ((resizeDirection & VirtualGuiElement.RESIZE_LEFT) != 0) {
                        float rightEdge = resizeStartAbsX + resizeStartW;
                        float newLeft = resizeStartAbsX + dx;
                        newLeft = Math.max(clampMinX, Math.min(newLeft, rightEdge - minW));
                        newW_fromDx = Math.min(rightEdge - newLeft, maxW);
                        newAbsX_fromDx = newLeft;
                    } else {
                        float leftEdge = resizeStartAbsX;
                        float newRight = resizeStartAbsX + resizeStartW + dx;
                        newRight = Math.max(leftEdge + minW, Math.min(newRight, clampMaxX));
                        newW_fromDx = Math.min(newRight - leftEdge, maxW);
                        newAbsX_fromDx = leftEdge;
                    }

                    // Compute new width from VERTICAL drag (via aspect ratio)
                    // height change from dy, then convert to width via aspect.
                    float newW_fromDy;
                    float newAbsX_fromDy;
                    if ((resizeDirection & VirtualGuiElement.RESIZE_TOP) != 0) {
                        // Bottom edge fixed; top moves with dy (up = negative dy = taller)
                        float bottomEdge = resizeStartAbsY + resizeStartH;
                        float newTop = resizeStartAbsY + dy;
                        newTop = Math.max(clampMinY, Math.min(newTop, bottomEdge - minH));
                        float hFromDy = bottomEdge - newTop;
                        hFromDy = Math.max(minH, Math.min(hFromDy, maxH));
                        newW_fromDy = hFromDy * aspect;
                        newW_fromDy = Math.max(minW, Math.min(newW_fromDy, maxW));
                    } else {
                        // Top edge fixed; bottom moves with dy (down = positive dy = taller)
                        float topEdge = resizeStartAbsY;
                        float newBottom = resizeStartAbsY + resizeStartH + dy;
                        newBottom = Math.max(topEdge + minH, Math.min(newBottom, clampMaxY));
                        float hFromDy = newBottom - topEdge;
                        hFromDy = Math.max(minH, Math.min(hFromDy, maxH));
                        newW_fromDy = hFromDy * aspect;
                        newW_fromDy = Math.max(minW, Math.min(newW_fromDy, maxW));
                    }
                    // Compute the X position for the dy-driven width
                    if ((resizeDirection & VirtualGuiElement.RESIZE_LEFT) != 0) {
                        newAbsX_fromDy = (resizeStartAbsX + resizeStartW) - newW_fromDy;
                    } else {
                        newAbsX_fromDy = resizeStartAbsX;
                    }

                    // Use whichever produces the larger absolute change from the
                    // starting width — this makes the corner responsive to both
                    // horizontal and vertical mouse movement. When the user drags
                    // mostly vertically (e.g. straight up toward a sibling above),
                    // the dy-driven width is used and the element actually grows
                    // instead of staying at its original size.
                    float changeDx = Math.abs(newW_fromDx - resizeStartW);
                    float changeDy = Math.abs(newW_fromDy - resizeStartW);
                    if (changeDy > changeDx) {
                        newW = newW_fromDy;
                        newAbsX = newAbsX_fromDy;
                    } else {
                        newW = newW_fromDx;
                        newAbsX = newAbsX_fromDx;
                    }

                    // Derive height from the chosen width via aspect ratio
                    newH = newW / aspect;
                    newH = Math.max(minH, Math.min(newH, maxH));
                    // If height was clamped, recompute width to maintain aspect
                    if (Math.abs(newH * aspect - newW) > 0.5f) {
                        newW = newH * aspect;
                        if ((resizeDirection & VirtualGuiElement.RESIZE_LEFT) != 0) {
                            newAbsX = (resizeStartAbsX + resizeStartW) - newW;
                        }
                    }

                    // Position the fixed corner
                    if ((resizeDirection & VirtualGuiElement.RESIZE_TOP) != 0) {
                        // Bottom edge fixed
                        newAbsY = (resizeStartAbsY + resizeStartH) - newH;
                    } else {
                        // Top edge fixed
                        newAbsY = resizeStartAbsY;
                    }
                    newAbsY = Math.max(clampMinY, Math.min(newAbsY, clampMaxY));

                } else {
                // Horizontal resize
                if ((resizeDirection & VirtualGuiElement.RESIZE_LEFT) != 0) {
                    // Right edge is fixed
                    float rightEdge = resizeStartAbsX + resizeStartW;
                    float newLeft = resizeStartAbsX + dx;
                    // Clamp: left edge can't go past region min, can't overlap right edge (min width)
                    newLeft = Math.max(clampMinX, Math.min(newLeft, rightEdge - minW));
                    if (solidBounds != null) {
                        // Left edge can't move past (right of) the leftmost solid box —
                        // that would clip it. Re-clamp to the region min afterward.
                        newLeft = Math.max(clampMinX, Math.min(newLeft, solidBounds[0]));
                    }
                    newAbsX = newLeft;
                    newW = Math.min(rightEdge - newLeft, maxW);
                } else if ((resizeDirection & VirtualGuiElement.RESIZE_RIGHT) != 0) {
                    // Left edge is fixed
                    float leftEdge = resizeStartAbsX;
                    float newRight = resizeStartAbsX + resizeStartW + dx;
                    // Clamp: right edge can't go past region max, can't overlap left (min width)
                    newRight = Math.max(leftEdge + minW, Math.min(newRight, clampMaxX));
                    if (solidBounds != null) {
                        // Right edge can't move past (left of) the rightmost solid box.
                        newRight = Math.min(clampMaxX, Math.max(newRight, solidBounds[2]));
                    }
                    newAbsX = leftEdge;
                    newW = Math.min(newRight - leftEdge, maxW);
                }

                // Vertical resize
                if ((resizeDirection & VirtualGuiElement.RESIZE_TOP) != 0) {
                    // Bottom edge is fixed
                    float bottomEdge = resizeStartAbsY + resizeStartH;
                    float newTop = resizeStartAbsY + dy;
                    // Clamp: top edge can't go past region min, can't overlap bottom edge (min height)
                    newTop = Math.max(clampMinY, Math.min(newTop, bottomEdge - minH));
                    if (solidBounds != null) {
                        // Top edge can't move past (below) the topmost solid box.
                        newTop = Math.max(clampMinY, Math.min(newTop, solidBounds[1]));
                    }
                    newAbsY = newTop;
                    newH = Math.min(bottomEdge - newTop, maxH);
                } else if ((resizeDirection & VirtualGuiElement.RESIZE_BOTTOM) != 0) {
                    // Top edge is fixed
                    float topEdge = resizeStartAbsY;
                    float newBottom = resizeStartAbsY + resizeStartH + dy;
                    // Clamp: bottom edge can't go past region max, can't overlap top (min height)
                    newBottom = Math.max(topEdge + minH, Math.min(newBottom, clampMaxY));
                    if (solidBounds != null) {
                        // Bottom edge can't move past (above) the bottommost solid box.
                        newBottom = Math.min(clampMaxY, Math.max(newBottom, solidBounds[3]));
                    }
                    newAbsY = topEdge;
                    newH = Math.min(newBottom - topEdge, maxH);
                }

                } // end normal (non-aspect) resize

                // Sibling collision: if this element has collision=true,
                // prevent resizing it on top of other collision=true siblings.
                // Skip if the parent has a grid — grid takes priority.
                boolean resizeParentHasGrid = false;
                if (el.getParentId() != null) {
                    VirtualGuiElement rp = current.elements.get(el.getParentId());
                    resizeParentHasGrid = rp != null && rp.hasGrid();
                }
                if (el.hasCollision() && !resizeParentHasGrid) {
                    float elLeft = newAbsX;
                    float elRight = newAbsX + newW;
                    float elTop = newAbsY;
                    float elBottom = newAbsY + newH;
                    String elParentId = el.getParentId();

                    // Track which dimensions were actually clamped by collision
                    // so the aspect-ratio recompute knows which one to keep.
                    boolean widthClampedByCollision = false;
                    boolean heightClampedByCollision = false;

                    for (String sibId : current.getAllElementIds()) {
                        if (sibId.equals(el.getId())) continue;
                        VirtualGuiElement sib = current.elements.get(sibId);
                        if (sib == null || !sib.hasCollision() || !sib.isVisible()) continue;
                        if (!java.util.Objects.equals(sib.getParentId(), elParentId)) continue;

                        float sibLeft = sib.getAbsoluteX(current);
                        float sibTop = sib.getAbsoluteY(current);
                        float sibRight = sibLeft + sib.getEffectiveWidth(current);
                        float sibBottom = sibTop + sib.getEffectiveHeight(current);

                        // Check horizontal edges (LEFT / RIGHT)
                        // Only collide if the sibling is actually in the direction
                        // being resized (not just overlapping in that axis).
                        if ((resizeDirection & VirtualGuiElement.RESIZE_LEFT) != 0) {
                            // Sibling must be to the LEFT of where we started
                            if (sibRight <= resizeStartAbsX &&
                                sibBottom > elTop && sibTop < elBottom) {
                                if (newAbsX < sibRight) {
                                    newAbsX = sibRight;
                                    newW = (resizeStartAbsX + resizeStartW) - newAbsX;
                                    newW = Math.max(minW, newW);
                                    widthClampedByCollision = true;
                                }
                            }
                        }
                        if ((resizeDirection & VirtualGuiElement.RESIZE_RIGHT) != 0) {
                            // Sibling must be to the RIGHT of where we started
                            float fixedLeft = resizeStartAbsX;
                            if (sibLeft >= resizeStartAbsX + resizeStartW &&
                                sibBottom > elTop && sibTop < elBottom) {
                                if (newAbsX + newW > sibLeft) {
                                    newW = sibLeft - fixedLeft;
                                    newW = Math.max(minW, newW);
                                    widthClampedByCollision = true;
                                }
                            }
                        }
                        // Check vertical edges (TOP / BOTTOM)
                        if ((resizeDirection & VirtualGuiElement.RESIZE_TOP) != 0) {
                            // Sibling must be ABOVE where we started
                            if (sibBottom <= resizeStartAbsY &&
                                sibRight > newAbsX && sibLeft < newAbsX + newW) {
                                if (newAbsY < sibBottom) {
                                    newAbsY = sibBottom;
                                    newH = (resizeStartAbsY + resizeStartH) - newAbsY;
                                    newH = Math.max(minH, newH);
                                    heightClampedByCollision = true;
                                }
                            }
                        }
                        if ((resizeDirection & VirtualGuiElement.RESIZE_BOTTOM) != 0) {
                            // Sibling must be BELOW where we started
                            float fixedTop = resizeStartAbsY;
                            if (sibTop >= resizeStartAbsY + resizeStartH &&
                                sibRight > newAbsX && sibLeft < newAbsX + newW) {
                                if (newAbsY + newH > sibTop) {
                                    newH = sibTop - fixedTop;
                                    newH = Math.max(minH, newH);
                                    heightClampedByCollision = true;
                                }
                            }
                        }
                    }

                    // Also check solid render boxes on the parent (non-tracked solid rects/items)
                    if (elParentId != null) {
                        VirtualGuiElement parent = current.elements.get(elParentId);
                        if (parent != null) {
                            float baseX = parent.getAbsoluteX(current) - parent.getResizeDeltaX();
                            float baseY = parent.getAbsoluteY(current) - parent.getResizeDeltaY();
                            for (float[] box : parent.getSolidRenderBoxes()) {
                                float sibLeft = baseX + box[0];
                                float sibTop = baseY + box[1];
                                float sibRight = baseX + box[2];
                                float sibBottom = baseY + box[3];

                                if ((resizeDirection & VirtualGuiElement.RESIZE_LEFT) != 0) {
                                    if (sibRight <= resizeStartAbsX &&
                                        sibBottom > newAbsY && sibTop < newAbsY + newH) {
                                        if (newAbsX < sibRight) {
                                            newAbsX = sibRight;
                                            newW = (resizeStartAbsX + resizeStartW) - newAbsX;
                                            newW = Math.max(minW, newW);
                                            widthClampedByCollision = true;
                                        }
                                    }
                                }
                                if ((resizeDirection & VirtualGuiElement.RESIZE_RIGHT) != 0) {
                                    float fixedLeft = resizeStartAbsX;
                                    if (sibLeft >= resizeStartAbsX + resizeStartW &&
                                        sibBottom > newAbsY && sibTop < newAbsY + newH) {
                                        if (newAbsX + newW > sibLeft) {
                                            newW = sibLeft - fixedLeft;
                                            newW = Math.max(minW, newW);
                                            widthClampedByCollision = true;
                                        }
                                    }
                                }
                                if ((resizeDirection & VirtualGuiElement.RESIZE_TOP) != 0) {
                                    if (sibBottom <= resizeStartAbsY &&
                                        sibRight > newAbsX && sibLeft < newAbsX + newW) {
                                        if (newAbsY < sibBottom) {
                                            newAbsY = sibBottom;
                                            newH = (resizeStartAbsY + resizeStartH) - newAbsY;
                                            newH = Math.max(minH, newH);
                                            heightClampedByCollision = true;
                                        }
                                    }
                                }
                                if ((resizeDirection & VirtualGuiElement.RESIZE_BOTTOM) != 0) {
                                    float fixedTop = resizeStartAbsY;
                                    if (sibTop >= resizeStartAbsY + resizeStartH &&
                                        sibRight > newAbsX && sibLeft < newAbsX + newW) {
                                        if (newAbsY + newH > sibTop) {
                                            newH = sibTop - fixedTop;
                                            newH = Math.max(minH, newH);
                                            heightClampedByCollision = true;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Re-apply min size after collision clamping
                    if (newW < minW) newW = minW;
                    if (newH < minH) newH = minH;

                    // For aspect-ratio elements, recompute the dependent dimension
                    // after collision clamping so the aspect ratio is preserved.
                    // Use the clamped-dimension tracking to know which dimension
                    // was actually constrained by collision (not just which
                    // resize direction flags happen to be active for a corner
                    // resize — both are active but only one may have collided).
                    if (el.isMaintainAspectRatio() && resizeStartH > 0) {
                        float aspect = resizeStartW / resizeStartH;
                        if (widthClampedByCollision && !heightClampedByCollision) {
                            // Width was clamped — recompute height from width
                            newH = newW / aspect;
                            newH = Math.max(minH, Math.min(newH, maxH));
                            if ((resizeDirection & VirtualGuiElement.RESIZE_TOP) != 0) {
                                newAbsY = (resizeStartAbsY + resizeStartH) - newH;
                            }
                        } else if (heightClampedByCollision && !widthClampedByCollision) {
                            // Height was clamped — recompute width from height
                            newW = newH * aspect;
                            newW = Math.max(minW, Math.min(newW, maxW));
                            if ((resizeDirection & VirtualGuiElement.RESIZE_LEFT) != 0) {
                                newAbsX = (resizeStartAbsX + resizeStartW) - newW;
                            }
                        } else if (widthClampedByCollision && heightClampedByCollision) {
                            // Both clamped — keep the smaller (more restrictive) one
                            // and derive the other from it.
                            if (newW <= newH * aspect) {
                                newH = newW / aspect;
                                newH = Math.max(minH, Math.min(newH, maxH));
                                if ((resizeDirection & VirtualGuiElement.RESIZE_TOP) != 0) {
                                    newAbsY = (resizeStartAbsY + resizeStartH) - newH;
                                }
                            } else {
                                newW = newH * aspect;
                                newW = Math.max(minW, Math.min(newW, maxW));
                                if ((resizeDirection & VirtualGuiElement.RESIZE_LEFT) != 0) {
                                    newAbsX = (resizeStartAbsX + resizeStartW) - newW;
                                }
                            }
                        }
                    }
                }

                // Compute minimum size from collision=true children: the panel can't
                // be resized smaller than what fits all collision children (they get
                // pushed, but when there's no space, the resize must stop).
                if ("panel".equals(el.getType()) || "scroll_view".equals(el.getType())) {
                    float minCollisionW = computeMinCollisionWidth(el, current);
                    float minCollisionH = computeMinCollisionHeight(el, current);
                    if (newW < minCollisionW) {
                        newW = minCollisionW;
                        if ((resizeDirection & VirtualGuiElement.RESIZE_LEFT) != 0) {
                            newAbsX = (resizeStartAbsX + resizeStartW) - newW;
                        }
                    }
                    if (newH < minCollisionH) {
                        newH = minCollisionH;
                        if ((resizeDirection & VirtualGuiElement.RESIZE_TOP) != 0) {
                            newAbsY = (resizeStartAbsY + resizeStartH) - newH;
                        }
                    }
                }

                // ── Final aspect-ratio enforcement ──────────────────────────────
                // After all clamping (boundaries, collision children, sibling
                // collision) may have changed newW or newH independently, re-check
                // the aspect ratio. If it's been broken, pick the smaller
                // dimension and derive the other from it, then reposition the
                // fixed corner so the opposite corner stays put.
                if (el.isMaintainAspectRatio() && resizeStartH > 0) {
                    float aspect = resizeStartW / resizeStartH;
                    float currentRatio = newW / Math.max(newH, 0.001f);
                    if (Math.abs(currentRatio - aspect) > 0.01f) {
                        // Pick the smaller dimension to ensure we stay within
                        // whatever boundary/clamp just restricted us.
                        if (newW / aspect <= newH) {
                            // Width is the limiting factor — shrink height
                            newH = newW / aspect;
                            newH = Math.max(minH, Math.min(newH, maxH));
                        } else {
                            // Height is the limiting factor — shrink width
                            newW = newH * aspect;
                            newW = Math.max(minW, Math.min(newW, maxW));
                        }
                        // Reposition to keep the fixed corner fixed
                        if ((resizeDirection & VirtualGuiElement.RESIZE_LEFT) != 0) {
                            newAbsX = (resizeStartAbsX + resizeStartW) - newW;
                        }
                        if ((resizeDirection & VirtualGuiElement.RESIZE_TOP) != 0) {
                            newAbsY = (resizeStartAbsY + resizeStartH) - newH;
                        }
                        // Re-clamp position to boundary region
                        newAbsX = Math.max(clampMinX, Math.min(newAbsX, clampMaxX - newW));
                        newAbsY = Math.max(clampMinY, Math.min(newAbsY, clampMaxY - newH));
                    }
                }

                // Save old absolute position BEFORE updating (for delta calculation).
                float prevAbsX = el.getAbsoluteX(current);
                float prevAbsY = el.getAbsoluteY(current);

                // Convert absolute to local (relative to parent). Position is not
                // scaled, so: localX = absX - parent.absX (no division by scale).
                // For shouldStick=true:  localX = absX - parent.absX + parent.resizeDelta
                // For shouldStick=false: localX = absX - parent.absX
                float localX = newAbsX;
                float localY = newAbsY;
                String pid = el.getParentId();
                if (pid != null) {
                    VirtualGuiElement parent = current.elements.get(pid);
                    if (parent != null) {
                        if (el.shouldStick()) {
                            localX = newAbsX - parent.getAbsoluteX(current) + parent.getResizeDeltaX();
                            localY = newAbsY - parent.getAbsoluteY(current) + parent.getResizeDeltaY();
                        } else {
                            localX = newAbsX - parent.getAbsoluteX(current);
                            localY = newAbsY - parent.getAbsoluteY(current);
                        }
                    }
                }

                el.setX(localX);
                el.setY(localY);
                el.setWidth(newW);
                el.setHeight(newH);

                // Accumulate resize delta: how much the panel's absolute position
                // changed this frame. Children and render calls subtract this to
                // stay pinned at their screen position during left/top resize.
                float currAbsX = el.getAbsoluteX(current);
                float currAbsY = el.getAbsoluteY(current);
                el.accumulateResizeDelta(currAbsX - prevAbsX, currAbsY - prevAbsY);

                // Push non-stick (stick=false) children inward to stay inside the
                // new panel bounds. stick=true children stay at their screen position
                // (compensated by resizeDelta) and are scissored if outside.
                // stick=false children stay at their SCREEN position (compensated
                // for the parent's position change this frame), then get clamped
                // to the panel's new local bounds [0, newW] × [0, newH].
                // Without the position compensation, children would shift when the
                // parent's top/left edge is resized (since the parent's origin
                // moves), even though right/bottom resize works fine (no origin
                // movement).
                if ("panel".equals(el.getType()) || "scroll_view".equals(el.getType())) {
                    float frameDeltaX = currAbsX - prevAbsX;
                    float frameDeltaY = currAbsY - prevAbsY;
                    // Skip child pushing for scaled-content panels — children stay
                    // in design space and are scaled at render time, so they
                    // should NOT be pushed inward during live resize.
                    if (!el.getScaledContent() && !el.hasGrid()) {
                    for (VirtualGuiElement child : el.getChildren()) {
                        if (child.shouldStick() || !child.isVisible()) continue;
                        // Compensate for parent's position change so the child
                        // stays at its screen position. Then clamp to new bounds.
                        child.setX(child.getX() - frameDeltaX);
                        child.setY(child.getY() - frameDeltaY);
                        float childW = child.getEffectiveWidth(current);
                        float childH = child.getEffectiveHeight(current);
                        float maxChildX = Math.max(0, newW - childW);
                        float maxChildY = Math.max(0, newH - childH);
                        if (child.getX() > maxChildX) child.setX(maxChildX);
                        if (child.getY() > maxChildY) child.setY(maxChildY);
                        if (child.getX() < 0) child.setX(0);
                        if (child.getY() < 0) child.setY(0);
                    }
                    }

                    // Push collision=true children inward with chain pushing.
                    // Skip when the parent has a grid or scaled content — children
                    // stay in design space and are scaled at render time.
                    if (!el.hasGrid() && !el.getScaledContent()) {
                        pushCollisionChildren(el, current, newW, newH);
                    }

                    // Re-snap grid children after resize to maintain relative position.
                    // Items stay in their grid cells; if the panel shrinks, items
                    // get pushed by the grid walls (like Windows folder list view).
                    // updateGridScale() recomputes the grid area based on the panel's
                    // new size BEFORE re-snapping, so items follow the grid in real-time.
                    if (el.hasGrid()) {
                        el.reSnapAllChildren(current);
                    }
                }

                // If the resized element is NOT a panel but its parent has a grid,
                // re-snap ALL siblings — the cell size may have changed (auto-computed
                // from biggest child), so other items need to re-center.
                if (!("panel".equals(el.getType()) || "scroll_view".equals(el.getType()))) {
                    if (pid != null) {
                        VirtualGuiElement parent = current.elements.get(pid);
                        if (parent != null && parent.hasGrid()) {
                            parent.reSnapAllChildren(current);
                        }
                    }
                }

                // Final boundary enforcement using render coordinates — catches
                // content-scale mismatch where absolute coords are clamped but
                // the visual render area escapes the boundary.
                enforceRenderBounds(el);
                return;
            }
        }

        // Check if pending click should convert to drag (moved beyond threshold)
        if (pendingClickElementId != null && current != null) {
            float dx = mx - pendingClickStartX;
            float dy = my - pendingClickStartY;
            if (dx * dx + dy * dy > DRAG_THRESHOLD * DRAG_THRESHOLD) {
                VirtualGuiElement el = current.elements.get(pendingClickElementId);
                if (el != null) {
                    bringToFront(el.getId());
                    draggingElementId = el.getId();
                    preDragParentId = el.getParentId();
                    preDragAbsX = el.getAbsoluteX(current);
                    preDragAbsY = el.getAbsoluteY(current);
                    dragOffsetX = pendingClickStartX - el.getAbsoluteX(current);
                    dragOffsetY = pendingClickStartY - el.getAbsoluteY(current);
                }
                pendingClickElementId = null;
            }
        }

        // Slider drag takes priority
        if (draggingSliderId != null && current != null) {
            VirtualGuiElement slider = current.elements.get(draggingSliderId);
            if (slider != null) {
                updateSliderFromMouse(slider, mx, my);
                return;
            }
            draggingSliderId = null;
        }

        if (draggingElementId == null || !isAnyOpen() || current == null) return;
        VirtualGuiElement el = current.elements.get(draggingElementId);
        if (el == null) { draggingElementId = null; return; }

        float newAbsX, newAbsY;

        // ── Step 1: Compute new absolute position ─────────────────────────────
        // dragOut: clamp to screen (can leave parent). No dragOut: clamp to parent.
        if (el.canDragOut()) {
            newAbsX = mx - dragOffsetX;
            newAbsY = my - dragOffsetY;
            float sw = VirtualGui.getScreenWidth();
            float sh = VirtualGui.getScreenHeight();
            float ew = el.getEffectiveWidth(current);
            float eh = el.getEffectiveHeight(current);
            // Screen bounds
            if (sw > 0) {
                float screenMax = sw - ew;
                if (screenMax < 0) screenMax = 0;
                newAbsX = Math.max(0, Math.min(newAbsX, screenMax));
            }
            if (sh > 0) {
                float screenMax = sh - eh;
                if (screenMax < 0) screenMax = 0;
                newAbsY = Math.max(0, Math.min(newAbsY, screenMax));
            }
            // Explicit bounds (setBoundaries) — these ALWAYS apply, even with
            // dragOut. dragOut lets an element leave its PARENT, but it must
            // never leave the explicit boundary region (e.g. a "tablet" or
            // "monitor" frame). Without this, elements visually escape the
            // boundary during drag and only snap back on release.
            if (el.hasBounds()) {
                float minBx = el.getBoundsX1();
                float maxBx = el.getBoundsX2() - ew;
                if (maxBx < minBx) maxBx = minBx;
                newAbsX = Math.max(minBx, Math.min(newAbsX, maxBx));
                float minBy = el.getBoundsY1();
                float maxBy = el.getBoundsY2() - eh;
                if (maxBy < minBy) maxBy = minBy;
                newAbsY = Math.max(minBy, Math.min(newAbsY, maxBy));
            }
        } else {
            newAbsX = el.clampAbsX(current, mx - dragOffsetX);
            newAbsY = el.clampAbsY(current, my - dragOffsetY);
        }

        // ── Step 2: Reparenting (BEFORE sibling collision) ───────────────────
        // dragIn: reparenting is DEFERRED to handleRelease. During drag the
        //   element just moves freely — no reparenting, no scissor changes.
        //   This prevents the "looks like it's inside" visual confusion when
        //   the element is over a panel but not actually nested.
        // dragOut-only (no dragIn): reparent to root when center leaves parent
        //   (still happens during drag so the element isn't scissored to the
        //   old parent when dragged outside).
        boolean reparentedThisFrame = false;

        if (el.canDragOut()) {
            // dragOut: when center leaves parent, reparent to root.
            // Works for both dragOut-only AND dragIn+dragOut elements.
            // For dragIn+dragOut: the element is unparented during drag,
            // and on release the dragIn logic tries to find a new panel.
            String currentParentId = el.getParentId();
            if (currentParentId != null) {
                float ew = el.getEffectiveWidth(current);
                float eh = el.getEffectiveHeight(current);
                float centerX = newAbsX + ew / 2f;
                float centerY = newAbsY + eh / 2f;
                VirtualGuiElement parent = current.elements.get(currentParentId);
                if (parent != null) {
                    float px = parent.getAbsoluteX(current);
                    float py = parent.getAbsoluteY(current);
                    float pw = parent.getEffectiveWidth(current);
                    float ph = parent.getEffectiveHeight(current);
                    boolean outside = centerX < px || centerX > px + pw
                            || centerY < py || centerY > py + ph;
                    if (outside) {
                        setActionReason("drag_out");
                        setActionElementId(el.getId());
                        callAction(el.getActionId());
                        callAction(el.getButtonActionId());
                        reparentElement(el, null, current);
                        reparentedThisFrame = true;
                    }
                }
            }
        }

        // ── Step 3: Sibling collision — PUSH instead of STOP ──────────────────
        // Collision=true elements push collision=true siblings out of the way
        // instead of just stopping. Chain-push: if the pushed sibling overlaps
        // another collision sibling, that one gets pushed too. Siblings are
        // clamped to the parent panel bounds — they can never be pushed outside.
        // If the chain is fully compressed (all siblings at the parent edge),
        // the dragged element stops.
        // Skipped on the reparent frame, for panels (panels are containers),
        // and for children of a grid panel — the grid takes priority over
        // collision: items stay snapped to their grid cell regardless.
        boolean parentHasGrid = false;
        if (el.getParentId() != null) {
            VirtualGuiElement gridParent = current.elements.get(el.getParentId());
            parentHasGrid = gridParent != null && gridParent.hasGrid();
        }
        if (el.hasCollision() && !reparentedThisFrame && !"panel".equals(el.getType()) && !parentHasGrid) {
            float oldAbsX = el.getAbsoluteX(current);
            float oldAbsY = el.getAbsoluteY(current);
            float w = el.getEffectiveWidth(current);
            float h = el.getEffectiveHeight(current);

            // X-axis first (allows sliding along a sibling's vertical edge)
            if (newAbsX != oldAbsX) {
                newAbsX = pushCollisionSiblingsX(el, oldAbsX, newAbsX, oldAbsY, w, h, current);
            }
            // Y-axis (with updated X position)
            if (newAbsY != oldAbsY) {
                newAbsY = pushCollisionSiblingsY(el, newAbsX, oldAbsY, newAbsY, w, h, current);
            }

            // Solid render boxes (non-tracked rects/items with collision) can't
            // be pushed — they still block the dragged element.
            if (overlapsSolidRenderBox(el, newAbsX, oldAbsY, w, h, current)) {
                newAbsX = oldAbsX;
            }
            if (overlapsSolidRenderBox(el, newAbsX, newAbsY, w, h, current)) {
                newAbsY = oldAbsY;
            }
        }

        // ── Step 4: Convert absolute → local and apply ───────────────────────
        // For shouldStick=true:  localX = absX - parent.absX + parent.resizeDelta
        // For shouldStick=false: localX = absX - parent.absX
        // reparentElement already did this conversion if we reparented this
        // frame, but Step 4 overwrites with the LATEST mouse position — which
        // is correct (reparentElement used the old position, Step 4 uses the
        // new drag position).
        String parentId = el.getParentId();
        if (parentId != null) {
            VirtualGuiElement parent = current.elements.get(parentId);
            if (parent != null) {
                // When the parent is a scroll view, add back the scroll shift.
                // getAbsoluteY for children subtracts scrollShift, so to
                // convert screen→local we must add it back. Otherwise items
                // dragged in a scrolled view snap to the wrong grid cell.
                float scrollShiftY = parent.isScrollView() ? parent.getEffectiveScrollShift() : 0f;
                if (el.shouldStick()) {
                    newAbsX = newAbsX - parent.getAbsoluteX(current) + parent.getResizeDeltaX();
                    newAbsY = newAbsY - parent.getAbsoluteY(current) + parent.getResizeDeltaY() + scrollShiftY;
                } else {
                    newAbsX = newAbsX - parent.getAbsoluteX(current);
                    newAbsY = newAbsY - parent.getAbsoluteY(current) + scrollShiftY;
                }
            }
        }

        el.setX(newAbsX);
        el.setY(newAbsY);

        // Final boundary enforcement using render coordinates.
        enforceRenderBounds(el);
    }

    /**
     * Finds the topmost (innermost) panel at the given screen coordinates,
     * excluding the specified element and its descendants. Returns null for
     * "root level" (not inside any panel).
     */
    private static String findPanelAt(VirtualGui gui, float screenX, float screenY, String excludeId) {
        String result = null; // null = root level
        List<String> order = gui.getElementOrder();
        for (int i = order.size() - 1; i >= 0; i--) {
            String id = order.get(i);
            if (id.equals(excludeId)) continue;
            VirtualGuiElement el = gui.getElement(id);
            if (el == null || !el.isVisible()) continue;
            // Only panels and scroll views can be drop targets
            if (!"panel".equals(el.getType()) && !"scroll_view".equals(el.getType())) continue;
            // Skip if this element is a descendant of the dragged element
            // (prevents dropping a panel into itself or its children)
            if (isDescendantOf(el, excludeId, gui)) continue;
            float ex = el.getAbsoluteX(gui);
            float ey = el.getAbsoluteY(gui);
            float ew = el.getEffectiveWidth(gui);
            float eh = el.getEffectiveHeight(gui);
            if (screenX >= ex && screenX <= ex + ew && screenY >= ey && screenY <= ey + eh) {
                result = id;
                break; // Topmost first (iterating in reverse order)
            }
        }
        return result;
    }

    /**
     * Finds the topmost (innermost) panel that FULLY CONTAINS the given box
     * (all four edges inside the panel). Returns null for "root level" if no
     * panel fully contains the box. This is stricter than findPanelAt (which
     * only checks the center point) — it prevents reparenting when the element
     * is only partially overlapping a panel.
     */
    private static String findPanelContaining(VirtualGui gui, float absX, float absY,
                                               float w, float h, String excludeId) {
        // Find the SMALLEST panel (by area) that fully contains the element.
        // This naturally prefers nested/inner panels over their parents —
        // if panelB is inside panelA and both contain the element, panelB
        // (smaller area) wins. Ties broken by z-order (later in elementOrder
        // = rendered on top = preferred).
        String bestId = null;
        float bestArea = Float.MAX_VALUE;
        int bestZ = -1;
        List<String> order = gui.getElementOrder();
        for (int i = 0; i < order.size(); i++) {
            String id = order.get(i);
            if (id.equals(excludeId)) continue;
            VirtualGuiElement el = gui.getElement(id);
            if (el == null || !el.isVisible()) continue;
            if (!"panel".equals(el.getType()) && !"scroll_view".equals(el.getType())) continue;
            if (isDescendantOf(el, excludeId, gui)) continue;
            float ex = el.getAbsoluteX(gui);
            float ey = el.getAbsoluteY(gui);
            float ew = el.getEffectiveWidth(gui);
            float eh = el.getEffectiveHeight(gui);
            // Child must be smaller in both dimensions
            boolean smaller = w < ew && h < eh;
            // All four edges must be inside the target
            boolean fullyInside = absX >= ex && absX + w <= ex + ew
                    && absY >= ey && absY + h <= ey + eh;
            if (smaller && fullyInside) {
                float area = ew * eh;
                // Prefer smaller area; on tie, prefer higher z (later in order)
                if (area < bestArea - 0.5f || (Math.abs(area - bestArea) < 0.5f && i > bestZ)) {
                    bestArea = area;
                    bestId = id;
                    bestZ = i;
                }
            }
        }
        return bestId;
    }

    /**
     * Returns true if `el` is a descendant of the element with id `ancestorId`.
     */
    private static boolean isDescendantOf(VirtualGuiElement el, String ancestorId, VirtualGui gui) {
        String pid = el.getParentId();
        while (pid != null) {
            if (pid.equals(ancestorId)) return true;
            VirtualGuiElement parent = gui.getElement(pid);
            if (parent == null) break;
            pid = parent.getParentId();
        }
        return false;
    }

    /**
     * Reparents an element to a new parent (or root level if newParentId is null).
     * Removes from old parent's children, adds to new parent's children, updates
     * parentId. The element's absolute position is preserved (local coordinates
     * are converted). Also marks the old parent as having reparented children
     * so its scissor is skipped (the element's content is still rendered in the
     * old parent's procedure scope).
     */
    private static void reparentElement(VirtualGuiElement el, String newParentId, VirtualGui gui) {
        // Save absolute position BEFORE changing parent — getAbsoluteX uses
        // parentId to recurse, so we must capture it while the old parent
        // is still set.
        float absX = el.getAbsoluteX(gui);
        float absY = el.getAbsoluteY(gui);

        String oldParentId = el.getParentId();

        // Remove from old parent's children
        if (oldParentId != null) {
            VirtualGuiElement oldParent = gui.getElement(oldParentId);
            if (oldParent != null) {
                oldParent.removeChild(el);
            }
        }

        // Add to new parent's children (at the END so it renders on top)
        if (newParentId != null) {
            VirtualGuiElement newParent = gui.getElement(newParentId);
            if (newParent != null) {
                newParent.addChild(el);
            }
            el.setParentId(newParentId);
        } else {
            // Root level — no parent
            el.setParentId(null);
        }

        // Convert saved absolute → local for the NEW parent.
        // For shouldStick=true: getAbsoluteX = parent.absX + x - parent.resizeDelta
        //   => x = absX - parent.absX + parent.resizeDelta
        // For shouldStick=false: getAbsoluteX = parent.absX + x
        //   => x = absX - parent.absX
        if (newParentId != null) {
            VirtualGuiElement newParent = gui.getElement(newParentId);
            if (newParent != null) {
                float scrollShiftY = newParent.isScrollView() ? newParent.getEffectiveScrollShift() : 0f;
                if (el.shouldStick()) {
                    el.setX(absX - newParent.getAbsoluteX(gui) + newParent.getResizeDeltaX());
                    el.setY(absY - newParent.getAbsoluteY(gui) + newParent.getResizeDeltaY() + scrollShiftY);
                } else {
                    el.setX(absX - newParent.getAbsoluteX(gui));
                    el.setY(absY - newParent.getAbsoluteY(gui) + scrollShiftY);
                }
            }
        } else {
            // Root level — local = absolute
            el.setX(absX);
            el.setY(absY);
        }

        // Bring to front in elementOrder (root-level z-order).
        if (gui.getElementOrder().remove(el.getId())) {
            gui.getElementOrder().add(el.getId());
        }
    }

    // ── Collision push helpers ─────────────────────────────────────────────────
    // Push collision siblings out of the way when dragging, with chain-push
    // and parent-bounds clamping. Siblings can never be pushed outside the
    // parent panel. If the chain is fully compressed, the dragged element stops.

    /**
     * Pushes collision siblings on the X axis. Returns the adjusted newAbsX
     * for the dragged element (may be limited if siblings can't be pushed).
     */
    private static float pushCollisionSiblingsX(VirtualGuiElement el, float oldAbsX, float newAbsX,
                                                   float absY, float w, float h, VirtualGui gui) {
        float result = newAbsX;
        boolean pushRight = newAbsX > oldAbsX;
        String parentId = el.getParentId();

        for (String id : gui.getAllElementIds()) {
            if (id.equals(el.getId())) continue;
            VirtualGuiElement sibling = gui.getElement(id);
            if (sibling == null || !sibling.hasCollision() || !sibling.isVisible()) continue;
            if (!java.util.Objects.equals(sibling.getParentId(), parentId)) continue;

            float sx = sibling.getAbsoluteX(gui);
            float sy = sibling.getAbsoluteY(gui);
            float sw = sibling.getEffectiveWidth(gui);
            float sh = sibling.getEffectiveHeight(gui);

            // Check overlap at (result, absY)
            if (result >= sx + sw || result + w <= sx || absY >= sy + sh || absY + h <= sy) continue;

            // Overlap! Calculate how much to push the sibling.
            float overlap;
            if (pushRight) {
                overlap = (result + w) - sx; // right edge of dragged penetrates left edge of sibling
            } else {
                overlap = (sx + sw) - result; // left edge of dragged penetrates right edge of sibling
            }
            if (overlap <= 0) continue;

            // Chain-push the sibling (and its own overlapping siblings)
            java.util.Set<String> visited = new java.util.HashSet<>();
            visited.add(el.getId());
            float actualPush = pushSiblingChainX(sibling, overlap, pushRight, gui, visited);

            // If the sibling couldn't be pushed the full overlap amount,
            // limit the dragged element to just barely touching the sibling
            if (actualPush < overlap) {
                float shortfall = overlap - actualPush;
                if (pushRight) {
                    result -= shortfall;
                } else {
                    result += shortfall;
                }
            }
        }

        return result;
    }

    /**
     * Pushes collision siblings on the Y axis. Returns the adjusted newAbsY.
     */
    private static float pushCollisionSiblingsY(VirtualGuiElement el, float absX, float oldAbsY, float newAbsY,
                                                   float w, float h, VirtualGui gui) {
        float result = newAbsY;
        boolean pushDown = newAbsY > oldAbsY;
        String parentId = el.getParentId();

        for (String id : gui.getAllElementIds()) {
            if (id.equals(el.getId())) continue;
            VirtualGuiElement sibling = gui.getElement(id);
            if (sibling == null || !sibling.hasCollision() || !sibling.isVisible()) continue;
            if (!java.util.Objects.equals(sibling.getParentId(), parentId)) continue;

            float sx = sibling.getAbsoluteX(gui);
            float sy = sibling.getAbsoluteY(gui);
            float sw = sibling.getEffectiveWidth(gui);
            float sh = sibling.getEffectiveHeight(gui);

            // Check overlap at (absX, result)
            if (absX >= sx + sw || absX + w <= sx || result >= sy + sh || result + h <= sy) continue;

            // Overlap! Calculate push amount.
            float overlap;
            if (pushDown) {
                overlap = (result + h) - sy;
            } else {
                overlap = (sy + sh) - result;
            }
            if (overlap <= 0) continue;

            java.util.Set<String> visited = new java.util.HashSet<>();
            visited.add(el.getId());
            float actualPush = pushSiblingChainY(sibling, overlap, pushDown, gui, visited);

            if (actualPush < overlap) {
                float shortfall = overlap - actualPush;
                if (pushDown) {
                    result -= shortfall;
                } else {
                    result += shortfall;
                }
            }
        }

        return result;
    }

    /**
     * Recursively pushes a sibling on the X axis, chain-pushing any collision
     * siblings it would overlap. Clamps to parent bounds. Returns the actual
     * push amount (may be less than requested).
     */
    private static float pushSiblingChainX(VirtualGuiElement sibling, float pushAmount, boolean pushRight,
                                              VirtualGui gui, java.util.Set<String> visited) {
        if (visited.contains(sibling.getId()) || pushAmount <= 0) return 0;
        visited.add(sibling.getId());

        String parentId = sibling.getParentId();
        float parentLeft = 0, parentRight = Float.MAX_VALUE;
        if (parentId != null) {
            VirtualGuiElement parent = gui.getElement(parentId);
            if (parent != null) {
                parentLeft = parent.getAbsoluteX(gui);
                parentRight = parentLeft + parent.getEffectiveWidth(gui);
            }
        }

        float sx = sibling.getAbsoluteX(gui);
        float sy = sibling.getAbsoluteY(gui);
        float sw = sibling.getEffectiveWidth(gui);
        float sh = sibling.getEffectiveHeight(gui);

        // Maximum the sibling can move before hitting the parent wall
        float maxMove;
        if (pushRight) {
            maxMove = (parentRight - sw) - sx;
        } else {
            maxMove = sx - parentLeft;
        }
        if (maxMove < 0) maxMove = 0;
        float actualPush = Math.min(pushAmount, maxMove);

        // Check if pushing this sibling by actualPush would overlap another collision sibling
        float newSx = sx + (pushRight ? actualPush : -actualPush);
        float checkX1 = pushRight ? newSx + sw : newSx; // the leading edge
        float checkX2 = pushRight ? checkX1 : newSx + sw; // not needed, just clarity

        for (String id : gui.getAllElementIds()) {
            if (id.equals(sibling.getId()) || visited.contains(id)) continue;
            VirtualGuiElement next = gui.getElement(id);
            if (next == null || !next.hasCollision() || !next.isVisible()) continue;
            if (!java.util.Objects.equals(next.getParentId(), parentId)) continue;

            float nx = next.getAbsoluteX(gui);
            float ny = next.getAbsoluteY(gui);
            float nw = next.getEffectiveWidth(gui);
            float nh = next.getEffectiveHeight(gui);

            // Y overlap check
            if (sy >= ny + nh || sy + sh <= ny) continue;

            // X overlap check at the pushed position
            boolean overlapsAtPush;
            if (pushRight) {
                overlapsAtPush = newSx + sw > nx && newSx < nx + nw;
            } else {
                overlapsAtPush = newSx < nx + nw && newSx + sw > nx;
            }
            if (!overlapsAtPush) continue;

            // Chain-push this sibling too
            float chainOverlap;
            if (pushRight) {
                chainOverlap = (newSx + sw) - nx;
            } else {
                chainOverlap = (nx + nw) - newSx;
            }
            if (chainOverlap <= 0) continue;

            float chainPush = pushSiblingChainX(next, chainOverlap, pushRight, gui, visited);
            float chainShortfall = chainOverlap - chainPush;

            // If the chain couldn't absorb the full overlap, reduce our push
            if (chainShortfall > 0) {
                actualPush -= chainShortfall;
                if (actualPush < 0) actualPush = 0;
                newSx = sx + (pushRight ? actualPush : -actualPush);
            }
        }

        // Apply the push
        if (actualPush > 0) {
            if (pushRight) {
                sibling.setX(sibling.getX() + actualPush);
            } else {
                sibling.setX(sibling.getX() - actualPush);
            }
        }

        return actualPush;
    }

    /**
     * Recursively pushes a sibling on the Y axis (same logic as X, rotated).
     */
    private static float pushSiblingChainY(VirtualGuiElement sibling, float pushAmount, boolean pushDown,
                                              VirtualGui gui, java.util.Set<String> visited) {
        if (visited.contains(sibling.getId()) || pushAmount <= 0) return 0;
        visited.add(sibling.getId());

        String parentId = sibling.getParentId();
        float parentTop = 0, parentBottom = Float.MAX_VALUE;
        if (parentId != null) {
            VirtualGuiElement parent = gui.getElement(parentId);
            if (parent != null) {
                parentTop = parent.getAbsoluteY(gui);
                parentBottom = parentTop + parent.getEffectiveHeight(gui);
            }
        }

        float sx = sibling.getAbsoluteX(gui);
        float sy = sibling.getAbsoluteY(gui);
        float sw = sibling.getEffectiveWidth(gui);
        float sh = sibling.getEffectiveHeight(gui);

        float maxMove;
        if (pushDown) {
            maxMove = (parentBottom - sh) - sy;
        } else {
            maxMove = sy - parentTop;
        }
        if (maxMove < 0) maxMove = 0;
        float actualPush = Math.min(pushAmount, maxMove);

        float newSy = sy + (pushDown ? actualPush : -actualPush);

        for (String id : gui.getAllElementIds()) {
            if (id.equals(sibling.getId()) || visited.contains(id)) continue;
            VirtualGuiElement next = gui.getElement(id);
            if (next == null || !next.hasCollision() || !next.isVisible()) continue;
            if (!java.util.Objects.equals(next.getParentId(), parentId)) continue;

            float nx = next.getAbsoluteX(gui);
            float ny = next.getAbsoluteY(gui);
            float nw = next.getEffectiveWidth(gui);
            float nh = next.getEffectiveHeight(gui);

            // X overlap check
            if (sx >= nx + nw || sx + sw <= nx) continue;

            // Y overlap check at pushed position
            boolean overlapsAtPush;
            if (pushDown) {
                overlapsAtPush = newSy + sh > ny && newSy < ny + nh;
            } else {
                overlapsAtPush = newSy < ny + nh && newSy + sh > ny;
            }
            if (!overlapsAtPush) continue;

            float chainOverlap;
            if (pushDown) {
                chainOverlap = (newSy + sh) - ny;
            } else {
                chainOverlap = (ny + nh) - newSy;
            }
            if (chainOverlap <= 0) continue;

            float chainPush = pushSiblingChainY(next, chainOverlap, pushDown, gui, visited);
            float chainShortfall = chainOverlap - chainPush;

            if (chainShortfall > 0) {
                actualPush -= chainShortfall;
                if (actualPush < 0) actualPush = 0;
                newSy = sy + (pushDown ? actualPush : -actualPush);
            }
        }

        if (actualPush > 0) {
            if (pushDown) {
                sibling.setY(sibling.getY() + actualPush);
            } else {
                sibling.setY(sibling.getY() - actualPush);
            }
        }

        return actualPush;
    }

    /**
     * Checks whether the box (absX, absY, absX+w, absY+h) would overlap any
     * OTHER solid (stick=false) sibling of `el` (same parent, visible, not
     * itself) OR any solid render boxes registered on the parent (non-tracked
     * rects/items with stick=false that don't have their own element entry).
     * Used to block dragging one solid element on top of another or past
     * a solid render box.
     */
    private static boolean overlapsSolidSibling(VirtualGuiElement el, float absX, float absY,
                                                  float w, float h, VirtualGui gui) {
        // Check tracked collision siblings
        for (String id : gui.getAllElementIds()) {
            if (id.equals(el.getId())) continue;
            VirtualGuiElement sibling = gui.getElement(id);
            if (sibling == null || !sibling.hasCollision() || !sibling.isVisible()) continue;
            if (!java.util.Objects.equals(sibling.getParentId(), el.getParentId())) continue;
            float sx = sibling.getAbsoluteX(gui);
            float sy = sibling.getAbsoluteY(gui);
            float sw = sibling.getEffectiveWidth(gui);
            float sh = sibling.getEffectiveHeight(gui);
            boolean overlap = absX < sx + sw && absX + w > sx && absY < sy + sh && absY + h > sy;
            if (overlap) return true;
        }

        // Check solid render boxes
        return overlapsSolidRenderBox(el, absX, absY, w, h, gui);
    }

    /** Checks only the parent's solid render boxes (non-tracked rects/items). */
    private static boolean overlapsSolidRenderBox(VirtualGuiElement el, float absX, float absY,
                                                     float w, float h, VirtualGui gui) {
        String parentId = el.getParentId();
        if (parentId != null) {
            VirtualGuiElement parent = gui.getElement(parentId);
            if (parent != null) {
                // Parent's solid render boxes are in LOCAL coordinates relative to the parent.
                // Convert to absolute using the parent's compensated position (same as getSolidUnionBounds).
                float baseX = parent.getAbsoluteX(gui) - parent.getResizeDeltaX();
                float baseY = parent.getAbsoluteY(gui) - parent.getResizeDeltaY();
                for (float[] box : parent.getSolidRenderBoxes()) {
                    float bx1 = baseX + box[0];
                    float by1 = baseY + box[1];
                    float bx2 = baseX + box[2];
                    float by2 = baseY + box[3];
                    boolean overlap = absX < bx2 && absX + w > bx1 && absY < by2 && absY + h > by1;
                    if (overlap) return true;
                }
            }
        }

        return false;
    }


    /**
     * Checks if the element's center is inside a panel that is NOT its
     * current parent (and not a descendant of the element). Used to detect
     * "dropped on top of another panel" for snap-back.
     */
    private static boolean isInsideOtherPanel(VirtualGuiElement el, VirtualGui gui) {
        float absX = el.getAbsoluteX(gui);
        float absY = el.getAbsoluteY(gui);
        float w = el.getEffectiveWidth(gui);
        float h = el.getEffectiveHeight(gui);
        float centerX = absX + w / 2f;
        float centerY = absY + h / 2f;
        List<String> order = gui.getElementOrder();
        for (int i = order.size() - 1; i >= 0; i--) {
            String id = order.get(i);
            if (id.equals(el.getId())) continue;
            VirtualGuiElement panel = gui.getElement(id);
            if (panel == null || !panel.isVisible()) continue;
            if (!"panel".equals(panel.getType()) && !"scroll_view".equals(panel.getType())) continue;
            if (java.util.Objects.equals(id, el.getParentId())) continue;
            if (isDescendantOf(panel, el.getId(), gui)) continue;
            float px = panel.getAbsoluteX(gui);
            float py = panel.getAbsoluteY(gui);
            float pw = panel.getEffectiveWidth(gui);
            float ph = panel.getEffectiveHeight(gui);
            if (centerX > px && centerX < px + pw && centerY > py && centerY < py + ph) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reverts an element to its pre-drag position and parent. Sets the local
     * position so the absolute position matches preDragAbsX/Y, then restores
     * the parent if it changed during drag (reparentElement re-converts).
     */
    private static void snapBackToPreDrag(VirtualGuiElement el, VirtualGui gui) {
        // Set local position so absolute = pre-drag absolute (current parent)
        String currentPid = el.getParentId();
        if (currentPid != null) {
            VirtualGuiElement parent = gui.getElement(currentPid);
            if (parent != null) {
                float scrollShiftY = parent.isScrollView() ? parent.getEffectiveScrollShift() : 0f;
                if (el.shouldStick()) {
                    el.setX(preDragAbsX - parent.getAbsoluteX(gui) + parent.getResizeDeltaX());
                    el.setY(preDragAbsY - parent.getAbsoluteY(gui) + parent.getResizeDeltaY() + scrollShiftY);
                } else {
                    el.setX(preDragAbsX - parent.getAbsoluteX(gui));
                    el.setY(preDragAbsY - parent.getAbsoluteY(gui) + scrollShiftY);
                }
            }
        } else {
            el.setX(preDragAbsX);
            el.setY(preDragAbsY);
        }
        // Restore parent if it changed during drag
        if (!java.util.Objects.equals(currentPid, preDragParentId)) {
            reparentElement(el, preDragParentId, gui);
        }
    }

    /**
     * Finds the topmost drop-target element whose bounds contain the given
     * point (center of the dragged element). Skips panels (panels use the
     * dragIn reparent system, not drop targets). Returns null if none found.
     */
    private static VirtualGuiElement findDropTarget(VirtualGui gui, float cx, float cy, String excludeId) {
        // Iterate in reverse order so topmost (last rendered) elements are checked first
        for (int i = gui.elementOrder.size() - 1; i >= 0; i--) {
            String id = gui.elementOrder.get(i);
            if (id.equals(excludeId)) continue;
            VirtualGuiElement el = gui.elements.get(id);
            if (el == null || !el.isVisible() || !el.isDropTarget()) continue;
            // Skip panels — they use dragIn reparenting, not drop targets
            if ("panel".equals(el.getType()) || "scrollview".equals(el.getType())) continue;
            float ex = el.getAbsoluteX(gui);
            float ey = el.getAbsoluteY(gui);
            float ew = el.getEffectiveWidth(gui);
            float eh = el.getEffectiveHeight(gui);
            if (cx >= ex && cx <= ex + ew && cy >= ey && cy <= ey + eh) {
                return el;
            }
        }
        return null;
    }

    public static void handleRelease() {
        // Clear resize state
        resizingElementId = null;
        resizeDirection = 0;

        // If pending click exists (no drag started), fire button action
        if (pendingClickElementId != null && current != null) {
            VirtualGuiElement el = current.elements.get(pendingClickElementId);
            if (el != null) {
                // Check for double-click on release path (button + draggable elements)
                long now = System.currentTimeMillis();
                if (el.getDoubleClickActionId() != null && !el.getDoubleClickActionId().isEmpty()
                        && now - lastClickTime < DOUBLE_CLICK_INTERVAL_MS
                        && el.getId().equals(lastClickElementId)) {
                    setActionReason("double_click");
                    setActionElementId(el.getId());
                    callAction(el.getDoubleClickActionId());
                    lastClickTime = 0;
                    pendingClickElementId = null;
                } else {
                    lastClickTime = now;
                    lastClickElementId = el.getId();
                    if (el.getButtonActionId() != null && !el.getButtonActionId().isEmpty()) {
                        setActionReason("button_clicked");
                        setActionElementId(el.getId());
                        callAction(el.getButtonActionId());
                    }
                    pendingClickElementId = null;
                }
            } else {
                pendingClickElementId = null;
            }
        }

        // ── Post-drag: reparent dragIn elements + snap-back ──────────────────
        // For dragIn elements: find the best (smallest) panel that fully
        // contains the element and reparent to it. If no valid panel is found
        // and the element is visually inside a panel it's not a child of,
        // snap back to the pre-drag position (prevents "looks like it's
        // inside but isn't" confusion).
        // For dragOut-only elements: if dropped on top of a panel they're not
        // a child of, snap back to pre-drag position.
        if (current != null && draggingElementId != null) {
            VirtualGuiElement el = current.elements.get(draggingElementId);
            if (el != null) {
                boolean wasReparentedDuringDrag = !java.util.Objects.equals(
                        el.getParentId(), preDragParentId);

                if (el.canDragIn()) {
                    // Try to find the best panel at the element's current position
                    float absX = el.getAbsoluteX(current);
                    float absY = el.getAbsoluteY(current);
                    float w = el.getEffectiveWidth(current);
                    float h = el.getEffectiveHeight(current);
                    String targetPanel = findPanelContaining(current, absX, absY, w, h, el.getId());

                    if (targetPanel != null
                            && !java.util.Objects.equals(targetPanel, el.getParentId())) {
                        // Skip reparenting if this element is excluded from grids
                        // and the target panel has a grid (floating windows shouldn't snap in)
                        VirtualGuiElement targetEl = current.elements.get(targetPanel);
                        if (el.isExcludeFromGrid() && targetEl != null && targetEl.hasGrid()) {
                            // Don't reparent — let it float freely
                        } else {
                        // Reparent to the new panel
                        setActionReason("drag_in");
                        setActionElementId(el.getId());
                        callAction(el.getActionId());
                        callAction(el.getButtonActionId());
                        reparentElement(el, targetPanel, current);
                        // Check for solid sibling overlap — revert if blocked
                        float newAbsX = el.getAbsoluteX(current);
                        float newAbsY = el.getAbsoluteY(current);
                        if (overlapsSolidSibling(el, newAbsX, newAbsY, w, h, current)) {
                            // Blocked by solid sibling — revert to pre-drag state
                            snapBackToPreDrag(el, current);
                        } else {
                            // If the new parent has a grid, snap the element to it
                            VirtualGuiElement gridParent = current.elements.get(targetPanel);
                            if (gridParent != null && gridParent.hasGrid()) {
                                setActionReason("grid_add");
                                setActionElementId(el.getId());
                                callAction(el.getActionId());
                                callAction(el.getButtonActionId());
                                boolean snapped = gridParent.snapChildToGrid(el, current);
                                if (!snapped) {
                                    // Object too big for grid cell — reject, snap back
                                    snapBackToPreDrag(el, current);
                                }
                            }
                        }
                        } // end else (not excludeFromGrid)
                    } else if (targetPanel == null && !wasReparentedDuringDrag) {
                        // No panel found and parent didn't change during drag.
                        // If the element is visually inside another panel,
                        // snap back to pre-drag position.
                        if (isInsideOtherPanel(el, current)) {
                            snapBackToPreDrag(el, current);
                        }
                    }
                } else if (!wasReparentedDuringDrag) {
                    // No dragIn and parent didn't change (no dragOut, or didn't
                    // leave parent). If visually inside another panel, snap back.
                    // Skip for excludeFromGrid elements (floating windows over grid panels).
                    if (isInsideOtherPanel(el, current) && !el.isExcludeFromGrid()) {
                        snapBackToPreDrag(el, current);
                    }
                }

                // ── Drop target check ─────────────────────────────────────────────
                // After all panel/grid reparenting, check if the element was
                // dropped onto a drop-target element (e.g. trashbin). If so,
                // fire the drop target's action. The action can cancel to snap
                // the dragged element back to its pre-drag position.
                boolean wasDroppedOnTarget = false;
                {
                    float elCX = el.getAbsoluteX(current) + el.getEffectiveWidth(current) / 2f;
                    float elCY = el.getAbsoluteY(current) + el.getEffectiveHeight(current) / 2f;
                    VirtualGuiElement target = findDropTarget(current, elCX, elCY, el.getId());
                    if (target != null && target.getDropActionId() != null && !target.getDropActionId().isEmpty()) {
                        wasDroppedOnTarget = true;
                        droppedElementId = el.getId();
                        dropTargetId = target.getId();
                        setActionReason("drop_on_target");
                        setActionElementId(el.getId());
                        callAction(target.getDropActionId());
                        // If the drop action called cancelAction(), snap back
                        if (isActionCancelled()) {
                            snapBackToPreDrag(el, current);
                        }
                        droppedElementId = null;
                        dropTargetId = null;
                    }
                }

                // If the element's current parent has a grid, snap the dragged
                // element with occupancy check. If the target cell is occupied
                // (or the object is too big), snap back to pre-drag position.
                // Then re-snap all other children (no occupancy check — just
                // re-center them in their existing cells).
                // Note: we run the grid check even if the element was dropped on
                // a drop target, because the drop action is asynchronous (queued
                // via callAction and consumed on the next render tick). The
                // grid occupancy check must happen NOW, synchronously, to prevent
                // two elements from sharing a cell. If the drop target action
                // later cancels, it will snap back on its own.
                if (el.getParentId() != null) {
                    VirtualGuiElement p = current.elements.get(el.getParentId());
                    if (p != null && p.hasGrid()) {
                        boolean snapped = p.snapChildToGrid(el, current, true);
                        if (!snapped) {
                            // Cell occupied or too big — snap back to pre-drag
                            snapBackToPreDrag(el, current);
                        }
                        // Re-snap all children (no occupancy check — just re-center)
                        p.reSnapAllChildren(current);
                    }
                }
            }
        }
        preDragParentId = null;
        clearActionCancel();

        draggingElementId = null;
        if (current != null) {
            for (String id : current.elementOrder) {
                VirtualGuiElement el = current.elements.get(id);
                if (el != null && "button".equals(el.getType())) {
                    el.setActive(false);
                }
            }
        }
        draggingSliderId = null;

        // Auto-save persistent state after drag/resize
        savePersistentState();
    }

    public static boolean isElementBeingDragged(String id) {
        return id != null && id.equals(draggingElementId);
    }

    public static boolean isElementBeingResized(String id) {
        return id != null && id.equals(resizingElementId);
    }

    /** Returns true if ANY element is currently being dragged. */
    public static boolean isAnyElementBeingDragged() {
        return draggingElementId != null;
    }

    /** Returns true if ANY element is currently being resized. */
    public static boolean isAnyElementBeingResized() {
        return resizingElementId != null;
    }

    /** Returns the current resize direction bitmask (for handle rendering during resize). */
    public static int getResizeDirection() { return resizeDirection; }

    // ── Rendering ────────────────────────────────────────────────────────────────

    // ── MENU PARTS ─────────────────────────────────────────────────────────────

    /**
     * Fires a MenuPartEvent on the NeoForge event bus with the given part ID.
     * Called from the main render procedure via the "Add Menu Part" block.
     * All procedures subscribed to FomekMenuPart will run; each one should
     * start with "Register Menu Part" (matchMenuPart) to filter by ID.
     *
     * The event fires synchronously within the current menu context, so all
     * parts share the same menu data, panel stack, boundaries, and elements.
     */
    public static void addMenuPart(String id) {
        if (id == null || id.isEmpty()) return;
        // Need an active menu and render context
        if (current == null) return;
        GuiGraphics gui = lastGuiGraphics;
        if (gui == null) gui = MenuRenderHelper.getGuiGraphics();
        if (gui == null) return;
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.entity.player.Player player = mc.player;
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        currentPartId = id;
        GuiState.MenuPartEvent event = new GuiState.MenuPartEvent(
            gui, player, (int) renderMouseX, (int) renderMouseY,
            w, h, lastPartialTick, id);
        NeoForge.EVENT_BUS.post(event);
        currentPartId = null;
    }

    /**
     * Returns true if the current menu part ID matches the given ID.
     * Used by the "Register Menu Part" block at the top of part procedures
     * as a guard clause — if it returns false, the procedure exits early.
     */
    public static boolean matchMenuPart(String id) {
        if (id == null || currentPartId == null) return false;
        return id.equals(currentPartId);
    }

    /**
     * Returns the current menu part ID (set by addMenuPart, cleared after).
     */
    public static String getCurrentPartId() {
        return currentPartId;
    }

    public static void onScreenRender(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        // Poll InputManager (frame + second frequencies) before user procedures run
        InputManager.updateAll();
        // Compute which update frequencies are active this frame
        UpdateManager.beginFrame();

        GuiState.updateMousePosition((float) mouseX, (float) mouseY);

        // Always fire the RenderEvent — user procedures need it to call beginMenu
        // and build their GUI. The event fires on EVERY screen render, and the
        // user's procedure decides whether to do work (e.g. check containerMenu).
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.entity.player.Player player = mc.player;
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        // Detect screen size change — proportionally scale all root-level
        // element positions and sizes so they stay in the same relative
        // position when the window is resized (e.g. exiting fullscreen or
        // dragging the window edge).
        float newSW = (float) w;
        float newSH = (float) h;
        if (prevScreenWidth > 0 && prevScreenHeight > 0 && current != null
                && (newSW != prevScreenWidth || newSH != prevScreenHeight)) {
            float scaleX = newSW / prevScreenWidth;
            float scaleY = newSH / prevScreenHeight;
            // Scale ALL elements (root + nested children) proportionally.
            // Children's local positions are relative to their parent, so
            // scaling them by the same ratio keeps them in the same relative
            // spot inside the resized parent.
            for (String id : current.elementOrder) {
                VirtualGuiElement el = current.elements.get(id);
                if (el == null || !el.isVisible()) continue;
                if (id.equals(resizingElementId) || id.equals(draggingElementId)) continue;
                // Skip root-level elements with bounds — they're repositioned
                // proportionally to the BOUNDARY in beginPanel (not the screen),
                // because boundaries like screenWidth/2-200 aren't proportional
                // to the screen. Scaling them here too would conflict.
                // Also skip their children — the parent's size change is handled
                // by beginPanel (boundary ratio), so children's local positions
                // should NOT be scaled by the screen ratio (which would shift
                // them within the unchanged parent for fixed-size boundaries).
                if (el.getParentId() == null && el.hasBounds()) continue;
                if (el.getParentId() != null) {
                    VirtualGuiElement rootAnc = getRootAncestor(el, current);
                    if (rootAnc.hasBounds()) continue;
                    // Skip children of scaled-content panels — they stay in design space
                    if (rootAnc.getScaledContent()) continue;
                }
                el.setX(el.getX() * scaleX);
                el.setY(el.getY() * scaleY);
                el.setWidth(el.getWidth() * scaleX);
                el.setHeight(el.getHeight() * scaleY);
                // NOTE: grid area + cell size are NOT scaled here. Panels/scroll
                // views re-apply PanelAttribute.Grid every frame, which calls
                // updateGridScale() and recomputes the grid area AND explicit
                // cell size from the FIXED original anchor (captured once at grid
                // creation) vs this element's now-updated width/height. Scaling
                // the grid here too used to also mutate that "original" anchor
                // (gridOrigBoxX1..Y2, gridOrigPanelW/H), which corrupted the ratio
                // baseline and caused compounding drift across repeated resizes
                // (e.g. toggling fullscreen a few times would leave the grid
                // increasingly out of sync with the panel).
            }
            resizeJustHappened = true;

            // Second pass: re-snap grid children to their rounded cell centers.
            // The multiply above scales child x/y by a float ratio that does NOT
            // generally land on the same rounded integer cell-center that
            // snapToGrid() computes — that mismatch shifts grid objects a few
            // pixels on every fullscreen toggle. Same thing the live drag-resize
            // path already does (reSnapAllChildren); window resize was just
            // missing it.
            for (String id : current.elementOrder) {
                VirtualGuiElement el = current.elements.get(id);
                if (el == null || !el.isVisible()) continue;
                if (id.equals(resizingElementId) || id.equals(draggingElementId)) continue;
                if (el.getParentId() == null && el.hasBounds()) continue;
                if (el.getParentId() != null) {
                    VirtualGuiElement rootAnc = getRootAncestor(el, current);
                    if (rootAnc.hasBounds()) continue;
                }
                if (el.hasGrid()) {
                    el.reSnapAllChildren(current);
                }
            }
        }
        prevScreenWidth = newSW;
        prevScreenHeight = newSH;
        // Store screen dimensions for boundary clamping (drag, resize, snap)
        screenWidth = (float) w;
        screenHeight = (float) h;
        // Store mouse position for beginPanel decoration rendering
        renderMouseX = (float) mouseX;
        renderMouseY = (float) mouseY;
        lastPartialTick = partialTick;
        lastGuiGraphics = gui;

        GuiState.RenderEvent event = new GuiState.RenderEvent(gui, player, mouseX, mouseY, w, h, partialTick);
        NeoForge.EVENT_BUS.post(event);

        // After the event, clamp existing top-level panels to screen bounds.
        // Handles drag pushing panels off-screen. SKIPPED on the frame a window
        // resize just happened — the proportional scaling already keeps panels
        // in-bounds, and clamping on top of that causes one-way drift for panels
        // near edges (clamp moves them inward, reverse resize doesn't move them
        // back out, net drift after each toggle).
        if (current != null && !resizeJustHappened) {
            for (String id : current.elementOrder) {
                VirtualGuiElement el = current.elements.get(id);
                if (el == null || !el.isVisible()) continue;
                // Skip elements being actively resized or dragged
                if (id.equals(resizingElementId) || id.equals(draggingElementId)) continue;
                // Top-level elements: clamp size + position via absolute coords
                if (el.getParentId() == null) {
                    float absX = el.getAbsoluteX(current);
                    float absY = el.getAbsoluteY(current);
                    float elW = el.getWidth();
                    float elH = el.getHeight();
                    float maxW = (screenWidth > 0) ? screenWidth : Float.MAX_VALUE;
                    float maxH = (screenHeight > 0) ? screenHeight : Float.MAX_VALUE;
                    if (el.hasBounds()) {
                        maxW = Math.min(maxW, el.getBoundsX2() - el.getBoundsX1());
                        maxH = Math.min(maxH, el.getBoundsY2() - el.getBoundsY1());
                    }
                    if (elW > maxW) el.setWidth(maxW);
                    if (elH > maxH) el.setHeight(maxH);
                    float clampedX = el.clampAbsX(current, absX);
                    float clampedY = el.clampAbsY(current, absY);
                    if (clampedX != absX) el.setX(clampedX);
                    if (clampedY != absY) el.setY(clampedY);
                }
                // All elements with bounds (top-level + nested): enforce render
                // bounds to catch content-scale mismatches that absolute clamping misses
                enforceRenderBounds(el);
            }
        }
        resizeJustHappened = false;
    }

    public static void renderElements(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        if (current == null) return;
        // Reset background + decoration render flags so elements re-render both
        // in z-order (elementOrder). Backgrounds are now rendered in render()
        // (not during beginPanel) to ensure correct z-ordering across panels.
        // Pending immediate fills (renderRect inside panels) are also rendered
        // in render() after the background, so they respect elementOrder.
        for (String id : current.elementOrder) {
            VirtualGuiElement el = current.elements.get(id);
            if (el != null) {
                el.resetBackgroundRendered();
                el.resetDecorationsRendered();
            }
        }
        // Render root-level elements in z-order, SKIPPING the element being
        // dragged (if any). The dragged element is rendered LAST so it always
        // appears on top of everything — including sibling panels that would
        // otherwise render after it and cover the dragged item.
        //
        // IMPORTANT: gui.flush() is called after EACH top-level element's
        // render() call. GuiGraphics buffers drawString() calls separately
        // from fill() calls — fill() draws immediately in call order, but
        // text glyphs get queued and can end up rendered in one big batch at
        // the end of the frame, AFTER all fills, regardless of when they were
        // called. Without flushing per-panel, a panel drawn earlier in
        // elementOrder (e.g. desktop) would have its TEXT appear on top of a
        // panel drawn later (e.g. app_console), even though its fills/grid
        // correctly render underneath. Flushing after each panel forces its
        // queued text to actually hit the screen before the next panel draws,
        // so text respects elementOrder just like fills do.
        String draggedId = draggingElementId;
        for (String id : current.elementOrder) {
            if (id.equals(draggedId)) continue; // dragged element rendered last
            VirtualGuiElement el = current.elements.get(id);
            if (el != null && el.getParentId() == null && el.isVisible()) {
                el.render(gui, current, mouseX, mouseY, partialTick);
                gui.flush();
            }
        }
        // Render the dragged element LAST (on top of everything).
        // This works for both root-level and nested elements — the element is
        // skipped in its parent's children rendering (see VirtualGuiElement.render)
        // and rendered here at the top level instead, so it's never covered by
        // a sibling panel. The drag state is reset in handleRelease.
        if (draggedId != null && current.elements.containsKey(draggedId)) {
            VirtualGuiElement draggedEl = current.elements.get(draggedId);
            if (draggedEl != null && draggedEl.isVisible()) {
                draggedEl.render(gui, current, mouseX, mouseY, partialTick);
                gui.flush();
            }
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────────

    VirtualGuiElement getElement(String id) { return elements.get(id); }
    Set<String> getAllElementIds() { return elements.keySet(); }
    List<String> getElementOrder() { return elementOrder; }
    private VirtualGui(String id) { this.id = id; }


    // ── Collision push system ─────────────────────────────────────────────────────
    // When a panel is resized, collision=true children are pushed inward instead
    // of blocking the resize. Children push each other (chain pushing). When
    // there's no more space, the resize is blocked by the minimum-size clamp.

    /**
     * Pushes all collision=true children of a panel inward to fit within the
     * new local bounds [0, newW] × [0, newH]. Children push each other:
     * if child A is pushed into child B (and they overlap in the other axis),
     * child B is pushed too, cascading down the chain.
     */
    private static void pushCollisionChildren(VirtualGuiElement panel, VirtualGui gui, float newW, float newH) {
        java.util.List<VirtualGuiElement> collisionChildren = new java.util.ArrayList<>();
        for (VirtualGuiElement child : panel.getChildren()) {
            if (child.hasCollision() && child.isVisible()) {
                collisionChildren.add(child);
            }
        }
        if (collisionChildren.isEmpty()) return;

        // X axis: sort by x, push left-to-right then right-to-left
        pushCollisionAxis(collisionChildren, gui, true, newW);
        // Y axis: sort by y, push top-to-bottom then bottom-to-top
        pushCollisionAxis(collisionChildren, gui, false, newH);
    }

    /**
     * Pushes collision children along one axis (X if isX=true, Y otherwise)
     * to fit within [0, newSize]. Uses a two-pass algorithm:
     * 1. Forward pass (left-to-right / top-to-bottom): push children forward
     *    if they overlap the wall or a previous child.
     * 2. Backward pass (right-to-left / bottom-to-top): push children backward
     *    if they overlap the opposite wall or a next child.
     * Two children only interact if they also overlap in the other axis.
     */
    private static void pushCollisionAxis(java.util.List<VirtualGuiElement> children, VirtualGui gui,
                                           boolean isX, float newSize) {
        // Sort by position in this axis
        children.sort((a, b) -> {
            float pa = isX ? a.getX() : a.getY();
            float pb = isX ? b.getX() : b.getY();
            return Float.compare(pa, pb);
        });

        // Forward pass: push rightward (or downward)
        for (int i = 0; i < children.size(); i++) {
            VirtualGuiElement child = children.get(i);
            float pos = isX ? child.getX() : child.getY();
            float size = isX ? child.getEffectiveWidth(gui) : child.getEffectiveHeight(gui);

            // Push against left/top wall (0)
            if (pos < 0) {
                pos = 0;
                if (isX) child.setX(pos); else child.setY(pos);
            }

            // Push against previous children (chain push) — only if they overlap in the other axis
            for (int j = 0; j < i; j++) {
                VirtualGuiElement prev = children.get(j);
                if (!overlapsInOtherAxis(child, prev, isX, gui)) continue;
                float prevPos = isX ? prev.getX() : prev.getY();
                float prevSize = isX ? prev.getEffectiveWidth(gui) : prev.getEffectiveHeight(gui);
                if (pos < prevPos + prevSize) {
                    pos = prevPos + prevSize;
                    if (isX) child.setX(pos); else child.setY(pos);
                }
            }
        }

        // Backward pass: push leftward (or upward)
        for (int i = children.size() - 1; i >= 0; i--) {
            VirtualGuiElement child = children.get(i);
            float pos = isX ? child.getX() : child.getY();
            float size = isX ? child.getEffectiveWidth(gui) : child.getEffectiveHeight(gui);

            // Push against right/bottom wall (newSize)
            if (pos + size > newSize) {
                pos = newSize - size;
                if (isX) child.setX(pos); else child.setY(pos);
            }

            // Push against next children (chain push) — only if they overlap in the other axis
            for (int j = children.size() - 1; j > i; j--) {
                VirtualGuiElement next = children.get(j);
                if (!overlapsInOtherAxis(child, next, isX, gui)) continue;
                float nextPos = isX ? next.getX() : next.getY();
                if (pos + size > nextPos) {
                    pos = nextPos - size;
                    if (isX) child.setX(pos); else child.setY(pos);
                }
            }
        }

        // Final forward pass to resolve any new overlaps created by backward pass
        for (int i = 0; i < children.size(); i++) {
            VirtualGuiElement child = children.get(i);
            float pos = isX ? child.getX() : child.getY();
            float size = isX ? child.getEffectiveWidth(gui) : child.getEffectiveHeight(gui);
            if (pos < 0) {
                pos = 0;
                if (isX) child.setX(pos); else child.setY(pos);
            }
            for (int j = 0; j < i; j++) {
                VirtualGuiElement prev = children.get(j);
                if (!overlapsInOtherAxis(child, prev, isX, gui)) continue;
                float prevPos = isX ? prev.getX() : prev.getY();
                float prevSize = isX ? prev.getEffectiveWidth(gui) : prev.getEffectiveHeight(gui);
                if (pos < prevPos + prevSize) {
                    pos = prevPos + prevSize;
                    if (isX) child.setX(pos); else child.setY(pos);
                }
            }
        }
    }

    /**
     * Checks if two elements overlap in the axis perpendicular to the push axis.
     * When pushing along X, checks Y overlap. When pushing along Y, checks X overlap.
     */
    private static boolean overlapsInOtherAxis(VirtualGuiElement a, VirtualGuiElement b,
                                                boolean isX, VirtualGui gui) {
        if (isX) {
            // Check Y overlap
            float ay1 = a.getY(), ay2 = a.getY() + a.getEffectiveHeight(gui);
            float by1 = b.getY(), by2 = b.getY() + b.getEffectiveHeight(gui);
            return ay1 < by2 && by1 < ay2;
        } else {
            // Check X overlap
            float ax1 = a.getX(), ax2 = a.getX() + a.getEffectiveWidth(gui);
            float bx1 = b.getX(), bx2 = b.getX() + b.getEffectiveWidth(gui);
            return ax1 < bx2 && bx1 < ax2;
        }
    }

    /**
     * Computes the minimum width needed to fit all collision=true children
     * of a panel. Children that overlap in Y must be in the same "row" and
     * their widths add up. Children in different rows can share the same X
     * space. Returns the max row width.
     */
    private static float computeMinCollisionWidth(VirtualGuiElement panel, VirtualGui gui) {
        java.util.List<VirtualGuiElement> children = new java.util.ArrayList<>();
        for (VirtualGuiElement child : panel.getChildren()) {
            if (child.hasCollision() && child.isVisible()) {
                children.add(child);
            }
        }
        if (children.isEmpty()) return 0;
        return computeMinAxis(children, gui, true);
    }

    /**
     * Computes the minimum height needed to fit all collision=true children.
     */
    private static float computeMinCollisionHeight(VirtualGuiElement panel, VirtualGui gui) {
        java.util.List<VirtualGuiElement> children = new java.util.ArrayList<>();
        for (VirtualGuiElement child : panel.getChildren()) {
            if (child.hasCollision() && child.isVisible()) {
                children.add(child);
            }
        }
        if (children.isEmpty()) return 0;
        return computeMinAxis(children, gui, false);
    }

    /**
     * Computes the minimum size along one axis for a set of children.
     * Groups children by overlap in the perpendicular axis (connected components),
     * then sums sizes within each group. Returns the max group sum.
     */
    private static float computeMinAxis(java.util.List<VirtualGuiElement> children, VirtualGui gui, boolean isX) {
        int n = children.size();
        // Union-find for connected components by perpendicular-axis overlap
        int[] root = new int[n];
        for (int i = 0; i < n; i++) root[i] = i;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (overlapsInOtherAxis(children.get(i), children.get(j), isX, gui)) {
                    // Merge i and j
                    int ri = root[i]; while (root[ri] != ri) ri = root[ri];
                    int rj = root[j]; while (root[rj] != rj) rj = root[rj];
                    if (ri != rj) root[ri] = rj;
                }
            }
        }
        // Sum sizes per group
        java.util.Map<Integer, Float> groupSums = new java.util.HashMap<>();
        for (int i = 0; i < n; i++) {
            int r = i; while (root[r] != r) r = root[r];
            float size = isX ? children.get(i).getEffectiveWidth(gui) : children.get(i).getEffectiveHeight(gui);
            groupSums.merge(r, size, Float::sum);
        }
        float minSize = 0;
        for (float s : groupSums.values()) {
            minSize = Math.max(minSize, s);
        }
        return minSize;
    }

}
