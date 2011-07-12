/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.jythonsupport;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import org.python.core.Py;
import org.python.core.PyObject;
import org.python.core.PyString;
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
        
        //r.
        ///  http://www.gossamer-threads.com/lists/python/python/697524
        org.python.core.PySystemState pySys = new org.python.core.PySystemState();
        URL jarUrl= InteractiveInterpreter.class.getResource("/glob.py");
        if ( jarUrl!=null ) {
            String jarFile= jarUrl.toString();

            if ( jarFile.startsWith("jar:") && jarFile.contains("!") ) {
                int i= jarFile.indexOf("!");
                String jar= jarFile.substring(9,i);
                pySys.path.insert(0, new PyString(jar));
            }
            
        } else {
            System.err.println("Not adding Lib stuff!!!  See https://sourceforge.net/tracker/index.php?func=detail&aid=3134982&group_id=199733&atid=970682");
        }

        InteractiveInterpreter interp = new InteractiveInterpreter( null, pySys );
        
        Py.getAdapter().addPostClass(new PyQDataSetAdapter());
        URL imports= JythonOps.class.getResource("imports.py");
        if ( imports==null ) {
            throw new RuntimeException("unable to locate imports.py on classpath");
        }
        interp.execfile(imports.openStream(), "imports.py");
        return interp;

    }
}
