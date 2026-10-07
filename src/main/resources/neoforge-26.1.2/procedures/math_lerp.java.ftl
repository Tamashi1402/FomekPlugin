<#function checkFloat>
  <#if input$step?starts_with("/*@int*/") == true || input$step?starts_with("/*@float*/") == true>
  <#if input$start?starts_with("/*@int*/") == true || input$start?starts_with("/*@float*/") == true>
  <#if input$end?starts_with("/*@int*/") == true || input$end?starts_with("/*@float*/") == true>
    <#return true>
  </#if>
  </#if>
  </#if>
  <#return false>
</#function>
<#if checkFloat() == true>
/*@float*/Mth.lerp(${input$step}, ${input$start}, ${input$end})
<#else>
Mth.lerp(${input$step}, ${input$start}, ${input$end})
</#if>