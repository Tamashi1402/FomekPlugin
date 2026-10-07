<#include "mcitems.ftl">
<#if input$itemstack?has_content>
${package}.api.render.RenderAPI.addBEWRLItemPart(${mappedMCItemToItemStackCode(input$itemstack, 1)}, ${input$glowing}, ${input$pos}, ${input$rot}, ${input$scale});
</#if>
