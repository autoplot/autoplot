// *****************************************************************************
//
// Global variables
//
// *****************************************************************************

var imgurl = '';
var zoomurl = '';
var startdateinmilliseconds = 0.0;
var enddateinmilliseconds = 0.0;
var diffmilliseconds = 0.0;
var leftside = 0; // These now come from plotInfo.
var rightside = 0;
var topside = 0;
var bottomside = 0;
var graphwidth = 0; // rightside - leftside;
var graphheight = 0; // bottomside - topside;
var msecperpx = 0;
var center = undefined;
var imgloaded= 0;  // 1=onload.  2=plotinfo ajax  3=both

// ****************************************************************************
//
// Function() definitions
//
// ****************************************************************************

function buildImgUrl(srcurl, start, end) {
    var outurl = '';
    var inpurl = srcurl;
    var iso8601s = new Date(start).toISOString();
    var iso8601e = new Date(end).toISOString(); 
    var slt = inpurl.split('&timeRange=');
    outurl = slt[0] + "&timeRange=" + iso8601s + "/" + iso8601e;
    console.log('' + start + " - " + end + " " + new Date(start));
    console.log('' + iso8601s + "/" + iso8601e);
    return outurl;
}

function echoImgUrl() {
    //$('#idechourl').text(imgurl);
}

function echoGraphParams() {
    //$('#iddates').text('StartDate = ' + PLOTINFO.plots[0].xaxis.min + ' ,    ' + 'EndDate = ' + PLOTINFO.plots[0].xaxis.max );
    //$('#idwidthheight').text('');
}

/**
 * scan to the previous interval.
 */
function scanprev() {
    setTime(startdateinmilliseconds - diffmilliseconds, enddateinmilliseconds - diffmilliseconds);
}

/**
 * scan to the next interval
 */
function scannext() {
    setTime(startdateinmilliseconds + diffmilliseconds, enddateinmilliseconds + diffmilliseconds);
}

/**
 * scan half way to the previous interval.
 */
function scanhalfprev() {
    setTime(startdateinmilliseconds - diffmilliseconds/2, enddateinmilliseconds - diffmilliseconds/2);
}

/**
 * scan half way to the next interval
 */
function scanhalfnext() {
    setTime(startdateinmilliseconds + diffmilliseconds/2, enddateinmilliseconds + diffmilliseconds/2);
}

/**
 * zoom out so the plotted range is 3 times the width.
 */
function zoomout() {
    setTime(startdateinmilliseconds - diffmilliseconds, enddateinmilliseconds + diffmilliseconds);
}

/**
 * allow the URI to be reset
 */
function refresh() {
    setTime(startdateinmilliseconds, enddateinmilliseconds);
}

/**
 * the current URL to set.  The timerange is reset by appending to this "timeRange=" + iso8601s + "/" + iso8601e;
 * If blank, then read the URL from vapta input area.
 */
function resetUrl(url) {
    if ( url.length===0 ) {
        url= '../../SimpleServlet?vap='+document.getElementById('vapta').value;
    }
    $('#idstatus').text("reset url "+url);
    $('#progress').attr('src', 'spinner.gif');
    imgurl = url;
    ImageInfo.loadInfo(imgurl, mycallback);
    document.getElementById('idplot').src = imgurl;
}

/**
 * zoom in to the middle third.
 * @returns {undefined}
 */
function zoomin() {
    third = (enddateinmilliseconds - startdateinmilliseconds) / 3;
    setTime(startdateinmilliseconds + third, enddateinmilliseconds - third);
}

/**
 * center to the focus position
 */
function centerFocus() {
    if (typeof center === "undefined") {
        $("#info").html('click on the plot to set focus position');
    } else {
        sdatax = $('#xclick').val();
        $("#info").html(sdatax);
        datax = new Date(sdatax).toJSON();
        center = new Date(datax).getTime();
        half = (enddateinmilliseconds - startdateinmilliseconds) / 2;
        setTime(center - half, center + half);
    }
}

/**
 * 
 * @param number startMilliseconds
 * @param number endMilliseconds
 * @returns the formatted string, limited to resolution
 */
function iso8601RangeStr( startMilliseconds, endMilliseconds ) {
    st1= iso8601Str( startMilliseconds, endMilliseconds, startMilliseconds );
    st2= iso8601Str( startMilliseconds, endMilliseconds, endMilliseconds );
    return st1 + "/"+ st2;
}

