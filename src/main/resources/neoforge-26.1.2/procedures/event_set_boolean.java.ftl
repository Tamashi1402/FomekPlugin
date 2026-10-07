if (event instanceof <#if 
field$event_type == "shield_block">
ShieldBlockEvent _event) {
<#elseif field$event_type == "entity_breathes">
LivingBreatheEvent _event) {
</#if>
 _event.<#if
field$event_type == "shield_block">
setShieldTakesDamage(${input$boolean});
<#elseif field$event_type == "entity_breathes">
setCanBreathe(${input$boolean});
setCanRefillAir(${input$boolean});
</#if>
}