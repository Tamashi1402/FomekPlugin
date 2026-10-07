<#include "mcitems.ftl">
(((${input$entity} instanceof LivingEntity _entity) ? _entity.getMainHandItem() : ItemStack.EMPTY).getItem() instanceof ${generator.map(field$item_type, "itemtypes")}Item
|| (entity instanceof LivingEntity _entity ? _entity.getOffhandItem() : ItemStack.EMPTY).getItem() instanceof ${generator.map(field$item_type, "itemtypes")}Item)