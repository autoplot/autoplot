
package org.autoplot.jythonsupport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.autoplot.datasource.DataSetURI;
import org.das2.jythoncompletion.JavadocLookup;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.NullProgressMonitor;
import org.python.core.PyFloat;
import org.python.core.PyInteger;
import org.python.parser.SimpleNode;
import org.python.parser.ast.Assign;
import org.python.parser.ast.Attribute;
import org.python.parser.ast.AugAssign;
import org.python.parser.ast.BinOp;
import org.python.parser.ast.BoolOp;
import org.python.parser.ast.Call;
import org.python.parser.ast.ClassDef;
import org.python.parser.ast.Compare;
import org.python.parser.ast.Continue;
import org.python.parser.ast.Expr;
import org.python.parser.ast.ExtSlice;
import org.python.parser.ast.For;
import org.python.parser.ast.FunctionDef;
import org.python.parser.ast.Global;
import org.python.parser.ast.If;
import org.python.parser.ast.ImportFrom;
import org.python.parser.ast.Index;
import org.python.parser.ast.Name;
import org.python.parser.ast.Num;
import org.python.parser.ast.Print;
import org.python.parser.ast.Raise;
import org.python.parser.ast.Return;
import org.python.parser.ast.Slice;
import org.python.parser.ast.Str;
import org.python.parser.ast.Subscript;
import org.python.parser.ast.Tuple;
import org.python.parser.ast.UnaryOp;
import org.python.parser.ast.VisitorBase;
import org.python.parser.ast.While;
import org.python.parser.ast.exprType;
import org.python.parser.ast.keywordType;
import org.python.parser.ast.sliceType;
import org.python.parser.ast.stmtType;

/**
 * experiment with code which converts the Jython AST (syntax tree) into Java
 * code.
 *
 * @author jbf
 */
public class JythonToJavaConverter {

    private static final Logger logger= LoggerManager.getLogger("jython");
    
    private static Map<String,String> packages= null;
    
