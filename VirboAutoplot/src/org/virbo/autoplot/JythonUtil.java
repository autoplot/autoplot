/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import java.io.IOException;
import org.python.util.PythonInterpreter;

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

}
