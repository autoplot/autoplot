/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import external.PlotCommand;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
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
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import org.das2.system.RequestProcessor;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.python.core.PySystemState;
import org.python.util.InteractiveInterpreter;
import org.python.util.PythonInterpreter;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.scriptconsole.MakeToolPanel;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSourceUtil;
import org.virbo.datasource.URISplit;
import org.virbo.jythonsupport.ui.EditorTextPane;
import org.virbo.jythonsupport.ui.ParametersFormPanel;
import org.virbo.jythonsupport.ui.ScriptPanelSupport;

/**
 *
 * @author jbf
 */
public class JythonUtil {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.jython");

    /**
     * create an interpreter object configured for Autoplot contexts:
     *   * QDataSets are wrapped so that operators are overloaded.
     *   * a standard set of names are imported.
     *   
     * @param appContext load in additional symbols that make sense in application context.
     * @param sandbox limit symbols to safe symbols for server.
     * @return PythonInterpreter ready for commands.
     * @throws java.io.IOException
     */
    public static InteractiveInterpreter createInterpreter( boolean appContext, boolean sandbox ) throws IOException {
        InteractiveInterpreter interp= org.virbo.jythonsupport.JythonUtil.createInterpreter(sandbox);
        if ( org.virbo.jythonsupport.Util.isLegacyImports() ) {
            if ( appContext ) {
                InputStream in= JythonUtil.class.getResource("appContextImports.py").openStream();
                try {
                    interp.execfile( in, "appContextImports.py" );
                } finally {
                    in.close();
                }
            }
        }
        interp.set( "monitor", new NullProgressMonitor() );
        interp.set( "plotx", new PlotCommand() );
        return interp;
    }

    public static InteractiveInterpreter createInterpreter( boolean appContext, boolean sandbox, Application dom, ProgressMonitor mon ) throws IOException {
        InteractiveInterpreter interp= createInterpreter(appContext, sandbox);
        if ( dom!=null ) interp.set("dom", dom );
        if ( mon!=null ) interp.set("monitor", mon ); else interp.set( "monitor", new NullProgressMonitor() );
        interp.set( "plotx", new PlotCommand() );
        return interp;
    }

    protected static void runScript( ApplicationModel model, String script, String[] argv ) throws IOException {
        URL url= DataSetURI.getURL(script);
        InputStream in= url.openStream();
        try {
            runScript( model, in, null, argv );
        } finally {
            in.close();
        }
    }

    /**
     * Run the script in the input stream.
     * @param model provides the dom to the environment.
     * @param in stream containing script. This will be left open.
     * @param name the name of the file for human reference, or null.
     * @param argv parameters passed into the script, each should be name=value.
     * @throws IOException
     */
    protected static void runScript( ApplicationModel model, InputStream in, String name, String[] argv) throws IOException {
        if ( argv==null ) argv= new String[] {""};
        PySystemState.initialize( PySystemState.getBaseProperties(), null, argv ); // legacy support sys.argv. now we use getParam
        PythonInterpreter interp = JythonUtil.createInterpreter(true, false, model.getDocumentModel(), new NullProgressMonitor() );

        interp.exec("import autoplot");
        int iargv=-1;  // skip the zeroth one, it is the name of the script
        for (String s : argv ) {
            int ieq= s.indexOf("=");
            if ( ieq>0 ) {
                String snam= s.substring(0,ieq).trim();
                if ( DataSourceUtil.isJavaIdentifier(snam) ) {
                    String sval= s.substring(ieq+1).trim();
                    interp.exec("autoplot.params['" + snam + "']='" + sval+"'");
                } else {
                    if ( snam.startsWith("-") ) {
                        System.err.println("\n!!! Script arguments should not start with -, they should be name=value");
                    }
                    System.err.println("bad parameter: "+ snam);
                }
            } else {
                if ( iargv>=0 ) {
                    interp.exec("autoplot.params['arg_" + iargv + "']='" + s +"'" );
                    iargv++;
                } else {
                    //System.err.println("skipping parameter" + s );
                    iargv++;
                }
            }
        }
        
        if ( name==null ) {
            interp.execfile(in);
        } else {
            interp.execfile(in,name);
        }

    }

    /**
     * invoke the python script on another thread.
     * @param url the address of the script.
     */
    public static void invokeScriptSoon( final URL url ) throws IOException {
        invokeScriptSoon( url, null, new NullProgressMonitor() );
    }

    
    /**
     * invoke the python script on another thread.
     * @param url the address of the script.
     * @param dom if null, then null is passed into the script and the script must not use dom.
     * @param mon monitor to detect when script is finished.  If null, then a NullProgressMonitor is created.
     */
    public static void invokeScriptSoon( final URL url, final Application dom, ProgressMonitor mon ) throws IOException {
        invokeScriptSoon( url, dom, new HashMap(), false, false, mon );
    }
    
