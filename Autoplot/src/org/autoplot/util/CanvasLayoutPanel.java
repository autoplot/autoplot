
package org.autoplot.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Event;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.Timer;
import org.autoplot.pngwalk.ImageResize;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasPlot;
import org.das2.graph.GraphUtil;
import org.das2.graph.Renderer;
import org.das2.util.ClassMap;

/**
 * This is the small GUI in the upper left corner of the layout tab, which
 * shows abstractly where plots sit in relation to one another, for
 * reference.
 * 
 * @author jbf
 */
public class CanvasLayoutPanel extends JLabel {

    private JComponent target;
    private ClassMap<Color> types;
    private Timer timer;
    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.layout.panel");
    private Rectangle cursor= null; // initial click for range select.

    public CanvasLayoutPanel() {
        types = new ClassMap<>();
        timer= new Timer(100,new ActionListener(){
            @Override
            public void actionPerformed( ActionEvent e ) {
                repaint();
            }
        });
        timer.setRepeats(false);
        getCanvasImageTimer= new Timer( 100, new ActionListener() {        
            @Override
            public void actionPerformed(ActionEvent e) {
                itsme= true;
                BufferedImage img= new BufferedImage( target.getWidth(), target.getHeight(), BufferedImage.TYPE_INT_ARGB );
                ((DasCanvas)target).writeToImageImmediatelyNoCount(img);
                canvasImage= img;
                itsme= false;
                repaint();
            }
        });
        getCanvasImageTimer.setRepeats(false);
        addMouseListener(mouseListener);
    }

    private transient BufferedImage canvasImage= null;
    
    private boolean itsme= false;
    private Timer getCanvasImageTimer;
    
    private boolean rectEdgeClicked( Rectangle r, int x, int y ) {
        boolean e0= Math.abs( r.getX() - x ) < 10 ;
        boolean e1= Math.abs( r.getY() - y ) < 10 ;
        boolean e2= Math.abs( r.getX() + r.getWidth() - x ) < 10;
        boolean e3= Math.abs( r.getY() + r.getHeight() - y ) < 10;
        return ( e0 || e1 || e2 || e3 ) && r.intersects( x-10, y-10, x+20, y+20);
    }

    private transient boolean handlingEvent= false;
    
    private final transient MouseListener mouseListener = new MouseAdapter() {

        @Override
        public void mouseClicked(MouseEvent e) {
            final int km = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
            long t0= System.currentTimeMillis();
            logger.log(Level.FINE, "mouseClicked: {0} getMenuShortcutKeyMask={1}", new Object[] { e.getModifiers(), km } );

            if ( ( e.getModifiers() & km )== 0 && ( e.getModifiers() & Event.SHIFT_MASK )== 0 && ( e.getModifiers()>16 ) ) {
                return;
            }
            if (target == null) {
                return;
            }
            int twidth = target.getWidth();
            int theight= target.getHeight();
            int mwidth = getWidth();
            double scale= (double) mwidth / twidth;
            if ( theight * scale > getHeight() ) {
               scale= (double)getHeight() / theight;
            }
            
            boolean shiftClick=  ( e.getModifiers() & Event.SHIFT_MASK ) == Event.SHIFT_MASK;
            if ( !shiftClick ) {
                for (int i = target.getComponentCount() - 1; i >= 0; i--) {
                    Component c = target.getComponent(i);
                    Color color = types.get(c.getClass());
                    if (color != null ) {
                        java.awt.Rectangle bounds = ((JComponent) c).getBounds();
                        Rectangle mbounds = new Rectangle( (int)( bounds.x * scale ),
                                (int)( bounds.y * scale ),
                                (int)( bounds.width * scale ),
                                (int)( bounds.height * scale) );
                        // if the click is on an edge of an invisible component, select it.
                        boolean invisibleEdgeClick= !c.isVisible() && rectEdgeClicked( mbounds, e.getX(), e.getY() );
                        if ( c.isVisible() || invisibleEdgeClick ) {
                            if ( mbounds.contains(e.getX(), e.getY()) || invisibleEdgeClick ) {
                                if ( ( e.getModifiers() & km ) ==km ) {
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
                                    cursor= new Rectangle( (int)( e.getX() / scale ), (int)( e.getY() / scale ), 1, 1 );
                                }
                                repaint();
                                handlingEvent= true;
                                firePropertyChange(PROP_COMPONENT, null, c);
                                if ( ( e.getModifiers() & km ) == km ) {
                                    firePropertyChange( PROP_SELECTEDCOMPONENTS, null, selectedComponents );
                                }
                                handlingEvent= false;
                            }
                        }
                    }
                }
            } else {
                Rectangle m= new Rectangle( (int)( e.getX() / scale ), (int)( e.getY() / scale), 1, 1 );
                Rectangle select;
                if ( cursor==null ) {
                    select= m;
                } else {
                    select= m;
                    Rectangle.union( cursor, m, select );
                }
                List<Object> newSelect= new ArrayList();
                for (int i = target.getComponentCount() - 1; i >= 0; i--) {
                    Component c= target.getComponent(i);
                    Color color = types.get(c.getClass());
                    if ( color!=null ) {
                        if ( select.intersects(c.getBounds() ) ) {
                            newSelect.add(c);
                        }
                    }
                }
                if ( !selectedComponents.equals(newSelect) ) {
                    selectedComponents.clear();
                    selectedComponents.addAll(newSelect);
                    handlingEvent= true;
                    firePropertyChange( PROP_SELECTEDCOMPONENTS, null, selectedComponents );
                    handlingEvent= false;
                    repaint();
                }
            }
            long ms= System.currentTimeMillis() - t0;
            logger.log(Level.FINE, "done in {0}ms mouseClicked: {1} getMenuShortcutKeyMask={2}", new Object[] { ms, e.getModifiers(), km } ); // this takes 700 ms to complete!!!
        }
    };
    protected Object component = null;
    public static final String PROP_COMPONENT = "component";

