<#if input$number?starts_with("/*@int*/") == true>
  false
<#elseif input$number?starts_with("/*@float*/") == true>
  Float.isNaN(${input$number})
<#else>
  Double.isNaN(${input$number})
</#if>