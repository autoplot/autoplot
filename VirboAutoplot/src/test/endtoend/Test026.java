/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import org.das2.datum.DatumUtil;
import org.das2.datum.DatumRange;
import static org.das2.datum.DatumRangeUtil.*;

/**
 * Tests of time parsing.  A little testing appears in Test009, but this
 * provides an comprehensive list that will be a good reference.
 * @author jbf
 */
public class Test026 {

    public static void doTest(int id, String test, String ref) throws Exception {

        DatumRange dr= parseTimeRange(test);
        DatumRange drref= parseTimeRange(ref);
        if ( drref.equals(dr) ) {
            System.err.println( id + ": "+ test + "\t" + drref.min() + "\t" + DatumUtil.asOrderOneUnits( drref.width() ) );
        } else {
            System.err.println( id + ": "+ test + " != " + ref + "     " + dr + " != " + drref );
            //dr= parseTimeRange(test); // for debugging
            //drref= parseTimeRange(ref);
            throw new IllegalArgumentException("no parse exception, but parsed incorrectly.");
        }
    }

    public static void main(String[] args) {
        try {

            //das2 times.  Note das2 likes to format things with through, not "to" as used in the tests.
            doTest(0, "2000-01-01T13:00Z to 2000-01-01T14:00",  "2000-01-01T13:00Z to 2000-01-01T14:00" );
            doTest(1, "2000-01-01 13:00 to 14:00",  "2000-01-01T13:00Z to 2000-01-01T14:00" );
            doTest(2, "2000-01-02",  "2000-01-02T00:00Z to 2000-01-03T00:00" );
            doTest(3, "2000-002",  "2000-01-02T00:00Z to 2000-01-03T00:00" );
            doTest(4, "2000-02",  "2000-02-01T00:00Z to 2000-03-01T00:00" );
            doTest(5, "2000-feb",  "2000-02-01T00:00Z to 2000-03-01T00:00" );
            doTest(6, "2000",  "2000-01-01T00:00Z to 2001-01-01T00:00" );
            doTest(7, "2000-01-01 to 2000-01-05", "2000-01-01T00:00Z to 2000-01-05T00:00" );
            doTest(8, "2000-01-01 through 2000-01-05", "2000-01-01T00:00Z to 2000-01-06T00:00" );
            doTest(9, "2001-01-01 span 10 days", "2001-01-01T00:00Z to 2001-01-11T00:00" );

            //ISO8601 times
            doTest(10, "2000-01-01T13:00Z/PT1H", "2000-01-01T13:00Z/2000-01-01T14:00");
            doTest(11, "20000101T1300Z/PT1H", "2000-01-01T13:00Z/2000-01-01T14:00");
            doTest(12, "2000-01-01T00:00Z/P1D", "2000-01-01T00:00Z/2000-01-01T24:00");
            doTest(13, "2007-03-01T13:00:00Z/P1Y2M10DT2H30M", "2007-03-01T13:00:00Z/2008-05-11T15:30:00Z");
            doTest(14, "2007-03-01T13:00:00Z/2008-05-11T15:30:00Z", "2007-03-01T13:00:00Z/2008-05-11T15:30:00Z");
            doTest(15, "P1Y2M10DT2H30M/2008-05-11T15:30:00Z", "2007-03-01T13:00:00Z/2008-05-11T15:30:00Z");
            doTest(16, "2007-009/2007-021", "2007-01-09T00:00:00Z/2007-01-21T00:00:00Z");
            doTest(17, "2007-05-15/2007-05-30", "2007-05-15T00:00:00Z/2007-05-30T00:00:00Z");
            doTest(18, "2007-03-01/P5D", "2007-03-01T00:00:00Z/2007-03-06T00:00:00Z");
            doTest(19, "P5D/2007-03-06", "2007-03-01T00:00:00Z/2007-03-06T00:00:00Z");
            doTest(20, "2000-01-01T13:00/PT1H", "2000-01-01 13:00 to 14:00" );

            //Extreme times
            doTest(30, "1000",  "1000-01-01T00:00Z to 1001-01-01T00:00" );
            doTest(31, "9000",  "9000-01-01T00:00Z to 9001-01-01T00:00" );
            doTest(32, "2000-01-01T00:00:00 span .000001 sec",  "2000-01-01T00:00:00.000000 to 2000-01-01T00:00:00.000001" );
            doTest(33, "2000-01-01T00:00:00 span .000000001 sec",  "2000-01-01T00:00:00.000000 to 2000-01-01T00:00:00.000000001" );
            doTest(34, "2002-01-01T10:10:10 span .000000001 sec",  "2002-01-01T10:10:10.000000 to 2002-01-01T10:10:10.000000001" );

            //month boundaries crossing year boundary caused problems.
            doTest(35, "Aug 1969 through Sep 1970", "Aug 1 1969 to Oct 1 1970" );

            System.exit(0);  // TODO: something is firing up the event thread
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
