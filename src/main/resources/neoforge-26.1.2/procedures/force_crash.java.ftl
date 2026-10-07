if (!world.isClientSide() && world.getServer() != null)
    ServerLifecycleHooks.getCurrentServer().stopServer();