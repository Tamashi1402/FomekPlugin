<#include "procedures.java.ftl">
@Mod.EventBusSubscriber public class ${name}Procedure {
	@SubscribeEvent public static void onEntityLeave(EntityLeaveLevelEvent event) {
    if (event.getEntity() != null) {

      String removalreason = "null";
      if (event.getEntity().getRemovalReason() != null) {
        removalreason = event.getEntity().getRemovalReason().toString().toLowerCase();   
      }
    
      <#assign dependenciesCode><#compress>
        <@procedureDependenciesCode dependencies, {
        "x": "event.getEntity().getX()",
        "y": "event.getEntity().getY()",
        "z": "event.getEntity().getZ()",
        "world": "event.getLevel()",
        "entity": "event.getEntity()",
        "removalreason": "removalreason",
        "event": "event"
        }/>
      </#compress></#assign>
      execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
    }
  }
