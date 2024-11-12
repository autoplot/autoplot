/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.jythonsupport;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import org.autoplot.jythonsupport.ui.EditorAnnotationsSupport;
import org.das2.jythoncompletion.Utilities;
import org.das2.util.LoggerManager;
import org.python.core.PyList;
import org.python.core.PyObject;
import org.python.core.PyStringMap;
import org.python.parser.SimpleNode;
import org.python.parser.ast.Assign;
import org.python.parser.ast.Call;
import org.python.parser.ast.ClassDef;
import org.python.parser.ast.For;
import org.python.parser.ast.FunctionDef;
import org.python.parser.ast.Global;
import org.python.parser.ast.If;
import org.python.parser.ast.Import;
import org.python.parser.ast.ImportFrom;
import org.python.parser.ast.Module;
import org.python.parser.ast.Name;
import org.python.parser.ast.VisitorBase;
import org.python.parser.ast.While;
import org.python.parser.ast.aliasType;
import org.python.parser.ast.exprType;
import org.python.parser.ast.stmtType;
import org.python.util.InteractiveInterpreter;

/**
 * Static Code Analysis checks for bug patterns like variable writes without a read.
 * @author jbf
 */
public class StaticCodeAnalysis {
    
    private static final Logger logger = LoggerManager.getLogger("jython.staticcodeanalysis");

    private static final Map<String,SimpleNode> definedNamesApp= new HashMap<>();
    
