<#include "mcitems.ftl">
{
  ItemStack _customNBTContext = ${mappedMCItemToItemStackCode(input$item, 1)};
  CompoundTag datamap = _customNBTContext.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
  ${statement$do}
  CustomData.set(DataComponents.CUSTOM_DATA, _customNBTContext, datamap);
}