<#assign toTarget = input$entity>
<#include "redwires_plugin_utils.ftl">
<#if toTarget == "null">
if (event instanceof LivingChangeTargetEvent _targetEvent${index})
	_targetEvent${index}.setNewAboutToBeSetTarget(null);
<#else>
if (event instanceof LivingChangeTargetEvent _targetEvent${index} && ${input$entity} instanceof LivingEntity _toTarget${index})
	_targetEvent${index}.setNewAboutToBeSetTarget(_toTarget${index});
</#if>