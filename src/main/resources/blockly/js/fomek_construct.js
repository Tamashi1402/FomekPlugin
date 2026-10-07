// ── FomekRenderer Construct blocks ───────────────────────────────────────────
// Colors match the rest of the BEWLR family (green 140) for visual consistency.
//
// Mutators are fully self-contained — they do NOT depend on simpleRepeatingInputMixin
// or multiTypeRepeatingInputMixin from FomekMenus.  This avoids a compose() bug in
// those shared mixins where the input index was double-appended (animators0 →
// animators00), which disconnected existing blocks and broke the mutator cycle.

Blockly.Msg.FOMEK_CONSTRUCT_HUE = 140;

// Small helper: returns true if an input name belongs to the mutator (not a
// regular args0 input).  prefix is the repeating input base name.
function fomek_isMutatorInput(name, prefix) {
    if (name === prefix) return true;
    if (name.length > prefix.length && name.startsWith(prefix) &&
        /^\d+$/.test(name.substring(prefix.length))) return true;
    return false;
}

function fomek_alignRight() {
    return Blockly.ALIGN_RIGHT || (Blockly.Input && Blockly.Input.Align && Blockly.Input.Align.RIGHT);
}

// ═══════════════════════════════════════════════════════════════════════════════
// Beam / Trail Constructor — mutator container + input blocks
// ═══════════════════════════════════════════════════════════════════════════════
//
// Both the beam and trail constructors share the same mutator:
// drag "Animator" blocks into the flyout to add animator slots on the parent.
// Each slot accepts a BEWRL.Animator block (from the Animator constructor).
// Multiple animators stack — their effects are composited in order.

Blockly.Blocks['fomek_construct_beam_container'] = {
    init: function () {
        this.appendDummyInput().appendField(javabridge.t('blockly.fomek_construct_beam_mutator.container'));
        this.appendStatementInput('STACK');
        this.contextMenu = false;
        this.setColour(140);
    }
};

Blockly.Blocks['fomek_construct_beam_animator_input'] = {
    init: function () {
        this.appendDummyInput().appendField(javabridge.t('blockly.fomek_construct_beam_mutator.input'));
        this.setPreviousStatement(true);
        this.setNextStatement(true);
        this.contextMenu = false;
        this.setColour(140);
    }
};

// ── Self-contained mutator mixin for beam/trail animator slots ──

var fomekBeamMutatorMixin = (function () {
    var PREFIX = 'animators';

    function isMut(name) { return fomek_isMutatorInput(name, PREFIX); }

    return {
        mutationToDom: function () {
            var container = document.createElement('mutation');
            var count = 0;
            for (var i = 0; i < this.inputList.length; i++) {
                if (isMut(this.inputList[i].name)) count++;
            }
            container.setAttribute('count', count);
            return container;
        },

        domToMutation: function (element) {
            // Read 'count', fall back to 'inputs' for FomekMenus compatibility
            var count = parseInt(element.getAttribute('count') ||
                element.getAttribute('inputs') || '0', 10);

            // Remove existing mutator inputs
            for (var i = this.inputList.length - 1; i >= 0; i--) {
                if (isMut(this.inputList[i].name)) {
                    this.removeInput(this.inputList[i].name);
                }
            }

            // Recreate
            var align = fomek_alignRight();
            for (var i = 0; i < count; i++) {
                this.appendValueInput(PREFIX + i)
                    .setCheck('BEWRL.Animator')
                    .setAlign(align)
                    .appendField('animator ' + (i + 1) + ':');
            }
        },

        decompose: function (workspace) {
            var containerBlock = workspace.newBlock('fomek_construct_beam_container');
            containerBlock.initSvg();
            var connection = containerBlock.getInput('STACK').connection;

            for (var i = 0; i < this.inputList.length; i++) {
                if (isMut(this.inputList[i].name)) {
                    var inputBlock = workspace.newBlock('fomek_construct_beam_animator_input');
                    inputBlock.initSvg();
                    connection.connect(inputBlock.previousConnection);
                    connection = inputBlock.nextConnection;
                }
            }
            return containerBlock;
        },

        compose: function (containerBlock) {
            // Save connections keyed by current input name
            var saved = {};
            for (var i = 0; i < this.inputList.length; i++) {
                var input = this.inputList[i];
                if (isMut(input.name) && input.connection && input.connection.targetConnection) {
                    saved[input.name] = input.connection.targetConnection;
                }
            }

            // Remove all mutator inputs
            for (var i = this.inputList.length - 1; i >= 0; i--) {
                if (isMut(this.inputList[i].name)) {
                    this.removeInput(this.inputList[i].name);
                }
            }

            // Recreate from flyout stack — name = PREFIX + index (NO double-append)
            var align = fomek_alignRight();
            var block = containerBlock.getInputTargetBlock('STACK');
            var index = 0;
            while (block) {
                var inputName = PREFIX + index;          // e.g. 'animators0'
                this.appendValueInput(inputName)
                    .setCheck('BEWRL.Animator')
                    .setAlign(align)
                    .appendField('animator ' + (index + 1) + ':');

                if (saved[inputName]) {
                    var inp = this.getInput(inputName);
                    if (inp && inp.connection) {
                        inp.connection.connect(saved[inputName]);
                    }
                }
                index++;
                block = block.getNextBlock();
            }
        }
    };
})();

