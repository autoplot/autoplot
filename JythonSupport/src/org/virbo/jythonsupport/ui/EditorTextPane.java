/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.jythonsupport.ui;

import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
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
import org.das2.util.LoggerManager;
import org.python.core.PyObject;
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

    public EditorTextPane() {

        Runnable run= new Runnable() {
            public void run() {

        final UndoManager undo = new UndoManager();

        getActionMap().put( "undo", new AbstractAction( undo.getUndoPresentationName() ) {
            public void actionPerformed( ActionEvent e ) {
                if ( undo.canUndo() ) undo.undo();
            }
        });

        getActionMap().put( "redo", new AbstractAction( undo.getRedoPresentationName() ) {
            public void actionPerformed( ActionEvent e ) {
               try {
                    if ( undo.canRedo() ) undo.redo();
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

        Preferences prefs= Preferences.userNodeForPackage( EditorTextPane.class );

        Toolkit tk= Toolkit.getDefaultToolkit();

        getInputMap().put( KeyStroke.getKeyStroke( KeyEvent.VK_Z,tk.getMenuShortcutKeyMask() ), "undo" );
        getInputMap().put( KeyStroke.getKeyStroke( KeyEvent.VK_Y,tk.getMenuShortcutKeyMask() ), "redo" );
        getInputMap().put( KeyStroke.getKeyStroke( KeyEvent.VK_EQUALS, tk.getMenuShortcutKeyMask() ), "biggerFont" );
        getInputMap().put( KeyStroke.getKeyStroke( KeyEvent.VK_MINUS, tk.getMenuShortcutKeyMask() ), "smallerFont" );
        
        doLayout(); // kludge for DefaultSyntaxKit
        DefaultSyntaxKit.initKit();

        JPopupMenu oldPopup= EditorTextPane.this.getComponentPopupMenu();
        EditorTextPane.this.setContentType("text/python");
        getDocument().addUndoableEditListener(undo);
        if ( oldPopup!=null ) EditorTextPane.this.setComponentPopupMenu(oldPopup);
        
        String sf= prefs.get( PROP_FONT, "monospaced" );
        setFont( Font.decode(sf) );
        
            }

        };
        SwingUtilities.invokeLater(run);

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

    void plot(String doThis) {
        if ( support.interp==null ) {
            JOptionPane.showMessageDialog(this,"Session is not running.  There must be an active debugger to plot variables.");
            return;
        }
        try {
            PyObject po= support.interp.eval(doThis);
            if ( po instanceof PyQDataSet ) {
                try {
                    PyQDataSet pds = (PyQDataSet) po;
                    File tmpDir= File.createTempFile( "autoplot", ".qds" ).getParentFile();
                    File tmpfile =  new File( tmpDir, "autoplot.qds" );
                    String cmd = "plot( 'file:" + tmpfile.toString() + "' );";
                    new org.virbo.qstream.SimpleStreamFormatter().format(pds.getQDataSet(), new FileOutputStream(tmpfile), true );
                    Socket s= new Socket("localhost",12345);
                    OutputStream out= s.getOutputStream();
                    out.write( ( cmd + "\n").getBytes() );
                    out.close();
                } catch (StreamException ex) {
                    logger.log(Level.SEVERE, null, ex);
                } catch (IOException ex) {
                    if ( ex instanceof ConnectException ) {
                        JOptionPane.showMessageDialog(this,"<html>Unable to connect to socket 12345.  Start a second Autoplot and enable the Server feature.</html>");
                        return;
                    }
                    logger.log(Level.SEVERE, null, ex);
                }

            } else {
                JOptionPane.showMessageDialog(this,"Selected item is not a dataset");
            }

        } catch ( Exception e  ) {
            JOptionPane.showMessageDialog(this,"Selected item caused exception: " + e.toString() );
        }
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
            String s= loadFileToString(f);
            Document d = this.getDocument();
            d.remove( 0, d.getLength() );
            d.insertString( 0, s, null );
            //setDirty(false);
        } catch (BadLocationException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
    }

}
