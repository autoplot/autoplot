/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.scriptconsole;

import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.text.BadLocationException;
import org.virbo.datasource.DataSetSelector;

/**
 *
 * @author jbf
 */
public class EditorContextMenu {

    private EditorTextPane editor;
    private JPopupMenu menu;
    private DataSetSelector dataSetSelector;

    EditorContextMenu( EditorTextPane edit ) {
        this.editor = edit;
        editor.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if ( e.getButton()==MouseEvent.BUTTON3 ) {
                    maybeCreateMenu();
                    menu.show( editor, e.getX(), e.getY() );
                }
            }
        });

    }
    
    public void setDataSetSelector( DataSetSelector sel ) {
        this.dataSetSelector= sel;
    }

    private synchronized void maybeCreateMenu() {
        if ( menu==null ) {
            menu= new JPopupMenu();
            JMenu insertCodeMenu= new JMenu("Insert Code");
            insertCodeMenu.add( new AbstractAction("getDataSet()") {
                public void actionPerformed(ActionEvent e) {
                    String surl= dataSetSelector.getValue();
                    insertCode( "getDataSet('"+surl+"')\n");
                }
            });
            menu.add( insertCodeMenu );
        }
    }
    
    private void insertCode( String code ) {
        try {
            editor.getDocument().insertString(editor.getCaretPosition(), code, null);
        } catch (BadLocationException ex) {
            Logger.getLogger(EditorContextMenu.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
