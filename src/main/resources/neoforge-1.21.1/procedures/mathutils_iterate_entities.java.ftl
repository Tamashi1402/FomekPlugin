for (Entity entityiterator : world.getEntitiesOfClass(Entity.class, new AABB(${input$min}, ${input$max}))) {
  ${statement$foreach}
}