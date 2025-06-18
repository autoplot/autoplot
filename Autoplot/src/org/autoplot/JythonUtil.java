
package org.autoplot;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import external.AnnotationCommand;
import external.PlotCommand;
import external.FixLayoutCommand;
import external.SimpleCommand;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Window;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.jythonsupport.JythonRefactory;
import org.das2.system.RequestProcessor;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.python.core.PyException;
import org.python.core.PySystemState;
import org.python.util.InteractiveInterpreter;
import org.python.util.PythonInterpreter;
import org.autoplot.dom.Application;
import org.autoplot.scriptconsole.MakeToolPanel;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.datasource.URISplit;
import org.autoplot.jythonsupport.DatasetCommand;
import org.autoplot.jythonsupport.GetDataSetCommand;
import org.autoplot.jythonsupport.GetDataSetsCommand;
import org.autoplot.jythonsupport.ui.EditorTextPane;
import org.autoplot.jythonsupport.ui.ParametersFormPanel;
import org.autoplot.jythonsupport.ui.ScriptPanelSupport;
import org.das2.util.FileUtil;
import org.python.core.PyJavaInstance;
import org.python.core.PySyntaxError;

/**
 * Utilities for Jython functions, such as a standard way to initialize
 * an interpreter and invoke a script asynchronously.
 * TODO: this needs review, since the autoplot.py was added to the imports.
 * 
 * @see org.autoplot.jythonsupport.JythonUtil
 * @see https://sourceforge.net/p/autoplot/bugs/1310/
 * @author jbf
 */
public class JythonUtil {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.jython");

    /**
     * create an interpreter object configured for Autoplot contexts:
     * <ul>
     *   <li> QDataSets are wrapped so that operators are overloaded.
     *   <li> a standard set of names are imported.
     * </ul>
     *   
     * @param appContext load in additional symbols that make sense in application context.
     * @param sandbox limit symbols to safe symbols for server.
     * @return PythonInterpreter ready for commands.
     * @throws java.io.IOException
     */
    public static InteractiveInterpreter createInterpreter( boolean appContext, boolean sandbox ) throws IOException {
        InteractiveInterpreter interp= org.autoplot.jythonsupport.JythonUtil.createInterpreter(sandbox);
        if ( org.autoplot.jythonsupport.Util.isLegacyImports() ) {
            if ( appContext ) {
                try ( InputStream in = JythonUtil.class.getResource("/appContextImports2025.py").openStream() ) {
                    interp.execfile( in, "/appContextImports2025.py" ); // JythonRefactory okay
                }
            }
        }
        interp.set( "monitor", new NullProgressMonitor() );
        interp.set( "dataset", new DatasetCommand() );
        interp.set( "getDataSet", new GetDataSetCommand() );
        interp.set( "getDataSets", new GetDataSetsCommand() );
        
        return interp;
    }

    /**
     * create a Jython interpreter, with the dom and monitor available to the
     * code.
     * @param appContext run this in the application context, with access to the dom.  (TODO: this is probably always equivalent to dom!=null)
     * @param sandbox limit symbols to safe symbols for server.
     * @param dom the application state, if available.
     * @param mon a monitor, if available.  If it is not a monitor is created.
     * @return the interpreter.
     * @throws IOException 
     */
    public static InteractiveInterpreter createInterpreter( boolean appContext, boolean sandbox, Application dom, ProgressMonitor mon ) throws IOException {
        InteractiveInterpreter interp= createInterpreter(appContext, sandbox);
        // interp.get("peekAt");  // This is how to see if the context 
        if ( dom!=null ) {
            interp.set("dom", dom );
            interp.set("scriptContext", new PyJavaInstance( dom.getController().getScriptContext()) );
            interp.set( "plotx", new PlotCommand(dom) );
            interp.set( "plot", new PlotCommand(dom) );    
            interp.set( "annotation", new AnnotationCommand(dom) );
            interp.set( "fixLayout", new FixLayoutCommand(dom) );
            Class c = dom.getController().getScriptContext().getClass();
            Set<String> exclude=new HashSet<>(Arrays.asList("hashCode"));
            for ( Method m : c.getDeclaredMethods() ) {
                if ( m.getName().startsWith("_") || exclude.contains(m.getName()) ) {
                    continue;
                }
                if ( interp.get(m.getName())!=null ) {
                    continue;
                }
                boolean isPublic = Modifier.isPublic(m.getModifiers());
                if ( !isPublic ) {
                    continue;
                }
                interp.exec(""+m.getName()+"=scriptContext."+m.getName());
            }
        }
        if ( mon!=null ) interp.set("monitor", mon ); else interp.set( "monitor", new NullProgressMonitor() );
        return interp;
    }

