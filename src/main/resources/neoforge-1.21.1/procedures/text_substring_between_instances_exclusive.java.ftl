(${input$text}.substring((int) ${input$text}.

<#if field$from_index_order == "first">
  indexOf
<#else>
  lastIndexOf
</#if>

(${input$from_instance}) + ${input$from_instance}.length(), (int) ${input$text}.

<#if field$to_index_order == "first">
  indexOf
<#else>
  lastIndexOf
</#if>

(${input$to_instance})))