{
    ResourceLocation texture = ${input$texture};
    renderEntity(entityRenderEvent, (LivingEntity) entity, ${input$model}, entityRenderEvent.getMultiBufferSource().getBuffer(RenderType.${generator.map(field$rendertype, "rendertypes")})));
}