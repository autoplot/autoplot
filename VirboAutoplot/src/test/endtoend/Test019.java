/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import java.io.File;
import java.net.URI;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.DatumUtil;
import org.das2.datum.TimeParser;
import org.das2.datum.Units;
import org.das2.fsm.FileStorageModelNew;
import org.das2.graph.DasDevicePosition;
import org.das2.util.filesystem.FileSystem;

/**
 * tests of das2 internals.  These are tests of non-graphic parts (see test009 for this), and does not include
 * tests of interpretation of time strings (test026).
 * @author jbf
 */
public class Test019 {

    public static void testRestrictedFileSystemAccess() throws Exception {
        
        String uri;
        FileStorageModelNew fsm;
        File[] ff;

        uri= "http://autoplot.org/data/pngwalk/";
        fsm= FileStorageModelNew.create(FileSystem.create( new URI( uri ) ),
               "product_$Y$m$d.png" );
        ff= fsm.getBestFilesFor( DatumRangeUtil.parseTimeRange( "2008-003" ) );
        if ( ff.length==1 ) System.err.println(ff[0]); else throw new IllegalStateException("no files found");



        uri= "http://demo:demo@www-pw.physics.uiowa.edu/~jbf/data/restrict/";
        fsm= FileStorageModelNew.create(FileSystem.create( new URI( uri ) ),
               "data_$Y_$m_$d_v$v.qds" );
        ff= fsm.getBestFilesFor( DatumRangeUtil.parseTimeRange( "2010-03-02" ) );
        if ( ff.length==1 ) System.err.println(ff[0]); else throw new IllegalStateException("no files found");

        
        uri= "http://demo@host:demo@www-pw.physics.uiowa.edu/~jbf/data/restrictAt/";
        fsm= FileStorageModelNew.create(FileSystem.create( new URI( uri ) ),
               "data_$Y_$m_$d_v$v.qds" );
        ff= fsm.getBestFilesFor( DatumRangeUtil.parseTimeRange( "2010-03-02" ) );
        if ( ff.length==1 ) System.err.println(ff[0]); else throw new IllegalStateException("no files found");

        //Leave these commented out, since we don't want to bother their server.
//        uri= "http://sy%40space.physics.uiowa.edu:password@mapsview.engin.umich.edu/data/MAG/KSM/";
//        fsm= FileStorageModelNew.create(FileSystem.create( new URI( uri ) ),
//               "MAG__KSM__$Y$j_$v.TAB" );
//        ff= fsm.getBestFilesFor( DatumRangeUtil.parseTimeRange( "2003-196" ) );
//        if ( ff.length==1 ) System.err.println(ff[0]);
//
//
//        uri= "http://sy@space.physics.uiowa.edu:password@mapsview.engin.umich.edu/data/MAG/KSM/";
//        fsm= FileStorageModelNew.create(FileSystem.create( new URI( uri ) ),
//               "MAG__KSM__$Y$j_$v.TAB" );
//        ff= fsm.getBestFilesFor( DatumRangeUtil.parseTimeRange( "2003-196" ) );
//        if ( ff.length==1 ) {
//            System.err.println(ff[0]);
//        }
    }

    public static void testLayout( ) throws ParseException {
        double [] res0, res1, res2;
        res0= DasDevicePosition.parseFormatStr("100 % -5 em +4 px");
        res0= DasDevicePosition.parseFormatStr("100% -5em +4px");
        res1= DasDevicePosition.parseFormatStr("100%-5em+4pt");
        res2= DasDevicePosition.parseFormatStr("+4pt-5em+100%");
        for ( int i=0; i<3; i++ ) {
            if ( res0[i]!=res1[i] ) throw new IllegalArgumentException("layout parsing res0!=res1");
            if ( res0[i]!=res2[i] ) throw new IllegalArgumentException("layout parsing res0!=res2");
        }
        res0= DasDevicePosition.parseFormatStr("100%");
        res0= DasDevicePosition.parseFormatStr("0%");
        res0= DasDevicePosition.parseFormatStr(""); // should be same as "0%"
    }

