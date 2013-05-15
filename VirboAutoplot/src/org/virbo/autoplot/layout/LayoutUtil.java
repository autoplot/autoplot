/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
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

/**
 * utility methods for adjusting canvas layout.
 * @author jbf
 */
public class LayoutUtil {

    private static final Logger logger = org.das2.util.LoggerManager.getLogger("autoplot.layout");

    private static boolean ALLOW_EXCESS_SPACE = true;

    private static boolean maybeSetMaximum(DasDevicePosition c, double need, double norm, double em, int pt) {
        em = Math.floor(em);
        double excess = -1 * (c.getEmMaximum() - em);
        if (ALLOW_EXCESS_SPACE && c.getMaximum() == norm && excess >= 0 && excess < 4) return false;
        if (Math.abs(c.getEmMaximum() - em) < 0.1 && Math.abs(norm - c.getMaximum()) < 0.001) return false;
        if ( Math.abs(em)>100 ) {
            System.err.println("autolayout failure.");
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
            System.err.println("autolayout failure.");
        }
        c.setMin(norm,em,pt);
        logger.log(Level.FINE, "reset minimum: {0}", c);
        return true;
    }

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
        // horizontal
        double em = c.getEmSize();

        int xmin = 90000;
        int xmax = -90000;
        int ymin = 90000;
        int ymax = -90000;

        if ( canvas.getWidth()==0 ) return;

        Rectangle bounds;
        for (DasCanvasComponent cc : canvas.getCanvasComponents()) {
            if (cc.getColumn() == c && cc.isVisible()) {
                bounds = cc.getBounds();
                xmin = Math.min(xmin, bounds.x);
                xmax = Math.max(xmax, bounds.x + bounds.width);
            }
            if (cc.getRow() == r && cc.isVisible()) {
                bounds = cc.getBounds();
                ymin = Math.min(ymin, bounds.y);
                ymax = Math.max(ymax, bounds.y + bounds.height);
            }
        }

        //90000 or -90000 means no components are connected directly to the margin row
        // or column.  this is normal.
        //if ( xmin==90000 || xmax==-90000 || ymin==90000 || ymax==-90000 ) {
            //System.err.println("marching axis state?");
           // return;
        //}

        bounds = getChildBounds(c);
        if (bounds != null) {
            xmin = Math.min(xmin, bounds.x);
            xmax = Math.max(xmax, bounds.x + bounds.width);
            ymin = Math.min(ymin, bounds.y);
            ymax = Math.max(ymax, bounds.y + bounds.height);
        }

        bounds = getChildBounds(r);
        if (bounds != null) {
            xmin = Math.min(xmin, bounds.x);
            xmax = Math.max(xmax, bounds.x + bounds.width);
            ymin = Math.min(ymin, bounds.y);
            ymax = Math.max(ymax, bounds.y + bounds.height);
        }

        double MARGIN_LEFT_RIGHT_EM = 1;

        boolean changed = false;

        if ( Math.abs(xmin)>9999 || Math.abs(xmax)>9999 || Math.abs(ymin)>9999 || Math.abs(ymax)>9999  ) {
            logger.warning("invalid bounds returned, returning.");
            return;
        }
        int old;

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
            logger.log(Level.WARNING, "needXmax: {0}", needXmax);
            c.getParent().resizeAllComponents();
            return;
        }
        
        if ( needYmax<-120 ) {
            logger.log(Level.WARNING, "needYmax: {0}", needYmax);
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

    }

    /**
     * 
     * @param col
     * @return
     */
    public static List<DasDevicePosition> getChildColumns(DasDevicePosition col, boolean doCol) {
        DasCanvas canvas = col.getParent();
        List<DasDevicePosition> result = new ArrayList<DasDevicePosition>();
        for (DasCanvasComponent cc : canvas.getCanvasComponents()) {
            DasDevicePosition ccol = doCol ? cc.getColumn() : cc.getRow();
            if (cc.getColumn().getParentDevicePosition() == col) {
                result.add(cc.getColumn());
            }
        }
        return result;
    }

    /**
     * look for attached columns, get the bounds of all attachments.
     * @param col
     * @return the bounds of all children, or null.
     */
    public static Rectangle getChildBounds(DasColumn col) {
        DasCanvas canvas = col.getParent();
        Rectangle rect = null;
        for (DasCanvasComponent cc : canvas.getCanvasComponents()) {
            if (cc.getColumn().getParentDevicePosition() == col && cc.isVisible()) {
                if (rect == null) {
                    rect = cc.getBounds();
                } else {
                    rect.add(cc.getBounds());
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
                if (rect == null) {
                    rect = cc.getBounds();
                } else {
                    rect.add(cc.getBounds());
                }
            }
        }
        return rect;
    }
}