    protected static void runScript( ApplicationModel model, String script, String[] argv, String pwd ) throws IOException {
        logger.entering( "org.autoplot.JythonUtil", "runScript {0}", script );
        try {
            URI scriptURI;
            scriptURI= DataSetURI.getURI(script);
            try (InputStream in = DataSetURI.getInputStream( scriptURI, new NullProgressMonitor() ) ) {
               runScript(model, in, script, argv, pwd );
            }
        } catch (URISyntaxException ex) {
            URL scriptURL= DataSetURI.getURL(script);
            try (InputStream in = DataSetURI.getInputStream( scriptURL, new NullProgressMonitor() ) ) {
               runScript(model, in, script, argv, pwd );
            }
        }
        logger.exiting( "org.autoplot.JythonUtil", "runScript {0}", script );
    }

    /**
     * Run the script in the input stream.
     * @param model provides the dom to the environment.
     * @param in stream containing script. This will be left open.
     * @param name the name of the file for human reference, or null.
     * @param argv parameters passed into the script, each should be name=value, or positional.  The name of the script should not be the zeroth element.
     * @param pwd the present working directory, if available.  Note this is a String because pwd can be a remote folder.
     * @throws IOException
     */
    protected static void runScript( ApplicationModel model, InputStream in, String name, String[] argv, String pwd ) throws IOException {
        runScript( model.getDom(), in, name, argv, pwd );
    }
    
    /**
     * Run the script in the input stream.
     * @param dom provides the dom to the environment.
     * @param in stream containing script. This will be left open.
     * @param name the name of the file for human reference, or null.
     * @param argv parameters passed into the script, each should be name=value, or positional.  The name of the script should not be the zeroth element.
     * @param pwd the present working directory, if available.  Note this is a String because pwd can be a remote folder.
     * @throws IOException
     */
    public static void runScript( Application dom, InputStream in, String name, String[] argv, String pwd ) throws IOException {    
        
        if ( argv==null ) argv= new String[] {};
        
        String[] pyInitArgv= new String[ argv.length+1 ];
        pyInitArgv[0]= name;
        System.arraycopy(argv, 0, pyInitArgv, 1, argv.length);
        
        PySystemState.initialize( PySystemState.getBaseProperties(), null, pyInitArgv ); // legacy support sys.argv. now we use getParam
        
        PythonInterpreter interp = JythonUtil.createInterpreter(true, false, dom, new NullProgressMonitor() );
        if ( pwd!=null ) {
            pwd= URISplit.format( URISplit.parse(pwd) ); // sanity check against injections
            interp.exec("PWD='"+pwd+"'");// JythonRefactory okay
        }

        interp.exec("import autoplot2023 as autoplot");// JythonRefactory okay
        int iargv=1;  // skip the zeroth one, it is the name of the script
        for (String s : argv ) {
            int ieq= s.indexOf('=');
            if ( ieq>0 ) {
                String snam= s.substring(0,ieq).trim();
                if ( DataSourceUtil.isJavaIdentifier(snam) ) {
                    String sval= s.substring(ieq+1).trim();
//                    if ( snam.equals("resourceURI") ) {  // check to see if pwd can be inserted
//                        URISplit split= URISplit.parse(sval);
//                        if ( split.path==null ) {
//                            sval= pwd + sval;
//                        }
//                    }
                    interp.exec("autoplot.params['" + snam + "']='" + sval+"'");// JythonRefactory okay
                } else {
                    if ( snam.startsWith("-") ) {
                        System.err.println("\n!!! Script arguments should not start with -, they should be name=value");
                    }
                    System.err.println("bad parameter: "+ snam);
                }
            } else {
                interp.exec("autoplot.params['arg_" + iargv + "']='" + s +"'" );// JythonRefactory okay
                iargv++;
            }
        }
        
        if ( name==null ) {
            interp.execfile(JythonRefactory.fixImports(in));
        } else {
            interp.execfile(JythonRefactory.fixImports(in,name),name);
        }

    }

