private static BlockPos getBlockBetweenEntities(Entity ent1, Entity ent2) {
	if (ent1 instanceof LivingEntity _liveEnt && ent2 != null && !_liveEnt.hasLineOfSight(ent2))
		return ent1.level().clip(new ClipContext(ent1.position(), ent2.position(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ent1)).getBlockPos();
	return BlockPos.ZERO;
}