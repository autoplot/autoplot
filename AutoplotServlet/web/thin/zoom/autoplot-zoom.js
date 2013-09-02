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
var center= undefined;

// ****************************************************************************
//
// Function() definitions
//
// ****************************************************************************

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
    $('#idwidthheight').text('');
}

/**
 * scan to the previous interval.
 */
function scanprev() {
    setTime( startdateinmilliseconds - diffmilliseconds, enddateinmilliseconds - diffmilliseconds );
}

/**
 * scan to the next interval
 */
function scannext() {
    setTime( startdateinmilliseconds + diffmilliseconds, enddateinmilliseconds + diffmilliseconds );
}

/**
 * zoom out so the plotted range is 3 times the width.
 */
function zoomout() {
    setTime( startdateinmilliseconds - diffmilliseconds, enddateinmilliseconds + diffmilliseconds );
}

/**
 * allow the URI to be reset
 */
function refresh() {
    setTime( startdateinmilliseconds, enddateinmilliseconds );
}

/**
 * the current URL to set.  The timerange is reset by appending to this "timeRange=" + iso8601s + "/" + iso8601e;
 */
function resetUrl( url ) {
    $('#progress').attr('src','spinner.gif');
    imgurl= url;
    ImageInfo.loadInfo( imgurl, mycallback);
    document.getElementById('idplot').src= imgurl;
}

/**
 * zoom in to the middle third.
 * @returns {undefined}
 */
function zoomin() {
    third= ( enddateinmilliseconds - startdateinmilliseconds ) / 3;
    setTime( startdateinmilliseconds + third, enddateinmilliseconds - third );
}

/**
 * center to the focus position
 */
function centerFocus() {
    if ( typeof center==="undefined" ) {
        $("#info").html('click on the plot to set focus position');
    } else {
        half= ( enddateinmilliseconds - startdateinmilliseconds ) / 2;
        setTime( center - half, center + half );
    }
}


function testing() {
    setTime( 1104451200000, 1104451200000 + 86400000 );
}

function clickshift(subEvent) {
    
	plotInfo = PLOTINFO;  // there's just the one...

        console.log(plotInfo);

	var info = $(subEvent.target).attr("info");
	if (plotInfo == -1) {
		$("#info" ).html('No metadata found.');
		return;
	}

	var xx = subEvent.offsetX || ( subEvent.pageX - subEvent.target.offsetLeft );
        var yy = subEvent.offsetY || ( subEvent.pageY - subEvent.target.offsetTop );

	//console.log(subEvent);
	var found= false;
	for ( i=0; i<plotInfo.plots.length; i++ ) {
		var p= plotInfo.plots[i];
		//console.log(p)
		if ( p.xaxis.left<=xx && xx<p.xaxis.right && p.yaxis.top<=yy && yy<p.yaxis.bottom ) {
			l= p.xaxis.right - p.xaxis.left;
			
			if ( p.xaxis.units=='UTC' ) {
				dmin= Date.parse(p.xaxis.min);
				dmax= Date.parse(p.xaxis.max);    
				datax= ( ( xx-p.xaxis.left ) * dmax + ( p.xaxis.right - xx ) * dmin ) / l;
				datax= new Date( datax ).toJSON();
			} else {
				if ( p.xaxis.type=='log' ) {
					oo= ( ( p.xaxis.right - xx ) / l );
					zz=  Math.log( p.xaxis.max / p.xaxis.min );
					datax= Math.exp( Math.log( p.xaxis.min ) + ( ( xx - p.xaxis.left ) / l ) * Math.log( p.xaxis.max / p.xaxis.min ) );
				} else {
					datax= ( ( xx-p.xaxis.left ) * p.xaxis.min + ( xx - p.xaxis.left ) * p.xaxis.max ) / l;
				}
			}
			l= p.yaxis.bottom - p.yaxis.top;
			if ( p.yaxis.type=='log' ) {
				datay= Math.exp( Math.log( p.yaxis.min ) + ( ( p.yaxis.bottom - yy ) / l ) * Math.log( p.yaxis.max / p.yaxis.min ) );
			} else {
				datay= ( ( yy-p.yaxis.top ) * p.yaxis.min + ( p.yaxis.bottom - yy ) * p.yaxis.max ) / l;
			}
			
			$("#info").html('x:'+datax + ' y:'+datay);
                        //'x:' + xx + ' y:' + yy + ' np:' + plotInfo.numberOfPlots + ' ip:'+i + 
			found=true;
		}
	}
	if (!found) {
		$("#info").html('outside axis bounds');
	} else {
                center= new Date( datax ).getTime();
        }
       
}


