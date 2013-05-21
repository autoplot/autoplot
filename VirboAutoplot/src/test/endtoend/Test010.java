/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import org.virbo.datasource.DataSetURI;

/**
 * checks to see if our favorite servers are responsive.
 * @author jbf
 */
public class Test010 {

    public static void doTest( String suri ) throws Exception {
        URL url= DataSetURI.getWebURL( DataSetURI.toUri( suri ) );

        URLConnection connect= url.openConnection();
        connect.setConnectTimeout(500);

        connect.connect();

    }
    
    public static void main(String[] args)  {
        List<String> tests= new ArrayList();
        tests.add("http://autoplot.org/data/foo.dat");
        tests.add("http://timeseries.org/get.cgi?StartDate=19980101&EndDate=20090101&ppd=1&ext=bin&out=tsml&param1=NGDC_NOAA15_SEM2-33-v0" );
        tests.add("http://stevens.lanl.gov/");
        tests.add("http://cdaweb.gsfc.nasa.gov/istp_public/data/");
        tests.add("ftp://cdaweb.gsfc.nasa.gov/pub/istp/");
        //tests.add("ftp://nssdcftp.gsfc.nasa.gov/spacecraft_data/omni/");
        tests.add("http://caa.estec.esa.int/caa/search.xml");
        tests.add("http://papco.org/data");

        List<Exception> exceptions= new ArrayList();
        for ( String uri: tests ) {
            System.out.println("## "+uri+" ##");
            try {
                doTest( uri );
                System.out.println("ok");
            } catch (Exception ex) {
                ex.printStackTrace(System.out);
                exceptions.add(ex);
            }
        }
        if ( exceptions.size()==0 ) {
            System.exit(0);
        } else {
            System.exit(1);
        }
    }
}
