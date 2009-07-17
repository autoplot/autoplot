/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.jythonsupport.ui;

import java.awt.Font;
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
               try {
                    undo.redo();
               } catch ( javax.swing.undo.CannotRedoException ex ) {
                   
               }
            }
        });

        getActionMap().put( "biggerFont", new AbstractAction( "Text Size Bigger" ) {
            public void actionPerformed( ActionEvent e ) {
               Font f= getFont();
               float size= f.getSize2D();
               float step= size < 14 ? 1 : 2;
               setFont( f.deriveFont( Math.min( 40, size + step ) ) );
            }
        } );

        getActionMap().put( "smallerFont", new AbstractAction( "Text Size Smaller" ) {
            public void actionPerformed( ActionEvent e ) {
               Font f= getFont();
               float size= f.getSize2D();
               float step= size < 14 ? 1 : 2;
               setFont( f.deriveFont( Math.max( 4, size - step ) ) );
            }
        } );

        getInputMap().put( KeyStroke.getKeyStroke( KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK ), "undo" );
        getInputMap().put( KeyStroke.getKeyStroke( KeyEvent.VK_Y, KeyEvent.CTRL_DOWN_MASK ), "redo" );
        getInputMap().put( KeyStroke.getKeyStroke( KeyEvent.VK_EQUALS, KeyEvent.CTRL_DOWN_MASK ), "biggerFont" );
        getInputMap().put( KeyStroke.getKeyStroke( KeyEvent.VK_MINUS, KeyEvent.CTRL_DOWN_MASK ), "smallerFont" );
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
