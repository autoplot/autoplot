package org.das2.jythoncompletion;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.Utilities;
import org.autoplot.jythonsupport.DatasetCommand;
import org.autoplot.jythonsupport.GetDataSetCommand;
import org.autoplot.jythonsupport.GetDataSetsCommand;
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
import org.autoplot.jythonsupport.JythonToJavaConverter;
import org.autoplot.jythonsupport.SimplifyScriptSupport;
import org.autoplot.jythonsupport.ui.EditorTextPane;
import org.autoplot.jythonsupport.ui.ScriptPanelSupport;
import org.das2.graph.GraphUtil;
import org.das2.util.monitor.NullProgressMonitor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.python.core.PyArray;
import org.python.core.PyFloat;
import org.python.core.PyReflectedField;

/**
 * Completions for Jython code.  The completion task is created with the
 * editor configured for completions (code and caret position within code),
 * and "query" is called which will fill a CompletionResultSet.
 * @author jbf
 * @see  org.das2.jythoncompletion.JythonCompletionProvider
 */
public class JythonCompletionTask implements CompletionTask {

    private static final Logger logger= LoggerManager.getLogger("jython.editor.completion");
            
    private static final ImageIcon LOCALVARICON= new ImageIcon( JythonCompletionTask.class.getResource("ui/localVariable.png") );
    private static final ImageIcon JAVA_CLASS_ICON= new ImageIcon( JythonCompletionTask.class.getResource("ui/javaClass.png") );
    private static final ImageIcon JYTHONCOMMANDICON= new ImageIcon( JythonCompletionTask.class.getResource("ui/jythonCommand.png") );
    private static final ImageIcon JAVA_JYTHON_METHOD_ICON= new ImageIcon( JythonCompletionTask.class.getResource("ui/javaJythonMethod.png") );
    private static final ImageIcon JAVA_FIELD_ICON= new ImageIcon( JythonCompletionTask.class.getResource("ui/javaStaticField.png") );
    private static final ImageIcon JAVA_METHOD_ICON= new ImageIcon( JythonCompletionTask.class.getResource("ui/javaMethod.png") );
    private static final ImageIcon JAVA_STATIC_METHOD_ICON= new ImageIcon( JythonCompletionTask.class.getResource("ui/javaStaticMethod.png") );
    private static final ImageIcon JAVA_CONSTRUCTOR_ICON= new ImageIcon( JythonCompletionTask.class.getResource("ui/javaConstructor.png") );
    
    private static final int JYTHONCOMMAND_SORT = 2;
    private static final int JAVAMETHOD_SORT = 1;
    private static final int JAVACLASS_SORT = 1;
    private static final int PYREFLECTEDFIELD_SORT = 3;
    private static final int PYCLASS_SORT = 3;
    private static final int LOCALVAR_SORT = -10;
    private static final int AUTOVAR_SORT = -3;
    private static final int AUTOCOMMAND_SORT = -2;
    private static final int AUTOVARHIDE_SORT = 9;
    private static final int JAVASTATICFIELD_SORT=1;
    
