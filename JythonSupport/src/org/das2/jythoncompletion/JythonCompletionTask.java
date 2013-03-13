package org.das2.jythoncompletion;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import org.python.core.PyClassPeeker;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.Utilities;

/*
 * This is the engine that does the Jython completions.  We figure out the
 * context and the completable, then query a interpreter.
 */
import org.das2.jythoncompletion.support.CompletionResultSet;
import org.das2.jythoncompletion.support.CompletionTask;
import org.python.core.PyClass;
import org.python.core.PyException;
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

    private static final Logger logger= Logger.getLogger("jython.editor");
    
    public static final String CLIENT_PROPERTY_INTERPRETER_PROVIDER = "JYTHON_INTERPRETER_PROVIDER";
    JTextComponent editor;
    String context;
    private JythonInterpreterProvider jythonInterpreterProvider;

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
            return null;
        } catch (SecurityException ex) {
            return null;
        }
    }

    public void query(CompletionResultSet arg0) {
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
            JythonCompletionProvider.getInstance().setMessage("done getting completions");

        } catch (BadLocationException ex) {
        } finally {
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
            eval= sanitizeLeaveImports( editor.getText(0, Utilities.getRowStart(editor, editor.getCaretPosition())) );
        } else {
            eval= editor.getText(0, Utilities.getRowStart(editor, editor.getCaretPosition()));
        }

        //kludge to handle increase in indent level
        if (eval.endsWith(":\n")) {
            eval = eval + "  pass\n";
        }

        Logger logger = Logger.getLogger(JythonCompletionTask.class.getName());

        try {
            interp.exec(eval);
        } catch ( PyException ex ) {
            rs.addItem(new MessageCompletionItem("Eval error in code before current position", ex.toString()));
            return;
        }

        PyObject context;
        try {
            context = interp.eval(cc.contextString);
        } catch (PyException ex) {
            rs.addItem(new MessageCompletionItem("Eval error: " + cc.contextString, ex.toString()));
            return;
        }

        PyList po2;
        try {
            po2= (PyList) context.__dir__();
        } catch ( PyException e ) {
            logger.log( Level.SEVERE, "", e );
            return;
        }
        
        for (int i = 0; i < po2.__len__(); i++) {
            PyString s = (PyString) po2.__getitem__(i);
            String ss = s.toString();
            if (ss.startsWith(cc.completable)) {
                PyObject po;
                try {
                    po = context.__getattr__(s);
                } catch (PyException e) {
                    logger.log(Level.FINE, "PyException from \"{0}\":", ss);
                    logger.log( Level.SEVERE, "", e );
                    continue;
                } catch ( IllegalArgumentException e ) {
                    logger.log( Level.SEVERE, "", e );
            continue;
                }
                String label = ss;
                String signature = null;
                String args = "";
                if (context instanceof PyJavaClass) {
                    if (po instanceof PyReflectedFunction) {
                        Method m = new PyReflectedFunctionPeeker((PyReflectedFunction) po).getMethod(0);
                        signature = methodSignature(m);
                        args = methodArgs(m);
                    }
                } else if ( context instanceof PyJavaPackage ) {
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
                } else if (context instanceof PyClass) {
                    PyClassPeeker peek = new PyClassPeeker((PyClass) context);
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
                } else if (context instanceof PyJavaInstance) {
                    if (po instanceof PyMethod) {
                        PyMethod m = (PyMethod) po;
                        Method jm;
                        try {
                            jm = getJavaMethod(m, 0);
                            if ( getMethodCount(m)>1 ) {
                                jm = getJavaMethod(m, getMethodCount(m)-1); //TODO: show completions for each argument type.
                            }
                        } catch ( RuntimeException ex ) {
                            continue;
                        }
                        signature = methodSignature(jm);
                        args = methodArgs(jm);
                        label= ss + args;
                    } else {
                        PyJavaInstancePeeker peek = new PyJavaInstancePeeker((PyJavaInstance) context);
                        Class dc = peek.getInstanceClass();
                        Method propReadMethod = getReadMethod(context, po, dc, label);
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
                } else if ( context instanceof PyObject ) {
                    PyObject o= context.__dir__();
                    label= ss;
                    signature= null;
                    //String link = "http://docs.python.org/library/"; //TODO: this could probably be done
                } else {
                    if (po instanceof PyReflectedFunction) {
                        label = ss + "() STATIC JAVA";
                    } else if (po.isCallable()) {
                        label = ss + "() " + (context instanceof PyJavaInstance ? "JAVA" : "");
                        PyMethod m = (PyMethod) po;
                        Method jm = getJavaMethod(m, 0);
                        signature = methodSignature(getJavaMethod(m, 0));
                    } else {
                        logger.fine("");
                    }
                }
                String link = null;
                if ( signature != null) {
                    if ( signature.startsWith("javax") || signature.startsWith("java") || signature.startsWith("org.w3c.dom") || signature.startsWith("org.xml.sax") ) {
                        link= "http://download.oracle.com/javase/1.5.0/docs/api/" + signature.replaceAll(",", ", ");
                    } else if ( signature.startsWith("org/")) {
                        link= JythonCompletionProvider.getInstance().settings.getDocHome() + signature;
                    } else {
                        //String docHome= JythonCompletionProvider.getInstance().settings().getDocHome();
                        //docHome= docHome.replaceAll("AUTOPLOT_HOME", FileSystem.settings().getLocalCacheDir().toString() );
                        //link = JythonCompletionProvider.getInstance().settings().getDocHome() + signature;
                        link= null;
                    }
                }
                rs.addItem(new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label, link));
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

        String eval = "targetComponents = '" + cc.contextString + "'.split('.')\n" +
                "base = targetComponents[0]\n" +
                "baseModule = __import__(base, globals(), locals())\n" +
                "module = baseModule    \n" +
                "for component in targetComponents[1:]:\n" +
                "    module = getattr(module, component)\n" +
                "list = dir(module)\n" +
                "if ( '__name__' in list ): list.remove('__name__')\n" +
                "list.append('*')\n" +
                "list";
        PyList po2;
        try {
            interp.exec(eval);
        } catch ( PyException e ) {  // "no module called c" when c<TAB>
            // empty list
            return;
        }
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
     * return the imports for the python script.
     * @param src
     * @return
     */
    private static String sanitizeLeaveImports( String src ) {
        StringBuilder buf= new StringBuilder();
        BufferedReader read= new BufferedReader( new StringReader(src) );
        try {
            String s= read.readLine();
            while ( s!=null ) {
                if ( s.startsWith("from ") || s.startsWith("import ") ) {
                    buf.append(s).append("\n");
                }
                s= read.readLine();
            }
        } catch ( IOException ex ) {
            logger.log( Level.SEVERE, "", ex );
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
        if ( JythonCompletionProvider.getInstance().settings().isSafeCompletions() ) {
            eval= sanitizeLeaveImports( editor.getText(0, Utilities.getRowStart(editor, editor.getCaretPosition())) );
        } else {
            eval= editor.getText(0, Utilities.getRowStart(editor, editor.getCaretPosition()));
        }

        try {
            Map<String,String> locals= JythonUtil.getLocals( new BufferedReader( new StringReader( eval ) ) );
        } catch ( IOException ex ) {
        }

        // reduce eval so it doesn't call procedures like "plot" and "plotx"
        try {
            eval = JythonUtil.removeSideEffects( new BufferedReader( new StringReader( eval ) ) );
        } catch ( IOException ex ) {
        }

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

                URL imports = JythonOps.class.getResource("imports.py");
                interp.execfile(imports.openStream());

            } else {
                interp = new PythonInterpreter();

                URL imports = JythonOps.class.getResource("imports.py");
                interp.execfile(imports.openStream());
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
                } else {
                    logger.fine("");
                }
                String link = null;
                if (signature != null) {
                    String autoplotDoc= "http://autoplot.org/developer.scripting#";
                    //link = JythonCompletionProvider.getInstance().settings().getDocHome() + signature;
                    link = autoplotDoc + ss;
                }
                rs.addItem(new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label, link));
            }
        }
    }
}
