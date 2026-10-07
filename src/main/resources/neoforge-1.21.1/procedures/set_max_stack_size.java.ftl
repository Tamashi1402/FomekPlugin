<#include "mcitems.ftl">
${mappedMCItemToItemStackCode(input$provideditemstack)}.set(DataComponents.MAX_STACK_SIZE, ${opt.toInt(input$stacksize)});