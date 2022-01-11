
// require( ['TimeRangeParser.js' ] );
// you must bring in TimeRangeParser.js before using this.


function previousInterval( tf ) {
    s= tf.value;
    r= parseISO8601Range( s, undefined );
    dt= [ r[7]-r[0], r[8]-r[1], r[9]-r[2], r[10]-r[3], r[11]-r[4], r[12]-r[5], r[13]-r[6] ];
    r= subtract( r, dt );
    tf.value= formatISO8601Range( r );
}

function nextInterval( tf ) {
    s= tf.value;
    r= parseISO8601Range( s, undefined );
    dt= [ r[7]-r[0], r[8]-r[1], r[9]-r[2], r[10]-r[3], r[11]-r[4], r[12]-r[5], r[13]-r[6] ];
    r= add( r, dt );    
    tf.value= formatISO8601Range( r );
}

function add( t, dt ) {
    if ( t.length===14 ) {
        result= [ t[0]+dt[0], t[1]+dt[1], t[2]+dt[2], t[3]+dt[3], t[4]+dt[4], t[5]+dt[5], t[6]+dt[6],
            t[7]+dt[0], t[8]+dt[1], t[9]+dt[2], t[10]+dt[3], t[11]+dt[4], t[12]+dt[5], t[13]+dt[6] ];
        result= normalizeTime(result,7);
    } else {
        result= [ t[0]+dt[0], t[1]+dt[1], t[2]+dt[2], t[3]+dt[3], t[4]+dt[4], t[5]+dt[5], t[6]+dt[6] ];
    }
    result= normalizeTime(result,0);
    return result;
}
    
function subtract( t, dt ) {
    if ( t.length===14 ) {
        result = [ t[0]-dt[0], t[1]-dt[1], t[2]-dt[2], t[3]-dt[3], t[4]-dt[4], t[5]-dt[5], t[6]-dt[6],
            t[7]-dt[0], t[8]-dt[1], t[9]-dt[2], t[10]-dt[3], t[11]-dt[4], t[12]-dt[5], t[13]-dt[6] ];
        result= borrowTime(result,7);
    } else {
        result = [ t[0]-dt[0], t[1]-dt[1], t[2]-dt[2], t[3]-dt[3], t[4]-dt[4], t[5]-dt[5], t[6]-dt[6] ];
    }
    result= borrowTime(result,0);
    return result;
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