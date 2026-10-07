package net.tamashi.fomekcore.api.guisystems;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * MenuObject — a reusable composition of visual elements (rectangles, textures,
 * items, text) that can be built once and rendered at any position with any pivot.
 *
 * Each element is identified by a string ID, so properties (color, texture, item,
 * text) can be changed on the fly without rebuilding the object.
 *
 * Elements are rendered in add-order (z-order): the first element added is at
 * the bottom, each new element goes on top.
 *
 * Items use the same depth-disabling trick as VirtualGuiElement.render() so
 * they don't fight z-order with panel backgrounds — they render purely in
 * call order, like fills.
 */
public class MenuObject {

    private static class Element {
        final String id;
        String studioKey = StudioRuntime.key();
        final String type; // "rectangle", "texture", "item", "text"
        float x, y;       // relative to menu object origin (pre-pivot)
        float w, h;       // width/height (rect, texture; 16 for item)
        int color;        // rect, text (ARGB)
        String texturePath; // texture
        String itemRef;     // item (MCreator item reference)
        String text;        // text content
        String pivot;       // text pivot
        boolean shadow;     // text shadow
        float textScale = 1.0f; // text render scale (1.0 = native)
        boolean stick;
        boolean collision;

        Element(String id, String type) {
            this.id = id;
            this.type = type;
        }
    }

    private final List<Element> elements = new ArrayList<>();

    // Optional manual collision box. When set (non-null), overrides the
    // auto-computed bounding box used by getBoundingBox(), getOriginalWidth/Height.
    private Box collisionBox = null;

    public MenuObject() {
    }

    /** Create a MenuObject with a pre-set collision box. */
    public MenuObject(float cboxX1, float cboxY1, float cboxX2, float cboxY2) {
        this.collisionBox = new Box(cboxX1, cboxY1, cboxX2, cboxY2);
    }

    /** Create a MenuObject with a pre-set collision box. */
    public MenuObject(Box collisionBox) {
        this.collisionBox = collisionBox;
    }

    /** Set a manual collision box (x1, y1, x2, y2). Pass null to clear and
     *  revert to auto-computed bounding box from elements. */
    public void setCollisionBox(float x1, float y1, float x2, float y2) {
        this.collisionBox = new Box(x1, y1, x2, y2);
    }

    public void setCollisionBox(Box box) {
        this.collisionBox = box;
    }

    public void clearCollisionBox() {
        this.collisionBox = null;
    }

    public Box getCollisionBox() {
        return collisionBox;
    }

    /**
     * Creates a deep copy of this MenuObject — all elements and collision box
     * are duplicated so the copy can be modified independently.
     */
    public MenuObject copy() {
        MenuObject copy = new MenuObject();
        if (this.collisionBox != null) {
            copy.collisionBox = new Box(this.collisionBox.x1(), this.collisionBox.y1(),
                                        this.collisionBox.x2(), this.collisionBox.y2());
        }
        for (Element el : this.elements) {
            Element copyEl = new Element(el.id, el.type);
            copyEl.studioKey = el.studioKey;
            copyEl.x = el.x;
            copyEl.y = el.y;
            copyEl.w = el.w;
            copyEl.h = el.h;
            copyEl.color = el.color;
            copyEl.texturePath = el.texturePath;
            copyEl.itemRef = el.itemRef;
            copyEl.text = el.text;
            copyEl.pivot = el.pivot;
            copyEl.shadow = el.shadow;
            copyEl.textScale = el.textScale;
            copyEl.stick = el.stick;
            copyEl.collision = el.collision;
            copy.elements.add(copyEl);
        }
        return copy;
    }

