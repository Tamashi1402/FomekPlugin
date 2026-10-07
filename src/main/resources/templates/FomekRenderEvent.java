package __RENDERAPI_PACKAGE__;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import __RENDERAPI_PACKAGE__.FomekBEWRL.Registry;

/**
 * All Fomek render events and the event handler, merged into one class.
 *
 * Inner classes:
 *   Item              — fired when an item is about to be rendered (cancelable)
 *   Overlay           — fired when the HUD/overlay is rendered
 *   World             — fired after particles in the world render stage
 *    *   Handler           — NeoForge event listener that fires the above events
 */
public class FomekRenderEvent {

    // ═══════════════════════════════════════════════════════════════════════════
    // Item Render Event
    // ═══════════════════════════════════════════════════════════════════════════

    public static class Item extends Event {

        private final ItemStack itemStack;
            private final ItemDisplayContext displayContext;
            private final PoseStack poseStack;
            private final MultiBufferSource bufferSource;
            private final int packedLight;
            private final int packedOverlay;
            private final LivingEntity entity;
            private final Level world;
            private final float x, y, z;
            private final float partialTick;
            private boolean canceled = false;

            public Item(ItemStack itemStack, ItemDisplayContext displayContext,
                    PoseStack poseStack, MultiBufferSource bufferSource,
                    int packedLight, int packedOverlay,
                    LivingEntity entity, Level world,
                    float x, float y, float z,
                    float partialTick) {
                this.itemStack = itemStack;
                this.displayContext = displayContext;
                this.poseStack = poseStack;
                this.bufferSource = bufferSource;
                this.packedLight = packedLight;
                this.packedOverlay = packedOverlay;
                this.entity = entity;
                this.world = world;
                this.x = x; this.y = y; this.z = z;
                this.partialTick = partialTick;
            }

            public ItemStack getItemStack()         { return itemStack; }
            public ItemDisplayContext getDisplayContext() { return displayContext; }
            public String getDisplayContextName() { return displayContext != null ? displayContext.name() : "NONE"; }
            public PoseStack getPoseStack()          { return poseStack; }
            public MultiBufferSource getBufferSource() { return bufferSource; }
            public int getPackedLight()              { return packedLight; }
            public int getPackedOverlay()            { return packedOverlay; }
            public LivingEntity getEntity()          { return entity; }
            public Level getWorld()                   { return world; }
            public float getX()  { return x; }
            public float getY()  { return y; }
            public float getZ()  { return z; }
            public float getPartialTick() { return partialTick; }

            public boolean isCanceled() { return canceled; }
            public void setCanceled(boolean canceled) { this.canceled = canceled; }

    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Overlay Render Event
    // ═══════════════════════════════════════════════════════════════════════════

    public static class Overlay extends Event {

        private final GuiGraphicsExtractor guiGraphics;
            private final Player player;
            private final int mouseX;
            private final int mouseY;
            private final int width;
            private final int height;
            private final float partialTick;

            public Overlay(GuiGraphicsExtractor guiGraphics, Player player,
                    int mouseX, int mouseY, int width, int height, float partialTick) {
                this.guiGraphics  = guiGraphics;
                this.player       = player;
                this.mouseX       = mouseX;
                this.mouseY       = mouseY;
                this.width        = width;
                this.height       = height;
                this.partialTick  = partialTick;
            }

            public GuiGraphicsExtractor getGuiGraphics()  { return guiGraphics; }
            public Player      getPlayer()        { return player; }
            public int         getMouseX()        { return mouseX; }
            public int         getMouseY()        { return mouseY; }
            public int         getWidth()         { return width; }
            public int         getHeight()        { return height; }
            public float       getPartialTick()   { return partialTick; }

    }

    // ═══════════════════════════════════════════════════════════════════════════
    // World Render Event
    // ═══════════════════════════════════════════════════════════════════════════

    public static class World extends Event {

        private final PoseStack         poseStack;
            private final MultiBufferSource bufferSource;
            private final Camera            camera;
            private final Entity            entity;
            private final Level             world;
            private final float             x, y, z;
            private final float             partialTick;   // 0-1 tick fraction (for getPosition, Mth.lerp)
            private final float             renderTime;    // continuous time (gameTime + fraction, for animations)
            private final ResourceKey<Level> dimension;

