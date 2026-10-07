new ${package}.api.guisystems.MenuStyle(${opt.toInt(input$background)}, ${opt.toInt(input$border)}, ${opt.toInt(input$textColor)}, ${input$text}, ${input$font}, ${opt.toFloat(input$size)}, ${input$texture}, <#if input$BOOL_hasText?has_content>${input$BOOL_hasText}<#else>${field$hasText?lower_case}</#if>, "${field$buttonStyle}", <#if input$BOOL_smooth?has_content>${input$BOOL_smooth}<#else>${field$smooth?lower_case}</#if>)
.withLayout(<#if input$borderWidth?has_content>${opt.toInt(input$borderWidth)}<#else>1</#if>, <#if input$paddingX?has_content>${opt.toInt(input$paddingX)}<#else>4</#if>, <#if input$paddingY?has_content>${opt.toInt(input$paddingY)}<#else>3</#if>, "${field$align!"left"}", <#if input$BOOL_shadow?has_content>${input$BOOL_shadow}<#else>${(field$shadow!"false")?lower_case}</#if>)
<#if input$PART_text?has_content>.withPart("text", ${input$PART_text})</#if>
<#if input$PART_placeholder?has_content>.withPart("placeholder", ${input$PART_placeholder})</#if>
<#if input$PART_label?has_content>.withPart("label", ${input$PART_label})</#if>
<#if input$PART_indicator?has_content>.withPart("indicator", ${input$PART_indicator})</#if>
<#if input$PART_list?has_content>.withPart("list", ${input$PART_list})</#if>
<#if input$PART_selection?has_content>.withPart("selection", ${input$PART_selection})</#if>
