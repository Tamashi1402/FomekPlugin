<#include "procedures.java.ftl">
@Mod.EventBusSubscriber public class ${name}Procedure {
	@SubscribeEvent public static void playerTeleportsWithPearl(EntityTeleportEvent.EnderPearl event) {
		if (event!=null && event.getPlayer()!=null) {
			<#assign dependenciesCode><#compress>
			<@procedureDependenciesCode dependencies, {
				"x": "event.getPrevX()",
				"y": "event.getPrevY()",
				"z": "event.getPrevZ()",
        "dx": "event.getTargetX()",
				"dy": "event.getTargetY()",
				"dz": "event.getTargetZ()",
				"world": "event.getPlayer().level()",
				"entity": "event.getPlayer()",
				"sourceentity": "event.getPearlEntity()",
				"hitresult": "event.getHitResult().getType().toString().toLowerCase()",
				"hx": "event.getHitResult().getLocation().x",
				"hy": "event.getHitResult().getLocation().y",
				"hz": "event.getHitResult().getLocation().z",
				"event": "event"
				}/>
			</#compress></#assign>
			execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
		}
	}