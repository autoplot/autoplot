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
function getInt(val, deft) {
    if (val === undefined) {
        if (deft !== -99)
            return deft;
        else
            alert("bad digit");
    }
    n = val.length - 1;
    if (isLetter(val.charAt(n))) {
        return parseInt(val.substring(0, n));
    } else {
        return parseInt(val);
    }
}

/**
 * get the double, allowing a letter at the end.
 * @param val string value
 * @param deft double value to return if the string is not valid. -99 will throw exception
 * @return 
 */
function getDouble(val, deft) {
    if (val === undefined) {
        if (deft !== -99)
            return deft;
        else
            alert("bad digit");
    }
    n = val.length - 1;
    if (isLetter(val.charAt(n))) {
        return parseFloat(val.substring(0, n));
    } else {
        return parseFloat(val);
    }
}


var simpleFloat = "\\d?\\.?\\d+";
var iso8601duration = "P(\\d+Y)?(\\d+M)?(\\d+D)?(T(\\d+H)?(\\d+M)?(" + simpleFloat + "S)?)?";

/**
 * returns a 7 element array with [year,mon,day,hour,min,sec,nanos] or [-9999].
 * @param stringIn
 * @return [year,mon,day,hour,min,sec,nanos]
 */
function parseISO8601Duration(stringIn) {
    var iso8601DurationPattern = new RegExp(iso8601duration, 'g');
    m = iso8601DurationPattern.exec(stringIn);
    if (m !== null) {
        dsec = getDouble(m[7], 0);
        sec = Math.floor(dsec);
        nanosec = Math.floor((dsec - sec) * 1e9);
        return [getInt(m[1], 0), getInt(m[2], 0), getInt(m[3], 0), getInt(m[5], 0), getInt(m[6], 0), sec, nanosec];
    } else {
        alert("unable to parse: " + stringIn);
    }
}

/**
 * find the next instance of delim in str, where delim is
 * one of the chars in delims
 * @param str the string we are parsing
 * @param index the start index
 * @param delims string of delims ("-T:.Z")
 */