    /**
     * When a manual collision box is set, computes the offset to center the
     * auto-computed element bounding box on the center of the collision box.
     * Returns [offsetX, offsetY]. If no collision box is set, returns [0, 0].
     */
    private float[] getCollisionCenterOffset() {
        if (collisionBox == null || elements.isEmpty()) return new float[]{0, 0};

        // Auto-compute element bounding box (the old logic, ignoring collisionBox)
        float aMinX = Float.MAX_VALUE, aMinY = Float.MAX_VALUE;
        float aMaxX = -Float.MAX_VALUE, aMaxY = -Float.MAX_VALUE;
        for (Element el : elements) {
            float[] r = visualRect(el);
            aMinX = Math.min(aMinX, r[0]);
            aMinY = Math.min(aMinY, r[1]);
            aMaxX = Math.max(aMaxX, r[0] + r[2]);
            aMaxY = Math.max(aMaxY, r[1] + r[3]);
        }
        float elemW = aMaxX - aMinX;
        float elemH = aMaxY - aMinY;

        // Center of collision box
        float cboxCenterX = collisionBox.x1() + (collisionBox.x2() - collisionBox.x1()) / 2f;
        float cboxCenterY = collisionBox.y1() + (collisionBox.y2() - collisionBox.y1()) / 2f;

        // Center of element bounding box
        float elemCenterX = aMinX + elemW / 2f;
        float elemCenterY = aMinY + elemH / 2f;

        return new float[]{cboxCenterX - elemCenterX, cboxCenterY - elemCenterY};
    }


    // ── Add elements ──────────────────────────────────────────────────────────

    /** Add a filled rectangle. Box is in local coords relative to the object origin. */
    public void addRectangle(String id, Box box, int color, boolean stick, boolean collision) {
        Element el = new Element(id, "rectangle");
        el.x = box.x1();
        el.y = box.y1();
        el.w = box.x2() - box.x1();
        el.h = box.y2() - box.y1();
        el.color = color;
        el.stick = stick;
        el.collision = collision;
        elements.add(el);
    }

    /** Add a texture element. */
    public void addTexture(String id, String texturePath, float x, float y, float w, float h,
                           boolean stick, boolean collision) {
        Element el = new Element(id, "texture");
        el.x = x;
        el.y = y;
        el.w = w;
        el.h = h;
        el.texturePath = texturePath;
        el.stick = stick;
        el.collision = collision;
        elements.add(el);
    }

    /** Add a render-item element (16x16 icon). */
    public void addItem(String id, String itemRef, float x, float y,
                         boolean stick, boolean collision) {
        Element el = new Element(id, "item");
        el.x = x;
        el.y = y;
        el.w = 16;
        el.h = 16;
        el.itemRef = itemRef;
        el.stick = stick;
        el.collision = collision;
        elements.add(el);
    }

    /** Add a text element. */
    public void addText(String id, String text, float x, float y, int color,
                        String pivot, boolean shadow, boolean stick, boolean collision) {
        addText(id, text, x, y, color, pivot, shadow, 1.0f, stick, collision);
    }

    public void addText(String id, String text, float x, float y, int color,
                        String pivot, boolean shadow, float textScale, boolean stick, boolean collision) {
        Element el = new Element(id, "text");
        el.x = x;
        el.y = y;
        el.color = color;
        el.text = text;
        el.pivot = pivot != null ? pivot : "top-left";
        el.shadow = shadow;
        el.textScale = textScale > 0 ? textScale : 1.0f;
        el.stick = stick;
        el.collision = collision;
        elements.add(el);
    }

    // ── Change element properties by ID ───────────────────────────────────────

    /** Change the item reference of an "item" element. */
    public void changeItem(String id, String newItemRef) {
        for (Element el : elements) {
            if (el.id.equals(id) && "item".equals(el.type)) {
                el.itemRef = newItemRef;
                return;
            }
        }
    }

    /** Change the texture path of a "texture" element. */
    public void changeTexture(String id, String newTexturePath) {
        for (Element el : elements) {
            if (el.id.equals(id) && "texture".equals(el.type)) {
                el.texturePath = newTexturePath;
                return;
            }
        }
    }

