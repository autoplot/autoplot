/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.jythonsupport.ui;

import java.awt.Event;
import org.das2.components.propertyeditor.PropertyEditor;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import org.das2.jythoncompletion.CompletionSettings;
import org.das2.jythoncompletion.JythonCompletionProvider;
import org.virbo.datasource.DataSetSelector;

/**
 *
 * @author jbf
 */
public class EditorContextMenu {

    private EditorTextPane editor;
    private JPopupMenu menu;
    private DataSetSelector dataSetSelector;
    private JMenu examplesMenu;

    public EditorContextMenu( EditorTextPane edit  ) {
        this.editor = edit;
        maybeCreateMenu();
        editor.addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    if (menu != null) {
                        menu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    if (menu != null) {
                        menu.show(e.getComponent(), e.getX(), e.getY());
                    }
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
            JMenu submenu= new JMenu("Example Scripts");
            examplesMenu= submenu;
            menu.add( submenu );
            JMenu actionsMenu= new JMenu("Actions");
            actionsMenu.add( new AbstractAction("plot") {
                public void actionPerformed(ActionEvent e) {
                    String doThis= editor.getSelectedText();
                    if ( doThis==null ) return;
                    editor.plot(doThis);
                }
            } );
            menu.add( actionsMenu );
            JMenu settingsMenu= new JMenu("Settings");
            settingsMenu.add( new AbstractAction("Edit Settings") {
                public void actionPerformed(ActionEvent e) {
                    CompletionSettings settings= JythonCompletionProvider.getInstance().settings();
                    PropertyEditor p= new PropertyEditor(settings);
                    p.showModalDialog(editor);
                }
            });
            menu.add( settingsMenu );

			menu.addSeparator();
			JMenuItem cutItem = menu.add(new DefaultEditorKit.CutAction());
			cutItem.setText("Cut");
			JMenuItem copyItem = menu.add(new DefaultEditorKit.CopyAction());
			copyItem.setText("Copy");
			JMenuItem pasteItem = menu.add(new DefaultEditorKit.PasteAction());
			pasteItem.setText("Paste");

        }
    }

    private void insertCode( String code ) {
        try {
            editor.getDocument().insertString(editor.getCaretPosition(), code, null);
        } catch (BadLocationException ex) {
            Logger.getLogger(EditorContextMenu.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * adds an action to examples submenu.
     * @param a
     */
    public void addExampleAction( Action a ) {
        this.examplesMenu.add(a);
    }

}
