<#include "procedures.java.ftl">
@Mod.EventBusSubscriber public class ${name}Procedure {
	@SubscribeEvent public static void playerTeleportsWithChorusFruit(EntityTeleportEvent.ChorusFruit event) {
    <#assign dependenciesCode><#compress>
    <@procedureDependenciesCode dependencies, {
      "x": "event.getPrevX()",
      "y": "event.getPrevY()",
      "z": "event.getPrevZ()",
      "dx": "event.getTargetX()",
      "dy": "event.getTargetY()",
      "dz": "event.getTargetZ()",
      "world": "event.getEntityLiving().level()",
      "entity": "event.getEntityLiving()",
      "event": "event"
      }/>
    </#compress></#assign>
    execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
  }