    /** Change the text content of a "text" element. */
    public void changeText(String id, String newText) {
        for (Element el : elements) {
            if (el.id.equals(id) && "text".equals(el.type)) {
                el.text = newText;
                return;
            }
        }
    }

    /** Change the color of any element (rectangle or text). */
    public void changeColor(String id, int newColor) {
        for (Element el : elements) {
            if (el.id.equals(id)) {
                el.color = newColor;
                return;
            }
        }
    }

    /** Change the text scale of a "text" element. */
    public void changeTextScale(String id, float newScale) {
        for (Element el : elements) {
            if (el.id.equals(id) && "text".equals(el.type)) {
                el.textScale = newScale > 0 ? newScale : 1.0f;
                return;
            }
        }
    }

    // ── Bounding box (for tracked element creation) ─────────────────────────────

    /**
     * Resolves an element's own pivot (text only — rect/texture/item are always
     * top-left anchored) into a top-left-anchored visual rect: [x, y, w, h].
     *
     * This is the single source of truth for "where does this element actually
     * sit visually" — used identically by the bounding box computation AND by
     * both render paths, so an element's footprint for collision/sizing purposes
     * is always exactly the box it's drawn in (no double-counting the pivot).
     */
    private static float[] visualRect(Element el) {
        float ex = el.x, ey = el.y, ew = el.w, eh = el.h;
        if ("text".equals(el.type)) {
            Font font = Minecraft.getInstance().font;
            float ts = el.textScale > 0 ? el.textScale : 1.0f;
            MenuStyle style = StudioRuntime.style(el.studioKey);
            if(style != null){style=style.part("text");ts = Math.max(.05f,style.size()/9f);}
            ew = (style == null ? font.width(el.text != null ? el.text : "") : font.width(style.component(el.text))) * ts;
            eh = font.lineHeight * ts;
            String p = el.pivot != null ? el.pivot : "top-left";
            switch (p) {
                case "top": case "center": case "bottom":
                    ex -= ew / 2f; break;
                case "top-right": case "right": case "bottom-right":
                    ex -= ew; break;
                default: break;
            }
            switch (p) {
                case "left": case "center": case "right":
                    ey -= eh / 2f; break;
                case "bottom-left": case "bottom": case "bottom-right":
                    ey -= eh; break;
                default: break;
            }
        }
        return new float[]{ex, ey, ew, eh};
    }

    /**
     * Computes the bounding box of all elements — a single "mantle" box that
     * encloses every element's actual visual footprint (each element's own
     * pivot already resolved via visualRect). Returns [minX, minY, maxX, maxY].
     */
    public float[] getBoundingBox() {
        // If a manual collision box is set, use it directly (1:1 with what the user specified)
        if (collisionBox != null) {
            return new float[]{collisionBox.x1(), collisionBox.y1(), collisionBox.x2(), collisionBox.y2()};
        }
        if (elements.isEmpty()) return new float[]{0, 0, 0, 0};

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (Element el : elements) {
            float[] r = visualRect(el);
            minX = Math.min(minX, r[0]);
            minY = Math.min(minY, r[1]);
            maxX = Math.max(maxX, r[0] + r[2]);
            maxY = Math.max(maxY, r[1] + r[3]);
        }
        return new float[]{minX, minY, maxX, maxY};
    }

    /** Original width of the bounding box (before any scaling). */
    public float getOriginalWidth() {
        float[] bb = getBoundingBox();
        return bb[2] - bb[0];
    }

    /** Original height of the bounding box (before any scaling). */
    public float getOriginalHeight() {
        float[] bb = getBoundingBox();
        return bb[3] - bb[1];
    }

    // ── Render ────────────────────────────────────────────────────────────────

