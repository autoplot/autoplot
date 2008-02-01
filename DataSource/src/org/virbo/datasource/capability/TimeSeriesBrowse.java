/*
 * TimeSeriesBrowse.java
 *
 * Created on November 12, 2007, 12:29 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.datasource.capability;

import edu.uiowa.physics.pw.das.datum.Datum;
import edu.uiowa.physics.pw.das.datum.DatumRange;

/**
 * This capability allows DataSources that know how to produce data sets
 * from a long time series to provide views of the DataSource for different
 * times and resolutions.
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
     * set the resolution for the desired view of the timeseries.
     */
    void setTimeResolution( Datum d );
    
}
