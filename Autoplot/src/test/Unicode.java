
package test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

/**
 * See https://sourceforge.net/p/autoplot/bugs/2108/
 * @author jbf
 */
public class Unicode {
    public static void main(String[] args) throws UnsupportedEncodingException, IOException {
        // There is interesting code in the SVN history.
        System.err.println("Charset.defaultCharset()="+Charset.defaultCharset() );
        try ( PrintWriter fw = new PrintWriter( new FileWriter("unicode.output.txt") ) ) {
            
            String[] ff= new File("/home/jbf/public_html/i18n/").list();
            System.err.println("ls /home/jbf/public_html/i18n/");
            fw.println("ls /home/jbf/public_html/i18n/");
            for ( String f: ff ) {
                System.err.println(f);
                fw.println(f);
            }
        }
    }
}
