/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import java.io.IOException;
import java.lang.reflect.Array;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ConsoleTextProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.ScriptContext;
import org.das2.qds.DataSetUtil;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.das2.qds.ops.Ops;

/**
 * Tests of the IDL/Matlab interface.
 * @author jbf
 */
public class Test024 {
    private static ScriptContext scriptContext= ScriptContext.getInstance();
    public static void example1() throws Exception {
        System.err.println( "\n= example1 =\n");
        org.autoplot.idlsupport.APDataSet apds  = new org.autoplot.idlsupport.APDataSet();
        apds.setDataSetURI("http://www.autoplot.org/data/swe-np.xls?column=data&depend0=dep0");
        apds.doGetDataSet();
        System.err.println( apds.toString() );

        apds.setPreferredUnits( "hours since 2007-01-17T00:00" );
        scriptContext.plot( DataSetUtil.asDataSet( apds.values( apds.depend(0) )), DataSetUtil.asDataSet(apds.values()) );

        scriptContext.writeToPng("test024_001");

    }

    public static void example2() throws Exception {
        System.err.println( "\n= example2 =\n");
        org.autoplot.idlsupport.APDataSet apds  = new org.autoplot.idlsupport.APDataSet();
        apds.setDataSetURI("vap+inline:ripples(20)");
        apds.doGetDataSet();
        System.err.println( apds.toString() );

        apds.setFillValue( -999 );
        scriptContext.plot( DataSetUtil.asDataSet(apds.values()) );

        double[] vv= (double[]) apds.values();
        for ( int i=0; i<vv.length; i++ ) {
            System.err.printf("%9.3f ",vv[i]);
        }
        System.err.println();

        scriptContext.writeToPng("test024_002");

    }

    public static void example3() throws Exception {
        System.err.println( "\n= example3 =\n");
        org.autoplot.idlsupport.APDataSet apds  = new org.autoplot.idlsupport.APDataSet();
        apds.setDataSetURI("vap:file:///home/jbf/ct/hudson/data.backup/xls/hourlyForecast.xls?column=Temperature_F&depend0=Rel_Humidity_");
        apds.doGetDataSet();
        System.err.println( apds.toString() );

    }

    public static void example4() throws Exception {
        System.err.println( "\n= example4 =\n");
        org.autoplot.idlsupport.APDataSet apds  = new org.autoplot.idlsupport.APDataSet();
        apds.setDataSetURI("vap+inline:ripples(20)");
        apds.doGetDataSet();

        if ( apds.getStatus()!=0 ) {
            System.err.println( apds.getStatusMessage() );
            return;
        }
        System.err.println( apds.toString() );

        apds.setFillValue( -999 );
        scriptContext.plot( DataSetUtil.asDataSet(apds.values()) );

        double[] vv= (double[]) apds.values();
        for ( int i=0; i<vv.length; i++ ) {
            System.err.printf("%9.3f ",vv[i]);
        }
        System.err.println();

        scriptContext.writeToPng("test024_004");

    }

    /**
     * this was failing in PaPCo because it was clipping off the "http:" part.
     */
    public static void example5() throws InterruptedException, IOException {
        
        System.err.println( "\n= example5 =\n");
        org.autoplot.idlsupport.APDataSet apds  = new org.autoplot.idlsupport.APDataSet();
        apds.setDataSetURI("http://acdisc.gsfc.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMAEROe.003/2005/OMI-Aura_L3-OMAEROe_2005m0101_v003-2011m1109t081947.he5.dds?lat");
        apds.doGetDataSet();
        if ( apds.getStatus()!=0 ) {
            System.err.println( apds.getStatusMessage() );
            return;
        }
        System.err.println( apds.toString() );

        apds.setFillValue( -999 );
        scriptContext.plot( DataSetUtil.asDataSet(apds.values()) );

        double[] vv= (double[]) apds.values();
        for ( int i=0; i<vv.length; i++ ) {
            System.err.printf("%9.3f ",vv[i]);
        }
        System.err.println();

        scriptContext.writeToPng("test024_005");

    }

    /**
     * we have a case where Reiner is getting zeros in a rank 3 dataset.  It was an indexing bug.
     * @throws Exception
     */
    public static void example6() throws Exception {
        System.err.println( "\n= example6 =\n");
        org.autoplot.idlsupport.APDataSet apds  = new org.autoplot.idlsupport.APDataSet();
        apds.setDataSetURI("vap+h5:file:///home/jbf/data.backup/examples/h5/19970101_Polar_23802_FluxAssimOut.v2.h5?Flux");
        apds.doGetDataSet();
        if ( apds.getStatus()!=0 ) {
            System.err.println( apds.getStatusMessage() );
            return;
        }
        System.err.println( apds.toString() );

        apds.setFillValue( -999 );

        double[][][] vv= (double[][][]) apds.values();

        for ( int i=0; i<vv.length; i++ ) {
            for ( int j=0; j<vv[i].length; j++ ) {
                System.err.printf("%9.3f ",vv[i][j][17]);                
            }
            System.err.println();
        }
        System.err.println();


    }

