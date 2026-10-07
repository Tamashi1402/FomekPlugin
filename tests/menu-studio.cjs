// Regression test for MCreator 2026.1 (Blockly 9.2.0).
// Requires Playwright. Set BLOCKLY_DIST to the extracted jsdist from MCreator lib/blockly.jar.
// Optionally set CHROME_PATH to a local Chrome executable, otherwise use Playwright Chromium.
const fs = require('fs');
const path = require('path');
const assert = require('assert/strict');
const { chromium } = require('playwright');
const resources = path.resolve(__dirname, '../src/main/resources');
if (!process.env.BLOCKLY_DIST) throw new Error('Set BLOCKLY_DIST to MCreator Blockly jsdist');
(async () => {
const browser = await chromium.launch({headless:true, ...(process.env.CHROME_PATH ? {executablePath:process.env.CHROME_PATH} : {})});
const page = await browser.newPage({viewport:{width:1500,height:1000}});
const errors=[]; page.on('pageerror', e=>errors.push(e.message));
try {
await page.setContent('<html><body style="margin:0"><div id="blockly-div" style="position:relative;width:1500px;height:1000px"></div></body></html>');
for (const file of ['blockly_compressed.js','msg/en.js','blocks_compressed.js']) await page.addScriptTag({path:path.resolve(process.env.BLOCKLY_DIST,file)});
await page.evaluate(()=>{window.workspace=Blockly.inject('blockly-div',{toolbox:'<xml><category name="Menu"></category></xml>',renderer:'geras',scrollbars:true,sounds:false});});
for (const file of ['fomekmenu.js','fomekmenu_studio.js','fomekmenu_studio_preview.js']) await page.addScriptTag({path:path.resolve(path.join(resources, 'blockly/js'),file)});
const defs = fs.readdirSync(path.join(resources, 'procedures')).filter(f=>f.startsWith('fomekmenu_')&&f.endsWith('.json')).map(f=>({type:f.slice(0,-5),...JSON.parse(fs.readFileSync(path.join(resources, 'procedures', f)))}));
// ExternalBlockLoader (MCreator 2026.1) replaces message0 with the language
// bundle before handing the block JSON to Blockly. Test that real load path.
function blockMessages(file) {
 const messages=new Map();
 for(const line of fs.readFileSync(file,'utf8').split(/\r?\n/)) {
  const match=line.match(/^blockly\.block\.(\w+)=(.*)$/);
  if(match)messages.set(match[1],match[2].replace(/\\u([0-9a-f]{4})|\\(.)/gi,(_,hex,char)=>hex?String.fromCharCode(parseInt(hex,16)):char));
 }
 return messages;
}
const englishMessages=blockMessages(path.join(resources,'lang/texts.properties'));
for(const def of defs)if(englishMessages.has(def.type))def.message0=englishMessages.get(def.type);
const hosts=defs.filter(d=>d.extensions?.includes('fomekmenu_studio_icon')).map(d=>d.type);
for (const file of fs.readdirSync(path.join(resources,'lang')).filter(f=>/^texts.*\.properties$/.test(f))) {
 const messages=new Map([...englishMessages,...blockMessages(path.join(resources,'lang',file))]);
 for (const type of hosts) {
  const def=defs.find(d=>d.type===type);
  const message=messages.get(type) || def.message0;
  const indexes=[...message.matchAll(/%(\d+)/g)].map(m=>Number(m[1])).sort((a,b)=>a-b);
  assert.deepEqual(indexes,def.args0.map((_,i)=>i+1),file+': '+type+' localized arguments');
 }
}
console.log('PASS: MCreator language-bundle override matches arguments in all 20 host definitions and every bundled locale');
await page.evaluate(({defs,hosts})=>{
 Blockly.defineBlocksWithJsonArray(defs);
 workspace.updateToolbox('<xml><category name="Menu">'+hosts.map(type=>'<block type="'+type+'"></block>').join('')+'</category></xml>');
 workspace.getToolbox().selectItemByPosition(0);
 window.testHosts=hosts;
}, {defs,hosts});
const flyout=await page.evaluate(()=>workspace.getFlyout().getWorkspace().getTopBlocks(false).map(b=>({type:b.type,icons:b.getIcons().length,pencil:!!b.mutator.iconGroup_.querySelector('path'),rect:b.mutator.iconGroup_.querySelector('rect').getAttribute('width'),extra:!!b.getInput('FOMEK_STUDIO')})));
assert.equal(flyout.length,20); for(const b of flyout){assert.equal(b.icons,1);assert.equal(b.rect,'16');assert.equal(b.pencil,true);assert.equal(b.extra,false);}
console.log('PASS: identical native-size pencil icons on all 20 flyout blocks');
// Create every host, and its insertion marker using the actual Blockly machinery.
const shapes=await page.evaluate(()=>testHosts.map(type=>{
 const b=workspace.newBlock(type);b.initSvg();b.render();
 const manager=new Blockly.InsertionMarkerManager(b);
 const marker=manager.createMarkerBlock(b);
 const match=JSON.stringify(b.inputList.map(i=>i.name))===JSON.stringify(marker.inputList.map(i=>i.name));
 Blockly.Events.disable(); try {marker.dispose(false);manager.dispose();} finally {Blockly.Events.enable();} b.dispose(false);return {type,match};
}));
for(const s of shapes)assert(s.match,s.type);
console.log('PASS: drag insertion-marker shape for all 20 host types');
// Real pointer drag from toolbox, then drag the resulting block again.
const fly=await page.locator('.blocklyFlyout [data-id]').first().boundingBox();
await page.mouse.move(fly.x+90,fly.y+12);await page.mouse.down();await page.mouse.move(720,120,{steps:20});await page.mouse.up();await page.waitForTimeout(500);
assert.equal(await page.evaluate(()=>workspace.getTopBlocks(false).length),1);
await page.evaluate(()=>{window.host=workspace.getTopBlocks(false)[0];host.getSvgRoot().setAttribute("data-test", "main-host");});
const before=await page.evaluate(()=>({x:host.getRelativeToSurfaceXY().x,y:host.getRelativeToSurfaceXY().y}));
const hostId=await page.evaluate(()=>host.id);
const body=await page.locator('[data-test="main-host"] > .blocklyPath').boundingBox();
await page.mouse.move(body.x+95,body.y+12);await page.mouse.down();await page.mouse.move(body.x+265,body.y+102,{steps:20});await page.mouse.up();await page.waitForTimeout(400);
const after=await page.evaluate(()=>({x:host.getRelativeToSurfaceXY().x,y:host.getRelativeToSurfaceXY().y}));
assert(after.x-before.x>100,JSON.stringify({before,after}));assert.equal(await page.locator('.fomek-studio.open').count(),0);
console.log('PASS: drag from toolbox and drag placed block without opening editor');
await page.locator('[data-test="main-host"] > .blocklyIconGroup').click();
assert.equal(await page.locator('.fomek-studio.open').count(),1);
assert.equal(await page.locator('.fomek-studio .blocklyTrash,.fomek-studio .blocklyZoom').count(),0);
console.log('PASS: pencil opens editor, no trashcan or zoom controls');
await page.click('#fomek-studio-close');
// Save/load hidden options, confirm native IF still has its gear.
const roundtrip=await page.evaluate(()=>{
 const xml=Blockly.Xml.textToDom('<block type="fomekmenu_button"><mutation items="checks,stick" count="2"></mutation><value name="checks0"><block type="text"><field name="TEXT">persisted</field></block></value><value name="stick0"><block type="logic_boolean"><field name="BOOL">FALSE</field></block></value></block>');
 const b=Blockly.Xml.domToBlock(xml,workspace);
 const clone=Blockly.Xml.domToBlock(Blockly.Xml.blockToDom(b),workspace);
 const result={hidden:!clone.getInput('checks0').isVisible(),text:clone.getInputTargetBlock('checks0').getFieldValue('TEXT'),bool:clone.getInputTargetBlock('stick0').getFieldValue('BOOL')};
 const control=workspace.newBlock('controls_if');control.initSvg();control.render();result.gear=!!control.mutator.iconGroup_.querySelector('circle');
 window.loadedHost=clone; return result;
});
assert.deepEqual(roundtrip,{hidden:true,text:'persisted',bool:'FALSE',gear:true});
await page.evaluate(()=>window.__fomekStudioOpen(loadedHost));
await page.waitForTimeout(450);
assert.equal(await page.locator('.fomek-studio .blocklyTrash,.fomek-studio .blocklyZoom').count(),0);
await page.evaluate(()=>{
 const popupWs=Blockly.Workspace.getAll().find(w=>w.rendered && w.getInjectionDiv && w.getInjectionDiv().closest('#fomek-studio-ws') && !w.isFlyout);
 const text=popupWs.getAllBlocks(false).find(b=>b.type==='text' && b.getFieldValue('TEXT')==='persisted');
 text.setFieldValue('edited in studio','TEXT');
});
await page.waitForTimeout(500);
await page.click('#fomek-studio-close');
assert.equal(await page.evaluate(()=>loadedHost.getInputTargetBlock('checks0').getFieldValue('TEXT')),'edited in studio');
console.log('PASS: options survive XML reload and editor editing; native IF gear unchanged');
await page.evaluate(()=>{loadedHost.moveBy(600,100);loadedHost.getSvgRoot().setAttribute('data-test','loaded-host');workspace.scrollCenter();});
const loadedBefore=await page.evaluate(()=>loadedHost.getRelativeToSurfaceXY().x);
const loadedBox=await page.locator('[data-test="loaded-host"] > .blocklyPath').boundingBox();
await page.mouse.move(loadedBox.x+90,loadedBox.y+12);await page.mouse.down();await page.mouse.move(loadedBox.x+210,loadedBox.y+72,{steps:20});await page.mouse.up();await page.waitForTimeout(400);
assert(await page.evaluate(()=>loadedHost.getRelativeToSurfaceXY().x)>loadedBefore+70);
assert.equal(await page.locator('.fomek-studio.open').count(),0);
assert.equal(await page.evaluate(()=>loadedHost.getInputTargetBlock('checks0').getFieldValue('TEXT')),'edited in studio');
console.log('PASS: placed button drags after editing, with saved hidden values attached');

// Match MCreator's item value block for the standalone browser fixture.
await page.evaluate(()=>{
 Blockly.Blocks.mcitem_all={init:function(){this.appendDummyInput().appendField(new Blockly.FieldTextInput('Items.STICK'),'value');this.setOutput(true,'MCItem');this.setColour(30);}};
 window.sourceDefs={};
});
await page.evaluate(defs=>{for(const d of defs)sourceDefs[d.type]=d;},defs);
const expected={fomekmenu_button:['id','x','y'],fomekmenu_slider:['id','x','y'],fomekmenu_panel:['id','atX','atY','CHILDREN'],fomekmenu_scroll_view:['id','CHILDREN'],fomekmenu_book:['id','x','y','CHILDREN'],fomekmenu_book_page:['id','LEFT','RIGHT'],fomekmenu_obj_add_item:['menuObject','item','x','y'],fomekmenu_obj_add_rect:['menuObject'],fomekmenu_obj_add_text:['menuObject','x','y'],fomekmenu_obj_add_texture:['menuObject','x','y'],fomekmenu_obj_render:['menuObject','id','x','y'],fomekmenu_render_item:['item','x','y'],fomekmenu_render_rect:['x','y'],fomekmenu_render_rect_outline:['x','y'],fomekmenu_render_text:['x','y'],fomekmenu_render_texture:['x','y']};
for(const t of ['inputfield','text_area','checkbox','dropdown'])expected['fomekmenu_'+t]=['id','x','y'];
for(const type of hosts){
 const state=await page.evaluate(type=>{
  workspace.clear();const def=sourceDefs[type];window.host=Blockly.Xml.domToBlock(Blockly.Xml.textToDom('<block type="'+type+'">'+def.mcreator.toolbox_init.join('')+'</block>'),workspace);
  const visible=host.inputList.filter(i=>i.isVisible()).map(i=>i.name).filter(Boolean);
  window.__fomekStudioOpen(host);
  window.pw=Blockly.Workspace.getAll().find(w=>w.rendered&&!w.isFlyout&&w.getInjectionDiv().closest('#fomek-studio-ws'));
  window.cap=pw.getTopBlocks(false).find(b=>b.type.startsWith('fomekmenu_studio_settings_'));
  return {visible,inputs:cap.inputList.map(i=>i.name).filter(Boolean),rows:cap.getInputTargetBlock('SETTINGS')!==null};
 },type);
 assert.deepEqual(state.visible,expected[type]);assert.deepEqual(state.inputs,['SETTINGS','STACK','ACTIONS','STYLE']);assert(state.rows || ['fomekmenu_book_page','fomekmenu_render_item'].includes(type),type);
 await page.click('#fomek-studio-close');
 const saved=await page.evaluate(()=>host.getFieldValue('STUDIO_LAYOUT'));assert(saved.includes('fomekmenu_studio_settings_'));
 await page.evaluate(()=>window.__fomekStudioOpen(host));await page.click('#fomek-studio-close');
 assert.equal(await page.evaluate(()=>host.getFieldValue('STUDIO_LAYOUT')),saved,type+' layout no-op round trip');
}
console.log('PASS: all 20 elements have only four section sockets; separate defaults survive reopening');
await page.evaluate(()=>{
 workspace.clear();workspace.getToolbox().clearSelection();workspace.scroll(0,0);
 const def=sourceDefs.fomekmenu_inputfield;host=Blockly.Xml.domToBlock(Blockly.Xml.textToDom('<block type="fomekmenu_inputfield">'+def.mcreator.toolbox_init.join('')+'</block>'),workspace);window.__fomekStudioOpen(host);
 pw=Blockly.Workspace.getAll().find(w=>w.rendered&&!w.isFlyout&&w.getInjectionDiv().closest('#fomek-studio-ws'));cap=pw.getTopBlocks(false).find(b=>b.type.startsWith('fomekmenu_studio_settings_'));
 pw.getAllBlocks(false).find(b=>b.type.endsWith('_maxLength')&&b.type.startsWith('fomekmenu_studio_setting_')).dispose(true);
 pw.getAllBlocks(false).find(b=>b.type.endsWith('_initial')&&b.type.startsWith('fomekmenu_studio_setting_')).dispose(true);
});
await page.click('#fomek-studio-close');
assert.equal(await page.evaluate(()=>host.getInputTargetBlock('maxLength').getFieldValue('NUM')),2147483647);
assert.equal(await page.evaluate(()=>host.getInputTargetBlock('initial').getFieldValue('TEXT')),'');
await page.evaluate(()=>window.__fomekStudioOpen(host));
assert.equal(await page.evaluate(()=>Blockly.Workspace.getAll().find(w=>w.rendered&&!w.isFlyout&&w.getInjectionDiv().closest('#fomek-studio-ws')).getAllBlocks(false).some(b=>b.type.endsWith('_maxLength'))),false);
await page.click('#fomek-studio-close');console.log('PASS: deleted settings remain absent; no default text and unlimited characters reach generated inputs');

await page.evaluate(()=>{
 workspace.clear();const def=sourceDefs.fomekmenu_button;host=Blockly.Xml.domToBlock(Blockly.Xml.textToDom('<block type="fomekmenu_button">'+def.mcreator.toolbox_init.join('')+'</block>'),workspace);host.moveBy(20,30);workspace.scroll(0,0);
 __fomekStudioOpen(host);pw=Blockly.Workspace.getAll().find(w=>w.rendered&&!w.isFlyout&&w.getInjectionDiv().closest('#fomek-studio-ws'));cap=pw.getTopBlocks(false).find(b=>b.type.startsWith('fomekmenu_studio_settings_'));
 window.row=pw.getAllBlocks(false).find(b=>b.type.endsWith('_w')&&b.type.startsWith('fomekmenu_studio_setting_'));row.getInputTargetBlock('VALUE').dispose();pw.centerOnBlock(row.id);
 window.shared=workspace.newBlock('math_number');shared.setFieldValue(42,'NUM');shared.initSvg();shared.render();shared.moveBy(70,860);shared.getSvgRoot().setAttribute('data-test','shared');
});
async function socket(){return page.evaluate(()=>{const c=row.getInput('VALUE').connection,r=pw.getInjectionDiv().getBoundingClientRect(),o=pw.getOriginOffsetInPixels();return {x:r.left+o.x+c.x*pw.scale+12,y:r.top+o.y+c.y*pw.scale+4};});}
let point=await socket(),box=await page.locator('[data-test="shared"] > .blocklyPath').boundingBox();
await page.mouse.move(box.x+box.width-5,box.y+box.height/2);await page.mouse.down();await page.mouse.move(point.x,point.y,{steps:30});
assert.equal(await page.evaluate(()=>pw.getTopBlocks(false).some(b=>b.type==='math_number'&&b.getFieldValue('NUM')===42)),true,'copy is visible INSIDE target before mouse release');
await page.mouse.up();await page.waitForTimeout(450);
assert.equal(await page.evaluate(()=>row.getInputTargetBlock('VALUE')?.getFieldValue('NUM')),42);
assert.equal(await page.evaluate(()=>shared.isDisposed()),true);
await page.evaluate(()=>{const b=row.getInputTargetBlock('VALUE');pw.centerOnBlock(b.id);b.getSvgRoot().setAttribute('data-test','out');});
box=await page.locator('[data-test="out"] > .blocklyPath').boundingBox();await page.mouse.move(box.x+box.width-5,box.y+box.height/2);await page.mouse.down();await page.mouse.move(160,910,{steps:30});
assert.equal(await page.evaluate(()=>workspace.getTopBlocks(false).some(b=>b.type==='math_number'&&b.getFieldValue('NUM')===42)),true,'live outbound copy');
await page.mouse.up();await page.waitForTimeout(450);
assert.equal(await page.evaluate(()=>row.getInputTargetBlock('VALUE')===null),true);
console.log('PASS: live transfer in both directions, visible before release, with native connection snapping');

await page.evaluate(()=>{
 const v=workspace.createVariable('shared variable','','shared-var');const b=workspace.newBlock('variables_get');b.setFieldValue(v.getId(),'VAR');b.initSvg();b.render();b.moveBy(80,820);b.getSvgRoot().setAttribute('data-test','var');
});
point=await socket();box=await page.locator('[data-test="var"] > .blocklyPath').boundingBox();
await page.mouse.move(box.x+10,box.y+box.height/2);await page.mouse.down();await page.mouse.move(point.x,point.y,{steps:25});await page.mouse.up();await page.waitForTimeout(450);
assert.equal(await page.evaluate(()=>row.getInputTargetBlock('VALUE')?.getVarModels()[0].getId()),'shared-var');
await page.evaluate(()=>{
 const hat=pw.newBlock('fomekmenu_studio_on_click');hat.initSvg();hat.render();cap.getInput('ACTIONS').connection.connect(hat.previousConnection);
 const body=pw.newBlock('variables_set');body.setFieldValue('shared-var','VAR');body.initSvg();body.render();hat.getInput('DO').connection.connect(body.previousConnection);hat.getSvgRoot().setAttribute('data-test','hat');pw.centerOnBlock(hat.id);
 const style=pw.newBlock('fomekmenu_studio_style_hat');style.initSvg();style.render();style.setFieldValue('HOVER','STATE');cap.getInput('STYLE').connection.connect(style.previousConnection);
 const property=pw.newBlock('fomekmenu_studio_property_background');property.initSvg();property.render();style.getInput('PROPERTIES').connection.connect(property.previousConnection);
 const color=pw.newBlock('math_number');color.initSvg();color.render();color.setFieldValue(-65536,'NUM');property.getInput('VALUE').connection.connect(color.outputConnection);
});
box=await page.locator('[data-test="hat"] > .blocklyPath').boundingBox();await page.mouse.move(box.x+35,box.y+12);await page.mouse.down();await page.mouse.move(160,930,{steps:25});await page.mouse.up();await page.waitForTimeout(400);
assert.equal(await page.evaluate(()=>workspace.getAllBlocks(false).some(b=>b.type==='fomekmenu_studio_on_click')),false);
assert.equal(await page.evaluate(()=>pw.getAllBlocks(false).some(b=>b.type==='fomekmenu_studio_on_click')),true);
assert.equal(await page.evaluate(()=>cap.getInputTargetBlock('ACTIONS')?.type),'fomekmenu_studio_on_click','rejected export restores its original section connection');
await page.evaluate(()=>{const hat=pw.getAllBlocks(false).find(b=>b.type==='fomekmenu_studio_on_click');if(!hat.getParent())cap.getInput('ACTIONS').connection.connect(hat.previousConnection);});
await page.click('#fomek-studio-close');
assert.equal(await page.evaluate(()=>host.getInputTargetBlock('STUDIO_CLICK')?.type),'variables_set');
assert.equal(await page.evaluate(()=>host.getInputTargetBlock('STUDIO_HOVER_STYLE')?.getInputTargetBlock('background').getFieldValue('NUM')),-65536);
await page.evaluate(()=>__fomekStudioOpen(host));
assert.equal(await page.evaluate(()=>{const ws=Blockly.Workspace.getAll().find(w=>w.rendered&&!w.isFlyout&&w.getInjectionDiv().closest('#fomek-studio-ws'));const root=ws.getTopBlocks(false).find(b=>b.type.startsWith('fomekmenu_studio_settings_'));return root.getInputTargetBlock('ACTIONS')?.type;}),'fomekmenu_studio_on_click');
await page.click('#fomek-studio-close');
console.log('PASS: shared variables, restricted editor rows, attached actions and modular styles persist');
// The preview uses the same setting and style rows as generation, without
// changing procedure variables or configuration when controls are operated.
await page.evaluate(()=>{
 window.previewOpen=function(type){workspace.clear();const def=sourceDefs[type];host=Blockly.Xml.domToBlock(Blockly.Xml.textToDom('<block type="'+type+'">'+def.mcreator.toolbox_init.join('')+'</block>'),workspace);__fomekStudioOpen(host);pw=Blockly.Workspace.getAll().find(w=>w.rendered&&!w.isFlyout&&w.getInjectionDiv().closest('#fomek-studio-ws'));cap=pw.getTopBlocks(false).find(b=>b.type.startsWith('fomekmenu_studio_settings_'));};
 previewOpen('fomekmenu_checkbox');
});
await page.waitForTimeout(100);
const checkboxPoint=await page.evaluate(()=>{const sim=FomekStudioPreview.state(),r=sim.canvas.getBoundingClientRect();return {x:r.x+sim.geometry.x+7,y:r.y+sim.geometry.y+7};});
const originalChecked=await page.evaluate(()=>FomekStudioPreview.state().checked);
await page.mouse.click(checkboxPoint.x,checkboxPoint.y);await page.waitForTimeout(80);
assert.equal(await page.evaluate(()=>FomekStudioPreview.state().checked),!originalChecked);
assert.equal(await page.evaluate(()=>FomekStudioPreview.state().events.CHECK),1);
assert.equal(await page.evaluate(()=>host.getInputTargetBlock('checked').getFieldValue('BOOL')),'FALSE');
await page.click('#fomek-studio-close');
await page.evaluate(()=>previewOpen('fomekmenu_inputfield'));
await page.locator('.pv-input').fill('Hello preview');await page.waitForTimeout(90);
assert.equal(await page.evaluate(()=>FomekStudioPreview.state().text),'Hello preview');
assert.equal(await page.evaluate(()=>host.getInputTargetBlock('initial').getFieldValue('TEXT')),'');
await page.click('#fomek-studio-close');
await page.evaluate(()=>{
 previewOpen('fomekmenu_dropdown');const setting=pw.getAllBlocks(false).find(b=>b.type.endsWith('_items')&&b.type.startsWith('fomekmenu_studio_setting_'));setting.getInputTargetBlock('VALUE').setFieldValue('A\nB\nC','TEXT');
});
await page.waitForTimeout(150);
let dropdownPoint=await page.evaluate(()=>{const sim=FomekStudioPreview.state(),r=sim.canvas.getBoundingClientRect();return {x:r.x+7,y:r.y+7};});
await page.mouse.click(dropdownPoint.x,dropdownPoint.y);await page.waitForTimeout(60);
dropdownPoint=await page.evaluate(()=>{const sim=FomekStudioPreview.state(),r=sim.canvas.getBoundingClientRect();return {x:r.x+7,y:r.y+sim.listY+sim.rowHeight+4};});
await page.mouse.click(dropdownPoint.x,dropdownPoint.y);await page.waitForTimeout(60);
assert.equal(await page.evaluate(()=>FomekStudioPreview.state().selected),'B');
await page.click('#fomek-studio-close');
await page.evaluate(()=>{
 previewOpen('fomekmenu_render_rect');
 const style=pw.newBlock('fomekmenu_studio_style_hat');style.initSvg();style.render();cap.getInput('STYLE').connection.connect(style.previousConnection);
 const prop=pw.newBlock('fomekmenu_studio_property_background');prop.initSvg();prop.render();style.getInput('PROPERTIES').connection.connect(prop.previousConnection);
 const value=pw.newBlock('math_number');value.setFieldValue(-65536,'NUM');value.initSvg();value.render();prop.getInput('VALUE').connection.connect(value.outputConnection);
 const click=pw.newBlock('fomekmenu_studio_on_click');click.initSvg();click.render();cap.getInput('ACTIONS').connection.connect(click.previousConnection);
 const variable=pw.createVariable('preview counter','','preview-counter'),set=pw.newBlock('variables_set'),number=pw.newBlock('math_number');set.setFieldValue(variable.getId(),'VAR');number.setFieldValue(7,'NUM');set.initSvg();number.initSvg();set.render();number.render();set.getInput('VALUE').connection.connect(number.outputConnection);click.getInput('DO').connection.connect(set.previousConnection);
});
await page.waitForTimeout(150);
assert.deepEqual(await page.evaluate(()=>{const sim=FomekStudioPreview.state(),g=sim.geometry;return Array.from(sim.ctx.getImageData(Math.max(0,g.x)+4,Math.max(0,g.y)+4,1,1).data);}),[255,0,0,255]);
const rectanglePoint=await page.evaluate(()=>{const sim=FomekStudioPreview.state(),r=sim.canvas.getBoundingClientRect();return {x:r.x+sim.geometry.x+4,y:r.y+sim.geometry.y+4};});
await page.mouse.click(rectanglePoint.x,rectanglePoint.y);await page.waitForTimeout(60);
assert.equal(await page.evaluate(()=>FomekStudioPreview.state().variables['preview counter']),7);
await page.locator('[data-pv="scale"]').selectOption('0.5');await page.waitForTimeout(60);
assert.equal(await page.evaluate(()=>FomekStudioPreview.state().canvas.getBoundingClientRect().width),160);
if(process.env.SCREENSHOT_DIR)await page.screenshot({path:path.join(process.env.SCREENSHOT_DIR,'studio-v2-preview.png')});
await page.click('#fomek-studio-close');
console.log('PASS: preview checkbox, input, dropdown, style pixels, click action sandbox and exact 50% viewport scale');

await page.evaluate(()=>{
 previewOpen('fomekmenu_inputfield');
 workspace.updateToolbox('<xml><category name="Shared"><block type="math_number"><field name="NUM">91</field></block></category></xml>');workspace.getToolbox().selectItemByPosition(0);
 row=pw.getAllBlocks(false).find(b=>b.type.endsWith('_maxLength')&&b.type.startsWith('fomekmenu_studio_setting_'));row.getInputTargetBlock('VALUE').dispose();pw.centerOnBlock(row.id);
 workspace.getFlyout().getWorkspace().getTopBlocks(false)[0].getSvgRoot().setAttribute('data-test','direct-flyout');
});
point=await socket();box=await page.locator('[data-test="direct-flyout"] > .blocklyPath').boundingBox();
await page.mouse.move(box.x+box.width-5,box.y+box.height/2);await page.mouse.down();await page.mouse.move(point.x,point.y,{steps:40});
assert.equal(await page.evaluate(()=>pw.getTopBlocks(false).some(b=>b.type==='math_number'&&b.getFieldValue('NUM')===91)),true,'direct flyout live transfer');
await page.mouse.up();await page.waitForTimeout(400);
assert.equal(await page.evaluate(()=>row.getInputTargetBlock('VALUE')?.getFieldValue('NUM')),91);
await page.click('#fomek-studio-close');
assert.equal(await page.evaluate(()=>host.getInputTargetBlock('maxLength').getFieldValue('NUM')),91);
console.log('PASS: direct main toolbox to editor drag attaches and persists value');
await page.evaluate(()=>{
 workspace.getToolbox().clearSelection();previewOpen('fomekmenu_inputfield');
 window.newStudio=function(type){const b=pw.newBlock(type);b.initSvg();b.render();return b;};
 window.appendStudio=function(parent,input,child){let c=parent.getInput(input).connection;while(c.targetBlock())c=c.targetBlock().nextConnection;c.connect(child.previousConnection);};
 window.propertyStudio=function(parent,input,key,type,value,field){const p=newStudio('fomekmenu_studio_property_'+key),v=newStudio(type);v.setFieldValue(value,field);p.getInput('VALUE').connection.connect(v.outputConnection);appendStudio(parent,input,p);return p;};
 const setting=pw.getAllBlocks(false).find(b=>b.type.endsWith('_label')&&b.type.startsWith('fomekmenu_studio_setting_'));
 propertyStudio(setting,'STYLE','textColor','math_number',-16711936,'NUM');
 const a=newStudio('fomekmenu_studio_style_hat'),b=newStudio('fomekmenu_studio_style_hat');appendStudio(cap,'STYLE',a);appendStudio(cap,'STYLE',b);
 propertyStudio(a,'PROPERTIES','background','math_number',-65536,'NUM');propertyStudio(b,'PROPERTIES','paddingX','math_number',8,'NUM');
 const flag=newStudio('logic_compare'),visibility=newStudio('fomekmenu_studio_property_hasText');flag.setFieldValue('EQ','OP');visibility.getInput('VALUE').connection.connect(flag.outputConnection);appendStudio(a,'PROPERTIES',visibility);
});
await page.click('#fomek-studio-close');
assert.deepEqual(await page.evaluate(()=>{const st=host.getInputTargetBlock('STUDIO_NORMAL_STYLE');return {bg:st.getInputTargetBlock('background').getFieldValue('NUM'),padding:st.getInputTargetBlock('paddingX').getFieldValue('NUM'),placeholder:st.getInputTargetBlock('PART_placeholder').getInputTargetBlock('textColor').getFieldValue('NUM'),flag:st.getInputTargetBlock('BOOL_hasText').type};}),{bg:-65536,padding:8,placeholder:-16711936,flag:'logic_compare'});
const modularLayout=await page.evaluate(()=>host.getFieldValue('STUDIO_LAYOUT'));
await page.evaluate(()=>__fomekStudioOpen(host));await page.click('#fomek-studio-close');
assert.equal(await page.evaluate(()=>host.getFieldValue('STUDIO_LAYOUT')),modularLayout);
console.log('PASS: nested placeholder style, merged style sections and boolean expressions survive saved layout');
await page.evaluate(()=>{
 previewOpen('fomekmenu_panel');
 const childDef=sourceDefs.fomekmenu_render_rect;window.previewChild=Blockly.Xml.domToBlock(Blockly.Xml.textToDom('<block type="fomekmenu_render_rect">'+childDef.mcreator.toolbox_init.join('')+'</block>'),workspace);
 host.getInput('CHILDREN').connection.connect(previewChild.previousConnection);
 previewChild.getInputTargetBlock('x').setFieldValue(90,'NUM');previewChild.getInputTargetBlock('y').setFieldValue(10,'NUM');previewChild.getInputTargetBlock('box').getInputTargetBlock('x2').setFieldValue(40,'NUM');previewChild.getInputTargetBlock('box').getInputTargetBlock('y2').setFieldValue(20,'NUM');previewChild.getInputTargetBlock('color').getInputTargetBlock('g').setFieldValue(0,'NUM');previewChild.getInputTargetBlock('color').getInputTargetBlock('b').setFieldValue(0,'NUM');
 const box=pw.getAllBlocks(false).find(b=>b.type==='fomekmenu_box');box.getInputTargetBlock('x1').setFieldValue(0,'NUM');box.getInputTargetBlock('y1').setFieldValue(0,'NUM');box.getInputTargetBlock('x2').setFieldValue(100,'NUM');box.getInputTargetBlock('y2').setFieldValue(50,'NUM');
});
await page.waitForTimeout(180);
assert.deepEqual(await page.evaluate(()=>{const sim=FomekStudioPreview.state();return {inside:Array.from(sim.ctx.getImageData(95,15,1,1).data),outside:Array.from(sim.ctx.getImageData(110,15,1,1).data),nodes:sim.nodes.length};}),{inside:[255,0,0,255],outside:[0,0,0,0],nodes:2});
await page.click('#fomek-studio-close');
console.log('PASS: nested elements use parent coordinates and clip to the panel boundary');

await page.evaluate(()=>{
 const image=document.createElement('canvas');image.width=1;image.height=1;const ctx=image.getContext('2d');ctx.fillStyle='#ffffff';ctx.fillRect(0,0,1,1);const data=image.toDataURL();
 window.assetRequests=[];window.fomekstudioassets={asset:function(id){assetRequests.push(id);if(id==='minecraft:font/default.json')return JSON.stringify({providers:[{type:'space',advances:{' ':4}},{type:'bitmap',file:'test:font/fixture.png',height:8,ascent:7,chars:['A']}]});if(id==='test:textures/font/fixture.png')return data;return '';},refresh:function(){}};
 previewOpen('fomekmenu_render_text');const setting=pw.getAllBlocks(false).find(b=>b.type.endsWith('_text')&&b.type.startsWith('fomekmenu_studio_setting_'));setting.getInputTargetBlock('VALUE').setFieldValue('A A','TEXT');
});
await page.waitForTimeout(180);
assert.equal(await page.evaluate(()=>FomekStudioPreview.state().geometry.w),22);
assert.equal(await page.evaluate(()=>assetRequests.includes('test:textures/font/fixture.png')),true);
assert.equal(await page.evaluate(()=>FomekStudioPreview.state().container.querySelector('.pv-status').textContent.includes('fallback')),false);
await page.click('#fomek-studio-close');await page.evaluate(()=>delete window.fomekstudioassets);
console.log('PASS: resource font providers load texture path, glyph advances and space metrics');

assert.deepEqual(errors,[]);
console.log('PASS: no browser errors; Blockly '+await page.evaluate(()=>Blockly.VERSION));
} finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
