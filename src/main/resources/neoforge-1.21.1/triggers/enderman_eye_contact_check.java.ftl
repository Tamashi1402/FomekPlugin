<#include "procedures.java.ftl">
@Mod.EventBusSubscriber public class ${name}Procedure {
	@SubscribeEvent public static void endermanAngered(EnderManAngerEvent event) {
		<#assign dependenciesCode><#compress>
		<@procedureDependenciesCode dependencies, {
			"x": "event.getEntity().getX()",
			"y": "event.getEntity().getY()",
			"z": "event.getEntity().getZ()",
			"px": "event.getPlayer().getX()",
			"py": "event.getPlayer().getY()",
			"pz": "event.getPlayer().getZ()",
      "world": "event.getEntity().level()",
			"entity": "event.getEntity()",
			"sourceentity": "event.getPlayer()",
			"event": "event"

			}/>
		</#compress></#assign>
		execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
	}