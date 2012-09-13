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


/**
 * Support for invoking Jython script.  This is Just ".../AutoplotIU --script ..." stripped down, and was rewritten after Bob and Jeremy
 * would see inconsistent behavior between:
 *   /usr/local/jre1.6.0_25/bin/java -cp ./autoplot.jar -Djava.awt.headless=true org.virbo.autoplot.JythonMain `pwd`/testVars.jy   (and)
 *   /usr/local/jre1.6.0_25/bin/java -cp ./autoplot.jar -Djava.awt.headless=true org.virbo.autoplot.AutoplotUI --script=`pwd`/testVars.jy
 * @author jbf
 */
public class JythonMain {
    
    /** Creates a new instance of JythonLauncher */
    public JythonMain() {
    }
    
    public static void main(String[] args) throws Exception {

        System.err.println("org.virbo.autoplot.JythonMain "+APSplash.getVersion());

        String argv[]= new String[ Math.max(0,args.length-1) ];
        for ( int i=1; i<args.length; i++ ) { // first arg is the name of the script.
            argv[i-1]= args[i];
        }

        ApplicationModel model= ScriptContext.getApplicationModel();

        InputStream in= null;
        if ( args.length==0 ) {
            in= System.in;
        } else {
            in= new FileInputStream( new File( args[0] ) );
        }
        JythonUtil.runScript( model, in, argv );

        in.close();
        System.exit(0);
    }
}
