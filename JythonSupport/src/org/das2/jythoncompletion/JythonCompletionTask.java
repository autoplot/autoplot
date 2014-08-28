package org.das2.jythoncompletion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import org.python.core.PyInteger;
import org.python.core.PyJavaClass;
import org.python.core.PyJavaClassPeeker;
import org.python.core.PyJavaInstance;
import org.python.core.PyJavaInstancePeeker;
import org.python.core.PyJavaPackage;
import org.python.core.PyList;
import org.python.core.PyMethod;
import org.python.core.PyMethodPeeker;
import org.python.core.PyObject;
import org.python.core.PyReflectedFunction;
import org.python.core.PyReflectedFunctionPeeker;
import org.python.core.PyString;
import org.python.core.PyStringMap;
import org.python.util.PythonInterpreter;
import org.virbo.jythonsupport.JythonOps;
import org.virbo.jythonsupport.JythonUtil;

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
                } catch ( NoSuchMethodException ex2 ) {
                    return null;
                } catch ( SecurityException ex2 ) {
                    return null;
                }
            }
            return null;
        } catch (SecurityException ex) {
            return null;
        }
    }

    public void query(CompletionResultSet arg0) throws PyException {
        try {
            JythonCompletionProvider.getInstance().setMessage("busy: getting completions");
            CompletionContext cc = CompletionSupport.getCompletionContext(editor);
            if (cc == null) {
                logger.fine("no completion context");
            } else {
                doQuery( cc, arg0);
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
            if (cc.contextType.equals(CompletionContext.MODULE_NAME)) {
                c= queryModules(cc, arg0);
            } else if (cc.contextType.equals(CompletionContext.PACKAGE_NAME)) {
                c= queryPackages(cc, arg0);
            } else if (cc.contextType.equals(CompletionContext.DEFAULT_NAME)) {
                c= queryNames(cc, arg0);
            } else if (cc.contextType.equals(CompletionContext.METHOD_NAME)) {
                c= queryMethods(cc, arg0);
            } else if (cc.contextType.equals(CompletionContext.STRING_LITERAL_ARGUMENT)) {
                c= queryStringLiteralArgument(cc, arg0);
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
        PythonInterpreter interp;

        interp = getInterpreter();

        String eval;
        if ( JythonCompletionProvider.getInstance().settings().isSafeCompletions() ) {
            eval = editor.getText(0, Utilities.getRowStart(editor, editor.getCaretPosition()));
            eval = JythonUtil.removeSideEffects( eval );
        } else {
            eval= editor.getText(0, Utilities.getRowStart(editor, editor.getCaretPosition()));
        }

        //kludge to handle increase in indent level
        if (eval.endsWith(":\n")) {
            eval = eval + "  pass\n";
        }

        putInGetDataSetStub( interp );
        
        try {
            interp.exec(eval);
        } catch ( PyException ex ) {
            eval = editor.getText(0, Utilities.getRowStart(editor, editor.getCaretPosition()));
            eval = JythonUtil.removeSideEffects( eval );
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
                    if (po instanceof PyReflectedFunction) {
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
                    } catch (NoSuchFieldException ex) {
                    } catch (SecurityException ex) {
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
                            label = ss + " <em>("+type+")</em>";
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
                    String link = getLinkForJavaSignature(signature);
                    rs.addItem(new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label, link));
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
                rs.addItem(new DefaultCompletionItem(ss, cc.completable.length(), ss, ss, null));
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
        PythonInterpreter interp = getInterpreter();

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
        
        int count=0;
        po2 = (PyList) interp.eval("list");
        for (int i = 0; i < po2.__len__(); i++) {
            PyString s = (PyString) po2.__getitem__(i);
            String ss = s.toString();
            if (ss.startsWith(cc.completable)) {
                rs.addItem(new DefaultCompletionItem(ss, cc.completable.length(), ss, ss, null));
                count++;
            }
        }
        
        BufferedReader reader= null;
        try {
            reader= new BufferedReader( new InputStreamReader( JythonCompletionTask.class.getResourceAsStream("packagelist.txt") ) );
            String ss= reader.readLine();
            String search= cc.contextString + "." + cc.completable;
            while ( ss!=null ) {
                if ( ss.startsWith(search) ) {
                    rs.addItem(new DefaultCompletionItem(ss, search.length(), ss, ss, null));
                    count++;
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
     * return the imports for the python script, also the def's are 
     * returned with a trivial definition, and assignments are converted to
     * be a trivial assignment.
     * @param src
     * @return
     */
    private static String sanitizeLeaveImports( String src ) {
        StringBuilder buf= new StringBuilder();
        BufferedReader read= new BufferedReader( new StringReader(src) );
        Pattern assign= Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\s*=(.*)");
        Matcher m;
        try {
            String s= read.readLine();
            while ( s!=null ) {
                s= popOffComments(s);
                if ( s.startsWith("from ") || s.startsWith("import ") ) {
                    buf.append(s).append("\n");
                } else if ( s.startsWith("def ") ) {
                    buf.append(s).append("\n  pass\n");
                } else if ( (m=assign.matcher(s)).matches() ) {
                    String safeArg= m.group(2).trim();
                    if ( safeArg.startsWith("\'") && safeArg.endsWith("\'") ) {
                        // do nothing, it's already a string.
                    } else if ( safeArg.startsWith("\"") && safeArg.endsWith("\"") ) {
                        // do nothing, it's already a string.
                    } else {
                        if ( safeArg.startsWith("getDataSet") ) {
                            safeArg= "fltarr(100)";
                        } else {
                            safeArg= "'" + safeArg.replaceAll("'","\"") + "'";
                        }
                    }
                    buf.append(m.group(1)).append("=").append(safeArg).append("\n");
                }
                s= read.readLine();
            }
        } catch ( IOException ex ) {
            logger.log( Level.SEVERE, ex.getMessage(), ex );
        } finally {
            try {
                read.close();
            } catch ( IOException ex2 ) {
            }
        }
        return buf.toString();
    }

    /**
     * put in function that is trivial to evaluate so we can still do completions on datasets.
     * @param interp 
     */
    private void putInGetDataSetStub( PythonInterpreter interp ) {
        String ss2= "def getDataSet( st, tr=None, mon=None ):\n   return findgen(100)\n\n";
        logger.fine(ss2);
        interp.exec( ss2  );
    }
    
    private int queryNames(CompletionContext cc, CompletionResultSet rs) throws BadLocationException {
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
        eval= editor.getText(0, Utilities.getRowStart(editor, editor.getCaretPosition()));
        
        if ( JythonCompletionProvider.getInstance().settings().isSafeCompletions() ) {
            eval= sanitizeLeaveImports( eval );
        } 
        
        try {
            interp.exec(eval);
        } catch ( PyException ex ) {
            rs.addItem(new MessageCompletionItem("Eval error in code before current position", ex.toString()));
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
        List<String> sargs = new ArrayList<String>();

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
        List<String> sargs = new ArrayList<String>();


        for (Class arg : javaMethod.getParameterTypes()) {
            sargs.add(arg.getCanonicalName());
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

    private int queryStringLiteralArgument(CompletionContext cc, CompletionResultSet arg0) {
        String method = cc.contextString;
        int [] pos= new int[2];
        String s= DataSetUrlCompletionTask.popString(editor,pos);
        if (method.equals("getDataSet")) {
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
            if ( org.virbo.jythonsupport.Util.isLegacyImports() ) {
                URL imports = JythonOps.class.getResource("imports.py");
                InputStream in= imports.openStream();
                try {
                    interp.execfile(in);
                } finally {
                    in.close();
                }
            }
            return interp;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void refresh(CompletionResultSet arg0) {
        //throw new UnsupportedOperationException("Not supported yet.");
    }

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
        Pattern p= Pattern.compile("(org.virbo.dataset.QDataSet|java.lang.String|java.lang.Object|org.das2.util.monitor.ProgressMonitor|org.das2.datum.DatumRange|org.das2.datum.Datum)");
        Matcher m= p.matcher(label);
        while ( m.find() ) {
            String s= m.group(1);
            if ( s.equals("org.virbo.dataset.QDataSet") ) {
                m.appendReplacement(build,"QDataSet");
            } else if ( s.equals("java.lang.String") ) {
                m.appendReplacement(build,"String");
            } else if ( s.equals("java.lang.Object") ) {
                m.appendReplacement(build,"Object");
            } else if ( s.equals("org.das2.util.monitor.ProgressMonitor") ) {
                m.appendReplacement(build,"Monitor");
            } else if ( s.equals("org.das2.datum.DatumRange") ) {
                m.appendReplacement(build,"DatumRange");
            } else if ( s.equals("org.das2.datum.Datum") ) {
                m.appendReplacement(build,"Datum");
            }
        }
        m.appendTail(build);
        return build.toString();
    }
    
    public static List<DefaultCompletionItem> getLocalsCompletions(PythonInterpreter interp, CompletionContext cc) {
        
        List<DefaultCompletionItem> result= new ArrayList();
        
        PyStringMap locals = (PyStringMap) interp.getLocals();

        PyList po2 = locals.keys();
        for (int i = 0; i < po2.__len__(); i++) {
            PyString s = (PyString) po2.__getitem__(i);
            String ss = s.toString();
            String signature = null; // java signature
            List<String> signatures= new ArrayList();
            if (ss.startsWith(cc.completable)) {
                PyObject po = locals.get(s);
                String label = ss;
                List<String> labels= new ArrayList();
                String args = "";
                if (po instanceof PyReflectedFunction) {
                    label = ss + "() JAVA";
                    PyReflectedFunction prf = (PyReflectedFunction) po;
                    PyReflectedFunctionPeeker peek = new PyReflectedFunctionPeeker(prf);
                    for ( int jj=0; jj<peek.getArgsCount(); jj++ ) {
                        signature = methodSignature(peek.getMethod(jj));
                        args = methodArgs(peek.getMethod(jj));
                        int j= signature.indexOf("#");
                        if ( j>-1 ) {
                            label= signature.substring(j+1);
                            label= hideJavaPaths( label );
                            Class ret= peek.getMethod(0).getReturnType();
                            label= label + "->" + hideJavaPaths( ret.getCanonicalName() );
                        }
                        signatures.add(signature);
                        labels.add(label);
                    }
                } else if (po.isCallable()) {
                    label = ss + "() ";
                    signature= "x"; // oh wow we don't use signature anymore...
                } else if (po.isNumberType()) {
                    if ( po.getType().getFullName().equals("javaclass")  ) {
                        label = ss;
                        PyJavaClassPeeker peek= new PyJavaClassPeeker((PyJavaClass)po);
                        Class jclass= peek.getProxyClass();
                        String n= jclass.getCanonicalName();
                        signature=  join( n.split("\\."), "/") + ".html#"+ jclass.getSimpleName() + "()";
                    } else if ( po.getType().getFullName().equals("javapackage")  ) {
                        label = ss;
                    } else { //TODO: check for PyFloat, etc.
                        String sss= po.toString();
                        if ( sss.contains("<") ) { // it's not what I think it is, a number
                            label = ss;
                        } else {
                            label = ss + " = " + sss;
                        }
                    }
                } else if ( po instanceof PyJavaClass ) {
                    
                } else {
                    logger.fine("");
                }
                
                if ( !signatures.isEmpty() ) {
                    for ( int jj= 0; jj<signatures.size(); jj++ ) {
                        signature= signatures.get(jj);
                        label= labels.get(jj);
                        String link = null;
                        if (signature != null) {
                            link= getLinkForJavaSignature(signature);
                        }
                        if ( ss.equals("dom") ) {
                            link= "http://autoplot.org/developer.scripting#DOM";
                        }
                        result.add( new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label, link) );
                    }
                } else {
                    String link = null;
                    if (signature != null) {
                        link= getLinkForJavaSignature(signature);
                    }
                    if ( ss.equals("dom") ) {
                        link= "http://autoplot.org/developer.scripting#DOM";
                    }
                    result.add( new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label, link) );
                }
            }
        }
        
        return result;
    }

    /**
     * return a link to the documentation for a java signature.  For standard library
     * things, this goes to Oracle's website.  For other things, this goes
     * to the Autoplot/Das2 javadocs.
     * @param signature signature like javax.swing.JCheckBox#paramString()
     * @return the link, like http://docs.oracle.com/javase/6/docs/api/javax/swing/JCheckBox#paramString()
     */
    private static String getLinkForJavaSignature(String signature) {
        String link = null;
        if ( signature != null) {
            if ( signature.startsWith("javax") || signature.startsWith("java") || signature.startsWith("org.w3c.dom") || signature.startsWith("org.xml.sax") ) {
                link= "http://docs.oracle.com/javase/6/docs/api/" + signature.replaceAll(",", ", ");
            } else if ( signature.startsWith("org/")) {
                link= JythonCompletionProvider.getInstance().settings.getDocHome() + signature.replaceAll(",", ", ");
            } else {
                //String docHome= JythonCompletionProvider.getInstance().settings().getDocHome();
                //docHome= docHome.replaceAll("AUTOPLOT_HOME", FileSystem.settings().getLocalCacheDir().toString() );
                //link = JythonCompletionProvider.getInstance().settings().getDocHome() + signature;
                link= null;
            }
        }
        return link;
    }
}
