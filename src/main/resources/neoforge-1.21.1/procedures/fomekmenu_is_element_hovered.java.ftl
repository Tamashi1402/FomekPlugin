<#assign fomek = w.getWorkspace().getWorkspaceSettings().getModElementsPackage() + ".api.guisystems">
${fomek}.VirtualGui.isElementHovered(${input$id}, ${fomek}.GuiState.getMouseX(${input$entity}), ${fomek}.GuiState.getMouseY(${input$entity}))
