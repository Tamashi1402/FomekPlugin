<#assign fomek = w.getWorkspace().getWorkspaceSettings().getModElementsPackage() + ".api.guisystems">
${fomek}.Book.byId(${input$id}).setCurrentPage((int) (${opt.toInt(input$page)}));
