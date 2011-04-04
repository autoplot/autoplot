/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.jythonsupport.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import org.python.util.PythonInterpreter;

/**
 *
 * @author jbf
 */
public class EditorAnnotationsSupport {

    private JTextPane editorPanel;
    PythonInterpreter interp;

    EditorAnnotationsSupport(JTextPane editorPanel) {
        this.editorPanel = editorPanel;
        editorPanel.getDocument().addDocumentListener(new DocumentListener() {

            public void insertUpdate(DocumentEvent e) {
                clearAnnotations(e.getOffset());
            }

            public void removeUpdate(DocumentEvent e) {
                clearAnnotations(e.getOffset());
            }

            public void changedUpdate(DocumentEvent e) {
                clearAnnotations(e.getOffset());
            }
        });
        editorPanel.setToolTipText("this will contain annotations");
    }

    private synchronized void addStyles(StyledDocument doc) {
        Style def = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
        Style s1 = doc.addStyle("error", def);
        StyleConstants.setBackground(s1, Color.PINK);
        Style s2 = doc.addStyle("programCounter", def);
        StyleConstants.setBackground(s2, Color.GREEN.brighter().brighter() );
    }

    /**
     * remove all annotations
     */
    public void clearAnnotations() {
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                Style def = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
                StyledDocument doc = editorPanel.getStyledDocument();
                doc.setCharacterAttributes(0, doc.getLength(), def, true);
            }
        } );
        annotations= new TreeMap<Integer, Annotation>();
    }

    /**
     * remove all annotations at the position
     */
    public void clearAnnotations(int pos) {
        final Annotation ann = annotationAt(pos);
        if (ann != null) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    Style def = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
                    StyledDocument doc = editorPanel.getStyledDocument();
                    doc.setCharacterAttributes(ann.offset, ann.len, def, true);
                    annotations.remove(ann.offset);
                }
            } );
        }
    }

    private static class Annotation {
        String text;
        int offset;
        int len;
    }
    SortedMap<Integer, Annotation> annotations = new TreeMap<Integer, Annotation>();

    private Annotation annotationAt(int offset) {

        int annoOffset;
        SortedMap<Integer, Annotation> head = annotations.headMap(offset);
        if (head.size() == 0) {
            return null;
        } else {
            annoOffset = head.lastKey();
        }
        Annotation ann = annotations.get(annoOffset);
        if (ann.len > offset - ann.offset) {
            return ann;
        } else {
            return null;
        }
    }

    /**
     * highlite the line by setting the background to color.  null clears the highlite.
     * @param line, the line number to highlite.  1 is the first line.
     * @param name, the name of the style, including "error" and "programCounter"
     * @param text, annotation to display when hovering. Currently ignored.
     */
    public void annotateLine(int line, String name, String text) throws BadLocationException {
        annotateLine( line, name, text, null );
    }

    /**
     * highlite the line by setting the background to color.  null clears the highlite.
     * @param line, the line number to highlite.  1 is the first line.
     * @param name, the name of the style, including "error" and "programCounter"
     * @param text, annotation to display when hovering. Currently ignored.
     * @param interp, the interpretter to focus on.
     */
    public void annotateLine( final int line, final String name, final String text, final PythonInterpreter interp ) {
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                StyledDocument doc = editorPanel.getStyledDocument();
                Element root = editorPanel.getDocument().getDefaultRootElement();

                if ( line<1 || line>root.getElementCount()+1 ) {
                    throw new IllegalArgumentException( "no such line: "+line );
                }

                int i0, i1;

                if ( line<=root.getElementCount() ) {
                    i0 = root.getElement(line - 1).getStartOffset();
                    i1 = root.getElement(line - 1).getEndOffset();
                } else {
                    i0 = Math.max(0, doc.getLength()-2 );
                    i1 = doc.getLength();
                }
                annotateChars(i0, i1, name, text, interp);
            }
        } );
    }

    public void annotateChars( final int i0, final int i1, final String name, final String text, final PythonInterpreter interp ) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {

                StyledDocument doc = editorPanel.getStyledDocument();
                Element root = editorPanel.getDocument().getDefaultRootElement();

                Style style = doc.getStyle(name);
                if (style == null) {
                    addStyles(doc);
                    style = doc.getStyle(name);
                }
                doc.setParagraphAttributes(i0, i1 - i0, StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE), false);
                doc.setCharacterAttributes(i0, i1 - i0, style, true);
                Annotation ann = new Annotation();
                ann.len = i1 - i0;
                ann.offset = i0;
                ann.text = text;
                annotations.put(ann.offset, ann);
                EditorAnnotationsSupport.this.interp= interp;
            }
        } );
    }

    private String htmlify( String text ) {
        StringBuffer buff= new StringBuffer();
        buff.append("<html>");
        String[] ss= text.split("\n",-2);
        for ( int i=0; i<ss.length-1; i++ ) {
            buff.append(ss[i]+"<br>");
        }
        buff.append(ss[ss.length-1]);
        buff.append("</html>");
        return buff.toString();
    }
    /**
     * The editor component should delegate to these.
     * @param me
     * @return
     */
    public String getToolTipText(MouseEvent me) {
        int offset= editorPanel.viewToModel(me.getPoint());
        if ( editorPanel.getSelectionStart()<offset && offset<editorPanel.getSelectionEnd() ) {
            String eval= editorPanel.getSelectedText();
            if ( interp!=null ) {
                try {
                    String peek= interp.eval(eval).toString();
                    return peek;
                } catch ( Exception ex ) {
                    return ""+ex.toString();
                }
            }
        }

        if ( offset>0 ) {
            Annotation ann= annotationAt(offset);
            if ( ann!=null ) {
                return htmlify(ann.text);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }
    
    /**
     * The editor component should delegate to these.
     * @param me
     * @return
     */
    public Dimension getPreferredSize() {
        return new Dimension(350,250);
    }    
    
    
}
