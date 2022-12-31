
// require( ['TimeUtil.js'] );
// require( ['TimeRangeParser.js' ] );
// you must bring in TimeUtil.js before using this.

function handleLoose(s) {
    parts = s.split("/", 2);
    if ( parts.length===1 ) {
        if ( parts[0].length<4 ) {
            throw Exception('time must have 4, 7, 8, or 10 digits');
        } else if ( parts[0].length===4 ) { // YYYY
            s= s+'/P1Y';
        } else if ( parts[0].length===7 ) { // YYYY-DD
            s= s+'/P1M';
        } else if ( parts[0].length===8 ) { // YYYY-MMM
            s= s+'/P1D';
        } else if ( parts[0].length===10 ) { // YYYY-MM-DD
            s= s+'/P1D';
        }
    }
    return s;
}

function previousInterval( tf ) {
    s= tf.value;
    s= handleLoose(s);
    r= TimeUtil.parseISO8601TimeRange(s);
    r= TimeUtil.previousRange(r);
    s= TimeUtil.formatIso8601TimeRange(r);
    tf.value= s;
}

function nextInterval( tf ) {
    s= tf.value;
    s= handleLoose(s);
    r= TimeUtil.parseISO8601TimeRange(s);
    r= TimeUtil.nextRange(r);
    s= TimeUtil.formatIso8601TimeRange(r);
    tf.value= s;
}

