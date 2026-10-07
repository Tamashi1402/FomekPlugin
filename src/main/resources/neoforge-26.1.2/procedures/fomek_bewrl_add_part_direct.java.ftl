<#if input$part?has_content && input$bewrl?has_content>
${package}.api.render.RenderAPI.addPartDirect(
    ${input$bewrl},
    ${input$part},
    ${input$pos},
    ${input$rot},
    ${input$scale},
    <#if input$shader?has_content>${input$shader}<#else>null</#if>
);
</#if>
