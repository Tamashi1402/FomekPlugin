<#include "procedures.java.ftl">
@Mod.EventBusSubscriber public class ${name}Procedure {
	@SubscribeEvent public static void onNonattributableBlockPlacement(BlockEvent.EntityPlaceEvent event) {
    <#assign dependenciesCode><#compress>
    <@procedureDependenciesCode dependencies, {
      "x": "event.getPos().getX()",
      "y": "event.getPos().getY()",
      "z": "event.getPos().getZ()",
      "blockstate": "event.getState()",
      //"current": "event.getBlockSnapshot().getCurrentBlock()",
      //"replaced": "event.getBlockSnapshot().getReplacedBlock()",
      //"placedagainst": "event.getPlacedAgainst()",
      //"placed": "event.getPlacedBlock()",
      "world": "event.getLevel()",
      "event": "event"
      }/>
    </#compress></#assign>
    execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
  }