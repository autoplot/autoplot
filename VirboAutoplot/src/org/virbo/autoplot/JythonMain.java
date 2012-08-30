/*
 * JythonLauncher.java
 *
 * Created on November 1, 2007, 3:04 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import org.python.core.Py;
import org.python.core.PyDictionary;
import org.python.util.PythonInterpreter;
import org.virbo.datasource.DataSourceUtil;


/**
 *
 * @author jbf
 */
public class JythonMain {
    
    /** Creates a new instance of JythonLauncher */
    public JythonMain() {
    }
    
    static PythonInterpreter interp = null;
    
    public static PythonInterpreter getInterpreter() {
        return interp;
    }

    private static void insertArgs( String[] argv ) {
        interp.exec("params=dict()"); // untested.
        int iargv=-1;  // skip the zeroth one, it is the name of the script
        for ( int i=0; i<argv.length; i++ ) {
            String s= argv[i];
            int ieq= s.indexOf("="); 
            if ( ieq>0 ) {
                String snam= s.substring(0,ieq).trim();
                if ( DataSourceUtil.isJavaIdentifier(snam) ) {
                    String sval= s.substring(ieq+1).trim();
                    interp.exec("params['" + snam + "']='" + sval+"'");
                } else {
                    if ( snam.startsWith("-") ) {
                        System.err.println("script arguments should not start with -, they should be name=value");
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
    }
    
    public static void main(String[] args) throws Exception {

        System.err.println("org.virbo.autoplot.JythonMain "+APSplash.getVersion());
        interp = JythonUtil.createInterpreter(true,false);
        interp.set("dom", ScriptContext.getDocumentModel() );
        interp.set("params", new PyDictionary()); //TODO: mimic autoplotUI, which allows arguments to script.
        interp.set("resourceURI", Py.None );

        InputStream in;
        String inIdentifier;
        
        if ( args.length==0 ) {
            in= System.in;
            inIdentifier= "<stdin>";
        } else {
            if ( args.length>1 ) {
                insertArgs( args );
            }
            File f= new File( args[0] );
            if ( !f.exists() ) {
                System.err.println("File does not exist: "+f);
                System.exit(-1);
            }
            in= new FileInputStream(f);
            inIdentifier= f.toString();
        }
        
        interp.execfile( in, inIdentifier );
        System.exit(0);
    }
}
