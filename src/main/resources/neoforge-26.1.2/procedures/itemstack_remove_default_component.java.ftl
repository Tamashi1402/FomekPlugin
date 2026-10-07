<#include "mcitems.ftl">
<#include "redwires_plugin_utils.ftl">
if (event instanceof ModifyDefaultComponentsEvent _modComponent${index})
	_modComponent${index}.modify(${mappedMCItemToItem(input$provideditemstack)}, builder -> builder.remove(${generator.map(field$datacomponents, "datacomponents")}));