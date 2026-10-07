// FomekMenus block colors
Blockly.Msg['FOMEKMENU_HUE'] = '160';
Blockly.Msg['FOMEKMENU_BUILDING_HUE'] = '160';
Blockly.Msg['FOMEKMENU_DATA_HUE'] = '210';
Blockly.Msg['FOMEKMENU_ACTION_HUE'] = '0';
Blockly.Msg['FOMEKMENU_RENDER_HUE'] = '280';
Blockly.Msg['FOMEKMENU_TYPE_HUE'] = '130';
Blockly.Msg['FOMEKMENU_ATTR_HUE'] = '50';
Blockly.Msg['FOMEKMENU_OBJECT_HUE'] = '190';

// ── Mutator container block (shared) ─────────────────────────────────────────
// NOTE (v3.4.0): these "mutator input" blocks now live ONLY inside the
// Element Studio popup (see fomekmenu_studio.js) — the old gear bubble is
// hidden on studio host blocks. Each row block carries its own VALUE socket
// so options can be edited inside the popup; the studio round-trips the
// value blocks into the host block's (hidden) mutator inputs, which the
// MCreator generator reads exactly as before — no ftl changes.
Blockly.Blocks['fomekmenu_mutator_container'] = {
    init: function () {
        this.appendDummyInput().appendField("Inputs");
        this.appendStatementInput('STACK');
        this.contextMenu = false;
        this.setColour(160);
    }
};

Blockly.Blocks['fomekmenu_mutator_input'] = {
    init: function () {
        this.appendDummyInput().appendField("Input");
        this.setPreviousStatement(true);
        this.setNextStatement(true);
        this.contextMenu = false;
        this.setColour(160);
    }
};

// ── Studio option row blocks (data-driven) ─────────────────────────────────
// check = Blockly output type of the row's VALUE socket.
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

Object.keys(FOMEKMENU_MUTATOR_ROWS).forEach(function (t) {
    var cfg = FOMEKMENU_MUTATOR_ROWS[t];
    Blockly.Blocks[t] = {
        init: function () {
            this.appendDummyInput().appendField(cfg.label);
            this.appendValueInput('VALUE').setCheck(cfg.check);
            this.setPreviousStatement(true);
            this.setNextStatement(true);
            this.contextMenu = false;
            this.setColour(160);
        }
    };
});

// ── Multi-type repeating input mixin ────────────────────────────────────────
// A custom mutator mixin that supports multiple input types in the flyout.
// Each type creates a different kind of labeled value input on the block.
//
// typeConfigs: array of { blockType, label, check, repeatingInput }
//   blockType      — the mutator block type shown in the flyout
//   label         — the field label shown on the parent block's input
//   check         — the Blockly type check for the value input
//   repeatingInput — the repeating input name (used in FTL as input_list$<name>)
//
// Input naming convention: <repeatingInput><index> (e.g. mt_checks0, mt_checks1)
// This matches MCreator's repeating_inputs system.

