<#include "mcitems.ftl">
<#if input$itemstack?has_content && input$tagName?has_content>
${package}.api.render.BEWRLStorage.getItemBEWRL(${mappedMCItemToItemStackCode(input$itemstack, 1)}, ${input$tagName})
<#else>null</#if>