    /**
     * Render the menu object at (screenX, screenY) with the given pivot.
     * The pivot offsets the bounding box of all elements so the object
     * is positioned relative to (screenX, screenY) like the text pivot system.
     *
     * Each element is positioned using its resolved visualRect (top-left
     * anchored, own pivot already applied) shifted into the object's bounding
     * box space — so the object's overall pivot centers/aligns the WHOLE
     * mantle box, and no element's position double-applies its own pivot.
     *
     * Uses the current parent render offset (VirtualGui.getCurrentParentRenderX/Y)
     * so it works correctly inside panels and scroll views.
     */
    public void render(float screenX, float screenY, String pivot, GuiGraphicsExtractor gui) {
        if (elements.isEmpty()) return;

        float[] bb = getBoundingBox();
        float minX = bb[0], minY = bb[1], maxX = bb[2], maxY = bb[3];
        float objW = maxX - minX;
        float objH = maxY - minY;

        // When collision box is set, center elements on the collision box center
        float[] centerOff = getCollisionCenterOffset();

        // Apply object-level pivot to compute the origin offset
        float offsetX = 0f, offsetY = 0f;
        if (pivot == null) pivot = "top-left";
        switch (pivot) {
            case "top": case "center": case "bottom":
                offsetX = -objW / 2f; break;
            case "top-right": case "right": case "bottom-right":
                offsetX = -objW; break;
            default: break;
        }
        switch (pivot) {
            case "left": case "center": case "right":
                offsetY = -objH / 2f; break;
            case "bottom-left": case "bottom": case "bottom-right":
                offsetY = -objH; break;
            default: break;
        }

        // Parent render offset (for panels/scroll views)
        int parentOffX = VirtualGui.getCurrentParentRenderX();
        int parentOffY = VirtualGui.getCurrentParentRenderY();

        // Absolute origin
        int originX = (int) (screenX + offsetX) + parentOffX;
        int originY = (int) (screenY + offsetY) + parentOffY;

        // Render each element in z-order (add order)
        for (Element el : elements) {
            float[] r = visualRect(el);
            int elX = (int) (originX + r[0] - minX + centerOff[0]);
            int elY = (int) (originY + r[1] - minY + centerOff[1]);
            int elW = (int) r[2];
            int elH = (int) r[3];

            StudioRuntime.hit(el.studioKey, elX, elY, elW, elH, el.id);
            MenuStyle style = StudioRuntime.style(el.studioKey);
            if (style != null && !el.type.equals("text")) style.background(gui, elX, elY, elW, elH);
            switch (el.type) {
                case "rectangle":
                    if (style == null) gui.fill(elX, elY, elX + elW, elY + elH, el.color);
                    break;

                case "texture":
                    if (el.texturePath != null && !el.texturePath.isEmpty()) {
                        Identifier texture = Identifier.parse(style != null && !style.texture().isEmpty() ? style.texture() : el.texturePath);
                        gui.blit(texture, elX, elY, 0, 0, elW, elH);
                    }
                    break;

                case "item":
                    if (el.itemRef != null && !el.itemRef.isEmpty()) {
                        ItemStack itemStack = MenuRenderHelper.resolveItemRef(el.itemRef);
                        // Disable depth occlusion — same as VirtualGuiElement.render()
                        // so items render purely in call order (z-order) and don't
                        // poke through panel backgrounds rendered before this object.
                        RenderSystem.depthFunc(GL11.GL_ALWAYS);
                        RenderSystem.depthMask(false);
                        float sx = elW / 16f;
                        float sy = elH / 16f;
                        if (sx == 1f && sy == 1f) {
                            gui.renderItem(itemStack, elX, elY);
                        } else {
                            gui.pose().pushPose();
                            gui.pose().translate(elX, elY, 0);
                            gui.pose().scale(sx, sy, 1f);
                            gui.renderItem(itemStack, 0, 0);
                            gui.pose().popPose();
                        }
                        RenderSystem.depthFunc(GL11.GL_LEQUAL);
                        RenderSystem.depthMask(true);
                    }
                    break;

                case "text":
                    if (el.text != null) {
                        if (style != null) style.text(gui, el.text, elX, elY, el.shadow);
                        else MenuText.draw(gui, net.minecraft.network.chat.Component.literal(el.text), elX, elY,
                            Math.max(.05f, el.textScale * 1f), el.color, el.shadow, true);
                    }
                    break;
            }
        }
    }

