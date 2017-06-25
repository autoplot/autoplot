
package org.autoplot.servlet;

import java.util.Arrays;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extract a clean Java code for parsing ISO8601 strings.  
 * @author jbf
 */
public class TimeRangeParser {

    /**
     * get an integer, allowing a letter at the end.
     * @param val
     * @param deft
     * @return 
     */
    private static int getInt( String val, int deft ) {
        if ( val==null ) {
            if ( deft!=-99 ) return deft; else throw new IllegalArgumentException("bad digit");
        }
        int n= val.length()-1;
        if ( Character.isLetter( val.charAt(n) ) ) {
            return Integer.parseInt(val.substring(0,n));
        } else {
            return Integer.parseInt(val);
        }
    }

    /**
     * get the double, allowing a letter at the end.
     * @param val
     * @param deft
     * @return 
     */
    private static double getDouble( String val, double deft ) {
        if ( val==null ) {
            if ( deft!=-99 ) return deft; else throw new IllegalArgumentException("bad digit");
        }
        int n= val.length()-1;
        if ( Character.isLetter( val.charAt(n) ) ) {
            return Double.parseDouble(val.substring(0,n));
        } else {
            return Double.parseDouble(val);
        }
    }
    
    private static final String simpleFloat= "\\d?\\.?\\d+";
    public static final String iso8601duration= "P(\\d+Y)?(\\d+M)?(\\d+D)?(T(\\d+H)?(\\d+M)?("+simpleFloat+"S)?)?";
    public static final Pattern iso8601DurationPattern= Pattern.compile(iso8601duration);

    /**
     * returns a 7 element array with [year,mon,day,hour,min,sec,nanos] or [-9999].
     * @param stringIn
     * @return [year,mon,day,hour,min,sec,nanos]
     */
    public static int[] parseISO8601Duration( String stringIn ) {
        Matcher m= iso8601DurationPattern.matcher(stringIn);
        if ( m.matches() ) {
            double dsec=getDouble( m.group(7),0 );
            int sec= (int)dsec;
            int nanosec= (int)( ( dsec - sec ) * 1e9 );
            return new int[] { getInt( m.group(1), 0 ), getInt( m.group(2), 0 ), getInt( m.group(3), 0 ), getInt( m.group(5), 0 ), getInt( m.group(6), 0 ), sec, nanosec };
        } else {
            throw new IllegalArgumentException("unable to parse: "+stringIn);
        }
    }
    
    
    /**
     * ISO8601 datum parser.  This does not support 2-digit years, which
     * were removed in ISO 8601:2004.
     * 
     * @param str
     * @param context
     * @return 
     */
    public static int parseISO8601Datum( String str, int[] result, int lsd ) {
        StringTokenizer st= new StringTokenizer( str, "-T:.Z", true );
        Object dir= null;
        final Object DIR_FORWARD = "f";
        final Object DIR_REVERSE = "r";
        int want= 0;
        boolean haveDelim= false;
        while ( st.hasMoreTokens() ) {
            char delim= ' ';
            if ( haveDelim ) {
                delim= st.nextToken().charAt(0);
                if ( st.hasMoreElements()==false ) { // "Z"
                    break;
                }
            } else {
                haveDelim= true;
            }
            String tok= st.nextToken();
            if ( dir==null ) {
                if ( tok.length()==4 ) { // typical route
                    int iyear= Integer.parseInt( tok ); 
                    result[0]= iyear;
                    want= 1;
                    dir=DIR_FORWARD;
                } else if ( tok.length()==6 ) {
                    want= lsd;
                    if ( want!=6 ) throw new IllegalArgumentException("lsd must be 6");
                    result[want]= Integer.parseInt( tok.substring(0,2) );
                    want--;
                    result[want]= Integer.parseInt( tok.substring(2,4) );
                    want--;
                    result[want]= Integer.parseInt( tok.substring(4,6) );
                    want--;
                    dir=DIR_REVERSE; 
                } else if ( tok.length()==7 ) {
                    result[0]= Integer.parseInt( tok.substring(0,4) );
                    result[1]= 1;
                    result[2]= Integer.parseInt( tok.substring(4,7) );
                    want= 3;                    
                    dir=DIR_FORWARD; 
                } else if ( tok.length()==8 ) {
                    result[0]= Integer.parseInt( tok.substring(0,4) );
                    result[1]= Integer.parseInt( tok.substring(4,6) );
                    result[2]= Integer.parseInt( tok.substring(6,8) );
                    want= 3;                    
                    dir=DIR_FORWARD;
                } else {
                    dir= DIR_REVERSE;
                    want= lsd;  // we are going to have to reverse these when we're done.
                    int i= Integer.parseInt( tok );
                    result[want]= i;
                    want--;
                }
            } else if ( dir==DIR_FORWARD) {
                if ( want==1 && tok.length()==3 ) { // $j
                    result[1]= 1;
                    result[2]= Integer.parseInt( tok ); 
                    want= 3;
                } else if ( want==3 && tok.length()==6 ) {
                    result[want]= Integer.parseInt( tok.substring(0,2) );
                    want++;
                    result[want]= Integer.parseInt( tok.substring(2,4) );
                    want++;
                    result[want]= Integer.parseInt( tok.substring(4,6) );
                    want++;
                } else if ( want==3 && tok.length()==4 ) {
                    result[want]= Integer.parseInt( tok.substring(0,2) );
                    want++;
                    result[want]= Integer.parseInt( tok.substring(2,4) );
                    want++;
                } else {
                    int i= Integer.parseInt( tok );
                    if ( delim=='.' && want==6 ) {
                        int n= 9-tok.length();
                        result[want]= i * ((int)Math.pow(10,n));
                    } else {
                        result[want]= i;
                    }
                    want++;
                }
            } else if ( dir==DIR_REVERSE ) { // what about 1200 in reverse?
                int i= Integer.parseInt( tok ); 
                if ( delim=='.' ) {
                    int n= 9-tok.length();
                    result[want]= i * ((int)Math.pow(10,n));
                } else {
                    result[want]= i;
                }
                want--;
            }
        }
        
        if ( dir==DIR_REVERSE ) {
            int iu= want+1;
            int id= lsd;
            while( iu<id ) {
                int t= result[iu];
                result[iu]= result[id];
                result[id]= t;
                iu= iu+1;
                id= id-1;
            }
        } else {
            lsd= want-1;
        }
        
        return lsd;
    }
    
