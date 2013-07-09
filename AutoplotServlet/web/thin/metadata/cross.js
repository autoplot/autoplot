function cross(subEvent) {

	// ID of clicked image.
	var canvas = $(subEvent.target).attr("id"); 
	plotInfo = PLOTINFO[canvas];

	var info = $(subEvent.target).attr("info");
	if (plotInfo == -1) {
		$("#"+info).html('No metadata found.');
		return;
	}

	var xx = subEvent.offsetX || ( subEvent.pageX - subEvent.target.offsetLeft );
        var yy = subEvent.offsetY || ( subEvent.pageY - subEvent.target.offsetTop );

	//console.log(subEvent);
	var found= false;
	for ( i=0; i<4; i++ ) {
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
			
			$("#"+info).html('x:' + xx + ' y:' + yy + ' np:' + plotInfo.numberOfPlots + ' ip:'+i + ' datax:'+datax + ' datay:'+datay);
			found=true;
		}
	}
	if (!found) {
		$("#"+info).html('x:' + xx + ' y:' + yy + ' np:' + plotInfo.numberOfPlots);
	}
}
