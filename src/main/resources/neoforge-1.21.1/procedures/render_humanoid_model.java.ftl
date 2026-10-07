{
    ResourceLocation texture = ${input$texture};
    renderHumanoid(playerRenderEvent, ${generator.map(field$model, "humanoidmodels")?replace("CUSTOM:", "")}, playerRenderEvent.getMultiBufferSource().getBuffer(RenderType.${generator.map(field$rendertype, "rendertypes")})));
}