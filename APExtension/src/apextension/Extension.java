
package apextension;

/**
 * Demo of extension to Autoplot.  Note an extension will have restricted security, so do not attempt 
 * to load and save files.
 *
 * Create a jar file from this project (APExtenson), and load it like so:
 *<blockquote><pre><small>{@code
 * import sys
 * sys.path.append("/home/jbf/APExtension.jar")
 * from apextension import Extension
 * print Extension.total( 3,4 )
 *}</small></pre></blockquote>
 * 
 * @author jbf
 */
public class Extension {
    public static double total( double a, double b ) {
        return a+b;
    }
}
