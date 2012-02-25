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
import org.das2.datum.Units;
import org.das2.fsm.FileStorageModelNew;
import org.das2.graph.DasDevicePosition;
import org.das2.util.filesystem.FileSystem;

/**
 * tests of das2 internals
 * @author jbf
 */
public class Test019 {

    private static boolean testTimeRange( String norm, String test ) {
        DatumRange dr1= DatumRangeUtil.parseTimeRangeValid(test);
        DatumRange dr2= DatumRangeUtil.parseTimeRangeValid(norm);
        if ( !dr1.equals(dr2) ) {
            throw new IllegalStateException("fail after parsing test->"+dr1.toString()+" width="+DatumUtil.asOrderOneUnits(dr1.width()) );
        } else {
            String format= DatumRangeUtil.formatTimeRange(dr1);
            dr1= DatumRangeUtil.parseTimeRangeValid(format);
            if ( !dr1.equals(dr2) ) {
               throw new IllegalStateException("fail after format:  format="+format+" width="+DatumUtil.asOrderOneUnits(dr1.width()) );
            }
        }
        return true;
    }

    /**
     * test against a list of time ranges.  This may serve as a guide for how to
     * format time strings.  Each test is a string to parse against an easy-to-parse
     * explicit range.
     *
     * commented entries are strings that fail in the parser but should handled.
     */
    public static void testTimeRangeFormatParse() {
        // testTimeRange( easy-to-parse norm, test string ).
        testTimeRange( "2001-11-03 23:00 to 2001-11-05 00:00", "2001-11-03 23:00 to 2001-11-04 24:00"  );
        testTimeRange( "2001-01-01 00:00 to 2002-01-01 00:00", "2001"  );
        testTimeRange( "2001-01-01 00:00 to 2004-01-01 00:00", "2001-2004"  ); // das2 "-" is exclusive
        testTimeRange( "2001-01-01 00:00 to 2004-01-01 00:00", "2001 to 2004"  ); // das2 to is exclusive
        testTimeRange( "2001-01-01 00:00 to 2004-01-01 00:00", "2001 through 2003"  ); // das2 through is inclusive
        testTimeRange( "2001-06-01 00:00 to 2001-07-01 00:00", "2001 Jun" );
        testTimeRange( "2001-06-01 00:00 to 2001-08-01 00:00", "2001 Jun through July" );
        testTimeRange( "2001-06-01 00:00 to 2001-07-01 00:00", "2001 Jun to July" );
        testTimeRange( "2001-06-08 00:00 to 2001-06-09 00:00", "2001 Jun 8" );
        testTimeRange( "2001-06-01 00:00 to 2001-07-01 00:00", "2001 Jun to July" );
        testTimeRange( "2001-06-08 00:00 to 2001-06-09 00:00", "2001 Jun 8 00:00 to 24:00" );
        testTimeRange( "2001-01-01 00:00 to 2001-01-06 00:00", "2001 Jan 01 span 5 day" );
        testTimeRange( "2001-01-01 05:00 to 2001-01-01 07:00", "2001 Jan 01 05:00 span 2 hr" );
        testTimeRange( "2010-09-01 00:00 to 2010-09-02 00:00", "2010-244" );  // day of year is three digits (001-366)
        testTimeRange( "2010-03-01 00:00 to 2010-03-02 00:00", "2010-060" );  
        //testTimeRange( "2000 01 span 5 d", "2000-jan-01 to 2000-jan-06" );

    }

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

    
    public static void main( String[] args ) {
        try {
            testTimeRangeFormatParse();
            testRestrictedFileSystemAccess();
            testLayout();
            testFileSystemModel();
        } catch (Exception ex) {
            Logger.getLogger( Test019.class.getName()).log( Level.SEVERE, "error in test019", ex );
            ex.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }
}
