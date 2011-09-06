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
import org.python.core.Py;
import org.python.core.PyDictionary;
import org.python.util.PythonInterpreter;


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
