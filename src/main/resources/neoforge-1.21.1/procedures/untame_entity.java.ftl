<#include "redwires_plugin_utils.ftl">
if (${input$entity} instanceof TamableAnimal _ent${index})
	_ent${index}.setTame(false, false);