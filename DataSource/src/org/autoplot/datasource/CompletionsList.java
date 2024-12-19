/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.datasource;

import java.awt.event.ActionEvent;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.autoplot.datasource.DataSetURI.CompletionResult;

/**
 * Old class, which I believe is no longer used.
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
            //int stopAt = Math.min(i + 30, completions.size());
            //int stopAt= 10000;
            while (i < completions.size() ) {
                final CompletionResult s1 = completions.get(i);
                int icaret= s1.completable==null ? 0 : s1.completable.length();
                if ( s1==CompletionResult.SEPARATOR ) {
                    //menuItem= new javax.swing.JButton("HELLO");
                    menuItem= new javax.swing.JSeparator();
                    //menuItem.setMinimumSize( new Dimension(20,20) );  Linux suppresses these...
                    
                } else {
                    String label = s1.label;
                    if (label.startsWith(labelprefix)) {
                        label = label.substring(labelprefix.length());
                    }
                    int i2= s1.completion.lastIndexOf("?");
                    if ( i2>-1 ) {
                        int i3= s1.completion.lastIndexOf("&");
                        if ( i3>-1 ) i2=i3;
                        i3= s1.completion.lastIndexOf("=");
                        if ( i3>-1 && i3<s1.completion.length()-1 && i3>i2 ) i2=i3;
                    }
                    String ll= s1.completion.substring(i2+1);
                    if ( i2>-1 && !label.startsWith(ll) && (i2<=icaret) ) {
                        label= ll + ": "+ label ;
                    }
                    Action a = new AbstractAction(label) {
                        @Override
                        public void actionPerformed(ActionEvent ev) {
                            org.das2.util.LoggerManager.logGuiEvent(ev);                    
                            listener.itemSelected(s1);
                        }
                    };
                    JMenuItem item= new JMenuItem(a);
                    if ( s1.doc!=null ) item.setToolTipText(s1.doc);
                    if ( !s1.label.endsWith("/") && s1.completion.contains("?") ) {
                        if ( s1.maybePlot ) item.setIcon( new javax.swing.ImageIcon( CompletionsList.class.getResource("/org/autoplot/datasource/go-small.png")) );
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
        if (completions.isEmpty()) {
            popupMenu.add("<html><i>(empty)</i></html>");
        }
        return popupMenu;
    }

}
