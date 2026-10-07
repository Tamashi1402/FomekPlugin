package net.tamashi.fomek.inveditor.parts.gui;

import net.mcreator.element.parts.gui.Button;
import net.mcreator.element.parts.gui.GUIComponent;
import net.mcreator.element.parts.gui.Image;
import net.mcreator.element.parts.gui.Slot;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.component.JEmptyBox;
import net.mcreator.ui.component.util.ComponentUtils;
import net.mcreator.ui.component.util.PanelUtils;
import net.mcreator.ui.component.zoompane.JZoomPane;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;
import net.mcreator.ui.laf.themes.Theme;
import net.mcreator.ui.wysiwyg.WYSIWYGComponentRegistration;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import net.mcreator.element.parts.IWorkspaceDependent;
import net.mcreator.element.parts.gui.*;
import net.mcreator.element.parts.gui.Label;
import net.mcreator.ui.wysiwyg.WYSIWYGEditor;
import net.mcreator.util.ArrayListListModel;
import net.mcreator.util.GSONClone;
import net.mcreator.util.image.IconUtils;
import net.mcreator.util.image.ImageUtils;
import net.tamashi.fomek.inveditor.parts.gui.components.*;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;

public class InveditGuiEditor extends JPanel {

    //@formatter:off
    public static final List<WYSIWYGComponentRegistration<?>> COMPONENT_REGISTRY = new ArrayList<>() {{
        add(new WYSIWYGComponentRegistration<>("text_label", "addlabel", true, Label.class, InveditLabelDialog.class));
        add(new WYSIWYGComponentRegistration<>("image", "addimage", true, Image.class, InveditImageDialog.class));
        add(new WYSIWYGComponentRegistration<>("sprite", "addsprite", true, Sprite.class, InveditSpriteDialog.class));
        add(new WYSIWYGComponentRegistration<>("button", "addbutton", false, Button.class, InveditButtonDialog.class));
        add(new WYSIWYGComponentRegistration<>("imagebutton", "addimagebutton", false, ImageButton.class, InveditImageButtonDialog.class));
        add(new WYSIWYGComponentRegistration<>("tooltip", "addtooltip", false, Tooltip.class, InveditTooltipDialog.class));
        add(new WYSIWYGComponentRegistration<>("entity_model", "addmodel", true, EntityModel.class, InveditEntityModelDialog.class));
    }};
    //@formatter:on

    public final InveditGui editor = new InveditGui(this);

    public final ArrayListListModel<GUIComponent> components = new ArrayListListModel<>();
    public final JList<GUIComponent> list = new JList<>(components);

    private final JButton moveComponent = new JButton(UIRES.get("18px.move"));
    private final JButton editComponent = new JButton(UIRES.get("18px.edit"));
    private final JButton removeComponent = new JButton(UIRES.get("18px.remove"));
    private final JButton moveComponentUp = new JButton(UIRES.get("18px.up"));
    private final JButton moveComponentDown = new JButton(UIRES.get("18px.down"));
    private final JButton lockComponent = new JButton(UIRES.get("18px.lock"));

    public final JSpinner spa1 = new JSpinner(new SpinnerNumberModel(176, 0, 512, 1));
    public final JSpinner spa2 = new JSpinner(new SpinnerNumberModel(166, 0, 512, 1));

    public final JSpinner sx = new JSpinner(new SpinnerNumberModel(18, 1, 100, 1));
    public final JSpinner sy = new JSpinner(new SpinnerNumberModel(18, 1, 100, 1));
    public final JSpinner ox = new JSpinner(new SpinnerNumberModel(11, 1, 100, 1));
    public final JSpinner oy = new JSpinner(new SpinnerNumberModel(15, 1, 100, 1));

    public final JCheckBox snapOnGrid = L10N.checkbox("elementgui.gui.snap_components_on_grid");

    private boolean opening = false;

    public final JCheckBox renderBgLayer = new JCheckBox((L10N.t("elementgui.gui.render_background_layer")));

    public final MCreator mcreator;

    public final JPanel sidebar = new JPanel(new BorderLayout(0, 0));

    private final Map<WYSIWYGComponentRegistration<?>, JButton> addComponentButtonsMap = new HashMap<>();

    public BufferedImage guiImage = ImageUtils.toBufferedImage(UIRES.get("invimg").getImage());

    public final JComboBox<String> guiType = new JComboBox<>(
            new String[] { "Survival", "Creative" });

