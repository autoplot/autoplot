/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.datasource.jython;

import java.text.ParseException;
import java.util.Map;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.capability.TimeSeriesBrowse;

/**
 * container for the state simply manages the timerange argument.
 * @author jbf
 */
public class JythonDataSourceTimeSeriesBrowse implements TimeSeriesBrowse {

    DatumRange timeRange;
    String uri;
    JythonDataSource jds;

    JythonDataSourceTimeSeriesBrowse(String uri) {
        this.uri = uri;
    }

    protected void setJythonDataSource( JythonDataSource jds ) {
        this.jds= jds;
    }

    public void setTimeRange(DatumRange dr) {
        if ( jds!=null ) {
            if ( this.timeRange==null || !( this.timeRange!=null && this.timeRange.equals(dr)) ) {
                synchronized ( jds ) {
                    jds.interp= null; // no caching...  TODO: this probably needs work.  For example, if we zoom in.
                }
            }
        }
        this.timeRange = dr;
        URISplit split = URISplit.parse(uri);
        Map<String, String> params = URISplit.parseParams(split.params);
        params.put(JythonDataSource.PARAM_TIMERANGE, dr.toString());
        split.params = URISplit.formatParams(params);
        this.uri = URISplit.format(split);
    }

    public DatumRange getTimeRange() {
        return this.timeRange;
    }

    public void setTimeResolution(Datum d) {
        // do nothing.
    }

    public Datum getTimeResolution() {
        return null;
    }

    public String getURI() {
        return uri;
    }

    public void setURI(String suri) throws ParseException {
        this.uri = suri;
        DatumRange tr = URISplit.parseTimeRange(uri);
        if (tr != null) {
            this.timeRange = tr;
        }
    }
}
