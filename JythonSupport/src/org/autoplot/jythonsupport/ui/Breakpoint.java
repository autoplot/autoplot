
package org.autoplot.jythonsupport.ui;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.Position;

/**
 *
 * @author jbf
 */
public class Breakpoint implements Comparable {
    
    Document document;
    Position position;
    
    String filename = null; // This will need more work, for now one file is assumed.
    boolean enabled = true;
    String printExpr = "";
    String ifExpr= ""; 

    public Breakpoint(Document document, int line) throws BadLocationException {
        this("",document,line);
    }

    public Breakpoint(String filename, Document document, int line) throws BadLocationException {
        this.filename = filename;
        this.document = document;

        
        Element root = document.getDefaultRootElement();

        if (line < 1 || line > root.getElementCount()) {
            throw new IllegalArgumentException(
                    "Invalid line number: " + line);
        }

        int offset = root.getElement(line - 1).getStartOffset();
        this.position = document.createPosition(offset);
    }
    
    public static Position getPositionForLine( Document document, int line ) throws BadLocationException {
        Element root = document.getDefaultRootElement();

        if (line < 1 || line > root.getElementCount()) {
            throw new IllegalArgumentException(
                    "Invalid line number: " + line);
        }

        int offset = root.getElement(line - 1).getStartOffset();
        return document.createPosition(offset);
    }
    
    /**
     * get the line within the document
     * @return 
     */
    public int getLine() {
        Element root = document.getDefaultRootElement();
        return root.getElementIndex(position.getOffset()) + 1;
    }
    
    /**
     * get the Position within the document
     * @return 
     */
    public Position getPosition() {
        return position;
    }    
    
    @Override
    public int compareTo(Object o) {
        Breakpoint obp = (Breakpoint) o;
        return obp.getLine() - this.getLine();
    }
    
}
