<#include "redwires_plugin_utils.ftl">
if (event instanceof PlayerEvent.HarvestCheck _harvest${index})
	_harvest${index}.setCanHarvest(${input$result});