function multiTypeRepeatingInputMixin(containerType, typeConfigs) {
    var blockTypeToConfig = {};
    var repeatingInputToConfig = {};
    var allRepeatingInputs = [];

    typeConfigs.forEach(function(c) {
        blockTypeToConfig[c.blockType] = c;
        repeatingInputToConfig[c.repeatingInput] = c;
        allRepeatingInputs.push(c.repeatingInput);
    });

    // Check if an input name belongs to this mutator (matches prefix + digits)
    function isMutatorInput(name) {
        for (var i = 0; i < allRepeatingInputs.length; i++) {
            var prefix = allRepeatingInputs[i];
            if (name === prefix) return true;
            if (name.startsWith(prefix) && /^\d+$/.test(name.substring(prefix.length))) return true;
        }
        return false;
    }

    // Get the repeating input prefix for a given input name
    function getPrefix(name) {
        // Sort by length descending so longer prefixes match first
        var sorted = allRepeatingInputs.slice().sort(function(a, b) { return b.length - a.length; });
        for (var i = 0; i < sorted.length; i++) {
            var prefix = sorted[i];
            if (name === prefix) return prefix;
            if (name.startsWith(prefix) && /^\d+$/.test(name.substring(prefix.length))) return prefix;
        }
        return null;
    }

    function getAlignRight() {
        return Blockly.ALIGN_RIGHT ?? (Blockly.Input && Blockly.Input.Align && Blockly.Input.Align.RIGHT);
    }

    return {
        mutationToDom: function() {
            var container = document.createElement('mutation');
            var items = [];
            for (var i = 0; i < this.inputList.length; i++) {
                var input = this.inputList[i];
                if (isMutatorInput(input.name)) {
                    var prefix = getPrefix(input.name);
                    if (prefix) items.push(prefix);
                }
            }
            container.setAttribute('items', items.join(','));
            container.setAttribute('count', items.length);
            return container;
        },

        domToMutation: function(element) {
            var itemsStr = element.getAttribute('items') || '';
            var items = itemsStr ? itemsStr.split(',') : [];

            // Remove existing mutator inputs
            for (var i = this.inputList.length - 1; i >= 0; i--) {
                if (isMutatorInput(this.inputList[i].name)) {
                    this.removeInput(this.inputList[i].name);
                }
            }

            // Recreate inputs from saved state
            var counts = {};
            for (var i = 0; i < items.length; i++) {
                var prefix = items[i];
                var config = repeatingInputToConfig[prefix];
                if (config) {
                    counts[prefix] = (counts[prefix] || 0) + 1;
                    var inputName = prefix + (counts[prefix] - 1);
                    this.appendValueInput(inputName)
                        .setCheck(config.check)
                        .setAlign(getAlignRight())
                        .appendField(config.label);
                }
            }
            if (this.fomekHideStudioInputs_) this.fomekHideStudioInputs_();
        },

        decompose: function(workspace) {
            var containerBlock = workspace.newBlock(containerType);
            containerBlock.initSvg();
            var connection = containerBlock.getInput('STACK').connection;

            for (var i = 0; i < this.inputList.length; i++) {
                var input = this.inputList[i];
                if (isMutatorInput(input.name)) {
                    var prefix = getPrefix(input.name);
                    if (prefix) {
                        var config = repeatingInputToConfig[prefix];
                        if (config) {
                            var inputBlock = workspace.newBlock(config.blockType);
                            inputBlock.initSvg();
                            connection.connect(inputBlock.previousConnection);
                            connection = inputBlock.nextConnection;
                        }
                    }
                }
            }
            return containerBlock;
        },

        compose: function(containerBlock) {
            // Save connected blocks before removing inputs
            var savedConnections = {};
            for (var i = 0; i < this.inputList.length; i++) {
                var input = this.inputList[i];
                if (isMutatorInput(input.name) && input.connection && input.connection.targetConnection) {
                    savedConnections[input.name] = input.connection.targetConnection;
                }
            }

            // Remove existing mutator inputs
            for (var i = this.inputList.length - 1; i >= 0; i--) {
                if (isMutatorInput(this.inputList[i].name)) {
                    this.removeInput(this.inputList[i].name);
                }
            }

            // Read mutator workspace and create inputs
            var block = containerBlock.getInputTargetBlock('STACK');
            var counts = {};
            while (block) {
                var config = blockTypeToConfig[block.type];
                if (config) {
                    counts[config.repeatingInput] = (counts[config.repeatingInput] || 0) + 1;
                    var inputName = config.repeatingInput + (counts[config.repeatingInput] - 1);
                    this.appendValueInput(inputName)
                        .setCheck(config.check)
                        .setAlign(getAlignRight())
                        .appendField(config.label);
                    // Restore saved connection if this input existed before
                    if (savedConnections[inputName]) {
                        this.getInput(inputName).connection.connect(savedConnections[inputName]);
                    }
                }
                block = block.getNextBlock();
            }
            if (this.fomekHideStudioInputs_) this.fomekHideStudioInputs_();
        }
    };
}


// ── Single-type repeating input mixin ────────────────────────────────────────
// A simpler version of multiTypeRepeatingInputMixin for blocks that only need
// one kind of repeating input (e.g. just "checks" or just "panel_attributes").
//
// containerType  — the mutator container block type
// inputBlockType — the mutator input block type shown in the flyout
// repeatingInput — the repeating input name (used in FTL as input_list$<name>)
// addInputFn     — callback(thisBlock, inputName, index) that appends the value input

