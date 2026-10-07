package net.tamashi.fomek.cem.elements;

import net.mcreator.minecraft.JavaModels;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.component.SearchableComboBox;
import net.mcreator.ui.component.util.ComboBoxUtil;
import net.mcreator.ui.component.util.ComponentUtils;
import net.mcreator.ui.component.util.PanelUtils;
import net.mcreator.ui.help.HelpUtils;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.laf.renderer.ModelComboBoxRenderer;
import net.mcreator.ui.laf.themes.Theme;
import net.mcreator.ui.modgui.ModElementGUI;
import net.mcreator.ui.validation.AggregatedValidationResult;
import net.mcreator.ui.validation.component.VComboBox;
import net.mcreator.util.ListUtils;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.resources.Model;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;

public class HumanoidModelGUI extends ModElementGUI<HumanoidModel> {
    private final SearchableComboBox<Model> humanoidModel = new SearchableComboBox<>();

    private final VComboBox<String> helmetModelPart = new SearchableComboBox<>(new String[]{"Empty"});

    private final VComboBox<String> bodyModelPart = new SearchableComboBox<>(new String[]{"Empty"});
    private final VComboBox<String> armsModelPartL = new SearchableComboBox<>(new String[]{"Empty"});
    private final VComboBox<String> armsModelPartR = new SearchableComboBox<>(new String[]{"Empty"});

    private final VComboBox<String> leggingsModelPartL = new SearchableComboBox<>(new String[]{"Empty"});
    private final VComboBox<String> leggingsModelPartR = new SearchableComboBox<>(new String[]{"Empty"});

    private ActionListener modelListener = null;

    public HumanoidModelGUI(MCreator mcreator, @Nonnull ModElement modElement, boolean editingMode) {
        super(mcreator, modElement, editingMode);
        initGUI();
        super.finalizeGUI();
    }

