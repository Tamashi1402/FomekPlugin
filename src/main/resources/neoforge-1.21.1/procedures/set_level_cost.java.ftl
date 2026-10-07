<#include "redwires_plugin_utils.ftl">
if (event instanceof AnvilUpdateEvent _anvil${index})
    _anvil${index}.setCost(${opt.toInt(input$cost)});