    public InveditGuiEditor(final MCreator mcreator) {
        super(new BorderLayout(5, 0));
        setOpaque(false);

        this.mcreator = mcreator;
        spa1.setPreferredSize(new Dimension(60, 24));
        spa2.setPreferredSize(new Dimension(60, 24));
        renderBgLayer.setSelected(true);
        renderBgLayer.setOpaque(false);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        list.addListSelectionListener(event -> {
            if (list.getSelectedValue() != null) {
                editor.setSelectedComponent(list.getSelectedValue());
                moveComponent.setEnabled(!list.getSelectedValue().locked);
                editComponent.setEnabled(true);
                removeComponent.setEnabled(true);
                moveComponentUp.setEnabled(true);
                moveComponentDown.setEnabled(true);
                lockComponent.setEnabled(true);
            } else {
                moveComponent.setEnabled(false);
                editComponent.setEnabled(false);
                removeComponent.setEnabled(false);
                moveComponentUp.setEnabled(false);
                moveComponentDown.setEnabled(false);
                lockComponent.setEnabled(false);
            }
        });

        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    editComponent.doClick();
                }
            }
        });
        list.addKeyListener(new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE)
                    editor.removeMode();
            }
        });

        moveComponent.addActionListener(event -> editor.moveMode());
        removeComponent.addActionListener(e -> editor.removeMode());

        moveComponentUp.addActionListener(e -> {
            boolean mu = components.moveUp(list.getSelectedIndex());
            if (mu)
                list.setSelectedIndex(list.getSelectedIndex() - 1);
        });
        moveComponentDown.addActionListener(e -> {
            boolean mu = components.moveDown(list.getSelectedIndex());
            if (mu)
                list.setSelectedIndex(list.getSelectedIndex() + 1);
        });

        lockComponent.addActionListener(e -> {
            GUIComponent component = list.getSelectedValue();
            component.locked = !component.locked;
            moveComponent.setEnabled(!component.locked);
            list.repaint();
        });

        editComponent.addActionListener(e -> editCurrentlySelectedComponent());

        list.setOpaque(false);
        list.setCellRenderer(new InveditGuiEditor.GUIComponentRenderer());

        JScrollPane span = new JScrollPane(list);
        span.setBorder(BorderFactory.createEmptyBorder());
        span.setOpaque(false);
        span.getViewport().setOpaque(false);

        JPanel adds = new JPanel();
        adds.setLayout(new BoxLayout(adds, BoxLayout.PAGE_AXIS));

        JPanel comppan = new JPanel(new BorderLayout());
        comppan.setOpaque(false);
        comppan.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Theme.current().getAltBackgroundColor(), 1),
                (L10N.t("elementgui.gui.component_list")), 0, 0, getFont().deriveFont(12.0f),
                Theme.current().getForegroundColor()));

        JToolBar bar2 = new JToolBar();
        bar2.setOpaque(false);
        bar2.setFloatable(false);

        moveComponent.setToolTipText((L10N.t("elementgui.gui.move_component")));
        editComponent.setToolTipText((L10N.t("elementgui.gui.edit_component")));
        removeComponent.setToolTipText((L10N.t("elementgui.gui.remove_component")));
        moveComponentUp.setToolTipText((L10N.t("elementgui.gui.move_component_up")));
        moveComponentDown.setToolTipText((L10N.t("elementgui.gui.move_component_down")));
        lockComponent.setToolTipText(L10N.t("elementgui.gui.lock_component"));

        moveComponent.setMargin(new Insets(1, 1, 1, 1));
        removeComponent.setMargin(new Insets(1, 1, 1, 1));
        editComponent.setMargin(new Insets(1, 1, 1, 1));
        moveComponentUp.setMargin(new Insets(1, 1, 1, 1));
        moveComponentDown.setMargin(new Insets(1, 1, 1, 1));
        lockComponent.setMargin(new Insets(1, 1, 1, 1));

        bar2.add(moveComponent);
        bar2.add(moveComponentUp);
        bar2.add(moveComponentDown);
        bar2.add(editComponent);
        bar2.add(lockComponent);
        bar2.add(removeComponent);

        comppan.add("North", bar2);
        comppan.add("Center", span);

        JPanel add = new JPanel() {
            @Override public Component add(Component component) {
                Component c = super.add(component);
                super.add(new JEmptyBox(3, 3));
                return c;
            }
        };
        add.setOpaque(false);
        add.setLayout(new BoxLayout(add, BoxLayout.PAGE_AXIS));

        for (WYSIWYGComponentRegistration<?> componentRegistration : COMPONENT_REGISTRY) {
            JButton componentButton = new JButton(UIRES.get("wysiwyg_editor." + componentRegistration.icon()));
            componentButton.setToolTipText((L10N.t("elementgui.gui.add_" + componentRegistration.machineName())));
            componentButton.setMargin(new Insets(0, 0, 0, 0));
            componentButton.addActionListener(e -> {
                try {
                    componentRegistration.editor()
                            .getConstructor(InveditGuiEditor.class, componentRegistration.component())
                            .newInstance(this, null);
                } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                         InvocationTargetException ex) {
                    throw new RuntimeException(ex);
                }
            });
            add.add(componentButton);
            addComponentButtonsMap.put(componentRegistration, componentButton);
        }

        snapOnGrid.setOpaque(false);
        snapOnGrid.addActionListener(event -> {
            editor.showGrid = snapOnGrid.isSelected();
            editor.repaint();
        });

        sx.addChangeListener(e -> {
            editor.grid_x_spacing = (int) sx.getValue();
            editor.repaint();
        });

        sy.addChangeListener(e -> {
            editor.grid_y_spacing = (int) sy.getValue();
            editor.repaint();
        });

        ox.addChangeListener(e -> {
            editor.grid_x_offset = (int) ox.getValue();
            editor.repaint();
        });

        oy.addChangeListener(e -> {
            editor.grid_y_offset = (int) oy.getValue();
            editor.repaint();
        });

        adds.add(PanelUtils.join(FlowLayout.LEFT, snapOnGrid));

        JPanel gx = new JPanel(new FlowLayout(FlowLayout.LEFT, 1, 0));
        gx.setOpaque(false);
        gx.add(new JLabel((L10N.t("elementgui.gui.grid_x"))));
        gx.add(sx);
        gx.add(new JLabel((L10N.t("elementgui.gui.offset_x"))));
        gx.add(ox);
        adds.add(gx);

        JPanel gy = new JPanel(new FlowLayout(FlowLayout.LEFT, 1, 1));
        gy.setOpaque(false);
        gy.add(new JLabel((L10N.t("elementgui.gui.grid_y"))));
        gy.add(sy);
        gy.add(new JLabel((L10N.t("elementgui.gui.offset_y"))));
        gy.add(oy);
        adds.add(gy);

        adds.add(new JEmptyBox(1, 1));

        editComponent.setEnabled(false);
        moveComponent.setEnabled(false);
        removeComponent.setEnabled(false);
        moveComponentUp.setEnabled(false);
        moveComponentDown.setEnabled(false);
        lockComponent.setEnabled(false);

        adds.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Theme.current().getAltBackgroundColor(), 1),
                (L10N.t("elementgui.gui.editor_options")), 0, 0, getFont().deriveFont(12.0f),
                Theme.current().getForegroundColor()));

        adds.setOpaque(false);

        JPanel adds2 = new JPanel();
        adds2.setLayout(new BoxLayout(adds2, BoxLayout.PAGE_AXIS));
        ComponentUtils.makeSection(adds2, L10N.t("elementgui.gui.gui_properties"));

        JComponent pon = PanelUtils.westAndEastElement(new JLabel((L10N.t("elementgui.gui.gui_type"))), guiType);

        adds2.add(PanelUtils.join(FlowLayout.LEFT, pon));
        adds2.setOpaque(false);

        JScrollPane scrollPane = new JScrollPane(adds2);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        
        sidebar.add("North", scrollPane);
        sidebar.add("Center", comppan);
        sidebar.add("South", adds);

        spa1.addChangeListener(event -> checkAndUpdateGUISize());
        spa2.addChangeListener(event -> checkAndUpdateGUISize());
        renderBgLayer.addActionListener(e -> checkAndUpdateGUISize());
        guiType.addActionListener(e -> checkAndUpdateGUISize());

        editor.setOpaque(false);

        JPanel zoomHolder = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setColor(Theme.current().getBackgroundColor());
                g2d.setComposite(AlphaComposite.SrcOver.derive(0.6f));
                g2d.fillRect(0, 0, getWidth(), getHeight());
                g2d.dispose();
                super.paintComponent(g);
            }
        };
        zoomHolder.setOpaque(false);

        add.setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 0));

        zoomHolder.add("Center", new JZoomPane(editor));
        zoomHolder.add("West", add);

        sidebar.setPreferredSize(new Dimension(250, 10));

        add("East", sidebar);
        add("Center", zoomHolder);
    }

    public WYSIWYGEditor getFakeEditor() {
        WYSIWYGEditor fakeEditor = new WYSIWYGEditor(mcreator, true);
        fakeEditor.spa1.setValue(this.spa1.getWidth());
        fakeEditor.spa2.setValue(this.spa2.getHeight());
        fakeEditor.invOffX.setValue(0);
        fakeEditor.invOffY.setValue(0);
        fakeEditor.setComponentList(this.components);
        fakeEditor.renderBgLayer.setSelected(this.renderBgLayer.isSelected());
        fakeEditor.doesPauseGame.setSelected(false);
        fakeEditor.getGUITypeSelector().setSelectedIndex(1);
        fakeEditor.setSlotComponentsEnabled(true);
        fakeEditor.sx.setValue(0);
        fakeEditor.sy.setValue(0);
        fakeEditor.ox.setValue(0);
        fakeEditor.oy.setValue(0);
        fakeEditor.snapOnGrid.setSelected(false);
        fakeEditor.editor.showGrid = false;
        return fakeEditor;
    }

    protected void editCurrentlySelectedComponent() {
        if (list.getSelectedValue() != null) {
            GUIComponent component = list.getSelectedValue();
            final boolean wasLocked = component.locked;

            for (WYSIWYGComponentRegistration<?> componentRegistration : COMPONENT_REGISTRY) {
                if (componentRegistration.component() == component.getClass()
                        && componentRegistration.editor() != null) {
                    try {
                        component = componentRegistration.editor()
                                .getConstructor(InveditGuiEditor.class, componentRegistration.component())
                                .newInstance(this, component).getEditingComponent();
                    } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                             InvocationTargetException ex) {
                        throw new RuntimeException(ex);
                    }
                    break;
                }
            }
            if (component != null)
                component.locked = wasLocked;

            list.setSelectedValue(component, true);
        }
    }

    public void setSlotComponentsEnabled(boolean enable) {
        for (Map.Entry<WYSIWYGComponentRegistration<?>, JButton> entry : addComponentButtonsMap.entrySet()) {
            if (Slot.class.isAssignableFrom(entry.getKey().component())) {
                entry.getValue().setEnabled(enable);
            }
        }
    }

    public JComboBox<String> getGUITypeSelector() {
        return guiType;
    }

    private void checkAndUpdateGUISize() {
        if (guiType.getSelectedIndex() == 0) {
            guiImage = ImageUtils.toBufferedImage(UIRES.get("invimg").getImage());
        } else {
            guiImage = ImageUtils.toBufferedImage(UIRES.get("creative_invimg").getImage());
        }
        editor.repaint();
    }

    private boolean isOpening() {
        return opening;
    }

    public void setOpening(boolean opening) {
        this.opening = opening;
    }

    public void setComponentList(List<GUIComponent> components) {
        this.components.clear();
        for (GUIComponent component : components) {
            GUIComponent copy = GSONClone.clone(component, component.getClass());
            copy.uuid = UUID.randomUUID(); // init UUID for deserialized component
            // Populate workspace-dependant fields with workspace reference
            IWorkspaceDependent.processWorkspaceDependentObjects(copy,
                    workspaceDependent -> workspaceDependent.setWorkspace(mcreator.getWorkspace()));

            this.components.add(copy);
        }
    }

    public List<GUIComponent> getComponentList() {
        return components;
    }

    static class GUIComponentRenderer extends JLabel implements ListCellRenderer<GUIComponent> {

        public GUIComponentRenderer() {
            setBorder(null);
            setHorizontalTextPosition(JLabel.RIGHT);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends GUIComponent> list, GUIComponent value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            if (isSelected) {
                setForeground(Theme.current().getBackgroundColor());
                setBackground(Theme.current().getForegroundColor());
                setOpaque(true);
            } else {
                setForeground(Theme.current().getForegroundColor());
                setOpaque(false);
            }

            if (value.locked) {
                ImageIcon icon = IconUtils.resize(UIRES.get("18px.lock"), 16);
                if (isSelected)
                    icon = ImageUtils.colorize(icon, Theme.current().getBackgroundColor(), true);
                setIcon(icon);
            } else {
                setIcon(null);
            }

            setOpaque(isSelected);
            setText(value.toString());
            return this;
        }
    }

}