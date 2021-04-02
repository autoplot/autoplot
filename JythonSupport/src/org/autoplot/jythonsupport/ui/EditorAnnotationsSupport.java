
package org.autoplot.jythonsupport.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
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
import org.python.core.PyException;
import org.python.core.PyInteger;
import org.python.core.PyJavaInstance;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.core.PySyntaxError;
import org.python.util.PythonInterpreter;

/**
 * annotations support for the editor, marking program counter position 
 * and errors.
 * @see ScriptPanelSupport
 * @author jbf
 */
public class EditorAnnotationsSupport {
    
    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.jython");
    
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
     * warning in the code
     */
    public static final String ANNO_CODE_HINT = "codeHint";

    /**
     * usage of a symbol in the code
     */
    public static final String ANNO_USAGE = "usage";

    /**
     * return the symbol (e.g. variable name) at the caret position, or "".
     * @param editor the code editor
     * @param position typically editor.getCarotPosition
     * @return the symbol (e.g. variable name) at the current caret location
     */
    public static String getSymbolAt( EditorTextPane editor, int position) {
        int i= position;
        String s= editor.getText();
        if ( i>0 && i<s.length() && !Character.isJavaIdentifierPart(s.charAt(i)) && Character.isJavaIdentifierPart(s.charAt(i-1)) ) {
            i=i-1;
        }
        if ( i>0 && i==s.length() && Character.isJavaIdentifierPart(s.charAt(i-1) ) ) {
            i=i-1;
        }
        while ( i>0 && i<s.length() && Character.isJavaIdentifierPart(s.charAt(i)) ) {
            i=i-1;
        }
        if ( i>=s.length() ) {
            return "";
        }
        if ( !Character.isJavaIdentifierPart(s.charAt(i)) ) i=i+1;
        int i0= i;
        while ( i<s.length() && Character.isJavaIdentifierPart(s.charAt(i)) ) {
            i=i+1;
        }
        if ( s.length()>=i ) {
            return s.substring(i0,i);
        } else {
            return "";
        }
    }

    private final JEditorPane editorPanel;

