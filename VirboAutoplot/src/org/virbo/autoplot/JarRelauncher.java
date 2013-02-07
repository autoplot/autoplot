/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The only way to get an executable jar to have more memory is to have a little java process that
 * launches a bigger one that has more memory available to it.
 *
 * http://stackoverflow.com/questions/12201878/specifying-jvm-heap-sizes-in-jar?rq=1
 *
 * This is also Ed West's recommendation.
 * 
 * @author jbf
 */
public class JarRelauncher {
    public static void main( String[] args ) {
        //TODO: can a CDF file be dropped onto a jar file?
        String myPath= System.getProperty("pwd"); //TODO: check this
        if ( System.getProperty("os.family").equals("Windows") ) {
            String javaPath = System.getProperty("java.home") + "\\bin\\java.exe";
            try {
                Runtime.getRuntime().exec("\\" + javaPath + " -Xmx1000M -jar " + myPath + "autoplot.jar org.virbo.autoplot.AutoplotUI");
            } catch (IOException ex) {
                Logger.getLogger(JarRelauncher.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
