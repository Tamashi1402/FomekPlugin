<#include "procedures.java.ftl">
@Mod.EventBusSubscriber public class ${name}Procedure {
	@SubscribeEvent public static void livingBreathes(LivingBreatheEvent event) {
    <#assign dependenciesCode><#compress>
    <@procedureDependenciesCode dependencies, {
      "x": "event.getEntity().getX()",
      "y": "event.getEntity().getY()",
      "z": "event.getEntity().getZ()",
      "world": "event.getEntity().level()",
      "entity": "event.getEntity()",
      "canbreathe": "event.canBreathe()",
      "refillair": "event.getRefillAirAmount()",
      "consumeair": "event.getConsumeAirAmount()",
      "event": "event"

      }/>
    </#compress></#assign>
    execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
  }