/**
 * return the iso string, limited in resolution by startMilliseconds and endMilliseconds
 * @param number startMilliseconds time in ms since 1970
 * @param number endMilliseconds time in ms since 1970
 * @param number t the time to be formatted
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

function resetWidth() {
    if (typeof xwidth === "undefined") {
        $("#info").html('reset the width of the time axis');
    } else {
        swidthx = $('#xwidth').val();
        center = (enddateinmilliseconds + startdateinmilliseconds) / 2;
        half = (swidthx*3600000)/2;
        setTime(center - half, center + half);
    }
}

/**
 * callback that the image was loaded.
 * @returns {undefined}
 */
function logloaded() {
    imgloaded= imgloaded & ( ~1 );
    console.log('image loaded '+imgloaded);
    if ( imgloaded===0 ) {
       $('#progress').attr('src', 'idle-icon.png');
    }
}

/**
 * reset the timerange to the contents of $('#timerange').val().  The times
 * are parsed with an ISO8601 parser, so the following example timeranges 
 * are supported:
 * "2014-01-12T03:07:09.200/2015-02-12T03:04"
 * "2014-01-12T03:07/P1D"      1-day following the datum
 * "2014-01-12T03:07/P1DT12H"  1-day 12-hours following the datum
 * "P1D/2014-01-12T03:07"      1-day preceeding the datum
 * "P1D/now"                   1-day preceeding this instant
 * @returns {undefined}
 */
function resetTime() {
    if (typeof timerange === "undefined") {
        $("#info").html('reset the timerange for the time axis');
    } else {
        stimerange = $('#timerange').val();
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
        st1 = new Date( itr[0], itr[1]-1, itr[2], itr[3], itr[4], itr[5], itr[6]/1000000 ); //stimerange.substring(0,n)).toJSON();
        t1= st1.getTime() - st1.getTimezoneOffset() * 60000;  // really?  
        st2 = new Date( itr[7], itr[8]-1, itr[9], itr[10], itr[11], itr[12], itr[13]/1000000 );
        t2= st2.getTime() - st2.getTimezoneOffset() * 60000;
        setTime( t1,t2 );
    }
}

function testing() {
    setTime(1104451200000, 1104451200000 + 86400000);
}

function clickshift(subEvent) {

    plotInfo = PLOTINFO;  // there's just the one...

    console.log(plotInfo);

    if (plotInfo == -1) {
        $("#info").html('No metadata found.');
        return;
    }

    var xx = subEvent.offsetX || (subEvent.pageX - subEvent.target.offsetLeft);
    var yy = subEvent.offsetY || (subEvent.pageY - subEvent.target.offsetTop);

    //console.log(subEvent);
    var found = false;
    for (i = 0; i < plotInfo.plots.length; i++) {
        var p = plotInfo.plots[i];
        //console.log(p)
        if (p.xaxis.left <= xx && xx < p.xaxis.right && p.yaxis.top <= yy && yy < p.yaxis.bottom) {
            l = p.xaxis.right - p.xaxis.left;

            if (p.xaxis.units === 'UTC') {
                dmin = Date.parse(p.xaxis.min);
                dmax = Date.parse(p.xaxis.max);
                datax = ((xx - p.xaxis.left) * dmax + (p.xaxis.right - xx) * dmin) / l;
                datax = iso8601Str( dmin, dmax, datax );
            } else {
                if (p.xaxis.type === 'log') {
                    oo = ((p.xaxis.right - xx) / l);
                    zz = Math.log(p.xaxis.max / p.xaxis.min);
                    datax = Math.exp(Math.log(p.xaxis.min) + ((xx - p.xaxis.left) / l) * Math.log(p.xaxis.max / p.xaxis.min));
                } else {
                    datax = ((xx - p.xaxis.left) * p.xaxis.min + (xx - p.xaxis.left) * p.xaxis.max) / l;
                }
            }
            l = p.yaxis.bottom - p.yaxis.top;
            if (p.yaxis.type === 'log') {
                datay = Math.exp(Math.log(p.yaxis.min) + ((p.yaxis.bottom - yy) / l) * Math.log(p.yaxis.max / p.yaxis.min));
            } else {
                datay = ((yy - p.yaxis.top) * p.yaxis.min + (p.yaxis.bottom - yy) * p.yaxis.max) / l;
            }

            $("#xclick").val(datax);
            $("#info").html('x:' + datax + ' y:' + datay);
            //'x:' + xx + ' y:' + yy + ' np:' + plotInfo.numberOfPlots + ' ip:'+i + 
            found = true;
        }
    }
    if (!found) {
        $("#info").html('outside axis bounds');
    } else {
        center = new Date(datax).getTime();
    }

}


