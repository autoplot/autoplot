/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource;

import java.awt.event.ActionEvent;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/**
 *
 * @author jbf
 */
public class CompletionsList {
    
    public interface CompletionListListener {
        public void itemSelected( DataSetURL.CompletionResult item );
    }
    
    public static JPopupMenu fillPopupNew( final List<DataSetURL.CompletionResult> completions, 
            final String labelprefix, 
            JPopupMenu popupMenu, 
            final CompletionListListener listener ) {

        JMenu subMenu = null;

        int i = 0;
        while (i < completions.size()) {
            int stopAt = Math.min(i + 30, completions.size());
            while (i < stopAt) {
                final DataSetURL.CompletionResult s1 = completions.get(i);
                String label = s1.label;
                if (label.startsWith(labelprefix)) {
                    label = label.substring(labelprefix.length());
                }
                Action a = new AbstractAction(label) {
                    public void actionPerformed(ActionEvent ev) {
                        listener.itemSelected(s1);
                    }
                };
                JMenuItem menuItem= new JMenuItem(a);
                if ( s1.doc!=null ) menuItem.setToolTipText(s1.doc);
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
