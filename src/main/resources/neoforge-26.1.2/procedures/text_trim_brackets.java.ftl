<#if field$type == "first_and_last_char">
  ${input$text}.substring(1, (${input$text}).length() - 1)
<#elseif field$type == "all_brackets">
  (${input$text}.replaceAll("[\\u005B\\u005D\\u0028\\u0029\\u007B\\u007D]", ""))
<#elseif field$type == "namespace">
  ${input$text}.substring((int) (${input$text}.indexOf(":") + 1), (${input$text}).length())
</#if>