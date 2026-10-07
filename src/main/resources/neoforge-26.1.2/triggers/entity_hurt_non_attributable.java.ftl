<#include "procedures.java.ftl">
@Mod.EventBusSubscriber public class ${name}Procedure {
	@SubscribeEvent public static void onEntityDamagedNonattributable(LivingDamageEvent event) {
		if (event!=null && event.getEntity()!=null && event.getSource().getEntity()==null) {
			<#assign dependenciesCode><#compress>
			<@procedureDependenciesCode dependencies, {
				"x": "event.getEntity().getX()",
				"y": "event.getEntity().getY()",
				"z": "event.getEntity().getZ()",
				"amount": "event.getAmount()",
				"world": "event.getEntity().level()",
				"entity": "event.getEntity()",
				"damagesource": "event.getSource()",
				"event": "event"
				}/>
			</#compress></#assign>
			execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
		}
	}