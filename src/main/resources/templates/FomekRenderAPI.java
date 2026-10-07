package __RENDERAPI_PACKAGE__;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import com.mojang.math.Axis;

import org.joml.Matrix4f;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.lang.reflect.Field;
import org.lwjgl.opengl.GL11;

public class FomekRenderAPI {

    // ── Render Context ─────────────────────────────────────────────────────────

    private static FomekRenderEvent.Item currentContext;
    private static boolean bypassMixin = false;

    public static void setCurrentContext(FomekRenderEvent.Item event) {
        currentContext = event;
        bypassMixin = false;
    }

    public static void clearCurrentContext() {
        currentContext = null;
        bypassMixin = false;
    }

    public static boolean isBypassMixin() { return bypassMixin; }

    /** Set the bypass mixin flag — when true, the ItemRenderer mixin skips the Fomek render event. */
    public static void setBypassMixin(boolean value) { bypassMixin = value; }

    /** True when we're inside an item render event (FIRST_PERSON, THIRD_PERSON, GUI, GROUND). */
    public static boolean isInItemContext() { return currentContext != null; }

    // ── Registration Context ────────────────────────────────────────────────────

    private static FomekBEWRL.RegisterEvent currentRegEvent;

    /** Called by the trigger handler before executing the procedure body. */
    public static void setCurrentRegEvent(FomekBEWRL.RegisterEvent event) {
        currentRegEvent = event;
    }

    /** Called by the trigger handler after executing the procedure body. */
    public static void clearCurrentRegEvent() {
        currentRegEvent = null;
    }

    /**
     * Register a BEWRL model. Uses the mod id from the current registration event.
     * Must be called inside a "Register BEWRL Models" trigger procedure.
     *
     * @param path    Identifier path, e.g. "__MODID__:bewrlmodels/sword.json"
     * @param modelId Short id, e.g. "fire_sword" — stored as "modid:fire_sword"
     */
    public static void registerBEWRLModel(String path, String modelId) {
        if (currentRegEvent != null) {
            currentRegEvent.register(path, modelId);
        }
    }

    // ── Event control ──────────────────────────────────────────────────────────

    public static void cancelRender() {
        if (currentContext != null) currentContext.setCanceled(true);
    }

    public static boolean isRenderType(String type) {
        if (currentContext == null) return false;
        return currentContext.getDisplayContext().name().equals(type);
    }

    public static ItemDisplayContext getCurrentDisplayContext() {
        if (currentContext == null) return ItemDisplayContext.NONE;
        return currentContext.getDisplayContext();
    }

    // ── Context getters ────────────────────────────────────────────────────────

    public static ItemStack getItemStack() {
        return currentContext != null ? currentContext.getItemStack() : ItemStack.EMPTY;
    }

    public static LivingEntity getEntity() {
        return currentContext != null ? currentContext.getEntity() : null;
    }

    public static Level getWorld() {
        return currentContext != null ? currentContext.getWorld() : null;
    }

    public static float getX() { return currentContext != null ? currentContext.getX() : 0f; }
    public static float getY() { return currentContext != null ? currentContext.getY() : 0f; }
    public static float getZ() { return currentContext != null ? currentContext.getZ() : 0f; }
    public static float getXHand() { return 0f; }
    public static float getYHand() { return 0f; }
    public static float getZHand() { return 0f; }

    public static float getPartialTick() {
        // In world context, return the 0-1 tick fraction (for getPosition, Mth.lerp).
        // In item context, return the item render partial tick.
        if (currentWorldContext != null) return currentWorldContext.getPartialTick();
        return currentContext != null ? currentContext.getPartialTick() : 0f;
    }

    public static float getGameTime() {
        // renderTime is continuous (gameTime + tickFraction), so use it for animations.
        if (currentWorldContext != null) return currentWorldContext.getRenderTime();
        return getActivePartialTick();
    }

    public static float getRenderTime() {
        return getGameTime() / 20.0f;
    }

    // ── Overlay dimensions ────────────────────────────────────────────────────

    public static int getOverlayWidth() {
        return currentOverlayContext != null ? currentOverlayContext.getWidth() : 0;
    }

    public static int getOverlayHeight() {
        return currentOverlayContext != null ? currentOverlayContext.getHeight() : 0;
    }

    // ── Item display transform extraction ──────────────────────────────────────

    /**
     * Extracts the yaw (Y rotation) from the item's model display transformation
     * for the current render context (e.g. FIRST_PERSON_RIGHT_HAND).
     *
     * This lets you pass the item's own yaw to {@link #renderText} via the
     * {@code itemYaw} parameter, so that x/y/z translation works in the item's
     * local coordinate space — +z always moves "forward" relative to the item,
     * regardless of how the item is rotated by its display settings.
     *
     * @return yaw in degrees, or 0 if it cannot be determined
     */
    public static float getItemDisplayYaw() {
        if (currentContext == null) return 0f;
        ItemStack stack = currentContext.getItemStack();
        if (stack == null || stack.isEmpty()) return 0f;
        ItemDisplayContext ctx = currentContext.getDisplayContext();
        Level level = currentContext.getWorld();
        LivingEntity entity = currentContext.getEntity();

        try {
            ResolvedModel model = Minecraft.getInstance().getItemRenderer()
                    .getModel(stack, level, entity, 0);
            ItemTransform transform = model.wrapped().transforms().getTransform(ctx);
            if (transform != null && transform != ItemTransform.NO_TRANSFORM) {
                // rotation is a Vector3f in degrees: x=pitch, y=yaw, z=roll
                return transform.rotation.y();
            }
        } catch (Exception ignored) {
        }
        return 0f;
    }

    /**
     * Extracts the pitch (X rotation) from the item's model display transformation
     * for the current render context.
     *
     * @return pitch in degrees, or 0 if it cannot be determined
     */
    public static float getItemDisplayPitch() {
        if (currentContext == null) return 0f;
        ItemStack stack = currentContext.getItemStack();
        if (stack == null || stack.isEmpty()) return 0f;
        ItemDisplayContext ctx = currentContext.getDisplayContext();
        Level level = currentContext.getWorld();
        LivingEntity entity = currentContext.getEntity();

        try {
            ResolvedModel model = Minecraft.getInstance().getItemRenderer()
                    .getModel(stack, level, entity, 0);
            ItemTransform transform = model.wrapped().transforms().getTransform(ctx);
            if (transform != null && transform != ItemTransform.NO_TRANSFORM) {
                // rotation is a Vector3f in degrees: x=pitch, y=yaw, z=roll
                return transform.rotation.x();
            }
        } catch (Exception ignored) {
        }
        return 0f;
    }

    /**
     * Extracts the roll (Z rotation) from the item's model display transformation
     * for the current render context.
     *
     * @return roll in degrees, or 0 if it cannot be determined
     */
    public static float getItemDisplayRoll() {
        if (currentContext == null) return 0f;
        ItemStack stack = currentContext.getItemStack();
        if (stack == null || stack.isEmpty()) return 0f;
        ItemDisplayContext ctx = currentContext.getDisplayContext();
        Level level = currentContext.getWorld();
        LivingEntity entity = currentContext.getEntity();

        try {
            ResolvedModel model = Minecraft.getInstance().getItemRenderer()
                    .getModel(stack, level, entity, 0);
            ItemTransform transform = model.wrapped().transforms().getTransform(ctx);
            if (transform != null && transform != ItemTransform.NO_TRANSFORM) {
                return transform.rotation.z();
            }
        } catch (Exception ignored) {
        }
        return 0f;
    }

    // ── Item display transform ─────────────────────────────────────────────────

    /**
     * Apply the item display transform's ROTATION to the PoseStack.
     *
     * This orients custom models to match the item's display angle:
     *   - GUI: isometric angle (e.g. 30° X, 225° Y)
     *   - FIRST_PERSON: held angle
     *   - THIRD_PERSON: side-held angle
     *   - GROUND: flat angle
     *
     * Only the rotation component is applied — translation and scale from the
     * item display transform are NOT applied. The user controls size via the
     * pix/scale parameters and position via x/y/z offsets. This matches how
     * vanilla applies the transform in ItemRenderer.render(), but without the
     * -0.5 centering (which is for block models, not entity/BEWRL models).
     *
     * Must be called in the item render context (currentContext != null).
     * No-op if not in an item render context or if the item has no transform.
     */
    public static void applyItemDisplayRotation(PoseStack pose) {
        if (currentContext == null) return;
        ItemStack stack = currentContext.getItemStack();
        if (stack == null || stack.isEmpty()) return;
        ItemDisplayContext ctx = currentContext.getDisplayContext();
        Level level = currentContext.getWorld();
        LivingEntity entity = currentContext.getEntity();

        try {
            ResolvedModel model = Minecraft.getInstance().getItemRenderer()
                    .getModel(stack, level, entity, 0);
            ItemTransform transform = model.wrapped().transforms().getTransform(ctx);
            if (transform != null && transform != ItemTransform.NO_TRANSFORM) {
                // ItemTransform stores rotation as Vector3f(x=pitch, y=yaw, z=roll) in degrees.
                // The apply() method uses rotationZYX(z, y, x), which via mulPose
                // means: apply Z first, then Y, then X (matching the order below).
                float rZ = transform.rotation.z();
                float rY = transform.rotation.y();
                float rX = transform.rotation.x();
                if (rZ != 0) pose.mulPose(Axis.ZP.rotationDegrees(rZ));
                if (rY != 0) pose.mulPose(Axis.YP.rotationDegrees(rY));
                if (rX != 0) pose.mulPose(Axis.XP.rotationDegrees(rX));
            }
        } catch (Exception ignored) {}
    }

    // ── Item rendering ─────────────────────────────────────────────────────────

    public static void renderItem(ItemStack stack, float x, float y, float z,
            float yaw, float pitch, float roll, float scale, boolean glowing) {
        if (stack == null || stack.isEmpty()) return;

        // ── Overlay context: enqueue for depth-sorted rendering ──
        if (currentOverlayContext != null) {
            final float _z = z;
            final float _yaw = yaw, _pitch = pitch, _roll = roll, _scale = scale;
            final boolean _glowing = glowing;
            final ItemStack _stack = stack;
            enqueueOverlay(_z, () -> {
                Minecraft mc = Minecraft.getInstance();
                GuiGraphicsExtractor gui = currentOverlayContext.getGuiGraphics();
                PoseStack pose = gui.pose();
                MultiBufferSource.BufferSource buffer = gui.bufferSource();
                Level level = mc.level;
                LivingEntity entity = currentOverlayContext.getPlayer();
                int light = LightCoordsUtil.FULL_BRIGHT;

                pose.pushPose();
                pose.translate(x + 8, y + 8, _z);
                if (_yaw   != 0) pose.mulPose(Axis.YP.rotationDegrees(_yaw));
                if (_pitch != 0) pose.mulPose(Axis.XP.rotationDegrees(_pitch));
                if (_roll  != 0) pose.mulPose(Axis.ZP.rotationDegrees(_roll));
                pose.scale(1, -1, 1);
                float s = 16 * _scale;
                pose.scale(s, s, s);

                bypassMixin = true;
                try {
                    mc.getItemRenderer().renderStatic(
                        entity, _stack, ItemDisplayContext.GUI, false,
                        pose, buffer, level, light, OverlayTexture.NO_OVERLAY, 0);
                } finally {
                    bypassMixin = false;
                }
                pose.popPose();
            });
            return;
        }

        // ── Item/world context ──
        if (currentContext == null && currentWorldContext == null) return;

        PoseStack pose;
        MultiBufferSource buffer;
        LivingEntity entity;
        Level level;
        int light;

        if (currentContext != null) {
            pose = currentContext.getPoseStack();
            buffer = currentContext.getBufferSource();
            entity = currentContext.getEntity();
            level = currentContext.getWorld();
            light = glowing ? LightCoordsUtil.FULL_BRIGHT : currentContext.getPackedLight();
        } else {
            // World context — the event handler already translates the PoseStack
            // by -cameraPosition, so we pass raw world coordinates directly.
            pose = currentWorldContext.getPoseStack();
            buffer = currentWorldContext.getBufferSource();
            entity = null;
            level = currentWorldContext.getWorld();
            if (glowing) {
                light = LightCoordsUtil.FULL_BRIGHT;
            } else {
                light = net.minecraft.client.renderer.LevelRenderer.getLightColor(level,
                        net.minecraft.core.BlockPos.containing(x, y, z));
            }
        }

        pose.pushPose();
        pose.translate(x, y, z);
        if (yaw   != 0) pose.mulPose(Axis.YP.rotationDegrees(yaw));
        if (pitch != 0) pose.mulPose(Axis.XP.rotationDegrees(pitch));
        if (roll  != 0) pose.mulPose(Axis.ZP.rotationDegrees(roll));
        if (scale != 1) pose.scale(scale, scale, scale);

        // Don't bypass the mixin in world context — let item render procedures
        // intercept the render. The mixin itself sets bypassMixin=true during
        // the event handler to prevent recursion.
        Minecraft.getInstance().getItemRenderer().renderStatic(
            entity, stack, ItemDisplayContext.NONE, false,
            pose, buffer, level, light, OverlayTexture.NO_OVERLAY, 0);

        pose.popPose();

        // Flush immediately in world context so GL state changes apply.
        if (currentWorldContext != null && buffer instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }
    }

    /**
     * Render an item on the overlay at screen coordinates.
     * Uses ItemRenderer.renderStatic directly (not gui.renderItem) to avoid
     * the internal Z=100 translate that causes rotation arcs.
     * Only works in overlay context.
     */
    public static void renderItemOverlay(net.minecraft.world.item.ItemStack stack,
            float x, float y, float depth,
            float yaw, float pitch, float roll, float scale, boolean glowing) {
        if (currentOverlayContext == null || stack == null || stack.isEmpty()) return;
        final ItemStack _stack = stack;
        final float _depth = depth, _yaw = yaw, _pitch = pitch, _roll = roll, _scale = scale;
        enqueueOverlay(_depth, () -> {
            Minecraft mc = Minecraft.getInstance();
            GuiGraphicsExtractor gui = currentOverlayContext.getGuiGraphics();
            PoseStack pose = gui.pose();
            MultiBufferSource.BufferSource buffer = gui.bufferSource();
            Level level = mc.level;
            LivingEntity entity = currentOverlayContext.getPlayer();
            int light = LightCoordsUtil.FULL_BRIGHT;

            pose.pushPose();
            pose.translate(x + 8, y + 8, _depth);
            if (_yaw   != 0) pose.mulPose(Axis.YP.rotationDegrees(_yaw));
            if (_pitch != 0) pose.mulPose(Axis.XP.rotationDegrees(_pitch));
            if (_roll  != 0) pose.mulPose(Axis.ZP.rotationDegrees(_roll));
            pose.scale(1, -1, 1);
            float s = 16 * _scale;
            pose.scale(s, s, s);

            bypassMixin = true;
            try {
                mc.getItemRenderer().renderStatic(
                    entity, _stack, ItemDisplayContext.GUI, false,
                    pose, buffer, level, light, OverlayTexture.NO_OVERLAY, 0);
            } finally {
                bypassMixin = false;
            }
            pose.popPose();
        });
    }

    // ── Shape building ─────────────────────────────────────────────────────────

    private static Shape currentShape;
    private static Shape lastShape;

    public static boolean beginShape(com.mojang.blaze3d.vertex.VertexFormat.Mode mode,
            boolean hasTexture, boolean update) {
        if (!update && lastShape != null && !lastShape.isEmpty()) return false;
        currentShape = new Shape();
        currentShape.begin(mode, hasTexture);
        lastShape = currentShape;
        return true;
    }

    public static void addVertex(float x, float y, float z, int color) {
        if (currentShape != null) currentShape.addVertex(x, y, z, color);
    }

    public static void addVertexUV(float x, float y, float z, float u, float v, int color) {
        if (currentShape != null) currentShape.addVertexUV(x, y, z, u, v, color);
    }

    public static void endShape() {
        if (currentShape != null) currentShape.end();
    }

    public static void clearShape() {
        if (currentShape != null) currentShape.clear();
    }

    public static Shape getShape() {
        Shape s = lastShape;
        lastShape = null;
        return s;
    }

    // ── Shape rendering ────────────────────────────────────────────────────────

    public static void renderShape(Shape shape, float x, float y, float z,
            float yaw, float pitch, float roll,
            float xscale, float yscale, float zscale, int color) {
        PoseStack pose = getActivePoseStack();
        MultiBufferSource buf = getActiveBufferSource();
        if (pose == null || buf == null || shape == null || shape.isEmpty()) return;
        shape.render(pose, buf,
            x, y, z, yaw, pitch, roll, xscale, yscale, zscale, color,
            getGuiAwareLight(), getActivePackedOverlay());
        // Flush immediately in world context so that any GL state changes
        // (disable/enable culling, depth test, blending) set BEFORE this call
        // are actually in effect when the vertices are drawn.
        if (currentWorldContext != null && buf instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }
    }

    // ── Texture ─────────────────────────────────────────────────────────────────

    public static void setTexture(net.minecraft.resources.Identifier texture) {
        RenderSystem.setShaderTexture(0, texture);
    }


    // ── Overlay 2D rendering ───────────────────────────────────────────────
    // These methods use GuiGraphicsExtractor for flat 2D rendering on the HUD/overlay.
    // They only work when an overlay context is active.

    /**
     * Render a filled rectangle on the overlay.
     * Coordinates are screen-space (pixels), depth is the z-layer.
     */
    public static void renderRectangle(float x1, float y1, float x2, float y2, float depth, int color) {
        if (currentOverlayContext == null) return;
        enqueueOverlay(depth, () -> {
            GuiGraphicsExtractor gui = currentOverlayContext.getGuiGraphics();
            gui.pose().pushPose();
            gui.pose().translate(0, 0, depth);
            gui.fill((int) x1, (int) y1, (int) x2, (int) y2, color);
            gui.pose().popPose();
        });
    }

    /**
     * Render a texture on the overlay at screen coordinates.
     * texturePath is a Identifier string like "minecraft:textures/block/stone.png".
     */
    public static void renderTexture(String texturePath, float x, float y, float depth,
            float angle, float scale, int color, int alignment) {
        if (currentOverlayContext == null) return;
        enqueueOverlay(depth, () -> {
            GuiGraphicsExtractor gui = currentOverlayContext.getGuiGraphics();

            net.minecraft.resources.Identifier rl;
            try { rl = net.minecraft.resources.Identifier.parse(texturePath); }
            catch (Exception e) { return; }

            final int texNative = 16;

            float drawW = texNative * scale;
            float drawH = texNative * scale;

            float ix = x;
            float iy = y;
            switch (alignment) {
                case 1: ix -= drawW / 2; break;
                case 2: ix -= drawW; break;
                case 3: iy -= drawH / 2; break;
                case 4: ix -= drawW / 2; iy -= drawH / 2; break;
                case 5: ix -= drawW; iy -= drawH / 2; break;
                case 6: iy -= drawH; break;
                case 7: ix -= drawW / 2; iy -= drawH; break;
                case 8: ix -= drawW; iy -= drawH; break;
            }

            gui.pose().pushPose();
            gui.pose().translate(ix, iy, depth);
            if (scale != 1f) gui.pose().scale(scale, scale, 1f);

            if (angle != 0) {
                gui.pose().translate(texNative / 2.0, texNative / 2.0, 0);
                gui.pose().mulPose(Axis.ZP.rotationDegrees(angle));
                gui.pose().translate(-texNative / 2.0, -texNative / 2.0, 0);
            }

            if (color != 0xFFFFFFFF) {
                float r = ((color >> 16) & 0xFF) / 255.0f;
                float g = ((color >> 8) & 0xFF) / 255.0f;
                float b = (color & 0xFF) / 255.0f;
                float a = ((color >> 24) & 0xFF) / 255.0f;
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(r, g, b, a);
            }

            gui.blit(rl, 0, 0, 0, 0, texNative, texNative, texNative, texNative);

            if (color != 0xFFFFFFFF) {
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }

            gui.pose().popPose();
        });
    }

    public static void renderTextOverlay(String text, float x, float y, float depth,
            float angle, float scale, int color, int alignment) {
        if (currentOverlayContext == null) return;
        enqueueOverlay(depth, () -> {
            GuiGraphicsExtractor gui = currentOverlayContext.getGuiGraphics();
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            net.minecraft.client.gui.Font font = mc.font;

            if (text == null || text.isEmpty()) return;

            int argbColor = color;
            if ((argbColor >> 24 & 0xFF) == 0) {
                argbColor = (0xFF << 24) | (argbColor & 0x00FFFFFF);
            }

            int rawTextW = font.width(text);
            int rawTextH = font.lineHeight;

            int scaledW = (int) (rawTextW * scale);
            int scaledH = (int) (rawTextH * scale);

            int ix = (int) x;
            int iy = (int) y;
            switch (alignment) {
                case 1: ix -= scaledW / 2; break;
                case 2: ix -= scaledW; break;
                case 3: iy -= scaledH / 2; break;
                case 4: ix -= scaledW / 2; iy -= scaledH / 2; break;
                case 5: ix -= scaledW; iy -= scaledH / 2; break;
                case 6: iy -= scaledH; break;
                case 7: ix -= scaledW / 2; iy -= scaledH; break;
                case 8: ix -= scaledW; iy -= scaledH; break;
            }

            gui.pose().pushPose();
            gui.pose().translate(0, 0, depth);
            gui.pose().translate(ix, iy, 0);

            if (scale != 1.0f) {
                gui.pose().scale(scale, scale, 1);
            }

            if (angle != 0) {
                gui.pose().translate(rawTextW / 2.0, rawTextH / 2.0, 0);
                gui.pose().mulPose(Axis.ZP.rotationDegrees(angle));
                gui.pose().translate(-rawTextW / 2.0, -rawTextH / 2.0, 0);
            }

            gui.drawString(font, text, 0, 0, argbColor, false);

            gui.pose().popPose();
        });
    }

    public static void renderShapeOverlay(Shape shape, float x, float y, float depth,
            float yaw, float pitch, float roll,
            float xscale, float yscale, float zscale, int color) {
        if (currentOverlayContext == null || shape == null || shape.isEmpty()) return;
        enqueueOverlay(depth, () -> {
            GuiGraphicsExtractor gui = currentOverlayContext.getGuiGraphics();
            PoseStack pose = gui.pose();
            MultiBufferSource.BufferSource buffer = gui.bufferSource();

            pose.pushPose();
            pose.translate(x, y, depth);
            if (yaw != 0) pose.mulPose(Axis.YP.rotationDegrees(yaw));
            if (pitch != 0) pose.mulPose(Axis.XP.rotationDegrees(pitch));
            if (roll != 0) pose.mulPose(Axis.ZP.rotationDegrees(roll));
            if (xscale != 1 || yscale != 1 || zscale != 1) pose.scale(xscale, yscale, zscale);

            shape.render(pose, buffer,
                0, 0, 0,
                0, 0, 0,
                1, 1, 1,
                color, net.minecraft.client.renderer.LightCoordsUtil.FULL_BRIGHT,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);

            pose.popPose();
        });
    }

    // ── GL state ──────────────────────────────────────────────────────────────

    // ── Blend modes ──────────────────────────────────────────────────────────

    public enum BlendMode {
        DEFAULT,
        ADDITION,
        ALPHA,
        MULTIPLICATION,
        SCREEN,
        SUBTRACTION,
        OPAQUE;
    }

    // Tracks the current blend mode for render-type-aware blending.
    // When non-null, Shape.resolveRenderType creates a custom RenderType
    // with the blend mode baked into the TransparencyStateShard, so
    // setupRenderState() applies our blend instead of the default.
    private static BlendMode currentBlendMode = null;

    public static BlendMode getCurrentBlendMode() { return currentBlendMode; }

