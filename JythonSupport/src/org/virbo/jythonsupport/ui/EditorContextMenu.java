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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
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
import org.virbo.datasource.DataSourceUtil;

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

    /**
     * indent or dedent the code.  When amount is positive, we indent.  When it's negative, we remove so many spaces
     * from each line.
     * @param txt the block of text, starting but not including the newline, and ending just before a newline.
     * @param amount positive or negative number of spaces.
     * @return
     */
    private static String indent( String txt, int amount ) {
        try {
            StringBuilder result= new StringBuilder();
            BufferedReader r= new BufferedReader( new StringReader(txt) );
            String dedent= amount<0 ? "   ".substring(0,-1*amount) : "";
            String indent= amount>0 ? "   ".substring(0,   amount) : "";
            String line= r.readLine();
            while ( line!=null ) {
                if ( amount<0 && line.startsWith(dedent) ) line= line.substring(dedent.length());
                if ( amount>0 ) line= indent + line;
                result.append(line);
                line= r.readLine();
                if ( line!=null ) result.append("\n");
            }
            return result.toString();
        } catch ( IOException ex ) {
            throw new RuntimeException(ex);
        }
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
                        String name= DataSourceUtil.guessNameFor(surl);
                        insertCode( name + "= getDataSet('"+surl+"')\n");
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
                        insertCode( "p1= getParam( 'p1', 0.0, 'parameter p1 (default=0.0)' )\n");
                    } else {
                        insertCode( var + "= getParam( '"+var+"', 0.0, 'parameter "+var+" (default=0.0)' )\n" );
                    }
                }
            };
            item= new JMenuItem( a );
            item.setToolTipText("<html>get a parameter for the script, for example, from the URI or command line depending on context<br>The first argument is the parameter name,<br>second is the default value and type,<br>optional third is description</html>");
            insertCodeMenu.add( item );

            a= new AbstractAction("getParam() with enumeration") {
                public void actionPerformed(ActionEvent e) {
                    insertCode( "sc= getParam( 'sc', 'c1', 'the spacecraft name', ['c1','c2','c3','c4'] )\n");
                }
            };
            item= new JMenuItem( a );
            item.setToolTipText("<html>get a parameter for the script, constraining the list of values to an enumeration.</html>");
            insertCodeMenu.add( item );

            a= new AbstractAction("getParam() for timerange to support time series browse") {
                public void actionPerformed(ActionEvent e) {
                    insertCode( "tr= getParam( 'timerange', '2012-04-18', 'timerange to load' )\n");
                }
            };
            item= new JMenuItem( a );
            item.setToolTipText("<html>When getParam timerange is read, then the script will the time axis to be set to any time.</html>");
            insertCodeMenu.add( item );

            a= new AbstractAction("getParam() to get the resource URI") {
                public void actionPerformed(ActionEvent e) {
                    insertCode( "resourceURI= getParam( 'resourceURI', 'http://autoplot.org/data/rainfall_KIOW_20120522_0252.html', 'example file to load' )\n" );
                }
            };
            item= new JMenuItem( a );
            item.setToolTipText("<html>This special variable is the vap+jyds:<resourceURI>?script=<script>");
            insertCodeMenu.add( item );

            JMenu fragmentsMenu= new JMenu("Code Fragments");
            fragmentsMenu.add( createInsertMenuItem( "procedure", "def myproc(x,y):\n  z=x+y\n  return z\n" ) );

            fragmentsMenu.add( createInsertMenuItem( "if block", "x=0\nif (x<0):\n  print 'x<0'\nelif (x==0):\n  print 'x==0'\nelse:\n  print 'x>0'\n" ) );

            fragmentsMenu.add( createInsertMenuItem( "for loop with index", "a= sin( linspace(0,PI,100) )\nfor i in xrange(len(a)):\n  print i, a[i]\n" ) );

            fragmentsMenu.add( createInsertMenuItem( "for loop over dataset", "a= sin( linspace(0,PI,100) )\nfor i in a:\n  print i\n" ) );

            fragmentsMenu.add( createInsertMenuItem( "try-except", "try:\n  fil=downloadResourceAsTempFile(URL('http://autoplot.org/data/nofile.dat'),monitor)\nexcept java.io.IOException,ex:\n  print 'file not found'\n" ) );

            fragmentsMenu.add( createInsertMenuItem( "raise exception", "if ( ds.length()==0 ):\n  raise Exception('Dataset is empty')") );
            
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
            mi= new JMenuItem( new AbstractAction("indent block") {
                public void actionPerformed(ActionEvent e) {
                    int i= editor.getSelectionStart();  // note the netbeans source has all these operators, implemented correctly...
                    int j= editor.getSelectionEnd();
                    int limit= editor.getText().length();
                    try {
                        while ( i>=0 && !editor.getText(i,1).equals("\n") ) i--;
                        if ( i>=0 && editor.getText(i,1).equals("\n") ) i++;
                        while ( j<limit && !editor.getText(j,1).equals("\n" ) ) j++;
                        String txt= editor.getText( i, j-i );
                        txt= indent( txt, 2 );
                        editor.getDocument().remove( i, j-i );
                        editor.getDocument().insertString( i, txt, null );
                        editor.setSelectionStart(i);
                        editor.setSelectionEnd(i+txt.length());
                    } catch ( BadLocationException ex ) {

                    }
                }
            } );
            mi.setToolTipText("indent the selected block of lines");
            actionsMenu.add( mi );
            mi= new JMenuItem( new AbstractAction("dedent block") {
                public void actionPerformed(ActionEvent e) {
                    int i= editor.getSelectionStart();
                    int j= editor.getSelectionEnd();
                    int limit= editor.getText().length();
                    try {
                        while ( i>=0 && !editor.getText(i,1).equals("\n") ) i--;
                        if ( i>=0 && editor.getText(i,1).equals("\n") ) i++;
                        while ( j<limit && !editor.getText(j,1).equals("\n" ) ) j++;
                        String txt= editor.getText( i, j-i );
                        txt= indent( txt, -2 );
                        editor.getDocument().remove( i, j-i );
                        editor.getDocument().insertString( i, txt, null );
                        editor.setSelectionStart(i);
                        editor.setSelectionEnd(i+txt.length());
                    } catch ( BadLocationException ex ) {

                    }
                }
            } );
            mi.setToolTipText("indent the selected block of lines");
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
