<#include "procedures.java.ftl">
@Mod.EventBusSubscriber public class ${name}Procedure {
	@SubscribeEvent public static void onArrowNocked(ArrowNockEvent event) {
  
    String usehand = "";
    if (event.getHand() == InteractionHand.MAIN_HAND) {
      usehand = "mainhand";
    } else {
      usehand = "offhand";
    }
  
		if (event != null && event.getEntity() != null) {
			<#assign dependenciesCode><#compress>
			<@procedureDependenciesCode dependencies, {
				"x": "event.getEntity().getX()",
				"y": "event.getEntity().getY()",
				"z": "event.getEntity().getZ()",
				"world": "event.getLevel()",
				"entity": "event.getEntity()",
        "itemstack": "event.getBow()",
        "usehand": "usehand",
        "hasAmmo": "event.hasAmmo()",
        "action": "event.getAction()",
				"event": "event"

				}/>
			</#compress></#assign>
			execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
		}
	}