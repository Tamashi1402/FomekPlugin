<#include "mcitems.ftl">
((${input$entity} instanceof LivingEntity _entity) ? _entity.isHolding(${mappedMCItemToItem(input$item)}) : false)