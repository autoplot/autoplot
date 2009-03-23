/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.util;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.das2.util.ClassMap;

/**
 *
 * @author jbf
 */
public class CanvasLayoutPanel extends JLabel {

    JComponent target;
    ClassMap<Color> types;

    public CanvasLayoutPanel() {
        types = new ClassMap<Color>();
        addMouseListener(mouseListener);
    }
    transient MouseListener mouseListener = new MouseAdapter() {

        @Override
        public void mouseClicked(MouseEvent e) {
            if (target == null) {
                return;
            }
            int twidth = target.getWidth();
            int mwidth = getWidth();
            for (int i = target.getComponentCount() - 1; i >= 0; i--) {
                Component c = target.getComponent(i);
                Color color = types.get(c.getClass());
                if (color != null) {
                    java.awt.Rectangle bounds = ((JComponent) c).getBounds();
                    Rectangle mbounds = new Rectangle(bounds.x * mwidth / twidth,
                            bounds.y * mwidth / twidth,
                            bounds.width * mwidth / twidth,
                            bounds.height * mwidth / twidth);
                    if (mbounds.contains(e.getX(), e.getY())) {
                        component = c;
                        repaint();
                        firePropertyChange(PROP_COMPONENT, null, c);
                    }
                }
            }
        }
    };
    protected Object component = null;
    public static final String PROP_COMPONENT = "component";

    public Object getComponent() {
        return component;
    }

    public void setComponent(Object component) {
        Object oldComponent = this.component;
        this.component = component;
        repaint();
        firePropertyChange(PROP_COMPONENT, oldComponent, component);
    }

    /**
     * get a translucent version of this color.
     * @param c
     * @param alpha 255 means completely opaque, 0 means transparent.
     * @return
     */
    private Color getTranslucentColor(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    @Override
    protected void paintComponent(Graphics g1) {
        if (target == null) {
            return;
        }
        Graphics2D g = (Graphics2D) g1;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(getBackground());
        int theight = target.getHeight();
        int twidth = target.getWidth();
        int mwidth = getWidth();
        g.fillRect(0, 0, mwidth, theight * mwidth / twidth);
        for (int i = 0; i < target.getComponentCount(); i++) {
            Component c = target.getComponent(i);
            Color color = types.get(c.getClass());
            if (color != null) {
                java.awt.Rectangle bounds = ((JComponent) c).getBounds();
                Rectangle mbounds = new Rectangle(bounds.x * mwidth / twidth,
                        bounds.y * mwidth / twidth,
                        bounds.width * mwidth / twidth,
                        bounds.height * mwidth / twidth);
                if (c == component) {
                    g.setColor(getTranslucentColor(color, 160));
                    g.fillRoundRect(mbounds.x, mbounds.y, mbounds.width, mbounds.height, 10, 10);
                    g.setColor(getTranslucentColor(color, 220));
                    g.drawRoundRect(mbounds.x, mbounds.y, mbounds.width, mbounds.height, 10, 10);
                } else {
                    g.setColor(getTranslucentColor(color, 100));
                    g.fillRoundRect(mbounds.x, mbounds.y, mbounds.width, mbounds.height, 10, 10);
                    g.setColor(getTranslucentColor(color, 160));
                    g.drawRoundRect(mbounds.x, mbounds.y, mbounds.width, mbounds.height, 10, 10);
                }
            }
        }
    }
    
    transient ComponentListener componentListener = new ComponentListener() {

        public void componentResized(ComponentEvent e) {
            repaint();
        }

        public void componentMoved(ComponentEvent e) {
            repaint();
        }

        public void componentShown(ComponentEvent e) {
            repaint();
        }

        public void componentHidden(ComponentEvent e) {
            repaint();
        }
    };

    public void setContainer(JComponent c) {
        this.target = c;
        c.addContainerListener(new ContainerListener() {

            public void componentAdded(ContainerEvent e) {
                e.getChild().addComponentListener(componentListener);
                repaint();
            }

            public void componentRemoved(ContainerEvent e) {
                e.getChild().removeComponentListener(componentListener);
                repaint();
            }
        });

    }

    public void addComponentType(Class c, Color color) {
        this.types.put(c, color);
    }
}
