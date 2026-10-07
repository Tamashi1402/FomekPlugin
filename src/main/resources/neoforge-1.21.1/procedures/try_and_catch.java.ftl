<#include "redwires_plugin_utils.ftl">
try {
    ${statement$attempt}
} catch (<#if (field$throwable!"FALSE") == "FALSE">Exception<#else>Throwable</#if> _exception${index}) {
    <#if field$printerror == "TRUE">_exception${index}.printStackTrace();</#if>
    ${statement$failure}
}