    /**
     * get the primary selected component.
     * @return 
     */
    public Object getComponent() {
        return component;
    }

    /**
     * return the plot component at the position on this GUI.
     * @param x the x position, 0 is left side of this component.
     * @param y the x position, 0 is top of this component.
     * @return DasPlot, DasAxis, etc.
     */
    public Object getCanvasComponentAt( int x, int y ) {
        int twidth = target.getWidth();
        int theight= target.getHeight();
        int mwidth = getWidth();
        double scale= (double) mwidth / twidth;
        if ( theight * scale > getHeight() ) {
           scale= (double)getHeight() / theight;
        }
        Rectangle m= new Rectangle( (int)( x / scale ), (int)( y / scale), 1, 1 );
        Rectangle select;
        select= m;

        List<Object> newSelect= new ArrayList();
        for (int i = target.getComponentCount() - 1; i >= 0; i--) {
            Component c= target.getComponent(i);
            Color color = types.get(c.getClass());
            if ( color!=null ) {
                if ( select.intersects(c.getBounds() ) ) {
                    newSelect.add(c);
                }
            }
        } 
        if ( newSelect.isEmpty() ) {
            return null;
        } else {
            return newSelect.get(0);
        }
    }
    
    /**
     * get the canvas components within the rectangle.
     * @param r rectangle within the GUI.
     * @return 
     */
    public List<Object> getCanvasComponentsWithin( Rectangle r ) {
        int twidth = target.getWidth();
        int theight= target.getHeight();
        int mwidth = getWidth();
        double scale= (double) mwidth / twidth;
        if ( theight * scale > getHeight() ) {
           scale= (double)getHeight() / theight;
        }
        r= new Rectangle( (int)( r.x / scale ), (int)( r.y / scale), (int)(r.width/scale), (int)(r.height/scale) );
        List<Object> newSelect= new ArrayList();
        for (int i = target.getComponentCount() - 1; i >= 0; i--) {
            Component c= target.getComponent(i);
            Color color = types.get(c.getClass());
            if ( color!=null ) {
                Rectangle b= GraphUtil.shrinkRectangle(c.getBounds(),30);
                if ( r.contains( b ) ) {
                    newSelect.add(c);
                }
            }
        } 
        return newSelect;
    }
    
    private Rectangle rectangleSelect=null;
    
    /**
     * set the bounds of the selecting rectangle, to provide feedback.
     * @param r 
     */
    public void setRectangleSelect( Rectangle r ) {
        this.rectangleSelect=r;
    }
    
    /**
     * set the primary selected component.
     * @param component null or the selected component.
     */
    public void setComponent(Object component) {
        
        if ( handlingEvent ) return;
        
        logger.log(Level.FINER, "setComponent({0})", component);
        Object oldComponent = this.component;
        this.component = component;
        handlingEvent= true;
        firePropertyChange(PROP_COMPONENT, oldComponent, component);
        handlingEvent= false;
        repaint();
    }

    protected List<Object> selectedComponents = new ArrayList();
    public static final String PROP_SELECTEDCOMPONENTS = "selectedComponents";

    /**
     * get the user-selected components.
     * @return a list containing the components.
     */
    public List<Object> getSelectedComponents() {
        return new ArrayList(selectedComponents);
    }

    /**
     * set the selected components.
     * @param selectedComponents 
     */
    public void setSelectedComponents(List<Object> selectedComponents) {
        if ( handlingEvent ) return;
        logger.log(Level.FINER, "setSelectedComponents({0})", selectedComponents);
        List<Object> oldSelectedComponents = this.selectedComponents;
        this.selectedComponents = new ArrayList( selectedComponents );
        handlingEvent= true;
        propertyChangeSupport.firePropertyChange(PROP_SELECTEDCOMPONENTS, oldSelectedComponents, selectedComponents);
        handlingEvent= false;
        repaint();
    }
    
