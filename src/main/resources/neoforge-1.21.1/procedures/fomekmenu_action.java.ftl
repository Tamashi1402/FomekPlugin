<#assign fomek = w.getWorkspace().getWorkspaceSettings().getModElementsPackage() + ".api.guisystems">
if (${fomek}.VirtualGui.consumeAction(${input$id})) {
    if (!${fomek}.VirtualGui.isActionCancelled()) {
<#if statement$DO?has_content>
${statement$DO}
</#if>
    }
    ${fomek}.VirtualGui.clearActionCancel();
}