    public static void testFileSystemModel() throws Exception {
        FileStorageModelNew fsm= FileStorageModelNew.create( FileSystem.create( "file:///home/jbf/ct/hudson/data/dat/span/omni2/" ),
                "omni2_h0_mrg1hr_$Y$(m,span=6)$d_v01.cdf" );
        System.err.println( fsm );

        File[] files;
        String tr;

        tr="1984";
        System.err.println( tr );
        files= fsm.getFilesFor( DatumRangeUtil.parseTimeRangeValid( tr ) );
        for ( File f: files ) System.err.println(" -> "+f );

        tr= "1984-03";
        System.err.println( tr );
        files= fsm.getFilesFor( DatumRangeUtil.parseTimeRangeValid( tr ) ); // Reiner obversed that this does not identify file.
        for ( File f: files ) System.err.println(" -> "+f );

    }

    static boolean testTimeParser1( String spec, String test, String norm ) throws Exception {
        TimeParser tp= TimeParser.create(spec);
        DatumRange dr= tp.parse(test).getTimeRange();
        DatumRange drnorm= org.das2.datum.DatumRangeUtil.parseTimeRangeValid(norm);

        if ( !dr.equals(drnorm) ) {
            throw new IllegalStateException("ranges do not match: "+spec + " " +test + "--> " + dr + ", should be "+norm );
        }
        return true;
    }

    /**
     * test time parsing when the format is known.  This time parser is much faster than the time parser of Test009, which must
     * infer the format as it parses.
     * @throws Exception
     */
    public static void testTimeParser() throws Exception {
        testTimeParser1( "$-1Y $-1m $-1d $H$M", "2012 03 30 1620", "2012-03-30T16:20 to 2012-03-30T16:21" );
        testTimeParser1( "$Y",            "2012",     "2012-01-01T00:00 to 2013-01-01T00:00");
        testTimeParser1( "$Y-$j",         "2012-017", "2012-01-17T00:00 to 2012-01-18T00:00");
        testTimeParser1( "$(j,Y=2012)",   "017",      "2012-01-17T00:00 to 2012-01-18T00:00");

        // speed tests
        long t0;
        String test;
        TimeParser tp;
        int nt= 500000; // number of invocations

        test= "2012-017 00:00:00";
        t0= System.currentTimeMillis();
        tp= TimeParser.create("$Y-$j $H:$M:$S");
        for ( int i=0;i<nt; i++ ) {
            tp.parse(test).getTimeRange();
        }
        System.err.printf( "%d parses of %s: %d(ms)\n", nt, test, System.currentTimeMillis()-t0 );

        test= "2012-01-17 00:00:00.000";
        t0= System.currentTimeMillis();
        tp= TimeParser.create("$Y-$m-$d $H:$M:$S.$(milli)");
        for ( int i=0;i<nt; i++ ) {
            tp.parse(test).getTimeRange();
        }
        System.err.printf( "%d parses of %s: %d ms\n", nt, test, System.currentTimeMillis()-t0 );

        test= "omni2_h0_mrg1hr_19840701_v01.cdf";
        t0= System.currentTimeMillis();
        tp= TimeParser.create("omni2_h0_mrg1hr_$Y$(m,span=6)$d_v01.cdf");
        for ( int i=0;i<nt; i++ ) {
            tp.parse(test).getTimeRange();
        }
        System.err.printf( "%d parses of %s: %d ms\n", nt, test, System.currentTimeMillis()-t0 );
    }
    
    public static void main( String[] args ) {
        try {
            testRestrictedFileSystemAccess();
            testLayout();
            testFileSystemModel();
            testTimeParser();
        } catch (Exception ex) {
            Logger.getLogger( Test019.class.getName()).log( Level.SEVERE, "error in test019", ex );
            ex.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }
}
