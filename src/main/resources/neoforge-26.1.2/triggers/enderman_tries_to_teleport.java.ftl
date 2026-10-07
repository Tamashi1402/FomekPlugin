<#include "procedures.java.ftl">
@Mod.EventBusSubscriber public class ${name}Procedure {
	@SubscribeEvent public static void endermanTeleports(EntityTeleportEvent.EnderEntity event) {
		if (event!=null && event.getEntity()!=null) {
			<#assign dependenciesCode><#compress>
			<@procedureDependenciesCode dependencies, {
				"x": "event.getPrevX()",
				"y": "event.getPrevY()",
				"z": "event.getPrevZ()",
                "dx": "event.getTargetX()",
				"dy": "event.getTargetY()",
				"dz": "event.getTargetZ()",
				"world": "event.getEntity().level()",
				"entity": "event.getEntity()",
				"event": "event"
				}/>
			</#compress></#assign>
			execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
		}
	}