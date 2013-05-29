/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import java.io.File;
import java.net.URI;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.print.attribute.HashAttributeSet;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import static org.das2.datum.DatumRangeUtil.parseISO8601Range;
import org.das2.datum.DatumUtil;
import org.das2.datum.TimeParser;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.fsm.FileStorageModelNew;
import org.das2.graph.DasDevicePosition;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import static test.datum.TestDatumRangeUtil.testParse8601_1;

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

    public static void testFSMVersioning() throws Exception {
        String uri= "http://sarahandjeremy.net/~jbf/autoplot/tests/test019_fsm/vers/";
        FileStorageModelNew fsm;
        String[] ss;
        fsm= FileStorageModelNew.create(FileSystem.create( new URI( uri ) ),
               "rbspa_pre_ect-mageis-L2_$Y$m$d_v$(v,sep).cdf" );
        System.err.println(fsm);
        ss= fsm.getBestNamesFor(null,new NullProgressMonitor());
        for ( String s: ss ) {
            System.err.println(s);
        }
        fsm= FileStorageModelNew.create(FileSystem.create( new URI( uri ) ),
               "rbspa_pre_ect-mageis-L2_$Y$m$d_v$(v,sep,ge=2).cdf" );
        System.err.println(fsm);
        ss= fsm.getBestNamesFor(null,new NullProgressMonitor());
        for ( String s: ss ) {
            System.err.println(s);
        }
        fsm= FileStorageModelNew.create(FileSystem.create( new URI( uri ) ),
               "rbspa_pre_ect-mageis-L2_$Y$m$d_v$(v,sep,lt=2).cdf" );
        System.err.println(fsm);
        ss= fsm.getBestNamesFor(null,new NullProgressMonitor());
        for ( String s: ss ) {
            System.err.println(s);
        }
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
        } else {
            System.err.println( String.format( "%s:  \t%s-->\t%s", spec, test, norm ) );
        }
        return true;
    }

    /**
     * test time parsing when the format is known.  This time parser is much faster than the time parser of Test009, which must
     * infer the format as it parses. 
     * @throws Exception
     */
    public static void testTimeParser() throws Exception {
        //LoggerManager.getLogger("datum.timeparser").setLevel(Level.ALL);
        testTimeParser1( "$Y$m$d-$(enum,values=a|b|c|d)", "20130202-a", "2013-02-02/2013-02-03" );
        testTimeParser1( "$Y$m$d-$(Y,end)$m$d", "20130202-20140303", "2013-02-02/2014-03-03" );
        testTimeParser1( "$Y$m$d-$(Y,end)$m$(d,shift=1)", "20130202-20140303", "2013-02-02/2014-03-04" );
        testTimeParser1( "$Y$m$d-$(d,end)", "20130202-13", "2013-02-02/2013-02-13" );
        testTimeParser1( "$(periodic;offset=0;start=2000-001;period=P1D)", "0",  "2000-001");
        testTimeParser1( "$(periodic;offset=0;start=2000-001;period=P1D)", "20", "2000-021");        
        testTimeParser1( "$(periodic;offset=2285;start=2000-346;period=P27D)", "1", "1832-02-08/P27D");
        testTimeParser1( "$(periodic;offset=2285;start=2000-346;period=P27D)", "2286", "2001-007/P27D");        
        testTimeParser1( "$(j,Y=2012)$(hrinterval,names=01|02|03|04)", "01702", "2012-01-17T06:00/12:00");
        testTimeParser1( "$(j,Y=2012).$H$M$S.$(subsec,places=3)", "017.020000.245", "2012-01-17T02:00:00.245/02:00:00.246");
        testTimeParser1( "$(j,Y=2012).$x.$X.$(ignore).$H", "017.x.y.z.02", "2012-01-17T02:00:00/03:00:00");
        testTimeParser1( "$(j,Y=2012).*.*.*.$H", "017.x.y.z.02", "2012-01-17T02:00:00/03:00:00");
        // The following shows a bug where it doesn't consider the length of $H and just stops on the next period.
        // A field cannot contain the following delimiter.
        //  testTimeParser1( "$(j,Y=2012).*.$H", "017.x.y.z.02", "2012-01-17T02:00:00/03:00:00");
        testTimeParser1( "$(o,id=rbspa-pp)", "31",  "2012-09-10T14:45:51.316Z/2012-09-10T23:44:37.174Z");
        //testTimeParser1( "$(j,Y=2012)$(hrinterval,names=01|02|03|04)", "01702", "2012-01-17T06:00/18:00");
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
    
    public static void testParse8601_1( String test, String ref ) throws ParseException {
        DatumRange drref= parseISO8601Range(ref);
        DatumRange dr= parseISO8601Range(test);
        if ( drref.equals(dr) ) {
            System.err.println( "OK: "+test );
        } else {
            throw new IllegalArgumentException( "test failed: "+ test + " != " + ref + ", " + dr + "!=" + drref );
        }
    }

    /**
     * Test026 also checks this.
     */
    public static void testParse8601() throws ParseException {
        testParse8601_1( "2012-01-17T02:00:00.245/02:00:00.246", "2012-01-17T02:00:00.245/2012-01-17T02:00:00.246" );
        testParse8601_1( "2007-12-14T13:30/15:30", "2007-12-14T13:30/2007-12-14T15:30" );
        testParse8601_1( "2007-11-13/15", "2007-11-13T00:00/2007-11-15T00:00" );
        testParse8601_1( "2000-01-01T13:00Z/PT1H", "2000-01-01T13:00Z/2000-01-01T14:00" );
        testParse8601_1( "20000101T1300Z/PT1H", "2000-01-01T13:00Z/2000-01-01T14:00" );
        testParse8601_1( "2000-01-01T00:00Z/P1D", "2000-01-01T00:00Z/2000-01-01T24:00" );
        testParse8601_1( "2007-03-01T13:00:00Z/P1Y2M10DT2H30M", "2007-03-01T13:00:00Z/2008-05-11T15:30:00Z" );
        testParse8601_1( "2007-03-01T13:00:00Z/2008-05-11T15:30:00Z", "2007-03-01T13:00:00Z/2008-05-11T15:30:00Z" );
        testParse8601_1( "P1Y2M10DT2H30M/2008-05-11T15:30:00Z", "2007-03-01T13:00:00Z/2008-05-11T15:30:00Z" );
        testParse8601_1( "2008-05-10/2008-05-11", "2008-05-10T00:00:00Z/2008-05-11T00:00:00Z" );
        testParse8601_1( "2008-009/2008-010", "2008-01-09T00:00:00Z/2008-01-10T00:00:00Z" );
    }
    
    public static void main( String[] args ) {
        try {
            TimeParser tp= TimeParser.create( TimeParser.TIMEFORMAT_Z );
            tp.format( TimeUtil.now(), null);
            
            testFSMVersioning();
            
            tp= TimeParser.create("$Y$m$d-$(enum,values=a|b|c|d,id=sc)");
            Map<String,String> extra= new HashMap();
            System.err.println( tp.parse( "20130524-b", extra ) );
            System.err.println( "sc="+extra.get("sc") );
            testParse8601(); // this test comes from a test within das2.
            testTimeParser();
            testRestrictedFileSystemAccess();
            testLayout();
            testFileSystemModel();
        } catch (Exception ex) {
            TestSupport.logger.log( Level.SEVERE, "error in test019", ex );
            ex.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }
}