function setTime( startMilliseconds, endMilliseconds ) {
        console.log( '==setTime()==' );
        console.log( '    startdateinmilliseconds='+ startMilliseconds );
        console.log( '    diffmilliseconds='+ ( endMilliseconds-startMilliseconds ) );
        console.log( '    mod86400000/3600000= ' + ( ( startMilliseconds % 86400000) /3600000 ) );
        console.log('PLOTINFO.plots[0].xaxis.min,max=' + PLOTINFO.plots[0].xaxis.min + '/' + PLOTINFO.plots[0].xaxis.max );
        zoomurl = buildImgUrl(imgurl, startMilliseconds, endMilliseconds );
        n = zoomurl.length;
        zoomurlc = zoomurl.substring(0, 30) + '...' + zoomurl.substring(n - 20);
        $('#idstatus').text("loading " + zoomurlc + " ...");
        console.log('imgAreaSelect() : ' + 'zoomurl = ' + zoomurl);
        $('#idplot').attr('src', imgurl);
        $('#idplot').attr('src', zoomurl);
        $('#progress').attr('src','spinner.gif');

        // update imgurl
        imgurl = zoomurl;
        startdateinmilliseconds= startMilliseconds;
        enddateinmilliseconds= endMilliseconds;
        diffmilliseconds = enddateinmilliseconds - startdateinmilliseconds;
        msecperpx = diffmilliseconds / graphwidth;
        
        echoImgUrl();
        
        ImageInfo.loadInfo( imgurl, mycallback);
        console.log( '--> startdateinmilliseconds='+ startdateinmilliseconds );        

}

// Callback function for when metadata extracted
function mycallback() {
    splotInfo = ImageInfo.getField( imgurl, "data")['plotInfo'];
    PLOTINFO = $.parseJSON(splotInfo);
    console.log( '   mycallback--> startdateinmilliseconds='+ startdateinmilliseconds );        
    startdateinmilliseconds = new Date(PLOTINFO.plots[0].xaxis.min).getTime();
    enddateinmilliseconds = new Date(PLOTINFO.plots[0].xaxis.max).getTime();
    diffmilliseconds = enddateinmilliseconds - startdateinmilliseconds;
    console.log( '   mycallback--> startdateinmilliseconds='+ startdateinmilliseconds + " (exit)");        


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

    echoImgUrl();
    
    ImageInfo.loadInfo( imgurl, mycallback);

    // **************************************************************************
    // imgAreaSelect()
    // **************************************************************************
    $('#idplot').imgAreaSelect({
        handles: true,
        autoHide: true,
        onSelectEnd: function(img, selection) {
            var x1milliseconds = 0;
            var x2milliseconds = 0;

            console.log( '   onselectend--> startdateinmilliseconds='+ startdateinmilliseconds );  
            x1milliseconds = (selection.x1 - leftside) * msecperpx + startdateinmilliseconds; // exclude leftside margin pixels
            x2milliseconds = (selection.x2 - leftside) * msecperpx + startdateinmilliseconds; // exclude leftside margin pixels

            setTime( x1milliseconds, x2milliseconds );
            
        }

    });

});