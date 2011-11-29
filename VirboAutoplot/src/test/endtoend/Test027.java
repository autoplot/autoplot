/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSetURI.CompletionResult;
import org.virbo.datasource.URISplit;

/**
 * Tests of URI parsing. This should have been done ages ago, but better late than never. This is
 * added along with new support where vap+inline:rand(200) is property parsed and formatted.
 * @author jbf
 */
public class Test027 {

    private static String spaces= "         "
            + "                             "
            + "                             "
            + "                             ";


    public static void doTest( int id, String uri ) throws Exception {
        URISplit split;
        
        int cp= uri.length()/2;

        PrintWriter out = new PrintWriter( String.format( "test027_%03d.txt",id) );
        out.println("===");
        out.println(uri);
        out.println( spaces.substring(0,cp) + "^" );

        split= URISplit.parse( uri, cp, true );

        out.println( URISplit.format(split) );

        out.println( spaces.substring(0,split.formatCarotPos) + "^" );
        out.println( URISplit.implicitVapScheme(split) );
        out.println("===");
        
        out.println(split);

        out.close();

    }

    private static void doTestComp( int id, String uri ) throws Exception {
        // test completions
        System.err.print( String.format( "=== %d %s ===", id, uri ) );
        for ( int i=0; i<uri.length(); i++ ) {
            try {
                System.err.print( uri.substring(0,i)+"<C>...");
                List<CompletionResult> result= DataSetURI.getCompletions(uri.substring(0,i), i, new NullProgressMonitor() );
                System.err.println( result.size() );
            } catch ( Exception e ) {
                System.err.println( e.getMessage() );
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args)  {
        try {

            URISplit split;

            doTest( 13, "vap:file:///home/jbf/ct/hudson/data.backup/cdf/po_hyd/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX&timerange=2000-01-09" );
            doTest( 0, "vap+cdaweb:ds=ac_k0_epm&H_lo&timerange=2010-01" );
            doTest( 1, "vap+inline:rand(200)" );
            doTest( 2, "vap+inline:accum(randomn(0,1000))&DEPEND_0=accum(randomn(1,1000))" );
            doTest( 3, "/tmp/foo.txt" );
            doTest( 4, "vap+dat:file:///tmp/foo.txt" );
            doTest( 5, "vap+dat:http://autoplot.org/data/foo.txt" );
            doTest( 6, "vap+dat:http://user:pass@autoplot.org/data/foo.txt" );
            doTest( 7, "vap+dat:http://autoplot.org/data/foo.txt?" );
            doTest( 8, "vap+dat:http://user:pass@autoplot.org/data/foo.txt?foo=x&bar=y" );
            doTest( 9, "file:/home/jbf/ct/hudson/data.backup/xls/2008-lion and tiger summary.xls?sheet=Samantha+tiger+lp+lofreq&firstRow=53&column=Complex_Modulus&depend0=Frequency" );
            doTest( 10, "vap+internal:" );
            doTest( 11, "vap+cdaweb:" ); // still adds file:///
            doTest( 12, "Enter Data Set" ); //TODO: fix NullPointer
            doTest( 13, "vap:file:///home/jbf/ct/hudson/data.backup/cdf/po_hyd/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX&timerange=2000-01-09" );
            doTest( 14, "c:/Users/sarah/Desktop/x.vap" );
            doTest( 15, "http://sarahandjeremy.net:8080/albumServer/PhotoServer?image=20080201_misc_geothermal/IMG_2414crop.JPG&size=600&width=500&rotate=0");

            //this would get confused because it would use the .net as the start of the extension.
            doTest( 16, "file:///home/jbf/autoplot_data/fscache/temp/http/sarahandjeremy.net/albumServer/PhotoServer__imageeq20080201_misc_geothermaldivIMG_2414cropptJPG_sizeeq600_widtheq500_rotateeq0?channel=hue");
            
            doTestComp( 100, "vap+cdaweb:ds=ac_k0_epm&H_lo&timerange=2010-01" );
            doTestComp( 101, "Enter Data Set" );
            doTestComp( 103, "papco@mrfrench.lanl.gov/" );

            System.exit(0);  // TODO: something is firing up the event thread
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
