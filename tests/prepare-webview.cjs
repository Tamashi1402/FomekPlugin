const fs=require('fs'),path=require('path'),{pathToFileURL}=require('url'),base=path.resolve(__dirname,'../src/main/resources'),dist=process.env.BLOCKLY_DIST,output=process.argv[2];if(!dist||!output)throw Error('Set BLOCKLY_DIST and pass the output HTML path');
const scripts=[path.join(dist,'blockly_compressed.js'),path.join(dist,'msg/en.js'),path.join(dist,'blocks_compressed.js'),base+'/blockly/js/fomekmenu.js',base+'/blockly/js/fomekmenu_studio.js',base+'/blockly/js/fomekmenu_studio_preview.js'];
const defs=fs.readdirSync(base+'/procedures').filter(n=>n.startsWith('fomekmenu_')&&n.endsWith('.json')).map(n=>({type:n.slice(0,-5),...JSON.parse(fs.readFileSync(base+'/procedures/'+n))}));
const html='<html><body style="margin:0"><div id="blockly-div" style="width:1500px;height:1000px"></div>'+scripts.slice(0,3).map(p=>'<script src="'+pathToFileURL(path.resolve(p))+'"></script>').join('')+'<script>var workspace=Blockly.inject("blockly-div",{toolbox:{kind:"categoryToolbox",contents:[{kind:"category",name:"Menu",contents:[]}]},renderer:"geras",scrollbars:true,sounds:false});</script>'+scripts.slice(3).map(p=>'<script src="'+pathToFileURL(path.resolve(p))+'"></script>').join('')+'<script>Blockly.defineBlocksWithJsonArray('+JSON.stringify(defs)+');'+`
function runWebViewTest(){try{
 const host=workspace.newBlock('fomekmenu_button');host.initSvg();host.render();__fomekStudioOpen(host);
 const popup=Blockly.Workspace.getAll().find(w=>w.rendered&&!w.isFlyout&&w.getInjectionDiv().closest('#fomek-studio-ws'));
 const cap=popup.getTopBlocks(false).find(b=>b.type.startsWith('fomekmenu_studio_settings_'));
 if(cap.inputList.filter(i=>i.name).map(i=>i.name).join(',')!=='SETTINGS,STACK,ACTIONS,STYLE')throw Error('Sections');
 const number=workspace.newBlock('math_number');number.setFieldValue(73,'NUM');number.initSvg();number.render();number.moveBy(50,850);
 const source=number.getSvgRoot().getBoundingClientRect(),dest=popup.getInjectionDiv().getBoundingClientRect();
 const x=dest.left+300,y=dest.top+160,delta={x:x-source.left-10,y:y-source.top-15};
 const dragger=new Blockly.BlockDragger(number,workspace);dragger.startDrag({x:0,y:0},false);
 const event=new MouseEvent('mousemove',{clientX:x,clientY:y,button:0});dragger.drag(event,delta);
 if(!popup.getAllBlocks(false).some(b=>b.type==='math_number'&&b.getFieldValue('NUM')===73))throw Error('No live inbound copy');
 dragger.endDrag(new MouseEvent('mouseup',{clientX:x,clientY:y,button:0}),delta);dragger.dispose();
 if(!number.isDisposed())throw Error('Source not moved');
 document.getElementById('fomek-studio-close').click();
 if(!host.getFieldValue('STUDIO_LAYOUT'))throw Error('Missing saved layout');
 return 'PASS: JavaFX WebView + Blockly '+Blockly.VERSION+'; modular sections, native MouseEvent drag lifecycle, live copy and save';
}catch(e){return 'FAIL: '+e.message+' '+e.stack;}}
`+'</script></body></html>';
fs.writeFileSync(output,html);

