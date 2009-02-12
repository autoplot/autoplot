/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.scriptconsole;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import javax.swing.AbstractAction;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.undo.UndoManager;

/**
 *
 * @author jbf
 */
public class EditorTextPane extends JTextPane {
    
    private EditorAnnotationsSupport support= new EditorAnnotationsSupport( this );

    public EditorTextPane() {

        final UndoManager undo = new UndoManager();
        getDocument().addUndoableEditListener(undo);

        getActionMap().put( "undo", new AbstractAction( undo.getUndoPresentationName() ) {
            public void actionPerformed( ActionEvent e ) {
                undo.undo();
            }
        });

        getActionMap().put( "redo", new AbstractAction( undo.getRedoPresentationName() ) {
            public void actionPerformed( ActionEvent e ) {
                undo.redo();
            }
        });

        getInputMap().put( KeyStroke.getKeyStroke( KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK ), "undo" );
        getInputMap().put( KeyStroke.getKeyStroke( KeyEvent.VK_Y, KeyEvent.CTRL_DOWN_MASK ), "redo" );
    }


    @Override
    public String getToolTipText( MouseEvent event ) {
        return support.getToolTipText(event);
    }

    public void setEditorAnnotationsSupport( EditorAnnotationsSupport support ) {
        this.support= support;
    }
    
    public EditorAnnotationsSupport getEditorAnnotationsSupport() {
        return support;
    }
}