    // ── Scaled render (for tracked elements) ─────────────────────────────────────

    /**
     * Render the menu object at an ABSOLUTE screen position (originX, originY)
     * with proportional scaling. Used when the menu object is rendered as a
     * tracked VirtualGuiElement (via beginRenderElement) so it can be dragged
     * and resized. The scale factors are computed from the element's current
     * size vs the original bounding box size.
     *
     * Each element's visualRect (pivot already resolved) is shifted into the
     * object's bounding box space, then scaled by scaleX/scaleY — same
     * single-source-of-truth positioning as render().
     *
     * Unlike render(), this does NOT add the parent render offset — the
     * originX/originY are already absolute screen coordinates.
     */
    public void renderAt(int originX, int originY, float scaleX, float scaleY, GuiGraphicsExtractor gui) {
        if (elements.isEmpty()) return;

        float[] bb = getBoundingBox();
        float minX = bb[0], minY = bb[1];

        // When collision box is set, center elements on the collision box center
        float[] centerOff = getCollisionCenterOffset();

        for (Element el : elements) {
            float[] r = visualRect(el);
            int elX = originX + Math.round((r[0] - minX + centerOff[0]) * scaleX);
            int elY = originY + Math.round((r[1] - minY + centerOff[1]) * scaleY);
            int elW = Math.round(r[2] * scaleX);
            int elH = Math.round(r[3] * scaleY);

            StudioRuntime.hit(el.studioKey, elX, elY, elW, elH, el.id);
            MenuStyle style = StudioRuntime.style(el.studioKey);
            if (style != null && !el.type.equals("text")) style.background(gui, elX, elY, elW, elH);
            switch (el.type) {
                case "rectangle":
                    if (elW > 0 && elH > 0)
                        if (style == null) gui.fill(elX, elY, elX + elW, elY + elH, el.color);
                    break;

                case "texture":
                    if (el.texturePath != null && !el.texturePath.isEmpty() && elW > 0 && elH > 0) {
                        Identifier texture = Identifier.parse(style != null && !style.texture().isEmpty() ? style.texture() : el.texturePath);
                        gui.blit(texture, elX, elY, 0, 0, elW, elH);
                    }
                    break;

                case "item":
                    if (el.itemRef != null && !el.itemRef.isEmpty()) {
                        ItemStack itemStack = MenuRenderHelper.resolveItemRef(el.itemRef);
                        RenderSystem.depthFunc(GL11.GL_ALWAYS);
                        RenderSystem.depthMask(false);
                        float sx = scaleX;
                        float sy = scaleY;
                        if (sx == 1f && sy == 1f) {
                            gui.renderItem(itemStack, elX, elY);
                        } else {
                            gui.pose().pushPose();
                            gui.pose().translate(elX, elY, 0);
                            gui.pose().scale(sx, sy, 1f);
                            gui.renderItem(itemStack, 0, 0);
                            gui.pose().popPose();
                        }
                        RenderSystem.depthFunc(GL11.GL_LEQUAL);
                        RenderSystem.depthMask(true);
                    }
                    break;

                case "text":
                    if (el.text != null) {
                        gui.pose().pushPose();
                        try {gui.pose().translate(elX,elY,0);gui.pose().scale(scaleX,scaleY,1);
                            if (style != null) style.text(gui, el.text, 0, 0, el.shadow);
                            else MenuText.draw(gui, net.minecraft.network.chat.Component.literal(el.text), 0, 0, Math.max(.05f, el.textScale), el.color, el.shadow, true);
                        } finally {gui.pose().popPose();}
                    }
                    break;
            }
        }
    }
}
