// Local, side-effect-free GUI simulator. One logical pixel is one Minecraft GUI
// pixel. Unsupported procedure expressions/assets are reported, never invented.
(function(){
    'use strict';
    var instance=null;
    var defaults={background:-14342875,border:-8947849,borderWidth:1,textColor:-1,text:'',font:'minecraft:default',size:9,texture:'',hasText:true,buttonStyle:'flat',smooth:true,paddingX:4,paddingY:3,align:'left',shadow:false,parts:{}};
    function rgba(value){var n=Number(value)|0;return 'rgba('+((n>>>16)&255)+','+((n>>>8)&255)+','+(n&255)+','+((n>>>24)/255)+')';}
    function safeId(value){return String(value||'').replace(/[^a-zA-Z0-9_]/g,'_');}
    function create(container,options){
        container.innerHTML='<div class="pv-t">Simulation · 1 GUI px = 1 px at 100%</div><div class="pv-tools"><label>Width <input data-pv="width" type="number" min="1" max="4096" value="320"></label><label>Height <input data-pv="height" type="number" min="1" max="4096" value="240"></label><label>Scale <select data-pv="scale"><option value="1">100%</option><option value="0.75">75%</option><option value="0.5">50%</option><option value="2">200%</option></select></label><button data-pv="reset">Reset</button><button data-pv="assets">Reload assets</button></div><div class="pv-viewport"><div class="pv-surface"><canvas tabindex="0" aria-label="Interactive menu preview"></canvas><textarea class="pv-input" spellcheck="false" aria-label="Preview text input"></textarea></div></div><details><summary>Scenario variables</summary><div class="pv-vars"></div></details><div class="pv-events"></div><div class="pv-status" role="status"></div>';
        var canvas=container.querySelector('canvas'),ctx=canvas.getContext('2d'),input=container.querySelector('textarea'),surface=container.querySelector('.pv-surface');
        var sim={container:container,options:options,canvas:canvas,ctx:ctx,input:input,variables:{},resources:{},images:{},fonts:{},warnings:{},events:{},text:null,checked:null,selected:null,open:false,hover:false,held:false,clickedUntil:0,scroll:0,slider:null,edited:false,alive:true,children:{},nodes:[],focusId:null};
        function warn(text){sim.warnings[text]=true;}
        function resource(id){
            if(Object.prototype.hasOwnProperty.call(sim.resources,id)){if(!sim.resources[id])warn('Resource unavailable: '+id);return sim.resources[id];}
            var value='';try{if(window.fomekstudioassets)value=String(window.fomekstudioassets.asset(id)||'');}catch(e){warn('Asset bridge: '+e.message);}
            sim.resources[id]=value;if(!value)warn('Resource unavailable: '+id);return value;
        }
        function image(id){
            if(sim.images[id])return sim.images[id];var uri=resource(id);if(!uri)return null;
            var img=new Image();sim.images[id]=img;img.src=uri;return img;
        }
        function json(id){try{var value=resource(id);return value?JSON.parse(value):null;}catch(e){warn('Invalid resource JSON: '+id);return null;}}
        function normalize(id,suffix){var text=String(id||'');if(text.indexOf(':')<0)text='minecraft:'+text;return suffix&&!text.endsWith(suffix)?text+suffix:text;}
        function font(id){
            id=id||'minecraft:default';if(sim.fonts[id])return sim.fonts[id];
            var result={glyphs:{},advances:{},family:'monospace',exact:false};sim.fonts[id]=result;
            var seen={};
            function providers(name){if(seen[name])return;seen[name]=true;var ns=name.split(':')[0],path=name.split(':')[1],data=json(ns+':font/'+path+'.json');if(!data)return;
                (data.providers||[]).forEach(function(provider){
                    if(provider.type==='space'){Object.keys(provider.advances||{}).forEach(function(ch){if(!(ch in result.advances))result.advances[ch]=provider.advances[ch];});return;}
                    if(provider.type==='reference'){providers(normalize(provider.id));return;}
                    if(provider.type==='ttf'){
                        var file=normalize(provider.file),uri=resource(file);if(!uri||typeof FontFace==='undefined'){warn('TTF preview unavailable: '+file);return;}
                        var face=new FontFace('FomekPreview_'+safeId(id),'url('+uri+')');face.load().then(function(loaded){document.fonts.add(loaded);result.family=loaded.family;result.ttfSize=provider.size||11;result.exact=true;}).catch(function(){warn('Cannot load font: '+file);});return;
                    }
                    if(provider.type!=='bitmap')return;
                    var img=image(normalize(provider.file).replace(':',':textures/'));if(!img)return;
                    var build=function(){if(!img.naturalWidth)return;var rows=provider.chars||[],columns=Array.from(rows[0]||'').length;if(!columns||!rows.length)return;
                        var tileW=img.naturalWidth/columns,tileH=img.naturalHeight/rows.length,height=provider.height||8;
                        var tmp=document.createElement('canvas');tmp.width=img.naturalWidth;tmp.height=img.naturalHeight;var tc=tmp.getContext('2d');tc.drawImage(img,0,0);var pixels=tc.getImageData(0,0,tmp.width,tmp.height).data;
                        rows.forEach(function(row,ry){Array.from(row).forEach(function(ch,rx){if(result.glyphs[ch]||ch==='\u0000')return;var visible=0;for(var x=0;x<tileW;x++)for(var y=0;y<tileH;y++)if(pixels[((ry*tileH+y)*tmp.width+rx*tileW+x)*4+3]>0)visible=x+1;
                            result.glyphs[ch]={image:img,x:rx*tileW,y:ry*tileH,w:tileW,h:tileH,height:height,ascent:provider.ascent||7,advance:Math.floor(visible*height/tileH+.5)+1};
                        });});result.exact=true;
                    };if(img.complete)build();else img.addEventListener('load',build,{once:true});
                });
            }providers(normalize(id));return result;
        }
        function read(block,depth){
            if(!block)return null;if((depth||0)>64){warn('Expression nesting limit');return null;}
            function val(name){return read(block.getInputTargetBlock(name),(depth||0)+1);}
            function field(name){return block.getFieldValue(name);}
            var type=block.type;
            if(type==='math_number')return Number(field('NUM'));
            if(type==='text')return field('TEXT');
            if(type==='logic_boolean')return field('BOOL')==='TRUE';
            if(type==='logic_null')return null;
            if(type==='variables_get'){var v=block.getVarModels&&block.getVarModels()[0],name=v?v.name:field('VAR');if(!(name in sim.variables)){sim.variables[name]=0;refreshVariables();}return sim.variables[name];}
            if(type==='math_arithmetic'){var a=Number(val('A')),b=Number(val('B'));switch(field('OP')){case'ADD':return a+b;case'MINUS':return a-b;case'MULTIPLY':return a*b;case'DIVIDE':return a/b;case'POWER':return Math.pow(a,b);}}
            if(type==='logic_compare'){var a=val('A'),b=val('B');switch(field('OP')){case'EQ':return a===b;case'NEQ':return a!==b;case'LT':return a<b;case'LTE':return a<=b;case'GT':return a>b;case'GTE':return a>=b;}}
            if(type==='logic_negate')return !val('BOOL');
            if(type==='logic_operation')return field('OP')==='AND'?Boolean(val('A')&&val('B')):Boolean(val('A')||val('B'));
            if(type==='text_join'||type==='lists_create_with'){var values=block.inputList.filter(function(i){return i.name.indexOf('ADD')===0;}).map(function(i){return val(i.name);});return type==='text_join'?values.join(''):values;}
            if(type==='fomekmenu_box')return {x1:Number(val('x1')),y1:Number(val('y1')),x2:Number(val('x2')),y2:Number(val('y2'))};
            if(type==='fomekmenu_rgb')return (255<<24)|((Number(val('r'))&255)<<16)|((Number(val('g'))&255)<<8)|(Number(val('b'))&255);
            if(type.indexOf('mcitem')===0)return field('value')||field('item')||'';
            if(type==='fomekmenu_get_control_text'||type==='fomekmenu_is_checked'){var control=sim.nodes.find(function(n){return String(inputValue(n.host,'id',''))===String(val('id'));}),state=control?control.state:sim;if(!control)warn('Control not present in this preview: '+val('id'));return type==='fomekmenu_is_checked'?Boolean(state.checked):state.text===null?String(state.selected||''):state.text;}
            if(type==='fomekmenu_studio_style'){
                var st=Object.assign({},defaults,{parts:{}});Object.keys(defaults).forEach(function(key){if(block.getInput(key))st[key]=val(key);else if(block.getField(key))st[key]=field(key)==='TRUE'?true:field(key)==='FALSE'?false:field(key);});['text','placeholder','label','indicator','list','selection'].forEach(function(part){var value=val('PART_'+part);if(value)st.parts[part]=value;});['hasText','smooth','shadow'].forEach(function(key){if(block.getInputTargetBlock('BOOL_'+key))st[key]=val('BOOL_'+key);});return st;
            }
            if(type==='fomekmenu_type_empty')return {kind:'empty'};
            if(type==='fomekmenu_type_rectangle')return {kind:'rectangle',color:val('color')};
            if(type==='fomekmenu_type_texture')return {kind:'texture',texture:val('texture')};
            warn('Needs game/runtime value: '+type);return null;
        }
        function inputValue(host,name,fallback){var value=sim.options.getValue(host,name,read);return value===null||value===undefined?fallback:value;}
        function listRows(host){return Math.max(1,Math.min(30,Number(inputValue(host,'listRows',5))||1));}
        function visualStyle(host,state){
            host=host||sim.options.host;state=state||sim;
            var keys=[];if(state.clickedUntil>Date.now())keys.push('CLICK');if(state.held)keys.push('HELD');if(state.hover)keys.push('HOVER');keys.push('NORMAL');
            if(host!==sim.options.host){for(var i=0;i<keys.length;i++){var style=read(host.getInputTargetBlock('STUDIO_'+keys[i]+'_STYLE'));if(style)return style;}return null;}
            var model=null;for(var i=0;i<keys.length&&!model;i++)model=sim.options.getStyle(keys[i]);if(!model)return null;
            function resolve(m){var out=Object.assign({},defaults,{parts:{}});Object.keys(m).forEach(function(key){if(key==='parts')return;out[key]=m[key]&&m[key].studioScale?9*Number(read(m[key].studioScale)):m[key]&&m[key].workspace?read(m[key]):m[key];});Object.keys(m.parts||{}).forEach(function(key){out.parts[key]=resolve(Object.assign({},m,m.parts[key],{parts:{}}));});return out;}return resolve(model);
        }
        function part(style,name){return style.parts&&style.parts[name]||style;}
        function measure(text,style){var f=font(style.font),scale=Math.max(.05,Number(style.size)/9),width=0;ctx.font=((f.ttfSize||9)*scale)+'px '+f.family;Array.from(String(text)).forEach(function(ch){width+=(ch in f.advances?f.advances[ch]*scale:f.glyphs[ch]?f.glyphs[ch].advance*scale:ch===' '?4*scale:ctx.measureText(ch).width);});return width;}
        function text(value,x,y,style){
            if(!style.hasText)return;var f=font(style.font),scale=Math.max(.05,Number(style.size)/9),t=String(value==null?'':value);
            if(!f.exact)warn('Font preview uses fallback metrics: '+style.font);
            ctx.imageSmoothingEnabled=style.smooth;
            function pass(dx,dy,color){var px=x+dx;Array.from(t).forEach(function(ch){if(ch in f.advances){px+=f.advances[ch]*scale;return;}var glyph=f.glyphs[ch];if(glyph){
                    var tiles=glyph.tints||(glyph.tints={}),tile=tiles[color];if(!tile){tile=document.createElement('canvas');tile.width=glyph.w;tile.height=glyph.h;var tc=tile.getContext('2d');tc.drawImage(glyph.image,glyph.x,glyph.y,glyph.w,glyph.h,0,0,glyph.w,glyph.h);tc.globalCompositeOperation='source-in';tc.fillStyle=color;tc.fillRect(0,0,tile.width,tile.height);if(Object.keys(tiles).length>32)glyph.tints={};glyph.tints[color]=tile;}
                    ctx.drawImage(tile,px,y+dy+(7-glyph.ascent)*scale,glyph.w*glyph.height/glyph.h*scale,glyph.height*scale);px+=glyph.advance*scale;
                }else{if(f.exact&&!f.ttfSize)warn('Font glyph uses fallback metrics: '+ch);ctx.fillStyle=color;ctx.font=((f.ttfSize||9)*scale)+'px '+f.family;ctx.textBaseline='top';ctx.fillText(ch,px,y+dy);px+=ch===' '?4*scale:ctx.measureText(ch).width;}});}
            if(style.shadow)pass(scale,scale,'rgba(0,0,0,.7)');pass(0,0,rgba(style.textColor));
        }
        function texture(id,x,y,w,h){if(!id)return;var img=image(normalize(id));if(img&&img.complete&&img.naturalWidth){ctx.imageSmoothingEnabled=false;ctx.drawImage(img,x,y,w,h);}else{ctx.strokeStyle='#bd6565';ctx.setLineDash([2,2]);ctx.strokeRect(x+.5,y+.5,w-1,h-1);ctx.setLineDash([]);}}
        function background(x,y,w,h,style){
            if(style.buttonStyle!=='none'&&style.buttonStyle!=='outline'){ctx.fillStyle=rgba(style.background);ctx.fillRect(x,y,w,h);}
            texture(style.texture,x,y,w,h);
            if(style.buttonStyle!=='none'){var bw=Math.max(0,Math.min(Number(style.borderWidth),w/2,h/2));ctx.fillStyle=rgba(style.border);ctx.fillRect(x,y,w,bw);ctx.fillRect(x,y+h-bw,w,bw);ctx.fillRect(x,y,bw,h);ctx.fillRect(x+w-bw,y,bw,h);}
            if(style.buttonStyle==='raised'){ctx.fillStyle='#ffffff55';ctx.fillRect(x+1,y+1,w-2,1);ctx.fillStyle='#00000055';ctx.fillRect(x+1,y+h-2,w-2,1);}
        }
        function label(value,g,style){var st=part(style,'text'),value=st.text||value,width=measure(value,st),x=st.align==='center'?g.x+(g.w-width)/2:st.align==='right'?g.x+g.w-width-st.paddingX:g.x+st.paddingX;text(value,x,g.y+st.paddingY,st);}
        function geometry(host,state){
            function n(name,fallback){return Number(inputValue(host,name,fallback||0));}
            var type=host.type,box=inputValue(host,'box',null),g={x:n('x')+n('atX'),y:n('y')+n('atY'),w:n('w',80),h:n('h',20)};
            if(box&&typeof box==='object'){g.x+=box.x1;g.y+=box.y1;g.w=box.x2-box.x1;g.h=box.y2-box.y1;}
            if(type.indexOf('item')>=0){g.w=16;g.h=16;}
            if(type==='fomekmenu_book'){g.w=2*n('pagew',132);g.h=n('height',180);warn('Page-turn shader and book textures require a game preview');}
            if(type==='fomekmenu_book_page'||type==='fomekmenu_obj_render')warn('Composite object/page execution requires game/runtime values');
            if(type==='fomekmenu_obj_add_text'||type==='fomekmenu_render_text'){var style=visualStyle(host,state)||Object.assign({},defaults,{size:9*n('textScale',1)});style=part(style,'text');g.w=measure(style.text||inputValue(host,'text',''),style);g.h=style.size;}
            var pivot=inputValue(host,'pivot','top-left');if(['top','center','bottom'].indexOf(pivot)>=0)g.x-=g.w/2;else if(['top-right','right','bottom-right'].indexOf(pivot)>=0)g.x-=g.w;
            if(['left','center','right'].indexOf(pivot)>=0)g.y-=g.h/2;else if(['bottom','bottom-left','bottom-right'].indexOf(pivot)>=0)g.y-=g.h;
            return g;
        }
        function execute(first,budget){for(var b=first;b&&budget.count++<500;b=b.getNextBlock()){
            var model=b.getVarModels&&b.getVarModels()[0],name=model?model.name:b.getFieldValue('VAR');
            if(b.type==='variables_set'){sim.variables[name]=read(b.getInputTargetBlock('VALUE'));continue;}
            if(b.type==='math_change'){sim.variables[name]=Number(sim.variables[name]||0)+Number(read(b.getInputTargetBlock('DELTA')));continue;}
            if(b.type==='controls_if'){var done=false;for(var i=0;b.getInput('IF'+i);i++)if(read(b.getInputTargetBlock('IF'+i))){execute(b.getInputTargetBlock('DO'+i),budget);done=true;break;}if(!done)execute(b.getInputTargetBlock('ELSE'),budget);continue;}
            if(b.type==='controls_repeat_ext'){var times=Math.min(100,Math.max(0,Number(read(b.getInputTargetBlock('TIMES')))));for(var i=0;i<times;i++)execute(b.getInputTargetBlock('DO'),budget);continue;}
            warn('Action needs Minecraft; not executed in preview: '+b.type);
        }}
        function event(name,host){host=host||sim.options.host;var key=host===sim.options.host?name:host.type+':'+name;sim.events[key]=(sim.events[key]||0)+1;(host===sim.options.host?sim.options.actions(name):[host.getInputTargetBlock('STUDIO_'+name)].filter(Boolean)).forEach(function(body){execute(body,{count:0});});}
        function refreshVariables(){var list=container.querySelector('.pv-vars');Object.keys(sim.variables).forEach(function(name){if(Array.prototype.some.call(list.children,function(row){return row.dataset.name===name;}))return;var label=document.createElement('label');label.dataset.name=name;label.appendChild(document.createTextNode(name+' '));var field=document.createElement('input');field.value=JSON.stringify(sim.variables[name]);field.addEventListener('change',function(){try{sim.variables[name]=JSON.parse(field.value);}catch(e){sim.variables[name]=field.value;}});label.appendChild(field);list.appendChild(label);});}
        function scene(host,ox,oy,clip,depth){
            host=host||sim.options.host;ox=ox||0;oy=oy||0;depth=depth||0;if(depth>20)return;
            var state=host===sim.options.host?sim:(sim.children[host.id]||(sim.children[host.id]={text:null,checked:null,selected:null,open:false,hover:false,held:false,scroll:0,slider:null,clickedUntil:0}));
            var g=geometry(host,state);g.x+=ox;g.y+=oy;
            var type=host.type,style=visualStyle(host,state),control=/fomekmenu_(inputfield|text_area|checkbox|dropdown)$/.test(type);state.geometry=g;sim.nodes.push({host:host,state:state,g:g,clip:clip});
            var st=style||defaults;
            if(control||style)background(g.x,g.y,g.w,g.h,st);
            if(type==='fomekmenu_checkbox'){
                if(state.checked===null)state.checked=Boolean(inputValue(host,'checked',false));var side=Math.min(g.h,g.w);ctx.fillStyle=rgba(st.border);ctx.fillRect(g.x+3,g.y+3,side-6,side-6);
                if(state.checked){var indicator=part(st,'indicator');text('✓',g.x+indicator.paddingX,g.y+indicator.paddingY,indicator);}var cbLabel=part(st,'label').text||String(inputValue(host,'label','')||'');if(cbLabel){var labelStyle=part(st,'label');text(cbLabel,g.x+side+labelStyle.paddingX,g.y+labelStyle.paddingY,labelStyle);}
            }else if(type==='fomekmenu_dropdown'){
                var list=inputValue(host,'items',[]);if(!Array.isArray(list))list=String(list).split(/\r?\n/);list=list.map(String);state.items=list;
                if(state.selected===null)state.selected=String(inputValue(host,'initial',''));if(list.indexOf(state.selected)<0)state.selected=list[0]||'';
                text(state.selected,g.x+part(st,'text').paddingX,g.y+part(st,'text').paddingY,part(st,'text'));text('▾',g.x+g.w-12,g.y+part(st,'indicator').paddingY,part(st,'indicator'));
                if(state.open){var ls=part(st,'list'),height=Math.max(14,Math.ceil(ls.size)+5),rows=Math.min(listRows(host),list.length);state.rowHeight=height;state.listY=g.y+g.h+height*rows>sim.height?Math.max(0,g.y-height*rows):g.y+g.h;
                    sim.overlays.push(function(){for(var r=0;r<rows&&r+state.scroll<list.length;r++){background(g.x,state.listY+r*height,g.w,height,ls);text(list[r+state.scroll],g.x+ls.paddingX,state.listY+r*height+ls.paddingY,ls);}});}
            }else if(type==='fomekmenu_inputfield'||type==='fomekmenu_text_area'){
                if(state.text===null)state.text=String(inputValue(host,'initial',''));
                var max=Math.max(1,Number(inputValue(host,'maxLength',2147483647)));
                var ts=part(st,state.text?'text':'placeholder'),value=state.text||String(inputValue(host,'label',''));
                if(host===sim.options.host||sim.focusId===host.id){input.style.display='block';input.maxLength=Math.min(max,2147483647);input.style.clipPath=clip?'inset('+Math.max(0,clip.y-g.y-st.paddingY)*sim.scale+'px '+Math.max(0,g.x+g.w-st.paddingX-clip.x-clip.w)*sim.scale+'px '+Math.max(0,g.y+g.h-st.paddingY-clip.y-clip.h)*sim.scale+'px '+Math.max(0,clip.x-g.x-st.paddingX)*sim.scale+'px)':'none';sim.inputState=state;if(input.value!==state.text)input.value=state.text;input.dataset.host=host.id;input.style.left=(g.x+st.paddingX)*sim.scale+'px';input.style.top=(g.y+st.paddingY)*sim.scale+'px';input.style.width=Math.max(1,g.w-st.paddingX*2)*sim.scale+'px';input.style.height=Math.max(1,g.h-st.paddingY*2)*sim.scale+'px';input.style.font=ts.size*sim.scale+'px '+font(ts.font).family;}
                ctx.save();ctx.beginPath();ctx.rect(g.x+st.paddingX,g.y+st.paddingY,g.w-st.paddingX*2,g.h-st.paddingY*2);ctx.clip();var lines=[''];Array.from(value).forEach(function(ch){var last=lines.length-1;if(type==='fomekmenu_text_area'&&(ch==='\n'||measure(lines[last]+ch,ts)>g.w-st.paddingX*2)){lines.push(ch==='\n'?'':ch);}else lines[last]+=ch;});lines.forEach(function(line,i){text(line,g.x+st.paddingX,g.y+st.paddingY+(i-state.scroll)*(ts.size+2),ts);});ctx.restore();
            }else if(type==='fomekmenu_button'){
                if(!style){var color=Number(inputValue(host,'color',-1)),factor=state.held?.7:state.hover?.85:1;var dark=(color&0xff000000)|((Math.floor(((color>>>16)&255)*factor))<<16)|((Math.floor(((color>>>8)&255)*factor))<<8)|Math.floor((color&255)*factor);background(g.x,g.y,g.w,g.h,Object.assign({},defaults,{background:dark,border:0xff333333}));}else label('',g,st);
            }else if(type==='fomekmenu_slider'){
                var min=Number(inputValue(host,'min',0)),max=Number(inputValue(host,'max',1));if(state.slider===null)state.slider=inputValue(host,'reverseDefault','false')==='true'?max:min;
                var vertical=inputValue(host,'direction','horizontal')==='vertical',pct=max===min?0:(state.slider-min)/(max-min);ctx.fillStyle=style?rgba(st.background):'#333';ctx.fillRect(g.x,g.y,g.w,g.h);ctx.fillStyle=style?rgba(st.border):'#4a90d9';ctx.fillRect(g.x,g.y,vertical?g.w:pct*(g.w-4)+4,vertical?pct*(g.h-4)+4:g.h);ctx.fillStyle=style?rgba(st.textColor):'#ccc';ctx.fillRect(g.x+(vertical?0:pct*(g.w-4)),g.y+(vertical?pct*(g.h-4):0),vertical?g.w:4,vertical?4:g.h);
            }else if(type.indexOf('text')>=0&&type.indexOf('texture')<0){text(part(st,'text').text||inputValue(host,'text',''),g.x,g.y,style?part(style,'text'):Object.assign({},defaults,{textColor:inputValue(host,'color',-1),size:9*Number(inputValue(host,'textScale',1)),shadow:inputValue(host,'shadow','false')==='true'}));
            }else if(type.indexOf('texture')>=0){texture(st.texture||inputValue(host,'texture',''),g.x,g.y,g.w,g.h);
            }else if(type.indexOf('item')>=0){var ref=String(inputValue(host,'item','')).replace(/^Items\./,'minecraft:').replace(/^Blocks\./,'minecraft:').toLowerCase(),id=normalize(ref),ns=id.split(':')[0],name=id.split(':')[1],model=json(ns+':models/item/'+name+'.json');
                if(model&&model.textures&&model.textures.layer0)texture(normalize(model.textures.layer0).replace(':',':textures/')+'.png',g.x,g.y,16,16);
                else warn('Item model requires Minecraft renderer: '+ref);
            }else if(type.indexOf('rect')>=0){if(!style)background(g.x,g.y,g.w,g.h,Object.assign({},defaults,{background:inputValue(host,'color',-1),border:inputValue(host,'color',-1),borderWidth:type.indexOf('outline')>=0?1:0,buttonStyle:type.indexOf('outline')>=0?'outline':'flat'}));
            }else if(type==='fomekmenu_panel'||type==='fomekmenu_scroll_view'){
                var appearance=inputValue(host,'as',null);if(!style&&appearance){if(appearance.kind==='rectangle'){ctx.fillStyle=rgba(appearance.color);ctx.fillRect(g.x,g.y,g.w,g.h);}if(appearance.kind==='texture')texture(appearance.texture,g.x,g.y,g.w,g.h);}
                var bottom=g.y+g.h,offset=0,children=[];
                for(var child=host.getInputTargetBlock('CHILDREN');child;child=child.getNextBlock()){
                    if(child.type.indexOf('fomekmenu_')===0&&child.getField('STUDIO_KEY')){children.push(child);var cg=geometry(child);bottom=Math.max(bottom,g.y+cg.y+cg.h);}
                    else warn('Nested procedure needs runtime: '+child.type);
                }
                if(type==='fomekmenu_scroll_view'){var range=Math.max(0,bottom-g.y-g.h),value=Number(inputValue(host,'scroll',0));if(!state.scrolled)state.scroll=value<=1?value*range:value;state.scroll=Math.max(0,Math.min(range,state.scroll));state.maxScroll=range;offset=state.scroll;}
                var bounds={x:g.x,y:g.y,w:g.w,h:g.h};if(clip){var right=Math.min(bounds.x+bounds.w,clip.x+clip.w),lower=Math.min(bounds.y+bounds.h,clip.y+clip.h);bounds.x=Math.max(bounds.x,clip.x);bounds.y=Math.max(bounds.y,clip.y);bounds.w=Math.max(0,right-bounds.x);bounds.h=Math.max(0,lower-bounds.y);}
                ctx.save();ctx.beginPath();ctx.rect(bounds.x,bounds.y,bounds.w,bounds.h);ctx.clip();children.forEach(function(child){scene(child,g.x,g.y-offset,bounds,depth+1);});ctx.restore();
            }
        }
        function pointer(e){var r=canvas.getBoundingClientRect();return{x:(e.clientX-r.left)/sim.scale,y:(e.clientY-r.top)/sim.scale};}
        function within(p,g){return g&&p.x>=g.x&&p.x<g.x+g.w&&p.y>=g.y&&p.y<g.y+g.h;}
        function hit(p){var nodes=sim.nodes.filter(function(n){return within(p,n.g)&&(!n.clip||within(p,n.clip));});return nodes[nodes.length-1];}
        function slider(p,node){var g=node.g,h=node.host,state=node.state,min=Number(inputValue(h,'min',0)),max=Number(inputValue(h,'max',1)),vertical=inputValue(h,'direction','horizontal')==='vertical';var pct=Math.max(0,Math.min(1,vertical?(p.y-g.y)/(g.h||1):(p.x-g.x)/(g.w||1)));state.slider=min+pct*(max-min);if(inputValue(h,'sliderType','smooth')==='step'){var step=Number(inputValue(h,'step',1));if(step>0)state.slider=Math.max(min,Math.min(max,min+Math.round((state.slider-min)/step)*step));}}
        canvas.addEventListener('mousemove',function(e){var p=pointer(e);sim.nodes.forEach(function(n){n.state.hover=within(p,n.g)&&(!n.clip||within(p,n.clip));});if(sim.active&&sim.active.state.held&&sim.active.host.type==='fomekmenu_slider')slider(p,sim.active);});
        canvas.addEventListener('mouseleave',function(){sim.nodes.forEach(function(n){n.state.hover=false;});});
        canvas.addEventListener('mousedown',function(e){var p=pointer(e),node=hit(p);
            var dropdown=sim.nodes.find(function(n){return n.state.open&&n.state.items&&p.x>=n.g.x&&p.x<n.g.x+n.g.w&&p.y>=n.state.listY&&p.y<n.state.listY+n.state.rowHeight*Math.min(n.state.items.length,listRows(n.host));});
            if(dropdown){var ds=dropdown.state;ds.selected=ds.items[Math.floor((p.y-ds.listY)/ds.rowHeight)+ds.scroll];ds.open=false;return;}
            sim.nodes.forEach(function(n){if(n!==node)n.state.open=false;});if(!node)return;sim.focusId=node.host.id;sim.active=node;var state=node.state,type=node.host.type;state.held=true;state.clickedUntil=Date.now()+34;event('CLICK',node.host);
            if(type==='fomekmenu_checkbox'){state.checked=!state.checked;event('CHECK',node.host);}if(type==='fomekmenu_dropdown'){state.open=!state.open;state.scroll=Math.max(0,Math.min(state.items.indexOf(state.selected),Math.max(0,state.items.length-listRows(node.host))));}if(type==='fomekmenu_slider')slider(p,node);
            if(type==='fomekmenu_inputfield'||type==='fomekmenu_text_area'){sim.focusId=node.host.id;setTimeout(function(){input.focus();},40);}
        });
        sim.release=function(){sim.nodes.forEach(function(n){n.state.held=false;});sim.active=null;};document.addEventListener('mouseup',sim.release);
        canvas.addEventListener('keydown',function(e){var n=sim.nodes.find(function(n){return n.host.id===sim.focusId;});if(!n)return;var s=n.state,t=n.host.type;if(e.key==='Escape'){s.open=false;sim.focusId=null;return;}if(t==='fomekmenu_checkbox'&&(e.key===' '||e.key==='Enter')){s.checked=!s.checked;event('CHECK',n.host);e.preventDefault();}if(t==='fomekmenu_dropdown'){if(e.key===' '||e.key==='Enter'){s.open=!s.open;e.preventDefault();}if(e.key==='ArrowUp'||e.key==='ArrowDown'){var i=Math.max(0,s.items.indexOf(s.selected))+(e.key==='ArrowDown'?1:-1);s.selected=s.items[Math.max(0,Math.min(s.items.length-1,i))]||'';e.preventDefault();}}});
        canvas.addEventListener('wheel',function(e){var node=sim.nodes.find(function(n){return n.state.open&&n.state.items;})||hit(pointer(e));if(!node)return;var state=node.state;if(state.open&&state.items){e.preventDefault();state.scroll=Math.max(0,Math.min(Math.max(0,state.items.length-listRows(node.host)),state.scroll+Math.sign(e.deltaY)));}else{var scroll=sim.nodes.filter(function(n){return n.host.type==='fomekmenu_scroll_view'&&within(pointer(e),n.g);}).pop();if(scroll){e.preventDefault();scroll.state.scroll=Math.max(0,Math.min(scroll.state.maxScroll||0,scroll.state.scroll+Math.sign(e.deltaY)*16));scroll.state.scrolled=true;}}},{passive:false});
        input.addEventListener('input',function(){var state=sim.inputState||sim;var n=inputNode();state.text=n&&n.host.type==='fomekmenu_inputfield'?input.value.replace(/[\r\n]/g,''):input.value;if(input.value!==state.text)input.value=state.text;state.edited=true;});
        input.addEventListener('keydown',function(e){if((sim.nodes.find(function(n){return n.host.id===input.dataset.host;})||{host:sim.options.host}).host.type==='fomekmenu_inputfield'&&e.key==='Enter')e.preventDefault();});
        function inputNode(){return sim.nodes.find(function(n){return n.host.id===input.dataset.host;});}
        input.addEventListener('mousedown',function(){var n=inputNode();if(n){n.state.held=true;n.state.clickedUntil=Date.now()+34;event('CLICK',n.host);}});
        input.addEventListener('mouseenter',function(){var n=inputNode();if(n)n.state.hover=true;});input.addEventListener('mouseleave',function(){var n=inputNode();if(n)n.state.hover=false;});
        input.addEventListener('wheel',function(e){var n=inputNode();if(n&&n.host.type==='fomekmenu_text_area'){n.state.scroll=Math.max(0,n.state.scroll+Math.sign(e.deltaY));e.preventDefault();}},{passive:false});
        container.querySelector('[data-pv="reset"]').onclick=function(){sim.children={};sim.focusId=null;sim.scrolled=false;sim.text=null;sim.checked=null;sim.selected=null;sim.open=false;sim.scroll=0;sim.slider=null;sim.events={};sim.edited=false;};
        container.querySelector('[data-pv="assets"]').onclick=function(){sim.resources={};sim.images={};sim.fonts={};if(window.fomekstudioassets)window.fomekstudioassets.refresh();};
        function frame(){if(!sim.alive)return;sim.warnings={};sim.width=Math.max(1,Math.min(4096,Number(container.querySelector('[data-pv="width"]').value)||320));sim.height=Math.max(1,Math.min(4096,Number(container.querySelector('[data-pv="height"]').value)||240));sim.scale=Number(container.querySelector('[data-pv="scale"]').value)||1;
            var dpr=window.devicePixelRatio||1,w=Math.round(sim.width*sim.scale*dpr),h=Math.round(sim.height*sim.scale*dpr);if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}canvas.style.width=sim.width*sim.scale+'px';canvas.style.height=sim.height*sim.scale+'px';surface.style.width=sim.width*sim.scale+'px';surface.style.height=sim.height*sim.scale+'px';
            ctx.setTransform(sim.scale*dpr,0,0,sim.scale*dpr,0,0);ctx.clearRect(0,0,sim.width,sim.height);input.style.display='none';
            try{sim.nodes=[];sim.overlays=[];scene();sim.overlays.forEach(function(draw){draw();});sim.nodes.forEach(function(node){event('UPDATE',node.host);if(node.state.hover)event('HOVER',node.host);});}catch(error){warn('Preview: '+error.message);}
            container.querySelector('.pv-events').textContent=Object.keys(sim.events).map(function(k){return k+': '+sim.events[k];}).join(' · ');
            container.querySelector('.pv-status').textContent=Object.keys(sim.warnings).join('\n');refreshVariables();sim.timer=setTimeout(frame,33);
        }
        sim.read=read;sim.frame=frame;frame();return sim;
    }
    window.FomekStudioPreview={render:function(options){var container=document.getElementById('fomek-studio-preview');if(!container)return;if(!instance||instance.options.host!==options.host){this.close();instance=create(container,options);}else instance.options=options;},close:function(){if(instance){instance.alive=false;clearTimeout(instance.timer);document.removeEventListener('mouseup',instance.release);instance=null;}},state:function(){return instance;}};
})();
