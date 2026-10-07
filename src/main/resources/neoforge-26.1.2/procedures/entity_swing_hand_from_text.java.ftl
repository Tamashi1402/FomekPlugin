if (${input$hand}.equals("offhand")) {
  if (${input$entity} instanceof LivingEntity _entity)
    _entity.swing(InteractionHand.OFF_HAND, true);
} else {
  if (${input$entity} instanceof LivingEntity _entity)
    _entity.swing(InteractionHand.MAIN_HAND, true);
}