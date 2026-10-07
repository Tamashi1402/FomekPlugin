<#if field$operator == "=">
  (${input$left}.equals(${input$right}))
<#elseif field$operator == "≠">
  (!${input$left}.equals(${input$right}))
</#if>