    static {
        try {
            
            //TODO: this is silly.  Figure out how to do this correctly:
            definedNamesApp.put("None",null);
            definedNamesApp.put("True",null);
            definedNamesApp.put("False",null);
            definedNamesApp.put("len",null);
            definedNamesApp.put("open",null);
            definedNamesApp.put("str",null);
            definedNamesApp.put("range",null);
            definedNamesApp.put("xrange",null);
            definedNamesApp.put("int",null);
            definedNamesApp.put("float",null);
            definedNamesApp.put("Exception",null);
            
            //TODO: more are needed, this is still experimental!
            InteractiveInterpreter interp= JythonUtil.createInterpreter(true);
            
            if ( org.autoplot.jythonsupport.Util.isLegacyImports() ) {
                boolean appContext= true;
                if ( appContext ) {
                    try ( InputStream in = JythonUtil.class.getResource("/appContextImports2017.py").openStream() ) {
                        interp.execfile( in, "/appContextImports2017.py" ); // JythonRefactory okay
                    }
                }
            }
            
            definedNamesApp.put( "dom", null );
            definedNamesApp.put( "monitor", null );
            definedNamesApp.put( "plotx", null );
            definedNamesApp.put( "plot", null );
            definedNamesApp.put( "dataset", null );
            definedNamesApp.put( "annotation", null );
            
            PyObject po= interp.getLocals();
            if ( po instanceof PyStringMap ) {
                PyStringMap psm= (PyStringMap)po;
                PyList k= psm.keys();
                for ( int i=0; i<k.__len__(); i++ ) {
                    definedNamesApp.put( k.get(i).toString(), null );
                }
            }
            
        } catch (IOException ex) {
            Logger.getLogger(JythonUtil.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    private static class VisitNamesVisitorBase<R> extends VisitorBase {

        String name;
        
        /**
         * list containing nodes where name is found.
         */
        List<SimpleNode> names;
        
        Map<String,SimpleNode> assignButNotReadWarning;
        List<SimpleNode> reassignedBeforeReadWarning;
        Map<String,SimpleNode> readButNotAssignedError;
        Map<String,SimpleNode> definedNames;
        Map<String,SimpleNode> reassignedFunctionCalls; // the things that have been reassigned.
        List<SimpleNode> reassignedFunctionCallWarning; // the things reassigned and then used as functions.

        VisitNamesVisitorBase(String name) {
            if ( name==null ) throw new NullPointerException("set to empty string not null");
            this.name = name;
            names = new ArrayList();
            assignButNotReadWarning= new LinkedHashMap<>();
            reassignedBeforeReadWarning= new ArrayList<>();
            reassignedFunctionCalls= new LinkedHashMap<>();
            reassignedFunctionCallWarning= new ArrayList<>();
            readButNotAssignedError= new LinkedHashMap<>();
            definedNames= new HashMap<>(definedNamesApp);
            //definedNames= new HashMap<>();
        }
        
        /**
         * add a name which is known to be valid, like PWD.
         * @param name 
         */
        public void addName( String name ) {
            if ( !definedNames.containsKey(name) ) {
                definedNames.put( name, null );
            }
        }

        private void handleStmtType( stmtType st ) {
            logger.log(Level.FINER, "handleStmtType line{0}", st.beginLine);
            try {
                if ( st instanceof ImportFrom ) {
                    for ( aliasType a: ((ImportFrom) st).names ) {
                        if ( a.asname!=null ) {
                            this.addName( a.asname );
                            logger.log(Level.FINE, "assignButNotReadWarning asname {0}", a.asname);
                            this.assignButNotReadWarning.put( a.asname, a );
                        } else {
                            this.addName( a.name );
                            logger.log(Level.FINE, "assignButNotReadWarning name {0}", a.name);
                            this.assignButNotReadWarning.put( a.name, a );
                        }
                    }
                    st.traverse(this);
                } else if ( st instanceof Import ) {
                    for ( aliasType a: ((Import) st).names ) {
                        if ( a.asname!=null ) {
                            this.addName( a.asname );
                        } else {
                            this.addName( a.name );
                        }
                    }
                    st.traverse(this);
                    
                } else if ( st instanceof Global ) {
                    Global gst= (Global)st;
                    for ( String s: gst.names ) {
                        this.addName( s );
                    }
                    
                } else if ( st instanceof FunctionDef ) {
                    FunctionDef fd= (FunctionDef)st;
                    this.addName( fd.name );
                    for ( exprType att : fd.args.args ) {
                        if ( att instanceof Name ) {
                            this.addName( ((Name) att).id ); // TODO: This is silly, since we need to remove the name after the function.
                        }
                    }
                    for ( stmtType sst : fd.body ) {
                        handleStmtType(sst);
                    }
                    
                } else if ( st instanceof ClassDef ) {
                    ClassDef cld= (ClassDef)st;                    
                    this.addName( cld.name );
                    for ( exprType t : cld.bases ) {
                        handleExprTypeRead(t);
                    }
                    for ( stmtType sst : cld.body ) {
                        handleStmtType(sst);
                    }
                    
                } else if ( st instanceof Assign ) {
                    Assign ast= ((Assign) st);
                    handleExprTypeRead(ast.value);
                    logger.log(Level.FINER, "assignButNotRead={0}", this.assignButNotReadWarning);
                    logger.log(Level.FINER, "reassignedBeforeRead={0}", this.reassignedBeforeReadWarning);
                    for ( exprType t: ast.targets ) {
                        handleExprTypeAssign(t);
                    }
                } else if ( st instanceof If ) {
                    If ist= ((If) st);
                    handleExprTypeRead(ist.test);
                    
                    Map<String,SimpleNode> beforeIf= new HashMap<>(this.assignButNotReadWarning);
                    for ( stmtType sst: ist.body ) {
                        handleStmtType(sst);
                    }
                    if ( ist.orelse!=null ) {
                        Map<String,SimpleNode> afterIf= new HashMap<>(this.assignButNotReadWarning);
                        List<String> ifClears= new ArrayList<>();
                        for ( String n: beforeIf.keySet() ) {
                            if ( !afterIf.containsKey(n) ) {
                                ifClears.add(n);
                            }
                        }
                        this.assignButNotReadWarning= beforeIf;
                        Map<String,SimpleNode> beforeElse= new HashMap<>(this.assignButNotReadWarning);
                        for ( stmtType sst: ist.orelse ) {
                            handleStmtType(sst);
                        }
                        Map<String,SimpleNode> afterElse= new HashMap<>(this.assignButNotReadWarning);
                        List<String> elseClears= new ArrayList<>();
                        for ( String n: beforeElse.keySet() ) {
                            if ( !afterElse.containsKey(n) ) {
                                elseClears.add(n);
                            }
                        }
                        this.assignButNotReadWarning.putAll(afterIf);
                        for ( String n: ifClears ) {
                            this.assignButNotReadWarning.remove(n);
                        }
                        for ( String n: elseClears ) {
                            this.assignButNotReadWarning.remove(n);
                        }
                    }
                } else if ( st instanceof For ) {
                    For fst= ((For) st);
                    handleExprTypeRead(fst.iter);
                    handleExprTypeAssign(fst.target);
                    for ( stmtType sst: fst.body ) {
                        handleStmtType(sst);
                    }
                    if ( fst.orelse!=null ) {
                        for ( stmtType sst: fst.orelse ) {
                            handleStmtType(sst);
                        }
                    }
                } else if ( st instanceof While ) {
                    While fst= ((While) st);
                    handleExprTypeRead(fst.test);
                    for ( stmtType sst: fst.body ) {
                        handleStmtType(sst);
                    }
                    handleExprTypeRead(fst.test);
                } else {
                    st.traverse(this);
                }
            } catch (Exception ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }

        private void handleExprTypeRead( exprType t ) throws Exception {
            if ( t instanceof Name ) {
                visitName((Name)t);
            } else {
                t.traverse(this);
            }
        }
        
        private void handleExprTypeAssign( exprType t ) throws Exception {
            if ( t instanceof Name ) {
                String n= ((Name)t).id;
                if ( definedNames.containsKey(n) ) {
                    logger.finer("reassign name");
                    this.reassignedFunctionCalls.put(n,t);
                }
                this.addName( n ); //  !!!! Why must I do this manually?!?!?
                SimpleNode notRead= (SimpleNode)this.assignButNotReadWarning.get(n);
                if ( notRead!=null ) {
                    logger.log(Level.FINE, "reassignedBeforeReadWarning {0} line {1}", new Object[]{n, ((Name) t).beginLine});
                    this.reassignedBeforeReadWarning.add( notRead );
                }
                this.assignButNotReadWarning.put( n, t );
              
            } 
            t.traverse(this);
        }
        
        
        @Override
        public Object visitName(Name node) throws Exception {
            
            logger.log(Level.FINER, "visitName line{0} {1} {2}", new Object[]{node.beginLine, node.id, Name.expr_contextTypeNames[node.ctx] });
            if ( name.equals(node.id)) {
                names.add(node);
            }
            
            if ( node.ctx==Name.Store ) {
                if ( assignButNotReadWarning.containsKey( node.id ) ) {
                    reassignedBeforeReadWarning.add( assignButNotReadWarning.get(node.id) );
                }
                assignButNotReadWarning.put(node.id, node);
                definedNames.put(node.id, node);
            } else if ( node.ctx==Name.Load ) {
                if ( assignButNotReadWarning.containsKey(node.id) ) {
                    logger.log(Level.FINE, "assignedBeforeReadWarning use {0} line {1}", new Object[]{node.id, ((Name) node).beginLine});
                }
                assignButNotReadWarning.remove(node.id);
                if ( !definedNames.containsKey( node.id ) ) {
                    readButNotAssignedError.put(node.id,node);
                }
            }
            
            return node;
        }

        @Override
        public Object visitCall(Call node) throws Exception {
            if ( node.func instanceof Name ) {
                String name= ((Name)node.func).id;
                if ( reassignedFunctionCalls.containsKey(name) ) {
                    reassignedFunctionCallWarning.add(reassignedFunctionCalls.get(name));
                    reassignedFunctionCallWarning.add(node);
                }
            }
            return super.visitCall(node); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        protected Object unhandled_node(SimpleNode sn) throws Exception {
            return sn;
        }

        @Override
        public void traverse(SimpleNode sn) throws Exception {
            sn.traverse(this);
        }

        @Override
        public Object visitImport(Import node) throws Exception {
            handleStmtType(node);
            return super.visitImport(node); 
        }

        @Override
        public Object visitImportFrom(ImportFrom node) throws Exception {
            handleStmtType(node);
            return super.visitImportFrom(node); 
        }
        
        /**
         * return the nodes where the name is used.
         *
         * @return
         */
        public List<SimpleNode> getNames() {
            return this.names;
        }
        
        /**
         * return the nodes where a value is assigned but then never read.
         * @return 
         */
        public List<SimpleNode> getAssignedButNotRead() {
            ArrayList<SimpleNode> result= new ArrayList<>( this.reassignedBeforeReadWarning );
            result.addAll( this.assignButNotReadWarning.values() ); 
            return result;
        }
        
        /**
         * return the nodes which contain a symbol which has not been assigned.
         * @return 
         */
        public List<SimpleNode> getReadButNotAssigned() {
            return new ArrayList<>( this.readButNotAssignedError.values() );
        }

        private List<SimpleNode> getReassignedFunctionCalls() {
            return new ArrayList<>( this.reassignedFunctionCallWarning );
        }
    }

    /**
     * return any name which is used as a function call but has been reassigned.
     * @param script the script code (not the filename).
     * @param appContext true if application codes are loaded
     * @param pwd null or the value of the working directory.
     * @return 
     */
    public static List<SimpleNode> showReassignFunctionCall(String script, boolean appContext, String pwd ) {
        Module n = (Module) org.python.core.parser.parse(script, "exec");
        VisitNamesVisitorBase vb = new VisitNamesVisitorBase("");
        if ( pwd!=null ) {
            vb.addName("PWD");
        }
        for (stmtType st : n.body) {
            vb.handleStmtType(st);
        }
        return vb.getReassignedFunctionCalls();
    }
    
    /**
     * return any node where a variable is assigned but then not later read.  This is 
     * not an error, but is a nice way to flag suspicious code.
     * @param script the script code (not the filename).
     * @return 
     */
    public static List<SimpleNode> showWriteWithoutRead(String script) {
        logger.log(Level.FINE, "# showWriteWithoutRead (script length={0})", script.length());
        Module n = (Module) org.python.core.parser.parse(script, "exec");
        VisitNamesVisitorBase vb = new VisitNamesVisitorBase("");
        for (stmtType st : n.body) {
            logger.log(Level.FINER, "line {0}", st.beginLine);
            vb.handleStmtType( st );
        }
        return vb.getAssignedButNotRead();
    }

    /**
     * return any node where a name is read but has not been assigned.  This is an
     * error which would show when the code is run.
     * @param script the script code (not the filename).
     * @param appContext true if application codes are loaded
     * @param pwd null or the value of the working directory.
     * @return 
     */
    public static List<SimpleNode> showReadButNotAssigned(String script, boolean appContext, String pwd ) {
        Module n = (Module) org.python.core.parser.parse(script, "exec");
        VisitNamesVisitorBase vb = new VisitNamesVisitorBase("");
        if ( pwd!=null ) {
            vb.addName("PWD");
        }
        for (stmtType st : n.body) {
            vb.handleStmtType(st);
        }
        return vb.getReadButNotAssigned();
    }
    
    /**
     * get the nodes where the symbol is used.
     *
     * @param script the jython script which is parsed.
     * @param symbol the symbol to look for, a Jython name
     * @return the AST nodes which contain location information.
     */
    public static List<SimpleNode> showUsage(String script, String symbol) {
        Module n = (Module) org.python.core.parser.parse(script, "exec");
        VisitNamesVisitorBase vb = new VisitNamesVisitorBase(symbol);
        for (stmtType st : n.body) {
            try {
                st.traverse(vb);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
        List<SimpleNode> usages= vb.getNames();
        usages.forEach((use) -> {
            int start= Utilities.getOffsetForLineNumber( script, use.beginLine-1 ); 
            int stop= Utilities.getOffsetForLineNumber( script, use.beginLine );
            String theLine= script.substring( start, stop );
            String theWord= theLine.substring( use.beginColumn-1, (use.beginColumn-1)+symbol.length() );
            if (!theWord.equals(symbol)) {
                logger.info("That bug with the parens has happened");
                int shift= theLine.indexOf(symbol,use.beginColumn-1) - (use.beginColumn-1);
                if (shift>0) {
                    use.beginColumn += shift;
                }
            }
        });
        
        return usages;
        
    }

}
