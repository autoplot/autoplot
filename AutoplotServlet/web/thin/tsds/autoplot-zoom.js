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

function parseImgUrlItem(srcurl, itemType) {
    var item = '';
    var inpurl = srcurl;
    var slt1 = '';
    var slt2 = '';
    var slt3 = '';
    var slt4 = '';

    switch (itemType) {
        case "startdate":
            slt1 = inpurl.split('StartDate%3D');
            slt2 = slt1[1].split('%26EndDate%3D');
            item = slt2[0];
            break;
        case "enddate":
            slt1 = inpurl.split('EndDate%3D');
            slt2 = slt1[1].split('%26ext');
            item = slt2[0];
            break;
        case "width":
            slt1 = inpurl.split('width=');
            slt2 = slt1[1].split('&height=');
            item = slt2[0];
            break;
        case "height":
            slt1 = inpurl.split('height=');
            slt2 = slt1[1].split('&column=');
            item = slt2[0];
            break;
        case "column":
            slt1 = inpurl.split('column=');
            slt2 = slt1[1].split('&row=');
            slt3 = slt2[0].split('%2C');
            item = slt3[0];
            slt4 = slt3[1].split('%25');
            item = item + ',' + slt4[0] + '%' + slt4[1];
            break;
        case "row":
            slt1 = inpurl.split('row=');
            slt2 = slt1[1].split('&renderType=');
            slt3 = slt2[0].split('%2C');
            item = slt3[0];
            slt4 = slt3[1].split('%25');
            item = item + ',' + slt4[0] + '%' + slt4[1];
            break;
        default:
            alert('ERROR : parseImgUrlItem() : Undefine parse item type');
    }

    return item;
}

function buildImgUrl(srcurl, startdate, enddate) {
    var outurl = '';
    var inpurl = srcurl;

    var slt = inpurl.split('&timeRange=');
    outurl = slt[0] + "&timeRange=" + startdate + "+to+" + enddate;

    return outurl;
}

function echoImgUrl() {
    $('#idechourl').text(imgurl);
}

/**
 * Use this to get the initial parameters.  TODO: This needs to be redone
 * by looking at the Autoplot metadata.
 * @returns {undefined}
 */
function parseImgUrl() {
    startdate_str = parseImgUrlItem(imgurl, "startdate");
    enddate_str = parseImgUrlItem(imgurl, "enddate");
    startdate = parseInt(startdate_str);
    enddate = parseInt(enddate_str);
    width = parseImgUrlItem(imgurl, "width");
    height = parseImgUrlItem(imgurl, "height");
    column = parseImgUrlItem(imgurl, "column");
    row = parseImgUrlItem(imgurl, "row");

    //alert('parseImgUrl() : ' + 'StartDate = ' + startdate + ', ' + 'EndDate = ' + enddate);
    //console.log('parseImgUrl() : ' + 'StartDate = ' + startdate + ', ' + 'EndDate = ' + enddate);
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
    parseImgUrl();
}

var ias;
$('#idstatus').text("v20130709_1010");

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

            // convert milliseconds to iso date in yyyymmdd
            zoomstartdate = msecondToDate(x1milliseconds);
            zoomenddate = msecondToDate(x2milliseconds);
            //alert('imgAreaSelect() : ' + 'zoomstartdate = ' + zoomstartdate + '   ' + 'zoomenddate = ' + zoomenddate);
            //console.log('imgAreaSelect() : ' + 'zoomstartdate = ' + zoomstartdate + '   ' + 'zoomenddate = ' + zoomenddate);
            console.log('PLOTINFO.plots[0].xaxis.min,max=' + PLOTINFO.plots[0].xaxis.min + '/' + PLOTINFO.plots[0].xaxis.max );
            zoomurl = buildImgUrl(imgurl, zoomstartdate, zoomenddate);
            n = zoomurl.length
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
            startdateinmilliseconds= x1milliseconds;
            enddateinmilliseconds= x2milliseconds;
            diffmilliseconds = enddateinmilliseconds - startdateinmilliseconds;
            msecperpx = diffmilliseconds / graphwidth;
            
            ImageInfo.loadInfo( imgurl, mycallback);
            
            //alert('document.ready() : ' + 'done');
            //console.log('document.ready() : ' + 'done');
        }

    });

})