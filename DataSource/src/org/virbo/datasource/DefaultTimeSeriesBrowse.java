/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource;

import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.util.LoggerManager;
import org.virbo.datasource.capability.TimeSeriesBrowse;

/**
 * Default implementation commonly found, doesn't use resolution.  This
 * uses URISplit.PARAM_TIME_RANGE='timerange' and URISplit.PARAM_TIME_RANGE='resolution'
 * for representing the TSB state.
 * @author jbf
 */
public class DefaultTimeSeriesBrowse implements TimeSeriesBrowse {
    public String uri;
    DatumRange timeRange;
    
    private static final Logger logger= LoggerManager.getLogger("apdss.util");
    
    @Override
    public void setURI(String suri) throws ParseException {
        logger.log(Level.FINE, "setURI {0}", suri );
        this.uri= suri;
        URISplit split= URISplit.parse(uri);
        Map<String,String> params= URISplit.parseParams(split.params);
        String sdr= params.get( URISplit.PARAM_TIME_RANGE );
        if ( sdr!=null && sdr.trim().length()>0 ) {
            timeRange= DatumRangeUtil.parseTimeRange(sdr.replaceAll("\\+", " "));
        }
    }

    @Override
    public String getURI() {
        return this.uri;
    }

    @Override
    public void setTimeRange(DatumRange dr) {
        logger.log(Level.FINE, "setTimeRange {0}", dr);
        URISplit split= URISplit.parse(uri);
        Map<String,String> params= URISplit.parseParams(split.params);
        params.put( URISplit.PARAM_TIME_RANGE, dr.toString().replaceAll(" ","+"));
        if ( split.file!=null && split.file.equals("file:///") ) {
            split.file= null; //grr... TODO: figure out why this is back.  DataSetURI.toURI vs DataSetURI.asUri...
        }
        split.params= URISplit.formatParams(params);
        this.uri= URISplit.format(split);
        timeRange= dr;
    }

    @Override
    public DatumRange getTimeRange() {
        return timeRange;
    }

    @Override
    public void setTimeResolution(Datum d) {
        logger.log(Level.FINE, "setTimeResolution {0} ignores resolution", d );
        // do nothing
    }

    @Override
    public Datum getTimeResolution() {
        return null;
    }

    public static boolean reject( Map map, List<String> problems ) {
        if (!map.containsKey("timerange")) {
            problems.add( TimeSeriesBrowse.PROB_NO_TIMERANGE_PROVIDED );
            return true;
        }
        String timeRange = ((String) map.get("timerange"));
        timeRange= timeRange.replaceAll("\\+"," ");
        if (timeRange.length() < 3) { // P2D is a valid timerange
            problems.add( TimeSeriesBrowse.PROB_NO_TIMERANGE_PROVIDED );
            return true;
        }
        try {
            DatumRange dr= DatumRangeUtil.parseTimeRange(timeRange);
            logger.log(Level.FINEST, "timeRange parses to {0}", dr);
        } catch ( ParseException ex ) {
            problems.add( TimeSeriesBrowse.PROB_PARSE_ERROR_IN_TIMERANGE);
            return true;
        }
        return false;
    }

    @Override
    public String blurURI() {
        URISplit split= URISplit.parse(uri);
        Map<String,String> params= URISplit.parseParams(split.params);
        params.remove( URISplit.PARAM_TIME_RANGE );
        params.remove( URISplit.PARAM_TIME_RESOLUTION );
        split.params= URISplit.formatParams(params);
        return URISplit.format(split);
    }
    
}
