<#include "mcelements.ftl">
if (world.getLevelData() instanceof ServerLevelData _levelData${cbi})
    _levelData${cbi}.setGameTime(${opt.toInt(input$time)});