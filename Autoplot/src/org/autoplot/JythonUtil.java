
package org.autoplot;

import external.PlotCommand;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.text.BadLocationException;
import org.autoplot.jythonsupport.JythonRefactory;
import org.das2.system.RequestProcessor;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.python.core.PyException;
import org.python.core.PySystemState;
import org.python.util.InteractiveInterpreter;
import org.python.util.PythonInterpreter;
import org.autoplot.dom.Application;
import org.autoplot.scriptconsole.JythonScriptPanel;
import org.autoplot.scriptconsole.MakeToolPanel;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.datasource.URISplit;
import org.autoplot.jythonsupport.ui.EditorTextPane;
import org.autoplot.jythonsupport.ui.ParametersFormPanel;
import org.autoplot.jythonsupport.ui.ScriptPanelSupport;

/**
 * Utilities for Jython functions, such as a standard way to initialize
 * an interpreter and invoke a script asynchronously.  See also 1310.
 * TODO: this needs review, since the autoplot.py was added to the imports.
 * 
 * @see org.autoplot.jythonsupport.JythonUtil
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
                try ( InputStream in = JythonUtil.class.getResource("/appContextImports2017.py").openStream() ) {
                    interp.execfile( in, "/appContextImports2017.py" ); // JythonRefactory okay
                }
            }
        }
        interp.set( "monitor", new NullProgressMonitor() );
        interp.set( "plotx", new PlotCommand() );
        interp.set( "plot", new PlotCommand() );
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
        if ( dom!=null ) interp.set("dom", dom );
        if ( mon!=null ) interp.set("monitor", mon ); else interp.set( "monitor", new NullProgressMonitor() );
        return interp;
    }

    protected static void runScript( ApplicationModel model, String script, String[] argv, String pwd ) throws IOException {
        logger.entering( "org.autoplot.JythonUtil", "runScript {0}", script );
        URL url= DataSetURI.getURL(script);
        try (InputStream in = url.openStream()) {
            runScript(model, in, script, argv, pwd );
        }
        logger.exiting( "org.autoplot.JythonUtil", "runScript {0}", script );
    }

    /**
     * Run the script in the input stream.
     * @param model provides the dom to the environment.
     * @param in stream containing script. This will be left open.
     * @param name the name of the file for human reference, or null.
     * @param argv parameters passed into the script, each should be name=value.
     * @param pwd the present working directory, if available.  Note this is a String because pwd can be a remote folder.
     * @throws IOException
     */
    protected static void runScript( ApplicationModel model, InputStream in, String name, String[] argv, String pwd ) throws IOException {
        if ( argv==null ) argv= new String[] {""};
        PySystemState.initialize( PySystemState.getBaseProperties(), null, argv ); // legacy support sys.argv. now we use getParam
        PythonInterpreter interp = JythonUtil.createInterpreter(true, false, model.getDocumentModel(), new NullProgressMonitor() );
        if ( pwd!=null ) {
            pwd= URISplit.format( URISplit.parse(pwd) ); // sanity check against injections
            interp.exec("PWD='"+pwd+"'");// JythonRefactory okay
        }

        interp.exec("import autoplot2017 as autoplot");// JythonRefactory okay
        int iargv=0;  // skip the zeroth one, it is the name of the script
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
                if ( iargv>=0 ) {
                    interp.exec("autoplot.params['arg_" + iargv + "']='" + s +"'" );// JythonRefactory okay
                    iargv++;
                } else {
                    //System.err.println("skipping parameter" + s );
                    iargv++;
                }
            }
        }
        
        if ( name==null ) {
            interp.execfile(JythonRefactory.fixImports(in));
        } else {
            interp.execfile(JythonRefactory.fixImports(in),name);
        }

    }

    /**
     * invoke the python script on another thread.
     * @param url the address of the script.
     * @throws java.io.IOException
     * @deprecated use invokeScriptSoon with URI.
     */
    public static void invokeScriptSoon( final URL url ) throws IOException {
        invokeScriptSoon( url, null, new NullProgressMonitor() );
    }

    /**
     * invoke the python script on another thread.
     * @param uri the address of the script.
     * @throws java.io.IOException
     */
    public static void invokeScriptSoon( final URI uri ) throws IOException {
        invokeScriptSoon( uri, null, new NullProgressMonitor() );
    }

    
    /**
     * invoke the python script on another thread.
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
     * invoke the python script on another thread.
     * @param uri the address of the script.
     * @param dom if null, then null is passed into the script and the script must not use dom.
     * @param mon monitor to detect when script is finished.  If null, then a NullProgressMonitor is created.
     * @throws java.io.IOException
     */
    public static void invokeScriptSoon( final URI uri, final Application dom, ProgressMonitor mon ) throws IOException {
        invokeScriptSoon( uri, dom, new HashMap(), false, false, mon );
    }
    
    private static final HashMap<String,String> okayed= new HashMap();
    
    private static boolean isScriptOkayed( String filename, String contents ) {
        String okayedContents= okayed.get(filename);
        return contents.equals( okayedContents  );
    }
            
    /**
     * show the script and the variables (like we have always done with jyds scripts), and offer to run the script.
     * @param parent parent GUI to follow 
     * @param env 
     * @param file file containing the script.
     * @param fparams parameters for the script.
     * @param makeTool the dialog is always shown and the user can have the script installed as a tool.
     * @param resourceUri when the user decides to make a tool, we need the source location.
     * @return JOptionPane.OK_OPTION or JOptionPane.CANCEL_OPTION if the user cancels.
     * @throws java.io.IOException
     */
    public static int showScriptDialog( Component parent, Map<String,Object> env, File file, Map<String,String> fparams, boolean makeTool, final URI resourceUri ) throws IOException {
        
        if ( !EventQueue.isDispatchThread() ) {
            System.err.println("*** called from off of event thread!!!");
        }
        JPanel p= new JPanel();
        p.setLayout( new BoxLayout(p,BoxLayout.Y_AXIS) );
        
        ParametersFormPanel fpf= new org.autoplot.jythonsupport.ui.ParametersFormPanel();
        ParametersFormPanel.FormData fd=  fpf.doVariables( env, file, fparams, p );

        if ( fd.count==0 && !makeTool ) {
            return JOptionPane.OK_OPTION;
        }
        
        JPanel scriptPanel= new JPanel( new BorderLayout() );
        JTabbedPane tp= new JTabbedPane();
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
        MakeToolPanel makeToolPanel= new MakeToolPanel();
        if ( makeTool ) {
            scriptPanel.add( makeToolPanel, BorderLayout.SOUTH );
        }
        
        tp.add( scriptPanel, "script" );
        
        JScrollPane params= new JScrollPane(p); // TODO: why do I need this?
        params.setMinimumSize( new Dimension(640,480) );
        tp.add( params, "params" );
        
        if ( makeTool ) {
            if ( isScriptOkayed( file.toString(), theScript ) ) {
                tp.setSelectedIndex(1);
                p.add( Box.createGlue() );
                JLabel l= new JLabel("You have run this script before.");
                l.setAlignmentX( 0.0f );
                p.add( l );
            } else {
                tp.setSelectedIndex(0);
            }
        } else {
            tp.setSelectedIndex(1);
        }
                
        int result= AutoplotUtil.showConfirmDialog2( parent, tp, "Run Script "+file.getName(), JOptionPane.OK_CANCEL_OPTION );
        if ( result==JOptionPane.OK_OPTION ) {
            fd=  fpf.getFormData();
            org.autoplot.jythonsupport.ui.ParametersFormPanel.resetVariables( fd, fparams );
            if ( makeTool ) {
                if ( makeToolPanel.isInstall() ) { // the user has requested that the script be installed.
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
     * invoke the python script on another thread.  Script parameters can be passed in, and the user can be 
     * provided a dialog to set the parameters.  Note this will return before the script is actually
     * executed, and monitor should be used to detect that the script is finished.
     * @param url the address of the script.
     * @param dom if null, then null is passed into the script and the script must not use dom.
     * @param params values for parameters, or null.
     * @param askParams if true, query the user for parameter settings.
     * @param makeTool if true, offer to put the script into the tools area for use later (only if askParams).
     * @param mon1 monitor to detect when script is finished.  If null, then a NullProgressMonitor is created.
     * @return JOptionPane.OK_OPTION of the script is invoked.
     * @throws java.io.IOException
     * @deprecated use invokeScriptSoon with URI.
     */
    public static int invokeScriptSoon( final URL url, final Application dom, 
            Map<String,String> params, 
            boolean askParams, boolean makeTool, 
            ProgressMonitor mon1) throws IOException {
        return invokeScriptSoon( url, dom, params, askParams, makeTool, null, mon1 );
    }            
    
    /**
     * invoke the python script on another thread.  Script parameters can be passed in, and the user can be 
     * provided a dialog to set the parameters.  Note this will return before the script is actually
     * executed, and monitor should be used to detect that the script is finished.
     * @param uri the address of the script.
     * @param dom if null, then null is passed into the script and the script must not use dom.
     * @param vars values for parameters, or null.
     * @param askParams if true, query the user for parameter settings.
     * @param makeTool if true, offer to put the script into the tools area for use later (only if askParams).
     * @param mon1 monitor to detect when script is finished.  If null, then a NullProgressMonitor is created.
     * @return JOptionPane.OK_OPTION of the script is invoked.
     * @throws java.io.IOException
     */
    public static int invokeScriptSoon( final URI uri, final Application dom, 
            Map<String,String> vars, 
            boolean askParams, boolean makeTool, 
            ProgressMonitor mon1) throws IOException {
        return invokeScriptSoon( uri, dom, vars, askParams, makeTool, null, mon1 );
    }       
    
    /**
     * invoke the python script on another thread.  Script parameters can be passed in, and the user can be 
     * provided a dialog to set the parameters.  Note this will return before the script is actually
     * executed, and monitor should be used to detect that the script is finished.
     * @param url the address of the script.
     * @param dom if null, then null is passed into the script and the script must not use dom.
     * @param params values for parameters, or null.
     * @param askParams if true, query the user for parameter settings.
     * @param makeTool if true, offer to put the script into the tools area for use later (only if askParams).
     * @param scriptPanel null or place to mark error messages and to mark as running a script.
     * @param mon1 monitor to detect when script is finished.  If null, then a NullProgressMonitor is created.
     * @return JOptionPane.OK_OPTION of the script is invoked.
     * @throws java.io.IOException
     * @deprecated use invokeScriptSoon with URI.
     */    
    public static int invokeScriptSoon( final URL url, final Application dom, 
            Map<String,String> params, 
            boolean askParams, 
            final boolean makeTool, 
            final JythonScriptPanel scriptPanel,
            ProgressMonitor mon1) throws IOException {
        try {
            URI uri= url.toURI();
            return invokeScriptSoon( uri, dom, params, askParams, makeTool, scriptPanel, mon1 );
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(ex);
        }
    }
    
    /**
     * invoke the python script on another thread.  Script parameters can be passed in, and the user can be 
     * provided a dialog to set the parameters.  Note this will return before the script is actually
     * executed, and monitor should be used to detect that the script is finished.
     * @param uri the resource URI of the script (without parameters).
     * @param dom if null, then null is passed into the script and the script must not use dom.
     * @param params values for parameters, or null.
     * @param askParams if true, query the user for parameter settings.
     * @param makeTool if true, offer to put the script into the tools area for use later (only if askParams).
     * @param scriptPanel null or place to mark error messages and to mark as running a script.
     * @param mon1 monitor to detect when script is finished.  If null, then a NullProgressMonitor is created.
     * @return JOptionPane.OK_OPTION of the script is invoked.
     * @throws java.io.IOException
     */
    public static int invokeScriptSoon( final URI uri, final Application dom, 
            Map<String,String> params, 
            boolean askParams, 
            final boolean makeTool, 
            final JythonScriptPanel scriptPanel,
            ProgressMonitor mon1) throws IOException {
        
        final ProgressMonitor mon;
        if ( mon1==null ) {
            mon= new NullProgressMonitor();
        } else {
            mon= mon1;
        }
        final File file;
        
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
            file = DataSetURI.getFile( uri, new NullProgressMonitor() );
            Map<String,Object> args= new HashMap();
            args.put( "dom", dom );
            args.put( "PWD", split.path ); 
    
            JPanel paramPanel= new JPanel();
            fd=  pfp.doVariables( env, file, params, paramPanel );
            
            response= showScriptDialog( dom.getController().getDasCanvas(), args, file, fparams, makeTool, uri );
            
        } else {
            file = DataSetURI.getFile( uri, new NullProgressMonitor() );
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
                        
                        if ( scriptPanel!=null ) {
                            if ( ! scriptPanel.isDirty() && makeTool ) {
                                scriptPanel.loadFile(file);
                            }
                            scriptPanel.setRunningScript(file);
                        }
                        try ( FileInputStream in = new FileInputStream(file) ) {
                            
                            interp.execfile( JythonRefactory.fixImports(in), uri.toString());
                            
                        } catch ( PyException ex ) {
                            if ( scriptPanel!=null ) {
                                try {
                                    scriptPanel.getAnnotationsSupport().annotateError( ex, 0 );
                                } catch (BadLocationException ex1) {
                                    logger.log(Level.SEVERE, null, ex1);
                                }
                            }
                            throw ex;
                        } finally {
                            if ( !mon.isFinished() ) mon.finished();
                            if ( scriptPanel!=null ) scriptPanel.setRunningScript(null);
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
}
