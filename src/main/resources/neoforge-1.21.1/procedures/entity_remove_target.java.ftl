<#include "redwires_plugin_utils.ftl">
if (${input$entity} instanceof Mob _entity${index})
	_entity${index}.setTarget(null);