<#assign fomek = w.getWorkspace().getWorkspaceSettings().getModElementsPackage() + ".api.guisystems">
${fomek}.Book.spineOffset(${input$id}, (int) (${opt.toInt(input$spine)}));
