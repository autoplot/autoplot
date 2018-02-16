package org.das2.jythoncompletion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.Utilities;
import org.das2.jythoncompletion.support.CompletionResultSet;
import org.das2.jythoncompletion.support.CompletionTask;
import org.das2.util.LoggerManager;
import org.python.core.PyClass;
import org.python.core.PyClassPeeker;
import org.python.core.PyException;
import org.python.core.PyFunction;
import org.python.core.PyInteger;
import org.python.core.PyJavaClass;
import org.python.core.PyJavaClassPeeker;
import org.python.core.PyJavaInstance;
import org.python.core.PyJavaInstancePeeker;
import org.python.core.PyJavaPackage;
import org.python.core.PyList;
import org.python.core.PyMethod;
import org.python.core.PyMethodPeeker;
import org.python.core.PyNone;
import org.python.core.PyObject;
import org.python.core.PyReflectedFunction;
import org.python.core.PyReflectedFunctionPeeker;
import org.python.core.PyString;
import org.python.core.PyStringMap;
import org.python.core.PyTableCode;
import org.python.util.PythonInterpreter;
import org.autoplot.jythonsupport.JythonOps;
import org.autoplot.jythonsupport.JythonRefactory;
import org.autoplot.jythonsupport.JythonUtil;
import org.autoplot.jythonsupport.SimplifyScriptSupport;

/**
 * Completions for Jython code.
 * @author jbf
 */
public class JythonCompletionTask implements CompletionTask {

    private static final Logger logger= LoggerManager.getLogger("jython.editor.completion");
    
    public static final String CLIENT_PROPERTY_INTERPRETER_PROVIDER = "JYTHON_INTERPRETER_PROVIDER";
    JTextComponent editor;
    private final JythonInterpreterProvider jythonInterpreterProvider;

    /**
     * create the completion task on the text component, using its content and caret position.
     * @param t the text component
     */
    public JythonCompletionTask(JTextComponent t) {
        this.editor = t;
        jythonInterpreterProvider = (JythonInterpreterProvider) t.getClientProperty(CLIENT_PROPERTY_INTERPRETER_PROVIDER);
    }

    private Method getReadMethod(PyObject context, PyObject po, Class dc, String propName) {
        try {
            String methodName = "get" + propName.substring(0,1).toUpperCase() + propName.substring(1);
            Method m = dc.getMethod(methodName);
            return m;
        } catch (NoSuchMethodException ex) {
            if ( po instanceof PyInteger ) {
                String methodName = "is" + propName.substring(0,1).toUpperCase() + propName.substring(1);
                try {
                    Method m = dc.getMethod(methodName);
                    return m;
                } catch ( NoSuchMethodException | SecurityException ex2 ) {
                    return null;
                }
            }
            return null;
        } catch (SecurityException ex) {
            return null;
        }
    }

    @Override
    public void query(CompletionResultSet arg0) throws PyException {
        try {
            JythonCompletionProvider.getInstance().setMessage("busy: getting completions");
            CompletionContext cc = CompletionSupport.getCompletionContext(editor);
            if (cc == null) {
                logger.fine("no completion context");
            } else {
                doQuery( cc, arg0);
                // TODO: how to make it so the plotx reference documentation waits?  I guess we add multiple completions.
                //( arg0.addItem(new MessageCompletionItem("please wait")) );
            }
        } catch ( BadLocationException ex ) {
            logger.log( Level.WARNING, null, ex );
            arg0.addItem( new MessageCompletionItem( ex.getMessage() ) );            
        } finally {
            JythonCompletionProvider.getInstance().setMessage("done getting completions");
            arg0.finish();
        }
    }

    /**
     * perform the completions query.
     * @param cc
     * @param arg0
     * @return the count
     */
    public int doQuery( CompletionContext cc, CompletionResultSet arg0 ) {
        int c=0;
        try {
            switch (cc.contextType) {
                case CompletionContext.MODULE_NAME:
                    c= queryModules(cc, arg0);
                    break;
                case CompletionContext.PACKAGE_NAME:
                    c= queryPackages(cc, arg0);
                    break;
                case CompletionContext.DEFAULT_NAME:
                    c= queryNames(cc, arg0);
                    break;
                case CompletionContext.METHOD_NAME:
                    c= queryMethods(cc, arg0);
                    break;
                case CompletionContext.STRING_LITERAL_ARGUMENT:
                    c= queryStringLiteralArgument(cc, arg0);
                    break;
                case CompletionContext.COMMAND_ARGUMENT:
                    c= queryCommandArgument(cc, arg0);
                    c+= queryNames(cc, arg0);
                    break;
                default:
                    break;
            }
        } catch ( BadLocationException ex ) {
            logger.log( Level.WARNING, null, ex );
            arg0.addItem( new MessageCompletionItem( ex.getMessage() ) );
        } finally {
            
        }
        return c;
    }
    
    private Method getJavaMethod(PyMethod m, int i) {
        PyMethodPeeker mpeek = new PyMethodPeeker(m);
        //PyJavaInstancePeeker peek = new PyJavaInstancePeeker((PyJavaInstance) context);
        return new PyReflectedFunctionPeeker(mpeek.getReflectedFunction()).getMethod(i);
    }

    private int getMethodCount( PyMethod m ) {
        PyMethodPeeker mpeek = new PyMethodPeeker(m);
        return new PyReflectedFunctionPeeker(mpeek.getReflectedFunction()).getArgsCount();
    }

