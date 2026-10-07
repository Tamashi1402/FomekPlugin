<#if input$target?has_content && input$source?has_content>
${package}.api.render.RenderAPI.setBEWRL(${input$target}, ${input$source});
</#if>
