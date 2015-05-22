/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.jythonsupport.ui;

import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.ConnectException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.undo.UndoManager;
import jsyntaxpane.DefaultSyntaxKit;
import org.das2.DasApplication;
import org.das2.components.propertyeditor.PropertyEditor;
import org.das2.jythoncompletion.CompletionSettings;
import org.das2.jythoncompletion.JythonCompletionProvider;
import org.das2.util.LoggerManager;
import org.python.core.PyObject;
import org.python.parser.SimpleNode;
import org.virbo.dataset.ArrayDataSet;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.jythonsupport.JythonUtil;
import org.virbo.jythonsupport.PyQDataSet;
import org.virbo.qstream.StreamException;

/**
 * Special editor for Jython scripts, adding undo and redo actions, bigger/smaller
 * keystrokes and the action "plot."  A property "font" is managed as well, which
 * was introduced when the jython mode in the jsyntaxpane editor was using a poor choice.
 * @author jbf
 */
public class EditorTextPane extends JEditorPane {

    private static final Logger logger= LoggerManager.getLogger("jython.editor");

    protected static final String PROP_FONT= "font";

    private EditorAnnotationsSupport support= new EditorAnnotationsSupport( this );

    private boolean initialized= false;
    
    public EditorTextPane() {

        Runnable run= new Runnable() {
            @Override
            public void run() {

                final UndoManager undo = new UndoManager();

                getActionMap().put( "undo", new AbstractAction( undo.getUndoPresentationName() ) {
                    @Override
                    public void actionPerformed( ActionEvent e ) {
                        if ( undo.canUndo() ) undo.undo();
                    }
                });

                getActionMap().put( "redo", new AbstractAction( undo.getRedoPresentationName() ) {
                    @Override
                    public void actionPerformed( ActionEvent e ) {
                       try {
                            if ( undo.canRedo() ) undo.redo();
                       } catch ( javax.swing.undo.CannotRedoException ex ) {

                       }
                    }
                });

                getActionMap().put( "biggerFont", new AbstractAction( "Text Size Bigger" ) {
                    @Override
                    public void actionPerformed( ActionEvent e ) {
                       Font f= getFont();
                       float size= f.getSize2D();
                       float step= size < 14 ? 1 : 2;
                       setFont( f.deriveFont( Math.min( 40, size + step ) ) );
                    }
                } );

                getActionMap().put( "smallerFont", new AbstractAction( "Text Size Smaller" ) {
                    @Override
                    public void actionPerformed( ActionEvent e ) {
                       Font f= getFont();
                       float size= f.getSize2D();
                       float step= size < 14 ? 1 : 2;
                       setFont( f.deriveFont( Math.max( 4, size - step ) ) );
                    }
                } );

                getActionMap().put( "settings", new AbstractAction( "settings" ) {
                    @Override
                    public void actionPerformed( ActionEvent e ) {
                        CompletionSettings settings= JythonCompletionProvider.getInstance().settings();
                        PropertyEditor p= new PropertyEditor(settings);
                        p.showModalDialog(EditorTextPane.this);
                    }
                } );
                
                getActionMap().put( "inspect", new AbstractAction( "inspect" ) {
                    @Override
                    public void actionPerformed( ActionEvent e ) {
                        LoggerManager.logGuiEvent(e);                
                        String doThis= getSelectedText();
                        if ( doThis==null ) return;
                        plotSoon(doThis);
                    }
                } );                

                getActionMap().put( "usages", new AbstractAction( "usages" ) {
                    @Override
                    public void actionPerformed( ActionEvent e ) {
                        LoggerManager.logGuiEvent(e);  
                        showUsages();
                    }
                } );                

                Toolkit tk= Toolkit.getDefaultToolkit();

                getInputMap().put( KeyStroke.getKeyStroke( KeyEvent.VK_Z,tk.getMenuShortcutKeyMask() ), "undo" );
                getInputMap().put( KeyStroke.getKeyStroke( KeyEvent.VK_Y,tk.getMenuShortcutKeyMask() ), "redo" );
                getInputMap().put( KeyStroke.getKeyStroke( KeyEvent.VK_EQUALS, tk.getMenuShortcutKeyMask() ), "biggerFont" );
                getInputMap().put( KeyStroke.getKeyStroke( KeyEvent.VK_MINUS, tk.getMenuShortcutKeyMask() ), "smallerFont" );
                getInputMap().put( KeyStroke.getKeyStroke( KeyEvent.VK_F5, InputEvent.SHIFT_DOWN_MASK ), "settings" );
                getInputMap().put( KeyStroke.getKeyStroke( KeyEvent.VK_C, InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK ), "inspect" );
                getInputMap().put( KeyStroke.getKeyStroke( KeyEvent.VK_U, InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK ), "usages" );
                
                doLayout(); // kludge for DefaultSyntaxKit
                DefaultSyntaxKit.initKit();

                JPopupMenu oldPopup= EditorTextPane.this.getComponentPopupMenu();
                EditorTextPane.this.setContentType("text/python");
                getDocument().addUndoableEditListener(undo);
                if ( oldPopup!=null ) EditorTextPane.this.setComponentPopupMenu(oldPopup);

                String sf= JythonCompletionProvider.getInstance().settings().getEditorFont();
                setFont( Font.decode(sf) );

                initialized= true;
            }

        };
        
        if ( !DasApplication.getDefaultApplication().isHeadless() ) {
            SwingUtilities.invokeLater(run);
        }

    }

    /**
     * Ed and I verified that this is being set off of the event thread.
     * @param doc 
     */
    @Override
    public void setDocument(Document doc) {
        if ( !SwingUtilities.isEventDispatchThread() ) {
            logger.fine("called from off the event queue.");
        }
        super.setDocument(doc); //To change body of generated methods, choose Tools | Templates.
    }
    
    

