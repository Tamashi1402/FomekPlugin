${package}.api.render.RenderAPI.renderBEWRLWorld(
    (${package}.api.render.BEWRL.Model) ${input$bewrl},
    <#if input$shader?has_content>(${package}.api.render.Shader) ${input$shader}<#else>null</#if>,
    ${input$pos}, ${input$rot}, ${input$scale}
);