function setTime(startMilliseconds, endMilliseconds) {
    console.log('==setTime()==');
    console.log('    startMilliseconds=' + iso8601Str( startMilliseconds,endMilliseconds,startMilliseconds ) );
    console.log('    diffmilliseconds=' + (endMilliseconds - startMilliseconds));
    console.log('PLOTINFO.plots[0].xaxis.min,max=' + PLOTINFO.plots[0].xaxis.min + '/' + PLOTINFO.plots[0].xaxis.max);
    zoomurl = buildImgUrl(imgurl, startMilliseconds, endMilliseconds);
    console.log('imgAreaSelect() : ' + 'zoomurl = ' + zoomurl);
    console.log('    imgurl:  ', imgurl );
    console.log('    zoomurl: ', zoomurl );
    imgloaded= 3;
    $('#idplot').attr('src', zoomurl);
    $('#xwidth').val( ''+((endMilliseconds-startMilliseconds) /3600000) );
    $('#timerange').val( iso8601RangeStr(startMilliseconds,endMilliseconds) );
    $('#idstatus').text("setTime "+iso8601RangeStr(startMilliseconds,endMilliseconds));
    $('#progress').attr('src', 'spinner.gif');

    // update imgurl
    imgurl = zoomurl;
    startdateinmilliseconds = startMilliseconds;
    enddateinmilliseconds = endMilliseconds;
    diffmilliseconds = enddateinmilliseconds - startdateinmilliseconds;
    msecperpx = diffmilliseconds / graphwidth;
    
    //echoImgUrl();

    ImageInfo.loadInfo(imgurl, mycallback);
    console.log('--> startdateinmilliseconds=' + startdateinmilliseconds);

}

// Callback function for when metadata extracted
function mycallback() {
    splotInfo = ImageInfo.getField(imgurl, "data")['plotInfo'];
    PLOTINFO = $.parseJSON(splotInfo);
    console.log('   mycallback--> startdateinmilliseconds=' + iso8601Str( startdateinmilliseconds,enddateinmilliseconds,startdateinmilliseconds ) );
    startdateinmilliseconds = new Date(PLOTINFO.plots[0].xaxis.min).getTime();
    enddateinmilliseconds = new Date(PLOTINFO.plots[0].xaxis.max).getTime();
    diffmilliseconds = enddateinmilliseconds - startdateinmilliseconds;
    console.log('   mycallback--> startdateinmilliseconds=' + iso8601Str( startdateinmilliseconds,enddateinmilliseconds,startdateinmilliseconds) + " (exit)");

    $('#xwidth').val( diffmilliseconds/3600000 );
    dd1= new Date(startdateinmilliseconds);
    dd2= new Date(enddateinmilliseconds);
    digits= [ dd1.getUTCFullYear(), dd1.getUTCMonth()+1, dd1.getUTCDate(), dd1.getUTCHours(), dd1.getUTCMinutes(), dd1.getUTCSeconds(), dd1.getUTCMilliseconds()*1000000,
                   dd2.getUTCFullYear(), dd2.getUTCMonth()+1, dd2.getUTCDate(), dd2.getUTCHours(), dd2.getUTCMinutes(), dd2.getUTCSeconds(), dd2.getUTCMilliseconds()*1000000 ];
    $('#timerange').val( formatISO8601Range(digits) );

    topside = PLOTINFO.plots[0].yaxis.top;
    bottomside = PLOTINFO.plots[0].yaxis.bottom;
    leftside = PLOTINFO.plots[0].xaxis.left;
    rightside = PLOTINFO.plots[0].xaxis.right;

    graphwidth = rightside - leftside;
    graphheight = bottomside - topside;
    msecperpx = diffmilliseconds / graphwidth;
    echoGraphParams();
    $('#idstatus').text("ready");
    imgloaded= imgloaded & ( ~2 );
    if ( imgloaded===0 ) {
       $('#progress').attr('src', 'idle-icon.png');
    }
}


var ias;
$('#idstatus').text("v20140111_0754");

var PLOTINFO;

// *****************************************************************************
//
// jQuery code
//
// *****************************************************************************
$(document).ready(function() {
    imgurl = $('#idplot').attr('src');

    echoImgUrl();

    ImageInfo.loadInfo(imgurl, mycallback);

    // **************************************************************************
    // imgAreaSelect()
    // **************************************************************************
    $('#idplot').imgAreaSelect({
        handles: true,
        autoHide: true,
        onSelectEnd: function(img, selection) {
            var x1milliseconds = 0;
            var x2milliseconds = 0;

            console.log('   onselectend--> startdateinmilliseconds=' + startdateinmilliseconds);
            x1milliseconds = (selection.x1 - leftside) * msecperpx + startdateinmilliseconds; // exclude leftside margin pixels
            x2milliseconds = (selection.x2 - leftside) * msecperpx + startdateinmilliseconds; // exclude leftside margin pixels

            if (selection.x2 - selection.x1 > 20) {  // make sure it's deliberate
                setTime(x1milliseconds, x2milliseconds);
            }

        }

    });

});