function simpleRepeatingInputMixin(containerType, inputBlockType, repeatingInput, addInputFn) {
    function isMutatorInput(name) {
        if (name === repeatingInput) return true;
        if (name.startsWith(repeatingInput) && /^\d+$/.test(name.substring(repeatingInput.length))) return true;
        return false;
    }

    function getAlignRight() {
        return Blockly.ALIGN_RIGHT ?? (Blockly.Input && Blockly.Input.Align && Blockly.Input.Align.RIGHT);
    }

    return {
        mutationToDom: function() {
            var container = document.createElement('mutation');
            var count = 0;
            for (var i = 0; i < this.inputList.length; i++) {
                if (isMutatorInput(this.inputList[i].name)) count++;
            }
            container.setAttribute('inputs', count);
            container.setAttribute('count', count);
            return container;
        },

        domToMutation: function(element) {
            var count = parseInt(element.getAttribute('count') || '0', 10);

            // Remove existing mutator inputs
            for (var i = this.inputList.length - 1; i >= 0; i--) {
                if (isMutatorInput(this.inputList[i].name)) {
                    this.removeInput(this.inputList[i].name);
                }
            }

            // Recreate inputs from saved count
            for (var i = 0; i < count; i++) {
                addInputFn(this, repeatingInput, i);
            }
        },

        decompose: function(workspace) {
            var containerBlock = workspace.newBlock(containerType);
            containerBlock.initSvg();
            var connection = containerBlock.getInput('STACK').connection;

            for (var i = 0; i < this.inputList.length; i++) {
                var input = this.inputList[i];
                if (isMutatorInput(input.name)) {
                    var inputBlock = workspace.newBlock(inputBlockType);
                    inputBlock.initSvg();
                    connection.connect(inputBlock.previousConnection);
                    connection = inputBlock.nextConnection;
                }
            }
            return containerBlock;
        },

        compose: function(containerBlock) {
            // Save connected blocks before removing inputs
            var savedConnections = {};
            for (var i = 0; i < this.inputList.length; i++) {
                var input = this.inputList[i];
                if (isRepeatingInput(input.name) && input.connection && input.connection.targetConnection) {
                    savedConnections[input.name] = input.connection.targetConnection;
                }
            }

            // Remove existing mutator inputs
            for (var i = this.inputList.length - 1; i >= 0; i--) {
                if (isRepeatingInput(this.inputList[i].name)) {
                    this.removeInput(this.inputList[i].name);
                }
            }

            // Read mutator workspace and create inputs
            var block = containerBlock.getInputTargetBlock('STACK');
            var index = 0;
            while (block) {
                var inputName = repeatingInput + index;
                addInputFn(this, inputName, index);
                // Restore saved connection if this input existed before
                if (savedConnections[inputName]) {
                    var inp = this.getInput(inputName);
                    if (inp && inp.connection) {
                        inp.connection.connect(savedConnections[inputName]);
                    }
                }
                index++;
                block = block.getNextBlock();
            }
        }
    };
}

// ── Panel interactions + stick mutator ────────────────────────────────────────
// Gear icon flyout shows "Interactions" (repeating) and "Stick" (optional).
// Interactions → FomekMenuAttribute input (labeled "interactions") → FTL: input_list$panel_attributes
// Stick        → Boolean input (labeled "stick")                   → FTL: input_list$stick
// If no Stick block is added, the panel defaults to stick=false (resizes with its parent).
Blockly.Extensions.registerMutator('fomekmenu_panel_mutator', multiTypeRepeatingInputMixin(
    'fomekmenu_mutator_container',
    [
        { blockType: 'fomekmenu_mutator_attribute', label: 'interactions', check: 'FomekMenuAttribute', repeatingInput: 'panel_attributes' },
        { blockType: 'fomekmenu_mutator_stick',      label: 'stick',        check: 'Boolean',            repeatingInput: 'stick'      },
        { blockType: 'fomekmenu_mutator_collision',   label: 'collision',    check: 'Boolean',            repeatingInput: 'collision'   },
        { blockType: 'fomekmenu_mutator_grid',        label: 'grid',         check: 'FomekMenuAttribute', repeatingInput: 'grid'        },
        { blockType: 'fomekmenu_mutator_scale',       label: 'scale',        check: 'Boolean',            repeatingInput: 'scale'       }
    ]
), undefined, ['fomekmenu_mutator_attribute', 'fomekmenu_mutator_stick', 'fomekmenu_mutator_collision', 'fomekmenu_mutator_grid', 'fomekmenu_mutator_scale']);