    /**
     * invoke the Jython script on another thread.
     * @param url the address of the script.
     * @throws java.io.IOException
     * @deprecated use invokeScriptSoon with URI.
     */
    public static void invokeScriptSoon( final URL url ) throws IOException {
        invokeScriptSoon( url, null, new NullProgressMonitor() );
    }

    /**
     * invoke the Jython script on another thread.
     * @param uri the address of the script.
     * @throws java.io.IOException
     */
    public static void invokeScriptSoon( final URI uri ) throws IOException {
        invokeScriptSoon( uri, null, new NullProgressMonitor() );
    }

    
    /**
     * invoke the Jython script on another thread.
     * @param url the address of the script.
     * @param dom if null, then null is passed into the script and the script must not use dom.
     * @param mon monitor to detect when script is finished.  If null, then a NullProgressMonitor is created.
     * @throws java.io.IOException
     * @deprecated use invokeScriptSoon with URI.
     */
    public static void invokeScriptSoon( final URL url, final Application dom, ProgressMonitor mon ) throws IOException {
        invokeScriptSoon( url, dom, new HashMap(), false, false, mon );
    }
    
    /**
     * invoke the Jython script on another thread.
     * @param uri the address of the script, possibly having parameters.
     * @param dom if null, then null is passed into the script and the script must not use dom.
     * @param mon monitor to detect when script is finished.  If null, then a NullProgressMonitor is created.
     * @throws java.io.IOException
     */
    public static void invokeScriptSoon( final URI uri, final Application dom, ProgressMonitor mon ) throws IOException {
        URISplit split= URISplit.parse(uri);
        Map<String,String> params= URISplit.parseParams(split.params);
        invokeScriptSoon( split.resourceUri, dom, params, false, false, mon );
    }

    private static final HashMap<String,String> okayed= new HashMap();
    
    private static boolean isScriptOkayed( String filename, String contents ) {
        String okayedContents= okayed.get(filename);
        if ( okayedContents==null ) {
            final File lastVersionDir= Paths.get( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ), "scripts" ).toFile();
            final File lastVersionFile= Paths.get( lastVersionDir.toString(), String.format( "%010d.jy", Math.abs( (long)filename.hashCode()) ).trim() ).toFile();        
            if ( lastVersionFile.exists() ) {
                try {
                    String lastVersionContents= FileUtil.readFileToString(lastVersionFile);
                    if ( lastVersionContents.equals(contents) ) {
                        logger.log(Level.FINE, "matches file previously okayed: {0}", lastVersionFile);
                        return true;
                    } else {
                        logger.log(Level.FINE, "does not match file previously run: {0}", lastVersionFile);
                    }
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            } else {
                logger.log(Level.FINE, "not been run before: {0}", lastVersionFile);
            }
        }
        return contents.equals( okayedContents  );
    }
    
    private static String stripTrailingWhitespace( String param ) {
        int len= param.length();
        for (; len > 0; len--) {
            if (!Character.isWhitespace(param.charAt(len - 1)))
                break;
        }
        return param.substring(0, len);
    }
    
    /**
     * The diff code has some problem on Windows, so clip off white space
     * from the end of lines.
     * @param src
     * @return 
     */
    private static List<String> splitAndTrimLines( String src ) {
        String[] ss= src.split("\n");
        for ( int i=0; i<ss.length; i++ ) {
            ss[i]= stripTrailingWhitespace(ss[i]);
        }
        return Arrays.asList(ss);
    }
    
