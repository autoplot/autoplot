
package org.autoplot.datasource;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.das2.util.LoggerManager;

/**
 * Utility class for working with GUIs, first introduced to listen for
 * who is setting the minimum size.
 * @author jbf
 */
public class GuiUtil {
    
    private static final Logger logger= LoggerManager.getLogger("apdss.gui");
    
    private static void dumpAllMinSizes( String indent, Component c ) {
        System.err.println( String.format( "%s%s %d", indent, c.getName(), c.getMinimumSize().width ) );
        if ( c instanceof JComponent ) {
            JComponent jc= (JComponent)c;
            for ( int i=0; i<jc.getComponentCount(); i++ ) {
                dumpAllMinSizes( indent + "  ", jc.getComponent(i) );
            }
        } 
    }
    
    /**
     * utility for figuring out who is setting the minimum size of a
     * component.
     * @param c the parent component.
     */
    public static void addResizeListenerToAll( JComponent c ) {
        ComponentListener l=  new ComponentListener() {

            @Override
            public void componentResized(ComponentEvent e) {
                int minw= e.getComponent().getMinimumSize().width;
                System.err.println("resize "+ e.getComponent().getName() + " minw=" + e.getComponent().getMinimumSize().width + " " + e.getComponent().getWidth() + " " + e.getID());
                if ( minw==e.getComponent().getWidth() ) {
                    dumpAllMinSizes( "  ", e.getComponent() );
                } else {
                }
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                
            }

            @Override
            public void componentShown(ComponentEvent e) {

            }

            @Override
            public void componentHidden(ComponentEvent e) {

            }
            
        };        
        for ( int i=0; i<c.getComponentCount(); i++ ) {
            c.getComponent(i).addComponentListener(l);
        }
    }
    
    /**
     * scan through all the child components looking to see if there is a 
     * JScrollPane.  This was introduced when a JSplitPane with two JScrollPanes
     * was used with addTab, and an extra JScrollPane was added.
     * @param c the component
     * @return true if a child has a scroll
     */
    public static boolean hasScrollPane( JComponent c ) {       
        if ( c instanceof JScrollPane ) {
            return true;
        } else {
            for ( int i=0; i<c.getComponentCount(); i++ ) {
                Component child= c.getComponent(i);
                if ( child instanceof JScrollPane ) {
                    return true;
                } else if ( child instanceof JComponent ) {
                    if ( hasScrollPane( (JComponent)child ) ) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
    // see SlicesFilterEditorPanel for addMouseWheelListenerToSpinner
    
    private static void collectButtons(Component c, List<JButton> out) {
        if (c instanceof JButton) {
            out.add((JButton) c);
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                collectButtons(child, out);
            }
        }
    }
    
    /**
     * resize the dialog on the event thread to show all buttons.  Thanks ChatGPT!
     * @param dialog
     */
    public static void ensureAllButtonsVisible( final Window dialog ) {
        logger.fine("ensureAllButtonsVisible");
        SwingUtilities.invokeLater(() -> {
            List<JButton> children= new ArrayList<>();
            collectButtons( dialog, children );
            Rectangle result= new Rectangle(0, 0, 0, 0);
            int dlgWidth  = dialog.getWidth();
            int dlgHeight = dialog.getHeight();

            for ( JButton c: children ) {
                Point p = SwingUtilities.convertPoint( c, 0, 0, dialog);
                int left   = p.x;
                int top    = p.y;
                int right  = left + c.getWidth();
                int bottom = top + c.getHeight();

                if ( c.getHeight()<=0 || c.getWidth()<=0 ) {

                } else {
                    Rectangle r= new Rectangle( left, top, c.getWidth(), c.getHeight() );
                    result.add( r );
                }
            }
            Insets insets= dialog.getInsets();
            int requireX= result.width+insets.left+insets.right;
            int requireY= result.height+insets.top+insets.bottom;
            if ( dialog.getWidth()<requireX || dialog.getHeight()<requireY ) {
                if ( dialog.getWidth()>requireX ) {
                    requireX= dialog.getWidth();
                }
                if ( dialog.getHeight()>requireY ) {
                    requireY= dialog.getHeight();
                }
                dialog.setPreferredSize( new Dimension(requireX,requireY) );
                dialog.setSize( new Dimension(requireX,requireY) );
                dialog.pack();
                dialog.validate();
            }      
        });
    }
    
    /**
     * resize the dialog to clearly show all the buttons in a dialog after it is
     * made visible.  This will add a componentAdapter to the dialog.
     * @param dialog
     */
    public static void ensureAllButtonsVisibleSoon( final Window dialog ) {
        logger.fine("ensureAllButtonsVisibleSoon");
        dialog.addComponentListener(new ComponentAdapter() {
            private boolean first = true;
            @Override
            public void componentShown(ComponentEvent e) {
                if (first) {
                    first = false;
                    GuiUtil.ensureAllButtonsVisible(dialog);
                }
            }
        });
    }
}
