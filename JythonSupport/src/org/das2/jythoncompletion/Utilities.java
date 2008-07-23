/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.das2.jythoncompletion;

import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;

/**
 *
 * @author jbf
 */
public class Utilities {

    static int getRowStart(JTextComponent editor, int pos) {
        Element root = editor.getDocument().getDefaultRootElement();
        int iele= root.getElementIndex(pos);
        return root.getElement(iele).getStartOffset();
    }

    static int getRowEnd(JTextComponent editor, int pos) {
        Element root = editor.getDocument().getDefaultRootElement();
        int iele= root.getElementIndex(pos);
        return root.getElement(iele).getEndOffset();
    }

    public static int getLineNumberForOffset(JTextArea a, int offset) throws BadLocationException {
        int line = 0;
        while (line < a.getRows()) {
            if (a.getLineEndOffset(line) > offset) {
                break;
            }
            line++;
        }
        return line;
    }
    
    public static int getRowStart( JTextArea a, int offset) throws BadLocationException {
        int line = getLineNumberForOffset( a, offset );
        return a.getLineStartOffset(line);
    }

    public static int getRowEnd( JTextArea a, int offset) throws BadLocationException {
        int line = getLineNumberForOffset( a, offset );
        return a.getLineEndOffset(line);
    }
        
}