    /**
     * return a Patch showing how the new version compares to the last run version.
     * @param filename
     * @param contents
     * @return 
     */
    private static Patch<String> diffToOkayedScript( String filename, String contents ) {
        String okayedContents= okayed.get(filename);
        if ( okayedContents==null ) {
            final File lastVersionDir= Paths.get( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ), "scripts" ).toFile();
            final File lastVersionFile= Paths.get( lastVersionDir.toString(), String.format( "%010d.jy", Math.abs( (long)filename.hashCode()) ).trim() ).toFile();
            if ( lastVersionFile.exists() ) {
                try {
                    String lastVersionContents= FileUtil.readFileToString(lastVersionFile);
                    return DiffUtils.diff( splitAndTrimLines( lastVersionContents ), splitAndTrimLines( contents ) );
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            } else {
                logger.log(Level.FINE, "not been run before: {0}", lastVersionFile);
                return null;
            }
        }
        return DiffUtils.diff( okayedContents, contents );
    }
            
    /**
     * show the script and the variables (like we have always done with jyds scripts), and offer to run the script.
     * @param parent parent GUI to follow 
     * @param env 
     * @param file file containing the script.
     * @param fparams parameters for the script.
     * @param makeTool the dialog is always shown and the scientist can have the script installed as a tool.
     * @param resourceUri when the scientist decides to make a tool, we need the source location.
     * @return JOptionPane.OK_OPTION or JOptionPane.CANCEL_OPTION if the scientist cancels.
     * @throws java.io.IOException
     */
    public static int showScriptDialog( 
            Component parent, 
            Map<String,Object> env, 
            File file, 
            Map<String,String> fparams, 
            boolean makeTool, 
            final URI resourceUri ) throws IOException {
        
        if ( !EventQueue.isDispatchThread() ) {
            System.err.println("*** called from off of event thread!!!");
        }
        JPanel paramsPanel= new JPanel();
        paramsPanel.setLayout( new BoxLayout(paramsPanel,BoxLayout.Y_AXIS) );
        paramsPanel.setAlignmentX(0.0f);
        
        ParametersFormPanel fpf= new org.autoplot.jythonsupport.ui.ParametersFormPanel();
        ParametersFormPanel.FormData fd;
        try {
            fd=  fpf.doVariables( env, file, fparams, paramsPanel );
        } catch ( PySyntaxError ex ) {
            System.err.println("pse: "+ex);
            fd= new ParametersFormPanel.FormData();
            fd.count=0;
        }

        if ( fd.count==0 && !makeTool ) {
            return JOptionPane.OK_OPTION;
        }
        
        JPanel scriptPanel= new JPanel( new BorderLayout() );
        JTabbedPane tabbedPane= new JTabbedPane();
        org.autoplot.jythonsupport.ui.EditorTextPane textArea= new EditorTextPane();
        
        String theScript= EditorTextPane.loadFileToString( file ) ;
        try {
            textArea.loadFile(file);
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
        
        ScriptPanelSupport support;
        support= new ScriptPanelSupport(textArea);
        support.setReadOnly();
        
        JScrollPane script= new JScrollPane(textArea);
        script.setMinimumSize( new Dimension(640,380) );
        script.setPreferredSize( new Dimension(640,380) );
        scriptPanel.add( script, BorderLayout.CENTER );
        scriptPanel.add( new JLabel("<html>Run the script:<br>"+file ), BorderLayout.NORTH );
        
        tabbedPane.add( scriptPanel, "script" );
        
        JScrollPane params= new JScrollPane(paramsPanel); // TODO: why do I need this?
        params.setMinimumSize( new Dimension(640,480) );
        tabbedPane.add( params, "params" );

        final boolean scriptOkay= isScriptOkayed( file.toString(), theScript );
        if ( !scriptOkay ) {
            Patch<String> p= diffToOkayedScript( file.toString(), theScript );
            if ( p!=null ) {
                textArea.getDocument();
                Runnable run = () -> {
                    support.annotatePatch(p);
                };
                SwingUtilities.invokeLater(run);
            }
        }
        
        if ( makeTool ) {
            if ( scriptOkay ) {
                tabbedPane.setSelectedIndex(1);
            } else {
                tabbedPane.setSelectedIndex(0);
            }
        } else {
            tabbedPane.setSelectedIndex(1);
        }
        
        JPanel theP= new JPanel(new BorderLayout());
        theP.add( tabbedPane, BorderLayout.CENTER );
        MakeToolPanel makeToolPanel=null;
        if ( makeTool ) {
            makeToolPanel= new MakeToolPanel(scriptOkay);
            theP.add( makeToolPanel, BorderLayout.SOUTH );
        } else {
            if ( scriptOkay ) {
                theP.add( new JLabel("You have run this version of the script before."), BorderLayout.SOUTH );
            } else {
                JLabel trustedScriptLabel= new JLabel("Make sure this script does not contain malicious code.");
                trustedScriptLabel.setIcon(AutoplotUI.WARNING_ICON);
                theP.add( trustedScriptLabel, BorderLayout.SOUTH );
            }
        }
        
        int result= AutoplotUtil.showConfirmDialog2( parent, theP, "Run Script "+file.getName(), JOptionPane.OK_CANCEL_OPTION );
        if ( result==JOptionPane.OK_OPTION ) {
            fd=  fpf.getFormData();
            org.autoplot.jythonsupport.ui.ParametersFormPanel.resetVariables( fd, fparams );
            if ( makeTool ) {
                assert makeToolPanel!=null;
                if ( makeToolPanel.isInstall() ) { // the scientist has requested that the script be installed.
                    Window w= ScriptContext.getViewWindow();
                    if ( w instanceof AutoplotUI ) {
                        ((AutoplotUI)w).installTool( file, resourceUri );
                        ((AutoplotUI)w).reloadTools();
                    } else {
                        throw new RuntimeException("Unable to install"); // and hope the submit the error.
                    }
                }
            }
            okayed.put( file.toString(), theScript );
        }
        return result;
    }
            
            
    /**
     * invoke the Jython script on another thread.  Script parameters can be passed in, and the scientist can be 
     * provided a dialog to set the parameters.  Note this will return before the script is actually
     * executed, and monitor should be used to detect that the script is finished.
     * @param url the address of the script.
     * @param dom if null, then null is passed into the script and the script must not use dom.
     * @param params values for parameters, or null.
     * @param askParams if true, query the scientist for parameter settings.
     * @param makeTool if true, offer to put the script into the tools area for use later (only if askParams).
     * @param mon1 monitor to detect when script is finished.  If null, then a NullProgressMonitor is created.
     * @return JOptionPane.OK_OPTION of the script is invoked.
     * @throws java.io.IOException
     * @deprecated use invokeScriptSoon with URI.
     */
    public static int invokeScriptSoon( 
            final URL url, 
            final Application dom, 
            Map<String,String> params, 
            boolean askParams, boolean makeTool, 
            ProgressMonitor mon1) throws IOException {
        try {
            return invokeScriptSoon( url.toURI(), dom, params, askParams, makeTool, null, mon1 );
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * invoke the Jython script on another thread.  Script parameters can be passed in, and the scientist can be 
     * provided a dialog to set the parameters.  Note this will return before the script is actually
     * executed, and monitor should be used to detect that the script is finished.
     * @param uri the address of the script.
     * @param dom if null, then null is passed into the script and the script must not use dom.
     * @param vars values for parameters, or null.
     * @param askParams if true, query the scientist for parameter settings.
     * @param makeTool if true, offer to put the script into the tools area for use later (only if askParams).
     * @param mon1 monitor to detect when script is finished.  If null, then a NullProgressMonitor is created.
     * @return JOptionPane.OK_OPTION of the script is invoked.
     * @throws java.io.IOException
     */
    public static int invokeScriptSoon( 
            final URI uri, 
            final Application dom, 
            Map<String,String> vars, 
            boolean askParams, boolean makeTool, 
            ProgressMonitor mon1) throws IOException {
        return invokeScriptSoon( uri, dom, vars, askParams, makeTool, null, mon1 );
    }       
    
//    /**
//     * invoke the Jython script on another thread.  Script parameters can be passed in, and the scientist can be 
//     * provided a dialog to set the parameters.  Note this will return before the script is actually
//     * executed, and monitor should be used to detect that the script is finished.
//     * @param url the address of the script.
//     * @param dom if null, then null is passed into the script and the script must not use dom.
//     * @param params values for parameters, or null.
//     * @param askParams if true, query the scientist for parameter settings.
//     * @param makeTool if true, offer to put the script into the tools area for use later (only if askParams).
//     * @param scriptPanel null or place to mark error messages and to mark as running a script.
//     * @param mon1 monitor to detect when script is finished.  If null, then a NullProgressMonitor is created.
//     * @return JOptionPane.OK_OPTION of the script is invoked.
//     * @throws java.io.IOException
//     * @deprecated use invokeScriptSoon with URI.
//     */    
//    public static int invokeScriptSoon( 
//            final URL url, 
//            final Application dom, 
//            Map<String,String> params, 
//            boolean askParams, 
//            final boolean makeTool, 
//            final JythonScriptPanel scriptPanel,
//            ProgressMonitor mon1) throws IOException {
//        try {
//            URI uri= url.toURI();
//            return invokeScriptSoon( uri, dom, params, askParams, makeTool, scriptPanel, mon1 );
//        } catch (URISyntaxException ex) {
//            throw new IllegalArgumentException(ex);
//        }
//    }
    
    /**
     * invoke the Jython script on another thread.  Script parameters can be passed in, and the scientist can be 
     * provided a dialog to set the parameters.  Note this will return before the script is actually
     * executed, and monitor should be used to detect that the script is finished.
     * @param uri the resource URI of the script (without parameters).
     * @param dom if null, then null is passed into the script and the script must not use dom.
     * @param params values for parameters, or null.
     * @param askParams if true, query the scientist for parameter settings.
     * @param makeTool if true, offer to put the script into the tools area for use later (only if askParams).
     * @param runListener null or place to mark error messages and to mark as running a script.
     * @param mon1 monitor to detect when script is finished.  If null, then a NullProgressMonitor is created.
     * @return JOptionPane.OK_OPTION of the script is invoked.
     * @throws java.io.IOException
     */
    public static int invokeScriptSoon( 
            final URI uri, 
            final Application dom, 
            final Map<String,String> params, 
            final boolean askParams, 
            final boolean makeTool, 
            final JythonRunListener runListener,
            final ProgressMonitor mon1) throws IOException {
        
        if ( EventQueue.isDispatchThread() ) {
            logger.warning("THIS IS THE EVENT THREAD, AND ATTEMPTS TO DOWNLOAD A FILE.");
        }
        
        final File file = DataSetURI.getFile( uri, new NullProgressMonitor() ); 
        
        final ArrayList<Object> result= new ArrayList<>();
        Runnable run= new Runnable() {
            @Override
            public void run() {
                try {
                    result.add( invokeScriptSoon( uri, file, dom, params, askParams, makeTool, runListener, mon1 ) );
                } catch (IOException ex) {
                    result.add(ex);
                }
            }
        };
        
        if ( SwingUtilities.isEventDispatchThread() ) {
            run.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(run);
            } catch (InterruptedException | InvocationTargetException ex) {
                result.add(ex);
            }
        }
        Object result0= result.get(0);
        if ( result0 instanceof IOException ) {
            throw (IOException)result0;
        } else if ( result0 instanceof RuntimeException ) {
            throw (RuntimeException)result0;
        } else if ( result0 instanceof Exception ) {
            throw new RuntimeException((Exception)result0);
        } else {
            return (Integer)result.get(0);
        }
    }
        
    /**
     * Do a search for the number of places where JythonUtil.createInterpreter
     * is called and it should be clear that there's a need for one code that
     * does this.  There probably is one such code, but I can't find it right 
     * now.
     * @param environ
     * @param file
     * @throws IOException 
     * @see #runScript(org.autoplot.ApplicationModel, java.lang.String, java.lang.String[], java.lang.String) 
     *  which doesn't allow for control of the environ (and arbitrary parameters).
     */
    public static void invokeScriptNow( Map<String,Object> environ, File file ) throws IOException, PyException {
        
        ProgressMonitor mon= (ProgressMonitor)environ.get( "monitor" );
        if ( mon==null ) {
            logger.log(Level.FINE, "creating NullProgressMonitor to run {0}", file);
            mon= new NullProgressMonitor();
        }
                
        PythonInterpreter interp = JythonUtil.createInterpreter(true, false, (Application)environ.get("dom"), mon );
        for ( Entry<String,Object> e: environ.entrySet() ) {
            interp.set( e.getKey(), e.getValue() );
        }
                        
        try ( FileInputStream in = new FileInputStream(file) ) {

            final File lastVersionDir= Paths.get( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ), "scripts" ).toFile();
            if ( !lastVersionDir.exists() ) {
                if ( !lastVersionDir.mkdirs() ) {
                    logger.log(Level.WARNING, "unable to mkdir {0}", lastVersionDir);
                } else {
                    File readme= new File(lastVersionDir,"README.txt");
                    try ( PrintStream out= new PrintStream(readme) ) {
                        out.print("Files here have been okayed to run and can be run again without a warning.  See https://autoplot.org/1310\n");
                    }
                }
            }

            final File lastVersionFile= Paths.get( lastVersionDir.toString(), String.format( "%010d.jy", Math.abs((long)file.toString().hashCode()) ).trim() ).toFile();        

            FileUtil.fileCopy( file, lastVersionFile );

            interp.execfile( JythonRefactory.fixImports(in,file.getName()), file.toString() );

        } catch ( PyException ex ) {
            throw ex;
        } finally {
            if ( !mon.isFinished() ) mon.finished();
        } 
    }
    
    
    /**
     * invoke the Jython script on another thread.  Script parameters can be passed in, and the scientist can be 
     * provided a dialog to set the parameters.  Note this will return before the script is actually
     * executed, and monitor should be used to detect that the script is finished.
     * This should be called from the event thread!
     * @param uri the resource URI of the script (without parameters).
     * @param file the file which has been downloaded.
     * @param dom if null, then null is passed into the script and the script must not use dom.
     * @param params values for parameters, or null.
     * @param askParams if true, query the scientist for parameter settings.
     * @param makeTool if true, offer to put the script into the tools area for use later (only if askParams).
     * @param jythonRunListener null or place to mark error messages and to mark as running a script.
     * @param mon1 monitor to detect when script is finished.  If null, then a NullProgressMonitor is created.
     * @return JOptionPane.OK_OPTION of the script is invoked.
     * @throws java.io.IOException
     */
    public static int invokeScriptSoon( 
            final URI uri, 
            final File file, 
            final Application dom, 
            Map<String,String> params, 
            boolean askParams, 
            final boolean makeTool, 
            final JythonRunListener jythonRunListener,
            ProgressMonitor mon1) throws IOException {       

        final ProgressMonitor mon;
        if ( mon1==null ) {
            mon= new NullProgressMonitor();
        } else {
            mon= mon1;
        }
        
        final Map<String,String> fparams;
        if ( params==null ) {
            fparams= new HashMap();
        } else {
            fparams= params;
        }
        
        ParametersFormPanel pfp= new org.autoplot.jythonsupport.ui.ParametersFormPanel();
        Map<String,Object> env= new HashMap();
        env.put("dom",dom );
        URISplit split= URISplit.parse(uri);
        env.put( "PWD", split.path );
        
        final ParametersFormPanel.FormData fd;
        
        int response= JOptionPane.OK_OPTION;
        if ( askParams ) {     
            
            Map<String,Object> args= new HashMap();
            args.put( "dom", dom );
            args.put( "PWD", split.path ); 
    
            JPanel paramPanel= new JPanel();
            try {
                fd=  pfp.doVariables( env, file, params, paramPanel );
            } catch ( PySyntaxError ex ) {
                AutoplotUtil.showMessageDialog( dom.getController().getDasCanvas(), 
                        "<html>The script has a syntax error which prevents use in the address bar.<br>"+
                                "(Note Autoplot runs a subset of the code and may introduce problems.)", 
                        "Syntax Error", JOptionPane.OK_OPTION );
                
                return JOptionPane.CANCEL_OPTION;
            }
                         
            response= showScriptDialog( dom.getController().getDasCanvas(), args, file, fparams, makeTool, uri );
            
        } else {
            fd=  pfp.doVariables( env, file, params, null );
        }
        
        if ( response==JOptionPane.OK_OPTION ) {
            Runnable run= new Runnable() {
                @Override
                public void run() {
                    try {
                        PythonInterpreter interp = JythonUtil.createInterpreter(true, false, dom, mon );
                        logger.log(Level.FINE, "invokeScriptSoon({0})", uri);
                        for ( Map.Entry<String,String> p: fparams.entrySet() ) {
                            try {
                                fd.implement( interp, p.getKey(), p.getValue() );
                            } catch ( ParseException ex ) {
                                logger.log( Level.WARNING, null, ex );
                            }
                        }
                        URISplit split= URISplit.parse(uri);
                        interp.set( "dom", dom );
                        interp.set( "PWD", split.path ); 
                        
                        if ( jythonRunListener!=null ) {
                            jythonRunListener.runningScript(file);
                        }
                        try ( FileInputStream in = new FileInputStream(file) ) {
                            
                            final File lastVersionDir= Paths.get( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ), "scripts" ).toFile();
                            if ( !lastVersionDir.exists() ) {
                                if ( !lastVersionDir.mkdirs() ) {
                                    logger.log(Level.WARNING, "unable to mkdir {0}", lastVersionDir);
                                } else {
                                    File readme= new File(lastVersionDir,"README.txt");
                                    try ( PrintStream out= new PrintStream(readme) ) {
                                        out.print("Files here have been okayed to run and can be run again without a warning.  See http://autoplot.org/1310\n");
                                    }
                                }
                            }
                            
                            final File lastVersionFile= Paths.get( lastVersionDir.toString(), String.format( "%010d.jy", Math.abs((long)file.toString().hashCode()) ).trim() ).toFile();        
                            
                            FileUtil.fileCopy( file, lastVersionFile );
                            
                            interp.execfile( JythonRefactory.fixImports(in,file.getName()), uri.toString() );
                            
                        } catch ( PyException ex ) {
                            if ( jythonRunListener!=null ) {
                                jythonRunListener.exceptionEncountered(file,ex);
                            }
                            throw ex;
                        } finally {
                            if ( !mon.isFinished() ) mon.finished();
                            if ( jythonRunListener!=null ) jythonRunListener.runningScript(null);
                        } 
                        //TODO: error annotations on the editor.  This really would be nice.
                    } catch (IOException ex) {
                        logger.log(Level.SEVERE, ex.getMessage(), ex);
                    }
                }
            };
            RequestProcessor.invokeLater(run);
        }
        
        return response;
    }
    
