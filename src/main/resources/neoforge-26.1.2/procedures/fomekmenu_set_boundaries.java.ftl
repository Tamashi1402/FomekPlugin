<#assign fomek = w.getWorkspace().getWorkspaceSettings().getModElementsPackage() + ".api.guisystems">
${fomek}.VirtualGui.setBoundaries(${input$box});
<#if statement$DO?has_content>
${statement$DO}
</#if>
${fomek}.VirtualGui.clearBoundaries();
