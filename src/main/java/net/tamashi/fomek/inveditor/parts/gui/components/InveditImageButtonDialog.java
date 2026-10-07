package net.tamashi.fomek.inveditor.parts.gui.components;

import net.mcreator.blockly.data.Dependency;
import net.mcreator.element.parts.gui.ImageButton;
import net.mcreator.ui.component.JEmptyBox;
import net.mcreator.ui.component.util.PanelUtils;
import net.mcreator.ui.dialogs.wysiwyg.AbstractWYSIWYGDialog;
import net.mcreator.ui.help.IHelpContext;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.minecraft.TextureComboBox;
import net.mcreator.ui.procedure.AbstractProcedureSelector;
import net.mcreator.ui.procedure.ProcedureSelector;
import net.mcreator.ui.validation.ValidationResult;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.util.image.ImageUtils;
import net.mcreator.workspace.elements.VariableTypeLoader;
import net.tamashi.fomek.inveditor.parts.gui.InveditGuiEditor;

import javax.annotation.Nullable;
import javax.swing.*;
import java.util.Locale;

public class InveditImageButtonDialog extends AbstractWYSIWYGDialog<ImageButton> {

    public InveditImageButtonDialog(InveditGuiEditor editor, @Nullable ImageButton button) {
        super(editor.getFakeEditor(), button);
        setModal(true);
        setSize(540, 310);
        setLocationRelativeTo(editor.mcreator);
        setTitle(L10N.t("dialog.gui.image_button_add_title"));

        JPanel options = new JPanel();
        options.setLayout(new BoxLayout(options, BoxLayout.PAGE_AXIS));

        TextureComboBox textureSelector = new TextureComboBox(editor.mcreator, TextureType.SCREEN, false);
        TextureComboBox hoveredTextureSelector = new TextureComboBox(editor.mcreator, TextureType.SCREEN, true);

        hoveredTextureSelector.setValidator(() -> {
            // The first image can never be null as this is the reference
            if (!textureSelector.hasTexture())
                return new ValidationResult(ValidationResult.Type.ERROR, L10N.t("validator.image_size.empty"));

            if (!hoveredTextureSelector.hasTexture())
                return ValidationResult.PASSED;

            // Finally, we can check if both images have the same height and width
            ImageIcon image1 = textureSelector.getTexture().getTextureIcon(editor.mcreator.getWorkspace());
            ImageIcon image2 = hoveredTextureSelector.getTexture().getTextureIcon(editor.mcreator.getWorkspace());

            if (ImageUtils.checkIfSameSize(image1.getImage(), image2.getImage()))
                return ValidationResult.PASSED;
            else
                return new ValidationResult(ValidationResult.Type.ERROR, L10N.t("validator.image_size"));
        });
        hoveredTextureSelector.getComboBox().enableRealtimeValidation();

        add("North", PanelUtils.centerInPanel(L10N.label("dialog.gui.image_button_size")));

        options.add(PanelUtils.northAndCenterElement(
                PanelUtils.westAndCenterElement(L10N.label("dialog.gui.image_texture"), textureSelector),
                PanelUtils.westAndCenterElement(L10N.label("dialog.gui.hovered_image_texture"), hoveredTextureSelector),
                2, 2));

        AbstractProcedureSelector.ReloadContext context = AbstractProcedureSelector.ReloadContext.create(
                editor.mcreator.getWorkspace());

        ProcedureSelector onClick = new ProcedureSelector(IHelpContext.NONE.withEntry("gui/on_button_clicked"),
                editor.mcreator, L10N.t("dialog.gui.button_event_on_clicked"), ProcedureSelector.Side.BOTH, false,
                Dependency.fromString("x:number/y:number/z:number/world:world/entity:entity"));
        onClick.refreshList(context);

        ProcedureSelector displayCondition = new ProcedureSelector(
                IHelpContext.NONE.withEntry("gui/button_display_condition"), editor.mcreator,
                L10N.t("dialog.gui.button_display_condition"), ProcedureSelector.Side.CLIENT, false,
                VariableTypeLoader.BuiltInTypes.LOGIC,
                Dependency.fromString("x:number/y:number/z:number/world:world/entity:entity"));
        displayCondition.refreshList(context);

        options.add(new JEmptyBox(20, 20));

        options.add(PanelUtils.gridElements(1, 2, 5, 5, onClick, displayCondition));

        add("Center", new JScrollPane(PanelUtils.centerInPanel(options)));

        JButton ok = new JButton(UIManager.getString("OptionPane.okButtonText"));
        JButton cancel = new JButton(UIManager.getString("OptionPane.cancelButtonText"));
        add("South", PanelUtils.join(ok, cancel));

        getRootPane().setDefaultButton(ok);

        if (button != null) {
            ok.setText(L10N.t("dialog.common.save_changes"));
            textureSelector.setTextureFromTextureName(button.image);
            hoveredTextureSelector.setTextureFromTextureName(button.hoveredImage);
            onClick.setSelectedProcedure(button.onClick);
            displayCondition.setSelectedProcedure(button.displayCondition);
        }

        cancel.addActionListener(arg01 -> dispose());
        ok.addActionListener(arg01 -> {
            if (hoveredTextureSelector.getValidationStatus().type() != ValidationResult.Type.ERROR) {
                dispose();
                if (textureSelector.hasTexture()) {
                    if (button == null) {
                        String name = textToMachineName(editor.getComponentList(), "imagebutton_",
                                textureSelector.getTexture().getTextureName().toLowerCase(Locale.ENGLISH));

                        ImageButton component = new ImageButton(name, 0, 0, textureSelector.getTextureName(),
                                hoveredTextureSelector.getTextureName(), onClick.getSelectedProcedure(),
                                displayCondition.getSelectedProcedure());

                        setEditingComponent(component);
                        editor.editor.addComponent(component);
                        editor.list.setSelectedValue(component, true);
                        editor.editor.moveMode();
                    } else {
                        int idx = editor.components.indexOf(button);
                        editor.components.remove(button);
                        ImageButton buttonNew = new ImageButton(button.name, button.getX(), button.getY(),
                                textureSelector.getTextureName(), hoveredTextureSelector.getTextureName(),
                                onClick.getSelectedProcedure(), displayCondition.getSelectedProcedure());
                        editor.components.add(idx, buttonNew);
                        setEditingComponent(buttonNew);
                    }
                }
            }
        });

        setVisible(true);
    }
}