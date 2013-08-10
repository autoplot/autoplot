/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.das2.jythoncompletion;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.das2.jythoncompletion.support.CompletionItem;
import org.das2.jythoncompletion.support.CompletionTask;
import org.virbo.datasource.DataSetURI.CompletionResult;

/**
 * adapts CompletionResult to Netbeans model.
 * @author jbf
 */
public class DataSetUrlCompletionItem implements CompletionItem {

    private static final Logger logger= Logger.getLogger("jython.editor");

    CompletionResult rs;
    
    DataSetUrlCompletionItem( CompletionResult rs ) {
        this.rs= rs;
    }
    
    public CompletionTask createDocumentationTask() {
        return null;
       /* if ( rs.doc!=null ) return new DocCompletionTask() {
            public void query(CompletionResultSet resultSet) {
                resultSet.addItem( new DefaultDocumentationItem(rs.doc) );
            }
            public void refresh(CompletionResultSet resultSet) {
                throw new UnsupportedOperationException("Not supported yet.");
            }
            public void cancel() {
                throw new UnsupportedOperationException("Not supported yet.");
            }   
        };*/
    }

    public CompletionTask createToolTipTask() {
        return null;
    }

    public void defaultAction(JTextComponent jTextComponent) {
        try {
            logger.fine("defaultAction of DataSetUrlCompletionItem");
            int pos = jTextComponent.getCaretPosition();
            Document d = jTextComponent.getDocument();
	    if ( rs.completion.startsWith(rs.completable) ) {
                String txt= d.getText(pos,d.getLength()-pos);
                int ii= txt.indexOf("'");
                int jj= txt.indexOf("\n");
                if ( ii>-1 && ii<jj ) {
                    logger.fine("ii="+ii);
                    d.remove( pos, ii );
                }
		d.insertString(pos, rs.completion.substring(rs.completable.length()), null);
	    } else {
		throw new IllegalArgumentException("implementation problem, completion ("+rs.completion+") must start with completable ("+rs.completable+")");
	    }
        } catch (BadLocationException ex) {
            Logger.getLogger(DataSetUrlCompletionItem.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public CharSequence getInsertPrefix() {
        return rs.completion;
    }

    public int getPreferredWidth(Graphics graphics, Font font) {
        int n= rs.label.length();
        String label;
        if ( n>80 ) {
           label= rs.label.substring(n-80,n);
        } else {
            label= rs.label;
        }
        return graphics.getFontMetrics(font).stringWidth(label);
    }

    public int getSortPriority() {
        return 0;
    }

    public CharSequence getSortText() {
        return rs.completion;
    }

    public boolean instantSubstitution(JTextComponent jTextComponent) {
        defaultAction(jTextComponent);
        return true;
    }

    public void processKeyEvent(KeyEvent keyEvent) {
    }

    public void render(Graphics graphics, Font font, Color color, Color color0, int i, int i0, boolean b) {
        int n= rs.label.length();
        String label;
        if ( n>80 ) {
           label= rs.label.substring(n-80,n);
        } else {
            label= rs.label;
        }
        graphics.drawString( label, 0, graphics.getFontMetrics().getHeight() );
    }

    public String getLabel() {
        return rs.completion;
    }

}
