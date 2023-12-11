/*
 * JythonLauncher.java
 *
 * Created on November 1, 2007, 3:04 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.autoplot;

import org.autoplot.ApplicationModel;
import org.autoplot.APSplash;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;


/**
 * Support for invoking Jython script.  This is Just ".../AutoplotUI --script ..." stripped down, and was rewritten after Bob and Jeremy
 * would see inconsistent behavior between:<pre>
 *  /usr/local/jre1.6.0_25/bin/java -cp ./autoplot.jar -Djava.awt.headless=true org.autoplot.JythonMain `pwd`/testVars.jy   (and)
 *  /usr/local/jre1.6.0_25/bin/java -cp ./autoplot.jar -Djava.awt.headless=true org.autoplot.AutoplotUI --script=`pwd`/testVars.jy
 * </pre>
 * @author jbf
 */
public class JythonMain {
    
    public JythonMain() {
    }
    
    /**
     * org.virbo.autoplot.JythonMain 
     * <ul>
     * <li>no args, get input from stdin
     * <li>one arg is the name of a local file containing the script.
     * </ul>
     * @param args zero or one command line argument.
     * @throws Exception 
     */
    public static void main(String[] args) throws Exception {

        System.err.println("org.autoplot.JythonMain "+APSplash.getVersion());

        String argv[]= new String[ Math.max(0,args.length-1) ];
        for ( int i=1; i<args.length; i++ ) { // first arg is the name of the script.
            argv[i-1]= args[i];
        }

        ApplicationModel model= ScriptContext2023.getInstance().getApplicationModel();

        InputStream in;
        String name= null;
        String pwd;
        if ( args.length==0 ) {
            in= System.in;
            pwd= new File(".").getAbsolutePath();
            pwd= pwd.substring(0,pwd.length()-1);
        } else {
            File f= new File( args[0] );
            f= f.getAbsoluteFile();
            in= new FileInputStream( f );
            name= f.getName();
            pwd= f.getParent();
        }
        JythonUtil.runScript(model, in, name, argv, pwd );

        in.close();
        System.exit(0);
    }
}
