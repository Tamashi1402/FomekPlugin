<#include "mcitems.ftl">
${package}.api.render.RenderAPI.renderItem(${mappedMCItemToItemStackCode(input$item, 1)}, ${opt.toFloat(input$x)}, ${opt.toFloat(input$y)}, ${opt.toFloat(input$z)}, ${opt.toFloat(input$yaw)}, ${opt.toFloat(input$pitch)}, ${opt.toFloat(input$roll)}, ${opt.toFloat(input$scale)}, ${input$glowing});
