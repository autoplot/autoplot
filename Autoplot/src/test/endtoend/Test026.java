/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import org.das2.datum.Datum;
import org.das2.datum.Units;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.DatumUtil;
import org.das2.datum.DatumRange;
import org.das2.datum.UnitsConverter;
import static org.das2.datum.DatumRangeUtil.*;
import org.das2.datum.TimeUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedWriter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * Tests of time parsing. A little testing appears in Test019, but this provides
 * an comprehensive list that will be a good reference.
 *
 * @author jbf
 */
public class Test026 {

    private static final String filePath = "Examples.html";
    private static BufferedWriter bw;

    private static boolean testTimeRange(String norm, String test) {
        DatumRange dr1 = DatumRangeUtil.parseTimeRangeValid(test);
        DatumRange dr2 = DatumRangeUtil.parseTimeRangeValid(norm);
        if (!dr1.equals(dr2)) {
            throw new IllegalStateException("fail after parsing test->" + dr1.toString() + " width=" + DatumUtil.asOrderOneUnits(dr1.width()));
        } else {
            String format = DatumRangeUtil.formatTimeRange(dr1);
            dr1 = DatumRangeUtil.parseTimeRangeValid(format);
            if (!dr1.equals(dr2)) {
                throw new IllegalStateException("fail after format:  format=" + format + " width=" + DatumUtil.asOrderOneUnits(dr1.width()));
            }
        }
        return true;
    }

    /**
     * test against a list of time ranges. This may serve as a guide for how to
     * format time strings. Each test is a string to parse against an
     * easy-to-parse explicit range.
     *
     * commented entries are strings that fail in the parser but should handled.
     */
    public static void testTimeRangeFormatParse() {
        // testTimeRange( easy-to-parse norm, test string ).
        testTimeRange("2001-11-03 23:00 to 2001-11-05 00:00", "2001-11-03 23:00 to 2001-11-04 24:00");
        testTimeRange("2001-01-01 00:00 to 2002-01-01 00:00", "2001");
        testTimeRange("2001-01-01 00:00 to 2004-01-01 00:00", "2001-2004"); // das2 "-" is exclusive
        testTimeRange("2001-01-01 00:00 to 2004-01-01 00:00", "2001 to 2004"); // das2 to is exclusive
        testTimeRange("2001-01-01 00:00 to 2004-01-01 00:00", "2001 through 2003"); // das2 through is inclusive
        testTimeRange("2001-06-01 00:00 to 2001-07-01 00:00", "2001 Jun");
        testTimeRange("2001-06-01 00:00 to 2001-08-01 00:00", "2001 Jun through July");
        testTimeRange("2001-06-01 00:00 to 2001-07-01 00:00", "2001 Jun to July");
        testTimeRange("2001-06-08 00:00 to 2001-06-09 00:00", "2001 Jun 8");
        testTimeRange("2001-06-01 00:00 to 2001-07-01 00:00", "2001 Jun to July");
        testTimeRange("2001-06-08 00:00 to 2001-06-09 00:00", "2001 Jun 8 00:00 to 24:00");
        testTimeRange("2001-01-01 00:00 to 2001-01-06 00:00", "2001 Jan 01 span 5 day");
        testTimeRange("2001-01-01 05:00 to 2001-01-01 07:00", "2001 Jan 01 05:00 span 2 hr");
        testTimeRange("2016-10-01 00:00 to 2016-11-01 00:00", "2016 October"); //  bug https://sourceforge.net/p/autoplot/bugs/1774/
        testTimeRange("2010-09-01 00:00 to 2010-09-02 00:00", "2010-244");  // day of year is three digits (001-366)
        testTimeRange("2010-03-01 00:00 to 2010-03-02 00:00", "2010-060");
        //  testTimeRange( "2001-07-01 00:00 to 2002-01-01 00:00", "July+through+Dec+2001" );  //TODO: should plus be a delimiter?  minus is, so I don't think this would hurt anything...  Autoplot often converts plus to space to make them continuous.

        //testTimeRange( "2012-04-07 0:00 to 2012-04-18", "2012-04-07 to 2012-04-18"); // this fails
        //testTimeRange( "2000 01 span 5 d", "2000-jan-01 to 2000-jan-06" );
    }

    public static void doTest(int id, String test, String ref) throws Exception {
        doTest(id, test, ref, 0., false);
    }

    private static LinkedHashMap<Integer,String> usedIds= new LinkedHashMap<>();
    
