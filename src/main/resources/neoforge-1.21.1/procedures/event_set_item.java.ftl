<#include "mcitems.ftl">
if (event instanceof <#if
field$event_type == "finish_using_item">
LivingEntityUseItemEvent.Finish
</#if>
 _event) {
_event.<#if
field$event_type == "finish_using_item">
setResultStack(${mappedMCItemToItemStackCode(input$item, 1)});
</#if>
}