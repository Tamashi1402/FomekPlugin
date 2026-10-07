<#assign fomek = w.getWorkspace().getWorkspaceSettings().getModElementsPackage() + ".api.guisystems">
new ${fomek}.PanelAttribute.RightClick(${input$action}, new String[]{<#if input_list$checks?has_content><#list input_list$checks as c>${c}<#if c_has_next>, </#if></#list></#if>})
