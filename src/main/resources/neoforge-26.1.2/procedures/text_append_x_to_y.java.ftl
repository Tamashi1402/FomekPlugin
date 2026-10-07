<#if !input$local_var.contains("\"")>
  <#if field$append_pos == "start">
    ${input$local_var} = ${input$append} + ${input$local_var};
  <#else>
    ${input$local_var} = ${input$local_var} + ${input$append};
  </#if>
<#else>
//text append procedure was not loaded because the target is not a local variable
</#if>