package net.tamashi.fomek.inveditor.parts.gui.components;

import net.mcreator.blockly.data.Dependency;
import net.mcreator.element.parts.gui.GUIComponent;
import net.mcreator.element.parts.gui.Image;
import net.mcreator.ui.component.util.PanelUtils;
import net.mcreator.ui.dialogs.wysiwyg.AbstractWYSIWYGDialog;
import net.mcreator.ui.help.IHelpContext;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.minecraft.TextureComboBox;
import net.mcreator.ui.procedure.AbstractProcedureSelector;
import net.mcreator.ui.procedure.ProcedureSelector;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.workspace.elements.VariableTypeLoader;
import net.tamashi.fomek.inveditor.parts.gui.InveditGuiEditor;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;

public class InveditImageDialog extends AbstractWYSIWYGDialog<Image> {

    public InveditImageDialog(InveditGuiEditor editor, @Nullable Image image) {
        super(editor.getFakeEditor(), image);
        setSize(650, 180);
        setLocationRelativeTo(editor.mcreator);
        setModal(true);

        TextureComboBox textureSelector = new TextureComboBox(editor.mcreator, TextureType.SCREEN, false);

        JPanel options = new JPanel();
        options.setLayout(new BoxLayout(options, BoxLayout.PAGE_AXIS));
        options.add(PanelUtils.westAndCenterElement(L10N.label("dialog.gui.image_texture"), textureSelector));

        JCheckBox scale1x = L10N.checkbox("dialog.gui.image_use_scale");
        options.add(PanelUtils.join(FlowLayout.LEFT, scale1x));

        final JComboBox<GUIComponent.AnchorPoint> anchor = new JComboBox<>(GUIComponent.AnchorPoint.values());
        anchor.setSelectedItem(GUIComponent.AnchorPoint.CENTER);

        AbstractProcedureSelector.ReloadContext context = AbstractProcedureSelector.ReloadContext.create(
                editor.mcreator.getWorkspace());

        ProcedureSelector displayCondition = new ProcedureSelector(
                IHelpContext.NONE.withEntry("gui/image_display_condition"), editor.mcreator,
                L10N.t("dialog.gui.image_display_condition"), ProcedureSelector.Side.CLIENT, false,
                VariableTypeLoader.BuiltInTypes.LOGIC,
                Dependency.fromString("x:number/y:number/z:number/world:world/entity:entity"));
        displayCondition.refreshList(context);

        add("Center", PanelUtils.totalCenterInPanel(PanelUtils.centerAndEastElement(options, displayCondition, 20, 5)));

        setTitle(L10N.t("dialog.gui.image_title"));

        JButton ok = new JButton(UIManager.getString("OptionPane.okButtonText"));

        getRootPane().setDefaultButton(ok);

        JButton cancel = new JButton(UIManager.getString("OptionPane.cancelButtonText"));
        add("South", PanelUtils.join(ok, cancel));

        if (image != null) {
            ok.setText(L10N.t("dialog.common.save_changes"));
            textureSelector.setTextureFromTextureName(image.image);
            scale1x.setSelected(image.use1Xscale);
            displayCondition.setSelectedProcedure(image.displayCondition);
            anchor.setSelectedItem(image.anchorPoint);
        }

        cancel.addActionListener(arg01 -> dispose());
        ok.addActionListener(arg01 -> {
            dispose();
            if (textureSelector.hasTexture()) {
                if (image == null) {
                    Image component = new Image(0, 0, textureSelector.getTextureName(), scale1x.isSelected(),
                            displayCondition.getSelectedProcedure());
                    setEditingComponent(component);
                    editor.editor.addComponent(component);
                    editor.list.setSelectedValue(component, true);
                    editor.editor.moveMode();
                } else {
                    int idx = editor.components.indexOf(image);
                    editor.components.remove(image);
                    Image imageNew = new Image(image.getX(), image.getY(), textureSelector.getTextureName(),
                            scale1x.isSelected(), displayCondition.getSelectedProcedure());
                    editor.components.add(idx, imageNew);
                    setEditingComponent(imageNew);
                }
            }
        });

        setVisible(true);
    }

}