/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.util.Arrays;

/**
 * Many operations are defined within the DOM object controllers that needn't
 * be.  This class is a place for operations that are performed on the DOM
 * independent of the controllers.  For example, the operation to swap the
 * position of two plots is easily implemented by changing the rowid and columnid
 * properties of the two plots.
 *
 * @author jbf
 */
public class DomOps {
    /**
     * swap the position of the two plots.  If one plot has its tick labels hidden,
     * then this is swapped as well.
     * @param a
     * @param b
     */
    public static void swapPosition( Plot a, Plot b ) {
        String trowid= a.getRowId();
        String tcolumnid= a.getColumnId();
        boolean txtv= a.getXaxis().isDrawTickLabels();
        boolean tytv= a.getYaxis().isDrawTickLabels();

        a.setRowId(b.getRowId());
        a.setColumnId(b.getColumnId());
        a.getXaxis().setDrawTickLabels(b.getXaxis().isDrawTickLabels());
        a.getYaxis().setDrawTickLabels(b.getYaxis().isDrawTickLabels());
        b.setRowId(trowid);
        b.setColumnId(tcolumnid);
        b.getXaxis().setDrawTickLabels(txtv);
        b.getYaxis().setDrawTickLabels(tytv);

    }

    public static Plot copyPlotAndPanels( Plot srcPlot, boolean copyPanels, boolean bindx, boolean bindy, Object direction ) {
        Application application= srcPlot.getController().getApplication();
        ApplicationController ac= application.getController();

        Plot that = ac.addPlot( direction );
        that.getController().setAutoBinding(false);

        ac.addPanel(that, null);

        that.syncTo( srcPlot, Arrays.asList( DomNode.PROP_ID, Plot.PROP_ROWID, Plot.PROP_COLUMNID ) );

        if (bindx) {
            BindingModel bb = ac.findBinding(application, Application.PROP_TIMERANGE, srcPlot.getXaxis(), Axis.PROP_RANGE);
            if (bb == null) {
                ac.bind(srcPlot.getXaxis(), Axis.PROP_RANGE, that.getXaxis(), Axis.PROP_RANGE);
            } else {
                ac.bind(application, Application.PROP_TIMERANGE, that.getXaxis(), Axis.PROP_RANGE);
            }

        }

        if (bindy) {
            ac.bind(srcPlot.getYaxis(), Axis.PROP_RANGE, that.getYaxis(), Axis.PROP_RANGE);
        }

        return that;

    }
}