// ── Optional checks + stick mutator ───────────────────────────────────────────
// Shared by Button and Slider.
// Gear icon flyout shows "Check" (repeating) and "Stick" (optional).
// Check → String input (labeled "check")   → FTL: input_list$checks
// Stick → Boolean input (labeled "stick")  → FTL: input_list$stick
// If no Stick block is added, the element defaults to stick=false (resizes with its parent).
// All attached checks are AND'ed together (must ALL return true).
Blockly.Extensions.registerMutator('fomekmenu_checks_mutator', multiTypeRepeatingInputMixin(
    'fomekmenu_mutator_container',
    [
        { blockType: 'fomekmenu_mutator_check',    label: 'check',     check: 'String',  repeatingInput: 'checks'   },
        { blockType: 'fomekmenu_mutator_stick',    label: 'stick',     check: 'Boolean', repeatingInput: 'stick'    },
        { blockType: 'fomekmenu_mutator_collision', label: 'collision', check: 'Boolean', repeatingInput: 'collision' }
    ]
), undefined, ['fomekmenu_mutator_check', 'fomekmenu_mutator_stick', 'fomekmenu_mutator_collision']);

// ── Draggable mutator (Check + Drag Out + Drag In) ────────────────────────────
// Draggable is an effect, not a render — Interactions makes no sense nested in it.
// Gear icon flyout shows:
//   Check    → String input (labeled "check")    → FTL: input_list$mt_checks
//   Drag Out → Boolean input (labeled "drag out") → FTL: input_list$mt_drag_out
//   Drag In  → Boolean input (labeled "drag in")  → FTL: input_list$mt_drag_in
// Drag Out and Drag In are independent — add either, both, or neither:
//   dragOut=false, dragIn=false (default) — current/classic behavior: clamped
//     to its own parent's bounds, never reparented.
//   dragOut=true  — clamped to the SCREEN instead of the parent, so it can be
//     physically dragged outside the panel. Never changes parents.
//   dragIn=true   — while dragging, if its center moves over a different
//     panel/scroll view, it's reparented into it (or root-level if over none).
//   Both true     — the classic "pick up and drop into another panel" combo.
// Works the same for panels (plugged into a panel's own Draggable interaction)
// and render elements/objects (plugged into a Render block's Draggable
// interaction) — same underlying attribute either way.
Blockly.Extensions.registerMutator('fomekmenu_draggable_mutator', multiTypeRepeatingInputMixin(
    'fomekmenu_mutator_container',
    [
        { blockType: 'fomekmenu_mutator_check',          label: 'check',          check: 'String',  repeatingInput: 'mt_checks' },
        { blockType: 'fomekmenu_mutator_drag_out',       label: 'drag out',       check: 'Boolean', repeatingInput: 'mt_drag_out' },
        { blockType: 'fomekmenu_mutator_drag_in',        label: 'drag in',        check: 'Boolean', repeatingInput: 'mt_drag_in' },
        { blockType: 'fomekmenu_mutator_highlight',      label: 'highlight',      check: 'Boolean', repeatingInput: 'mt_highlight' },
        { blockType: 'fomekmenu_mutator_highlight_color', label: 'highlight color', check: 'Number',  repeatingInput: 'mt_highlight_color' },
        { blockType: 'fomekmenu_mutator_drag_bounds',     label: 'drag bounds',     check: 'FomekMenuBox', repeatingInput: 'mt_drag_bounds' }
    ]
), undefined, ['fomekmenu_mutator_check', 'fomekmenu_mutator_drag_out', 'fomekmenu_mutator_drag_in', 'fomekmenu_mutator_highlight', 'fomekmenu_mutator_highlight_color', 'fomekmenu_mutator_drag_bounds']);

// ── Render mutator (Check, Interactions, Stick) ───────────────────────────────
// Used by all Render* blocks (Render text, Render filled rectangle,
// Render rectangle outline, Render texture, Render item) — anything that
// draws inside a panel/scroll view and should be individually stickable.
// The gear icon flyout shows Check, Interactions, Stick blocks.
//   Check        → String input (labeled "check")                     → FTL: input_list$mt_checks
//   Interactions → FomekMenuAttribute input (labeled "interactions")  → FTL: input_list$mt_attrs
//   Stick        → Boolean input (labeled "stick")                    → FTL: input_list$stick
// If no Stick block is added, the render call defaults to stick=false
// (it resizes/scales along with its parent panel, same as before).
// stick=true keeps it pinned at its exact position/size in the parent and
// it will not grow/shrink when the parent is resized (may get clipped under
// the parent's edges if the parent shrinks).
Blockly.Extensions.registerMutator('fomekmenu_render_mutator', multiTypeRepeatingInputMixin(
    'fomekmenu_mutator_container',
    [
        { blockType: 'fomekmenu_mutator_check',      label: 'check',        check: 'String',             repeatingInput: 'mt_checks'  },
        { blockType: 'fomekmenu_mutator_attribute',  label: 'interactions', check: 'FomekMenuAttribute', repeatingInput: 'mt_attrs'   },
        { blockType: 'fomekmenu_mutator_stick',      label: 'stick',        check: 'Boolean',            repeatingInput: 'stick'      },
        { blockType: 'fomekmenu_mutator_collision',  label: 'collision',    check: 'Boolean',            repeatingInput: 'collision'   }
    ]
), undefined, ['fomekmenu_mutator_check', 'fomekmenu_mutator_attribute', 'fomekmenu_mutator_stick', 'fomekmenu_mutator_collision']);

