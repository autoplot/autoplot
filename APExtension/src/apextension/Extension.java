/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package apextension;

/**
 * Demo of extension to Autoplot.  Note an extension will have restricted security, so do not attempt to load and save files.
 *
 * create a jar file from this project, and load it like so:
 * import sys
 * sys.path.append("/home/jbf/APExtension.jar")
 * from apextension import Extension
 * print Extension.total( 3,4 )
 * 
 * @author jbf
 */
public class Extension {
    public static double total( double a, double b ) {
        return a+b;
    }
}
