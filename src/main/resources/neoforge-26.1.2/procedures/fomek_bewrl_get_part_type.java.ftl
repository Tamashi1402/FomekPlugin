<#if input$part?has_content>
(${input$part}.childModel != null ? "bewrl" :
 ${input$part}.itemStack != null ? "item" :
 ${input$part}.text != null ? "text" :
 ${input$part}.shape != null ? "shape" :
 ${input$part}.javaModelName != null ? "java" : "empty")
<#else>"empty"</#if>
