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
 * Completion that just shows a message.
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
        if( documentation!=null && documentation.startsWith("inline:") ) {
            documentation= documentation.substring(7);
        }
        this.documentation= documentation;
    }

    @Override
    public void defaultAction(JTextComponent component) {

    }

    @Override
    public void processKeyEvent(KeyEvent evt) {

    }

    @Override
    public int getPreferredWidth(Graphics g, Font defaultFont) {
        return g.getFontMetrics(defaultFont).stringWidth(message);
    }

    @Override
    public void render(Graphics graphics, Font defaultFont, Color defaultColor, Color backgroundColor, int width, int height, boolean selected) {
        graphics.drawString( message, 0, graphics.getFontMetrics().getHeight() );
    }

    @Override
    public CompletionTask createDocumentationTask() {
        if ( documentation==null ) {
            return null;
        } else {
            return new CompletionTask( ) {
                @Override
                public void query(CompletionResultSet resultSet) {
                    resultSet.setDocumentation( new DefaultDocumentationItem(null,documentation) );
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
    public boolean instantSubstitution(JTextComponent component) {
        return false;
    }

    @Override
    public int getSortPriority() {
        return -100;
    }

    @Override
    public CharSequence getSortText() {
        return "a";
    }

    @Override
    public CharSequence getInsertPrefix() {
        return "";
    }

}
