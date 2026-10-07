<#if input$number?starts_with("/*@int*/") == true>
  false
<#elseif input$number?starts_with("/*@float*/") == true>
  Float.isInfinite(${input$number})
<#else>
  Double.isInfinite(${input$number})
</#if>