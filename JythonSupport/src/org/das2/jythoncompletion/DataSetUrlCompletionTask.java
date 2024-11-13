
package org.das2.jythoncompletion;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import org.das2.jythoncompletion.support.CompletionResultSet;
import org.das2.jythoncompletion.support.CompletionTask;
import org.das2.util.monitor.NullProgressMonitor;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSetURI.CompletionResult;
import org.autoplot.datasource.URISplit;
import org.autoplot.jythonsupport.SimplifyScriptSupport;
import org.autoplot.jythonsupport.ui.EditorTextPane;
import static org.das2.jythoncompletion.JythonCompletionTask.CLIENT_PROPERTY_PWD;
import org.python.parser.Node;
import org.python.parser.SimpleNode;
import org.python.parser.ast.Assign;
import org.python.parser.ast.Expr;
import org.python.parser.ast.Module;
import org.python.parser.ast.Str;
import org.python.parser.ast.exprType;
import org.python.parser.ast.stmtType;

/**
 * completions of a URI within the editor, which delegate down to the 
 * data source.
 * @author jbf
 */
class DataSetUrlCompletionTask implements CompletionTask {

    private static final Logger logger = Logger.getLogger("jython.editor");

    JTextComponent editor;

    public DataSetUrlCompletionTask(JTextComponent arg1) {
        this.editor = arg1;
    }

    @Override
    public void cancel() {

    }

    public static String popStringSyntax( JTextComponent editor, int[] pos) {
        try {
            String scri= SimplifyScriptSupport.alligatorParse( editor.getText() );
            Module n= (Module) org.python.core.parser.parse(scri, "exec");
            int i0 = Utilities.getRowStart(editor, editor.getCaretPosition());
            int iline= 1 + Utilities.getLineNumberForOffset(editor, i0);
            return SimplifyScriptSupport.tryResolveStringNode( n, iline, pos[0]-i0, new LinkedHashMap<>() );
        } catch (BadLocationException ex) {
            Logger.getLogger(DataSetUrlCompletionTask.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    /**
     * returns a map of the string and any offset that is applied to the position 
     * because a field was resolved with String addition.
     * @param editor
     * @param pos
     * @return map containing string:String and offset:int tags.
     */
    public static Map<String,Object> popString(JTextComponent editor, int[] pos) {
        try {
            String s= popStringSyntax( editor, pos );
            int i0 = Utilities.getRowStart(editor, editor.getCaretPosition());
            int i1 = Utilities.getRowEnd(editor, editor.getCaretPosition()) - 1; // trim end of line
            String line = editor.getText(i0, i1 - i0);
            int ipos = editor.getCaretPosition() - i0;
            i0 = line.lastIndexOf('\'', ipos - 1);
            boolean doubleQuotes = false;
            if (i0 == -1) {
                i0 = line.lastIndexOf('\"', ipos - 1);
                if (i0 == -1) {
                    throw new IllegalArgumentException("expected single quote");
                } else {
                    doubleQuotes = true;
                }
            }
            i0 += 1;
            if (doubleQuotes) {
                i1 = line.indexOf('\"', ipos);
            } else {
                i1 = line.indexOf('\'', ipos);
            }
            if (i1 == -1) {
                i1 = line.length();
            }
            pos[0] = i0;
            pos[1] = i1;
            Map<String,Object> result= new HashMap<>();
            if ( s!=null && s.endsWith(line.substring(i0, i1)) ) {
                result.put( "string", s );
                result.put( "offset", s.length()-(i1-i0) );
            } else {
                result.put( "string", line.substring(i0, i1) );
                result.put( "offset", 0 );
            }
            return result;
        } catch (BadLocationException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
        return null;
    }

    @Override
    public void query(CompletionResultSet arg0) {
        try {
            int i0 = Utilities.getRowStart(editor, editor.getCaretPosition());
            int ipos = editor.getCaretPosition() - i0;
            int[] pos = new int[2];
            
            Map<String,Object> r= popString(editor, pos);
            String surl1 = (String)r.get("string");
            int carotPos = ipos - pos[0] + (int)r.get("offset");

            if ( editor instanceof EditorTextPane ) {
                EditorTextPane etp= (EditorTextPane)editor;
                String pwd= (String)etp.getClientProperty( CLIENT_PROPERTY_PWD );
                if ( pwd!=null ) {
                    if ( pwd.endsWith("/") ) {
                        if ( !( surl1.startsWith("/") || (surl1.length()>6 && surl1.substring(0,6).contains(":") ) ) ) {
                            surl1= pwd + surl1;
                            carotPos+= pwd.length();
                        }
                    }
                }
            }
            
            List<CompletionResult> rs = DataSetURI.getCompletions(surl1, carotPos, new NullProgressMonitor());

            for (CompletionResult rs1 : rs) {
                arg0.addItem(new DataSetUrlCompletionItem(rs1));
            }
        } catch (BadLocationException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    @Override
    public void refresh(CompletionResultSet arg0) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
