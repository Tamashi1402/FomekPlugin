<#include "mcitems.ftl">
${mappedMCItemToItemStackCode(input$provideditemstack)}.set(DataComponents.MAX_DAMAGE, ${opt.toInt(input$maxdmg)});