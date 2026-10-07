<#include "redwires_plugin_utils.ftl">
if (${input$entity} instanceof EnderMan _ender${index})
	_ender${index}.setCarriedBlock(null);