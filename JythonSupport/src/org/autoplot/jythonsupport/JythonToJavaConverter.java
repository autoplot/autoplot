
package org.autoplot.jythonsupport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
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
import org.python.parser.ast.Assert;
import org.python.parser.ast.Assign;
import org.python.parser.ast.Attribute;
import org.python.parser.ast.AugAssign;
import org.python.parser.ast.BinOp;
import org.python.parser.ast.BoolOp;
import org.python.parser.ast.Break;
import org.python.parser.ast.Call;
import org.python.parser.ast.ClassDef;
import org.python.parser.ast.Compare;
import org.python.parser.ast.Continue;
import org.python.parser.ast.Dict;
import org.python.parser.ast.Expr;
import org.python.parser.ast.ExtSlice;
import org.python.parser.ast.For;
import org.python.parser.ast.FunctionDef;
import org.python.parser.ast.Global;
import org.python.parser.ast.If;
import org.python.parser.ast.Import;
import org.python.parser.ast.ImportFrom;
import org.python.parser.ast.Index;
import org.python.parser.ast.ListComp;
import org.python.parser.ast.Name;
import org.python.parser.ast.Num;
import org.python.parser.ast.Pass;
import org.python.parser.ast.Print;
import org.python.parser.ast.Raise;
import org.python.parser.ast.Return;
import org.python.parser.ast.Slice;
import org.python.parser.ast.Str;
import org.python.parser.ast.Subscript;
import org.python.parser.ast.TryExcept;
import org.python.parser.ast.Tuple;
import org.python.parser.ast.UnaryOp;
import org.python.parser.ast.VisitorBase;
import org.python.parser.ast.While;
import org.python.parser.ast.aliasType;
import org.python.parser.ast.excepthandlerType;
import org.python.parser.ast.exprType;
import org.python.parser.ast.keywordType;
import org.python.parser.ast.listcompType;
import org.python.parser.ast.sliceType;
import org.python.parser.ast.stmtType;

/**
 * experiment with code which converts the Jython AST (syntax tree) into Java
 * code.  See also https://cottagesystems.com/JavaJythonConverter/ which goes
 * the other way.
 *
 * @author jbf
 */
public class JythonToJavaConverter {

    private static final Logger logger= LoggerManager.getLogger("jython");
    
    private static Map<String,String> packages= null;
    
    public static String TYPE_INT="int";
    
    public static String TYPE_FLOAT="float";
    
    public static String TYPE_STRING="String";
    
    public static String TYPE_STRING_ARRAY="String[]";
    
    public static String TYPE_OBJECT="Object";
    
    public static String TYPE_MAP="Map";
    
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
    
    private static class Context {
        Map<String,String> names= new HashMap<>();
    }
    
    private static class MyVisitorBase<R> extends VisitorBase {

        boolean looksOkay = true;
        boolean visitNameFail = false;

        StringBuilder builder;
        int lineNumber = 1;
        boolean includeLineNumbers = false;

        // when a Return is encountered, make a note of the return type.
        String lastReturnType= "";
        
        Stack<Context> contexts= new Stack<>();

        MyVisitorBase(StringBuilder builder) {
            this.builder= builder;
            contexts.push( new Context() );
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
            StringBuilder builder= this.builder;
            traverse( builder,"", sn, false);
        }

        private static final Map<Integer,String> ops= new HashMap<>();
        static {
            ops.put( 1, "+" );
            ops.put( 2, "-" );
            ops.put( 3, "*" );
            ops.put( 4, "/" );
            ops.put( 5, "%" );
            ops.put( 9, "|" );
            ops.put( 10, "^" );
            ops.put( 11, "&" );
            ops.put( 12, "/floordiv/" );
        };
                
        private static final String spaces4 = "    ";