    /**
     * 
     * @param id the test identifier
     * @param test the string to parse
     * @param ref a string which will reliably parse, containing the same value.
     * @param diffMicros allowable difference.
     * @throws Exception 
     */
    private static void doTest(int id, String test, String ref, double diffMicros, boolean secondChance) throws Exception {

        String previousUsedId= usedIds.get(id);
        if ( previousUsedId!=null && !previousUsedId.equals(test) ) {
            throw new IllegalArgumentException("id "+id+" used twice, test code needs attention");
        }
        usedIds.put(id,test);
        
        DatumRange dr = parseTimeRange(test);
        DatumRange drref = parseTimeRange(ref);
        if (drref.equals(dr)) {
            System.err.println(id + ": " + test + "\t" + drref.min() + "\t" + DatumUtil.asOrderOneUnits(drref.width()));
        } else {
            Datum d1 = dr.min().subtract(drref.min()).abs();
            Datum d2 = dr.max().subtract(drref.max()).abs();
            if (d1.lt(Units.microseconds.createDatum(diffMicros))
                    && d2.lt(Units.microseconds.createDatum(diffMicros))) {
                System.err.println(id + ": " + test + "\t" + drref + "\t within " + diffMicros + " micros (" + d1 + " " + d2 + ")");
            } else {
                System.err.println(id + ": " + test + " != " + ref + "\n    " + dr + " != " + drref + "\n    not within " + diffMicros + " micros (" + d1 + " " + d2 + ")"); 
                //dr= parseTimeRange(test); // for debugging
                //drref= parseTimeRange(ref);
                if ( secondChance ) {
                    System.err.println("try again...");
                    doTest( id, test, ref, diffMicros, false ); //NOW has the problem that occasionally they will fail.
                } else {
                    throw new IllegalArgumentException("no parse exception, but parsed incorrectly.");
                }
            }
        }
        
        writeToHTML(id, test, ref); // uncomment this for testing.
    }

    private static void doTestDR(int id, String test, DatumRange norm) throws Exception {
        DatumRange dr = DatumRangeUtil.parseDatumRange(test, norm.getUnits());
        if (!norm.equals(dr)) {
            throw new IllegalArgumentException("test \"" + test + "\" is not equal to " + norm);
        }
    }

    public static void createHTMLHead() throws IOException {
        File f = new File(filePath);
        
        String htmlOpen = "<html>";
        String headerString = "<head><title>Test 026</title></head>";
        String bodyString = "<body style=\"background-color: #6B6B6B; margin=0;\">";
        String headerOpen = "<div style=\"top: 0px; margin-right=0px; font-size:40px; background-color:black; color:white;height:100px;\">"
                + "TEST COMPARISON TABLE (Test026.java)" + "</div>";
        String tableOpen = "<table border=\"1\" style=\"width:100%; color:white;\">\n";

        bw = new BufferedWriter(new FileWriter(f));

        bw.write(htmlOpen); //opens html
        bw.write(headerString); //writes html header
        bw.write(bodyString); //opens body and gives style
        bw.write(headerOpen); //writes header of webpage
        bw.write(tableOpen);  //opens table for doTest method to write to
    }

    public static void closeHTML() throws IOException {
        String htmlClose = "</table></body></html>";

        bw.write(htmlClose); //closes html page
        bw.close(); //closes buffer
    }

    public static void writeToHTML(int id, String test, String ref) throws IOException {     
        String table = "<tr><td><strong>Test Number:</strong> " + id + "</td>"
                + "<td><strong>Test: </strong> " + test + "</td>"
                + "<td><strong>Ref: </strong> " + ref + "</td></tr>\n";

        bw.write(table);
    }

