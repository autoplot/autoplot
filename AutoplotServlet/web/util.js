/**
 * returns the time found in an iso8601 string, or null.  This supports
 * periods (durations) as in: 2007-03-01T13:00:00Z/P1Y2M10DT2H30M
 * Other examples:
 *   2007-03-01T13:00:00Z/2008-05-11T15:30:00Z
 *   2007-03-01T13:00:00Z/P1Y2M10DT2H30M
 *   P1Y2M10DT2H30M/2008-05-11T15:30:00Z
 *   2007-03-01T00:00Z/P1D
 *   2012-100T02:00/03:45
 * https://en.wikipedia.org/wiki/ISO_8601#Time_intervals
 * @param stringIn
 * @param result if non-null should be an int[14] to provide storage to routine.
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
        digits0= [ dd.getUTCFullYear(), dd.getUTCMonth()+1, dd.getUTCDate(), dd.getUTCHours(), dd.getUTCMinutes(), dd.getUTCSeconds(), dd.getUTCMilliseconds()*1000000 ];
    } else if ( parts[0].startsWith('now-') ) {
        dd= new Date();
        delta= parseISO8601Duration(parts[0].substring(4));
        digits0= [ dd.getUTCFullYear(), dd.getUTCMonth()+1, dd.getUTCDate(), dd.getUTCHours(), dd.getUTCMinutes(), dd.getUTCSeconds(), dd.getUTCMilliseconds()*1000000 ];          
        for ( j=0; j<7; j++ ) digits0[j]-= delta[j]; 
    } else if ( parts[0].startsWith('now+') ) {
        dd= new Date();
        delta= parseISO8601Duration(parts[0].substring(4));
        digits0= [ dd.getUTCFullYear(), dd.getUTCMonth()+1, dd.getUTCDate(), dd.getUTCHours(), dd.getUTCMinutes(), dd.getUTCSeconds(), dd.getUTCMilliseconds()*1000000 ];         
        for ( j=0; j<7; j++ ) digits0[j]+= delta[j]; 
    } else {
        digits0= [0,0,0,0,0,0,0];
        lsd= parseISO8601Datum( parts[0], digits0, lsd );
        for ( j=lsd+1; j<3; j++ ) digits0[j]=1; // month 1 is first month, not 0. day 1 
    }

    if ( d2 ) {
        digits1= parseISO8601Duration(parts[1]);
    } else if ( parts[1]==='now' ) {
        dd= new Date();
        digits1= [ dd.getUTCFullYear(), dd.getUTCMonth()+1, dd.getUTCDate(), dd.getUTCHours(), dd.getUTCMinutes(), dd.getUTCSeconds(), dd.getUTCMilliseconds()*1000000 ];
    } else if ( parts[1].startsWith('now-') ) {
        dd= new Date();
        delta= parseISO8601Duration(parts[1].substring(4));
        digits1= [ dd.getUTCFullYear(), dd.getUTCMonth()+1, dd.getUTCDate(), dd.getUTCHours(), dd.getUTCMinutes(), dd.getUTCSeconds(), dd.getUTCMilliseconds()*1000000 ];            
        for ( j=0; j<7; j++ ) digits1[j]-= delta[j]; 
    } else if ( parts[1].startsWith('now+') ) {
        dd= new Date();
        delta= parseISO8601Duration(parts[1].substring(4));
        digits1= [ dd.getUTCFullYear(), dd.getUTCMonth()+1, dd.getUTCDate(), dd.getUTCHours(), dd.getUTCMinutes(), dd.getUTCSeconds(), dd.getUTCMilliseconds()*1000000 ];            
        for ( j=0; j<7; j++ ) digits1[j]+= delta[j]; 
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
 * efficiently format t1 and t2 as ISO8601 times range.
 * @param {type} t1
 * @param {type} t2
 * @returns {undefined}
 */
function formatISO8601Range( t1, t2 ) {
    if ( typeof t2 === "undefined" ) {
        t2= [ t1[7], t1[8], t1[9], t1[10], t1[11], t1[12], t1[13] ];
        t1= [ t1[0], t1[1], t1[2], t1[3], t1[4], t1[5], t1[6] ];
    }
    d=7;
    // id the first non-zero trailing digit.
    while ( t1[d]===0 && t2[d]===0 && d>3 ) {
        d=d-1;
    }
    s1= new Date(t1).toJSON();
    s2= new Date(t2).toJSON();
    return s1+'/'+s2;
}

