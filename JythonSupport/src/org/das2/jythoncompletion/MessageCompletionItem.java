/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.das2.jythoncompletion;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import javax.swing.text.JTextComponent;
import org.das2.jythoncompletion.support.CompletionItem;
import org.das2.jythoncompletion.support.CompletionResultSet;
import org.das2.jythoncompletion.support.CompletionTask;

/**
 *
 * @author jbf
 */
public class MessageCompletionItem implements CompletionItem {

    String message;
    String documentation;
    
    public MessageCompletionItem( String message ) {
        this( message, null );
    }

    public MessageCompletionItem( String message, String documentation ) {
        this.message= message;
        this.documentation= documentation;
    }

    public void defaultAction(JTextComponent component) {

    }

    public void processKeyEvent(KeyEvent evt) {

    }

    public int getPreferredWidth(Graphics g, Font defaultFont) {
        return g.getFontMetrics(defaultFont).stringWidth(message);
    }

    public void render(Graphics graphics, Font defaultFont, Color defaultColor, Color backgroundColor, int width, int height, boolean selected) {
        graphics.drawString( message, 0, graphics.getFontMetrics().getHeight() );
    }

    public CompletionTask createDocumentationTask() {
        if ( documentation==null ) {
            return null;
        } else {
            return new CompletionTask( ) {
                public void query(CompletionResultSet resultSet) {
                    resultSet.setDocumentation( new DefaultDocumentationItem(null,documentation) );
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

    public boolean instantSubstitution(JTextComponent component) {
        return false;
    }

    public int getSortPriority() {
        return 0;
    }

    public CharSequence getSortText() {
        return "a";
    }

    public CharSequence getInsertPrefix() {
        return "";
    }

}
