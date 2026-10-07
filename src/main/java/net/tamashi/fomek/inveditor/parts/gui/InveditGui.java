package net.tamashi.fomek.inveditor.parts.gui;

import net.mcreator.element.parts.gui.GUIComponent;
import net.mcreator.element.parts.gui.SizedComponent;
import net.mcreator.ui.component.zoompane.IZoomable;
import net.mcreator.ui.component.zoompane.JZoomPane;
import net.mcreator.ui.component.zoompane.JZoomport;
import net.mcreator.ui.init.UIRES;
import net.mcreator.ui.laf.themes.Theme;
import net.mcreator.ui.wysiwyg.WYSIWYGEditor;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InveditGui extends JComponent implements MouseMotionListener, MouseListener, IZoomable {

    public final static int W = 427;
    public final static int H = 240;

    public static final FontRenderContext frc = new FontRenderContext(new AffineTransform(), true, false);

    public static Font fontMC;

    private boolean positioningModeSettingWidth = false;
    private boolean positioningModeSettingHeight = false;

    private boolean componentMoveMode;
    private boolean componentDragMode;

    public boolean showGrid = false;

    @Nullable private GUIComponent selected;

    private int ox, oy;
    private int ow, oh;
    private int dragOffsetX, dragOffsetY;

    private final InveditGuiEditor InveditGuiEditor;
    public WYSIWYGEditor fakeEditor;

    private final Image background = UIRES.get("guieditor").getImage();

    private JZoomport owner;

    int grid_x_spacing = 18;
    int grid_y_spacing = 18;

    int grid_x_offset = 11;
    int grid_y_offset = 15;

    InveditGui(InveditGuiEditor InveditGuiEditor) {
        if (fontMC == null)
            fontMC = Theme.current().getConsoleFont().deriveFont(8f).deriveFont(Font.BOLD);

        this.InveditGuiEditor = InveditGuiEditor;

        addMouseListener(this);
        addMouseMotionListener(this);
    }

    @Override public void setZoomPane(JZoomPane jZoomPane) {
        this.owner = jZoomPane.getZoomport();
    }

    @Override public Dimension getPreferredSize() {
        return new Dimension(W * 2, H * 2);
    }

    @Override public int getWidth() {
        return W * 2;
    }

    @Override public int getHeight() {
        return H * 2;
    }

    void setSelectedComponent(GUIComponent selected) {
        this.selected = selected;
        repaint();
    }

    public void moveMode() {
        if (selected != null && selected.locked)
            return;

        owner.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
        componentMoveMode = true;

        if (selected instanceof SizedComponent) {
            this.ow = ((SizedComponent) selected).width;
            this.oh = ((SizedComponent) selected).height;
        }
    }

    void removeMode() {
        InveditGuiEditor.components.remove(selected);
        selected = null;
        repaint();
    }

    public void addComponent(GUIComponent component) {
        InveditGuiEditor.components.add(component);
        repaint();
    }

    @Override public void paint(Graphics gx) {
        Graphics2D g = (Graphics2D) gx;

        g.drawImage(background, 0, 0, null);

        g.setColor(Theme.current().getInterfaceAccentColor());
        g.drawRect(0, 0, getWidth(), getHeight());

        // draw JeiGui
        BufferedImage bi = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D wg = bi.createGraphics();
        drawJeiGui(wg);
        wg.dispose();
        g.drawImage(bi, 0, 0, this);

        // draw selection box
        if (selected != null) {
            int cx = selected.getX(), cy = selected.getY();
            if (componentMoveMode) {
                cx = ox;
                cy = oy;
            }

            int cw = selected.getWidth(InveditGuiEditor.mcreator.getWorkspace());
            int ch = selected.getHeight(InveditGuiEditor.mcreator.getWorkspace());

            g.setColor(new Color(255, 255, 255, 100));
            g.fillRect(cx * 2, cy * 2, cw * 2, ch * 2);

            Stroke original = g.getStroke();
            g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] { 3 }, 0));
            g.setColor(Color.white);

            if (selected.isSizeKnown()) {
                g.drawRect(cx * 2, cy * 2, cw * 2, ch * 2);
            } else {
                g.drawLine(cx * 2, cy * 2 + ch * 2, cx * 2 + 20, cy * 2 + ch * 2);
                g.drawLine(cx * 2, cy * 2, cx * 2, cy * 2 + ch * 2);
            }

            g.setStroke(original);
        }

        if (componentMoveMode) {
            float[] dash = { 2.0f };
            g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash, 0.0f));
            g.setColor(new Color(255, 255, 255, 150));

            g.drawLine(0, oy * 2, getWidth(), oy * 2);
            g.drawLine(ox * 2, 0, ox * 2, getHeight());

            if (positioningModeSettingWidth || positioningModeSettingHeight) {
                if ((ow != 0 || oh != 0) && (selected == null || selected.isSizeKnown())) {
                    g.drawLine(0, oy * 2 + oh * 2, getWidth(), oy * 2 + oh * 2);
                    g.drawLine(ox * 2 + ow * 2, 0, ox * 2 + ow * 2, getHeight());

                    g.setStroke(
                            new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] { 3 }, 0));
                    g.setColor(Color.white);
                    g.drawRect(ox * 2, oy * 2, ow * 2, oh * 2);
                }
            } else if (selected != null && selected.isSizeKnown()) {
                g.drawLine(0, oy * 2 + selected.getHeight(InveditGuiEditor.mcreator.getWorkspace()) * 2, getWidth(),
                        oy * 2 + selected.getHeight(InveditGuiEditor.mcreator.getWorkspace()) * 2);
                g.drawLine(ox * 2 + selected.getWidth(InveditGuiEditor.mcreator.getWorkspace()) * 2, 0,
                        ox * 2 + selected.getWidth(InveditGuiEditor.mcreator.getWorkspace()) * 2, getHeight());
            }
        }

        if (showGrid)
            drawGrid(g);

        if (this.owner != null)
            this.owner.repaint();
    }

    private void drawJeiGui(Graphics2D g) {
        g.scale(2, 2);

        g.setFont(fontMC);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        g.setColor(Color.gray.brighter().brighter());

        int gw = (Integer) InveditGuiEditor.guiType.getSelectedIndex() == 0 ? 176 : 195;
        int gh = (Integer) InveditGuiEditor.guiType.getSelectedIndex() == 0 ? 166 : 136;

        if (InveditGuiEditor.renderBgLayer.isSelected()) {
            g.drawImage(InveditGuiEditor.guiImage, (int) Math.ceil(W / 2.0 - gw / 2.0),
                    (int) Math.ceil(H / 2.0 - gh / 2.0), gw, gh, this);
        }

        List<GUIComponent> toDraw = new ArrayList<>(InveditGuiEditor.components);
        if (selected != null && !toDraw.contains(selected))
            toDraw.add(selected);

        toDraw.stream().sorted().forEach(component -> {
            g.setColor(Color.gray.brighter().brighter());
            Font originalFont = g.getFont();
            Stroke originalStroke = g.getStroke();

            int cx = component.getX(), cy = component.getY();
            if (component.equals(selected) && componentMoveMode) {
                cx = ox;
                cy = oy;
            }

            // paint actual component
            if (fakeEditor == null) {
                fakeEditor = InveditGuiEditor.getFakeEditor();
            }
            component.paintComponent(cx, cy, fakeEditor, g);

            g.setFont(originalFont);
            g.setStroke(originalStroke);
        });
    }

    private void drawGrid(Graphics2D g) {
        float[] dash = { 2.0f };
        g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash, 0.0f));

        // vertical lines
        for (int i = 0; i < getWidth(); i = i + grid_x_spacing) {
            if (i % (grid_x_spacing * 2) == 0)
                g.setColor(new Color(255, 255, 255, 80));
            else
                g.setColor(new Color(255, 255, 255, 30));
            g.drawLine(i + grid_x_offset, 0, i + grid_x_offset, getHeight());
        }

        // horizontal lines
        for (int i = 0; i < getHeight(); i = i + grid_y_spacing) {
            if (i % (grid_y_spacing * 2) == 0)
                g.setColor(new Color(255, 255, 255, 80));
            else
                g.setColor(new Color(255, 255, 255, 30));
            g.drawLine(0, i + grid_y_offset, getWidth(), i + grid_y_offset);
        }
    }

    @Override public void mouseMoved(MouseEvent e) {
        int ex = e.getX();
        int ey = e.getY();

        if (showGrid) {
            ex -= grid_x_offset;
            ey -= grid_y_offset;
            ex = Math.round(ex / (float) grid_x_spacing) * grid_x_spacing;
            ey = Math.round(ey / (float) grid_y_spacing) * grid_y_spacing;
            ex += grid_x_offset + 1;
            ey += grid_y_offset + 1;
        }

        // scale to coordinate system of the Minecraft
        ex = (int) Math.round(ex / 2.0);
        ey = (int) Math.round(ey / 2.0);

        if (positioningModeSettingWidth) {
            ow = Math.abs(ox - ex);
            if (positioningModeSettingHeight)
                oh = Math.abs(oy - ey);
        } else if (componentMoveMode) {
            ox = ex;
            oy = ey;
        }

        repaint();
    }

    @Override public void mouseClicked(MouseEvent e) {
        int ex = e.getX();
        int ey = e.getY();

        // scale to coordinate system of the Minecraft
        ex = (int) Math.round(ex / 2.0);
        ey = (int) Math.round(ey / 2.0);

        if (componentMoveMode) {
            if (e.getButton() == 1 || !(selected instanceof SizedComponent component) || positioningModeSettingWidth) {
                finishGUIComponentMove();
            } else {
                positioningModeSettingWidth = true;
                if (component.canChangeHeight())
                    positioningModeSettingHeight = true;
            }
        } else { // "click-on-component" mode
            GUIComponent component = getGUIComponentAt(ex, ey);
            if (component != null) {
                if (e.getClickCount() > 1) {
                    InveditGuiEditor.editCurrentlySelectedComponent();
                } else {
                    InveditGuiEditor.list.setSelectedValue(component, true);
                    InveditGuiEditor.list.requestFocus();
                }
            } else {
                InveditGuiEditor.list.clearSelection();
                this.selected = null;
                repaint();
            }
        }
    }

    @Override public void mouseDragged(MouseEvent e) {
        int ex = e.getX();
        int ey = e.getY();

        if (showGrid && componentDragMode) {
            ex -= grid_x_offset;
            ey -= grid_y_offset;
            ex = Math.round(ex / (float) grid_x_spacing) * grid_x_spacing;
            ey = Math.round(ey / (float) grid_y_spacing) * grid_y_spacing;
            ex += grid_x_offset + 1;
            ey += grid_y_offset + 1;
        }

        // scale to coordinate system of the Minecraft
        ex = (int) Math.round(ex / 2.0);
        ey = (int) Math.round(ey / 2.0);

        if (componentDragMode) {
            ox = ex + (showGrid ? 0 : dragOffsetX);
            oy = ey + (showGrid ? 0 : dragOffsetY);
        } else if (componentMoveMode) {
            if (selected instanceof SizedComponent component && !positioningModeSettingWidth) {
                positioningModeSettingWidth = true;
                if (component.canChangeHeight())
                    positioningModeSettingHeight = true;
            }
            mouseMoved(e); // if in move mode, we consider dragging as moving the mouse
        } else {
            GUIComponent component = getGUIComponentAt(ex, ey);
            if (component != null) {
                InveditGuiEditor.list.setSelectedValue(component, true);

                dragOffsetX = component.getX() - ex;
                dragOffsetY = component.getY() - ey;

                ox = ex + (showGrid ? 0 : dragOffsetX);
                oy = ey + (showGrid ? 0 : dragOffsetY);

                componentDragMode = true;
                moveMode();
            } else {
                InveditGuiEditor.list.clearSelection();
                this.selected = null;
                repaint();
            }
        }
    }

    @Override public void mouseEntered(MouseEvent e) {

    }

    @Override public void mouseExited(MouseEvent e) {

    }

    @Override public void mousePressed(MouseEvent e) {

    }

    @Override public void mouseReleased(MouseEvent e) {
        if (componentDragMode) {
            componentDragMode = false;
            finishGUIComponentMove();
        }
    }

    @Override public void paintPreZoom(Graphics g, Dimension d) {

    }

    @Override public void paintPostZoom(Graphics g, Dimension d) {

    }

    @Nullable private GUIComponent getGUIComponentAt(int ex, int ey) {
        List<GUIComponent> guiComponentList = new ArrayList<>(InveditGuiEditor.components);
        guiComponentList.sort(Collections.reverseOrder());

        for (GUIComponent component : guiComponentList) {
            if (ex >= component.getX() && ex <= component.getX() + component.getWidth(
                    InveditGuiEditor.mcreator.getWorkspace())) {
                if (ey >= component.getY() && ey <= component.getY() + component.getHeight(
                        InveditGuiEditor.mcreator.getWorkspace())) {
                    return component;
                }
            }
        }
        return null;
    }

    private void finishGUIComponentMove() {
        for (int i = 0; i < InveditGuiEditor.components.size(); i++) {
            GUIComponent component = InveditGuiEditor.components.get(i);
            if (component.equals(selected)) {
                if (!selected.locked) {
                    component.x = ox;
                    component.y = oy;
                    if (positioningModeSettingWidth
                            && component instanceof net.mcreator.element.parts.gui.SizedComponent) {
                        ((SizedComponent) component).width = ow;
                        ((SizedComponent) component).height = oh;
                    }
                }
                break;
            }
        }
        componentMoveMode = false;
        positioningModeSettingWidth = false;
        positioningModeSettingHeight = false;

        owner.setCursor(Cursor.getDefaultCursor());

        repaint();
    }

}