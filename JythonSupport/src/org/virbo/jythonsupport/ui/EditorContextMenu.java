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

    /**
     * create a menu item with a tool tip preview.
     * @param text
     * @return
     */
    private JMenuItem createInsertMenuItem( String label, final String text ) {
        JMenuItem result= new JMenuItem( new AbstractAction( label ) {
            public void actionPerformed(ActionEvent e) {
                insertCode( text );
            }
        } );
        String htmlText= text;
        htmlText= htmlText.replaceAll("<", "&lt;") ;
        htmlText= htmlText.replaceAll(">", "&gt;") ;
        htmlText= htmlText.replaceAll("\n", "<br>");
        htmlText= htmlText.replaceAll(" ", "&nbsp;") ;
        result.setToolTipText( "<html><tt>" + htmlText + "</tt></html>" );
        return result;

    }

    private synchronized void maybeCreateMenu() {
        if ( menu==null ) {
            menu= new JPopupMenu();
            Action a;
            JMenuItem item;
            JMenu insertCodeMenu= new JMenu("Insert Code");
            a= new AbstractAction("getDataSet()") {
                public void actionPerformed(ActionEvent e) {
                    String var= editor.getSelectedText();
                    String surl= dataSetSelector.getValue();
                    if ( var==null || var.length()==0 ) {
                        insertCode( "ds= getDataSet('"+surl+"')\n");
                    } else {
                        insertCode( var + "= getDataSet('"+surl+"')\n");
                    }
                }
            };
            item= new JMenuItem( a );
            item.setToolTipText("<html>load the dataset from the specified URL into a variable.</html>");
            insertCodeMenu.add( item );

            a= new AbstractAction("getParam()") {
                public void actionPerformed(ActionEvent e) {
                    String var= editor.getSelectedText();
                    if ( var==null || var.length()==0 ) {
                        insertCode( "p1= getParam( 'p1', 0.0 , 'parameter p1 (default=0.0)' )\n");
                    } else {
                        insertCode( var + "= getParam( '"+var+"', 0.0, 'parameter "+var+" (default=0.0)' )\n" );
                    }
                }
            };
            item= new JMenuItem( a );
            item.setToolTipText("<html>get a parameter for the script, for example, from the URI or command line depending on context<br>The first argument is the parameter name,<br>second is the default value,<br>optional third is description</html>");
            insertCodeMenu.add( item );

            JMenu fragmentsMenu= new JMenu("Code Fragments");
            fragmentsMenu.add( createInsertMenuItem( "procedure", "def myproc(x,y):\n  z=x+y\n  return z\n" ) );

            fragmentsMenu.add( createInsertMenuItem( "if block", "x=0\nif (x<0):\n  print 'x<0'\nelif (x==0):\n  print 'x==0'\nelse:\n  print 'x>0'\n" ) );

            fragmentsMenu.add( createInsertMenuItem( "for loop with index", "a= sin( linspace(0,PI,100) )\nfor i in xrange(len(a)):\n  print i, a[i]\n" ) );

            fragmentsMenu.add( createInsertMenuItem( "for loop over dataset", "a= sin( linspace(0,PI,100) )\nfor i in a:\n  print i\n" ) );
            
            insertCodeMenu.add(fragmentsMenu);

            menu.add( insertCodeMenu );
            JMenu submenu= new JMenu("Example Scripts");
            examplesMenu= submenu;
            menu.add( submenu );
            JMenu actionsMenu= new JMenu("Actions");
            JMenuItem mi= new JMenuItem( new AbstractAction("plot") {
                public void actionPerformed(ActionEvent e) {
                    String doThis= editor.getSelectedText();
                    if ( doThis==null ) return;
                    editor.plot(doThis);
                }
            } );
            mi.setToolTipText("Plot dataset reference in a second Autoplot with its server port open");
            actionsMenu.add( mi );
            menu.add( actionsMenu );
            JMenu settingsMenu= new JMenu("Settings");
            mi= new JMenuItem( new AbstractAction("Edit Settings") {
                public void actionPerformed(ActionEvent e) {
                    CompletionSettings settings= JythonCompletionProvider.getInstance().settings();
                    PropertyEditor p= new PropertyEditor(settings);
                    p.showModalDialog(editor);
                }
            } );
            mi.setToolTipText( "Settings for the editor" );
            settingsMenu.add( mi );
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