    /**
     * Invoke the script on the current thread.
     * @param uri the URI, providing pwd.
     * @param file null or the file to use.
     * @param dom the application 
     * @param fparams parameters to pass into the script.
     * @param mon feedback monitor for the thread.
     * @throws IOException 
     */
    public static void invokeScript20181217( 
            URI uri, 
            File file, 
            Application dom, 
            Map<String,String> fparams, 
            ProgressMonitor mon ) throws IOException {
        
        if ( mon==null ) mon= new NullProgressMonitor();

        if ( file==null ) {    
            if ( SwingUtilities.isEventDispatchThread() ) {
                throw new IllegalArgumentException("invokeScript called from EventQueue");
            }
            file = DataSetURI.getFile( uri, new NullProgressMonitor() ); 
        }
        
        ParametersFormPanel.FormData fd;
          
        PythonInterpreter interp = JythonUtil.createInterpreter(true, false, dom, mon );

        ParametersFormPanel pfp= new org.autoplot.jythonsupport.ui.ParametersFormPanel();
        
        Map<String,Object> env= new HashMap();
        env.put("dom",dom );
        URISplit split= URISplit.parse(uri);
        env.put( "PWD", split.path );
        
        fd=  pfp.doVariables( env, file, fparams, null );

        logger.log(Level.FINE, "invokeScriptSoon({0})", uri);
        
        for ( Map.Entry<String,String> p: fparams.entrySet() ) {
            try {
                fd.implement( interp, p.getKey(), p.getValue() );
            } catch ( ParseException ex ) {
                logger.log( Level.WARNING, null, ex );
            }
        }

        interp.set( "dom", dom );
        interp.set( "PWD", split.path ); 

        try ( FileInputStream in = new FileInputStream(file) ) {

            interp.execfile( JythonRefactory.fixImports(in,file.getName()), uri.toString());

        } catch ( PyException ex ) {
            throw ex;

        } finally {
            if ( !mon.isFinished() ) mon.finished();
        } 
        
    }
}
