<#include "mcelements.ftl">
(world.getBiome(${toBlockPos(input$x,input$y,input$z)}).value().coldEnoughToSnow(${toBlockPos(input$x,input$y,input$z)}))