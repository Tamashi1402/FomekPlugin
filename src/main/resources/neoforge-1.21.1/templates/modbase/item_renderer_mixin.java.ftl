package ${package}.mixin;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Mixin;

import ${package}.api.render.RenderAPI;
import ${package}.api.render.RenderEvent;

import net.neoforged.neoforge.common.NeoForge;

import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.Minecraft;

import com.mojang.blaze3d.vertex.PoseStack;

@Mixin(targets = "net.minecraft.client.renderer.entity.ItemRenderer")
public class ItemRendererMixin {

    private static float getPartialTick() {
        Minecraft mc = Minecraft.getInstance();
        float fraction = ((MinecraftAccessorMixin) mc).fomek$getTimer().getGameTimeDeltaPartialTick(false);
        return (mc.level != null ? mc.level.getGameTime() : 0f) + fraction;
    }

    @Inject(
        method = "renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1
    )
    private void fomek$onRenderStatic(
            LivingEntity entity, ItemStack stack,
            ItemDisplayContext displayContext, boolean leftHand,
            PoseStack poseStack, MultiBufferSource bufferSource,
            Level level, int combinedLight, int combinedOverlay, int seed,
            CallbackInfo ci) {

        if (RenderAPI.isBypassMixin()) return;

        float ex = entity != null ? (float) entity.getX() : 0f;
        float ey = entity != null ? (float) entity.getY() : 0f;
        float ez = entity != null ? (float) entity.getZ() : 0f;
        float partialTick = getPartialTick();

        RenderEvent.Item event = new RenderEvent.Item(
            stack, displayContext, poseStack, bufferSource,
            combinedLight, combinedOverlay, entity, level,
            ex, ey, ez, partialTick);

        RenderAPI.setCurrentContext(event);
        RenderAPI.setBypassMixin(true);
        NeoForge.EVENT_BUS.post(event);
        RenderAPI.setBypassMixin(false);
        if (event.isCanceled()) {
            ci.cancel();
        }
        RenderAPI.clearCurrentContext();
    }

    @Inject(
        method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void fomek$onRender(
            ItemStack stack, ItemDisplayContext displayContext, boolean leftHand,
            PoseStack poseStack, MultiBufferSource bufferSource,
            int combinedLight, int combinedOverlay, BakedModel bakedModel,
            CallbackInfo ci) {

        if (RenderAPI.isBypassMixin()) return;

        // Fire for ALL display contexts — not just GUI and GROUND.
        // This mixin is a FALLBACK for when onRenderStatic doesn't fire
        // (e.g. if the renderStatic method descriptor doesn't match in
        // a specific NeoForge version). Without this, held items
        // (FIRST_PERSON, THIRD_PERSON) would never get the render event.
        //
        // This is safe: if onRenderStatic already fired and the procedure
        // called cancelRender(), ci.cancel() stops renderStatic() before
        // it reaches render(), so onRender never fires. No double-event.
        Minecraft mc = Minecraft.getInstance();
        LivingEntity entity;
        Level level = mc.level;
        int light;
        int overlay;
        float partialTick = getPartialTick();

        if (displayContext == ItemDisplayContext.GUI) {
            entity = mc.player;
            light = LightTexture.FULL_BRIGHT;
            overlay = OverlayTexture.NO_OVERLAY;
        } else {
            // For non-GUI contexts (GROUND, FIRST_PERSON, THIRD_PERSON, etc.)
            // use the values passed to render() by renderStatic().
            entity = null;
            light = combinedLight;
            overlay = combinedOverlay;
        }

        RenderEvent.Item event = new RenderEvent.Item(
            stack, displayContext, poseStack, bufferSource,
            light, overlay, entity, level,
            0f, 0f, 0f, partialTick);

        RenderAPI.setCurrentContext(event);
        RenderAPI.setBypassMixin(true);
        NeoForge.EVENT_BUS.post(event);
        RenderAPI.setBypassMixin(false);
        if (event.isCanceled()) {
            ci.cancel();
        }
        RenderAPI.clearCurrentContext();
    }
}
