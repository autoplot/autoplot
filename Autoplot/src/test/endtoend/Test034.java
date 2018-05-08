
package test.endtoend;

import java.io.File;
import java.util.logging.Level;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.DatumRange;
import org.autoplot.dom.Application;

import static org.autoplot.ScriptContext.*;
import org.autoplot.datasource.AutoplotSettings;

/**
 * Test tool to simulate more sophistocated GUI operations
 * See https://sourceforge.net/p/autoplot/bugs/748/
 * 
 * @author jbf
 */
public class Test034 {

    private final static String tsbURI= "http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hydra/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX&timerange=20000109";
    private final static String noTsbURI= "http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hydra/hyd_h0/2000/po_h0_hyd_20000109_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX";

    private static void test001() throws Exception {
        reset();
        final Application dom = getDocumentModel();
        plot(tsbURI);
        dom.getPlots(0).getController().contextOverview();
        DatumRange z = dom.getPlots(1).getXaxis().getRange();
        z = DatumRangeUtil.rescale(z, -0.3, 1.3);
        dom.getPlots(1).getXaxis().setRange(z);
        writeToPng( "test034_001.png" );
    }

    private static void test002() throws Exception {
        reset();
        final Application dom= getDocumentModel();
        plot( tsbURI );
        dom.getPlots(0).getController().contextOverview();
        dom.getPlotElements(1).setComponent("|slice0(100)");
        writeToPng( "test034_002.png" );
    }

    private static void test003() throws Exception {
        reset();
        org.das2.util.LoggerManager.getLogger("autoplot.dom").setLevel(Level.ALL);
        final Application dom= getDocumentModel();
        plot( noTsbURI );
        dom.getPlots(0).getController().contextOverview();
        dom.getPlotElements(1).setComponent("|slice0(100)");

        writeToPng( "test034_003.png" );
    }

    private static void test004() throws Exception {
        reset();
        Application dom= getDocumentModel();
        plot( "file:/home/jbf/ct/hudson/vap/lanl/lanlGeoEpDemo4.vap" );
        writeToPng( "test034_004a.png" );
        DatumRange tr= dom.getTimeRange();
        tr= DatumRangeUtil.rescale( tr, -0.7, 0.3 );
        dom.setTimeRange(tr);
        writeToPng( "test034_004b.png" );
    }

    /**
     * main method for testing.
     * @param args
     * @throws Exception 
     */
    public static void main( String[] args ) throws Exception {

        long t0;

        getDocumentModel().getOptions().setAutolayout(false);

        System .err.println("pwd: "+(new File(".")).getCanonicalPath());
        System.err.println("autoplot_data: "+AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA) );
        System.err.println("fscache: "+AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_FSCACHE) );
        
        t0= System.currentTimeMillis();
        test003();
        System.err.printf( "test 003a: done in %9.2f sec\n", ( System.currentTimeMillis()-t0 ) / 1000. );
        System.err.println( "==========================" );

        t0= System.currentTimeMillis();
        test001();
        System.err.printf( "test 001: done in %9.2f sec\n", ( System.currentTimeMillis()-t0 ) / 1000. );
        System.err.println( "==========================" );

        t0= System.currentTimeMillis();
        test002();
        System.err.printf( "test 002: done in %9.2f sec\n", ( System.currentTimeMillis()-t0 ) / 1000. );
        System.err.println( "==========================" );

        t0= System.currentTimeMillis();
        test003();
        System.err.printf( "test 003: done in %9.2f sec\n", ( System.currentTimeMillis()-t0 ) / 1000. );
        System.err.println( "==========================" );

        t0= System.currentTimeMillis();
        // this shows hidden TSB, where bottom two panels listen to the dom.timeRange to filter what they see.
        test004();
        System.err.printf( "test 004: done in %9.2f sec\n", ( System.currentTimeMillis()-t0 ) / 1000. );

        System.exit(0);
    }
}
