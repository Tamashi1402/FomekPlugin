  <#if field$type == "any">
!${input$entity}.getEyeInFluidType().toString().equals("minecraft:empty")
  <#else>
${input$entity}.getEyeInFluidType().toString().equals("minecraft:${field$type}")
  </#if>