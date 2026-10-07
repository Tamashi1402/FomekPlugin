if (world.getServer() != null) {
	LevelAccessor _origWorld = world;
	for (ServerLevel worlditerator: world.getServer().getAllLevels()) {
		world = worlditerator;
		${statement$todo}
	}
	world = _origWorld;
}