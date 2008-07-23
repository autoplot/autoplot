package org.das2.jythoncompletion;

import org.python.core.PyClassPeeker;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.Utilities;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import org.das2.jythoncompletion.support.CompletionResultSet;
import org.das2.jythoncompletion.support.CompletionTask;
import org.python.core.PyClass;
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
import org.python.util.PythonInterpreter;
import org.virbo.jythonsupport.JythonOps;

/**
 *
 * @author jbf
 */
public class JythonCompletionTask implements CompletionTask {

    JTextComponent editor;

    private static final String DOC_HOME= "http://www.autoplot.org/javadoc/javadoc/";
            
    public JythonCompletionTask(JTextComponent t) {
        this.editor = t;
    }

    public void query(CompletionResultSet arg0) {
        try {
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
            arg0.finish();
        }
    }

    private Method getJavaMethod( PyMethod m, int i) {
        PyMethodPeeker mpeek= new PyMethodPeeker(m);
        //PyJavaInstancePeeker peek = new PyJavaInstancePeeker((PyJavaInstance) context);
        return new PyReflectedFunctionPeeker(mpeek.getReflectedFunction()).getMethod(i);
        
    }

    private void queryMethods(CompletionContext cc, CompletionResultSet rs) throws BadLocationException {
        PythonInterpreter interp = getInterpreter();

        String eval = editor.getText(0, Utilities.getRowStart(editor, editor.getCaretPosition()));

        interp.exec(eval);

        PyObject context = interp.eval(cc.contextString);

        PyList po2 = (PyList) context.__dir__();
        for (int i = 0; i < po2.__len__(); i++) {
            PyString s = (PyString) po2.__getitem__(i);
            String ss = s.toString();
            if (ss.startsWith(cc.completable)) {
                
                String label = ss;
                String signature= null;
                if ( context instanceof PyClass ) {
                    PyClassPeeker peek= new PyClassPeeker( (PyClass)context );
                    Class dc= peek.getJavaClass();
                    Field f=null;
                    try {
                        f= dc.getField(label);
                    } catch (NoSuchFieldException ex) {
                    } catch (SecurityException ex) {
                    }          
                    if ( f==null ) continue;
                    signature= fieldSignature( f );
                } else if ( context instanceof PyJavaInstance ) {
                    PyJavaInstancePeeker peek = new PyJavaInstancePeeker((PyJavaInstance) context);
                    Class dc= peek.getInstanceClass();
                    Field f=null;
                    try {
                        f= dc.getField(label);
                    } catch (NoSuchFieldException ex) {
                    } catch (SecurityException ex) {
                    }                       
                    if ( f==null ) continue;
                    signature= fieldSignature( f );
                    label= ss;
                } else {
                    PyObject po = context.__getattr__(s);
                
                    if (po instanceof PyReflectedFunction) {
                        label = ss + "() STATIC JAVA";
                    } else if (po.isCallable()) {
                        label = ss + "() " + (context instanceof PyJavaInstance ? "JAVA" : "");
                        PyMethod m = (PyMethod) po;
                        Method jm= getJavaMethod(m,0);
                        signature= methodSignature( getJavaMethod(m, 0) );
                    } else {
                        System.err.println("");
                    }
                }
                String link = null;
                if ( signature!=null ) {
                    link = DOC_HOME + signature;
                }
                rs.addItem(new DefaultCompletionItem(ss, cc.completable.length(), ss, label, link));
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
                if (po instanceof PyReflectedFunction) {
                    label = ss + "() JAVA";
                    PyReflectedFunction prf = (PyReflectedFunction) po;
                    PyReflectedFunctionPeeker peek = new PyReflectedFunctionPeeker(prf);

                    signature = methodSignature( peek.getMethod(0) );

                } else if (po.isCallable()) {
                    label = ss + "() ";
                } else if (po.isNumberType()) {
                    label = ss + " =" + po;
                } else {
                    System.err.println("");
                }
                String link = null;
                if (signature != null) {
                    link = DOC_HOME + signature;
                }
                rs.addItem(new DefaultCompletionItem(ss, cc.completable.length(), ss, label, link));
            }
        }

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
        
        
        for (Class arg : javaMethod.getParameterTypes() ) {
            sargs.add(arg.getCanonicalName());
        }
        sig.append(join(sargs, "," + SPACE));
        sig.append(RPAREN);
        return sig.toString();
    }
    
    private String fieldSignature( Field f ) {
        String javadocPath = join(f.getDeclaringClass().getCanonicalName().split("\\."), "/") + ".html";

        StringBuffer sig = new StringBuffer(javadocPath);

        sig.append("#" + f.getName() );
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
     * return an interpretter to match the one the user's code lives in.
     * @return
     */
    private PythonInterpreter getInterpreter() {
        try {
            
            PythonInterpreter interp = new PythonInterpreter();

            URL imports = JythonOps.class.getResource("imports.py");
            interp.execfile(imports.openStream());

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