    private int queryMethods(CompletionContext cc, CompletionResultSet rs) throws BadLocationException {
        logger.fine("queryMethods");
        PythonInterpreter interp;

        interp = getInterpreter();

        String eval;
        if ( JythonCompletionProvider.getInstance().settings().isSafeCompletions() ) {
            eval = editor.getText(0, Utilities.getRowStart(editor, editor.getCaretPosition()));
            String eval1 = SimplifyScriptSupport.removeSideEffects( eval );
            //String eval2 = JythonUtil.removeSideEffects( eval );
            eval = eval1;
        } else {
            eval= editor.getText(0, Utilities.getRowStart(editor, editor.getCaretPosition()));
        }

        //kludge to handle increase in indent level
        if (eval.endsWith(":\n")) {
            eval = eval + "  pass\n";
        }

        putInGetDataSetStub( interp );
        
        try {
            interp.exec(JythonRefactory.fixImports(eval));
        } catch ( PyException ex ) {
            eval = editor.getText(0, Utilities.getRowStart(editor, editor.getCaretPosition()));
            String eval1 = SimplifyScriptSupport.removeSideEffects( eval );
            //String eval2 = JythonUtil.removeSideEffects( eval );
            eval= eval1;
            if (eval.endsWith(":\n")) {
               eval = eval + "  pass\n";
            }
            try {
                eval= sanitizeLeaveImports(eval);
                interp.exec(eval);
            } catch (PyException ex2 ) {            
                rs.addItem(new MessageCompletionItem("Eval error in code before current position", ex2.toString()));
                return 0;
            }
        } catch (IOException ex) {
            rs.addItem(new MessageCompletionItem("Exception occurred: " + ex.toString()));
            return 0;
        }

        PyObject lcontext;
        try {
            lcontext = interp.eval(cc.contextString);
        } catch (PyException ex) {
            rs.addItem(new MessageCompletionItem("Eval error: " + cc.contextString, ex.toString()));
            return 0;
        }

        PyList po2;
        try {
            po2= (PyList) lcontext.__dir__();
        } catch ( PyException e ) {
            logger.log( Level.SEVERE, e.getMessage(), e );
            return 0;
        }
        
        int count=0;
        for (int i = 0; i < po2.__len__(); i++) {
            PyString s = (PyString) po2.__getitem__(i);
            String ss = s.toString();
            logger.log(Level.FINEST, "does {0} start {1}", new Object[] { cc.completable, ss } );
            if (ss.startsWith(cc.completable)) {
                boolean notAlreadyAdded= true;
                PyObject po;
                try {
                    po = lcontext.__getattr__(s);
                } catch (PyException e) {
                    logger.log(Level.FINE, "PyException from \"{0}\":", ss);
                    logger.log( Level.SEVERE, e.getMessage(), e );
                    continue;
                } catch ( IllegalArgumentException e ) {
                    logger.log( Level.SEVERE, e.getMessage(), e );
                    continue;
                }
                String label = ss;
                String signature = null;
                String args = "";
                if (lcontext instanceof PyJavaClass) {
                    if (po.getClass().toString().equals( "class org.python.core.PyReflectedConstructor" ) ) {
                        args= "()";
                        signature= "";
                    } else if (po instanceof PyReflectedFunction) {
                        Method m = new PyReflectedFunctionPeeker((PyReflectedFunction) po).getMethod(0);
                        signature = methodSignature(m);
                        args = methodArgs(m);
                    } else if ( po instanceof PyString || po instanceof PyJavaInstance) {
                        Class c= new PyClassPeeker((PyJavaClass) lcontext).getJavaClass();
                        try {
                            Field f = c.getField(ss);
                            signature= fieldSignature(f);
                        } catch ( NoSuchFieldException ex ) {   
                        }
                    }
                } else if ( lcontext instanceof PyJavaPackage ) {
                    if (po instanceof PyJavaClass) {
                        Class dc = new PyJavaClassPeeker((PyJavaClass)po).getProxyClass();
                        if ( dc.getConstructors().length>0 ) {
                            Constructor constructor= dc.getConstructors()[0];
                            signature= constructorSignature( constructor );
                            args= argsList( constructor.getParameterTypes() );
                            signature= signature+args;
                        } else {
                            signature= dc.getCanonicalName().replaceAll("\\.","/")+".html";
                        }
                        //Method m = new PyJavaClassPeeker((PyJavaClass)po).getMethod(0);
                        //signature = methodSignature(m);
                        //args = methodArgs(m);
                    } else if ( po instanceof PyJavaPackage ) {
                        //Method m = new PyJavaClassPeeker((PyJavaClass)po).getMethod(0);
                        //signature = methodSignature(m);
                        //args = methodArgs(m);
                    }
                } else if (lcontext instanceof PyClass) {
                    PyClassPeeker peek = new PyClassPeeker((PyClass) lcontext);
                    Class dc = peek.getJavaClass();
                    Field f = null;
                    try {
                        f = dc.getField(label);
                    } catch (NoSuchFieldException | SecurityException ex) {
                    }
                    if (f == null) {
                        continue;
                    }
                    signature = fieldSignature(f);
                } else if (lcontext instanceof PyJavaInstance) {
                    if (po instanceof PyMethod) {
                        PyMethod m = (PyMethod) po;
                        Method jm;
                        try {
                            for ( int im=0; im<getMethodCount(m); im++ ) {
                                jm = getJavaMethod(m, im);
                                signature = methodSignature(jm);
                                args = methodArgs(jm);
                                label= ss + args;
                                String link = getLinkForJavaSignature(signature);
                                rs.addItem(new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label, link));
                                count++;
                                notAlreadyAdded= false;
                            }
                        } catch ( RuntimeException ex ) {
                            logger.fine(ex.toString());
                            continue;
                        }
                    } else {
                        PyJavaInstancePeeker peek = new PyJavaInstancePeeker((PyJavaInstance) lcontext);
                        Class dc = peek.getInstanceClass();
                        Method propReadMethod = getReadMethod(lcontext, po, dc, label);
                        if (propReadMethod != null) {
                            signature = methodSignature(propReadMethod);
                            args = "";
                            String type= propReadMethod.getReturnType().getCanonicalName();
                            label = ss + " <i>("+type+")</i>";
                        } else {
                            Field f = null;
                            try {
                                f = dc.getField(label);
                            } catch (NoSuchFieldException ex) {
                                logger.log(Level.FINEST, "NoSuchFieldException for item {0}", s);
                            } catch (SecurityException ex) {
                                logger.log(Level.FINEST, "SecurityException for item {0}", s);
                            }
                            if (f == null) continue;
                            //TODO: don't include static fields in list.
                            signature = fieldSignature(f);
                            label = ss;
                        }
                    }
                } else if ( lcontext instanceof PyObject ) {
                    //PyObject o= context.__dir__();
                    label= ss;
                    signature= null;
                    if ( po instanceof PyMethod ) {
                        PyMethod pm= (PyMethod)po;
                        PyObject pm2= pm.im_func;
                        if ( pm2 instanceof PyFunction ) {
                            Object doc= ((PyFunction)pm2).__doc__;
                            if ( doc!=null ) {
                                signature= doc instanceof PyNone ? "(No documentation)" : doc.toString();
                                String[] ss2= signature.split("\n");
                                if ( ss2.length>1 ) {
                                    for ( int jj= 0; jj< ss2.length; jj++ ){
                                        ss2[jj]= escapeHtml(ss2[jj]);
                                    }
                                    String sig= getPyFunctionSignature( (PyFunction)pm2 );
                                    if ( !signature.startsWith("<html>" ) ) {
                                        signature= "<html><b>"+sig+ "</b><br><br>"+join( ss2, "<br>" )+"</html>";
                                    } else {
                                        signature= "<html><b>"+sig+ "</b><br><br>" + signature.substring(6)+"</html>";
                                    }
                                }
                                signature= "inline:"+signature;
                            }
                        }
                    }
                    //String link = "http://docs.python.org/library/"; //TODO: this could probably be done
                } else {
                    if (po instanceof PyReflectedFunction) {
                        label = ss + "() STATIC JAVA";
                    } else if (po.isCallable()) {
                        label = ss + "() " + (lcontext instanceof PyJavaInstance ? "JAVA" : "");
                        PyMethod m = (PyMethod) po;
                        //Method jm = getJavaMethod(m, 0);
                        signature = methodSignature(getJavaMethod(m, 0));
                    } else {
                        logger.fine("");
                    }
                }
                if ( notAlreadyAdded ) {
                    if ( signature!=null && signature.startsWith("inline:") ) {
                        rs.addItem(new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label, signature));
                    } else {
                        String link = getLinkForJavaSignature(signature);
                        rs.addItem(new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label, link));
                    }
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 
     * @param cc
     * @param rs
     * @return the count
     */
    private int queryModules(CompletionContext cc, CompletionResultSet rs) {
        logger.fine("queryModules");
        PythonInterpreter interp = getInterpreter();

        String eval = "targetComponents = '" + cc.contextString + "'.split('.')\n" +
                "base = targetComponents[0]\n" +
                "baseModule = __import__(base, globals(), locals())\n" +
                "module = baseModule    \n" +
                "for component in targetComponents[1:]:\n" +
                "    module = getattr(module, component)\n" +
                "list = dir(module)\n" +
                "if ( list.count('__name__')>0 ):\n" +
                "    list.remove('__name__')\n" +
                "list.append('*')\n" +
                "list";

        try {
            interp.exec(eval);
        } catch ( PyException ex ) {
            rs.addItem(new MessageCompletionItem("Eval error in code before current position", ex.toString()));
            return 0;
        }
        
        int count=0;
        PyList po2 = (PyList) interp.eval("list");
        for (int i = 0; i < po2.__len__(); i++) {
            PyString s = (PyString) po2.__getitem__(i);
            String ss = s.toString();
            if (ss.startsWith(cc.completable)) {
                String javaClass= cc.contextString + "." + ss;
                String signature;  
                String link;
                if ( ss.length()>0 && Character.isUpperCase(ss.charAt(0)) ) {
                    signature= join( javaClass.split("\\."), "/") + ".html";  
                    link= JavadocLookup.getInstance().getLinkForJavaSignature(signature);
                } else {
                    signature= join( javaClass.split("\\."), "/") + "/package-summary.html";  
                    link= JavadocLookup.getInstance().getLinkForJavaSignature(signature);
                }
                if ( link!=null ) link+= "#skip.navbar.top";                
                rs.addItem(new DefaultCompletionItem(ss, cc.completable.length(), ss, ss, link));
                count++;
            }
        }
        return count;
    }

    /**
     * look for package names.
     * @param cc
     * @param rs
     * @return the count
     */
    private int queryPackages(CompletionContext cc, CompletionResultSet rs) {
        logger.fine("queryPackages");
        PythonInterpreter interp = getInterpreter();

        HashSet<String> results= new HashSet();       
        int count=0;
        
        if ( !cc.contextString.equals( cc.completable ) ) { // something to work with
            String eval = "import " + cc.contextString + "\n" +
                    "targetComponents = '" + cc.contextString + "'.split('.')\n" +
                    "base = targetComponents[0]\n" + 
                    "baseModule = __import__(base, globals(), locals(), [], -1 )\n" + 
                    "module = baseModule    \n" + 
                    "name= base\n" + 
                    "for component in targetComponents[1:]:\n" + 
                    "    name= name + '.' + component\n" + 
                    "    baseModule = __import__( name, None, None )\n" + 
                    "    module = getattr(module, component)\n" + 
                    "list = dir(module)\n" + 
                    "if ( '__name__' in list ): list.remove('__name__')\n" + 
                    "list\n";
            PyList po2;

            try {
                interp.exec(eval);
            } catch ( PyException ex ) {
                rs.addItem(new MessageCompletionItem("Eval error in code before current position", ex.toString()));
                return 0;
            }

            po2 = (PyList) interp.eval("list");
            for (int i = 0; i < po2.__len__(); i++) {
                PyString s = (PyString) po2.__getitem__(i);
                String ss = s.toString();
                if (ss.startsWith(cc.completable)) {
                    String javaClass= cc.contextString + "." + ss;
                    String signature;  
                    String link;
                    if ( ss.length()>0 && Character.isUpperCase(ss.charAt(0)) ) {
                        signature= join( javaClass.split("\\."), "/") + ".html";  
                        link= JavadocLookup.getInstance().getLinkForJavaSignature(signature);
                    } else {
                        signature= join( javaClass.split("\\."), "/") + "/package-summary.html";  
                        link= JavadocLookup.getInstance().getLinkForJavaSignature(signature);
                    }
                    if ( link!=null ) link+= "#skip.navbar.top";
                    rs.addItem(new DefaultCompletionItem(ss, cc.completable.length(), ss, ss, link ));
                    count++;
                    results.add(ss);
                }
            }
        }
        
                
        BufferedReader reader= null;
        try {
            reader= new BufferedReader( new InputStreamReader( JythonCompletionTask.class.getResourceAsStream("packagelist.txt") ) );
            String ss= reader.readLine();
            String search= cc.contextString + "." + cc.completable;
            int plen= cc.contextString.length()+1;
            if ( cc.contextString.equals(cc.completable) ) {
                search= cc.contextString;
                plen= search.length();
            }
            while ( ss!=null ) {
                if ( !ss.startsWith("#") && ss.length()>0 ) {
                    if ( ss.startsWith(search) && !results.contains(ss.substring(plen)) ) {
                        String link= "http://www-pw.physics.uiowa.edu/~jbf/autoplot/javadoc/" + ss.replaceAll("\\.","/") + "/package-summary.html";
                        rs.addItem(new DefaultCompletionItem(ss, search.length(), ss, ss, link ));
                        count++;
                    }
                }
                ss= reader.readLine();
            }
        } catch ( IOException ex ) {
            logger.log( Level.WARNING, null, ex );
        } finally {
            if ( reader!=null ) try {
                reader.close();
            } catch (IOException ex) {
                logger.log( Level.WARNING, null, ex );
            }
        }
        
        return count;
    }

    private static String join(String[] list, String delim) {
        return join(Arrays.asList(list), delim);
    }

    private static String join(List<String> list, String delim) {
        if (list.isEmpty()) {
            return "";
        } else {
            StringBuilder result = new StringBuilder(list.get(0));
            for (int i = 1; i < list.size(); i++) {
                result.append(delim).append(list.get(i));
            }
            return result.toString();
        }

    }

    /**
     * quick-n-dirty one line parser is better than nothing.
     * @param s
     * @return 
     */
    private static String popOffComments( String s ) {
        char inString= 0;
        for ( int i=0; i<s.length(); i++ ) {
            if( inString==0 && s.charAt(i)=='#' ) {
                return s.substring(0,i);
            } else if ( s.charAt(i)=='\'' || s.charAt(i)=='"' ){
                if ( s.charAt(i)==inString ) {
                    inString= 0;
                } else {
                    inString= s.charAt(i);
                }
            }
        }
        return s;
    }
    
    /**
     * get the documentation, looking for terminator.  TODO: this should all be redone with Jython AST.
     * @param line 
     * @param read continue to read from here.
     * @return the source containing the documentation.
     */
    private static String popDoc( String line, BufferedReader read ) throws IOException {
        String lin= line.trim();
        if ( lin.startsWith("\"") && lin.endsWith("\"") ) {
            return line;
        } else if ( lin.startsWith("'") && lin.endsWith("'") ) {
            return line;
        } else if ( lin.startsWith("\"\"\"" ) || lin.startsWith("'''") ) {
            String term= lin.substring(0,3);
            if ( lin.endsWith(term) ) return line;
            StringBuilder build= new StringBuilder(line);
            build.append("\n");
            line= read.readLine();
            while ( line!=null ) {
                build.append(line).append("\n");
                lin= line.trim();
                if ( lin.endsWith(term) ) {
                    break;
                }
                line= read.readLine();
            }
            if ( line==null ) {
                throw new IllegalArgumentException("unterminated string");
            } else {
                return build.toString();
            }
        } else {
            return null;
        }

    }
    
    /**
     * return the imports for the python script, also the def's are 
     * returned with a trivial definition, and assignments are converted to
     * be a trivial assignment.
     * 
     * @param src jython source
     * @return subset sufficient to provide completions
     */
    private static String sanitizeLeaveImports( String src ) {
        return SimplifyScriptSupport.simplifyScriptToCompletions( src );
    }

    /**
     * put in function that is trivial to evaluate so we can still do completions on datasets.
     * @param interp 
     */
    private void putInGetDataSetStub( PythonInterpreter interp ) {
        String ss2= "def getDataSet( st, tr=None, mon=None ):\n   return findgen(100)\n\n";
        logger.finer(ss2);
        interp.exec( ss2  );
    }
    
    private int queryNames(CompletionContext cc, CompletionResultSet rs) throws BadLocationException {
        logger.fine("queryNames");
        int count=0;
        String[] keywords = new String[]{"assert", "def", "elif", "except", "from", "for", "finally", "import", "while", "print", "raise"}; //TODO: not complete
        for (String kw : keywords) {
            if (kw.startsWith(cc.completable)) {
                rs.addItem(new DefaultCompletionItem(kw, cc.completable.length(), kw, kw, null, 0));
                count++;
            }
        }

        PythonInterpreter interp = getInterpreter();

        String eval;
        int eolnCarot= Utilities.getRowStart(editor, editor.getCaretPosition());
        eval= editor.getText(0, eolnCarot);
        if ( eolnCarot>0 ) {
            int startLastLine= Utilities.getRowStart(editor, eolnCarot-1 );
            String lastLine= editor.getText( startLastLine, eolnCarot-startLastLine );
            Matcher m= Pattern.compile("def .*").matcher(lastLine.trim());
            if ( m.matches() ) {
                int i= lastLine.indexOf("def ");
                String indent= lastLine.substring(0,i);
                eval= eval + indent + "\t" + "__dummy__=1\n";
            }
        }
        
        if ( JythonCompletionProvider.getInstance().settings().isSafeCompletions() ) {
            eval= sanitizeLeaveImports( eval );
        } 
        
        try {
            interp.exec( JythonRefactory.fixImports(eval) );
        } catch ( PyException ex ) {
            rs.addItem(new MessageCompletionItem("Eval error in code before current position", ex.toString()));
            return 0;
        } catch (IOException ex) {
            rs.addItem(new MessageCompletionItem("Error with completions",ex.toString()));
            return 0;
        }
        
        return count + getLocalsCompletions( interp, cc, rs);
    }

    private static String argsList( Class[] classes ) {
        //String LPAREN="%28";
        String LPAREN = "(";
        //String RPAREN="%29";
        String RPAREN = ")";
        String SPACE = " "; // "%20";

        StringBuilder sig = new StringBuilder();

        sig.append(LPAREN);
        List<String> sargs = new ArrayList<>();

        for (Class arg : classes ) {
            sargs.add(arg.getSimpleName());
        }
        sig.append(join(sargs, "," ));
        sig.append(RPAREN);
        return sig.toString();
    }

    private static String methodArgs(Method javaMethod) {
        return argsList( javaMethod.getParameterTypes());
    }
    
    /**
     * Javadocs don't have the path on the constructor internal links.
     * @param c
     * @return 
     */
    private static String constructorSignatureNew( Constructor c ) {
        String n= c.getName();
        //String[] ss= n.split("\\.");
        String javadocPath = join( n.split("\\."), "/") + ".html";

        StringBuilder sig = new StringBuilder(javadocPath);
        //String LPAREN="%28";
        String LPAREN = "(";
        //String RPAREN="%29";
        String RPAREN = ")";

        String name= c.getName();
        int i= name.lastIndexOf(".");
        if ( i>-1 ) name= name.substring(i+1);
        sig.append("#").append( name ).append(LPAREN);
        List<String> sargs = new ArrayList<>();


        for (Class arg : c.getParameterTypes()) {
            sargs.add(arg.getCanonicalName());
        }
        sig.append(join(sargs, "," ));
        sig.append(RPAREN);
        return sig.toString();
        
    }
    
    private static String getCanonicalName( Class clas ) {
        Package p= clas.getPackage();
        if ( p!=null && p.getName().endsWith("org.python.core") ) {
            return clas.getSimpleName();
        } else {
            return clas.getCanonicalName();
        }
    }
    
    private static String methodSignature(Method javaMethod) {
        String n= javaMethod.getDeclaringClass().getCanonicalName();
        if ( n==null ) {
            // anonymous methods or inner class.
            return "<inner>";
        }
        String javadocPath = join( n.split("\\."), "/") + ".html";

        StringBuilder sig = new StringBuilder(javadocPath);
        //String LPAREN="%28";
        String LPAREN = "(";
        //String RPAREN="%29";
        String RPAREN = ")";
        String SPACE = " "; // "%20";

        sig.append("#").append(javaMethod.getName()).append(LPAREN);
        List<String> sargs = new ArrayList<>();

        for (Class arg : javaMethod.getParameterTypes()) {
            sargs.add(getCanonicalName(arg));
        }
        sig.append(join(sargs, "," ));
        sig.append(RPAREN);
        return sig.toString();
    }

    private String fieldSignature(Field f) {
        String javadocPath = join(f.getDeclaringClass().getCanonicalName().split("\\."), "/") + ".html";

        StringBuilder sig = new StringBuilder(javadocPath);

        sig.append("#").append(f.getName());
        return sig.toString();

    }

    private String constructorSignature( Constructor f ) {
        String javadocPath = join( f.getDeclaringClass().getCanonicalName().split("\\."), "/") + ".html";

        StringBuilder sig = new StringBuilder(javadocPath);

        int i= f.getName().lastIndexOf(".");
        sig.append("#").append(f.getName().substring(i + 1));
        return sig.toString();
    }

    /**
     * Do dataset URL completions on strings that start with / and getDataSet calls.
     * There's a bug here where "formatDataSet" is not detected as the context in 
     * formatDataSet(ds,'/home/jbf/foo.cdf')
     * @param cc
     * @param arg0
     * @return 
     */
    private int queryStringLiteralArgument(CompletionContext cc, CompletionResultSet arg0) {
        String method = cc.contextString;
        int [] pos= new int[2];
        String s= DataSetUrlCompletionTask.popString(editor,pos);
        if (method.equals("getDataSet") || method.equals("plot") || method.equals("plotx") || method.equals("getCompletions") ) {
            DataSetUrlCompletionTask task = new DataSetUrlCompletionTask(editor);
            task.query(arg0);
        } else if ( method.equals("File") && s.startsWith("/") ) {
            DataSetUrlCompletionTask task = new DataSetUrlCompletionTask(editor);
            task.query(arg0);
        } else if ( s.startsWith("/") ) {
            DataSetUrlCompletionTask task = new DataSetUrlCompletionTask(editor);
            task.query(arg0);
        }
        return 0;
    }

    /**
     * show the documentation found for the command.
     * @param cc
     * @param result
     * @return
     * @throws BadLocationException 
     */
    private int queryCommandArgument(CompletionContext cc, CompletionResultSet result ) throws BadLocationException {
        logger.fine("queryCommandArgument");
        String method = cc.contextString;
        
        PythonInterpreter interp = getInterpreter();

        String eval;
        eval= editor.getText(0, Utilities.getRowStart(editor, editor.getCaretPosition()));
        
        if ( JythonCompletionProvider.getInstance().settings().isSafeCompletions() ) {
            eval= sanitizeLeaveImports( eval );
        } 
        
        try {
            interp.exec(eval);
        } catch ( PyException ex ) {
            result.addItem(new MessageCompletionItem("Eval error in code before current position", ex.toString()));
            return 0;
        }

        PyObject po= interp.eval(method);
        PyObject doc= interp.eval(method+".__doc__");
         if ( po instanceof PyFunction ) {
            method= getPyFunctionSignature((PyFunction)po);
            String signature= makeInlineSignature( po, doc );
            result.addItem( new MessageCompletionItem( method, signature ) );
        } else if ( po instanceof PyReflectedFunction ) {
            PyReflectedFunction prf = (PyReflectedFunction) po;
            List<String> labels= new ArrayList();
            List<String> signatures= new ArrayList();
            List<String> argss= new ArrayList();
            doPyReflectedFunction(eval, prf, labels, signatures, argss );    
            for ( int jj=0; jj<labels.size(); jj++ ) {
                String signature= signatures.get(jj);
                if ( signature==null ) continue; // I don't this this happens, but findbugs pointed out inconsistent code.
                String link = getLinkForJavaSignature(signature);
                DefaultCompletionItem item = new DefaultCompletionItem( method, 0, signature, labels.get(jj), link );
                item.setReferenceOnly(true);
                result.addItem( item );
                //result.addItem( new MessageCompletionItem( method + labels.get(jj), signatures.get(jj) ) );
            }
        } else {
            String signature= makeInlineSignature( po, doc );
            result.addItem( new MessageCompletionItem( method, signature ) );
        }
        //logger.fine( "DefaultCompletionItem("+ss+","+cc.completable.length()+",\n" + ss + argss.get(jj)+",\n"+label+",\n"+link+")");
                                            
        return 1;
    }

    /**
     * return an interpreter to match the one the user's code lives in.
     * @return
     */
    private PythonInterpreter getInterpreter() {
        PythonInterpreter interp;
        try {
            if (jythonInterpreterProvider != null) {
                interp = jythonInterpreterProvider.createInterpreter();
            } else {
                interp = new PythonInterpreter();
            }
            if ( org.autoplot.jythonsupport.Util.isLegacyImports() ) {
                URL imports = JythonOps.class.getResource("/imports2017.py");
                try (InputStream in = imports.openStream()) {
                    interp.execfile(in,"imports2017.py");
                }
            }
            return interp;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void refresh(CompletionResultSet arg0) {
        //throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void cancel() {
        //throw new UnsupportedOperationException("Not supported yet.");
    }

    public static int getLocalsCompletions(PythonInterpreter interp, CompletionContext cc, CompletionResultSet rs) {
        int count= 0;
        List<DefaultCompletionItem> rr= getLocalsCompletions( interp, cc );
        for ( DefaultCompletionItem item: rr ) {
            rs.addItem( item );
            count++;
        }
        return count;
    }
    
    /**
     * replace java names like org.virbo.dataset.QDataSet with less-ominous names like "QDataSet"
     * @param label
     * @return the simplified name.
     */
    private static String hideJavaPaths( String label ) {
        StringBuffer build= new StringBuffer();
        Pattern p= Pattern.compile("(org.das2.qds.QDataSet|java.lang.String|java.lang.Object|org.das2.util.monitor.ProgressMonitor|org.das2.datum.DatumRange|org.das2.datum.Datum)");
        Matcher m= p.matcher(label);
        while ( m.find() ) {
            String s= m.group(1);
            switch (s) {
                case "org.das2.qds.QDataSet":
                    m.appendReplacement(build,"QDataSet");
                    break;
                case "java.lang.String":
                    m.appendReplacement(build,"String");
                    break;
                case "java.lang.Object":
                    m.appendReplacement(build,"Object");
                    break;
                case "org.das2.util.monitor.ProgressMonitor":
                    m.appendReplacement(build,"Monitor");
                    break;
                case "org.das2.datum.DatumRange":
                    m.appendReplacement(build,"DatumRange");
                    break;
                case "org.das2.datum.Datum":
                    m.appendReplacement(build,"Datum");
                    break;
                default:
                    break;
            }
        }
        m.appendTail(build);
        return build.toString();
    }
    
    public static String escapeHtml( String s ) {
        StringBuffer out= new StringBuffer();
        Pattern p= Pattern.compile("([\\<\\>])");
        Matcher m= p.matcher(s);
        while ( m.find() ) {
            m.appendReplacement( out, "" );
            String ss= m.group(1);
            if ( ss.equals("<") ) {
                out.append( "&lt;");
            } else if ( ss.equals(">") ) {
                out.append( "&gt;");
            }
        }
        m.appendTail(out);
        return out.toString();
    }
    
    /**
     * get the python signature for the function.  I can't figure out how to get defaults for the named keyword parameters.
     * @param pf the function
     * @return String like "docDemo8( arg )"
     */
    private static String getPyFunctionSignature( PyFunction pf ) {
        Object[] defaults= pf.func_defaults;
        String[] vars= ((PyTableCode)pf.func_code).co_varnames;
        int count= ((PyTableCode)pf.func_code).co_argcount;
        StringBuilder sig= new StringBuilder( pf.__name__+"(" );
        if ( count>0 ) {
            if ( defaults.length==vars.length ) {
                sig.append(vars[0]).append("=").append(defaults[0]);
            } else {
                sig.append(vars[0]);
            }
        }
        int nreq= vars.length-defaults.length;
        for ( int i=1; i<count; i++ ) {
            if ( i>=nreq) {
                sig.append(",").append(vars[i]).append("=").append(defaults[i-nreq]);
            } else {
                sig.append(",").append(vars[i]);
            }
        }
        if ( count + defaults.length == vars.length-2 ) { // quick kludge for var args see /home/jbf/ct/autoplot/script/demos/operators/synchronizeDemo.jy
            sig.append(",...");
        }
        sig.append(")");
        return sig.toString();
        
    }
    
    /**
     * get __doc__ from the function.
     * @param po PyFunction, typically.
     * @param doc the documentation for the function.
     * @return "inline:..."
     */
    private static String makeInlineSignature( PyObject po, PyObject doc ) {
        String sig= ( po instanceof PyFunction ) ? getPyFunctionSignature((PyFunction)po) : "";
        String signature= doc instanceof PyNone ? "(No documentation)" : doc.toString();

        if ( sig.length()>0 ) {
            sig= "<b>"+sig+ "</b><br><br>";
        }
        String[] ss2= signature.split("\n");
        if ( ss2.length>1 ) {
            for ( int jj= 0; jj< ss2.length; jj++ ){
                ss2[jj]= escapeHtml(ss2[jj]);
            }
            if ( !signature.startsWith("<html>" ) ) {
                signature= "<html>"+sig+join( ss2, "<br>" )+"</html>";
            } else {
                signature= "<html>"+sig+ signature.substring(6)+"</html>";
            }
        } else {
            signature= "<html>"+sig+ signature+"</html>";
        }
        signature= "inline:" + signature;
        return signature;
    }
    
    private static void doPyReflectedFunction( String ss, PyReflectedFunction prf, List<String> labels, List<String> signatures, List<String> argss ) {
        PyReflectedFunctionPeeker peek = new PyReflectedFunctionPeeker(prf);
        for ( int jj=0; jj<peek.getArgsCount(); jj++ ) {
            String signature = methodSignature(peek.getMethod(jj));
            String args = methodArgs(peek.getMethod(jj));
            int j= signature.indexOf("#");
            String label= ss + "() JAVA";
            if ( j>-1 ) {
                label= signature.substring(j+1);
                label= hideJavaPaths( label );
                Class ret= peek.getMethod(0).getReturnType();
                label= label + "->" + hideJavaPaths( ret.getCanonicalName() );
            }
            signatures.add(signature);
            labels.add(label);
            argss.add(args);
        }                    
    }
    
    private static void doConstructors( Constructor[] constructors, List<String> labels, List<String> signatures, String ss, List<String> argss ) {
        for (Constructor constructor : constructors) {
            String signature = constructorSignatureNew(constructor);
            if ( signature.contains("$") ) {
                signature= signature.replaceAll("\\$",".");
            }
            int j= signature.indexOf("#");
            String label= ss + "() JAVA";
            if (j>-1) {
                label= signature.substring(j+1);
                label= hideJavaPaths( label );
                Class ret = constructor.getDeclaringClass();
                label= label + "->" + hideJavaPaths( ret.getCanonicalName() );
            }
            signatures.add(signature);
            labels.add(label);
            argss.add(argsList(constructor.getParameterTypes()));
        }  
    }
    
    /**
     * sorts all the lists by the first list.  
     * See http://stackoverflow.com/questions/15400514/syncronized-sorting-between-two-arraylists/24688828#24688828
     * Note the key list must be repeated for it to be sorted as well!
     * @param <T> the list type
     * @param key the list used to sort
     * @param lists the lists to be sorted, often containing the key as well.
     */
    public static <T extends Comparable<T>> void keySort(
                                        final List<T> key, List<?>... lists){
        // Create a List of indices
        List<Integer> indices = new ArrayList<>();
        for(int i = 0; i < key.size(); i++) {
            indices.add(i);
        }
        
        // Sort the indices list based on the key
        Collections.sort(indices, new Comparator<Integer>(){
            @Override public int compare(Integer i, Integer j) {
                return key.get(i).compareTo(key.get(j));
            }
        });

        // Create a mapping that allows sorting of the List by N swaps.
        // Only swaps can be used since we do not know the type of the lists
        Map<Integer,Integer> swapMap = new HashMap<>(indices.size());
        List<Integer> swapFrom = new ArrayList<>(indices.size()),
                      swapTo   = new ArrayList<>(indices.size());
        for(int i = 0; i < key.size(); i++){
            int k = indices.get(i);
            while(i != k && swapMap.containsKey(k)) {
                k = swapMap.get(k);
            }
            swapFrom.add(i);
            swapTo.add(k);
            swapMap.put(i, k);
        }

        // use the swap order to sort each list by swapping elements
        for(List<?> list : lists) {
            for(int i = 0; i < list.size(); i++) {
                Collections.swap(list, swapFrom.get(i), swapTo.get(i));
            }
        }

    }

    public static List<DefaultCompletionItem> getLocalsCompletions(PythonInterpreter interp, CompletionContext cc) {
        
        List<DefaultCompletionItem> result= new ArrayList();
        
        PyStringMap locals = (PyStringMap) interp.getLocals();
        
        PyList po2 = locals.keys();
        for (int i = 0; i < po2.__len__(); i++) {
            PyString s = (PyString) po2.__getitem__(i);
            String ss = s.toString();
            System.err.println("xx "+ss);
            String signature = null; // java signature
            List<String> signatures= new ArrayList();
            List<String> argss= new ArrayList();
            if (ss.startsWith(cc.completable)) {
                logger.log(Level.FINER, "found completion item: {0}", ss);
                PyObject po = locals.get(s);
                String label = ss;
                List<String> labels= new ArrayList();
                String args = "";
                if (po instanceof PyReflectedFunction) {
                    PyReflectedFunction prf = (PyReflectedFunction) po;
                    doPyReflectedFunction( ss, prf, labels, signatures, argss );
                } else if (po.isCallable()) {
                    label = ss + "() ";
                    if ( po instanceof PyFunction ) {
                        label= getPyFunctionSignature((PyFunction)po);
                        args= label.substring(ss.length());
                    }
                    PyObject doc= interp.eval(ss+".__doc__");
                    signature= makeInlineSignature( po, doc );
                    
                } else if (po.isNumberType()) {
                    switch (po.getType().getFullName()) {
                        case "javaclass":
                        case "javainnerclass":
                            label = ss;
                            PyJavaClassPeeker peek= new PyJavaClassPeeker((PyJavaClass)po);
                            Class jclass= peek.getProxyClass();
                            String n= jclass.getCanonicalName();
                            boolean allStatic= true;
                            Method[] mm= jclass.getMethods();
                            for ( Method m: mm ) {
                                if ( !m.getDeclaringClass().equals(Object.class) ) {
                                    if ( !Modifier.isStatic(m.getModifiers()) ) {
                                        allStatic= false;
                                    }
                                }
                            }   if ( allStatic ) {
                                doConstructors(jclass.getConstructors(),labels,signatures,n,argss);
                                for ( int i1=0; i1<argss.size(); i1++ ) {
                                    argss.set(i1,"");
                                }
                            } else {
                                doConstructors(jclass.getConstructors(),labels,signatures,n,argss);
                            }
                            //signature=  join( n.split("\\."), "/") + ".html#"+ jclass.getSimpleName() + "()";
                            break;
                        case "javapackage":
                            label = ss;
                            break;
                        default:
                            //TODO: check for PyFloat, etc.
                            String sss= po.toString();
                            if ( sss.contains("<") ) { // it's not what I think it is, a number
                                label = ss;
                            } else {
                                label = ss + " = " + sss;
                            }   break;
                    }
                } else if ( po instanceof PyJavaClass ) {
                    
                } else {
                    logger.log(Level.FINE, "skipping {0}", ss);
                }
                    
                keySort( signatures, signatures, labels, argss );
                
                if ( !signatures.isEmpty() ) {
                    
                    for ( int jj= 0; jj<signatures.size(); jj++ ) {
                        signature= signatures.get(jj);
                        label= labels.get(jj);
                        String link = null;
                        if (signature != null) {
                            link= getLinkForJavaSignature(signature);  // TODO: inner class like Rectangle.Double is only Double
                        }
                        if ( ss.equals("dom") ) {
                            link= "http://autoplot.org/developer.scripting#DOM";
                        }
                        logger.log(Level.FINER, "DefaultCompletionItem({0},{1},\n{2}{3},\n{4},\n{5})", new Object[]{ss, cc.completable.length(), ss, argss.get(jj), label, link});
                        result.add( new DefaultCompletionItem(ss, cc.completable.length(), ss + argss.get(jj), label, link) );
                    }
                } else {
                    String link = null;
                    if ( signature!=null && signature.startsWith("inline:") ) {
                        link= signature;
                    } else if ( ss.equals("dom") ) {
                        link= "http://autoplot.org/developer.scripting#DOM";
                    } else if (signature != null) {
                        link= getLinkForJavaSignature(signature);
                    }
                    if ( po instanceof PyString ) {
                        result.add( new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label+" -> "+po+"", link) );
                    } else {
                        result.add( new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label, link) );
                    }
                }
            }
        }
        
        logger.log( Level.FINE, "getLocalsCompletions found {0} completions", new Object[]{ result.size() } );
        return result;
    }

    /**
     * return a link to the documentation for a java signature.  For standard library
     * things, this goes to Oracle's website.  For other things, this goes
     * to the Autoplot/Das2 javadocs.
     * @param signature signature like javax.swing.JCheckBox#paramString()
     * @return the link, like http://docs.oracle.com/javase/7/docs/api/javax/swing/JCheckBox#paramString()
     */
    private static String getLinkForJavaSignature(String signature) {
        return JavadocLookup.getInstance().getLinkForJavaSignature(signature);
    }
}
