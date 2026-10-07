private static String getModInfo(String modid, int type) {
	for (IModInfo mod : ModList.get().getMods()) {
		if (mod.getModId().equals(modid.toLowerCase())) {
			if (type == 0) {
				return mod.getVersion().toString();
			} else {
				return mod.getDisplayName();
			}
		}
	}
	return "";
}