        public void traverse( StringBuilder builder, String indent, SimpleNode sn, boolean inline) throws Exception {
            if (includeLineNumbers && (builder.length() == 0 || builder.charAt(builder.length() - 1) == '\n')) {
                builder.append(String.format("%04d: ", lineNumber));
            }
//            while ( !(sn instanceof  TryExcept) && sn.beginLine > lineNumber) {
//                builder.append("\n");
//                lineNumber++;
//                if (includeLineNumbers) {
//                    builder.append(String.format("%04d: ", lineNumber));
//                }
//            }
            
            if ( !inline ) {
                boolean endsWithIndent= 
                        builder.indexOf( indent, builder.length()-indent.length() ) >-1;
                if (!endsWithIndent) {
                    builder.append(indent);
                }
            }

            //if ( lineNumber==4 ) {
            //    System.err.println("here line number breakpoint at line "+lineNumber );
            //}
            if (sn instanceof FunctionDef) {
                handleFunctionDef( builder,(FunctionDef)sn, indent, inline );

            } else if (sn instanceof ClassDef ) {
                handleClassDef(builder, (ClassDef)sn, indent, inline );

            } else if (sn instanceof Global) {
                Global g= (Global)sn;
                builder.append("// global ");
                for ( int i=0; i<g.names.length; i++ ){
                    if ( i>0 ) builder.append(",");
                    builder.append(g.names[i]);
                }
            } else if (sn instanceof Break ) {
                builder.append("break");
            } else if (sn instanceof Expr) {
                Expr ex = (Expr) sn;
                traverse(builder,"", ex.value, true);
            } else if (sn instanceof Print) {
                handlePrint( builder, (Print)sn, indent, inline );
            } else if (sn instanceof Return) {
                Return rt = ((Return) sn);
                builder.append("return");
                if ( rt.value!=null ) {
                    builder.append(" ");
                    traverse(builder,"", rt.value, true);
                    lastReturnType= guessType(rt.value);
                }
                
            } else if (sn instanceof ImportFrom) {
                ImportFrom ff = ((ImportFrom) sn);
                for (int i = 0; i < ff.names.length; i++) {
                    builder.append("import ").append(ff.module).append('.').append(ff.names[i].name);
                    if ( i<ff.names.length-1 ) builder.append(";\n");
                }
            } else if (sn instanceof Str) {
                Str ss = (Str) sn;
                builder.append("\"");
                String s= ss.s.replaceAll("\n", "\\\\n");
                builder.append(s);
                builder.append("\"");
            } else if (sn instanceof Num) {
                Num ex = (Num) sn;
                builder.append(ex.n);
            } else if (sn instanceof UnaryOp) {
                UnaryOp op= ((UnaryOp)sn);
                switch (op.op) {
                    case UnaryOp.UAdd:
                        builder.append("+");
                        traverse(builder,"",op.operand,true);
                        break;
                    case UnaryOp.USub:
                        builder.append("-");
                        traverse(builder,"",op.operand,true);
                        break;
                    case UnaryOp.Not:
                        builder.append("!");
                        traverse(builder,"",op.operand,true);
                        break;              
                    case UnaryOp.Invert:
                        builder.append("~");
                        traverse(builder,"",op.operand,true);
                        break;                                
                    default:
                        builder.append(op.toString());
                        break;
                }
            } else if (sn instanceof BinOp) {
                BinOp as = ((BinOp) sn);
                if (as.left instanceof Str && as.op == 5) {
                    builder.append("String.format(");
                    traverse(builder,"", as.left, true);
                    builder.append(",");
                    traverse(builder,"", as.right, true);
                    builder.append(")");
                } else if ( as.left instanceof Str && as.op==3 && as.right instanceof Num ) { // '#'*50
                    builder.append("new StringBuilder().repeat(");
                    traverse(builder,"", as.left, true);
                    builder.append(",");
                    traverse(builder,"", as.right, true);
                    builder.append(")");
                } else if ( as.op==6 ) {
                    builder.append("Math.pow(");
                    traverse(builder,"", as.left, true);
                    builder.append(",");
                    traverse(builder,"", as.right, true);
                    builder.append(")");
                } else {
                    traverse(builder,"", as.left, true);
                    String sop= ops.get(as.op);
                    if ( sop==null ) {
                        sop= " ?? ";
                    }
                    builder.append( sop );
                    traverse(builder,"", as.right, true);
                }
            } else if ( sn instanceof BoolOp ) {
                BoolOp as = ((BoolOp) sn);
                if ( as.op==1 || as.op==2 ) {
                    String opstr;
                    switch ( as.op ) {
                        case 1: opstr=" && "; break;
                        case 2: opstr=" || "; break;
                        default: throw new UnsupportedOperationException("operator is not supported: "+ as);
                    }
                        
                    builder.append("(");
                    traverse(builder,"", as.values[0], true);
                    builder.append(")");
                    for ( exprType o: Arrays.copyOfRange( as.values, 1, as.values.length ) ) {
                        builder.append(opstr);
                        builder.append("(");
                        traverse(builder,"", o, true);
                        builder.append(")");
                    }
                } else {
                    throw new IllegalArgumentException("not supported BoolOp as.op="+as.op);
                }
            } else if (sn instanceof Assign) {
                handleAssign(builder, (Assign)sn, indent, inline );
            } else if ( sn instanceof Assert ) {
                Assert a= (Assert)sn;
                if (a.test instanceof Call) {
                    org.python.parser.ast.Call cc = (org.python.parser.ast.Call) a.test;
                    exprType f = cc.func;
                    if (f instanceof Name) {
                        if (((Name) f).id.equals("isinstance")) {
                            if (cc.args.length == 2) {
                                exprType a1 = cc.args[0];
                                if (a1 instanceof Name ) {
                                    exprType a2 = cc.args[1];
                                    if ( a2 instanceof Name ) {
                                        if ( ((Name) a2).id.equals("str") ) {
                                            assertType( ((Name) a1).id, "String" );
                                        } else {
                                            assertType( ((Name) a1).id, ((Name) a2).id );
                                        }
                                        return;
                                    }
                                }
                            }
                        }
                    }   
                }                
            } else if (sn instanceof Name) {
                handleName( builder,(Name)sn, indent, inline );

            } else if (sn instanceof Call) {
                handleCall( builder,(Call)sn, indent, inline );

            } else if ( sn instanceof Index ) {
                Index id= (Index)sn;
                traverse(builder,"", id.value, true);
                
            } else if (sn instanceof For) {
                handleFor( builder,(For)sn, indent, inline );

            } else if (sn instanceof While) {
                handleWhile(builder, (While)sn, indent, inline );

            } else if (sn instanceof If) {
                handleIf( builder,(If)sn, indent, inline );

            } else if (sn instanceof Compare) {
                Compare cp = (Compare) sn;
                if ( cp.ops.length==1 ) {
                    if ( cp.ops[0]==Compare.In && cp.comparators.length==1 ) {
                        String t1= guessType(cp.left);
                        String t2= guessType(cp.comparators[0]);
                        if ( t1==TYPE_STRING && t2==TYPE_STRING ) {                        
                            traverse(builder,"",cp.comparators[0],true);
                            builder.append(".contains(");
                            traverse(builder,"",cp.left,true);
                            builder.append(")");
                            return;
                        } else if ( t1==TYPE_STRING && t2.equals(TYPE_STRING_ARRAY) ) {
                            traverse(builder,"",cp.comparators[0],true);
                            builder.append(".contains(");
                            traverse(builder,"",cp.left,true);
                            builder.append(")");
                            return;
                        }
                    }
                }
                
                if ( cp.ops[0]!=Compare.In ) {
                    traverse(builder,"", cp.left, true);
                }
                
                for ( int i : cp.ops ) {
                    switch (i) {
                        case Compare.Gt:
                            builder.append(">");
                            break;
                        case Compare.GtE:
                            builder.append(">=");
                            break;
                        case Compare.Lt:
                            builder.append("<");
                            break;
                        case Compare.LtE:
                            builder.append("<=");
                            break;
                        case Compare.Eq:
                            builder.append("==");
                            break;
                        case Compare.NotEq:
                            builder.append("!=");
                            break;                       
                        case Compare.In:
                            if ( cp.comparators.length==1 && guessType( cp.comparators[0] )==TYPE_MAP ) {
                                traverse(builder,"", cp.comparators[0], inline);
                                builder.append(".containsKey(");
                                traverse(builder,"", cp.left, inline);
                                builder.append(")");
                            } else {
                                traverse(builder,"", cp.left, inline);
                                builder.append("<in>");
                                for (exprType t : cp.comparators) {
                                    traverse(builder,"", t, inline);
                                }
                            }
                            break;
                        case Compare.Is:
                            builder.append(" instanceof ");
                            break;
                        default:
                            builder.append("?<>?");
                            break;
                    }   
                }
                if ( cp.ops[0]!=Compare.In ) {
                    for (exprType t : cp.comparators) {
                        traverse(builder,"", t, inline);
                    }
                }
            } else if (sn instanceof Continue) {
                builder.append("continue");
            } else if (sn instanceof Raise) {
                Raise r= (Raise)sn;
                builder.append("throw ");
                traverse(builder,"", r.type, true );

            } else if (sn instanceof ExtSlice ) {
                ExtSlice r= (ExtSlice)sn;
                for ( int i=0; i<r.dims.length; i++ ) {
                    if ( i>0 ) builder.append(",");
                    sliceType st= r.dims[i];
                    traverse(builder,"", st, true );
                }
            } else if (sn instanceof Slice) {
                Slice s= (Slice)sn;
                builder.append( "[" + String.valueOf(s.lower)+":"+ String.valueOf(s.upper)+":"+ String.valueOf(s.step) + "]" );
            } else if (sn instanceof Subscript ) {
                handleSubscript( builder,(Subscript)sn, indent, inline );
            } else if (sn instanceof Attribute) {
                Attribute at = ((Attribute) sn);
                String type= guessType(at.value);
                if ( type.equals("String") ) {
                    if ( at.attr.equals("strip") ) {
                        traverse(builder,"", at.value, true);
                        builder.append(".");
                        builder.append("trim");
                        return;
                    } else if ( at.attr.equals("split") ) {
                        traverse(builder,"", at.value, true);
                        builder.append(".");
                        builder.append("trim().split(\"\\\\s+\")");
                        return;
                    } else if ( at.attr.equals("splitlines") ) {
                        traverse(builder,"", at.value, true);
                        builder.append(".");
                        builder.append("split(\"\\n\")");
                        return;
                    } else if ( at.attr.equals("find") ) {
                        traverse(builder,"", at.value, true);
                        builder.append(".");
                        builder.append("indexOf");
                        return;
                    } else if ( at.attr.equals("startswith") ) {
                        traverse(builder,"", at.value, true);
                        builder.append(".");
                        builder.append("startsWith");
                        return;
                    } else if ( at.attr.equals("endswith") ) {
                        traverse(builder,"", at.value, true);
                        builder.append(".");
                        builder.append("endsWith");
                        return;
                    }
                } else {
                    if ( at.attr.equals("strip") && ( at.value instanceof Name ) ) {
                        traverse(builder,"", at.value, true);
                        builder.append(".");
                        builder.append("trim");
                        assertType(((Name)at.value).id, TYPE_STRING );
                        return;
                    } 
                }
                traverse(builder,"", at.value, true);
                builder.append(".");
                builder.append(at.attr);
            } else if ( sn instanceof org.python.parser.ast.List ) {
                org.python.parser.ast.List ll = ((org.python.parser.ast.List) sn);
                String open= getJavaListType( ll );
                builder.append(open);
                builder.append(" { ");
                
                for ( int i=0; i<ll.elts.length; i++ ) {
                    if ( i>0 ) builder.append(",");
                    traverse(builder,"", ll.elts[i], true);
                }
                builder.append(" } ");
            } else if ( sn instanceof org.python.parser.ast.Subscript ) {
                org.python.parser.ast.Subscript ss= (org.python.parser.ast.Subscript)sn;
                traverse( builder,"", ss.value, true );
                builder.append("[");
                traverse( builder,"", ss.slice, true );
                builder.append("]");
            } else if ( sn instanceof Tuple ) {
                org.python.parser.ast.Tuple ss= (org.python.parser.ast.Tuple)sn;
                for ( int i=0; i<ss.elts.length; i++ ) {
                    if ( i>0 ) builder.append(',');
                    traverse( builder,"", ss.elts[i], true );
                }
            } else if ( sn instanceof AugAssign ) {
                AugAssign a1= (AugAssign)sn;
                switch (a1.op) {
                    case AugAssign.Add:
                        traverse(builder,"", a1.target, true);
                        builder.append("+=");
                        traverse(builder,"", a1.value, true);
                        break;
                    case AugAssign.Sub:
                        traverse(builder,"", a1.target, true);
                        builder.append("+=");
                        traverse(builder,"", a1.value, true);   
                        break;
                    default:
                        builder.append(sn.toString()).append("\n");
                        break;
                }
            } else if( sn instanceof TryExcept ) {
                TryExcept te= (TryExcept)sn;
                System.err.println(""+indent.length()+" length");
                builder.append(indent).append("try {\n");
                handleBody( builder, te.body, indent+spaces4 );
                builder.append(indent).append("} catch ( Exception e ) {").append("\n");
                for ( int i=0; i<te.handlers.length; i++ ) {
                    if ( te.handlers[i] instanceof excepthandlerType ) {
                        handleBody( builder,((excepthandlerType)te.handlers[i]).body, indent + spaces4 );
                        if ( te.handlers.length>1 ) {
                            builder.append( "not sure line830");
                        }
                    } else {
                        builder.append( "not sure line833");
                    }
                }
                if ( te.orelse==null ) {
                    
                } else {
                    builder.append( "not sure line839");
                    handleBody(builder,te.orelse, indent+spaces4 );
                    builder.append( "not sure line832");
                }
                builder.append(indent).append("}");
            } else if ( sn instanceof Import ) {
                Import imp= (Import)sn;
                builder.append( indent ).append( "import " );
                for ( aliasType a: imp.names ) {
                    traverse( builder,"", a, true );
                    builder.append(",");
                }
            } else if ( sn instanceof ImportFrom ) {
                ImportFrom imp= (ImportFrom)sn;
                builder.append( indent ).append( "import " ) .append( imp.module );
            } else if ( sn instanceof aliasType ) {
                aliasType at= (aliasType)sn;
                builder.append( indent ).append( at.name );
            } else if ( sn instanceof Pass ) {
                builder.append( indent ).append( "//pass" );
            } else if ( sn instanceof Dict ) {
                builder.append( indent ).append( "new HashMap<>()" );
            } else if ( sn instanceof ListComp ) {
                handleListComp(builder, (ListComp)sn, indent, inline);
            } else {
                builder.append(sn.toString()).append("\n");
                lineNumber++;
            }
            
            if ( !inline ) {
                String ss= builder.toString().trim();
                if ( ss.charAt(ss.length()-1)=='}' ) {
                    builder.append("\n");
                } else {
                    builder.append(";\n");
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

        /**
         * return the type for the name, if the name is known, or null.  This
         * climbs up the stack of contexts, looking for the name
         * @param name
         * @return 
         */
        private String getTypeForName( String name ) {
            for ( int i=contexts.size()-1; i>=0; i-- ) {
                Context c= contexts.get(i);
                String ss= c.names.get(name);
                if ( ss!=null ) {
                    return ss;
                }
            }
            return null;
        }
        
        /**
         * return "String" or "Object
         * @param ex
         * @return 
         */
        private String guessType( exprType ex ) {
            if ( ex instanceof Str ) {
                return TYPE_STRING;
            } else if ( ex instanceof Num ) {
                Num n1= ((Num)ex);
                if ( n1.n instanceof PyInteger ) {
                    return TYPE_INT;
                } else {
                    return TYPE_FLOAT;
                }
            } else if ( ex instanceof Name ) {
                String s= getTypeForName(((Name)ex).id);
                if ( s==null ) {
                    //TODO: static
                    return TYPE_OBJECT;
                } else {
                    return s;
                }
            } else if ( ex instanceof Call ) {
                Call call= (Call)ex;
                if ( call.func instanceof Attribute ) {
                    Attribute attr= (Attribute)call.func;
                    String type= guessType( attr.value );
                    if ( type==TYPE_STRING ) {
                        if ( attr.attr.equals("find") ) {
                            return TYPE_INT;
                        } else if ( attr.attr.equals("substring") ) {
                            return TYPE_STRING;
                        } else if ( attr.attr.equals("strip") ) {
                            return TYPE_STRING;
                        } else if ( attr.attr.equals("splitlines") ) {
                            return TYPE_STRING_ARRAY;
                        }
                    }
                } else if ( call.func instanceof Name ) {
                    Name n= (Name)call.func;
                    if ( n.id.equals("len") ) {
                        return TYPE_INT;
                    }
                }
                return TYPE_OBJECT;
            } else if ( ex instanceof BinOp ) {
                BinOp bo= (BinOp)ex;
                String t1= guessType( bo.left );
                String t2= guessType( bo.right );
                if ( t1==TYPE_INT && t2==TYPE_INT ) {
                    return TYPE_INT;
                } else if ( t1==TYPE_STRING && t2==TYPE_STRING ) {
                    return TYPE_STRING;
                } else {
                    return TYPE_OBJECT;
                }
            } else if ( ex instanceof Subscript ) {
                return guessType( ((Subscript)ex).value );
                        
            } else {
                return TYPE_OBJECT;
            }
        }
        
        /**
         * this returns the type of objects the iterator iterates over.
         * @param iter
         * @return 
         */
        private String getJavaIterExprType(exprType iter) {
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
                } else if ( cc.func instanceof Attribute ) {
                    Attribute att= (Attribute)cc.func;
                    if ( att.value instanceof Name ) {
                        Name n= (Name)att.value;
                        String ftype= getJavaExprType(n); // ideally this would be file
                        if ( att.attr.equals("readlines") ) {
                            return TYPE_STRING;
                        } else if ( att.attr.equals("splitlines") ) {
                            return TYPE_STRING;
                        }
                    }

                }
            }
            return "Object";
        }

        /**
         * there's an issue here where for iter==Call, this is returning the
         * object which the iterator returns.
         * @param iter
         * @return 
         */
        private String getJavaExprType(exprType iter) {
            if ( iter instanceof Call ) {
                Call cc= (Call)iter;
                if ( cc.func instanceof Name ) {
                    Name n= (Name)cc.func;
                    if ( n.id.equals("int") ) {
                        return TYPE_INT;
                    } else if ( n.id.equals("range") ) {
                        return "Stream<int>";
                    } else if ( n.id.equals("xrange") ) {
                        return "Stream<int>";
                    } else if ( n.id.equals("getDataSet") ) {
                        return "QDataSet";
                    } else if ( n.id.equals("downloadResourceAsTempFile") ) {
                        return "File";
                    } else if ( n.id.equals("getParam") ) {
                        if ( cc.args.length>1 ) {
                            exprType arg1= cc.args[1];
                            return getJavaExprType(arg1);
                        }      
                    } else if ( Character.isUpperCase(n.id.charAt(0)) ) {
                        return n.id; // assume it's a constructor
                    } else if ( n.id.equals("open") ) {
                        return "Stream<String>";
                    }
                } else if ( cc.func instanceof Attribute ) { 
                    Attribute attr= (Attribute)cc.func;
                    String staticClass= "";
                    if ( attr.value instanceof Name ) {
                        if ( Character.isUpperCase(((Name)attr.value).id.charAt(0)) ) {
                            staticClass= ((Name)attr.value).id;
                        }
                    }
                    if (attr instanceof Attribute ) {
                        Attribute attr2= (Attribute)attr;
                        if ( attr2.attr.equals("strip") ) {
                            return TYPE_STRING;
                        } else if ( attr2.attr.equals("split") ) {
                            return TYPE_STRING_ARRAY;
                        }
                    }
                    if ( staticClass.equals("FileUtil") && attr.attr.equals("readFileToString") ) {
                        return TYPE_STRING;
                    }
                }
            } else if ( iter instanceof Str ) {
                return TYPE_STRING;
            } else if ( iter instanceof Name ) {
                Name n= (Name)iter;
                if ( n.id.equals("False") || n.id.equals("True")) {
                    return "boolean";
                } else {
                    return TYPE_OBJECT;
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
            } else if ( iter instanceof org.python.parser.ast.List ) {
                org.python.parser.ast.List l = (org.python.parser.ast.List)iter;
                String t0=null;
                for ( exprType e1 : l.elts ) {
                    String t1= getJavaExprType(e1);
                    if ( t0==null ) {
                        t0= t1;
                    } else if ( !t0.equals(t1) ) {
                        return "Object[]";
                    }
                }
                if ( t0==null ) {
                    return "Object[]";
                } else {
                    return t0+"[]";
                }
            } else if ( iter instanceof Subscript ) {
                Subscript s= (Subscript)iter;
                String type= guessType(s.value);
                if ( type.equals( TYPE_OBJECT ) ) {
                    type= getJavaExprType( s.value );
                }
                if ( type.endsWith("[]") ) {
                    return type.substring(0,type.length()-2).intern();
                } else {
                    return TYPE_OBJECT;
                }
            } else if ( iter instanceof Dict ) {
                return TYPE_MAP;
            }
            return "Object";
        }
        
        
        /**
         * assert that the symbol has the name
         * @param name
         * @param type type, including String or Stream&lt;String&gt;
         */
        private void assertType( String name, String type ) {
            contexts.peek().names.put( name, type );
        }
        
        private String guessReturnType( stmtType[] statements ) {
            this.lastReturnType= "Object";
            StringBuilder dummy= new StringBuilder();
            try {
                handleBody(dummy,statements,"");
            } catch (Exception ex) {
                Logger.getLogger(JythonToJavaConverter.class.getName()).log(Level.SEVERE, null, ex);
            }
            return this.lastReturnType;
        }
        
        private void handleFunctionDef( StringBuilder builder, FunctionDef fd, String indent, boolean inline ) throws Exception {
            String returnType= guessReturnType(fd.body );
            // check for single-line documentation string
            if ( fd.body.length>0 && fd.body[0] instanceof Expr ) {
                Expr expr= (Expr)fd.body[0];
                if ( expr.value instanceof Str ) {
                    builder
                            .append(indent).append("/**\n")
                            .append(indent).append(" * ").append(((Str)expr.value).s).append("\n")
                            .append(indent).append(" */\n");
                    fd.body= Arrays.copyOfRange( fd.body, 1, fd.body.length );
                }
            }
            builder.append("private ").append(returnType).append(" ").append(fd.name).append("(");
            for (int i = 0; i < fd.args.args.length; i++) {
                if (i > 0) {
                    builder.append(",");
                }
                traverse( builder,"", fd.args.args[i], true );
            }
            builder.append(") {\n");
            lineNumber++;
            handleBody(builder, fd.body, indent+spaces4 );
            builder.append(indent).append("}");
        }

        private void handlePrint( StringBuilder builder, Print pr, String indent, boolean inline) throws Exception {
            builder.append("System.out.println(");
            for (int i = 0; i < pr.values.length; i++) {
                if (i > 0) {
                    builder.append(",");
                }
                traverse(builder,"", pr.values[i], true);
            }
            builder.append(")");
        }
        
        private void handleClassDef( StringBuilder builder, ClassDef classDef, String indent, boolean inline) throws Exception {
            if ( classDef.bases.length>0 ) {
                builder.append("private class ").append(classDef.name).append(" extends ");
                traverse( builder, indent, classDef.bases[0], true );
                builder.append(" {");
            } else {
                builder.append("private class ").append(classDef.name).append(" {");
            }
            handleBody( builder,classDef.body, indent+spaces4 );
            builder.append("}");
        }

        private void handleAssign(StringBuilder builder, Assign as, String indent, boolean inline) throws Exception {
            logger.log(Level.FINE, "handleAssign at {0}", as.beginLine);
            if ( as.targets[0] instanceof Subscript && as.targets.length==1 )  { // is this an assignment to a dictionary
                Subscript subscript = (Subscript)as.targets[0];
                if ( subscript.value instanceof Name ) {
                    Name name= (Name)subscript.value;
                    if ( guessType( subscript.value ).equals("Map") ) {
                        builder.append(indent).append(name.id).append(".put(");
                        traverse(builder,"", subscript.slice, true);
                        builder.append(indent).append(",");
                        traverse(builder,"", as.value, true);
                        builder.append(indent).append(")");
                        return;
                    }
                }
            }
            if ( as.targets.length==1 && ( as.targets[0] instanceof Name ) )  {
                String typeOf1= guessType( ((Name)as.targets[0]) );
                if ( typeOf1==null || typeOf1==TYPE_OBJECT ) {
                    String typeOf= getJavaExprType( as.value );
                    if ( typeOf==TYPE_OBJECT ) {
                        typeOf=guessType(as.value);
                    }
                    builder.append(typeOf).append(" ");
                    assertType( ((Name)as.targets[0]).id, typeOf );
                }
            }
            for (int i = 0; i < as.targets.length; i++) {
                if (i > 0) {
                    builder.append(",");
                }
                traverse(builder,"", as.targets[i], true);
            }
            builder.append(" = ");
            traverse(builder,"", as.value, true);

        }
        
        private void handleListComp( StringBuilder builder, ListComp lc, String indent, boolean inline) throws Exception {
            if ( lc.generators.length==1 ) {
                if ( lc.generators[0] instanceof listcompType ) {                    
                    listcompType lct= (listcompType)lc.generators[0];
                    if ( lct.ifs.length==0 ) {
                        traverse(builder,"", lct.iter, true );
                        builder.append(".stream().map( ");
                        traverse(builder,"", lct.target, true );
                        builder.append(" -> ");
                        traverse(builder,"", lc.elt, true );
                        builder.append(").collect(Collectors.toList())");
                        return;
                    }
                }
            } 
            builder.append(lc.toString());
        }

        private void handleName(StringBuilder builder, Name nn, String indent, boolean inline) {
            String name= nn.id;
            if ( name.equals("False") ) {
                builder.append("false");
            } else if ( name.equals("True") ) {
                builder.append("true");
            } else if ( name.equals("None") ) {
                builder.append("null");
            } else {
                builder.append(nn.id);
            }
        }

        private void handleCall(StringBuilder builder, Call cc, String indent, boolean inline) throws Exception {
            if (cc.func instanceof Name) {
                String name = ((Name) cc.func).id;
                if (Character.isUpperCase(name.charAt(0))) {
                    builder.append("new").append(" ");
                } else if ( name.equals("int") ) {
                    builder.append("(int)(");
                    traverse(builder,"",cc.args[0],true);
                    builder.append(")");
                    return;
                }
            } else if ( cc.func instanceof Attribute ) { // static method
                exprType clas= ((Attribute) cc.func).value;
                String method = ((Attribute) cc.func).attr;
                if ( clas instanceof Name ) {
                    String n= ((Name)clas).id;
                    if ( n.equals("struct") ) {
                        if ( method.equals("pack") ) {
                            System.err.println( "struct.pack could be implemented with ByteBuffer maybe" );
                        }
                    }
                }
            }
            traverse(builder,"", cc.func, true);
            builder.append("(");
            for (int i = 0; i < cc.args.length; i++) {
                if (i > 0) {
                    builder.append(",");
                }
                traverse(builder,"", cc.args[i], true);
            }
            builder.append(")");
            for ( int i=0; i<cc.keywords.length; i++ ) {
                builder.append(" //" );
                handleKeywordType( builder, cc.keywords[i], true );
            }

        }
        
        private void handleKeywordType( StringBuilder builder, keywordType kw, boolean inline ) {
            builder.append(kw.arg).append("=");
            try {
                traverse( builder,"", kw.value, true );
            } catch (Exception ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }

        private void handleBody( StringBuilder builder, stmtType[] body, String thisIndent ) throws Exception {
            contexts.push( new Context() );
            for (int i = 0; i < body.length; i++) {
                traverse(builder,thisIndent, body[i], false);
            }
            contexts.pop( );
        }

        private void handleFor(StringBuilder builder, For ff, String indent, boolean inline) throws Exception {
            logger.log(Level.FINE, "handleFor at {0}", ff.beginLine);
            String typeOf= getJavaIterExprType( ff.iter );
            if ( typeOf==TYPE_OBJECT ) {
                String t= guessType( ff.iter );
                if ( t==TYPE_STRING_ARRAY ) {
                    typeOf= TYPE_STRING;
                }
            }
            if ( ff.target instanceof Name ) {
                assertType( ((Name)ff.target).id, typeOf ); 
            }
            if ( ff.iter instanceof Call ) {
                Call c= (Call)ff.iter;
                if (c.func instanceof Name ) {
                    Name n= ((Name)c.func);
                    if ( n.id.equals("xrange") ) {
                        builder.append("for ( ").append(typeOf).append(" ");
                        traverse(builder,"", ff.target, true);
                        if ( c.args.length==1 ) {
                            builder.append("=").append("0; " );
                            traverse(builder,"", ff.target, true);
                            builder.append("<");
                            traverse(builder,"", c.args[0], true);
                            builder.append("; ");
                            traverse(builder,"", ff.target, true);
                            builder.append("++ ) {\n");
                            handleBody(builder,ff.body, spaces4+ indent );
                            builder.append(indent).append("}\n");
                            return;
                        } else if ( c.args.length==2 ) {
                            builder.append("=");
                            traverse(builder,"", c.args[0], true);
                            builder.append("; " );
                            traverse(builder,"", ff.target, true);
                            builder.append("<");
                            traverse(builder,"", c.args[1], true);
                            builder.append("; ");
                            traverse(builder,"", ff.target, true);
                            builder.append("++ ) {\n");
                            handleBody(builder,ff.body, spaces4+ indent );
                            builder.append(indent).append("}\n");
                            return;
                        }
                    }
                }
            }
            builder.append("for ( ").append(typeOf).append(" ");
            traverse(builder,"", ff.target, true);
            builder.append(" : ");
            traverse(builder,"", ff.iter, true);
            builder.append(" ) {\n");
            lineNumber++;
            handleBody(builder,ff.body, spaces4+ indent );
            builder.append(indent).append("}\n");
            lineNumber++;
        }

        private void handleWhile(StringBuilder builder, While ff, String indent, boolean inline) throws Exception {
            builder.append("while ( ");
            traverse(builder,"", ff.test, true );
            builder.append(" ) {\n");
            lineNumber++;
            handleBody(builder,ff.body, spaces4+ indent );
            builder.append(indent).append("}\n");
            lineNumber++;
        }

        private void handleIf(StringBuilder builder, If ff, String indent, boolean inline) throws Exception {
            builder.append("if ( ");
            traverse(builder,"", ff.test, true);
            builder.append(" ) {\n");
            lineNumber++;
            handleBody(builder,ff.body,spaces4+ indent ); 
            if ( ff.orelse==null ) {
                builder.append(indent).append("}");
            } else {
                lineNumber++;
                if ( ff.orelse.length==1 && ff.orelse[0] instanceof If ) {
                    builder.append(indent).append("} else ");
                    handleBody(builder,ff.orelse, indent );
                } else {
                    builder.append(indent).append("} else {\n");
                    handleBody( builder, ff.orelse, spaces4+indent );
                    builder.append(indent).append("}");
                }
            }
        }
        
        private void handleSubscript(StringBuilder builder, Subscript s, String indent, boolean inline) throws Exception {
            logger.log(Level.FINE, "Subscript at line {0}", ((Subscript) s).beginLine);
            if (guessType(s.value).equals(TYPE_MAP)) {
                traverse(builder, "", s.value, true);
                builder.append(".get(");
                traverse(builder, "", s.slice, true);
                builder.append(")");
            } else {
                traverse(builder, "", s.value, true);
                String t;
                if (s.value instanceof Subscript) {
                    t = guessType(((Subscript) s.value).value);
                } else {
                    t = guessType(s.value);
                }
                if (t.equals("Object") && (s.value instanceof Name)) {
                    String n = ((Name) s.value).id;
                    t = getTypeForName(n);
                    if (t == null) {
                        t = "Object";
                    }
                }
                sliceType st = s.slice;
                if (st instanceof Slice) {
                    Slice slice = (Slice) st;
                    if (t.equals(TYPE_STRING)) {
                        builder.append(".substring(");
                        traverse(builder, "", slice.lower, true);
                        if (slice.step != null) {
                            builder.append("[ERR slice.step!=null]");
                        }
                        if (slice.upper != null) {
                            builder.append(",");
                            traverse(builder, "", slice.upper, true);
                        }
                        builder.append(")");
                    } else {
                        traverse(builder, "", slice, true);
                    }
                } else if (st instanceof Index) {
                    builder.append("[");
                    traverse(builder, "", ((Index) st).value, true);
                    builder.append("]");
                } else {
                    builder.append("[");
                    traverse(builder, "", st, true);
                    builder.append("]");
                }
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
        System.err.println( convert("'XXX'*50") );
        //String furi= "/home/jbf/project/autoplot/script/lookAtUserComments.jy";
        //String furi= "/home/jbf/project/autoplot/script/curveFitting.jy";
        //String furi = "/home/jbf/project/autoplot/script/addLabelToPng.jy";
        //String furi = "/home/jbf/ct/autoplot/git/dev/demos/2023/20231115/demoDas2Gui.jy";
        String furi = "/home/jbf/ct/autoplot/u/2024/sadie/20241029/idlsav.py";
        File src = DataSetURI.getFile(furi, new NullProgressMonitor());

        try (FileReader reader = new FileReader(src)) {
            code = JythonUtil.readScript(new BufferedReader(reader));
            System.err.println(convert(code));
        }
    }

}
