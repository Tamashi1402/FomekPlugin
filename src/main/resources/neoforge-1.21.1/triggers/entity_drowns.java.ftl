<#include "procedures.java.ftl">
@Mod.EventBusSubscriber public class ${name}Procedure {
	@SubscribeEvent public static void livingDrowns(LivingDrownEvent event) {
    <#assign dependenciesCode><#compress>
    <@procedureDependenciesCode dependencies, {
      "x": "event.getEntity().getX()",
      "y": "event.getEntity().getY()",
      "z": "event.getEntity().getZ()",
      "world": "event.getEntity().level()",
      "entity": "event.getEntity()",
      "isdrowning": "event.isDrowning()",
      "damage": "event.getDamageAmount()",
      "event": "event"

      }/>
    </#compress></#assign>
    execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
  }