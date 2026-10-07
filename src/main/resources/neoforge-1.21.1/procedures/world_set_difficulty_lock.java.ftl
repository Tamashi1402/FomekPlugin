if (!world.isClientSide() && world.getServer() != null)
    ServerLifecycleHooks.getCurrentServer().setDifficultyLocked(${input$boolean});