<#include "mcitems.ftl">
  <#if field$rotation_type == "HorizontalDirectional">
((${mappedBlockToBlock(input$block)} instanceof ${field$rotation_type}Block) || (${mappedBlockToBlock(input$block)} instanceof StairBlock))
  <#else>
(${mappedBlockToBlock(input$block)} instanceof ${field$rotation_type}Block)
  </#if>