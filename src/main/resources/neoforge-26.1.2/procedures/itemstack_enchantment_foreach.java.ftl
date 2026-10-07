<#include "mcitems.ftl">
{
    ItemStack _redwire$itemstack = ${mappedMCItemToItemStackCode(input$item)};
	ItemEnchantments _appliedEnchantments = _redwire$itemstack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
	for (Holder<Enchantment> _enchantHolder : _appliedEnchantments.keySet()) {
		String stringiterator = world.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getKey(_enchantHolder.value()).toString();
		${statement$foreach}
	}
}