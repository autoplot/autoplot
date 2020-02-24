
package org.autoplot.datasource;

import java.awt.Component;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import javax.swing.JComponent;
import javax.swing.JScrollPane;

/**
 * Utility class for working with GUIs, first introduced to listen for
 * who is setting the minimum size.
 * @author jbf
 */
public class GuiUtil {
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
    
}
