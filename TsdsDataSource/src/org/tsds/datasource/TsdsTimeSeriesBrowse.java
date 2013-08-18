/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tsds.datasource;

import java.text.ParseException;
import java.util.Map;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.TimeParser;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.capability.TimeSeriesBrowse;

/**
 *
 * @author jbf
 */
public class TsdsTimeSeriesBrowse implements TimeSeriesBrowse {

    /**
     * current timeRange, which will be quantized to granule boundaries.
     */
    DatumRange timeRange;
    /**
     * current timeRange, which will be quantized to granule boundaries.
     */
    Datum resolution;

    /**
     * current points per day, should be short-circuit to timeRange.
     */
    int currentPpd = -1;
    
    int parameterPpd = -1; // max resolution of the parameter.

    private DatumRange quantizeTimeRange(DatumRange timeRange) {
        timeRange = new DatumRange(TimeUtil.prevMidnight(timeRange.min()), TimeUtil.nextMidnight(timeRange.max()));
        return timeRange;
    }

    private int quantizePpd(Datum resolution) {
        int[] ppds = new int[]{1, 8, 24, 96, 144, 4320, 17280, 86400, 864000};
        if (resolution == null) {
            return 1;
        }
        double resdays = resolution.doubleValue(Units.days);
        double dppd = 1 / resdays;
        int ppd = ppds[ppds.length - 1];
        for (int i = 0; i < ppds.length && ppds[i] <= parameterPpd; i++) {
            if (ppds[i] > dppd) {
                ppd = ppds[i];
                return ppd;
            }
        }
        return parameterPpd;
    }


    public void setTimeRange(DatumRange dr) {
        System.out.println(dr);
        timeRange = quantizeTimeRange(dr);
        System.out.println(timeRange);
        System.out.println(timeRange.width());
    }

    public void setTimeResolution(Datum d) {
        resolution = d;
        if (resolution == null) {
            currentPpd = -1;
        } else {
            currentPpd = quantizePpd(resolution);
            resolution = Units.days.createDatum(1.0).divide(currentPpd);
        }
    }

    public String getURI() {
        TimeParser tp = TimeParser.create("%Y%m%d");

        String sparams =
            "StartDate=" + tp.format(timeRange.min(), null) + "&EndDate=" + tp.format(timeRange.max(), null) +
            "&ppd=" + currentPpd ;
        return "vap+tsds:" + sparams;
    }
    
    public String blurURI() {
        return "vap+tsds:";     // TODO: methinks getURI was never actually called...
    }

    public DatumRange getTimeRange() {
        return timeRange;
    }

    public Datum getTimeResolution() {
        return resolution;
    }

    public void setURI(String suri) throws ParseException {
        URISplit split= URISplit.parse(suri);
        Map<String,String> params= URISplit.parseParams(split.params);

        String startTime= params.remove( "StartDate" );
        String endTime= params.get( "EndDate" );
        String sppd= params.get("ppd");

        if ( startTime!=null && endTime!=null ) {
           timeRange= new DatumRange( Units.us2000.parse(startTime), Units.us2000.parse(endTime) );
        }
        if ( sppd!=null ) {
            currentPpd= Integer.parseInt(sppd);
        }
    }
}
