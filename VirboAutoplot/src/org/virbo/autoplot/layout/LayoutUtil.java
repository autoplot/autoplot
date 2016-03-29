
package org.virbo.autoplot.layout;

import java.util.logging.Level;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasCanvasComponent;
import org.das2.graph.DasColumn;
import org.das2.graph.DasDevicePosition;
import org.das2.graph.DasRow;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.das2.graph.DasAnnotation;
import org.das2.graph.DasColorBar;

/**
 * utility methods for adjusting canvas layout.
 * @author jbf
 */
public class LayoutUtil {

    private static final Logger logger = org.das2.util.LoggerManager.getLogger("autoplot.layout");

    private static final boolean ALLOW_EXCESS_SPACE = true;

    private static boolean maybeSetMaximum(DasDevicePosition c, double need, double norm, double em, int pt) {
        em = Math.floor(em);
        double excess = -1 * (c.getEmMaximum() - em);
        if (ALLOW_EXCESS_SPACE && c.getMaximum() == norm && excess >= 0 && excess < 4) return false;
        if (Math.abs(c.getEmMaximum() - em) < 0.1 && Math.abs(norm - c.getMaximum()) < 0.001) return false;
        if ( Math.abs(em)>100 ) {
            logger.log(Level.SEVERE, "autolayout failure: {0}", em);
        }
        c.setMax(norm,em,pt);
        logger.log(Level.FINE, "reset maximum: {0}", c);
        return true;
    }

    /**
     *
     * @param c
     * @param need
     * @param norm
     * @param em
     * @param pt
     * @return return true if it was changed
     */
    private static boolean maybeSetMinimum(final DasDevicePosition c, double need, double norm, double em, int pt) {
        em = Math.ceil(em);
        double excess = c.getEmMinimum() - em;
        if (ALLOW_EXCESS_SPACE && c.getMinimum() == norm && excess >= 0 && excess < 4) return false;
        if (Math.abs(c.getEmMinimum() - em) < 0.1 && Math.abs(norm - c.getMinimum()) < 0.001) return false;

        if ( Math.abs(em)>100 ) {
            logger.log(Level.SEVERE, "autolayout failure: {0}", em);
        }
        c.setMin(norm,em,pt);
        logger.log(Level.FINE, "reset minimum: {0}", c);
        return true;
    }

    private static int count=0;
    