    EditorAnnotationsSupport(JEditorPane editorPanel) {
        this.editorPanel = editorPanel;
        final DocumentListener annoList= new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                clearAnnotations(e.getOffset());
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
                clearAnnotations(e.getOffset());
            }
            @Override
            public void changedUpdate(DocumentEvent e) {
                clearAnnotations(e.getOffset());
            }
        };
        editorPanel.getDocument().addDocumentListener(annoList);
        this.editorPanel.addPropertyChangeListener( "document", new PropertyChangeListener() {
            @Override
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
            annotations= new TreeMap<>();
        } else {
           SwingUtilities.invokeLater( new Runnable() {
                @Override
                public void run() {
                    Markers.removeMarkers(editorPanel);
                    editorPanel.getHighlighter().removeAllHighlights();
                    annotations= new TreeMap<>();
                }
            } );
        }
    }

    /**
     * remove all annotations at the position
     * @param pos the position in character position within the document.
     */
    public void clearAnnotations(int pos) {
        final Annotation ann = annotationAt(pos);
        if (ann != null) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
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

    /**
     * scroll to make sure offset is visible.
     * @param offset
     * @throws BadLocationException 
     */
    public void scrollToOffset( int  offset  ) throws BadLocationException {
        Rectangle r= editorPanel.modelToView( offset );
        editorPanel.scrollRectToVisible(r);
    }

    private static class Annotation {
        String text;
        int offset;
        int len;
        SimpleMarker marker;
        Object highlightInfo;
    }
    SortedMap<Integer, Annotation> annotations = new TreeMap<>();

    private Annotation annotationAt(int offset) {

        int annoOffset;
        SortedMap<Integer, Annotation> head = annotations.headMap(offset);
        if (head.isEmpty()) {
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
     * mark the error in the editor by looking at the python exception to get the line number.
     * @param ex the python exception
     * @param offset line offset from beginning of file where execution began.
     * @throws javax.swing.text.BadLocationException
     */
    public void annotateError(PyException ex, int offset) throws BadLocationException {
        if (ex instanceof PySyntaxError) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            int lineno = offset + ((PyInteger) ex.value.__getitem__(1).__getitem__(1)).getValue();
            //int col = ((PyInteger) ex.value.__getitem__(1).__getitem__(2)).getValue();
            annotateLine(lineno, "error", ex.toString());
        } else {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            annotateLine(offset + ex.traceback.tb_lineno, "error", ex.toString());
        }
    }

    /**
     * highlite the line by setting the background to color.  null clears the highlite.
     * @param line the line number to highlite.  1 is the first line.
     * @param name the name of the style, including "error" and "programCounter"
     * @param text annotation to display when hovering. Currently ignored.
     * @throws javax.swing.text.BadLocationException
     */
    public void annotateLine(int line, String name, String text) throws BadLocationException {
        annotateLine( line, name, text, null );
    }

    /**
     * highlite the line by setting the background to color.  null clears the highlite.
     * @param lline the line number to highlite.  1 is the first line.
     * @param name the name of the style, including "error" and "programCounter"
     * @param ltext annotation to display when hovering.
     * @param interp the interpreter (or null) to focus on.
     */
    public void annotateLine( int lline, final String name, String ltext, final PythonInterpreter interp ) {
        if ( lline<1 ) {
            new IllegalArgumentException("no such line: "+lline).printStackTrace();
            lline=1;
            ltext= ltext+ "\n(line number was "+lline+", see stderr)";
        }
        final int line= lline;
        final String text= ltext;
        
        Element root = editorPanel.getDocument().getDefaultRootElement();
        if ( line>root.getElementCount()+1 ) {
            System.err.println("*** can't annotate line: "+lline );
            return;
        }
        
        SwingUtilities.invokeLater( new Runnable() {
            @Override
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
     * return the position of the line in chars.  The second is exclusive.
     * @param line the line number 1 is the first line.
     * @return [st,en]
     */
    public int[] getLinePosition( int line ) {
        Document doc = editorPanel.getDocument();
        Element root = editorPanel.getDocument().getDefaultRootElement();

        if ( root.getElementCount()==1 ) { // transitional case where the document is cleared.
            return new int[] { 0,0 };
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
        return new int[] { i0, i1 };
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
            @Override
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
     * @param interp the interpreter or null, to allow for further queries by resetting the interpreter.
     */
    public void annotateChars( final int i0, final int i1, final String name, final String text, final PythonInterpreter interp ) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                
                boolean lightBackground= ( ( 
                        editorPanel.getBackground().getRed() +
                        editorPanel.getBackground().getGreen() +
                        editorPanel.getBackground().getBlue() ) / 3 ) > 100;
                        
                SimpleMarker mark;
                Object highlightInfo=null;
                
                switch (name) {
                    case ANNO_WARNING:
                        mark= new SimpleMarker( lightBackground ? Color.YELLOW : new Color(120,120,0) );
                        break;
                    case ANNO_CODE_HINT:
                        mark= new SimpleMarker( lightBackground ? new Color(255,255,0,80) : new Color(120,120,0,80) );
                        break;
                    case ANNO_USAGE:
                        mark= new SimpleMarker( lightBackground ? Color.GREEN.brighter() : new Color(0,100,0) );
                        break;
                    case ANNO_ERROR:
                        mark= new SimpleMarker( lightBackground ? Color.PINK : new Color(120,80,80));
                        break;
                    case ANNO_PROGRAM_COUNTER:
                        mark=  new SimpleMarker( lightBackground ? new Color( 0,255,0,80 ) :  new Color( 0,200,0,80 ) );
                        break;
                    default:
                        mark=  new SimpleMarker(Color.GRAY );
                        break;
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
    
    public static interface ExpressionLookup {
        /**
         * evaluate the expression, or return null.
         * @param expr the expression
         * @return the value or null.
         */
        PyObject lookup( String expr );
    }
    
    private static ExpressionLookup expressionLookup;
    
    public static void setExpressionLookup( ExpressionLookup l ) {
        expressionLookup= l;
    }
    
    public static ExpressionLookup getExpressionLookup() {
        return expressionLookup;
    }
    
    public ExpressionLookup getForInterp( final PythonInterpreter interp ) {
        return new ExpressionLookup() {
            @Override
            public PyObject lookup(String expr) {
                if ( expr==null ) {
                    return new PyString("<html>highlite an expression");
                }
                try {
                    PyObject po= interp.eval(expr);
                    return po;
                } catch ( Exception e ) {
                    String msg= e.getMessage();
                    if ( msg==null ) {
                        msg=e.toString();
                        int i= msg.lastIndexOf("?\n");
                        if ( i>-1 ) msg= msg.substring(i+2).trim();
                    }
                    msg= msg.replaceAll("\n","<br>\n");
                    //msg= "<b>"+expr+"</b><br>\n" + msg;
                    return new PyString("<html>highlite an expression:<br>"+msg);
                }
            }
        };
    }
    
    /**
     * The editor component should delegate to these.
     * @param me
     * @return the text
     */
    public String getToolTipText(MouseEvent me) {
        int offset= editorPanel.viewToModel(me.getPoint());
        if ( editorPanel.getSelectionStart()<=offset && offset<=editorPanel.getSelectionEnd() ) {
            String expr= editorPanel.getSelectedText();
            if ( expressionLookup!=null ) {
                if ( expr!=null ) {
                    PyObject po= expressionLookup.lookup(expr);
                    String peek;
                    peek= String.valueOf( po.__str__() );
                    if ( peek.startsWith("<html>" ) ) {
                        return peek;
                    } else {
                        if ( po instanceof PyJavaInstance ) {
                            try {
                                return "<html>"+expr+"="+peek+"<br>"+((PyJavaInstance)po).instclass.safeRepr();
                            } catch ( Exception ex ) {
                                return "<html>"+expr+"="+peek+"<br>"+po.getType();
                            }
                        } else {
                            return "<html>"+expr+"="+peek+"<br>"+po.getType();
                        }
                    }
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
     * @return the preferred size
     */
    public Dimension getPreferredSize() {
        return new Dimension(350,250);
    }    
    
    
}