Blockly.Extensions.registerMutator('fomek_construct_beam_mutator',
    fomekBeamMutatorMixin,
    undefined,
    ['fomek_construct_beam_animator_input']
);

// ═══════════════════════════════════════════════════════════════════════════════
// Animator — mutator container + channel blocks
// ═══════════════════════════════════════════════════════════════════════════════
//
// The animator block has its own mutator that adds animation channel inputs.
// Each channel is a Number value — drag the channel block into the flyout
// to add that input slot on the animator block.
//
// Channels:
//   Pulse       — oscillates beam length (grow/shrink)     [speed, Hz-like]
//   Flicker     — random brightness flicker (Homelander)    [0=off, 1=max]
//   Color Shift — cycles beam hue over time (rainbow laser) [speed]
//   Trail       — fades the far end of the beam              [0=solid, 1=full fade]
//   Width Pulse — oscillates beam thickness                  [speed]
//   Spin        — rotates beam around its long axis          [speed]

Blockly.Blocks['fomek_construct_animator_container'] = {
    init: function () {
        this.appendDummyInput().appendField(javabridge.t('blockly.fomek_construct_animator_mutator.container'));
        this.appendStatementInput('STACK');
        this.contextMenu = false;
        this.setColour(140);
    }
};

Blockly.Blocks['fomek_construct_anim_pulse'] = {
    init: function () {
        this.appendDummyInput().appendField(javabridge.t('blockly.fomek_construct_anim.pulse'));
        this.setPreviousStatement(true);
        this.setNextStatement(true);
        this.contextMenu = false;
        this.setColour(140);
    }
};

Blockly.Blocks['fomek_construct_anim_flicker'] = {
    init: function () {
        this.appendDummyInput().appendField(javabridge.t('blockly.fomek_construct_anim.flicker'));
        this.setPreviousStatement(true);
        this.setNextStatement(true);
        this.contextMenu = false;
        this.setColour(140);
    }
};

Blockly.Blocks['fomek_construct_anim_color_shift'] = {
    init: function () {
        this.appendDummyInput().appendField(javabridge.t('blockly.fomek_construct_anim.color_shift'));
        this.setPreviousStatement(true);
        this.setNextStatement(true);
        this.contextMenu = false;
        this.setColour(140);
    }
};

Blockly.Blocks['fomek_construct_anim_trail'] = {
    init: function () {
        this.appendDummyInput().appendField(javabridge.t('blockly.fomek_construct_anim.trail'));
        this.setPreviousStatement(true);
        this.setNextStatement(true);
        this.contextMenu = false;
        this.setColour(140);
    }
};

Blockly.Blocks['fomek_construct_anim_width'] = {
    init: function () {
        this.appendDummyInput().appendField(javabridge.t('blockly.fomek_construct_anim.width'));
        this.setPreviousStatement(true);
        this.setNextStatement(true);
        this.contextMenu = false;
        this.setColour(140);
    }
};

Blockly.Blocks['fomek_construct_anim_spin'] = {
    init: function () {
        this.appendDummyInput().appendField(javabridge.t('blockly.fomek_construct_anim.spin'));
        this.setPreviousStatement(true);
        this.setNextStatement(true);
        this.contextMenu = false;
        this.setColour(140);
    }
};

// ── Self-contained multi-type mutator mixin for animator channels ──

