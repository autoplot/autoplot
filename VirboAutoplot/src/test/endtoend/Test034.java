/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import org.das2.datum.DatumRangeUtil;
import org.das2.datum.DatumRange;
import org.virbo.autoplot.dom.Application;

import static org.virbo.autoplot.ScriptContext.*;

/**
 * Test tool to simulate more sophistocated GUI operations
 * @author jbf
 */
public class Test034 {

    private final static String tsbURI= "http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX&timerange=20000109";
    private final static String noTsbURI= "http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hyd_h0/2000/po_h0_hyd_20000109_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX";

    private static void test001() throws Exception {
        reset();
        Application dom= getDocumentModel();
        plot( tsbURI );
        dom.getPlots(0).getController().contextOverview();
        DatumRange z= dom.getPlots(1).getXaxis().getRange();
        z= DatumRangeUtil.rescale( z, -0.3, 1.3 );
        dom.getPlots(1).getXaxis().setRange(z);
        writeToPng( "test034_001.png" );
    }

    private static void test002() throws Exception {
        reset();
        Application dom= getDocumentModel();
        plot( tsbURI );
        dom.getPlots(0).getController().contextOverview();
        dom.getPlotElements(1).setComponent("|slice0(100)");
        writeToPng( "test034_002.png" );
    }

    private static void test003() throws Exception {
        reset();
        Application dom= getDocumentModel();
        plot( noTsbURI );
        dom.getPlots(0).getController().contextOverview();
        dom.getPlotElements(1).setComponent("|slice0(100)");
        writeToPng( "test034_003.png" );
    }

    public static void main( String[] args ) throws Exception {

        long t0;

        t0= System.currentTimeMillis();
        test001();
        System.err.printf( "test 001: done in %9.2f sec\n", ( System.currentTimeMillis()-t0 ) / 1000. );

        t0= System.currentTimeMillis();
        test002();
        System.err.printf( "test 002: done in %9.2f sec\n", ( System.currentTimeMillis()-t0 ) / 1000. );

        t0= System.currentTimeMillis();
        test003();
        System.err.printf( "test 003: done in %9.2f sec\n", ( System.currentTimeMillis()-t0 ) / 1000. );
        
        System.exit(0);
    }
}