function add( t, dt ) {
    return [ t[0]+dt[0], t[1]+dt[1], t[2]+dt[2], t[3]+dt[3], t[4]+dt[4], t[5]+dt[5], t[6]+dt[6] ];
}
    
function subtract( t, dt ) {
    return [ t[0]-dt[0], t[1]-dt[1], t[2]-dt[2], t[3]-dt[3], t[4]-dt[4], t[5]-dt[5], t[6]-dt[6] ];
}    

/**
 * 
 * @param {number} startMilliseconds the time in ms since 1970.
 * @param {number} endMilliseconds the time in ms since 1970.
 * @returns the formatted string, limited to resolution
 */
function iso8601RangeStr( startMilliseconds, endMilliseconds ) {
    st1= iso8601Str( startMilliseconds, endMilliseconds, startMilliseconds );
    st2= iso8601Str( startMilliseconds, endMilliseconds, endMilliseconds );
    return st1 + "/"+ st2;
}

/**
 * return the iso string, limited in resolution by startMilliseconds and endMilliseconds
 * @param {number} startMilliseconds time in ms since 1970
 * @param {number} endMilliseconds time in ms since 1970
 * @param {number} t the time to be formatted
 * @returns the formatted string, limited to the resolution
 */
function iso8601Str( startMilliseconds, endMilliseconds, t ) {
    
    s= new Date(t).toJSON();
    if ( endMilliseconds - startMilliseconds > 100*24*86400000 ) {
        s= s.substring(0,11)+"00:00Z";
    } else if ( endMilliseconds - startMilliseconds > 5*24*86400000 ) {
        s= s.substring(0,13)+":00Z";
    } else if ( endMilliseconds - startMilliseconds > 43200000 ) {
        s= s.substring(0,16)+"Z";
    } else if ( endMilliseconds - startMilliseconds > 3600000 ) {
        s= s.substring(0,19)+"Z";
    }
    return s;
}

class DatumRange {
    constructor( stimerange, t2 ) {
        if ( typeof stimerange === "undefined" ) {
            console.log('either pass in ISO8601 string for parsing, or two times');
            return;
        }
        if ( typeof stimerange === "string") {
            n= stimerange.indexOf('/');
            if ( n===-1 ) {
                if ( stimerange.length<4 ) {
                    return;
                } else if ( stimerange.length===4 ) { // YYYY
                    stimerange= stimerange+'/P1Y';
                } else if ( stimerange.length===7 ) { // YYYY-DD
                        stimerange= stimerange+'/P1M';
                } else if ( stimerange.length===8 ) { // YYYY-MMM
                    stimerange= stimerange+'/P1D';
                } else if ( stimerange.length===10 ) { // YYYY-MM-DD
                    stimerange= stimerange+'/P1D';
                }
            }        
            itr = parseISO8601Range( stimerange );
            st1 = [ itr[0], itr[1]-1, itr[2], itr[3], itr[4], itr[5], itr[6]/1000000 ];
            t1= st1.getTime() - st1.getTimezoneOffset() * 60000;  // really?  
            st2 = [ itr[7], itr[8]-1, itr[9], itr[10], itr[11], itr[12], itr[13]/1000000 ];
            t2= st2.getTime() - st2.getTimezoneOffset() * 60000;
            this.t1= t1;
            this.t2= t2;
        } else {
            this.t1= stimerange;
            this.t2= t2;
        }
    }
    
    span() {
        s= [ t2[0]-t1[0], t2[1]-t1[1], t2[2]-t1[2], t2[3]-t1[3], t2[4]-t1[4], t2[5]-t1[5], t2[6]-t1[6] ];
        return s;
    }
        
    next() {
        s= span();
        result= DatumRange( add( this.t1, s ), add( this.t2, s ) );
    }
    
    prev() {
        s= span();
        result= DatumRange( subtract( this.t1, s ), subtract( this.t2, s ) );
    }
    
    
}