    protected void initGUI() {
        humanoidModel.setRenderer(new ModelComboBoxRenderer());
        ComponentUtils.deriveFont(humanoidModel, 16);
        ComponentUtils.deriveFont(helmetModelPart, 16);
        ComponentUtils.deriveFont(bodyModelPart, 16);
        ComponentUtils.deriveFont(leggingsModelPartL, 16);
        ComponentUtils.deriveFont(leggingsModelPartR, 16);
        ComponentUtils.deriveFont(armsModelPartL, 16);
        ComponentUtils.deriveFont(armsModelPartR, 16);
        
        JPanel pane2 = new JPanel(new BorderLayout(10, 10));
        JPanel model = new JPanel(new GridLayout(1, 2, 10, 10));

        model.add(HelpUtils.wrapWithHelpButton(withEntry("curios/java_model"), L10N.label("elementgui.humanoidmodel.java_model", new Object[0])));
        model.add(humanoidModel);

        model.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Theme.current().getForegroundColor(), 1),
                L10N.t("elementgui.humanoidmodel.model"), TitledBorder.LEADING,
                TitledBorder.DEFAULT_POSITION, getFont(), Theme.current().getForegroundColor()));

        JPanel renderingPanel = new JPanel(new BorderLayout(10, 10));
        JPanel humanoidParts = new JPanel(new GridLayout(4, 1, 2, 2));

        humanoidParts.add(PanelUtils.westAndCenterElement(L10N.label("elementgui.humanoidmodel.head"), helmetModelPart, 5, 5));
        humanoidParts.add(PanelUtils.westAndCenterElement(L10N.label("elementgui.humanoidmodel.body"), bodyModelPart, 5, 5));
        humanoidParts.add(PanelUtils.gridElements(1, 2, 2, 2,
                PanelUtils.westAndCenterElement(L10N.label("elementgui.armor.part_arm_left"), armsModelPartL, 5, 5),
                PanelUtils.westAndCenterElement(L10N.label("elementgui.armor.part_arm_right"), armsModelPartR, 5, 5)));
        humanoidParts.add(PanelUtils.gridElements(1, 2, 2, 2,
                PanelUtils.westAndCenterElement(L10N.label("elementgui.armor.part_leg_left"), leggingsModelPartL, 5, 5),
                PanelUtils.westAndCenterElement(L10N.label("elementgui.armor.part_leg_right"), leggingsModelPartR, 5, 5)));

        humanoidParts.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Theme.current().getForegroundColor(), 1),
                L10N.t("elementgui.humanoidmodel.body_parts"), TitledBorder.LEADING,
                TitledBorder.DEFAULT_POSITION, getFont(), Theme.current().getForegroundColor()));

        renderingPanel.add("Center", PanelUtils.northAndCenterElement(model, humanoidParts));
        pane2.add("Center", PanelUtils.totalCenterInPanel(renderingPanel));

        pane2.setOpaque(false);
        model.setOpaque(false);
        humanoidParts.setOpaque(false);
        renderingPanel.setOpaque(false);

        modelListener = actionEvent -> {
            Model javaModel = humanoidModel.getSelectedItem();
            if (javaModel != null) {
                helmetModelPart.removeAllItems();
                bodyModelPart.removeAllItems();
                armsModelPartL.removeAllItems();
                armsModelPartR.removeAllItems();
                leggingsModelPartL.removeAllItems();
                leggingsModelPartR.removeAllItems();
                reloadPartList(helmetModelPart);
                reloadPartList(bodyModelPart);
                reloadPartList(armsModelPartL);
                reloadPartList(armsModelPartR);
                reloadPartList(leggingsModelPartL);
                reloadPartList(leggingsModelPartR);
                return;
            }
            helmetModelPart.removeAllItems();
            bodyModelPart.removeAllItems();
            armsModelPartL.removeAllItems();
            armsModelPartR.removeAllItems();
            leggingsModelPartL.removeAllItems();
            leggingsModelPartR.removeAllItems();
            helmetModelPart.addItem("Empty");
            bodyModelPart.addItem("Empty");
            armsModelPartL.addItem("Empty");
            armsModelPartR.addItem("Empty");
            leggingsModelPartL.addItem("Empty");
            leggingsModelPartR.addItem("Empty");
        };
        modelListener.actionPerformed(new ActionEvent("", 0, ""));
        humanoidModel.setFocusable(false);

        addPage(pane2).lazyValidate(() -> humanoidModel.getSelectedItem() != null ? new AggregatedValidationResult.PASS() : new AggregatedValidationResult.FAIL(L10N.t("elementgui.humanoidmodel.no_model", new Object[0])));
    }

    public void reloadPartList(VComboBox<String> list) {
        try {
            ComboBoxUtil.updateComboBoxContents(list, ListUtils.merge(Collections.singletonList("Empty"),
                    JavaModels.getModelParts((JavaClassSource) Roaster.parse(humanoidModel.getSelectedItem().getFile()))));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void reloadDataLists() {
        super.reloadDataLists();
        humanoidModel.removeActionListener(modelListener);
        ComboBoxUtil.updateComboBoxContents(humanoidModel, Model.getModels(mcreator.getWorkspace()).stream()
                .filter(el -> el.getType() == Model.Type.JAVA)
                .collect(Collectors.toList()));
        humanoidModel.addActionListener(modelListener);
    }

    protected void openInEditingMode(HumanoidModel model) {
        Model _humanoidModel = model.getHumanoidModel();
        if (_humanoidModel != null)
            humanoidModel.setSelectedItem(_humanoidModel);
        helmetModelPart.setSelectedItem(model.helmetModelPart);
        bodyModelPart.setSelectedItem(model.bodyModelPart);
        armsModelPartL.setSelectedItem(model.armsModelPartL);
        armsModelPartR.setSelectedItem(model.armsModelPartR);
        leggingsModelPartL.setSelectedItem(model.leggingsModelPartL);
        leggingsModelPartR.setSelectedItem(model.leggingsModelPartR);
    }

    public HumanoidModel getElementFromGUI() {
        HumanoidModel model = new HumanoidModel(modElement);
        model.modelName = humanoidModel.getSelectedItem() != null ? (Objects.requireNonNull(humanoidModel.getSelectedItem())).getReadableName() : null;
        model.helmetModelPart = helmetModelPart.getSelectedItem();
        model.bodyModelPart = bodyModelPart.getSelectedItem();
        model.armsModelPartL = armsModelPartL.getSelectedItem();
        model.armsModelPartR = armsModelPartR.getSelectedItem();
        model.leggingsModelPartL = leggingsModelPartL.getSelectedItem();
        model.leggingsModelPartR = leggingsModelPartR.getSelectedItem();
        return model;
    }

    @Override
    @Nullable
    public URI contextURL() throws URISyntaxException {
        return null;
    }
}