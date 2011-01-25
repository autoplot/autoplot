/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.DatumUtil;

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

    public static void main( String[] args ) {
        testTimeRangeFormatParse();
    }
}
