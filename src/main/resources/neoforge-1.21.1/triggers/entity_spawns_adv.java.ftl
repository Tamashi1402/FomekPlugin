<#include "procedures.java.ftl">
@Mod.EventBusSubscriber public class ${name}Procedure {
	@SubscribeEvent public static void entitySpawns(MobSpawnEvent.PositionCheck event) {
    <#assign dependenciesCode><#compress>
    <@procedureDependenciesCode dependencies, {
      "x": "event.getX()",
      "y": "event.getY()",
      "z": "event.getZ()",
      "world": "event.getLevel()",
      "entity": "event.getEntity()",
      "mobspawner": "event.getBaseSpawner()",
      "spawnType": "event.getSpawnType().toString().toLowerCase()",
      "defaultResult": "event.getDefaultResult()",
      "event": "event"

      }/>
    </#compress></#assign>
    execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
  }