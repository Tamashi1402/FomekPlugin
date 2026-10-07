if (!world.isClientSide() && world.getServer() != null)
    ServerLifecycleHooks.getCurrentServer().setPvpAllowed(${input$boolean});