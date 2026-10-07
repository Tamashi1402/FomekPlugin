package net.tamashi.fomekcore.api.guisystems;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * MenuRenderHelper — Native rendering for FomekMenus.
 *
 * Stores the current GuiGraphics from the render event so procedure blocks
 * can render text, rectangles, textures, and items without needing a
 * guigraphics dependency input.
 *
 * Called by MenuEventHandler.onScreenRenderPost() before firing the RenderEvent.
 *
 * Also sets up the FomekRenderer context so FomekRenderer overlay blocks
 * work natively inside the Menu System trigger if FomekRenderer is installed.
 *
 * ── Stick ──────────────────────────────────────────────────────────────────
 * All render* methods have an overload that accepts a trailing `boolean stick`.
 * stick = false (default, matches the no-stick overloads) → the render call is
 *   scaled by the current parent panel's scale factor, so it resizes along
 *   with its parent (same as before Stick existed).
 * stick = true → scale is forced to 1, so the render call keeps its exact
 *   pixel size/position relative to the parent's origin and does not grow or
 *   shrink when the parent panel is resized (it "sticks" — and can end up
 *   clipped/hidden under the parent's edges if the parent shrinks).
 * The parent's absolute offset (offX/offY) is always applied either way —
 * stick only removes the scale multiplier, not the positional anchor.
 */
public class MenuRenderHelper {

    private static GuiGraphics currentGuiGraphics;

    // ── Context management ──────────────────────────────────────────────────────

    public static void setGuiGraphics(GuiGraphics gui) { currentGuiGraphics = gui; }
    public static GuiGraphics getGuiGraphics() { return currentGuiGraphics; }
    public static void clear() { currentGuiGraphics = null; }

    public static GuiGraphics getOrThrow() {
        if (currentGuiGraphics == null) {
            throw new IllegalStateException("MenuRenderHelper: no GuiGraphics context. " +
                "Render blocks can only be called inside the Menu System trigger.");
        }
        return currentGuiGraphics;
    }

    // ── Text rendering ───────────────────────────────────────────────────────────

    /** Render text at x, y with color (ARGB). Shadow = true by default. */
    public static void renderText(String text, int x, int y, int color) {
        renderText(text,x,y,color,"top-left",true);
    }

    /** Render text at x, y with color (ARGB) and optional shadow. */
    public static void renderText(String text, int x, int y, int color, boolean shadow) {
        renderText(text,x,y,color,"top-left",shadow);
    }

    /** Render text at x, y with color (ARGB), pivot alignment, and optional shadow. */
    public static void renderText(String text, int x, int y, int color, String pivot, boolean shadow) {
        renderText(text, x, y, color, pivot, shadow, false);
    }

    /** Render text at x, y with color (ARGB), pivot alignment, optional shadow, and stick flag. */
    public static void renderText(String text, int x, int y, int color, String pivot, boolean shadow, boolean stick) {
        renderText(text, x, y, color, pivot, shadow, stick, false);
    }

    public static void renderText(String text, int x, int y, int color, String pivot, boolean shadow, boolean stick, boolean collision) {
        VirtualGuiElement renderTextElement = VirtualGui.getCurrentRenderElement();
        if (renderTextElement != null && "render_text".equals(renderTextElement.getType())) { renderTextElement.setStudioText(text, color, shadow); return; }
        // Position is never scaled — only size (for non-stick). Subtract parent's
        // resize delta so the text stays pinned during left/top panel resize.
        int offX = VirtualGui.getCurrentParentRenderX();
        int offY = VirtualGui.getCurrentParentRenderY();

        float csx = VirtualGui.getCurrentContentScaleX();

        float csy = VirtualGui.getCurrentContentScaleY();
        Font font = Minecraft.getInstance().font;
        int textW = font.width(text);
        int textH = font.lineHeight;
        int drawX = (int)(x * csx) + offX;
        int drawY = (int)(y * csy) + offY;

        // Full 9-point pivot: horizontal
        if (pivot == null) pivot = "top-left";
        switch (pivot) {
            // X: center the text horizontally
            case "top": case "center": case "bottom":
                drawX -= textW / 2; break;
            // X: right-align the text
            case "top-right": case "right": case "bottom-right":
                drawX -= textW; break;
            // X: left-align (default, no offset)
            default: break;
        }
        switch (pivot) {
            // Y: center vertically
            case "left": case "center": case "right":
                drawY -= textH / 2; break;
            // Y: bottom-align (text ends at y)
            case "bottom-left": case "bottom": case "bottom-right":
                drawY -= textH; break;
            // Y: top-align (default, no offset)
            default: break;
        }

        StudioRuntime.hit(StudioRuntime.key(),drawX,drawY,textW,textH,"");
        MenuStyle style=StudioRuntime.style();
        if(style!=null)style.text(getOrThrow(),text,drawX,drawY,shadow);
        else MenuText.draw(getOrThrow(),net.minecraft.network.chat.Component.literal(text),drawX,drawY,1f,color,shadow,true);
    }

    /** Render text centered at x, y with color (ARGB). */
    public static void renderTextCentered(String text, int x, int y, int color) {
        int offX = VirtualGui.getCurrentParentRenderX();
        int offY = VirtualGui.getCurrentParentRenderY();

        float csx = VirtualGui.getCurrentContentScaleX();

        float csy = VirtualGui.getCurrentContentScaleY();
        Font font = Minecraft.getInstance().font;
        getOrThrow().drawString(font, text, (int)(x * csx) + offX - font.width(text) / 2, y + offY, color, true);
    }

    /** Render text centered at x, y with color (ARGB) and optional shadow. */
    public static void renderTextCentered(String text, int x, int y, int color, boolean shadow) {
        int offX = VirtualGui.getCurrentParentRenderX();
        int offY = VirtualGui.getCurrentParentRenderY();

        float csx = VirtualGui.getCurrentContentScaleX();

        float csy = VirtualGui.getCurrentContentScaleY();
        Font font = Minecraft.getInstance().font;
        getOrThrow().drawString(font, text, (int)(x * csx) + offX - font.width(text) / 2, y + offY, color, shadow);
    }

    /** Get the width of text in pixels using the default font. */
    public static int getTextWidth(String text) {
        return Minecraft.getInstance().font.width(text);
    }

    /** Get the font line height. */
    public static int getLineHeight() {
        return Minecraft.getInstance().font.lineHeight;
    }

    // ── Rectangle rendering ──────────────────────────────────────────────────────

    /** Render a filled rectangle from (x1, y1) to (x2, y2) with color (ARGB). */
    public static void renderRect(int x1, int y1, int x2, int y2, int color) {
        VirtualGui.registerSolidBox(x1, y1, x2, y2);
        int offX = VirtualGui.getCurrentParentRenderX();
        int offY = VirtualGui.getCurrentParentRenderY();

        float csx = VirtualGui.getCurrentContentScaleX();

        float csy = VirtualGui.getCurrentContentScaleY();
        getOrThrow().fill((int)(x1 * csx) + offX, y1 + offY, x2 + offX, y2 + offY, color);
    }

    /** Render a rectangle outline (1px border) from (x1, y1) to (x2, y2) with color. */
    public static void renderRectOutline(int x1, int y1, int x2, int y2, int color) {
        int offX = VirtualGui.getCurrentParentRenderX();
        int offY = VirtualGui.getCurrentParentRenderY();

        float csx = VirtualGui.getCurrentContentScaleX();

        float csy = VirtualGui.getCurrentContentScaleY();
        x1 = (int)(x1 * csx) + offX; y1 = y1 + offY;
        x2 = (int)(x2 * csx) + offX; y2 = y2 + offY;
        GuiGraphics gui = getOrThrow();
        gui.fill(x1, y1, x2, y1 + 1, color);          // top
        gui.fill(x1, y2 - 1, x2, y2, color);            // bottom
        gui.fill(x1, y1, x1 + 1, y2, color);            // left
        gui.fill(x2 - 1, y1, x2, y2, color);            // right
    }


    /** Render a filled rectangle from a Box with color (ARGB). */
    public static void renderRect(Box box, int color) {
        renderRect(box, color, false);
    }

    /** Render a filled rectangle from a Box with color (ARGB) and stick flag. */
    public static void renderRect(Box box, int color, boolean stick) {
        renderRect(box, color, stick, false);
    }

    public static void renderRect(Box box, int color, boolean stick, boolean collision) {
        if (collision) VirtualGui.registerSolidBox(box.x1(), box.y1(), box.x2(), box.y2());
        int offX = VirtualGui.getCurrentParentRenderX();
        int offY = VirtualGui.getCurrentParentRenderY();

        float csx = VirtualGui.getCurrentContentScaleX();

        float csy = VirtualGui.getCurrentContentScaleY();
        int drawX1 = (int)(box.x1() * csx) + offX;
        int drawY1 = (int)(box.y1() * csy) + offY;
        int drawX2 = (int)(box.x2() * csx) + offX;
        int drawY2 = (int)(box.y2() * csy) + offY;
        getOrThrow().fill(drawX1, drawY1, drawX2, drawY2, color);
    }

    /** Render a rectangle outline from a Box with color. */
    public static void renderRectOutline(Box box, int color) {
        renderRectOutline(box, color, false);
    }

    /** Render a rectangle outline from a Box with color and stick flag. */
    public static void renderRectOutline(Box box, int color, boolean stick) {
        renderRectOutline(box, color, stick, false);
    }

    public static void renderRectOutline(Box box, int color, boolean stick, boolean collision) {
        if (collision) VirtualGui.registerSolidBox(box.x1(), box.y1(), box.x2(), box.y2());
        int offX = VirtualGui.getCurrentParentRenderX();
        int offY = VirtualGui.getCurrentParentRenderY();

        float csx = VirtualGui.getCurrentContentScaleX();

        float csy = VirtualGui.getCurrentContentScaleY();
        int x1 = (int)(box.x1() * csx) + offX, y1 = (int)(box.y1() * csy) + offY;
        int x2 = (int)(box.x2() * csx) + offX, y2 = (int)(box.y2() * csy) + offY;
        GuiGraphics gui = getOrThrow();
        gui.fill(x1, y1, x2, y1 + 1, color);
        gui.fill(x1, y2 - 1, x2, y2, color);
        gui.fill(x1, y1, x1 + 1, y2, color);
        gui.fill(x2 - 1, y1, x2, y2, color);
    }


    // ── Pivot-aware rect rendering (box = size, x/y = position, pivot = anchor) ──

    public static void renderRect(Box box, int x, int y, int color, String pivot, boolean stick, boolean collision) {
        VirtualGuiElement renderEl = VirtualGui.getCurrentRenderElement();
        if (renderEl != null) {
            renderEl.setLastRectColor(color);
        } else {
            int w = (int)(box.x2() - box.x1());
            int h = (int)(box.y2() - box.y1());
            int drawX1 = x, drawY1 = y;
            int drawX2 = x + w, drawY2 = y + h;
            if (pivot == null) pivot = "top-left";
            switch (pivot) {
                case "top": case "center": case "bottom":
                    drawX1 -= w / 2; drawX2 -= w / 2; break;
                case "top-right": case "right": case "bottom-right":
                    drawX1 -= w; drawX2 -= w; break;
            }
            switch (pivot) {
                case "left": case "center": case "right":
                    drawY1 -= h / 2; drawY2 -= h / 2; break;
                case "bottom-left": case "bottom": case "bottom-right":
                    drawY1 -= h; drawY2 -= h; break;
            }
            if (collision) VirtualGui.registerSolidBox(drawX1, drawY1, drawX2, drawY2);
            // If we're inside a panel (currentParentId set), defer this fill to
            // renderElements so it respects elementOrder z-ordering. Without
            // this, immediate fills would be drawn during the procedure phase
            // and end up UNDER deferred content from panels that render earlier.
            VirtualGuiElement parentEl = VirtualGui.getCurrentElement();
            if (parentEl != null && VirtualGui.isInsidePanel()) {
                parentEl.addPendingFill(drawX1, drawY1, drawX2, drawY2, color);
            } else {
                int offX = VirtualGui.getCurrentParentRenderX();
                int offY = VirtualGui.getCurrentParentRenderY();

                float csx = VirtualGui.getCurrentContentScaleX();

                float csy = VirtualGui.getCurrentContentScaleY();
                getOrThrow().fill((int)(drawX1 * csx) + offX, (int)(drawY1 * csy) + offY, (int)(drawX2 * csx) + offX, (int)(drawY2 * csy) + offY, color);
            }
        }
    }

    public static void renderRectOutline(Box box, int x, int y, int color, String pivot, boolean stick, boolean collision) {
        VirtualGuiElement renderEl = VirtualGui.getCurrentRenderElement();
        if (renderEl != null) {
            renderEl.setLastRectColor(color);
        } else {
            int w = (int)(box.x2() - box.x1());
            int h = (int)(box.y2() - box.y1());
            int drawX1 = x, drawY1 = y;
            int drawX2 = x + w, drawY2 = y + h;
            if (pivot == null) pivot = "top-left";
            switch (pivot) {
                case "top": case "center": case "bottom":
                    drawX1 -= w / 2; drawX2 -= w / 2; break;
                case "top-right": case "right": case "bottom-right":
                    drawX1 -= w; drawX2 -= w; break;
            }
            switch (pivot) {
                case "left": case "center": case "right":
                    drawY1 -= h / 2; drawY2 -= h / 2; break;
                case "bottom-left": case "bottom": case "bottom-right":
                    drawY1 -= h; drawY2 -= h; break;
            }
            if (collision) VirtualGui.registerSolidBox(drawX1, drawY1, drawX2, drawY2);
            int offX = VirtualGui.getCurrentParentRenderX();
            int offY = VirtualGui.getCurrentParentRenderY();

            float csx = VirtualGui.getCurrentContentScaleX();

            float csy = VirtualGui.getCurrentContentScaleY();
            int x1 = (int)(drawX1 * csx) + offX, y1 = (int)(drawY1 * csy) + offY;
            int x2 = (int)(drawX2 * csx) + offX, y2 = (int)(drawY2 * csy) + offY;
            GuiGraphics gui = getOrThrow();
            gui.fill(x1, y1, x2, y1 + 1, color);
            gui.fill(x1, y2 - 1, x2, y2, color);
            gui.fill(x1, y1, x1 + 1, y2, color);
            gui.fill(x2 - 1, y1, x2, y2, color);
        }
    }

    // ── Texture rendering ──────────────────────────────────────────────────────────

    /** Render a texture at (x, y) with size (width, height). */
    public static void renderTexture(String texturePath, int x, int y, int width, int height) {
        renderTexture(texturePath, x, y, width, height, false);
    }

    /** Render a texture at (x, y) with size (width, height) and stick flag. */
    public static void renderTexture(String texturePath, int x, int y, int width, int height, boolean stick) {
        renderTexture(texturePath, x, y, width, height, stick, false);
    }

    public static void renderTexture(String texturePath, int x, int y, int width, int height, boolean stick, boolean collision) {
        VirtualGuiElement renderEl = VirtualGui.getCurrentRenderElement();
        GuiGraphics gui = getOrThrow();
        if (renderEl != null) {
            renderEl.setLastTexturePath(texturePath);
        } else {
            ResourceLocation texture = ResourceLocation.parse(texturePath);
            if (collision) VirtualGui.registerSolidBox(x, y, x + width, y + height);
            int offX = VirtualGui.getCurrentParentRenderX();
            int offY = VirtualGui.getCurrentParentRenderY();

            float csx = VirtualGui.getCurrentContentScaleX();

            float csy = VirtualGui.getCurrentContentScaleY();
            gui.blit(texture, (int)(x * csx) + offX, y + offY, 0, 0, width, height);
        }
    }

    /** Render a texture at (x, y) with size (width, height) from a texture atlas at (u, v). */
    public static void renderTextureRegion(String texturePath, int x, int y, int u, int v,
                                             int width, int height, int textureWidth, int textureHeight) {
        int offX = VirtualGui.getCurrentParentRenderX();
        int offY = VirtualGui.getCurrentParentRenderY();

        float csx = VirtualGui.getCurrentContentScaleX();

        float csy = VirtualGui.getCurrentContentScaleY();
        ResourceLocation texture = ResourceLocation.parse(texturePath);
        getOrThrow().blit(texture, (int)(x * csx) + offX, y + offY, u, v, width, height, textureWidth, textureHeight);
    }

    // ── Item rendering ──────────────────────────────────────────────────────────────

    /** Render an item icon at (x, y). */
    public static void renderItem(ItemStack itemStack, int x, int y) {
        int offX = VirtualGui.getCurrentParentRenderX();
        int offY = VirtualGui.getCurrentParentRenderY();

        float csx = VirtualGui.getCurrentContentScaleX();

        float csy = VirtualGui.getCurrentContentScaleY();
        getOrThrow().renderItem(itemStack, (int)(x * csx) + offX, y + offY);
    }

/**
     * Render an item from a MCreator item reference string.
     * Handles both "minecraft:stick" (vanilla) and "CUSTOM:EchoDust" (modded) formats.
     */
    public static void renderItem(String itemRef, int x, int y) {
        renderItem(itemRef, x, y, false);
    }

    /** Render an item from a MCreator item reference string, with a stick flag. */
    public static void renderItem(String itemRef, int x, int y, boolean stick) {
        renderItem(itemRef, x, y, stick, false);
    }

    public static void renderItem(String itemRef, int x, int y, boolean stick, boolean collision) {
        VirtualGuiElement renderEl = VirtualGui.getCurrentRenderElement();
        GuiGraphics gui = getOrThrow();
        if (renderEl != null) {
            renderEl.setLastItemRef(itemRef);
        } else {
            if (collision) VirtualGui.registerSolidBox(x, y, x + 16, y + 16);
            int offX = VirtualGui.getCurrentParentRenderX();
            int offY = VirtualGui.getCurrentParentRenderY();

            float csx = VirtualGui.getCurrentContentScaleX();

            float csy = VirtualGui.getCurrentContentScaleY();
            StudioRuntime.hit(StudioRuntime.key(),(int)(x*csx)+offX,y+offY,16,16,"");
            gui.renderItem(resolveItemRef(itemRef), (int)(x * csx) + offX, y + offY);
        }
    }

/**
     * Convert a MCreator item reference to an ItemStack.
     * "minecraft:stick" -> vanilla item from registry
     * "CUSTOM:EchoDust" -> searches registry for matching modded item
     */
    public static ItemStack resolveItemRef(String ref) {
        if (ref == null || ref.isEmpty()) return ItemStack.EMPTY;

        // MCreator modded item format: CUSTOM:ItemName
        if (ref.startsWith("CUSTOM:")) {
            String itemName = ref.substring(7);
            // Convert CamelCase to snake_case (EchoDust -> echo_dust)
            String snakeCase = itemName.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
            // Search the item registry for a matching path
            for (var entry : BuiltInRegistries.ITEM.entrySet()) {
                if (entry.getKey().location().getPath().equals(snakeCase)) {
                    return new ItemStack(entry.getValue());
                }
            }
            return ItemStack.EMPTY;
        }

        // MCreator Java-field reference format: Blocks.GRASS_BLOCK, Items.STICK
        // These come from MCreator's MCItem input blocks. Convert to registry name.
        if (ref.startsWith("Blocks.") || ref.startsWith("Items.")) {
            String fieldName = ref.substring(ref.indexOf('.') + 1);
            String registryPath = fieldName.toLowerCase();
            ResourceLocation rl = ResourceLocation.tryParse("minecraft:" + registryPath);
            if (rl != null) {
                Item item = BuiltInRegistries.ITEM.get(rl);
                return new ItemStack(item);
            }
            return ItemStack.EMPTY;
        }

        // Try as a resource location (e.g., "minecraft:stick")
        ResourceLocation rl = ResourceLocation.tryParse(ref);
        if (rl != null) {
            Item item = BuiltInRegistries.ITEM.get(rl);
            return new ItemStack(item);
        }
        return ItemStack.EMPTY;
    }
}

