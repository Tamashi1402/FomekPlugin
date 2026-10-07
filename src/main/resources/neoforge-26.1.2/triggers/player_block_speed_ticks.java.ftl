<#include "procedures.java.ftl">
@EventBusSubscriber public class ${name}Procedure {
	@SubscribeEvent public static void onBlockBreaking(PlayerEvent.BreakSpeed event) {
		if (event.getPosition().isEmpty())
			return;
		<#assign dependenciesCode><#compress>
			<@procedureDependenciesCode dependencies, {
			"x": "event.getPosition().get().getX()",
			"y": "event.getPosition().get().getY()",
			"z": "event.getPosition().get().getZ()",
			"world": "event.getEntity().level()",
			"entity": "event.getEntity()",
			"breakSpeed": "event.getNewSpeed()",
			"blockstate": "event.getState()"
			}/>
		</#compress></#assign>
		execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
	}
