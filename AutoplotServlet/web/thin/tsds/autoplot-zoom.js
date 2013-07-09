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
var startyear_str = '';
var startmonth_str = '';
var startday_str = '';
var endyear_str = '';
var endmonth_str = '';
var endday_str = '';
var epochdate_iso = "1970/01/01";
var startdate_iso = '';
var enddate_iso = '';
var millisecondperday = 1000 * 60 * 60 * 24;
var startdateinmilliseconds = 0.0;
var enddateinmilliseconds = 0.0;
var diffmilliseconds = 0.0;
var diffseconds = 0.0;
var diffminutes = 0.0;
var diffhours = 0.0;
var diffdays = 0.0;
var diffmonths = 0.0;
var diffyears = 0.0;
var splitedcol1 = '';
var splitedcol2 = '';
var splitedcol3 = '';
var splitedrow1 = '';
var splitedrow2 = '';
var splitedrow3 = '';
var leftcolmargin = '';
var percentcolmargin = '';
var rightcolmargin = '';
var toprowmargin = '';
var percentrowmargin = '';
var bottomrowmargin = '';
var emperpx = 8; // 1 em = 8 px or pixel
var leftside = 0; // parseInt(leftcolmargin) * emperpx;
var rightside = 0; // (parseInt(width) * parseInt(percentcolmargin) / 100) + (parseInt(rightcolmargin) * emperpx);
var topside = 0; // parseInt(toprowmargin) * emperpx;
var bottomside = 0; // (parseInt(height) * parseInt(percentrowmargin) / 100) + (parseInt(bottomrowmargin) * emperpx);
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

function formatDates() {
    // convert date in integer to string
    startyear_str = startdate_str.substring(0, 4);
    startmonth_str = startdate_str.substring(4, 6);
    startday_str = startdate_str.substring(6, 8);
    endyear_str = enddate_str.substring(0, 4);
    endmonth_str = enddate_str.substring(4, 6);
    endday_str = enddate_str.substring(6, 8);

    // formulate date in iso format
    startdate_iso = startyear_str + '/' + startmonth_str + '/' + startday_str;
    enddate_iso = endyear_str + '/' + endmonth_str + '/' + endday_str;

    // baseline startdate time in milliseconds since epoch date or 1970-01-01
    startdateinmilliseconds = setAsUTC(startdate_iso) - setAsUTC(epochdate_iso); // + millisecondperday; // adjusted for 1 day utc 
    enddateinmilliseconds = setAsUTC(enddate_iso) - setAsUTC(epochdate_iso); // + millisecondperday; // adjusted for 1 day utc

}

function calcTimeDiff() {
    // getTimeBetweenDates
    diffmilliseconds = enddateinmilliseconds - startdateinmilliseconds;
    
    diffseconds = getTimeBetweenDates(startdate_iso, enddate_iso, "seconds");
    diffminutes = getTimeBetweenDates(startdate_iso, enddate_iso, "minutes");
    diffhours = getTimeBetweenDates(startdate_iso, enddate_iso, "hours");
    diffdays = getTimeBetweenDates(startdate_iso, enddate_iso, "days");
    diffmonths = getTimeBetweenDates(startdate_iso, enddate_iso, "months");
    diffyears = getTimeBetweenDates(startdate_iso, enddate_iso, "years");

}

function calcGraphMargins() {
    // extract border margins graph area
    splitedcol1 = column.split('em,');
    splitedcol2 = splitedcol1[1].split('%');
    splitedcol3 = splitedcol2[1].split('e');
    splitedrow1 = row.split('em,');
    splitedrow2 = splitedrow1[1].split('%');
    splitedrow3 = splitedrow2[1].split('e');

    leftcolmargin = splitedcol1[0];
    percentcolmargin = splitedcol2[0];
    rightcolmargin = splitedcol3[0];
    toprowmargin = splitedrow1[0];
    percentrowmargin = splitedrow2[0];
    bottomrowmargin = splitedrow3[0];

    // compute border graph area
    leftside = parseInt(leftcolmargin) * emperpx;
    rightside = (parseInt(width) * parseInt(percentcolmargin) / 100) + (parseInt(rightcolmargin) * emperpx);
    topside = parseInt(toprowmargin) * emperpx;
    bottomside = (parseInt(height) * parseInt(percentrowmargin) / 100) + (parseInt(bottomrowmargin) * emperpx);
    graphwidth = rightside - leftside;
    graphheight = bottomside - topside;

    // interpolate per pixel along graphwidth with diffmilliseconds
    msecperpx = diffmilliseconds / graphwidth;
    //alert('calcGraphMargins() : ' + 'each pixel = ' + msecperpx );
    console.log('calcGraphMargins() : ' + 'each pixel = ' + msecperpx );
}

function echoGraphParams() {
    $('#iddates').text('StartDate = ' + startdate + ' ,    ' + 'EndDate = ' + enddate);
    $('#idwidthheight').text('framewidth = ' + width + ' , ' +
            'frameheight = ' + height + ' , ' +
            'graphwidth = ' + graphwidth + ' , ' +
            'graphheight = ' + graphheight);
    $('#idcolumn').text('column = ' + column);
    $('#idrow').text('row = ' + row);
    $('#iddifftime').text('DiffSeconds = ' + diffseconds + '     ' + 'DiffDays = ' + diffdays);
}

function echoSetup() {
    // Extract the data.
			
    echoImgUrl();
    parseImgUrl();
    formatDates();
    calcTimeDiff();
    calcGraphMargins();
    echoGraphParams();
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