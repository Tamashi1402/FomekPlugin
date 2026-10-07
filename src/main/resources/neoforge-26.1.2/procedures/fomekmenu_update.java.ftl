<#assign fomek = w.getWorkspace().getWorkspaceSettings().getModElementsPackage() + ".api.guisystems">
if (${fomek}.UpdateManager.shouldRun("${field$frequency}")) {
<#if statement$DO?has_content>
${statement$DO}
</#if>
}