    private void showUsages() {
        String script= getText();
        String var= getSelectedText();
        if ( var==null || var.length()==0 ) {
            var= EditorAnnotationsSupport.getSymbolAt( this );
        }
        support.clearAnnotations();
        List<SimpleNode> usages= JythonUtil.showUsage( script,var );
        for ( SimpleNode n: usages ) {
            support.annotateChars( n.beginLine, n.beginColumn, n.beginColumn+var.length(), EditorAnnotationsSupport.ANNO_USAGE, var, null );
        }
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

    /**
     * this makes the connection to another Autoplot.  This should not be called off the event thread.
     * @param doThis expression to evaluate
     */
    private void plot( String doThis ) {
        EditorAnnotationsSupport.ExpressionLookup l= EditorAnnotationsSupport.getExpressionLookup();
        if ( l==null ) {
            throw new IllegalArgumentException("No interpreter available to evaluate expression");
        }
        try {
            PyObject po= l.lookup(doThis);
            if ( po instanceof PyQDataSet ) {
                try {
                    PyQDataSet pds = (PyQDataSet) po;
                    File tmpDir= File.createTempFile( "autoplot", ".qds" ).getParentFile();
                    File tmpfile =  new File( tmpDir, "autoplot.qds" );
                    String cmd = "plot( 'file:" + tmpfile.toString() + "' );";
                    MutablePropertyDataSet mpds= ArrayDataSet.copy(pds.getQDataSet());
                    String oldTitle= (String) mpds.property(QDataSet.TITLE);
                    mpds.putProperty(QDataSet.TITLE, oldTitle==null ? doThis : ( doThis+": "+oldTitle ) );
                    new org.virbo.qstream.SimpleStreamFormatter().format(mpds, new FileOutputStream(tmpfile), true );
                    Socket s= new Socket("localhost",12345);
                    OutputStream out= s.getOutputStream();
                    try {
                        out.write( ( cmd + "\n").getBytes() );
                    } finally {
                        out.close();
                    }
                } catch (StreamException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                } catch (IOException ex) {
                    if ( ex instanceof ConnectException ) {
                        JOptionPane.showMessageDialog(this,"<html>Unable to connect to socket 12345.  Start a second Autoplot and enable the Server feature.</html>");
                        return;
                    }
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }

            } else {
                JOptionPane.showMessageDialog(this,"Selected item is not a dataset");
            }

        } catch ( Exception e  ) {
            JOptionPane.showMessageDialog(this,"Selected item caused exception: " + e.toString() );
        }        
    }
    
    /**
     * send the QDataSet resolved from the String doThis to the Autoplot server on port 12345.
     * @param doThis an expression evaluated by the current interpreter.
     */
    void plotSoon( final String doThis ) {
        EditorAnnotationsSupport.ExpressionLookup l= EditorAnnotationsSupport.getExpressionLookup();
        if ( l==null ) {
            throw new IllegalArgumentException("Session is not running.  There must be an active debugger to plot variables.");
        }
        Runnable run= new Runnable() {
            @Override
            public void run() {
                plot(doThis);
            }
        };
        new Thread(run,"plotExpression").start();
    }

    public static String loadFileToString( File f ) throws FileNotFoundException, IOException {
        BufferedReader r = null;
        StringBuilder buf = new StringBuilder();
        try {
            r = new BufferedReader( new InputStreamReader( new FileInputStream(f) ));
            String s = r.readLine();
            while (s != null) {
                buf.append(s).append("\n");
                s = r.readLine();
            }
        } finally {
            if ( r!=null ) r.close();
        }
        return buf.toString();
    }

    public void loadFile( File f ) throws FileNotFoundException, IOException {
        try {
            final String s= loadFileToString(f);
            //setDirty(false);
            if ( initialized ) {
                Document d = this.getDocument();
                d.remove( 0, d.getLength() );
                d.insertString( 0, s, null );
            } else {
                SwingUtilities.invokeLater( new Runnable() { 
                    @Override
                    public void run() {
                        try {
                            Document d = EditorTextPane.this.getDocument();
                            d.remove( 0, d.getLength() );
                            d.insertString( 0, s, null );
                        } catch (BadLocationException ex) {
                            logger.log(Level.SEVERE, ex.getMessage(), ex);
                        }
                    }
                });
            }
        } catch (BadLocationException ex) {
            throw new RuntimeException(ex);
        }
    }

    String[] jumpToList( ) {
        LineNumberReader reader;
        List<String> jumpToList= new ArrayList();
        jumpToList.add("1:top");
        try {
            Document d = EditorTextPane.this.getDocument();
            String s= d.getText( 0, d.getLength() );
            reader= new LineNumberReader( new StringReader(s) );
            String line="";
            
            int length= -1;
            try {
                while ( line!=null ) {
                    if ( line.startsWith("def ") && line.contains("(") ) {
                        int i= line.indexOf("(");
                        jumpToList.add( reader.getLineNumber() + ":" + line.substring(0,i) );
                    }
                    length= reader.getLineNumber()+1;
                    line= reader.readLine();
                }
            } finally {
                reader.close();
            }
            jumpToList.add( String.format( "%d:bottom",(length) ) );
            return jumpToList.toArray( new String[jumpToList.size()] );
        } catch ( IOException ex ) {
            return jumpToList.toArray( new String[jumpToList.size()] );
        } catch (BadLocationException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            return jumpToList.toArray( new String[jumpToList.size()] );
        }
        
    }
    
    @Override
    public void setFont(Font font) {
        super.setFont(font);
    }

}
