<#if input$part?has_content && input$bewrl?has_content>
${package}.api.render.RenderAPI.removePartFromBEWRL(${input$bewrl}, ${input$part});
</#if>