// ── Resize mutator (Check + Min/Max Resize X/Y + Highlight) ────────────────────
// Used by the Resize attribute block. The gear icon flyout shows:
//   Check         → String input (labeled "check")           → FTL: input_list$mt_checks
//   Min Resize X  → Number input (labeled "min resize X")   → FTL: input_list$mt_min_resize_x
//   Min Resize Y  → Number input (labeled "min resize Y")   → FTL: input_list$mt_min_resize_y
//   Max Resize X  → Number input (labeled "max resize X")   → FTL: input_list$mt_max_resize_x
//   Max Resize Y  → Number input (labeled "max resize Y")   → FTL: input_list$mt_max_resize_y
//   Highlight     → Boolean input (labeled "highlight")     → FTL: input_list$mt_highlight
//   Highlight Color → Number input (labeled "highlight color") → FTL: input_list$mt_highlight_color
// By default (no highlight block added), highlight=true with default color.
// Add highlight=false to disable, or add highlight color to customize.
Blockly.Extensions.registerMutator('fomekmenu_resize_mutator', multiTypeRepeatingInputMixin(
    'fomekmenu_mutator_container',
    [
        { blockType: 'fomekmenu_mutator_check',       label: 'check',       check: 'String', repeatingInput: 'mt_checks' },
        { blockType: 'fomekmenu_mutator_min_resize_x', label: 'min resize X', check: 'Number', repeatingInput: 'mt_min_resize_x' },
        { blockType: 'fomekmenu_mutator_min_resize_y', label: 'min resize Y', check: 'Number', repeatingInput: 'mt_min_resize_y' },
        { blockType: 'fomekmenu_mutator_max_resize_x', label: 'max resize X', check: 'Number', repeatingInput: 'mt_max_resize_x' },
        { blockType: 'fomekmenu_mutator_max_resize_y', label: 'max resize Y', check: 'Number', repeatingInput: 'mt_max_resize_y' },
        { blockType: 'fomekmenu_mutator_highlight',      label: 'highlight',      check: 'Boolean', repeatingInput: 'mt_highlight' },
        { blockType: 'fomekmenu_mutator_highlight_color', label: 'highlight color', check: 'Number',  repeatingInput: 'mt_highlight_color' },
        { blockType: 'fomekmenu_mutator_corner_only',     label: 'corner only',     check: 'Boolean', repeatingInput: 'mt_corner_only' },
        { blockType: 'fomekmenu_mutator_aspect_ratio',    label: 'aspect ratio',    check: 'Boolean', repeatingInput: 'mt_aspect_ratio' },
        { blockType: 'fomekmenu_mutator_resize_bounds',   label: 'resize bounds',   check: 'FomekMenuBox', repeatingInput: 'mt_resize_bounds' }
    ]
), undefined, ['fomekmenu_mutator_check', 'fomekmenu_mutator_min_resize_x', 'fomekmenu_mutator_min_resize_y', 'fomekmenu_mutator_max_resize_x', 'fomekmenu_mutator_max_resize_y', 'fomekmenu_mutator_highlight', 'fomekmenu_mutator_highlight_color', 'fomekmenu_mutator_corner_only', 'fomekmenu_mutator_aspect_ratio', 'fomekmenu_mutator_resize_bounds']);

