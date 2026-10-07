<#include "redwires_plugin_utils.ftl">
if (event instanceof LivingIncomingDamageEvent _hurt${index})
    _hurt${index}.setAmount(${opt.toFloat(input$dmg)});