{
    ResourceLocation texture = ${input$texture};
    renderHumanoid(playerRenderEvent, (PlayerModel) entityModel, playerRenderEvent.getMultiBufferSource().getBuffer(RenderType.${generator.map(field$rendertype, "rendertypes")})));
}