/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.das2.datasource;

import java.text.ParseException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.das2.util.LoggerManager;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.capability.TimeSeriesBrowse;

/**
 *
 * @author jbf
 */
public class Das2ServerTimeSeriesBrowse implements TimeSeriesBrowse {
    DatumRange timeRange;
    Datum resolution;
    String uri;

    private final static Logger logger= LoggerManager.getLogger("apdss.das2server");

    public void setTimeRange(DatumRange dr) {
        timeRange= dr;
    }

    public void setTimeResolution(Datum d) {
        resolution= d;
    }

    public String getURI() {
        URISplit split= URISplit.parse(uri);
        Map<String,String> params= URISplit.parseParams(split.params);

        String stime= timeRange.min().toString().replace(" ", "+");
        String etime= timeRange.max().toString().replace(" ", "+");

        params.put( "start_time", stime );
        params.put( "end_time", etime );
        if ( resolution==null ) {
            params.remove("resolution");
        } else {
            params.put( "resolution", String.valueOf(resolution.doubleValue(Units.seconds)) );
        }

        split.params= URISplit.formatParams(params);

        String suri= URISplit.format( split );
        logger.log(Level.FINER, "tsb getURI->{0}", suri);
        return suri;
        
    }

    public DatumRange getTimeRange() {
        return timeRange;
    }

    public Datum getTimeResolution() {
        return resolution;
    }

    public void setURI(String suri) throws ParseException {
        logger.log(Level.FINER, "tsb setURI {0}", suri);
        this.uri= suri;
        URISplit split= URISplit.parse(suri);
        Map<String,String> params= URISplit.parseParams(split.params);
        String startTime= params.remove( "start_time" );
        String endTime= params.get( "end_time" );
        String sresolution= params.get("resolution");
        if ( startTime!=null && endTime!=null ) {
            timeRange= new DatumRange( Units.us2000.parse(startTime), Units.us2000.parse(endTime) );
        }
        if ( sresolution!=null ) {
            resolution= Units.seconds.parse(sresolution);
        }
    }

    public String blurURI() {
        URISplit split= URISplit.parse(uri);
        Map<String,String> params= URISplit.parseParams(split.params);

        params.remove( "start_time" );
        params.remove( "end_time" );
        params.remove("resolution");

        split.params= URISplit.formatParams(params);

        String suri= URISplit.format( split );
        logger.log(Level.FINER, "tsb blurURI->{0}", suri);
        return suri;
    }
}
