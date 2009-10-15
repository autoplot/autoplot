/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.system.RequestProcessor;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.python.core.PySystemState;
import org.python.util.PythonInterpreter;
import org.virbo.autoplot.dom.Application;
import org.virbo.datasource.DataSetURI;

/**
 *
 * @author jbf
 */
public class JythonUtil {
    /**
     * create an interpretter object configured for Autoplot contexts:
     *   * QDataSets are wrapped so that operators are overloaded.
     *   * a standard set of names are imported.
     *   
     * @param appContext load in additional symbols that make sense in application context.
     * @param sandbox limit symbols to safe symbols for server.
     * @return PythonInterpreter ready for commands.
     * @throws java.io.IOException
     */
    public static PythonInterpreter createInterpreter( boolean appContext, boolean sandbox ) throws IOException {
        PythonInterpreter interp= org.virbo.jythonsupport.JythonUtil.createInterpreter(sandbox);
        if ( appContext ) interp.execfile( JythonUtil.class.getResource("appContextImports.py").openStream(), "appContextImports.py" );
        return interp;
    }

    public static PythonInterpreter createInterpreter( boolean appContext, boolean sandbox, Application dom, ProgressMonitor mon ) throws IOException {
        PythonInterpreter interp= createInterpreter(appContext, sandbox);
        if ( dom!=null ) interp.set("dom", dom );
        if ( mon!=null ) interp.set("monitor", mon );
        return interp;
    }

    protected static void runScript( ApplicationModel model, String script, String[] argv ) throws IOException {
        if ( argv==null ) argv= new String[] {""};
        PySystemState.initialize( PySystemState.getBaseProperties(), null, argv );
        PythonInterpreter interp = JythonUtil.createInterpreter(true, false, model.getDocumentModel(), new NullProgressMonitor() );
        URL url= DataSetURI.getURL(script);
        InputStream in= url.openStream();
        interp.execfile(in);
        in.close();
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
                    interp.execfile(url.openStream(), url.toString());
                    mon.finished();
                } catch (IOException ex) {
                    Logger.getLogger(AutoPlotUI.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };
        RequestProcessor.invokeLater(run);
    }
}
