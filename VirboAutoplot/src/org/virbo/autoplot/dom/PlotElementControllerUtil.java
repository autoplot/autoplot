/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import org.das2.datum.DatumRange;
import org.das2.datum.UnitsUtil;
import org.virbo.datasource.capability.TimeSeriesBrowse;

/**
 *
 * @author jbf
 */
public class PlotElementControllerUtil {

    /**
     * return the DatumRange for the plot element's data.  When there is a
     * TimeSeriesBrowse, this is it's timerange, otherwise it comes from
     * the data.  If none is found, then null is returned.
     * @param pe
     * @return the DatumRange or null.
     */
    public static DatumRange getTimeRange( Application dom, PlotElement pe ) {

        DataSourceFilter dsf= dom.getController().getDataSourceFilterFor(pe);
        if ( dsf==null ) {
            return null;
        }
        TimeSeriesBrowse tsb= dsf.getController().getTsb();
        if ( tsb!= null ) {
            return tsb.getTimeRange();
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
