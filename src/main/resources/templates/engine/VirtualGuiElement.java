package net.tamashi.fomekcore.api.guisystems;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * VirtualGuiElement — A single element in the virtual GUI tree.
 *
 * Types: "panel", "rectangle", "button", "slider"
 * Supports nesting/parenting. Movable panels can be dragged.
 * Draggable zones define where on the panel the user must click to drag.
 * Buttons and sliders support optional check IDs that gate interaction.
 */
public class VirtualGuiElement {

    private final String id;
    private final String type;
    private final String studioKey = StudioRuntime.key();
    MenuControls.Control control;
    private String studioText;
    private int studioTextColor;
    private boolean studioTextShadow;
    public void setStudioText(String text,int color,boolean shadow){
        studioText=text;studioTextColor=color;studioTextShadow=shadow;
        width=StudioRuntime.textWidth(text);height=StudioRuntime.textHeight();
    }
    private float x, y;
    private float width, height;
    private float originalWidth, originalHeight;
    private int color;

    private String parentId;
    private final List<VirtualGuiElement> children = new ArrayList<>();
    private boolean visible = true;
    private boolean movable = false;

    // Draggable zone (local coordinates, relative to element position)
    private boolean hasDragZone = false;
    private float dragZoneX1, dragZoneY1, dragZoneX2, dragZoneY2;
    private String[] dragZoneCheckIds = null;

    // Panel type (visual)
    private PanelType panelType;

    // Drag bounds
    private boolean hasBounds = false;
    private float boundsX1, boundsY1, boundsX2, boundsY2;

    // Button metadata
    private String actionId;
    private String[] checkIds = null;
    private boolean active;

    // Slider metadata
    private float sliderValue = 0f;
    private float sliderMin = 0f;
    private float sliderMax = 1f;
    private String sliderType = "smooth";
    private String sliderDirection = "horizontal";
    private float stepSize = 0f; // 0 = auto (fallback to old behavior)
    private boolean reverseDefault = false; // slider starts at max instead of min

    // Scroll view metadata
    private boolean scrollView = false;
    private float scrollOffsetY = 0f;
    private float contentHeight = 0f;  // total height of all children (for scroll clamping)
    // Scroll anchor: false (default) = "top" — offset 0 shows the top of the
    // content (traditional list/folder view, scrolling down reveals lower content).
    // true = "bottom" — offset 0 shows the BOTTOM of the content (chat-log style;
    // scrolling reveals earlier/upper content as you scroll toward offset max).
    private boolean scrollAnchorBottom = false;

    // Button attribute (applied to panels via PanelAttribute.Button)
    private boolean hasButtonAttribute = false;
    private String buttonActionId = null;
    private String[] buttonCheckIds = null;

    // Resize attribute (applied to panels via PanelAttribute.Resize)
    public static final int RESIZE_NONE = 0;
    public static final int RESIZE_LEFT = 1;
    public static final int RESIZE_RIGHT = 2;
    public static final int RESIZE_TOP = 4;
    public static final int RESIZE_BOTTOM = 8;
    private boolean resizable = false;
    private String[] resizeCheckIds = null;
    private float resizeBorderWidth = 5f;
    // Min/max resize constraints (-1 = no constraint)
    private float minResizeX = -1f;
    private float minResizeY = -1f;
    private float maxResizeX = -1f;
    private float maxResizeY = -1f;
    // Highlight settings for drag/resize edge indicators
    private boolean dragHighlight = true;
    private int dragHighlightColor = Integer.MIN_VALUE;
    private boolean resizeHighlight = true;
    private int resizeHighlightColor = Integer.MIN_VALUE;

    // ── Stick / Solid model ──────────────────────────────────────────────────
    // Every element (children + render calls) has a FIXED local position and
    // FIXED size — nothing scales or moves when the parent panel is resized.
    // (Position is always compensated by the parent's resizeDelta so it never
    // drifts on screen during a left/top-edge resize; it only moves when the
    // parent panel itself is DRAGGED.)
    //
    // stick = true  → purely decorative. Ignored by the resize/collision system:
    //                  it can be clipped/hidden if the panel shrinks past it.
    // stick = false (default) → SOLID. Acts like a real placed object with a
    //                  fixed collision box: the parent panel's resize is
    //                  clamped so it can never shrink small enough to clip a
    //                  solid child, and solid siblings block each other from
    //                  being dragged into an overlapping position.
    private boolean shouldStick = false;

    // Collision flag (independent of stick). When true, this element blocks
    // sibling overlap (drag + resize collision) and blocks parent resize
    // from going past it. Works regardless of stick setting.
    private boolean collision = false;
    private boolean scaledContent = false;
    private float designWidth = 0f;
    private float designHeight = 0f;

    // When true, this element's drag is clamped to the SCREEN instead of its
    // parent panel — it can physically leave the parent's bounds. Independent
    // of canDragIn: dragOut alone lets it float outside without changing owners.
    private boolean canDragOut = false;

    // When true, while being dragged, if this element's center moves over a
    // different panel/scroll view, it's reparented into it (or becomes
    // root-level if over no panel). Independent of canDragOut: dragIn alone
    // lets it snap into an overlapping panel without ever leaving its own
    // parent's bounds.
    private boolean canDragIn = false;

    // When true, this element acts as a drop target — other draggable elements
    // dropped onto it (center point within bounds) trigger its drop action.
    // The action can accept (do nothing → element stays) or cancel (snap back).
    private boolean isDropTarget = false;
    private String dropActionId = null;
    private String doubleClickActionId = null;
    private String rightClickActionId = null;
    private String[] dropCheckIds = null;

    // The parent the element was originally created with (set at creation time).
    // Used by dragOut-only: when the element is dragged back inside its home
    // parent's bounds, it reparents back so it moves with the panel again.
    private String homeParentId = null;

    // When true, resize handles only activate at CORNERS (both horizontal and
    // vertical edge hit simultaneously). Used for render items and textures.
    private boolean cornerResizeOnly = false;

    // When true, corner resize maintains the element's original aspect ratio
    // (width / height). Used for render items (16:16 = 1:1 by default).
    private boolean maintainAspectRatio = false;

    // Tracks how much this element's absolute position has shifted due to
    // resize operations (accumulated). Children and render calls subtract
    // this from the parent's absolute position so they stay pinned at
    // their screen position when the panel's top-left corner moves during
    // left/top resize. Reset to 0 when the element is recreated (menu change).
    private float resizeDeltaX = 0f;
    private float resizeDeltaY = 0f;

    // Grid system (applied to panels/scroll-views via PanelAttribute.Grid)
    private boolean hasGrid = false;
    private boolean excludeFromGrid = false; // if true, this element won't snap to parent's grid
    private boolean hasDragBounds = false; // optional drag constraint box (tighter than screen/boundary)
    private float dragBoundsX1, dragBoundsY1, dragBoundsX2, dragBoundsY2;
    private boolean hasResizeBounds = false; // optional resize constraint box
    private float resizeBoundsX1, resizeBoundsY1, resizeBoundsX2, resizeBoundsY2;
    private float gridX1, gridY1, gridX2, gridY2; // grid area in local coords (scaled)
    private String gridOrientation = "both";
    private boolean gridSnapToEnd = false;
    private float gridCellSize = -1f; // -1 = auto
    private boolean gridHasCellSize = false;
    private boolean gridRender = false;       // render grid lines?
    private int gridRenderColor = 0xFF888888; // gray default
    // Grid direction: "horizontal" = fill left-to-right then wrap to next row;
    // "vertical" = fill top-to-bottom then wrap to next column.
    private String gridDirection = "horizontal";
    // Grid layout dimensions. 0 = infinite (for scroll views).
    private int gridCellsAmount = 0; // cells per row (columns)
    private int gridRowsAmount = 0; // rows
    private float gridBeginX = 0f; // grid area start X offset (default 0)
    private float gridBeginY = 0f; // grid area start Y offset (default 0)
    // Original grid box and panel size — stored ONCE on first setGrid call and
    // NEVER touched again. This is the fixed anchor that updateGridScale() uses
    // every frame (via getWidth()/gridOrigPanelW ratio) to recompute the grid
    // area proportionally when the panel is resized (window resize, drag-resize,
    // whatever). Must stay immutable — mutating it elsewhere (e.g. from window
    // resize handling) breaks the ratio math and causes compounding drift across
    // repeated resizes.
    private float gridOrigBoxX1, gridOrigBoxY1, gridOrigBoxX2, gridOrigBoxY2;
    private float gridOrigPanelW = 0f, gridOrigPanelH = 0f;
    // Original (unscaled) explicit cell size, captured once alongside the box/panel
    // anchor above. gridScaledCellSize is the live, per-frame recomputed value
    // (orig * current panel scale) that rendering/snapping actually uses — this is
    // what makes cells grow/shrink proportionally with the panel instead of staying
    // pinned to a fixed pixel size across window resizes.
    private float gridOrigCellSize = 0f;
    private float gridScaledCellSize = -1f;

    // Local-coordinate boxes of "solid" (stick=false) render calls made
    // during THIS frame's beginPanel/beginScrollView...endPanel scope (items,
    // rects, textures — anything drawn via MenuRenderHelper, not a tracked
    // element). Cleared at the start of each beginPanel/beginScrollView call
    // and repopulated as the procedure's render calls execute. Used together
    // with tracked solid children (buttons/sliders/sub-panels) to compute the
    // resize-blocking collision boundary for this panel.
    private final List<float[]> solidRenderBoxes = new ArrayList<>();

    // Set when decorations (borders, resize handles) are rendered early in
    // beginPanel so procedure content (items, rects) appears on top of them.
    private boolean decorationsRendered = false;

    // Last rendered item reference (for deferred rendering of render_item elements).
    // Stored during the procedure phase by MenuRenderHelper.renderItem(), then
    // used by render() during the renderElements phase to render the item icon
    // in proper z-order (elementOrder).
    private String lastItemRef = null;

    // Last rendered texture path (for deferred rendering of render_texture elements).
    private String lastTexturePath = null;
    private int lastRectColor = -1;  // Color for deferred rendering of render_rect/render_rect_outline elements.
    private MenuObject menuObject = null;  // for "menu_object" tracked elements
    private String menuObjectPivot = "top-left";