    /**
     * scan through the list of imports in /importLookup.jy, to see
     * if the symbol can be imported.  This will return null (None) if
     * there are no suggestions, or the name of the package.
     * @param clas the class name, for example "JSlider"
     * @return the package or null, for example "javax.swing"
     */
    public synchronized static String guessPackage( String clas ) {
        if ( packages==null ) {
            try {
                Map lpackages=new HashMap<>();
                try ( BufferedReader r = new BufferedReader( new InputStreamReader(
                    JythonToJavaConverter.class.getResourceAsStream("/importLookup.jy") ) ) ) {
                    String l;
                    Pattern p= Pattern.compile("from (.*) import (.*)");
                    while ( (l= r.readLine() )!=null ) {
                        if ( l.length()==0 ) continue;
                        if ( l.charAt(0)=='#' ) continue;
                        Matcher m= p.matcher(l);
                        if ( m.matches() ) {
                            lpackages.put( m.group(2),m.group(1) );
                        } else {
                            logger.log(Level.INFO, "does not match pattern: {0}", l);
                        }       
                    }
                }
                packages= lpackages;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }

        String result= packages.get(clas);
        
        if ( result==null ) {
            List<String> sss= JavadocLookup.getInstance().searchForSignature(clas);
            for ( int i=0; i<sss.size(); i++ ) {
                String s= sss.get(i);
                int idx= s.lastIndexOf(".");
                if ( s.substring(idx+1).equals(clas) ) {
                    result= s.substring(0,idx);
                }
            }
        }

        return result;
    }

    /**
     * return a list of classes which are reasonable completions for the class
     * provided. For example, "JP" would result in "JPanel" and "JPasswordField"
     * TODO: what if this were to run through all the known JavaDoc
     * @param clas
     * @return list of completions.
     */
    public synchronized static List<String> guessCompletions(String clas) {
        ArrayList<String> result = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                JythonToJavaConverter.class.getResourceAsStream("/importLookup.jy")))) {
            String l;
            Pattern p = Pattern.compile("from (.*) import (.*)");
            while ((l = r.readLine()) != null) {
                if (l.length() == 0) {
                    continue;
                }
                if (l.charAt(0) == '#') {
                    continue;
                }
                Matcher m = p.matcher(l);
                if (m.matches()) {
                    // here is the logic
                    String tclas = m.group(2);
                    if (tclas.startsWith(clas)) {
                        result.add(tclas);
                    }
                } else {
                    logger.log(Level.INFO, "does not match pattern: {0}", l);
                }
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        List<String> sss= JavadocLookup.getInstance().searchForSignature(clas);
        for ( int i=0; i<sss.size(); i++ ) {
            String s= sss.get(i);
            int idx= s.lastIndexOf(".");
            if ( idx>-1 ) {
                result.add(s.substring(idx+1));
            }
        }
        
        return result;
    }
    
    /**
     * add the class to the list of imports.
     * @param doc the document 
     * @param pkg the Java package
     * @param name the Java class name.
     */
    public static void addImport( Document doc, String pkg, String name ) {
        addImport( doc, pkg, name, doc.getLength() );
    }
        
    /**
     * add the class to the list of imports.
     * @param doc the document 
     * @param pkg the Java package
     * @param name the Java class name.
     * @param cursorPosition the cursor position.
     */
    public static void addImport( Document doc, String pkg, String name, int cursorPosition ) {
        try {
            String s= doc.getText( 0, cursorPosition );
            String[] ss= s.split("\n");
            Pattern p= Pattern.compile("from (.+) import (.*)");
            boolean haveIt=false;
            int addToLine= -1;
            int addAtOffset= -1;
            int offset= 0;
            for ( int i=0; i<ss.length; i++ ) {
                String line= ss[i];
                Matcher m= p.matcher(line);
                if ( m.matches() ) {
                    if ( m.group(1).equals(pkg) ) {
                        String names= m.group(2);
                        String[] namess= names.split(",",-2);
                        for ( String n: namess ) {
                            if ( n.equals(name) ) {
                                haveIt= true;
                            }
                        }
                        if ( haveIt==false ) {
                            addToLine= i;
                            addAtOffset= offset + line.length();
                        }
                    }
                }
                offset= offset + line.length() + 1;
            }
            if ( haveIt==false ) {
                if ( addToLine>-1 ) {
                    doc.insertString( addAtOffset, ","+name, null );
                } else {
                    doc.insertString( 0, "from "+pkg+" import "+name + "\n", null );
                }
            }
            
        } catch (BadLocationException ex) {
            Logger.getLogger(JythonToJavaConverter.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }    
    
    /**
     * add the class to the list of imports.
     * @param src the Jython source
     * @param pkg the Java package
     * @param name the Java class name.
     * @return the new version of the script.
     */
    public static String addImport( String src, String pkg, String name ) {
        String[] ss= src.split("\n");
        Pattern p= Pattern.compile("from (.+) import (.*)");
        boolean haveIt=false;
        int addToLine= -1;
        for ( int i=0; i<ss.length; i++ ) {
            String line= ss[i];
            Matcher m= p.matcher(line);
            if ( m.matches() ) {
                if ( m.group(1).equals(pkg) ) {
                    String names= m.group(2);
                    String[] namess= names.split(",",-2);
                    for ( String n: namess ) {
                        if ( n.equals(name) ) {
                            haveIt= true;
                        }
                    }
                    if ( haveIt==false ) {
                        addToLine= i;
                    }
                }
            }
        }
        if ( haveIt==false ) {
            if ( addToLine>-1 ) {
                ss[addToLine]= ss[addToLine]+","+name;
                return String.join("\n",ss);
            } else {
                return "from "+pkg+" import "+name + "\n" + String.join("\n",ss);
            }
        } else {
            return src;
        }
        
    }    
    
    /**
     * return true if the class has been imported.  Note this is not thorough
     * and should be reviewed at some point.
     * @param src the Jython source
     * @param pkg the Java package
     * @param name the Java class name.
     * @return true if the class has been imported already.
     */
    public static boolean hasImport( String src, String pkg, String name ) {
        String[] ss= src.split("\n");
        Pattern p= Pattern.compile("from (.+) import (.*)");
        boolean haveIt=false;
        for ( int i=0; i<ss.length; i++ ) {
            String line= ss[i];
            Matcher m= p.matcher(line);
            if ( m.matches() ) {
                if ( m.group(1).equals(pkg) ) {
                    String names= m.group(2);
                    String[] namess= names.split(",",-2);
                    for ( String n: namess ) {
                        if ( n.equals(name) ) {
                            haveIt= true;
                        }
                    }
                }
            }
        }
        return haveIt;
    }
        
    
    private static int[] count( String line, char[] chrs ) {
        int[] result= new int[chrs.length];
        for ( int i=0; i<chrs.length; i++ ) {
            result[i]= 0;
        }
        for ( char c: line.toCharArray() ) {
            for ( int i=0; i<chrs.length; i++ ) {
                if ( c==chrs[i] ) {
                    result[i]++;
                }
            }
        }
        return result;
    }
    
    /**
     * The goal is to take Java snippets and turn them into Jython code.
     * This is all overly simplistic and should be done properly.  Cheesy!
     * 
     * More TODOs:
     * throw IllegalArgumentException -> raise exception
     * Character.isDigit -> string.isnumeric
     * "".startsWith -> "".startswith
     * "".trim() -> "".strip()
     * || -> or
     * && -> and
     * ! -> not
     * int[] d -> d
     * System.arraycopy -> for i in range(0,6): a[i]=a[i+6]
     * @param javaCode
     * @return conversion to Jython-like code.
     */
    public static String convertReverse(String javaCode) {
        String[] ss= javaCode.split("\n");
        StringBuilder b= new StringBuilder();
        Pattern assignPattern= Pattern.compile("([a-zA-Z.]*[A-Z]\\S+)(\\s+)(\\S+)(\\s*=.*)");
        Pattern importPattern1= Pattern.compile("import ([a-z\\.]*)\\.([A-Za-z\\*]*)");
        Pattern newPattern= Pattern.compile("(.*)([=\\s]*)?new\\s*([a-zA-Z\\.]+)(.*)");
        int indentLevel= 0;
        String indent="";
        boolean withinComment= false;
        
        ArrayList<String> importedPaths= new ArrayList<>();
        char[] chrs= new char[] { '(', ')', '{', '}' };
        int lineNumber= 0;
        for ( String s: ss ) {
            logger.log(Level.FINER, "line {0}: {1}", new Object[]{lineNumber, s});
            lineNumber++;
            
            String strim= s.trim();
            int javaIndent= strim.length()>1 ? s.indexOf(strim.substring(0,1)) : 0;
            s= strim;
            indentLevel= javaIndent;
            if ( indentLevel!=indent.length()) {
                indent= "                                                                       ".substring(0,indentLevel);
            }

            if ( s.endsWith(";") ) s= s.substring(0,s.length()-1);
            s= s.replaceAll("//","#");
            if ( s.startsWith("/*") ) withinComment= true;
            Matcher m= newPattern.matcher(s);
            if ( m.matches() ) {
                String clas= "import " + m.group(3);
                if ( !importedPaths.contains(clas) ) {
                    importedPaths.add(clas);
                }
                s= m.group(1) + m.group(2) + m.group(3) + m.group(4);
            }
            if ( s.contains("Short.") ) { // support Matisse
                String clas= "from java.lang import Short";
                if ( !importedPaths.contains(clas)) {
                    importedPaths.add(clas);
                }
            }

            s= s.replaceAll("null","None");
            s= s.replaceAll(" new "," " );
            s= s.replaceAll("throw", "raise");
            s= s.replaceAll("false","False");
            s= s.replaceAll("true","True");
            s= s.replaceAll("startsWith","startswith");
            s= s.replaceAll("endsWith","endswith");
            s= s.replaceAll("else if","elif");
            s= s.replaceAll("\\|\\|","or");
            s= s.replaceAll("\\&\\&","and");
            s= s.replaceAll("String.format\\((.*?),(.*)\\)", "$1 % \\($2\\)");
            s= s.replaceAll("public static final ([a-zA-Z0-9_]+)","# returns $1\n"+indent+"def");
            s= s.replaceAll("private static final ([a-zA-Z0-9_]+)","# returns $1\n"+indent+"def");
            s= s.replaceAll("public static ([a-zA-Z0-9_]+)","# returns $1\n"+indent+"def");
            s= s.replaceAll("private static ([a-zA-Z0-9_]+)","# returns $1\n"+indent+"def");
            s= s.replaceAll("for\\s+\\(\\s*int\\s+([a-z]+)\\s*=\\s*(\\d+)\\s*\\; \\s*\\1\\s*\\<\\s*(\\d+)\\;\\s*\\1\\+\\+\\s*\\)", "for $1 in xrange($2,$3)");
            s= s.replaceAll("\\.substring\\(([a-z\\+\\-\\.0-9]+\\s*)(,\\s*([a-z\\+\\-\\.0-9]+)\\s*)?\\)","[$1:$3]" );
            s= s.replaceAll("\\.substring\\(([a-z\\+\\-\\.0-9\\(\\)]+\\s*)(,\\s*([a-z\\+\\-\\.0-9]+)\\s*)?\\)","[$1:$3]" );
            s= s.replaceAll(".charAt\\(([a-z\\+\\-\\.0-9\\(\\)]+\\s*)\\)","[$1]" );
            s= s.replaceAll("([a-zA-Z0-9_]+).length\\(\\)","len($1)" );
            
            m= assignPattern.matcher(s);
            if ( m.matches() ) {
                s= m.group(3)+m.group(4);
            } else {
                m= importPattern1.matcher(s);
                if ( m.matches() ) {
                    s= "from "+m.group(1)+" import "+m.group(2);
                }
            }
            
            if ( s.contains("reformatIsoTime")) {
                System.err.println("Stop here jeremy");
            }
            
            if ( s.contains("{") ) {
                s= s.replace("{",":");
            } 
            if ( s.contains("}") ) {
                s= s.replace("}","");
            } 
            s= s.trim();
            b.append(indent);
            if ( withinComment ) {
                b.append("# ");
                if ( s.endsWith("*/") ) withinComment= false;
                if ( s.startsWith("/*") ) s= s.substring(2).trim();
                if ( s.startsWith("*/") ) s= s.substring(2).trim();
                if ( s.startsWith("*") ) s= s.substring(1).trim();
            }
            
            if ( s.startsWith("public static") && s.endsWith("{")) {
                s= s.substring(13).trim();
                int i= s.indexOf(" ");
                if ( i>0 ) {
                    s= s.substring(i).trim();
                }
                s= "def " + s;
            }
            b.append(s).append("\n");
            logger.log(Level.FINER, "out  {0}: {1}", new Object[]{lineNumber, s});
                        
        }
        StringBuilder sb= new StringBuilder();
        for ( String s : importedPaths ) {
            sb.append(s).append("\n");
        }
        if ( !importedPaths.isEmpty() ) sb.append("\n");
        sb.append(b);
        return sb.toString();
    }
    
    /**
     * return the Java type to use for the list.
     * @param list
     * @return 
     */
    public static String getJavaListType(  org.python.parser.ast.List list ) {
        if ( list.elts.length==0 ) {
            return "new Object[]";
        } else {
            Object o= list.elts[0];
            for ( int i=1; i<list.elts.length; i++ ) {
                if ( list.elts[i].getClass()!=o.getClass() ) {
                    return "new Object[]";
                } 
            }
            if ( o instanceof Num ) {
                Num n= (Num)o;
                if ( n.n instanceof PyInteger ) {
                    return "new int[]";
                } else if ( n.n instanceof PyFloat ) {
                    return "new double[]";
                } else {
                    return "new Number[]";
                }
            } else if ( o instanceof Str ) {
                return "new String[]";
            } else {
                return "new Object[]";
            }
        }
    }

    private static String getJavaExprType(exprType iter) {
        if ( iter instanceof Call ) {
            Call cc= (Call)iter;
            if ( cc.func instanceof Name ) {
                Name n= (Name)cc.func;
                if ( n.id.equals("range") ) {
                    return "int";
                } else if ( n.id.equals("xrange") ) {
                    return "int";
                } else if ( n.id.equals("getDataSet") ) {
                    return "QDataSet";
                } else if ( n.id.equals("getParam") ) {
                    if ( cc.args.length>1 ) {
                        exprType arg1= cc.args[1];
                        return getJavaExprType(arg1);
                    }      
                }
            }
        } else if ( iter instanceof Str ) {
            return "String";
        } else if ( iter instanceof Name ) {
            Name n= (Name)iter;
            if ( n.id.equals("False") || n.id.equals("True")) {
                return "boolean";
            } else {
                return "Object";
            }
        } else if ( iter instanceof Num ) {
            Num n= (Num)iter;
            if ( n.n instanceof PyFloat ) {
                return "float";
            } else if ( n.n instanceof PyInteger ) {
                return "int"; 
            } else {
                return "Number";
            }

        } else if ( iter instanceof Str ) {
            return "String";
        }
        return "Object";
    }
    
    private static class MyVisitorBase<R> extends VisitorBase {

        boolean looksOkay = true;
        boolean visitNameFail = false;

        StringBuilder builder;
        int lineNumber = 1;
        boolean includeLineNumbers = false;

        MyVisitorBase(StringBuilder builder) {
            this.builder = builder;
        }

        @Override
        public Object visitName(Name node) throws Exception {
            return super.visitName(node); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public Object visitCall(Call node) throws Exception {
            return super.visitCall(node); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        protected Object unhandled_node(SimpleNode sn) throws Exception {
            return sn;
        }

        @Override
        public void traverse(SimpleNode sn) throws Exception {
            traverse("", sn, false);
        }

        private static final Map<Integer,String> ops= new HashMap<>();
        static {
            ops.put( 1, "+" );
            ops.put( 2, "-" );
            ops.put( 3, "*" );
            ops.put( 4, "/" );
            ops.put( 6, "^" );
            ops.put( 12, "/floordiv/" );
        };
                
        private static final String spaces4 = "    ";

        private Map<String,String> targetTypes= new LinkedHashMap<>();

        public void traverse(String indent, SimpleNode sn, boolean inline) throws Exception {
            if (includeLineNumbers && (this.builder.length() == 0 || builder.charAt(this.builder.length() - 1) == '\n')) {
                this.builder.append(String.format("%04d: ", lineNumber));
            }
            while (sn.beginLine > lineNumber) {
                this.builder.append("\n");
                lineNumber++;
                if (includeLineNumbers) {
                    this.builder.append(String.format("%04d: ", lineNumber));
                }
            }

            if ( !inline ) this.builder.append(indent);

            //if ( lineNumber==4 ) {
            //    System.err.println("here line number breakpoint at line "+lineNumber );
            //}
            if (sn instanceof FunctionDef) {
                handleFunctionDef( (FunctionDef)sn, indent, inline );

            } else if (sn instanceof ClassDef ) {
                handleClassDef( (ClassDef)sn, indent, inline );

            } else if (sn instanceof Global) {
                Global g= (Global)sn;
                this.builder.append("// global ");
                for ( int i=0; i<g.names.length; i++ ){
                    if ( i>0 ) this.builder.append(",");
                    this.builder.append(g.names[i]);
                }

            } else if (sn instanceof Expr) {
                Expr ex = (Expr) sn;
                traverse("", ex.value, true);
            } else if (sn instanceof Print) {
                handlePrint( (Print)sn, indent, inline );
            } else if (sn instanceof Return) {
                Return rt = ((Return) sn);
                this.builder.append("return");
                if ( rt.value!=null ) {
                    this.builder.append(" ");
                    traverse("", rt.value, true);
                }
            } else if (sn instanceof ImportFrom) {
                ImportFrom ff = ((ImportFrom) sn);
                for (int i = 0; i < ff.names.length; i++) {
                    this.builder.append("import ").append(ff.module).append('.').append(ff.names[i].name);
                }
            } else if (sn instanceof Str) {
                Str ss = (Str) sn;
                this.builder.append("\"");
                String s= ss.s.replaceAll("\n", "\\\\n");
                this.builder.append(s);
                this.builder.append("\"");
            } else if (sn instanceof Num) {
                Num ex = (Num) sn;
                this.builder.append(ex.n);
            } else if (sn instanceof UnaryOp) {
                UnaryOp op= ((UnaryOp)sn);
                switch (op.op) {
                    case UnaryOp.UAdd:
                        this.builder.append("+");
                        traverse("",op.operand,true);
                        break;
                    case UnaryOp.USub:
                        this.builder.append("-");
                        traverse("",op.operand,true);
                        break;
                    default:
                        this.builder.append(op.toString());
                        break;
                }
            } else if (sn instanceof BinOp) {
                BinOp as = ((BinOp) sn);
                if (as.left instanceof Str && as.op == 5) {
                    this.builder.append("String.format(");
                    traverse("", as.left, true);
                    this.builder.append(",");
                    traverse("", as.right, true);
                    this.builder.append(")");
                } else {
                    traverse("", as.left, true);
                    String sop= ops.get(as.op);
                    if ( sop==null ) sop= " ?? ";
                    this.builder.append( sop );
                    traverse("", as.right, true);
                }
            } else if ( sn instanceof BoolOp ) {
                BoolOp as = ((BoolOp) sn);
                if ( as.op==1 ) {
                    this.builder.append("(");
                    traverse("", as.values[0], true);
                    this.builder.append(")");
                    for ( exprType o: Arrays.copyOfRange( as.values, 1, as.values.length ) ) {
                        this.builder.append(" && ");
                        this.builder.append("(");
                        traverse("", o, true);
                        this.builder.append(")");
                    }
                } else {
                    throw new IllegalArgumentException("not supported BoolOp as.op="+as.op);
                }
            } else if (sn instanceof Assign) {
                handleAssign( (Assign)sn, indent, inline );
                
            } else if (sn instanceof Name) {
                handleName( (Name)sn, indent, inline );

            } else if (sn instanceof Call) {
                handleCall( (Call)sn, indent, inline );

            } else if ( sn instanceof Index ) {
                Index id= (Index)sn;
                traverse("", id.value, true);
                
            } else if (sn instanceof For) {
                handleFor( (For)sn, indent, inline );

            } else if (sn instanceof While) {
                handleWhile( (While)sn, indent, inline );

            } else if (sn instanceof If) {
                handleIf( (If)sn, indent, inline );

            } else if (sn instanceof Compare) {
                Compare cp = (Compare) sn;
                traverse("", cp.left, true);
                for ( int i : cp.ops ) {
                    switch (i) {
                        case Compare.Gt:
                            this.builder.append(">");
                            break;
                        case Compare.GtE:
                            this.builder.append(">=");
                            break;
                        case Compare.Lt:
                            this.builder.append("<");
                            break;
                        case Compare.LtE:
                            this.builder.append("<=");
                            break;
                        case Compare.Eq:
                            this.builder.append("==");
                            break;
                        case Compare.NotEq:
                            this.builder.append("!=");
                            break;
                        default:
                            this.builder.append("?in?");
                            break;
                    }   
                }
                for (exprType t : cp.comparators) {
                    traverse("", t, inline);
                }
            } else if (sn instanceof Continue) {
                this.builder.append("continue");
            } else if (sn instanceof Raise) {
                Raise r= (Raise)sn;
                this.builder.append("throw ");
                traverse("", r.type, true );

            } else if (sn instanceof ExtSlice ) {
                ExtSlice r= (ExtSlice)sn;
                for ( int i=0; i<r.dims.length; i++ ) {
                    if ( i>0 ) this.builder.append(",");
                    sliceType st= r.dims[i];
                    traverse("", st, true );
                }
            } else if (sn instanceof Slice) {
                Slice s= (Slice)sn;
                this.builder.append( String.valueOf(s.lower)+":"+ String.valueOf(s.upper)+":"+ String.valueOf(s.step) );
            } else if (sn instanceof Subscript ) {
                Subscript s= (Subscript)sn;
                traverse( "", s.value, true );
                String t= getJavaExprType( s.value );
                if ( t.equals("Object") && ( s.value instanceof Name ) ) {
                    String n= ((Name)s.value).id ;
                    if ( targetTypes.containsKey(n))
                    t= targetTypes.get(n);
                }
                sliceType st= s.slice;
                if ( st instanceof Slice ) {
                    Slice slice= (Slice)st;
                    if ( t.equals("String") ) {
                        this.builder.append(".substring(");
                        traverse("",slice.lower,true);
                        this.builder.append(",");
                        traverse("",slice.upper,true);
                        this.builder.append(")");
                    } else {
                        traverse("",slice,true);
                    }
                } else if ( st instanceof Index ) {
                    this.builder.append("[");
                    traverse("",((Index)st).value,true);
                    this.builder.append("]");
                } else {
                    this.builder.append("[");
                    traverse("",st,true);
                    this.builder.append("]");
                }
            } else if (sn instanceof Attribute) {
                Attribute at = ((Attribute) sn);
                traverse("", at.value, true);
                this.builder.append(".");
                this.builder.append(at.attr);
            } else if ( sn instanceof org.python.parser.ast.List ) {
                org.python.parser.ast.List ll = ((org.python.parser.ast.List) sn);
                String open= getJavaListType( ll );
                this.builder.append(open);
                this.builder.append(" { ");
                
                for ( int i=0; i<ll.elts.length; i++ ) {
                    if ( i>0 ) this.builder.append(",");
                    traverse("", ll.elts[i], true);
                }
                this.builder.append(" } ");
            } else if ( sn instanceof org.python.parser.ast.Subscript ) {
                org.python.parser.ast.Subscript ss= (org.python.parser.ast.Subscript)sn;
                traverse( "", ss.value, true );
                this.builder.append("[");
                traverse( "", ss.slice, true );
                this.builder.append("]");
            } else if ( sn instanceof Tuple ) {
                org.python.parser.ast.Tuple ss= (org.python.parser.ast.Tuple)sn;
                for ( int i=0; i<ss.elts.length; i++ ) {
                    if ( i>0 ) this.builder.append(',');
                    traverse( "", ss.elts[i], true );
                }
            } else if ( sn instanceof AugAssign ) {
                AugAssign a1= (AugAssign)sn;
                switch (a1.op) {
                    case AugAssign.Add:
                        traverse("", a1.target, true);
                        this.builder.append("+=");
                        traverse("", a1.value, true);
                        break;
                    case AugAssign.Sub:
                        traverse("", a1.target, true);
                        this.builder.append("+=");
                        traverse("", a1.value, true);   
                        break;
                    default:
                        this.builder.append(sn.toString()).append("\n");
                        break;
                }
            } else {
                this.builder.append(sn.toString()).append("\n");
                lineNumber++;
            }
            
            if ( !inline ) {
                String ss= this.builder.toString().trim();
                if ( ss.charAt(ss.length()-1)=='}' ) {
                    this.builder.append("\n");
                } else {
                    this.builder.append(";\n");
                }
                lineNumber++;
            }
        }

        public boolean looksOkay() {
            return looksOkay;
        }

        /**
         * this contains a node whose name we can't resolve.
         *
         * @return
         */
        public boolean visitNameFail() {
            return visitNameFail;
        }

        private String guessType( exprType ex ) {
            return "Object";
        }
        
        private String guessReturnType( stmtType[] statements ) {
            String returnType= "void";
            for ( stmtType s : statements ) {
                if ( s instanceof If ) {
                    String s1= guessReturnType(((If)s).body);
                    if ( !s1.equals("void") ) {
                        returnType= s1;
                    }
                } else if ( s instanceof For ) {
                    String s1= guessReturnType(((For)s).body);
                    if ( !s1.equals("void") ) {
                        returnType= s1;
                    }
                } else if ( s instanceof Return ) {
                    return guessType( ((Return)s).value );
                }
            }
            return returnType;
        }
        
        private void handleFunctionDef( FunctionDef fd, String indent, boolean inline ) throws Exception {
            String returnType= guessReturnType(fd.body );
            this.builder.append("private ").append(returnType).append(" ").append(fd.name).append("(");
            for (int i = 0; i < fd.args.args.length; i++) {
                if (i > 0) {
                    this.builder.append(",");
                }
                traverse( "", fd.args.args[i], true );
            }
            this.builder.append(") {\n");
            lineNumber++;
            handleBody( fd.body, indent+spaces4 );
            this.builder.append(indent).append("}");
        }

        private void handlePrint( Print pr, String indent, boolean inline) throws Exception {
            this.builder.append("System.out.println(");
            for (int i = 0; i < pr.values.length; i++) {
                if (i > 0) {
                    this.builder.append(",");
                }
                traverse("", pr.values[i], true);
            }
            this.builder.append(")");
        }
        
        private void handleClassDef( ClassDef classDef, String indent, boolean inline) throws Exception {
            if ( classDef.bases.length>0 ) {
                this.builder.append("private class ").append(classDef.name).append(" extends ");
                traverse( indent, classDef.bases[0], true );
                this.builder.append(" {");
            } else {
                this.builder.append("private class ").append(classDef.name).append(" {");
            }
            handleBody(classDef.body, indent+spaces4 );
            this.builder.append("}");
        }

        private void handleAssign(Assign as, String indent, boolean inline ) throws Exception {
            if ( as.targets.length==1 && ( as.targets[0] instanceof Name ) )  {
                String typeOf1= targetTypes.get( ((Name)as.targets[0]).id );
                if ( typeOf1==null ) {
                    String typeOf= getJavaExprType( as.value );
                    this.builder.append(typeOf).append(" ");
                    targetTypes.put( ((Name)as.targets[0]).id, typeOf );
                }
            } 
            for (int i = 0; i < as.targets.length; i++) {
                if (i > 0) {
                    this.builder.append(",");
                }
                traverse("", as.targets[i], true);
            }
            this.builder.append(" = ");
            traverse("", as.value, true);

        }

        private void handleName(Name nn, String indent, boolean inline) {
            String name= nn.id;
            if ( name.equals("False") ) {
                this.builder.append("false");
            } else if ( name.equals("True") ) {
                this.builder.append("true");
            } else if ( name.equals("None") ) {
                this.builder.append("null");
            } else {
                this.builder.append(nn.id);
            }
        }

        private void handleCall(Call cc, String indent, boolean inline) throws Exception {
            if (cc.func instanceof Name) {
                if (Character.isUpperCase(((Name) cc.func).id.charAt(0))) {
                    this.builder.append("new").append(" ");
                }
            }
            traverse("", cc.func, true);
            this.builder.append("(");
            for (int i = 0; i < cc.args.length; i++) {
                if (i > 0) {
                    this.builder.append(",");
                }
                traverse("", cc.args[i], true);
            }
            this.builder.append(")");
            for ( int i=0; i<cc.keywords.length; i++ ) {
                this.builder.append(" //" );
                handleKeywordType( cc.keywords[i], true );
            }

        }
        
        private void handleKeywordType( keywordType kw, boolean inline ) {
            this.builder.append(kw.arg).append("=");
            try {
                traverse( "", kw.value, true );
            } catch (Exception ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }

        private void handleBody( stmtType[] body, String thisIndent ) throws Exception {
            for (int i = 0; i < body.length; i++) {
                traverse(thisIndent, body[i], false);
            }
        }

        private void handleFor(For ff, String indent, boolean inline) throws Exception {
            String typeOf= getJavaExprType( ff.iter );
            this.builder.append("for ( ").append(typeOf).append(" ");
            traverse("", ff.target, true);
            this.builder.append(" : ");
            traverse("", ff.iter, true);
            this.builder.append(" ) {\n");
            lineNumber++;
            handleBody(ff.body, spaces4+ indent );
            this.builder.append(indent).append("}\n");
            lineNumber++;
        }

        private void handleWhile(While ff, String indent, boolean inline) throws Exception {
            this.builder.append("while ( ");
            traverse( "", ff.test, true );
            this.builder.append(" ) {\n");
            lineNumber++;
            handleBody(ff.body, spaces4+ indent );
            this.builder.append(indent).append("}\n");
            lineNumber++;
        }

        private void handleIf(If ff, String indent, boolean inline) throws Exception {
            this.builder.append("if ( ");
            traverse("", ff.test, true);
            this.builder.append(" ) {\n");
            lineNumber++;
            handleBody(ff.body,spaces4+ indent ); 
            if ( ff.orelse==null ) {
                this.builder.append(indent).append("}");
            } else {
                this.builder.append(indent).append("} else {\n");
                lineNumber++;
                handleBody(ff.orelse, spaces4+ indent );
                this.builder.append(indent).append("}");
            }
        }

    }

    /**
     * convert Jython script to Java
     * @return Java attempt
     */
    public static String convert(String script) throws Exception {
        org.python.parser.ast.Module n = (org.python.parser.ast.Module) org.python.core.parser.parse(script, "exec");
        StringBuilder b = new StringBuilder();
        convert(b, n);
        return b.toString();
    }

    private static void convert(StringBuilder sb, org.python.parser.ast.Module n) throws Exception {
        VisitorBase vb = new MyVisitorBase(sb);
        n.traverse(vb);

    }

    public static void main(String[] args) throws Exception {
        String code;
        //String code= "def foo():\n  print 'hello'\nfoo()";
        //System.err.println( convert(code) );

        //String furi= "/home/jbf/project/autoplot/script/lookAtUserComments.jy";
        //String furi= "/home/jbf/project/autoplot/script/curveFitting.jy";
        String furi = "/home/jbf/project/autoplot/script/addLabelToPng.jy";

        File src = DataSetURI.getFile(furi, new NullProgressMonitor());

        try (FileReader reader = new FileReader(src)) {
            code = JythonUtil.readScript(new BufferedReader(reader));
            System.err.println(convert(code));
        }
    }

}
