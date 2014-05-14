package org.das2.jythoncompletion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
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
 *
 * @author jbf
 */
public class JythonCompletionTask implements CompletionTask {

    private static final Logger logger= LoggerManager.getLogger("jython.editor.completion");
    
    public static final String CLIENT_PROPERTY_INTERPRETER_PROVIDER = "JYTHON_INTERPRETER_PROVIDER";
    JTextComponent editor;
    String context;
    private final JythonInterpreterProvider jythonInterpreterProvider;

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
                return;
            }
            if (cc.contextType == CompletionContext.MODULE_NAME) {
                queryModules(cc, arg0);
            } else if (cc.contextType == CompletionContext.PACKAGE_NAME) {
                queryPackages(cc, arg0);
            } else if (cc.contextType == CompletionContext.DEFAULT_NAME) {
                queryNames(cc, arg0);
            } else if (cc.contextType == CompletionContext.METHOD_NAME) {
                queryMethods(cc, arg0);
            } else if (cc.contextType == CompletionContext.STRING_LITERAL_ARGUMENT) {
                queryStringLiteralArgument(cc, arg0);
            }

        } catch (BadLocationException ex) {
        } finally {
            JythonCompletionProvider.getInstance().setMessage("done getting completions");
            arg0.finish();
        }
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

    private void queryMethods(CompletionContext cc, CompletionResultSet rs) throws BadLocationException {
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

        try {
            interp.exec(eval);
        } catch ( PyException ex ) {
            rs.addItem(new MessageCompletionItem("Eval error in code before current position", ex.toString()));
            return;
        }

        PyObject lcontext;
        try {
            lcontext = interp.eval(cc.contextString);
        } catch (PyException ex) {
            rs.addItem(new MessageCompletionItem("Eval error: " + cc.contextString, ex.toString()));
            return;
        }

        PyList po2;
        try {
            po2= (PyList) lcontext.__dir__();
        } catch ( PyException e ) {
            logger.log( Level.SEVERE, e.getMessage(), e );
            return;
        }
        
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
                }
            }
        }
    }

    private void queryModules(CompletionContext cc, CompletionResultSet rs) {
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

        interp.exec(eval);
        PyList po2 = (PyList) interp.eval("list");
        for (int i = 0; i < po2.__len__(); i++) {
            PyString s = (PyString) po2.__getitem__(i);
            String ss = s.toString();
            if (ss.startsWith(cc.completable)) {
                rs.addItem(new DefaultCompletionItem(ss, cc.completable.length(), ss, ss, null));
            }
        }

    }

    private void queryPackages(CompletionContext cc, CompletionResultSet rs) {
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
                "list.append('*')\n" + 
                "list\n";
        PyList po2;

        //try {    // this is handled at a higher level now.
        interp.exec(eval);
//        } catch ( PyException e ) {  // "no module called c" when c<TAB>
//            // empty list
//            e.printStackTrace();
//            return;
//        }
        po2 = (PyList) interp.eval("list");
        for (int i = 0; i < po2.__len__(); i++) {
            PyString s = (PyString) po2.__getitem__(i);
            String ss = s.toString();
            if (ss.startsWith(cc.completable)) {
                rs.addItem(new DefaultCompletionItem(ss, cc.completable.length(), ss, ss, null));
            }
        }

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
                        safeArg= "'" + safeArg.replaceAll("'","\"") + "'";
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

    private void queryNames(CompletionContext cc, CompletionResultSet rs) throws BadLocationException {

        String[] keywords = new String[]{"assert", "def", "elif", "except", "from", "for", "finally", "import", "while", "print", "raise"}; //TODO: not complete
        for (String kw : keywords) {
            if (kw.startsWith(cc.completable)) {
                rs.addItem(new DefaultCompletionItem(kw, cc.completable.length(), kw, kw, null, 0));
            }
        }

        PythonInterpreter interp = getInterpreter();

        String eval;
        eval= editor.getText(0, Utilities.getRowStart(editor, editor.getCaretPosition()));
        eval = JythonUtil.removeSideEffects( eval );

        String ss2= "def getDataSet( st, mon ):\n   return findgen(100)\n\ndef getDataSet( st ):\n   return findgen(100)\n\n";
        logger.fine(ss2);
        interp.exec( ss2  );
        
        interp.exec(eval);
        getLocalsCompletions( interp, cc, rs);
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

    private void queryStringLiteralArgument(CompletionContext cc, CompletionResultSet arg0) {
        String method = cc.contextString;
        if (method.equals("getDataSet")) {
            DataSetUrlCompletionTask task = new DataSetUrlCompletionTask(editor);
            task.query(arg0);
        }
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

    public static void getLocalsCompletions(PythonInterpreter interp, CompletionContext cc, CompletionResultSet rs) {
        List<DefaultCompletionItem> rr= getLocalsCompletions( interp, cc );
        for ( DefaultCompletionItem item: rr ) {
            rs.addItem( item );
        }
        
    }
    
    public static List<DefaultCompletionItem> getLocalsCompletions(PythonInterpreter interp, CompletionContext cc) {
        
        List<DefaultCompletionItem> result= new ArrayList();
        
        PyStringMap locals = (PyStringMap) interp.getLocals();

        PyList po2 = locals.keys();
        for (int i = 0; i < po2.__len__(); i++) {
            PyString s = (PyString) po2.__getitem__(i);
            String ss = s.toString();
            String signature = null; // java signature
            if (ss.startsWith(cc.completable)) {
                PyObject po = locals.get(s);
                String label = ss;
                String args = "";
                if (po instanceof PyReflectedFunction) {
                    label = ss + "() JAVA";
                    PyReflectedFunction prf = (PyReflectedFunction) po;
                    PyReflectedFunctionPeeker peek = new PyReflectedFunctionPeeker(prf);
                    signature = methodSignature(peek.getMethod(0));
                    args = methodArgs(peek.getMethod(0));
                    int j= signature.indexOf("#");
                    if ( j>-1 ) {
                        label= signature.substring(j+1);
                        label= label.replaceAll("org.virbo.dataset.QDataSet", "QDataSet").replaceAll("java.lang.String", "String");
                        Class ret= peek.getMethod(0).getReturnType();
                        label= label + "->" + ret.getCanonicalName().replaceAll("org.virbo.dataset.QDataSet", "QDataSet").replaceAll("java.lang.String", "String");
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
