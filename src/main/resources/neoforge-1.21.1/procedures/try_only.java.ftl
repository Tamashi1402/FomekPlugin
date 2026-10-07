try {
    ${statement$attempt}
} catch (<#if (field$throwable!"FALSE") == "FALSE">Exception<#else>Throwable</#if> _exception) {
    <#if field$printerror == "TRUE">_exception.printStackTrace();</#if>
}