<#include "procedures.java.ftl">
@Mod.EventBusSubscriber public class ${name}Procedure {
	@SubscribeEvent public static void onSecondaryToolUse(BlockEvent.BlockToolModificationEvent event) {
  
    String toolaction = event.getToolAction().toString().toLowerCase();
    toolaction = toolaction.replace("]", "").replace("toolaction[", "");
  
    String usehand = "";
    if (event.getContext().getHand() == InteractionHand.MAIN_HAND) {
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
  	  "entity": "event.getPlayer()",
  	  "blockstate": "event.getState()",
  	  "direction": "event.getContext().getClickedFace()",
  	  "itemstack": "event.getHeldItemStack()",
  	  "toolaction": "toolaction",
  	  "usehand": "usehand",
  	  "event": "event"

  	}/>
  	</#compress></#assign>
  	execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
  }