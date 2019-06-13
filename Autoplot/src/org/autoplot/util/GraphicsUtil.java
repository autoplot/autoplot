
package org.autoplot.util;

import javax.swing.Icon;
import org.autoplot.RenderType;
import org.autoplot.dom.PlotElement;
import org.das2.graph.SeriesRenderer;

/**
 *
 * @author jbf
 */
public class GraphicsUtil {
    
    /**
     * return null or the icon 16x16 for the plotElement.
     * @param pe
     * @return 
     */
    public static Icon guessIconFor( PlotElement pe ) {
        if ( pe.getRenderType()==RenderType.series ) {
            SeriesRenderer r= new SeriesRenderer();
            r.setColor( pe.getStyle().getColor() );
            r.setFillToReference(pe.getStyle().isFillToReference() );
            r.setSymSize( pe.getStyle().getSymbolSize() );
            return r.getListIcon();
        } else {
            return null;
        }
    }
}
