package org.das2.jythoncompletion;

import java.util.logging.Level;
import javax.swing.text.Document;
import org.python.core.PyClassPeeker;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.Utilities;

/*
 * This is the engine that does the Jython completions.  We figure out the
 * context and the completable, then query a interpreter.
 */
import org.das2.jythoncompletion.support.AsyncCompletionQuery;
import org.das2.jythoncompletion.support.CompletionResultSet;
import org.das2.jythoncompletion.support.CompletionTask;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.FileSystemSettings;
import org.python.core.PyClass;
import org.python.core.PyException;
import org.python.core.PyInstance;
import org.python.core.PyJavaClass;
import org.python.core.PyJavaInstance;
import org.python.core.PyJavaInstancePeeker;
import org.python.core.PyList;
import org.python.core.PyMethod;
import org.python.core.PyMethodPeeker;
import org.python.core.PyObject;
import org.python.core.PyReflectedFunction;
import org.python.core.PyReflectedFunctionPeeker;
import org.python.core.PyString;
import org.python.core.PyStringMap;
import org.python.core.PyType;
import org.python.util.PythonInterpreter;
import org.virbo.jythonsupport.JythonOps;

/**
 *
 * @author jbf
 */
public class JythonCompletionTask implements CompletionTask {

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

    private void queryMethods(CompletionContext cc, CompletionResultSet rs) throws BadLocationException {
        PythonInterpreter interp;

        interp = getInterpreter();

        String eval = editor.getText(0, Utilities.getRowStart(editor, editor.getCaretPosition()));

        //kludge to handle increase in indent level
        if (eval.endsWith(":\n")) {
            eval = eval + "  pass\n";
        }

        Logger logger = Logger.getLogger(JythonCompletionTask.class.getName());

        interp.exec(eval);

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
            e.printStackTrace();
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
                    System.err.println("PyException from \"" + ss + "\":");
                    e.printStackTrace();
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
                        } catch ( RuntimeException ex ) {
                            continue;
                        }
                        signature = methodSignature(jm);
                        args = methodArgs(jm);
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
                                logger.finest("NoSuchFieldException for item " + s);
                            } catch (SecurityException ex) {
                                logger.finest("SecurityException for item " + s);
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
                } else {
                    if (po instanceof PyReflectedFunction) {
                        label = ss + "() STATIC JAVA";
                    } else if (po.isCallable()) {
                        label = ss + "() " + (context instanceof PyJavaInstance ? "JAVA" : "");
                        PyMethod m = (PyMethod) po;
                        Method jm = getJavaMethod(m, 0);
                        signature = methodSignature(getJavaMethod(m, 0));
                    } else {
                        System.err.println("");
                    }
                }
                String link = null;
                if ( signature != null) {
                    if ( signature.startsWith("javax") || signature.startsWith("java") ) {
                        link= "http://java.sun.com/j2se/1.5.0/docs/api/" + signature.replaceAll(",", ", ");
                    } else if ( signature.startsWith("org/das2/")) {
                        link= "http://www-pw.physics.uiowa.edu/das2/javadoc/" + signature;
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
                "list.remove('__name__')\n" +
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

    private static String join(String[] list, String delim) {
        return join(Arrays.asList(list), delim);
    }

    private static String join(List<String> list, String delim) {
        if (list.size() == 0) {
            return "";
        } else {
            StringBuffer result = new StringBuffer(list.get(0));
            for (int i = 1; i < list.size(); i++) {
                result.append(delim).append(list.get(i));
            }
            return result.toString();
        }

    }

    private void queryNames(CompletionContext cc, CompletionResultSet rs) throws BadLocationException {

        String[] keywords = new String[]{"assert", "def", "elif", "except", "from", "for", "finally", "import", "while", "print", "raise"}; //TODO: not complete
        for (String kw : keywords) {
            if (kw.startsWith(cc.completable)) {
                rs.addItem(new DefaultCompletionItem(kw, cc.completable.length(), kw, kw, null, 0));
            }
        }

        PythonInterpreter interp = getInterpreter();

        String eval = editor.getText(0, Utilities.getRowStart(editor, editor.getCaretPosition()));

        interp.exec(eval);
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
                    System.err.println("");
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

    private String methodArgs(Method javaMethod) {

        //String LPAREN="%28";
        String LPAREN = "(";
        //String RPAREN="%29";
        String RPAREN = ")";
        String SPACE = " "; // "%20";

        StringBuffer sig = new StringBuffer();

        sig.append(LPAREN);
        List<String> sargs = new ArrayList<String>();

        for (Class arg : javaMethod.getParameterTypes()) {

            sargs.add(arg.getSimpleName());
        }
        sig.append(join(sargs, "," ));
        sig.append(RPAREN);
        return sig.toString();
    }

    private String methodSignature(Method javaMethod) {
        String javadocPath = join(javaMethod.getDeclaringClass().getCanonicalName().split("\\."), "/") + ".html";

        StringBuffer sig = new StringBuffer(javadocPath);
        //String LPAREN="%28";
        String LPAREN = "(";
        //String RPAREN="%29";
        String RPAREN = ")";
        String SPACE = " "; // "%20";

        sig.append("#" + javaMethod.getName() + LPAREN);
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

        StringBuffer sig = new StringBuffer(javadocPath);

        sig.append("#" + f.getName());
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
}
