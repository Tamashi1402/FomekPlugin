<#assign fomek = w.getWorkspace().getWorkspaceSettings().getModElementsPackage() + ".api.guisystems">
${fomek}.VirtualGui.setElementPosition(${input$id}, ${opt.toFloat(input$x)}, ${opt.toFloat(input$y)});
