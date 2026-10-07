<#include "redwires_plugin_utils.ftl">
if (world instanceof ServerLevel _level${index})
	_level${index}.getServer().getCommands().performPrefixedCommand(
	new CommandSourceStack(CommandSource.NULL, new Vec3(${input$x}, ${input$y}, ${input$z}), Vec2.ZERO,
	_level${index}, ${opt.toInt(input$permlevel)}, "", Component.literal(""), _level${index}.getServer(), null).withSuppressedOutput(), ${input$command});