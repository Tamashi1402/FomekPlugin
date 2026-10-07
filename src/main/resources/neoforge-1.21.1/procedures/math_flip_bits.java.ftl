<#if input$number?starts_with("/*@int*/") == true>
/*@int*/~${input$number}
<#else>
/*@int*/(~(int) ${input$number})
</#if>