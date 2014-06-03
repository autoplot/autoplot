/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.jythonsupport.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import jsyntaxpane.components.Markers;
import jsyntaxpane.components.Markers.SimpleMarker;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;

/**
 *
 * @author jbf
 */
public class EditorAnnotationsSupport {
    /**
     * error marked in the code
     */
    public static final String ANNO_ERROR = "error";

    /**
     * current interpreter position
     */
    public static final String ANNO_PROGRAM_COUNTER = "programCounter";
    
    /**
     * warning in the code
     */
    public static final String ANNO_WARNING = "warning";

    /**
     * usage of a symbol in the code
     */
    public static final String ANNO_USAGE = "usage";

    /**
     * return the symbol at the current location, or ""
     * @param editor
     * @return the symbol (e.g. variable name) at the current location
     */
    public static String getSymbolAt( EditorTextPane editor ) {
        int i= editor.getCaretPosition();
        String s= editor.getText();
        if ( i>=1 && !Character.isJavaIdentifierPart(s.charAt(i)) && Character.isJavaIdentifierPart(s.charAt(i-1)) ) {
            i=i-1;
        }
        while ( i>=0 && Character.isJavaIdentifierPart(s.charAt(i)) ) {
            i=i-1;
        }
        if ( !Character.isJavaIdentifierPart(s.charAt(i)) ) i=i+1;
        int i0= i;
        while ( i<s.length() && Character.isJavaIdentifierPart(s.charAt(i)) ) {
            i=i+1;
        }
        return s.substring(i0,i);
    }

    private JEditorPane editorPanel;
    PythonInterpreter interp;

