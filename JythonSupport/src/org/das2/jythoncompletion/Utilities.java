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

    /**
     * return the offset of the first character of the line.
     * See javax.swing.text.Utilities, it's the same thing.
     * @param editor
     * @param pos
     * @return
     */
    public static int getRowStart(JTextComponent editor, int pos) {
        Element root = editor.getDocument().getDefaultRootElement();
        int iele= root.getElementIndex(pos);
        return root.getElement(iele).getStartOffset();
    }

    /**
     * return the offset of the last character of the line.
     * @param editor
     * @param pos
     * @return
     */
    public static int getRowEnd(JTextComponent editor, int pos) {
        Element root = editor.getDocument().getDefaultRootElement();
        int iele= root.getElementIndex(pos);
        return root.getElement(iele).getEndOffset();
    }

    /**
     * return the line number that contains the offset.
     * @param a
     * @param offset
     * @return the line number, zero is the first line.
     * @throws javax.swing.text.BadLocationException
     */
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
