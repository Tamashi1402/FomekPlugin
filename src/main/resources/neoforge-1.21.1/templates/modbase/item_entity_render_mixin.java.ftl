package ${package}.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.item.ItemEntity;
import ${package}.api.render.ItemEntityFlags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.entity.ItemEntityRenderer")
public abstract class ItemEntityRenderMixin {

    @Inject(
        method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void fomek$onRenderItemEntity(
            ItemEntity entity, float entityYaw, float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo ci) {

        if (ItemEntityFlags.isCancelRender(entity)) {
            ci.cancel();
            return;
        }

        if (ItemEntityFlags.isCancelSpin(entity)) {
            ItemEntityFlags.setSpinOverrideActive(true);
        }
    }

    @Inject(
        method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("RETURN"),
        require = 0
    )
    private void fomek$onRenderItemEntityReturn(
            ItemEntity entity, float entityYaw, float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo ci) {
        ItemEntityFlags.setSpinOverrideActive(false);
    }
}