    /**
     * returns the time found in an iso8601 string, or null.  This supports
     * periods (durations) as in: 2007-03-01T13:00:00Z/P1Y2M10DT2H30M
     * Other examples:
     *   2007-03-01T13:00:00Z/2008-05-11T15:30:00Z
     *   2007-03-01T13:00:00Z/P1Y2M10DT2H30M
     *   P1Y2M10DT2H30M/2008-05-11T15:30:00Z
     *   2007-03-01T00:00Z/P1D
     *   2012-100T02:00/03:45
     * http://en.wikipedia.org/wiki/ISO_8601#Time_intervals
     * @param stringIn
     * @param result, if non-null should be an int[14] to provide storage to routine.
     * @return int[14] with [Y,M,D,H,M,S,NS,Y,M,D,H,M,S,NS]
     */
    public static int[] parseISO8601Range( String stringIn, int[] result ) {

        String[] parts= stringIn.split("/",-2);
        if ( parts.length!=2 ) return null;

        boolean d1= parts[0].charAt(0)=='P'; // true if it is a duration
        boolean d2= parts[1].charAt(0)=='P';

        int[] digits0;
        int[] digits1;
        int lsd= -1;

        if ( d1 ) {
            digits0= parseISO8601Duration( parts[0] );
        } else {
            digits0= new int[7];
            lsd= parseISO8601Datum( parts[0], digits0, lsd );
            for ( int j=lsd+1; j<3; j++ ) digits0[j]=1; // month 1 is first month, not 0. day 1 
        }

        if ( d2 ) {
            digits1= parseISO8601Duration(parts[1]);
        } else {
            if ( d1 ) {
                digits1= new int[7];
            } else {
                digits1= Arrays.copyOf( digits0, digits0.length );
            }
            lsd= parseISO8601Datum( parts[1], digits1, lsd );
            for ( int j=lsd+1; j<3; j++ ) digits1[j]=1; // month 1 is first month, not 0. day 1 
        }

        if ( digits0==null || digits1==null ) return null;
        
        if ( d1 ) {
            for ( int i=0; i<7; i++ ) digits0[i] = digits1[i] - digits0[i];
        }

        if ( d2 ) {
            for ( int i=0; i<7; i++ ) digits1[i] = digits0[i] + digits1[i];
        }

        if ( result==null ) {
            result= new int[14];
        }
        System.arraycopy( digits0, 0, result, 0, 7 );
        System.arraycopy( digits1, 0, result, 7, 7 );

        return result;

    }
    
    public static void main( String[] args ) {
        int[] r= new int[14];
        
        parseISO8601Range( "2014-01-12T03:07:09.200/2015-02-12T03:04",r);
        for ( int i=0; i<14; i++ ) System.err.printf(" %4d",r[i]);
        System.err.println();
        
        parseISO8601Range( "2014-01-12T03:07/P1D",r);
        for ( int i=0; i<14; i++ ) System.err.printf(" %4d",r[i]);
        System.err.println();
        
        parseISO8601Range( "2014-01-12T03:07/P1DT12H",r);
        for ( int i=0; i<14; i++ ) System.err.printf(" %4d",r[i]);
        System.err.println();
        
        parseISO8601Range( "P1D/2014-01-12T03:07",r);
        for ( int i=0; i<14; i++ ) System.err.printf(" %4d",r[i]);
        System.err.println();        
    }
}
