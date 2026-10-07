if (world instanceof net.minecraft.client.multiplayer.ClientLevel) {
  for(net.minecraft.world.entity.Entity entityiterator : ((net.minecraft.client.multiplayer.ClientLevel) world).entitiesForRendering()) {
    ${statement$foreach}
  }
}
