<#include "mcelements.ftl">
(world.getBlockState(${toBlockPos(input$x,input$y,input$z)}).getFluidState().getHeight(world, (${toBlockPos(input$x,input$y,input$z)})))
