if (event instanceof

<#if field$event_type == "attack_target">
  LivingChangeTargetEvent
</#if>

 _event) {
  _event.

<#if field$event_type == "attack_target">
  setTarget( 
</#if>

  ${input$entity});
}