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
 * Utilities for the editor.
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
     * @see JTextArea#getLineOfOffset(int) 
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
    
    /**
     * return the line number that contains the offset.
     * @param comp
     * @param offset
     * @return the line number, zero is the first line.
     * @throws javax.swing.text.BadLocationException
     */
    public static int getLineNumberForOffset(JTextComponent comp, int offset) throws BadLocationException {
        Document doc = comp.getDocument();
        if (offset < 0) {
            throw new BadLocationException("Can't translate offset to line", -1);
        } else if (offset > doc.getLength()) {
            throw new BadLocationException("Can't translate offset to line", doc.getLength() + 1);
        } else {
            Element map = doc.getDefaultRootElement();
            return map.getElementIndex(offset);
        }
    }
    
    /**
     * given a line number, where zero is the first line, what is the offset?
     * @param text the text document
     * @param line, the line number, where zero is the first line
     * @return the character offset of the line.
     */
    public static int getOffsetForLineNumber( String text, int line ) {
        String[] ss= text.split("\n");
        if ( ss.length==1 ) {
            return 0;
        } else {
            int firstNewLine= ss[0].length();
            int newlineLength=1;
            if ( text.length()>firstNewLine+2 ) {
                if ( (int)text.charAt(firstNewLine)==13 && (int)text.charAt(firstNewLine+1)==10 ) {
                    newlineLength=2;
                }
            }
            int index= 0;
            for ( int i=0; i<line; i++ ) {
                index+= ss[i].length() + newlineLength; 
            }
            return index;
        }
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