    EditorAnnotationsSupport(JEditorPane editorPanel) {
        this.editorPanel = editorPanel;
        final DocumentListener annoList= new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                clearAnnotations(e.getOffset());
            }
            public void removeUpdate(DocumentEvent e) {
                clearAnnotations(e.getOffset());
            }
            public void changedUpdate(DocumentEvent e) {
                clearAnnotations(e.getOffset());
            }
        };
        editorPanel.getDocument().addDocumentListener(annoList);
        this.editorPanel.addPropertyChangeListener( "document", new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ( evt.getOldValue()!=null ) {
                    ((Document)evt.getOldValue()).removeDocumentListener(annoList);
                }
                ((Document)evt.getNewValue()).addDocumentListener(annoList);
            }

        });
        editorPanel.setToolTipText("this will contain annotations");
    }

    /**
     * remove all annotations
     */
    public void clearAnnotations() {
        if ( SwingUtilities.isEventDispatchThread() ) {
            Markers.removeMarkers(editorPanel);
            editorPanel.getHighlighter().removeAllHighlights();
        } else {
           SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    Markers.removeMarkers(editorPanel);
                    editorPanel.getHighlighter().removeAllHighlights();
                }
            } );
        }
        annotations= new TreeMap<Integer, Annotation>();
    }

    /**
     * remove all annotations at the position
     * @param pos the position in character position within the document.
     */
    public void clearAnnotations(int pos) {
        final Annotation ann = annotationAt(pos);
        if (ann != null) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    Markers.removeMarkers(editorPanel,ann.marker);
                    annotations.remove(ann.offset);
                    if ( ann.highlightInfo!=null ) {
                        editorPanel.getHighlighter().removeHighlight(ann.highlightInfo);
                    }
                }
            } );
        }
    }

    private static class Annotation {
        String text;
        int offset;
        int len;
        SimpleMarker marker;
        Object highlightInfo;
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
     * @param line the line number to highlite.  1 is the first line.
     * @param name the name of the style, including "error" and "programCounter"
     * @param text annotation to display when hovering. Currently ignored.
     */
    public void annotateLine(int line, String name, String text) throws BadLocationException {
        annotateLine( line, name, text, null );
    }

    /**
     * highlite the line by setting the background to color.  null clears the highlite.
     * @param lline the line number to highlite.  1 is the first line.
     * @param name the name of the style, including "error" and "programCounter"
     * @param ltext annotation to display when hovering.
     * @param interp the interpreter to focus on.
     */
    public void annotateLine( int lline, final String name, String ltext, final PythonInterpreter interp ) {
        if ( lline<1 ) {
            new IllegalArgumentException("no such line: "+lline).printStackTrace();
            lline=1;
            ltext= ltext+ "\n(line number was "+lline+", see stderr)";
        }
        final int line= lline;
        final String text= ltext;
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                Document doc = editorPanel.getDocument();
                Element root = editorPanel.getDocument().getDefaultRootElement();

                if ( root.getElementCount()==1 ) { // transitional case where the document is cleared.
                    return;
                }
                
                if ( line>root.getElementCount()+1 ) {
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

    /**
     * annotate the characters on the line.  This was introduced to highlite the location of symbol names.
     * @param line the line number, where 1 is the first line.
     * @param i0 the column number, where 1 is the first column.
     * @param i1 the last column number, exclusive.
     * @param name annotation type, such as "usage" or "error" see constants.
     * @param text the tooltip text.
     * @param interp null or the interpreter.
     */
    public void annotateChars( final int line, final int i0, final int i1, final String name, final String text, final PythonInterpreter interp ) {
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                Document doc = editorPanel.getDocument();
                Element root = editorPanel.getDocument().getDefaultRootElement();

                if ( root.getElementCount()==1 ) { // transitional case where the document is cleared.
                    return;
                }
                
                if ( line>root.getElementCount()+1 ) {
                    throw new IllegalArgumentException( "no such line: "+line );
                }

                int lineStart;

                if ( line<=root.getElementCount() ) {
                    lineStart = root.getElement(line - 1).getStartOffset();
                } else {
                    lineStart = Math.max(0, doc.getLength()-2 );
                }
                annotateChars( lineStart+i0-1, lineStart+i1-1, name, text, interp );
            }
        } );
    }
    
    /**
     * 
     * @param i0 char offset for the beginning
     * @param i1 char offset for the end.
     * @param name ANNO_WARNING, ANNO_ERROR, ANNO_PROGRAM_COUNTER
     * @param text text to further explain
     * @param interp allow for further queries by resetting the interpreter.
     */
    public void annotateChars( final int i0, final int i1, final String name, final String text, final PythonInterpreter interp ) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {

                SimpleMarker mark;
                Object highlightInfo=null;
                
                if ( name.equals(ANNO_WARNING) ) {
                    mark= new SimpleMarker(Color.YELLOW);
                } else if ( name.equals(ANNO_USAGE) ) {
                    mark= new SimpleMarker(Color.YELLOW.brighter());
                } else if ( name.equals(ANNO_ERROR) ) {
                    mark= new SimpleMarker(Color.PINK);
                } else if ( name.equals(ANNO_PROGRAM_COUNTER) ){
                    mark=  new SimpleMarker(Color.GREEN.brighter().brighter() );
                } else {
                    mark=  new SimpleMarker(Color.GRAY );
                }
                
                if (  name.equals(ANNO_ERROR) ) {
                    SquigglePainter red= new SquigglePainter( Color.RED );
                    try {
                        highlightInfo= editorPanel.getHighlighter().addHighlight(i0, i1, red);
                    } catch (BadLocationException ex) {
                        Logger.getLogger(EditorAnnotationsSupport.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else {
                    Markers.markText( editorPanel, i0, i1, mark );
                }
                
                Annotation ann = new Annotation();
                ann.len = i1 - i0;
                ann.offset = i0;
                ann.text = text;
                ann.marker= mark;
                ann.highlightInfo= highlightInfo;
                annotations.put(ann.offset, ann);
                EditorAnnotationsSupport.this.interp= interp;
            }
        } );
    }

    private String htmlify( String text ) {
        StringBuilder buff= new StringBuilder();
        buff.append("<html>");
        String[] ss= text.split("\n",-2);
        for ( int i=0; i<ss.length-1; i++ ) {
            buff.append(ss[i]).append("<br>");
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
        if ( editorPanel.getSelectionStart()<=offset && offset<editorPanel.getSelectionEnd() ) {
            String eval= editorPanel.getSelectedText();
            if ( interp!=null ) {
                try {
                    PyObject po= interp.eval(eval);
                    String peek;
                    peek= String.valueOf( po.__str__() );
                    return peek;
                } catch ( Exception ex ) {
                    return ""+ex.toString();
                }
            } else {
                return "Interpreter is not active";
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
