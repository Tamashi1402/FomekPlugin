<#assign fomek = w.getWorkspace().getWorkspaceSettings().getModElementsPackage() + ".api.guisystems">
${fomek}.VirtualGui.setScrollOffset(${input$id}, ${opt.toFloat(input$value)});
