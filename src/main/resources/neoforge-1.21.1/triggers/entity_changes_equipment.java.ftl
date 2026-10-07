<#include "procedures.java.ftl">
@Mod.EventBusSubscriber public class ${name}Procedure {
	@SubscribeEvent public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
		<#assign dependenciesCode><#compress>
		<@procedureDependenciesCode dependencies, {
			"x": "event.getEntity().getX()",
			"y": "event.getEntity().getY()",
			"z": "event.getEntity().getZ()",
      "world": "event.getEntity().level()",
			"entity": "event.getEntity()",
		  "from": "event.getFrom()",
		  "to": "event.getTo()",
      "slot": "event.getSlot().toString().toLowerCase()",
			"event": "event"

			}/>
		</#compress></#assign>
		execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
	}