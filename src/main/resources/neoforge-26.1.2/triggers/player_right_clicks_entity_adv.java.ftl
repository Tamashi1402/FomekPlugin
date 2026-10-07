<#include "procedures.java.ftl">
@Mod.EventBusSubscriber public class ${name}Procedure {
	@SubscribeEvent public static void onRightClickEntity(PlayerInteractEvent.EntityInteract event) {

    String usehand = "";
    if (event.getHand() == InteractionHand.MAIN_HAND) {
      usehand = "mainhand";
    } else {
      usehand = "offhand";
    }

		<#assign dependenciesCode><#compress>
		<@procedureDependenciesCode dependencies, {
			"x": "event.getPos().getX()",
			"y": "event.getPos().getY()",
			"z": "event.getPos().getZ()",
			"world": "event.getLevel()",
			"entity": "event.getTarget()",
			"sourceentity": "event.getEntity()",
		  "itemstack": "event.getItemStack()",
      "usehand": "usehand",
      "clientside": "event.getSide() == LogicalSide.CLIENT",
			"event": "event"

			}/>
		</#compress></#assign>
		execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
	}