    /**
     * resets the layout on the canvas so that labels are not clipped (somewhat).
     * Child row and columns are inspected as well, and it's assumed that adjusting
     * this row and column, that everyone will be correctly adjusted.
     * 
     * We calculate bounds on each component dependent on the row and column, then
     * the region outside the canvas determines how much the row and column should
     * be brought in.
     * 
     * @param canvas
     * @param c
     * @param r 
     */
    public static void autolayout(DasCanvas canvas, DasRow r, DasColumn c) {
        
        logger.fine( "enter autolayout" );
        
        // horizontal
        double em = c.getEmSize();

        int xmin = 90000;
        int xmax = -90000;
        int ymin = 90000;
        int ymax = -90000;

        if ( canvas.getWidth()==0 ) return;

        count++;
        
        for (DasCanvasComponent cc : canvas.getCanvasComponents()) {

            Rectangle bounds;
            
            if ( cc instanceof DasAnnotation ) continue; // there's a set of components we want to ignore because it's easy to mess up.
                        
            if ( cc.isVisible() && ( cc.getColumn() == c || cc.getColumn().getParentDevicePosition() == c ) ) {
                
                logger.log(Level.FINEST, "here cc= {0}", cc);
            
                if ( cc instanceof DasColorBar ) {
                    logger.log(Level.FINEST, "here colorbar" ); // breakpoint
                }
                
                bounds = cc.getBounds();
                
                if ( bounds.width>0 ) {
                    logger.finest( String.format( "%d %d %d %s", count, bounds.x, bounds.width, cc.toString() ) );
                
                    xmin = Math.min(xmin, bounds.x);
                    xmax = Math.max(xmax, bounds.x + bounds.width);
                    
                    if ( Math.abs(xmin)>9999 ) {
                        logger.log(Level.FINER, "component messes up bounds: {0}", cc);
                        //bounds = cc.getBounds(); // for debugging.
                    }
                }
            }
            
            if ( cc.isVisible() && ( cc.getRow() == r ||  cc.getRow().getParentDevicePosition()==r ) ) {
                bounds = cc.getBounds();
                if ( bounds.height>0 ) {
                    ymin = Math.min(ymin, bounds.y);
                    ymax = Math.max(ymax, bounds.y + bounds.height);
                }
            }
        }

        //90000 or -90000 means no components are connected directly to the margin row
        // or column.  this is normal.
        //if ( xmin==90000 || xmax==-90000 || ymin==90000 || ymax==-90000 ) {
            //System.err.println("marching axis state?");
           // return;
        //}

        logger.fine( String.format( "%d %d %d %s", count, xmin, xmax-xmin, "all_together" ) );
        
        double MARGIN_LEFT_RIGHT_EM = 1;

        boolean changed = false;

        if ( Math.abs(xmin)>9999 || Math.abs(xmax)>9999 || Math.abs(ymin)>9999 || Math.abs(ymax)>9999  ) {
            logger.fine("invalid bounds returned, returning.");
            return;
        }
        int old;

        // these are the additional pixels needed in each direction.
        int needXmin, needXmax, needYmin, needYmax;

        old = c.getDMinimum();
        needXmin = old - xmin;

        old = c.getDMaximum();
        needXmax = xmax - old;

        old = r.getDMinimum();
        needYmin = old - ymin;

        old = r.getDMaximum();
        needYmax = ymax - old;

        if ( needXmax<-120 ) {
            logger.log(Level.FINE, "needXmax: {0}", needXmax);
            c.getParent().resizeAllComponents();
            return;
        }
        
        if ( needYmax<-120 ) {
            logger.log(Level.FINE, "needYmax: {0}", needYmax); // this clearly doesn't matter since it happens all the time.
            c.getParent().resizeAllComponents();
            return;
        }
        
        logger.log( Level.FINE, "needYmin: {0} needYmax: {1}", new Object[]{needYmin, needYmax});
       
        changed = changed | maybeSetMinimum(c, needXmin, 0, needXmin / em + MARGIN_LEFT_RIGHT_EM, 0);
        changed = changed | maybeSetMaximum(c, needXmax, 1.0, -needXmax / em - MARGIN_LEFT_RIGHT_EM, 0);
        changed = changed | maybeSetMinimum(r, needYmin, 0, needYmin / em, 0);
        changed = changed | maybeSetMaximum(r, needYmax, 1.0, -needYmax / em, 0);

        if (changed) {
            c.getParent().resizeAllComponents();
        }
        
        logger.log(Level.FINER, "exit autolayout, changed={0}", changed);

    }

    /**
     * Return a list of DasColumns where the parent is the given column.
     * @param col the column
     * @return list of columns.
     */
    public static List<DasDevicePosition> getChildColumns(DasDevicePosition col) {
        DasCanvas canvas = col.getParent();
        List<DasDevicePosition> result = new ArrayList<>();
        for (DasCanvasComponent cc : canvas.getCanvasComponents()) {
            if (cc.getColumn().getParentDevicePosition() == col) {
                result.add(cc.getColumn());
            }
        }
        return result;
    }

    /**
     * look for attached columns, get the bounds of all attachments.  For 
     * example, this includes the bounds of a colorbar attached to the plot.
     *
     * @param col
     * @return the bounds of all children, or null.
     */
    public static Rectangle getChildBounds(DasColumn col) {
        DasCanvas canvas = col.getParent();
        Rectangle rect = null;
        for (DasCanvasComponent cc : canvas.getCanvasComponents()) {
            if (cc.getColumn().getParentDevicePosition() == col && cc.isVisible()) {
                Rectangle b= cc.getBounds();
                if ( b.height>0 && b.width>0 ) {
                    if (rect == null) {
                        rect = cc.getBounds();
                    } else {
                        rect.add(cc.getBounds());
                    }
                }
            }
        }
        return rect;
    }

    /**
     * look for attached columns, get the bounds of all attachments.
     * @param row
     * @return the bounds of all children, or null.
     */
    public static Rectangle getChildBounds(DasRow row) {
        DasCanvas canvas = row.getParent();
        Rectangle rect = null;
        for (DasCanvasComponent cc : canvas.getCanvasComponents()) {
            if (cc.getRow().getParentDevicePosition() == row && cc.isVisible()) {
                Rectangle b= cc.getBounds();
                if ( b.height>0 && b.width>0 ) {
                    if (rect == null) {
                        rect = b;
                    } else {
                        rect.add( b );
                    }
                }
            }
        }
        return rect;
    }
}