    public static final String CLIENT_PROPERTY_INTERPRETER_PROVIDER = "JYTHON_INTERPRETER_PROVIDER";
    public static final String CLIENT_PROPERTY_PWD = "JYTHON_INTERPRETER_PWD";
    
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
            //HERE is a nice place for a breakpoint
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
     * perform the completions query.  This is the heart of Jython completions.
     * @param cc
     * @param resultSet
     * @return the count
     */
    public int doQuery( CompletionContext cc, CompletionResultSet resultSet ) {
        int c=0;
        try {
            switch (cc.contextType) {
                case CompletionContext.MODULE_NAME:
                    c= queryModules(cc, resultSet);
                    break;
                case CompletionContext.PACKAGE_NAME:
                    c= queryPackages(cc, resultSet);
                    break;
                case CompletionContext.DEFAULT_NAME:
                    c= queryNames(cc, resultSet);
                    break;
                case CompletionContext.METHOD_NAME:
                    c= queryMethods(cc, resultSet);
                    break;
                case CompletionContext.STRING_LITERAL_ARGUMENT:
                    c= queryStringLiteralArgument(cc, resultSet);
                    break;
                case CompletionContext.COMMAND_ARGUMENT:
                    c= queryCommandArgument(cc, resultSet);
                    c+= queryNames(cc, resultSet);
                    break;
                case CompletionContext.CLASS_METHOD_NAME:
                    c= queryClassMethods( cc, resultSet );
                    break;
                default:
                    break;
            }
        } catch ( BadLocationException ex ) {
            logger.log( Level.WARNING, null, ex );
            if ( resultSet!=null ) resultSet.addItem( new MessageCompletionItem( ex.getMessage() ) );
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

    private int queryClassMethods(CompletionContext cc, CompletionResultSet rs) {
        int count= 0;
        Class c= cc.getContextObjectClass();
        while ( c!=null && c!=Object.class ) {
            Method[] mm= c.getDeclaredMethods();
            for ( Method m: mm ){
                if ( m.getName().startsWith(cc.completable) ) {
                    String signature = methodSignature(m);
                    String args = methodArgs(m);
                    String ss= m.getName();
                    String label= ss + args;
                    String link = getLinkForJavaSignature(signature);
                    rs.addItem(new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label, link));
                    count++;
                }
            }
            c= c.getSuperclass();
        }
        return count;
    }
    
    /**
     * remove getProp and setProp and replace with just "prop"
     * @param po2
     * @return 
     */
    private List<String> reduceGetterSetters( PyObject lcontext, PyList po2, boolean cullGetterSetters ) {
        Map<String,String> mm= new LinkedHashMap<>();
        for (int i = 0; i < po2.__len__(); i++) {
            PyString s = (PyString) po2.__getitem__(i);
            mm.put( s.toString(), s.toString() );
        }
        
        if ( cullGetterSetters ) {
            List<String> ss=  new ArrayList<>( mm.keySet() );
            for ( String s: ss ) {
                if ( s.startsWith("set") ) {
                    String prop= s.substring(3);
                    if ( prop.length()==0 ) {
                        continue;
                    }
                    if ( mm.get("get"+prop )!=null ) {
                        String propName= Character.toLowerCase( prop.charAt(0) ) + prop.substring(1);
                        if ( mm.containsKey(propName) ) {
                            mm.remove("get"+prop);
                            mm.remove("set"+prop);                
                        }
                    } else if ( mm.get("is"+prop )!=null ) {
                        String propName= Character.toLowerCase( prop.charAt(0) ) + prop.substring(1);
                        if ( mm.containsKey(propName) ) {
                            mm.remove("is"+prop);
                            mm.remove("set"+prop);
                        }
                    }
                }
            }
        }
        
        return new ArrayList<>( mm.keySet() );
                
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
        
        try {
            interp.exec(JythonRefactory.fixImports(eval));
        } catch ( PyException ex ) {
            // something bad has happened, remove side effects (we might have done this already) and try again.
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

        PyObject lcontext=null;
        PyJavaClass lcontextClass=null;
        
        boolean fromArray= false;
        
        try {
            lcontext = interp.eval(cc.contextString);
        } catch (PyException ex) {
            try {
                if ( cc.contextString.endsWith("]") ) {
                    int k= cc.contextString.lastIndexOf("[");
                    if ( k>-1 ) {
                        PyObject occ= interp.eval(cc.contextString.substring(0,k));
                        if ( occ instanceof PyArray ) {
                            PyArray pa= (PyArray)occ;
                            Object o= pa.getArray();
                            Class oc= o.getClass();
                            if ( oc.isArray() ) {
                                lcontextClass= PyJavaClass.lookup( oc.getComponentType() );
                                try {
                                    lcontext = new PyJavaInstance( oc.getComponentType().getDeclaredConstructors()[0].newInstance() );
                                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex1) {
                                    Logger.getLogger(JythonCompletionTask.class.getName()).log(Level.SEVERE, null, ex1);
                                }
                                fromArray= true;
                            }
                        }
                    }
                }
                // check to see if we have identified the class of the symbol.
                if ( lcontextClass==null ) {
                    PyObject occ= interp.eval(cc.contextString+__CLASSTYPE);
                    if ( occ!=null && occ instanceof PyJavaClass ) {
                        lcontextClass= (PyJavaClass)occ;
                    } else {
                        rs.addItem(new MessageCompletionItem("EVAL error: " + cc.contextString, ex.toString()));
                        return 0;
                    }
                }
            } catch ( PyException ex2 ) {
                rs.addItem(new MessageCompletionItem("Eval error: " + cc.contextString, ex.toString()));
                return 0;
            }
        }

        if ( lcontext==null ) {
            logger.log(Level.FINE, "completions have the class but not the instance to work with: {0}", lcontextClass.__name__);
            lcontext= lcontextClass;
        }
        
        PyList po2;
        try {
            po2= (PyList) lcontext.__dir__();
        } catch ( PyException e ) {
            logger.log( Level.SEVERE, e.getMessage(), e );
            return 0;
        }
        
        List<String> po3= reduceGetterSetters( lcontext, po2, fromArray || ( lcontext!=lcontextClass ) );
                
        int count=0;
        for (int i = 0; i < po3.size(); i++) {
            String ss = po3.get(i);
            logger.log(Level.FINEST, "does {0} start {1}", new Object[] { cc.completable, ss } );
            if (ss.startsWith(cc.completable)) {
                boolean notAlreadyAdded= true;
                PyObject po;
                try {
                    po = lcontext.__getattr__(ss);
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
                ImageIcon icon= null;
                if (lcontext instanceof PyJavaClass) {
                    if (po.getClass().toString().equals( "class org.python.core.PyReflectedConstructor" ) ) {
                        args= "()";
                        signature= "";
                    } else if (po instanceof PyReflectedFunction) {
                        Method m = new PyReflectedFunctionPeeker((PyReflectedFunction) po).getMethod(0);
                        if ( Modifier.isStatic( m.getModifiers() ) ) {
                            signature = methodSignature(m);
                            icon= getIconFor(m);
                            args = methodArgs(m);
                        } else {
                            if ( lcontext==lcontextClass ) { // whoops, we have an instance of a class here
                                signature = methodSignature(m);
                                icon= getIconFor(m);
                                args = methodArgs(m);
                            } else {
                                continue;
                            }
                        }
                    } else if ( po instanceof PyString || po instanceof PyInteger || po instanceof PyJavaInstance) {
                        Class c= new PyClassPeeker((PyJavaClass) lcontext).getJavaClass();
                        try {
                            Field f = c.getField(ss);
                            signature= fieldSignature(f);
                            icon= getIconFor(f);
                        } catch ( NoSuchFieldException ex ) {   
                        }
                    }
                } else if ( lcontext instanceof PyJavaPackage ) {
                    if (po instanceof PyJavaClass) {
                        Class dc = getJavaClass((PyJavaClass)po);
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
                    if ( dc==null ) {
                        logger.fine("unable to identify JavaClass");
                        signature = "" + lcontext.__getattr__(label);
                    } else {
                        Field f = null;
                        try {
                            f = dc.getField(label);
                        } catch (NoSuchFieldException | SecurityException ex) {
                        }
                        if (f == null) {
                            continue;
                        }
                        signature = fieldSignature(f);
                    }
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
                                icon= getIconFor(jm);
                                String link = getLinkForJavaSignature(signature);
                                rs.addItem( new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label, link, JAVAMETHOD_SORT, icon ) );
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
                                logger.log(Level.FINEST, "NoSuchFieldException for item {0}", ss);
                            } catch (SecurityException ex) {
                                logger.log(Level.FINEST, "SecurityException for item {0}", ss);
                            }
                            if (f == null) continue;
                            icon= getIconFor(f);
                            //TODO: don't include static fields in list.
                            signature = fieldSignature(f);
                            boolean showValues=false;
                            if ( showValues ) {
                                if ( po instanceof PyInteger ) {
                                    label= ss + " = " + po.toString();
                                } else if ( po instanceof PyFloat ) {
                                    label= ss + " = " + po.toString();
                                } else if ( po instanceof PyString ) {
                                    label= ss + " = " + po.toString();
                                } else {
                                    label = ss;
                                }
                            } else {
                                label = ss;
                            }
                        }
                    }
                } else {
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
                }
                if ( notAlreadyAdded ) {
                    if ( signature!=null && signature.startsWith("inline:") ) {
                        rs.addItem(new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label, signature));
                    } else {
                        String link = getLinkForJavaSignature(signature);
                        if ( icon==null ) {
                            rs.addItem(new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label, link, JAVAMETHOD_SORT, null ) );
                        } else {
                            rs.addItem(new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label, link, JAVAMETHOD_SORT, icon ) );
                        }
                        
                    }
                    count++;
                }
            }
        }
        return count;
    }
    public static final String __CLASSTYPE = "__CLASSTYPE";

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
            if ( rs!=null ) rs.addItem(new MessageCompletionItem("Eval error in code before current position", ex.toString()));
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
                if ( rs!=null ) rs.addItem(new DefaultCompletionItem(ss, cc.completable.length(), ss, ss, link));
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
        
        if ( cc.completable.equals("import") ) {
            if ( rs!=null ) rs.addItem(new DefaultCompletionItem( " ", 0, " ", "space", null ));
            return 1;
        }
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
                if ( rs!=null ) rs.addItem(new MessageCompletionItem("Eval error in code before current position", ex.toString()));
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
                    if ( rs!=null ) rs.addItem(new DefaultCompletionItem(ss, cc.completable.length(), ss, ss, link ));
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
                        String link= "http://www-pw.physics.uiowa.edu/~jbf/autoplot/javadoc2018/" + ss.replaceAll("\\.","/") + "/package-summary.html";
                        if ( rs!=null ) rs.addItem(new DefaultCompletionItem(ss, search.length(), ss, ss, link ));
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
    
    public static String getLastLine( String script ) {
        int i = script.lastIndexOf("\n");
        if ( i==-1 ) return script; // just one line
        String l= script.substring(i+1); // +1 is for the new line
        String s= l.trim();
        if ( s.length()==0 ) return ""; // on an empty line
        LinkedList<String> lastLine= new LinkedList<>();
        int i1= script.indexOf(s,i);
        String indent= script.substring(i+1,i1);
        lastLine.add( 0, l );
        int i2= script.lastIndexOf("\n",i-1);
        String l2= script.substring(i2+1,i);
        lastLine.add( 0, l2 );
        while ( l2.startsWith(indent) ) {
            i= i2;
            i2= script.lastIndexOf("\n",i-1);
            l2= script.substring(i2+1,i);
            lastLine.add( 0, l2 );
        }
        return String.join( "\n",lastLine );
    }
    
    /**
     * introduced to see if we can pop a little code from the end, in case we
     * are within a triple-quoted string.
     * @param script the script
     * @return the script, possibly with a few fewer lines.
     * @see SimplifyScriptSupport#alligatorParse(java.lang.String) 
     */
    public static String trimLinesToMakeValid( String script ) {
        return SimplifyScriptSupport.alligatorParse(script);
    }
    
    private int queryNames(CompletionContext cc, CompletionResultSet rs) throws BadLocationException {
        logger.fine("queryNames");
        int count=0;
        String[] keywords = new String[]{ "def", "elif", "except", "from", "for", "finally", "import", "while", "print", "raise"}; //TODO: not complete
        for (String kw : keywords) {
            if (kw.startsWith(cc.completable)) {
                if ( rs!=null ) rs.addItem(new DefaultCompletionItem(kw, cc.completable.length(), kw, kw, null, JYTHONCOMMAND_SORT, JYTHONCOMMANDICON));
                count++;
            }
        }

        PythonInterpreter interp = getInterpreter();

        String eval;
        int eolnCarot= Utilities.getRowStart(editor, editor.getCaretPosition());
        eval= editor.getText(0, eolnCarot);
        eval= trimLinesToMakeValid( eval );
        if ( eolnCarot>0 ) {
            int startLastLine= Utilities.getRowStart(editor, eolnCarot-1 );
            String lastLine= editor.getText( startLastLine, eolnCarot-startLastLine );
            Matcher m= Pattern.compile("(\\s*)(\\S+).*(\\s)*").matcher(lastLine);
            if ( m.matches() ) {
                int i= m.group(1).length();
                String indent= lastLine.substring(0,i);
                if ( !eval.endsWith("\n") ) {
                    eval= eval + "\n" + indent + "__dummy__=1\n";
                } else {
                    eval= eval + indent + "__dummy__=1\n";
                }
            }
        }
        
        if ( JythonCompletionProvider.getInstance().settings().isSafeCompletions() ) {
            try {
                eval= sanitizeLeaveImports( eval );
            } catch ( Exception ex ) {
                // adding __dummy__ didn't work, so start removing lines at the end.
                eval= editor.getText(0, eolnCarot);
                eval= trimLinesToMakeValid( eval );
                eval= sanitizeLeaveImports( eval );
            }
        } 
        
        try {
            interp.exec( JythonRefactory.fixImports(eval) );
        } catch ( PyException ex ) {
            String message= "<html><p>Code completions couldn't run on a simplified version of the script.  This may"
                + " due to a bug in the simplification process, or there may be a bug in the script. "
                + "The error is shown below, and the simplified script can be reveiwed using "
                + "Actions&rarr;Developer&rarr;\"Show Simplified Script used for Completions.\"</p><br><hr><code>"+ex.toString()+"</code>";
            if ( rs!=null ) rs.addItem( new MessageCompletionItem("Eval error in code before current position", message));
            int nlocal=  getLocalsCompletions( interp, cc, rs);
            int nimportable;
            if ( cc.completable.length()>0 ) {
                nimportable= getImportableCompletions( eval, cc, rs );
            } else {
                nimportable= 0;
            }
            return count + nlocal + nimportable + 1;
            
        } catch (IOException ex) {
            if ( rs!=null ) rs.addItem(new MessageCompletionItem("Error with completions",ex.toString()));
            return 0;
        }
        
        int nlocal=  getLocalsCompletions( interp, cc, rs);
        int nimportable;
        if ( cc.completable.length()>0 ) {
            nimportable= getImportableCompletions( eval, cc, rs );
        } else {
            nimportable= 0;
        }
        return count + nlocal + nimportable;
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
        Map<String,Object> r= DataSetUrlCompletionTask.popString(editor,pos);
        String s= (String)r.get("string");
        
        String pwd=null;
        if ( editor!=null ) {
            pwd = (String)editor.getClientProperty( CLIENT_PROPERTY_PWD );
            if ( pwd!=null ) {
                if ( !pwd.endsWith("/") ) pwd=null;
            }
        }
        if (method.equals("getDataSet") || method.equals("getFile") || method.equals("plot") || method.equals("plotx") || method.equals("getCompletions") ) {
            DataSetUrlCompletionTask task = new DataSetUrlCompletionTask(editor);
            task.query(arg0);
        } else if ( method.equals("'resourceURI'") ) {
            DataSetUrlCompletionTask task = new DataSetUrlCompletionTask(editor);
            task.query(arg0);
        } else if ( method.equals("PWD") ) {  // PWD + "demo.dat"
            // how to find the name of the file we are editing?
            if ( editor != null ) {
                if ( pwd!=null ) {
                    DataSetUrlCompletionTask task = new DataSetUrlCompletionTask(editor);
                    task.query(arg0);
                }
                return 0;
            }
        } else if ( method.startsWith("/") || method.startsWith("http" ) ) {
            DataSetUrlCompletionTask task = new DataSetUrlCompletionTask(editor);
            task.query(arg0);
            return 0;
        } else if ( s.startsWith("/") || s.startsWith("http://") || s.startsWith("https://") 
                || s.startsWith("file:/") || s.startsWith("sftp://") ) {
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

        try {
            PyObject po= interp.eval(method);
            PyObject doc= interp.eval(method+".__doc__");
            PyObject completions;
            try {
                completions = interp.eval(method+".__completions__");
            } catch ( PyException ex ) {
                completions = null;
            }
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
                if ( completions!=null ) {
                    try {
                        JSONObject jo= new JSONObject( completions.toString() );
                        JSONArray kws= jo.getJSONArray("keywords");
                        for ( int i=0; i<kws.length(); i++ ) {
                            JSONObject kw = kws.getJSONObject(i);
                            String name= kw.getString("name");
                            if ( name.startsWith(cc.completable) ) {
                                String docs= kw.optString("description");
                                DefaultCompletionItem item= new DefaultCompletionItem( name, cc.completable.length(), name, name, "inline:"+docs );
                                item.sortPriority= -100;
                                item.icon= JAVA_JYTHON_METHOD_ICON;
                                result.addItem( item );
                            }
                        }
                    } catch (JSONException ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                }
                MessageCompletionItem item= new MessageCompletionItem( method, signature );
                result.addItem( item );
            }
        } catch ( RuntimeException ex ) {
            return 0;
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
            
            interp.set("PWD","file:/tmp/");
            interp.set("dataset", new DatasetCommand());
            interp.set("getDataSet", new GetDataSetCommand() );
            interp.set("getDataSets", new GetDataSetsCommand() );
            interp.set("monitor", new NullProgressMonitor());
            
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

    /**
     * get the locals completions, populating the result set
     * @param interp the interpreter
     * @param cc the completion context
     * @param rs the result set object which will contain the completions
     * @return the number of completions found.
     * @see #getLocalsCompletions(org.python.util.PythonInterpreter, org.das2.jythoncompletion.CompletionContext) 
     */
    public static int getLocalsCompletions(PythonInterpreter interp, CompletionContext cc, CompletionResultSet rs) {
        int count= 0;
        List<DefaultCompletionItem> rr= getLocalsCompletions( interp, cc );
        for ( DefaultCompletionItem item: rr ) {
            if ( rs!=null ) rs.addItem( item );
            count++;
        }
        return count;
    }
    
    /**
     * get completions by looking at importLookup.jy, which is a list of commonly imported codes.
     * @param source the script source.
     * @param cc
     * @param result
     * @return 
     */
    public static int getImportableCompletions( String source, CompletionContext cc, CompletionResultSet result ) {
        int count= 0;
        List<String> completions= JythonToJavaConverter.guessCompletions(cc.completable);
        for ( String ss: completions ) {
            String pkg= JythonToJavaConverter.guessPackage(ss);
            if ( !JythonToJavaConverter.hasImport( source, pkg, ss ) ) {
                String javaClass= pkg+"."+ss;
                String signature= join( javaClass.split("\\."), "/") + ".html";
                String link= JavadocLookup.getInstance().getLinkForJavaSignature(signature);
                ClassImportCompletionItem ci= 
                        new ClassImportCompletionItem( 
                                cc.completable, cc.completable.length(), 
                                ss, ss + " and import from " + pkg, link, 
                                0, JAVA_CLASS_ICON, pkg, ss );
                result.addItem( ci );
            }
            count++;
        }
        return count;
    }
    
    /**
     * replace java names like org.das2.qds.QDataSet with less-ominous names like "QDataSet"
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
    private static String getPyJavaClassSignature( PyJavaClass pf ) {
        Class javaClass= getJavaClass(pf);
        return javaClass.getCanonicalName().replaceAll("\\.","/");
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
            Method method1= peek.getMethod(jj);
            String signature = methodSignature(method1);
            String args = methodArgs(method1);
            int j= signature.indexOf("#");
            String label= ss + "() JAVA";
            if ( j>-1 ) {
                label= signature.substring(j+1);
                label= hideJavaPaths( label );
                Class ret= method1.getReturnType();
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
    
    /**
     * At some point we decided all the methods would take Object as well as QDataSet, and then convert
     * to these.  This should be discouraged, and hide these in the popups.
     * @param m1
     * @param m2
     * @return 
     */
    private static boolean methodIsSuperset( String m1, String m2 ) {
        Pattern p0= Pattern.compile("([a-zA-Z0-9/]*\\.html)#([a-zA-Z0-9]*)\\((([a-zA-Z0-9\\.\\[\\]]+)?(,([a-zA-Z0-9\\.\\[\\]]+))*)\\)" );
        Matcher m8= p0.matcher(m1);
        Matcher m9= p0.matcher(m2);
        if ( m8.matches() && m9.matches() ) {
            String s1= m8.group(3);
            String s2= m9.group(3);
            String[] s8= s1.split(",",-2);
            String[] s9= s2.split(",",-2);
            if ( s8.length==s9.length ) {
                boolean superSet= true;
                for ( int i=0; i<s8.length; i++ ) {
                    if ( !s8[i].equals("java.lang.Object") && !s8[i].equals(s9[i]) ) {
                        superSet=false;
                    }
                }
                return superSet;
            }
        }
        return false;
    }
    
    public static void reduceObject( List<String> signatures, List<String> labels, List<String> argss ) {
        if ( signatures.size()>1 ) {
            for ( int i=1; i<signatures.size(); i++ ) {
                if ( methodIsSuperset( signatures.get(0), signatures.get(i) ) ) {
                    signatures.remove(0);
                    labels.remove(0);
                    argss.remove(0);
                    break;
                }
            }
        }
    }

    /**
     * get the list of available names at this point in the code.
     * @param interp the interpreter
     * @param cc the completion context
     * @return the list of completions.
     */
    public static List<DefaultCompletionItem> getLocalsCompletions(PythonInterpreter interp, CompletionContext cc) {
        
        logger.log(Level.FINE, "get local completions for completable: {0}", cc.completable);
        List<DefaultCompletionItem> result= new ArrayList();
        
        PyStringMap locals = (PyStringMap) interp.getLocals();
        
        PyList po2 = locals.keys();
        
        for (int i = 0; i < po2.__len__(); i++) {
            ImageIcon icon= null;
            PyString s = (PyString) po2.__getitem__(i);
            String ss = s.toString();
            String signature = null; // java signature
            List<String> signatures= new ArrayList();
            List<String> argss= new ArrayList();
            if (ss.startsWith(cc.completable)) {
                if ( ss.endsWith(__CLASSTYPE) ) {
                    ss= ss.substring(0,ss.length()-__CLASSTYPE.length());
                    if ( !ss.startsWith(cc.completable) ){
                        continue;
                    } else {
                        result.add( new DefaultCompletionItem(ss, cc.completable.length(), ss, ss, null, LOCALVAR_SORT, LOCALVARICON ) );
                        continue;
                    }
                }
                logger.log(Level.FINER, "found completion item: {0}", ss);
                boolean allStatic= false;  // true if the completion is a utility class.
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
                            Class jclass= getJavaClass((PyJavaClass)po);
                            String n= jclass.getCanonicalName();
                            allStatic= true;
                            logger.log(Level.FINER, "check for non-static methods: {0}", n);
                            Method[] mm= jclass.getMethods();
                            for ( Method m: mm ) {
                                if ( !m.getDeclaringClass().equals(Object.class) ) {
                                    if ( !Modifier.isStatic(m.getModifiers()) ) {
                                        logger.log(Level.FINEST, "not static: {0}", m.getName());
                                        allStatic= false;
                                    }
                                }
                            }   
                            logger.log(Level.FINER, "  class is all static methods: {0}", allStatic );
                            if ( allStatic ) {
                                doConstructors(jclass.getConstructors(),labels,signatures,n,argss);
                                for ( int i1=0; i1<argss.size(); i1++ ) {
                                    argss.set(i1,"");
                                }
                            } else {
                                doConstructors(jclass.getConstructors(),labels,signatures,n,argss);
                            }
                            icon= JAVA_CONSTRUCTOR_ICON;
                            //signature=  join( n.split("\\."), "/") + ".html#"+ jclass.getSimpleName() + "()";
                            break;
                        case "javapackage":
                            label = ss;
                            break;
                        default:
                            //TODO: check for PyFloat, etc.
                            String sss= po.toString();
                            if ( po instanceof PyJavaInstance ) {
                                Object jo= po.__tojava__(Object.class);
                                if ( jo instanceof org.das2.qds.QDataSet ) { //TODO: mark it so we know it is a placeholder.
                                    sss= "dataset";
                                } else {
                                    sss= jo.toString();
                                }
                            }
                            if ( sss.contains("<") ) { // it's not what I think it is, a number
                                label = ss;
                            } else {
                                label = ss + " = " + sss;
                            }   
                            break;
                    }
                } else if ( po instanceof PyJavaClass ) {
                    
                } else {
                    logger.log(Level.FINE, "skipping {0}", ss);
                }
                   
                keySort( signatures, signatures, labels, argss );
                
                if ( !signatures.isEmpty() ) {
                    
                    String objectRemoved= "";
                    int n= signatures.size();
                    reduceObject( signatures, labels, argss );
                    if ( signatures.size()!=n ) {
                        objectRemoved= "*";
                    }
                    
                    for ( int jj= 0; jj<signatures.size(); jj++ ) {
                        signature= signatures.get(jj);
                        label= labels.get(jj)+objectRemoved;
                        String link = null;
                        if (signature != null) {
                            link= getLinkForJavaSignature(signature);  // TODO: inner class like Rectangle.Double is only Double
                        }
                        if ( ss.equals("dom") ) {
                            link= "http://autoplot.org/developer.scripting#DOM";
                        }
                        logger.log(Level.FINER, "DefaultCompletionItem({0},{1},\n{2}{3},\n{4},\n{5})", new Object[]{ss, cc.completable.length(), ss, argss.get(jj), label, link});
                        result.add( new DefaultCompletionItem(ss, cc.completable.length(), ss + argss.get(jj), label, link, JAVAMETHOD_SORT, icon ) );
                    }
                } else {
                    String link = null;
                    if ( signature!=null && signature.startsWith("inline:") ) {
                        link= signature;
                    } else if ( ss.equals("dom") ) {
                        link= "http://autoplot.org/developer.scripting#DOM";
                    } else if (signature != null) {
                        link= getLinkForJavaSignature(signature);
                    } else if ( po instanceof PyJavaClass ) {
                        link= getLinkForJavaSignature( getPyJavaClassSignature( (PyJavaClass)po ) );
                    }
                    if ( po instanceof PyString ) {
                        if ( ss.equals("PWD") ) {
                            result.add( new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label, link, LOCALVAR_SORT, LOCALVARICON ) );
                        } else if ( !ss.equals("__name__") ) {
                            result.add( new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label+" -> "+po+"", link, LOCALVAR_SORT, LOCALVARICON ) );
                        } else {
                            result.add( new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label+" -> "+po+"", link, JAVASTATICFIELD_SORT, null ) );
                        }
                    } else {
                        if ( allStatic ) {
                            result.add( new DefaultCompletionItem(ss, cc.completable.length(), ss + args + ".", label, link, JAVACLASS_SORT, JAVA_CLASS_ICON) );
                        } else {
                            if ( po instanceof PyJavaClass ) {
                                result.add( new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label, link, JAVACLASS_SORT, JAVA_CONSTRUCTOR_ICON ) );
                            } else if ( po.getType().toString().contains("Command") ) { // TODO: FIX THIS
                                if ( ss.equals("plotx") ) continue;
                                result.add( new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label, link, AUTOCOMMAND_SORT, JAVA_JYTHON_METHOD_ICON ) );
                            } else if ( po instanceof PyFunction ) {
                                result.add( new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label, link, AUTOCOMMAND_SORT, JAVA_JYTHON_METHOD_ICON ) );
                            } else if ( po instanceof PyClass ) {
                                result.add( new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label, link, PYCLASS_SORT, JAVA_JYTHON_METHOD_ICON ) );
                            } else if ( po instanceof PyReflectedField ) {
                                result.add( new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label, link, PYREFLECTEDFIELD_SORT, JAVA_JYTHON_METHOD_ICON ) );
                            } else {
                                switch (ss) {
                                    case "monitor":
                                    case "dom":
                                    case "PI":
                                    case "TAU":
                                    case "E":
                                        result.add( new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label, link, AUTOVAR_SORT, LOCALVARICON ) );
                                        break;
                                    case "params":
                                    case "outputParams":
                                    case "__doc__":
                                        //things I don't want developers to see
                                        result.add( new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label, link, AUTOVARHIDE_SORT, LOCALVARICON ) );
                                        break;
                                    default:
                                        result.add( new DefaultCompletionItem(ss, cc.completable.length(), ss + args, label, link, LOCALVAR_SORT, LOCALVARICON ) );
                                        break;
                                }
                            }
                        }
                    }
                }
            }
        }
        
        logger.log( Level.FINE, "getLocalsCompletions found {0} completions", new Object[]{ result.size() } );
        return result;
    }

    /**
     * return the Java class for the PyJavaClass.  The implementation may change
     * with Jython2.7.
     * @param po the PyJavaClass wrapper.
     * @return the Java class.
     */
    private static Class getJavaClass(PyJavaClass po) {
        PyJavaClassPeeker peek= new PyJavaClassPeeker(po);
        Class jclass= peek.getProxyClass();
        return jclass;
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

    /**
     * return an identifying icon for the object, or null.
     * @param jm java.lang.reflect.Method, or PyInteger, etc.
     * @return the icon or null.
     */
    public static ImageIcon getIconFor(Object jm) {
        ImageIcon icon=null;
        if ( jm instanceof java.lang.reflect.Method ) {
            Method m= (Method)jm;
            if ( Modifier.isStatic(m.getModifiers()) ) {
                icon= JAVA_STATIC_METHOD_ICON;
            } else {
                icon= JAVA_METHOD_ICON;
            }
        } else if ( jm instanceof java.lang.reflect.Field ) { 
            Field m= (Field)jm;
            if ( Modifier.isStatic(m.getModifiers()) ) {
                try {
                    Object o= m.get(java.awt.Color.class);
                    if ( o instanceof Color ) {
                        Color testColor= (Color) o;
                        return GraphUtil.colorImageIcon( testColor, 16, 16 );
                    } else {
                        return JAVA_FIELD_ICON;
                    }
                } catch (IllegalArgumentException | IllegalAccessException ex) {
                    logger.log( Level.FINE, null, ex );
                    return JAVA_FIELD_ICON;
                }
            } else {
                icon= JAVA_FIELD_ICON;
            }
        }
        return icon;
    }

}
