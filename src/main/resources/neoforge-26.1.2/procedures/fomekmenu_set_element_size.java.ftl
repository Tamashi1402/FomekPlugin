<#assign fomek = w.getWorkspace().getWorkspaceSettings().getModElementsPackage() + ".api.guisystems">
${fomek}.VirtualGui.setElementSize(${input$id}, ${opt.toFloat(input$w)}, ${opt.toFloat(input$h)});
