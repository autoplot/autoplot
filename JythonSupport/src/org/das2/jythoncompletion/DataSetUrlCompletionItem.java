
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
import org.autoplot.datasource.DataSetURI.CompletionResult;

/**
 * Completions for the URI when (for example) getDataSet('http:&lt;C&gt;') is entered.
 *
 * @author jbf
 */
public class DataSetUrlCompletionItem implements CompletionItem {

    private static final Logger logger = Logger.getLogger("jython.editor");
    CompletionResult rs;

    DataSetUrlCompletionItem(CompletionResult rs) {
        this.rs = rs;
    }

    @Override
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

    @Override
    public CompletionTask createToolTipTask() {
        return null;
    }

    @Override
    public void defaultAction(JTextComponent jTextComponent) {
        try {
            logger.fine("defaultAction of DataSetUrlCompletionItem");
            int pos = jTextComponent.getCaretPosition();
            Document d = jTextComponent.getDocument();
            if (rs.completion.startsWith(rs.completable)) {
                String txt = d.getText(pos, d.getLength() - pos);
                int ii = txt.indexOf("'");
                int jj = txt.indexOf("\n");
                if (ii > -1 && ii < jj) {
                    logger.log(Level.FINE, "ii={0}", ii);
                    d.remove(pos, ii);
                }
                d.insertString(pos, rs.completion.substring(rs.completable.length()), null);
            } else {
                throw new IllegalArgumentException("implementation problem, completion (" + rs.completion + ") must start with completable (" + rs.completable + ")");
            }
        } catch (BadLocationException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    @Override
    public CharSequence getInsertPrefix() {
        return rs.completion;
    }

    @Override
    public int getPreferredWidth(Graphics graphics, Font font) {
        int n = rs.label.length();
        String label;
        if (n > 80) {
            label = rs.label.substring(n - 80, n);
        } else {
            label = rs.label;
        }
        return graphics.getFontMetrics(font).stringWidth(label);
    }

    @Override
    public int getSortPriority() {
        return 0;
    }

    @Override
    public CharSequence getSortText() {
        return rs.completion;
    }

    @Override
    public boolean instantSubstitution(JTextComponent jTextComponent) {
        defaultAction(jTextComponent);
        return true;
    }

    @Override
    public void processKeyEvent(KeyEvent keyEvent) {
    }

    @Override
    public void render(Graphics graphics, Font font, Color color, Color color0, int i, int i0, boolean b) {
        int n = rs.label.length();
        String label;
        if (n > 80) {
            label = rs.label.substring(n - 80, n);
        } else {
            label = rs.label;
        }
        graphics.drawString(label, 0, graphics.getFontMetrics().getHeight());
    }

    public String getLabel() {
        return rs.completion;
    }

    @Override
    public String toString() {
        return "completion "+ rs.completion;
    }
    
    
}