            public World(PoseStack poseStack, MultiBufferSource bufferSource,
                    Camera camera, Entity entity, Level world,
                    float x, float y, float z, float partialTick, float renderTime,
                    ResourceKey<Level> dimension) {
                this.poseStack    = poseStack;
                this.bufferSource = bufferSource;
                this.camera       = camera;
                this.entity       = entity;
                this.world        = world;
                this.x = x; this.y = y; this.z = z;
                this.partialTick  = partialTick;
                this.renderTime   = renderTime;
                this.dimension    = dimension;
            }

            public PoseStack         getPoseStack()     { return poseStack; }
            public MultiBufferSource getBufferSource()  { return bufferSource; }
            public Camera            getCamera()        { return camera; }
            public Entity            getEntity()        { return entity; }
            public Level             getWorld()         { return world; }
            public float             getX()             { return x; }
            public float             getY()             { return y; }
            public float             getZ()             { return z; }
            public float             getPartialTick()   { return partialTick; }
            public float             getRenderTime()    { return renderTime; }
            public ResourceKey<Level> getDimension()    { return dimension; }

    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Event Handler (NeoForge listener)
    // ═══════════════════════════════════════════════════════════════════════════

    @net.neoforged.fml.common.EventBusSubscriber(value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static class Handler {

        // ── Overlay ──────────────────────────────────────────────────────────────

            @SubscribeEvent
            public static void onRenderGui(RenderGuiEvent.Post event) {
                Minecraft mc = Minecraft.getInstance();
                Player player = mc.player;
                if (player == null) return;

                GuiGraphicsExtractor guiGraphics = event.getGuiGraphics();
                DeltaTracker deltaTracker = event.getPartialTick();
                float partialTick = mc.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(true);

                // Mouse position — scaled to GUI coordinate space
                double mouseX = mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth()  / (double) mc.getWindow().getScreenWidth();
                double mouseY = mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();

                FomekRenderEvent.Overlay fomekEvent = new FomekRenderEvent.Overlay(
                    guiGraphics, player, (int) mouseX, (int) mouseY,
                    mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                    partialTick);
                FomekRenderAPI.setCurrentOverlayContext(fomekEvent);
                NeoForge.EVENT_BUS.post(fomekEvent);
                // Execute depth-sorted overlay render queue (sorts by depth, flushes after each)
                FomekRenderAPI.flushOverlayQueue();
                // Final flush for any remaining buffered geometry
                mc.renderBuffers().bufferSource().endBatch();
                FomekRenderAPI.clearCurrentOverlayContext();
            }

            // ── World ─────────────────────────────────────────────────────────────────

            @SubscribeEvent
            public static void onRenderLevelStage(RenderLevelStageEvent event) {
                if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

                Minecraft mc = Minecraft.getInstance();
                Camera camera = event.getCamera();
                Entity entity = camera.getEntity();
                Level world = mc.level;
                if (world == null) return;

                DeltaTracker deltaTracker = event.getPartialTick();
                float tickFraction = deltaTracker.getGameTimeDeltaPartialTick(true);
                // partialTick = 0-1 tick fraction (for getPosition, Mth.lerp interpolation)
                // renderTime  = continuous time (gameTime + fraction, for animations)
                float partialTick = tickFraction;
                float renderTime = world.getGameTime() + tickFraction;

                // x/y/z = interpolated entity position in ABSOLUTE WORLD coordinates.
                float x = (float)(net.minecraft.util.Mth.lerp(tickFraction, entity.xOld, entity.getX()));
                float y = (float)(net.minecraft.util.Mth.lerp(tickFraction, entity.yOld, entity.getY()));
                float z = (float)(net.minecraft.util.Mth.lerp(tickFraction, entity.zOld, entity.getZ()));

                PoseStack poseStack = event.getPoseStack();
                MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

                // The RenderLevelStageEvent PoseStack has camera ROTATION but NOT
                // camera translation. Subtract camera position so render methods
                // can use raw WORLD coordinates without any camera math.
                // push/pop ensures we don't leak this to other renderers.
                poseStack.pushPose();
                poseStack.translate(-camera.getPosition().x, -camera.getPosition().y, -camera.getPosition().z);

                FomekRenderEvent.World fomekEvent = new FomekRenderEvent.World(
                    poseStack, bufferSource, camera, entity, world,
                    x, y, z, partialTick, renderTime, world.dimension());
                FomekRenderAPI.setCurrentWorldContext(fomekEvent);
                NeoForge.EVENT_BUS.post(fomekEvent);
                bufferSource.endBatch();
                FomekRenderAPI.clearCurrentWorldContext();

                poseStack.popPose();
            }

    }

}
