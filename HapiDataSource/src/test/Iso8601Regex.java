/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.util.LoggerManager;

/**
 *
 * @author jbf
 */
public class Iso8601Regex {
    public static void main( String[] regex ) {
        String r= "(?<DAY>(?:(?<YEARM>(?:16|17|18|19|20|21)\\d{2})-(?<MONTH>\\d{2})-(?<DOM>\\d{2}))|(?:(?<YEARY>(?:16|17|18|19|20|21)\\d{2})-(?<DOY>\\d{3})))T(?<TIMEOFDAY>(?<HOURS>[01]\\d|2[0-4])(:(?<MINUTES>[0-5]\\d):(?<SECONDS>[0-6]\\d)?)?(?<SUBSECONDS>.\\d{1,9})?)?(?:Z)?";
        Pattern p= Pattern.compile(r);
        String t= "2017-150T24:00:30.0Z";
        System.err.println( p.matcher(t).matches() );
        
        long t0= System.currentTimeMillis();
        for ( int i=0; i<1000000; i++ ) {
            p.matcher(t).matches();
        }
        System.err.println( System.currentTimeMillis()-t0 );
        
        Matcher m= p.matcher(t);
        if ( !m.matches() ) {
            System.err.println("Expression does not match");
        } else {
            if ( m.group("YEARM")!=null ) {
                System.err.println("Year: "+m.group("YEARM"));
                System.err.println("Month: "+m.group("MONTH"));
                System.err.println("Day: "+m.group("DOM"));
            } else {
                System.err.println("Year: "+m.group("YEARY"));
                System.err.println("Day Of Year: "+m.group("DOY"));
            }
            System.err.println("Hours: "+m.group("HOURS"));
            System.err.println("Minutes: "+m.group("MINUTES"));
            System.err.println("Seconds: "+m.group("SECONDS"));
            System.err.println("Subseconds: "+m.group("SUBSECONDS"));
        }
        
    }
}