    /*
     * new getTimeSeriesBrowse showed a branch were names with implicit names ("http://" instead of "vap+cdf:http://") and TimeSeriesBrowse
     * were not parsed correctly.
     */
    public static void test6() throws Exception {
        System.err.println( "\n= test6 =\n");
        {
            DataSource dss= org.autoplot.datasource.DataSetURI.getDataSource( "http://acdisc.gsfc.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMAEROe.003/2005/OMI-Aura_L3-OMAEROe_2005m0101_v003-2011m1109t081947.he5.dds?lat" );
            TimeSeriesBrowse tsb= org.autoplot.datasource.DataSourceUtil.getTimeSeriesBrowse(dss);
            System.err.println(tsb);
        }
        {
            DataSource dss= org.autoplot.datasource.DataSetURI.getDataSource( "http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hydra/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX&timerange=20000109" );
            TimeSeriesBrowse tsb= org.autoplot.datasource.DataSourceUtil.getTimeSeriesBrowse(dss);
            System.err.println(tsb);
        }
    }
    
    /**
     * test of large read, which in IDL was slow but did work.  The
     * problem is that floats are converted to doubles.
     * @throws Exception 
     */
    public static void test8() throws Exception {
        System.err.println( "\n= test8 =\n");
        String date="2012-11-02";

        String timeformat="seconds since "+date+"T00:00:00";

        String range= "2012-11-01 23:00 to 2012-11-02 01:00";
        
        long t0= System.currentTimeMillis();

        org.autoplot.idlsupport.APDataSet apds  = new org.autoplot.idlsupport.APDataSet();
        apds.setDataSetURI( TestSupport.TEST_HOME+"data.backup/cdf/rbsp-a_magnetometer_hires-sm_emfisis-L3_$Y$m$d_v$(v,sep).cdf?coordinates&timerange="+range );

        System.err.println( "t05: "+ (System.currentTimeMillis()-t0)/1000. + " seconds" );

        apds.doGetDataSet();
        apds.setPreferredUnits( timeformat );
        
        System.err.println( "t10: "+ (System.currentTimeMillis()-t0)/1000. + " seconds" );
        
        //x,y,z, positions in SM coordinates in km.  These should already be joined onto the 
        //same time base as the magnetic field data.
        Object vv= apds.values();
        System.err.println( "t20: "+ (System.currentTimeMillis()-t0)/1000. + " seconds" );

        System.err.println( String.valueOf( Array.get(Array.get(vv,0),0)) + " " + vv.getClass().getSimpleName() );
    }

    /**
     * test of CDF_TT2000 timetags, which need to be read in as longs.
     * This shows that by default cdfTT2000 longs will be used, and if 
     * "seconds since 2014-01-17" is used then doubles are returned.
     * @throws Exception 
     */
    public static void test9() throws Exception {
        System.err.println( "\n= test9 =\n");
        org.autoplot.idlsupport.APDataSet apds  = new org.autoplot.idlsupport.APDataSet();
        apds.setDataSetURI( TestSupport.TEST_HOME+"data.backup/cdf/rbsp-a_WFR-waveform-continuous-burst-magnitude_emfisis-L4_20140117T00_v1.3.2.cdf?Epoch" );
        //apds.setDataSetURI( "/home/jbf/tmp/rbsp-a_WFR-waveform-continuous-burst-magnitude_emfisis-L4_20140117T00_v1.3.2.cdf?Epoch" );
        
        apds.doGetDataSet();

        Object vv= apds.values();
        System.err.println( String.valueOf( Array.get(vv,0) ) + " " + vv.getClass().getSimpleName() );
      
        String timeformat="seconds since 2014-01-17T00:00:00";
        apds.setPreferredUnits( timeformat );
        
        vv= apds.values();
        System.err.println( String.valueOf( Array.get(vv,0) ) + " " + vv.getClass().getSimpleName() );
        
        timeformat="cdfTT2000";
        apds.setPreferredUnits( timeformat );
        
        vv= apds.values();
        System.err.println( String.valueOf( Array.get(vv,0) ) + " " + vv.getClass().getSimpleName() );
        
    }
    
    /**
     * model PaPCo's use of the interface, which also uses TSB.
     * @param uri
     * @param tr
     * @return
     * @throws Exception
     */
    private static String checkAPDS( String uri, String tr ) throws Exception {
        if ( tr!=null ) {
            // mimic code in papco that is failing
            DataSource dss= org.autoplot.datasource.DataSetURI.getDataSource( uri );
            TimeSeriesBrowse tsb= org.autoplot.datasource.DataSourceUtil.getTimeSeriesBrowse( dss );
            DatumRange dr= DatumRangeUtil.parseTimeRange( tr ) ;
            tsb.setTimeRange(dr);
            uri= tsb.getURI();
        }
        org.autoplot.idlsupport.APDataSet apds  = new org.autoplot.idlsupport.APDataSet();
        apds.setDataSetURI(uri);
        apds.doGetDataSet();
        if ( apds.getStatus()!=0 ) {
            return apds.getStatusMessage();
        }
        return apds.toString();
    }

