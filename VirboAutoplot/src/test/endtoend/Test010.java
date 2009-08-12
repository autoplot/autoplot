/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import org.virbo.autoplot.ScriptContext;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetURL;
import org.virbo.dsops.Ops;
import org.virbo.jythonsupport.Util;

/**
 * checks to see if our favorite servers are responsive.
 * @author jbf
 */
public class Test010 {

    public static void doTest( int id, String uri ) throws Exception {
        URL url= DataSetURL.getWebURL( new URI( uri ) );

        URLConnection connect= url.openConnection();
        connect.setConnectTimeout(500);

        connect.connect();
        
    }
    
    public static void main(String[] args) throws InterruptedException, IOException, Exception {
        try {
            doTest( 0, "http://autoplot.org/data/foo.dat" );
            System.exit(0);  // TODO: something is firing up the event thread
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