    /**
     * set the selected components to those within the rectangle.
     * @param r rectangle in GUI coordinates.
     */
    public void setSelectedComponents( Rectangle r ) {
        setSelectedComponents( getCanvasComponentsWithin( r ) );
    }
    
    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

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
        logger.log(Level.FINER, "paintComponent target={0}", target);
        if (target == null) {
            return;
        }
        Graphics2D g = (Graphics2D) g1;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(getBackground());
        int theight = target.getHeight();
        int twidth = target.getWidth();
        int mwidth = getWidth();
        double scale= (double) mwidth / twidth;
        if ( theight * scale > getHeight() ) {
            scale= (double) getHeight() / theight;
        }
        g.fillRect(0, 0, (int)( twidth * scale ), (int)( theight * scale ) );
        Stroke selectedStroke= new BasicStroke( 3.f );
        Stroke normalStroke= new BasicStroke( 1.f );

        BufferedImage img= canvasImage;
        if ( img!=null ) {
            img= ImageResize.getScaledInstance( img,
                                                (int)( target.getWidth() * scale ),
                                                (int)( target.getHeight() * scale ),
                                                RenderingHints.VALUE_INTERPOLATION_BILINEAR, true );

            g.drawImage( img, 0,0, this );

            // mute colors and lines
            Color back= target.getBackground();
            g.setColor( new Color( back.getRed(), back.getGreen(), back.getBlue(), 100 ) );
            g.fillRect(0,0,img.getWidth(),img.getHeight());
        }

        Graphics2D gs= (Graphics2D)g.create();
        AffineTransform at= g.getTransform();
        at.scale(scale, scale);
        gs.setTransform(at);
        gs.setColor(  new Color( 255, 255, 0, 180 ) );
        for (int i = 0; i < target.getComponentCount(); i++) {
            Component c = target.getComponent(i);
            if ( c instanceof DasPlot ) {
                DasPlot plot= (DasPlot)c;
                gs.setClip( plot.getBounds() );
                for ( Renderer r: plot.getRenderers() ) {
                    if ( selectedComponents.contains(r) ) {
                        Shape s = org.das2.graph.SelectionUtil.getSelectionArea(r);
                        gs.fill(s);
                    }
                }
            }
        }

        for (int i = 0; i < target.getComponentCount(); i++) {
            Component c = target.getComponent(i);
            Color color = types.get(c.getClass());
            if (color != null) {
                java.awt.Rectangle bounds = ((JComponent) c).getBounds();
                Rectangle mbounds = new Rectangle( (int)( bounds.x * scale ),
                        (int)( bounds.y * scale ),
                        (int)( bounds.width * scale ),
                        (int)( bounds.height * scale) );
                if ( !c.isVisible() ) {
                    g.setColor(getTranslucentColor(color, 160));
                    Graphics2D g2= g;
                    if ( selectedComponents.contains(c) ) {
                        g2.setStroke( selectedStroke );
                    } else {
                        g2.setStroke( normalStroke );
                    }
                    mbounds= new Rectangle( mbounds.x+5, mbounds.y+5, mbounds.width-10, mbounds.height-10 );
                    g2.drawRoundRect(mbounds.x, mbounds.y, mbounds.width, mbounds.height, 10, 10);
                    g2.setStroke(normalStroke);
                } else {
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

        if ( rectangleSelect!=null ) {
            g.setColor( Color.GRAY );
            g.draw(rectangleSelect);
        }

    }
    
    private final transient ComponentListener componentListener = new ComponentListener() {
        @Override
        public void componentResized(ComponentEvent e) {
            timer.restart();
        }
        @Override
        public void componentMoved(ComponentEvent e) {
            timer.restart();
        }
        @Override
        public void componentShown(ComponentEvent e) {
            timer.restart();
        }
        @Override
        public void componentHidden(ComponentEvent e) {
            timer.restart();
        }
    };
            
    private final transient PropertyChangeListener repaintListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            logger.finer("canvas was painted, get a screenshot.");
            if ( itsme ) {
                logger.finer("its me...");
            } else {
                getCanvasImageTimer.restart();
            }
        }
    };
    
    /**
     * this is the JComponent we are monitoring.  If this is a
     * DasCanvas, then a special listener is added for repaints.
     * @param c 
     */
    public void setContainer( DasCanvas c) {
        if ( this.target!=null ) {            
            this.target.removePropertyChangeListener( DasCanvas.PROP_PAINTCOUNT, repaintListener );
        }
        this.target = c;
        c.addPropertyChangeListener( DasCanvas.PROP_PAINTCOUNT, repaintListener );
        
        c.addContainerListener(new ContainerListener() {
            @Override
            public void componentAdded(ContainerEvent e) {
                e.getChild().addComponentListener(componentListener);
                timer.restart();
            }
            @Override
            public void componentRemoved(ContainerEvent e) {
                e.getChild().removeComponentListener(componentListener);
                timer.restart();
            }
        });

    }

    /**
     * mark this type of component with the given color.
     * @param c the class of the component, like org.das2.graph.DasPlot.class
     * @param color the color, like Color.BLUE
     */
    public void addComponentType(Class c, Color color) {
        this.types.put(c, color);
    }
}
