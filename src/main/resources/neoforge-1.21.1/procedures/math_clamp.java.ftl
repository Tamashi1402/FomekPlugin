<#function checkFloat>
  <#if input$number?starts_with("/*@int*/") == true || input$number?starts_with("/*@float*/") == true>
  <#if input$min?starts_with("/*@int*/") == true || input$min?starts_with("/*@float*/") == true>
  <#if input$max?starts_with("/*@int*/") == true || input$max?starts_with("/*@float*/") == true>
    <#return true>
  </#if>
  </#if>
  </#if>
  <#return false>
</#function>
<#function checkInt>
  <#if input$number?starts_with("/*@int*/") == true>
  <#if input$min?starts_with("/*@int*/") == true>
  <#if input$max?starts_with("/*@int*/") == true>
    <#return true>
  </#if>
  </#if>
  </#if>
  <#return false>
</#function>
<#if checkFloat() == true>
<#if checkInt() == true>
/*@int*/Mth.clamp(${input$number}, ${input$min}, ${input$max})
<#else>
/*@float*/Mth.clamp(${input$number}, ${input$min}, ${input$max})
</#if>
<#else>
Mth.clamp(${input$number}, ${input$min}, ${input$max})
</#if>