    public static void main(String[] args) {
        try {

            createHTMLHead();
            //doTests
            doTestDR(70, "0 to 35", DatumRange.newDatumRange(0, 35, Units.dimensionless));
            doTestDR(71, "0to35", DatumRange.newDatumRange(0, 35, Units.dimensionless));
            doTestDR(72, "0 to 35 apples", DatumRange.newDatumRange(0, 35, Units.lookupUnits("apples")));
            doTestDR(73, "0 to 35 sector", DatumRange.newDatumRange(0, 35, Units.lookupUnits("sector")));
            doTestDR(74, "0to35 sector", DatumRange.newDatumRange(0, 35, Units.lookupUnits("sector")));
            doTestDR(75, "-50to-35", DatumRange.newDatumRange(-50, -35, Units.dimensionless));
            doTestDR(76, "0 to 10 kHz", DatumRange.newDatumRange(0, 10000, Units.hertz));
            doTestDR(77, "0 to .01 MHz", DatumRange.newDatumRange(0, 10000, Units.hertz));
            Units cm = Units.lookupUnits("cm");
            cm.registerConverter(Units.meters, new UnitsConverter.ScaleOffset(1. / 100, 0));
            doTestDR(78, "0 to 10 cm", DatumRange.newDatumRange(0, .1, Units.meters));
            Units mm = Units.lookupUnits("mm");
            mm.registerConverter(Units.meters, new UnitsConverter.ScaleOffset(1. / 1000, 0));
            doTestDR(79, "0 to 100 mm", DatumRange.newDatumRange(0, 10, cm));
            doTestDR(80, "0 to 100 mm", DatumRange.newDatumRange(0, .1, Units.meters));

            // These tests show two equivalent strings
            //das2 times.  Note das2 likes to format things with through, not "to" as used in the tests.
            doTest(0, "2000-01-01T13:00Z to 2000-01-01T14:00", "2000-01-01T13:00Z to 2000-01-01T14:00");
            doTest(1, "2000-01-01 13:00 to 14:00", "2000-01-01T13:00Z to 2000-01-01T14:00");
            doTest(2, "2000-01-02", "2000-01-02T00:00Z to 2000-01-03T00:00");
            doTest(3, "2000-002", "2000-01-02T00:00Z to 2000-01-03T00:00");
            doTest(4, "2000-02", "2000-02-01T00:00Z to 2000-03-01T00:00");
            doTest(5, "2000-feb", "2000-02-01T00:00Z to 2000-03-01T00:00");
            doTest(6, "2000", "2000-01-01T00:00Z to 2001-01-01T00:00");
            doTest(7, "2000-01-01 to 2000-01-05", "2000-01-01T00:00Z to 2000-01-05T00:00");
            doTest(8, "2000-01-01 through 2000-01-05", "2000-01-01T00:00Z to 2000-01-06T00:00");
            doTest(9, "2001-01-01 span 10 days", "2001-01-01T00:00Z to 2001-01-11T00:00");

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
            doTest(20, "2000-01-01T13:00/PT1H", "2000-01-01 13:00 to 14:00");
            doTest(21, "20000101T13:00Z/14:00Z", "2000-01-01 13:00 to 14:00");
            //doTest(22, "20000101T1300Z/1400Z", "2000-01-01 13:00 to 14:00" );

            //Extreme times
            doTest(30, "1000", "1000-01-01T00:00Z to 1001-01-01T00:00");
            doTest(31, "9000", "9000-01-01T00:00Z to 9001-01-01T00:00");
            doTest(32, "2000-01-01T00:00:00 span .000001 sec", "2000-01-01T00:00:00.000000 to 2000-01-01T00:00:00.000001");
            doTest(33, "2000-01-01T00:00:00 span .000000001 sec", "2000-01-01T00:00:00.000000 to 2000-01-01T00:00:00.000000001");
            doTest(34, "2002-01-01T10:10:10 span .000000001 sec", "2002-01-01T10:10:10.000000 to 2002-01-01T10:10:10.000000001");

            //month boundaries crossing year boundary caused problems.
            doTest(35, "Aug 1969 through Sep 1970", "Aug 1 1969 to Oct 1 1970");
            doTest(36, "2004-12-03T20:19:59.990/PT.02S", "2004-12-03 20:19:59.990 to 20:20:00.010", 30, false);
            doTest(37, "2004-12-03T20:19:56.2/PT.2S", "2004-12-03 20:19:56.200 to 20:19:56.400");

            testTimeRangeFormatParse(); // these are tests that used to be in test009.

            Datum now = TimeUtil.now();
            System.err.println("now= " + now);
            int micros = 60000000;
            doTest(40, "P1D", new DatumRange(now.subtract(1, Units.days), now).toString(), micros, true);
            doTest(41, "PT1H", new DatumRange(now.subtract(1, Units.hours), now).toString(), micros, true);
            doTest(42, "orbit:rbspa-pp:403", "2013-01-27T18:58:17.392Z to 2013-01-28T03:57:01.358Z", micros, false);
            doTest(43, "orbit:rbspa-pp:403-406", "2013-01-27T18:58:17.392Z to 2013-01-29T06:53:13.619Z", micros, false);
            doTest(44, "1972/now-P1D", "1972-01-01T00:00/" + now.subtract(1, Units.days), micros, true);
            doTest(45, "now-P10D/now-P1D", new DatumRange(now.subtract(10, Units.days), now.subtract(1, Units.days)).toString(), micros, true);
            
            // time zone support and pluses for spaces
            doTest(50, "2001-01-01T06:08-0600/P1D", "2001-01-01 12:08 to 2001-01-02 12:08" );
            doTest(51, "2001-01-01T06:08+to+10:08", "2001-01-01 06:08 to 2001-01-01 10:08" );
            doTest(52, "20010101T0608-0600/P1D", "2001-01-01 12:08 to 2001-01-02 12:08" );
            doTest(53, "20010101T0608+0600/P1D", "2001-01-01 00:08 to 2001-01-02 00:08" );  // Note the plus here is interpretted as a space in some codes, so make sure this case is handled.
            
            int[] tt= TimeUtil.fromDatum(now);
            tt[2]=1;
            tt[3]=0;
            tt[4]=0;
            tt[5]=0;
            tt[6]=0;
            Datum t2= TimeUtil.toDatum(tt);
            tt[1]--;
            if ( tt[1]==0 ) {
                tt[0]--;
                tt[1]=12;
            }
            Datum t1= TimeUtil.toDatum(tt);
                    
            doTest(46, "P1M/lastmonth", t1.toString()+"/"+t2.toString(), micros, false); // these things

            closeHTML(); //closes body and html
            System.exit(0);  // TODO: something is firing up the event thread
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
