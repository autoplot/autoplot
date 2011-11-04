/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource;

import java.text.ParseException;
import java.util.Map;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.virbo.datasource.capability.TimeSeriesBrowse;

/**
 * Default implementation commonly found, doesn't use resolution
 * @author jbf
 */
public class DefaultTimeSeriesBrowse implements TimeSeriesBrowse {
    public String uri;
    DatumRange timeRange;

    public void setURI(String suri) throws ParseException {
        this.uri= suri;
        URISplit split= URISplit.parse(uri);
        Map<String,String> params= URISplit.parseParams(split.params);
        String sdr= params.get( URISplit.PARAM_TIME_RANGE );
        if ( sdr!=null ) {
            timeRange= DatumRangeUtil.parseTimeRange(sdr.replaceAll("\\+", " "));
        }
    }

    public String getURI() {
        return this.uri;
    }

    public void setTimeRange(DatumRange dr) {
        URISplit split= URISplit.parse(uri);
        Map<String,String> params= URISplit.parseParams(split.params);
        params.put( URISplit.PARAM_TIME_RANGE, dr.toString().replaceAll(" ","+"));
        split.params= URISplit.formatParams(params);
        this.uri= URISplit.format(split);
        timeRange= dr;
    }

    public DatumRange getTimeRange() {
        return timeRange;
    }

    public void setTimeResolution(Datum d) {
        // do nothing
    }

    public Datum getTimeResolution() {
        return null;
    }

}