var fomekAnimatorMutatorMixin = (function () {
    // Maps: block type → config, repeating input → config
    var blockTypeToConfig = {};
    var repeatingInputToConfig = {};
    var allPrefixes = [];

    var configs = [
        { blockType: 'fomek_construct_anim_pulse',       label: 'pulse',       check: 'Number', repeatingInput: 'pulse' },
        { blockType: 'fomek_construct_anim_flicker',      label: 'flicker',      check: 'Number', repeatingInput: 'flicker' },
        { blockType: 'fomek_construct_anim_color_shift',  label: 'color shift',  check: 'Number', repeatingInput: 'color_shift' },
        { blockType: 'fomek_construct_anim_trail',        label: 'trail',        check: 'Number', repeatingInput: 'trail' },
        { blockType: 'fomek_construct_anim_width',        label: 'width pulse',  check: 'Number', repeatingInput: 'width_pulse' },
        { blockType: 'fomek_construct_anim_spin',         label: 'spin',         check: 'Number', repeatingInput: 'spin' }
    ];

    configs.forEach(function (c) {
        blockTypeToConfig[c.blockType] = c;
        repeatingInputToConfig[c.repeatingInput] = c;
        allPrefixes.push(c.repeatingInput);
    });

    // Sort longest-first so 'color_shift' matches before 'color' if there's overlap
    var sortedPrefixes = allPrefixes.slice().sort(function (a, b) { return b.length - a.length; });

    function isMut(name) {
        for (var i = 0; i < sortedPrefixes.length; i++) {
            var prefix = sortedPrefixes[i];
            if (name === prefix) return true;
            if (name.length > prefix.length && name.startsWith(prefix) &&
                /^\d+$/.test(name.substring(prefix.length))) return true;
        }
        return false;
    }

    function getPrefix(name) {
        for (var i = 0; i < sortedPrefixes.length; i++) {
            var prefix = sortedPrefixes[i];
            if (name === prefix) return prefix;
            if (name.length > prefix.length && name.startsWith(prefix) &&
                /^\d+$/.test(name.substring(prefix.length))) return prefix;
        }
        return null;
    }

    return {
        mutationToDom: function () {
            var container = document.createElement('mutation');
            var items = [];
            for (var i = 0; i < this.inputList.length; i++) {
                var input = this.inputList[i];
                if (isMut(input.name)) {
                    var prefix = getPrefix(input.name);
                    if (prefix) items.push(prefix);
                }
            }
            container.setAttribute('items', items.join(','));
            container.setAttribute('count', items.length);
            return container;
        },

        domToMutation: function (element) {
            var itemsStr = element.getAttribute('items') || '';
            var items = itemsStr ? itemsStr.split(',') : [];

            // Remove existing mutator inputs
            for (var i = this.inputList.length - 1; i >= 0; i--) {
                if (isMut(this.inputList[i].name)) {
                    this.removeInput(this.inputList[i].name);
                }
            }

            // Recreate
            var align = fomek_alignRight();
            var counts = {};
            for (var i = 0; i < items.length; i++) {
                var prefix = items[i];
                var config = repeatingInputToConfig[prefix];
                if (config) {
                    counts[prefix] = (counts[prefix] || 0) + 1;
                    var inputName = prefix + (counts[prefix] - 1);
                    this.appendValueInput(inputName)
                        .setCheck(config.check)
                        .setAlign(align)
                        .appendField(config.label);
                }
            }
        },

        decompose: function (workspace) {
            var containerBlock = workspace.newBlock('fomek_construct_animator_container');
            containerBlock.initSvg();
            var connection = containerBlock.getInput('STACK').connection;

            for (var i = 0; i < this.inputList.length; i++) {
                var input = this.inputList[i];
                if (isMut(input.name)) {
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

        compose: function (containerBlock) {
            // Save connections keyed by current input name
            var saved = {};
            for (var i = 0; i < this.inputList.length; i++) {
                var input = this.inputList[i];
                if (isMut(input.name) && input.connection && input.connection.targetConnection) {
                    saved[input.name] = input.connection.targetConnection;
                }
            }

            // Remove all mutator inputs
            for (var i = this.inputList.length - 1; i >= 0; i--) {
                if (isMut(this.inputList[i].name)) {
                    this.removeInput(this.inputList[i].name);
                }
            }

            // Recreate from flyout stack
            var align = fomek_alignRight();
            var block = containerBlock.getInputTargetBlock('STACK');
            var counts = {};
            while (block) {
                var config = blockTypeToConfig[block.type];
                if (config) {
                    counts[config.repeatingInput] = (counts[config.repeatingInput] || 0) + 1;
                    var inputName = config.repeatingInput + (counts[config.repeatingInput] - 1);
                    this.appendValueInput(inputName)
                        .setCheck(config.check)
                        .setAlign(align)
                        .appendField(config.label);

                    if (saved[inputName]) {
                        var inp = this.getInput(inputName);
                        if (inp && inp.connection) {
                            inp.connection.connect(saved[inputName]);
                        }
                    }
                }
                block = block.getNextBlock();
            }
        }
    };
})();

Blockly.Extensions.registerMutator('fomek_construct_animator_mutator',
    fomekAnimatorMutatorMixin,
    undefined,
    ['fomek_construct_anim_pulse', 'fomek_construct_anim_flicker',
     'fomek_construct_anim_color_shift', 'fomek_construct_anim_trail',
     'fomek_construct_anim_width', 'fomek_construct_anim_spin']
);
