((new Object() {
  public Vec3 get(Entity entity, double length) {
    return Vec3.atLowerCornerOf(entity.level().clip(new ClipContext(entity.getEyePosition(), entity.getEyePosition().add(entity.getLookAngle().scale(length)), ClipContext.Block.${field$blockmode}, ClipContext.Fluid.${field$fluidmode}, (Entity) null)).getBlockPos());
  }
}).get(${input$entity}, ${input$length}))