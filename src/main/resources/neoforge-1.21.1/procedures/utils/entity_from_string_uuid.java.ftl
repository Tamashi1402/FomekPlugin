private static Entity entityFromStringUUID(String uuid, LevelAccessor world) {
	if (world instanceof ServerLevel _server) {
		try {
			return _server.getEntity(UUID.fromString(uuid));
		} catch (Exception e) {}
	}
	return null;
}