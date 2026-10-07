<#if input$number?starts_with("/*@int*/") == true>
/*@int*/(-${input$number})
<#elseif input$number?starts_with("/*@float*/") == true>
/*@float*/(-${input$number})
<#else>
(-${input$number})
</#if>