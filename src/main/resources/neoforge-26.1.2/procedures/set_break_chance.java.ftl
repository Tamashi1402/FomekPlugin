<#include "redwires_plugin_utils.ftl">
if (event instanceof AnvilRepairEvent _repair${index})
	_repair${index}.setBreakChance(${opt.toFloat(input$chance)});