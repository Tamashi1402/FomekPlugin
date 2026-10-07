<#include "redwires_plugin_utils.ftl">
if (event instanceof PlayerEvent.BreakSpeed _speed${index})
	_speed${index}.setNewSpeed(${opt.toFloat(input$speed)});