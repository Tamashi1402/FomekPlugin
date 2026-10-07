<#include "mcitems.ftl">
${mappedMCItemToItemStackCode(input$provideditemstack)}.set(DataComponents.REPAIR_COST, ${opt.toInt(input$repaircost)});