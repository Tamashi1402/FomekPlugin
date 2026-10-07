// Fomek Menu Studio: modular settings, attached actions and live workspace transfer.
(function () {
    'use strict';

    // ── kind registry ──────────────────────────────────────────────
    var KINDS = {
        'fomekmenu_button':         { kind: 'button',   title: 'Button' },
        'fomekmenu_slider':          { kind: 'slider',  title: 'Slider' },
        'fomekmenu_panel':          { kind: 'panel',   title: 'Panel' },
        'fomekmenu_scroll_view':     { kind: 'scroll',  title: 'Scroll view' },
        'fomekmenu_obj_add_item':    { kind: 'obj_item',    title: 'Item' },
        'fomekmenu_obj_add_rect':    { kind: 'obj_rect',    title: 'Rectangle' },
        'fomekmenu_obj_add_text':    { kind: 'obj_text',    title: 'Text' },
        'fomekmenu_obj_add_texture': { kind: 'obj_texture', title: 'Texture' },
        'fomekmenu_book':            { kind: 'book',        title: 'Book' },
        'fomekmenu_book_page':       { kind: 'page',        title: 'Book page' },
        // render calls carry the same option set (check/interactions/stick/collision)
        'fomekmenu_render_text':      { kind: 'render', title: 'Render text' },
        'fomekmenu_render_rect':     { kind: 'render', title: 'Render rectangle' },
        'fomekmenu_render_rect_outline': { kind: 'render', title: 'Render outline' },
        'fomekmenu_render_texture':   { kind: 'render', title: 'Render texture' },
        'fomekmenu_render_item':      { kind: 'render', title: 'Render item' },
        'fomekmenu_obj_render':      { kind: 'render', title: 'Render menu object' }
    };

    // Detailed inputs keep their original names for XML and the MCreator generator.
    var DETAILS = {
        "fomekmenu_button": [
            {"label":"Width","arg":{"type":"input_value","name":"w","check":"Number"}},
            {"label":"Height","arg":{"type":"input_value","name":"h","check":"Number"}},
            {"label":"Color","arg":{"type":"input_value","name":"color","check":"Number"}},
            {"label":"Action","arg":{"type":"input_value","name":"action","check":"String"}},
            {"label":"Anchor","arg":{"type":"field_dropdown","name":"pivot","options":[["top-left","top-left"],["top","top"],["top-right","top-right"],["left","left"],["center","center"],["right","right"],["bottom-left","bottom-left"],["bottom","bottom"],["bottom-right","bottom-right"]]}}
        ],
        "fomekmenu_slider": [
            {"label":"Width","arg":{"type":"input_value","name":"w","check":"Number"}},
            {"label":"Height","arg":{"type":"input_value","name":"h","check":"Number"}},
            {"label":"Direction","arg":{"type":"field_dropdown","name":"direction","options":[["Horizontal","horizontal"],["Vertical","vertical"]]}},
            {"label":"Slider type","arg":{"type":"field_dropdown","name":"sliderType","options":[["Smooth","smooth"],["Step","step"]]}},
            {"label":"Reverse by default","arg":{"type":"field_dropdown","name":"reverseDefault","options":[["false","false"],["true","true"]]}},
            {"label":"Minimum","arg":{"type":"input_value","name":"min","check":"Number"}},
            {"label":"Maximum","arg":{"type":"input_value","name":"max","check":"Number"}},
            {"label":"Step","arg":{"type":"input_value","name":"step","check":"Number"}},
            {"label":"Action","arg":{"type":"input_value","name":"action","check":"String"}}
        ],
        "fomekmenu_panel": [
            {"label":"Box","arg":{"type":"input_value","name":"box","check":"FomekMenuBox"}},
            {"label":"Appearance","arg":{"type":"input_value","name":"as","check":"FomekMenuType"}},
            {"label":"Anchor","arg":{"type":"field_dropdown","name":"pivot","options":[["top-left","top-left"],["top","top"],["top-right","top-right"],["left","left"],["center","center"],["right","right"],["bottom-left","bottom-left"],["bottom","bottom"],["bottom-right","bottom-right"]]}}
        ],
        "fomekmenu_scroll_view": [
            {"label":"Box","arg":{"type":"input_value","name":"box","check":"FomekMenuBox"}},
            {"label":"Scroll","arg":{"type":"input_value","name":"scroll","check":"Number"}},
            {"label":"Anchor","arg":{"type":"field_dropdown","name":"anchor","options":[["top","top"],["bottom","bottom"]]}}
        ],
        "fomekmenu_book": [
            {"label":"Anchor","arg":{"type":"field_dropdown","name":"pivot","options":[["center","center"],["top-left","top-left"],["top","top"],["top-right","top-right"],["left","left"],["right","right"],["bottom-left","bottom-left"],["bottom","bottom"],["bottom-right","bottom-right"]]}},
            {"label":"Page width","arg":{"type":"input_value","name":"pagew","check":"Number"}},
            {"label":"Height","arg":{"type":"input_value","name":"height","check":"Number"}},
            {"label":"Flip duration (ms)","arg":{"type":"input_value","name":"duration","check":"Number"}}
        ],
        "fomekmenu_book_page": [

        ],
        "fomekmenu_obj_add_item": [
            {"label":"ID","arg":{"type":"input_value","name":"id","check":"String"}}
        ],
        "fomekmenu_obj_add_rect": [
            {"label":"ID","arg":{"type":"input_value","name":"id","check":"String"}},
            {"label":"Box","arg":{"type":"input_value","name":"box","check":"FomekMenuBox"}},
            {"label":"Color","arg":{"type":"input_value","name":"color","check":"Number"}}
        ],
        "fomekmenu_obj_add_text": [
            {"label":"ID","arg":{"type":"input_value","name":"id","check":"String"}},
            {"label":"Text","arg":{"type":"input_value","name":"text","check":"String"}},
            {"label":"Color","arg":{"type":"input_value","name":"color","check":"Number"}},
            {"label":"Anchor","arg":{"type":"field_dropdown","name":"pivot","options":[["top-left","top-left"],["top","top"],["top-right","top-right"],["left","left"],["center","center"],["right","right"],["bottom-left","bottom-left"],["bottom","bottom"],["bottom-right","bottom-right"]]}},
            {"label":"Shadow","arg":{"type":"field_checkbox","name":"shadow","checked":true}},
            {"label":"Text scale","arg":{"type":"input_value","name":"textScale","check":"Number"}}
        ],
        "fomekmenu_obj_add_texture": [
            {"label":"ID","arg":{"type":"input_value","name":"id","check":"String"}},
            {"label":"Texture","arg":{"type":"input_value","name":"texture","check":"String"}},
            {"label":"Width","arg":{"type":"input_value","name":"w","check":"Number"}},
            {"label":"Height","arg":{"type":"input_value","name":"h","check":"Number"}}
        ],
        "fomekmenu_obj_render": [
            {"label":"Anchor","arg":{"type":"field_dropdown","name":"pivot","options":[["top-left","top-left"],["top","top"],["top-right","top-right"],["left","left"],["center","center"],["right","right"],["bottom-left","bottom-left"],["bottom","bottom"],["bottom-right","bottom-right"]]}}
        ],
        "fomekmenu_render_item": [

        ],
        "fomekmenu_render_rect": [
            {"label":"Box","arg":{"type":"input_value","name":"box","check":"FomekMenuBox"}},
            {"label":"Color","arg":{"type":"input_value","name":"color","check":"Number"}},
            {"label":"Anchor","arg":{"type":"field_dropdown","name":"pivot","options":[["top-left","top-left"],["top","top"],["top-right","top-right"],["left","left"],["center","center"],["right","right"],["bottom-left","bottom-left"],["bottom","bottom"],["bottom-right","bottom-right"]]}}
        ],
        "fomekmenu_render_rect_outline": [
            {"label":"Box","arg":{"type":"input_value","name":"box","check":"FomekMenuBox"}},
            {"label":"Color","arg":{"type":"input_value","name":"color","check":"Number"}},
            {"label":"Anchor","arg":{"type":"field_dropdown","name":"pivot","options":[["top-left","top-left"],["top","top"],["top-right","top-right"],["left","left"],["center","center"],["right","right"],["bottom-left","bottom-left"],["bottom","bottom"],["bottom-right","bottom-right"]]}}
        ],
        "fomekmenu_render_text": [
            {"label":"Text","arg":{"type":"input_value","name":"text","check":"String"}},
            {"label":"Color","arg":{"type":"input_value","name":"color","check":"Number"}},
            {"label":"Anchor","arg":{"type":"field_dropdown","name":"pivot","options":[["top-left","top-left"],["top","top"],["top-right","top-right"],["left","left"],["center","center"],["right","right"],["bottom-left","bottom-left"],["bottom","bottom"],["bottom-right","bottom-right"]]}},
            {"label":"Shadow","arg":{"type":"field_dropdown","name":"shadow","options":[["with shadow","true"],["no shadow","false"]]}}
        ],
        "fomekmenu_render_texture": [
            {"label":"Texture","arg":{"type":"input_value","name":"texture","check":"String"}},
            {"label":"Width","arg":{"type":"input_value","name":"w","check":"Number"}},
            {"label":"Height","arg":{"type":"input_value","name":"h","check":"Number"}}
        ]
    };

    Object.assign(KINDS, {"fomekmenu_inputfield":{"kind":"control","title":"Input field"},"fomekmenu_text_area":{"kind":"control","title":"Text area"},"fomekmenu_checkbox":{"kind":"control","title":"Check box"},"fomekmenu_dropdown":{"kind":"control","title":"Dropdown"}});
    Object.assign(DETAILS, {"fomekmenu_inputfield":[{"label":"Box","arg":{"type":"input_value","name":"box","check":"FomekMenuBox"}},{"label":"Default text / selection","arg":{"type":"input_value","name":"initial","check":"String"}},{"label":"Placeholder / label","arg":{"type":"input_value","name":"label","check":"String"}},{"label":"Maximum characters","arg":{"type":"input_value","name":"maxLength","check":"Number"}}],"fomekmenu_text_area":[{"label":"Box","arg":{"type":"input_value","name":"box","check":"FomekMenuBox"}},{"label":"Default text / selection","arg":{"type":"input_value","name":"initial","check":"String"}},{"label":"Placeholder / label","arg":{"type":"input_value","name":"label","check":"String"}},{"label":"Maximum characters","arg":{"type":"input_value","name":"maxLength","check":"Number"}}],"fomekmenu_checkbox":[{"label":"Box","arg":{"type":"input_value","name":"box","check":"FomekMenuBox"}},{"label":"Default text / selection","arg":{"type":"input_value","name":"initial","check":"String"}},{"label":"Placeholder / label","arg":{"type":"input_value","name":"label","check":"String"}},{"label":"Maximum characters","arg":{"type":"input_value","name":"maxLength","check":"Number"}},{"label":"Checked by default","arg":{"type":"input_value","name":"checked","check":"Boolean"}}],"fomekmenu_dropdown":[{"label":"Box","arg":{"type":"input_value","name":"box","check":"FomekMenuBox"}},{"label":"Default text / selection","arg":{"type":"input_value","name":"initial","check":"String"}},{"label":"Placeholder / label","arg":{"type":"input_value","name":"label","check":"String"}},{"label":"Maximum characters","arg":{"type":"input_value","name":"maxLength","check":"Number"}},{"label":"Items (list or lines of text)","arg":{"type":"input_value","name":"items"}},{"label":"Visible list rows","arg":{"type":"input_value","name":"listRows","check":"Number"}}]});

    // per-kind option rows: { t: row block type, in: repeating input prefix,
    // check: value socket type } — must match the registered mutator configs.
    var C = { check: function (inName) { return { t: 'fomekmenu_mutator_check', in_: inName, check: 'String' }; },
              stick: { t: 'fomekmenu_mutator_stick', in_: 'stick', check: 'Boolean' },
              collision: { t: 'fomekmenu_mutator_collision', in_: 'collision', check: 'Boolean' } };
    var ATTRS = ['fomekmenu_attr_draggable', 'fomekmenu_attr_resize', 'fomekmenu_attr_double_click',
                 'fomekmenu_attr_drop_target', 'fomekmenu_attr_exclude_from_grid', 'fomekmenu_attr_right_click',
                 'fomekmenu_attr_button', 'fomekmenu_attr_empty'];

    var ROWSET = {
        control: [C.check("checks"), C.stick, C.collision],
        button:  [C.check('checks'), C.stick, C.collision],
        slider:  [C.check('checks'), C.stick, C.collision],
        panel:   [{ t: 'fomekmenu_mutator_attribute', in_: 'panel_attributes', check: 'FomekMenuAttribute' },
                  C.stick, C.collision,
                  { t: 'fomekmenu_mutator_grid', in_: 'grid', check: 'FomekMenuAttribute' },
                  { t: 'fomekmenu_mutator_scale', in_: 'scale', check: 'Boolean' }],
        scroll:  [C.stick, C.collision, { t: 'fomekmenu_mutator_grid', in_: 'grid', check: 'FomekMenuAttribute' }],
        obj_item:    [C.check('mt_checks'), C.stick, C.collision],
        obj_rect:    [C.check('mt_checks'), C.stick, C.collision],
        obj_text:    [C.check('mt_checks'), C.stick, C.collision],
        obj_texture: [C.check('mt_checks'), C.stick, C.collision],
        render: [{ t: 'fomekmenu_mutator_check', in_: 'mt_checks', check: 'String' },
                 { t: 'fomekmenu_mutator_attribute', in_: 'mt_attrs', check: 'FomekMenuAttribute' },
                 C.stick, C.collision],
        book:    [],
        page:    []
    };

    // blocks that may only exist inside the studio popup (or connected
    // to a studio host's hidden mutator inputs)
    var POPUP_ONLY = {};
    Object.keys(ROWSET).forEach(function (k) {
        ROWSET[k].forEach(function (r) { POPUP_ONLY[r.t] = true; });
    });
    POPUP_ONLY['fomekmenu_mutator_container'] = true;
    POPUP_ONLY['fomekmenu_mutator_input'] = true;
    ATTRS.forEach(function (a) { POPUP_ONLY[a] = true; });
    POPUP_ONLY['fomekmenu_grid'] = true;
    var ACTIONS = ['HOVER', 'CLICK', 'CHECK', 'UPDATE'];
    ACTIONS.forEach(function (event) { POPUP_ONLY['fomekmenu_studio_on_' + event.toLowerCase()] = true; });
    POPUP_ONLY.fomekmenu_studio_style = true;

    function settingsType(hostType) { return 'fomekmenu_studio_settings_' + hostType; }

    // Editor-only rows are persisted separately from the generated procedure inputs.
    // Removing a row therefore remains meaningful after reopening and recompiling.
    var STYLE_STATES = ['NORMAL', 'HOVER', 'CLICK', 'HELD'];
    var STYLE_PARTS = ['text', 'placeholder', 'label', 'indicator', 'list', 'selection'];
    var STYLE_PROPS = [
        {name:'background',label:'Background color',check:'Number',value:-14342875},
        {name:'border',label:'Border color',check:'Number',value:-8947849},
        {name:'borderWidth',label:'Border width',check:'Number',value:1},
        {name:'textColor',label:'Text color',check:'Number',value:-1},
        {name:'text',label:'Text',check:'String',value:''},
        {name:'font',label:'Font resource',check:'String',value:'minecraft:default'},
        {name:'size',label:'Font size',check:'Number',value:9},
        {name:'texture',label:'Texture',check:'String',value:''},
        {name:'hasText',label:'Show text',check:'Boolean',value:true,field:true},
        {name:'buttonStyle',label:'Surface',options:[['Flat','flat'],['Outline','outline'],['Raised','raised'],['Text only','none']],value:'flat',field:true},
        {name:'smooth',label:'Smooth text',check:'Boolean',value:true,field:true},
        {name:'paddingX',label:'Horizontal padding',check:'Number',value:4},
        {name:'paddingY',label:'Vertical padding',check:'Number',value:3},
        {name:'align',label:'Text alignment',options:[['Left','left'],['Center','center'],['Right','right']],value:'left',field:true},
        {name:'shadow',label:'Text shadow',check:'Boolean',value:false,field:true}
    ];
    function chain(block, input) {
        var out=[],next=block && block.getInputTargetBlock(input);
        while(next){out.push(next);next=next.getNextBlock();}
        return out;
    }
    function appendChain(parent,input,block) {
        var rows=chain(parent,input),connection=rows.length?rows[rows.length-1].nextConnection:parent.getInput(input).connection;
        connection.connect(block.previousConnection);
    }
    function settingType(type,name){return 'fomekmenu_studio_setting_'+type+'_'+name;}
    function settingPart(type,name){
        if(name==='initial'||name==='text')return 'text';
        if(name==='label')return type==='fomekmenu_checkbox'?'label':'placeholder';
        if(name==='items')return 'list';
        if(name==='checked')return 'indicator';
        return null;
    }
    Object.keys(KINDS).forEach(function(type){
        var cap=settingsType(type);POPUP_ONLY[cap]=true;
        Blockly.Blocks[cap]={init:function(){
            this.appendDummyInput().appendField(KINDS[type].title);
            this.appendStatementInput('SETTINGS').setCheck('FomekStudioSetting').appendField('Settings');
            this.appendStatementInput('STACK').setCheck('FomekStudioOption').appendField('Options');
            this.appendStatementInput('ACTIONS').setCheck('FomekStudioAction').appendField('Actions');
            this.appendStatementInput('STYLE').setCheck(['FomekStudioStyle','FomekStyleProperty']).appendField('Style');
            this.setColour(160);this.contextMenu=false;
        }};
        DETAILS[type].forEach(function(setting){
            var arg=setting.arg,name=settingType(type,arg.name);POPUP_ONLY[name]=true;
            Blockly.Blocks[name]={init:function(){
                if(arg.type.indexOf('field_')===0){
                    var field=arg.type==='field_dropdown'?new Blockly.FieldDropdown(arg.options):arg.type==='field_checkbox'?new Blockly.FieldCheckbox(arg.checked?'TRUE':'FALSE'):new Blockly.FieldTextInput(arg.text||'');
                    this.appendDummyInput().appendField(setting.label).appendField(field,'VALUE');
                }else this.appendValueInput('VALUE').setCheck(arg.check||null).appendField(setting.label);
                if(settingPart(type,arg.name))this.appendStatementInput('STYLE').setCheck(['FomekStyleProperty','FomekStudioStyle']).appendField('Style');
                this.setPreviousStatement(true,'FomekStudioSetting');this.setNextStatement(true,'FomekStudioSetting');this.setColour(160);
            }};
        });
    });
    // Local copy of the mutator-row table that fomekmenu.js also defines. MCreator
    // concatenates all plugin blockly js files into ONE script and runs it once,
    // in arbitrary order, so a file must never depend on another file's globals.
    // Keep in sync with the table in fomekmenu.js.
    var FOMEKMENU_MUTATOR_ROWS = {
    'fomekmenu_mutator_check':           { label: 'Check',           check: 'String' },
    'fomekmenu_mutator_attribute':       { label: 'Interactions',    check: 'FomekMenuAttribute' },
    'fomekmenu_mutator_stick':           { label: 'Stick',           check: 'Boolean' },
    'fomekmenu_mutator_collision':       { label: 'Collision',       check: 'Boolean' },
    'fomekmenu_mutator_min_resize_x':    { label: 'Min Resize X',    check: 'Number' },
    'fomekmenu_mutator_min_resize_y':    { label: 'Min Resize Y',    check: 'Number' },
    'fomekmenu_mutator_max_resize_x':    { label: 'Max Resize X',    check: 'Number' },
    'fomekmenu_mutator_max_resize_y':    { label: 'Max Resize Y',    check: 'Number' },
    'fomekmenu_mutator_drag_out':       { label: 'Drag Out',        check: 'Boolean' },
    'fomekmenu_mutator_drag_in':        { label: 'Drag In',         check: 'Boolean' },
    'fomekmenu_mutator_grid':            { label: 'Grid',            check: 'FomekMenuAttribute' },
    'fomekmenu_mutator_snap_to_end':     { label: 'Snap To End',     check: 'Boolean' },
    'fomekmenu_mutator_cell_size':       { label: 'Cell Size',       check: 'Number' },
    'fomekmenu_mutator_highlight':       { label: 'Highlight',       check: 'Boolean' },
    'fomekmenu_mutator_highlight_color': { label: 'Highlight Color',  check: 'Number' },
    'fomekmenu_mutator_corner_only':     { label: 'Corner Only',     check: 'Boolean' },
    'fomekmenu_mutator_drag_bounds':     { label: 'Drag Bounds',     check: 'FomekMenuBox' },
    'fomekmenu_mutator_resize_bounds':   { label: 'Resize Bounds',   check: 'FomekMenuBox' },
    'fomekmenu_mutator_aspect_ratio':    { label: 'Aspect Ratio',    check: 'Boolean' },
    'fomekmenu_mutator_render_grid':     { label: 'Render Grid',     check: 'Boolean' },
    'fomekmenu_mutator_scale':           { label: 'Scale Content',   check: 'Boolean' },
    'fomekmenu_mutator_grid_color':     { label: 'Grid Color',      check: 'Number' },
    'fomekmenu_mutator_begin_x':         { label: 'Begin X',         check: 'Number' },
    'fomekmenu_mutator_begin_y':         { label: 'Begin Y',         check: 'Number' }
    };
    // These option-row blocks are defined in fomekmenu.js. Plugin js files run in
    // arbitrary order (MCreator concatenates them into one script), so the patching
    // must be deferred until every file has executed.
    setTimeout(function(){
        if (typeof FOMEKMENU_MUTATOR_ROWS === 'undefined') return; // defined in fomekmenu.js (arbitrary load order)
        Object.keys(FOMEKMENU_MUTATOR_ROWS).forEach(function(type){
            var base=Blockly.Blocks[type];
            if(!base||!base.init)return; // block not defined (yet): skip
            var original=base.init;
            base.init=function(){original.call(this);this.setPreviousStatement(true,'FomekStudioOption');this.setNextStatement(true,'FomekStudioOption');this.contextMenu=true;};
        });
    },0);
    POPUP_ONLY.fomekmenu_studio_style_hat=true;
    Blockly.Blocks.fomekmenu_studio_style_hat={init:function(){
        this.appendDummyInput().appendField('Style').appendField(new Blockly.FieldDropdown([['Normal','NORMAL'],['Hovered','HOVER'],['Clicked','CLICK'],['Held','HELD']]),'STATE');
        this.appendStatementInput('PROPERTIES').setCheck('FomekStyleProperty');
        this.setPreviousStatement(true,'FomekStudioStyle');this.setNextStatement(true,'FomekStudioStyle');this.setColour(280);
    }};
    STYLE_PROPS.forEach(function(prop){
        var type='fomekmenu_studio_property_'+prop.name;POPUP_ONLY[type]=true;
        Blockly.Blocks[type]={init:function(){
            if(prop.options)this.appendDummyInput().appendField(prop.label).appendField(new Blockly.FieldDropdown(prop.options),'VALUE');
            else this.appendValueInput('VALUE').setCheck(prop.check).appendField(prop.label);
            this.setPreviousStatement(true,'FomekStyleProperty');this.setNextStatement(true,'FomekStyleProperty');this.setColour(280);
        }};
    });
    STYLE_PARTS.forEach(function(part){
        var type='fomekmenu_studio_part_'+part;POPUP_ONLY[type]=true;
        Blockly.Blocks[type]={init:function(){
            this.appendDummyInput().appendField(part.charAt(0).toUpperCase()+part.slice(1));
            this.appendStatementInput('STYLE').setCheck(['FomekStyleProperty','FomekStudioStyle']).appendField('Style');
            this.setPreviousStatement(true,'FomekStyleProperty');this.setNextStatement(true,'FomekStyleProperty');this.setColour(260);
        }};
    });
    function literalDom(check,value){
        var type=check==='Number'?'math_number':check==='Boolean'?'logic_boolean':'text';
        var dom=document.createElement('block');dom.setAttribute('type',type);
        var field=document.createElement('field');field.setAttribute('name',check==='Number'?'NUM':check==='Boolean'?'BOOL':'TEXT');
        field.textContent=check==='Boolean'?(value?'TRUE':'FALSE'):String(value);dom.appendChild(field);return dom;
    }
    function fallback(setting){
        var arg=setting.arg,name=arg.name;
        if(arg.type.indexOf('field_')===0)return arg.options?arg.options[0][1]:arg.type==='field_checkbox'?'FALSE':arg.text||'';
        if(arg.check==='Boolean')return literalDom('Boolean',false);
        if(arg.check==='Number')return literalDom('Number',name==='maxLength'?2147483647:name==='listRows'?5:name==='textScale'?1:name==='color'?-1:0);
        if(arg.check==='FomekMenuBox')return textToDom('<block type="fomekmenu_box">'+['x1','y1','x2','y2'].map(function(n){return '<value name="'+n+'">'+Blockly.Xml.domToText(literalDom('Number',0))+'</value>';}).join('')+'</block>');
        if(arg.check==='FomekMenuType')return textToDom('<block type="fomekmenu_type_empty"></block>');
        return literalDom('String','');
    }
    function setHostValue(host,name,dom){
        var old=host.getInputTargetBlock(name),next=dom?Blockly.Xml.domToText(dom):'';
        if(valueState(old)===next)return;
        var input=host.getInput(name);if(!input)return;
        if(input.connection)input.connection.setShadowDom(null);
        if(old)old.dispose(false);
        if(dom){var b=Blockly.Xml.domToBlock(dom,host.workspace);input.connection.connect(b.outputConnection||b.previousConnection);}
    }
    function propertyDom(prop,valueBlock,fieldValue){
        var d=document.createElement('block');d.setAttribute('type','fomekmenu_studio_property_'+prop.name);
        var v=document.createElement(prop.options?'field':'value');v.setAttribute('name','VALUE');
        if(prop.options)v.textContent=fieldValue===undefined?prop.value:fieldValue;
        else v.appendChild(valueBlock?Blockly.Xml.blockToDom(valueBlock,true):literalDom(prop.check,fieldValue===undefined?prop.value:prop.check==='Boolean'?String(fieldValue).toLowerCase()==='true':fieldValue));
        d.appendChild(v);return d;
    }
    // One style row folded into a model. Accepted anywhere in a style chain:
    // property rows, part blocks, and state hats (their properties merge into
    // the model; the hat is a grouping device inside part/row sockets).
    function foldRow(model,row){
        if(row.type==='fomekmenu_studio_style_hat'){var sub=styleModel(row,'PROPERTIES',{});Object.keys(sub).forEach(function(k){if(k!=='parts')model[k]=sub[k];});Object.keys(sub.parts||{}).forEach(function(k){model.parts[k]=sub.parts[k];});return model;}
        if(row.type.indexOf('fomekmenu_studio_part_')===0){var part=row.type.replace('fomekmenu_studio_part_','');model.parts[part]=styleModel(row,'STYLE',model.parts[part]||{});return model;}
        var key=row.type.replace('fomekmenu_studio_property_','');
        var def=STYLE_PROPS.find(function(p){return p.name===key;});if(!def)return model;
        model[key]=def.options?row.getFieldValue('VALUE'):row.getInputTargetBlock('VALUE');return model;
    }
    function styleModel(parent,input,base){
        var props=Object.assign({},base||{}),parts=Object.assign({},props.parts||{});props.parts=parts;
        chain(parent,input).forEach(function(row){foldRow(props,row);});return props;
    }
    function styleDom(model){
        var root=document.createElement('block');root.setAttribute('type','fomekmenu_studio_style');
        STYLE_PROPS.forEach(function(prop){
            var value=model[prop.name],node=document.createElement(prop.field?'field':'value');node.setAttribute('name',prop.name);
            if(prop.field){node.textContent=prop.options?(typeof value==='string'?value:prop.value):value&&value.getFieldValue?String(value.getFieldValue('BOOL')):(prop.value?'TRUE':'FALSE');}
            else if(value&&value.studioScale){var multiply=textToDom('<block type="math_arithmetic"><field name="OP">MULTIPLY</field><value name="B"><block type="math_number"><field name="NUM">9</field></block></value></block>');var scale=document.createElement('value');scale.setAttribute('name','A');scale.appendChild(Blockly.Xml.blockToDom(value.studioScale,true));multiply.appendChild(scale);node.appendChild(multiply);}
            else node.appendChild(value&&value.workspace?Blockly.Xml.blockToDom(value,true):literalDom(prop.check,value===undefined?prop.value:value));
            root.appendChild(node);
            if(prop.field&&prop.check==='Boolean'){var expression=document.createElement('value');expression.setAttribute('name','BOOL_'+prop.name);expression.appendChild(value&&value.workspace?Blockly.Xml.blockToDom(value,true):literalDom('Boolean',value===undefined?prop.value:!!value));root.appendChild(expression);}
        });
        STYLE_PARTS.forEach(function(part){if(!model.parts||!model.parts[part])return;var v=document.createElement('value');v.setAttribute('name','PART_'+part);var m=Object.assign({},model,model.parts[part],{parts:{}});v.appendChild(styleDom(m));root.appendChild(v);});
        return root;
    }
    function migrateStudio(host,cap){
        DETAILS[host.type].forEach(function(setting){
            var arg=setting.arg,row=S.ws.newBlock(settingType(host.type,arg.name));row.initSvg();
            if(arg.type.indexOf('field_')===0)row.setFieldValue(host.getFieldValue(arg.name),'VALUE');
            else if(host.getInputTargetBlock(arg.name))copyTo(host.getInputTargetBlock(arg.name),S.ws,row.getInput('VALUE').connection);
            row.render();appendChain(cap,'SETTINGS',row);
        });
        if(hostCanDecompose(host)){
            var old=host.decompose(S.ws),first=old.getInputTargetBlock('STACK');
            if(first){first.previousConnection.disconnect();cap.getInput('STACK').connection.connect(first.previousConnection);}old.dispose(false);
            var inputs=host.inputList.filter(function(i){return isMutInput(host,i.name);});
            chain(cap,'STACK').forEach(function(row,i){var value=inputs[i]&&inputs[i].connection.targetBlock();if(value)copyTo(value,S.ws,row.getInput('VALUE').connection);});
        }
        ACTIONS.forEach(function(event){var body=host.getInputTargetBlock('STUDIO_'+event);if(!body)return;var hat=S.ws.newBlock('fomekmenu_studio_on_'+event.toLowerCase());hat.initSvg();copyTo(body,S.ws,hat.getInput('DO').connection);hat.render();appendChain(cap,'ACTIONS',hat);});
        STYLE_STATES.forEach(function(state){var source=host.getInputTargetBlock('STUDIO_'+state+'_STYLE');if(!source)return;var hat=S.ws.newBlock('fomekmenu_studio_style_hat');hat.setFieldValue(state,'STATE');hat.initSvg();hat.render();
            STYLE_PROPS.forEach(function(prop){var value=source.getInputTargetBlock(prop.name)||(prop.check==='Boolean'?source.getInputTargetBlock('BOOL_'+prop.name):null),field=prop.field?source.getFieldValue(prop.name):undefined;
                if(!value&&field==null)return;var row=Blockly.Xml.domToBlock(propertyDom(prop,value,field),S.ws);appendChain(hat,'PROPERTIES',row);});appendChain(cap,'STYLE',hat);
        });
    }
    // First open (no saved layout): a few starter blocks so it's obvious
    // what each section is for. Existing content is never duplicated.
    function seedDefaults(cap,host){
        var kind=KINDS[host.type]?KINDS[host.type].kind:null;
        if(!chain(cap,'STYLE').length){
            var hat=S.ws.newBlock('fomekmenu_studio_style_hat');hat.setFieldValue('NORMAL','STATE');hat.initSvg();hat.render();appendChain(cap,'STYLE',hat);
            ['background','textColor'].forEach(function(name){
                var prop=STYLE_PROPS.find(function(p){return p.name===name;});if(!prop)return;
                appendChain(hat,'PROPERTIES',Blockly.Xml.domToBlock(propertyDom(prop),S.ws));
            });
        }
        if(!chain(cap,'ACTIONS').length){
            var click=S.ws.newBlock('fomekmenu_studio_on_click');click.initSvg();click.render();appendChain(cap,'ACTIONS',click);
        }
        // Example Check row only for elements that were never configured
        // before: adding a check to an existing element would hide it.
        if(kind){var rowCfg=(ROWSET[kind]||[]).find(function(r){return r.t==='fomekmenu_mutator_check';});
            if(rowCfg&&!host.inputList.some(function(i){return isMutInput(host,i.name);})){
                var row=S.ws.newBlock('fomekmenu_mutator_check');row.initSvg();row.render();
                var d=Blockly.Xml.domToBlock(literalDom('String','check_id'),S.ws);
                row.getInput('VALUE').connection.connect(d.outputConnection);
                appendChain(cap,'STACK',row);
            }}
    }
    function saveLayout(host){
        var xml=Blockly.Xml.workspaceToDom(S.ws);
        // Loose shared blocks return to the main workspace on close, rather than
        // being duplicated in this editor's saved layout.
        Array.prototype.slice.call(xml.children).forEach(function(el){if(el.tagName.toLowerCase()==='block'&&!POPUP_ONLY[el.getAttribute('type')])xml.removeChild(el);});
        var saved=Blockly.Xml.domToText(xml);if(host.getFieldValue('STUDIO_LAYOUT')!==saved)host.setFieldValue(saved,'STUDIO_LAYOUT');
    }
    function styleBase(host){
        var base={parts:{}};
        if(host.getInputTargetBlock('color'))base.background=host.getInputTargetBlock('color');
        if(host.type.indexOf('rect')>=0){base.borderWidth=host.type.indexOf('outline')>=0?1:0;if(base.borderWidth){base.buttonStyle='outline';base.border=base.background;}}
        if(host.type.indexOf('text')>=0&&host.type.indexOf('texture')<0&&host.type!=='fomekmenu_text_area'){base.buttonStyle='none';if(host.getInputTargetBlock('color'))base.textColor=host.getInputTargetBlock('color');if(host.getInputTargetBlock('textScale'))base.size={studioScale:host.getInputTargetBlock('textScale')};base.shadow=String(host.getFieldValue('shadow')).toLowerCase()==='true';}
        return base;
    }
    function syncDetails(host){
        var settings=chain(S.cap,'SETTINGS');
        DETAILS[host.type].forEach(function(setting){
            var arg=setting.arg,row=settings.find(function(b){return b.type===settingType(host.type,arg.name);});
            if(arg.type.indexOf('field_')===0){var value=row?row.getFieldValue('VALUE'):fallback(setting);if(host.getFieldValue(arg.name)!==value)host.setFieldValue(value,arg.name);}
            else{var block=row&&row.getInputTargetBlock('VALUE');setHostValue(host,arg.name,block?Blockly.Xml.blockToDom(block,true):fallback(setting));}
        });
        ACTIONS.forEach(function(event){
            var hats=chain(S.cap,'ACTIONS').filter(function(b){return b.type==='fomekmenu_studio_on_'+event.toLowerCase();}),xml=null,tail=null;
            hats.forEach(function(hat){var body=hat.getInputTargetBlock('DO');if(!body)return;var dom=Blockly.Xml.blockToDom(body,true);if(!xml)xml=dom;else{var next=document.createElement('next');next.appendChild(dom);tail.appendChild(next);}tail=dom;while(tail.querySelector(':scope > next > block'))tail=tail.querySelector(':scope > next > block');});
            setHostValue(host,'STUDIO_'+event,xml);
        });
        var styles=chain(S.cap,'STYLE');
        var hats=styles.filter(function(b){return b.type==='fomekmenu_studio_style_hat';});
        var bare=styles.filter(function(b){return b.type!=='fomekmenu_studio_style_hat';});
        // bare property/part rows dropped straight into the Style section act as Normal styling
        var base=bare.length?bare.reduce(function(model,b){return foldRow(model,b);},styleBase(host)):null;
        var normalHeaders=hats.filter(function(b){return b.getFieldValue('STATE')==='NORMAL';});
        if(normalHeaders.length)base=normalHeaders.reduce(function(model,b){return styleModel(b,'PROPERTIES',model);},base||styleBase(host));
        settings.forEach(function(row){var setting=DETAILS[host.type].find(function(s){return settingType(host.type,s.arg.name)===row.type;});if(!setting||!row.getInput('STYLE')||!row.getInputTargetBlock('STYLE'))return;var part=settingPart(host.type,setting.arg.name);if(!base)base=styleBase(host);if(!base.parts)base.parts={};base.parts[part]=styleModel(row,'STYLE',base.parts[part]||{});});
        STYLE_STATES.forEach(function(state){var headers=hats.filter(function(b){return b.getFieldValue('STATE')===state;}),model=state==='NORMAL'?base:headers.length?headers.reduce(function(model,b){return styleModel(b,'PROPERTIES',model);},base||styleBase(host)):state==='NORMAL'?base:null;setHostValue(host,'STUDIO_'+state+'_STYLE',model?styleDom(model):null);});
        saveLayout(host);
    }

    function rowFor(kind, type) {
        var set = ROWSET[kind] || [];
        for (var i = 0; i < set.length; i++) if (set[i].t === type) return set[i];
        return null;
    }

    function getMainWs() {
        try {
            if (typeof workspace !== 'undefined' && workspace && workspace.getAllBlocks) return workspace;
        } catch (e) {}
        try { return Blockly.getMainWorkspace(); } catch (e) {}
        return null;
    }

    function textToDom(t) {
        var u = (typeof Blockly !== 'undefined') && Blockly.utils;
        var f = (u && u.xml && u.xml.textToDom) || (Blockly.Xml && Blockly.Xml.textToDom);
        return f ? f(t) : null;
    }

    // Use Blockly 9's native mutator slot and event handling (MCreator 2026.1).
    // Install during jsonInit, including flyouts and insertion markers: adding
    // an input after creation makes the drag preview's shape differ from its source.
    Blockly.Extensions.register('fomekmenu_studio_icon', function () {
        var block = this;
        if (block.getField("STUDIO_KEY") && !block.getFieldValue("STUDIO_KEY")) block.setFieldValue("studio_" + block.id.replace(/[^a-zA-Z0-9]/g, ""), "STUDIO_KEY");
        if (block.getField('STUDIO_KEY')) block.getField('STUDIO_KEY').setValidator(function (value) {
            var duplicate = block.workspace.getAllBlocks(false).some(function (other) {
                return other !== block && other.type in KINDS && other.getFieldValue('STUDIO_KEY') === value;
            });
            return duplicate ? 'studio_' + block.id.replace(/[^a-zA-Z0-9]/g, '') : value;
        });
        var icon = new Blockly.Mutator([], block);
        icon.drawIcon_ = function (group) {
            var svg = Blockly.utils.dom.createSvgElement;
            svg('rect', { 'class': 'blocklyIconShape', rx: '4', ry: '4',
                width: '16', height: '16' }, group);
            svg('path', { 'class': 'blocklyIconSymbol',
                d: 'M3,10.5 L3,13 L5.5,13 L11.5,7 L9,4.5 Z '
                    + 'M9.8,3.7 L11,2.5 Q11.5,2 12,2.5 L13.5,4 '
                    + 'Q14,4.5 13.5,5 L12.3,6.2 Z' }, group);
            group.setAttribute('aria-label', 'Edit ' + KINDS[block.type].title);
            svg('title', {}, group).textContent = 'Edit ' + KINDS[block.type].title;
        };
        // Keep native iconClick_: it ignores dragging, flyouts and right clicks.
        icon.setVisible = function (visible) {
            if (visible && !block.isInFlyout && block.isEditable()
                    && !(block.isInsertionMarker && block.isInsertionMarker())) {
                window.__fomekStudioOpen(block);
            }
        };
        block.setMutator(icon);
        block.fomekHideStudioInputs_ = function () { hideAllMutInputs(block); };
        // Keep visibility synchronous with Blockly's render pass. A deferred
        // create listener must never re-layout a block during its first drag.
        var render = block.render;
        block.render = function () {
            hideAllMutInputs(this);
            return render.apply(this, arguments);
        };
        var setCollapsed = block.setCollapsed;
        block.setCollapsed = function (collapsed) {
            setCollapsed.call(this, collapsed);
            if (!collapsed) hideAllMutInputs(this);
        };
        hideAllMutInputs(block);
    });

    function mutInputNames(host) {
        // prefixes used by the host's mutator, taken from its kind rowset
        var k = KINDS[host.type];
        var rows = k ? ROWSET[k.kind] : [];
        var names = [];
        rows.forEach(function (r) { names.push(r.in_); });
        return names;
    }

    function isMutInput(host, name) {
        var names = mutInputNames(host);
        for (var i = 0; i < names.length; i++) {
            var p = names[i];
            if (name === p || (name.indexOf(p) === 0 && /^\d+$/.test(name.substring(p.length)))) return true;
        }
        return false;
    }

    function hideAllMutInputs(host) {
        try {
            var details = DETAILS[host.type] || [];
            var hidden = details.map(function (setting) {
                return setting.arg.type.indexOf('field_') === 0
                    ? 'FOMEK_STUDIO_FIELD_' + setting.arg.name : setting.arg.name;
            });
            for (var i = 0; i < host.inputList.length; i++) {
                var inp = host.inputList[i];
                if ((hidden.indexOf(inp.name) !== -1 || inp.name.indexOf('STUDIO_') === 0 || isMutInput(host, inp.name)) && inp.setVisible) {
                    try { inp.setVisible(false); } catch (e) {}
                    var child = inp.connection && inp.connection.targetBlock();
                    if (child && child.getSvgRoot()) child.getSvgRoot().style.display = 'none';
                }
            }
        } catch (e) {}
    }

    function sweep(ws) {
        try {
            ws.getAllBlocks(false).forEach(function (block) {
                if (block.type in KINDS) hideAllMutInputs(block);
            });
        } catch (e) {}
    }

    // Blockly's drag lifecycle works in JavaFX WebView as well as Chromium.
    // This is the same live source/target approach used by MacroForge's studio;
    // it deliberately does not depend on pointerup or Blockly.getSelected().
    function installTransfer(main){
        var prototype=Blockly.BlockDragger&&Blockly.BlockDragger.prototype;
        if(!prototype||prototype.fomekStudioTransfer)return;
        prototype.fomekStudioTransfer=true;
        function inside(rect,event){return event.clientX>=rect.left&&event.clientX<rect.right&&event.clientY>=rect.top&&event.clientY<rect.bottom;}
        function move(copy,x,y){var p=copy.getRelativeToSurfaceXY();copy.moveBy(x-p.x,y-p.y);}
        function discard(dragger){var live=dragger.fomekLive;if(!live)return;if(live.copy&&!live.copy.isDisposed())live.copy.dispose(false);if(!live.source.isDisposed())live.source.getSvgRoot().style.opacity='';dragger.fomekLive=null;S.live=false;}
        function snap(copy){
            var plug=copy.outputConnection||copy.previousConnection;if(!plug)return;
            var nearest=null,radius=45/copy.workspace.scale,desc=copy.getDescendants(false);
            copy.workspace.getAllBlocks(false).forEach(function(b){if(desc.indexOf(b)>=0)return;b.getConnections_(false).forEach(function(c){
                if(c.isConnected()||!copy.workspace.connectionChecker.canConnect(plug,c,false))return;
                var distance=Math.hypot(c.x-plug.x,c.y-plug.y);if(distance<radius){nearest=c;radius=distance;}
            });});if(nearest)plug.connect(nearest);
        }
        var start=prototype.startDrag,drag=prototype.drag,end=prototype.endDrag,dispose=prototype.dispose;
        prototype.startDrag=function(){
            var block=this.draggingBlock_;
            if(S.open&&block&&block.workspace===S.ws){var plug=block.outputConnection||block.previousConnection;var p=block.getRelativeToSurfaceXY();this.fomekOrigin={x:p.x,y:p.y,connection:plug&&plug.targetConnection};}
            return start.apply(this,arguments);
        };
        prototype.drag=function(event,delta){
            drag.call(this,event,delta);
            if(!S.open||!S.ws||!event||event.clientX==null)return;
            var block=this.draggingBlock_,source=block&&block.workspace;
            if(!block||block.isDisposed()||(source!==S.ws&&source!==S.host.workspace))return;
            var rect=S.ws.getInjectionDiv().getBoundingClientRect(),inPopup=inside(rect,event);
            var destination=inPopup?S.ws:S.host.workspace;
            var isEditor=block.getDescendants(false).some(function(b){return POPUP_ONLY[b.type];});
            if(destination===source||!inside(S.host.workspace.getInjectionDiv().getBoundingClientRect(),event)){
                if(destination===source)this.fomekReturn=false;
                discard(this);return;
            }
            // Keep editor rows alive if a drag reaches the main toolbox's delete zone.
            if(isEditor){this.wouldDeleteBlock_=false;this.dragTarget_=null;this.fomekReturn=true;return;}
            this.fomekReturn=false;
            var live=this.fomekLive;
            if(!live){
                var bounds=block.getSvgRoot().getBoundingClientRect();
                live={source:block,destination:destination,dx:event.clientX-bounds.left,dy:event.clientY-bounds.top,copy:null};
                var xml=Blockly.Xml.blockToDom(block,true);
                block.getDescendants(false).forEach(function(b){(b.getVarModels?b.getVarModels():[]).forEach(function(v){
                    var variable=destination.getVariable(v.name,v.type)||destination.createVariable(v.name,v.type,v.getId());
                    Array.prototype.forEach.call(xml.querySelectorAll('field[id]'),function(field){if(field.getAttribute('id')===v.getId())field.setAttribute('id',variable.getId());});
                });});
                S.live=true;
                try{live.copy=Blockly.Xml.domToBlock(xml,destination);live.copy.getSvgRoot().style.opacity='.75';block.getSvgRoot().style.opacity='0';this.fomekLive=live;}
                catch(error){S.live=false;console.error('Studio transfer',error);return;}
            }
            var p=Blockly.utils.svgMath.screenToWsCoordinates(destination,{x:event.clientX-live.dx,y:event.clientY-live.dy});
            move(live.copy,p.x,p.y);
            this.wouldDeleteBlock_=false;this.dragTarget_=null;
        };
        prototype.endDrag=function(event,delta){
            try{end.call(this,event,delta);}catch(error){discard(this);throw error;}
            var live=this.fomekLive;
            if(live&&live.copy){
                this.fomekLive=null;
                live.copy.getSvgRoot().style.opacity='';
                if(!live.source.isDisposed())live.source.dispose(false);
                snap(live.copy);live.copy.select();S.live=false;scheduleSync();
            }else if(this.fomekReturn&&this.draggingBlock_&&!this.draggingBlock_.isDisposed()){
                var original=this.fomekOrigin,block=this.draggingBlock_,plug=block.outputConnection||block.previousConnection;
                move(block,original?original.x:24,original?original.y:24);
                if(original&&original.connection&&plug&&!plug.isConnected()&&!original.connection.isConnected()&&block.workspace.connectionChecker.canConnect(plug,original.connection,false))plug.connect(original.connection);
                this.fomekReturn=false;scheduleSync();
            }
        };
        prototype.dispose=function(){discard(this);return dispose.apply(this,arguments);};
    }

    // ── escape guard: popup-only blocks may not live on the main canvas ──
    function installEscapeGuard(ws) {
        ws.addChangeListener(function (ev) {
            try {
                if (!ev || !ev.blockId) return;
                var b = ws.getBlockById(ev.blockId);
                if (!b || !(b.type in POPUP_ONLY)) return;
                if (b.__fomekStudio) return; // created by the studio's own round-trip
                if (ev.type !== 'create' && ev.type !== 'move') return;
                setTimeout(function () {
                    try {
                        if (b.disposed || b.isDeadOrDying && b.isDeadOrDying()) return;
                        if (b.__fomekStudio) return;
                        var parent = b.getParent();
                        // connected into a studio host's hidden inputs = legitimate
                        while (parent) {
                            if (parent.type in KINDS) return;
                            parent = parent.getParent();
                        }
                        b.dispose(false);
                    } catch (e) {}
                }, 350);
            } catch (e) {}
        });
    }

    // ── DOM + CSS (injected once) ───────────────────────────────────
    var DOM_BUILT = false, popup = null;

    function buildDom() {
        if (DOM_BUILT) return;
        var ws = getMainWs();
        var root = null;
        try { root = ws && ws.getInjectionDiv ? ws.getInjectionDiv() : null; } catch (e) {}
        if (!root) root = document.getElementById('blockly-div');
        if (!root) root = document.body;
        if (!root) return;

        var css = document.createElement('style');
        css.textContent = ''
            + '.fomek-studio{position:absolute;z-index:9000;display:none;flex-direction:column;'
            + 'background:#ffffff;border:1px solid #b8b8b8;border-radius:6px;box-shadow:0 6px 24px rgba(0,0,0,.35);'
            + 'font-family:sans-serif;font-size:12px;color:#333;'
            + 'min-width:620px;min-height:420px;resize:both;overflow:hidden;}'
            + '.fomek-studio.open{display:flex;}'
            + '.fomek-studio-head{display:flex;align-items:center;gap:8px;padding:6px 10px;cursor:move;'
            + 'flex-shrink:0;background:#2b6cb0;color:#fff;border-radius:5px 5px 0 0;user-select:none;font-weight:600;}'
            + '.fomek-studio-close{margin-left:auto;cursor:pointer;background:rgba(255,255,255,.15);'
            + 'border:none;color:#fff;border-radius:3px;padding:1px 7px;font-weight:700;}'
            + '.fomek-studio-body{display:flex;flex:1;min-height:0;}'
            + '.fomek-studio-ws{flex:1 1 auto;min-width:360px;height:100%;}'
            + '.fomek-studio-prev{width:350px;flex-shrink:0;border-left:1px solid #e2e2e2;display:flex;flex-direction:column;overflow:auto;}'
            + '.fomek-studio-prev canvas{display:block;margin:0;background:transparent;outline:none;}'
            + '.pv-tools{display:flex;gap:5px;flex-wrap:wrap;padding:8px;}.pv-tools input{width:58px;}.pv-viewport{overflow:auto;max-height:460px;min-height:240px;margin:8px;background:repeating-conic-gradient(#ddd 0% 25%,#eee 0% 50%) 0/16px 16px;}'
            + '.pv-surface{position:relative;}.pv-input{position:absolute;display:none;margin:0;padding:0;border:0;resize:none;background:transparent;color:transparent;caret-color:#ddd;outline:none;overflow:hidden;}'
            + '.pv-status{white-space:pre-wrap;color:#96531c;padding:8px;font-size:11px;}.pv-events{padding:8px;font-size:11px;}.pv-vars label{display:block;padding:4px;}.pv-vars input{width:150px;}'
            + '.fomek-studio-prev .pv-t{padding:4px 8px 0 8px;font-weight:600;color:#666;}'
            + '.fomek-studio-prev .pv-n{padding:2px 8px;color:#999;font-size:11px;}'
            + '.fomek-studio .blocklyMainBackground{stroke:none!important;}'
            + '.fomek-studio-host > .blocklyToolboxDiv{z-index:9002!important;}'
            + '.fomek-studio-host > svg > .blocklyFlyout{z-index:9001!important;}'
            + '.fomek-studio-host .blocklyBlockDragSurface{z-index:9003!important;}'
            + '.fomek-studio-host > .blocklyFlyout,.fomek-studio-host > .blocklyFlyoutScrollbar{z-index:9001!important;}'
            + 'body.fomek-studio-open .blocklyDropDownDiv,body.fomek-studio-open .blocklyWidgetDiv{z-index:10000!important;}'
            ;
        root.appendChild(css);root.classList.add('fomek-studio-host');

        popup = document.createElement('div');
        popup.className = 'fomek-studio';
        popup.id = 'fomek-studio';
        popup.innerHTML = ''
            + '<div class="fomek-studio-head" id="fomek-studio-head"><span id="fomek-studio-title">Element studio</span>'
            + '<button class="fomek-studio-close" id="fomek-studio-close">\u2715</button></div>'
            + '<div class="fomek-studio-body">'
            + '<div class="fomek-studio-ws" id="fomek-studio-ws"></div>'
            + '<div class="fomek-studio-prev" id="fomek-studio-preview"><div class="pv-t">Preview (1:1 scale)</div>'
            + '<canvas id="fomek-studio-canvas" width="214" height="240"></canvas>'
            + '<div class="pv-n" id="fomek-studio-note"></div></div>'
            + '</div>';
        root.appendChild(popup);

        document.getElementById('fomek-studio-close').onclick = function () { close(); };

        // drag by header
        (function () {
            var head = document.getElementById('fomek-studio-head');
            var sx = 0, sy = 0, ox = 0, oy = 0, drag = false;
            head.addEventListener('mousedown', function (e) {
                if (e.target.id === 'fomek-studio-close') return;
                drag = true; sx = e.clientX; sy = e.clientY;
                var r = popup.getBoundingClientRect(), pr = popup.parentElement.getBoundingClientRect();
                ox = r.left - pr.left; oy = r.top - pr.top;
                e.preventDefault();
            });
            document.addEventListener('mousemove', function (e) {
                if (!drag) return;
                popup.style.left = (ox + e.clientX - sx) + 'px';
                popup.style.top = (oy + e.clientY - sy) + 'px';
            });
            document.addEventListener('mouseup', function () { drag = false; });
        })();
        DOM_BUILT = true;
    }

// ── flyout XML per kind ────────────────────────────────────────
    function shadowFor(check) {
        // Real editable blocks, not <shadow>: pale shadow blocks inside the
        // editor looked locked/uneditable (reported in 3.6.1 testing).
        if (check === 'String') return '<block type="text"><field name="TEXT">check_id</field></block>';
        if (check === 'Boolean') return '<block type="logic_boolean"><field name="BOOL">TRUE</field></block>';
        if (check === 'Number') return '<block type="math_number"><field name="NUM">0</field></block>';
        return '';
    }

    function flyoutXml(kind) {
        var rows = ROWSET[kind] || [];
        var optionBlocks = [];
        rows.forEach(function (r) {
            var sh = shadowFor(r.check);
            optionBlocks.push('<block type="' + r.t + '">' + (sh ? '<value name="VALUE">' + sh + '</value>' : '') + '</block>');
        });

        var interactionBlocks = [];
        if (kind === 'panel' || kind === 'scroll' || kind === 'render') {
            if (kind === 'panel' || kind === 'render') {
                ATTRS.forEach(function (a) { interactionBlocks.push('<block type="' + a + '"></block>'); });
            }
            interactionBlocks.push('<block type="fomekmenu_grid"></block>');
        }

        var cats = [];
        var type=S.host.type;
        cats.push('<category name="Settings" colour="160">'+DETAILS[type].map(function(setting){
            var arg=setting.arg,xml='<block type="'+settingType(type,arg.name)+'">';
            if(arg.type.indexOf('field_')!==0){var value=S.host.getInputTargetBlock(arg.name);xml+='<value name="VALUE">'+(value?Blockly.Xml.domToText(Blockly.Xml.blockToDom(value,true)):Blockly.Xml.domToText(fallback(setting)))+'</value>';}
            return xml+'</block>';
        }).join('')+'</category>');

        if (optionBlocks.length) {
            cats.push('<category name="Options" colour="160">' + optionBlocks.join('') + '</category>');
        }
        if (interactionBlocks.length) {
            cats.push('<category name="Interactions" colour="50">' + interactionBlocks.join('') + '</category>');
        }
        cats.push('<category name="Actions" colour="20">' + ACTIONS.map(function (event) { return '<block type="fomekmenu_studio_on_' + event.toLowerCase() + '"></block>'; }).join('') + '</category>');
        cats.push('<category name="Styles" colour="280">'+STYLE_STATES.map(function(state){return '<block type="fomekmenu_studio_style_hat"><field name="STATE">'+state+'</field></block>';}).join('')+STYLE_PROPS.map(function(prop){return Blockly.Xml.domToText(propertyDom(prop));}).join('')+STYLE_PARTS.map(function(part){return '<block type="fomekmenu_studio_part_'+part+'"></block>';}).join('')+'</category>');
        return '<xml>' + cats.join('') + '</xml>';
    }

    // ── literal readers for the preview ────────────────────────────
    function target(host, name) {
        try { return host.getInputTargetBlock(name); } catch (e) { return null; }
    }
    function num(host, name, def) {
        var b = target(host, name);
        if (b && b.type === 'math_number') { var n = parseFloat(b.getFieldValue('NUM')); return isNaN(n) ? def : n; }
        return def;
    }
    function str(host, name, def) {
        var b = target(host, name);
        if (b && b.type === 'text') return b.getFieldValue('TEXT');
        return def;
    }
    function bool(host, name, def) {
        var b = target(host, name);
        if (b && b.type === 'logic_boolean') return b.getFieldValue('BOOL') === 'TRUE';
        return def;
    }
    function color(host, name, def) {
        var b = target(host, name);
        if (!b) return def;
        if (b.type === 'fomekmenu_rgb') {
            var g = function (n) {
                var c = b.getInputTargetBlock(n);
                if (c && c.type === 'math_number') { var v = parseFloat(c.getFieldValue('NUM')); return isNaN(v) ? 0 : v; }
                return 0;
            };
            return (255 << 24) | ((g('r') & 255) << 16) | ((g('g') & 255) << 8) | (g('b') & 255);
        }
        if (b.type === 'math_number') { var n = parseFloat(b.getFieldValue('NUM')); return isNaN(n) ? def : (n | 0); }
        return def;
    }
    function argbCss(argb) {
        var a = (argb >>> 24) & 255, r = (argb >>> 16) & 255, g = (argb >>> 8) & 255, b = argb & 255;
        if (a === 255 || a === 0) a = 255; // legacy rgb ints have no alpha
        return 'rgba(' + r + ',' + g + ',' + b + ',' + (a / 255).toFixed(3) + ')';
    }

    // ── preview (1:1 canvas px = 1 GUI px) ─────────────────────────
    function drawPreview(host){
        if(!window.FomekStudioPreview||!S.open)return;
        window.FomekStudioPreview.render({host:host,
            getValue:function(block,name,read){
                var detail=DETAILS[block.type]&&DETAILS[block.type].find(function(setting){return setting.arg.name===name;});
                if(block===S.host&&detail){var row=chain(S.cap,'SETTINGS').find(function(b){return b.type===settingType(block.type,name);});
                    if(detail.arg.type.indexOf('field_')===0)return row?row.getFieldValue('VALUE'):fallback(detail);
                    if(row&&row.getInputTargetBlock('VALUE'))return read(row.getInputTargetBlock('VALUE'));
                    if(detail.arg.check==='Number')return name==='maxLength'?2147483647:name==='listRows'?5:name==='textScale'?1:name==='color'?-1:0;
                    if(detail.arg.check==='Boolean')return false;
                    if(detail.arg.check==='FomekMenuBox')return {x1:0,y1:0,x2:0,y2:0};
                    return '';
                }
                return block.getField(name)?block.getFieldValue(name):read(block.getInputTargetBlock(name));
            },
            getStyle:function(state){
                var styles=chain(S.cap,'STYLE'),normal=styles.find(function(b){return b.getFieldValue('STATE')==='NORMAL';});
                var normalHeaders=styles.filter(function(b){return b.getFieldValue('STATE')==='NORMAL';});var base=normalHeaders.length?normalHeaders.reduce(function(model,b){return styleModel(b,'PROPERTIES',model);},styleBase(host)):null;
                chain(S.cap,'SETTINGS').forEach(function(row){var detail=DETAILS[host.type].find(function(d){return row.type===settingType(host.type,d.arg.name);});if(!detail||!row.getInput('STYLE')||!row.getInputTargetBlock('STYLE'))return;if(!base)base=styleBase(host);if(!base.parts)base.parts={};var part=settingPart(host.type,detail.arg.name);base.parts[part]=styleModel(row,'STYLE',base.parts[part]||{});});
                var headers=styles.filter(function(b){return b.getFieldValue('STATE')===state;});return state==='NORMAL'?base:headers.length?headers.reduce(function(model,b){return styleModel(b,'PROPERTIES',model);},base||styleBase(host)):null;
            },
            actions:function(event){return chain(S.cap,'ACTIONS').filter(function(b){return b.type==='fomekmenu_studio_on_'+event.toLowerCase();}).map(function(b){return b.getInputTargetBlock('DO');}).filter(Boolean);}
        });
    }

    // ── studio state + open/close/sync ─────────────────────────────
    var S = { open: false, host: null, ws: null, cap: null, kind: null, syncTimer: null, prevTimer: null, resizeObs: null };

    function currentRows() {
        var out = [];
        try {
            var b = S.cap ? S.cap.getInputTargetBlock('STACK') : null;
            while (b) { out.push(b); b = b.getNextBlock(); }
        } catch (e) {}
        return out;
    }

    function copyTo(valueBlock, destWs, connection) {
        try {
            var dom = Blockly.Xml.blockToDom(valueBlock, true);
            // flyout defaults arrive as shadows: materialize them as real blocks
            var nn = (dom.nodeName || '').toLowerCase();
            if (nn === 'shadow') {
                var blk = document.createElement('block');
                for (var i = 0; i < dom.attributes.length; i++) {
                    blk.setAttribute(dom.attributes[i].name, dom.attributes[i].value);
                }
                blk.innerHTML = dom.innerHTML;
                dom = blk;
            }
            var nb = Blockly.Xml.domToBlock(dom, destWs);
            connection.connect(nb.outputConnection || nb.previousConnection);
            return nb;
        } catch (e) { return null; }
    }

    function valueState(block) {
        return block ? Blockly.Xml.domToText(Blockly.Xml.blockToDom(block, true)) : '';
    }

    function optionState() {
        return currentRows().map(function (row) {
            return row.type + ':' + valueState(row.getInputTargetBlock('VALUE'));
        }).join('\n');
    }

    function syncToHost() {
        var host = S.host;
        if (!host || !S.ws || host.isDisposed() || S.live) return;
        if (!(host.type in KINDS)) return;
        var k = KINDS[host.type];
        var group = Blockly.Events.getGroup();
        if (!group) Blockly.Events.setGroup(true);
        try {
            syncDetails(host);
            var options = optionState();
            if (ROWSET[k.kind].length === 0 || options === S.optionSnapshot) {
                hideAllMutInputs(host);
                drawPreview(host);
                return;
            }
            var oldMutation = Blockly.Xml.domToText(host.mutationToDom());
            // 1. dispose current value blocks in the host's mutator inputs
            var list = host.inputList.slice();
            for (var i = 0; i < list.length; i++) {
                var inp = list[i];
                if (isMutInput(host, inp.name) && inp.connection) {
                    var tb = inp.connection.targetBlock();
                    if (tb && !tb.__fomekStudio) { try { tb.dispose(false); } catch (e) {} }
                }
            }
            // 2. rebuild inputs from the popup chain (the host's own compose)
            host.compose(S.cap);
            var newMutation = Blockly.Xml.domToText(host.mutationToDom());
            if (oldMutation !== newMutation) {
                Blockly.Events.fire(new Blockly.Events.BlockChange(host, 'mutation', null, oldMutation, newMutation));
            }
            // 3. round-trip popup values into the fresh inputs, chain order
            var rows = currentRows();
            var counts = {};
            for (var r = 0; r < rows.length; r++) {
                var row = rows[r];
                var cfg = rowFor(k.kind, row.type);
                if (!cfg) continue;
                counts[cfg.in_] = (counts[cfg.in_] || 0);
                var input = host.getInput(cfg.in_ + counts[cfg.in_]);
                counts[cfg.in_]++;
                if (!input || !input.connection) continue;
                var val = row.getInputTargetBlock('VALUE');
                if (val) {
                    var nb = copyTo(val, host.workspace, input.connection);
                    if (nb) { nb.__fomekStudio = true; setTimeout(function (b) { return function () { delete b.__fomekStudio; }; }(nb), 100); }
                }
            }
            S.optionSnapshot = options;
            hideAllMutInputs(host);
            drawPreview(host);
        } finally { Blockly.Events.setGroup(group); }
    }

    function scheduleSync() {
        if (S.syncTimer) return;
        S.syncTimer = setTimeout(function () {
            S.syncTimer = null;
            syncToHost();
        }, 350);
    }
    function schedulePreview() {
        if (S.prevTimer) return;
        S.prevTimer = setTimeout(function () {
            S.prevTimer = null;
            if (S.host) drawPreview(S.host);
        }, 150);
    }

    function close() {
        if (!S.open) return;
        if(window.FomekStudioPreview)window.FomekStudioPreview.close();
        if (S.syncTimer) clearTimeout(S.syncTimer);
        if (S.prevTimer) clearTimeout(S.prevTimer);
        try { syncToHost(); } catch (e) {}
        // Never silently discard ordinary blocks imported from the main canvas.
        S.ws.getTopBlocks(false).forEach(function (block) {
            if (block.getDescendants(false).some(function (b) { return POPUP_ONLY[b.type]; })) return;
            var copy = Blockly.Xml.domToBlock(Blockly.Xml.blockToDom(block, true), S.host.workspace);
            var pos = S.host.getRelativeToSurfaceXY();
            copy.moveBy(pos.x + 40, pos.y + S.host.height + 30);
        });
        try {
            var mws = getMainWs();
            if (mws && S.hostListener) mws.removeChangeListener(S.hostListener);
        } catch (e) {}
        try { if (S.resizeObs) S.resizeObs.disconnect(); } catch (e) {}
        try { S.ws.dispose(); } catch (e) {}
        S = { open: false, host: null, ws: null, cap: null, kind: null, syncTimer: null, prevTimer: null, resizeObs: null };
        try { document.body.classList.remove('fomek-studio-open'); } catch (e) {}
        if (popup) popup.classList.remove('open');
    }

    window.__fomekStudioOpen = function (block) {
        if (S.open || !block || block.isInFlyout || !block.isEditable()
                || (block.isInsertionMarker && block.isInsertionMarker())) return;
        var k = KINDS[block.type];
        if (!k) return;
        buildDom();
        var ws = getMainWs();
        if (!ws) return;

        var root = popup.parentElement;
        var r = root.getBoundingClientRect();
        var W = Math.min(1120, Math.max(680, Math.round(r.width * 0.72)));
        var H = Math.min(760, Math.max(460, Math.round(r.height * 0.78)));
        popup.style.width = W + 'px';
        popup.style.height = H + 'px';
        popup.style.left = Math.max(16, Math.round((r.width - W) / 2)) + 'px';
        popup.style.top = Math.max(16, Math.round((r.height - H) / 2) - 20) + 'px';
        popup.classList.add('open');
        try { document.body.classList.add('fomek-studio-open'); } catch (e) {}
        document.getElementById('fomek-studio-title').textContent = 'Edit ' + k.title;
        var note=document.getElementById('fomek-studio-note');if(note)note.textContent='';

        S.open = true; S.host = block; S.kind = k.kind;

        var fxml = flyoutXml(k.kind);
        var tdom = fxml ? textToDom(fxml) : null;
        var opts = {
            toolbox: tdom,
            trashcan: false,
            sounds: ws.options.hasSounds,
            scrollbars: true,
            zoom: { controls: false, wheel: true, startScale: 1, maxScale: 2, minScale: 0.4 },
            move: { scrollbars: true, drag: true, wheel: true },
            renderer: (ws.getRenderer && ws.getRenderer().name) || 'geras'
        };
        try { var th = ws.getTheme && ws.getTheme(); if (th) opts.theme = th; } catch (e) {}
        S.ws = Blockly.inject('fomek-studio-ws', opts);

        // keep the workspace (and its toolbox/flyout layout) in sync while the
        // popup window is being resized by its native CSS resize handle
        try {
            S.resizeObs = new ResizeObserver(function () {
                try { Blockly.svgResize(S.ws); } catch (e) {}
            });
            S.resizeObs.observe(popup);
        } catch (e) {}

        var saved=block.getFieldValue('STUDIO_LAYOUT'),cap=null;
        if(saved){try{Blockly.Xml.domToWorkspace(textToDom(saved),S.ws);cap=S.ws.getTopBlocks(false).find(function(b){return b.type===settingsType(block.type);});}catch(error){console.error('Studio layout restore',error);S.ws.clear();}}
        if(!cap){cap=S.ws.newBlock(settingsType(block.type));cap.initSvg();cap.render();S.cap=cap;migrateStudio(block,cap);seedDefaults(cap,block);cap.moveBy(24,24);}
        cap.setDeletable(false);cap.setMovable(false);cap.contextMenu=false;cap.render();S.cap=cap;
        S.optionSnapshot=optionState();
        Blockly.svgResize(S.ws);
        // popup blocks may not be dragged out to the main canvas
        try { S.ws.addChangeListener(onPopupChange); } catch (e) {}

        // live preview of the host's basic args as they change outside
        try {
            S.hostListener = function (ev) {
                if (!ev || !ev.blockId || !S.host) return;
                var b = ws.getBlockById(ev.blockId);
                if (b === S.host || (b && b.getParent && b.getParent && b.getParent() === S.host)) schedulePreview();
            };
            ws.addChangeListener(S.hostListener);
        } catch (e) {}

        drawPreview(block);
    };

    function hostCanDecompose(block) {
        return !!(block.decompose && block.compose && KINDS[block.type] && ROWSET[KINDS[block.type].kind].length > 0);
    }

    function onPopupChange(ev){if(ev&&ev.isUiEvent)return;if(S.live)return;scheduleSync();schedulePreview();}

    // ── boot: hide stored option inputs and guard the main canvas ──
    function boot() {
        var tries = 0;
        (function waitWs() {
            var ws = getMainWs();
            if (!ws) { if (++tries < 120) setTimeout(waitWs, 500); return; }
            sweep(ws);
            installEscapeGuard(ws);
            installTransfer(ws);
        })();
    }

    boot();
})();

