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
var millisecondperday = 1000*60*60*24;
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
    var millisecondsPerSecond	= 1000;
    var millisecondsPerMinute	= 1000 * 60;
    var millisecondsPerHour	= 1000 * 60 * 60;
    var millisecondsPerDay		= 1000 * 60 * 60 * 24;
    var millisecondsPerMonth	= 1000 * 60 * 60 * 24 * 30;
    var millisecondsPerYear	= 1000 * 60 * 60 * 24 * 30 * 12;
   
    var diffTimeInMilliseconds = setAsUTC(endDate) - setAsUTC(startDate) + millisecondsPerDay;  // adjusted for 1 day utc
    //alert('getTimeBetweenDates() : diffTimeInMilliseconds = ' + diffTimeInMilliseconds);
    //console.log('getTimeBetweenDates() : diffTimeInMilliseconds = ' + diffTimeInMilliseconds);
    
    var diffTime = 0;
	 switch(timeType){
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

function zeroPad(number, places){
    var zero = places - number.toString().length + 1;
    return Array(+(zero > 0 && zero)).join("0") + number;
}

function msecondToDate(milliseconds){
    var time = new Date(milliseconds);
    var year = time.getFullYear();
    var month = time.getMonth() + 1;
    var day = time.getDate();
    var date = year.toString() + zeroPad(month, 2) + zeroPad(day, 2); // zeroPad() will pad '1' = '01'
    //alert('msecondToDate() : msecToDate date = ' + date);
    //console.log('msecondToDate() : msecToDate date = ' + date);
    return date;
}

function parseImgUrlItem(srcurl, itemType){
    var item = '';
    var inpurl = srcurl;
    var slt1 = '';
    var slt2 = '';
    var slt3 = '';
    var slt4 = '';
    
    switch(itemType){
        case "startdate":
            slt1 = inpurl.split('StartDate%3D');
            /*
            alert('parseImgUrlItem() : slt1[0] = ' + slt1[0]);
            alert('parseImgUrlItem() : slt1[1] = ' + slt1[1]);
            */
            /*
            console.log('parseImgUrlItem() : slt1[0] = ' + slt1[0]);
            console.log('parseImgUrlItem() : slt1[1] = ' + slt1[1]);
            */
            slt2 = slt1[1].split('%26EndDate%3D');
            /*
            alert('parseImgUrlItem() : slt2[0] = ' + slt2[0]);
            alert('parseImgUrlItem() : slt2[1] = ' + slt2[1]);
            */
            /*
            console.log('parseImgUrlItem() : slt2[0] = ' + slt2[0]);
            console.log('parseImgUrlItem() : slt2[1] = ' + slt2[1]);
            */
            item = slt2[0];
            //alert('parseImgUrlItem() : itemType = ' + itemType + '   item = ' + item);
            //console.log('parseImgUrlItem() : itemType = ' + itemType + '   item = ' + item);
            break;
        case "enddate":
            slt1 = inpurl.split('EndDate%3D');
            /*
            alert('parseImgUrlItem() : slt1[0] = ' + slt1[0]);
            alert('parseImgUrlItem() : slt1[1] = ' + slt1[1]);
            */
            /*
            console.log('parseImgUrlItem() : slt1[0] = ' + slt1[0]);
            console.log('parseImgUrlItem() : slt1[1] = ' + slt1[1]);
            */
            slt2 = slt1[1].split('%26ext');
            /*
            alert('parseImgUrlItem() : slt2[0] = ' + slt2[0]);
            alert('parseImgUrlItem() : slt2[1] = ' + slt2[1]);
            */
            /*
            console.log('parseImgUrlItem() : slt2[0] = ' + slt2[0]);
            console.log('parseImgUrlItem() : slt2[1] = ' + slt2[1]);
            */
            item = slt2[0];
            //alert('parseImgUrlItem() : itemType = ' + itemType + '   item = ' + item);
            //console.log('parseImgUrlItem() : itemType = ' + itemType + '   item = ' + item);
            break;
        case "width":
            slt1 = inpurl.split('width=');
            /*
            alert('parseImgUrlItem() : slt1[0] = ' + slt1[0]);
            alert('parseImgUrlItem() : slt1[1] = ' + slt1[1]);
            */
            /*
            console.log('parseImgUrlItem() : slt1[0] = ' + slt1[0]);
            console.log('parseImgUrlItem() : slt1[1] = ' + slt1[1]);
            */
            slt2 = slt1[1].split('&height=');
            /*
            alert('parseImgUrlItem() : slt2[0] = ' + slt2[0]);
            alert('parseImgUrlItem() : slt2[1] = ' + slt2[1]);
            */
            /*
            console.log('parseImgUrlItem() : slt2[0] = ' + slt2[0]);
            console.log('parseImgUrlItem() : slt2[1] = ' + slt2[1]);
            */
            item = slt2[0];
            //alert('parseImgUrlItem() : itemType = ' + itemType + '   item = ' + item);
            //console.log('parseImgUrlItem() : itemType = ' + itemType + '   item = ' + item);
            break;
        case "height":
            slt1 = inpurl.split('height=');
            /*
            alert('parseImgUrlItem() : slt1[0] = ' + slt1[0]);
            alert('parseImgUrlItem() : slt1[1] = ' + slt1[1]);
            */
            /*
            console.log('parseImgUrlItem() : slt1[0] = ' + slt1[0]);
            console.log('parseImgUrlItem() : slt1[1] = ' + slt1[1]);
            */
            slt2 = slt1[1].split('&column=');
            /*
            alert('parseImgUrlItem() : slt2[0] = ' + slt2[0]);
            alert('parseImgUrlItem() : slt2[1] = ' + slt2[1]);
            */
            /*
            console.log('parseImgUrlItem() : slt2[0] = ' + slt2[0]);
            console.log('parseImgUrlItem() : slt2[1] = ' + slt2[1]);
            */
            item = slt2[0];
            //alert('parseImgUrlItem() : itemType = ' + itemType + '   item = ' + item);
            //console.log('parseImgUrlItem() : itemType = ' + itemType + '   item = ' + item);
            break;
        case "column":
            slt1 = inpurl.split('column=');
            /*
            alert('parseImgUrlItem() : slt1[0] = ' + slt1[0]);
            alert('parseImgUrlItem() : slt1[1] = ' + slt1[1]);
            */
            /*
            console.log('parseImgUrlItem() : slt1[0] = ' + slt1[0]);
            console.log('parseImgUrlItem() : slt1[1] = ' + slt1[1]);
            */
            slt2 = slt1[1].split('&row=');
            /*
            alert('parseImgUrlItem() : slt2[0] = ' + slt2[0]);
            alert('parseImgUrlItem() : slt2[1] = ' + slt2[1]);
            */
            /*
            console.log('parseImgUrlItem() : slt2[0] = ' + slt2[0]);
            console.log('parseImgUrlItem() : slt2[1] = ' + slt2[1]);
            */
            slt3 = slt2[0].split('%2C');
            /*
            alert('parseImgUrlItem() : slt3[0] = ' + slt3[0]);
            alert('parseImgUrlItem() : slt3[1] = ' + slt3[1]);
            */
            /*
            console.log('parseImgUrlItem() : slt3[0] = ' + slt3[0]);
            console.log('parseImgUrlItem() : slt3[1] = ' + slt3[1]);
            */
            item = slt3[0];
            slt4 = slt3[1].split('%25');
            /*
            alert('parseImgUrlItem() : slt4[0] = ' + slt4[0]);
            alert('parseImgUrlItem() : slt4[1] = ' + slt4[1]);
            */
            /*
            console.log('parseImgUrlItem() : slt4[0] = ' + slt4[0]);
            console.log('parseImgUrlItem() : slt4[1] = ' + slt4[1]);
            */
            item = item + ',' + slt4[0] + '%' + slt4[1];
            //alert('parseImgUrlItem() : itemType = ' + itemType + '   item = ' + item);
            //console.log('parseImgUrlItem() : itemType = ' + itemType + '   item = ' + item);
            break;
        case "row":
            slt1 = inpurl.split('row=');
            /*
            alert('parseImgUrlItem() : slt1[0] = ' + slt1[0]);
            alert('parseImgUrlItem() : slt1[1] = ' + slt1[1]);
            */
            /*
            console.log('parseImgUrlItem() : slt1[0] = ' + slt1[0]);
            console.log('parseImgUrlItem() : slt1[1] = ' + slt1[1]);
            */
            slt2 = slt1[1].split('&renderType=');
            /*
            alert('parseImgUrlItem() : slt2[0] = ' + slt2[0]);
            alert('parseImgUrlItem() : slt2[1] = ' + slt2[1]);
            */
            /*
            console.log('parseImgUrlItem() : slt2[0] = ' + slt2[0]);
            console.log('parseImgUrlItem() : slt2[1] = ' + slt2[1]);
            */
            slt3 = slt2[0].split('%2C');
            /*
            alert('parseImgUrlItem() : slt3[0] = ' + slt3[0]);
            alert('parseImgUrlItem() : slt3[1] = ' + slt3[1]);
            */
            /*
            console.log('parseImgUrlItem() : slt3[0] = ' + slt3[0]);
            console.log('parseImgUrlItem() : slt3[1] = ' + slt3[1]);
            */
            item = slt3[0];
            slt4 = slt3[1].split('%25');
            /*
            alert('parseImgUrlItem() : slt4[0] = ' + slt4[0]);
            alert('parseImgUrlItem() : slt4[1] = ' + slt4[1]);
            */
            /*
            console.log('parseImgUrlItem() : slt4[0] = ' + slt4[0]);
            console.log('parseImgUrlItem() : slt4[1] = ' + slt4[1]);
            */
            item = item + ',' + slt4[0] + '%' + slt4[1];
            //alert('parseImgUrlItem() : itemType = ' + itemType + '   item = ' + item);
            //console.log('parseImgUrlItem() : itemType = ' + itemType + '   item = ' + item);
            break;
        default:
            alert('ERROR : parseImgUrlItem() : Undefine parse item type');
    }
    
    return item;
}

function buildImgUrl(srcurl, startdate, enddate){
    var outurl = '';
    var inpurl = srcurl;
    
    var slt = inpurl.split('&timeRange=');
    outurl= slt[0] +  "&timeRange="+startdate + "+to+"+enddate;
    
    return outurl;
}

function echoImgUrl(){
    $('#idechourl').text(imgurl);
}

function parseImgUrl(){
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

function formatDates(){
	// convert date in integer to string
	startyear_str = startdate_str.substring(0,4);
	startmonth_str = startdate_str.substring(4,6);
	startday_str = startdate_str.substring(6,8);
	endyear_str = enddate_str.substring(0,4);
	endmonth_str = enddate_str.substring(4,6);
	endday_str = enddate_str.substring(6,8);
	
	/*
	alert('formatDates() : ' + 'startyear_str = ' + startyear_str);
	alert('formatDates() : ' + 'startmonth_str = ' + startmonth_str);
	alert('formatDates() : ' + 'startday_str = ' + startday_str);
	alert('formatDates() : ' + 'endyear_str = ' + endyear_str);
	alert('formatDates() : ' + 'endmonth_str = ' + endmonth_str);
	alert('formatDates() : ' + 'endday_str = ' + endday_str);
	*/
	/*
	console.log('formatDates() : ' + 'startyear_str = ' + startyear_str);
	console.log('formatDates() : ' + 'startmonth_str = ' + startmonth_str);
	console.log('formatDates() : ' + 'startday_str = ' + startday_str);
	console.log('formatDates() : ' + 'endyear_str = ' + endyear_str);
	console.log('formatDates() : ' + 'endmonth_str = ' + endmonth_str);
	console.log('formatDates() : ' + 'endday_str = ' + endday_str);
	*/
	
	// formulate date in iso format
	startdate_iso = startyear_str + '/' + startmonth_str + '/' + startday_str;
	enddate_iso = endyear_str + '/' + endmonth_str + '/' + endday_str;
	
	/*
	alert('formatDates() : startdate_iso = ' + startdate_iso);
	alert('formatDates() : enddate_iso = ' + enddate_iso);
	*/
	/*
	console.log('formatDates() : startdate_iso = ' + startdate_iso);
	console.log('formatDates() : enddate_iso = ' + enddate_iso);
	*/
	
	// baseline startdate time in milliseconds since epoch date or 1970-01-01
	startdateinmilliseconds = setAsUTC(startdate_iso) - setAsUTC(epochdate_iso); // + millisecondperday; // adjusted for 1 day utc 
	enddateinmilliseconds	 = setAsUTC(enddate_iso) - setAsUTC(epochdate_iso); // + millisecondperday; // adjusted for 1 day utc
	
	/*
	alert('formatDates() : ' + 'startdateinmilliseconds = ' + startdateinmilliseconds);
	alert('formatDates() : ' + 'enddateinmilliseconds = ' + enddateinmilliseconds);
	alert('formatDates() : ' + 'startdate fr ms = ' + msecondToDate(startdateinmilliseconds));
	alert('formatDates() : ' + 'enddate fr ms = ' + msecondToDate(enddateinmilliseconds));
	*/
	/*
	console.log('formatDates() : ' + 'startdateinmilliseconds = ' + startdateinmilliseconds);
	console.log('formatDates() : ' + 'enddateinmilliseconds = ' + enddateinmilliseconds);
	console.log('formatDates() : ' + 'startdate fr ms = ' + msecondToDate(startdateinmilliseconds));
	console.log('formatDates() : ' + 'enddate fr ms = ' + msecondToDate(enddateinmilliseconds));
	*/
}

function calcTimeDiff(){
    // getTimeBetweenDates
	diffmilliseconds = getTimeBetweenDates(startdate_iso, enddate_iso, "milliseconds");
	diffseconds = getTimeBetweenDates(startdate_iso, enddate_iso, "seconds");
	diffminutes = getTimeBetweenDates(startdate_iso, enddate_iso, "minutes");
	diffhours = getTimeBetweenDates(startdate_iso, enddate_iso, "hours");
	diffdays = getTimeBetweenDates(startdate_iso, enddate_iso, "days");
	diffmonths = getTimeBetweenDates(startdate_iso, enddate_iso, "months");
	diffyears = getTimeBetweenDates(startdate_iso, enddate_iso, "years");
	
	/*
	alert('calcTimeDiff() : ' + 'milliseconds between diff dates = ' + diffmilliseconds);
	alert('calcTimeDiff() : ' + 'seconds between diff dates = ' + diffseconds);
	alert('calcTimeDiff() : ' + 'minutes between diff dates = ' + diffminutes);
	alert('calcTimeDiff() : ' + 'hours between diff dates = ' + diffhours);
	alert('calcTimeDiff() : ' + 'days between diff dates = ' + diffdays);
	alert('calcTimeDiff() : ' + 'months between diff dates = ' + diffmonths);
	alert('calcTimeDiff() : ' + 'years between diff dates = ' + diffyears);
	*/
	/*
	console.log('calcTimeDiff() : ' + 'milliseconds between diff dates = ' + diffmilliseconds);
	console.log('calcTimeDiff() : ' + 'seconds between diff dates = ' + diffseconds);
	console.log('calcTimeDiff() : ' + 'minutes between diff dates = ' + diffminutes);
	console.log('calcTimeDiff() : ' + 'hours between diff dates = ' + diffhours);
	console.log('calcTimeDiff() : ' + 'days between diff dates = ' + diffdays);
	console.log('calcTimeDiff() : ' + 'months between diff dates = ' + diffmonths);
	console.log('calcTimeDiff() : ' + 'years between diff dates = ' + diffyears);
	*/
}

function calcGraphMargins(){
    // extract border margins graph area
	splitedcol1 = column.split('em,');
	splitedcol2 = splitedcol1[1].split('%');
	splitedcol3 = splitedcol2[1].split('e');
	splitedrow1 = row.split('em,');
	splitedrow2 = splitedrow1[1].split('%');
	splitedrow3 = splitedrow2[1].split('e');
	
	/*
	alert('calcGraphMargins() : ' + splitedcol1[0]+'   ' +splitedcol1[1]);
	alert('calcGraphMargins() : ' + splitedcol2[0]+'   ' +splitedcol2[1]);
	alert('calcGraphMargins() : ' + splitedcol3[0]+'   ' +splitedcol3[1]);
	alert('calcGraphMargins() : ' + splitedrow1[0]+'   ' +splitedrow1[1]);
	alert('calcGraphMargins() : ' + splitedrow2[0]+'   ' +splitedrow2[1]);
	alert('calcGraphMargins() : ' + splitedrow3[0]+'   ' +splitedrow3[1]);
	*/
	/*
	console.log('calcGraphMargins() : ' + splitedcol1[0]+'   ' +splitedcol1[1]);
	console.log('calcGraphMargins() : ' + splitedcol2[0]+'   ' +splitedcol2[1]);
	console.log('calcGraphMargins() : ' + splitedcol3[0]+'   ' +splitedcol3[1]);
	console.log('calcGraphMargins() : ' + splitedrow1[0]+'   ' +splitedrow1[1]);
	console.log('calcGraphMargins() : ' + splitedrow2[0]+'   ' +splitedrow2[1]);
	console.log('calcGraphMargins() : ' + splitedrow3[0]+'   ' +splitedrow3[1]);
	*/
	
	leftcolmargin = splitedcol1[0];
	percentcolmargin = splitedcol2[0];
	rightcolmargin = splitedcol3[0];
	toprowmargin = splitedrow1[0];
	percentrowmargin = splitedrow2[0];
	bottomrowmargin = splitedrow3[0];
	
	/*
	alert('calcGraphMargins() : ' + 'leftcolmargin = ' + leftcolmargin);
	alert('calcGraphMargins() : ' + 'percentcolmargin = ' + percentcolmargin);
	alert('calcGraphMargins() : ' + 'rightcolmargin = ' + rightcolmargin);
	alert('calcGraphMargins() : ' + 'toprowmargin = ' + toprowmargin);
	alert('calcGraphMargins() : ' + 'percentrowmargin = ' + percentrowmargin);
	alert('calcGraphMargins() : ' + 'bottomrowmargin = ' + bottomrowmargin);
	*/
	/*
	console.log('calcGraphMargins() : ' + 'leftcolmargin = ' + leftcolmargin);
	console.log('calcGraphMargins() : ' + 'percentcolmargin = ' + percentcolmargin);
	console.log('calcGraphMargins() : ' + 'rightcolmargin = ' + rightcolmargin);
	console.log('calcGraphMargins() : ' + 'toprowmargin = ' + toprowmargin);
	console.log('calcGraphMargins() : ' + 'percentrowmargin = ' + percentrowmargin);
	console.log('calcGraphMargins() : ' + 'bottomrowmargin = ' + bottomrowmargin);
	*/
	
	// compute border graph area
	leftside = parseInt(leftcolmargin) * emperpx;
	rightside = (parseInt(width) * parseInt(percentcolmargin) / 100) + (parseInt(rightcolmargin) * emperpx);
	topside = parseInt(toprowmargin) * emperpx;
	bottomside = (parseInt(height) * parseInt(percentrowmargin) / 100) + (parseInt(bottomrowmargin) * emperpx);
	graphwidth = rightside - leftside;
	graphheight = bottomside - topside;
	
	/*
	alert('calcGraphMargins() : ' + 'leftside = ' + leftside);
	alert('calcGraphMargins() : ' + 'rightside = ' + rightside);
	alert('calcGraphMargins() : ' + 'topside = ' + topside);
	alert('calcGraphMargins() : ' + 'bottomside = ' + bottomside);
	alert('calcGraphMargins() : ' + 'graphwidth = ' + adjustedwidth);
	alert('calcGraphMargins() : ' + 'graphheight = ' + adjustedheight);
	*/
	/*
	console.log('calcGraphMargins() : ' + 'leftside = ' + leftside);
	console.log('calcGraphMargins() : ' + 'rightside = ' + rightside);
	console.log('calcGraphMargins() : ' + 'topside = ' + topside);
	console.log('calcGraphMargins() : ' + 'bottomside = ' + bottomside);
	console.log('calcGraphMargins() : ' + 'graphwidth = ' + adjustedwidth);
	console.log('calcGraphMargins() : ' + 'graphheight = ' + adjustedheight);
	*/
	
	// interpolate per pixel along graphwidth with diffmilliseconds
	msecperpx = diffmilliseconds / graphwidth;
	//alert('calcGraphMargins() : ' + 'each pixel = ' + msecperpx );
	//console.log('calcGraphMargins() : ' + 'each pixel = ' + msecperpx );
}

function echoGraphParams(){
    $('#iddates').text('StartDate = ' + startdate + ' ,    ' + 'EndDate = ' + enddate);
	$('#idwidthheight').text('framewidth = ' + width + ' , ' + 
									'frameheight = ' + height + ' , ' + 
									'graphwidth = ' + graphwidth + ' , ' + 
									'graphheight = ' + graphheight);
	$('#idcolumn').text('column = ' + column);
	$('#idrow').text('row = ' + row);
	$('#iddifftime').text('DiffSeconds = ' + diffseconds + '     ' + 'DiffDays = ' + diffdays);
}

function echoSetup(){
    echoImgUrl();
	parseImgUrl();
	formatDates();
	calcTimeDiff();
	calcGraphMargins();
	echoGraphParams();
}

var ias;
$('#idstatus').text( "v1249" );

// *****************************************************************************
//
// jQuery code
//
// *****************************************************************************
$(document).ready(function () {
    imgurl = $('#idplot').attr('src');
    
    echoSetup();
	
	// **************************************************************************
	// imgAreaSelect()
	// **************************************************************************
	$('#idplot').imgAreaSelect({
		handles: true,
                autoHide: true,
        onSelectEnd: function (img, selection) { 
        
        echoSetup();
        
      	/*
      	alert('imgAreaSelect() : ' + '  left column x1 = ' + selection.x1 + 
      			', top row y1 = ' + selection.y1 + 
      			', right column x2 = ' + selection.x2 + 
      			', bottom row y2 = ' + selection.y2 +
      			', selected area width = ' + selection.width + 
      			', selected area height = ' + selection.height);
      	*/
      	/*
      	console.log('imgAreaSelect() : ' + '  left column x1 = ' + selection.x1 + 
      			', top row y1 = ' + selection.y1 + 
      			', right column x2 = ' + selection.x2 + 
      			', bottom row y2 = ' + selection.y2 +
      			', selected area width = ' + selection.width + 
      			', selected area height = ' + selection.height);
      	*/
      	
      		//alert('imgAreaSelect() : ' + 'Selected graph area is valid');
      		//console.log('imgAreaSelect() : ' + 'Selected graph area is valid');
      		
      		// ***** no y-axis computing for simulation *****
      		// compute the x-axis new zoom-in startdate and enddate
      		// i.e., work with column components: x1 and x2
      		// convert x1 in px to startdate in yyyymmdd
      		x1milliseconds = (selection.x1-leftside) * msecperpx + startdateinmilliseconds; // exclude leftside margin pixels
      		x2milliseconds = (selection.x2-leftside) * msecperpx + startdateinmilliseconds; // exclude leftside margin pixels
      		//alert('imgAreaSelect() : ' + 'x1milliseconds = ' + x1milliseconds + '   ' + 'x2milliseconds = ' + x2milliseconds);
      		//console.log('imgAreaSelect() : ' + 'x1milliseconds = ' + x1milliseconds + '   ' + 'x2milliseconds = ' + x2milliseconds);
      		
      		// convert milliseconds to iso date in yyyymmdd
      		zoomstartdate = msecondToDate(x1milliseconds);
      		zoomenddate = msecondToDate(x2milliseconds);
      		//alert('imgAreaSelect() : ' + 'zoomstartdate = ' + zoomstartdate + '   ' + 'zoomenddate = ' + zoomenddate);
      		console.log('imgAreaSelect() : ' + 'zoomstartdate = ' + zoomstartdate + '   ' + 'zoomenddate = ' + zoomenddate);
      		
      		// check selected day range is valid
      		if(parseInt(zoomstartdate) == parseInt(zoomenddate)){
      			
      			alert('imgAreaSelect() : ' + 'Selected date range is invalid');
      			console.log('imgAreaSelect() : ' + 'Selected date range is invalid');
      			
      		} else {
      			// build zoom img src url
      			zoomurl = buildImgUrl(imgurl, zoomstartdate, zoomenddate);
                        n= zoomurl.length
      			zoomurlc= zoomurl.substring(0,30) + '...' + zoomurl.substring(n-20);
                        $('#idstatus').text( "loading " + zoomurlc + " ...");
      			//alert('imgAreaSelect() : ' + 'zoomurl = ' + zoomurl);
      			console.log('imgAreaSelect() : ' + 'zoomurl = ' + zoomurl);
      			$('#idplot').attr('src', imgurl);
      			$('#idplot').attr('src', zoomurl);
      			//alert('imgAreaSelect() : ' + 'done');
      			//console.log('imgAreaSelect() : ' + 'done');
      			
      			// update imgurl
      			imgurl = zoomurl;
      			echoSetup();
      		}
      		      	
      	//alert('document.ready() : ' + 'done');
      	//console.log('document.ready() : ' + 'done');
		}
	
	});
	
})