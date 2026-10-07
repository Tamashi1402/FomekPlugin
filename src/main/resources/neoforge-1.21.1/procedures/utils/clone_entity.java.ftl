private static Entity createClone(Entity ent, LevelAccessor world) {
	Entity _clone = null;
	if (world instanceof ServerLevel _level) {
		_clone = ent.getType().spawn(_level, BlockPos.containing(ent.getX()+0.5, ent.getY(), ent.getZ()+0.5), MobSpawnType.MOB_SUMMONED);
		if (_clone != null) {
			_clone.load(ent.saveWithoutId(new CompoundTag()));
			_clone.setUUID(UUID.randomUUID());
		}
	}
	return _clone;
}
