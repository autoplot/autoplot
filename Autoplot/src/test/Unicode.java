
package test;

import java.io.File;
import java.io.UnsupportedEncodingException;

/**
 *
 * @author jbf
 */
public class Unicode {
    public static void main(String[] args) throws UnsupportedEncodingException {
        // There is interesting code in the SVN history.
        String[] ff= new File("/home/jbf/public_html/i18n/").list();
        System.err.println("ls /home/jbf/public_html/i18n/");
        for ( String f: ff ) { 
            System.err.println(f);
        }
    }
}
