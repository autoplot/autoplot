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
import javax.swing.ImageIcon;
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
 * Completion item which will insert text when accepted.
 * @author jbf
 */
public class DefaultCompletionItem implements CompletionItem  {
    
    String text;
    int offset;
    String complete;
    String label;
    String link;
    int sortPriority;
    boolean referenceOnly= false;
    ImageIcon icon= null;
    
    final static Logger logger= Logger.getLogger( "jython.editor" );
    
    /**
     * 
     * @param text used for sort and insert prefix.  Typically same as complete.
     * @param offset number of chars already typed.
     * @param complete complete.substring(offset) is inserted.
     * @param label the human readable presentation of this, maybe with html.
     * @param link handed over to DefaultDocumentationItem, if non null.  May be "inline:&lt;html&gt;..."
     * @param sortPriority 1 is default.
     * @param icon the icon to show next to this completion.
     */
    public DefaultCompletionItem( String text, int offset, String complete, String label, String link, int sortPriority, ImageIcon icon) {
        if ( complete.length()<offset ) {
            throw new IllegalArgumentException("completion offset is less than length");
        }
        this.text= text;
        this.offset= offset;
        this.complete= complete;
        if ( label==null ) label= complete;
        this.label= label;
        this.link= link;
        this.sortPriority= sortPriority;
        this.icon= icon;
    }

    public void setReferenceOnly( boolean ref ) {
        this.referenceOnly= ref;
    }
    
    /**
     * 
     * @param text  used for sort and insert prefix.  Typically same as complete.
     * @param offset  number of chars already typed.
     * @param complete  complete.substring(offset) is inserted.
     * @param label  the human readable presentation of this, maybe with html.
     * @param link  handed over to DefaultDocumentationItem, if non null.  May be "inline:&lt;html&gt;..."
     */
    public DefaultCompletionItem( String text, int offset, String complete, String label, String link ) {
        //http://apps-pw.physics.uiowa.edu/hudson/job/autoplot-javadoc2018/ws/doc/org/autoplot/jythonsupport/Util.html#getCompletions(java.lang.String)
        this(text, offset, complete, label, link, 1, null);
    }
    
    public static DefaultCompletionItem error( String message ) {
        return new DefaultCompletionItem( message, 0, "", message, null );
    }
    
    @Override
    public void defaultAction( JTextComponent jTextComponent ) {
        if ( referenceOnly ) return;
        try {
            int pos= jTextComponent.getCaretPosition();
            Document d= jTextComponent.getDocument();
            int lineEnd= Utilities.getRowEnd( jTextComponent, pos );
            String restOfLine= d.getText(pos,lineEnd-pos);
            if ( !restOfLine.startsWith( complete.substring(offset) ) ) { // in case they triggered completion just for reference
                d.insertString( pos, complete.substring(offset), null );
            }
        } catch ( BadLocationException ex ) {
            throw new RuntimeException(ex);
        }
        CompletionImpl.get().hideCompletion(false);
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

    
    @Override
    public void processKeyEvent(KeyEvent keyEvent) {
            //JTextComponent component = (JTextComponent) keyEvent.getSource();
            //int caretOffset = component.getSelectionEnd();
            //substituteText(component, offset, caretOffset - offset, Character.toString(keyEvent.getKeyChar()));
            //CompletionImpl.get().showCompletion();
            //keyEvent.consume();
            //System.err.println("here");

    }
    
    @Override
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
    
    @Override
    public void render(Graphics graphics, Font font, Color color, Color color0, int i, int i0, boolean b) {
        String left= label;
        String right= null;
        int i2= label.indexOf("->");
        if ( i2>-1 ) {
            right= label.substring(i2+2);
            left= label.substring(0,i2);
        }
        CompletionUtilities.renderHtml(icon,left,right,graphics,font, color,i,i0,b);
    }
    
    /**
     * return the first item which looks like a link from the documentation string.
     * @param s the inline documentation string
     * @return the link or null.
     */
    private String findLink( String s ) {
        String find= "http://";
        String[] ss= s.split(find,2);
        if ( ss.length==1 ) {
            find= "https://";
            ss= s.split(find,2);
        }
        if ( ss.length==1 ) return null;
        String[] ss2= ss[1].split("\\s",2);
        //remove BR
        ss2= ss2[0].split("<br>",2);
        ss2= ss2[0].split("'",2);
        ss2= ss2[0].split("\"",2);
        return find + ss2[0];
    }
    
    @Override
    public CompletionTask createDocumentationTask() {
        if ( link==null ) {
            return null;
        } else if ( link.startsWith("inline:") ) {
            final String fdoc= link.substring(7);
            return new CompletionTask() {
                @Override
                public void query(CompletionResultSet resultSet) {
                    String link= findLink(fdoc);
                    resultSet.setDocumentation( new DefaultDocumentationItem(link,fdoc) );
                    resultSet.finish();
                }
                @Override
                public void refresh(CompletionResultSet resultSet) {
                    query(resultSet);
                }
                @Override
                public void cancel() {
                }  
            };
        } else {
            return new CompletionTask( ) {
                @Override
                public void query(CompletionResultSet resultSet) {
                    resultSet.setDocumentation( new DefaultDocumentationItem(link) );
                    resultSet.finish();
                }
                @Override
                public void refresh(CompletionResultSet resultSet) {
                    query(resultSet);
                }
                @Override
                public void cancel() {
                }  
            };
        }
    }
    
    @Override
    public CompletionTask createToolTipTask() {
        return null;
    }
    
    @Override
    public boolean instantSubstitution(JTextComponent jTextComponent) {
        if ( referenceOnly ) return false;
        defaultAction(jTextComponent);
        return true;
    }
    
    @Override
    public int getSortPriority() {
        return sortPriority;
    }
    
    @Override
    public CharSequence getSortText() {
        return text;
    }
    
    @Override
    public CharSequence getInsertPrefix() {
        return text.substring(0,offset);
    }
    
}
