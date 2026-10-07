<#include "procedures.java.ftl">

import net.neoforged.neoforge.event.entity.EntityEvent;

@EventBusSubscriber public class ${name}Procedure {
	@SubscribeEvent public static void onHitboxCalculated(EntityEvent.Size event) {
		<#assign dependenciesCode>
			<@procedureDependenciesCode dependencies, {
				"x": "event.getEntity().getX()",
				"y": "event.getEntity().getY()",
				"z": "event.getEntity().getZ()",
				"world": "event.getEntity().level()",
				"entity": "event.getEntity()",
				"oldWidth": "event.getOldSize().width()",
				"oldHeight": "event.getOldSize().height()",
				"hitboxCalculationEvent": "event",
				"event": "event"
			}/>
		</#assign>
		execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
	}