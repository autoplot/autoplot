/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.test;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import javax.swing.JMenuItem;
import javax.swing.UnsupportedLookAndFeelException;
import org.das2.graph.DasPlot;
import org.netbeans.jemmy.ComponentChooser;
import org.netbeans.jemmy.operators.JPopupMenuOperator;
import org.netbeans.jemmy.operators.JTabbedPaneOperator;
import org.autoplot.AutoplotUI;
import util.RegexComponentChooser;

/**
 *
 * @author jbf
 */
public class Util {

    private static final Logger logger= Logger.getLogger("vatesting");
    
    /**
     * push the context menu item identified by the items.  This is a
     * list of regular expressions identifying the levels.
     * @param c the focus plot
     * @param items labels, eg [ "Plot Style", "Series" ]
     * @return the JMenuItem found, which has been pushed.
     */
    public static JMenuItem pushContextMenu( DasPlot c, String[] items ) {
            javax.swing.JPopupMenu menu= c.getDasMouseInputAdapter().getPrimaryPopupMenu();
            menu.show( c, 300, 300 );
            JPopupMenuOperator op= new JPopupMenuOperator( menu );
            
            ComponentChooser[] push= new ComponentChooser[items.length];
            for ( int i=0; i<items.length; i++ ) {
                push[i]= new RegexComponentChooser(items[i] );
            }
            JMenuItem item= op.pushMenu( push );
            return item;
    }

    /**
     * set the look and feel to the local platform
     */
    static void setLAF() {
        try {
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * switch to the named tab
     * @param app
     * @param name  
     */
    static void switchToTab( AutoplotUI app, String name ) {
        new JTabbedPaneOperator( app.getTabs() ).selectPage(name);
    }
}
