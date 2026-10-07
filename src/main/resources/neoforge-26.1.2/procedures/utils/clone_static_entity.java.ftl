private static Entity createStaticClone(Entity ent) {
	Entity staticClone = ent.getType().create(ent.level());
	if (staticClone != null)
		staticClone.load(ent.saveWithoutId(new CompoundTag()));
	return staticClone;
}