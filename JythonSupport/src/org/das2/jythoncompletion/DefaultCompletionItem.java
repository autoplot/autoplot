/*
 * UserCompletionItem.java
 *
 * Created on August 2, 2006, 3:34 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.das2.jythoncompletion;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import org.das2.jythoncompletion.support.CompletionItem;
import org.das2.jythoncompletion.support.CompletionResultSet;
import org.das2.jythoncompletion.support.CompletionTask;
import org.das2.jythoncompletion.support.CompletionUtilities;
import org.das2.jythoncompletion.ui.CompletionImpl;

/**
 *
 * @author jbf
 */
public class DefaultCompletionItem implements CompletionItem  {
    
    String text;
    int offset;
    String complete;
    String label;
    String link;
    int sortPriority;
    
    final static Logger logger= Logger.getLogger( "pvwave" );
    
    /**
     * @param text  used for sort and insert prefix.  Typically same as complete.
     * @param offset  number of chars already typed.
     * @param complete  complete.substring(offset) is inserted.
     * @param label  the human readable presentation of this, maybe with html.
     * @param link  handed over to DefaultDocumentationItem, if non null. 
     */
    public DefaultCompletionItem( String text, int offset, String complete, String label, String link, int sortPriority ) {
        this.text= text;
        this.offset= offset;
        this.complete= complete;
        if ( label==null ) label= complete;
        this.label= label;
        this.link= link;
        this.sortPriority= sortPriority;
    }

    public DefaultCompletionItem( String text, int offset, String complete, String label, String link ) {
        this(text, offset, complete, label, link, 1);
    }
    
    public static DefaultCompletionItem error( String message ) {
        return new DefaultCompletionItem( message, 0, "", message, null );
    }
    
    public void defaultAction( JTextComponent jTextComponent ) {
        try {
            int pos= jTextComponent.getCaretPosition();
            Document d= jTextComponent.getDocument();
            d.insertString( pos, complete.substring(offset), null );
        } catch ( BadLocationException ex ) {
            throw new RuntimeException(ex);
        }
        CompletionImpl.get().hideCompletion();
    }
    
    public String getComplete() {
        return complete;
    }
    
    protected void substituteText(JTextComponent c, int offset, int len, String toAdd) {
        Document doc = c.getDocument();
        String textl = getInsertPrefix().toString().substring(offset);
        if (textl != null) {

            try {
                String textToReplace = doc.getText(offset, len);
                if (textl.equals(textToReplace)) {
                    return;
                }                
                Position position = doc.createPosition(offset);
                
                doc.remove(offset, len);
                doc.insertString(position.getOffset(), textl, null);
                
            } catch (BadLocationException e) {
                // Can't update
            } finally {
                
            }
        }
    }

    
    public void processKeyEvent(KeyEvent keyEvent) {
            //JTextComponent component = (JTextComponent) keyEvent.getSource();
            //int caretOffset = component.getSelectionEnd();
            //substituteText(component, offset, caretOffset - offset, Character.toString(keyEvent.getKeyChar()));
            //CompletionImpl.get().showCompletion();
            //keyEvent.consume();
            //System.err.println("here");

    }
    
    public int getPreferredWidth(Graphics graphics, Font font) {
        String left= label;
        String right= null;
        int i2= label.indexOf("->");
        if ( i2>-1 ) {
            right= label.substring(i2+2);
            left= label.substring(0,i2);
        }
        return CompletionUtilities.getPreferredWidth( left, right, graphics, font );
    }
    
    public void render(Graphics graphics, Font font, Color color, Color color0, int i, int i0, boolean b) {
        String left= label;
        String right= null;
        int i2= label.indexOf("->");
        if ( i2>-1 ) {
            right= label.substring(i2+2);
            left= label.substring(0,i2);
        }
        CompletionUtilities.renderHtml(null,left,right,graphics,font, color,i,i0,b);
    }
    
    public CompletionTask createDocumentationTask() {
        if ( link==null ) {
            return null;
        } else {
            return new CompletionTask( ) {
                public void query(CompletionResultSet resultSet) {
                    resultSet.setDocumentation( new DefaultDocumentationItem(link) );
                    resultSet.finish();
                }
                public void refresh(CompletionResultSet resultSet) {
                    query(resultSet);
                }
                public void cancel() {
                }  
            };
        }
    }
    
    public CompletionTask createToolTipTask() {
        return null;
    }
    
    public boolean instantSubstitution(JTextComponent jTextComponent) {
        defaultAction(jTextComponent);
        return true;
    }
    
    public int getSortPriority() {
        return sortPriority;
    }
    
    public CharSequence getSortText() {
        return text;
    }
    
    public CharSequence getInsertPrefix() {
        return text.substring(0,offset);
    }

    
}
