<#assign left = "">
<#assign right = "">
<#if input$left?starts_with("/*@int*/") == true>
  <#assign left = input$left>
<#else>
  <#assign left = "((int)" + input$left + ")">
</#if>
<#if input$right?starts_with("/*@int*/") == true>
  <#assign right = input$right>
<#else>
  <#assign right = "((int)" + input$right + ")">
</#if>
/*@int*/(${left} ${field$operator} ${right})