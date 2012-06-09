/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test;

import javax.swing.JMenuItem;
import org.das2.graph.DasPlot;
import org.netbeans.jemmy.ComponentChooser;
import org.netbeans.jemmy.operators.JPopupMenuOperator;
import util.RegexComponentChooser;

/**
 *
 * @author jbf
 */
public class Util {

    /**
     * push the context menu item identified by the items.  This is a
     * list of regular expressions identifying the levels.
     * @param c
     * @param items
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
}