    public VirtualGuiElement(String id, String type, float x, float y, float width, float height, int color) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.originalWidth = width;
        this.originalHeight = height;
        this.color = color;
    }

    // ── Absolute position (parenting) ──────────────────────────────────────────

    public float getAbsoluteX(VirtualGui gui) {
        if (parentId != null) {
            VirtualGuiElement parent = gui.getElement(parentId);
            if (parent != null) {
                // stick=true: subtract parent's resizeDelta so child stays pinned
                // at its screen position during left/top resize.
                // stick=false: child moves WITH the parent (pushed inward to stay
                // inside the parent's bounds by the resize handler).
                if (shouldStick) {
                    return parent.getAbsoluteX(gui) + x - parent.getResizeDeltaX();
                } else {
                    return parent.getAbsoluteX(gui) + x;
                }
            }
        }
        return x;
    }

    public float getAbsoluteY(VirtualGui gui) {
        if (parentId != null) {
            VirtualGuiElement parent = gui.getElement(parentId);
            if (parent != null) {
                float result;
                if (shouldStick) {
                    result = parent.getAbsoluteY(gui) + y - parent.getResizeDeltaY();
                } else {
                    result = parent.getAbsoluteY(gui) + y;
                }
                if (parent.isScrollView()) {
                    result -= parent.getEffectiveScrollShift();
                }
                return result;
            }
        }
        return y;
    }

    /**
     * Integer render position — computed recursively from the parent's integer
     * position. This ensures children always move in lockstep with the parent
     * (no 1-pixel jiggle during drag) because the child's integer offset from
     * the parent is constant regardless of the parent's fractional position.
     */
    public int getRenderX(VirtualGui gui) {
        VirtualGuiElement parent = parentId != null ? gui.getElement(parentId) : null;
        if (parent != null) {
            // Scaled content: children stay in design space, scale at render time.
            // Use effective scale to support cascading (grandchild of scaled panel).
            if (parent.isInScaledContent(gui)) {
                return parent.getRenderX(gui) + Math.round(x * parent.getEffectiveContentScaleX(gui));
            }
            // During parent resize, the parent's render position and the child's
            // local position both shift by fractional amounts. Rounding them
            // separately (parent.renderX + round(x)) can drift by 1px from frame
            // to frame = JITTER. Using absolute rounding (round the complete
            // absolute position once) eliminates the drift for BOTH stick=true
            // and stick=false. At all other times (drag, idle) we use relative
            // rounding to keep spacing between nested siblings stable.
            if (VirtualGui.isElementBeingResized(parent.id)) {
                return Math.round(getAbsoluteX(gui));
            }
            if (shouldStick) {
                return parent.getRenderX(gui) + Math.round(x - parent.getResizeDeltaX());
            } else {
                return parent.getRenderX(gui) + Math.round(x);
            }
        }
        return Math.round(x);
    }

    public int getRenderY(VirtualGui gui) {
        VirtualGuiElement parent = parentId != null ? gui.getElement(parentId) : null;
        if (parent != null) {
            // Scaled content: children stay in design space, scale at render time.
            // Use effective scale to support cascading (grandchild of scaled panel).
            if (parent.isInScaledContent(gui)) {
                int result = parent.getRenderY(gui) + Math.round(y * parent.getEffectiveContentScaleY(gui));
                if (parent.isScrollView()) {
                    result -= Math.round(parent.getEffectiveScrollShift());
                }
                return result;
            }
            if (VirtualGui.isElementBeingResized(parent.id)) {
                return Math.round(getAbsoluteY(gui));
            }
            int result;
            if (shouldStick) {
                result = parent.getRenderY(gui) + Math.round(y - parent.getResizeDeltaY());
            } else {
                result = parent.getRenderY(gui) + Math.round(y);
            }
            // Apply scroll view offset — children shift up when the scroll view is scrolled
            if (parent.isScrollView()) {
                result -= Math.round(parent.getEffectiveScrollShift());
            }
            return result;
        }
        return Math.round(y);
    }

    /**
     * Integer render width — computed as the difference between the rounded
     * right edge and the rounded left edge. This ensures the rendered width
     * is always consistent with the rendered position (no 1px gap or overlap
     * between the background fill and the decoration border).
     */
    public int getRenderWidth(VirtualGui gui) {
        VirtualGuiElement parent = parentId != null ? gui.getElement(parentId) : null;
        if (parent != null) {
            int rx = getRenderX(gui);
            if (parent.isInScaledContent(gui)) {
                return Math.max(0, Math.round(getEffectiveWidth(gui) * parent.getEffectiveContentScaleX(gui)));
            }
            if (VirtualGui.isElementBeingResized(parent.id)) {
                // Absolute rounding during parent resize — prevents 1px width jitter
                // for BOTH stick=true and stick=false children.
                int targetRight = Math.round(getAbsoluteX(gui) + getEffectiveWidth(gui));
                return Math.max(0, targetRight - rx);
            }
            float offsetX = shouldStick ? x - parent.getResizeDeltaX() : x;
            int targetRight = parent.getRenderX(gui) + Math.round(offsetX + getEffectiveWidth(gui));
            return Math.max(0, targetRight - rx);
        }
        // ROOT LEVEL — if this element itself is being resized, use edge-to-edge
        // rounding so the opposite edge stays stable (no 1px jitter).
        if (VirtualGui.isElementBeingResized(this.id)) {
            return Math.max(0, Math.round(x + getEffectiveWidth(gui)) - Math.round(x));
        }
        return Math.round(getEffectiveWidth(gui));
    }

    public int getRenderHeight(VirtualGui gui) {
        VirtualGuiElement parent = parentId != null ? gui.getElement(parentId) : null;
        if (parent != null) {
            int ry = getRenderY(gui);
            if (parent.isInScaledContent(gui)) {
                return Math.max(0, Math.round(getEffectiveHeight(gui) * parent.getEffectiveContentScaleY(gui)));
            }
            if (VirtualGui.isElementBeingResized(parent.id)) {
                int targetBottom = Math.round(getAbsoluteY(gui) + getEffectiveHeight(gui));
                return Math.max(0, targetBottom - ry);
            }
            float offsetY = shouldStick ? y - parent.getResizeDeltaY() : y;
            int targetBottom = parent.getRenderY(gui) + Math.round(offsetY + getEffectiveHeight(gui));
            // Apply scroll view offset to bottom edge too — keeps height consistent
            // (both top and bottom shift by the same scroll amount)
            if (parent.isScrollView()) {
                targetBottom -= Math.round(parent.getEffectiveScrollShift());
            }
            return Math.max(0, targetBottom - ry);
        }
        // ROOT LEVEL — if this element itself is being resized, use edge-to-edge
        // rounding so the opposite edge stays stable (no 1px jitter).
        if (VirtualGui.isElementBeingResized(this.id)) {
            return Math.max(0, Math.round(y + getEffectiveHeight(gui)) - Math.round(y));
        }
        return Math.round(getEffectiveHeight(gui));
    }

    // ── Scale system (proportional resize of children) ──────────────────────────

    /**
     * This element's own scale factor = currentSize / originalSize.
     * When a panel is resized, this tells children how much to scale.
     */
    public float getScaleX() { return originalWidth > 0 ? width / originalWidth : 1f; }
    public float getScaleY() { return originalHeight > 0 ? height / originalHeight : 1f; }

    /**
     * Compound scale inherited from all ancestors (not including self).
     * A button 3 levels deep in resized panels gets all ancestor scales multiplied.
     */
    // Children never scale in size when a parent panel is resized — both
    // stick=true (decorative) and stick=false (solid) keep their exact
    // original size. Only the panel/scroll-view containers themselves grow
    // or shrink; their contents stay fixed and are simply clipped/scissored.
    public float getInheritedScaleX(VirtualGui gui) { return 1f; }
    public float getInheritedScaleY(VirtualGui gui) { return 1f; }

    /** Effective render width = own width × all ancestor scale factors. */
    public float getEffectiveWidth(VirtualGui gui) { return width * getInheritedScaleX(gui); }
    /** Effective render height = own height × all ancestor scale factors. */
    public float getEffectiveHeight(VirtualGui gui) { return height * getInheritedScaleY(gui); }

    /**
     * Check if this element is fully outside an ancestor's bounds and should be culled.
     * Walks up the parent chain. For scroll views, checks the viewport.
     * For regular panels, checks the panel bounds — children that are fully
     * outside a resized panel are hidden (like a Windows folder clipping contents).
     */
    public boolean isCulled(VirtualGui gui) {
        String pid = parentId;
        while (pid != null) {
            VirtualGuiElement ancestor = gui.getElement(pid);
            if (ancestor == null) break;
            float ew = getEffectiveWidth(gui);
            float eh = getEffectiveHeight(gui);
            if (ancestor.isScrollView()) {
                float ay = getAbsoluteY(gui);
                float ax = getAbsoluteX(gui);
                float vpY1 = ancestor.getAbsoluteY(gui);
                float vpY2 = vpY1 + ancestor.getEffectiveHeight(gui);
                float vpX1 = ancestor.getAbsoluteX(gui);
                float vpX2 = vpX1 + ancestor.getEffectiveWidth(gui);
                // Cull if fully outside viewport (no overlap at all)
                if (ay + eh <= vpY1 || ay >= vpY2) return true;
                if (ax + ew <= vpX1 || ax >= vpX2) return true;
                break;
            } else if ("panel".equals(ancestor.getType())) {
                // Regular panel — cull if fully outside panel bounds
                float ax = getAbsoluteX(gui);
                float ay = getAbsoluteY(gui);
                float px = ancestor.getAbsoluteX(gui);
                float py = ancestor.getAbsoluteY(gui);
                if (ax + ew <= px || ax >= px + ancestor.getEffectiveWidth(gui)) return true;
                if (ay + eh <= py || ay >= py + ancestor.getEffectiveHeight(gui)) return true;
                // Don't break — keep checking higher ancestors too
            }
            pid = ancestor.getParentId();
        }
        return false;
    }

    // ── Hit testing ─────────────────────────────────────────────────────────────

    public boolean isHovered(VirtualGui gui, float mx, float my) {
        if (isCulled(gui)) return false;
        int ix = getRenderX(gui);
        int iy = getRenderY(gui);
        int iw = getRenderWidth(gui);
        int ih = getRenderHeight(gui);
        return mx >= ix && mx <= ix + iw && my >= iy && my <= iy + ih;
    }

    public boolean isInDragZone(VirtualGui gui, float mx, float my) {
        if (!movable || !visible || isCulled(gui)) return false;
        // Check drag zone check IDs first
        if (dragZoneCheckIds != null && dragZoneCheckIds.length > 0) {
            for (String checkId : dragZoneCheckIds) {
                if (!VirtualGui.getCheckResult(checkId)) return false;
            }
        }
        int ix = getRenderX(gui);
        int iy = getRenderY(gui);
        if (hasDragZone) {
            // Drag zone is in local coordinates, not scaled
            return mx >= ix + dragZoneX1 && mx <= ix + dragZoneX2
                && my >= iy + dragZoneY1 && my <= iy + dragZoneY2;
        }
        // No drag zone defined → entire panel is draggable (but not on children)
        return isHitDirect(gui, mx, my);
    }

    public boolean isHitDirect(VirtualGui gui, float mx, float my) {
        if (!visible || !isHovered(gui, mx, my)) return false;
        for (String childId : gui.getAllElementIds()) {
            VirtualGuiElement child = gui.getElement(childId);
            if (child != null && child.getParentId() != null
                    && child.getParentId().equals(this.id)
                    && child.isVisible()
                    && child.isHovered(gui, mx, my)) {
                return false;
            }
        }
        return true;
    }

    // ── Button interaction ──────────────────────────────────────────────────────

    public boolean canInteract() {
        if (checkIds == null || checkIds.length == 0) return true;
        for (String checkId : checkIds) {
            if (!VirtualGui.getCheckResult(checkId)) return false;
        }
        return true;
    }

    public boolean canButtonInteract() {
        if (buttonCheckIds == null || buttonCheckIds.length == 0) return true;
        for (String checkId : buttonCheckIds) {
            if (!VirtualGui.getCheckResult(checkId)) return false;
        }
        return true;
    }

    public boolean canResizeInteract() {
        if (resizeCheckIds == null || resizeCheckIds.length == 0) return true;
        for (String checkId : resizeCheckIds) {
            if (!VirtualGui.getCheckResult(checkId)) return false;
        }
        return true;
    }

    /**
     * Check which resize edge(s) the mouse is on.
     * Returns a bitmask of RESIZE_LEFT/RIGHT/TOP/BOTTOM, or RESIZE_NONE.
     * Only active when the element is resizable, visible, not culled,
     * and all resize checks pass.
     */
    public int getResizeEdge(VirtualGui gui, float mx, float my) {
        if (!resizable || !visible || isCulled(gui) || !canResizeInteract()) return RESIZE_NONE;
        if (!isHovered(gui, mx, my)) return RESIZE_NONE;

        int ix = getRenderX(gui);
        int iy = getRenderY(gui);
        int iw = getRenderWidth(gui);
        int ih = getRenderHeight(gui);
        float bw = resizeBorderWidth;

        int dir = RESIZE_NONE;
        float zoneX = Math.min(bw, iw / 2f);
        float zoneY = Math.min(bw, ih / 2f);
        if (mx <= ix + zoneX) dir |= RESIZE_LEFT;
        if (mx >= ix + iw - zoneX) dir |= RESIZE_RIGHT;
        if (my <= iy + zoneY) dir |= RESIZE_TOP;
        if (my >= iy + ih - zoneY) dir |= RESIZE_BOTTOM;

        // Corner-only mode: only return non-NONE when BOTH a horizontal AND
        // vertical edge are hit (i.e., the mouse is at a corner).
        if (cornerResizeOnly) {
            boolean hasH = (dir & (RESIZE_LEFT | RESIZE_RIGHT)) != 0;
            boolean hasV = (dir & (RESIZE_TOP | RESIZE_BOTTOM)) != 0;
            if (!hasH || !hasV) return RESIZE_NONE;
        }
        return dir;
    }

    // ── Bounds clamping ──────────────────────────────────────────────────────────

    public float clampAbsX(VirtualGui gui, float absX) {
        float ew = getEffectiveWidth(gui);
        // Screen bounds (always apply — panels can't leave the screen)
        float sw = VirtualGui.getScreenWidth();
        if (sw > 0) {
            float screenMax = sw - ew;
            if (screenMax < 0) screenMax = 0;
            absX = Math.max(0, Math.min(absX, screenMax));
        }
        // Parent panel bounds (can't be dragged outside the parent)
        String pid = getParentId();
        if (pid != null) {
            VirtualGuiElement parent = gui.getElement(pid);
            if (parent != null) {
                float pX = parent.getAbsoluteX(gui);
                float pW = parent.getEffectiveWidth(gui);
                absX = Math.max(pX, Math.min(absX, pX + pW - ew));
            }
        }
        // Explicit bounds (optional, further restricts)
        if (hasBounds) {
            float min = boundsX1;
            float max = boundsX2 - ew;
            if (max < min) max = min;
            absX = Math.max(min, Math.min(absX, max));
        }
        // Drag bounds (optional, tightest constraint — e.g. exclude taskbar area)
        if (hasDragBounds) {
            float min = dragBoundsX1;
            float max = dragBoundsX2 - ew;
            if (max < min) max = min;
            absX = Math.max(min, Math.min(absX, max));
        }
        return absX;
    }

    public float clampAbsY(VirtualGui gui, float absY) {
        float eh = getEffectiveHeight(gui);
        // Screen bounds (always apply — panels can't leave the screen)
        float sh = VirtualGui.getScreenHeight();
        if (sh > 0) {
            float screenMax = sh - eh;
            if (screenMax < 0) screenMax = 0;
            absY = Math.max(0, Math.min(absY, screenMax));
        }
        // Parent panel bounds (can't be dragged outside the parent)
        String pid = getParentId();
        if (pid != null) {
            VirtualGuiElement parent = gui.getElement(pid);
            if (parent != null) {
                float pY = parent.getAbsoluteY(gui);
                float pH = parent.getEffectiveHeight(gui);
                absY = Math.max(pY, Math.min(absY, pY + pH - eh));
            }
        }
        // Explicit bounds (optional, further restricts)
        if (hasBounds) {
            float min = boundsY1;
            float max = boundsY2 - eh;
            if (max < min) max = min;
            absY = Math.max(min, Math.min(absY, max));
        }
        // Drag bounds (optional, tightest constraint — e.g. exclude taskbar area)
        if (hasDragBounds) {
            float min = dragBoundsY1;
            float max = dragBoundsY2 - eh;
            if (max < min) max = min;
            absY = Math.max(min, Math.min(absY, max));
        }
        return absY;
    }

    // ── Rendering ───────────────────────────────────────────────────────────────

    // Set to true when background is rendered early in beginPanel (during the
    // procedure's render phase), so render() skips drawing it again (which would
    // cover procedure-drawn content like header bars and items).
    private boolean backgroundRendered = false;

    // Pending immediate fills (renderRect calls inside this panel but without
    // a beginRenderElement scope). Stored during the procedure phase and
    // rendered during render() so they respect elementOrder z-ordering
    // instead of being drawn immediately (which would put them under deferred
    // content from panels that render earlier in elementOrder).
    private final java.util.List<int[]> pendingImmediateFills = new java.util.ArrayList<>();

    /**
     * Renders just the panel background immediately during the procedure's
     * render phase (called from beginPanel). This ensures procedure render
     * calls (renderRect, renderItem, etc.) appear ON TOP of the panel
     * background, not buried underneath it.
     */
    public void renderBackgroundOnly(GuiGraphics gui, VirtualGui virtualGui) {
        if (!visible) return;
        int ix = getRenderX(virtualGui);
        int iy = getRenderY(virtualGui);
        int iw = getRenderWidth(virtualGui);
        int ih = getRenderHeight(virtualGui);
        if (panelType != null) {
            // Pass integer render positions so the background aligns exactly
            // with the decoration border (both use getRenderX/Y + getRenderWidth/Height).
            panelType.render(gui, ix, iy, iw, ih);
        } else if ("rectangle".equals(type) || "panel".equals(type)) {
            gui.fill(ix, iy, ix + iw, iy + ih, color);
        }
        backgroundRendered = true;
    }

    /** Reset per-frame (called from beginMenu). */
    public void resetBackgroundRendered() { backgroundRendered = false; }

    /**
     * Renders just the panel decorations (movable border, resize handles)
     * immediately during the procedure's render phase (called from beginPanel).
     * This puts decorations UNDER the procedure content (items, rects, text),
     * so drag/resize highlights don't cover user-drawn content.
     */
    public void renderDecorations(GuiGraphics gui, VirtualGui virtualGui, float mouseX, float mouseY) {
        if (!visible) return;

        int ix = getRenderX(virtualGui);
        int iy = getRenderY(virtualGui);
        int iw = getRenderWidth(virtualGui);
        int ih = getRenderHeight(virtualGui);

        boolean beingDragged = VirtualGui.isElementBeingDragged(id);
        boolean beingResized = VirtualGui.isElementBeingResized(id);

        // Panels always show decorations. Render elements (items, textures, rects)
        // only show decorations when the mouse is hovering over them, or while
        // actively being dragged/resized.
        boolean isPanel = "panel".equals(type) || "rectangle".equals(type) || "scroll_view".equals(type);
        boolean anyInteracting = VirtualGui.isAnyElementBeingDragged() || VirtualGui.isAnyElementBeingResized();
        // Only show hover decorations when this element (or one of its
        // ancestors) is the topmost element at the mouse position —
        // prevents highlight borders from showing through panels that
        // are rendered on top of this one.
        boolean mouseOver = false;
        if (!anyInteracting && isHovered(virtualGui, mouseX, mouseY)) {
            VirtualGuiElement topmost = VirtualGui.getTopmostElementAt(mouseX, mouseY);
            mouseOver = VirtualGui.ownsPoint(this, topmost);
        }
        boolean showDecorations = isPanel || beingDragged || beingResized || mouseOver;

        if (!showDecorations) return;

        if (movable && !beingResized) {
            int dragColor = beingDragged ? (dragHighlightColor != Integer.MIN_VALUE ? dragHighlightColor : 0xFF6B9FFF) : 0xFF444444;
            if (!dragHighlight && !beingDragged) dragColor = 0xFF444444;
            if (!dragHighlight && beingDragged) dragColor = 0xFF444444;
            gui.fill(ix, iy, ix + iw, iy + 1, dragColor);
            gui.fill(ix, iy + ih - 1, ix + iw, iy + ih, dragColor);
            gui.fill(ix, iy, ix + 1, iy + ih, dragColor);
            gui.fill(ix + iw - 1, iy, ix + iw, iy + ih, dragColor);
        }

        if (resizable && !beingDragged) {
            int edge = beingResized ? VirtualGui.getResizeDirection() : getResizeEdge(virtualGui, mouseX, mouseY);
            if (edge != RESIZE_NONE) {
                if (!resizeHighlight && !beingResized) {
                    // Highlight disabled — only show hover handles, no active highlight
                    int handleColor = 0xFF88AA88;
                    if ((edge & RESIZE_LEFT) != 0) gui.fill(ix, iy, ix + 2, iy + ih, handleColor);
                    if ((edge & RESIZE_RIGHT) != 0) gui.fill(ix + iw - 2, iy, ix + iw, iy + ih, handleColor);
                    if ((edge & RESIZE_TOP) != 0) gui.fill(ix, iy, ix + iw, iy + 2, handleColor);
                    if ((edge & RESIZE_BOTTOM) != 0) gui.fill(ix, iy + ih - 2, ix + iw, iy + ih, handleColor);
                } else if (!resizeHighlight && beingResized) {
                    // Highlight disabled during active resize — show nothing
                } else {
                    int handleColor = beingResized ? (resizeHighlightColor != Integer.MIN_VALUE ? resizeHighlightColor : 0xFF4AFF4A) : 0xFF88AA88;
                    if ((edge & RESIZE_LEFT) != 0) gui.fill(ix, iy, ix + 2, iy + ih, handleColor);
                    if ((edge & RESIZE_RIGHT) != 0) gui.fill(ix + iw - 2, iy, ix + iw, iy + ih, handleColor);
                    if ((edge & RESIZE_TOP) != 0) gui.fill(ix, iy, ix + iw, iy + 2, handleColor);
                    if ((edge & RESIZE_BOTTOM) != 0) gui.fill(ix, iy + ih - 2, ix + iw, iy + ih, handleColor);
                }
            }
        }

        // Render grid lines (internal cell boundaries only — no outer border).
        // Scissored to this element's own bounds (ix,iy,iw,ih) so infinite-grid
        // lines computed out to the parent's full extent don't bleed outside it.
        if (hasGrid && gridRender) {
            int absX = getRenderX(virtualGui);
            int absY = getRenderY(virtualGui);
            int cellWPx = Math.round(getEffectiveCellWidth(virtualGui));
            int cellHPx = Math.round(getEffectiveCellHeight(virtualGui));
            if (cellWPx > 0 && cellHPx > 0) {
                int gx1 = absX + Math.round(gridX1 + gridBeginX);
                int gx2 = absX + Math.round(gridX2);
                int c = gridRenderColor;

                // Scroll views: grid lines must scroll with the content, not
                // stay fixed to the viewport. Shift Y positions by the scroll
                // offset so the grid moves in sync with the children.
                int scrollShift = isScrollView() ? Math.round(getEffectiveScrollShift()) : 0;
                int gy1 = absY + Math.round(gridY1 + gridBeginY) - scrollShift;
                int gy2 = absY + Math.round(gridY2) - scrollShift;

                gui.enableScissor(ix, iy, ix + iw, iy + ih);

                // Vertical lines (between cells, not at edges) — for horizontal & both
                if ("horizontal".equals(gridOrientation) || "both".equals(gridOrientation)) {
                    int maxLines = gridCellsAmount > 0 ? gridCellsAmount : (gx2 - gx1) / cellWPx + 1;
                    for (int i = 1; i < maxLines; i++) {
                        int x = gx1 + i * cellWPx;
                        if (x >= gx2) break;
                        gui.fill(x, gy1, x + 1, gy2, c);
                    }
                }
                // Horizontal lines (between cells, not at edges) — for vertical & both
                if ("vertical".equals(gridOrientation) || "both".equals(gridOrientation)) {
                    // For scroll views with infinite rows, extend grid lines to
                    // cover the full visible viewport (content may be taller than
                    // the original grid Box). Start from first visible line.
                    if (isScrollView() && gridRowsAmount <= 0) {
                        // Find first visible horizontal line within the viewport
                        int firstRow = Math.max(1, (iy - gy1) / cellHPx);
                        int lastRow = (iy + ih - gy1) / cellHPx + 1;
                        for (int i = firstRow; i <= lastRow; i++) {
                            int y = gy1 + i * cellHPx;
                            if (y <= iy || y >= iy + ih) continue;
                            gui.fill(gx1, y, gx2, y + 1, c);
                        }
                    } else {
                        int maxLines = gridRowsAmount > 0 ? gridRowsAmount : (gy2 - gy1) / cellHPx + 1;
                        for (int i = 1; i < maxLines; i++) {
                            int y = gy1 + i * cellHPx;
                            if (y >= gy2) break;
                            gui.fill(gx1, y, gx2, y + 1, c);
                        }
                    }
                }

                gui.disableScissor();
            }
        }

        decorationsRendered = true;
    }

    /** Reset per-frame (called from beginMenu). */
    public void resetDecorationsRendered() { decorationsRendered = false; }

    /** Stores a deferred fill to render during render() (z-order correct). */
    public void addPendingFill(int localX1, int localY1, int localX2, int localY2, int color) {
        pendingImmediateFills.add(new int[]{localX1, localY1, localX2, localY2, color});
    }

    /** Clears pending fills (called at the start of each frame in beginPanel). */
    public void clearPendingFills() { pendingImmediateFills.clear(); }

    public boolean shouldStick() { return shouldStick; }
    public void setShouldStick(boolean s) { this.shouldStick = s; }

    public boolean hasCollision() { return collision; }
    public void setCollision(boolean c) { this.collision = c; }

    public boolean getScaledContent() { return scaledContent; }
    public void setScaledContent(boolean s) { this.scaledContent = s; }
    public float getDesignWidth() { return designWidth; }
    public float getDesignHeight() { return designHeight; }
    public void setDesignSize(float w, float h) { this.designWidth = w; this.designHeight = h; }

    /** Content scale factor = actual size / design size. Returns 1.0 if not scaled. */
    public float getContentScaleX() { return scaledContent && designWidth > 0 ? width / designWidth : 1.0f; }
    public float getContentScaleY() { return scaledContent && designHeight > 0 ? height / designHeight : 1.0f; }

    /**
     * Returns the content scale of the nearest ancestor (including self) that
     * has scaledContent enabled. This allows content scaling to cascade to
     * grandchildren: if snake_game has scaledContent=true (scale 2.0) but
     * gameover (its child) has scaledContent=false, a button inside gameover
     * still needs to be scaled by 2.0.
     */
    public float getEffectiveContentScaleX(VirtualGui gui) {
        if (scaledContent && designWidth > 0) return width / designWidth;
        VirtualGuiElement parent = parentId != null ? gui.getElement(parentId) : null;
        if (parent != null) return parent.getEffectiveContentScaleX(gui);
        return 1.0f;
    }

    public float getEffectiveContentScaleY(VirtualGui gui) {
        if (scaledContent && designHeight > 0) return height / designHeight;
        VirtualGuiElement parent = parentId != null ? gui.getElement(parentId) : null;
        if (parent != null) return parent.getEffectiveContentScaleY(gui);
        return 1.0f;
    }

    /**
     * Returns true if this element or any ancestor has scaledContent enabled.
     */
    public boolean isInScaledContent(VirtualGui gui) {
        if (scaledContent) return true;
        VirtualGuiElement parent = parentId != null ? gui.getElement(parentId) : null;
        if (parent != null) return parent.isInScaledContent(gui);
        return false;
    }

    public boolean canDragOut() { return canDragOut; }
    public void setCanDragOut(boolean d) { this.canDragOut = d; }

    public boolean isDropTarget() { return isDropTarget; }
    public void setDropTarget(boolean d) { this.isDropTarget = d; }
    public String getDropActionId() { return dropActionId; }
    public void setDropActionId(String id) { this.dropActionId = id; }

    /** Action ID fired when this element is double-clicked. */
    public String getDoubleClickActionId() { return doubleClickActionId; }
    public void setDoubleClickActionId(String id) { this.doubleClickActionId = id; }

    /** Action ID fired when this element is right-clicked. */
    public String getRightClickActionId() { return rightClickActionId; }
    public void setRightClickActionId(String id) { this.rightClickActionId = id; }

    public String[] getDropCheckIds() { return dropCheckIds; }
    public void setDropCheckIds(String[] ids) { this.dropCheckIds = ids; }

    public boolean canDragIn() { return canDragIn; }
    public void setCanDragIn(boolean d) { this.canDragIn = d; }

    public boolean isDragHighlight() { return dragHighlight; }
    public void setDragHighlight(boolean h) { this.dragHighlight = h; }
    public int getDragHighlightColor() { return dragHighlightColor; }
    public void setDragHighlightColor(int c) { this.dragHighlightColor = c; }

    public boolean isResizeHighlight() { return resizeHighlight; }
    public void setResizeHighlight(boolean h) { this.resizeHighlight = h; }
    public int getResizeHighlightColor() { return resizeHighlightColor; }
    public void setResizeHighlightColor(int c) { this.resizeHighlightColor = c; }

    public String getHomeParentId() { return homeParentId; }
    public void setHomeParentId(String h) { this.homeParentId = h; }

    public boolean isCornerResizeOnly() { return cornerResizeOnly; }
    public void setCornerResizeOnly(boolean v) { this.cornerResizeOnly = v; }

    public boolean isMaintainAspectRatio() { return maintainAspectRatio; }
    public void setMaintainAspectRatio(boolean v) { this.maintainAspectRatio = v; }

    public float getResizeDeltaX() { return resizeDeltaX; }
    public float getResizeDeltaY() { return resizeDeltaY; }
    public void accumulateResizeDelta(float dx, float dy) {
        this.resizeDeltaX += dx;
        this.resizeDeltaY += dy;
    }
    public void resetResizeDelta() {
        this.resizeDeltaX = 0f;
        this.resizeDeltaY = 0f;
    }

    // ── Grid system ───────────────────────────────────────────────────────────

    // Backwards-compatible overload (no direction/cellsAmount/rowsAmount/beginX/beginY)
    public void setGrid(Box box, String orientation, boolean snapToEnd, float cellSize, boolean hasCellSize,
                          boolean renderGrid, int renderColor) {
        setGrid(box, orientation, "horizontal", 0, 0, 0f, 0f, snapToEnd, cellSize, hasCellSize, renderGrid, renderColor);
    }

    public void setGrid(Box box, String orientation, String direction, int cellsAmount, int rowsAmount,
                          float beginX, float beginY,
                          boolean snapToEnd, float cellSize, boolean hasCellSize,
                          boolean renderGrid, int renderColor) {
        this.hasGrid = true;
        this.gridOrientation = orientation != null ? orientation : "both";
        this.gridDirection = direction != null ? direction : "horizontal";
        this.gridCellsAmount = cellsAmount;
        this.gridRowsAmount = rowsAmount;
        this.gridBeginX = beginX;
        this.gridBeginY = beginY;
        this.gridSnapToEnd = snapToEnd;
        this.gridCellSize = cellSize;
        this.gridHasCellSize = hasCellSize;
        this.gridRender = renderGrid;
        this.gridRenderColor = renderColor != 0 ? renderColor : 0xFF888888;

        // Store original box + panel size on first call (gridOrigPanelW == 0).
        // On subsequent frames, scale the grid area proportionally to the
        // panel's current size vs the original. This makes the grid resize
        // with the panel (like Windows folder list view).
        if (gridOrigPanelW <= 0f) {
            gridOrigBoxX1 = box.x1(); gridOrigBoxY1 = box.y1();
            gridOrigBoxX2 = box.x2(); gridOrigBoxY2 = box.y2();
            gridOrigPanelW = getWidth();
            gridOrigPanelH = getHeight();
            if (gridOrigPanelW <= 0f) gridOrigPanelW = 1f;
            if (gridOrigPanelH <= 0f) gridOrigPanelH = 1f;
            gridOrigCellSize = hasCellSize ? cellSize : 0f;
        }

        updateGridScale();
    }

    /**
     * Recomputes the grid area (and explicit cell size, if any) based on the
     * panel's current size vs the original size captured at grid creation.
     * Runs every frame (called from setGrid, which the procedure re-applies
     * every frame) so it always reflects the panel's current dimensions —
     * including after window resizes and live drag-resizes.
     */
    public void updateGridScale() {
        if (gridOrigPanelW <= 0f) return;
        float scaleX = getWidth() / gridOrigPanelW;
        float scaleY = getHeight() / gridOrigPanelH;
        gridX1 = gridOrigBoxX1 * scaleX;
        gridY1 = gridOrigBoxY1 * scaleY;
        gridX2 = gridOrigBoxX2 * scaleX;
        gridY2 = gridOrigBoxY2 * scaleY;
        // Scale the explicit cell size proportionally too — otherwise cells stay
        // pinned to a fixed pixel size while the grid area around them grows/shrinks,
        // which is exactly the "grid looks different at different window sizes" bug.
        // Use the average of the X/Y scale since cell size is a single scalar
        // (works cleanly for the common case where resize preserves aspect ratio).
        if (gridOrigCellSize > 0f) {
            gridScaledCellSize = gridOrigCellSize * ((scaleX + scaleY) * 0.5f);
        } else {
            gridScaledCellSize = -1f;
        }
    }

    public boolean hasGrid() { return hasGrid; }
    public boolean isExcludeFromGrid() { return excludeFromGrid; }
    public void setExcludeFromGrid(boolean v) { excludeFromGrid = v; }
    public boolean hasDragBounds() { return hasDragBounds; }
    public void setDragBounds(float x1, float y1, float x2, float y2) {
        hasDragBounds = true;
        dragBoundsX1 = x1; dragBoundsY1 = y1;
        dragBoundsX2 = x2; dragBoundsY2 = y2;
    }
    public float getDragBoundsX1() { return dragBoundsX1; }
    public float getDragBoundsY1() { return dragBoundsY1; }
    public float getDragBoundsX2() { return dragBoundsX2; }
    public float getDragBoundsY2() { return dragBoundsY2; }

    public boolean hasResizeBounds() { return hasResizeBounds; }
    public void setResizeBounds(float x1, float y1, float x2, float y2) {
        hasResizeBounds = true;
        resizeBoundsX1 = x1; resizeBoundsY1 = y1;
        resizeBoundsX2 = x2; resizeBoundsY2 = y2;
    }
    public float getResizeBoundsX1() { return resizeBoundsX1; }
    public float getResizeBoundsY1() { return resizeBoundsY1; }
    public float getResizeBoundsX2() { return resizeBoundsX2; }
    public float getResizeBoundsY2() { return resizeBoundsY2; }
    public String getGridOrientation() { return gridOrientation; }
    public boolean isGridSnapToEnd() { return gridSnapToEnd; }
    public float getGridCellSize() { return gridCellSize; }
    public boolean isGridHasCellSize() { return gridHasCellSize; }
    public boolean isGridRender() { return gridRender; }
    public int getGridRenderColor() { return gridRenderColor; }
    public String getGridDirection() { return gridDirection; }
    public int getGridCellsAmount() { return gridCellsAmount; }
    public int getGridRowsAmount() { return gridRowsAmount; }
    public float getGridBeginX() { return gridBeginX; }
    public float getGridBeginY() { return gridBeginY; }

    /**
     * Effective cell width. If cellsAmount > 0, computed from grid area / cellsAmount.
     * If infinite (0), uses explicit cellSize or auto-computes from biggest child width.
     */
    public float getEffectiveCellWidth(VirtualGui gui) {
        if (gridCellsAmount > 0) {
            return (gridX2 - gridX1) / gridCellsAmount;
        }
        if (gridHasCellSize && gridScaledCellSize > 0) return gridScaledCellSize;
        if (gridHasCellSize && gridCellSize > 0) return gridCellSize;
        float maxW = 16f;
        for (VirtualGuiElement child : children) {
            if (!child.isVisible()) continue;
            maxW = Math.max(maxW, child.getEffectiveWidth(gui));
        }
        return maxW;
    }

    /**
     * Effective cell height. If rowsAmount > 0, computed from grid area / rowsAmount.
     * If infinite (0), uses explicit cellSize or auto-computes from biggest child height.
     */
    public float getEffectiveCellHeight(VirtualGui gui) {
        if (gridRowsAmount > 0) {
            return (gridY2 - gridY1) / gridRowsAmount;
        }
        if (gridHasCellSize && gridScaledCellSize > 0) return gridScaledCellSize;
        if (gridHasCellSize && gridCellSize > 0) return gridCellSize;
        float maxH = 16f;
        for (VirtualGuiElement child : children) {
            if (!child.isVisible()) continue;
            maxH = Math.max(maxH, child.getEffectiveHeight(gui));
        }
        return maxH;
    }

    /** Returns the grid area in ABSOLUTE screen coordinates, or null if no grid. */
    public float[] getGridAbsoluteArea(VirtualGui gui) {
        if (!hasGrid) return null;
        float absX = getAbsoluteX(gui);
        float absY = getAbsoluteY(gui);
        return new float[]{absX + gridX1, absY + gridY1, absX + gridX2, absY + gridY2};
    }

    /** Clears grid state (called each frame in beginPanel before re-applying). */
    public void clearGrid() {
        hasGrid = false;
        gridCellSize = -1f;
        gridHasCellSize = false;
        // NOTE: gridOrigBoxX1/Y1/X2/Y2, gridOrigPanelW/H, and gridOrigCellSize are
        // NOT cleared — they persist across frames as the fixed anchor so the grid
        // (area AND cell size) scale survives clearGrid being called every frame
        // in beginPanel. These fields must never be mutated after their initial
        // capture in setGrid() — see updateGridScale().
    }

    /**
     * Computes the effective cell size. If the user set an explicit cellSize, use it.
     * Otherwise, auto-compute from the biggest child element (biggest of width/height
     * across all visible children).
     */
    public float getEffectiveCellSize(VirtualGui gui) {
        if (gridHasCellSize && gridScaledCellSize > 0) return gridScaledCellSize;
        if (gridHasCellSize && gridCellSize > 0) return gridCellSize;
        // Auto-compute from biggest child
        float maxDim = 16f; // fallback
        for (VirtualGuiElement child : children) {
            if (!child.isVisible()) continue;
            float cw = child.getEffectiveWidth(gui);
            float ch = child.getEffectiveHeight(gui);
            maxDim = Math.max(maxDim, Math.max(cw, ch));
        }
        return maxDim;
    }

    /**
     * Snaps a local position to the grid. Returns the snapped local x,y.
     * If snapToEnd is true, snaps to the end of the occupied cells list.
     * If snapToEnd is false, snaps to the closest cell center.
     * Returns null if the object is too big for the cell (rejection signal).
     */
    public float[] snapToGrid(float localX, float localY, float objW, float objH, VirtualGui gui) {
        if (!hasGrid) return new float[]{localX, localY};

        // Round cell dimensions to integers so grid lines, snapping, and
        // centering all use the same pixel boundaries.  This eliminates the
        // 1px left/right (top/bottom) asymmetry that happens when float cell
        // widths produce different rounding at different stages.
        int cellWidth = Math.round(getEffectiveCellWidth(gui));
        int cellHeight = Math.round(getEffectiveCellHeight(gui));

        // Reject if object doesn't fit in the cell
        if (cellWidth > 0 && objW > cellWidth + 0.5f) return null;
        if (cellHeight > 0 && objH > cellHeight + 0.5f) return null;

        float snappedX = localX;
        float snappedY = localY;

        // Round start positions to integer pixels too — grid lines are drawn
        // at integer positions, so objects must snap to the same grid.
        int startX = Math.round(gridX1 + gridBeginX);
        int startY = Math.round(gridY1 + gridBeginY);

        if ("horizontal".equals(gridOrientation) || "both".equals(gridOrientation)) {
            if (cellWidth > 0) {
                float relX = localX - startX;
                int cellIndex = Math.round(relX / cellWidth);
                // Center object in cell — use integer math so the left/right
                // padding is always symmetric (or off by at most 1px, evenly
                // distributed to left & right rather than all to one side).
                int centerOffsetX = Math.max(0, (cellWidth - (int)Math.round(objW)) / 2);
                snappedX = startX + cellIndex * cellWidth + centerOffsetX;
                // Clamp to grid area (skip if infinite columns)
                if (gridCellsAmount > 0) {
                    int gridEnd = Math.round(gridX2);
                    snappedX = Math.max(startX, Math.min(snappedX, gridEnd - objW));
                }
            }
        }

        if ("vertical".equals(gridOrientation) || "both".equals(gridOrientation)) {
            if (cellHeight > 0) {
                float relY = localY - startY;
                int cellIndex = Math.round(relY / cellHeight);
                // Center object in cell — same integer approach for symmetry.
                int centerOffsetY = Math.max(0, (cellHeight - (int)Math.round(objH)) / 2);
                snappedY = startY + cellIndex * cellHeight + centerOffsetY;
                // Clamp to grid area (skip if infinite rows)
                if (gridRowsAmount > 0) {
                    int gridEnd = Math.round(gridY2);
                    snappedY = Math.max(startY, Math.min(snappedY, gridEnd - objH));
                }
            }
        }

        return new float[]{snappedX, snappedY};
    }

    /**
     * Snaps a child element to this panel's grid, accounting for stick=true
     * elements' resizeDelta offset. For stick=true children, the visual
     * position on the panel is (localX - resizeDelta), so we adjust before
     * snapping and restore after. Returns true if the child was snapped,
     * false if no grid or the object was too big (rejection).
     */
    /**
     * Re-snaps ALL visible children to the grid. Called when a child is
     * resized or dragged, so other children re-center if the cell size
     * changed (auto-computed from biggest child).
     */
    public void reSnapAllChildren(VirtualGui gui) {
        if (!hasGrid) return;
        updateGridScale();
        for (VirtualGuiElement child : children) {
            if (!child.isVisible()) continue;
            snapChildToGrid(child, gui, false);
        }
    }

    public boolean snapChildToGrid(VirtualGuiElement child, VirtualGui gui) {
        return snapChildToGrid(child, gui, true);
    }

    public boolean snapChildToGrid(VirtualGuiElement child, VirtualGui gui, boolean checkOccupancy) {
        if (!hasGrid) return false;
        if (child.isExcludeFromGrid()) return false;

        float childX = child.getX();
        float childY = child.getY();

        // For stick=true children, the visual position on the panel is
        // localX - resizeDelta. Adjust for snapping, then restore.
        if (child.shouldStick()) {
            childX -= resizeDeltaX;
            childY -= resizeDeltaY;
        }

        float[] snapped = snapToGrid(childX, childY,
                child.getEffectiveWidth(gui), child.getEffectiveHeight(gui), gui);

        if (snapped == null) return false;

        // Check if the target cell is already occupied by another child.
        // Skip this check during re-snap (resize) — children stay in their
        // current cells, they just re-center.
        if (checkOccupancy) {
            float checkX = child.shouldStick() ? snapped[0] + resizeDeltaX : snapped[0];
            float checkY = child.shouldStick() ? snapped[1] + resizeDeltaY : snapped[1];
            if (isCellOccupied(checkX, checkY, child.getId(), gui)) {
                return false;
            }
        }

        if (child.shouldStick()) {
            child.setX(snapped[0] + resizeDeltaX);
            child.setY(snapped[1] + resizeDeltaY);
        } else {
            child.setX(snapped[0]);
            child.setY(snapped[1]);
        }
        return true;
    }

    public boolean isCellOccupied(float snappedX, float snappedY, String excludeChildId, VirtualGui gui) {
        if (!hasGrid) return false;
        // Use same integer cell dimensions as snapToGrid for consistency.
        int cellW = Math.round(getEffectiveCellWidth(gui));
        int cellH = Math.round(getEffectiveCellHeight(gui));
        if (cellW <= 0 || cellH <= 0) return false;
        int startX = Math.round(gridX1 + gridBeginX);
        int startY = Math.round(gridY1 + gridBeginY);
        for (VirtualGuiElement child : children) {
            if (!child.isVisible()) continue;
            if (child.getId().equals(excludeChildId)) continue;
            float childX = child.getX();
            float childY = child.getY();
            if (child.shouldStick()) {
                childX -= resizeDeltaX;
                childY -= resizeDeltaY;
            }
            int thisCellX = Math.round((snappedX - startX) / cellW);
            int thisCellY = Math.round((snappedY - startY) / cellH);
            int childCellX = Math.round((childX - startX) / cellW);
            int childCellY = Math.round((childY - startY) / cellH);
            if (thisCellX == childCellX && thisCellY == childCellY) {
                return true;
            }
        }
        return false;
    }

    // ── Solid render-call boxes (for resize collision) ───────────────────────

    /** Clears this panel's registered solid render boxes. Called each frame at beginPanel/beginScrollView. */
    public void clearSolidRenderBoxes() { solidRenderBoxes.clear(); }

    /** Registers a solid (stick=false) render call's local box against this panel, for resize collision. */
    public void addSolidRenderBox(float x1, float y1, float x2, float y2) {
        solidRenderBoxes.add(new float[]{x1, y1, x2, y2});
    }

    public List<float[]> getSolidRenderBoxes() { return solidRenderBoxes; }

    /**
     * Computes the union bounding box (in ABSOLUTE screen coordinates) of every
     * solid (stick=false) thing living directly inside this panel — both
     * tracked children (buttons, sliders, sub-panels, scroll views) and
     * ad-hoc solid render calls (items, rects, textures) registered this frame.
     * Returns null if there are no solid children/boxes to constrain against.
     */
    public float[] getSolidUnionBounds(VirtualGui gui) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        boolean any = false;

        // Tracked children (buttons/sliders/sub-panels): their getAbsoluteX/Y
        // already subtracts this panel's resizeDelta, so their positions are
        // correct (they stay pinned during resize).
        for (VirtualGuiElement child : children) {
            if (!child.hasCollision() || !child.isVisible()) continue;
            float ax = child.getAbsoluteX(gui);
            float ay = child.getAbsoluteY(gui);
            float ew = child.getEffectiveWidth(gui);
            float eh = child.getEffectiveHeight(gui);
            minX = Math.min(minX, ax); minY = Math.min(minY, ay);
            maxX = Math.max(maxX, ax + ew); maxY = Math.max(maxY, ay + eh);
            any = true;
        }

        // Solid render-call boxes (items/rects/textures): these are stored in
        // LOCAL coordinates relative to this panel. The panel's getAbsoluteX/Y
        // does NOT subtract its OWN resizeDelta (only the parent's), so we must
        // subtract it manually here — otherwise the collision box drifts with
        // the panel during left/top resize while the actual rendered item stays
        // pinned (delta-compensated), causing the box to misalign and let the
        // panel resize "slightly over" the item on the non-clamped sides.
        float baseX = getAbsoluteX(gui) - getResizeDeltaX();
        float baseY = getAbsoluteY(gui) - getResizeDeltaY();
        for (float[] box : solidRenderBoxes) {
            float bx1 = baseX + box[0], by1 = baseY + box[1];
            float bx2 = baseX + box[2], by2 = baseY + box[3];
            minX = Math.min(minX, bx1); minY = Math.min(minY, by1);
            maxX = Math.max(maxX, bx2); maxY = Math.max(maxY, by2);
            any = true;
        }

        return any ? new float[]{minX, minY, maxX, maxY} : null;
    }

    /**
     * Like getSolidUnionBounds but only includes ad-hoc solid render-call boxes
     * (renderRect/renderTexture/renderItem with collision=true), NOT tracked
     * children. Tracked children with collision=true are pushed by the resize
     * handler instead of blocking it, so they should not be included in the
     * clamping bounds.
     */
    public float[] getRenderBoxUnionBounds(VirtualGui gui) {
        if (solidRenderBoxes.isEmpty()) return null;
        float baseX = getAbsoluteX(gui) - getResizeDeltaX();
        float baseY = getAbsoluteY(gui) - getResizeDeltaY();
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float[] box : solidRenderBoxes) {
            minX = Math.min(minX, baseX + box[0]);
            minY = Math.min(minY, baseY + box[1]);
            maxX = Math.max(maxX, baseX + box[2]);
            maxY = Math.max(maxY, baseY + box[3]);
        }
        return new float[]{minX, minY, maxX, maxY};
    }

    public void render(GuiGraphics gui, VirtualGui virtualGui, int mouseX, int mouseY, float partialTick) {
        if (!visible || isCulled(virtualGui)) return;

        int ix = getRenderX(virtualGui);
        int iy = getRenderY(virtualGui);
        int iw = getRenderWidth(virtualGui);
        int ih = getRenderHeight(virtualGui);

        float hitX=ix,hitY=iy,hitW=iw,hitH=ih;
        VirtualGuiElement clipParent=parentId==null?null:virtualGui.getElement(parentId);
        while(clipParent!=null){if("panel".equals(clipParent.getType())||"scroll_view".equals(clipParent.getType())){float px=clipParent.getRenderX(virtualGui),py=clipParent.getRenderY(virtualGui);float right=Math.min(hitX+hitW,px+clipParent.getRenderWidth(virtualGui)),bottom=Math.min(hitY+hitH,py+clipParent.getRenderHeight(virtualGui));hitX=Math.max(hitX,px);hitY=Math.max(hitY,py);hitW=Math.max(0,right-hitX);hitH=Math.max(0,bottom-hitY);}clipParent=clipParent.getParentId()==null?null:virtualGui.getElement(clipParent.getParentId());}
        StudioRuntime.hit(studioKey, hitX, hitY, canInteract()?hitW:0, hitH, id);
        MenuStyle studioStyle = StudioRuntime.style(studioKey);
        if (studioStyle != null && control == null && !"render_text".equals(type)) studioStyle.background(gui, ix, iy, iw, ih);
        if (control != null) control.render(gui, ix, iy, iw, ih, mouseX, mouseY, partialTick, studioKey);

        // Background is now always rendered here (in renderElements z-order)
        // instead of during beginPanel (procedure phase). This ensures panel
        // backgrounds respect elementOrder — a panel created later in the
        // procedure renders its background AFTER earlier panels' children.
        if (!backgroundRendered && studioStyle == null) {
            if (panelType != null) {
                panelType.render(gui, ix, iy, iw, ih);
            } else if ("rectangle".equals(type) || "panel".equals(type)) {
                gui.fill(ix, iy, ix + iw, iy + ih, color);
            }
        }

        // Render pending immediate fills (renderRect calls made inside this
        // panel without a beginRenderElement scope). These were stored during
        // the procedure phase and are rendered here so they respect elementOrder.
        if (!pendingImmediateFills.isEmpty()) {
            float csx = getContentScaleX();
            float csy = getContentScaleY();
            for (int[] fill : pendingImmediateFills) {
                gui.fill((int)(fill[0] * csx) + ix, (int)(fill[1] * csy) + iy,
                         (int)(fill[2] * csx) + ix, (int)(fill[3] * csy) + iy, fill[4]);
            }
        }

        if ("button".equals(type) && studioStyle == null) {
            boolean hovered = isHovered(virtualGui, mouseX, mouseY);
            int btnColor = color;
            if (active) btnColor = darken(color, 0.7f);
            else if (hovered) btnColor = darken(color, 0.85f);
            gui.fill(ix, iy, ix + iw, iy + ih, btnColor);
            gui.fill(ix, iy, ix + iw, iy + 1, 0xFF333333);
            gui.fill(ix, iy + ih - 1, ix + iw, iy + ih, 0xFF333333);
            gui.fill(ix, iy, ix + 1, iy + ih, 0xFF333333);
            gui.fill(ix + iw - 1, iy, ix + iw, iy + ih, 0xFF333333);
        }

        if ("slider".equals(type)) {
            // Track background
            if (studioStyle == null) gui.fill(ix, iy, ix + iw, iy + ih, 0xFF333333);
            // Filled portion
            float pct = (sliderMax == sliderMin) ? 0f : (sliderValue - sliderMin) / (sliderMax - sliderMin);
            if ("vertical".equals(sliderDirection)) {
                // Fill from top to handle position
                int handleH = 4;
                int handleY = iy + (int) (pct * (ih - handleH));
                int fillH = handleY + handleH - iy; // fill up to and including handle
                gui.fill(ix, iy, ix + iw, iy + Math.min(fillH, ih), studioStyle == null ? color : studioStyle.border());
                // Handle (clamped within track — never sticks out)
                gui.fill(ix, handleY, ix + iw, handleY + handleH, studioStyle == null ? 0xFFCCCCCC : studioStyle.textColor());
            } else {
                int handleW = 4;
                int handleX = ix + (int) (pct * (iw - handleW));
                int fillW = handleX + handleW - ix;
                gui.fill(ix, iy, ix + Math.min(fillW, iw), iy + ih, studioStyle == null ? color : studioStyle.border());
                // Handle (clamped within track)
                gui.fill(handleX, iy, handleX + handleW, iy + ih, studioStyle == null ? 0xFFCCCCCC : studioStyle.textColor());
            }
        }

        // Render item icon for render_item elements (deferred from procedure phase).
        // This is the actual visual rendering — during the procedure, MenuRenderHelper
        // only stored the itemRef. Here in renderElements, we render the icon in
        // proper z-order (elementOrder) so items respect panel z-ordering.
        if ("render_item".equals(type) && lastItemRef != null) {
            ItemStack itemStack = MenuRenderHelper.resolveItemRef(lastItemRef);
            float scaleX = iw / 16f;
            float scaleY = ih / 16f;
            // Disable depth occlusion for item rendering.
            // Panel backgrounds use gui.fill() which doesn't write to the depth buffer.
            // gui.renderItem() enables depth test internally — without this fix, items
            // always pass the depth test and poke through panel backgrounds, ignoring
            // the z-order we set up in renderElements. By forcing depthFunc(ALWAYS) and
            // disabling depth writes, items render purely in call order (elementOrder),
            // just like fills — the panel rendered last (front) stays on top.
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            RenderSystem.depthMask(false);
            if (scaleX == 1f && scaleY == 1f) {
                gui.renderItem(itemStack, ix, iy);
            } else {
                gui.pose().pushPose();
                gui.pose().translate(ix, iy, 0);
                gui.pose().scale(scaleX, scaleY, 1f);
                gui.renderItem(itemStack, 0, 0);
                gui.pose().popPose();
            }
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
        }

        if ("render_text".equals(type) && studioText != null) {
            gui.pose().pushPose();
            try {
                gui.pose().translate(ix,iy,0);gui.pose().scale(width>0?iw/width:1,height>0?ih/height:1,1);
                if (studioStyle != null) studioStyle.text(gui,studioText,0,0,studioTextShadow);
                else MenuText.draw(gui,net.minecraft.network.chat.Component.literal(studioText),0,0,1f,studioTextColor,studioTextShadow,true);
            } finally {gui.pose().popPose();}
        }
        // Render texture for render_texture elements (deferred from procedure phase).
        if ("render_texture".equals(type) && lastTexturePath != null) {
            ResourceLocation texture = ResourceLocation.parse(studioStyle != null && !studioStyle.texture().isEmpty() ? studioStyle.texture() : lastTexturePath);
            gui.blit(texture, ix, iy, 0, 0, iw, ih);
        }

        // Render filled rectangle for render_rect elements (deferred from procedure phase).
        if ("render_rect".equals(type) && lastRectColor != -1 && studioStyle == null) {
            gui.fill(ix, iy, ix + iw, iy + ih, studioStyle == null ? lastRectColor : studioStyle.background());
        }
        // Render rectangle outline for render_rect_outline elements (deferred from procedure phase).
        if ("render_rect_outline".equals(type) && lastRectColor != -1 && studioStyle == null) {
            gui.fill(ix, iy, ix + iw, iy + 1, lastRectColor);
            gui.fill(ix, iy + ih - 1, ix + iw, iy + ih, lastRectColor);
            gui.fill(ix, iy, ix + 1, iy + ih, lastRectColor);
            gui.fill(ix + iw - 1, iy, ix + iw, iy + ih, lastRectColor);
        }

        // Render menu object (composite: rectangles, textures, items, text).
        // Scales proportionally to fit the element's current width/height.
        if ("menu_object".equals(type) && menuObject != null) {
            float origW = menuObject.getOriginalWidth();
            float origH = menuObject.getOriginalHeight();
            float sx = origW > 0 ? iw / origW : 1f;
            float sy = origH > 0 ? ih / origH : 1f;
            if (maintainAspectRatio) {
                float s = Math.min(sx, sy);
                sx = s; sy = s;
            }
            menuObject.renderAt(ix, iy, sx, sy, gui);
        }

        if (studioStyle != null && control == null && !"render_text".equals(type) && studioStyle.part("text").hasText() && !studioStyle.part("text").text().isEmpty()) studioStyle.label(gui, "", ix, iy, iw, ih);

        // Clip children to this panel's bounds — like a Windows folder,
        // children that extend beyond the panel are hidden rather than sticking out.
        // Minecraft's scissor stack handles nesting (regions are intersected).
        boolean shouldClipChildren = ("panel".equals(type) || "scroll_view".equals(type)) && !children.isEmpty();
        if (shouldClipChildren) {
            gui.enableScissor(ix, iy, ix + iw, iy + ih);
        }
        for (VirtualGuiElement child : children) {
            // Skip children being dragged — they are rendered at the top level
            // in VirtualGui.renderElements() so they always appear on top of
            // everything (including sibling panels that would cover them).
            // This prevents the dragged item from being hidden behind a panel
            // that renders after this one in z-order.
            if (VirtualGui.isElementBeingDragged(child.getId())) continue;

            child.render(gui, virtualGui, mouseX, mouseY, partialTick);
        }
        if (shouldClipChildren) {
            gui.disableScissor();
        }

        // Render decorations (drag/resize borders) AFTER children and items so
        // they appear ON TOP of everything — including scissored child items that
        // would otherwise cover the highlight border and break the illusion.
        // Same hover-gating rules as before: panels always show, render elements
        // only show when hovered/active.
        if (!decorationsRendered) {
            boolean beingDragged = VirtualGui.isElementBeingDragged(id);
            boolean beingResized = VirtualGui.isElementBeingResized(id);
            boolean isPanelType = "panel".equals(type) || "rectangle".equals(type) || "scroll_view".equals(type);
            boolean anyInteracting = VirtualGui.isAnyElementBeingDragged() || VirtualGui.isAnyElementBeingResized();
            // Only show hover decorations when this element (or one of its
            // ancestors) is the topmost element at the mouse position —
            // prevents highlight borders from showing through panels that
            // are rendered on top of this one.
            boolean mouseOverEl = false;
            if (!anyInteracting && isHovered(virtualGui, mouseX, mouseY)) {
                VirtualGuiElement topmost = VirtualGui.getTopmostElementAt(mouseX, mouseY);
                mouseOverEl = VirtualGui.ownsPoint(this, topmost);
            }
            boolean showDeco = isPanelType || beingDragged || beingResized || mouseOverEl;

            if (showDeco) {
                if (movable && !beingResized) {
                    int dragColor;
                    if (!dragHighlight) {
                        // Highlight disabled — always show plain gray border
                        dragColor = 0xFF444444;
                    } else if (beingDragged) {
                        dragColor = dragHighlightColor != Integer.MIN_VALUE ? dragHighlightColor : 0xFF6B9FFF;
                    } else {
                        dragColor = 0xFF444444;
                    }
                    gui.fill(ix, iy, ix + iw, iy + 1, dragColor);
                    gui.fill(ix, iy + ih - 1, ix + iw, iy + ih, dragColor);
                    gui.fill(ix, iy, ix + 1, iy + ih, dragColor);
                    gui.fill(ix + iw - 1, iy, ix + iw, iy + ih, dragColor);
                }

                if (resizable && !beingDragged) {
                    int edge = beingResized ? VirtualGui.getResizeDirection() : getResizeEdge(virtualGui, mouseX, mouseY);
                    if (edge != RESIZE_NONE) {
                        if (!resizeHighlight && !beingResized) {
                            // Highlight disabled — show plain hover handles
                            int handleColor = 0xFF88AA88;
                            if ((edge & RESIZE_LEFT) != 0) gui.fill(ix, iy, ix + 2, iy + ih, handleColor);
                            if ((edge & RESIZE_RIGHT) != 0) gui.fill(ix + iw - 2, iy, ix + iw, iy + ih, handleColor);
                            if ((edge & RESIZE_TOP) != 0) gui.fill(ix, iy, ix + iw, iy + 2, handleColor);
                            if ((edge & RESIZE_BOTTOM) != 0) gui.fill(ix, iy + ih - 2, ix + iw, iy + ih, handleColor);
                        } else if (!resizeHighlight && beingResized) {
                            // Highlight disabled during active resize — show nothing
                        } else {
                            int handleColor = beingResized ? (resizeHighlightColor != Integer.MIN_VALUE ? resizeHighlightColor : 0xFF4AFF4A) : 0xFF88AA88;
                            if ((edge & RESIZE_LEFT) != 0) gui.fill(ix, iy, ix + 2, iy + ih, handleColor);
                            if ((edge & RESIZE_RIGHT) != 0) gui.fill(ix + iw - 2, iy, ix + iw, iy + ih, handleColor);
                            if ((edge & RESIZE_TOP) != 0) gui.fill(ix, iy, ix + iw, iy + 2, handleColor);
                            if ((edge & RESIZE_BOTTOM) != 0) gui.fill(ix, iy + ih - 2, ix + iw, iy + ih, handleColor);
                        }
                    }
                }
            }

            // Render grid lines (internal cell boundaries only — no outer border).
            // Scissored to this element's own bounds (ix,iy,iw,ih) — grid lines for
            // infinite grids (cellsAmount/rowsAmount == 0) are computed out to the
            // parent's full width/height and must be clipped, otherwise they bleed
            // outside the panel into whatever renders behind/after it.
            if (hasGrid && gridRender) {
                int cellWPx = Math.round(getEffectiveCellWidth(virtualGui));
                int cellHPx = Math.round(getEffectiveCellHeight(virtualGui));
                if (cellWPx > 0 && cellHPx > 0) {
                    int gx1 = ix + Math.round(gridX1 + gridBeginX);
                    int gy1 = iy + Math.round(gridY1 + gridBeginY);
                    int gx2 = ix + Math.round(gridX2);
                    int gy2 = iy + Math.round(gridY2);
                    int c = gridRenderColor;

                    gui.enableScissor(ix, iy, ix + iw, iy + ih);

                    // Vertical lines (between cells, not at edges)
                    if ("horizontal".equals(gridOrientation) || "both".equals(gridOrientation)) {
                        int maxLines = gridCellsAmount > 0 ? gridCellsAmount : (gx2 - gx1) / cellWPx + 1;
                        for (int i = 1; i < maxLines; i++) {
                            int x = gx1 + i * cellWPx;
                            if (x >= gx2) break;
                            gui.fill(x, gy1, x + 1, gy2, c);
                        }
                    }
                    // Horizontal lines (between cells, not at edges)
                    if ("vertical".equals(gridOrientation) || "both".equals(gridOrientation)) {
                        int maxLines = gridRowsAmount > 0 ? gridRowsAmount : (gy2 - gy1) / cellHPx + 1;
                        for (int i = 1; i < maxLines; i++) {
                            int y = gy1 + i * cellHPx;
                            if (y >= gy2) break;
                            gui.fill(gx1, y, gx2, y + 1, c);
                        }
                    }

                    gui.disableScissor();
                }
            }
        }
    }

    private static int darken(int argb, float factor) {
        int a = (argb >> 24) & 0xFF;
        int r = (int) (((argb >> 16) & 0xFF) * factor);
        int g = (int) (((argb >> 8) & 0xFF) * factor);
        int b = (int) ((argb & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ── Getters / Setters ────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getType() { return type; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }
    public int getColor() { return color; }
    public String getParentId() { return parentId; }
    public boolean isVisible() { return visible; }

    // Frame stamp for book page panels (beginPagePanel): the decl-frame
    // this element was last declared in. Book.end() hides side panels
    // whose page did not render this frame (page flipped away / book
    // closed) so their content stops rendering with the page art.
    private int lastDeclaredFrame = -1;
    public int getLastDeclaredFrame() { return lastDeclaredFrame; }
    public void setLastDeclaredFrame(int f) { this.lastDeclaredFrame = f; }
    public boolean isMovable() { return movable; }
    public PanelType getPanelType() { return panelType; }
    public String getActionId() { return actionId; }
    public String[] getCheckIds() { return checkIds; }
    public boolean isActive() { return active; }
    public float getSliderValue() { return sliderValue; }
    public float getSliderMin() { return sliderMin; }
    public float getSliderMax() { return sliderMax; }
    public String getSliderType() { return sliderType; }
    public String getSliderDirection() { return sliderDirection; }

    public void setX(float x) { this.x = x; }
    public void setY(float y) { this.y = y; }
    public void setWidth(float w) { this.width = w; }
    public void setHeight(float h) { this.height = h; }
    public void setColor(int c) { this.color = c; }
    public void setParentId(String p) { this.parentId = p; }
    public void setVisible(boolean v) { this.visible = v; }
    public void setMovable(boolean m) { this.movable = m; }
    public void setPanelType(PanelType t) { this.panelType = t; }
    public void setActionId(String a) { this.actionId = a; }
    public void setCheckIds(String[] c) { this.checkIds = c; }
    public void setActive(boolean a) { this.active = a; }
    public void setSliderValue(float v) { this.sliderValue = Math.max(sliderMin, Math.min(sliderMax, v)); }
    public void setSliderMin(float m) { this.sliderMin = m; }
    public void setSliderMax(float m) { this.sliderMax = m; }
    public void setSliderType(String t) { this.sliderType = t; }
    public void setSliderDirection(String d) { this.sliderDirection = d; }
    public float getStepSize() { return stepSize; }
    public void setStepSize(float s) { this.stepSize = s; }
    public boolean isReverseDefault() { return reverseDefault; }
    public void setReverseDefault(boolean r) { this.reverseDefault = r; }

    public boolean isScrollView() { return scrollView; }
    public void setScrollView(boolean sv) { this.scrollView = sv; }
    public float getScrollOffsetY() { return scrollOffsetY; }
    public void setScrollOffsetY(float offset) { this.scrollOffsetY = offset; }
    public float getContentHeight() { return contentHeight; }
    public void setContentHeight(float h) { this.contentHeight = h; }
    public boolean isScrollAnchorBottom() { return scrollAnchorBottom; }
    public void setScrollAnchorBottom(boolean b) { this.scrollAnchorBottom = b; }

    /**
     * Computes the actual pixel shift applied to children, accounting for
     * scroll anchor mode. "top" anchor (default): shift = scrollOffsetY directly
     * (offset 0 = top of content visible). "bottom" anchor: shift = maxScroll -
     * scrollOffsetY (offset 0 = bottom of content visible; increasing offset
     * reveals earlier/upper content — like a chat log).
     */
    public float getEffectiveScrollShift() {
        if (!scrollView) return 0f;
        float viewportH = getHeight();
        float maxScroll = Math.max(0f, contentHeight - viewportH);
        if (scrollAnchorBottom) {
            return maxScroll - scrollOffsetY;
        }
        return scrollOffsetY;
    }

    public boolean hasButtonAttribute() { return hasButtonAttribute; }
    public void setHasButtonAttribute(boolean b) { this.hasButtonAttribute = b; }
    public String getButtonActionId() { return buttonActionId; }
    public void setButtonActionId(String a) { this.buttonActionId = a; }
    public String[] getButtonCheckIds() { return buttonCheckIds; }
    public void setButtonCheckIds(String[] c) { this.buttonCheckIds = c; }

    public boolean isResizable() { return resizable; }
    public void setResizable(boolean r) { this.resizable = r; }
    public String[] getResizeCheckIds() { return resizeCheckIds; }
    public void setResizeCheckIds(String[] c) { this.resizeCheckIds = c; }
    public float getResizeBorderWidth() { return resizeBorderWidth; }
    public void setResizeBorderWidth(float w) { this.resizeBorderWidth = w; }

    public float getMinResizeX() { return minResizeX; }
    public void setMinResizeX(float v) { this.minResizeX = v; }
    public float getMinResizeY() { return minResizeY; }
    public void setMinResizeY(float v) { this.minResizeY = v; }
    public float getMaxResizeX() { return maxResizeX; }
    public void setMaxResizeX(float v) { this.maxResizeX = v; }
    public float getMaxResizeY() { return maxResizeY; }
    public void setMaxResizeY(float v) { this.maxResizeY = v; }

    public void setDraggableZone(float x1, float y1, float x2, float y2) {
        this.hasDragZone = true;
        this.dragZoneX1 = x1; this.dragZoneY1 = y1;
        this.dragZoneX2 = x2; this.dragZoneY2 = y2;
    }

    public void setDragZoneCheckIds(String[] ids) { this.dragZoneCheckIds = ids; }

    public void setBounds(float x1, float y1, float x2, float y2) {
        this.hasBounds = true;
        this.boundsX1 = x1; this.boundsY1 = y1;
        this.boundsX2 = x2; this.boundsY2 = y2;
    }

    public void clearBounds() { this.hasBounds = false; }

    public boolean hasBounds() { return hasBounds; }
    public float getBoundsX1() { return boundsX1; }
    public float getBoundsY1() { return boundsY1; }
    public float getBoundsX2() { return boundsX2; }
    public float getBoundsY2() { return boundsY2; }

    void addChild(VirtualGuiElement child) { children.add(child); }
    void removeChild(VirtualGuiElement child) { children.remove(child); }
    List<VirtualGuiElement> getChildren() { return children; }

    public String getLastItemRef() { return lastItemRef; }
    public void setLastItemRef(String ref) { this.lastItemRef = ref; }

    public String getLastTexturePath() { return lastTexturePath; }
    public void setLastTexturePath(String path) { this.lastTexturePath = path; }
    public int getLastRectColor() { return lastRectColor; }
    public void setLastRectColor(int color) { this.lastRectColor = color; }
    public MenuObject getMenuObject() { return menuObject; }
    public void setMenuObject(MenuObject mo) { this.menuObject = mo; }
    public String getMenuObjectPivot() { return menuObjectPivot; }
    public void setMenuObjectPivot(String p) { this.menuObjectPivot = p; }
}


