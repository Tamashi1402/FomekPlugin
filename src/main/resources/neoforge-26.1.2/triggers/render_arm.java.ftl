<#include "procedures.java.ftl">
@EventBusSubscriber(Dist.CLIENT) public class ${name}Procedure {
	@SubscribeEvent public static void onArmRendered(RenderArmEvent event) {
	    Minecraft mc = Minecraft.getInstance();
	    PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(mc.player);
		<#assign dependenciesCode>
			<@procedureDependenciesCode dependencies, {
				"x": "mc.player.getX()",
				"y": "mc.player.getY()",
				"z": "mc.player.getZ()",
				"world": "mc.player.level()",
				"entity": "mc.player",
				"poseStack": "event.getPoseStack()",
				"entityModel": "(EntityModel) renderer.getModel()",
				"entityTexture": "renderer.getTextureLocation((AbstractClientPlayer) mc.player)",
				"armRenderEvent": "event",
				"event": "event"
			}/>
		</#assign>
		execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
	}