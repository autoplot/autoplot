/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import external.PlotCommand;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.system.RequestProcessor;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.python.core.PySystemState;
import org.python.util.InteractiveInterpreter;
import org.python.util.PythonInterpreter;
import org.virbo.autoplot.dom.Application;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSourceUtil;

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
     * @param url
     */
    public static void invokeScriptSoon( final URL url ) {
        invokeScriptSoon( url, null, new NullProgressMonitor() );
    }

    /**
     * run the script on its own thread.  
     * @param url
     * @param dom, if null, then null is passed into the script and the script must not use dom.
     * @param mon, if null, then a NullProgressMonitor is created.
     */
    public static void invokeScriptSoon( final URL url, final Application dom, ProgressMonitor mon1 ) {
        final ProgressMonitor mon;
        if ( mon1==null ) {
            mon= new NullProgressMonitor();
        } else {
            mon= mon1;
        }
        Runnable run= new Runnable() {
            public void run() {
                try {
                    PythonInterpreter interp = JythonUtil.createInterpreter(true, false, dom, mon );
                    logger.log(Level.FINE, "invokeScriptSoon({0})", url);
                    interp.execfile(url.openStream(), url.toString());
                    mon.finished();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
        };
        RequestProcessor.invokeLater(run);
    }
}
