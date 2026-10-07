if (!world.isClientSide() && world.getServer() != null)                     
    ServerLifecycleHooks.getCurrentServer().setDefaultGameType(GameType.${generator.map(field$gamemode, "gamemodes")});
