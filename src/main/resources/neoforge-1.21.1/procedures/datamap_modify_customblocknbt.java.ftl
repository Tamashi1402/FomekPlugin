<#include "mcelements.ftl">
if(!world.isClientSide()) {
  BlockPos _customNBTPos = ${toBlockPos(input$x,input$y,input$z)};
  BlockEntity _customNBTContext = world.getBlockEntity(_customNBTPos);
  if (_customNBTContext != null) {
    CompoundTag datamap = _customNBTContext.getPersistentData();
    ${statement$do}
    if (world instanceof Level _customNBTlevel) {
      BlockState _customNBTState = _customNBTlevel.getBlockState(_customNBTPos);
      _customNBTlevel.sendBlockUpdated(_customNBTPos, _customNBTState, _customNBTState, 3);
    }
  }
}