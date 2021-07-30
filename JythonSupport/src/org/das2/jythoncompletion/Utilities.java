/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.das2.jythoncompletion;

import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;

/**
 * Utilties for the editor.
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

    /**
     * look for adjacent whitespace to identify the word at the location.
     * @param logTextArea
     * @param caret
     * @return
     * @throws BadLocationException 
     */
    public static String getWordAt(JTextPane logTextArea, int caret) throws BadLocationException {
        Document d= logTextArea.getDocument();
        while ( caret>0 && !Character.isWhitespace( d.getText(caret,1).charAt(0) ) ) caret--;
        caret++;
        int caretEnd= caret;
        while ( caretEnd<d.getLength() && !Character.isWhitespace( d.getText(caretEnd,1).charAt(0) ) ) caretEnd++;
        return d.getText( caret, caretEnd-caret );
    }
    
    /**
     * return the index of the start and end of the selection, rounded out
     * to complete lines.
     * @param editor the editor
     * @param carotPos the carot position within the editor.
     * @return [start,len] where start is the index of the first character and len is the number of characters.
     */
    public static int[] getLinePosition( JTextPane editor, int carotPos ) {
        int i= carotPos;
        int j= carotPos;
        try {
            int limit= editor.getText().length();
            
            while ( i>0 && !editor.getText(i,1).equals("\n") ) i--;
            if ( i>=0 && i<limit-1 && editor.getText(i,1).equals("\n") && !editor.getText(i+1,1).equals("\n") ) i++;
            while ( j<limit && !editor.getText(j,1).equals("\n" ) ) j++;
                        
            return new int[] { i, j-i };
            
        } catch (BadLocationException ex) {
            throw new RuntimeException(ex);
        }
        
    }
        
        
}
