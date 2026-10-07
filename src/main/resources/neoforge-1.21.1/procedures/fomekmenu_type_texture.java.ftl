<#assign fomek = w.getWorkspace().getWorkspaceSettings().getModElementsPackage() + ".api.guisystems">
new ${fomek}.PanelType.Texture(${input$texture}, ${opt.toInt(input$tint)})
