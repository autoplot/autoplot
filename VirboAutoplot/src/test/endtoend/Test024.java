/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import java.io.IOException;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.virbo.autoplot.ScriptContext;
import org.virbo.dataset.DataSetUtil;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.capability.TimeSeriesBrowse;
import org.virbo.idlsupport.APDataSet;

/**
 * Tests of the IDL/Matlab interface.
 * @author jbf
 */
public class Test024 {

    public static void example1() throws Exception {
        org.virbo.idlsupport.APDataSet apds  = new org.virbo.idlsupport.APDataSet();
        apds.setDataSetURI("http://www.autoplot.org/data/swe-np.xls?column=data&depend0=dep0");
        apds.doGetDataSet();
        System.err.println( apds.toString() );

        apds.setPreferredUnits( "hours since 2007-01-17T00:00" );
        ScriptContext.plot( DataSetUtil.asDataSet( apds.values( apds.depend(0) )), DataSetUtil.asDataSet(apds.values()) );

        ScriptContext.writeToPng("test024_001");

    }

    public static void example2() throws Exception {
        org.virbo.idlsupport.APDataSet apds  = new org.virbo.idlsupport.APDataSet();
        apds.setDataSetURI("vap+inline:ripples(20)");
        apds.doGetDataSet();
        System.err.println( apds.toString() );

        apds.setFillValue( -999 );
        ScriptContext.plot( DataSetUtil.asDataSet(apds.values()) );

        double[] vv= (double[]) apds.values();
        for ( int i=0; i<vv.length; i++ ) {
            System.err.printf("%9.3f ",vv[i]);
        }
        System.err.println();

        ScriptContext.writeToPng("test024_002");

    }

    public static void example3() throws Exception {
        org.virbo.idlsupport.APDataSet apds  = new org.virbo.idlsupport.APDataSet();
        apds.setDataSetURI("vap:file:///home/jbf/ct/hudson/data.backup/xls/hourlyForecast.xls?column=Temperature_F&depend0=Rel_Humidity_");
        apds.doGetDataSet();
        System.err.println( apds.toString() );

        apds  = new org.virbo.idlsupport.APDataSet();
        apds.setDataSetURI("vap+das2server:http://www-pw.physics.uiowa.edu/das/das2Server?dataset=das2_1/cassini/cassiniLrfc&key=33696757&start_time=2010-01-11T11:15:00.000Z&end_time=2010-01-11T21:45:00.000Z&-lfdr+ExEw+-mfdr+ExEw+-mfr+13ExEw+-hfr+ABC12EuEvEx+-n+hfr_snd+-n+lp_rswp+-n+bad_data+-n+dpf_zero+-n+mfdr_mfr2+-n+mfr3_hfra+-n+hf1_hfrc+-a+-b+30+-bgday=" );

        apds.doGetDataSet();
        System.err.println( apds.toString() );

        Object o;
        o= apds.slice("ds_1", 0 );
        double[] d= (double[])o;
        System.err.println( String.format( "apds.slice(%s,0)=double[%d]", "ds_1", d.length ) );

        o= apds.slice("ds_2", 0 );
        d= (double[])o;
        System.err.println( String.format( "apds.slice(%s,0)=double[%d]", "ds_2", d.length ) );

        System.err.println( String.format("apds.rank()=%d", apds.rank() ) );
        System.err.println( String.format("apds.length()=%d", apds.length() ) );

        System.err.print( "apds.lengths(0)=" );
        int[] lens= apds.lengths(0);
        for ( int i:lens ) System.err.print ( i + "," );
        System.err.println("");

    }

    public static void example4() throws Exception {
        org.virbo.idlsupport.APDataSet apds  = new org.virbo.idlsupport.APDataSet();
        apds.setDataSetURI("vap+inline:ripplesx(20)");
        apds.doGetDataSet();

        if ( apds.getStatus()!=0 ) {
            System.err.println( apds.getStatusMessage() );
            return;
        }
        System.err.println( apds.toString() );

        apds.setFillValue( -999 );
        ScriptContext.plot( DataSetUtil.asDataSet(apds.values()) );

        double[] vv= (double[]) apds.values();
        for ( int i=0; i<vv.length; i++ ) {
            System.err.printf("%9.3f ",vv[i]);
        }
        System.err.println();

        ScriptContext.writeToPng("test024_004");

    }

