<#include "procedures.java.ftl">
@Mod.EventBusSubscriber public class ${name}Procedure {
	@SubscribeEvent public static void explosionBegins(ExplosionEvent.Start event) {
		if (event != null) {
      <#assign dependenciesCode><#compress>
        <@procedureDependenciesCode dependencies, {
        "x": "event.getExplosion().getPosition().x",
        "y": "event.getExplosion().getPosition().y",
        "z": "event.getExplosion().getPosition().z",
        "damagesource": "event.getExplosion().getDamageSource()",
        "isGriefing": "event.getExplosion().interactsWithBlocks()",

        "entity": "event.getExplosion().getExploder()",
        "sourceentity": "event.getExplosion().getIndirectSourceEntity()",

        "world": "event.getLevel()",
        "event": "event"
        }/>
      </#compress></#assign>
      execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
    }
  }