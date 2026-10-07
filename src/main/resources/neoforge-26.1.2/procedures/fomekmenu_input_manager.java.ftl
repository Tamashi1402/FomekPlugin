<#assign fomek = w.getWorkspace().getWorkspaceSettings().getModElementsPackage() + ".api.guisystems">
${fomek}.InputManager.register(${input$id}, "${field$frequency}");
${fomek}.InputManager.setCurrentManagerId(${input$id});
<#if statement$DO?has_content>
${statement$DO}
</#if>