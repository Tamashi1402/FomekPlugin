<#if input$scale == "/*@int*/1">
  (Minecraft.getInstance().font.lineHeight)
<#else>
  (Minecraft.getInstance().font.lineHeight * ${input$scale})
</#if>
