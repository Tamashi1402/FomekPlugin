<#include "procedures.java.ftl">
@Mod.EventBusSubscriber public class ${name}Procedure {
	@SubscribeEvent public static void onItemCraftedAdv(PlayerEvent.ItemCraftedEvent event) {
		if (event != null && event.getEntity() != null) {

      ItemStack _slot1 = event.getInventory().getItem((int) 0);
      ItemStack _slot2 = event.getInventory().getItem((int) 1);
      ItemStack _slot3 = event.getInventory().getItem((int) 2);
      ItemStack _slot4 = event.getInventory().getItem((int) 3);

      ItemStack _slot5 = ItemStack.EMPTY;
      ItemStack _slot6 = ItemStack.EMPTY;
      ItemStack _slot7 = ItemStack.EMPTY;
      ItemStack _slot8 = ItemStack.EMPTY;
      ItemStack _slot9 = ItemStack.EMPTY;

      if (event.getInventory().getContainerSize() > 4) {
        _slot5 = event.getInventory().getItem((int) 4);
        _slot6 = event.getInventory().getItem((int) 5);
        _slot7 = event.getInventory().getItem((int) 6);
        _slot8 = event.getInventory().getItem((int) 7);
        _slot9 = event.getInventory().getItem((int) 8);
      }

			<#assign dependenciesCode><#compress>
			<@procedureDependenciesCode dependencies, {
				"x": "event.getEntity().getX()",
				"y": "event.getEntity().getY()",
				"z": "event.getEntity().getZ()",

				"itemstack": "event.getCrafting()",
				"slot1": "_slot1",
				"slot2": "_slot2",
				"slot3": "_slot3",
				"slot4": "_slot4",
				"slot5": "_slot5",
				"slot6": "_slot6",
				"slot7": "_slot7",
				"slot8": "_slot8",
				"slot9": "_slot9",

				"world": "event.getEntity().level()",
				"entity": "event.getEntity()",
				"event": "event"
				}/>
			</#compress></#assign>
			execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
		}
	}