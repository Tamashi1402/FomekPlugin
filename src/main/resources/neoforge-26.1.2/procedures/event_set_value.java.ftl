if (event instanceof <#if 
field$event_type == "entity_falls_1">
LivingFallEvent _event) {
<#elseif field$event_type == "entity_falls_2">
LivingFallEvent _event) {
<#elseif field$event_type == "use_item">
LivingEntityUseItemEvent _event) {
<#elseif field$event_type == "entity_damaged">
LivingDamageEvent _event) {
<#elseif field$event_type == "entity_hurt">
LivingHurtEvent _event) {
<#elseif field$event_type == "entity_heals">
LivingHealEvent _event) {
<#elseif field$event_type == "break_speed">
PlayerEvent.BreakSpeed _event) {
<#elseif field$event_type == "block_break">
BlockEvent.BreakEvent _event) {
<#elseif field$event_type == "entity_xp_drop">
LivingExperienceDropEvent _event) {
<#elseif field$event_type == "shield_block">
ShieldBlockEvent _event) {
<#elseif field$event_type == "entity_breathes_1">
LivingBreatheEvent _event) {
<#elseif field$event_type == "entity_breathes_2">
LivingBreatheEvent _event) {
<#elseif field$event_type == "entity_teleports_1">
EntityTeleportEvent _event) {
<#elseif field$event_type == "entity_teleports_2">
EntityTeleportEvent _event) {
<#elseif field$event_type == "entity_teleports_3">
EntityTeleportEvent _event) {
</#if>
 _event.<#if
field$event_type == "entity_falls_1">
setDistance(${opt.toFloat(input$amount)});
<#elseif field$event_type == "entity_falls_2">
setDamageMultiplier(${opt.toFloat(input$amount)}); 
<#elseif field$event_type == "use_item">
setDuration(${opt.toInt(input$amount)}); 
<#elseif field$event_type == "entity_damaged">
setAmount(${opt.toFloat(input$amount)});
<#elseif field$event_type == "entity_hurt">
setAmount(${opt.toFloat(input$amount)});
<#elseif field$event_type == "entity_heals">
setAmount(${opt.toFloat(input$amount)});
<#elseif field$event_type == "break_speed">
setNewSpeed(${opt.toFloat(input$amount)}); 
<#elseif field$event_type == "block_break">
setExpToDrop(${opt.toInt(input$amount)}); 
<#elseif field$event_type == "entity_xp_drop">
setDroppedExperience(${opt.toInt(input$amount)}); 
<#elseif field$event_type == "shield_block">
setBlockedDamage(${opt.toFloat(input$amount)}); 
<#elseif field$event_type == "entity_breathes_1">
setRefillAirAmount(${opt.toFloat(input$amount)}); 
<#elseif field$event_type == "entity_breathes_2">
setConsumeAirAmount(${opt.toFloat(input$amount)}); 
<#elseif field$event_type == "entity_teleports_1">
setTargetX((double) ${input$amount}); 
<#elseif field$event_type == "entity_teleports_2">
setTargetY((double) ${input$amount}); 
<#elseif field$event_type == "entity_teleports_3">
setTargetZ((double) ${input$amount}); 
</#if>
}