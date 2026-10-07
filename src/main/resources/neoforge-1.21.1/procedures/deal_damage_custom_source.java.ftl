<#include "redwires_plugin_utils.ftl">
if (${input$entity} instanceof LivingEntity _entity${index})
	_entity${index}.hurt(<@templateOrFallback "localized_damage_source"/>localizedDamageSource(${input$damagesource},
	${input$text}), ${opt.toFloat(input$amount)});