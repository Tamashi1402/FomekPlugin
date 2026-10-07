<#assign index = ((customBlockIndex)!(cbi))!"index isnt usable in this context">
<#macro ifHead>
    <#if head??>
        <@head>
            <#nested>
        </@head>
    <#else>
        <#nested>
    </#if>
</#macro>
<#macro ifTail>
    <#if tail??>
        <@tail>
            <#nested>
        </@tail>
    <#else>
        <#nested>
    </#if>
</#macro>
<#macro templateOrFallback files...>
    <#if addTemplate??>
        <#list files as file>
            <@addTemplate file=("utils/" + file + ".java.ftl")/>
        </#list>
    <#else>
        new Object() {
            <#list files as file>
                <#include ("utils/" + file + ".java.ftl")>
            </#list>
        }.
    </#if>
</#macro>