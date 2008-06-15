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
import org.python.util.PythonInterpreter;
import org.virbo.jythonsupport.PyQDataSetAdapter;

/**
 *
 * @author jbf
 */
public class JythonMain {
    
    /** Creates a new instance of JythonLauncher */
    public JythonMain() {
    }
    
    static PythonInterpreter interp =  new PythonInterpreter();
    
    public static PythonInterpreter getInterpreter() {
        return interp;
    }
    
    public static void main(String[] args) throws Exception {
        
        interp = new PythonInterpreter( );
        
        Py.getAdapter().addPostClass(new PyQDataSetAdapter());

        interp.execfile( JythonMain.class.getResource("imports.py").openStream(), "imports.py" );

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
        
    }
}
