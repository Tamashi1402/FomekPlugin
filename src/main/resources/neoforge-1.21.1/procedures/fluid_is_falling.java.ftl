<#include "mcitems.ftl">
(${mappedBlockToBlockStateCode(input$block)}.getFluidState().getAmount() == 8 && !(${mappedBlockToBlockStateCode(input$block)}.getFluidState().isSource()))