package net.tamashi.fomek.inveditor.parts.gui.components;


import net.mcreator.blockly.data.Dependency;
import net.mcreator.element.parts.gui.Tooltip;
import net.mcreator.element.parts.procedure.StringProcedure;
import net.mcreator.minecraft.RegistryNameFixer;
import net.mcreator.ui.component.util.PanelUtils;
import net.mcreator.ui.dialogs.wysiwyg.AbstractWYSIWYGDialog;
import net.mcreator.ui.help.IHelpContext;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.procedure.AbstractProcedureSelector;
import net.mcreator.ui.procedure.ProcedureSelector;
import net.mcreator.ui.procedure.StringProcedureSelector;
import net.mcreator.workspace.elements.VariableTypeLoader;
import net.tamashi.fomek.inveditor.parts.gui.InveditGuiEditor;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class InveditTooltipDialog extends AbstractWYSIWYGDialog<Tooltip> {

    public InveditTooltipDialog(InveditGuiEditor editor, @Nullable Tooltip tooltip) {
        super(editor.getFakeEditor(), tooltip);
        setModalityType(Dialog.DEFAULT_MODALITY_TYPE);
        setSize(580, 215);
        setLocationRelativeTo(editor.mcreator);

        JTextField textField = new JTextField();

        addWindowListener(new WindowAdapter() {
            @Override public void windowActivated(WindowEvent e) {
                SwingUtilities.invokeLater(textField::requestFocus);
            }
        });

        AbstractProcedureSelector.ReloadContext context = AbstractProcedureSelector.ReloadContext.create(
                editor.mcreator.getWorkspace());

        StringProcedureSelector tooltipText = new StringProcedureSelector(
                IHelpContext.NONE.withEntry("gui/tooltip_text"), editor.mcreator, textField, 200,
                Dependency.fromString("x:number/y:number/z:number/world:world/entity:entity"));
        tooltipText.refreshList(context);

        ProcedureSelector displayCondition = new ProcedureSelector(
                IHelpContext.NONE.withEntry("gui/tooltip_display_condition"), editor.mcreator,
                L10N.t("dialog.gui.tooltip_display_condition"), ProcedureSelector.Side.CLIENT, false,
                VariableTypeLoader.BuiltInTypes.LOGIC,
                Dependency.fromString("x:number/y:number/z:number/world:world/entity:entity"));
        displayCondition.refreshList(context);

        JPanel options = new JPanel();
        options.setLayout(new BoxLayout(options, BoxLayout.PAGE_AXIS));

        add("North", PanelUtils.join(FlowLayout.LEFT, L10N.label("dialog.gui.label_text"), tooltipText));

        add("Center", PanelUtils.totalCenterInPanel(displayCondition));

        setTitle(L10N.t("dialog.gui.add_tooltip"));

        JButton ok = new JButton(UIManager.getString("OptionPane.okButtonText"));

        getRootPane().setDefaultButton(ok);

        JButton cancel = new JButton(UIManager.getString("OptionPane.cancelButtonText"));
        add("South", PanelUtils.join(ok, cancel));

        if (tooltip != null) {
            ok.setText(L10N.t("dialog.common.save_changes"));
            tooltipText.setSelectedProcedure(tooltip.text);
            displayCondition.setSelectedProcedure(tooltip.displayCondition);
        }

        cancel.addActionListener(arg01 -> dispose());
        ok.addActionListener(arg01 -> {
            dispose();
            StringProcedure textProcedure = tooltipText.getSelectedProcedure();

            if (tooltip == null) {
                String nameBase;
                if (textProcedure.getName() != null) { // string procedure
                    nameBase = "proc_" + RegistryNameFixer.fromCamelCase(textProcedure.getName());
                } else { // fixed text
                    nameBase = textProcedure.getFixedValue();
                }

                String name = textToMachineName(editor.getComponentList(), "tooltip_", nameBase);

                Tooltip component = new Tooltip(name, 0, 0, 24, 24, textProcedure,
                        displayCondition.getSelectedProcedure());

                setEditingComponent(component);
                editor.editor.addComponent(component);
                editor.list.setSelectedValue(component, true);
                editor.editor.moveMode();
            } else {
                int idx = editor.components.indexOf(tooltip);
                editor.components.remove(tooltip);
                Tooltip tooltipNew = new Tooltip(tooltip.name, tooltip.getX(), tooltip.getY(),
                        tooltip.getWidth(editor.mcreator.getWorkspace()),
                        tooltip.getHeight(editor.mcreator.getWorkspace()), textProcedure,
                        displayCondition.getSelectedProcedure());
                editor.components.add(idx, tooltipNew);
                setEditingComponent(tooltipNew);
            }
        });

        setVisible(true);
    }
}
