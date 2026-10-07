<#assign fomek = w.getWorkspace().getWorkspaceSettings().getModElementsPackage() + ".api.guisystems">
${fomek}.VirtualGui.beginCheck(${input$id});
<#if statement$DO?has_content>
${statement$DO}
</#if>
${fomek}.VirtualGui.endCheck();
