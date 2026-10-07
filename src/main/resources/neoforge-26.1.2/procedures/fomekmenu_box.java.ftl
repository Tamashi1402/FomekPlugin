<#assign fomek = w.getWorkspace().getWorkspaceSettings().getModElementsPackage() + ".api.guisystems">
new ${fomek}.Box(${opt.toFloat(input$x1)}, ${opt.toFloat(input$y1)}, ${opt.toFloat(input$x2)}, ${opt.toFloat(input$y2)})
