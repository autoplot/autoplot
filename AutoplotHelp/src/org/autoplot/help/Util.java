
package org.autoplot.help;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jbf
 */
public class Util {

    /**
     * support restricted security environment by checking permissions before
     * checking property.
     * @param name
     * @param deft
     * @return
     */
    public static String getProperty(String name, String deft) {
        try {
            return System.getProperty(name, deft);
        } catch (SecurityException ex) {
            Logger.getLogger(Util.class.getName()).log(Level.INFO, null, ex);
            return deft;
        }
    }

    /**
     * open the URL in a browser.  
     * @param url
     */
    public static void openBrowser(String url) {
        try {
            java.net.URI target= new URI(url);
            Desktop.getDesktop().browse( target );
        } catch (IOException | URISyntaxException ex) {
            Logger.getLogger(Util.class.getName()).log(Level.SEVERE, null, ex);
        }
    }


}
