<#if input$part?has_content>
(<#switch field$partType>
  <#case "shape">    (${input$part}.shape != null)<#break>
  <#case "bewrl">    (${input$part}.childModel != null)<#break>
  <#case "item">     (${input$part}.itemStack != null)<#break>
  <#case "java">     (${input$part}.javaModelName != null && ${input$part}.shape == null && ${input$part}.text == null && ${input$part}.childModel == null && ${input$part}.itemStack == null)<#break>
  <#case "text">     (${input$part}.text != null)<#break>
  <#case "empty">    (${input$part}.shape == null && ${input$part}.text == null && ${input$part}.javaModelName == null && ${input$part}.childModel == null && ${input$part}.itemStack == null)<#break>
  <#default>false
</#switch>)
<#else>false</#if>
