<#include "procedures.java.ftl">
@Mod.EventBusSubscriber public class ${name}Procedure {
	@SubscribeEvent public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
  
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
		  "entity": "event.getEntity()",
		  "blockstate": "event.getLevel().getBlockState(event.getPos())",
		  "direction": "event.getFace()",
		  "itemstack": "event.getItemStack()",
		  "usehand": "usehand",
      "clientside": "event.getSide() == LogicalSide.CLIENT",
		  "hx": "event.getHitVec().getLocation().x()",
		  "hy": "event.getHitVec().getLocation().y()",
		  "hz": "event.getHitVec().getLocation().z()",
		  "insidehitbox": "event.getHitVec().isInside()",
		  "event": "event"

		}/>
		</#compress></#assign>
		execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
	}