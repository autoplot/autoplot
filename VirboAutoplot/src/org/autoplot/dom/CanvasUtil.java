/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.dom;

/**
 *
 * @author jbf
 */
public class CanvasUtil {
    public static Plot getMostBottomPlot( Canvas c ) {
        Row r= getMostBottomRow(c);
        for ( Plot p: c.getController().getApplicationController().getApplication().getPlots() ) {
            if ( p.getRowId().equals(r.getId() )) {
                return p;
            }
        }
        throw new IllegalArgumentException("bottom row has no plot");
    }

    public static Row getMostBottomRow( Canvas c ) {
        return c.getRows( c.getRows().length-1 );
    }
}
