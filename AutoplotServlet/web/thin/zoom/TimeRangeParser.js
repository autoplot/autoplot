// **************************
// JavaScript code for parsing ISO8601 Strings.
// **************************

    
function isLetter(str) {
    return str.length === 1 && str.match(/[a-z]/i);
}

/**
 * get an integer, allowing a letter at the end.
 * @param val string value
 * @param deft int value to return if the string is not valid. -99 will throw exception
 * @return 
 */
function getInt( val, deft ) {
    if ( val===undefined ) {
        if ( deft!==-99 ) return deft; else alert("bad digit");
    }
    n= val.length-1;
    if ( isLetter( val.charAt(n) ) ) {
        return parseInt(val.substring(0,n));
    } else {
        return parseInt(val);
    }
}

/**
/**
 * get the double, allowing a letter at the end.
 * @param val string value
 * @param deft double value to return if the string is not valid. -99 will throw exception
 * @return 
 */
function getDouble( val, deft ) {
    if ( val===undefined ) {
        if ( deft!==-99 ) return deft; else alert("bad digit");
    }
    n= val.length-1;
    if ( isLetter( val.charAt(n) ) ) {
        return parseFloat(val.substring(0,n));
    } else {
        return parseFloat(val);
    }
}
    
    
var simpleFloat= "\\d?\\.?\\d+";
var iso8601duration= "P(\\d+Y)?(\\d+M)?(\\d+D)?(T(\\d+H)?(\\d+M)?("+simpleFloat+"S)?)?";

/**
 * returns a 7 element array with [year,mon,day,hour,min,sec,nanos] or [-9999].
 * @param stringIn
 * @return [year,mon,day,hour,min,sec,nanos]
 */
function parseISO8601Duration( stringIn ) {
    var iso8601DurationPattern= new RegExp(iso8601duration,'g');
    m= iso8601DurationPattern.exec(stringIn);
    if ( m!==null ) {
        dsec=getDouble( m[7],0 );
        sec= Math.floor(dsec);
        nanosec= Math.floor( ( dsec - sec ) * 1e9 );
        return [ getInt( m[1], 0 ), getInt( m[2], 0 ), getInt( m[3], 0 ), getInt( m[5], 0 ), getInt( m[6], 0 ), sec, nanosec ];
    } else {
        alert("unable to parse: "+stringIn);
    }
}
    
/**
 * find the next instance of delim in str, where delim is
 * one of the chars in delims
 */    
function nextToken( str, index, delims ) {
    index= index+1;
    if ( index>str.length ) return -1;
    while ( index<str.length ) {
        ch= str.charAt(index);
        if ( delims.indexOf(ch) > -1 ) {
            break;
        } else {
            index= index+1;
        }
    }
    return index;
}

/**
 * ISO8601 datum parser.  This does not support 2-digit years, which
 * were removed in ISO 8601:2004.
 * 
 * @param str the string we are parsing
 * @param result the int[7] result
 * @param lsd
 * @return the lsd
 */
