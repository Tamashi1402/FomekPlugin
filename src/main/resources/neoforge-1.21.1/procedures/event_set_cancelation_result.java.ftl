<#include "mcelements.ftl">
if (event instanceof PlayerInteractEvent _intEvent) {
  _intEvent.setCancellationResult(InteractionResult.FAIL);
}