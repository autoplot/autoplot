/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.das2.util.monitor.NullProgressMonitor;
import org.autoplot.datasource.DataSetURI;

/**
 * checks to see if our favorite servers are responsive.
 * @author jbf
 */
public class Test010 {

    public static void doTest( String suri ) throws Exception {
        URL url= DataSetURI.getWebURL( DataSetURI.toUri( suri ) );
        DataSetURI.downloadResourceAsTempFile( url, 1, new NullProgressMonitor() );

    }
    
    public static void main(String[] args)  {
        List<String> tests= new ArrayList();
        tests.add("http://emfisis.physics.uiowa.edu/Flight/RBSP-B/Quick-Look/");
        tests.add("http://autoplot.org/data/autoplot.dat");
        //tests.add("http://timeseries.org/get.cgi?StartDate=19980101&EndDate=20090101&ppd=1&ext=bin&out=tsml&param1=NGDC_NOAA15_SEM2-33-v0" );
        //tests.add("http://www.rbsp-ect.lanl.gov/"); // see email 2016-03-09 14:27 CST
        tests.add("https://cdaweb.gsfc.nasa.gov/istp_public/data/");
        tests.add("https://cdaweb.gsfc.nasa.gov/pub/data/");
        //tests.add("ftp://nssdcftp.gsfc.nasa.gov/spacecraft_data/omni/");
        //tests.add("http://caa.estec.esa.int/caa/search.xml");
        tests.add("http://papco.org/data");
        tests.add("http://demo:demo@www-pw.physics.uiowa.edu/~jbf/data/restrict/data_2010_03_02_v1.02.qds");
        
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
