<#include "redwires_plugin_utils.ftl">
if (${input$entity} instanceof Mob _mob${index})
    _mob${index}.setPersistenceRequired();