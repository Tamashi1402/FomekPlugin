<#include "mcitems.ftl">
<#if input$itemstack?has_content>
<#if field$transform == "true">
${package}.api.render.RenderAPI.reconstructItemAsBEWRLTransformed(${mappedMCItemToItemStackCode(input$itemstack, 1)}, ${input$rimOnly})
<#else>
${package}.api.render.RenderAPI.reconstructItemAsBEWRL(${mappedMCItemToItemStackCode(input$itemstack, 1)}, ${input$rimOnly})
</#if>
<#else>
${package}.api.render.RenderAPI.reconstructItemAsBEWRL(net.minecraft.world.item.ItemStack.EMPTY, false)
</#if>
