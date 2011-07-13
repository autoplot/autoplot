/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.jythonsupport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.net.URI;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.util.monitor.NullProgressMonitor;
import org.python.core.Py;
import org.python.core.PyException;
import org.python.core.PyNone;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.core.PySystemState;
import org.python.util.InteractiveInterpreter;
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

    /**
     * check the script that it doesn't redefine symbol names like "str"
     * @param code
     * @return true if an err is suspected.
     */
    public static boolean pythonLint( URI uri, List<String> errs ) throws IOException {
        LineNumberReader reader= null;

        File src = DataSetURI.getFile( uri, new NullProgressMonitor());

        try {
            reader = new LineNumberReader( new BufferedReader( new FileReader(src)) );

            String vnarg= "\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*"; // any variable name  VERIFIED

            Pattern assign= Pattern.compile( vnarg+"=.*" );

            InteractiveInterpreter interp= createInterpreter(true);

            String line= reader.readLine();
            while ( line!=null ) {
                Matcher m= assign.matcher(line);
                if ( m.matches() ) {
                    String vname= m.group(1);
                    try {
                        PyObject po= interp.eval(vname);
                        errs.add( "" + reader.getLineNumber() + ": "+ vname + "=" + po.__repr__() );
                    } catch ( PyException ex ) {
                        // this is what we want
                    }
                }
                line= reader.readLine();
            }
            reader.close();

        } finally {
            try {
                reader.close();
            } catch (IOException ex) {
                Logger.getLogger(JythonUtil.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return errs.size()>0;


    }
}
