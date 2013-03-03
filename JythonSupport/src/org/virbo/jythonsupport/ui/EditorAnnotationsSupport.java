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
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import jsyntaxpane.components.Markers;
import jsyntaxpane.components.Markers.SimpleMarker;
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
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                Markers.removeMarkers(editorPanel);
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
                    Markers.removeMarkers(editorPanel,ann.marker);
                    annotations.remove(ann.offset);
                }
            } );
        }
    }

    private static class Annotation {
        String text;
        int offset;
        int len;
        SimpleMarker marker;
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
     * TODO: with JythonSyntaxPane, this prevents selections from being seen.  Fix this.
     * @param line, the line number to highlite.  1 is the first line.
     * @param name, the name of the style, including "error" and "programCounter"
     * @param text, annotation to display when hovering. Currently ignored.
     * @param interp, the interpretter to focus on.
     */
    public void annotateLine( final int line, final String name, final String text, final PythonInterpreter interp ) {
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                Document doc = editorPanel.getDocument();
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

                SimpleMarker mark;
                if ( name.equals(ANNO_WARNING) ) {
                    mark= new SimpleMarker(Color.YELLOW);
                } else if ( name.equals(ANNO_ERROR) ) {
                    mark= new SimpleMarker(Color.PINK);
                } else if ( name.equals(ANNO_PROGRAM_COUNTER) ){
                    mark=  new SimpleMarker(Color.GREEN.brighter().brighter() );
                } else {
                    mark=  new SimpleMarker(Color.GRAY );
                }
                
                Markers.markText( editorPanel, i0, i1, mark );
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
