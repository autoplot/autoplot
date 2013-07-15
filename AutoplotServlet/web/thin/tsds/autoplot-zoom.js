// *****************************************************************************
//
// Global variables
//
// *****************************************************************************

var imgurl = '';
var zoomurl = '';
var startdate_str = '';
var enddate_str = '';
var startdate = 0; // parseInt(startdate_str);
var enddate = 0; // parseInt(enddate_str);
var width = '';
var height = '';
var column = '';
var row = '';
var epochdate_iso = "1970/01/01";
var millisecondperday = 1000 * 60 * 60 * 24;
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
var x1milliseconds = 0;
var x2milliseconds = 0;
var zoomstartdate = 0;
var zoomenddate = 0;

// ****************************************************************************
//
// Function() definitions
//
// ****************************************************************************

function setAsUTC(date) {
    var utc = new Date(date);
    utc.setMinutes(utc.getMinutes() - utc.getTimezoneOffset());
    return utc;
}
//date.js
function getTimeBetweenDates(startDate, endDate, timeType) {
    var millisecondsPerSecond = 1000;
    var millisecondsPerMinute = 1000 * 60;
    var millisecondsPerHour = 1000 * 60 * 60;
    var millisecondsPerDay = 1000 * 60 * 60 * 24;
    var millisecondsPerMonth = 1000 * 60 * 60 * 24 * 30;
    var millisecondsPerYear = 1000 * 60 * 60 * 24 * 30 * 12;

    var diffTimeInMilliseconds = setAsUTC(endDate) - setAsUTC(startDate) + millisecondsPerDay;  // adjusted for 1 day utc
    //alert('getTimeBetweenDates() : diffTimeInMilliseconds = ' + diffTimeInMilliseconds);
    //console.log('getTimeBetweenDates() : diffTimeInMilliseconds = ' + diffTimeInMilliseconds);

    var diffTime = 0;
    switch (timeType) {
        case "milliseconds":
            diffTime = diffTimeInMilliseconds;
            break;
        case "seconds":
            diffTime = diffTimeInMilliseconds / millisecondsPerSecond;
            break;
        case "minutes":
            diffTime = diffTimeInMilliseconds / millisecondsPerMinute;
            break;
        case "hours":
            diffTime = diffTimeInMilliseconds / millisecondsPerHour;
            break;
        case "days":
            diffTime = diffTimeInMilliseconds / millisecondsPerDay;
            break;
        case "months":
            diffTime = diffTimeInMilliseconds / millisecondsPerMonth;
            break;
        case "years":
            diffTime = diffTimeInMilliseconds / millisecondsPerYear;
            break;
        default:
            alert('ERROR : getTimeBetweenDates() : Undefine get time type');
            console.log('ERROR : getTimeBetweenDates() : Undefine get time type');
    }

    return diffTime;
}

function zeroPad(number, places) {
    var zero = places - number.toString().length + 1;
    return Array(+(zero > 0 && zero)).join("0") + number;
}

function msecondToDate(milliseconds) {
    var time = new Date(milliseconds);
    var year = time.getFullYear();
    var month = time.getMonth() + 1;
    var day = time.getDate();
    var date = year.toString() + zeroPad(month, 2) + zeroPad(day, 2); // zeroPad() will pad '1' = '01'
    //alert('msecondToDate() : msecToDate date = ' + date);
    //console.log('msecondToDate() : msecToDate date = ' + date);
    return date;
}


function buildImgUrl(srcurl, start, end) {
    var outurl = '';
    var inpurl = srcurl;
    var iso8601s= new Date( start ).toISOString();
    var iso8601e= new Date( end ).toISOString(); // toLocalIsoString( end );
    var slt = inpurl.split('&timeRange=');
    outurl = slt[0] + "&timeRange=" + iso8601s + "/" + iso8601e;
    console.log( ''+ start + " - " + end + " " + new Date(start)  ) ;
    console.log( ''+ iso8601s + "/" + iso8601e );
    return outurl;
}

function echoImgUrl() {
    $('#idechourl').text(imgurl);
}


function echoGraphParams() {
    $('#iddates').text('StartDate = ' + PLOTINFO.plots[0].xaxis.min + ' ,    ' + 'EndDate = ' + PLOTINFO.plots[0].xaxis.max );
    $('#idwidthheight').text('framewidth = ' + width + ' , ' +
            'frameheight = ' + height + ' , ' +
            'graphwidth = ' + graphwidth + ' , ' +
            'graphheight = ' + graphheight);
    $('#idcolumn').text('column = ' + column);
    $('#idrow').text('row = ' + row);
}

function echoSetup() {
    // Extract the data.
			
    echoImgUrl();
}

function zoomprev() {
    setTime( startdateinmilliseconds - diffmilliseconds, enddateinmilliseconds - diffmilliseconds );
}

function zoomnext() {
    setTime( startdateinmilliseconds + diffmilliseconds, enddateinmilliseconds + diffmilliseconds );
}

function zoomout() {
    setTime( startdateinmilliseconds - diffmilliseconds, enddateinmilliseconds + diffmilliseconds );
}