function parseISO8601Datum( str, result, lsd ) {
    delims= "-T:.Z";
    dir= "";
    DIR_FORWARD = "f";
    DIR_REVERSE = "r";
    want= 0;
    haveDelim= false;
    index= -1;
    index1= nextToken( str, index, delims );
    while ( index1>-1 ) {
        if ( haveDelim ) {
            delim= str.charAt(index-1); // delim is the delimiter before tok.
            if ( index1===str.length-1 ) { // "Z"
                break;
            }
        } else {
            delim= '';
            haveDelim= true;
        }
        tok= str.substring(index,index1);
        if ( dir==="" ) {
            if ( tok.length===4 ) { // typical route
                iyear= parseInt(tok); 
                result[0]= iyear;
                want= 1;
                dir=DIR_FORWARD;
                result[1]= 0; result[2]= 0; result[3]=0; result[4]=0; result[5]=0; result[6]=0;
                
            } else if ( tok.length===6 ) {
                want= lsd;
                if ( want!==6 ) alert("lsd must be 6");
                result[want]= parseInt( tok.substring(0,2) );
                want--;
                result[want]= parseInt( tok.substring(2,4) );
                want--;
                result[want]= parseInt( tok.substring(4,6) );
                want--;
                dir=DIR_REVERSE; 
            } else if ( tok.length===7 ) {
                result[0]= parseInt( tok.substring(0,4) );
                result[1]= 1;
                result[2]= parseInt( tok.substring(4,7) );
                want= 3;                    
                dir=DIR_FORWARD; 
                result[3]=0; result[4]=0; result[5]=0; result[6]=0;
            } else if ( tok.length===8 ) {
                result[0]= Integer.parseInt( tok.substring(0,4) );
                result[1]= Integer.parseInt( tok.substring(4,6) );
                result[2]= Integer.parseInt( tok.substring(6,8) );
                want= 3;                    
                dir=DIR_FORWARD;
                result[3]=0; result[4]=0; result[5]=0; result[6]=0;
            } else {
                dir= DIR_REVERSE;
                want= lsd;  // we are going to have to reverse these when we're done.
                i= parseInt( tok );
                result[want]= i;
                want--;
            }
        } else if ( dir===DIR_FORWARD) {
            if ( want===1 && tok.length===3 ) { // $j
                result[1]= 1;
                result[2]= parseInt( tok ); 
                want= 3;
            } else if ( want===3 && tok.length===6 ) {
                result[want]= parseInt( tok.substring(0,2) );
                want++;
                result[want]= parseInt( tok.substring(2,4) );
                want++;
                result[want]= parseInt( tok.substring(4,6) );
                want++;
            } else if ( want===3 && tok.length===4 ) {
                result[want]= parseInt( tok.substring(0,2) );
                want++;
                result[want]= parseInt( tok.substring(2,4) );
                want++;
            } else {
                i= parseInt( tok );
                if ( delim==='.' && want===6 ) {
                    n= 9-tok.length;
                    result[want]= i * Math.pow(10,n);
                } else {
                    result[want]= i;
                }
                want++;
            }
        } else if ( dir===DIR_REVERSE ) { // what about 1200 in reverse?
            i= parseInt( tok ); 
            if ( delim==='.' ) {
                n= 9-tok.length;
                result[want]= i * Math.pow(10,n);
            } else {
                result[want]= i;
            }
            want--;
        }
        index= index1+1;
        index1= nextToken( str, index, delims );
    }

    if ( dir===DIR_REVERSE ) {
        iu= want+1;
        id= lsd;
        while( iu<id ) {
            t= result[iu];
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
    function parseISO8601Range( stringIn, result ) {

        parts= stringIn.split("/",2);
        if ( parts.length!==2 ) return null;

        d1= parts[0].charAt(0)==='P'; // true if it is a duration
        d2= parts[1].charAt(0)==='P';

        lsd= -1;

        if ( d1 ) {
            digits0= parseISO8601Duration( parts[0] );
        } else if ( parts[0]==='now' ) {
            dd= new Date();
            digits0= [ dd.getUTCFullYear(), dd.getUTCMonth()+1, dd.getUTCDate(), dd.getUTCHours(), dd.getUTCMinutes(), dd.getUTCSeconds(), dd.getUTCMilliseconds()*1000000 ]
        } else {
            digits0= [0,0,0,0,0,0,0];
            lsd= parseISO8601Datum( parts[0], digits0, lsd );
            for ( j=lsd+1; j<3; j++ ) digits0[j]=1; // month 1 is first month, not 0. day 1 
        }

        if ( d2 ) {
            digits1= parseISO8601Duration(parts[1]);
        } else if ( parts[1]==='now' ) {
            dd= new Date();
            digits1= [ dd.getUTCFullYear(), dd.getUTCMonth()+1, dd.getUTCDate(), dd.getUTCHours(), dd.getUTCMinutes(), dd.getUTCSeconds(), dd.getUTCMilliseconds()*1000000 ]
        } else {
            if ( d1 ) {
                digits1= [0,0,0,0,0,0,0];
            } else {
                digits1= digits0.slice(0); // make a clone of the array
            }
            lsd= parseISO8601Datum( parts[1], digits1, lsd );
            for ( j=lsd+1; j<3; j++ ) digits1[j]=1; // month 1 is first month, not 0. day 1 
        }

        if ( digits0===null || digits1===null ) return null;
        
        if ( d1 ) {
            for ( i=0; i<7; i++ ) digits0[i] = digits1[i] - digits0[i];
        }

        if ( d2 ) {
            for ( i=0; i<7; i++ ) digits1[i] = digits0[i] + digits1[i];
        }

        if ( result===undefined ) {
            result= [0,0,0,0,0,0,0,0,0,0,0,0,0,0];
        }
        for ( i=0; i<7; i++ ) result[i]= digits0[i];
        for ( i=0; i<7; i++ ) result[i+7]= digits1[i];

        return result;

    }
    
    /**
     * again I am shocked that javascript doesn't support sprintf style formatting.
     * @param int num zero or positive number
     * @param int size total number of digits, must be less than 10.
     * @returns formatted in with zeroes prefix.
     */
    function zeroPad( num, size ) {
        var s = "000000000" + num;
        return s.substr(s.length-size);
    }
    
    /**
     * format the seven digits starting at index.
     * @param int arr like [ 2014, 1, 5, 0, 0, 3, 300000000, 2014, 1, 5, 0, 0, 3, 600000000 ]
     * @param int index into array like 7
     * @returns ISO8601 formatted string like "2014-01-05T00:00:03.600" 
     */
    function formatISO8601( arr, index ) {
        var s1;
        if ( arr[index+1]===1 && arr[index+2]>31 ) {  // day-of-year
            s1= zeroPad( arr[index+0], 4 ) + "-" + zeroPad( arr[index+2], 3 ) + "T" + zeroPad( arr[index+3], 2 ) + ":" + zeroPad( arr[index+4], 2 );            
        } else {
            s1= zeroPad( arr[index+0], 4 ) + "-" + zeroPad( arr[index+1], 2 ) + "-" + zeroPad( arr[index+2], 2 ) + "T" + zeroPad( arr[index+3], 2 ) + ":" + zeroPad( arr[index+4], 2 );
        }
        if ( arr[index+5]>0 || arr[index+6]>0 ) { 
            s1= s1+":"+zeroPad( arr[index+5], 2 );
        }
        if ( arr[index+6]>0 ) {
            s1= s1+"."+zeroPad( arr[index+6]/1e6, 3 ); // nanos
        }
        return s1;
    }
    
    /**
     * format the 14-element array efficiently (few characters).  
     * @param int array 14-element array of [Y,M,D,H,M,S,NS,Y,M,D,H,M,S,NS]
     * @returns String
     */
    function formatISO8601Range( arr ) {
        var s1,s2;
        ds= [ arr[7]-arr[0], arr[8]-arr[1], arr[9]-arr[2], arr[10]-arr[3], arr[11]-arr[4], arr[12]-arr[5], arr[13]-arr[6] ];
        uu= [ "Y","M","D","H","M","S" ];
        dur= "P";
        havet= false;
        for ( i=0; i<ds.length; i++ ) {
            if ( ds[i]!==0 ) {
                if ( i>2 && havet===false ) {
                    dur= dur + "T";
                    havet= true;
                }
                dur= dur + ds[i] + uu[i];
            }
        }
        s1= formatISO8601( arr, 0 );
        if ( dur.length > 1 && dur.length < 6 ) {
            return s1+"/"+dur;
        } else {
            s2= formatISO8601( arr, 7 );
            return s1+"/"+s2;
        }
        
    }
    
//    public static void main( String[] args ) {
//        int[] r= new int[14];
//        
//        parseISO8601Range( "2014-01-12T03:07:09.200/2015-02-12T03:04",r);
//        for ( int i=0; i<14; i++ ) System.err.printf(" %4d",r[i]);
//        System.err.println();
//        
//        parseISO8601Range( "2014-01-12T03:07/P1D",r);
//        for ( int i=0; i<14; i++ ) System.err.printf(" %4d",r[i]);
//        System.err.println();
//        
//        parseISO8601Range( "2014-01-12T03:07/P1DT12H",r);
//        for ( int i=0; i<14; i++ ) System.err.printf(" %4d",r[i]);
//        System.err.println();
//        
//        parseISO8601Range( "P1D/2014-01-12T03:07",r);
//        for ( int i=0; i<14; i++ ) System.err.printf(" %4d",r[i]);
//        System.err.println();        
//    }

