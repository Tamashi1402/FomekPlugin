<#include "mcitems.ftl">
<#include "redwires_plugin_utils.ftl">
if (event instanceof AnvilUpdateEvent _anvil${index})
	_anvil${index}.setOutput(${mappedMCItemToItemStackCode(input$itemoutput, 1)});