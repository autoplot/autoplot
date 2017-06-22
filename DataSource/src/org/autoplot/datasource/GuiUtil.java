/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.datasource;

import java.awt.Component;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import javax.swing.CellRendererPane;
import javax.swing.JComponent;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

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
    
    // see SlicesFilterEditorPanel for addMouseWheelListenerToSpinner
    
}
