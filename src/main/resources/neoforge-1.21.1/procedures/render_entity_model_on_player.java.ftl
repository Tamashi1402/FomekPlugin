{
    ResourceLocation texture = ${input$texture};
    renderEntity(playerRenderEvent, ${input$model}, playerRenderEvent.getMultiBufferSource().getBuffer(RenderType.${generator.map(field$rendertype, "rendertypes")})));
}