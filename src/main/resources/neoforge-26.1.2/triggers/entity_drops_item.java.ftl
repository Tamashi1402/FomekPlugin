<#include "procedures.java.ftl">
@Mod.EventBusSubscriber public class ${name}Procedure {
	@SubscribeEvent public static void onEntityItemDrop(LivingDropsEvent event) {
		if (event != null && event.getEntity() != null) {
			<#assign dependenciesCode><#compress>
			<@procedureDependenciesCode dependencies, {
				"x": "event.getEntity().getX()",
				"y": "event.getEntity().getY()",
				"z": "event.getEntity().getZ()",
				"world": "event.getEntity().level()",
				"entity": "event.getEntity()",
        "sourceentity": "event.getSource().getEntity()",
        "immediatesourceentity": "event.getSource().getDirectEntity()",
        "damagesource": "event.getSource()",
				"drops": "event.getDrops().toString()",
			  "lootingLevel": "event.getLootingLevel()",
				"recentlyHit": "event.isRecentlyHit()",
				"event": "event"
				}/>
			</#compress></#assign>
			execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
		}
	}