function nextToken(str, index, delims) {
    index = index + 1;
    if (index > str.length)
        return -1;
    while (index < str.length) {
        ch = str.charAt(index);
        if (delims.indexOf(ch) > -1) {
            break;
        } else {
            index = index + 1;
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
function parseISO8601Datum(str, result, lsd) {
    delims = "-T:.Z";
    dir = "";
    DIR_FORWARD = "f";
    DIR_REVERSE = "r";
    want = 0;
    haveDelim = false;
    index = -1;
    index1 = nextToken(str, index, delims);
    while (index1 > -1) {
        if (haveDelim) {
            delim = str.charAt(index - 1); // delim is the delimiter before tok.
            if (index1 === str.length - 1) { // "Z"
                break;
            }
        } else {
            delim = '';
            haveDelim = true;
        }
        tok = str.substring(index, index1);
        if (dir === "") {
            if (tok.length === 4) { // typical route
                iyear = parseInt(tok);
                result[0] = iyear;
                want = 1;
                dir = DIR_FORWARD;
                result[1] = 0;
                result[2] = 0;
                result[3] = 0;
                result[4] = 0;
                result[5] = 0;
                result[6] = 0;

            } else if (tok.length === 6) {
                want = lsd;
                if (want !== 6)
                    alert("lsd must be 6");
                result[want] = parseInt(tok.substring(0, 2));
                want--;
                result[want] = parseInt(tok.substring(2, 4));
                want--;
                result[want] = parseInt(tok.substring(4, 6));
                want--;
                dir = DIR_REVERSE;
            } else if (tok.length === 7) {
                result[0] = parseInt(tok.substring(0, 4));
                result[1] = 1;
                result[2] = parseInt(tok.substring(4, 7));
                want = 3;
                dir = DIR_FORWARD;
                result[3] = 0;
                result[4] = 0;
                result[5] = 0;
                result[6] = 0;
            } else if (tok.length === 8) {
                result[0] = Integer.parseInt(tok.substring(0, 4));
                result[1] = Integer.parseInt(tok.substring(4, 6));
                result[2] = Integer.parseInt(tok.substring(6, 8));
                want = 3;
                dir = DIR_FORWARD;
                result[3] = 0;
                result[4] = 0;
                result[5] = 0;
                result[6] = 0;
            } else {
                dir = DIR_REVERSE;
                want = lsd;  // we are going to have to reverse these when we're done.
                i = parseInt(tok);
                result[want] = i;
                want--;
            }
        } else if (dir === DIR_FORWARD) {
            if (want === 1 && tok.length === 3) { // $j
                result[1] = 1;
                result[2] = parseInt(tok);
                want = 3;
            } else if (want === 3 && tok.length === 6) {
                result[want] = parseInt(tok.substring(0, 2));
                want++;
                result[want] = parseInt(tok.substring(2, 4));
                want++;
                result[want] = parseInt(tok.substring(4, 6));
                want++;
            } else if (want === 3 && tok.length === 4) {
                result[want] = parseInt(tok.substring(0, 2));
                want++;
                result[want] = parseInt(tok.substring(2, 4));
                want++;
            } else {
                i = parseInt(tok);
                if (delim === '.' && want === 6) {
                    n = 9 - tok.length;
                    result[want] = i * Math.pow(10, n);
                } else {
                    result[want] = i;
                }
                want++;
            }
        } else if (dir === DIR_REVERSE) { // what about 1200 in reverse?
            i = parseInt(tok);
            if (delim === '.') {
                n = 9 - tok.length;
                result[want] = i * Math.pow(10, n);
            } else {
                result[want] = i;
            }
            want--;
        }
        index = index1 + 1;
        index1 = nextToken(str, index, delims);
    }

    if (dir === DIR_REVERSE) {
        iu = want + 1;
        id = lsd;
        while (iu < id) {
            t = result[iu];
            result[iu] = result[id];
            result[id] = t;
            iu = iu + 1;
            id = id - 1;
        }
    } else {
        lsd = want - 1;
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
 * https://en.wikipedia.org/wiki/ISO_8601#Time_intervals
 * @param stringIn
 * @param result if non-null should be an int[14] to provide storage to routine.
 * @return int[14] with [Y,M,D,H,M,S,NS,Y,M,D,H,M,S,NS]
 */
function parseISO8601Range(stringIn, result) {

    parts = stringIn.split("/", 2);
    if ( parts.length!==2 ) {
        if ( parts[0].length<4 ) {
            throw Exception('time must have 4, 7, 8, or 10 digits');
        } else if ( parts[0].length===4 ) { // YYYY
            stringIn= stringIn+'/P1Y';
        } else if ( stringIn.length===7 ) { // YYYY-DD
            stringIn= stringIn+'/P1M';
        } else if ( stringIn.length===8 ) { // YYYY-MMM
            stringIn= stringIn+'/P1D';
        } else if ( stringIn.length===10 ) { // YYYY-MM-DD
            stringIn= stringIn+'/P1D';
        }
        parts= stringIn.split("/",2);
    }

    d1 = parts[0].charAt(0) === 'P'; // true if it is a duration
    d2 = parts[1].charAt(0) === 'P';

    lsd = -1;

    if (d1) {
        digits0 = parseISO8601Duration(parts[0]);
    } else if (parts[0] === 'now') {
        dd = new Date();
        digits0 = [dd.getUTCFullYear(), dd.getUTCMonth() + 1, dd.getUTCDate(), dd.getUTCHours(), dd.getUTCMinutes(), dd.getUTCSeconds(), dd.getUTCMilliseconds() * 1000000];
    } else if (parts[0].startsWith('now-')) {
        dd = new Date();
        delta = parseISO8601Duration(parts[0].substring(4));
        digits0 = [dd.getUTCFullYear(), dd.getUTCMonth() + 1, dd.getUTCDate(), dd.getUTCHours(), dd.getUTCMinutes(), dd.getUTCSeconds(), dd.getUTCMilliseconds() * 1000000];
        for (j = 0; j < 7; j++)
            digits0[j] -= delta[j];
    } else if (parts[0].startsWith('now+')) {
        dd = new Date();
        delta = parseISO8601Duration(parts[0].substring(4));
        digits0 = [dd.getUTCFullYear(), dd.getUTCMonth() + 1, dd.getUTCDate(), dd.getUTCHours(), dd.getUTCMinutes(), dd.getUTCSeconds(), dd.getUTCMilliseconds() * 1000000];
        for (j = 0; j < 7; j++)
            digits0[j] += delta[j];
    } else {
        digits0 = [0, 0, 0, 0, 0, 0, 0];
        lsd = parseISO8601Datum(parts[0], digits0, lsd);
        for (j = lsd + 1; j < 3; j++)
            digits0[j] = 1; // month 1 is first month, not 0. day 1 
    }

    if (d2) {
        digits1 = parseISO8601Duration(parts[1]);
    } else if (parts[1] === 'now') {
        dd = new Date();
        digits1 = [dd.getUTCFullYear(), dd.getUTCMonth() + 1, dd.getUTCDate(), dd.getUTCHours(), dd.getUTCMinutes(), dd.getUTCSeconds(), dd.getUTCMilliseconds() * 1000000];
    } else if (parts[1].startsWith('now-')) {
        dd = new Date();
        delta = parseISO8601Duration(parts[1].substring(4));
        digits1 = [dd.getUTCFullYear(), dd.getUTCMonth() + 1, dd.getUTCDate(), dd.getUTCHours(), dd.getUTCMinutes(), dd.getUTCSeconds(), dd.getUTCMilliseconds() * 1000000];
        for (j = 0; j < 7; j++)
            digits1[j] -= delta[j];
    } else if (parts[1].startsWith('now+')) {
        dd = new Date();
        delta = parseISO8601Duration(parts[1].substring(4));
        digits1 = [dd.getUTCFullYear(), dd.getUTCMonth() + 1, dd.getUTCDate(), dd.getUTCHours(), dd.getUTCMinutes(), dd.getUTCSeconds(), dd.getUTCMilliseconds() * 1000000];
        for (j = 0; j < 7; j++)
            digits1[j] += delta[j];
    } else {
        if (d1) {
            digits1 = [0, 0, 0, 0, 0, 0, 0];
        } else {
            digits1 = digits0.slice(0); // make a clone of the array
        }
        lsd = parseISO8601Datum(parts[1], digits1, lsd);
        for (j = lsd + 1; j < 3; j++)
            digits1[j] = 1; // month 1 is first month, not 0. day 1 
    }

    if (digits0 === null || digits1 === null)
        return null;

    if (d1) {
        for (i = 0; i < 7; i++)
            digits0[i] = digits1[i] - digits0[i];
    }

    if (d2) {
        for (i = 0; i < 7; i++)
            digits1[i] = digits0[i] + digits1[i];
    }

    if (result === undefined) {
        result = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
    }
    for (i = 0; i < 7; i++)
        result[i] = digits0[i];
    for (i = 0; i < 7; i++)
        result[i + 7] = digits1[i];

    return result;

}

/**
 * returns the time found in the string.  This can be an ISO8601 time 
 * range string, or four-digit year, or other convenient forms.  
 * Other examples:
 * <ul>
 *   <li> 2007-03-01T13:00:00Z/2008-05-11T15:30:00Z (or any ISO8601 time range)
 *   <li> 2007
 *   <li> 2007-12  year, month
 *   <li> 2007-120  year, day of year
 *   <li> 2007-12-01  year, month, day
 *   <li> 20071201 year, month, day
 *   <li> 2007120 year, day-of-year
 * </ul>
 * https://en.wikipedia.org/wiki/ISO_8601#Time_intervals
 * @param stringIn
 * @param result if non-null should be an int[14] to provide storage to routine.
 * @return int[14] with [Y,M,D,H,M,S,NS,Y,M,D,H,M,S,NS]
 */
function parseTimeRange(stringIn, result) {
    parts = stringIn.split("/", 2);
    ll= stringIn.length;
    
    if (parts.length === 1) { // check for Das2 string "to" keyword
        temp= stringIn.split("to", 2);
        if ( temp.length===2 ) {
            temp[0]= temp[0].trim();
            temp[1]= temp[1].trim();
            parts= temp;
            stringIn= parts[0]+"/"+parts[1];
        }
    }
    if (parts.length === 1) {
        if ( ll === 4) {
            yr = parseInt(stringIn);
            return [yr, 1, 1, 0, 0, 0, 0, yr + 1, 1, 1, 0, 0, 0, 0];
        } else if (ll === 7) {
            yr = parseInt(stringIn.substring(0, 4));
            c = stringIn.substring(4, 5);
            if (c >= '0' && c <= '9') {
                doy = parseInt(stringIn.substring(4, 7));
                return [yr, 1, doy, 0, 0, 0, 0, yr, 1, doy + 1, 0, 0, 0, 0];

            } else {
                month = parseInt(stringIn.substring(5, 7));
                if (month === 12) {
                    return [yr, 12, 1, 0, 0, 0, 0, yr + 1, 1, 1, 0, 0, 0, 0];
                } else {
                    return [yr, month, 1, 0, 0, 0, 0, yr, month + 1, 1, 0, 0, 0, 0];
                }
            }
        } else if (ll === 8) {
            yr = parseInt(stringIn.substring(0, 4));
            c = stringIn.substring(4, 5);
            if (c >= '0' && c <= '9') {
                month = parseInt(stringIn.substring(4, 6));
                day = parseInt(stringIn.substring(6, 8));
                return [yr, month, day, 0, 0, 0, 0, yr, month, day + 1, 0, 0, 0, 0];
            } else {
                doy = parseInt(stringIn.substring(5, 8));
                return [yr, 1, doy, 0, 0, 0, 0, yr, 1, doy + 1, 0, 0, 0, 0];
            }
        } else if (ll === 10) {
            yr = parseInt(stringIn.substring(0, 4));
            month = parseInt(stringIn.substring(5, 7));
            day = parseInt(stringIn.substring(8, 10));
            return [yr, month, day, 0, 0, 0, 0, yr, month, day + 1, 0, 0, 0, 0];
        }
    } else {
        return parseISO8601Range(stringIn, result);
    }
}

function isLeapYear(year) {
    if (year < 1800 || year > 2400) {
        alert("year must be between 1800 and 2400");
    }
    return (year % 4) === 0 && (year % 400 === 0 || year % 100 !== 0);
}

DAYS_IN_MONTH = [
    [0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31, 0],
    [0, 31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31, 0]
];

DAY_OFFSET = [
    [0, 0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334, 365],
    [0, 0, 31, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335, 366]
];

/**
 * return the leap year for years 1901-2099.
 * @param year
 * @return
 */
function isLeapYear( year ) {
    return (year % 4)===0 && ( year%400===0 || year%100!==0 );
}
    
function daysInMonth( year, month ) {
    return DAYS_IN_MONTH[isLeapYear(year)?1:0][month];
}

/**
 * Normalize the TimeStruct by decrementing higher digits.
 * @throws IllegalArgumentException if t.day&lt;0 or t.month&lt;1
 * @param t the time in seven components: [ Y, m, d, H, M, S, nanos ].
 * @param offset 0 or 7, to support time ranges
 * @return the normalized array
 * @see #normalize(org.das2.datum.TimeUtil.TimeStruct) 
 */
function borrowTime( t, offset ) {
    result= [ t[0+offset],t[1+offset],t[2+offset],t[3+offset],t[4+offset],t[5+offset],t[6+offset] ];
    while ( result[6]<0 ) {
        result[6]= result[6]+1000000000;
        result[5]= result[5]-1;
    }
    while ( result[5]<0 ) {
        result[5]= result[5]+60;
        result[4]= result[4]-1;
    }
    while ( result[4]<0 ) {
        result[4]= result[4]+60;
        result[3]= result[3]-1;
    }
    while ( result[3]<0 ) {
        result[3]= result[3]+24;
        result[2]= result[2]-1;
    }
    if (result[2]===0) {
        if (result[1]>1) {
            daysLastMonth= daysInMonth(result[0],result[1]-1);
        } else {
            daysLastMonth= 31;
        }
        result[2]+=daysLastMonth;
        result[1]--;
    }
        
    if (result[1]===0) {
        result[1]+=12;
        result[0]--;
    }
    
    for ( i=0; i<7; i++ ) {
        t[i+offset] = result[i];
    }
    
    return t;           
}

/**
 * normalize the decomposed time by expressing day of year and month
 * and day of month, and moving hour="24" into the next day. This
 * also handles day increment or decrements, by:<ul>
 * <li>handle day=0 by decrementing month and adding the days in the new month.
 * <li>handle day=32 by incrementing month.
 * </ul>
 * @param time 7 or 14 element array
 * @param offset 0 or 7 for offset of year component of time to normalize
 * @returns time
 */
function normalizeTime(time,offset) {
    if (time[3+offset] === 24) {
        time[2+offset] += 1;
        time[3+offset] = 0;
    }
    if (time[3+offset] > 24) {
        throw new IllegalArgumentException("time[3] is greater than 24 (hours)");
    }
    if (time[1+offset] > 12 && time[2+offset]===1 ) {
        time[1+offset]= time[1+offset]-12;
        time[0+offset]= time[0+offset]+1;
    }
    if ( time[1+offset]>12 ) {
        throw new IllegalArgumentException("time[1] is greater than 12 (months)");
    }
    if (time[1+offset] === 12 && time[2+offset] === 32) {
        time[0+offset] = time[0+offset] + 1;
        time[1+offset] = 1;
        time[2+offset] = 1;
        return time;
    }
    leap = isLeapYear(time[0+offset]) ? 1 : 0;
    if (time[2+offset] === 0) {
        time[1+offset] = time[1+offset] - 1;
        if (time[1+offset] === 0) {
            time[0+offset] = time[0+offset] - 1;
            time[1+offset] = 12;
        }
        time[2+offset] = DAYS_IN_MONTH[leap][time[1+offset]];
    }
    d = DAYS_IN_MONTH[leap][time[1+offset]];
    while (time[2+offset] > d) {
        time[1+offset]++;
        time[2+offset] -= d;
        d = DAYS_IN_MONTH[leap][time[1+offset]];
        if (time[1+offset] > 12) {
            throw new IllegalArgumentException("time["+(2+offset)+"] is too big");
        }
    }
    return time;
}

/**
 * javascript doesn't support sprintf style formatting, so support this by hand.
 * @param num zero or positive number
 * @param size total number of digits, must be less than 10.
 * @returns formatted in with zeroes prefix.
 */
function zeroPad(num, size) {
    var s = "000000000" + num;
    return s.substr(s.length - size);
}

/**
 * format the seven digits starting at index.
 * @param arr like [ 2014, 1, 5, 0, 0, 3, 300000000, 2014, 1, 5, 0, 0, 3, 600000000 ]
 * @param index into array like 7
 * @returns ISO8601 formatted string like "2014-01-05T00:00:03.600" 
 */
function formatISO8601(arr, index) {
    var s1;
    if (arr[index + 1] === 1 && arr[index + 2] > 31) {  // day-of-year
        s1 = zeroPad(arr[index + 0], 4) + "-" + zeroPad(arr[index + 2], 3) + "T" + zeroPad(arr[index + 3], 2) + ":" + zeroPad(arr[index + 4], 2);
    } else {
        s1 = zeroPad(arr[index + 0], 4) + "-" + zeroPad(arr[index + 1], 2) + "-" + zeroPad(arr[index + 2], 2) + "T" + zeroPad(arr[index + 3], 2) + ":" + zeroPad(arr[index + 4], 2);
    }
    if (arr[index + 5] > 0 || arr[index + 6] > 0) {
        s1 = s1 + ":" + zeroPad(arr[index + 5], 2);
    }
    if (arr[index + 6] > 0) {
        s1 = s1 + "." + zeroPad(arr[index + 6] / 1e6, 3); // nanos
    }
    return s1;
}

/**
 * format the 14-element array efficiently (few characters).  
 * @param arr array 14-element array of [Y,M,D,H,M,S,NS,Y,M,D,H,M,S,NS]
 * @returns String
 */
function formatISO8601Range(arr) {
    var s1, s2;
    ds = width( arr );
    uu = ["Y", "M", "D", "H", "M", "S"];
    dur = "P";
    havet = false;
//    for (i = 0; i < ds.length; i++) {
//        if (ds[i] !== 0) {
//            if (i > 2 && havet === false) {
//                dur = dur + "T";
//                havet = true;
//            }
//            dur = dur + ds[i] + uu[i];
//        }
//    }
    s1 = formatISO8601(arr, 0);
    if (dur.length > 1 && dur.length < 6) {
        if ( dur==='P1D' && s1.endsWith("T00:00") ) {
            return s1.substring(0,10);
        } else if ( dur==='P1M' && s1.endsWith("01T00:00") ) {
            return s1.substring(0,7);
        } else {
            return s1 + "/" + dur;
        }
    } else {
        s2 = formatISO8601(arr, 7);
        return s1 + "/" + s2;
    }

}

/**
 * return the width of the 14-element time range
 * @param {type} r
 * @returns {undefined}
 */
function width( r ) {
    dt= [ r[7]-r[0], r[8]-r[1], r[9]-r[2], r[10]-r[3], r[11]-r[4], r[12]-r[5], r[13]-r[6] ];
    while ( dt[6]<0 && dt[5]>0 ) {
        dt[6]= dt[6]+1000000000;
        dt[5]= dt[5]-1;
    }
    while ( dt[5]<0 && dt[4]>0 ) {
        dt[5]= dt[5]+60;
        dt[4]= dt[4]-1;
    }
    while ( dt[4]<0 && dt[3]>0 ) {
        dt[4]= dt[4]+60;
        dt[3]= dt[3]-1;
    }
    while ( dt[3]<0 && dt[2]>0 ) {
        dt[3]= dt[3]+24;
        dt[2]= dt[2]-1;
    }
    while ( dt[2]<0 && dt[1]>0 ) {
        dt[2]= dt[2]+daysInMonth(r[0],r[1]-1);  // TODO: this needs to be tested
        dt[1]= dt[1]-1;
    }
    while ( dt[1]<0 && dt[0]>0 ) {
        dt[1]= dt[1]+12;
        dt[0]= dt[0]-1;
    }
    return dt;
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