function testing() {
    setTime( 1104451200000, 1104451200000 + 86400000 );
}

function setTime( startMilliseconds, endMilliseconds ) {
        console.log( 'startdateinmilliseconds='+ startdateinmilliseconds );
        console.log( 'diffmilliseconds='+ diffmilliseconds );
        console.log( 'mod86400000/3600000= ' + ( ( startdateinmilliseconds % 86400000) /3600000 ) );
        // convert milliseconds to iso date in yyyymmdd
        //alert('imgAreaSelect() : ' + 'zoomstartdate = ' + zoomstartdate + '   ' + 'zoomenddate = ' + zoomenddate);
        //console.log('imgAreaSelect() : ' + 'zoomstartdate = ' + zoomstartdate + '   ' + 'zoomenddate = ' + zoomenddate);
        console.log('PLOTINFO.plots[0].xaxis.min,max=' + PLOTINFO.plots[0].xaxis.min + '/' + PLOTINFO.plots[0].xaxis.max );
        zoomurl = buildImgUrl(imgurl, startMilliseconds, endMilliseconds );
        n = zoomurl.length;
        zoomurlc = zoomurl.substring(0, 30) + '...' + zoomurl.substring(n - 20);
        $('#idstatus').text("loading " + zoomurlc + " ...");
        //alert('imgAreaSelect() : ' + 'zoomurl = ' + zoomurl);
        console.log('imgAreaSelect() : ' + 'zoomurl = ' + zoomurl);
        $('#idplot').attr('src', imgurl);
        $('#idplot').attr('src', zoomurl);
        $('#progress').attr('src','spinner.gif');

        //alert('imgAreaSelect() : ' + 'done');
        //console.log('imgAreaSelect() : ' + 'done');

        // update imgurl
        imgurl = zoomurl;
        startdateinmilliseconds= startMilliseconds;
        enddateinmilliseconds= endMilliseconds;
        diffmilliseconds = enddateinmilliseconds - startdateinmilliseconds;
        msecperpx = diffmilliseconds / graphwidth;

        ImageInfo.loadInfo( imgurl, mycallback);
        console.log( '--> startdateinmilliseconds='+ startdateinmilliseconds );        

}

// Callback function for when metadata extracted
function mycallback() {
    splotInfo = ImageInfo.getField( imgurl, "data")['plotInfo'];
    PLOTINFO = $.parseJSON(splotInfo);

    startdateinmilliseconds = setAsUTC(PLOTINFO.plots[0].xaxis.min) - setAsUTC(epochdate_iso); // + millisecondperday; // adjusted for 1 day utc 
    enddateinmilliseconds = setAsUTC(PLOTINFO.plots[0].xaxis.max) - setAsUTC(epochdate_iso); // + millisecondperday; // adjusted for 1 day utc
    diffmilliseconds = enddateinmilliseconds - startdateinmilliseconds;


    topside= PLOTINFO.plots[0].yaxis.top;
    bottomside= PLOTINFO.plots[0].yaxis.bottom;
    leftside= PLOTINFO.plots[0].xaxis.left;
    rightside= PLOTINFO.plots[0].xaxis.right;

    graphwidth = rightside - leftside;
    graphheight = bottomside - topside;    
    msecperpx = diffmilliseconds / graphwidth;  
    echoGraphParams();
    $('#idstatus').text("ready");
    var p= $('#progress');
    p.attr('src','idle-icon.png');
}    


var ias;
$('#idstatus').text("v20130709_1224");

var PLOTINFO;

// *****************************************************************************
//
// jQuery code
//
// *****************************************************************************
$(document).ready(function() {
    imgurl = $('#idplot').attr('src');

    echoSetup();
    
    ImageInfo.loadInfo( imgurl, mycallback);

    // **************************************************************************
    // imgAreaSelect()
    // **************************************************************************
    $('#idplot').imgAreaSelect({
        handles: true,
        autoHide: true,
        onSelectEnd: function(img, selection) {

            //alert('imgAreaSelect() : ' + 'Selected graph area is valid');
            //console.log('imgAreaSelect() : ' + 'Selected graph area is valid');

            // ***** no y-axis computing for simulation *****
            // compute the x-axis new zoom-in startdate and enddate
            // i.e., work with column components: x1 and x2
            // convert x1 in px to startdate in yyyymmdd

            x1milliseconds = (selection.x1 - leftside) * msecperpx + startdateinmilliseconds; // exclude leftside margin pixels
            x2milliseconds = (selection.x2 - leftside) * msecperpx + startdateinmilliseconds; // exclude leftside margin pixels
            //alert('imgAreaSelect() : ' + 'x1milliseconds = ' + x1milliseconds + '   ' + 'x2milliseconds = ' + x2milliseconds);
            //console.log('imgAreaSelect() : ' + 'x1milliseconds = ' + x1milliseconds + '   ' + 'x2milliseconds = ' + x2milliseconds);

            setTime( x1milliseconds, x2milliseconds );
            
            //alert('document.ready() : ' + 'done');
            //console.log('document.ready() : ' + 'done');
        }

    });

});