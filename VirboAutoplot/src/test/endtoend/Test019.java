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

    private static boolean testTimeRange( String test, String norm ) {
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

    public static void testTimeRangeFormatParse() {
        testTimeRange( "2001-11-03 23:00 to 2001-11-04 24:00", "2001-11-03 23:00 to 2001-11-05 00:00" );
    }

    public static void main( String[] args ) {
        testTimeRangeFormatParse();
    }
}
