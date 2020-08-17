
package org.das2.jythoncompletion;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import org.autoplot.jythonsupport.JythonToJavaConverter;
import org.das2.jythoncompletion.ui.CompletionImpl;

/**
 * Offer a class to use and add import for it.
 * @author jbf
 */
public class ClassImportCompletionItem extends DefaultCompletionItem{
    
    String pkg;
    String name;
    
    public ClassImportCompletionItem( String text, int offset, String complete, String label, String link, int sortPriority, ImageIcon icon, String pkg, String name ) {
        super(text, offset, complete, label, link, sortPriority, icon);
        this.pkg= pkg;
        this.name= name;
    }
    
    @Override
    public void defaultAction(JTextComponent jTextComponent) {
        try {
            int pos= jTextComponent.getCaretPosition();
            Document d= jTextComponent.getDocument();
            int lineEnd= Utilities.getRowEnd( jTextComponent, pos );
            String restOfLine= d.getText(pos,lineEnd-pos);
            if ( !restOfLine.startsWith( complete.substring(offset) ) ) { // in case they triggered completion just for reference
                d.insertString( pos, complete.substring(offset), null );
            }
            JythonToJavaConverter.addImport( d, pkg, name );
            //jTextComponent.setCaretPosition(carot);
            CompletionImpl.get().hideCompletion(false);
        } catch (BadLocationException ex) {
            Logger.getLogger(ClassImportCompletionItem.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public boolean instantSubstitution(JTextComponent jTextComponent) {
        return false;
    }
    
    
}
