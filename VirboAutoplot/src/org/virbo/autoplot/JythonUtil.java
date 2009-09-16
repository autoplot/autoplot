/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
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

    protected static void runScript( ApplicationModel model, String script, String[] argv ) throws IOException {
        if ( argv==null ) argv= new String[] {""};
        PySystemState.initialize( PySystemState.getBaseProperties(), null, argv );
        PythonInterpreter interp = JythonUtil.createInterpreter(true, false);
        interp.set("dom", model.getDocumentModel() );
        URL url= DataSetURI.getURL(script);
        InputStream in= url.openStream();
        interp.execfile(in);
        in.close();
    }

}
