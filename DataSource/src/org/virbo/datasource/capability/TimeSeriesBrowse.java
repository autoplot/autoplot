/*
 * TimeSeriesBrowse.java
 *
 * Created on November 12, 2007, 12:29 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.datasource.capability;

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
     */
    void setTimeRange( DatumRange dr );
    
    /**
     * get the time range for the current view of the timeseries.  Note this 
     * may not be the same as getTimeRange
     * @return
     */
    DatumRange getTimeRange();
    
    /**
     * set the resolution for the desired view of the timeseries.
     */
    void setTimeResolution( Datum d );
        
    /**
     * get the resolution for the current view of the timeseries.  Note this
     * may not be the same as setTimeResolution.  Also, this may be null, indicating
     * the native resolution is used.
     */
    Datum getTimeResolution();
        
    /**
     * return the URI for the current time range and resolution.  This is also
     * used to identify the dataset, so the same urls returned from here must
     * return the same dataset!
     */
    String getURI( );

}
