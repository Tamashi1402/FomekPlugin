{
    LivingEntity renderEntity${cbi} = (LivingEntity) ${input$entity};
    if (Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(renderEntity${cbi}) instanceof LivingEntityRenderer renderer${cbi}) {
        ResourceLocation texture = ${input$texture};
        renderEntity(entityRenderEvent, renderEntity${cbi}, renderer${cbi}.getModel(), entityRenderEvent.getMultiBufferSource().getBuffer(RenderType.${generator.map(field$rendertype, "rendertypes")})));
    }
}