<#if input$scale == "/*@int*/1">
  (Minecraft.getInstance().font.width(${input$texts}))
<#else>
  (Minecraft.getInstance().font.width(${input$texts}) * ${input$scale})
</#if>
