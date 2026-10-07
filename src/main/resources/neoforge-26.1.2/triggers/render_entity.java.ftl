<#include "procedures.java.ftl">
@EventBusSubscriber(Dist.CLIENT) public class ${name}Procedure {
	@SubscribeEvent public static void onEntityRendered(RenderLivingEvent.Pre event) {
		<#assign dependenciesCode>
			<@procedureDependenciesCode dependencies, {
				"x": "event.getEntity().getX()",
				"y": "event.getEntity().getY()",
				"z": "event.getEntity().getZ()",
				"world": "event.getEntity().level()",
				"entity": "event.getEntity()",
				"poseStack": "event.getPoseStack()",
				"entityModel": "(EntityModel) event.getRenderer().getModel()",
				"entityTexture": "event.getRenderer().getTextureLocation(event.getEntity())",
				"entityRenderEvent": "event",
				"event": "event"
			}/>
		</#assign>
		execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
	}

	public static void renderEntity(RenderLivingEvent entityRenderEvent, LivingEntity renderEntity, EntityModel model, VertexConsumer vertexConsumer) {
        PoseStack poseStack = entityRenderEvent.getPoseStack();
        entityRenderEvent.getRenderer().getModel().copyPropertiesTo(model);
        LivingEntity eventEntity_ = entityRenderEvent.getEntity();
        model.young = eventEntity_.isBaby();
        float partialTick = entityRenderEvent.getPartialTick();
        float limbSwing = eventEntity_.walkAnimation.position(partialTick);
        float limbSwingAmount = eventEntity_.walkAnimation.speed(partialTick);
        float ageInTicks = eventEntity_.tickCount + partialTick;
        float interpolatedBodyYaw = Mth.rotLerp(partialTick, eventEntity_.yBodyRotO, eventEntity_.yBodyRot);
        float interpolatedHeadYaw = Mth.rotLerp(partialTick, eventEntity_.yHeadRotO, eventEntity_.yHeadRot);
        float netHeadYaw = interpolatedHeadYaw - interpolatedBodyYaw;
        float headPitch = Mth.lerp(partialTick, eventEntity_.xRotO, eventEntity_.getXRot());
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - interpolatedBodyYaw));
        if (eventEntity_.deathTime > 0) {
            float f = ((float)eventEntity_.deathTime + partialTick - 1f) / 20f * 1.6f;
            f = Mth.sqrt(f);
            if (f > 1f) {
                f = 1f;
            }
            poseStack.mulPose(Axis.ZP.rotationDegrees(f * 90f));
        }
		poseStack.scale(-1, -1, 1);
		poseStack.translate(0.0D, -1.501, 0.0D);
        model.prepareMobModel(renderEntity, limbSwing, limbSwingAmount, partialTick);
        model.setupAnim(renderEntity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        model.renderToBuffer(poseStack, vertexConsumer, entityRenderEvent.getPackedLight(), LivingEntityRenderer.getOverlayCoords(eventEntity_, 0));
        poseStack.popPose();
    }