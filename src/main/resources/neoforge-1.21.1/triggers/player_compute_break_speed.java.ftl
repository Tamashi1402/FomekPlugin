<#include "procedures.java.ftl">
@Mod.EventBusSubscriber public class ${name}Procedure {
	@SubscribeEvent public static void onComputeBreakSpeed(PlayerEvent.BreakSpeed event) {
		if (event != null && event.getEntity() != null) {

      Entity entity = event.getEntity();
      BlockState blockstate = event.getState();
      ItemStack itemstack = entity instanceof Player _plr ? _plr.getMainHandItem() : ItemStack.EMPTY;
      LevelAccessor world = entity.level();

  		boolean iscorrecttool = itemstack.getItem().isCorrectToolForDrops(blockstate);
      float breakspeed = event.getOriginalSpeed();
      float hardness = blockstate.getDestroySpeed(world, BlockPos.containing(0, 0, 0));
      float breaktime = itemstack.getDestroySpeed(blockstate);

      if (iscorrecttool) {
        breaktime = 1.5f;
      } else {
        breaktime = 5f;
      }
  
      float toolfactor = (itemstack.getItem() instanceof TieredItem _item ? _item.getTier().getSpeed() : 1) / breaktime;
      if (iscorrecttool && (itemstack.getEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY)) != 0) {
        toolfactor = toolfactor + (float) (1 + Math.pow(itemstack.getEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY), 2));
      }
      breaktime = (int) Math.max(1, Math.ceil(breaktime * hardness * 20 / breakspeed));

			<#assign dependenciesCode><#compress>
			<@procedureDependenciesCode dependencies, {
        "x": "event.getPosition().get().getX()",
        "y": "event.getPosition().get().getY()",
        "z": "event.getPosition().get().getZ()",
				"world": "world",
				"entity": "entity",
				"blockstate": "blockstate",

				"hardness": "hardness",
				"breakspeed": "breakspeed",
				"breaktime": "breaktime",
				"toolfactor": "toolfactor",
				"itemstack": "itemstack",
				"iscorrecttool": "iscorrecttool",

				"event": "event"

				}/>
			</#compress></#assign>
			execute(event<#if dependenciesCode?has_content>,</#if>${dependenciesCode});
		}
	}