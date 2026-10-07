<#assign fomek = w.getWorkspace().getWorkspaceSettings().getModElementsPackage() + ".api.guisystems">
new ${fomek}.PanelAttribute.Resize(
    new String[]{<#if input_list$mt_checks?has_content><#list input_list$mt_checks as c>${c}<#if c_has_next>, </#if></#list></#if>},
    <#if input_list$mt_min_resize_x?has_content>${input_list$mt_min_resize_x?first}<#else>-1</#if>f,
    <#if input_list$mt_min_resize_y?has_content>${input_list$mt_min_resize_y?first}<#else>-1</#if>f,
    <#if input_list$mt_max_resize_x?has_content>${input_list$mt_max_resize_x?first}<#else>-1</#if>f,
    <#if input_list$mt_max_resize_y?has_content>${input_list$mt_max_resize_y?first}<#else>-1</#if>f,
    <#if input_list$mt_highlight?has_content>${input_list$mt_highlight?first}<#else>true</#if>,
    <#if input_list$mt_highlight_color?has_content>${input_list$mt_highlight_color?first}<#else>Integer.MIN_VALUE</#if>,
    <#if input_list$mt_corner_only?has_content>${input_list$mt_corner_only?first}<#else>false</#if>,
    <#if input_list$mt_aspect_ratio?has_content>${input_list$mt_aspect_ratio?first}<#else>false</#if><#if input_list$mt_resize_bounds?has_content>, true, ${input_list$mt_resize_bounds?first}<#else>, false, 0f, 0f, 0f, 0f</#if>
)
