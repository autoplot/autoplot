/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.jythonsupport;

import java.io.IOException;
import java.net.URL;
import org.python.core.Py;
import org.python.core.PySystemState;
import org.python.util.InteractiveInterpreter;
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
     * @param sandbox limit symbols to safe symbols for server.
     * @return PythonInterpreter ready for commands.
     * @throws java.io.IOException
     */
    public static InteractiveInterpreter createInterpreter(boolean sandbox) throws IOException {
        if ( PySystemState.cachedir==null ) {
            System.setProperty( "python.cachedir", System.getProperty("user.home")+"/autoplot_data/pycache" );
        }
        InteractiveInterpreter interp = new InteractiveInterpreter();
        Py.getAdapter().addPostClass(new PyQDataSetAdapter());
        URL imports= JythonOps.class.getResource("imports.py");
        if ( imports==null ) {
            throw new RuntimeException("unable to locate imports.py on classpath");
        }
        interp.execfile(imports.openStream(), "imports.py");
        return interp;

    }
}
