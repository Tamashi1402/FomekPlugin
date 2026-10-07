<#include "redwires_plugin_utils.ftl">
<#assign splitTxt = input$2split>
<#if (field$stringliteral!"TRUE") == "TRUE">
<#assign splitTxt = "Pattern.quote(${splitTxt})">
</#if>
String _splitContent${index} = ${splitTxt};
<#if (field$includedelimiter!"FALSE") == "TRUE">
<#assign splitTxt = "\"(?=\" + _splitContent${index} + \")|(?<=\" + _splitContent${index} + \")\"">
<#else>
<#assign splitTxt = "_splitContent${index}">
</#if>
String _toSplit${index} = ${input$text};
String[] _array${index} = _toSplit${index}.split(${splitTxt});
if (_array${index}.length != 0) {
    for (String stringiterator : _array${index}) {
        ${statement$foreach}
    }
}<#if field$usewholestring == "TRUE"> else {
    String stringiterator = _toSplit${index};
    for (int _yourmother${index} = 0; _yourmother${index} < 1; _yourmother${index}++) {
        ${statement$foreach}
    }
}
</#if>