/*
 * TimeSeriesBrowse.java
 *
 * Created on November 12, 2007, 12:29 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.autoplot.datasource.capability;

import java.text.ParseException;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;

/**
 * This capability allows DataSources that know how to produce data sets
 * from a long time series to provide views of the DataSource for different
 * times and resolutions.
 * 
 * The getURI() method of the DataSource should return the original URI and
 * getDataSet should return the original dataset.  getURI if TimeSeriesBrowse
 * should return the URI for the range and resolution specified.
 *
 * Note DataSources providing this capability must insert CacheTags into the
 * QDataSets they produce.
 *
 * @author jbf
 */
public interface TimeSeriesBrowse {
    /**
     * set the time range for the desired view of the timeseries.
     * @param dr the new time range.
     */
    void setTimeRange( DatumRange dr );
    
    /**
     * get the time range for the current view of the timeseries.  Note this 
     * may not be the same as getTimeRange
     * @return the current time range.
     */
    DatumRange getTimeRange();
    
    /**
     * set the resolution for the desired view of the timeseries.
     * @param d the time resolution
     */
    void setTimeResolution( Datum d );
        
    /**
     * get the resolution for the current view of the timeseries.  Note this
     * may not be the same as setTimeResolution.  Also, this may be null, indicating
     * the native resolution is used.
     * @return the resolution for the current view of the timeseries.
     */
    Datum getTimeResolution();
        
    /**
     * return the URI for the current time range and resolution.  This is also
     * used to identify the dataset, so the same urls returned from here must
     * return the same dataset!
     * @return the URI to load this data.
     */
    String getURI( );

    /**
     * return the URI without the timeSeriesBrowse settings, for use in .vap files and where the 
     * timerange is set elsewhere.
     * @return the URI simplified by removing the timerange and resolution.
     */
    String blurURI( );
    
    /**
     * Added in effort to make it easier to set the timerange if we have a timerange already.  This
     * allows the timerange part of the URI to be set without having to understand the rest of it.
     * set the URI, and possibly the timerange part.
     * @param suri
     * @throws ParseException if the uri cannot be parsed.
     */
    public void setURI( String suri ) throws ParseException ;

    /**
     * problem message to use in reject when the timerange was not provided in the URI.
     */
    public static final String PROB_NO_TIMERANGE_PROVIDED = "no timerange provided";

    /**
     * problem message to use in reject when the timerange does not parse properly.
     */
    public static final String PROB_PARSE_ERROR_IN_TIMERANGE = "parse error in timeRange";
}
