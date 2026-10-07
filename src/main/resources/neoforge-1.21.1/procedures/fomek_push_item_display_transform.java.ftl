<#include "mcitems.ftl">
<#if input$itemstack?has_content>
${package}.api.render.RenderAPI.pushItemDisplayTransform(${mappedMCItemToItemStackCode(input$itemstack, 1)});
<#else>
${package}.api.render.RenderAPI.pushItemDisplayTransform(net.minecraft.world.item.ItemStack.EMPTY);
</#if>
