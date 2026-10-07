if (world instanceof net.minecraft.client.multiplayer.ClientLevel _blockEntityContext) {
  int _scanRange = net.minecraft.client.Minecraft.getInstance().options.getEffectiveRenderDistance();
  net.minecraft.core.BlockPos _scanCenter = net.minecraft.client.Minecraft.getInstance().player.blockPosition();
  net.minecraft.world.level.chunk.LevelChunk _levelChunk;
  net.minecraft.world.level.block.state.BlockState blockstateiterator;
  int positionx, positiony, positionz;
  for (int _chunkZ = -_scanRange; _chunkZ <= _scanRange; ++_chunkZ) {
    for (int _chunkX = -_scanRange; _chunkX <= _scanRange; ++_chunkX) {
      _levelChunk = _blockEntityContext.getChunk(
        net.minecraft.core.SectionPos.blockToSectionCoord(_scanCenter.getX() + (_chunkX << 4)),
        net.minecraft.core.SectionPos.blockToSectionCoord(_scanCenter.getZ() + (_chunkZ << 4)));
      if (_levelChunk != null) {
        for (java.util.Map.Entry<net.minecraft.core.BlockPos, net.minecraft.world.level.block.entity.BlockEntity> _blockEntityEntry : _levelChunk.getBlockEntities().entrySet()) {
          blockstateiterator = _blockEntityEntry.getValue().getBlockState();
          positionx = _blockEntityEntry.getKey().getX();
          positiony = _blockEntityEntry.getKey().getY();
          positionz = _blockEntityEntry.getKey().getZ();
          ${statement$foreach}
        }
      }
    }
  }
}