    /**
     * this was failing in PaPCo because it was clipping off the "http:" part.
     */
    public static void example5() throws InterruptedException, IOException {
        org.virbo.idlsupport.APDataSet apds  = new org.virbo.idlsupport.APDataSet();
        apds.setDataSetURI("http://cdaweb.gsfc.nasa.gov/cgi-bin/opendap/nph-dods/istp_public/data/genesis/3dl2_gim/2003/genesis_3dl2_gim_20030501_v01.cdf.dds?Proton_Density");
        apds.doGetDataSet();
        if ( apds.getStatus()!=0 ) {
            System.err.println( apds.getStatusMessage() );
            return;
        }
        System.err.println( apds.toString() );

        apds.setFillValue( -999 );
        ScriptContext.plot( DataSetUtil.asDataSet(apds.values()) );

        double[] vv= (double[]) apds.values();
        for ( int i=0; i<vv.length; i++ ) {
            System.err.printf("%9.3f ",vv[i]);
        }
        System.err.println();

        ScriptContext.writeToPng("test024_005");

    }

    /*
     * new getTimeSeriesBrowse showed a branch were names with implicit names ("http://" instead of "vap+cdf:http://") and TimeSeriesBrowse
     * were not parsed correctly.
     */
    public static void test6() throws Exception {
        {
            DataSource dss= org.virbo.datasource.DataSetURI.getDataSource( "http://cdaweb.gsfc.nasa.gov/cgi-bin/opendap/nph-dods/istp_public/data/genesis/3dl2_gim/2003/genesis_3dl2_gim_20030501_v01.cdf.dds?Proton_Density" );
            TimeSeriesBrowse tsb= org.virbo.datasource.DataSourceUtil.getTimeSeriesBrowse(dss);
            System.err.println(tsb);
        }
        {
            DataSource dss= org.virbo.datasource.DataSetURI.getDataSource( "http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX&timerange=20000109" );
            TimeSeriesBrowse tsb= org.virbo.datasource.DataSourceUtil.getTimeSeriesBrowse(dss);
            System.err.println(tsb);
        }
    }

    private static String checkAPDS( String uri, String tr ) throws Exception {
        if ( tr!=null ) {
            // mimic code in papco that is failing
            DataSource dss= org.virbo.datasource.DataSetURI.getDataSource( uri );
            TimeSeriesBrowse tsb= org.virbo.datasource.DataSourceUtil.getTimeSeriesBrowse( dss );
            DatumRange dr= DatumRangeUtil.parseTimeRange( tr ) ;
            tsb.setTimeRange(dr);
            uri= tsb.getURI();
        }
        org.virbo.idlsupport.APDataSet apds  = new org.virbo.idlsupport.APDataSet();
        apds.setDataSetURI(uri);
        apds.doGetDataSet();
        if ( apds.getStatus()!=0 ) {
            return apds.getStatusMessage();
        }
        return apds.toString();
    }

    public static void test7() throws Exception {
        String uri= "vap+das2server:http://www-pw.physics.uiowa.edu/das/das2Server?dataset=cassini/mag/mag_vectorQ&start_time=2010-01-01T00:00:00.000Z&end_time=2010-01-02T00:00:00.000Z";
        System.err.println( checkAPDS( uri, null ) );

    }
    public static void main( String[] args )  {
        try {

            example1();
            example2();
            example3();  // Jared's slice
            example4();
            example5();
            test6();
            test7();
            checkAPDS( "vap+cdaweb:ds=PO_K0_MFE&id=MBTIGRF&filter=polar&timerange=2003-05-01", "2003-05-02" );

            System.exit(0);  // TODO: something is firing up the event thread.  Note, we finally figured out that this is das2's request processor threads.

        } catch ( Exception ex ) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
