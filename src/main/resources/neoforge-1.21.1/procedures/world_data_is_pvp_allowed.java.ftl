((!world.isClientSide() && world.getServer() != null) ?
(ServerLifecycleHooks.getCurrentServer().isPvpAllowed()):true)