    public static void test7() throws Exception {
        System.err.println( "\n= test7 =\n");        
        String uri= "vap+das2server:http://www-pw.physics.uiowa.edu/das/das2Server?dataset=cassini/mag/mag_vectorQ&start_time=2010-01-01T00:00:00.000Z&end_time=2010-01-02T00:00:00.000Z";
        System.err.println( checkAPDS( uri, null ) );

    }
    
    
    /**
     * test use with HAPI server
     * 
     * @throws java.lang.Exception
     */
    public static void test10() throws Exception {
        System.err.println( "\n= test10 =\n");        
        String uri= "vap+hapi:https://jfaden.net/HapiServerDemo/hapi?id=Iowa+City+Conditions&parameters=Time,Temperature,Humidity,Pressure&timerange=2017-May";
        org.autoplot.idlsupport.APDataSet apds  = new org.autoplot.idlsupport.APDataSet();
        apds.loadDataSet(uri);
        apds.setFillDouble(0);
        System.err.println( apds.toString() );
        System.err.println( Ops.mean( apds.values("Temperature") ) );
        System.err.println( Ops.mean( apds.values("Humidity") ) );
        System.err.println( Ops.mean( apds.values("Pressure") ) );
    }

    public static void testGuessNameFor() {
        System.err.println( "\n= testGuessNameFor =\n");
        System.err.println( DataSourceUtil.guessNameFor("vap+nc:file:///home/jbf/data.backup/examples/h5/19970101_Polar_23802_FluxAssimOut.v2.h5?Flux") );
        System.err.println( DataSourceUtil.guessNameFor("vap+nc:file:///home/jbf/data.backup/examples/h5/19970101_Polar_23802_FluxAssimOut.v2.h5?id=Flux" ) );
        System.err.println( DataSourceUtil.guessNameFor("vap+nc:file:///home/jbf/data.backup/examples/my.txt?column=Flux" ) );
        System.err.println( DataSourceUtil.guessNameFor("vap+cdf:file:///home/jbf/data.backup/examples/h5/19970101_Polar_23802_FluxAssimOut.v2.cdf?Flux[::2]" ) );
        System.err.println( DataSourceUtil.guessNameFor("vap+inline:sin(linspace(0,1000,2000))" ) );
        System.err.println( DataSourceUtil.guessNameFor("" ) );

        DataSourceUtil.guessNameFor("vap+nc:file:///home/jbf/data.backup/examples/h5/19970101_Polar_23802_FluxAssimOut.v2.h5?Flux");

    }
    
    /**
     * 
     * @throws Exception 
     */    
    public static void testFilters() throws Exception {
        System.err.println( "\n= testFilters =\n");
        org.autoplot.idlsupport.APDataSet apds  = new org.autoplot.idlsupport.APDataSet();
        apds.setDataSetURI("http://emfisis.physics.uiowa.edu/Flight/RBSP-B/Quick-Look/2015/04/04/rbsp-b_WFR-waveform-continuous-burst_emfisis-Quick-Look_20150404T16_v1.4.1.cdf?BuSpec");
        apds.setFilter("|histogram()");
        ProgressMonitor mon= new ConsoleTextProgressMonitor();
        apds.doGetDataSet(mon);
        while ( !mon.isFinished() ) {
            Thread.sleep(1000);
        }
        if ( apds.getStatus()==0 ) {
            System.err.println( apds.toString() );
        }
    }
    
    /**
     * There's a little problem with the mac when you run from the command line, 
     * because it will fail to bring up the credentials GUI when called from IDL
     * on the command line.
     * @throws InterruptedException 
     */
    public static void testAuth() throws InterruptedException {
        System.err.println( "\n= testAuth =\n");
        org.autoplot.idlsupport.APDataSet apds  = new org.autoplot.idlsupport.APDataSet();
        apds.loadDataSet("vap+das2Server:http://jupiter.physics.uiowa.edu/das/server?end_time=2015-05-22T00:00:00.000Z&start_time=2015-05-21T00:00:00.000Z&dataset=Juno/WAV/Survey");
        if ( apds.getStatus()==0 ) {
            System.err.println( apds.toString() );
        }
    }
    
    public static void main( String[] args )  {
        try {
            
            LoggerManager.readConfiguration();
            
            //testAuth();
            test10();
            
            testFilters();
            
            test9();
            
            test8();
            
            example1();
            example2();
            example3();  // Jared's slice
            example4();
            //example5(); //OpenDAP server/client code is very unreliable, and not used much.
            //example6();
            
            //test6();
            test7();
            checkAPDS( "vap+cdaweb:ds=PO_K0_MFE&id=MBTIGRF&filter=polar&timerange=2003-05-01", "2003-05-02" );
            checkAPDS( "vap+cdf:http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hydra/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX&timerange=2001-01-17", "2000-01-09" );

            testGuessNameFor();

            System.exit(0);  // TODO: something is firing up the event thread.  Note, we finally figured out that this is das2's request processor threads.

        } catch ( Exception ex ) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
