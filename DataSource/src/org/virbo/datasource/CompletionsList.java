/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import org.virbo.datasource.DataSetURI.CompletionResult;

/**
 *
 * @author jbf
 */
public class CompletionsList {
    
    public interface CompletionListListener {
        public void itemSelected( CompletionResult item );
    }
    
    public static JPopupMenu fillPopupNew( final List<CompletionResult> completions,
            final String labelprefix, 
            JPopupMenu popupMenu, 
            final CompletionListListener listener ) {

        JMenu subMenu = null;

        JComponent menuItem;
        
        int i = 0;
        while (i < completions.size()) {
            int stopAt = Math.min(i + 30, completions.size());
            while (i < stopAt) {
                final CompletionResult s1 = completions.get(i);
                if ( s1==CompletionResult.SEPARATOR ) {
                    //menuItem= new javax.swing.JButton("HELLO");
                    menuItem= new javax.swing.JSeparator();
                    //menuItem.setMinimumSize( new Dimension(20,20) );  Linux suppresses these...
                    
                } else {
                    String label = s1.label;
                    if (label.startsWith(labelprefix)) {
                        label = label.substring(labelprefix.length());
                    }
                    Action a = new AbstractAction(label) {
                        public void actionPerformed(ActionEvent ev) {
                            listener.itemSelected(s1);
                        }
                    };
                    JMenuItem item= new JMenuItem(a);
                    if ( s1.doc!=null ) item.setToolTipText(s1.doc);
                    if ( !s1.label.endsWith("/") && s1.completion.contains("?") ) {
                        if ( s1.maybePlot ) item.setIcon( new javax.swing.ImageIcon( CompletionsList.class.getResource("/org/virbo/datasource/go-small.png")) );
                    }
                    menuItem= item;
                }
                if (subMenu == null) {
                    popupMenu.add(menuItem);
                } else {
                    subMenu.add(menuItem);
                }
                i++;
            }
            if (i < completions.size()) {
                JMenu nextSubMenu = new JMenu("more");
                if (subMenu == null) {
                    popupMenu.add(nextSubMenu);
                } else {
                    subMenu.add(nextSubMenu);
                }
                subMenu = nextSubMenu;
            }
        }
        if (completions.size() == 0) {
            popupMenu.add("<html><em>(empty)</em></html>");
        }
        return popupMenu;
    }

}
