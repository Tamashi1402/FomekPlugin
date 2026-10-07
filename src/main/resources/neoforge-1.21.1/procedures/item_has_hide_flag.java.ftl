<#include "mcelements.ftl">
(new Object(){
	public boolean hasHideFlag(ItemStack _item, ItemStack.TooltipPart _hideflag) {
		ItemStack _itemcopy = _item.copy();
		_itemcopy.hideTooltipPart(_hideflag);
		return (_item.getOrCreateTag().getInt("HideFlags") == _itemcopy.getOrCreateTag().getInt("HideFlags"));
	}
}.hasHideFlag(${input$item}, ItemStack.TooltipPart.${field$hide_flag}))