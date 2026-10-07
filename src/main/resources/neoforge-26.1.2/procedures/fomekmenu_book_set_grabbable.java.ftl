<#assign fomek = w.getWorkspace().getWorkspaceSettings().getModElementsPackage() + ".api.guisystems">
${fomek}.Book.byId(${input$id}).setGrabbable(${opt.toFloat(input$inset)}, new String[]{<#if input_list$mt_checks?has_content><#list input_list$mt_checks as c>${c}<#if c_has_next>, </#if></#list></#if>});
