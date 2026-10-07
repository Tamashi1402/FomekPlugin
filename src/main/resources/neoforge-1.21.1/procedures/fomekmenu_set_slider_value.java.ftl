<#assign fomek = w.getWorkspace().getWorkspaceSettings().getModElementsPackage() + ".api.guisystems">
${fomek}.VirtualGui.setSliderValue(${input$id}, ${opt.toFloat(input$value)});
