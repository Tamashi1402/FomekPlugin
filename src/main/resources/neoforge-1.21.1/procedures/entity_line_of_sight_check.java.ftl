<#include "redwires_plugin_utils.ftl">
(${input$entity} instanceof LivingEntity _liveEntLoS${index} && ${input$sourceentity} != null && _liveEntLoS${index}.hasLineOfSight(${input$sourceentity}))