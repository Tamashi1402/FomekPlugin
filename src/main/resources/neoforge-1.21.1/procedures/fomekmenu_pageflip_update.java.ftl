<#assign fomek = w.getWorkspace().getWorkspaceSettings().getModElementsPackage() + ".api.guisystems">
${fomek}.PageFlip.update(${opt.toFloat(input$x)}, ${opt.toFloat(input$y)}, (int)(${opt.toInt(input$pageWidth)}), (int)(${opt.toInt(input$height)}));
