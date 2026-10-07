<#include "redwires_plugin_utils.ftl">
if (event instanceof AnvilUpdateEvent _anvil${index})
    _anvil${index}.setMaterialCost(${opt.toInt(input$cost)});