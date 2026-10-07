<#include "redwires_plugin_utils.ftl">
Entity _ent${index} = ${input$entity};
if(!_ent${index}.level().isClientSide() && _ent${index}.getServer() != null)
    _ent${index}.getServer().getCommands().performPrefixedCommand(new CommandSourceStack(
    CommandSource.NULL, _ent${index}.position(), _ent${index}.getRotationVector(),
    _ent${index}.level() instanceof ServerLevel ? (ServerLevel) _ent${index}.level() : null, ${opt.toInt(input$permlevel)},
    _ent${index}.getName().getString(), _ent${index}.getDisplayName(), _ent${index}.level().getServer(), _ent${index}), ${input$command});