    /**
     * Enable blending with the specified blend mode.
     * Modes match Minecraft's internal blend states:
     *   DEFAULT       — RenderSystem.defaultBlendFunc() (SRC_ALPHA, ONE_MINUS_SRC_ALPHA)
     *   ADDITION      — additive glow / lightning (SRC_ALPHA, ONE)
     *   ALPHA         — proper separate alpha (matches translucent render type)
     *   MULTIPLICATION— darken / multiply (DST_COLOR, ONE_MINUS_SRC_ALPHA)
     *   SCREEN        — lighten / screen blend (ONE, ONE_MINUS_SRC_COLOR)
     *   SUBTRACTION   — subtractive darkening (ZERO, ONE_MINUS_SRC_COLOR)
     *   OPAQUE        — no blending (ONE, ZERO) — blending still enabled but no-op
     */
    /**
     * Flush (endBatch) the active buffer source if it is a BufferSource.
     * Called before switching blend modes so previously-queued vertices
     * are drawn with the OLD blend state before we change it.
     */
    private static void flushActiveBuffer() {
        MultiBufferSource buf = getActiveBufferSource();
        if (buf instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }
    }

    public static void enableBlending(BlendMode mode) {
        // Flush any pending vertices in the shared buffer BEFORE switching blend mode.
        // Otherwise the base model (queued earlier) would draw AFTER the blended model
        // (which flushes immediately via flushBufferWithBlend), covering the glow.
        flushActiveBuffer();

        currentBlendMode = mode;
        RenderSystem.enableBlend();
        switch (mode) {
            case DEFAULT ->
                RenderSystem.defaultBlendFunc();
            case ADDITION ->
                RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE,     GlStateManager.DestFactor.ZERO);
            case ALPHA ->
                RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,     GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            case MULTIPLICATION ->
                RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.DST_COLOR,  GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,      GlStateManager.DestFactor.ZERO);
            case SCREEN ->
                RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            case SUBTRACTION ->
                RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            case OPAQUE ->
                RenderSystem.blendFunc(
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        }
    }

    public static void disableBlending() {
        currentBlendMode = null;
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }
    public static void enableDepthTest()  { RenderSystem.enableDepthTest(); }
    public static void disableDepthTest() { RenderSystem.disableDepthTest(); }
    public static void enableCulling()    { RenderSystem.enableCull(); }
    public static void disableCulling()   { RenderSystem.disableCull(); }
    public static void enableDepthMask()  { RenderSystem.depthMask(true); }
    public static void disableDepthMask() { RenderSystem.depthMask(false); }

        // ── Light helper ──────────────────────────────────────────────────────────────

    /**
     * Returns FULL_BRIGHT for GUI/inventory/hotbar/fixed/ground contexts so that
     * BEWRL models and shapes render at full brightness in the inventory/hotbar.
     * In first-person / third-person contexts the actual packed light is used
     * so the model shades correctly with the world.
     */
    public static int getGuiAwareLight() {
        if (currentContext == null) {
            // In overlay/world render context, always use full bright
            return LightCoordsUtil.FULL_BRIGHT;
        }
        switch (currentContext.getDisplayContext()) {
            case GUI:
            case FIXED:
            case GROUND:
            case NONE:
                return LightCoordsUtil.FULL_BRIGHT;
            default:
                return currentContext.getPackedLight();
        }
    }

    // ── BEWRL (manual build) ───────────────────────────────────────────────────

    private static FomekBEWRL.Model currentBEWRL;
    private static FomekBEWRL.Model lastModel;

    public static void beginBEWRL() {
        currentBEWRL = new FomekBEWRL.Model();
    }

    public static void addBEWRLPart(Shape shape, net.minecraft.resources.Identifier texture,
            float x, float y, float z, float yaw, float pitch, float roll,
            float xscale, float yscale, float zscale, int color, String renderType) {
        if (currentBEWRL == null || shape == null) return;
        currentBEWRL.addPart(shape, texture != null ? texture.toString() : "", x, y, z, yaw, pitch, roll,
            xscale, yscale, zscale, color, renderType);
    }

    /**
     * Add a Java entity model (ModelPart) as a BEWRL part.
     * The model is baked by name from FomekJavaModelRenderer's registry.
     * Orientation fix (180° X rotation) is applied automatically in item render context.
     */
    public static void addBEWRLJavaPart(String javaModelName, net.minecraft.resources.Identifier texture,
            float x, float y, float z, float yaw, float pitch, float roll,
            float xscale, float yscale, float zscale, int color, String renderType) {
        if (currentBEWRL == null || javaModelName == null) return;
        currentBEWRL.addJavaModelPart(javaModelName, texture != null ? texture.toString() : "",
            x, y, z, yaw, pitch, roll, xscale, yscale, zscale, color, renderType);
    }

    public static FomekBEWRL.Model endBEWRL() {
        FomekBEWRL.Model model = currentBEWRL;
        currentBEWRL = null;
        lastModel = model;
        return model;
    }

    public static FomekBEWRL.Model getLastModel() {
        FomekBEWRL.Model m = lastModel;
        lastModel = null;
        return m;
    }

    public static void renderBEWRL(FomekBEWRL.Model model,
            float x, float y, float z,
            float yaw, float pitch, float roll,
            float scale) {
        PoseStack pose = getActivePoseStack();
        MultiBufferSource buf = getActiveBufferSource();
        if (pose == null || buf == null || model == null || model.isEmpty()) return;

        if (currentOverlayContext != null) {
            // Overlay: enqueue for depth-sorted rendering. No hardcoded Z offset,
            // no depth test toggle, no immediate flush — the queue handles it all.
            final float _z = z;
            final float _yaw = yaw, _pitch = pitch, _roll = roll, _scale = scale;
            final FomekBEWRL.Model _model = model;
            final BlendMode _blend = currentBlendMode;
            enqueueOverlay(_z, () -> {
                GuiGraphicsExtractor gui = currentOverlayContext.getGuiGraphics();
                PoseStack _pose = gui.pose();
                MultiBufferSource.BufferSource _buf = gui.bufferSource();
                BlendMode _savedBlend = currentBlendMode;
                currentBlendMode = _blend;
                _model.render(_pose, _buf,
                    x, y, _z, _yaw, _pitch, _roll, _scale,
                    getGuiAwareLight(), getActivePackedOverlay());
                currentBlendMode = _savedBlend;
            });
        } else {
            model.render(pose, buf,
                x, y, z, yaw, pitch, roll, scale,
                getGuiAwareLight(), getActivePackedOverlay());
        }
    }

    // ── BEWRL (registered models) ──────────────────────────────────────────────

    /**
     * Get a registered BEWRL model by id ("modid:model_id").
     * Returns a COPY so you can safely attach an AnimationController
     * or modify it without affecting the cached original.
     * Returns null if not registered or load failed.
     */
    public static FomekBEWRL.Model getRegisteredModel(String modelId) {
        return FomekBEWRL.Registry.INSTANCE.getModelCopy(modelId);
    }

    /**
     * Convenience: render a registered model directly by id.
     */
    public static void renderRegisteredModel(String modelId,
            float x, float y, float z,
            float yaw, float pitch, float roll,
            float scale) {
        FomekBEWRL.Model model = FomekBEWRL.Registry.INSTANCE.getModel(modelId);
        if (model != null && !model.isEmpty()) {
            renderBEWRL(model, x, y, z, yaw, pitch, roll, scale);
        }
    }

    /**
     * Convenience: render a registered model with an AnimationController.
     */
    public static void renderRegisteredModel(String modelId, FomekAnimation.Controller controller,
            float x, float y, float z,
            float yaw, float pitch, float roll,
            float scale) {
        FomekBEWRL.Model model = FomekBEWRL.Registry.INSTANCE.getModel(modelId);
        PoseStack activePose = getActivePoseStack();
        MultiBufferSource activeBuf = getActiveBufferSource();
        if (model == null || model.isEmpty() || activePose == null) return;

        if (controller != null && controller.isPlaying()) {
            // Animated render — controller has a playing animation
            model.renderAnimated(
                activePose, activeBuf,
                x, y, z, yaw, pitch, roll, scale,
                getGuiAwareLight(), getActivePackedOverlay(),
                controller, getGameTime(), getPartialTick());
        } else {
            // Normal render (no animation or no controller)
            renderBEWRL(model, x, y, z, yaw, pitch, roll, scale);
        }
    }

    public static boolean isModelRegistered(String modelId) {
        return FomekBEWRL.Registry.INSTANCE.isRegistered(modelId);
    }

    // ── Text rendering ─────────────────────────────────────────────────────────

    /**
     * Backwards-compatible overload — delegates with startYaw/Pitch/Roll = 0.
     */
    public static void renderText(String text,
            float x, float y, float z,
            float yaw, float pitch, float roll,
            float scale,
            int color,
            boolean glowing) {
        renderText(text, x, y, z, yaw, pitch, roll, scale, color, glowing, 0f, 0f, 0f);
    }

    /**
     * Renders text with optional start rotation applied BEFORE translation.
     *
     * When startYaw/Pitch/Roll are non-zero, the rotation is applied first,
     * creating a local coordinate space that matches the item's facing direction:
     *   - z+0.2 moves "forward" relative to the item
     *   - x+0.2 moves "right" relative to the item
     *   - y+0.2 moves "up"
     *
     * Pass 0,0,0 for all three for world-space coordinates (original behavior).
     * Pass getItemDisplayYaw(), getItemDisplayPitch(), getItemDisplayRoll()
     * to auto-match the item's display settings.
     */
    public static void renderText(String text,
            float x, float y, float z,
            float yaw, float pitch, float roll,
            float scale,
            int color,
            boolean glowing,
            float startYaw, float startPitch, float startRoll) {
        PoseStack poseStack = getActivePoseStack();
        MultiBufferSource bufferSource = getActiveBufferSource();
        if (poseStack == null || bufferSource == null || text == null || text.isEmpty()) return;

        Font font = Minecraft.getInstance().font;
        int packedLight = getActivePackedLight();

        poseStack.pushPose();

        // ── Apply start rotation FIRST so translation happens in local space ──
        if (startYaw   != 0) poseStack.mulPose(Axis.YP.rotationDegrees(startYaw));
        if (startPitch != 0) poseStack.mulPose(Axis.XP.rotationDegrees(startPitch));
        if (startRoll  != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(startRoll));

        // ── Now translate in the (possibly rotated) coordinate space ──
        poseStack.translate(x, y, z);

        // ── Apply the text's own rotation (for orienting the text itself) ──
        if (yaw   != 0) poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        if (pitch != 0) poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        if (roll  != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(roll));

        float s = scale / 160f;
        poseStack.scale(s, -s, s);

        float textWidth = font.width(text);
        float drawX = -textWidth / 2f;
        float drawY = -font.lineHeight / 2f;

        int argbColor = color;
        if ((argbColor >> 24 & 0xFF) == 0) {
            argbColor = (0xFF << 24) | (argbColor & 0x00FFFFFF);
        }

        Matrix4f matrix = poseStack.last().pose();
        Font.DisplayMode displayMode = glowing ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;

        font.drawInBatch(text, drawX, drawY, argbColor, false,
            matrix, bufferSource, displayMode, 0, packedLight);

        poseStack.popPose();

        // Flush immediately in world context so GL state changes before this call apply.
        if (currentWorldContext != null && bufferSource instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }
    }

    // ── Java Model rendering ───────────────────────────────────────────────────

    /**
     * Add a text string as a BEWRL part.
     * Uses MC's font renderer (font.drawInBatch) to draw text in 3D item space.
     * scale=1 is roughly the same scale as the existing renderText block.
     */
    public static void addBEWRLTextPart(String text, boolean glowing,
            float x, float y, float z, float yaw, float pitch, float roll,
            float xscale, float yscale, float zscale, int color) {
        if (currentBEWRL == null || text == null || text.isEmpty()) return;
        currentBEWRL.addTextPart(text, glowing, x, y, z, yaw, pitch, roll,
            xscale, yscale, zscale, color);
    }

    // ── BEWRL Vec3-accepting overloads ──────────────────────────────────────────

    public static void addBEWRLPart(FomekRenderAPI.Shape shape, net.minecraft.resources.Identifier texture,
            FomekBEWRLVars.Vec3 pos, FomekBEWRLVars.Vec3 rot, FomekBEWRLVars.Vec3 scale,
            int color, String renderType) {
        if (currentBEWRL == null || shape == null) return;
        currentBEWRL.addPart(shape, texture != null ? texture.toString() : "",
            pos.x, pos.y, pos.z, rot.x, rot.y, rot.z,
            scale.x, scale.y, scale.z, color, renderType);
    }

    public static void addBEWRLJavaPart(String javaModelName, net.minecraft.resources.Identifier texture,
            FomekBEWRLVars.Vec3 pos, FomekBEWRLVars.Vec3 rot, FomekBEWRLVars.Vec3 scale,
            int color, String renderType) {
        if (currentBEWRL == null || javaModelName == null) return;
        currentBEWRL.addJavaModelPart(javaModelName, texture != null ? texture.toString() : "",
            pos.x, pos.y, pos.z, rot.x, rot.y, rot.z,
            scale.x, scale.y, scale.z, color, renderType);
    }

    public static void addBEWRLTextPart(String text, boolean glowing,
            FomekBEWRLVars.Vec3 pos, FomekBEWRLVars.Vec3 rot, FomekBEWRLVars.Vec3 scale,
            int color) {
        if (currentBEWRL == null || text == null || text.isEmpty()) return;
        currentBEWRL.addTextPart(text, glowing,
            pos.x, pos.y, pos.z, rot.x, rot.y, rot.z,
            scale.x, scale.y, scale.z, color);
    }

    public static void addBEWRLItemPart(net.minecraft.world.item.ItemStack itemStack, boolean glowing,
            FomekBEWRLVars.Vec3 pos, FomekBEWRLVars.Vec3 rot, FomekBEWRLVars.Vec3 scale) {
        if (currentBEWRL == null || itemStack == null || itemStack.isEmpty()) return;
        currentBEWRL.addItemPart(itemStack, glowing,
            pos.x, pos.y, pos.z, rot.x, rot.y, rot.z,
            scale.x, scale.y, scale.z);
    }

    // ── BEWRL Child Part (nesting BEWRL inside BEWRL) ─────────────────────────────

    /**
     * Add a child BEWRL model as a part inside the current BEWRL being built.
     * The child model is rendered with its own transform relative to the parent.
     * Shader is optional — null means no shader.
     */
    public static void addBEWRLChildPart(FomekBEWRL.Model childModel, FomekShader shader,
            FomekBEWRLVars.Vec3 pos, FomekBEWRLVars.Vec3 rot, FomekBEWRLVars.Vec3 scale) {
        if (currentBEWRL == null || childModel == null || childModel.isEmpty()) return;
        currentBEWRL.addChildPart(childModel, shader,
            pos.x, pos.y, pos.z, rot.x, rot.y, rot.z,
            scale.x, scale.y, scale.z);
    }

    /**
     * Directly add a Part to an existing BEWRL model (not the builder).
     * Smart-detects the part type:
     * - FomekBEWRL.Model → added as child BEWRL part (shader applied if provided)
     * - FomekRenderAPI.Shape → added as shape part (shader ignored, not supported on shapes)
     * - FomekBEWRL.Model.Part → added as-is (already constructed)
     */
    public static void addPartDirect(FomekBEWRL.Model targetModel, Object part,
            FomekBEWRLVars.Vec3 pos, FomekBEWRLVars.Vec3 rot, FomekBEWRLVars.Vec3 scale,
            FomekShader shader) {
        if (targetModel == null || part == null) return;

        if (part instanceof FomekBEWRL.Model childModel) {
            // Adding a BEWRL as child — shader supported
            if (childModel.isEmpty()) return;
            targetModel.addChildPart(childModel, shader,
                pos.x, pos.y, pos.z, rot.x, rot.y, rot.z,
                scale.x, scale.y, scale.z);
        } else if (part instanceof FomekRenderAPI.Shape shape) {
            // Adding a shape — shader NOT supported on shapes, ignore it
            targetModel.addPart(shape, "", pos.x, pos.y, pos.z, rot.x, rot.y, rot.z,
                scale.x, scale.y, scale.z, -1, "entityCutoutNoCull");
        } else if (part instanceof net.minecraft.world.item.ItemStack itemStack) {
            // Adding an ItemStack as an item part
            targetModel.addItemPart(itemStack, false,
                pos.x, pos.y, pos.z, rot.x, rot.y, rot.z,
                scale.x, scale.y, scale.z);
        } else if (part instanceof FomekBEWRL.Model.Part existingPart) {
            // Adding a pre-constructed Part (e.g. from iterator) — clone it with new transform
            FomekBEWRL.Model.Part copy = existingPart.copy();
            copy.x = pos.x; copy.y = pos.y; copy.z = pos.z;
            copy.yaw = rot.x; copy.pitch = rot.y; copy.roll = rot.z;
            copy.xscale = scale.x; copy.yscale = scale.y; copy.zscale = scale.z;
            if (copy.childModel != null && shader != null) {
                copy.childShader = shader;
            }
            targetModel.getParts().add(copy);
        }
    }

    /**
     * Remove a specific Part from a BEWRL model.
     */
    public static void removePartFromBEWRL(FomekBEWRL.Model model, FomekBEWRL.Model.Part part) {
        if (model == null || part == null) return;
        model.getParts().remove(part);
    }

    /**
     * Deep-copy all parts from source model into target model (replaces target's parts).
     */
    public static void setBEWRL(FomekBEWRL.Model target, FomekBEWRL.Model source) {
        if (target == null || source == null) return;
        target.getParts().clear();
        target.getParts().addAll(source.copy().getParts());
    }


    // ── Model reconstruction (vanilla → BEWRL shapes) ───────────────────────────
    //
    // These helpers extract geometry from vanilla Minecraft models (items, blocks)
    // and convert them to BEWRL Shape parts. Once in Shape format, ALL shader
    // effects (color, transparency, render type, texture, glowing) fully apply
    // through the normal shape render path — no limitations like item parts.
    //
    // Usage:
    //   Model m = FomekRenderAPI.reconstructItemAsBEWRL(itemStack);
    //   FomekRenderAPI.renderBEWRL(m, shader, pos, rot, scale);
    //
    // The geometry is in model space (0-16 for items, 0-1 for blocks).
    // Apply the item display rotation yourself via applyItemDisplayRotation()
    // if you want to match the original orientation.

    /**
     * Reconstruct a vanilla item model as a BEWRL Model with shape parts.
     * Uses the item's ResolvedModel quads, extracting position, UV, and color.
     * Texture is set to the block atlas (where item textures are baked).
     *
     * @param itemStack The item to reconstruct
     * @return A BEWRL Model containing shape parts with the item's geometry
     */
    public static FomekBEWRL.Model reconstructItemAsBEWRL(ItemStack itemStack) {
        return reconstructItemAsBEWRL(itemStack, false); // rimOnly = false (full model)
    }

    /**
     * Reconstruct a vanilla item as a BEWRL model.
     *
     * @param rimOnly  when true, only silhouette (boundary) pixels are emitted —
     *                 pixels with at least one transparent or out-of-bounds neighbor.
     *                 When false (default), all opaque pixels are emitted, producing
     *                 the full item shape.
     */
    public static FomekBEWRL.Model reconstructItemAsBEWRL(ItemStack itemStack, boolean rimOnly) {
        if (itemStack == null || itemStack.isEmpty()) return new FomekBEWRL.Model();

        LivingEntity entity = getEntity();
        Level level = getWorld();
        try {
            ResolvedModel bakedModel = Minecraft.getInstance().getItemRenderer()
                    .getModel(itemStack, level, entity, 0);
            FomekBEWRL.Model _result = reconstructBakedModel(bakedModel, true, true, rimOnly);
            _result.sourceItemStack = itemStack;
            return _result;
        } catch (Exception e) {
            return new FomekBEWRL.Model();
        }
    }

    /**
     * Apply the current item's display transform (rotation + scale + translation)
     * plus the -0.5 centering offset to the active PoseStack, exactly like
     * Minecraft's ItemRenderer.renderItem() does internally.
     *
     * This is called automatically by renderBEWRL when rendering inside an item
     * render context, so reconstructed item models appear at the correct position,
     * scale, and rotation without the user calling pushItemDisplayTransform.
     */
    private static boolean autoApplyItemDisplayTransform() {
        if (currentContext == null) return false;
        try {
            ItemStack stack = currentContext.getItemStack();
            if (stack == null || stack.isEmpty()) return false;
            ItemDisplayContext ctx = currentContext.getDisplayContext();
            if (ctx == null) ctx = ItemDisplayContext.NONE;
            PoseStack pose = getActivePoseStack();
            if (pose == null) return false;

            // GUI context: the display transform (rotation + scale) is ALREADY
            // applied by the caller (GuiGraphicsExtractor.renderItem) before the mixin
            // fires at HEAD of ItemRenderer.render(). We only need the -0.5
            // centering offset that render() would have applied.
            if (ctx == ItemDisplayContext.GUI) {
                pose.pushPose();
                pose.translate(-0.5f, -0.5f, -0.5f);
                return true;
            }

            // Non-GUI contexts (GROUND, FIRST_PERSON, THIRD_PERSON, etc.):
            // The mixin fires at HEAD of ItemRenderer.renderStatic(), BEFORE
            // the display transform is applied. We must apply both the
            // display transform AND the -0.5 centering offset.
            LivingEntity entity = getEntity();
            Level level = getWorld();
            ResolvedModel bakedModel = Minecraft.getInstance().getItemRenderer()
                    .getModel(stack, level, entity, 0);
            ItemTransform transform = bakedModel.wrapped().transforms().getTransform(ctx);
            if (transform != null && transform != ItemTransform.NO_TRANSFORM) {
                pose.pushPose();
                transform.apply(false, pose);
                pose.translate(-0.5f, -0.5f, -0.5f);
                return true;
            } else {
                // No transform defined for this context — still center.
                pose.pushPose();
                pose.translate(-0.5f, -0.5f, -0.5f);
                return true;
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    private static void autoPopItemDisplayTransform() {
        PoseStack pose = getActivePoseStack();
        if (pose != null) pose.popPose();
    }

    /**
     * Reconstruct a vanilla block model as a BEWRL Model with shape parts.
     * Uses the block's ResolvedModel quads, extracting position, UV, and color.
     * Texture is set to the block atlas.
     *
     * @param blockState The block state to reconstruct
     * @return A BEWRL Model containing shape parts with the block's geometry
     */
    public static FomekBEWRL.Model reconstructBlockAsBEWRL(BlockState blockState) {
        if (blockState == null) return new FomekBEWRL.Model();
        try {
            ResolvedModel bakedModel = Minecraft.getInstance().getBlockRenderer()
                    .getBlockModel(blockState);
            return reconstructBakedModel(bakedModel);
        } catch (Exception e) {
            return new FomekBEWRL.Model();
        }
    }

    /**
     * Internal: extract quads from a ResolvedModel and convert to BEWRL shape parts.
     * Handles both null-direction (general) and per-face quads.
     */
    private static FomekBEWRL.Model reconstructBakedModel(ResolvedModel bakedModel) {
        return reconstructBakedModel(bakedModel, false);
    }

    private static FomekBEWRL.Model reconstructBakedModel(ResolvedModel bakedModel, boolean itemModel) {
        return reconstructBakedModel(bakedModel, itemModel, true); // always include back face
    }

    /**
     * @param keepBackFace  when false (default for items), the NORTH-facing (back) quads
     *                      of a generated flat item are dropped. This means:
     *                      - With a translucent shader: only ONE layer of transparency
     *                        per pixel (no double-overlay "sandwich" from front+back).
     *                      - With a custom shader texture: the texture appears once on
     *                        the front face, not mirrored on a back layer behind it.
     *                      Pass true only if you explicitly need a double-sided result.
     */
    private static boolean[] readPixelAlphaMask(
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
            int w, int h) {
        if (sprite == null || w <= 0 || h <= 0) return null;
        try {
            boolean[] mask = new boolean[w * h];
            for (int py = 0; py < h; py++) {
                for (int px = 0; px < w; px++) {
                    int pixel = sprite.getPixelRGBA(0, px, py);
                    mask[py * w + px] = ((pixel >> 24) & 0xFF) > 0;
                }
            }
            return mask;
        } catch (Exception ignored) {
            return null; // can't read pixels — emit all per-pixel quads
        }
    }

    /**
     * Internal: extract quads from a ResolvedModel and convert to BEWRL shape parts.
     *
     * For ITEM MODELS: face quads (SOUTH/NORTH) are SUBDIVIDED into per-pixel
     * 1x1 quads (spriteW x spriteH grid, skipping transparent pixels), so the
     * energySwirl shader animates each face pixel independently.  All quads go
     * into ONE shape part with lockTexture=false so the shader treats all faces
     * identically.  Edge quads (between-pixel walls) are NOT emitted.
     *
     * For BLOCK MODELS (itemModel=false): all quads go into the single shape as-is.
     */
    private static FomekBEWRL.Model reconstructBakedModel(ResolvedModel bakedModel,
                                                           boolean itemModel,
                                                           boolean keepBackFace) {
        return reconstructBakedModel(bakedModel, itemModel, keepBackFace, false);
    }

    private static FomekBEWRL.Model reconstructBakedModel(ResolvedModel bakedModel,
                                                           boolean itemModel,
                                                           boolean keepBackFace,
                                                           boolean rimOnly) {
        FomekBEWRL.Model bewrl = new FomekBEWRL.Model();

        RandomSource random = RandomSource.create();
        java.util.List<BakedQuad> allQuads = new java.util.ArrayList<>();
        allQuads.addAll(bakedModel.getQuads(null, null, random));
        // Fetch direction-culled quads for ALL models — vanilla's generated item model
        // puts SOUTH/NORTH face quads under their respective Direction slots, not null.
        for (Direction dir : Direction.values()) {
            allQuads.addAll(bakedModel.getQuads(null, dir, random));
        }

        if (allQuads.isEmpty()) return bewrl;

        // ── Step 1: resolve the item's own texture path and sprite UV bounds ────
        String texture = "minecraft:textures/atlas/blocks.png";
        float spriteU0 = 0f, spriteV0 = 0f, spriteU1 = 1f, spriteV1 = 1f;
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = null;
        try {
            sprite = allQuads.get(0).getSprite();
            String spriteName = sprite.contents().name().toString();
            int colon = spriteName.indexOf(':');
            if (colon >= 0) {
                String namespace = spriteName.substring(0, colon);
                String path = spriteName.substring(colon + 1);
                texture = namespace + ":textures/" + path + ".png";
            }
            spriteU0 = sprite.getU0();
            spriteV0 = sprite.getV0();
            spriteU1 = sprite.getU1();
            spriteV1 = sprite.getV1();
        } catch (Exception ignored) {}

        float spriteURange = (spriteU1 - spriteU0) > 1e-8f ? (spriteU1 - spriteU0) : 1f;
        float spriteVRange = (spriteV1 - spriteV0) > 1e-8f ? (spriteV1 - spriteV0) : 1f;

        // ── Step 1b: read sprite dimensions and per-pixel alpha mask ──────────
        int spriteW = 16, spriteH = 16;
        boolean[] pixelOpaque = null;
        if (sprite != null && itemModel) {
            try {
                spriteW = sprite.contents().width();
                spriteH = sprite.contents().height();
            } catch (Exception ignored) {}
            pixelOpaque = readPixelAlphaMask(sprite, spriteW, spriteH);
        }

        // ── Step 2: build a single shape with all quads ──────────────────────
        FomekRenderAPI.Shape shape = new FomekRenderAPI.Shape();
        shape.begin(VertexFormat.Mode.QUADS, true);

        // ── Categorize quads by face normal ─────────────────────────────────
        // Vanilla's generated item model emits all quads as null-direction in
        // getQuads(null, null, random). We CANNOT rely on quad.getDirection() to
        // detect face vs edge quads — it's null for all of them.
        // Instead, compute the face normal from vertex positions:
        //   |nz| > 0.5  → face quad (SOUTH/front or NORTH/back)
        //   |nz| <= 0.5 → edge quad (left/right/top/bottom thin slab)
        java.util.List<BakedQuad> faceQuads = new java.util.ArrayList<>();
        java.util.List<BakedQuad> edgeQuads = new java.util.ArrayList<>();

        for (BakedQuad quad : allQuads) {
            int[] data = quad.getVertices();
            if (data == null || data.length < 8) { edgeQuads.add(quad); continue; }
            int stride = data.length / 4;

            // Compute normal from first 3 vertices via cross product
            float ax = Float.intBitsToFloat(data[0]),          ay = Float.intBitsToFloat(data[1]),          az = Float.intBitsToFloat(data[2]);
            float bx = Float.intBitsToFloat(data[stride]),     by = Float.intBitsToFloat(data[stride+1]),   bz = Float.intBitsToFloat(data[stride+2]);
            float cx = Float.intBitsToFloat(data[stride*2]),   cy = Float.intBitsToFloat(data[stride*2+1]), cz = Float.intBitsToFloat(data[stride*2+2]);
            float ux=bx-ax, uy=by-ay, uz=bz-az;
            float vx=cx-ax, vy=cy-ay, vz=cz-az;
            float nz = ux*vy - uy*vx; // only need Z component to decide face vs edge
            // Also check quad.getDirection() as a fallback (may be set on some models)
            Direction qd = quad.getDirection();
            boolean isFace = (Math.abs(nz) > 0.5f)
                          || (qd == Direction.SOUTH || qd == Direction.NORTH);
            if (isFace) {
                faceQuads.add(quad);
            } else {
                edgeQuads.add(quad);
            }
        }

        // ── Determine item face bounds from a face quad ───────────────────────
        // All face quads share the same XY bounds (the item's model-space footprint).
        // We read minX/maxX/minY/maxY once from the first face quad.
        float faceMinX = 0f, faceMaxX = 1f, faceMinY = 0f, faceMaxY = 1f;
        float frontZ = 0f, backZ = 0f;
        boolean foundFront = false, foundBack = false;
        for (BakedQuad quad : faceQuads) {
            int[] data = quad.getVertices();
            if (data == null || data.length < 4) continue;
            int stride = data.length / 4;
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
            float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            float fz = Float.intBitsToFloat(data[2]);
            for (int v = 0; v < 4; v++) {
                int base = v * stride;
                float vx = Float.intBitsToFloat(data[base]);
                float vy = Float.intBitsToFloat(data[base + 1]);
                if (vx < minX) minX = vx;
                if (vx > maxX) maxX = vx;
                if (vy < minY) minY = vy;
                if (vy > maxY) maxY = vy;
            }
            faceMinX = minX; faceMaxX = maxX;
            faceMinY = minY; faceMaxY = maxY;
            // Detect front (nz>0) vs back (nz<0) using computed normal
            if (data != null && data.length >= 8) {
                float ax2 = Float.intBitsToFloat(data[0]),        ay2 = Float.intBitsToFloat(data[1]);
                float bx2 = Float.intBitsToFloat(data[stride]),   by2 = Float.intBitsToFloat(data[stride+1]);
                float cx2 = Float.intBitsToFloat(data[stride*2]), cy2 = Float.intBitsToFloat(data[stride*2+1]);
                float ux2=bx2-ax2, uy2=by2-ay2;
                float vx2=cx2-ax2, vy2=cy2-ay2;
                float nz2 = ux2*vy2 - uy2*vx2;
                if (nz2 > 0 && !foundFront) { frontZ = fz; foundFront = true; }
                if (nz2 < 0 && !foundBack)  { backZ  = fz; foundBack  = true; }
            }
        }
        float faceSpanX = faceMaxX - faceMinX;
        float faceSpanY = faceMaxY - faceMinY;


        // ── Helper: check if a pixel is on the mantle (boundary of opaque region) ──
        // A pixel is "mantle" if at least one of its 4 neighbors is transparent or out of bounds.
        // Interior pixels (all neighbors opaque) are never visible from outside and can be
        // skipped for the back face to avoid "inside pixels" visible through transparency.
        boolean[] mantleMask = null;
        if (pixelOpaque != null && itemModel && spriteW > 0 && spriteH > 0) {
            mantleMask = new boolean[spriteW * spriteH];
            for (int py = 0; py < spriteH; py++) {
                for (int px = 0; px < spriteW; px++) {
                    int idx = py * spriteW + px;
                    if (!pixelOpaque[idx]) { mantleMask[idx] = false; continue; }
                    // Check 4 neighbors
                    boolean onBoundary =
                        (px == 0 || !pixelOpaque[idx - 1]) ||              // left
                        (px == spriteW - 1 || !pixelOpaque[idx + 1]) ||    // right
                        (py == 0 || !pixelOpaque[idx - spriteW]) ||        // top
                        (py == spriteH - 1 || !pixelOpaque[idx + spriteW]); // bottom
                    mantleMask[idx] = onBoundary;
                }
            }
        }

        // ── Emit face quads
        // UV assignment uses POSITION-DERIVED coords so the swirl pattern is
        // continuous across the seam where edge quads meet face quads:
        //   U = (worldX - faceMinX) / faceSpanX   (0 at left, 1 at right)
        //   V = (faceMaxY - worldY) / faceSpanY   (0 at top, 1 at bottom — sprite convention)
        // Edge quads use the same formula below, derived from their vertices,
        // so energySwirl's (xOff, zOff) shifts all faces by the same amount → seamless.
        boolean emittedFront = false, emittedBack = false;
        for (BakedQuad quad : faceQuads) {
            if (!itemModel) break; // block model: handled in edge loop below

            // Determine front vs back by computed normal (nz > 0 = front/SOUTH, nz < 0 = back/NORTH)
            boolean isFront = true;
            {
                int[] fdata = quad.getVertices();
                if (fdata != null && fdata.length >= 8) {
                    int fstride = fdata.length / 4;
                    float ax2 = Float.intBitsToFloat(fdata[0]),        ay2 = Float.intBitsToFloat(fdata[1]),        az2 = Float.intBitsToFloat(fdata[2]);
                    float bx2 = Float.intBitsToFloat(fdata[fstride]),   by2 = Float.intBitsToFloat(fdata[fstride+1]),bz2 = Float.intBitsToFloat(fdata[fstride+2]);
                    float cx2 = Float.intBitsToFloat(fdata[fstride*2]), cy2 = Float.intBitsToFloat(fdata[fstride*2+1]);
                    float ux2=bx2-ax2, uy2=by2-ay2;
                    float vx2=cx2-ax2, vy2=cy2-ay2;
                    float nz2 = ux2*vy2 - uy2*vx2;
                    isFront = (nz2 >= 0);
                }
                // Also respect Direction if set
                Direction qd = quad.getDirection();
                if (qd == Direction.SOUTH) isFront = true;
                if (qd == Direction.NORTH) isFront = false;
            }

            if (!isFront && !keepBackFace) continue;
            // Skip duplicate face quads — only emit per-pixel face quads ONCE
            // for front and ONCE for back, even if the model has multiple layers.
            // Multiple front quads at the same frontZ would create coplanar
            // per-pixel quads → Z-fighting.
            if (isFront && emittedFront) continue;
            if (!isFront && emittedBack) continue;
            if (isFront) emittedFront = true;
            else emittedBack = true;

            float faceZ = isFront ? frontZ : backZ;
            float fnx = 0f;
            float fny = 0f;
            float fnz = isFront ? 1f : -1f;

            int faceColor = -1;
            {
                int[] data = quad.getVertices();
                if (data != null && data.length >= 4) {
                    int mcColor = data[3]; // vertex 0 color
                    int a = (mcColor >> 24) & 0xFF; if (a == 0) a = 255;
                    int b = (mcColor >> 16) & 0xFF;
                    int g = (mcColor >> 8)  & 0xFF;
                    int r =  mcColor        & 0xFF;
                    faceColor = (a << 24) | (r << 16) | (g << 8) | b;
                }
            }

            for (int py = 0; py < spriteH; py++) {
                for (int px = 0; px < spriteW; px++) {
                    if (pixelOpaque != null && !pixelOpaque[py * spriteW + px]) continue;
                    // rimOnly: skip any pixel that is NOT on the outer silhouette (both front and back).
                    // (Interior = all 4 neighbors are opaque — mantleMask[idx] is false for those.)
                    boolean isMantlePixel = mantleMask != null && mantleMask[py * spriteW + px];
                    if (rimOnly && mantleMask != null && !isMantlePixel) continue;

                    // World coords for this pixel quad
                    float wx0 = faceMinX + (float) px       / spriteW * faceSpanX;
                    float wx1 = faceMinX + (float)(px + 1)  / spriteW * faceSpanX;
                    float wy0 = faceMaxY - (float) py       / spriteH * faceSpanY; // top of pixel
                    float wy1 = faceMaxY - (float)(py + 1)  / spriteH * faceSpanY; // bottom of pixel

                    // Position-derived UVs (same formula as edge quads below)
                    float u0 = (wx0 - faceMinX) / faceSpanX;
                    float u1 = (wx1 - faceMinX) / faceSpanX;
                    float v0 = (faceMaxY - wy0) / faceSpanY;
                    float v1 = (faceMaxY - wy1) / faceSpanY;

                    // Single quad (4 verts) — entityCutoutNoCull renders both sides.
                    // Double-winding (8 verts) was removed: it created a second coplanar
                    // quad at the same depth, causing Z-fighting with edge walls at
                    // the shared pixel boundary.
                    shape.addVertexUVNormal(wx0, wy1, faceZ, u0, v1, fnx, fny, fnz, faceColor);
                    shape.addVertexUVNormal(wx1, wy1, faceZ, u1, v1, fnx, fny, fnz, faceColor);
                    shape.addVertexUVNormal(wx1, wy0, faceZ, u1, v0, fnx, fny, fnz, faceColor);
                    shape.addVertexUVNormal(wx0, wy0, faceZ, u0, v0, fnx, fny, fnz, faceColor);
                }
            }
        }

        // ── Emit edge quads — per-pixel outer silhouette walls ─────────────────
        // For ITEM MODELS: generate edges from the per-pixel mask. For each opaque
        // pixel, emit a wall ONLY on sides where the neighbor is transparent or
        // out-of-bounds. Inner walls (all 4 neighbors opaque) are skipped.
        // UVs match the face quad UVs at each shared edge — the swirl texture
        // flows seamlessly from front face onto edges.
        // Single quad per wall (4 verts) — the no-cull render type handles both sides.
        //
        // For BLOCK MODELS or when frontZ/backZ can't be detected: emit baked quads.
        if (false && itemModel && pixelOpaque != null && spriteW > 0 && spriteH > 0
                && faceSpanX > 1e-8f && faceSpanY > 1e-8f
                && foundFront && foundBack) {
            int ec = 0xFFFFFFFF;
            // Extend wall Z BEYOND both face planes so walls always win the depth
            // test at the face-wall boundary. 10% of item thickness — invisible
            // (0.006 units on a standard 0.0625-thick item) but enough separation
            // 1% is enough — the main Z-fight was duplicate face quads, now fixed.
            float thick = Math.abs(frontZ - backZ);
            float zExt = thick * 0.01f;
            float zf = frontZ > backZ ? frontZ + zExt : frontZ - zExt;
            float zb = backZ  > frontZ ? backZ  + zExt : backZ  - zExt;

            // ── Merged wall emission ──────────────────────────────────────────
            // Instead of one tiny wall per pixel (which creates Z-fighting at
            // seams between adjacent walls), scan each column/row and merge
            // contiguous open sides into a single continuous wall strip.
            
            // Left walls: scan each column (fixed px), merge contiguous py with leftOpen
            for (int px = 0; px < spriteW; px++) {
                int runStart = -1;
                for (int py = 0; py <= spriteH; py++) {
                    boolean open = (py < spriteH)
                        && pixelOpaque[py * spriteW + px]
                        && (px == 0 || !pixelOpaque[py * spriteW + px - 1]);
                    if (open && runStart < 0) {
                        runStart = py;
                    } else if (!open && runStart >= 0) {
                        // Emit merged left wall from runStart to py-1
                        float wWy0 = faceMaxY - (float) runStart / spriteH * faceSpanY;
                        float wWy1 = faceMaxY - (float) py / spriteH * faceSpanY;
                        float wX = faceMinX + (float) px / spriteW * faceSpanX;
                        float wU = (wX - faceMinX) / faceSpanX;
                        float wV0 = (faceMaxY - wWy0) / faceSpanY;
                        float wV1 = (faceMaxY - wWy1) / faceSpanY;
                        shape.addVertexUVNormal(wX, wWy1, zf, wU, wV1, -1, 0, 0, ec);
                        shape.addVertexUVNormal(wX, wWy1, zb, wU, wV1, -1, 0, 0, ec);
                        shape.addVertexUVNormal(wX, wWy0, zb, wU, wV0, -1, 0, 0, ec);
                        shape.addVertexUVNormal(wX, wWy0, zf, wU, wV0, -1, 0, 0, ec);
                        runStart = -1;
                    }
                }
            }

            // Right walls: scan each column (fixed px), merge contiguous py with rightOpen
            for (int px = 0; px < spriteW; px++) {
                int runStart = -1;
                for (int py = 0; py <= spriteH; py++) {
                    boolean open = (py < spriteH)
                        && pixelOpaque[py * spriteW + px]
                        && (px == spriteW - 1 || !pixelOpaque[py * spriteW + px + 1]);
                    if (open && runStart < 0) {
                        runStart = py;
                    } else if (!open && runStart >= 0) {
                        float wWy0 = faceMaxY - (float) runStart / spriteH * faceSpanY;
                        float wWy1 = faceMaxY - (float) py / spriteH * faceSpanY;
                        float wX = faceMinX + (float)(px + 1) / spriteW * faceSpanX;
                        float wU = (wX - faceMinX) / faceSpanX;
                        float wV0 = (faceMaxY - wWy0) / faceSpanY;
                        float wV1 = (faceMaxY - wWy1) / faceSpanY;
                        shape.addVertexUVNormal(wX, wWy1, zf, wU, wV1, 1, 0, 0, ec);
                        shape.addVertexUVNormal(wX, wWy0, zf, wU, wV0, 1, 0, 0, ec);
                        shape.addVertexUVNormal(wX, wWy0, zb, wU, wV0, 1, 0, 0, ec);
                        shape.addVertexUVNormal(wX, wWy1, zb, wU, wV1, 1, 0, 0, ec);
                        runStart = -1;
                    }
                }
            }

            // Top walls: scan each row (fixed py), merge contiguous px with topOpen
            for (int py = 0; py < spriteH; py++) {
                int runStart = -1;
                for (int px = 0; px <= spriteW; px++) {
                    boolean open = (px < spriteW)
                        && pixelOpaque[py * spriteW + px]
                        && (py == 0 || !pixelOpaque[(py - 1) * spriteW + px]);
                    if (open && runStart < 0) {
                        runStart = px;
                    } else if (!open && runStart >= 0) {
                        float wWx0 = faceMinX + (float) runStart / spriteW * faceSpanX;
                        float wWx1 = faceMinX + (float) px / spriteW * faceSpanX;
                        float wY = faceMaxY - (float) py / spriteH * faceSpanY;
                        float wU0 = (wWx0 - faceMinX) / faceSpanX;
                        float wU1 = (wWx1 - faceMinX) / faceSpanX;
                        float wV = (faceMaxY - wY) / faceSpanY;
                        shape.addVertexUVNormal(wWx0, wY, zf, wU0, wV, 0, 1, 0, ec);
                        shape.addVertexUVNormal(wWx0, wY, zb, wU0, wV, 0, 1, 0, ec);
                        shape.addVertexUVNormal(wWx1, wY, zb, wU1, wV, 0, 1, 0, ec);
                        shape.addVertexUVNormal(wWx1, wY, zf, wU1, wV, 0, 1, 0, ec);
                        runStart = -1;
                    }
                }
            }

            // Bottom walls: scan each row (fixed py), merge contiguous px with bottomOpen
            for (int py = 0; py < spriteH; py++) {
                int runStart = -1;
                for (int px = 0; px <= spriteW; px++) {
                    boolean open = (px < spriteW)
                        && pixelOpaque[py * spriteW + px]
                        && (py == spriteH - 1 || !pixelOpaque[(py + 1) * spriteW + px]);
                    if (open && runStart < 0) {
                        runStart = px;
                    } else if (!open && runStart >= 0) {
                        float wWx0 = faceMinX + (float) runStart / spriteW * faceSpanX;
                        float wWx1 = faceMinX + (float) px / spriteW * faceSpanX;
                        float wY = faceMaxY - (float)(py + 1) / spriteH * faceSpanY;
                        float wU0 = (wWx0 - faceMinX) / faceSpanX;
                        float wU1 = (wWx1 - faceMinX) / faceSpanX;
                        float wV = (faceMaxY - wY) / faceSpanY;
                        shape.addVertexUVNormal(wWx0, wY, zf, wU0, wV, 0, -1, 0, ec);
                        shape.addVertexUVNormal(wWx1, wY, zf, wU1, wV, 0, -1, 0, ec);
                        shape.addVertexUVNormal(wWx1, wY, zb, wU1, wV, 0, -1, 0, ec);
                        shape.addVertexUVNormal(wWx0, wY, zb, wU0, wV, 0, -1, 0, ec);
                        runStart = -1;
                    }
                }
            }
        } else {
            // Emit baked edge quads (same Z as face quads = no Z-fighting).
            // For item models, internal edges (both sides opaque) are filtered out
            // using the alpha mask. External edges (silhouette + holes) are kept.
            for (BakedQuad quad : edgeQuads) {
                int[] data = quad.getVertices();
                if (data == null || data.length < 4) continue;
                int stride = data.length / 4;

                float nx = 0, ny = 0, nz = 0;
                Direction faceDir = quad.getDirection();
                if (faceDir != null) {
                    nx = faceDir.step().x();
                    ny = faceDir.step().y();
                    nz = faceDir.step().z();
                } else {
                    try {
                        float eax = Float.intBitsToFloat(data[0]),          eay = Float.intBitsToFloat(data[1]),          eaz = Float.intBitsToFloat(data[2]);
                        float ebx = Float.intBitsToFloat(data[stride]),     eby = Float.intBitsToFloat(data[stride+1]),   ebz = Float.intBitsToFloat(data[stride+2]);
                        float ecx = Float.intBitsToFloat(data[stride*2]),   ecy = Float.intBitsToFloat(data[stride*2+1]), ecz = Float.intBitsToFloat(data[stride*2+2]);
                        float eux=ebx-eax, euy=eby-eay, euz=ebz-eaz;
                        float evx2=ecx-eax, evy2=ecy-eay, evz2=ecz-eaz;
                        nx = euy*evz2 - euz*evy2;
                        ny = euz*evx2 - eux*evz2;
                        nz = eux*evy2 - euy*evx2;
                        float len = (float)Math.sqrt(nx*nx + ny*ny + nz*nz);
                        if (len > 1e-6f) { nx /= len; ny /= len; nz /= len; }
                    } catch (Exception ignored2) {}
                }

                // ── Filter internal edge quads for item models ──────────────
                // An edge quad is "internal" if both sides of the wall are opaque
                // pixels in the sprite's alpha mask. Internal edges create visible
                // lines inside the shape (bad for transparency effects). External
                // edges (silhouette boundary, hole boundaries) are kept.
                if (pixelOpaque != null && itemModel && spriteW > 0 && spriteH > 0
                        && faceSpanX > 1e-8f && faceSpanY > 1e-8f
                        && (Math.abs(nx) > 0.5f || Math.abs(ny) > 0.5f)) {
                    float qx0 = Float.intBitsToFloat(data[0]);
                    float qy0 = Float.intBitsToFloat(data[1]);
                    float qx1 = Float.intBitsToFloat(data[stride]);
                    float qy1 = Float.intBitsToFloat(data[stride + 1]);
                    float qx2 = Float.intBitsToFloat(data[2 * stride]);
                    float qy2 = Float.intBitsToFloat(data[2 * stride + 1]);
                    float qx3 = Float.intBitsToFloat(data[3 * stride]);
                    float qy3 = Float.intBitsToFloat(data[3 * stride + 1]);

                    boolean skip = false;

                    if (Math.abs(nx) > 0.5f) {
                        // Left/right wall: fixed X, spans range in Y
                        float wallX = qx0;
                        int pxBoundary = Math.round((wallX - faceMinX) / faceSpanX * spriteW);
                        float yMin = Math.min(Math.min(qy0, qy1), Math.min(qy2, qy3));
                        float yMax = Math.max(Math.max(qy0, qy1), Math.max(qy2, qy3));
                        int pyTop = Math.round((faceMaxY - yMax) / faceSpanY * spriteH);
                        int pyBot = Math.round((faceMaxY - yMin) / faceSpanY * spriteH);
                        boolean allInternal = (pyBot > pyTop);
                        for (int py = pyTop; py < pyBot && allInternal; py++) {
                            int leftPx = pxBoundary - 1;
                            int rightPx = pxBoundary;
                            boolean leftOpaque = leftPx >= 0 && leftPx < spriteW && py >= 0 && py < spriteH
                                    && pixelOpaque[py * spriteW + leftPx];
                            boolean rightOpaque = rightPx >= 0 && rightPx < spriteW && py >= 0 && py < spriteH
                                    && pixelOpaque[py * spriteW + rightPx];
                            if (!leftOpaque || !rightOpaque) allInternal = false;
                        }
                        skip = allInternal;
                    } else {
                        // Top/bottom wall: fixed Y, spans range in X
                        float wallY = qy0;
                        int pyBoundary = Math.round((faceMaxY - wallY) / faceSpanY * spriteH);
                        float xMin = Math.min(Math.min(qx0, qx1), Math.min(qx2, qx3));
                        float xMax = Math.max(Math.max(qx0, qx1), Math.max(qx2, qx3));
                        int pxLeft = Math.round((xMin - faceMinX) / faceSpanX * spriteW);
                        int pxRight = Math.round((xMax - faceMinX) / faceSpanX * spriteW);
                        boolean allInternal = (pxRight > pxLeft);
                        for (int px = pxLeft; px < pxRight && allInternal; px++) {
                            int topPy = pyBoundary - 1;
                            int botPy = pyBoundary;
                            boolean topOpaque = topPy >= 0 && topPy < spriteH && px >= 0 && px < spriteW
                                    && pixelOpaque[topPy * spriteW + px];
                            boolean botOpaque = botPy >= 0 && botPy < spriteH && px >= 0 && px < spriteW
                                    && pixelOpaque[botPy * spriteW + px];
                            if (!topOpaque || !botOpaque) allInternal = false;
                        }
                        skip = allInternal;
                    }

                    if (skip) continue;
                }

                float[] evx = new float[4], evy = new float[4], evz = new float[4];
                float[] enu = new float[4], env = new float[4];
                int[] ecol = new int[4];
                for (int v = 0; v < 4; v++) {
                    int base = v * stride;
                    evx[v] = Float.intBitsToFloat(data[base]);
                    evy[v] = Float.intBitsToFloat(data[base + 1]);
                    evz[v] = Float.intBitsToFloat(data[base + 2]);
                    int mcColor = data[base + 3];
                    int a = (mcColor >> 24) & 0xFF; if (a == 0) a = 255;
                    int b = (mcColor >> 16) & 0xFF;
                    int g = (mcColor >> 8)  & 0xFF;
                    int r =  mcColor        & 0xFF;
                    ecol[v] = (a << 24) | (r << 16) | (g << 8) | b;
                    float atlasU = Float.intBitsToFloat(data[base + 4]);
                    float atlasV = Float.intBitsToFloat(data[base + 5]);
                    enu[v] = (atlasU - spriteU0) / spriteURange;
                    env[v] = (atlasV - spriteV0) / spriteVRange;
                }
                // Snap edge quad Z to exactly match face Z values so there's no
                // gap where the edge wall meets the front/back face quads. The
                // baked edge Z should already match, but float precision can
                // cause a hairline gap you can see through.
                if (itemModel && foundFront && foundBack) {
                    for (int v = 0; v < 4; v++) {
                        evz[v] = (Math.abs(evz[v] - frontZ) < Math.abs(evz[v] - backZ))
                                ? frontZ : backZ;
                    }
                }
                for (int v = 0; v < 4; v++) {
                    shape.addVertexUVNormal(evx[v], evy[v], evz[v], enu[v], env[v], nx, ny, nz, ecol[v]);
                }
            }
        }

        shape.end();

        // All per-pixel face quads in one shape part,
        // lockTexture=false so shader texture overrides and energySwirl animation
        // apply uniformly to all faces.
        if (!shape.isEmpty()) {
            bewrl.addPart(shape, texture,
                0, 0, 0, 0, 0, 0, 1, 1, 1, -1, "entityCutoutNoCull");
        }

        return bewrl;
    }

    /**
     * Reconstruct a vanilla item model as a BEWRL.
     * This is equivalent to {@link #reconstructItemAsBEWRL(ItemStack)} — the display transform
     * must be applied separately via {@link #pushItemDisplayTransform(ItemStack)} / {@link #popItemDisplayTransform()}.
     *
     * @param itemStack The item to reconstruct
     * @return A BEWRL Model with raw model-space geometry (same as reconstructItemAsBEWRL)
     * @deprecated Use {@link #reconstructItemAsBEWRL(ItemStack)} with
     *             {@link #pushItemDisplayTransform(ItemStack)} instead.
     */
    public static FomekBEWRL.Model reconstructItemAsBEWRLTransformed(ItemStack itemStack) {
        return reconstructItemAsBEWRL(itemStack, false); // rimOnly = false
    }

    /**
     * Reconstruct a vanilla item as a BEWRL model with the rimOnly flag.
     * Use this when you want only the silhouette (boundary) pixels at reconstruction time.
     */
    public static FomekBEWRL.Model reconstructItemAsBEWRLTransformed(ItemStack itemStack, boolean rimOnly) {
        return reconstructItemAsBEWRL(itemStack, rimOnly);
    }

    /**
     * Push the item's display transform for the current render context onto the active PoseStack.
     * Call this BEFORE renderBEWRL() when rendering a reconstructed item model, then call
     * popItemDisplayTransform() AFTER.
     *
     * This replicates what ItemRenderer normally does inside renderStatic() — the mixin fires
     * BEFORE that transform is applied, so reconstructed models render in raw model space (0-1)
     * unless you apply the transform yourself.
     *
     * Example:
     *   pushItemDisplayTransform(itemstack);
     *   renderBEWRL(reconstructedModel, shader, Vec3.of(0,0,0), Vec3.of(0,0,0), Vec3.of(1.05,1.05,1.05));
     *   popItemDisplayTransform();
     *
     * @param itemStack The item whose display transform to apply
     */
    public static void pushItemDisplayTransform(ItemStack itemStack) {
        PoseStack pose = getActivePoseStack();
        if (pose == null || itemStack == null || itemStack.isEmpty()) return;

        LivingEntity entity = getEntity();
        Level level = getWorld();
        ItemDisplayContext ctx = getCurrentDisplayContext();
        if (ctx == null) ctx = ItemDisplayContext.NONE;

        try {
            ResolvedModel model = Minecraft.getInstance().getItemRenderer()
                    .getModel(itemStack, level, entity, 0);
            ItemTransform transform = model.wrapped().transforms().getTransform(ctx);
            if (transform != null && transform != ItemTransform.NO_TRANSFORM) {
                pose.pushPose();
                transform.apply(false, pose);
            } else {
                pose.pushPose(); // still push so pop is always safe
            }
        } catch (Exception e) {
            pose.pushPose(); // always push so pop is safe
        }
    }

    /**
     * Pop the display transform pushed by {@link #pushItemDisplayTransform(ItemStack)}.
     */
    public static void popItemDisplayTransform() {
        PoseStack pose = getActivePoseStack();
        if (pose != null) pose.popPose();
    }

    // DEAD CODE — replaced by pushItemDisplayTransform / popItemDisplayTransform
    private static FomekBEWRL.Model _reconstructItemAsBEWRLTransformed_UNUSED(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) return new FomekBEWRL.Model();

        LivingEntity entity = getEntity();
        Level level = getWorld();
        try {
            ResolvedModel bakedModel = Minecraft.getInstance().getItemRenderer()
                    .getModel(itemStack, level, entity, 0);

            ItemDisplayContext ctx = getCurrentDisplayContext();
            if (ctx == null) ctx = ItemDisplayContext.GUI;

            ItemTransform transform = bakedModel.wrapped().transforms().getTransform(ctx);
            if (transform == null || transform == ItemTransform.NO_TRANSFORM) {
                return reconstructBakedModel(bakedModel);
            }

            float rZ = transform.rotation.z();
            float rY = transform.rotation.y();
            float rX = transform.rotation.x();
            float sX = transform.scale.x();
            float sY = transform.scale.y();
            float sZ = transform.scale.z();
            float tX = transform.translation.x();
            float tY = transform.translation.y();
            float tZ = transform.translation.z();

            RandomSource random = RandomSource.create();
            java.util.List<BakedQuad> allQuads = new java.util.ArrayList<>();
            allQuads.addAll(bakedModel.getQuads(null, null, random));
            for (Direction dir : Direction.values()) {
                allQuads.addAll(bakedModel.getQuads(null, dir, random));
            }

            FomekBEWRL.Model bewrl = new FomekBEWRL.Model();
            if (allQuads.isEmpty()) return bewrl;

            FomekRenderAPI.Shape shape = new FomekRenderAPI.Shape();
            shape.begin(VertexFormat.Mode.QUADS, true);

            // Build transform matrix: translate → rotate(Z,Y,X) → scale
            org.joml.Matrix4f mat = new org.joml.Matrix4f();
            mat.translate(tX, tY, tZ);
            if (rZ != 0) mat.rotateZ((float) Math.toRadians(rZ));
            if (rY != 0) mat.rotateY((float) Math.toRadians(rY));
            if (rX != 0) mat.rotateX((float) Math.toRadians(rX));
            mat.scale(sX, sY, sZ);

            org.joml.Vector3f vpos = new org.joml.Vector3f();

            for (BakedQuad quad : allQuads) {
                int[] data = quad.getVertices();
                if (data == null || data.length < 4) continue;
                int stride = data.length / 4;

                for (int v = 0; v < 4; v++) {
                    int base = v * stride;
                    float x = Float.intBitsToFloat(data[base + 0]);
                    float y = Float.intBitsToFloat(data[base + 1]);
                    float z = Float.intBitsToFloat(data[base + 2]);

                    vpos.set(x, y, z);
                    mat.transformPosition(vpos);

                    int mcColor = data[base + 3];
                    int a = (mcColor >> 24) & 0xFF;
                    int b = (mcColor >> 16) & 0xFF;
                    int g = (mcColor >> 8) & 0xFF;
                    int r = mcColor & 0xFF;
                    if (a == 0) a = 255;
                    int color = (a << 24) | (r << 16) | (g << 8) | b;

                    float u = Float.intBitsToFloat(data[base + 4]);
                    float vt = Float.intBitsToFloat(data[base + 5]);

                    shape.addVertexUV(vpos.x, vpos.y, vpos.z, u, vt, color);
                }
            }
            shape.end();

            if (!shape.isEmpty()) {
                String texture = "minecraft:textures/atlas/blocks.png";
                bewrl.addPart(shape, texture,
                    0, 0, 0, 0, 0, 0, 1, 1, 1, -1, "entityCutoutNoCull");
            }

            return bewrl;
        } catch (Exception e) {
            return new FomekBEWRL.Model();
        }
    }


    // ── BEWRL Render with Vec3 pos/rot + Vec3 scale ──────────────────────────────

    public static void renderBEWRL(FomekBEWRL.Model model, FomekShader shader,
            FomekBEWRLVars.Vec3 pos, FomekBEWRLVars.Vec3 rot, FomekBEWRLVars.Vec3 scale) {
        renderBEWRL(model, shader, pos.x, pos.y, pos.z, rot.x, rot.y, rot.z, scale.x, scale.y, scale.z);
    }

    public static void renderBEWRLOverlay(FomekBEWRL.Model model, FomekShader shader,
            FomekBEWRLVars.Vec3 pos, FomekBEWRLVars.Vec3 rot, FomekBEWRLVars.Vec3 scale) {
        renderBEWRL(model, shader, pos.x, pos.y, pos.z, rot.x, rot.y, rot.z, scale.x, scale.y, scale.z);
    }

    public static void renderBEWRLWorld(FomekBEWRL.Model model, FomekShader shader,
            FomekBEWRLVars.Vec3 pos, FomekBEWRLVars.Vec3 rot, FomekBEWRLVars.Vec3 scale) {
        renderBEWRL(model, shader, pos.x, pos.y, pos.z, rot.x, rot.y, rot.z, scale.x, scale.y, scale.z);
    }

    public static void renderJavaModel(String name, net.minecraft.resources.Identifier texture, String renderType,
            float x, float y, float z,
            float yaw, float pitch, float roll,
            float scale) {

        // ── Overlay context: set up a proper 3D viewport like the inventory player preview ──
        if (currentOverlayContext != null) {
            renderJavaModelOnOverlay(name, texture, renderType, x, y, z, yaw, pitch, roll, scale);
            return;
        }

        PoseStack pose = getActivePoseStack();
        MultiBufferSource buffer = getActiveBufferSource();
        if (pose == null || buffer == null) return;
        int packedLight = getActivePackedLight();

        // ── Item render context ──
        // The mixin fires at HEAD of renderStatic/render — BEFORE MC applies any
        // item transform. The PoseStack is raw, in the same coordinate space that
        // ModelPart uses: Y-up, 1 unit = 1/16 block (1 pixel).
        //
        // So the only correction needed is the pixel-to-block scale (×0.0625).
        // No flip, no centering translation — those would fight the user's own
        // x/y/z/yaw/pitch/roll params and produce the floating-off-screen look.
        //
        // User sets x/y/z to position the model in item-space (0,0,0 = item origin)
        // and scale=1 = normal entity model size.
        if (currentContext != null) {
            // GUI context: PoseStack is in screen pixel space (Y goes down).
            // The display transform that would normally handle Y flip is cancelled.
            // So we tell the renderer to apply scale(16) + Z 180° for GUI.
            //
            // World contexts (THIRD_PERSON, FIRST_PERSON, GROUND, FIXED):
            // PoseStack is in block space (Y up). No extra scale or flip needed.
            boolean isGui = currentContext.getDisplayContext() == ItemDisplayContext.GUI;
            FomekJavaModelRenderer.renderJavaModel(
                name, texture, renderType, x, y, z, yaw, pitch, roll, scale,
                pose, buffer, packedLight, getActivePackedOverlay(), isGui);
            return;
        }

        FomekJavaModelRenderer.renderJavaModel(
            name, texture, renderType, x, y, z, yaw, pitch, roll, scale,
            pose, buffer, packedLight, getActivePackedOverlay(), false, true);
        // Flush immediately in world context so GL state changes before this call apply.
        if (buffer instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }
    }

    /**
     * Render a Java model on the HUD/overlay using a dedicated 3D scissored viewport.
     * x, y = screen pixel position (top-left of the model's "window")
     * z = depth bias (use 0 normally)
     * scale = size in pixels (think of it as: 1 unit = 1 pixel, model is ~8 units tall)
     * yaw/pitch/roll = rotation degrees
     */
    private static void renderJavaModelOnOverlay(String name, net.minecraft.resources.Identifier texture, String renderType,
            float x, float y, float z,
            float yaw, float pitch, float roll,
            float scale) {

        // Look up the model first — if it doesn't exist, nothing to render.
        net.minecraft.client.model.geom.ModelPart model = FomekJavaModelRenderer.getBakedModel(name);
        if (model == null) return;

        enqueueOverlay(z, () -> {
            GuiGraphicsExtractor gui = currentOverlayContext.getGuiGraphics();
            MultiBufferSource.BufferSource buffer = gui.bufferSource();

            net.minecraft.client.renderer.RenderType rt = FomekJavaModelRenderer.resolveRenderType(renderType, texture);
            com.mojang.blaze3d.vertex.VertexConsumer consumer = buffer.getBuffer(rt);

            float[] center = FomekJavaModelRenderer.calculateModelCenter(model);
            float cx = center[0] / 16f;
            float cy = center[1] / 16f;
            float cz = center[2] / 16f;

            PoseStack pose = gui.pose();
            pose.pushPose();
            // 1. Translate to screen position (z = user's depth directly, no hardcoded offset)
            pose.translate(x, y, z);
            // 2. Apply user rotations (around the screen position)
            if (yaw   != 0) pose.mulPose(Axis.YP.rotationDegrees(yaw));
            if (pitch != 0) pose.mulPose(Axis.XP.rotationDegrees(pitch));
            if (roll  != 0) pose.mulPose(Axis.ZP.rotationDegrees(roll));
            // 3. Flip Y (screen Y goes down, model Y goes up)
            pose.scale(1, -1, 1);
            // 4. Scale from block space to GUI pixels (16px per block)
            float s = 16f * scale;
            pose.scale(s, s, s);
            // 5. Y 180°: entity models face +Z (forward), but we want them facing -Z (toward camera)
            pose.mulPose(Axis.YP.rotationDegrees(180));
            // 6. Center the model at origin so rotations pivot around its center
            pose.translate(-cx, -cy, -cz);
            // 7. Render — ModelPart.render() internally divides by 16 (pixel to block)
            final BlendMode _blend = currentBlendMode;
            BlendMode _savedBlend = currentBlendMode;
            currentBlendMode = _blend;
            model.render(pose, consumer, LightCoordsUtil.FULL_BRIGHT,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            currentBlendMode = _savedBlend;
            pose.popPose();
        });
    }

    // ── Shader helper methods ───────────────────────────────────────────────────

    public static void setShaderRenderType(FomekShader shader, String renderType) {
        if (shader != null) shader.setRenderType(renderType);
    }

    public static void setShaderColor(FomekShader shader, int color) {
        if (shader != null) shader.setColor(color);
    }

    public static void setShaderTransparency(FomekShader shader, float transparency) {
        if (shader != null) shader.setTransparency(transparency);
    }

    public static void setShaderGlowing(FomekShader shader, boolean glowing) {
        if (shader != null) shader.setGlowing(glowing);
    }

    public static void setShaderDrawOrder(FomekShader shader, String drawOrder) {
        if (shader != null) shader.setDrawOrder(drawOrder);
    }

    // ── Render BEWRL with shader ────────────────────────────────────────────────
    //
    // Uses a DEDICATED MultiBufferSource.BufferSource for the custom model's
    // geometry, flushed immediately after rendering.
    //
    // Why a dedicated buffer:
    //   The shared MultiBufferSource (from LivingEntityRenderer / ItemInHandRenderer)
    //   also contains the player's pending skin/armor geometry. When getBuffer()
    //   is called for a new render type, the shared BufferSource may AUTO-FLUSH the
    //   player's pending batch — drawing it with whatever global RenderSystem state
    //   is currently active (setShaderColor, blend, etc.), NOT the correct state
    //   that the render type would set during normal end-of-frame flushing.
    //
    //   RenderSystem.setShaderColor() is especially dangerous: it is NOT managed
    //   by RenderType.setupRenderState()/clearRenderState(). It persists across ALL
    //   batch flushes and acts as a color multiplier on every vertex. If it's wrong
    //   at auto-flush time, the player's skin gets tinted with the custom model's
    //   shader color — appearing as a "shader leak."
    //
    // By using a dedicated buffer:
    //   1. The custom model's getBuffer() calls NEVER touch the shared buffer.
    //   2. No auto-flush of the player's pending batch occurs.
    //   3. The dedicated buffer is flushed immediately, drawing the custom model
    //      with its own render type state (correct shader, texture, blend, depth).
    //   4. After flushing, we restore the saved setShaderColor() so the player's
    //      batch (flushed later at end of frame) uses the entity renderer's value.
    //
    // Depth ordering: the custom model is drawn BEFORE the player's batch is
    // flushed (at end of frame). For opaque models, depth testing handles this
    // correctly — the player is drawn on top where it's closer to the camera.
    // For translucent models, the custom model's fragments are overwritten by
    // the player's opaque fragments where the player is closer. Where the custom
    // model is closer (held item in front), it correctly remains visible.

    public static void renderBEWRL(FomekBEWRL.Model model, FomekShader shader,
            float x, float y, float z,
            float yaw, float pitch, float roll,
            float scale) {
        PoseStack activePose = getActivePoseStack();
        MultiBufferSource activeBuf = getActiveBufferSource();
        if (activePose == null || activeBuf == null || model == null || model.isEmpty()) return;

        boolean isOverlay = currentOverlayContext != null;
        float rx = x, ry = y, rz = z, ryaw = yaw, rpitch = pitch, rroll = roll, rscale = scale;
        ByteBufferBuilder ob = null;
        MultiBufferSource.BufferSource obuf = null;
        if (isOverlay) {
            // Enqueue for depth-sorted rendering. No hardcoded Z offset or depth test toggle.
            final float _z = z;
            final float _yaw = yaw, _pitch = pitch, _roll = roll, _scale = scale;
            final FomekBEWRL.Model _model = model;
            final FomekShader _shader = shader;
            enqueueOverlay(_z, () -> {
                GuiGraphicsExtractor gui = currentOverlayContext.getGuiGraphics();
                PoseStack _pose = gui.pose();
                _pose.pushPose();
                _pose.translate(x, y, _z);
                _pose.scale(_scale, -_scale, _scale);
                if (_yaw   != 0) _pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(_yaw));
                if (_pitch != 0) _pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(_pitch));
                if (_roll  != 0) _pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(_roll));
                int _packedLight = (_shader != null && _shader.isGlowing())
                        ? LightCoordsUtil.FULL_BRIGHT : getGuiAwareLight();
                if (_shader != null) {
                    _shader.apply();
                    _model.renderWithShader(_pose, gui.bufferSource(),
                        0, 0, 0, 0, 0, 0, 1,
                        _shader, _packedLight, getActivePackedOverlay());
                    _shader.restore();
                } else {
                    _model.render(_pose, gui.bufferSource(),
                        0, 0, 0, 0, 0, 0, 1,
                        _packedLight, getActivePackedOverlay());
                }
                _pose.popPose();
            });
            return; // Skip the rest — we've enqueued the render
        }
        MultiBufferSource renderBuf = activeBuf;

        int packedLight = (shader != null && shader.isGlowing())
                ? LightCoordsUtil.FULL_BRIGHT
                : getGuiAwareLight();

        // Auto-apply item display transform (centering + rotation) — same as
        // the xyz-scale overload. Without this, reconstructed items rendered
        // with a shader via the uniform-scale API appear off-center in GUI.
        boolean appliedItemTransform = autoApplyItemDisplayTransform();

        // Centering compensation for scaling: when autoApplyItemDisplayTransform
        // applied translate(-0.5), scaling around the PoseStack origin shifts the
        // item. Compensate by translating 0.5*(1-scale) so the scale is effectively
        // centered around the item center (model-space 0.5,0.5,0.5).
        // The model render applies scale internally, so we add the compensation
        // to the PoseStack before the model render's own translate/scale.
        if (appliedItemTransform && scale != 1) {
            float comp = 0.5f * (1f - scale);
            activePose.translate(comp, comp, comp);
        }

        if (shader != null) {
            shader.apply();

            boolean useBehind = "behind".equals(shader.getDrawOrder());

            if (useBehind) {
                float[] savedColor = RenderSystem.getShaderColor();
                ByteBufferBuilder builder = new ByteBufferBuilder(786432);
                MultiBufferSource.BufferSource dedicatedBuffer = MultiBufferSource.immediate(builder);
                try {
                    model.renderWithShader(activePose, dedicatedBuffer,
                        rx, ry, rz, ryaw, rpitch, rroll, rscale,
                        shader, packedLight, getActivePackedOverlay());
                    dedicatedBuffer.endBatch();
                } finally {
                    builder.close();
                }
                RenderSystem.setShaderColor(savedColor[0], savedColor[1], savedColor[2], savedColor[3]);
            } else {
                // Use a DEDICATED buffer + immediate flush for ALL default renders.
                // The shared bufferSource is NOT flushed by our mixin (to avoid
                // contaminating the player model with wrong RenderSystem state).
                // If we write to the shared buffer, energySwirl and other custom
                // render types may never be flushed during the item render pass.
                ByteBufferBuilder builder = new ByteBufferBuilder(786432);
                MultiBufferSource.BufferSource dedicatedBuffer = MultiBufferSource.immediate(builder);
                try {
                    model.renderWithShader(activePose, dedicatedBuffer,
                        rx, ry, rz, ryaw, rpitch, rroll, rscale,
                        shader, packedLight, getActivePackedOverlay());
                    dedicatedBuffer.endBatch();
                } finally {
                    builder.close();
                }
            }

            shader.restore();
        } else {
            ByteBufferBuilder builder = new ByteBufferBuilder(786432);
            MultiBufferSource.BufferSource dedicatedBuffer = MultiBufferSource.immediate(builder);
            try {
                model.render(activePose, dedicatedBuffer,
                    rx, ry, rz, ryaw, rpitch, rroll, rscale,
                    packedLight, getActivePackedOverlay());
                dedicatedBuffer.endBatch();
            } finally {
                builder.close();
            }
        }

        // Pop the item display transform if we applied it
        if (appliedItemTransform) autoPopItemDisplayTransform();
    }


    // ── BEWRL Render with xyz scale (non-uniform) ────────────────────────────────

    public static void renderBEWRL(FomekBEWRL.Model model, FomekShader shader,
            float x, float y, float z,
            float yaw, float pitch, float roll,
            float xscale, float yscale, float zscale) {
        PoseStack activePose = getActivePoseStack();
        MultiBufferSource activeBuf = getActiveBufferSource();
        if (activePose == null || activeBuf == null || model == null || model.isEmpty()) return;

        boolean isOverlay = currentOverlayContext != null;
        if (isOverlay) {
            final float _z = z;
            final float _yaw = yaw, _pitch = pitch, _roll = roll;
            final float _xs = xscale, _ys = yscale, _zs = zscale;
            final FomekBEWRL.Model _model = model;
            final FomekShader _shader = shader;
            enqueueOverlay(_z, () -> {
                GuiGraphicsExtractor gui = currentOverlayContext.getGuiGraphics();
                PoseStack _pose = gui.pose();
                _pose.pushPose();
                _pose.translate(x, y, _z);
                _pose.scale(_xs, -_ys, _zs);
                if (_yaw   != 0) _pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(_yaw));
                if (_pitch != 0) _pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(_pitch));
                if (_roll  != 0) _pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(_roll));
                int _packedLight = (_shader != null && _shader.isGlowing())
                        ? LightCoordsUtil.FULL_BRIGHT : getGuiAwareLight();
                if (_shader != null) {
                    _shader.apply();
                    _model.renderWithShader(_pose, gui.bufferSource(),
                        0, 0, 0, 0, 0, 0, 1,
                        _shader, _packedLight, getActivePackedOverlay());
                    _shader.restore();
                } else {
                    _model.render(_pose, gui.bufferSource(),
                        0, 0, 0, 0, 0, 0, 1,
                        _packedLight, getActivePackedOverlay());
                }
                _pose.popPose();
            });
            return;
        }

        int packedLight = (shader != null && shader.isGlowing())
                ? LightCoordsUtil.FULL_BRIGHT : getGuiAwareLight();

        // Auto-apply item display transform for centering.
        // For GUI: only translate(-0.5) (display transform already applied by caller).
        // For non-GUI: apply display transform + translate(-0.5).
        boolean appliedItemTransform = autoApplyItemDisplayTransform();

        activePose.pushPose();
        activePose.translate(x, y, z);
        if (yaw   != 0) activePose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
        if (pitch != 0) activePose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pitch));
        if (roll  != 0) activePose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(roll));
        if (xscale != 1 || yscale != 1 || zscale != 1) {
            activePose.scale(xscale, yscale, zscale);
            // When autoApplyItemDisplayTransform applied translate(-0.5), scaling
            // around the PoseStack origin (model-space -0.5 corner) shifts the item.
            // Compensate by translating 0.5*(1/S) - 0.5 per axis so the scale is
            // effectively centered around the item center (model-space 0.5,0.5,0.5).
            if (appliedItemTransform) {
                activePose.translate(
                    0.5f * (1f/xscale - 1f),
                    0.5f * (1f/yscale - 1f),
                    0.5f * (1f/zscale - 1f)
                );
            }
        }

        if (shader != null) {
            shader.apply();
            boolean useBehind = "behind".equals(shader.getDrawOrder());
            boolean useBlend = currentBlendMode != null && currentBlendMode != BlendMode.DEFAULT;
            if (useBehind) {
                float[] savedColor = RenderSystem.getShaderColor();
                ByteBufferBuilder builder = new ByteBufferBuilder(786432);
                MultiBufferSource.BufferSource dedicatedBuffer = MultiBufferSource.immediate(builder);
                try {
                    model.renderWithShader(activePose, dedicatedBuffer,
                        0, 0, 0, 0, 0, 0, 1,
                        shader, packedLight, getActivePackedOverlay());
                    dedicatedBuffer.endBatch();
                } finally {
                    builder.close();
                }
                RenderSystem.setShaderColor(savedColor[0], savedColor[1], savedColor[2], savedColor[3]);
            } else if (useBlend && activeBuf instanceof MultiBufferSource.BufferSource bs) {
                // Custom blend mode active — render into a dedicated buffer and flush
                // immediately with the blend override so vertices are drawn NOW while
                // the blend func is still set (before disableBlending() resets it).
                ByteBufferBuilder builder = new ByteBufferBuilder(786432);
                MultiBufferSource.BufferSource dedicatedBuffer = MultiBufferSource.immediate(builder);
                try {
                    model.renderWithShader(activePose, dedicatedBuffer,
                        0, 0, 0, 0, 0, 0, 1,
                        shader, packedLight, getActivePackedOverlay());
                    flushBufferWithBlend(dedicatedBuffer, currentBlendMode);
                } finally {
                    builder.close();
                }
            } else if (useBlend) {
                // activeBuf is not a BufferSource we can flush — best effort
                ByteBufferBuilder builder = new ByteBufferBuilder(786432);
                MultiBufferSource.BufferSource dedicatedBuffer = MultiBufferSource.immediate(builder);
                try {
                    model.renderWithShader(activePose, dedicatedBuffer,
                        0, 0, 0, 0, 0, 0, 1,
                        shader, packedLight, getActivePackedOverlay());
                    flushBufferWithBlend(dedicatedBuffer, currentBlendMode);
                } finally {
                    builder.close();
                }
            } else {
                // Use a DEDICATED buffer + immediate flush (same as uniform-scale path).
                ByteBufferBuilder builder = new ByteBufferBuilder(786432);
                MultiBufferSource.BufferSource dedicatedBuffer = MultiBufferSource.immediate(builder);
                try {
                    model.renderWithShader(activePose, dedicatedBuffer,
                        0, 0, 0, 0, 0, 0, 1,
                        shader, packedLight, getActivePackedOverlay());
                    dedicatedBuffer.endBatch();
                } finally {
                    builder.close();
                }
            }
            shader.restore();
        } else {
            // No shader — but if a blend mode is active, we still need to flush immediately
            boolean useBlend = currentBlendMode != null && currentBlendMode != BlendMode.DEFAULT;
            if (useBlend && activeBuf instanceof MultiBufferSource.BufferSource bs) {
                ByteBufferBuilder builder = new ByteBufferBuilder(786432);
                MultiBufferSource.BufferSource dedicatedBuffer = MultiBufferSource.immediate(builder);
                try {
                    model.render(activePose, dedicatedBuffer,
                        0, 0, 0, 0, 0, 0, 1,
                        packedLight, getActivePackedOverlay());
                    flushBufferWithBlend(dedicatedBuffer, currentBlendMode);
                } finally {
                    builder.close();
                }
            } else if (useBlend) {
                ByteBufferBuilder builder = new ByteBufferBuilder(786432);
                MultiBufferSource.BufferSource dedicatedBuffer = MultiBufferSource.immediate(builder);
                try {
                    model.render(activePose, dedicatedBuffer,
                        0, 0, 0, 0, 0, 0, 1,
                        packedLight, getActivePackedOverlay());
                    flushBufferWithBlend(dedicatedBuffer, currentBlendMode);
                } finally {
                    builder.close();
                }
            } else {
                ByteBufferBuilder builder = new ByteBufferBuilder(786432);
                MultiBufferSource.BufferSource dedicatedBuffer = MultiBufferSource.immediate(builder);
                try {
                    model.render(activePose, dedicatedBuffer,
                        0, 0, 0, 0, 0, 0, 1,
                        packedLight, getActivePackedOverlay());
                    dedicatedBuffer.endBatch();
                } finally {
                    builder.close();
                }
            }
        }

        activePose.popPose();

        // Pop the item display transform if we applied it
        if (appliedItemTransform) autoPopItemDisplayTransform();
    }

    // ── Render registered model with shader ─────────────────────────────────────

    public static void renderRegisteredModel(String modelId, FomekShader shader,
            float x, float y, float z,
            float yaw, float pitch, float roll,
            float scale) {
        FomekBEWRL.Model model = FomekBEWRL.Registry.INSTANCE.getModel(modelId);
        if (model != null && !model.isEmpty()) {
            renderBEWRL(model, shader, x, y, z, yaw, pitch, roll, scale);
        }
    }

    public static void renderRegisteredModel(String modelId, FomekAnimation.Controller controller, FomekShader shader,
            float x, float y, float z,
            float yaw, float pitch, float roll,
            float scale) {
        FomekBEWRL.Model model = FomekBEWRL.Registry.INSTANCE.getModel(modelId);
        PoseStack activePose = getActivePoseStack();
        MultiBufferSource activeBuf = getActiveBufferSource();
        if (model == null || model.isEmpty() || activePose == null) return;

        int packedLight = (shader != null && shader.isGlowing())
                ? LightCoordsUtil.FULL_BRIGHT
                : getGuiAwareLight();

        if (shader != null) {
            shader.apply();

            boolean useBehind = "behind".equals(shader.getDrawOrder());

            if (useBehind) {
                float[] savedColor = RenderSystem.getShaderColor();
                ByteBufferBuilder builder = new ByteBufferBuilder(786432);
                MultiBufferSource.BufferSource dedicatedBuffer = MultiBufferSource.immediate(builder);
                try {
                    if (controller != null && controller.isPlaying()) {
                        model.renderAnimatedWithShader(
                            activePose, dedicatedBuffer,
                            x, y, z, yaw, pitch, roll, scale,
                            shader, packedLight, getActivePackedOverlay(),
                            controller, getGameTime(), getPartialTick());
                    } else {
                        model.renderWithShader(activePose, dedicatedBuffer,
                            x, y, z, yaw, pitch, roll, scale,
                            shader, packedLight, getActivePackedOverlay());
                    }
                    dedicatedBuffer.endBatch();
                } finally {
                    builder.close();
                }
                RenderSystem.setShaderColor(savedColor[0], savedColor[1], savedColor[2], savedColor[3]);
            } else {
                ByteBufferBuilder builder = new ByteBufferBuilder(786432);
                MultiBufferSource.BufferSource dedicatedBuffer = MultiBufferSource.immediate(builder);
                try {
                    if (controller != null && controller.isPlaying()) {
                        model.renderAnimatedWithShader(
                            activePose, dedicatedBuffer,
                            x, y, z, yaw, pitch, roll, scale,
                            shader, packedLight, getActivePackedOverlay(),
                            controller, getGameTime(), getPartialTick());
                    } else {
                        model.renderWithShader(activePose, dedicatedBuffer,
                            x, y, z, yaw, pitch, roll, scale,
                            shader, packedLight, getActivePackedOverlay());
                    }
                    dedicatedBuffer.endBatch();
                } finally {
                    builder.close();
                }
            }

            shader.restore();
        } else {
            ByteBufferBuilder builder = new ByteBufferBuilder(786432);
            MultiBufferSource.BufferSource dedicatedBuffer = MultiBufferSource.immediate(builder);
            try {
                if (controller != null && controller.isPlaying()) {
                    model.renderAnimatedWithShader(
                        activePose, dedicatedBuffer,
                        x, y, z, yaw, pitch, roll, scale,
                        shader, packedLight, getActivePackedOverlay(),
                        controller, getGameTime(), getPartialTick());
                } else {
                    model.render(activePose, dedicatedBuffer,
                        x, y, z, yaw, pitch, roll, scale,
                        packedLight, getActivePackedOverlay());
                }
                dedicatedBuffer.endBatch();
            } finally {
                builder.close();
            }
        }
    }

    // ── Level 2: Shader uniform API ─────────────────────────────────────────────

    public static void setShaderFloat(FomekShader shader, String name, float value) {
        if (shader != null && name != null) shader.setFloat(name, value);
    }

    public static void setShaderVec3(FomekShader shader, String name, float x, float y, float z) {
        if (shader != null && name != null) shader.setVec3(name, x, y, z);
    }

    public static float getShaderFloat(FomekShader shader, String name) {
        if (shader == null || name == null) return 0f;
        return shader.getFloat(name);
    }

    public static void setShaderVec2(FomekShader shader, String name, float x, float y) {
        if (shader != null && name != null) shader.setVec2(name, x, y);
    }

    public static void setShaderVec4(FomekShader shader, String name, float x, float y, float z, float w) {
        if (shader != null && name != null) shader.setVec4(name, x, y, z, w);
    }

    public static void setShaderInt(FomekShader shader, String name, int value) {
        if (shader != null && name != null) shader.setInt(name, value);
    }

    public static void setShaderBool(FomekShader shader, String name, boolean value) {
        if (shader != null && name != null) shader.setBool(name, value);
    }

    public static int getShaderInt(FomekShader shader, String name) {
        if (shader == null || name == null) return 0;
        return shader.getInt(name);
    }

    public static boolean getShaderBool(FomekShader shader, String name) {
        if (shader == null || name == null) return false;
        return shader.getBool(name);
    }

    public static void setShaderTexture(FomekShader shader, String texture) {
        if (shader != null) shader.setTexture(texture);
    }

    public static void setShaderGlowStrength(FomekShader shader, float strength) {
        if (shader != null) shader.setGlowStrength(strength);
    }

    public static void setShaderSwirlXSpeed(FomekShader shader, float speed) {
        if (shader != null) shader.setSwirlXSpeed(speed);
    }

    public static void setShaderSwirlZSpeed(FomekShader shader, float speed) {
        if (shader != null) shader.setSwirlZSpeed(speed);
    }

    public static void setShaderSwirlBlendMode(FomekShader shader, String mode) {
        if (shader != null) shader.setSwirlBlendMode(mode);
    }

    // ── Level 3: Custom GLSL API ────────────────────────────────────────────────

    public static void setShaderVertexCode(FomekShader shader, String code) {
        if (shader != null) shader.setVertexShaderSource(code);
    }

    public static void setShaderFragmentCode(FomekShader shader, String code) {
        if (shader != null) shader.setFragmentShaderSource(code);
    }

    public static void setShaderProgram(FomekShader shader, String name) {
        if (shader != null) shader.setShaderProgramName(name);
    }

    // ── Time API ─────────────────────────────────────────────────────────────────

    // ── GLSL Code String Builders ───────────────────────────────────────────────

    /** Append a line to an existing GLSL code string (adds newline). */
    public static String glslAppendLine(String base, String line) {
        if (base == null) base = "";
        return base + line + "\n";
    }

    /** Concatenate two GLSL strings. */
    public static String glslConcat(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        return a + b;
    }

    /** Produce a GLSL #version header line. */
    public static String glslVersion(int version, boolean core) {
        return "#version " + version + (core ? " core" : "") + "\n";
    }

    /** Produce a GLSL uniform declaration line: "uniform <type> <name>;\n" */
    public static String glslUniformDecl(String type, String name) {
        return "uniform " + type + " " + name + ";\n";
    }

    /** Produce a GLSL variable declaration: "<type> <name> = <value>;\n" */
    public static String glslVarDecl(String type, String name, String value) {
        if (value == null || value.isEmpty()) return type + " " + name + ";\n";
        return type + " " + name + " = " + value + ";\n";
    }

    /** Produce a GLSL assignment line: "<name> = <expr>;\n" */
    public static String glslAssign(String name, String expr) {
        return name + " = " + expr + ";\n";
    }

    /** Wrap code in a void main() { ... } block. */
    public static String glslMainBlock(String body) {
        return "void main() {\n" + (body != null ? body : "") + "}\n";
    }

    /** Produce a GLSL comment line. */
    public static String glslComment(String text) {
        return "// " + text + "\n";
    }

    /** Produce an if-block: "if (<cond>) {\n<body>}\n" */
    public static String glslIfBlock(String condition, String body) {
        return "if (" + condition + ") {\n" + (body != null ? body : "") + "}\n";
    }

    /** Produce an if-else-block. */
    public static String glslIfElseBlock(String condition, String ifBody, String elseBody) {
        return "if (" + condition + ") {\n" + (ifBody != null ? ifBody : "")
             + "} else {\n" + (elseBody != null ? elseBody : "") + "}\n";
    }

    /** Produce a GLSL vec2 literal: "vec2(x, y)". */
    public static String glslVec2(Object x, Object y) {
        return "vec2(" + x + ", " + y + ")";
    }

    /** Produce a GLSL vec3 literal: "vec3(x, y, z)". */
    public static String glslVec3(Object x, Object y, Object z) {
        return "vec3(" + x + ", " + y + ", " + z + ")";
    }

    /** Produce a GLSL vec4 literal: "vec4(x, y, z, w)". */
    public static String glslVec4(Object x, Object y, Object z, Object w) {
        return "vec4(" + x + ", " + y + ", " + z + ", " + w + ")";
    }

    /** Produce a GLSL mix() call string: "mix(a, b, t)". */
    public static String glslMix(String a, String b, String t) {
        return "mix(" + a + ", " + b + ", " + t + ")";
    }

    /** Produce a GLSL clamp() call string: "clamp(x, min, max)". */
    public static String glslClamp(String x, String minVal, String maxVal) {
        return "clamp(" + x + ", " + minVal + ", " + maxVal + ")";
    }

    /** Produce a GLSL abs() call string. */
    public static String glslAbs(String x) { return "abs(" + x + ")"; }

    /** Produce a GLSL length() call string. */
    public static String glslLength(String v) { return "length(" + v + ")"; }

    /** Produce a GLSL normalize() call string. */
    public static String glslNormalize(String v) { return "normalize(" + v + ")"; }

    /** Produce a GLSL dot() call string. */
    public static String glslDot(String a, String b) { return "dot(" + a + ", " + b + ")"; }

    /** Produce a GLSL smoothstep() call string. */
    public static String glslSmoothstep(String edge0, String edge1, String x) {
        return "smoothstep(" + edge0 + ", " + edge1 + ", " + x + ")";
    }

    /** Produce a GLSL pow() call string. */
    public static String glslPow(String x, String y) { return "pow(" + x + ", " + y + ")"; }

    /** Produce a GLSL sin() call string. */
    public static String glslSin(String x) { return "sin(" + x + ")"; }

    /** Produce a GLSL cos() call string. */
    public static String glslCos(String x) { return "cos(" + x + ")"; }

    /** Produce a GLSL fract() call string. */
    public static String glslFract(String x) { return "fract(" + x + ")"; }

    /** Produce a GLSL floor() call string. */
    public static String glslFloor(String x) { return "floor(" + x + ")"; }

    /** Produce a GLSL mod() call string. */
    public static String glslMod(String x, String y) { return "mod(" + x + ", " + y + ")"; }

    /** Produce a GLSL step() call string. */
    public static String glslStep(String edge, String x) { return "step(" + edge + ", " + x + ")"; }

    // ── v1.3 expression helpers ──

    public static String glslVarRef(String name) { return name; }
    public static String glslArith(String a, String op, String b) { return "(" + a + " " + op + " " + b + ")"; }
    public static String glslCompare(String a, String op, String b) { return "(" + a + " " + op + " " + b + ")"; }
    public static String glslNegate(String x) { return "(-(" + x + "))"; }
    public static String glslTernary(String cond, String ifVal, String elseVal) { return "(" + cond + " ? " + ifVal + " : " + elseVal + ")"; }
    public static String glslCast(String type, String expr) { return type + "(" + expr + ")"; }
    public static String glslSwizzle(String v, String comp) { return "(" + v + ")." + comp; }
    public static String glslForLoop(String var, int count, String body) { return "for(int " + var + " = 0; " + var + " < " + count + "; " + var + "++) {\n" + body + "\n}"; }
    public static String glslReturn(String expr) { return "return " + expr + ";"; }
    public static String glslCustomFunc(String retType, String name, String params, String body) { return retType + " " + name + "(" + params + ") {\n" + body + "\n}"; }
    public static String glslCallFunc(String name, String args) { return name + "(" + args + ")"; }
    public static String glslTexCoord() { return "texCoord"; }
    public static String glslTexture2D(String sampler, String uv) { return "texture2D(" + sampler + ", " + uv + ")"; }
    public static String glslToString(String expr) { return expr; }
    public static String glslFloatToString(float n) { return String.valueOf(n); }
    public static String glslIntToString(int n) { return String.valueOf(n); }
    public static String glslBoolToString(boolean b) { return String.valueOf(b); }


    /** Produce gl_FragColor = ... assignment string. */
    public static String glslFragColor(String expr) {
        return "gl_FragColor = " + expr + ";\n";
    }

    /** Dispatcher for single-arg GLSL string functions (abs, length, normalize, sin, cos, fract, floor). */
    public static String glslFunc1(String func, String x) {
        switch (func) {
            case "abs":       return "abs(" + x + ")";
            case "length":    return "length(" + x + ")";
            case "normalize": return "normalize(" + x + ")";
            case "sin":       return "sin(" + x + ")";
            case "cos":       return "cos(" + x + ")";
            case "fract":     return "fract(" + x + ")";
            case "floor":     return "floor(" + x + ")";
            default:          return func + "(" + x + ")";
        }
    }

    /** Dispatcher for two-arg GLSL string functions (dot, mod, pow, step, mix). */
    public static String glslFunc2(String func, String a, String b) {
        switch (func) {
            case "dot":  return "dot(" + a + ", " + b + ")";
            case "mod":  return "mod(" + a + ", " + b + ")";
            case "pow":  return "pow(" + a + ", " + b + ")";
            case "step": return "step(" + a + ", " + b + ")";
            case "mix":  return "mix(" + a + ", " + b + ", 0.5)"; // basic 2-arg mix
            default:     return func + "(" + a + ", " + b + ")";
        }
    }

        public static float getShaderTime() {
        return getGameTime() / 20.0f;  // convert ticks to seconds
    }

    // ── Shader math ──────────────────────────────────────────────────────────────

    public static float smoothstep(float edge0, float edge1, float x) {
        if (edge1 == edge0) return 0f;
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }

    public static float shaderNoise(float x, float y) {
        // Simple value noise based on hash function
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        float xf = x - xi;
        float yf = y - yi;

        // Smooth interpolation
        float u = xf * xf * (3f - 2f * xf);
        float v = yf * yf * (3f - 2f * yf);

        // Hash-based pseudo-random values at corners
        float aa = hash2D(xi, yi);
        float ba = hash2D(xi + 1, yi);
        float ab = hash2D(xi, yi + 1);
        float bb = hash2D(xi + 1, yi + 1);

        // Bilinear interpolation
        float x1 = aa + u * (ba - aa);
        float x2 = ab + u * (bb - ab);
        return x1 + v * (x2 - x1);
    }

    private static float hash2D(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        h = h ^ (h >> 16);
        return (h & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }

    public static float shaderDistance(float x1, float y1, float z1, float x2, float y2, float z2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

public static Fragment createFragment(String name) { return new Fragment(name); }
public static void setFragmentCode(Fragment frag, String code) { if (frag != null) frag.setCode(code); }
public static GLSL createGLSL() { return GLSL.create(); }
public static void addFragmentToGLSL(GLSL glsl, Fragment frag) { if (glsl != null && frag != null) glsl.addFragment(frag); }
public static void setGLSLVertexCode(GLSL glsl, String code) { if (glsl != null) glsl.setVertexCode(code); }
public static void setGLSLFragmentCode(GLSL glsl, String code) { if (glsl != null) glsl.setFragmentCode(code); }
public static void buildGLSL(GLSL glsl, FomekShader shader) { if (glsl != null && shader != null) glsl.build(shader); }

    // ── Template Shaders ─────────────────────────────────────────────────────────

    /**
     * TEMPLATE: Scanner shader — a glowing scan line that sweeps from top (Y=1) to
     * bottom (Y=0) repeatedly over any geometry that passes a "worldPos" varying.
     *
     * The fragment shader expects:
     *   uniform float uTime;      — current time in seconds (pass getShaderTime())
     *   uniform float uSpeed;     — sweep speed (default 0.5, lower = slower)
     *   uniform float uWidth;     — scan line thickness in 0..1 UV space (default 0.05)
     *   uniform float uIntensity; — glow multiplier (default 2.0)
     *   uniform vec3  uColor;     — RGB color of the scan line
     *
     * Vertex shader: passes through gl_Position and computes a scanY varying from
     * the model-space Y coordinate (assumed -0.5..+0.5 for a unit cube, remapped to 0..1).
     *
     * Usage:
     *   FomekShader sh = buildScannerShader(0.5f, 0.05f, 0f, 0.8f, 1f, 2f);
     *   FomekRenderAPI.renderShape(sh, ...);
     */
    public static FomekShader buildScannerShader(
            float speed, float width,
            float r, float g, float b,
            float intensity) {

        FomekShader shader = new FomekShader();

        // ── Uniforms (sent from Java every frame) ──────────────────────────────
        shader.setFloat("uTime",      getShaderTime());
        shader.setFloat("uSpeed",     speed);
        shader.setFloat("uWidth",     width);
        shader.setFloat("uIntensity", intensity);
        shader.setVec3 ("uColor",     r, g, b);

        // ── Vertex shader ──────────────────────────────────────────────────────
        // Passes the model-space Y position as a varying so the fragment shader
        // knows where it is on the 0..1 vertical axis.
        String vert =
            "#version 150 core\n" +
            "in vec3 Position;\n" +
            "in vec2 UV0;\n" +
            "uniform mat4 ModelViewMat;\n" +
            "uniform mat4 ProjMat;\n" +
            "out float vScanY;\n" +
            "void main() {\n" +
            "    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);\n" +
            // Remap Position.y from [-0.5, +0.5] to [0, 1]
            "    vScanY = Position.y + 0.5;\n" +
            "}\n";

        // ── Fragment shader ────────────────────────────────────────────────────
        // Computes scan line position from time, then uses smoothstep for a soft glow.
        String frag =
            "#version 150 core\n" +
            "uniform float uTime;\n" +
            "uniform float uSpeed;\n" +
            "uniform float uWidth;\n" +
            "uniform float uIntensity;\n" +
            "uniform vec3  uColor;\n" +
            "in  float vScanY;\n" +
            "out vec4  fragColor;\n" +
            "void main() {\n" +
            // scanPos cycles 0→1 based on time
            "    float scanPos = fract(uTime * uSpeed);\n" +
            // distance from this pixel to the scan line
            "    float dist = abs(vScanY - scanPos);\n" +
            // soft glow: full brightness at dist=0, falls off to 0 at dist=width
            "    float glow = smoothstep(uWidth, 0.0, dist);\n" +
            // base color: slight tint everywhere + bright glow line
            "    vec3 col = uColor * (0.15 + glow * uIntensity);\n" +
            "    float alpha = 0.25 + glow * 0.75;\n" +
            "    fragColor = vec4(col, alpha);\n" +
            "}\n";

        shader.setVertexShaderSource(vert);
        shader.setFragmentShaderSource(frag);

        return shader;
    }

// ═══════════════════════════════════════════════════════════════════════════
//  PLASMA STORM SHADER
//  Paste this BEFORE the final closing } of FomekRenderAPI class
// ═══════════════════════════════════════════════════════════════════════════

    /**
     * TEMPLATE: Plasma Storm — domain-warped FBM noise + 3D plasma + shockwave
     * rings + chromatic color cycling + fresnel edge glow + scanline interference
     * + electric crackles + global pulse. Fully self-contained shader.
     *
     * Usage:
     *   FomekShader sh = buildPlasmaStormShader(1.0f, 2.0f, 3.0f);
     *   FomekRenderAPI.renderBEWRL(model, sh, 0, 0, 0, 45, 0, 0, 0.5f);
     */
    public static FomekShader buildPlasmaStormShader(
            float speed, float intensity, float scale) {

        FomekShader shader = new FomekShader();

        // ── Uniforms ──────────────────────────────────────────────────────────
        shader.setFloat("uTime",      getShaderTime());
        shader.setFloat("uSpeed",     speed);
        shader.setFloat("uIntensity", intensity);
        shader.setFloat("uScale",     scale);

        // ── Vertex shader ──────────────────────────────────────────────────────
        String vert =
            "#version 150 core\n" +
            "in vec3 Position;\n" +
            "in vec2 UV0;\n" +
            "uniform mat4 ModelViewMat;\n" +
            "uniform mat4 ProjMat;\n" +
            "uniform float uTime;\n" +
            "uniform float uSpeed;\n" +
            "out vec2 vUV;\n" +
            "out vec3 vPos;\n" +
            "out float vDist;\n" +
            "void main() {\n" +
            "    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);\n" +
            "    vUV = UV0;\n" +
            // Slight vertex wobble for "breathing" effect
            "    float wobble = sin(uTime * uSpeed * 1.5 + Position.y * 8.0) * 0.015;\n" +
            "    vec3 wobbled = Position + vec3(wobble, wobble * 0.5, wobble);\n" +
            "    vPos = wobbled;\n" +
            "    vDist = length(wobbled);\n" +
            "}\n";

        // ── Fragment shader ────────────────────────────────────────────────────
        String frag =
            "#version 150 core\n" +
            "uniform float uTime;\n" +
            "uniform float uSpeed;\n" +
            "uniform float uIntensity;\n" +
            "uniform float uScale;\n" +
            "in  vec2 vUV;\n" +
            "in  vec3 vPos;\n" +
            "in  float vDist;\n" +
            "out vec4 fragColor;\n" +

            // ── Hash & noise ──
            "float hash(vec2 p) {\n" +
            "    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);\n" +
            "}\n" +
            "float noise(vec2 p) {\n" +
            "    vec2 i = floor(p);\n" +
            "    vec2 f = fract(p);\n" +
            "    f = f * f * (3.0 - 2.0 * f);\n" +
            "    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),\n" +
            "               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);\n" +
            "}\n" +

            // ── Fractal Brownian Motion (5 octaves) ──
            "float fbm(vec2 p) {\n" +
            "    float v = 0.0, a = 0.5;\n" +
            "    for (int i = 0; i < 5; i++) {\n" +
            "        v += a * noise(p);\n" +
            "        p *= 2.0;\n" +
            "        a *= 0.5;\n" +
            "    }\n" +
            "    return v;\n" +
            "}\n" +

            // ── Main ──
            "void main() {\n" +
            "    float t = uTime * uSpeed;\n" +
            "    vec3 p = vPos * uScale;\n" +

            // Domain warping — warp coords by noise, then sample noise again
            "    vec2 q = vec2(fbm(p.xy + t * 0.3), fbm(p.xy + vec2(5.2, 1.3)));\n" +
            "    vec2 r = vec2(fbm(p.xy + q + vec2(1.7, 9.2) + t * 0.15),\n" +
            "                  fbm(p.xy + q + vec2(8.3, 2.8) + t * 0.13));\n" +
            "    float n = fbm(p.xy + r);\n" +

            // 3D plasma — multiple sine waves on different axes
            "    float plasma = sin(p.x * 6.0 + t * 2.0) * cos(p.y * 5.0 + t * 1.5);\n" +
            "    plasma += sin(p.z * 7.0 + t * 1.8) * cos(length(p.xy) * 8.0 - t * 3.0);\n" +
            "    plasma += sin(atan(p.y, p.x) * 4.0 + t) * 0.5;\n" +
            "    plasma = plasma * 0.25 + 0.5;\n" +

            // Combine domain-warped noise with plasma
            "    float energy = n * 0.6 + plasma * 0.4;\n" +

            // Expanding shockwave rings from center
            "    float ringDist = length(p);\n" +
            "    float shock = sin(ringDist * 18.0 - t * 6.0);\n" +
            "    shock = pow(smoothstep(0.5, 1.0, shock), 2.0);\n" +

            // Chromatic color cycling (3-phase RGB)
            "    float h = energy * 3.0 + t * 0.4;\n" +
            "    vec3 col;\n" +
            "    col.r = sin(h) * 0.5 + 0.5;\n" +
            "    col.g = sin(h + 2.094) * 0.5 + 0.5;\n" +
            "    col.b = sin(h + 4.189) * 0.5 + 0.5;\n" +

            // Hot core — high-energy areas glow bright pink-white
            "    vec3 hot = vec3(1.0, 0.4, 0.9) * pow(energy, 4.0) * uIntensity * 2.0;\n" +
            "    col += hot;\n" +

            // Shockwave tint — blue-white rings
            "    col += shock * vec3(0.3, 0.7, 1.0) * uIntensity;\n" +

            // Fresnel edge glow — edges of the model glow cyan-white
            "    float fresnel = 1.0 - smoothstep(0.15, 0.4, vDist);\n" +
            "    fresnel = pow(fresnel, 1.5);\n" +
            "    col += fresnel * vec3(0.6, 0.9, 1.0) * uIntensity * 1.5;\n" +

            // Scanline interference
            "    float scan = sin(vUV.y * 100.0 + t * 12.0) * 0.5 + 0.5;\n" +
            "    col *= 0.8 + scan * 0.4;\n" +

            // Electric crackles — random bright lines
            "    float crack = abs(sin(p.x * 30.0 + t * 8.0)) * abs(sin(p.y * 25.0 - t * 7.0));\n" +
            "    crack = step(0.95, crack) * uIntensity;\n" +
            "    col += crack * vec3(1.0, 1.0, 0.8);\n" +

            // Global pulse
            "    float pulse = sin(t * 2.0) * 0.1 + 0.9;\n" +
            "    col *= pulse;\n" +
            "    col *= uIntensity;\n" +

            // Alpha
            "    float alpha = (0.4 + energy * 0.3 + fresnel * 0.2 + shock * 0.1) * pulse;\n" +
            "    fragColor = vec4(col, alpha);\n" +
            "}\n";

        shader.setVertexShaderSource(vert);
        shader.setFragmentShaderSource(frag);

        // Material settings
        shader.setRenderType("entityTranslucent");
        shader.setTransparency(0.85f);
        shader.setGlowing(true);
        shader.setGlowStrength(1.0f);


        return shader;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PULSE SHADER
    //  Rhythmic brightness pulse — the entire surface glows brighter and
    //  dimmer in a steady heartbeat-like rhythm.
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * TEMPLATE: Pulse — rhythmic brightness pulsing.
     * <p>
     * The surface color smoothly oscillates between dim and bright.
     * The pulse shape is a soft sine wave so it breathes naturally.
     *
     * @param speed       pulses per second (e.g. 1.0 = one pulse/sec)
     * @param minGlow     dimmest level 0..1 (e.g. 0.2 = never fully dark)
     * @param maxGlow     brightest level (e.g. 1.5 = can exceed 1.0 for bloom)
     * @param r,g,b       pulse color
     * @param edgeFalloff 0..1 — how much the rim dims relative to center (0=uniform)
     */
    public static FomekShader buildPulseShader(
            float speed, float minGlow, float maxGlow,
            float r, float g, float b, float edgeFalloff) {

        FomekShader shader = new FomekShader();
        shader.setFloat("uTime",  getShaderTime());
        shader.setFloat("uSpeed", speed);
        shader.setFloat("uMin",   minGlow);
        shader.setFloat("uMax",    maxGlow);
        shader.setVec3("uColor",  r, g, b);
        shader.setFloat("uEdge",  edgeFalloff);

        String vert =
            "#version 150 core\n" +
            "in vec3 Position;\n" +
            "in vec2 UV0;\n" +
            "uniform mat4 ModelViewMat;\n" +
            "uniform mat4 ProjMat;\n" +
            "out vec2 vUV;\n" +
            "out vec3 vPos;\n" +
            "void main() {\n" +
            "    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);\n" +
            "    vUV  = UV0;\n" +
            "    vPos = Position;\n" +
            "}\n";

        String frag =
            "#version 150 core\n" +
            "uniform float uTime;\n" +
            "uniform float uSpeed;\n" +
            "uniform float uMin;\n" +
            "uniform float uMax;\n" +
            "uniform vec3  uColor;\n" +
            "uniform float uEdge;\n" +
            "in  vec2 vUV;\n" +
            "in  vec3 vPos;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            // Smooth sine pulse between min and max
            "    float pulse = sin(uTime * uSpeed * 6.28318) * 0.5 + 0.5;\n" +
            "    float glow = mix(uMin, uMax, pulse);\n" +
            // Optional edge dimming — center is brighter, rim is darker
            "    float dist = length(vUV - 0.5);\n" +
            "    float edge = mix(1.0, 1.0 - dist * 2.0, uEdge);\n" +
            "    edge = clamp(edge, 0.0, 1.0);\n" +
            "    vec3 col = uColor * glow * edge;\n" +
            "    float alpha = clamp(glow * edge, 0.0, 1.0);\n" +
            "    fragColor = vec4(col, alpha);\n" +
            "}\n";

        shader.setVertexShaderSource(vert);
        shader.setFragmentShaderSource(frag);
        return shader;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  BLINKING SHADER
    //  A ring of brightness that starts at the center and expands outward
    //  to the rim, then fades. Repeats cyclically.
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * TEMPLATE: Blinking — radial blink expanding from middle to rim.
     * <p>
     * A bright ring starts at the center of the UV space, grows outward
     * to the rim, then fades away and restarts. The ring has a soft glow.
     *
     * @param speed     expansion cycles per second
     * @param ringWidth  thickness of the bright ring (0..1, e.g. 0.15)
     * @param r,g,b     ring color
     * @param intensity  brightness multiplier
     */
    public static FomekShader buildBlinkingShader(
            float speed, float ringWidth,
            float r, float g, float b, float intensity) {

        FomekShader shader = new FomekShader();
        shader.setFloat("uTime",      getShaderTime());
        shader.setFloat("uSpeed",     speed);
        shader.setFloat("uWidth",     ringWidth);
        shader.setFloat("uIntensity", intensity);
        shader.setVec3("uColor",      r, g, b);

        String vert =
            "#version 150 core\n" +
            "in vec3 Position;\n" +
            "in vec2 UV0;\n" +
            "uniform mat4 ModelViewMat;\n" +
            "uniform mat4 ProjMat;\n" +
            "out vec2 vUV;\n" +
            "void main() {\n" +
            "    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);\n" +
            "    vUV = UV0;\n" +
            "}\n";

        String frag =
            "#version 150 core\n" +
            "uniform float uTime;\n" +
            "uniform float uSpeed;\n" +
            "uniform float uWidth;\n" +
            "uniform float uIntensity;\n" +
            "uniform vec3  uColor;\n" +
            "in  vec2 vUV;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            // Distance from UV center, normalized 0..~0.707
            "    float dist = length(vUV - 0.5);\n" +
            // Ring position cycles from 0 (center) to 1 (rim)
            "    float ringPos = fract(uTime * uSpeed);\n" +
            // Bright glow band centered on ringPos
            "    float band = smoothstep(uWidth, 0.0, abs(dist - ringPos));\n" +
            // Fade out as ring approaches the rim
            "    float rimFade = 1.0 - smoothstep(0.6, 0.71, ringPos);\n" +
            // Combine
            "    float glow = band * rimFade * uIntensity;\n" +
            // Faint base tint everywhere
            "    vec3 col = uColor * (0.1 + glow);\n" +
            "    float alpha = 0.15 + glow * 0.85;\n" +
            "    fragColor = vec4(col, alpha);\n" +
            "}\n";

        shader.setVertexShaderSource(vert);
        shader.setFragmentShaderSource(frag);
        return shader;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TEMPORAL WAVE SHADER
    //  Multiple random wavefronts emanate from the center, grow outward
    //  to the rim, and dissolve. New waves spawn at random intervals with
    //  slightly different directions and speeds — all procedural.
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * TEMPLATE: Temporal Wave — random expanding wavefronts from center.
     * <p>
     * Procedural waves emanate from the UV center and expand outward.
     * Each wave has slightly randomized timing, direction offset, and speed.
     * Waves fade as they reach the rim, then new ones appear.
     *
     * @param speed       base wave speed (waves per second)
     * @param numWaves    how many simultaneous wavefronts (1..6)
     * @param r,g,b       wave color
     * @param intensity    brightness multiplier
     */
    public static FomekShader buildTemporalWaveShader(
            float speed, float numWaves,
            float r, float g, float b, float intensity) {

        FomekShader shader = new FomekShader();
        shader.setFloat("uTime",      getShaderTime());
        shader.setFloat("uSpeed",     speed);
        shader.setFloat("uWaves",     numWaves);
        shader.setFloat("uIntensity", intensity);
        shader.setVec3("uColor",      r, g, b);

        String vert =
            "#version 150 core\n" +
            "in vec3 Position;\n" +
            "in vec2 UV0;\n" +
            "uniform mat4 ModelViewMat;\n" +
            "uniform mat4 ProjMat;\n" +
            "out vec2 vUV;\n" +
            "void main() {\n" +
            "    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);\n" +
            "    vUV = UV0;\n" +
            "}\n";

        String frag =
            "#version 150 core\n" +
            "uniform float uTime;\n" +
            "uniform float uSpeed;\n" +
            "uniform float uWaves;\n" +
            "uniform float uIntensity;\n" +
            "uniform vec3  uColor;\n" +
            "in  vec2 vUV;\n" +
            "out vec4 fragColor;\n" +

            "float hash1(float n) {\n" +
            "    return fract(sin(n * 12.9898) * 43758.5453);\n" +
            "}\n" +

            "void main() {\n" +
            "    vec2  ctr  = vUV - 0.5;\n" +
            "    float dist = length(ctr);\n" +
            "    float t    = uTime * uSpeed;\n" +
            "    int   N    = int(clamp(uWaves, 1.0, 6.0));\n" +
            "    float glow = 0.0;\n" +
            "    for (int i = 0; i < 6; i++) {\n" +
            "        if (i >= N) break;\n" +
            "        float h1   = hash1(float(i) * 1.37 + 0.13);\n" +
            "        float h2   = hash1(float(i) * 2.71 + 0.89);\n" +
            "        float h3   = hash1(float(i) * 4.19 + 1.61);\n" +
            "        float period = 1.0 + h1 * 0.8;\n" +
            "        float phase  = fract(t / period + h2);\n" +
            "        float front  = phase * 0.75;\n" +
            "        float band   = smoothstep(0.08, 0.0, abs(dist - front));\n" +
            "        float life   = smoothstep(0.0, 0.15, phase) * (1.0 - smoothstep(0.7, 1.0, phase));\n" +
            "        float angle  = atan(ctr.y, ctr.x);\n" +
            "        float dirK  = 1.0 + h3 * 0.3 * cos(angle * (1.0 + h2 * 4.0));\n" +
            "        glow += band * life * dirK;\n" +
            "    }\n" +
            "    glow *= uIntensity;\n" +
            "    vec3  col  = uColor * (0.08 + glow);\n" +
            "    float alpha = 0.1 + glow * 0.9;\n" +
            "    fragColor = vec4(col, alpha);\n" +
            "}\n";

        shader.setVertexShaderSource(vert);
        shader.setFragmentShaderSource(frag);
        return shader;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  STORM SHADER
    //  Procedural noise-based waving with smoothly changing direction and
    //  speed. Uses multi-octave FBM noise whose domain is warped by a slow
    //  time-driven rotation, so the waving pattern organically shifts
    //  direction and tempo without hard transitions.
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * TEMPLATE: Storm — organic waving with randomly shifting direction & speed.
     * <p>
     * Multi-octave noise creates a flowing wave pattern on the surface.
     * A slow rotation of the noise domain changes the flow direction smoothly
     * over time, and a time-varying scale changes the speed — so the pattern
     * never looks the same twice and never hard-transitions.
     *
     * @param speed     base flow speed
     * @param scale     noise detail (higher = tighter waves, e.g. 3.0)
     * @param r,g,b     storm color
     * @param intensity  brightness multiplier
     */
    public static FomekShader buildStormShader(
            float speed, float scale,
            float r, float g, float b, float intensity) {

        FomekShader shader = new FomekShader();
        shader.setFloat("uTime",      getShaderTime());
        shader.setFloat("uSpeed",     speed);
        shader.setFloat("uScale",     scale);
        shader.setFloat("uIntensity", intensity);
        shader.setVec3("uColor",      r, g, b);

        String vert =
            "#version 150 core\n" +
            "in vec3 Position;\n" +
            "in vec2 UV0;\n" +
            "uniform mat4 ModelViewMat;\n" +
            "uniform mat4 ProjMat;\n" +
            "out vec2 vUV;\n" +
            "out vec3 vPos;\n" +
            "void main() {\n" +
            "    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);\n" +
            "    vUV  = UV0;\n" +
            "    vPos = Position;\n" +
            "}\n";

        String frag =
            "#version 150 core\n" +
            "uniform float uTime;\n" +
            "uniform float uSpeed;\n" +
            "uniform float uScale;\n" +
            "uniform float uIntensity;\n" +
            "uniform vec3  uColor;\n" +
            "in  vec2 vUV;\n" +
            "in  vec3 vPos;\n" +
            "out vec4 fragColor;\n" +

            "float hash(vec2 p) {\n" +
            "    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);\n" +
            "}\n" +
            "float noise(vec2 p) {\n" +
            "    vec2 i = floor(p);\n" +
            "    vec2 f = fract(p);\n" +
            "    f = f * f * (3.0 - 2.0 * f);\n" +
            "    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x),\n" +
            "               mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y);\n" +
            "}\n" +
            "float fbm(vec2 p) {\n" +
            "    float v = 0.0, a = 0.5;\n" +
            "    for (int i = 0; i < 5; i++) {\n" +
            "        v += a * noise(p);\n" +
            "        p *= 2.0;\n" +
            "        a *= 0.5;\n" +
            "    }\n" +
            "    return v;\n" +
            "}\n" +

            "void main() {\n" +
            "    float t = uTime * uSpeed;\n" +
            "    vec2  p = vUV * uScale;\n" +
            // Slow rotation — direction changes smoothly
            "    float rotAngle = sin(t * 0.13) * 3.14159;\n" +
            "    float ca = cos(rotAngle), sa = sin(rotAngle);\n" +
            "    vec2  rotated = vec2(p.x * ca - p.y * sa, p.x * sa + p.y * ca);\n" +
            // Speed varies slowly — tempo changes organically
            "    float speedMod = 0.7 + 0.6 * sin(t * 0.21) * cos(t * 0.09);\n" +
            "    vec2  flow = vec2(t * speedMod, t * speedMod * 0.6);\n" +
            // Domain warp
            "    vec2 q = vec2(fbm(rotated + flow),\n" +
            "                  fbm(rotated + flow + vec2(5.2, 1.3)));\n" +
            "    vec2 r = vec2(fbm(rotated + q + vec2(1.7, 9.2)),\n" +
            "                  fbm(rotated + q + vec2(8.3, 2.8)));\n" +
            "    float n = fbm(rotated + r);\n" +
            // Wave pattern
            "    float wave = sin((n + r.x) * 6.28318 + t * 2.0) * 0.5 + 0.5;\n" +
            "    float energy = n * 0.5 + wave * 0.5;\n" +
            // Bright streaks
            "    float streak = smoothstep(0.55, 0.75, n) * smoothstep(0.8, 0.6, r.y);\n" +
            "    energy += streak * 0.4;\n" +
            // Color
            "    float hueShift = sin(energy * 3.0 + t * 0.3) * 0.1;\n" +
            "    vec3 col = uColor * (energy + hueShift);\n" +
            "    col += streak * uColor * uIntensity * 0.8;\n" +
            "    col *= uIntensity;\n" +
            // Alpha
            "    float alpha = 0.25 + energy * 0.55 + streak * 0.2;\n" +
            "    alpha = clamp(alpha, 0.0, 1.0);\n" +
            "    fragColor = vec4(col, alpha);\n" +
            "}\n";

        shader.setVertexShaderSource(vert);
        shader.setFragmentShaderSource(frag);
        return shader;
    }

    /**
     * Apply a custom GLSL shader program from a FomekShader.
     * Compiles (or retrieves cached) GL program, activates it via glUseProgram,
     * and uploads all uniforms. Called by FomekShader when it has custom GLSL sources.
     */
    public static void applyShaderProgram(FomekShader shader) {
        if (shader == null) return;
        String vertSrc = shader.getVertexShaderSource();
        String fragSrc = shader.getFragmentShaderSource();
        if (vertSrc == null || fragSrc == null || vertSrc.isEmpty() || fragSrc.isEmpty()) return;

        int programId = FomekShader.Manager.getOrCreateProgram(vertSrc, fragSrc);
        if (programId == 0) return;

        org.lwjgl.opengl.GL20.glUseProgram(programId);

        // Upload ModelView and Projection matrices from RenderSystem
        try {
            Matrix4f modelView = RenderSystem.getModelViewMatrix();
            Matrix4f projection = RenderSystem.getProjectionMatrix();
            FomekShader.Manager.applyMatrices(programId, modelView, projection);
        } catch (Exception ignored) {}

        // Apply shader-specific uniforms (uTime, uColor, etc.)
        FomekShader.Manager.applyUniforms(programId, shader);
    }

    /**
     * Build a portal projection shader — projects an end-portal-like scrolling
     * texture onto the item surface. Uses the endPortal render type for the
     * authentic multi-layer portal effect.
     *
     * @param texture     Texture path (e.g. "minecraft:textures/block/end_portal_frame_top.png")
     * @param scrollSpeed UV scroll speed
     * @param uvScale     UV coordinate scale
     * @param r            Red color component (0-1)
     * @param g            Green color component (0-1)
     * @param b            Blue color component (0-1)
     * @param intensity    Effect intensity (0-1)
     * @param transparency Effect transparency (0-1)
     * @return A configured FomekShader with portal projection effect
     */
    public static FomekShader buildPortalProjectionShader(
            String texture, float scrollSpeed, float uvScale,
            float r, float g, float b, float intensity, float transparency) {

        FomekShader shader = new FomekShader();
        shader.setTexture(texture);
        shader.setRenderType("endPortal");
        shader.setFloat("uTime",         getShaderTime());
        shader.setFloat("uScrollSpeed",  scrollSpeed);
        shader.setFloat("uUVScale",      uvScale);
        shader.setVec3("uColor",         r, g, b);
        shader.setFloat("uIntensity",    intensity);
        shader.setFloat("uTransparency", transparency);

        String vert =
            "#version 150 core\n" +
            "in vec3 Position;\n" +
            "in vec2 UV0;\n" +
            "uniform mat4 ModelViewMat;\n" +
            "uniform mat4 ProjMat;\n" +
            "out vec2 vUV;\n" +
            "out vec3 vPos;\n" +
            "void main() {\n" +
            "    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);\n" +
            "    vUV  = UV0;\n" +
            "    vPos = Position;\n" +
            "}\n";

        String frag =
            "#version 150 core\n" +
            "uniform float uTime;\n" +
            "uniform float uScrollSpeed;\n" +
            "uniform float uUVScale;\n" +
            "uniform vec3  uColor;\n" +
            "uniform float uIntensity;\n" +
            "uniform float uTransparency;\n" +
            "in  vec2 vUV;\n" +
            "in  vec3 vPos;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    float t = uTime * uScrollSpeed;\n" +
            "    vec2  p = vUV * uUVScale;\n" +
            // Scrolling diagonal projection
            "    float wave = sin((p.x + p.y) * 3.14159 * 2.0 + t * 2.0) * 0.5 + 0.5;\n" +
            "    float pulse = sin(t * 1.5 + p.x * 4.0) * 0.3 + 0.7;\n" +
            "    float energy = wave * pulse;\n" +
            // Portal swirl pattern
            "    float angle = t * 0.5 + atan(p.y - 0.5, p.x - 0.5);\n" +
            "    float swirl = sin(angle * 3.0) * 0.5 + 0.5;\n" +
            "    energy = energy * 0.6 + swirl * 0.4;\n" +
            // Color
            "    vec3 col = uColor * (energy * uIntensity + 0.2);\n" +
            "    float alpha = clamp(energy * uIntensity * (1.0 - uTransparency) + uTransparency * 0.3, 0.0, 1.0);\n" +
            "    fragColor = vec4(col, alpha);\n" +
            "}\n";

        shader.setVertexShaderSource(vert);
        shader.setFragmentShaderSource(frag);
        return shader;
    }

    /**
     * TEMPLATE: Octahedron model — 8-triangle bipyramid (crystal shape).
     * Returns a FomekBEWRL.Model ready to render.
     */
    public static FomekBEWRL.Model buildOctahedronModel(float radius) {
        float r = radius;

        // 6 vertices of the octahedron
        float[] T  = {0,  r, 0};    // top
        float[] Rt = {r, 0, 0};     // right
        float[] F  = {0, 0,  r};    // front
        float[] L  = {-r,0, 0};    // left
        float[] Bk = {0, 0, -r};   // back
        float[] Bo = {0, -r, 0};   // bottom

        // 8 triangle faces (CCW from outside)
        float[][][] faces = {
            {T, F, Rt}, {T, L, F}, {T, Bk, L}, {T, Rt, Bk},
            {Bo, Rt, F}, {Bo, F, L}, {Bo, L, Bk}, {Bo, Bk, Rt}
        };

        // Build as one shape with TRIANGLES mode
        beginShape(com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES, true, false);
        for (float[][] face : faces) {
            for (float[] v : face) {
                addVertexUV(v[0], v[1], v[2], 0, 0, -1);  // -1 = white (0xFFFFFFFF)
            }
        }
        endShape();
        Shape shape = getShape();

        // Build BEWRL model
        beginBEWRL();
        addBEWRLPart(shape, net.minecraft.resources.Identifier.parse("minecraft:textures/block/dirt.png"),
            0, 0, 0, 0, 0, 0, 1, 1, 1, -1, "entityTranslucent");
        endBEWRL();

        return getLastModel();
    }



    // ═════════════════════════════════════════════════════════════════════════
    // ── Overlay Render Context ────────────────────────────────────────────────
    // ═════════════════════════════════════════════════════════════════════════


    // ═════════════════════════════════════════════════════════════════════════
    // ── Overlay Depth-Sorted Render Queue ───────────────────────────────────
    // ═════════════════════════════════════════════════════════════════════════
    // Instead of rendering immediately, overlay render methods enqueue commands
    // with their depth value. After all procedures finish, flushOverlayQueue()
    // sorts by depth (ascending) and executes each command, flushing the buffer
    // after each one so cross-render-type depth ordering is respected.

    private static final List<OverlayRenderCommand> overlayQueue = new ArrayList<>();
    // Saved context for the queue — survives clearCurrentOverlayContext() so
    // flushOverlayQueue() can still access GuiGraphicsExtractor even if user procedures
    // (e.g. MCreator-generated) clear the context before the handler flushes.
    private static FomekRenderEvent.Overlay overlayQueueContext;

    private static class OverlayRenderCommand {
        final float depth;
        final Runnable action;
        final BlendMode blendMode;  // blend mode active when this command was enqueued
        OverlayRenderCommand(float depth, Runnable action, BlendMode blendMode) {
            this.depth = depth;
            this.action = action;
            this.blendMode = blendMode;
        }
    }

    /** Enqueue an overlay render command with a depth value. */
    public static void enqueueOverlay(float depth, Runnable action) {
        overlayQueue.add(new OverlayRenderCommand(depth, action, currentBlendMode));
    }

    /**
     * Execute all queued overlay render commands in depth-sorted order.
     * Lower depth = drawn first (behind), higher depth = drawn last (in front).
     * Flushes the shared buffer after each command so cross-render-type
     * depth ordering is respected.
     */
    public static void flushOverlayQueue() {
        if (overlayQueue.isEmpty()) return;
        overlayQueue.sort((a, b) -> Float.compare(a.depth, b.depth));

        // Temporarily restore the overlay context so lambdas that reference
        // currentOverlayContext.getGuiGraphics() work correctly, even if the
        // user's procedure already called clearCurrentOverlayContext().
        FomekRenderEvent.Overlay savedContext = currentOverlayContext;
        if (overlayQueueContext != null) {
            currentOverlayContext = overlayQueueContext;
        }

        GuiGraphicsExtractor gui = currentOverlayContext != null ? currentOverlayContext.getGuiGraphics() : null;
        MultiBufferSource.BufferSource buffer = gui != null ? gui.bufferSource() : null;

        for (OverlayRenderCommand cmd : overlayQueue) {
            // Restore blend mode for this command
            BlendMode savedBlend = currentBlendMode;
            currentBlendMode = cmd.blendMode;

            cmd.action.run();

            if (cmd.blendMode != null && cmd.blendMode != BlendMode.DEFAULT && buffer != null) {
                // Manual flush with custom blend: access internal buffers via reflection,
                // call setupRenderState() (which enables blend), then OVERRIDE the blend
                // function before drawing. This is the only way to apply custom blend
                // modes because setupRenderState() overrides any global blend state.
                flushBufferWithBlend(buffer, cmd.blendMode);
            } else if (buffer != null) {
                buffer.endBatch();
            }

            currentBlendMode = savedBlend;
        }
        overlayQueue.clear();
        overlayQueueContext = null;

        // Restore the (possibly null) context
        currentOverlayContext = savedContext;
    }

    /**
     * Manually flush a BufferSource with a custom blend mode applied.
     * Uses reflection to access the internal BufferBuilder map, then for each
     * render type: calls setupRenderState(), overrides the blend function,
     * draws with BufferUploader.drawWithShader(), and calls clearRenderState().
     *
     * This bypasses BufferSource.endBatch() which calls setupRenderState() and
     * drawWithShader() in one step, preventing blend function override.
     */
    @SuppressWarnings("unchecked")
    private static void flushBufferWithBlend(MultiBufferSource.BufferSource buf, BlendMode blendMode) {
        try {
            // ── Strategy: find ALL buffer storage in the BufferSource ──────────
            // Minecraft's BufferSource stores buffers in multiple places:
            //   1. fixedBuffers: Map<RenderType, BufferBuilder> — pre-allocated for common types
            //   2. A fallback/dynamic buffer for render types NOT in fixedBuffers
            //      (like energySwirl). This is a single BufferBuilder shared by all
            //      non-fixed types, with the current render type tracked separately.
            //
            // The original code only found ONE map (fixedBuffers) and iterated it.
            // Vertices in the fallback buffer (energySwirl) were silently lost.
            // Now we find ALL maps AND the fallback buffer, and draw each with
            // the custom blend override.

            Class<?> bufClass = buf.getClass();

            // ── Collect ALL Map<RenderType, ?> fields ──────────────────────────
            java.util.List<Map<RenderType, Object>> allMaps = new java.util.ArrayList<>();
            for (Class<?> cls = bufClass; cls != null; cls = cls.getSuperclass()) {
                for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(f.getType())) {
                        try {
                            f.setAccessible(true);
                            Object val = f.get(buf);
                            if (val instanceof Map) {
                                // Try to cast — if keys are RenderType, this works
                                try {
                                    @SuppressWarnings("unchecked")
                                    Map<RenderType, Object> m = (Map<RenderType, Object>) val;
                                    allMaps.add(m);
                                } catch (ClassCastException ignored) {}
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            // ── Find the fallback buffer and its associated render type ─────────
            // Common field names for the fallback BufferBuilder:
            //   fallbackBuffer, fallbackBuilder, builder, buffer, immediateBuffer
            // Common field names for the current render type of the fallback:
              //   lastState, lastRoute, lastRenderType, currentType, fallbackType
            java.lang.reflect.Field fallbackField = null;
            for (String fieldName : new String[]{"fallbackBuffer", "fallbackBuilder", "builder", "buffer", "immediateBuffer"}) {
                for (Class<?> cls = bufClass; cls != null && fallbackField == null; cls = cls.getSuperclass()) {
                    try {
                        fallbackField = cls.getDeclaredField(fieldName);
                        break;
                    } catch (NoSuchFieldException ignored) {}
                }
                if (fallbackField != null) break;
            }

            java.lang.reflect.Field lastStateField = null;
            for (String fieldName : new String[]{"lastState", "lastRoute", "lastRenderType", "currentType", "fallbackType"}) {
                for (Class<?> cls = bufClass; cls != null && lastStateField == null; cls = cls.getSuperclass()) {
                    try {
                        lastStateField = cls.getDeclaredField(fieldName);
                        break;
                    } catch (NoSuchFieldException ignored) {}
                }
                if (lastStateField != null) break;
            }

            // ── Draw each buffer with the custom blend override ─────────────────
            boolean drewAny = false;

            // Process all Map<RenderType, BufferBuilder> fields
            for (Map<RenderType, Object> buffers : allMaps) {
                for (Map.Entry<RenderType, Object> entry : buffers.entrySet()) {
                    Object builderObj = entry.getValue();
                    RenderType rt = entry.getKey();
                    if (builderObj == null) continue;

                    drewAny |= drawBufferWithBlend(builderObj, rt, blendMode);
                }
            }

            // Process the fallback buffer (if found) with its associated render type
            if (fallbackField != null && lastStateField != null) {
                try {
                    fallbackField.setAccessible(true);
                    lastStateField.setAccessible(true);
                    Object fallbackBuilder = fallbackField.get(buf);
                    Object lastRt = lastStateField.get(buf);
                    if (fallbackBuilder != null && lastRt instanceof RenderType) {
                        drewAny |= drawBufferWithBlend(fallbackBuilder, (RenderType) lastRt, blendMode);
                    }
                } catch (Exception ignored) {}
            }

            // If we didn't find any buffers via reflection, fall back to endBatch
            if (!drewAny) {
                buf.endBatch();
            }
        } catch (Exception e) {
            // Fallback: just call endBatch
            buf.endBatch();
        }
    }

    /**
     * Draw a single buffer builder's vertices with a custom blend mode.
     * Calls end() to get the RenderedBuffer, sets up the render type's state,
     * overrides the blend function, draws, and clears the state.
     * @return true if any vertices were drawn
     */
    private static boolean drawBufferWithBlend(Object builderObj, RenderType rt, BlendMode blendMode) {
        try {
            java.lang.reflect.Method endMethod = builderObj.getClass().getMethod("end");
            Object rendered = endMethod.invoke(builderObj);
            if (rendered == null) return false;

            // Check vertex count — skip empty buffers
            try {
                java.lang.reflect.Method vcMethod = rendered.getClass().getMethod("vertexCount");
                int vc = (int) vcMethod.invoke(rendered);
                if (vc == 0) return false;
            } catch (Exception ignored) {}

            // Call rt.setupRenderState()
            rt.setupRenderState();

            // Override blend function for our custom blend mode
            applyBlendFunc(blendMode);

            // Draw: BufferUploader.drawWithShader(rendered)
            Class<?> uploaderClass = Class.forName("com.mojang.blaze3d.vertex.BufferUploader");
            java.lang.reflect.Method drawMethod = uploaderClass.getMethod("drawWithShader",
                Class.forName("com.mojang.blaze3d.vertex.BufferBuilder$RenderedBuffer"));
            drawMethod.invoke(null, rendered);

            // Call rt.clearRenderState()
            rt.clearRenderState();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Apply a blend mode's blend function to the current GL state. */
    private static void applyBlendFunc(BlendMode mode) {
        RenderSystem.enableBlend();
        switch (mode) {
            case SUBTRACTION ->
                RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            case ADDITION ->
                RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            case MULTIPLICATION ->
                RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            case ALPHA ->
                RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            case SCREEN ->
                RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            case OPAQUE ->
                RenderSystem.blendFunc(
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            default -> RenderSystem.defaultBlendFunc();
        }
    }

    private static FomekRenderEvent.Overlay currentOverlayContext;

    public static void setCurrentOverlayContext(FomekRenderEvent.Overlay event) {
        currentOverlayContext = event;
        if (event != null) overlayQueueContext = event;
    }

    public static void clearCurrentOverlayContext() {
        currentOverlayContext = null;
        // NOTE: Do NOT clear overlayQueueContext here — flushOverlayQueue() still
        // needs it.  It is cleared after flushOverlayQueue() finishes.
    }

    public static FomekRenderEvent.Overlay getCurrentOverlayContext() {
        return currentOverlayContext;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ── World Render Context ──────────────────────────────────────────────────
    // ═════════════════════════════════════════════════════════════════════════

    private static FomekRenderEvent.World currentWorldContext;

    public static void setCurrentWorldContext(FomekRenderEvent.World event) {
        currentWorldContext = event;
    }

    public static void clearCurrentWorldContext() {
        currentWorldContext = null;
    }

    public static FomekRenderEvent.World getCurrentWorldContext() {
        return currentWorldContext;
    }

    // ── World render getters ──────────────────────────────────────────────────

    public static float getWorldX() { return currentWorldContext != null ? currentWorldContext.getX() : 0f; }
    public static float getWorldY() { return currentWorldContext != null ? currentWorldContext.getY() : 0f; }
    public static float getWorldZ() { return currentWorldContext != null ? currentWorldContext.getZ() : 0f; }

    public static net.minecraft.client.Camera getCamera() {
        return currentWorldContext != null ? currentWorldContext.getCamera() : null;
    }

    public static float getWorldPartialTick() {
        return currentWorldContext != null ? currentWorldContext.getPartialTick() : 0f;
    }

    // ── Shared "active" context helper ───────────────────────────────────────
    // Returns a PoseStack from whichever render context is currently active.

    public static com.mojang.blaze3d.vertex.PoseStack getActivePoseStack() {
        if (currentContext       != null) return currentContext.getPoseStack();
        if (currentWorldContext  != null) return currentWorldContext.getPoseStack();
        // Overlay: GuiGraphicsExtractor owns the PoseStack
        if (currentOverlayContext != null) return currentOverlayContext.getGuiGraphics().pose();
        return null;
    }

    public static net.minecraft.client.renderer.MultiBufferSource getActiveBufferSource() {
        if (currentContext       != null) return currentContext.getBufferSource();
        if (currentWorldContext  != null) return currentWorldContext.getBufferSource();
        // Overlay: use Minecraft's shared buffer
        if (currentOverlayContext != null) return net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource();
        return null;
    }

    public static net.minecraft.world.level.Level getActiveWorld() {
        if (currentContext       != null) return currentContext.getWorld();
        if (currentWorldContext  != null) return currentWorldContext.getWorld();
        if (currentOverlayContext != null) return net.minecraft.client.Minecraft.getInstance().level;
        return null;
    }

    public static float getActivePartialTick() {
        if (currentContext       != null) return currentContext.getPartialTick();
        if (currentWorldContext  != null) return currentWorldContext.getRenderTime();
        if (currentOverlayContext != null) return currentOverlayContext.getPartialTick();
        return 0f;
    }


    public static int getActivePackedLight() {
        if (currentContext != null) return currentContext.getPackedLight();
        if (currentWorldContext != null) {
            return net.minecraft.client.renderer.LevelRenderer.getLightColor(
                    currentWorldContext.getWorld(),
                    net.minecraft.core.BlockPos.containing(
                            currentWorldContext.getX(),
                            currentWorldContext.getY(),
                            currentWorldContext.getZ()));
        }
        return net.minecraft.client.renderer.LightCoordsUtil.FULL_BRIGHT;
    }

    public static int getActivePackedOverlay() {
        if (currentContext != null) return currentContext.getPackedOverlay();
        return net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
    }

    // ── Dropped-item control ──────────────────────────────────────────────────

    /** Cancel the spin rotation of the current dropped item entity (in Item Render context). */
    public static void cancelDroppedItemSpin() {
        if (currentContext == null) return;
        net.minecraft.world.entity.Entity ent = currentContext.getEntity();
        if (ent instanceof net.minecraft.world.entity.item.ItemEntity ie) {
            FomekItemEntityFlags.setCancelSpin(ie, true);
        }
    }

    /** Cancel the shadow of the current dropped item entity (in Item Render context). */
    public static void cancelDroppedItemShadow() {
        if (currentContext == null) return;
        net.minecraft.world.entity.Entity ent = currentContext.getEntity();
        if (ent instanceof net.minecraft.world.entity.item.ItemEntity ie) {
            FomekItemEntityFlags.setCancelShadow(ie, true);
        }
    }

    // ── Template Shader methods ──────────────────────────────────────────────────

    public static FomekShader tplSolidColor(float r, float g, float b, float a) {
        FomekShader shader = new FomekShader();
        shader.setColor((int)(a * 255) << 24 | (int)(r * 255) << 16 | (int)(g * 255) << 8 | (int)(b * 255));
        shader.setRenderType("entityCutoutNoCull");
        return shader;
    }

    public static FomekShader tplGlowPulse(float r, float g, float b, float speed) {
        FomekShader shader = new FomekShader();
        shader.setGlowing(true);
        shader.setFloat("uTime", getShaderTime());
        shader.setFloat("uSpeed", speed);
        shader.setVec3("uColor", r, g, b);
        shader.setFragmentShaderSource(
            "#version 150 core\n" +
            "uniform float uTime;\n" +
            "uniform float uSpeed;\n" +
            "uniform vec3 uColor;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    float pulse = 0.5 + 0.5 * sin(uTime * uSpeed);\n" +
            "    fragColor = vec4(uColor * pulse, 1.0);\n" +
            "}\n");
        return shader;
    }

    public static FomekShader tplRainbow(float speed) {
        FomekShader shader = new FomekShader();
        shader.setFloat("uTime", getShaderTime());
        shader.setFloat("uSpeed", speed);
        shader.setFragmentShaderSource(
            "#version 150 core\n" +
            "uniform float uTime;\n" +
            "uniform float uSpeed;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    float t = uTime * uSpeed;\n" +
            "    vec3 col = vec3(0.5+0.5*sin(t), 0.5+0.5*sin(t+2.0), 0.5+0.5*sin(t+4.0));\n" +
            "    fragColor = vec4(col, 1.0);\n" +
            "}\n");
        return shader;
    }

    public static FomekShader tplDissolve(float threshold, float edgeWidth, float r, float g, float b) {
        FomekShader shader = new FomekShader();
        shader.setFloat("uTime", getShaderTime());
        shader.setFloat("uThreshold", threshold);
        shader.setFloat("uEdgeWidth", edgeWidth);
        shader.setVec3("uColor", r, g, b);
        shader.setFragmentShaderSource(
            "#version 150 core\n" +
            "uniform float uTime;\n" +
            "uniform float uThreshold;\n" +
            "uniform float uEdgeWidth;\n" +
            "uniform vec3 uColor;\n" +
            "in float vScanY;\n" +
            "out vec4 fragColor;\n" +
            "float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }\n" +
            "void main() {\n" +
            "    float noise = hash(vec2(vScanY * 100.0, uTime));\n" +
            "    float edge = smoothstep(uThreshold - uEdgeWidth, uThreshold, noise);\n" +
            "    fragColor = vec4(uColor, edge);\n" +
            "}\n");
        return shader;
    }

    public static FomekShader tplFire(float speed, float intensity) {
        FomekShader shader = new FomekShader();
        shader.setFloat("uTime", getShaderTime());
        shader.setFloat("uSpeed", speed);
        shader.setFloat("uIntensity", intensity);
        shader.setFragmentShaderSource(
            "#version 150 core\n" +
            "uniform float uTime;\n" +
            "uniform float uSpeed;\n" +
            "uniform float uIntensity;\n" +
            "in float vScanY;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    float t = uTime * uSpeed;\n" +
            "    float fire = vScanY + sin(vScanY * 10.0 + t) * 0.1;\n" +
            "    vec3 col = mix(vec3(1.0, 0.2, 0.0), vec3(1.0, 0.9, 0.2), fire);\n" +
            "    fragColor = vec4(col * uIntensity, 1.0);\n" +
            "}\n");
        return shader;
    }

    public static FomekShader tplHologram(float r, float g, float b, float scanSpeed, float lineFreq) {
        FomekShader shader = new FomekShader();
        shader.setFloat("uTime", getShaderTime());
        shader.setFloat("uSpeed", scanSpeed);
        shader.setFloat("uLineFreq", lineFreq);
        shader.setVec3("uColor", r, g, b);
        shader.setFragmentShaderSource(
            "#version 150 core\n" +
            "uniform float uTime;\n" +
            "uniform float uSpeed;\n" +
            "uniform float uLineFreq;\n" +
            "uniform vec3 uColor;\n" +
            "in float vScanY;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    float scan = step(0.5, fract(vScanY * uLineFreq + uTime * uSpeed));\n" +
            "    fragColor = vec4(uColor, scan * 0.8 + 0.2);\n" +
            "}\n");
        return shader;
    }


    // ═════════════════════════════════════════════════════════════
    // Inner class: Shape (merged from Shape.java)
    // ═════════════════════════════════════════════════════════════

    public static class Shape {

        // ── Vertex storage ────────────────────────────────────────────────────────

            public static class VertexData {
                public float x, y, z;
                public float u, v;
                public int color;
                public boolean hasUV;
                public float nx = 0, ny = 1, nz = 0; // per-vertex normal (default up)

                public VertexData(float x, float y, float z, int color) {
                    this.x = x; this.y = y; this.z = z;
                    this.color = color;
                    this.hasUV = false;
                }

                public VertexData(float x, float y, float z, float u, float v, int color) {
                    this.x = x; this.y = y; this.z = z;
                    this.u = u; this.v = v;
                    this.color = color;
                    this.hasUV = true;
                }

                public VertexData(float x, float y, float z, float u, float v,
                                   float nx, float ny, float nz, int color) {
                    this.x = x; this.y = y; this.z = z;
                    this.u = u; this.v = v;
                    this.nx = nx; this.ny = ny; this.nz = nz;
                    this.color = color;
                    this.hasUV = true;
                }
            }

            private final List<VertexData> vertices = new ArrayList<>();
            private VertexFormat.Mode mode = VertexFormat.Mode.QUADS;
            private boolean hasTexture = true;
            private boolean begun = false;
            private boolean ended = false;

            // ── Building ─────────────────────────────────────────────────────────────

            public void begin(VertexFormat.Mode mode, boolean hasTexture) {
                this.mode = mode;
                this.hasTexture = hasTexture;
                this.vertices.clear();
                this.begun = true;
                this.ended = false;
            }

            public void addVertex(float x, float y, float z, int color) {
                if (!begun) return;
                vertices.add(new VertexData(x, y, z, color));
            }

            public void addVertexUV(float x, float y, float z, float u, float v, int color) {
                if (!begun) return;
                vertices.add(new VertexData(x, y, z, u, v, color));
            }

            public void addVertexUVNormal(float x, float y, float z, float u, float v,
                                           float nx, float ny, float nz, int color) {
                if (!begun) return;
                vertices.add(new VertexData(x, y, z, u, v, nx, ny, nz, color));
            }

            public void end() {
                if (!begun) return;
                this.ended = true;
            }

            public boolean hasEnded() {
                return ended;
            }

            public void clear() {
                vertices.clear();
                begun = false;
                ended = false;
            }

            public boolean isEmpty() {
                return vertices.isEmpty();
            }

            public int vertexCount() {
                return vertices.size();
            }

            public List<VertexData> getVertices() {
                return vertices;
            }

            public VertexFormat.Mode getMode() {
                return mode;
            }

            public boolean hasTexture() {
                return hasTexture;
            }

            // ── RenderType resolution ──────────────────────────────────────────────────

            /**
             * Map a render-type name string to the appropriate RenderType with the
             * given texture.  This is how the part's render_type field from JSON
             * gets translated into a real Minecraft RenderType.
             *
             * IMPORTANT: entityTranslucentEmissive now maps to the REAL vanilla
             * RenderTypes.entityTranslucentEmissive(texture) method added in 1.21.1.
             * Previously this was incorrectly mapped to entityTranslucent() which
             * shared the same shader as the player skin, or eyes() which used
             * NO_DEPTH_TEST and drew on top of everything.
             */

            // ── Custom FomekSwirl RenderType ───────────────────────────────────────────

            /**
             * Create a custom "fomekSwirl" RenderType — our own swirl system.
             *
             * Unlike vanilla energySwirl which has a FIXED additive blend baked into
             * the RenderType, fomekSwirl lets you choose any blend mode (Addition,
             * Alpha, Screen, Multiplication, etc.) while keeping the animated UV
             * scrolling that makes the swirl effect work.
             *
             * The blend mode is picked up from:
             *   1. The explicit blendMode parameter (if non-null and non-DEFAULT)
             *   2. FomekRenderAPI.currentBlendMode (set via enableBlending())
             *   3. Fallback: ADDITION (same as vanilla energySwirl)
             *
             * The UV offset (xOff, zOff) scrolls the texture coordinates over time,
             * producing the swirl animation — same mechanism as energySwirl.
             */
            public static RenderType createFomekSwirlRenderType(
                    Identifier texture, float xOff, float zOff,
                    BlendMode blendMode) {

                // Resolve blend mode: explicit param → global currentBlendMode → ADDITION default
                BlendMode effectiveBlend = blendMode;
                if (effectiveBlend == null || effectiveBlend == BlendMode.DEFAULT) {
                    effectiveBlend = (currentBlendMode != null) ? currentBlendMode : BlendMode.ADDITION;
                }

                // Custom transparency state shard with the chosen blend mode
                final BlendMode bm = effectiveBlend;
                RenderStateShard.TransparencyStateShard transparency =
                    new RenderStateShard.TransparencyStateShard("fomek_swirl_blend",
                        () -> {
                            RenderSystem.enableBlend();
                            switch (bm) {
                                case ADDITION -> RenderSystem.blendFuncSeparate(
                                    GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
                                case ALPHA -> RenderSystem.blendFuncSeparate(
                                    GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                                case MULTIPLICATION -> RenderSystem.blendFuncSeparate(
                                    GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
                                case SCREEN -> RenderSystem.blendFuncSeparate(
                                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                                case SUBTRACTION -> RenderSystem.blendFuncSeparate(
                                    GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
                                case OPAQUE -> RenderSystem.blendFunc(
                                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
                                default -> RenderSystem.defaultBlendFunc();
                            }
                        },
                        () -> {
                            RenderSystem.disableBlend();
                            RenderSystem.defaultBlendFunc();
                        });

                // Custom texturing state shard for UV scrolling.
                // CRITICAL: Force GL_REPEAT wrapping so the % 1.0f modulo wrap is
                // seamless. Without GL_REPEAT, the texture defaults to CLAMP_TO_EDGE
                // and the UV offset snap from 0.99→0.0 is visible as a "jump".
                // We save the previous wrap mode and restore it in cleanup.
                final float _xOff = xOff;
                final float _zOff = zOff;
                final int[] prevWrap = new int[2]; // [prevWrapS, prevWrapT]
                RenderStateShard.TexturingStateShard texturing =
                    new RenderStateShard.TexturingStateShard("fomek_swirl_texturing",
                        () -> {
                            RenderSystem.setTextureMatrix(new Matrix4f().translation(_xOff, _zOff, 0.0f));
                            // Force GL_REPEAT so UV scroll loops seamlessly
                            prevWrap[0] = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S);
                            prevWrap[1] = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T);
                            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
                            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
                        },
                        () -> {
                            RenderSystem.resetTextureMatrix();
                            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, prevWrap[0]);
                            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, prevWrap[1]);
                        });

                // Get the shader state shard via reflection (private field in RenderType)
                // CRITICAL: Must use the ENERGY_SWIRL shader, not ENTITY_TRANSLUCENT —
                // only the energy swirl shader applies TextureMat to UV coordinates,
                // which is what makes the UV scroll animation actually work.
                // The entity translucent shader ignores TextureMat → no animation.
                RenderStateShard.ShaderStateShard shaderShard = getShaderStateShard("RENDERTYPE_ENERGY_SWIRL_SHADER");
                if (shaderShard == null) shaderShard = getShaderStateShard("ENERGY_SWIRL_SHADER");
                if (shaderShard == null) shaderShard = getShaderStateShard("RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER");
                if (shaderShard == null) shaderShard = getShaderStateShard("ENTITY_TRANSLUCENT_CULL_SHADER");
                if (shaderShard == null) shaderShard = getShaderStateShard("ITEM_ENTITY_TRANSLUCENT_CULL_SHADER");
                if (shaderShard == null) {
                    shaderShard = new RenderStateShard.ShaderStateShard(() -> null);
                }

                return RenderType.create("fomek_swirl",
                    DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS,
                    1536,
                    RenderType.CompositeState.builder()
                        .setLightmapState(new RenderStateShard.LightmapStateShard(true))
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTexturingState(texturing)
                        .setTransparencyState(transparency)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setShaderState(shaderShard)
                        .createCompositeState(true));
            }

            /**
             * Get a private static ShaderStateShard from RenderType by field name.
             * Uses reflection since these fields are package-private in vanilla MC.
             */
            private static RenderStateShard.ShaderStateShard getShaderStateShard(String fieldName) {
                try {
                    Field f = RenderType.class.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    Object val = f.get(null);
                    if (val instanceof RenderStateShard.ShaderStateShard sss) return sss;
                } catch (Exception ignored) {}
                // Also try RenderStateShard class
                try {
                    Field f = RenderStateShard.class.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    Object val = f.get(null);
                    if (val instanceof RenderStateShard.ShaderStateShard sss) return sss;
                } catch (Exception ignored) {}
                return null;
            }

            public static RenderType resolveRenderType(String renderTypeName, Identifier texture) {
                if (renderTypeName == null || renderTypeName.isEmpty()) {
                    renderTypeName = "entityCutoutNoCull";
                }

                // If a custom blend mode is active, use entityTranslucent (which enables
                // blend) so the manual flush in flushBufferWithBlend can override
                // the blend function. entityCutoutNoCull disables blend entirely.
                BlendMode bm = FomekRenderAPI.currentBlendMode;
                if (bm != null && bm != BlendMode.DEFAULT) {
                    return RenderTypes.entityTranslucent(texture);
                }

                switch (renderTypeName) {
                    case "entityCutout":
                        return RenderTypes.entityCutout(texture);
                    case "entityCutoutNoCull":
                        return RenderTypes.entityCutout(texture);
                    case "entityTranslucent":
                        return RenderTypes.entityTranslucent(texture);
                    case "entityTranslucentEmissive":
                        return RenderTypes.entityTranslucentEmissive(texture);
                    case "eyes":
                        return RenderTypes.eyes(texture);
                    case "fomekSwirl":
                        return createFomekSwirlRenderType(texture,
                                FomekRenderAPI.getRenderTime() % 1.0f,
                                FomekRenderAPI.getRenderTime() % 1.0f,
                                currentBlendMode);
                    case "energySwirl":
                        return RenderTypes.energySwirl(texture,
                                FomekRenderAPI.getRenderTime() % 1.0f,
                                FomekRenderAPI.getRenderTime() % 1.0f);
                    // energySwirlCustom handled via resolveRenderType(name, tex, xSpeed, zSpeed)
                    case "dragonExplosionAlpha":
                        return RenderType.dragonExplosionAlpha(texture);
                    // ── Portal / special render types ──────────────────────────────
                    // These don't take a texture parameter — they use their own
                    // built-in textures and shaders.
                    case "endPortal":
                        return RenderTypes.endPortal();
                    case "endGateway":
                        return RenderTypes.endGateway();
                    case "lightning":
                        return RenderTypes.lightning();
                    case "glint":
                        return RenderTypes.glint();
                    case "glintTranslucent":
                        return RenderTypes.glintTranslucent();
                    case "waterMask":
                        return RenderTypes.waterMask();
                    // These take a texture parameter
                    case "entityGlintDirect":
                        return RenderType.entityGlintDirect();
                    case "armorGlint":
                        return RenderTypes.armorEntityGlint();
                    case "outline":
                        return RenderTypes.outline(texture);
                    default:
                        return RenderTypes.entityCutout(texture);
                }
            }

            /**
             * Resolve a RenderType with custom swirl speed params.
             * If renderTypeName is "energySwirl", uses the provided xSpeed/zSpeed
             * to animate the UV offsets.  Falls back to resolveRenderType(name, tex)
             * for all other types.
             */
            public static RenderType resolveRenderType(String renderTypeName, Identifier texture,
                    float swirlXSpeed, float swirlZSpeed) {
                if ("fomekSwirl".equals(renderTypeName)) {
                    float t = FomekRenderAPI.getRenderTime();
                    return createFomekSwirlRenderType(texture,
                            (t * swirlXSpeed) % 1.0f,
                            (t * swirlZSpeed) % 1.0f,
                            currentBlendMode);
                }
                if ("energySwirl".equals(renderTypeName)) {
                    float t = FomekRenderAPI.getRenderTime();
                    return RenderTypes.energySwirl(texture,
                            (t * swirlXSpeed) % 1.0f,
                            (t * swirlZSpeed) % 1.0f);
                }
                return resolveRenderType(renderTypeName, texture);
            }

// ── Rendering ───────────────────────────────────────────────────────────────

            /**
             * Render with a fallback texture. Used by fomek_render_shape.
             * For textured shapes, call the overload with explicit texture instead.
             */
            public void render(PoseStack poseStack, MultiBufferSource bufferSource,
                    float x, float y, float z,
                    float yaw, float pitch, float roll,
                    float xscale, float yscale, float zscale,
                    int color, int packedLight, int packedOverlay) {
                // Fallback: use white texture.  For the manual path (set_texture + render_shape),
                // the texture is already bound via RenderSystem.setShaderTexture, but we can't
                // read it back as a Identifier (getShaderTexture returns int in 1.21.1).
                // So we use a neutral white texture as the default.
                render(poseStack, bufferSource, x, y, z, yaw, pitch, roll,
                       xscale, yscale, zscale, color, packedLight, packedOverlay,
                       Identifier.parse("minecraft:textures/misc/white.png"), "entityCutoutNoCull");
            }

            /**
             * Render with a specific texture and render type.
             * This is the path used by BEWRL models — the texture is baked into
             * the RenderType so it's properly bound at flush time.
             */
            public void render(PoseStack poseStack, MultiBufferSource bufferSource,
                    float x, float y, float z,
                    float yaw, float pitch, float roll,
                    float xscale, float yscale, float zscale,
                    int color, int packedLight, int packedOverlay,
                    Identifier texture, String renderTypeName) {

                if (vertices.isEmpty() || bufferSource == null || !ended) return;

                // Create a RenderType with the correct texture baked in
                RenderType renderType = resolveRenderType(renderTypeName, texture);
                VertexConsumer consumer = bufferSource.getBuffer(renderType);

                poseStack.pushPose();
                poseStack.translate(x, y, z);
                if (yaw   != 0) poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
                if (pitch != 0) poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
                if (roll  != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
                if (xscale != 1 || yscale != 1 || zscale != 1) {
                    poseStack.scale(xscale, yscale, zscale);
                }

                Matrix4f matrix = poseStack.last().pose();

                for (VertexData v : vertices) {
                    int vertColor = (color == -1 || color == 0xFFFFFFFF) ? v.color : color;
                    int r = (vertColor >> 16) & 0xFF;
                    int g = (vertColor >> 8) & 0xFF;
                    int b = vertColor & 0xFF;
                    int a = (vertColor >> 24) & 0xFF;
                    if (a == 0) a = 255;

                    // NeoForge 1.21.1 VertexConsumer API:
                    // addVertex / setColor / setUv / setOverlay / setLight / setNormal
                    // No endVertex() — vertices are implicitly finalized
                    consumer.addVertex(matrix, v.x, v.y, v.z)
                            .setColor(r, g, b, a)
                            .setUv(v.hasUV ? v.u : 0, v.hasUV ? v.v : 0)
                            .setOverlay(packedOverlay)
                            .setLight(packedLight)
                            .setNormal(v.nx, v.ny, v.nz);
                }

                poseStack.popPose();
            }

            /**
             * Render with a custom RenderType (used for custom GLSL shaders).
             */
            public void renderWithRenderType(PoseStack poseStack, MultiBufferSource bufferSource,
                    float x, float y, float z,
                    float yaw, float pitch, float roll,
                    float xscale, float yscale, float zscale,
                    int color, int packedLight, int packedOverlay,
                    RenderType customRenderType) {

                if (vertices.isEmpty() || bufferSource == null || !ended || customRenderType == null) return;

                VertexConsumer consumer = bufferSource.getBuffer(customRenderType);

                poseStack.pushPose();
                poseStack.translate(x, y, z);
                if (yaw   != 0) poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
                if (pitch != 0) poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
                if (roll  != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
                if (xscale != 1 || yscale != 1 || zscale != 1) {
                    poseStack.scale(xscale, yscale, zscale);
                }

                Matrix4f matrix = poseStack.last().pose();

                for (VertexData v : vertices) {
                    int vertColor = (color == -1 || color == 0xFFFFFFFF) ? v.color : color;
                    int r = (vertColor >> 16) & 0xFF;
                    int g = (vertColor >> 8) & 0xFF;
                    int b = vertColor & 0xFF;
                    int a = (vertColor >> 24) & 0xFF;
                    if (a == 0) a = 255;

                    consumer.addVertex(matrix, v.x, v.y, v.z)
                            .setColor(r, g, b, a)
                            .setUv(v.hasUV ? v.u : 0, v.hasUV ? v.v : 0)
                            .setOverlay(packedOverlay)
                            .setLight(packedLight)
                            .setNormal(v.nx, v.ny, v.nz);
                }

                poseStack.popPose();
            }
    }

    // ═════════════════════════════════════════════════════════════
    // Inner class: Fragment (merged from Fragment.java)
    // ═════════════════════════════════════════════════════════════

    public static class Fragment {

        private String name;
            private String code;  // raw GLSL code string

            public Fragment(String name) {
                this.name = name != null ? name : "";
                this.code = "";
            }

            public void setCode(String code) {
                this.code = (code != null ? code : "");
            }

            public String getCode() {
                return code;
            }

            public String getName() {
                return name;
            }
    }

    // ═════════════════════════════════════════════════════════════
    // Inner class: GLSL (merged from GLSL.java)
    // ═════════════════════════════════════════════════════════════

    public static class GLSL {

        private final List<Fragment> fragments = new ArrayList<>();
            private String vertexCode = "";
            private String fragmentCode = "";

            public GLSL() {}

            public static GLSL create() {
                return new GLSL();
            }

            public void addFragment(Fragment frag) {
                if (frag != null) {
                    fragments.add(frag);
                }
            }

            public void setVertexCode(String code) {
                this.vertexCode = (code != null ? code : "");
            }

            public void setFragmentCode(String code) {
                this.fragmentCode = (code != null ? code : "");
            }

            public List<Fragment> getFragments() {
                return fragments;
            }

            public String getVertexCode() {
                return vertexCode;
            }

            public String getFragmentCode() {
                return fragmentCode;
            }

            public void build(FomekShader shader) {
                if (shader == null) {
                    return;
                }

                // Build vertexSource = vertexCode (if not null/empty)
                String vertexSource = vertexCode;
                if (vertexSource != null && !vertexSource.trim().isEmpty()) {
                    shader.setVertexShaderSource(vertexSource);
                }

                // Build fragmentSource = join all fragments' getCode() with "\n" + "\n" + fragmentCode (if not null/empty)
                StringBuilder sb = new StringBuilder();
                for (Fragment frag : fragments) {
                    if (frag != null) {
                        String code = frag.getCode();
                        if (code != null && !code.trim().isEmpty()) {
                            if (sb.length() > 0) {
                                sb.append("\n");
                            }
                            sb.append(code);
                        }
                    }
                }

                if (fragmentCode != null && !fragmentCode.trim().isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(fragmentCode);
                }

                String fragmentSource = sb.toString();
                if (!fragmentSource.trim().isEmpty()) {
                    shader.setFragmentShaderSource(fragmentSource);
                }
            }
    }

    // ═════════════════════════════════════════════════════════════
    // Inner class: SafeBufferSource (merged from SafeBufferSource.java)
    // ═════════════════════════════════════════════════════════════

    public static class SafeBufferSource implements MultiBufferSource {

        private final MultiBufferSource delegate;
            private final Set<RenderType> usedTypes = new HashSet<>();

            public SafeBufferSource(MultiBufferSource delegate) {
                this.delegate = delegate;
            }

            @Override
            public VertexConsumer getBuffer(RenderType renderType) {
                usedTypes.add(renderType);
                return delegate.getBuffer(renderType);
            }

            /**
             * Flush ONLY the render types that this wrapper wrote to.
             * If the delegate is a BufferSource, this calls endBatch(renderType)
             * for each used type — flushing only the custom model's geometry,
             * not the player's pending geometry in other render types.
             */
            public void flushExact() {
                if (delegate instanceof MultiBufferSource.BufferSource bs) {
                    for (RenderType rt : usedTypes) {
                        bs.endBatch(rt);
                    }
                }
                usedTypes.clear();
            }

            /**
             * Reset tracked types without flushing (used when the event was not canceled).
             */
            public void reset() {
                usedTypes.clear();
            }
    }

}
