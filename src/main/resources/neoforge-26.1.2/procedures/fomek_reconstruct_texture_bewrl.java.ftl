<#if input$texture?has_content>
<#if field$transform == "true">
${package}.api.render.RenderAPI.reconstructTextureAsBEWRLTransformed(${input$texture}, ${input$rimOnly})
<#else>
${package}.api.render.RenderAPI.reconstructTextureAsBEWRL(${input$texture}, ${input$rimOnly})
</#if>
<#else>
${package}.api.render.RenderAPI.reconstructTextureAsBEWRL(net.minecraft.resources.ResourceLocation.parse("minecraft:textures/block/dirt.png"), false)
</#if>
