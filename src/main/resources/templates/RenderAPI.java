package __RENDERAPI_PACKAGE__;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.util.LightCoordsUtil;
import com.mojang.blaze3d.platform.Lighting;
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
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.BlendFunction;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.server.packs.resources.Resource;
import java.io.InputStream;
import java.util.Optional;
import java.util.HashMap;

public class RenderAPI {

    // ── Render Context ─────────────────────────────────────────────────────────

    private static RenderEvent.Item currentContext;
    private static boolean bypassMixin = false;

    public static void setCurrentContext(RenderEvent.Item event) {
        currentContext = event;
        bypassMixin = false;
    }

    public static void clearCurrentContext() {
        currentContext = null;
        bypassMixin = false;
    }

    public static boolean isBypassMixin() { return bypassMixin; }

    /** Set the bypass mixin flag — when true, the ItemRenderer mixin skips the render event. */
    public static void setBypassMixin(boolean value) { bypassMixin = value; }

    /** True when we're inside an item render event (FIRST_PERSON, THIRD_PERSON, GUI, GROUND). */
    public static boolean isInItemContext() { return currentContext != null; }

    // ── Registration Context ────────────────────────────────────────────────────

    private static BEWRL.RegisterEvent currentRegEvent;

    /** Called by the trigger handler before executing the procedure body. */
    public static void setCurrentRegEvent(BEWRL.RegisterEvent event) {
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

    /**
     * Get the slowmo-adjusted partial tick for a specific entity.
     * Uses TickRateClientManager which resolves: entity -> chunk -> server,
     * and applies perception. Falls back to vanilla getPartialTick() if the
     * slowmo system is not active or not installed.
     */
    public static float getEntitySlowmoPartialTick(net.minecraft.world.entity.Entity entity) {
        if (entity == null) return getPartialTick();
        // Optional FomekCore bridge (reflection): null when FomekCore is not
        // installed or its API is not enabled for this workspace.
        FomekCoreCompat.DeltaInfo info = FomekCoreCompat.getEntityDeltaInfo(entity);
        if (info != null) return info.partialTick;
        return getPartialTick();
    }

    /**
     * Get the slowmo-adjusted delta ticks for a specific entity.
     * Returns how many ticks elapsed this frame for this entity (can be < 1 for
     * slowmo, > 1 for sprint). Falls back to 1.0 if not available.
     */
    public static float getEntitySlowmoDeltaTicks(net.minecraft.world.entity.Entity entity) {
        if (entity == null) return 1.0f;
        FomekCoreCompat.DeltaInfo info = FomekCoreCompat.getEntityDeltaInfo(entity);
        if (info != null) return Math.max(0.0f, info.deltaTicks);
        return 1.0f;
    }

    /**
     * Get the slowmo time scale for visual effects. This is the CHUNK's
     * effective rate / 20.0 — NOT the entity's perceived rate.
     *
     * When a chunk is slowed to 1 TPS, this returns 0.05, meaning effects
     * (trails, flickers, fades) animate at 5% speed. This creates the
     * "Flash time" illusion: the player moves at normal speed (perception),
     * but the world's visual effects slow down.
     *
     * Resolution order (as requested):
     *   1. Check the chunk the entity is in (chunk TPS)
     *   2. If no chunk rate, check the entity's own tick state
     *   3. If no entity rate, check server state
     *   4. Default to 20 TPS (scale = 1.0)
     *
     * @return time scale: 1.0 = normal, 0.05 = 20x slow, 0.0 = frozen
     */
    public static float getEntitySlowmoTimeScale(net.minecraft.world.entity.Entity entity) {
        if (entity == null) return 1.0f;
        try {
            // 1. Check chunk first (the environment the entity is in)
            float effectiveRate = FomekCoreCompat.getEntityEffectiveRate(entity);

            // 2. If chunk rate is default (20), check entity's own rate
            if (effectiveRate >= 20.0f) {
                FomekCoreCompat.TickState entityState = FomekCoreCompat.getEntityTickState(entity);
                if (entityState != null && entityState.rate != -1 && entityState.rate < 20.0f) {
                    effectiveRate = entityState.frozen ? 0.0f : entityState.rate;
                }
            }

            return Math.max(0.0f, Math.min(1.0f, effectiveRate / 20.0f));
        } catch (Throwable ignored) {}
        return 1.0f;
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
            ItemTransform transform = resolveDisplayTransform(stack, ctx);
            if (transform != null && transform != ItemTransform.NO_TRANSFORM) {
                // rotation is a Vector3f in degrees: x=pitch, y=yaw, z=roll
                return transform.rotation().y();
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
            ItemTransform transform = resolveDisplayTransform(stack, ctx);
            if (transform != null && transform != ItemTransform.NO_TRANSFORM) {
                // rotation is a Vector3f in degrees: x=pitch, y=yaw, z=roll
                return transform.rotation().x();
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
            ItemTransform transform = resolveDisplayTransform(stack, ctx);
            if (transform != null && transform != ItemTransform.NO_TRANSFORM) {
                return transform.rotation().z();
            }
        } catch (Exception ignored) {
        }
        return 0f;
    }

    // ── 26.1 model resolution ──────────────────────────────────────────────────
    // ItemRenderer.getModel(stack, level, entity, seed) is gone in 26.1.
    // The stack's model is its ITEM_MODEL component id, resolvable via the
    // ModelManager's bakery. (display transforms: ResolvedModel.getTopTransforms)

    /**
     * Resolve a stack's baked quads + display transform through the 26.1 item
     * resolver. Replaces the old resolveItemModel(ResolvedModel) path — resolved
     * models are no longer reachable at runtime, but a scratch render state
     * exposes both the baked quads and the applied context transform.
     */
    private static final class ResolvedItemGeometry {
        final java.util.List<net.minecraft.client.resources.model.geometry.BakedQuad> quads =
                new java.util.ArrayList<>();
        final it.unimi.dsi.fastutil.ints.IntList tints = new it.unimi.dsi.fastutil.ints.IntArrayList();
        ItemTransform transform;
    }

    private static ResolvedItemGeometry resolveItemGeometry(net.minecraft.world.item.ItemStack stack,
            ItemDisplayContext ctx) {
        try {
            if (stack == null || stack.isEmpty()) return null;
            if (ctx == null) ctx = ItemDisplayContext.GUI;
            Minecraft mc = Minecraft.getInstance();
            net.minecraft.client.renderer.item.TrackingItemStackRenderState state =
                    new net.minecraft.client.renderer.item.TrackingItemStackRenderState();
            mc.getItemModelResolver().updateForTopItem(state, stack, ctx, mc.level, mc.player, 0);
            ResolvedItemGeometry out = new ResolvedItemGeometry();
            forEachLayer(state, layer -> {
                try {
                    java.util.List<net.minecraft.client.resources.model.geometry.BakedQuad> quads = layer.prepareQuadList();
                    if (quads != null) out.quads.addAll(quads);
                    it.unimi.dsi.fastutil.ints.IntList tl = layer.tintLayers();
                    if (tl != null && !tl.isEmpty()) out.tints.addAll(tl);
                    if (out.transform == null) {
                        java.lang.reflect.Field f = net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState.class
                                .getDeclaredField("itemTransform");
                        f.setAccessible(true);
                        out.transform = (ItemTransform) f.get(layer);
                    }
                } catch (Throwable ignored) {
                }
            });
            return out.quads.isEmpty() && out.transform == null ? null : out;
        } catch (Throwable e) {
            return null;
        }
    }


    // ── 26.1 item drawing (replaces ItemRenderer.renderStatic) ────────────────
    // Layers of an ItemStackRenderState are private; the only way to reach them
    // for per-layer local transforms / quad extraction is reflection. (26.1 has
    // no public layer accessor; vanilla itself only iterates them internally.)
    private static void forEachLayer(net.minecraft.client.renderer.item.ItemStackRenderState state,
            java.util.function.Consumer<net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState> fn) {
        try {
            java.lang.reflect.Field fl = net.minecraft.client.renderer.item.ItemStackRenderState.class
                    .getDeclaredField("layers");
            fl.setAccessible(true);
            Object[] layers = (Object[]) fl.get(state);
            java.lang.reflect.Field fc = net.minecraft.client.renderer.item.ItemStackRenderState.class
                    .getDeclaredField("activeLayerCount");
            fc.setAccessible(true);
            int count = fc.getInt(state);
            for (int i = 0; i < count; i++) {
                @SuppressWarnings("unchecked")
                net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState layer =
                        (net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState) layers[i];
                fn.accept(layer);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Draw a resolved item state's quads into an arbitrary MultiBufferSource
     * (world / item render context). Replaces ItemRenderer.renderStatic: 26.1
     * emits baked quads via VertexConsumer.putBakedQuad. Foil/glint layers are
     * not supported on this path (vanilla handles glint in ItemFeatureRenderer).
     */
    private static void drawItemQuads(MultiBufferSource buffer, PoseStack pose,
            net.minecraft.client.renderer.item.ItemStackRenderState state, int light, int overlay) {
        final com.mojang.blaze3d.vertex.QuadInstance qi = new com.mojang.blaze3d.vertex.QuadInstance();
        forEachLayer(state, layer -> {
            try {
                java.util.List<net.minecraft.client.resources.model.geometry.BakedQuad> quads = layer.prepareQuadList();
                if (quads == null || quads.isEmpty()) return;
                it.unimi.dsi.fastutil.ints.IntList tints = layer.tintLayers();
                for (net.minecraft.client.resources.model.geometry.BakedQuad quad : quads) {
                    net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo mi = quad.materialInfo();
                    if (mi == null || mi.itemRenderType() == null) continue;
                    qi.setLightCoords(net.minecraft.util.LightCoordsUtil
                            .lightCoordsWithEmission(light, Math.max(0, mi.lightEmission())));
                    qi.setOverlayCoords(overlay);
                    int tint = -1;
                    if (tints != null && !tints.isEmpty() && mi.tintIndex() >= 0 && mi.tintIndex() < tints.size())
                        tint = tints.getInt(mi.tintIndex());
                    qi.setColor(tint);
                    buffer.getBuffer(mi.itemRenderType()).putBakedQuad(pose.last(), quad, qi);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    /**
     * Submit an item into the GUI overlay via the 26.1 render-state pipeline
     * (getItemModelResolver + GuiItemRenderState). x/y use the old center-based
     * semantics; scale 1 = 16px item. yaw/pitch/roll are injected as per-layer
     * local transforms, applied after the GUI display transform.
     */
    private static void submitOverlayGuiItem(GuiGraphicsExtractor gui, ItemStack stack,
            float x, float y, float yaw, float pitch, float roll, float scale) {
        Minecraft mc = Minecraft.getInstance();
        LivingEntity entity = currentOverlayContext != null ? currentOverlayContext.getPlayer() : mc.player;
        Level level = mc.level;
        net.minecraft.client.renderer.item.TrackingItemStackRenderState state =
                new net.minecraft.client.renderer.item.TrackingItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, level, entity, 0);
        if (yaw != 0 || pitch != 0 || roll != 0) {
            // Old stack order: mulPose Y, then X, then Z (Z first applied to a vector)
            final org.joml.Matrix4f m = new org.joml.Matrix4f()
                    .rotationY((float) Math.toRadians(yaw))
                    .mul(new org.joml.Matrix4f().rotationX((float) Math.toRadians(pitch)))
                    .mul(new org.joml.Matrix4f().rotationZ((float) Math.toRadians(roll)));
            forEachLayer(state, layer -> layer.setLocalTransform(m));
        }
        gui.pose().pushMatrix();
        // old code drew the 16px box centered on (x+8, y+8); convert to box origin
        gui.pose().translate(x + 8 - 8 * scale, y + 8 - 8 * scale);
        if (scale != 1) gui.pose().scale(scale, scale);
        try {
            // GuiItemRenderState is not a GuiElementRenderState; vanilla adds items
            // via the extractor's private guiRenderState.addItem(...). Mirror that.
            java.lang.reflect.Field f = GuiGraphicsExtractor.class.getDeclaredField("guiRenderState");
            f.setAccessible(true);
            net.minecraft.client.renderer.state.gui.GuiRenderState grs =
                    (net.minecraft.client.renderer.state.gui.GuiRenderState) f.get(gui);
            grs.addItem(new net.minecraft.client.renderer.state.gui.GuiItemRenderState(
                    new org.joml.Matrix3x2f(gui.pose()), state, 0, 0, null));
        } catch (Throwable ignored) {
        }
        gui.pose().popMatrix();
    }

    private static ItemTransform resolveDisplayTransform(net.minecraft.world.item.ItemStack stack,
            ItemDisplayContext ctx) {
        // 26.1: ResolvedModel instances are not reachable at runtime (the bakery's
        // getModel is on inner classes only). Instead resolve the stack through the
        // item resolver into a scratch render state — the resolver applies the correct
        // per-context ItemTransform to every layer — and read it back from layer 0.
        try {
            Minecraft mc = Minecraft.getInstance();
            LivingEntity entity = mc.player;
            Level level = mc.level;
            net.minecraft.client.renderer.item.TrackingItemStackRenderState state =
                    new net.minecraft.client.renderer.item.TrackingItemStackRenderState();
            mc.getItemModelResolver().updateForTopItem(state, stack, ctx, level, entity, 0);
            final ItemTransform[] found = new ItemTransform[] { null };
            forEachLayer(state, layer -> {
                if (found[0] != null) return;
                try {
                    java.lang.reflect.Field f = net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState.class
                            .getDeclaredField("itemTransform");
                    f.setAccessible(true);
                    found[0] = (ItemTransform) f.get(layer);
                } catch (Throwable ignored) {
                }
            });
            return found[0];
        } catch (Throwable e) {
            return null;
        }
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
            ItemTransform transform = resolveDisplayTransform(stack, ctx);
            if (transform != null && transform != ItemTransform.NO_TRANSFORM) {
                // ItemTransform stores rotation as Vector3f(x=pitch, y=yaw, z=roll) in degrees.
                // The apply() method uses rotationZYX(z, y, x), which via mulPose
                // means: apply Z first, then Y, then X (matching the order below).
                float rZ = transform.rotation().z();
                float rY = transform.rotation().y();
                float rX = transform.rotation().x();
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
            // 26.1: GUI item overlays go through the render-state pipeline.
            // Depth sorting is handled by the enqueueOverlay draw order; there is
            // no Z translate in the 2D GUI pipeline anymore.
            enqueueOverlay(_z, () -> {
                GuiGraphicsExtractor gui = currentOverlayContext.getGuiGraphics();
                submitOverlayGuiItem(gui, _stack, x, y, _yaw, _pitch, _roll, _scale);
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
                light = net.minecraft.client.renderer.LevelRenderer.getLightCoords(level,
                        net.minecraft.core.BlockPos.containing(x, y, z));
            }
        }

        pose.pushPose();
        pose.translate(x, y, z);
        if (yaw   != 0) pose.mulPose(Axis.YP.rotationDegrees(yaw));
        if (pitch != 0) pose.mulPose(Axis.XP.rotationDegrees(pitch));
        if (roll  != 0) pose.mulPose(Axis.ZP.rotationDegrees(roll));
        if (scale != 1) pose.scale(scale, scale, scale);

        // 26.1: renderStatic is gone. Resolve the stack into an item render
        // state and emit its baked quads into the context's buffer directly.
        try {
            net.minecraft.client.renderer.item.TrackingItemStackRenderState _state =
                    new net.minecraft.client.renderer.item.TrackingItemStackRenderState();
            Minecraft.getInstance().getItemModelResolver().updateForTopItem(
                    _state, stack, ItemDisplayContext.NONE, level, entity, 0);
            drawItemQuads(buffer, pose, _state, light, OverlayTexture.NO_OVERLAY);
        } catch (Throwable ignored) {
        }

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
            GuiGraphicsExtractor gui = currentOverlayContext.getGuiGraphics();
            submitOverlayGuiItem(gui, _stack, x, y, _yaw, _pitch, _roll, _scale);
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
        // 26.1: global shader texture slots are gone; textures bind through the
        // render pipeline / blit calls. Retained for API compatibility (no-op).
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
            gui.pose().pushMatrix();
            gui.pose().translate(0, 0); // no Z in the 2D pipeline; enqueueOverlay owns draw order

            gui.fill((int) x1, (int) y1, (int) x2, (int) y2, color);
            gui.pose().popMatrix();
        });
    }

    /**
     * Render a texture on the overlay at screen coordinates.
     * texturePath is a Identifier string like "minecraft:textures/block/stone.png".
     */
    // Cache of real pixel dimensions per texture, so we never force-normalize
    // a texture to a hardcoded size (e.g. always assuming 16x16).
    private static final java.util.Map<net.minecraft.resources.Identifier, int[]> TEXTURE_SIZE_CACHE = new HashMap<>();

    /**
     * Gets the real native pixel dimensions of a texture by reading the PNG
     * directly from the resource manager. Cached after first read.
     * Falls back to 16x16 only if the resource genuinely can't be read.
     */
    private static int[] getNativeTextureSize(net.minecraft.resources.Identifier rl) {
        return TEXTURE_SIZE_CACHE.computeIfAbsent(rl, loc -> {
            try {
                Optional<Resource> resOpt = Minecraft.getInstance().getResourceManager().getResource(loc);
                if (resOpt.isPresent()) {
                    try (InputStream is = resOpt.get().open()) {
                        NativeImage img = NativeImage.read(is);
                        int w = img.getWidth();
                        int h = img.getHeight();
                        img.close();
                        return new int[]{w, h};
                    }
                }
            } catch (Exception ignored) {
            }
            return new int[]{16, 16};
        });
    }

    public static void renderTexture(String texturePath, float x, float y, float depth,
            float angle, float scale, int color, int alignment) {
        if (currentOverlayContext == null) return;
        enqueueOverlay(depth, () -> {
            GuiGraphicsExtractor gui = currentOverlayContext.getGuiGraphics();

            net.minecraft.resources.Identifier rl;
            try { rl = net.minecraft.resources.Identifier.parse(texturePath); }
            catch (Exception e) { return; }

            // Use the texture's REAL native pixel size — no forced normalization.
            // scale=1 now means "render at actual pixel dimensions", whatever they are.
            int[] texSize = getNativeTextureSize(rl);
            final int texW = texSize[0];
            final int texH = texSize[1];

            float drawW = texW * scale;
            float drawH = texH * scale;

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

            gui.pose().pushMatrix();
            gui.pose().translate(ix, iy);
            if (scale != 1f) gui.pose().scale(scale, scale);

            if (angle != 0) {
                gui.pose().translate((float)(texW / 2.0), (float)(texH / 2.0));
                gui.pose().rotate((float) Math.toRadians(angle));
                gui.pose().translate((float)(-texW / 2.0), (float)(-texH / 2.0));
            }

            int blitColor = (color == 0xFFFFFFFF) ? -1 : color;
            gui.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                    rl, 0, 0, 0.0f, 0.0f, texW, texH, texW, texH, blitColor);

            gui.pose().popMatrix();
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

            gui.pose().pushMatrix();
            gui.pose().translate(0, 0); // no Z in the 2D pipeline; enqueueOverlay owns draw order

            gui.pose().translate(ix, iy);

            if (scale != 1.0f) {
                gui.pose().scale(scale, scale);
            }

            if (angle != 0) {
                gui.pose().translate(rawTextW / 2.0f, rawTextH / 2.0f);
                gui.pose().rotate((float) Math.toRadians(angle));
                gui.pose().translate(-rawTextW / 2.0f, -rawTextH / 2.0f);
            }

            gui.text(font, text, 0, 0, argbColor, false);

            gui.pose().popMatrix();
        });
    }

    public static void renderShapeOverlay(Shape shape, float x, float y, float depth,
            float yaw, float pitch, float roll,
            float xscale, float yscale, float zscale, int color) {
        if (currentOverlayContext == null || shape == null || shape.isEmpty()) return;
        if (currentOverlayContext == null || shape == null || shape.isEmpty()) return;
        enqueueOverlay(depth, () -> {
            GuiGraphicsExtractor gui = currentOverlayContext.getGuiGraphics();
            // 26.1: the GUI pipeline is purely 2D (Matrix3x2f pose, no Z buffer),
            // so 3D shapes are software-projected to screen space and emitted as
            // a custom GuiElementRenderState via the flat-colored GUI pipeline.
            // Rotation uses the old stack order (Y, then X, then Z).
            float rad = (float) Math.PI / 180.0f;
            float cy = (float) Math.cos(yaw * rad), sy = (float) Math.sin(yaw * rad);
            float cp = (float) Math.cos(pitch * rad), sp = (float) Math.sin(pitch * rad);
            float cr = (float) Math.cos(roll * rad), sr = (float) Math.sin(roll * rad);
            java.util.List<Shape.VertexData> vs = shape.getVertices();
            float[] px = new float[vs.size()], py = new float[vs.size()];
            int[] pc = new int[vs.size()];
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            final int tint;
            if (color != -1 && color != 0xFFFFFFFF) {
                int ta = (color >> 24) & 0xFF, tr = (color >> 16) & 0xFF, tg = (color >> 8) & 0xFF, tb = color & 0xFF;
                tint = color;
                // applied per-vertex below
                for (int i = 0; i < vs.size(); i++) {
                    Shape.VertexData v = vs.get(i);
                    v.color = ProjectedShapeRenderState.tintColor(v.color, ta, tr, tg, tb);
                }
            } else {
                tint = -1;
            }
            if (tint == -2) return; // never
            for (int i = 0; i < vs.size(); i++) {
                Shape.VertexData v = vs.get(i);
                float vx = v.x, vy = v.y, vz = v.z;
                // yaw (Y)
                float tx = cy * vx + sy * vz;
                float tz = -sy * vx + cy * vz;
                vx = tx; vz = tz;
                // pitch (X)
                float ty = cp * vy - sp * vz;
                tz = sp * vy + cp * vz;
                vy = ty; vz = tz;
                // roll (Z)
                tx = cr * vx - sr * vy;
                ty = sr * vx + cr * vy;
                vx = tx; vy = ty;
                // orthographic projection into screen pixels (slight Y foreshortening for depth cue)
                px[i] = x + vx * xscale;
                py[i] = y + vy * yscale - vz * zscale * 0.35f;
                pc[i] = v.color;
                if (px[i] < minX) minX = px[i];
                if (px[i] > maxX) maxX = px[i];
                if (py[i] < minY) minY = py[i];
                if (py[i] > maxY) maxY = py[i];
            }
            final float[] fx = px, fy = py;
            final int[] fc = pc;
            final int iw = Math.max(1, (int) Math.ceil(maxX - minX));
            final int ih = Math.max(1, (int) Math.ceil(maxY - minY));
            final int bx = (int) Math.floor(minX), by = (int) Math.floor(minY);
            final org.joml.Matrix3x2f fpose = new org.joml.Matrix3x2f(gui.pose());
            final Shape fshape = shape;
            try {
                java.lang.reflect.Field f = GuiGraphicsExtractor.class.getDeclaredField("guiRenderState");
                f.setAccessible(true);
                net.minecraft.client.renderer.state.gui.GuiRenderState grs =
                        (net.minecraft.client.renderer.state.gui.GuiRenderState) f.get(gui);
                grs.addGuiElement(new ProjectedShapeRenderState(fpose, fshape, fx, fy, fc,
                        new net.minecraft.client.gui.navigation.ScreenRectangle(bx, by, iw, ih)));
            } catch (Throwable ignored) {
            }
        });
    }

    /**
     * A GuiElementRenderState that emits a software-projected 3D shape as flat
     * colored 2D triangles. QUADS are triangulated as (0,1,2)+(0,2,3); LINES
     * are approximated with 1px thick quads (the GUI_LINES pipeline needs a
     * different vertex layout with line widths, not worth a separate path).
     */
    private static final class ProjectedShapeRenderState
            implements net.minecraft.client.renderer.state.gui.GuiElementRenderState {
        private final org.joml.Matrix3x2f pose;
        private final Shape shape;
        private final float[] x, y;
        private final int[] c;
        private final net.minecraft.client.gui.navigation.ScreenRectangle bounds;

        ProjectedShapeRenderState(org.joml.Matrix3x2f pose, Shape shape,
                float[] x, float[] y, int[] c, net.minecraft.client.gui.navigation.ScreenRectangle bounds) {
            this.pose = pose; this.shape = shape;
            this.x = x; this.y = y; this.c = c;
            this.bounds = bounds;
        }

        @Override public void buildVertices(com.mojang.blaze3d.vertex.VertexConsumer vc) {
            int n = x.length;
            switch (shape.getMode()) {
                case QUADS -> {
                    for (int i = 0; i + 3 < n; i += 4) {
                        tri(vc, i, i + 1, i + 2);
                        tri(vc, i, i + 2, i + 3);
                    }
                }
                case TRIANGLES -> {
                    for (int i = 0; i + 2 < n; i += 3) tri(vc, i, i + 1, i + 2);
                }
                case TRIANGLE_STRIP -> {
                    for (int i = 0; i + 2 < n; i++) tri(vc, i, i + 1, i + 2);
                }
                case LINES, DEBUG_LINES, DEBUG_LINE_STRIP -> {
                    // 26.1: LINE_STRIP mode removed from VertexFormat.Mode.
                    // DEBUG_LINE_STRIP keeps strip semantics (step 1); paired modes step 2.
                    int step = (shape.getMode() == VertexFormat.Mode.LINES
                            || shape.getMode() == VertexFormat.Mode.DEBUG_LINES) ? 2 : 1;
                    for (int i = 0; i + 1 < n; i += step) {
                        lineQuad(vc, i, i + 1);
                    }
                }
                default -> {
                    for (int i = 0; i + 2 < n; i += 3) tri(vc, i, i + 1, i + 2);
                }
            }
        }

        private void tri(com.mojang.blaze3d.vertex.VertexConsumer vc, int a, int b, int d) {
            vc.addVertexWith2DPose(pose, x[a], y[a]).setColor(c[a]);
            vc.addVertexWith2DPose(pose, x[b], y[b]).setColor(c[b]);
            vc.addVertexWith2DPose(pose, x[d], y[d]).setColor(c[d]);
        }

        private void lineQuad(com.mojang.blaze3d.vertex.VertexConsumer vc, int a, int b) {
            float dx = x[b] - x[a], dy = y[b] - y[a];
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1.0E-4f) return;
            float nx = -dy / len * 0.5f, ny = dx / len * 0.5f; // half-pixel thickness
            int mid = avgColor(c[a], c[b]);
            vc.addVertexWith2DPose(pose, x[a] + nx, y[a] + ny).setColor(c[a]);
            vc.addVertexWith2DPose(pose, x[a] - nx, y[a] - ny).setColor(c[a]);
            vc.addVertexWith2DPose(pose, x[b] - nx, y[b] - ny).setColor(mid);
            vc.addVertexWith2DPose(pose, x[a] + nx, y[a] + ny).setColor(c[a]);
            vc.addVertexWith2DPose(pose, x[b] - nx, y[b] - ny).setColor(mid);
            vc.addVertexWith2DPose(pose, x[b] + nx, y[b] + ny).setColor(mid);
        }

        private static int tintColor(int c, int ta, int tr, int tg, int tb) {
            int a = ((c >> 24) & 0xFF) * ta / 255;
            int r = ((c >> 16) & 0xFF) * tr / 255;
            int g = ((c >> 8) & 0xFF) * tg / 255;
            int b = (c & 0xFF) * tb / 255;
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        private static int avgColor(int c1, int c2) {
            int a = (((c1 >> 24) & 0xFF) + ((c2 >> 24) & 0xFF)) / 2;
            int r = (((c1 >> 16) & 0xFF) + ((c2 >> 16) & 0xFF)) / 2;
            int g = (((c1 >> 8) & 0xFF) + ((c2 >> 8) & 0xFF)) / 2;
            int b = ((c1 & 0xFF) + (c2 & 0xFF)) / 2;
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        @Override public com.mojang.blaze3d.pipeline.RenderPipeline pipeline() {
            return net.minecraft.client.renderer.RenderPipelines.GUI;
        }

        @Override public net.minecraft.client.gui.render.TextureSetup textureSetup() {
            // textured overlay shapes fall back to flat colors: the 26.1 GUI
            // pipeline binds textures as GpuTextureViews, not shader slots
            return net.minecraft.client.gui.render.TextureSetup.noTexture();
        }

        @Override public net.minecraft.client.gui.navigation.ScreenRectangle scissorArea() { return null; }

        @Override public net.minecraft.client.gui.navigation.ScreenRectangle bounds() { return bounds; }
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
        flushActiveBuffer();

        // 26.1: blend functions live in each RenderPipeline's ColorTargetState, so
        // there is no global GL blend state to set here anymore. The active blend
        // mode is consumed where render types are chosen (createSwirlRenderType
        // derives a per-blend pipeline from ENERGY_SWIRL; JavaModelRenderer's
        // resolveRenderType picks a blending-capable type), so the mode gets
        // baked into the actual draw.
        currentBlendMode = mode;
    }

    public static void disableBlending() {
        currentBlendMode = null;
    }
    // 26.1: depth test, culling and depth-mask are pipeline states now — there is
    // no global GL toggle left. These are kept as no-ops for API compatibility
    // with existing procedures; shape/model rendering already expresses them
    // through the render type (pipeline) in use.
    public static void enableDepthTest()  { }
    public static void disableDepthTest() { }
    public static void enableCulling()    { }
    public static void disableCulling()   { }
    public static void enableDepthMask()  { }
    public static void disableDepthMask() { }

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

    // ── Held-item flat lighting fix ──────────────────────────────────────────
    //
    // Minecraft applies DIRECTIONAL diffuse shading (Lighting.setupFor3DItems())
    // to items in FIRST_PERSON / THIRD_PERSON (held) contexts — the same as
    // GROUND (dropped) items. The difference in how it LOOKS between the two:
    //   - A dropped item spins continuously, so over time you see every face
    //     from every angle relative to the fixed diffuse light direction —
    //     some orientations look bright, so it "looks fine" on average.
    //   - A held item's display transform applies a FIXED rotation (baked into
    //     the vanilla item model's firstperson/thirdperson transform) that is
    //     usually very different from GROUND's near-identity transform. That
    //     fixed rotation can consistently point our reconstructed geometry's
    //     normals AWAY from the diffuse light direction — so it's consistently
    //     dark, and any additive glow blended on top of near-black is barely
    //     visible ("glow doesn't work" / "blending doesn't work").
    //
    // Fix: force FLAT lighting (same as GUI — no directional shading, uniform
    // brightness on every face) specifically for held-item contexts, so our
    // custom BEWRL rendering always looks the same regardless of which way
    // the fixed hand transform happens to rotate it. GUI/GROUND/FIXED/NONE
    // are untouched since those already look correct.

    private static boolean isHeldDisplayContext(ItemDisplayContext ctx) {
        if (ctx == null) return false;
        return ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
            || ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
            || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
            || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    /**
     * If we're currently rendering a held item (first/third person), switch
     * to flat (GUI-style) lighting so custom BEWRL geometry renders at even
     * brightness regardless of the hand's fixed display-transform rotation.
     *
     * @return true if flat lighting was applied (caller must restore via
     *         {@link #restoreLightingIfHeld(boolean)} when done).
     */
    private static boolean applyFlatLightingIfHeld() {
        if (currentContext == null) return false;
        if (!isHeldDisplayContext(currentContext.getDisplayContext())) return false;
        try {
            Minecraft.getInstance().gameRenderer.getLighting().setupFor(com.mojang.blaze3d.platform.Lighting.Entry.ITEMS_FLAT);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Restore normal 3D directional lighting after {@link #applyFlatLightingIfHeld()}.
     * Must be called (with the value it returned) even if rendering throws —
     * callers wrap this in try/finally. Leaving flat lighting active would
     * make the player's arm/other hand render flat/bright for the rest of
     * the frame, which looks wrong.
     */
    private static void restoreLightingIfHeld(boolean wasApplied) {
        if (wasApplied) {
            try {
                Minecraft.getInstance().gameRenderer.getLighting().setupFor(com.mojang.blaze3d.platform.Lighting.Entry.ITEMS_3D);
            } catch (Exception ignored) {
            }
        }
    }

    // ── BEWRL (manual build) ───────────────────────────────────────────────────

    private static BEWRL.Model currentBEWRL;
    private static BEWRL.Model lastModel;

    public static void beginBEWRL() {
        currentBEWRL = new BEWRL.Model();
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
     * The model is baked by name from JavaModelRenderer's registry.
     * Orientation fix (180° X rotation) is applied automatically in item render context.
     */
    public static void addBEWRLJavaPart(String javaModelName, net.minecraft.resources.Identifier texture,
            float x, float y, float z, float yaw, float pitch, float roll,
            float xscale, float yscale, float zscale, int color, String renderType) {
        if (currentBEWRL == null || javaModelName == null) return;
        currentBEWRL.addJavaModelPart(javaModelName, texture != null ? texture.toString() : "",
            x, y, z, yaw, pitch, roll, xscale, yscale, zscale, color, renderType);
    }

    public static BEWRL.Model endBEWRL() {
        BEWRL.Model model = currentBEWRL;
        currentBEWRL = null;
        lastModel = model;
        return model;
    }

    public static BEWRL.Model getLastModel() {
        BEWRL.Model m = lastModel;
        lastModel = null;
        return m;
    }

    public static void renderBEWRL(BEWRL.Model model,
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
            final BEWRL.Model _model = model;
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
    public static BEWRL.Model getRegisteredModel(String modelId) {
        return BEWRL.Registry.INSTANCE.getModelCopy(modelId);
    }

    /**
     * Convenience: render a registered model directly by id.
     */
    public static void renderRegisteredModel(String modelId,
            float x, float y, float z,
            float yaw, float pitch, float roll,
            float scale) {
        BEWRL.Model model = BEWRL.Registry.INSTANCE.getModel(modelId);
        if (model != null && !model.isEmpty()) {
            renderBEWRL(model, x, y, z, yaw, pitch, roll, scale);
        }
    }

    /**
     * Convenience: render a registered model with an AnimationController.
     */
    public static void renderRegisteredModel(String modelId, Animation.Controller controller,
            float x, float y, float z,
            float yaw, float pitch, float roll,
            float scale) {
        BEWRL.Model model = BEWRL.Registry.INSTANCE.getModel(modelId);
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
        return BEWRL.Registry.INSTANCE.isRegistered(modelId);
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

    public static void addBEWRLPart(RenderAPI.Shape shape, net.minecraft.resources.Identifier texture,
            BEWRLVars.Vec3 pos, BEWRLVars.Vec3 rot, BEWRLVars.Vec3 scale,
            int color, String renderType) {
        if (currentBEWRL == null || shape == null) return;
        currentBEWRL.addPart(shape, texture != null ? texture.toString() : "",
            pos.x, pos.y, pos.z, rot.x, rot.y, rot.z,
            scale.x, scale.y, scale.z, color, renderType);
    }

    public static void addBEWRLJavaPart(String javaModelName, net.minecraft.resources.Identifier texture,
            BEWRLVars.Vec3 pos, BEWRLVars.Vec3 rot, BEWRLVars.Vec3 scale,
            int color, String renderType) {
        if (currentBEWRL == null || javaModelName == null) return;
        currentBEWRL.addJavaModelPart(javaModelName, texture != null ? texture.toString() : "",
            pos.x, pos.y, pos.z, rot.x, rot.y, rot.z,
            scale.x, scale.y, scale.z, color, renderType);
    }

    public static void addBEWRLTextPart(String text, boolean glowing,
            BEWRLVars.Vec3 pos, BEWRLVars.Vec3 rot, BEWRLVars.Vec3 scale,
            int color) {
        if (currentBEWRL == null || text == null || text.isEmpty()) return;
        currentBEWRL.addTextPart(text, glowing,
            pos.x, pos.y, pos.z, rot.x, rot.y, rot.z,
            scale.x, scale.y, scale.z, color);
    }

    public static void addBEWRLItemPart(net.minecraft.world.item.ItemStack itemStack, boolean glowing,
            BEWRLVars.Vec3 pos, BEWRLVars.Vec3 rot, BEWRLVars.Vec3 scale) {
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
    public static void addBEWRLChildPart(BEWRL.Model childModel, Shader shader,
            BEWRLVars.Vec3 pos, BEWRLVars.Vec3 rot, BEWRLVars.Vec3 scale) {
        if (currentBEWRL == null || childModel == null || childModel.isEmpty()) return;
        currentBEWRL.addChildPart(childModel, shader,
            pos.x, pos.y, pos.z, rot.x, rot.y, rot.z,
            scale.x, scale.y, scale.z);
    }

    /**
     * Directly add a Part to an existing BEWRL model (not the builder).
     * Smart-detects the part type:
     * - BEWRL.Model → added as child BEWRL part (shader applied if provided)
     * - RenderAPI.Shape → added as shape part (shader ignored, not supported on shapes)
     * - BEWRL.Model.Part → added as-is (already constructed)
     */
    public static void addPartDirect(BEWRL.Model targetModel, Object part,
            BEWRLVars.Vec3 pos, BEWRLVars.Vec3 rot, BEWRLVars.Vec3 scale,
            Shader shader) {
        if (targetModel == null || part == null) return;

        if (part instanceof BEWRL.Model childModel) {
            // Adding a BEWRL as child — shader supported
            if (childModel.isEmpty()) return;
            targetModel.addChildPart(childModel, shader,
                pos.x, pos.y, pos.z, rot.x, rot.y, rot.z,
                scale.x, scale.y, scale.z);
        } else if (part instanceof RenderAPI.Shape shape) {
            // Adding a shape — shader NOT supported on shapes, ignore it
            targetModel.addPart(shape, "", pos.x, pos.y, pos.z, rot.x, rot.y, rot.z,
                scale.x, scale.y, scale.z, -1, "entityCutoutNoCull");
        } else if (part instanceof net.minecraft.world.item.ItemStack itemStack) {
            // Adding an ItemStack as an item part
            targetModel.addItemPart(itemStack, false,
                pos.x, pos.y, pos.z, rot.x, rot.y, rot.z,
                scale.x, scale.y, scale.z);
        } else if (part instanceof BEWRL.Model.Part existingPart) {
            // Adding a pre-constructed Part (e.g. from iterator) — clone it with new transform
            BEWRL.Model.Part copy = existingPart.copy();
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
    public static void removePartFromBEWRL(BEWRL.Model model, BEWRL.Model.Part part) {
        if (model == null || part == null) return;
        model.getParts().remove(part);
    }

    /**
     * Deep-copy all parts from source model into target model (replaces target's parts).
     */
    public static void setBEWRL(BEWRL.Model target, BEWRL.Model source) {
        if (target == null || source == null) return;
        target.getParts().clear();
        target.getParts().addAll(source.copy().getParts());
    }



    // ── Construct: Beam ───────────────────────────────────────────────────────
    //
    // Builds a BEWRL model representing a beam (laser) from a thin emissive
    // box along the Z axis.  Pivot determines which end the origin sits at:
    //   "center_back"  → beam extends forward (+Z) from origin
    //   "center_front" → beam extends backward (-Z) from origin (tip at origin)
    //
    // When one or more BEWRL.Animator objects are passed, their channels are
    // composited onto the beam parameters using getRenderTime() so the model
    // is different every frame (client-side, unsynced — intentional).
    //
    // The beam uses per-vertex colors (part color = -1) so trail gradients
    // work: the far end fades to low alpha while the origin stays bright.

    public static BEWRL.Model buildBeam(float length, int color, String pivot, BEWRL.Animator... animators) {
        float time = getRenderTime();
        float beamLength = Math.max(0.01f, length);
        float beamWidth = 0.0625f;            // 1/16 block — thin laser
        int beamColor = color;
        float beamAlphaMul = 1.0f;
        float beamRoll = 0.0f;
        float trailFade = 0.0f;

        // ── Apply animators (composited — each one stacks) ──
        if (animators != null) {
            for (BEWRL.Animator anim : animators) {
                if (anim == null) continue;

                if (anim.pulseSpeed > 0) {
                    beamLength *= (1.0f + 0.3f * (float) Math.sin(time * anim.pulseSpeed * 6.283f));
                }

                if (anim.flickerIntensity > 0) {
                    // Homelander-style: random chance per frame to dim/flicker
                    float flicker = (float) Math.random();
                    if (flicker < anim.flickerIntensity * 0.35f) {
                        beamAlphaMul *= 0.15f + 0.35f * (float) Math.random();
                    }
                }

                if (anim.colorShiftSpeed > 0) {
                    float hue = (time * anim.colorShiftSpeed) % 1.0f;
                    if (hue < 0) hue += 1.0f;
                    java.awt.Color hc = java.awt.Color.getHSBColor(hue, 1.0f, 1.0f);
                    int origAlpha = (beamColor >> 24) & 0xFF;
                    beamColor = (origAlpha << 24) | (hc.getRed() << 16) | (hc.getGreen() << 8) | hc.getBlue();
                }

                if (anim.widthPulseSpeed > 0) {
                    beamWidth *= (1.0f + 0.5f * (float) Math.sin(time * anim.widthPulseSpeed * 6.283f));
                }

                if (anim.spinSpeed > 0) {
                    beamRoll = (time * anim.spinSpeed * 360.0f) % 360.0f;
                }

                if (anim.trailIntensity > 0) {
                    trailFade = Math.min(1.0f, trailFade + anim.trailIntensity);
                }
            }
        }

        // ── Clamp after animation ──
        beamLength = Math.max(0.01f, beamLength);
        beamWidth = Math.max(0.001f, beamWidth);

        // ── Compute colors ──
        int origA = (beamColor >> 24) & 0xFF;
        if (origA == 0) origA = 255;
        int a = Math.max(0, Math.min(255, (int) (origA * beamAlphaMul)));
        int r = (beamColor >> 16) & 0xFF;
        int g = (beamColor >> 8) & 0xFF;
        int b = beamColor & 0xFF;

        int fullColor = (a << 24) | (r << 16) | (g << 8) | b;
        int tipA = Math.max(0, (int) (a * (1.0f - trailFade)));
        int tipColor = (tipA << 24) | (r << 16) | (g << 8) | b;

        // ── Geometry: thin box along Z ──
        float zStart, zEnd;
        if ("center_front".equals(pivot)) {
            zStart = -beamLength;
            zEnd = 0;
        } else {
            zStart = 0;
            zEnd = beamLength;
        }

        // Origin end gets fullColor, far end gets tipColor (trail gradient)
        int originColor, farColor;
        if ("center_front".equals(pivot)) {
            originColor = fullColor;   // z=0 is the tip/origin
            farColor = tipColor;        // z=-length is the far end
        } else {
            originColor = fullColor;   // z=0 is the back/origin
            farColor = tipColor;        // z=length is the far end
        }

        float hw = beamWidth * 0.5f;

        Shape beamShape = new Shape();
        beamShape.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, false);

        // +Z face
        beamShape.addVertex(-hw, -hw, zEnd, zEnd == 0 ? originColor : farColor);
        beamShape.addVertex( hw, -hw, zEnd, zEnd == 0 ? originColor : farColor);
        beamShape.addVertex( hw,  hw, zEnd, zEnd == 0 ? originColor : farColor);
        beamShape.addVertex(-hw,  hw, zEnd, zEnd == 0 ? originColor : farColor);

        // -Z face
        beamShape.addVertex(-hw, -hw, zStart, zStart == 0 ? originColor : farColor);
        beamShape.addVertex(-hw,  hw, zStart, zStart == 0 ? originColor : farColor);
        beamShape.addVertex( hw,  hw, zStart, zStart == 0 ? originColor : farColor);
        beamShape.addVertex( hw, -hw, zStart, zStart == 0 ? originColor : farColor);

        // +Y face (top) — gradient along Z
        beamShape.addVertex(-hw, hw, zStart, zStart == 0 ? originColor : farColor);
        beamShape.addVertex( hw, hw, zStart, zStart == 0 ? originColor : farColor);
        beamShape.addVertex( hw, hw, zEnd,   zEnd == 0 ? originColor : farColor);
        beamShape.addVertex(-hw, hw, zEnd,   zEnd == 0 ? originColor : farColor);

        // -Y face (bottom) — gradient along Z
        beamShape.addVertex(-hw, -hw, zStart, zStart == 0 ? originColor : farColor);
        beamShape.addVertex(-hw, -hw, zEnd,   zEnd == 0 ? originColor : farColor);
        beamShape.addVertex( hw, -hw, zEnd,   zEnd == 0 ? originColor : farColor);
        beamShape.addVertex( hw, -hw, zStart, zStart == 0 ? originColor : farColor);

        // +X face (right) — gradient along Z
        beamShape.addVertex( hw, -hw, zStart, zStart == 0 ? originColor : farColor);
        beamShape.addVertex( hw, -hw, zEnd,   zEnd == 0 ? originColor : farColor);
        beamShape.addVertex( hw,  hw, zEnd,   zEnd == 0 ? originColor : farColor);
        beamShape.addVertex( hw,  hw, zStart, zStart == 0 ? originColor : farColor);

        // -X face (left) — gradient along Z
        beamShape.addVertex(-hw, -hw, zStart, zStart == 0 ? originColor : farColor);
        beamShape.addVertex(-hw,  hw, zStart, zStart == 0 ? originColor : farColor);
        beamShape.addVertex(-hw,  hw, zEnd,   zEnd == 0 ? originColor : farColor);
        beamShape.addVertex(-hw, -hw, zEnd,   zEnd == 0 ? originColor : farColor);

        beamShape.end();

        BEWRL.Model model = new BEWRL.Model();
        // part color = -1 so per-vertex colors (trail gradient) are respected
        model.addPart(beamShape, null, 0, 0, 0, 0, 0, beamRoll, 1, 1, 1, -1, "entityTranslucentEmissive");
        return model;
    }


    // ── Construct: Trail ───────────────────────────────────────────────────────
    //
    // Builds `amount` independent flash-trail lines that follow the entity's
    // real movement path.  Each trail line has:
    //   • A random body offset (local-space, rotated by entity facing)
    //   • A random lifetime of `lifetime +/- lifetimeVar` ticks — when it
    //     expires it respawns at a new random offset, so the swarm feels alive
    //   • A base length of `length` segments, +/- lengthVar per trail
    //   • Smooth fade-in on spawn and fade-out on death (3 ticks each)
    //   • Alpha fade from bright (now) to transparent (oldest)
    //
    // Point recording happens EVERY RENDER FRAME at the interpolated position
    // (not once per tick at the raw position), so trails stay glued to the entity
    // at any speed and fill up fast enough for `length` to be meaningful.
    //
    // When the entity stops moving, all trails melt away in ~0.15s (3 pts/tick).
    //
    // Parameters:
    //   amount       — number of independent trail lines
    //   color        — ARGB color
    //   spreadX      — body offset range on local X axis (left/right)
    //   spreadY      — body offset range on local Y axis (up/down)
    //   spreadZ      — body offset range on local Z axis (forward/back)
    //   length       — base number of segments per trail
    //   lengthVar    — +/- segment variation per trail
    //   lifetime     — base lifetime in ticks before respawn
    //   lifetimeVar  — +/- lifetime variation in ticks

    private static final class TrailData {
        final java.util.List<float[]>[] histories;
        final float[][] offsets;    // [trail][3] — local-space offset (x, y, z)
        final int[] lifetimes;      // [trail] — remaining ticks
        final int[] spawnAges;      // [trail] — ticks since spawn (for fade-in)
        final int[] maxPoints;      // [trail] — max segments this trail grows to
        float[] lastTickPos;        // raw tick position for movement detection
        int lastTick;
        boolean initialized;
        boolean entityMoving;       // set per-tick, read per-frame
        int deathFadeTicks;          // >0 while entity is dying (death fade-out)
        float[] deathRenderPos;      // last known position for rendering during death fade
        final float spreadX, spreadY, spreadZ;
        final int length, lengthVar;
        final int lifetimeBase, lifetimeVar;
        final int trailCount;
        int lastRenderFrame = -1;     // frame counter when buildTrail was last called
        float passiveFadeAlpha = 1.0f; // alpha multiplier for passive fade-out
        float[] lastRenderPos = null;  // last interpolated position used for rendering
        int lastColor = -1;            // color from the last buildTrail call
        float slowmoAccum = 0.0f;     // accumulates slowmo time scale for per-tick gating

        @SuppressWarnings("unchecked")
        TrailData(int count, float spreadX, float spreadY, float spreadZ,
                   int length, int lengthVar, int lifetimeBase, int lifetimeVar) {
            this.trailCount = count;
            this.spreadX = spreadX;
            this.spreadY = spreadY;
            this.spreadZ = spreadZ;
            this.length = length;
            this.lengthVar = lengthVar;
            this.lifetimeBase = lifetimeBase;
            this.lifetimeVar = lifetimeVar;
            histories = new java.util.List[count];
            offsets = new float[count][3];
            lifetimes = new int[count];
            spawnAges = new int[count];
            maxPoints = new int[count];
            for (int i = 0; i < count; i++) {
                histories[i] = new java.util.ArrayList<>();
                randomizeTrail(i);
            }
        }

        void randomizeTrail(int i) {
            offsets[i][0] = (float)(Math.random() * 2 - 1) * spreadX;   // +/-spreadX
            offsets[i][1] = (float)(Math.random() * 2 - 1) * spreadY;   // +/-spreadY
            offsets[i][2] = -(float)(Math.random()) * spreadZ;   // always behind (negative Z = trailing)
            // lifetime = base +/- variation (clamped to minimum 2)
            int lt = lifetimeBase + (int)(Math.random() * (lifetimeVar * 2 + 1)) - lifetimeVar;
            lifetimes[i] = Math.max(2, lt);
            spawnAges[i] = 0;
            maxPoints[i] = Math.max(3, length + (int)(Math.random() * (lengthVar * 2 + 1)) - lengthVar);
            histories[i].clear();
        }
    }

    private static final int FADE_TICKS_DEATH = 8;  // death fade-out duration (ticks)
    private static final java.util.Map<Long, TrailData> TRAIL_DATA =
        new java.util.concurrent.ConcurrentHashMap<>();

    public static void clearTrailHistory() { TRAIL_DATA.clear(); }
    public static void clearTrailHistory(int channel) {
        TRAIL_DATA.entrySet().removeIf(e -> (e.getKey() & 0xFFFFL) == (channel & 0xFFFFL));
    }

    // Backward-compatible overload (channel 0)
    public static BEWRL.Model buildTrail(net.minecraft.world.entity.Entity entity,
            int amount, int color, float spreadX, float spreadY, float spreadZ,
            int length, int lengthVar, int lifetime, int lifetimeVar,
            BEWRL.Animator... animators) {
        return buildTrail(entity, 0, amount, color, spreadX, spreadY, spreadZ,
                length, lengthVar, lifetime, lifetimeVar, animators);
    }

    // Multi-channel overload — call with different channel IDs to create
    // independent trail layers on the same entity (e.g. channel 0 = outer
    // orange, channel 1 = inner red).  Each channel has its own history,
    // offsets, lifetimes — no interference.
    public static BEWRL.Model buildTrail(net.minecraft.world.entity.Entity entity,
            int channel, int amount, int color, float spreadX, float spreadY, float spreadZ,
            int length, int lengthVar, int lifetime, int lifetimeVar,
            BEWRL.Animator... animators) {

        if (entity == null || amount <= 0) return new BEWRL.Model();

        long key = ((long) entity.getId() << 16) | (channel & 0xFFFFL);
        TrailData data = TRAIL_DATA.get(key);

        if (!entity.isAlive() || entity.isRemoved()) {
            if (data == null) return new BEWRL.Model();
            if (data.deathFadeTicks <= 0) {
                data.deathFadeTicks = FADE_TICKS_DEATH;
                data.deathRenderPos = new float[]{
                    (float) entity.getX(), (float) entity.getY(), (float) entity.getZ()};
            }
            data.deathFadeTicks--;
            if (data.deathFadeTicks <= 0) {
                TRAIL_DATA.remove(key);
                return new BEWRL.Model();
            }
            // Render existing trail with death fade-out alpha
            float deathAlpha = (float) data.deathFadeTicks / FADE_TICKS_DEATH;
            int origA2 = (color >> 24) & 0xFF;
            if (origA2 == 0) origA2 = 255;
            int r2 = (color >> 16) & 0xFF;
            int g2 = (color >> 8) & 0xFF;
            int b2 = color & 0xFF;
            float drx = data.deathRenderPos[0], dry = data.deathRenderPos[1], drz = data.deathRenderPos[2];
            BEWRL.Model dmodel = new BEWRL.Model();
            for (int t = 0; t < amount; t++) {
                java.util.List<float[]> hist = data.histories[t];
                int rc = hist.size() - 1;
                if (rc < 2) continue;
                float[] pts = new float[rc * 3];
                int[] cols = new int[rc];
                for (int i = 0; i < rc; i++) {
                    float[] hp = hist.get(hist.size() - 2 - i);
                    pts[i * 3]     = hp[0] - drx;
                    pts[i * 3 + 1] = hp[1] - dry;
                    pts[i * 3 + 2] = hp[2] - drz;
                    float fade = 1.0f - (float) i / Math.max(1, rc - 1) + 0.08f;
                    if (fade > 1.0f) fade = 1.0f;
                    int segA = Math.max(2, Math.min(255, (int)(origA2 * fade * deathAlpha)));
                    cols[i] = (segA << 24) | (r2 << 16) | (g2 << 8) | b2;
                }
                dmodel.addLinePart(pts, cols);
            }
            return dmodel;
        }

        if (data == null || data.trailCount != amount) {
            data = new TrailData(amount, spreadX, spreadY, spreadZ,
                    Math.max(3, length), Math.max(0, lengthVar),
                    Math.max(2, lifetime), Math.max(0, lifetimeVar));
            TRAIL_DATA.put(key, data);
        }

        final int FADE_TICKS = 3;
        final float MIN_DIST_SQ = 0.0025f;  // 0.05 blocks — dense, smooth trail

        // ── Per-tick logic: lifetime, respawn, movement detection ──
        int currentTick = entity.tickCount;
        // If rendering was paused (sprinting stopped), old trail history causes
        // jumps. Reset after even 2 ticks of no rendering.
        if (data.lastTick >= 0 && currentTick - data.lastTick > 1) {
            data = new TrailData(amount, spreadX, spreadY, spreadZ,
                    Math.max(3, length), Math.max(0, lengthVar),
                    Math.max(2, lifetime), Math.max(0, lifetimeVar));
            TRAIL_DATA.put(key, data);
        }
        if (data.lastTick != currentTick || !data.initialized) {
            data.lastTick = currentTick;

            // ── Slowmo: gate per-tick effect logic by the chunk's time scale ──
            // The player ticks at 20 TPS (perception), but the chunk may be at
            // 1 TPS. We accumulate the slowmo scale and only run lifetime/melt
            // logic when enough "slowmo time" has passed. This makes trails
            // persist 20x longer at 1 TPS, creating the Flash-time illusion.
            float slowmoScale = getEntitySlowmoTimeScale(entity);
            data.slowmoAccum += slowmoScale;
            boolean slowmoTick = data.slowmoAccum >= 1.0f;
            if (slowmoTick) data.slowmoAccum -= 1.0f;

            float tickX = (float) entity.getX();
            float tickY = (float) entity.getY();
            float tickZ = (float) entity.getZ();

            final float MOVE_EPSILON_SQ = 0.0009f;
            if (data.lastTickPos == null) {
                data.entityMoving = true;
            } else {
                float ddx = tickX - data.lastTickPos[0];
                float ddy = tickY - data.lastTickPos[1];
                float ddz = tickZ - data.lastTickPos[2];
                data.entityMoving = (ddx*ddx + ddy*ddy + ddz*ddz) > MOVE_EPSILON_SQ;
            }
            data.lastTickPos = new float[]{tickX, tickY, tickZ};

            if (data.entityMoving) {
                // Lifetime decrement + respawn (gated by slowmo)
                if (slowmoTick || !data.initialized) {
                    for (int t = 0; t < amount; t++) {
                        data.lifetimes[t]--;
                        if (data.lifetimes[t] <= 0) {
                            data.randomizeTrail(t);
                        } else {
                            data.spawnAges[t]++;
                        }
                    }
                }
            } else {
                // ── Entity stopped: melt ALL trails (gated by slowmo) ──
                if (slowmoTick || !data.initialized) {
                    for (int t = 0; t < amount; t++) {
                        java.util.List<float[]> hist = data.histories[t];
                        int removeCount = Math.min(3, hist.size());
                        for (int r = 0; r < removeCount; r++) {
                            hist.remove(0);
                        }
                    }
                }
            }

            data.initialized = true;
        }

        // ── Per-render-frame: interpolated position ──
        // Use entity.getPosition(partialTick) so the trail stays glued to the
        // entity at any speed.  This matches the caller's translation.
        float renderPartialTick = getEntitySlowmoPartialTick(entity);
        net.minecraft.world.phys.Vec3 interpPos = entity.getPosition(renderPartialTick);
        float curX = (float) interpPos.x();
        float curY = (float) interpPos.y();
        float curZ = (float) interpPos.z();

        // Entity facing for local->world rotation
        // Use INTERPOLATED yaw (getViewYRot) so the offset plane matches
        // the entity's visual rotation at render time, not the post-tick
        // rotation.  entity.getLookAngle() uses non-interpolated rotation
        // which leads the visual entity when turning — causing the trail
        // plane to appear shifted forward while running.
        float yawDeg = entity.getViewYRot(renderPartialTick);
        float yawRad = yawDeg * 0.017453292F;
        float fwdX = -(float)Math.sin(yawRad);
        float fwdZ = (float)Math.cos(yawRad);
        float rightX = fwdZ, rightZ = -fwdX;

        // ── Record points at interpolated position (every render frame) ──
        if (data.entityMoving) {
            for (int t = 0; t < amount; t++) {
                java.util.List<float[]> hist = data.histories[t];
                if (data.lifetimes[t] <= 0) continue;  // just respawned, skip this frame

                float[] off = data.offsets[t];

                // Local->world offset (rotate X/Z by entity facing, Y stays)
                float worldOffX = rightX * off[0] + fwdX * off[2];
                float worldOffZ = rightZ * off[0] + fwdZ * off[2];
                float ax = curX + worldOffX;
                float ay = curY + off[1];
                float az = curZ + worldOffZ;

                if (hist.isEmpty()) {
                    hist.add(new float[]{ ax, ay, az });
                } else {
                    float[] last = hist.get(hist.size() - 1);
                    float ddx = ax - last[0], ddy = ay - last[1], ddz = az - last[2];
                    if (ddx*ddx + ddy*ddy + ddz*ddz > MIN_DIST_SQ) {
                        hist.add(new float[]{ ax, ay, az });
                    }
                }

                // Trim to this trail's max length
                while (hist.size() > data.maxPoints[t]) {
                    hist.remove(0);
                }
            }
        }

        // Mark as rendered this frame and reset passive fade
        data.lastRenderFrame = renderFrameCounter;
        data.passiveFadeAlpha = 1.0f;
        data.lastRenderPos = new float[]{curX, curY, curZ};
        data.lastColor = color;

        // ── Render: build polylines with per-trail fade ──
        float time = getRenderTime();
        float alphaMul = 1.0f;
        int beamColor = color;

        if (animators != null) {
            for (BEWRL.Animator anim : animators) {
                if (anim == null) continue;
                if (anim.flickerIntensity > 0) {
                    float flicker = (float) Math.random();
                    if (flicker < anim.flickerIntensity * 0.35f) {
                        alphaMul *= 0.15f + 0.35f * (float) Math.random();
                    }
                }
                if (anim.colorShiftSpeed > 0) {
                    float hue = (time * anim.colorShiftSpeed) % 1.0f;
                    if (hue < 0) hue += 1.0f;
                    java.awt.Color hc = java.awt.Color.getHSBColor(hue, 1.0f, 1.0f);
                    int origAlpha = (beamColor >> 24) & 0xFF;
                    beamColor = (origAlpha << 24) | (hc.getRed() << 16) | (hc.getGreen() << 8) | hc.getBlue();
                }
            }
        }

        int origA = (beamColor >> 24) & 0xFF;
        if (origA == 0) origA = 255;
        int r = (beamColor >> 16) & 0xFF;
        int g = (beamColor >> 8) & 0xFF;
        int b = beamColor & 0xFF;

        BEWRL.Model model = new BEWRL.Model();

        for (int t = 0; t < amount; t++) {
            java.util.List<float[]> hist = data.histories[t];
            // ── Skip the newest point: it's at the entity's current position
            // and makes the trail appear to lead ahead of the model when running.
            // The point stays in history for next frame, we just don't draw it.
            // This pushes the trail head back by one frame's worth of movement. ──
            int renderCount = hist.size() - 1;
            if (renderCount < 2) continue;

            // ── Per-trail spawn/death fade ──
            int age = data.spawnAges[t];
            int remaining = data.lifetimes[t];
            float trailFade = 1.0f;
            if (age < FADE_TICKS) {
                trailFade = (float)(age + 1) / FADE_TICKS;
            }
            if (remaining < FADE_TICKS) {
                float deathFade = (float)(remaining + 1) / FADE_TICKS;
                trailFade = Math.min(trailFade, deathFade);
            }
            if (trailFade < 0) trailFade = 0;
            if (trailFade > 1) trailFade = 1;

            float[] points = new float[renderCount * 3];
            int[] colors = new int[renderCount];

            for (int i = 0; i < renderCount; i++) {
                // Start from second-newest (index size-2), go to oldest (index 0)
                float[] hp = hist.get(hist.size() - 2 - i);
                points[i * 3]     = hp[0] - curX;
                points[i * 3 + 1] = hp[1] - curY;
                points[i * 3 + 2] = hp[2] - curZ;

                float fade = 1.0f - (float) i / Math.max(1, renderCount - 1) + 0.08f;
                if (fade > 1.0f) fade = 1.0f;
                int segA = Math.max(2, Math.min(255, (int) (origA * fade * alphaMul * trailFade)));
                colors[i] = (segA << 24) | (r << 16) | (g << 8) | b;
            }

            model.addLinePart(points, colors);
        }

        return model;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Flicker — lightning arcs jumping between real body parts of the entity
    // ═══════════════════════════════════════════════════════════════════════════
    //
    // Instead of faking positions with a cylinder/hitbox, this extracts the
    // entity's ACTUAL model (EntityModel via LivingEntityRenderer.getModel()),
    // poses it with setupAnim(), then walks the ModelPart tree to compute
    // world-space positions of every body part (head, arms, legs, torso, etc.).
    //
    // Arc nodes are pinned to real model parts. Each node holds a part index
    // + a small local offset (in block space) so multiple arcs can land on
    // different spots of the same limb. When a node's lifetime expires, it
    // jumps to a NEW random part + offset — so the lightning visibly crawls
    // from arm to head to chest to leg, etc.
    //
    // Arc geometry: jittered line between consecutive nodes, re-rolled each
    // frame for a crackling lightning look. Zero displacement at endpoints so
    // arcs always land exactly on the body part.

    // Reflection cache for ModelPart private fields
    private static Field MODELPART_CHILDREN_FIELD;
    private static boolean modelPartReflectionReady = false;
    static {
        try {
            MODELPART_CHILDREN_FIELD = net.minecraft.client.model.geom.ModelPart.class
                    .getDeclaredField("children");
            MODELPART_CHILDREN_FIELD.setAccessible(true);
            modelPartReflectionReady = true;
        } catch (Exception e) {
            modelPartReflectionReady = false;
        }
    }

    /**
     * Compute world-space positions of every visible ModelPart that has geometry,
     * relative to the entity's render position (block space). Poses the model
     * with the entity's current animation state before walking.
     *
     * @return list of {x, y, z} positions in BEWRL model space (blocks,
     *         relative to entity render origin), or null if unavailable
     */
    private static int debugCounter = 0; // throttle debug output (computeModelPartPositions)
    private static int geoDebugCounter = 0; // throttle debug output (buildFlickerGeometry)

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<float[]> computeModelPartPositions(net.minecraft.world.entity.Entity entity) {
        boolean debug = false; // debug output disabled

        if (!(entity instanceof LivingEntity)) {
            if (debug) System.out.println("[FomekFlicker] Not a LivingEntity: " + entity.getClass().getSimpleName());
            return null;
        }
        LivingEntity living = (LivingEntity) entity;

        net.minecraft.client.renderer.entity.EntityRenderDispatcher dispatcher =
                Minecraft.getInstance().getEntityRenderDispatcher();
        net.minecraft.client.renderer.entity.EntityRenderer renderer;
        try {
            renderer = dispatcher.getRenderer(entity);
        } catch (Exception e) {
            if (debug) System.out.println("[FomekFlicker] getRenderer failed: " + e.getMessage());
            return null;
        }
        if (renderer == null) {
            if (debug) System.out.println("[FomekFlicker] renderer is null");
            return null;
        }
        if (!(renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer)) {
            if (debug) System.out.println("[FomekFlicker] Not a LivingEntityRenderer: " + renderer.getClass().getSimpleName());
            return null;
        }
        net.minecraft.client.renderer.entity.LivingEntityRenderer leRenderer =
                (net.minecraft.client.renderer.entity.LivingEntityRenderer) renderer;

        net.minecraft.client.model.EntityModel entityModel = leRenderer.getModel();
        if (entityModel == null) {
            if (debug) System.out.println("[FomekFlicker] model is null");
            return null;
        }

        if (debug) System.out.println("[FomekFlicker] model class: " + entityModel.getClass().getName());

        float partialTick = getEntitySlowmoPartialTick(entity);

        // ── Compute animation parameters ──
        float limbSwing = living.walkAnimation.position(partialTick);
        float limbSwingAmount = living.walkAnimation.speed();
        float ageInTicks = (float) living.tickCount + partialTick;
        float bodyYaw = net.minecraft.util.Mth.lerp(partialTick, living.yBodyRotO, living.yBodyRot);
        if (debug) System.out.println("[FomekFlicker] partialTick=" + partialTick + " bodyYaw=" + bodyYaw + " yBodyRotO=" + living.yBodyRotO + " yBodyRot=" + living.yBodyRot);
        float headYaw = net.minecraft.util.Mth.lerp(partialTick, living.yHeadRotO, living.yHeadRot) - bodyYaw;
        float headPitch = net.minecraft.util.Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        // ── Pose the model ──
        // 26.1: EntityModel no longer exposes attackTime/riding/young or the
        // entity-based setupAnim. Animation is driven by the render state now,
        // so we extract one and pose the model exactly like the renderer does.
        net.minecraft.client.renderer.entity.state.LivingEntityRenderState flickerState = null;
        try {
            Object st = ((net.minecraft.client.renderer.entity.EntityRenderer) leRenderer)
                    .createRenderState(living, partialTick);
            if (st instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState lst) {
                flickerState = lst;
                ((net.minecraft.client.model.EntityModel) entityModel).setupAnim(lst);
            }
        } catch (Exception e) {
            if (debug) System.out.println("[FomekFlicker] setupAnim failed: " + e.getMessage());
            return null;
        }
        if (flickerState == null) return null;

        // ── Get body part positions ──
        // Strategy 1: Try HierarchicalModel.root() via reflection (avoids instanceof issues)
        // Strategy 2: Find ALL ModelPart fields on the model class and use each as a position
        List<float[]> positions = new ArrayList<>();

        PoseStack pose = new PoseStack();
        pose.translate(0.0F, -1.5F, 0.0F);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - bodyYaw));

        // Strategy 1: try root() via reflection
        net.minecraft.client.model.geom.ModelPart root = null;
        try {
            java.lang.reflect.Method rootMethod = entityModel.getClass().getMethod("root");
            Object rootResult = rootMethod.invoke(entityModel);
            if (rootResult instanceof net.minecraft.client.model.geom.ModelPart mp) {
                root = mp;
            }
        } catch (Exception e) {
            if (debug) System.out.println("[FomekFlicker] root() reflection failed: " + e.getMessage());
        }

        debugBodyYaw = bodyYaw;
        debugPartialTick = partialTick;
        if (root != null) {
            if (debug) System.out.println("[FomekFlicker] Got root via reflection, walking tree");
            walkModelPartTree(root, pose, positions);
        }

        // Strategy 2: if root approach failed, find ALL ModelPart fields
        if (positions.size() < 3) {
            positions.clear();
            if (debug) System.out.println("[FomekFlicker] Trying all ModelPart fields directly");
            debugBodyYaw = bodyYaw;
            pose = new PoseStack();
            pose.translate(0.0F, -1.5F, 0.0F);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - bodyYaw));

            // Collect ALL ModelPart fields from the model and all its superclasses
            java.util.Set<java.lang.reflect.Field> modelPartFields = new java.util.LinkedHashSet<>();
            Class<?> clazz = entityModel.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field f : clazz.getDeclaredFields()) {
                    if (f.getType() == net.minecraft.client.model.geom.ModelPart.class) {
                        f.setAccessible(true);
                        modelPartFields.add(f);
                    }
                }
                clazz = clazz.getSuperclass();
            }

            if (debug) System.out.println("[FomekFlicker] Found " + modelPartFields.size() + " ModelPart fields");

            for (Field f : modelPartFields) {
                String fname = f.getName().toLowerCase();
                // Skip head and hat for player/biped models — no arcs on the head
                if (fname.contains("head") || fname.contains("hat") || fname.contains("cloak") || fname.contains("cape")) continue;
                try {
                    net.minecraft.client.model.geom.ModelPart part =
                            (net.minecraft.client.model.geom.ModelPart) f.get(entityModel);
                    if (part != null) {
                        // Walk each part individually (each is a top-level body part)
                        walkModelPartTree(part, pose, positions);
                    }
                } catch (Exception ignored) {}
            }
        }

        // Deduplicate positions that are at the same spot (head/hat, body/jacket, etc.)
        List<float[]> deduped = new ArrayList<>();
        for (float[] p : positions) {
            boolean dup = false;
            for (float[] d : deduped) {
                if (Math.abs(p[0]-d[0]) < 0.01f && Math.abs(p[1]-d[1]) < 0.01f && Math.abs(p[2]-d[2]) < 0.01f) {
                    dup = true; break;
                }
            }
            if (!dup) deduped.add(p);
        }
        positions = deduped;

        // Filter out positions too high (cape, hat, etc. above entity)
        float maxPartY = Math.max(0.5f, entity.getBbHeight() * 0.85f);
        List<float[]> filtered = new ArrayList<>();
        for (float[] p : positions) {
            if (p[1] <= maxPartY) filtered.add(p);
        }
        positions = filtered;

        if (debug) {
            StringBuilder sb = new StringBuilder();
            sb.append("[FomekFlicker] Total positions: ").append(positions.size())
              .append(" (deduped+filtered)\n");
            for (int i = 0; i < positions.size(); i++) {
                float[] p = positions.get(i);
                sb.append("  part ").append(i).append(": (")
                  .append(String.format("%.3f", p[0])).append(", ")
                  .append(String.format("%.3f", p[1])).append(", ")
                  .append(String.format("%.3f", p[2])).append(")\n");
            }
            System.out.println(sb.toString().trim());
        }

        if (positions.size() < 3) {
            if (debug) System.out.println("[FomekFlicker] Too few parts (" + positions.size() + "), using fallback");
            return null;
        }

        return positions;
    }

    private static java.lang.reflect.Field CUBES_FIELD;
    private static boolean cubesFieldReady = false;
    static {
        try {
            CUBES_FIELD = net.minecraft.client.model.geom.ModelPart.class
                    .getDeclaredField("cubes");
            CUBES_FIELD.setAccessible(true);
            cubesFieldReady = true;
        } catch (Exception e) {
            cubesFieldReady = false;
        }
    }

    private static int walkDebugCounter = 0;
    private static float debugBodyYaw = 0;
    private static float debugPartialTick = 0;

    private static void walkModelPartTree(net.minecraft.client.model.geom.ModelPart part,
            PoseStack pose, List<float[]> positions) {
        boolean wDebug = (walkDebugCounter++ % 120) == 0; // every ~2 seconds

        pose.pushPose();
        try {
            part.translateAndRotate(pose);
        } catch (Exception e) {
            pose.popPose();
            return;
        }

        if (part.visible && !part.skipDraw && !part.isEmpty()) {
            // Read raw model part values for debug
            float rawPx = 0, rawPy = 0, rawPz = 0;
            float rawRotX = 0, rawRotY = 0, rawRotZ = 0;
            if (wDebug) {
                try {
                    rawPx = part.x;
                    rawPy = part.y;
                    rawPz = part.z;
                    rawRotX = part.xRot;
                    rawRotY = part.yRot;
                    rawRotZ = part.zRot;
                } catch (Exception ignored) {}
            }

            // Compute the cube center offset in local space.
            float cy = 6.0f / 16.0f;
            float cx = 0, cz = 0;
            if (cubesFieldReady) {
                try {
                    @SuppressWarnings("unchecked")
                    List<Object> cubes = (List<Object>) CUBES_FIELD.get(part);
                    if (cubes != null && !cubes.isEmpty()) {
                        float sx = 0, sy = 0, sz = 0;
                        for (Object cube : cubes) {
                            try {
                                float mnx = cube.getClass().getDeclaredField("minX").getFloat(cube);
                                float mny = cube.getClass().getDeclaredField("minY").getFloat(cube);
                                float mnz = cube.getClass().getDeclaredField("minZ").getFloat(cube);
                                float mxx = mnx, mxy = mny, mxz = mnz;
                                try { mxx = cube.getClass().getDeclaredField("maxX").getFloat(cube); } catch(Exception ignored) {}
                                try { mxy = cube.getClass().getDeclaredField("maxY").getFloat(cube); } catch(Exception ignored) {}
                                try { mxz = cube.getClass().getDeclaredField("maxZ").getFloat(cube); } catch(Exception ignored) {}
                                sx += (mnx + mxx) / 2f;
                                sy += (mny + mxy) / 2f;
                                sz += (mnz + mxz) / 2f;
                            } catch(Exception ignored) {
                                sx += 0; sy += 6; sz += 0;
                            }
                        }
                        int n = cubes.size();
                        cx = (sx / n) / 16.0f;
                        cy = (sy / n) / 16.0f;
                        cz = (sz / n) / 16.0f;
                    }
                } catch (Exception ignored) {}
            }

            // Translate to cube center in the part's local frame, then extract
            pose.pushPose();
            pose.translate(cx, cy, cz);
            Matrix4f mat = pose.last().pose();
            float rawX = mat.m30();
            float rawY = mat.m31();
            float rawZ = mat.m32();

            // Print comprehensive debug for this part
            if (wDebug) {
                // Manual expected position calculation
                // Model pos = (rawPx/16 + cx, rawPy/16 + cy, rawPz/16 + cz) in model blocks
                float mpX = rawPx / 16.0f + cx;
                float mpY = rawPy / 16.0f + cy;
                float mpZ = rawPz / 16.0f + cz;
                // After translate(0, -1.5, 0): (mpX, mpY - 1.5, mpZ)
                float tY = mpY - 1.5f;
                // After rotate(180 - bodyYaw): manual rotation
                // We need the current bodyYaw — get it from the caller context
                // Actually we can compute it from the PoseStack matrix

                float yawRad2 = (float) Math.toRadians(180.0f - debugBodyYaw);
                float cosY2 = (float) Math.cos(yawRad2);
                float sinY2 = (float) Math.sin(yawRad2);
                float expX = mpX * cosY2 + mpZ * sinY2;
                float expZ = -mpX * sinY2 + mpZ * cosY2;
                float expWX = -expX;
                float expWY = -tY;
                float expWZ = -expZ;
                System.out.println("[FomekWalk] Part: rawModel=(" + rawPx + "," + rawPy + "," + rawPz + ")px " +
                    "rot=(" + String.format("%.1f",rawRotX) + "," + String.format("%.1f",rawRotY) + "," + String.format("%.1f",rawRotZ) + ")deg " +
                    "cubeOffset=(" + String.format("%.3f",cx) + "," + String.format("%.3f",cy) + "," + String.format("%.3f",cz) + ") " +
                    "bodyYaw=" + String.format("%.1f",debugBodyYaw));
                System.out.println("[FomekWalk]   modelBlocks=(" + String.format("%.4f",mpX) + "," + String.format("%.4f",mpY) + "," + String.format("%.4f",mpZ) + ") afterTranslateY=" + String.format("%.4f",tY));
                System.out.println("[FomekWalk]   Expected rotate: (" + String.format("%.4f",expX) + "," + String.format("%.4f",tY) + "," + String.format("%.4f",expZ) + ") -> world: (" + String.format("%.4f",expWX) + "," + String.format("%.4f",expWY) + "," + String.format("%.4f",expWZ) + ")");
                System.out.println("[FomekWalk]   Matrix extract: raw=(" + String.format("%.4f",rawX) + "," + String.format("%.4f",rawY) + "," + String.format("%.4f",rawZ) + ")");
                System.out.println("[FomekWalk]   After negate:  out=(" + String.format("%.4f",-rawX) + "," + String.format("%.4f",-rawY) + "," + String.format("%.4f",-rawZ) + ")");
                System.out.println("[FomekWalk]   MATCH: " + (Math.abs(-rawX - expWX) < 0.001 && Math.abs(-rawY - expWY) < 0.001 && Math.abs(-rawZ - expWZ) < 0.001 ? "YES" : "NO !!! MISMATCH !!!"));
                System.out.println("[FomekWalk]   Matrix col3: (" +
                    String.format("%.3f",mat.m30()) + "," + String.format("%.3f",mat.m31()) + "," + String.format("%.3f",mat.m32()) + "," + String.format("%.3f",mat.m33()) + ")");
                // Print full matrix for rotation analysis
                System.out.println("[FomekWalk]   Full matrix:");
                System.out.println("[FomekWalk]     [" + String.format("%.3f",mat.m00()) + " " + String.format("%.3f",mat.m10()) + " " + String.format("%.3f",mat.m20()) + " " + String.format("%.3f",mat.m30()) + "]");
                System.out.println("[FomekWalk]     [" + String.format("%.3f",mat.m01()) + " " + String.format("%.3f",mat.m11()) + " " + String.format("%.3f",mat.m21()) + " " + String.format("%.3f",mat.m31()) + "]");
                System.out.println("[FomekWalk]     [" + String.format("%.3f",mat.m02()) + " " + String.format("%.3f",mat.m12()) + " " + String.format("%.3f",mat.m22()) + " " + String.format("%.3f",mat.m32()) + "]");
                System.out.println("[FomekWalk]     [" + String.format("%.3f",mat.m03()) + " " + String.format("%.3f",mat.m13()) + " " + String.format("%.3f",mat.m23()) + " " + String.format("%.3f",mat.m33()) + "]");
            }

            // Model Y goes DOWN, world Y goes UP — negate Y.
            // After 180 rotation, both X and Z are flipped — negate both.
            positions.add(new float[]{-rawX, -rawY, -rawZ});
            pose.popPose();
        }

        // Recurse into children
        if (modelPartReflectionReady) {
            try {
                Map<String, net.minecraft.client.model.geom.ModelPart> children =
                        (Map<String, net.minecraft.client.model.geom.ModelPart>)
                                MODELPART_CHILDREN_FIELD.get(part);
                if (children != null) {
                    for (net.minecraft.client.model.geom.ModelPart child : children.values()) {
                        walkModelPartTree(child, pose, positions);
                    }
                }
            } catch (Exception e) {
                // Reflection failed — silently continue
            }
        }

        pose.popPose();
    }

    /**
     * Fallback: compute approximate body positions from the entity's bounding box.
     * Positions are rotated by the entity's body yaw so the arcs follow rotation.
     */
    private static List<float[]> computeBoundingBoxPositions(net.minecraft.world.entity.Entity entity) {
        List<float[]> positions = new ArrayList<>();
        float halfWidth = entity.getBbWidth() * 0.5f;
        float height = Math.max(0.1f, entity.getBbHeight());

        float partialTick = getEntitySlowmoPartialTick(entity);
        float bodyYaw = (entity instanceof LivingEntity living)
                ? net.minecraft.util.Mth.lerp(partialTick, living.yBodyRotO, living.yBodyRot)
                : entity.getYRot();
        debugBodyYaw = bodyYaw; // keep in sync with computeModelPartPositions
        float yawRad = (float) Math.toRadians(180.0F - bodyYaw);
        float cos = (float) Math.cos(yawRad);
        float sin = (float) Math.sin(yawRad);

        float[][] rawPositions = {
            {0, height * 0.95f, 0},           // head
            {0, height * 0.7f, 0},           // chest
            {0, height * 0.5f, 0},           // waist
            {-halfWidth, height * 0.75f, 0}, // left shoulder
            {halfWidth, height * 0.75f, 0},  // right shoulder
            {-halfWidth * 0.5f, height * 0.35f, 0}, // left hip
            {halfWidth * 0.5f, height * 0.35f, 0},  // right hip
            {0, height * 0.05f, 0},          // feet
        };

        for (float[] rp : rawPositions) {
            float rx = cos * rp[0] + sin * rp[2];
            float rz = -sin * rp[0] + cos * rp[2];
            positions.add(new float[]{rx, rp[1], rz});
        }
        return positions;
    }

    // ── Flicker data ──

    private static final class FlickerData {
        final int[] partIndices;       // which body part (index into positions list)
        final float[][] localOffsets;  // [node][3] — small offset within part (blocks)
        final int[] lifetimes;
        final int[] spawnAges;
        int lastTick = -1;
        boolean initialized = false;
        int deathFadeTicks;
        float[] deathRenderPos;
        final int lifetimeBase, lifetimeVar;
        final int nodeCount;
        int lastPartCount = 0;
        float slowmoAccum = 0.0f;     // accumulates slowmo time scale for per-tick gating

        FlickerData(int nodeCount, int lifetimeBase, int lifetimeVar) {
            this.nodeCount = nodeCount;
            this.lifetimeBase = lifetimeBase;
            this.lifetimeVar = lifetimeVar;
            partIndices = new int[nodeCount];
            localOffsets = new float[nodeCount][3];
            lifetimes = new int[nodeCount];
            spawnAges = new int[nodeCount];
            for (int i = 0; i < nodeCount; i++) {
                partIndices[i] = -1;
                randomize(i, 6, null);
                lifetimes[i] = Math.max(1, (int) (Math.random() * lifetimes[i]) + 1);
            }
        }

        /**
         * Pick a weighted body part: 80% torso (last part), 20% limbs.
         * Then spread the local offset so nodes aren't clumped on top of each other.
         */
        int pickWeightedPart(int partCount) {
            return pickWeightedPart(partCount, null);
        }

        int pickWeightedPart(int partCount, List<float[]> partPositions) {
            if (partCount <= 1) return 0;
            if (partPositions == null || partPositions.isEmpty()) {
                return (int) (Math.random() * partCount);
            }
            // Classify parts by position: body (center, upper), arms (sides, upper), legs (lower)
            List<Integer> bodyParts = new ArrayList<>();
            List<Integer> limbParts = new ArrayList<>();
            for (int i = 0; i < partCount; i++) {
                float[] p = partPositions.get(i);
                boolean isCenter = Math.abs(p[0]) < 0.18f; // near center horizontally
                boolean isUpper = p[1] > 0.35f; // upper body
                if (isCenter && isUpper) {
                    bodyParts.add(i);
                } else {
                    limbParts.add(i);
                }
            }
            // 50% body, 50% limbs — spreads nodes across ALL parts instead of
            // dumping 80% on a single "torso" part that ends up being the last
            // part in the list (which may not even be the torso).
            if (bodyParts.isEmpty()) bodyParts = limbParts;
            if (limbParts.isEmpty()) limbParts = bodyParts;
            if (Math.random() < 0.5 && !bodyParts.isEmpty()) {
                return bodyParts.get((int) (Math.random() * bodyParts.size()));
            } else if (!limbParts.isEmpty()) {
                return limbParts.get((int) (Math.random() * limbParts.size()));
            }
            return (int) (Math.random() * partCount);
        }

        /**
         * Generate a local offset that is spread away from existing nodes
         * on the same part. Tries multiple times to find a spot that is
         * at least minDist away from all existing nodes on the same part.
         */
        void assignSpreadOffset(int i, int partCount, List<float[]> partPositions) {
            // Bias nodes toward VISIBLE surfaces (front/back of body).
            // X = left-right (sides, where arms meet torso — hidden), keep narrow.
            // Z = front-back (visible from camera), full range.
            // Y = up-down, full range.
            float xRange = 0.04f;   // narrow — avoid sides where limbs connect
            float yRange = 0.12f;   // full vertical spread
            float zRange = 0.12f;   // full front/back spread (visible)

            // If no part positions available (initial init), just use random offset
            if (partPositions == null) {
                localOffsets[i][0] = (float) (Math.random() * 2 - 1) * xRange;
                localOffsets[i][1] = (float) (Math.random() * 2 - 1) * yRange;
                localOffsets[i][2] = (float) (Math.random() * 2 - 1) * zRange;
                return;
            }

            int myPart = partIndices[i];
            float[] myPP = (myPart >= 0 && myPart < partCount)
                    ? partPositions.get(myPart) : new float[]{0, 0, 0};
            float minDist = 0.06f; // minimum distance between nodes on same part (blocks)

            float bestOX = 0, bestOY = 0, bestOZ = 0;
            float bestMinDist = -1;

            for (int attempt = 0; attempt < 8; attempt++) {
                float ox = (float) (Math.random() * 2 - 1) * xRange;
                float oy = (float) (Math.random() * 2 - 1) * yRange;
                float oz = (float) (Math.random() * 2 - 1) * zRange;

                // Check distance to all other nodes on the same part
                float nearestDist = Float.MAX_VALUE;
                for (int j = 0; j < nodeCount; j++) {
                    if (j == i) continue;
                    if (partIndices[j] != myPart) continue;
                    float[] otherPP = (partIndices[j] >= 0 && partIndices[j] < partCount)
                            ? partPositions.get(partIndices[j]) : new float[]{0, 0, 0};
                    float dx = (myPP[0] + ox) - (otherPP[0] + localOffsets[j][0]);
                    float dy = (myPP[1] + oy) - (otherPP[1] + localOffsets[j][1]);
                    float dz = (myPP[2] + oz) - (otherPP[2] + localOffsets[j][2]);
                    float d = dx * dx + dy * dy + dz * dz;
                    if (d < nearestDist) nearestDist = d;
                }

                if (nearestDist >= minDist * minDist) {
                    bestOX = ox; bestOY = oy; bestOZ = oz;
                    break;
                }
                if (nearestDist > bestMinDist) {
                    bestMinDist = nearestDist;
                    bestOX = ox; bestOY = oy; bestOZ = oz;
                }
            }

            localOffsets[i][0] = bestOX;
            localOffsets[i][1] = bestOY;
            localOffsets[i][2] = bestOZ;
        }

        void jumpNearby(int i, int partCount, List<float[]> partPositions) {
            partIndices[i] = pickWeightedPart(partCount, partPositions);
            assignSpreadOffset(i, partCount, partPositions);
            int lt = lifetimeBase + (int) (Math.random() * (lifetimeVar * 2 + 1)) - lifetimeVar;
            lifetimes[i] = Math.max(FLICKER_FADE_TICKS * 2 + 1, lt);
            spawnAges[i] = 0;
        }

        void randomize(int i, int partCount, List<float[]> partPositions) {
            partIndices[i] = pickWeightedPart(partCount, partPositions);
            assignSpreadOffset(i, partCount, partPositions);
            int lt = lifetimeBase + (int) (Math.random() * (lifetimeVar * 2 + 1)) - lifetimeVar;
            lifetimes[i] = Math.max(FLICKER_FADE_TICKS * 2 + 1, lt);
            spawnAges[i] = 0;
        }
    }

    private static final int FLICKER_FADE_TICKS = 3;
    private static final int FLICKER_ARC_SEGMENTS = 6;
    private static final java.util.Map<Long, FlickerData> FLICKER_DATA =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static void clearFlickerHistory() { FLICKER_DATA.clear(); }
    public static void clearFlickerHistory(int channel) {
        FLICKER_DATA.entrySet().removeIf(e -> (e.getKey() & 0xFFFFL) == (channel & 0xFFFFL));
    }

    // Backward-compatible overload (channel 0)
    public static BEWRL.Model buildFlicker(net.minecraft.world.entity.Entity entity,
            int amount, int color, float waviness, float radius,
            int lifetime, int lifetimeVar, BEWRL.Animator... animators) {
        return buildFlicker(entity, 0, amount, color, waviness, radius, lifetime, lifetimeVar, animators);
    }

    // Multi-channel overload
    public static BEWRL.Model buildFlicker(net.minecraft.world.entity.Entity entity,
            int channel, int amount, int color, float waviness, float radius,
            int lifetime, int lifetimeVar, BEWRL.Animator... animators) {

        if (entity == null || amount <= 0) return new BEWRL.Model();

        int nodeCount = amount + 1;
        long key = ((long) entity.getId() << 16) | (channel & 0xFFFFL);
        FlickerData data = FLICKER_DATA.get(key);

        // ── Compute real model part positions this frame ──
        List<float[]> partPositions = computeModelPartPositions(entity);

        if (!entity.isAlive() || entity.isRemoved()) {
            if (data == null) return new BEWRL.Model();
            if (data.deathFadeTicks <= 0) {
                data.deathFadeTicks = FADE_TICKS_DEATH;
                data.deathRenderPos = new float[]{
                    (float) entity.getX(), (float) entity.getY(), (float) entity.getZ()};
            }
            data.deathFadeTicks--;
            if (data.deathFadeTicks <= 0) {
                FLICKER_DATA.remove(key);
                return new BEWRL.Model();
            }
            float deathAlpha = (float) data.deathFadeTicks / FADE_TICKS_DEATH;
            return buildFlickerGeometry(data, partPositions, color, waviness, radius, deathAlpha, animators);
        }

        int partCount = partPositions != null ? partPositions.size() : 0;
        if (partCount == 0) {
            partPositions = computeBoundingBoxPositions(entity);
            partCount = partPositions != null ? partPositions.size() : 0;
        }
        if (partCount == 0) return new BEWRL.Model();

        if (data == null || data.nodeCount != nodeCount) {
            data = new FlickerData(nodeCount,
                    Math.max(FLICKER_FADE_TICKS * 2 + 1, lifetime), Math.max(0, lifetimeVar));
            FLICKER_DATA.put(key, data);
        }

        // Re-map part indices if part count changed
        if (data.lastPartCount != partCount) {
            for (int i = 0; i < nodeCount; i++) {
                if (data.partIndices[i] < 0 || data.partIndices[i] >= partCount) {
                    data.randomize(i, partCount, partPositions);
                }
            }
            data.lastPartCount = partCount;
        }

        // ── Per-tick: lifetime countdown + jump to nearby body part when expired ──
        int currentTick = entity.tickCount;
        // If rendering was paused, stale data causes nodes to jump from old
        // positions. Reset after even 2 ticks of no rendering.
        if (data.lastTick >= 0 && currentTick - data.lastTick > 1) {
            FLICKER_DATA.remove(key);
            data = new FlickerData(nodeCount,
                    Math.max(FLICKER_FADE_TICKS * 2 + 1, lifetime), Math.max(0, lifetimeVar));
            data.lastPartCount = partCount;
            FLICKER_DATA.put(key, data);
        }
        if (data.lastTick != currentTick || !data.initialized) {
            data.lastTick = currentTick;

            // ── Slowmo: gate flicker lifetime by the chunk's time scale ──
            float slowmoScale = getEntitySlowmoTimeScale(entity);
            data.slowmoAccum += slowmoScale;
            boolean slowmoTick = data.slowmoAccum >= 1.0f;
            if (slowmoTick) data.slowmoAccum -= 1.0f;

            if (slowmoTick || !data.initialized) {
                for (int i = 0; i < nodeCount; i++) {
                    data.lifetimes[i]--;
                    if (data.lifetimes[i] <= 0) {
                        data.jumpNearby(i, partCount, partPositions);
                    } else {
                        data.spawnAges[i]++;
                    }
                }
            }
            data.initialized = true;
        }

        return buildFlickerGeometry(data, partPositions, color, waviness, radius, 1.0f, animators);
    }

    /**
     * Internal: build lightning arcs between body-part-anchored nodes.
     * Nodes are connected to their NEAREST neighbor (not sequentially),
     * so arcs stay short and connected to the body.
     */
    private static BEWRL.Model buildFlickerGeometry(FlickerData data,
            List<float[]> partPositions, int color, float waviness, float radius,
            float deathAlpha, BEWRL.Animator... animators) {

        boolean debug = false; // debug output disabled
        int origA = (color >> 24) & 0xFF;
        if (origA == 0) origA = 255;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        float alphaMul = 1.0f;
        float waveAmt = Math.max(0f, waviness);

        if (animators != null) {
            for (BEWRL.Animator anim : animators) {
                if (anim == null) continue;
                if (anim.flickerIntensity > 0) {
                    float flick = (float) Math.random();
                    if (flick < anim.flickerIntensity * 0.35f) {
                        alphaMul *= 0.15f + 0.35f * (float) Math.random();
                    }
                }
                if (anim.colorShiftSpeed > 0) {
                    float hue = (getRenderTime() * anim.colorShiftSpeed) % 1.0f;
                    if (hue < 0) hue += 1.0f;
                    java.awt.Color hc = java.awt.Color.getHSBColor(hue, 1.0f, 1.0f);
                    r = hc.getRed(); g = hc.getGreen(); b = hc.getBlue();
                }
                if (anim.pulseSpeed > 0) {
                    waveAmt *= (1.0f + 0.3f * (float) Math.sin(getRenderTime() * anim.pulseSpeed * 6.283f));
                }
            }
        }
        waveAmt = Math.max(0f, waveAmt);

        int nodeCount = data.nodeCount;
        if (partPositions == null || partPositions.isEmpty()) return new BEWRL.Model();
        int partCount = partPositions.size();

        // ── Per-node position + fade alpha ──
        float[][] nodePos = new float[nodeCount][3];
        int[] nodeAlpha = new int[nodeCount];
        float radMul = Math.max(0f, Math.min(1f, radius));
        // Rotate local offsets by bodyYaw so they stick to the body part
        // instead of staying fixed in world space (which causes nodes to
        // appear to "slide" off the body when rotating).
        float yawRad = (float) Math.toRadians(180.0f - debugBodyYaw);
        float cosY = (float) Math.cos(yawRad);
        float sinY = (float) Math.sin(yawRad);
        for (int i = 0; i < nodeCount; i++) {
            int partIdx = data.partIndices[i];
            if (partIdx < 0 || partIdx >= partCount) partIdx = 0;
            float[] pp = partPositions.get(partIdx);
            // Rotate local offset by bodyYaw (same rotation as part positions)
            float ox = data.localOffsets[i][0] * radMul;
            float oy = data.localOffsets[i][1] * radMul;
            float oz = data.localOffsets[i][2] * radMul;
            float rotX = ox * cosY + oz * sinY;
            float rotZ = -ox * sinY + oz * cosY;
            // Apply same negation as positions: -x, -y, -z
            nodePos[i][0] = pp[0] + (-rotX);
            nodePos[i][1] = pp[1] + (-oy);
            nodePos[i][2] = pp[2] + (-rotZ);

            int age = data.spawnAges[i];
            int remaining = data.lifetimes[i];
            float fade = 1.0f;
            if (age < FLICKER_FADE_TICKS) {
                fade = (float) (age + 1) / FLICKER_FADE_TICKS;
            }
            if (remaining < FLICKER_FADE_TICKS) {
                float deathFade = (float) (remaining + 1) / FLICKER_FADE_TICKS;
                fade = Math.min(fade, deathFade);
            }
            fade = Math.max(0, Math.min(1, fade)) * deathAlpha * alphaMul;
            nodeAlpha[i] = Math.max(0, Math.min(255, (int) (origA * fade)));
        }

        // DEBUG: print node positions with offset details
        if (debug) {
            StringBuilder sb = new StringBuilder("[FomekFlicker] Node positions:");
            for (int i = 0; i < Math.min(4, nodeCount); i++) {
                int pi = data.partIndices[i];
                float[] pp = (pi >= 0 && pi < partCount) ? partPositions.get(pi) : new float[]{0,0,0};
                float ox = data.localOffsets[i][0] * radMul;
                float oy = data.localOffsets[i][1] * radMul;
                float oz = data.localOffsets[i][2] * radMul;
                float rotX = ox * cosY + oz * sinY;
                float rotZ = -ox * sinY + oz * cosY;
                sb.append(String.format("\n  n%d=(%.3f,%.3f,%.3f) p%d a=%d" +
                    " part=(%.3f,%.3f,%.3f) rawOff=(%.3f,%.3f,%.3f)" +
                    " rotOff=(%.3f,%.3f,%.3f)",
                    i, nodePos[i][0], nodePos[i][1], nodePos[i][2],
                    pi, nodeAlpha[i],
                    pp[0], pp[1], pp[2],
                    ox, oy, oz,
                    -rotX, -oy, -rotZ));
            }
            System.out.println(sb.toString());
        }

        BEWRL.Model model = new BEWRL.Model();

        // ── Connect each node to its nearest neighbor (not sequential) ──
        // Build a minimum spanning tree-like chain: start from node 0, connect
        // to nearest unconnected node, repeat. This keeps arcs short.
        boolean[] connected = new boolean[nodeCount];
        connected[0] = true;
        int connectedCount = 1;

        while (connectedCount < nodeCount) {
            // Find the pair (connected, unconnected) with shortest distance
            float minDist = Float.MAX_VALUE;
            int bestA = -1, bestB = -1;
            for (int a = 0; a < nodeCount; a++) {
                if (!connected[a]) continue;
                for (int nb = 0; nb < nodeCount; nb++) {
                    if (connected[nb]) continue;
                    float dx = nodePos[a][0] - nodePos[nb][0];
                    float dy = nodePos[a][1] - nodePos[nb][1];
                    float dz = nodePos[a][2] - nodePos[nb][2];
                    float d = dx*dx + dy*dy + dz*dz;
                    if (d < minDist) {
                        minDist = d;
                        bestA = a;
                        bestB = nb;
                    }
                }
            }
            if (bestA < 0 || bestB < 0) break;

            // Build arc between bestA and bestB
            buildArc(model, nodePos[bestA], nodePos[bestB],
                     nodeAlpha[bestA], nodeAlpha[bestB],
                     r, g, b, origA, waveAmt);
            connected[bestB] = true;
            connectedCount++;
        }

        if (debug) {
            System.out.println("[FomekFlicker] Geo: nodeCount=" + nodeCount + " partCount=" + partCount + " modelParts=" + model.getPartCount() + " arcs=" + (model.getPartCount()));
            // Print entity world position for rendering verification
            if (data.deathRenderPos != null) {
                System.out.println("[FomekFlicker] Entity (death) pos: (" + data.deathRenderPos[0] + ", " + data.deathRenderPos[1] + ", " + data.deathRenderPos[2] + ")");
            }
        }

        return model;
    }

    /**
     * Build a single jittered lightning arc between two points.
     */
    private static void buildArc(BEWRL.Model model,
            float[] a, float[] b,
            int aAlpha, int bAlpha,
            int r, int g, int bl, int origA, float waveAmt) {

        if (aAlpha <= 1 && bAlpha <= 1) return;

        float ax = a[0], ay = a[1], az = a[2];
        float bx = b[0], by = b[1], bz = b[2];

        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.001f) return; // skip zero-length arcs

        float dirX = dx / len, dirY = dy / len, dirZ = dz / len;

        // Two perpendicular vectors for jittering
        float upX = 0, upY = 1, upZ = 0;
        if (Math.abs(dirY) > 0.9f) { upX = 1; upY = 0; upZ = 0; }
        float p1x = dirY * upZ - dirZ * upY;
        float p1y = dirZ * upX - dirX * upZ;
        float p1z = dirX * upY - dirY * upX;
        float p1len = (float) Math.sqrt(p1x * p1x + p1y * p1y + p1z * p1z);
        if (p1len > 0.0001f) { p1x /= p1len; p1y /= p1len; p1z /= p1len; }
        float p2x = dirY * p1z - dirZ * p1y;
        float p2y = dirZ * p1x - dirX * p1z;
        float p2z = dirX * p1y - dirY * p1x;

        // Scale jitter by arc length so short arcs have small jitter
        float jitterScale = Math.min(len * 0.3f, waveAmt);

        int segs = FLICKER_ARC_SEGMENTS;
        float[] pts = new float[(segs + 1) * 3];
        int[] cols = new int[segs + 1];
        for (int s = 0; s <= segs; s++) {
            float t = (float) s / segs;
            float bend = (float) Math.sin(t * Math.PI);
            float j1 = jitterScale * bend * (float) (Math.random() * 2 - 1);
            float j2 = jitterScale * bend * (float) (Math.random() * 2 - 1);

            pts[s * 3]     = ax + dx * t + p1x * j1 + p2x * j2;
            pts[s * 3 + 1] = ay + dy * t + p1y * j1 + p2y * j2;
            pts[s * 3 + 2] = az + dz * t + p1z * j1 + p2z * j2;

            int alpha = Math.max(2, (int) (aAlpha + (bAlpha - aAlpha) * t));
            cols[s] = (alpha << 24) | (r << 16) | (g << 8) | bl;
        }
        model.addLinePart(pts, cols);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Model Trail — entity ghost/afterimage copies with pose snapshots
    // ═══════════════════════════════════════════════════════════════════════════
    //
    // Records position AND animation state snapshots of the entity each tick
    // (only when moving). Each ghost copy is rendered with the EXACT pose the
    // entity had at that point in time — limb swing, head rotation, body
    // rotation, attack animation, hurt state — all restored from the snapshot.
    //
    // No entity texture is used — the model is rendered with a white texture
    // and tinted via shader color. Works with ANY entity because it uses the
    // entity's own EntityRenderer (handles model geometry, layers, animations
    // including mixin-based overrides).

    private static final net.minecraft.resources.Identifier MODEL_TRAIL_WHITE_TEX =
            net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "textures/misc/white.png");

    // No-op VertexConsumer that silently discards all vertex data.
    // Used to suppress armor layers, held items, capes, etc. in ghost copies.
    private static final VertexConsumer NO_OP_VC = new VertexConsumer() {
        @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setColor(int argb) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setOverlay(int u) { return this; }
        @Override public VertexConsumer setLight(int u) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        @Override public VertexConsumer setLineWidth(float lineWidth) { return this; }
    };

    // Reflection fields for WalkAnimationState (private fields, need reflection to save/restore)
    private static Field WALK_POS_FIELD;
    private static Field WALK_SPEED_FIELD;
    private static Field WALK_SPEED_OLD_FIELD;
    private static boolean walkReflectionReady = false;
    static {
        try {
            WALK_POS_FIELD = net.minecraft.world.entity.WalkAnimationState.class.getDeclaredField("position");
            WALK_POS_FIELD.setAccessible(true);
            WALK_SPEED_FIELD = net.minecraft.world.entity.WalkAnimationState.class.getDeclaredField("speed");
            WALK_SPEED_FIELD.setAccessible(true);
            WALK_SPEED_OLD_FIELD = net.minecraft.world.entity.WalkAnimationState.class.getDeclaredField("speedOld");
            WALK_SPEED_OLD_FIELD.setAccessible(true);
            walkReflectionReady = true;
        } catch (Exception e) {
            walkReflectionReady = false; // ghosts will use current animation as fallback
        }
    }

    // Snapshot layout: 18 floats
    // [0]=x  [1]=y  [2]=z  [3]=yaw(yRot)
    // [4]=yBodyRot  [5]=yBodyRotO
    // [6]=yHeadRot  [7]=yHeadRotO
    // [8]=xRot  [9]=xRotO
    // [10]=attackAnim  [11]=unused (attackAnimO doesn't exist in 1.21.1)
    // [12]=hurtTime  [13]=hurtDuration
    // [14]=tickCount
    // [15]=walkPos  [16]=walkSpeed  [17]=walkSpeedOld
    private static final int SNAP_SIZE = 18;

    private static final class ModelTrailData {
        final java.util.LinkedList<float[]> snapshots = new java.util.LinkedList<>();
        final int lifetime;
        int lastTick = -1;
        float lastX = Float.NaN;
        float lastY = Float.NaN;
        float lastZ = Float.NaN;
        int deathFadeTicks = 0;
        int lastRenderFrame = -1;      // frame counter when renderModelTrail was last called
        float passiveFadeAlpha = 1.0f;  // alpha multiplier for passive fade-out
        int lastColor = -1;             // color from the last renderModelTrail call
        int lastFadesAmount = 100;       // fadesAmount from the last call
        boolean lastTextured = true;      // textured flag from the last call
        float slowmoAccum = 0.0f;     // accumulates slowmo time scale for per-tick gating
        boolean initialized = false;

        ModelTrailData(int lifetime) {
            this.lifetime = Math.max(2, lifetime);
        }
    }

    private static final java.util.Map<Long, ModelTrailData> MODEL_TRAIL_DATA = new java.util.HashMap<>();

    public static void clearModelTrailHistory() { MODEL_TRAIL_DATA.clear(); }
    public static void clearModelTrailHistory(int channel) {
        MODEL_TRAIL_DATA.entrySet().removeIf(e -> (e.getKey() & 0xFFFFL) == (channel & 0xFFFFL));
    }

    /**
     * Capture a full animation snapshot of the entity.
     */
    private static float[] captureModelSnapshot(net.minecraft.world.entity.Entity entity) {
        float[] snap = new float[SNAP_SIZE];
        snap[0] = (float) entity.getX();
        snap[1] = (float) entity.getY();
        snap[2] = (float) entity.getZ();
        snap[3] = entity.getYRot();
        snap[8] = entity.getXRot();
        snap[9] = entity.xRotO;
        snap[14] = entity.tickCount;

        if (entity instanceof LivingEntity living) {
            snap[4] = living.yBodyRot;
            snap[5] = living.yBodyRotO;
            snap[6] = living.yHeadRot;
            snap[7] = living.yHeadRotO;
            // Don't capture action animations (punch, hurt) — ghosts should
            // only show position/rotation/walk, not combat actions.
            snap[10] = 0f;  // attackAnim — always neutral
            // snap[11] unused — attackAnimO doesn't exist in 1.21.1
            snap[12] = 0;   // hurtTime — always neutral
            snap[13] = 0;   // hurtDuration — always neutral

            if (walkReflectionReady) {
                try {
                    snap[15] = WALK_POS_FIELD.getFloat(living.walkAnimation);
                    snap[16] = WALK_SPEED_FIELD.getFloat(living.walkAnimation);
                    snap[17] = WALK_SPEED_OLD_FIELD.getFloat(living.walkAnimation);
                } catch (Exception ignored) {}
            }
        }
        return snap;
    }

    /**
     * Temporarily swap entity animation state with snapshot values,
     * render the ghost, then restore the original state.
     */
    private static void renderGhostWithPose(net.minecraft.world.entity.Entity entity,
            float[] snap, float partialTick, PoseStack pose,
            net.minecraft.client.renderer.entity.EntityRenderer<?, ?> renderer,
            MultiBufferSource flatBuffer, int packedLight) {

        // Save current entity state
        float savedYRot = entity.getYRot();
        float savedXRot = entity.getXRot();
        float savedXRotO = entity.xRotO;
        int savedTickCount = entity.tickCount;
        boolean isLiving = entity instanceof LivingEntity;
        LivingEntity living = isLiving ? (LivingEntity) entity : null;

        float savedYBodyRot = 0, savedYBodyRotO = 0;
        float savedYHeadRot = 0, savedYHeadRotO = 0;
        float savedAttackAnim = 0;
        int savedHurtTime = 0, savedHurtDuration = 0;
        float savedWalkPos = 0, savedWalkSpeed = 0, savedWalkSpeedOld = 0;
        boolean savedShiftKeyDown = false;

        if (isLiving) {
            savedYBodyRot = living.yBodyRot;
            savedYBodyRotO = living.yBodyRotO;
            savedYHeadRot = living.yHeadRot;
            savedYHeadRotO = living.yHeadRotO;
            savedAttackAnim = living.attackAnim;
            savedHurtTime = living.hurtTime;
            savedHurtDuration = living.hurtDuration;
            savedShiftKeyDown = entity.isShiftKeyDown();
            if (walkReflectionReady) {
                try {
                    savedWalkPos = WALK_POS_FIELD.getFloat(living.walkAnimation);
                    savedWalkSpeed = WALK_SPEED_FIELD.getFloat(living.walkAnimation);
                    savedWalkSpeedOld = WALK_SPEED_OLD_FIELD.getFloat(living.walkAnimation);
                } catch (Exception ignored) {}
            }
        }

        try {
            // Apply snapshot state to entity
            entity.setYRot(snap[3]);
            entity.setXRot(snap[8]);
            entity.xRotO = snap[9];
            entity.tickCount = (int) snap[14];

            if (isLiving) {
                living.yBodyRot = snap[4];
                living.yBodyRotO = snap[5];
                living.yHeadRot = snap[6];
                living.yHeadRotO = snap[7];
                // Neutralize action animations on ghosts — ghosts should show
                // position/rotation/walk only, not punching, hurting, or sneaking.
                living.attackAnim = 0f;
                living.hurtTime = 0;
                living.hurtDuration = 0;
                entity.setShiftKeyDown(false);
                if (walkReflectionReady) {
                    try {
                        WALK_POS_FIELD.setFloat(living.walkAnimation, snap[15]);
                        WALK_SPEED_FIELD.setFloat(living.walkAnimation, snap[16]);
                        WALK_SPEED_OLD_FIELD.setFloat(living.walkAnimation, snap[17]);
                    } catch (Exception ignored) {}
                }
            }

            // Render the entity with snapshot pose
            pose.pushPose();
            pose.translate(snap[0], snap[1], snap[2]);
            try {
                ((net.minecraft.client.renderer.entity.EntityRenderer) renderer)
                        .render(entity, snap[3], partialTick, pose, flatBuffer, packedLight);
            } catch (Exception ignored) {}
            pose.popPose();

        } finally {
            // Restore original entity state
            entity.setYRot(savedYRot);
            entity.setXRot(savedXRot);
            entity.xRotO = savedXRotO;
            entity.tickCount = savedTickCount;

            if (isLiving) {
                living.yBodyRot = savedYBodyRot;
                living.yBodyRotO = savedYBodyRotO;
                living.yHeadRot = savedYHeadRot;
                living.yHeadRotO = savedYHeadRotO;
                living.attackAnim = savedAttackAnim;
                living.hurtTime = savedHurtTime;
                living.hurtDuration = savedHurtDuration;
                entity.setShiftKeyDown(savedShiftKeyDown);
                if (walkReflectionReady) {
                    try {
                        WALK_POS_FIELD.setFloat(living.walkAnimation, savedWalkPos);
                        WALK_SPEED_FIELD.setFloat(living.walkAnimation, savedWalkSpeed);
                        WALK_SPEED_OLD_FIELD.setFloat(living.walkAnimation, savedWalkSpeedOld);
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    /**
     * Render fading entity model ghost copies at historical positions.
     *
     * Must be called inside a RenderEvent.World procedure (world render context).
     *
     * @param entity       the entity to follow (player, mob, modded entity — anything)
     * @param channel      multi-channel ID for independent trail layers (0, 1, 2, ...)
     * @param color        ARGB color tint (alpha controls max opacity)
     * @param lifetime     how many ticks each ghost copy lives (number of snapshots)
     * @param lifetimeVar  random variation added to lifetime (0 = no variation)
     */
    public static void renderModelTrail(net.minecraft.world.entity.Entity entity,
            int channel, int color, int lifetime, int lifetimeVar,
            int fadesAmount, boolean textured) {
        if (entity == null || currentWorldContext == null) return;

        long key = ((long) entity.getId() << 16) | (channel & 0xFFFFL);
        ModelTrailData data = MODEL_TRAIL_DATA.get(key);

        // ── Entity death/removal handling ──
        if (!entity.isAlive() || entity.isRemoved()) {
            if (data == null || data.snapshots.isEmpty()) return;
            if (data.deathFadeTicks <= 0) {
                data.deathFadeTicks = 8;
            }
            data.deathFadeTicks--;
            if (data.deathFadeTicks <= 0) {
                MODEL_TRAIL_DATA.remove(key);
                return;
            }
            float deathAlpha = (float) data.deathFadeTicks / 8.0f;
            renderModelTrailGhosts(entity, data, color, deathAlpha, fadesAmount, textured);
            return;
        }

        // ── Initialize or update data ──
        if (data == null) {
            data = new ModelTrailData(lifetime);
            MODEL_TRAIL_DATA.put(key, data);
        }

        // ── Per-tick: record snapshot only when moving ──
        int currentTick = entity.tickCount;
        // If rendering was paused, old snapshots cause jumps. Reset after 2 ticks.
        if (data.lastTick >= 0 && currentTick - data.lastTick > 1) {
            data = new ModelTrailData(lifetime);
            MODEL_TRAIL_DATA.put(key, data);
        }
        if (data.lastTick != currentTick) {
            data.lastTick = currentTick;

            // ── Slowmo: gate snapshot recording by chunk time scale ──
            // At 1 TPS, snapshots are recorded 20x less often, so the model
            // trail ghosts persist longer (each snapshot covers more time).
            float slowmoScale = getEntitySlowmoTimeScale(entity);
            data.slowmoAccum += slowmoScale;
            boolean slowmoTick = data.slowmoAccum >= 1.0f;
            if (slowmoTick) data.slowmoAccum -= 1.0f;

            float ex = (float) entity.getX();
            float ey = (float) entity.getY();
            float ez = (float) entity.getZ();

            // Movement detection — only record when the entity has actually moved
            if (!Float.isNaN(data.lastX)) {
                float dx = ex - data.lastX;
                float dy = ey - data.lastY;
                float dz = ez - data.lastZ;
                float distSq = dx * dx + dy * dy + dz * dz;

                if (distSq < 0.0001f) {
                    // Not moving — shrink trail by removing oldest snapshot
                    if (slowmoTick || !data.initialized) {
                        if (!data.snapshots.isEmpty()) {
                            data.snapshots.removeLast();
                        }
                    }
                    data.lastX = ex; data.lastY = ey; data.lastZ = ez;
                    data.initialized = true;
                    if (data.snapshots.isEmpty()) return;
                    renderModelTrailGhosts(entity, data, color, 1.0f, fadesAmount, textured);
                    return;
                }
            }

            data.lastX = ex;
            data.lastY = ey;
            data.lastZ = ez;

            // Capture full animation state snapshot (gated by slowmo)
            if (slowmoTick || !data.initialized) {
                float[] snap = captureModelSnapshot(entity);
                data.snapshots.addFirst(snap);

                // Trim to lifetime (max snapshots = lifetime ticks)
                int maxSnaps = data.lifetime + (int)(Math.random() * Math.max(0, lifetimeVar));
                while (data.snapshots.size() > Math.max(2, maxSnaps)) {
                    data.snapshots.removeLast();
                }
            }
            data.initialized = true;
        }

        if (data.snapshots.isEmpty()) return;

        // Mark as rendered this frame and reset passive fade
        data.lastRenderFrame = renderFrameCounter;
        data.passiveFadeAlpha = 1.0f;
        data.lastColor = color;
        data.lastFadesAmount = fadesAmount;
        data.lastTextured = textured;

        // ── Render ghost copies ──
        renderModelTrailGhosts(entity, data, color, 1.0f, fadesAmount, textured);
    }

    /**
     * Internal: render all ghost copies for a trail data set.
     *
     * Each ghost is rendered with the animation state captured at its snapshot
     * time. The entity's fields are temporarily swapped to the snapshot values,
     * the ghost is rendered, then the original values are restored.
     *
     * Uses a flat white texture (no mob texture). The first snapshot (i=0) is
     * skipped so ghosts start behind the entity, not overlapping it.
     */
    private static void renderModelTrailGhosts(net.minecraft.world.entity.Entity entity,
            ModelTrailData data, int color, float deathMul,
            int fadesAmount, boolean textured) {

        PoseStack pose = getActivePoseStack();
        MultiBufferSource buffer = getActiveBufferSource();
        if (pose == null || buffer == null) return;

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        if (a == 0) a = 255;

        float rf = r / 255.0f;
        float gf = g / 255.0f;
        float bf = b / 255.0f;
        float maxAlpha = (a / 255.0f) * deathMul;

        net.minecraft.client.renderer.entity.EntityRenderDispatcher dispatcher =
                Minecraft.getInstance().getEntityRenderDispatcher();
        net.minecraft.client.renderer.entity.EntityRenderer<?, ?> renderer;
        try {
            renderer = dispatcher.getRenderer(entity);
        } catch (Exception e) {
            return;
        }
        if (renderer == null) return;

        // ── Buffer: first getBuffer() call per ghost (main model) gets a
        //    TRANSLUCENT render type so blending (alpha fade) actually works.
        //    Most non-player entity models request entityCutoutNoCull which has
        //    NO_TRANSPARENCY — alpha is ignored and ghosts render fully opaque.
        //    The player model happens to use entityTranslucent (with blending),
        //    which is why the fade worked for the player but not other entities.
        //    Fix: always use entityTranslucent with the entity's own texture
        //    (textured mode) or the flat white texture (non-textured mode).
        //    All subsequent calls (armor, items, capes) get no-op (suppressed).
        //    The flag is reset before each ghost in the render loop below. ──
        final RenderType flatType = RenderTypes.entityTranslucent(MODEL_TRAIL_WHITE_TEX);
        final net.minecraft.resources.Identifier entityTex = ((net.minecraft.client.renderer.entity.EntityRenderer) renderer).getTextureLocation(entity);
        final RenderType texturedType = RenderTypes.entityTranslucent(entityTex);
        final boolean[] mainModelDone = {false};
        MultiBufferSource flatBuffer = renderType -> {
            if (!mainModelDone[0]) {
                mainModelDone[0] = true;
                return textured ? buffer.getBuffer(texturedType) : buffer.getBuffer(flatType);
            }
            return NO_OP_VC; // suppress armor, items, capes, etc.
        };

        // ── Blend setup for ghost copies ──
        // Depth test stays ENABLED so ghosts behind the entity body are properly
        // occluded — they should only be visible where not blocked by the model.
        // entityTranslucent already has blend + depthTest enabled in its render state.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int total = data.snapshots.size();
        // fadesAmount: controls how many distinct opacity levels the trail uses.
        // 255 = smooth continuous fade (255 quantization steps)
        // 10 = chunky stepped fade (10 visible opacity bands)
        // The fade always spans from maxAlpha (newest) to 0 (oldest) across all snapshots.
        int fadeSteps = Math.max(1, fadesAmount);
        float quantStep = maxAlpha / fadeSteps;

        // Skip i=0 (newest snapshot = entity's current position) so ghosts
        // start behind the entity, not overlapping the live model.
        // Ghosts stay at their fixed snapshot positions and just fade away.
        for (int i = 1; i < total; i++) {
            float[] snap = data.snapshots.get(i);
            // Continuous fade from newest to oldest, quantized to fadesAmount levels
            float ageFraction = (float)(i - 1) / Math.max(1, total - 2);
            float rawAlpha = (1.0f - ageFraction) * maxAlpha;
            // Quantize to fadesAmount discrete steps (creates banding when low)
            float alpha = (float)(Math.round(rawAlpha / quantStep) * quantStep);
            if (alpha <= 0.01f) continue;

            // Reset the per-ghost flag so this ghost's main model gets a real buffer
            mainModelDone[0] = false;

            // Render ghost with pose from snapshot time (swaps + restores entity state).
            // partialTick is frozen to 0 — the snapshot's tickCount is fixed, so
            // using a live sweeping partialTick would drift the animation phase
            // forward each frame then snap back at the next tick (bone twitch).
            // Light is computed from the actual world position instead of full-bright
            // so ghosts aren't glowing at night / in dark areas.
            int ghostLight = net.minecraft.client.renderer.LevelRenderer.getLightCoords(
                    entity.level(), net.minecraft.core.BlockPos.containing(snap[0], snap[1], snap[2]));
            renderGhostWithPose(entity, snap, 0.0f, pose, renderer, flatBuffer, ghostLight);

            // Set shader color AFTER render but BEFORE flush.
            // The renderer may set its own shader color during render() (e.g. for
            // hurt flash, invisibility, etc.). By setting it after render() and
            // before endBatch(), we ensure our alpha is the one uploaded to the
            // ColorModulator uniform when the buffer is actually drawn.
            RenderSystem.setShaderColor(rf, gf, bf, alpha);

            // Flush after each ghost so shader color is applied correctly
            if (buffer instanceof MultiBufferSource.BufferSource bs) {
                bs.endBatch();
            }
        }

        // ── Reset state ──
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static Shape buildBoxShape(float length, float width) {
        float hw = width * 0.5f;
        int c = 0xFFFFFFFF;

        Shape shape = new Shape();
        shape.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, false);

        // +Z face
        shape.addVertex(-hw, -hw, length, c);
        shape.addVertex( hw, -hw, length, c);
        shape.addVertex( hw,  hw, length, c);
        shape.addVertex(-hw,  hw, length, c);

        // -Z face
        shape.addVertex(-hw, -hw, 0, c);
        shape.addVertex(-hw,  hw, 0, c);
        shape.addVertex( hw,  hw, 0, c);
        shape.addVertex( hw, -hw, 0, c);

        // +Y face
        shape.addVertex(-hw, hw, 0, c);
        shape.addVertex( hw, hw, 0, c);
        shape.addVertex( hw, hw, length, c);
        shape.addVertex(-hw, hw, length, c);

        // -Y face
        shape.addVertex(-hw, -hw, 0, c);
        shape.addVertex(-hw, -hw, length, c);
        shape.addVertex( hw, -hw, length, c);
        shape.addVertex( hw, -hw, 0, c);

        // +X face
        shape.addVertex( hw, -hw, 0, c);
        shape.addVertex( hw, -hw, length, c);
        shape.addVertex( hw,  hw, length, c);
        shape.addVertex( hw,  hw, 0, c);

        // -X face
        shape.addVertex(-hw, -hw, 0, c);
        shape.addVertex(-hw,  hw, 0, c);
        shape.addVertex(-hw,  hw, length, c);
        shape.addVertex(-hw, -hw, length, c);

        shape.end();
        return shape;
    }



    // ── Model reconstruction (vanilla → BEWRL shapes) ───────────────────────────
    //
    // These helpers extract geometry from vanilla Minecraft models (items, blocks)
    // and convert them to BEWRL Shape parts. Once in Shape format, ALL shader
    // effects (color, transparency, render type, texture, glowing) fully apply
    // through the normal shape render path — no limitations like item parts.
    //
    // Usage:
    //   Model m = RenderAPI.reconstructItemAsBEWRL(itemStack);
    //   RenderAPI.renderBEWRL(m, shader, pos, rot, scale);
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
    public static BEWRL.Model reconstructItemAsBEWRL(ItemStack itemStack) {
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
    public static BEWRL.Model reconstructItemAsBEWRL(ItemStack itemStack, boolean rimOnly) {
        if (itemStack == null || itemStack.isEmpty()) return new BEWRL.Model();

        try {
            ResolvedItemGeometry geo = resolveItemGeometry(itemStack, getCurrentDisplayContext());
            if (geo == null) return new BEWRL.Model();
            BEWRL.Model _result = reconstructBakedModel(geo.quads, true, true, rimOnly);
            _result.sourceItemStack = itemStack;
            return _result;
        } catch (Exception e) {
            return new BEWRL.Model();
        }
    }    /**
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
            ItemTransform transform = resolveDisplayTransform(stack, ctx);
            if (transform != null && transform != ItemTransform.NO_TRANSFORM) {
                pose.pushPose();
                transform.apply(false, pose.last());
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
    public static BEWRL.Model reconstructBlockAsBEWRL(BlockState blockState) {
        if (blockState == null) return new BEWRL.Model();
        try {
            net.minecraft.client.renderer.block.BlockStateModelSet modelSet =
                    Minecraft.getInstance().getModelManager().getBlockStateModelSet();
            net.minecraft.client.renderer.block.dispatch.BlockStateModel model = modelSet.get(blockState);
            java.util.List<BakedQuad> quads = new java.util.ArrayList<>();
            java.util.List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts =
                    new java.util.ArrayList<>();
            model.collectParts(RandomSource.create(), parts);
            for (net.minecraft.client.renderer.block.dispatch.BlockStateModelPart part : parts) {
                quads.addAll(part.getQuads(null));
                for (Direction dir : Direction.values()) {
                    quads.addAll(part.getQuads(dir));
                }
            }
            return reconstructBakedModel(quads);
        } catch (Exception e) {
            return new BEWRL.Model();
        }
    }

    /**
     * Internal: extract quads from a ResolvedModel and convert to BEWRL shape parts.
     * Handles both null-direction (general) and per-face quads.
     */

    // ── 26.1 BakedQuad record adapters (replaces old int[] getVertices API) ──
    private static float qpx(BakedQuad q, int v) { return q.position(v).x(); }
    private static float qpy(BakedQuad q, int v) { return q.position(v).y(); }
    private static float qpz(BakedQuad q, int v) { return q.position(v).z(); }
    private static int qcol(BakedQuad q, int v) { return q.bakedColors().color(v); } // ARGB
    private static float qu(BakedQuad q, int v) {
        return net.minecraft.client.model.geom.builders.UVPair.unpackU(q.packedUV(v));
    }
    private static float qv(BakedQuad q, int v) {
        return net.minecraft.client.model.geom.builders.UVPair.unpackV(q.packedUV(v));
    }
    private static net.minecraft.client.renderer.texture.TextureAtlasSprite qsprite(BakedQuad q) {
        try {
            return q.materialInfo() != null ? q.materialInfo().sprite() : null;
        } catch (Throwable t) {
            return null;
        }
    }
    /** ARGB in → BEWRL/shape color out, with alpha fallback to opaque. */
    private static int toShapeColor(int argb) {
        int a = (argb >> 24) & 0xFF; if (a == 0) a = 255;
        return (a << 24) | (argb & 0x00FFFFFF); // already ARGB
    }

    private static BEWRL.Model reconstructBakedModel(java.util.List<BakedQuad> allQuads) {
        return reconstructBakedModel(allQuads, false);
    }

    private static BEWRL.Model reconstructBakedModel(java.util.List<BakedQuad> allQuads, boolean itemModel) {
        return reconstructBakedModel(allQuads, itemModel, true); // always include back face
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
    private static BEWRL.Model reconstructBakedModel(java.util.List<BakedQuad> allQuads,
                                                           boolean itemModel,
                                                           boolean keepBackFace) {
        return reconstructBakedModel(allQuads, itemModel, keepBackFace, false);
    }

    private static BEWRL.Model reconstructBakedModel(java.util.List<BakedQuad> allQuads,
                                                           boolean itemModel,
                                                           boolean keepBackFace,
                                                           boolean rimOnly) {
        BEWRL.Model bewrl = new BEWRL.Model();

        if (allQuads == null || allQuads.isEmpty()) return bewrl;

        // ── Step 1: resolve the item's own texture path and sprite UV bounds ────
        String texture = "minecraft:textures/atlas/blocks.png";
        float spriteU0 = 0f, spriteV0 = 0f, spriteU1 = 1f, spriteV1 = 1f;
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = null;
        try {
            sprite = qsprite(allQuads.get(0));
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
        RenderAPI.Shape shape = new RenderAPI.Shape();
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
            // Compute normal from first 3 vertices via cross product
            float ax = qpx(quad, 0), ay = qpy(quad, 0), az = qpz(quad, 0);
            float bx = qpx(quad, 1), by = qpy(quad, 1), bz = qpz(quad, 1);
            float cx = qpx(quad, 2), cy = qpy(quad, 2), cz = qpz(quad, 2);
            float ux=bx-ax, uy=by-ay, uz=bz-az;
            float vx=cx-ax, vy=cy-ay, vz=cz-az;
            float nz = ux*vy - uy*vx; // only need Z component to decide face vs edge
            // Also check quad.direction() as a fallback (may be set on some models)
            Direction qd = quad.direction();
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
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
            float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            float fz = qpz(quad, 0);
            for (int v = 0; v < 4; v++) {
                float vx = qpx(quad, v);
                float vy = qpy(quad, v);
                if (vx < minX) minX = vx;
                if (vx > maxX) maxX = vx;
                if (vy < minY) minY = vy;
                if (vy > maxY) maxY = vy;
            }
            faceMinX = minX; faceMaxX = maxX;
            faceMinY = minY; faceMaxY = maxY;
            // Detect front (nz>0) vs back (nz<0) using computed normal
            {
                float ax2 = qpx(quad, 0), ay2 = qpy(quad, 0);
                float bx2 = qpx(quad, 1), by2 = qpy(quad, 1);
                float cx2 = qpx(quad, 2), cy2 = qpy(quad, 2);
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
                float ax2 = qpx(quad, 0), ay2 = qpy(quad, 0), az2 = qpz(quad, 0);
                float bx2 = qpx(quad, 1), by2 = qpy(quad, 1), bz2 = qpz(quad, 1);
                float cx2 = qpx(quad, 2), cy2 = qpy(quad, 2);
                float ux2=bx2-ax2, uy2=by2-ay2;
                float vx2=cx2-ax2, vy2=cy2-ay2;
                float nz2 = ux2*vy2 - uy2*vx2;
                isFront = (nz2 >= 0);
                // Also respect Direction if set
                Direction qd = quad.direction();
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
                faceColor = toShapeColor(qcol(quad, 0)); // vertex 0 color
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
                float nx = 0, ny = 0, nz = 0;
                Direction faceDir = quad.direction();
                if (faceDir != null) {
                    nx = faceDir.step().x();
                    ny = faceDir.step().y();
                    nz = faceDir.step().z();
                } else {
                    try {
                        float eax = qpx(quad, 0), eay = qpy(quad, 0), eaz = qpz(quad, 0);
                        float ebx = qpx(quad, 1), eby = qpy(quad, 1), ebz = qpz(quad, 1);
                        float ecx = qpx(quad, 2), ecy = qpy(quad, 2), ecz = qpz(quad, 2);
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
                    float qx0 = qpx(quad, 0);
                    float qy0 = qpy(quad, 0);
                    float qx1 = qpx(quad, 1);
                    float qy1 = qpy(quad, 1);
                    float qx2 = qpx(quad, 2);
                    float qy2 = qpy(quad, 2);
                    float qx3 = qpx(quad, 3);
                    float qy3 = qpy(quad, 3);

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
                    evx[v] = qpx(quad, v);
                    evy[v] = qpy(quad, v);
                    evz[v] = qpz(quad, v);
                    ecol[v] = toShapeColor(qcol(quad, v));
                    float atlasU = qu(quad, v);
                    float atlasV = qv(quad, v);
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
    public static BEWRL.Model reconstructItemAsBEWRLTransformed(ItemStack itemStack) {
        return reconstructItemAsBEWRL(itemStack, false); // rimOnly = false
    }

    /**
     * Reconstruct a vanilla item as a BEWRL model with the rimOnly flag.
     * Use this when you want only the silhouette (boundary) pixels at reconstruction time.
     */
    public static BEWRL.Model reconstructItemAsBEWRLTransformed(ItemStack itemStack, boolean rimOnly) {
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
            ItemTransform transform = resolveDisplayTransform(itemStack, ctx);
            if (transform != null && transform != ItemTransform.NO_TRANSFORM) {
                pose.pushPose();
                transform.apply(false, pose.last());
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

    // ── BEWRL Render with Vec3 pos/rot + Vec3 scale ──────────────────────────────

    public static void renderBEWRL(BEWRL.Model model, Shader shader,
            BEWRLVars.Vec3 pos, BEWRLVars.Vec3 rot, BEWRLVars.Vec3 scale) {
        renderBEWRL(model, shader, pos.x, pos.y, pos.z, rot.x, rot.y, rot.z, scale.x, scale.y, scale.z);
    }

    public static void renderBEWRLOverlay(BEWRL.Model model, Shader shader,
            BEWRLVars.Vec3 pos, BEWRLVars.Vec3 rot, BEWRLVars.Vec3 scale) {
        renderBEWRL(model, shader, pos.x, pos.y, pos.z, rot.x, rot.y, rot.z, scale.x, scale.y, scale.z);
    }

    public static void renderBEWRLWorld(BEWRL.Model model, Shader shader,
            BEWRLVars.Vec3 pos, BEWRLVars.Vec3 rot, BEWRLVars.Vec3 scale) {
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
            JavaModelRenderer.renderJavaModel(
                name, texture, renderType, x, y, z, yaw, pitch, roll, scale,
                pose, buffer, packedLight, getActivePackedOverlay(), isGui);
            return;
        }

        JavaModelRenderer.renderJavaModel(
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
        net.minecraft.client.model.geom.ModelPart model = JavaModelRenderer.getBakedModel(name);
        if (model == null) return;

        enqueueOverlay(z, () -> {
            GuiGraphicsExtractor gui = currentOverlayContext.getGuiGraphics();
            MultiBufferSource.BufferSource buffer = gui.bufferSource();

            net.minecraft.client.renderer.RenderType rt = JavaModelRenderer.resolveRenderType(renderType, texture);
            com.mojang.blaze3d.vertex.VertexConsumer consumer = buffer.getBuffer(rt);

            float[] center = JavaModelRenderer.calculateModelCenter(model);
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

    public static void setShaderRenderType(Shader shader, String renderType) {
        if (shader != null) shader.setRenderType(renderType);
    }

    public static void setShaderColor(Shader shader, int color) {
        if (shader != null) shader.setColor(color);
    }

    public static void setShaderTransparency(Shader shader, float transparency) {
        if (shader != null) shader.setTransparency(transparency);
    }

    public static void setShaderGlowing(Shader shader, boolean glowing) {
        if (shader != null) shader.setGlowing(glowing);
    }

    public static void setShaderDrawOrder(Shader shader, String drawOrder) {
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

    public static void renderBEWRL(BEWRL.Model model, Shader shader,
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
            final BEWRL.Model _model = model;
            final Shader _shader = shader;
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

        // Force flat (GUI-style) lighting for held items — see comment on
        // applyFlatLightingIfHeld() above. Must restore in finally so we
        // never leave flat lighting active for the rest of the frame.
        boolean flatLit = applyFlatLightingIfHeld();
        try {
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
        } finally {
            restoreLightingIfHeld(flatLit);
        }

        // Pop the item display transform if we applied it
        if (appliedItemTransform) autoPopItemDisplayTransform();
    }


    // ── BEWRL Render with xyz scale (non-uniform) ────────────────────────────────

    public static void renderBEWRL(BEWRL.Model model, Shader shader,
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
            final BEWRL.Model _model = model;
            final Shader _shader = shader;
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

        // Force flat (GUI-style) lighting for held items — see comment on
        // applyFlatLightingIfHeld() above. Must restore in finally so we
        // never leave flat lighting active for the rest of the frame.
        boolean flatLit = applyFlatLightingIfHeld();
        try {
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
                    // Disable depth test so blended content (flickers, glows) renders on
                    // top of the entity body instead of being depth-culled by it.
                    RenderSystem.disableDepthTest();
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
                    RenderSystem.enableDepthTest();
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
                    RenderSystem.disableDepthTest();
                    ByteBufferBuilder builder = new ByteBufferBuilder(786432);
                    MultiBufferSource.BufferSource dedicatedBuffer = MultiBufferSource.immediate(builder);
                    try {
                        model.render(activePose, dedicatedBuffer,
                            0, 0, 0, 0, 0, 0, 1,
                            packedLight, getActivePackedOverlay());
                        flushBufferWithBlend(dedicatedBuffer, currentBlendMode);
                    } finally {
                        builder.close();
                    RenderSystem.enableDepthTest();
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
        } finally {
            restoreLightingIfHeld(flatLit);
        }

        activePose.popPose();

        // Pop the item display transform if we applied it
        if (appliedItemTransform) autoPopItemDisplayTransform();
    }

    // ── Render registered model with shader ─────────────────────────────────────

    public static void renderRegisteredModel(String modelId, Shader shader,
            float x, float y, float z,
            float yaw, float pitch, float roll,
            float scale) {
        BEWRL.Model model = BEWRL.Registry.INSTANCE.getModel(modelId);
        if (model != null && !model.isEmpty()) {
            renderBEWRL(model, shader, x, y, z, yaw, pitch, roll, scale);
        }
    }

    public static void renderRegisteredModel(String modelId, Animation.Controller controller, Shader shader,
            float x, float y, float z,
            float yaw, float pitch, float roll,
            float scale) {
        BEWRL.Model model = BEWRL.Registry.INSTANCE.getModel(modelId);
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

    public static void setShaderFloat(Shader shader, String name, float value) {
        if (shader != null && name != null) shader.setFloat(name, value);
    }

    public static void setShaderVec3(Shader shader, String name, float x, float y, float z) {
        if (shader != null && name != null) shader.setVec3(name, x, y, z);
    }

    public static float getShaderFloat(Shader shader, String name) {
        if (shader == null || name == null) return 0f;
        return shader.getFloat(name);
    }

    public static void setShaderVec2(Shader shader, String name, float x, float y) {
        if (shader != null && name != null) shader.setVec2(name, x, y);
    }

    public static void setShaderVec4(Shader shader, String name, float x, float y, float z, float w) {
        if (shader != null && name != null) shader.setVec4(name, x, y, z, w);
    }

    public static void setShaderInt(Shader shader, String name, int value) {
        if (shader != null && name != null) shader.setInt(name, value);
    }

    public static void setShaderBool(Shader shader, String name, boolean value) {
        if (shader != null && name != null) shader.setBool(name, value);
    }

    public static int getShaderInt(Shader shader, String name) {
        if (shader == null || name == null) return 0;
        return shader.getInt(name);
    }

    public static boolean getShaderBool(Shader shader, String name) {
        if (shader == null || name == null) return false;
        return shader.getBool(name);
    }

    public static void setShaderTexture(Shader shader, String texture) {
        if (shader != null) shader.setTexture(texture);
    }

    public static void setShaderGlowStrength(Shader shader, float strength) {
        if (shader != null) shader.setGlowStrength(strength);
    }

    public static void setShaderSwirlXSpeed(Shader shader, float speed) {
        if (shader != null) shader.setSwirlXSpeed(speed);
    }

    public static void setShaderSwirlZSpeed(Shader shader, float speed) {
        if (shader != null) shader.setSwirlZSpeed(speed);
    }

    public static void setShaderSwirlBlendMode(Shader shader, String mode) {
        if (shader != null) shader.setSwirlBlendMode(mode);
    }

    // ── Level 3: Custom GLSL API ────────────────────────────────────────────────

    /**
     * Decodes GLSL code from MCreator's text blocks.
     * Handles two cases:
     * 1. <newline> tokens (from single-line text blocks where newlines are stripped)
     * 2. Literal \n text (two chars: backslash + n) that should be real newlines
     *    (happens when MCreator escapes newlines in Java string literals)
     * Real newlines (from multiline blocks) pass through unchanged.
     */
    private static String decodeGLSL(String code) {
        if (code == null) return null;
        // Replace <newline> tokens with real newlines
        code = code.replace("<newline>", "\n");
        // Replace literal \n (backslash + n, two chars) with real newline
        // This handles MCreator's string escaping where \n becomes literal \n
        code = code.replace("\\n", "\n");
        // Replace literal \r with nothing (Windows line endings cleanup)
        code = code.replace("\\r", "");
        return code;
    }

    public static void setShaderVertexCode(Shader shader, String code) {
        if (shader != null) shader.setVertexShaderSource(decodeGLSL(code));
    }

    public static void setShaderFragmentCode(Shader shader, String code) {
        if (shader != null) shader.setFragmentShaderSource(decodeGLSL(code));
    }

    public static void setShaderProgram(Shader shader, String name) {
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
public static void setFragmentCode(Fragment frag, String code) { if (frag != null) frag.setCode(decodeGLSL(code)); }
public static GLSL createGLSL() { return GLSL.create(); }
public static void addFragmentToGLSL(GLSL glsl, Fragment frag) { if (glsl != null && frag != null) glsl.addFragment(frag); }
public static void setGLSLVertexCode(GLSL glsl, String code) { if (glsl != null) glsl.setVertexCode(decodeGLSL(code)); }
public static void setGLSLFragmentCode(GLSL glsl, String code) { if (glsl != null) glsl.setFragmentCode(decodeGLSL(code)); }
public static void buildGLSL(GLSL glsl, Shader shader) { if (glsl != null && shader != null) glsl.build(shader); }

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
     *   Shader sh = buildScannerShader(0.5f, 0.05f, 0f, 0.8f, 1f, 2f);
     *   RenderAPI.renderShape(sh, ...);
     */
    public static Shader buildScannerShader(
            float speed, float width,
            float r, float g, float b,
            float intensity) {

        Shader shader = new Shader();

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
//  Paste this BEFORE the final closing } of RenderAPI class
// ═══════════════════════════════════════════════════════════════════════════

    /**
     * TEMPLATE: Plasma Storm — domain-warped FBM noise + 3D plasma + shockwave
     * rings + chromatic color cycling + fresnel edge glow + scanline interference
     * + electric crackles + global pulse. Fully self-contained shader.
     *
     * Usage:
     *   Shader sh = buildPlasmaStormShader(1.0f, 2.0f, 3.0f);
     *   RenderAPI.renderBEWRL(model, sh, 0, 0, 0, 45, 0, 0, 0.5f);
     */
    public static Shader buildPlasmaStormShader(
            float speed, float intensity, float scale) {

        Shader shader = new Shader();

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
    public static Shader buildPulseShader(
            float speed, float minGlow, float maxGlow,
            float r, float g, float b, float edgeFalloff) {

        Shader shader = new Shader();
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
    public static Shader buildBlinkingShader(
            float speed, float ringWidth,
            float r, float g, float b, float intensity) {

        Shader shader = new Shader();
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
    public static Shader buildTemporalWaveShader(
            float speed, float numWaves,
            float r, float g, float b, float intensity) {

        Shader shader = new Shader();
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
    public static Shader buildStormShader(
            float speed, float scale,
            float r, float g, float b, float intensity) {

        Shader shader = new Shader();
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
     * Apply a custom GLSL shader program from a Shader.
     * Compiles (or retrieves cached) GL program, activates it via glUseProgram,
     * and uploads all uniforms. Called by Shader when it has custom GLSL sources.
     */
    public static void applyShaderProgram(Shader shader) {
        if (shader == null) return;
        String vertSrc = shader.getVertexShaderSource();
        String fragSrc = shader.getFragmentShaderSource();
        if (vertSrc == null || fragSrc == null || vertSrc.isEmpty() || fragSrc.isEmpty()) return;

        int programId = Shader.Manager.getOrCreateProgram(vertSrc, fragSrc);
        if (programId == 0) return;

        org.lwjgl.opengl.GL20.glUseProgram(programId);

        // Upload ModelView and Projection matrices from RenderSystem
        try {
            Matrix4f modelView = RenderSystem.getModelViewMatrix();
            Matrix4f projection = RenderSystem.getProjectionMatrix();
            Shader.Manager.applyMatrices(programId, modelView, projection);
        } catch (Exception ignored) {}

        // Apply shader-specific uniforms (uTime, uColor, etc.)
        Shader.Manager.applyUniforms(programId, shader);
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
     * @return A configured Shader with portal projection effect
     */
    public static Shader buildPortalProjectionShader(
            String texture, float scrollSpeed, float uvScale,
            float r, float g, float b, float intensity, float transparency) {

        Shader shader = new Shader();
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
     * Returns a BEWRL.Model ready to render.
     */
    public static BEWRL.Model buildOctahedronModel(float radius) {
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
    private static RenderEvent.Overlay overlayQueueContext;

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
        RenderEvent.Overlay savedContext = currentOverlayContext;
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
        // ── 1.21.x approach: use endBatch directly ─────────────────────────────
        //
        // In 1.21.x, the vertex system was overhauled:
        //   - BufferBuilder.RenderedBuffer was renamed to MeshData
        //   - BufferBuilder.end() was replaced by BufferBuilder.buildOrThrow()
        //   - BufferSource stores ByteBufferBuilder (not BufferBuilder)
        //   - BufferUploader.drawWithShader() takes MeshData
        //
        // The old reflection approach (drawBufferWithBlend) called
        // ByteBufferBuilder.end() which CONSUMED the buffer data, then tried
        // to call BufferUploader.drawWithShader(BufferBuilder.RenderedBuffer)
        // which no longer exists → ClassNotFoundException. The data was lost
        // and the fallback endBatch() found an empty buffer → nothing drawn.
        //
        // New approach: simply call endBatch(). The blend mode is already
        // baked into the render type:
        //   - For "swirl": createSwirlRenderType() bakes the blend mode into
        //     the TransparencyStateShard, so setupRenderState() applies it.
        //   - For non-swirl with blend: resolveRenderType() returns
        //     entityTranslucent which has TRANSLUCENT_TRANSPARENCY.
        //     For these, we set the global blend function AFTER endBatch
        //     re-applies it via a RenderSystem override hook.
        //
        // Since the swirl render type (the one used for glow effects) has the
        // correct blend baked in, endBatch() renders correctly with additive
        // blending.

        // Apply the blend function as a safety net — the render type's
        // setupRenderState() may override this, but for render types without
        // a custom transparency shard (e.g. entityTranslucent), our override
        // persists because TRANSLUCENT_TRANSPARENCY also calls
        // RenderSystem.enableBlend() + blendFuncSeparate, which we then
        // override here. However, setupRenderState() runs BEFORE the draw,
        // so we need to set it AFTER setupRenderState but BEFORE draw.
        // The only reliable way is to rely on the baked-in blend (swirl)
        // or accept the render type's default blend (translucent).
        //
        // For the swirl render type specifically: the baked-in blend IS
        // the user's requested blend mode (resolved from currentBlendMode
        // in createSwirlRenderType), so endBatch() applies it correctly.

        buf.endBatch();
    }


    private static RenderEvent.Overlay currentOverlayContext;

    public static void setCurrentOverlayContext(RenderEvent.Overlay event) {
        currentOverlayContext = event;
        if (event != null) overlayQueueContext = event;
    }

    public static void clearCurrentOverlayContext() {
        currentOverlayContext = null;
        // NOTE: Do NOT clear overlayQueueContext here — flushOverlayQueue() still
        // needs it.  It is cleared after flushOverlayQueue() finishes.
    }

    public static RenderEvent.Overlay getCurrentOverlayContext() {
        return currentOverlayContext;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ── World Render Context ──────────────────────────────────────────────────
    // ═════════════════════════════════════════════════════════════════════════

    private static RenderEvent.World currentWorldContext;
    private static int renderFrameCounter = 0;

    public static void setCurrentWorldContext(RenderEvent.World event) {
        currentWorldContext = event;
        renderFrameCounter++;
    }

    public static void clearCurrentWorldContext() {
        currentWorldContext = null;
    }

    /**
     * Passive trail fade-out: melt and fade trails that weren't rendered this
     * frame (e.g. entity stopped sprinting and the procedure stopped calling
     * buildTrail / renderModelTrail).  This ensures trails blend out gracefully
     * instead of vanishing instantly.
     *
     * Must be called from the world render event handler AFTER the event is
     * posted (so lastRenderFrame is up-to-date for actively rendered trails).
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void passiveTrailFadeOut(net.minecraft.world.entity.Entity entity,
            PoseStack pose, MultiBufferSource buffer, float partialTick) {
        if (entity == null) return;
        long entityId = entity.getId();
        boolean didAnything = false;

        // ── Detect entity position jumps (teleport, death/respawn) ──
        // If the entity moved more than 10 blocks in one tick, clear ALL
        // trail and flicker data for this entity to prevent stale offsets
        // from accumulating across respawn/teleport cycles.
        {
            long clearKey = entityId << 16;
            // Check lines trail data for position jumps
            TRAIL_DATA.entrySet().removeIf(e -> {
                if ((e.getKey() >> 16) != entityId) return false;
                TrailData td = e.getValue();
                if (td.lastRenderPos != null) {
                    double dx = entity.getX() - td.lastRenderPos[0];
                    double dy = entity.getY() - td.lastRenderPos[1];
                    double dz = entity.getZ() - td.lastRenderPos[2];
                    if (dx * dx + dy * dy + dz * dz > 100.0) return true; // > 10 blocks
                }
                return false;
            });
            // Check model trail data for position jumps
            MODEL_TRAIL_DATA.entrySet().removeIf(e -> {
                if ((e.getKey() >> 16) != entityId) return false;
                ModelTrailData mtd = e.getValue();
                if (!mtd.snapshots.isEmpty()) {
                    float[] snap = mtd.snapshots.get(0);
                    double dx = entity.getX() - snap[0];
                    double dy = entity.getY() - snap[1];
                    double dz = entity.getZ() - snap[2];
                    if (dx * dx + dy * dy + dz * dz > 100.0) return true;
                }
                return false;
            });
            // Clear flicker data on position jumps too
            FLICKER_DATA.entrySet().removeIf(e -> {
                if ((e.getKey() >> 16) != entityId) return false;
                FlickerData fd = e.getValue();
                if (fd.deathRenderPos != null) {
                    double dx = entity.getX() - fd.deathRenderPos[0];
                    double dy = entity.getY() - fd.deathRenderPos[1];
                    double dz = entity.getZ() - fd.deathRenderPos[2];
                    if (dx * dx + dy * dy + dz * dz > 100.0) return true;
                }
                return false;
            });
        }

        // ── Lines trail passive fade ──
        var trailIter = TRAIL_DATA.entrySet().iterator();
        while (trailIter.hasNext()) {
            var entry = trailIter.next();
            long key = entry.getKey();
            // Only handle trails for this entity
            if ((key >> 16) != entityId) continue;
            TrailData data = entry.getValue();
            // Skip if actively rendered this frame
            if (data.lastRenderFrame == renderFrameCounter) continue;

            didAnything = true;
            int currentTick = entity.tickCount;

            // Melt + fade, gated by slowmo time scale
            if (data.lastTick != currentTick) {
                data.lastTick = currentTick;

                // Scale melt and fade by the chunk's slowmo rate
                float slowmoScale = getEntitySlowmoTimeScale(entity);
                data.slowmoAccum += slowmoScale;
                if (data.slowmoAccum >= 1.0f) {
                    data.slowmoAccum -= 1.0f;
                    for (int t = 0; t < data.trailCount; t++) {
                        java.util.List<float[]> hist = data.histories[t];
                        int removeCount = Math.min(3, hist.size());
                        for (int r = 0; r < removeCount; r++) hist.remove(0);
                    }
                    data.passiveFadeAlpha -= 0.08f;
                }
            }

            if (data.passiveFadeAlpha <= 0) {
                trailIter.remove();
                continue;
            }

            // Check if all trails are empty
            boolean allEmpty = true;
            for (int t = 0; t < data.trailCount; t++) {
                if (!data.histories[t].isEmpty()) { allEmpty = false; break; }
            }
            if (allEmpty) {
                trailIter.remove();
                continue;
            }

            // Build and render the trail model with passive fade alpha
            float[] ref = data.lastRenderPos != null ? data.lastRenderPos
                    : new float[]{(float)entity.getX(), (float)entity.getY(), (float)entity.getZ()};
            float curX = ref[0], curY = ref[1], curZ = ref[2];

            BEWRL.Model model = new BEWRL.Model();
            for (int t = 0; t < data.trailCount; t++) {
                java.util.List<float[]> hist = data.histories[t];
                int renderCount = hist.size() - 1;
                if (renderCount < 2) continue;

                float[] points = new float[renderCount * 3];
                int[] colors = new int[renderCount];
                // Use the color from the last buildTrail call
                int storedColor = data.lastColor != -1 ? data.lastColor
                        : (250 << 24) | (255 << 16) | (196 << 8) | 25;
                int origA = (storedColor >> 24) & 0xFF; if (origA == 0) origA = 255;
                int r = (storedColor >> 16) & 0xFF;
                int g = (storedColor >> 8) & 0xFF;
                int b = storedColor & 0xFF;
                for (int i = 0; i < renderCount; i++) {
                    float[] hp = hist.get(hist.size() - 2 - i);
                    points[i * 3]     = hp[0] - curX;
                    points[i * 3 + 1] = hp[1] - curY;
                    points[i * 3 + 2] = hp[2] - curZ;
                    float fade = 1.0f - (float) i / Math.max(1, renderCount - 1) + 0.08f;
                    if (fade > 1.0f) fade = 1.0f;
                    int segA = Math.max(2, Math.min(255,
                            (int)(origA * fade * data.passiveFadeAlpha)));
                    colors[i] = (segA << 24) | (r << 16) | (g << 8) | b;
                }
                model.addLinePart(points, colors);
            }

            if (!model.isEmpty()) {
                // Render at the last known position (body center height)
                // Use the shader-based renderBEWRL which creates a dedicated
                // buffer and flushes immediately with the blend mode active.
                // The 8-parameter version renders into the shared buffer, but
                // disableBlending() would reset the blend before the buffer
                // flushes — making the trail invisible.
                float renderY = curY + entity.getBbHeight() / 2.0f;
                BlendMode savedBlend = currentBlendMode;
                enableBlending(BlendMode.SCREEN);
                Shader fadeShader = new Shader();
                renderBEWRL(model, fadeShader, curX, renderY, curZ, 0, 0, 0, 1);
                disableBlending();
                if (savedBlend != null) enableBlending(savedBlend);
            }
        }

        // ── Model trail passive fade ──
        var modelIter = MODEL_TRAIL_DATA.entrySet().iterator();
        while (modelIter.hasNext()) {
            var entry = modelIter.next();
            long key = entry.getKey();
            if ((key >> 16) != entityId) continue;
            ModelTrailData data = entry.getValue();
            if (data.lastRenderFrame == renderFrameCounter) continue;
            if (data.snapshots.isEmpty()) { modelIter.remove(); continue; }

            didAnything = true;
            int currentTick = entity.tickCount;

            // Decrease passive fade alpha, gated by slowmo time scale
            if (data.lastTick != currentTick) {
                data.lastTick = currentTick;
                float slowmoScale = getEntitySlowmoTimeScale(entity);
                data.slowmoAccum += slowmoScale;
                if (data.slowmoAccum >= 1.0f) {
                    data.slowmoAccum -= 1.0f;
                    data.passiveFadeAlpha -= 0.12f;
                }
            }

            if (data.passiveFadeAlpha <= 0) {
                modelIter.remove();
                continue;
            }

            // Render ghosts with passive fade alpha using stored params
            int mc = data.lastColor != -1 ? data.lastColor
                    : (100 << 24) | (255 << 16) | (255 << 8) | 255;
            renderModelTrailGhosts(entity, data, mc,
                    data.passiveFadeAlpha, data.lastFadesAmount, data.lastTextured);
        }

        // 26.1: no global blend state to clean up (blend is pipeline state).

        if (didAnything) {
            // Flush any buffered vertices from the passive render
            if (buffer instanceof MultiBufferSource.BufferSource bs) {
                bs.endBatch();
            }
        }
    }

    public static RenderEvent.World getCurrentWorldContext() {
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
            return net.minecraft.client.renderer.LevelRenderer.getLightCoords(
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
            ItemEntityFlags.setCancelSpin(ie, true);
        }
    }

    /** Cancel the shadow of the current dropped item entity (in Item Render context). */
    public static void cancelDroppedItemShadow() {
        if (currentContext == null) return;
        net.minecraft.world.entity.Entity ent = currentContext.getEntity();
        if (ent instanceof net.minecraft.world.entity.item.ItemEntity ie) {
            ItemEntityFlags.setCancelShadow(ie, true);
        }
    }

    // ── Template Shader methods ──────────────────────────────────────────────────

    public static Shader tplSolidColor(float r, float g, float b, float a) {
        Shader shader = new Shader();
        shader.setColor((int)(a * 255) << 24 | (int)(r * 255) << 16 | (int)(g * 255) << 8 | (int)(b * 255));
        shader.setRenderType("entityCutoutNoCull");
        return shader;
    }

    public static Shader tplGlowPulse(float r, float g, float b, float speed) {
        Shader shader = new Shader();
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

    public static Shader tplRainbow(float speed) {
        Shader shader = new Shader();
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

    public static Shader tplDissolve(float threshold, float edgeWidth, float r, float g, float b) {
        Shader shader = new Shader();
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

    public static Shader tplFire(float speed, float intensity) {
        Shader shader = new Shader();
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

    public static Shader tplHologram(float r, float g, float b, float scanSpeed, float lineFreq) {
        Shader shader = new Shader();
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

            // ── Custom Swirl RenderType ───────────────────────────────────────────

            /**
             * Create a custom "swirl" RenderType — our own swirl system.
             *
             * Unlike vanilla energySwirl which has a FIXED additive blend baked into
             * its pipeline, swirl lets you choose any blend mode (Addition, Alpha,
             * Screen, Multiplication, etc.) while keeping the animated UV scrolling
             * that makes the swirl effect work.
             *
             * 26.1 approach: blend lives in the RenderPipeline's ColorTargetState.
             * We derive a cached per-blend-mode pipeline from the vanilla
             * ENERGY_SWIRL pipeline, and pair it with a RenderSetup whose
             * TextureTransform carries the animated UV offsets — exactly the
             * mechanism vanilla RenderTypes.energySwirl uses.
             *
             * The blend mode is picked up from:
             *   1. The explicit blendMode parameter (if non-null and non-DEFAULT)
             *   2. RenderAPI.currentBlendMode (set via enableBlending())
             *   3. Fallback: ADDITION (same as vanilla energySwirl)
             */
            private static final Map<BlendMode, RenderPipeline> SWIRL_PIPELINES = new HashMap<>();

            private static BlendFunction swirlBlendFunction(BlendMode bm) {
                return switch (bm) {
                    case ADDITION -> new BlendFunction(
                        SourceFactor.SRC_ALPHA, DestFactor.ONE,
                        SourceFactor.ONE,      DestFactor.ZERO);
                    case ALPHA -> new BlendFunction(
                        SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA,
                        SourceFactor.ONE,       DestFactor.ONE_MINUS_SRC_ALPHA);
                    case MULTIPLICATION -> new BlendFunction(
                        SourceFactor.DST_COLOR, DestFactor.ONE_MINUS_SRC_ALPHA,
                        SourceFactor.ONE,       DestFactor.ZERO);
                    case SCREEN -> new BlendFunction(
                        SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_COLOR,
                        SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA);
                    case SUBTRACTION -> new BlendFunction(
                        SourceFactor.ZERO, DestFactor.ONE_MINUS_SRC_COLOR,
                        SourceFactor.ONE,  DestFactor.ZERO);
                    case OPAQUE -> new BlendFunction(
                        SourceFactor.ONE, DestFactor.ZERO);
                    default -> BlendFunction.ADDITIVE;
                };
            }

            public static RenderType createSwirlRenderType(
                    Identifier texture, float xOff, float zOff,
                    BlendMode blendMode) {

                // Resolve blend mode: explicit param → global currentBlendMode → ADDITION default
                BlendMode effectiveBlend = blendMode;
                if (effectiveBlend == null || effectiveBlend == BlendMode.DEFAULT) {
                    effectiveBlend = (currentBlendMode != null) ? currentBlendMode : BlendMode.ADDITION;
                }

                RenderPipeline pipeline = SWIRL_PIPELINES.computeIfAbsent(effectiveBlend, bm ->
                    RenderPipelines.ENERGY_SWIRL.toBuilder()
                        .withLocation("fomek_swirl_" + bm.name().toLowerCase(java.util.Locale.ROOT))
                        .withColorTargetState(new ColorTargetState(swirlBlendFunction(bm)))
                        .build());

                return RenderType.create("fomek_swirl",
                    RenderSetup.builder(pipeline)
                        .withTexture("Sampler0", texture)
                        .setTextureTransform(new TextureTransform.OffsetTextureTransform(xOff, zOff))
                        .useLightmap()
                        .useOverlay()
                        .createRenderSetup());
            }

            public static RenderType resolveRenderType(String renderTypeName, Identifier texture) {
                if (renderTypeName == null || renderTypeName.isEmpty()) {
                    renderTypeName = "entityCutoutNoCull";
                }

                // If a custom blend mode is active, use entityTranslucent (which enables
                // blend) so the manual flush in flushBufferWithBlend can override
                // the blend function. entityCutoutNoCull disables blend entirely.
                BlendMode bm = RenderAPI.currentBlendMode;
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
                    case "swirl":
                        return createSwirlRenderType(texture,
                                RenderAPI.getRenderTime() % 1.0f,
                                RenderAPI.getRenderTime() % 1.0f,
                                currentBlendMode);
                    case "energySwirl":
                        return RenderTypes.energySwirl(texture,
                                RenderAPI.getRenderTime() % 1.0f,
                                RenderAPI.getRenderTime() % 1.0f);
                    // energySwirlCustom handled via resolveRenderType(name, tex, xSpeed, zSpeed)
                    case "dragonExplosionAlpha":
                        // 26.1: dragonExplosionAlpha is gone; dragonRays is the
                        // closest successor (same translucent dragon-explosion look).
                        return RenderTypes.dragonRays();
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
                        return RenderTypes.entityGlint();
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
                if ("swirl".equals(renderTypeName)) {
                    float t = RenderAPI.getRenderTime();
                    return createSwirlRenderType(texture,
                            (t * swirlXSpeed) % 1.0f,
                            (t * swirlZSpeed) % 1.0f,
                            currentBlendMode);
                }
                if ("energySwirl".equals(renderTypeName)) {
                    float t = RenderAPI.getRenderTime();
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
                    if (a == 0 && (color == -1 || color == 0xFFFFFFFF)) a = 255;

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
                    if (a == 0 && (color == -1 || color == 0xFFFFFFFF)) a = 255;

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

            public void build(Shader shader) {
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
