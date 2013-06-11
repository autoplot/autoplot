/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import external.PlotCommand;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.das2.system.RequestProcessor;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.python.core.PySystemState;
import org.python.util.InteractiveInterpreter;
import org.python.util.PythonInterpreter;
import org.virbo.autoplot.dom.Application;
import org.virbo.datasource.AutoplotSettings;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSourceUtil;
import org.virbo.jythonsupport.ui.EditorTextPane;
import org.virbo.jythonsupport.ui.ScriptPanelSupport;

/**
 *
 * @author jbf
 */
public class JythonUtil {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot");

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
        if ( appContext ) interp.execfile( JythonUtil.class.getResource("appContextImports.py").openStream(), "appContextImports.py" );
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
        runScript( model, in, argv );
        in.close();
    }

    /**
     * Run the script in the input stream.
     * @param model
     * @param in stream containing script. This will be left open.
     * @param argv
     * @throws IOException
     */
    protected static void runScript( ApplicationModel model, InputStream in, String[] argv ) throws IOException {
        if ( argv==null ) argv= new String[] {""};
        PySystemState.initialize( PySystemState.getBaseProperties(), null, argv ); // legacy support sys.argv. now we use getParam
        PythonInterpreter interp = JythonUtil.createInterpreter(true, false, model.getDocumentModel(), new NullProgressMonitor() );

        System.err.println();
        
        interp.exec("params=dict()"); // untested.
        int iargv=-1;  // skip the zeroth one, it is the name of the script
        for (String s : argv ) {
            int ieq= s.indexOf("=");
            if ( ieq>0 ) {
                String snam= s.substring(0,ieq).trim();
                if ( DataSourceUtil.isJavaIdentifier(snam) ) {
                    String sval= s.substring(ieq+1).trim();
                    interp.exec("params['" + snam + "']='" + sval+"'");
                } else {
                    if ( snam.startsWith("-") ) {
                        System.err.println("\n!!! Script arguments should not start with -, they should be name=value");
                    }
                    System.err.println("bad parameter: "+ snam);
                }
            } else {
                if ( iargv>=0 ) {
                    interp.exec("params['arg_" + iargv + "']='" + s +"'" );
                    iargv++;
                } else {
                    //System.err.println("skipping parameter" + s );
                    iargv++;
                }
            }
        }
        
        interp.execfile(in);

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
    public static void invokeScriptSoon( final URL url, final Application dom, ProgressMonitor mon1 ) throws IOException {
        invokeScriptSoon( url, dom, new HashMap(), false, mon1 );
    }
    
    /**
     * show the script and the variables (like we have always done with jyds scripts)
     * @param parent
     * @param file
     * @param fvars
     * @return JOptionPane.OK_OPTION or JOptionPane.CANCEL_OPTION if the user cancels.
     */
    private static int showScriptDialog( Component parent, File file, Map<String,String> fvars ) {
        
        JPanel p= new JPanel();
        org.virbo.jythonsupport.ui.Util.FormData fd=  org.virbo.jythonsupport.ui.Util.doVariables( file, fvars, p );

        if ( fd.count==0 ) {
            return JOptionPane.OK_OPTION;
        }
        
        JTabbedPane tp= new JTabbedPane();
        org.virbo.jythonsupport.ui.EditorTextPane textArea= new EditorTextPane();
        try {
            textArea.loadFile(file);
            textArea.setMinimumSize( new Dimension(640,480) );
        } catch (FileNotFoundException ex) {
            Logger.getLogger(JythonUtil.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(JythonUtil.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        ScriptPanelSupport support;
        support= new ScriptPanelSupport(textArea);
        support.setReadOnly();
        
        tp.add( new JScrollPane(textArea), "script" );
        
        tp.add( new JScrollPane(p), "params" );
        tp.setSelectedIndex(1);
                
        int result= AutoplotUtil.showConfirmDialog2( parent, tp, "run script", JOptionPane.OK_CANCEL_OPTION );
        if ( result==JOptionPane.OK_OPTION ) {
            org.virbo.jythonsupport.ui.Util.resetVariables( fd, fvars );
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
     * @param mon monitor to detect when script is finished.  If null, then a NullProgressMonitor is created.
     */
    public static void invokeScriptSoon( final URL url, final Application dom, Map<String,String> vars, boolean askParams, ProgressMonitor mon1 ) throws IOException {
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
        
        int response= JOptionPane.OK_OPTION;
        if ( askParams ) {     
            file = DataSetURI.getFile( url, new NullProgressMonitor() );
            response= showScriptDialog( dom.getController().getDasCanvas(), file, fvars );
        } else {
            file = DataSetURI.getFile( url, new NullProgressMonitor() );
        }
        
        Runnable run= new Runnable() {
            public void run() {
                try {
                    PythonInterpreter interp = JythonUtil.createInterpreter(true, false, dom, mon );
                    logger.log(Level.FINE, "invokeScriptSoon({0})", url);
                    for ( Map.Entry<String,String> v: fvars.entrySet() ) {
                        if ( v.getValue() instanceof String ) {
                            if ( !v.getValue().startsWith("'") ) {
                                interp.exec( String.format("params['%s']='%s'", v.getKey(), v.getValue() ) );
                            } else {
                                interp.exec( String.format("params['%s']=%s", v.getKey(), v.getValue() ) );
                            }
                        } else {
                            interp.exec( String.format("params['%s']=%s", v.getKey(), v.getValue() ) );
                        }
                    }
                    interp.execfile( new FileInputStream(file), url.toString());
                    mon.finished();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
        };
        if ( response==JOptionPane.OK_OPTION ) {
            RequestProcessor.invokeLater(run);
        }
    }
}