// ── Grid mutator (Snap To End + Cell Size) ───────────────────────────────────
// Used by the Create Grid block. The gear icon flyout shows:
//   Snap To End → Boolean input (labeled "snap to end") → FTL: input_list$mt_snap_to_end
//   Cell Size   → Number input (labeled "cell size")    → FTL: input_list$mt_cell_size
// snapToEnd: true = snap to end of list (like folders); false (default) = snap to closest cell center.
// cellSize: grid cell size for both x/y. If not set, auto-computes from biggest child.
Blockly.Extensions.registerMutator('fomekmenu_grid_mutator', multiTypeRepeatingInputMixin(
    'fomekmenu_mutator_container',
    [
        { blockType: 'fomekmenu_mutator_snap_to_end', label: 'snap to end', check: 'Boolean', repeatingInput: 'mt_snap_to_end' },
        { blockType: 'fomekmenu_mutator_cell_size',   label: 'cell size',   check: 'Number',  repeatingInput: 'mt_cell_size' },
        { blockType: 'fomekmenu_mutator_render_grid', label: 'render grid', check: 'Boolean', repeatingInput: 'mt_render_grid' },
        { blockType: 'fomekmenu_mutator_grid_color',  label: 'grid color',  check: 'Number',  repeatingInput: 'mt_grid_color' },
        { blockType: 'fomekmenu_mutator_begin_x',     label: 'begin X',     check: 'Number',  repeatingInput: 'mt_begin_x' },
        { blockType: 'fomekmenu_mutator_begin_y',     label: 'begin Y',     check: 'Number',  repeatingInput: 'mt_begin_y' }
    ]
), undefined, ['fomekmenu_mutator_snap_to_end', 'fomekmenu_mutator_cell_size', 'fomekmenu_mutator_render_grid', 'fomekmenu_mutator_grid_color', 'fomekmenu_mutator_begin_x', 'fomekmenu_mutator_begin_y']);



// ── Book grabbable mutator (Check) ────────────────────────────────────────────
// Used by the "Set book grabbable" block. The gear icon flyout shows:
//   Check → String input (labeled "check") → FTL: input_list$mt_checks
// All listed check ids must pass for a page grab to start (same check
// system as panel interactions). No Check blocks = always grabbable.
Blockly.Extensions.registerMutator('fomekmenu_book_grabbable_mutator', multiTypeRepeatingInputMixin(
    'fomekmenu_mutator_container',
    [
        { blockType: 'fomekmenu_mutator_check', label: 'check', check: 'String', repeatingInput: 'mt_checks' }
    ]
), undefined, ['fomekmenu_mutator_check']);

// ── Scroll view stick mutator ─────────────────────────────────────────────────
// Gear icon flyout shows "Stick" only.
// Stick → Boolean input (labeled "stick") → FTL: input_list$stick
// If no Stick block is added, the scroll view defaults to stick=false (resizes with its parent).
Blockly.Extensions.registerMutator('fomekmenu_scrollview_mutator', multiTypeRepeatingInputMixin(
    'fomekmenu_mutator_container',
    [
        { blockType: 'fomekmenu_mutator_stick',     label: 'stick',     check: 'Boolean', repeatingInput: 'stick'     },
        { blockType: 'fomekmenu_mutator_collision', label: 'collision', check: 'Boolean', repeatingInput: 'collision' },
        { blockType: 'fomekmenu_mutator_grid',      label: 'grid',      check: 'FomekMenuAttribute', repeatingInput: 'grid' }
    ]
), undefined, ['fomekmenu_mutator_stick', 'fomekmenu_mutator_collision', 'fomekmenu_mutator_grid']);


// ── Menu Object mutator (Check + Stick + Collision) ──────────────────────────
// Used by Add Rectangle/Texture/Item/Text blocks on Menu Objects.
// The gear icon flyout shows:
//   Check     → String input (labeled "check")     → FTL: input_list$mt_checks
//   Stick     → Boolean input (labeled "stick")     → FTL: input_list$stick
//   Collision → Boolean input (labeled "collision") → FTL: input_list$collision
Blockly.Extensions.registerMutator('fomekmenu_menuobj_mutator', multiTypeRepeatingInputMixin(
    'fomekmenu_mutator_container',
    [
        { blockType: 'fomekmenu_mutator_check',     label: 'check',     check: 'String',  repeatingInput: 'mt_checks' },
        { blockType: 'fomekmenu_mutator_stick',     label: 'stick',     check: 'Boolean', repeatingInput: 'stick'     },
        { blockType: 'fomekmenu_mutator_collision', label: 'collision', check: 'Boolean', repeatingInput: 'collision'  }
    ]
), undefined, ['fomekmenu_mutator_check', 'fomekmenu_mutator_stick', 'fomekmenu_mutator_collision']);
