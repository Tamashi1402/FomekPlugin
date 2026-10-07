<#if input$bewrl?has_content>
{
    java.util.List<${package}.api.render.BEWRL.Model.Part> _bewrlIterList =
        new java.util.ArrayList<>(${input$bewrl}.getParts());
    for (${package}.api.render.BEWRL.Model.Part _bewrlIteratorPart : _bewrlIterList) {
        ${statement$do}
    }
}
</#if>
