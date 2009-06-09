/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.Timer;
import org.das2.util.ClassMap;

/**
 *
 * @author jbf
 */
public class CanvasLayoutPanel extends JLabel {

    JComponent target;
    ClassMap<Color> types;
    Timer timer;

    public CanvasLayoutPanel() {
        types = new ClassMap<Color>();
        timer= new Timer(100,new ActionListener(){
            public void actionPerformed( ActionEvent e ) {
                repaint();
            }
        });
        timer.setRepeats(false);
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
                        if ( e.getModifiersEx()==MouseEvent.CTRL_DOWN_MASK ) {
                            if ( selectedComponents.contains(c) ) {
                                selectedComponents.remove(c);
                                component = null;
                            } else {
                                selectedComponents.add(c);
                                component = c;
                            }
                        } else {
                            component= c;
                            selectedComponents.clear();
                            selectedComponents.add(c);
                        }
                        repaint();
                        firePropertyChange(PROP_COMPONENT, null, c);
                        if ( e.getModifiers()==MouseEvent.CTRL_DOWN_MASK ) {
                            firePropertyChange( PROP_SELECTEDCOMPONENTS, null, selectedComponents );
                        }
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

    protected List<Object> selectedComponents = new ArrayList();
    public static final String PROP_SELECTEDCOMPONENTS = "selectedComponents";

    public List<Object> getSelectedComponents() {
        return new ArrayList(selectedComponents);
    }

    public void setSelectedComponents(List<Object> selectedComponents) {
        List<Object> oldSelectedComponents = this.selectedComponents;
        this.selectedComponents = selectedComponents;
        propertyChangeSupport.firePropertyChange(PROP_SELECTEDCOMPONENTS, oldSelectedComponents, selectedComponents);
    }
    private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

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
        Stroke selectedStroke= new BasicStroke( 3.f );
        Stroke normalStroke= new BasicStroke( 1.f );

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
                    g.setStroke(selectedStroke);
                    g.drawRoundRect(mbounds.x, mbounds.y, mbounds.width, mbounds.height, 10, 10);
                    g.setStroke(normalStroke);
                } else if ( selectedComponents.contains(c)) {
                    g.setColor(getTranslucentColor(color, 130));
                    g.fillRoundRect(mbounds.x, mbounds.y, mbounds.width, mbounds.height, 10, 10);
                    g.setColor(getTranslucentColor(color, 220));
                    g.drawRoundRect(mbounds.x, mbounds.y, mbounds.width, mbounds.height, 10, 10);
                } else {
                    g.setColor(getTranslucentColor(color, 60));
                    g.fillRoundRect(mbounds.x, mbounds.y, mbounds.width, mbounds.height, 10, 10);
                    g.setColor(getTranslucentColor(color, 160));
                    g.drawRoundRect(mbounds.x, mbounds.y, mbounds.width, mbounds.height, 10, 10);
                }
            }
        }
    }
    
    transient ComponentListener componentListener = new ComponentListener() {

        public void componentResized(ComponentEvent e) {
            timer.restart();
        }

        public void componentMoved(ComponentEvent e) {
            timer.restart();
        }

        public void componentShown(ComponentEvent e) {
            timer.restart();
        }

        public void componentHidden(ComponentEvent e) {
            timer.restart();
        }
    };

    public void setContainer(JComponent c) {
        this.target = c;
        c.addContainerListener(new ContainerListener() {

            public void componentAdded(ContainerEvent e) {
                e.getChild().addComponentListener(componentListener);
                timer.restart();
            }

            public void componentRemoved(ContainerEvent e) {
                e.getChild().removeComponentListener(componentListener);
                timer.restart();
            }
        });

    }

    public void addComponentType(Class c, Color color) {
        this.types.put(c, color);
    }
}
