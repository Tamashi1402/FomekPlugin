<#include "redwires_plugin_utils.ftl">
final String _success${index} = ${input$message};
arguments.getSource().sendSuccess(() -> Component.literal(_success${index}), ${input$inform});