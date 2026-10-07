if(${input$entity} instanceof AbstractMinecart _cart${cbi} && _cart${cbi}.isOnRails()) {
  _cart${cbi}.moveMinecartOnRail(BlockPos.containing(_cart${cbi}.getX(), _cart${cbi}.getY(), _cart${cbi}.getZ()));
}