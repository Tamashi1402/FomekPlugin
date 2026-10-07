<#assign fomek = w.getWorkspace().getWorkspaceSettings().getModElementsPackage() + ".api.guisystems">
${fomek}.StudioRuntime.push("${field$STUDIO_KEY}" + ":" + String.valueOf(${input$id}), <#if input$STUDIO_NORMAL_STYLE?has_content>${input$STUDIO_NORMAL_STYLE}<#else>null</#if>, <#if input$STUDIO_HOVER_STYLE?has_content>${input$STUDIO_HOVER_STYLE}<#else>null</#if>, <#if input$STUDIO_HELD_STYLE?has_content>${input$STUDIO_HELD_STYLE}<#else>null</#if>, <#if input$STUDIO_CLICK_STYLE?has_content>${input$STUDIO_CLICK_STYLE}<#else>null</#if>);
try {
<#assign fomek = w.getWorkspace().getWorkspaceSettings().getModElementsPackage() + ".api.guisystems">
{
final ${fomek}.Book __fomekBook = ${fomek}.Book.current();
final ${fomek}.BookPage __fomekPage = __fomekBook.page(${input$id});
<#-- Incoming (destination) spread: rendered OFFSCREEN into the flip's
     "next" snapshot so the back of the turning sheet / the revealed
     underside have real content without showing that content on the
     live book. 0,0 of the panel is still the page edge. -->
if (__fomekBook.shouldRenderIncoming(__fomekPage, false)) {
if (${fomek}.PageFlip.beginIncomingPass()) {
try {
${fomek}.PageFlip.drawPageTexture(__fomekBook.getLeftFaceTexture(__fomekPage), __fomekBook.getRightBackgroundTexture(__fomekPage), __fomekBook.getRightX(), __fomekBook.getTopY(), __fomekBook.getPageWidth(), __fomekBook.getBottomY() - __fomekBook.getTopY(), false);
${fomek}.VirtualGui.beginPagePanel(__fomekBook.sidePanelId(__fomekPage, false) + "_in", __fomekBook.getRightX(), __fomekBook.getTopY(), __fomekBook.getRightX() + __fomekBook.getPageWidth(), __fomekBook.getBottomY(), false);
<#if statement$LEFT?has_content>
${statement$LEFT}
</#if>${fomek}.VirtualGui.endPanel();
} finally {
${fomek}.PageFlip.endIncomingPass();
}
}
}
if (__fomekBook.shouldRenderIncoming(__fomekPage, true)) {
if (${fomek}.PageFlip.beginIncomingPass()) {
try {
${fomek}.PageFlip.drawPageTexture(__fomekBook.getRightFaceTexture(__fomekPage), __fomekBook.getLeftBackgroundTexture(__fomekPage), __fomekBook.getLeftX(), __fomekBook.getTopY(), __fomekBook.getPageWidth(), __fomekBook.getBottomY() - __fomekBook.getTopY(), true);
${fomek}.VirtualGui.beginPagePanel(__fomekBook.sidePanelId(__fomekPage, true) + "_in", __fomekBook.getLeftX(), __fomekBook.getTopY(), __fomekBook.getLeftX() + __fomekBook.getPageWidth(), __fomekBook.getBottomY(), false);
<#if statement$RIGHT?has_content>
${statement$RIGHT}
</#if>${fomek}.VirtualGui.endPanel();
} finally {
${fomek}.PageFlip.endIncomingPass();
}
}
}
<#-- v2.10.26: OUTGOING (from-spread) pass — the side the sheet lifts
     OFF is also rendered OFFSCREEN, into the clean `out` buffer (RGBA,
     cleared to transparent). The flip composite used to replay the from
     side from a blit of the live framebuffer, which dragged the captured
     world/GUI background along with the art (ghost/stale-slide bugs).
     Mirrors the incoming branches exactly: same faces, same positions,
     same mirroring — just the FROM spread and "_out" panel ids. -->
if (__fomekBook.shouldRenderOutgoing(__fomekPage, false)) {
if (${fomek}.PageFlip.beginOutgoingPass()) {
try {
${fomek}.PageFlip.drawPageTexture(__fomekBook.getLeftFaceTexture(__fomekPage), __fomekBook.getRightBackgroundTexture(__fomekPage), __fomekBook.getRightX(), __fomekBook.getTopY(), __fomekBook.getPageWidth(), __fomekBook.getBottomY() - __fomekBook.getTopY(), false);
${fomek}.VirtualGui.beginPagePanel(__fomekBook.sidePanelId(__fomekPage, false) + "_out", __fomekBook.getRightX(), __fomekBook.getTopY(), __fomekBook.getRightX() + __fomekBook.getPageWidth(), __fomekBook.getBottomY(), false);
<#if statement$LEFT?has_content>
${statement$LEFT}
</#if>${fomek}.VirtualGui.endPanel();
} finally {
${fomek}.PageFlip.endOutgoingPass();
}
}
}
if (__fomekBook.shouldRenderOutgoing(__fomekPage, true)) {
if (${fomek}.PageFlip.beginOutgoingPass()) {
try {
${fomek}.PageFlip.drawPageTexture(__fomekBook.getRightFaceTexture(__fomekPage), __fomekBook.getLeftBackgroundTexture(__fomekPage), __fomekBook.getLeftX(), __fomekBook.getTopY(), __fomekBook.getPageWidth(), __fomekBook.getBottomY() - __fomekBook.getTopY(), true);
${fomek}.VirtualGui.beginPagePanel(__fomekBook.sidePanelId(__fomekPage, true) + "_out", __fomekBook.getLeftX(), __fomekBook.getTopY(), __fomekBook.getLeftX() + __fomekBook.getPageWidth(), __fomekBook.getBottomY(), false);
<#if statement$RIGHT?has_content>
${statement$RIGHT}
</#if>${fomek}.VirtualGui.endPanel();
} finally {
${fomek}.PageFlip.endOutgoingPass();
}
}
}
if (__fomekBook.shouldRender(__fomekPage, false)) {
// v2.10.21: the sheet lies on the RIGHT half — this is where a page's
// LEFT side (the side you read first) shows. Draw the left face art on
// the right half and run the LEFT statements inside a panel bound to it.
${fomek}.PageFlip.drawPageTexture(__fomekBook.getLeftFaceTexture(__fomekPage), __fomekBook.getRightBackgroundTexture(__fomekPage), __fomekBook.getRightX(), __fomekBook.getTopY(), __fomekBook.getPageWidth(), __fomekBook.getBottomY() - __fomekBook.getTopY(), false);
${fomek}.VirtualGui.beginPagePanel(__fomekBook.sidePanelId(__fomekPage, false), __fomekBook.getRightX(), __fomekBook.getTopY(), __fomekBook.getRightX() + __fomekBook.getPageWidth(), __fomekBook.getBottomY(), __fomekBook.acceptPageInput());
<#if statement$LEFT?has_content>
${statement$LEFT}
</#if>${fomek}.VirtualGui.endPanel();
}
if (__fomekBook.shouldRender(__fomekPage, true)) {
// The sheet has flipped over to the LEFT half: its RIGHT side (the
// back of the page) is face-up there.
${fomek}.PageFlip.drawPageTexture(__fomekBook.getRightFaceTexture(__fomekPage), __fomekBook.getLeftBackgroundTexture(__fomekPage), __fomekBook.getLeftX(), __fomekBook.getTopY(), __fomekBook.getPageWidth(), __fomekBook.getBottomY() - __fomekBook.getTopY(), true);
${fomek}.VirtualGui.beginPagePanel(__fomekBook.sidePanelId(__fomekPage, true), __fomekBook.getLeftX(), __fomekBook.getTopY(), __fomekBook.getLeftX() + __fomekBook.getPageWidth(), __fomekBook.getBottomY(), __fomekBook.acceptPageInput());
<#if statement$RIGHT?has_content>
${statement$RIGHT}
</#if>${fomek}.VirtualGui.endPanel();
}
}

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
