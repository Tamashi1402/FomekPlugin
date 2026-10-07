package net.tamashi.fomekcore.api.guisystems;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * PanelType — Visual type for a panel (goes into the "as" input).
 *
 * Rectangle: filled rectangle with color
 * Texture: textured background with optional tint
 * Empty: no rendering, just boundaries
 */
public interface PanelType {
    void render(GuiGraphicsExtractor gui, float x, float y, float w, float h);

    record Rectangle(int color) implements PanelType {
        @Override
        public void render(GuiGraphicsExtractor gui, float x, float y, float w, float h) {
            gui.fill((int) x, (int) y, (int) (x + w), (int) (y + h), color);
        }
    }

    record Texture(String texturePath, int tint) implements PanelType {
        @Override
        public void render(GuiGraphicsExtractor gui, float x, float y, float w, float h) {
            try {
                Identifier rl = Identifier.parse(texturePath);
                gui.blit(rl, (int) x, (int) y, 0, 0, (int) w, (int) h);
            } catch (Exception ignored) {}
        }
    }

    record Empty() implements PanelType {
        @Override
        public void render(GuiGraphicsExtractor gui, float x, float y, float w, float h) {}
    }
}