    /**
     * show the script and the variables (like we have always done with jyds scripts), and offer to run the script.
     * @param parent parent GUI to follow 
     * @param file file containing the script.
     * @param fvars parameters for the script.
     * @param makeTool the dialog is always shown and the user can have the script installed as a tool.
     * @param resourceUri when the user decides to make a tool, we need the source location.
     * @return JOptionPane.OK_OPTION or JOptionPane.CANCEL_OPTION if the user cancels.
     */
    private static int showScriptDialog( Component parent, Map<String,Object> env, File file, Map<String,String> fvars, boolean makeTool, final URI resourceUri ) throws IOException {
        
        JPanel p= new JPanel();
        
        ParametersFormPanel fpf= new org.virbo.jythonsupport.ui.ParametersFormPanel();
        ParametersFormPanel.FormData fd=  fpf.doVariables( env, file, fvars, p );

        if ( fd.count==0 && !makeTool ) {
            return JOptionPane.OK_OPTION;
        }
        
        JPanel scriptPanel= new JPanel( new BorderLayout() );
        JTabbedPane tp= new JTabbedPane();
        org.virbo.jythonsupport.ui.EditorTextPane textArea= new EditorTextPane();
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
            tp.setSelectedIndex(0);
        } else {
            tp.setSelectedIndex(1);
        }
                
        int result= AutoplotUtil.showConfirmDialog2( parent, tp, "Run Script "+file.getName(), JOptionPane.OK_CANCEL_OPTION );
        if ( result==JOptionPane.OK_OPTION ) {
            fd=  fpf.getFormData();
            org.virbo.jythonsupport.ui.ParametersFormPanel.resetVariables( fd, fvars );
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

        }
        return result;
    }
            
            
    /**
     * invoke the python script on another thread.  Script parameters can be passed in, and the user can be 
     * provided a dialog to set the parameters.  Note this will return before the script is actually
     * executed, and monitor should be used to detect that the script is finished.
     * @param url the address of the script.
     * @param dom if null, then null is passed into the script and the script must not use dom.
     * @param vars values for parameters, or null.
     * @param askParams if true, query the user for parameter settings.
     * @param makeTool if true, offer to put the script into the tools area for use later (only if askParams).
     * @param mon monitor to detect when script is finished.  If null, then a NullProgressMonitor is created.
     * @return JOptionPane.OK_OPTION of the script is invoked.
     */
    public static int invokeScriptSoon( final URL url, final Application dom, Map<String,String> vars, boolean askParams, boolean makeTool, ProgressMonitor mon1) throws IOException {
        final ProgressMonitor mon;
        if ( mon1==null ) {
            mon= new NullProgressMonitor();
        } else {
            mon= mon1;
        }
        final File file;
        
        final Map<String,String> fvars;
        if ( vars==null ) {
            fvars= new HashMap();
        } else {
            fvars= vars;
        }
        
        ParametersFormPanel pfp= new org.virbo.jythonsupport.ui.ParametersFormPanel();
        Map<String,Object> env= new HashMap();
        env.put("dom",dom );
        URISplit split= URISplit.parse(url.toString());
        env.put( "PWD", split.path );
        
        final ParametersFormPanel.FormData fd;
        
        int response= JOptionPane.OK_OPTION;
        if ( askParams ) {     
            URI uri = null;
            try {
                uri= url.toURI();
            } catch (URISyntaxException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
            file = DataSetURI.getFile( url, new NullProgressMonitor() );
            Map<String,Object> args= new HashMap();
            args.put( "dom", dom );
            args.put( "PWD", split.path ); 
    
            JPanel params= new JPanel();
            fd=  pfp.doVariables( env, file, vars, params );
            
            response= showScriptDialog( dom.getController().getDasCanvas(), args, file, fvars, makeTool, uri );
            
        } else {
            file = DataSetURI.getFile( url, new NullProgressMonitor() );
            fd=  pfp.doVariables( env, file, vars, null );
        }
        
        Runnable run= new Runnable() {
            public void run() {
                try {
                    PythonInterpreter interp = JythonUtil.createInterpreter(true, false, dom, mon );
                    logger.log(Level.FINE, "invokeScriptSoon({0})", url);
                    for ( Map.Entry<String,String> v: fvars.entrySet() ) {
                        try {
                            fd.implement( interp, v.getKey(), v.getValue() );
                        } catch ( ParseException ex ) {
                            ex.printStackTrace();
                            logger.log( Level.WARNING, null, ex );
                        }
                    }
                    URISplit split= URISplit.parse(url.toString());
                    interp.set( "dom", dom );
                    interp.set( "PWD", split.path );   
                    FileInputStream in= new FileInputStream(file);
                    try {
                        interp.execfile( in, url.toString());
                    } finally {
                        in.close();
                    }
                    //TODO: error annotations on the editor.
                    mon.finished();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
        };
        if ( response==JOptionPane.OK_OPTION ) {
            RequestProcessor.invokeLater(run);
        }
        return response;
    }
}
