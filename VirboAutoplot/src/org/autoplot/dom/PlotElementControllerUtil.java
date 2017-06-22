
package org.autoplot.dom;

import org.das2.datum.DatumRange;
import org.das2.datum.UnitsUtil;
import org.autoplot.datasource.capability.TimeSeriesBrowse;

/**
 *
 * @author jbf
 */
public class PlotElementControllerUtil {

    /**
     * return the DatumRange for the plot element's data.  When there is a
     * TimeSeriesBrowse, this is its timerange (the axis range or the loaded range
     * when a time axis is not visible), otherwise it comes from
     * the data.  If none is found, then null is returned.
     * @param dom the application
     * @param pe the plot element
     * @return the DatumRange or null.
     */
    public static DatumRange getTimeRange( Application dom, PlotElement pe ) {

        if ( pe==null ) return null;
        
        DataSourceFilter dsf= dom.getController().getDataSourceFilterFor(pe);
        if ( dsf==null ) return null;
        
        TimeSeriesBrowse tsb= dsf.getController().getTsb();
        if ( tsb!= null ) {
            if ( dsf.getController().getTimeSeriesBrowseController().isListeningToAxis() ) {
                return dsf.getController().getTimeSeriesBrowseController().getPlot().getXaxis().getRange();
            } else {
                return tsb.getTimeRange();
            }
        } else {
            DatumRange dr=null;
            if ( pe.getPlotDefaults()!=null && pe.getPlotDefaults().getXaxis()!=null ) {
                dr= pe.getPlotDefaults().getXaxis().getRange();
            }
            if ( dr==null ) return null;
            if ( UnitsUtil.isTimeLocation( dr.getUnits() ) ) {
                return dr;
            } else {
                return null;
            }

        }

    }
}
