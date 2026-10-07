<#include "redwires_plugin_utils.ftl">
<@templateOrFallback "timed_loop", "timed_loop_init"/>
<#-- @formatter:off -->
createTimedLoop(${opt.toInt(input$times)}, ${opt.toInt(input$tickwait)}, _timedLoop -> {
    ${statement$foreach}
    return true;
});
<#-- @formatter:on -->