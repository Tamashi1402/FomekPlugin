<#if field$type == "int">
<#if input$number?starts_with("/*@int*/") == true>
${input$number}
<#else>
/*@int*/((int) ${input$number})
</#if>
<#elseif field$type == "float">
<#if input$number?starts_with("/*@float*/") == true>
${input$number}
<#else>
/*@float*/((float) ${input$number})
</#if>
<#elseif field$type == "double">
((double) ${input$number})
</#if>