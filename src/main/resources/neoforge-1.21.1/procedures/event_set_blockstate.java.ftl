<#include "mcitems.ftl">
if (event instanceof <#if
field$event_type == "tool_secondary">
BlockEvent.BlockToolModificationEvent
</#if>
 _event) {
_event.<#if
field$event_type == "tool_secondary">
setFinalState(${mappedBlockToBlockStateCode(input$block)});
</#if>
}