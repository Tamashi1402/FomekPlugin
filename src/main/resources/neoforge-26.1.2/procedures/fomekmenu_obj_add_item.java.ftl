<#assign fomek = w.getWorkspace().getWorkspaceSettings().getModElementsPackage() + ".api.guisystems">
${fomek}.StudioRuntime.push("${field$STUDIO_KEY}" + ":" + String.valueOf(${input$id}), <#if input$STUDIO_NORMAL_STYLE?has_content>${input$STUDIO_NORMAL_STYLE}<#else>null</#if>, <#if input$STUDIO_HOVER_STYLE?has_content>${input$STUDIO_HOVER_STYLE}<#else>null</#if>, <#if input$STUDIO_HELD_STYLE?has_content>${input$STUDIO_HELD_STYLE}<#else>null</#if>, <#if input$STUDIO_CLICK_STYLE?has_content>${input$STUDIO_CLICK_STYLE}<#else>null</#if>);
try {
<#assign fomek = w.getWorkspace().getWorkspaceSettings().getModElementsPackage() + ".api.guisystems">
<#if input_list$mt_checks?has_content>if (<#list input_list$mt_checks as c>${fomek}.VirtualGui.getCheckResult(${c})<#if c_has_next> && </#if></#list>) {
</#if>${input$menuObject}.addItem(${input$id}, "${input$item}", ${opt.toInt(input$x)}, ${opt.toInt(input$y)}, <#if input_list$stick?has_content>${input_list$stick?first}<#else>false</#if>, <#if input_list$collision?has_content>${input_list$collision?first}<#else>false</#if>);
<#if input_list$mt_checks?has_content>}
</#if>
<#if statement$STUDIO_HOVER?has_content>
if (${fomek}.StudioRuntime.event("hover")) {
${statement$STUDIO_HOVER}
}
</#if>
<#if statement$STUDIO_CLICK?has_content>
if (${fomek}.StudioRuntime.event("click")) {
${statement$STUDIO_CLICK}
}
</#if>
<#if statement$STUDIO_CHECK?has_content>
if (${fomek}.StudioRuntime.event("check")) {
${statement$STUDIO_CHECK}
}
</#if>
<#if statement$STUDIO_UPDATE?has_content>
if (${fomek}.StudioRuntime.event("update")) {
${statement$STUDIO_UPDATE}
}
</#if>
} finally {
${fomek}.StudioRuntime.pop();
}
