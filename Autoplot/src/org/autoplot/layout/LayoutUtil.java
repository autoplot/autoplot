
package org.autoplot.layout;

import java.util.logging.Level;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasCanvasComponent;
import org.das2.graph.DasColumn;
import org.das2.graph.DasDevicePosition;
import org.das2.graph.DasRow;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.das2.graph.DasAnnotation;
import org.das2.graph.DasAxis;

/**
 * utility methods for adjusting canvas layout.
 * @author jbf
 */
public class LayoutUtil {

    private static final Logger logger = org.das2.util.LoggerManager.getLogger("autoplot.layout");

    private static final boolean ALLOW_EXCESS_SPACE = true;

    /**
     * reset the maximum, unless the current setting is acceptable.
     * @param c the row or column to adjust
     * @param need
     * @param norm proposed new normal position
     * @param em proposed new em offset
     * @param pt proposed new point offset
     * @return true if the position was changed.
     */
    private static boolean maybeSetMaximum(DasDevicePosition c, double need, double norm, double em, int pt) {
        em = Math.floor(em);
        double excess = -1 * (c.getEmMaximum() - em);
        if (ALLOW_EXCESS_SPACE && c.getMaximum() == norm && excess >= 0 && excess < 4) return false;
        if (Math.abs(c.getEmMaximum() - em) < 0.1 && Math.abs(norm - c.getMaximum()) < 0.001) return false;
        if ( Math.abs(em)>100 ) {
            logger.log(Level.SEVERE, "autolayout failure: {0}em", em);
        }
        c.setMax(norm,em,pt); // dampen by splitting the difference https://sourceforge.net/p/autoplot/bugs/1022/
        logger.log(Level.FINE, "reset maximum: {0}", c);
        return true;
    }

    /**
     *
     * @param c c the row or column to adjust
     * @param need
     * @param norm proposed new normal position
     * @param em proposed new em offset
     * @param pt proposed new point offset
     * @return return true if it was changed
     */
    private static boolean maybeSetMinimum(final DasDevicePosition c, double need, double norm, double em, int pt) {
        em = Math.ceil(em);
        double excess = c.getEmMinimum() - em;
        if (ALLOW_EXCESS_SPACE && c.getMinimum() == norm && excess >= 0 && excess < 4) return false;
        if (Math.abs(c.getEmMinimum() - em) < 0.1 && Math.abs(norm - c.getMinimum()) < 0.001) return false;
        if ( Math.abs(em)>100 ) {
            logger.log(Level.SEVERE, "autolayout failure: {0}em", em);
        }
        c.setMin(norm,em,pt);
        logger.log(Level.FINE, "reset minimum: {0}", c);
        return true;
    }

    /**
     * just for debugging, this keeps track by identifying interactions.
     */
    private static int count=0;
    
    /**
     * resets the layout on the canvas so that labels are not clipped (somewhat).
     * Child row and columns are inspected as well, and it's assumed that adjusting
     * this row and column (Autoplot's margin row and column), that everyone will be correctly adjusted.
     * 
     * We calculate bounds on each component dependent on the row and column, then
     * the region outside the canvas determines how much the row and column should
     * be brought in.
     * 
     * @param canvas
     * @param marginColumn
     * @param marginRow 
     */
    public static void autolayout(DasCanvas canvas, DasRow marginRow, DasColumn marginColumn) {
        
        logger.fine( "enter autolayout" );
        
        double em = marginColumn.getEmSize();
        logger.log(Level.FINE, "autolayout em size: {0}", em);

        int currentBoundsXMin = 90000;
        int currentBoundsXMax = -90000;
        int currentBoundsYMin = 90000;
        int currentBoundsYMax = -90000;

        if ( canvas.getWidth()==0 ) {
            logger.fine( "exit autolayout because canvas.getWidth()==0" );
            return;
        }

        count++;
        
        boolean tcaAreComing= false;
        
        for (DasCanvasComponent cc : canvas.getCanvasComponents()) {

            Rectangle bounds;
            
            if ( cc instanceof DasAnnotation ) continue; // there's a set of components we want to ignore because it's easy to mess up.
                        
            if ( cc.isVisible() && ( cc.getColumn() == marginColumn || cc.getColumn().getParentDevicePosition() == marginColumn 
                    || ( cc.getColumn().getParentDevicePosition()!=null && cc.getColumn().getParentDevicePosition().getParentDevicePosition() == marginColumn ) ) ) {
                
                logger.log(Level.FINER, "here cc= {0}", cc);
                            
                bounds = cc.getBounds();
                logger.log(Level.FINER, "considering for x position (count={0}): {1} {2}", new Object[]{count, cc.getDasName(), bounds});
                
                if ( bounds.width>0 ) {
                    logger.finest( String.format( "%d %d %d %s", count, bounds.x, bounds.width, cc.toString() ) );
                
                    currentBoundsXMin = Math.min(currentBoundsXMin, bounds.x);
                    currentBoundsXMax = Math.max(currentBoundsXMax, bounds.x + bounds.width);
                    
                    if ( Math.abs(currentBoundsXMin)>9999 ) {
                        logger.log(Level.FINER, "component messes up bounds: {0}", cc);
                        //bounds = cc.getBounds(); // for debugging.
                    }
                }
            }
            
            if ( cc.isVisible() && ( cc.getRow() == marginRow ||  cc.getRow().getParentDevicePosition()==marginRow 
                    || ( cc.getRow().getParentDevicePosition()!=null && cc.getRow().getParentDevicePosition().getParentDevicePosition() == marginRow ) ) ) {
                bounds = cc.getBounds();
                logger.log(Level.FINER, "considering for y position (count={0}): {1} {2}", new Object[]{count, cc.getDasName(), bounds});
                if ( bounds.height>0 ) {
                    currentBoundsYMin = Math.min(currentBoundsYMin, bounds.y);
                    currentBoundsYMax = Math.max(currentBoundsYMax, bounds.y + bounds.height);
                }
                
                if ( cc instanceof DasAxis && ((DasAxis)cc).isDrawTca() && !((DasAxis)cc).isTcaLoaded() ) {
                    tcaAreComing= true; // anticipate that TCA will be coming.
                }
            }
        }

        //90000 or -90000 means no components are connected directly to the margin row
        // or column.  this is normal.
        //if ( xmin==90000 || xmax==-90000 || ymin==90000 || ymax==-90000 ) {
            //System.err.println("marching axis state?");
           // return;
        //}

        logger.finest( String.format( "%d %d %d %s", count, currentBoundsXMin, currentBoundsXMax-currentBoundsXMin, "all_together" ) );
        
        double MARGIN_LEFT_RIGHT_EM = 1;

        boolean changed = false;

        if ( Math.abs(currentBoundsXMin)>9999 || Math.abs(currentBoundsXMax)>9999 || Math.abs(currentBoundsYMin)>9999 || Math.abs(currentBoundsYMax)>9999  ) {
            logger.fine("invalid bounds returned, returning.");
            return;
        }

        if ( currentBoundsYMin>canvas.getHeight()/2 ) {
            logger.fine("transitional state where currentBoundsYMin is large.");
            return;
        }
        
        // these are the additional pixels needed in each direction.
        int needXmin, needXmax, needYmin, needYmax;

        int oldxmin = marginColumn.getDMinimum();
        needXmin = oldxmin - currentBoundsXMin;

        int oldxmax = marginColumn.getDMaximum();
        needXmax = currentBoundsXMax - oldxmax;

        int oldymin = marginRow.getDMinimum();
        needYmin = oldymin - currentBoundsYMin;
        
        int oldymax = marginRow.getDMaximum();
        needYmax = currentBoundsYMax - oldymax;

        if ( needYmin< -7*em && tcaAreComing ) { // seven (or so) lines of tca might be coming, and this is why there's a big gap.
            logger.fine("anticipate that TCA data will be loaded, changing xaxis height.");
            needYmin= 0;
        }
                
        if ( needXmax<-120 ) {
            logger.log(Level.FINE, "needXmax: {0}", needXmax);
            marginColumn.getParent().resizeAllComponents();
            return;
        }
        
        if ( needYmax<-120 ) {
            logger.log(Level.FINE, "needYmax: {0}", needYmax); // this clearly doesn't matter since it happens all the time.
            marginColumn.getParent().resizeAllComponents();
            return;
        }
        
        logger.log( Level.FINE, "needYmin: {0} needYmax: {1}", new Object[]{needYmin, needYmax});
       
        if ( needYmin<-700 ) {
            logger.fine("needYmin is less than -700, returning.");
            return;
        }
        
        changed = changed | maybeSetMinimum(marginColumn, needXmin, 0, needXmin / em + MARGIN_LEFT_RIGHT_EM, 0);
        changed = changed | maybeSetMaximum(marginColumn, needXmax, 1.0, -needXmax / em - MARGIN_LEFT_RIGHT_EM, 0);
        changed = changed | maybeSetMinimum(marginRow, needYmin, 0, needYmin / em, 0);
        changed = changed | maybeSetMaximum(marginRow, needYmax, 1.0, -needYmax / em, 0);
        
        if ( false && changed ) {
            List<DasRow> rows= new ArrayList<>();
                   
            for (DasCanvasComponent cc : canvas.getCanvasComponents()) {

                if ( cc instanceof DasAnnotation ) continue; // there's a set of components we want to ignore because it's easy to mess up.
                       
                if ( cc.isVisible() && ( cc.getColumn() == marginColumn || cc.getColumn().getParentDevicePosition() == marginColumn
                    || ( cc.getColumn().getParentDevicePosition()!=null && cc.getColumn().getParentDevicePosition().getParentDevicePosition() == marginColumn ) ) ) {
                    if ( !rows.contains(cc.getRow() ) ) { 
                        rows.add( cc.getRow() );
                    }
                }
               
            }
            if ( true ) {
                normalizeRows( em, marginRow, rows );
            }
           
        }
               
        if (changed) {
            marginColumn.getParent().resizeAllComponents();
        }
        
        logger.log(Level.FINER, "exit autolayout, changed={0}", changed);

    }

    /**
     * preserve pixel locations of the rows, with corrections to the marginRow.
     * @param canvas
     */    
    public static void normalizeRows( org.autoplot.dom.Canvas canvas ) {
        List<DasRow> rows= new ArrayList<>();

        DasCanvas dasCanvas= canvas.getController().getDasCanvas();
        DasColumn marginColumn= canvas.getMarginColumn().getController().getDasColumn();
        DasRow marginRow= canvas.getMarginRow().getController().getDasRow();

        for (DasCanvasComponent cc : dasCanvas.getCanvasComponents()) {

            if ( cc instanceof DasAnnotation ) continue; // there's a set of components we want to ignore because it's easy to mess up.

            if ( cc.isVisible() && ( cc.getColumn() == marginColumn || cc.getColumn().getParentDevicePosition() == marginColumn
                || ( cc.getColumn().getParentDevicePosition()!=null && cc.getColumn().getParentDevicePosition().getParentDevicePosition() == marginColumn ) ) ) {

               rows.add( cc.getRow() );

            }

        }

        normalizeRows( 0, marginRow, rows );

    }
    
    /**
     * preserve pixel locations of the rows, with corrections to the marginRow.
     * @param em
     * @param marginRow
     * @param rows 
     */
    public static void normalizeRows( double em, DasRow marginRow, List<DasRow> rows ) {
        Map<DasRow,int[]> dposs= new HashMap<>();
        for ( DasRow r: rows ) {
            if ( r.getParentDevicePosition()==marginRow ) {
                int[] dpos= new int[] { r.getDMinimum(), r.getDMaximum() };
                dposs.put( r, dpos );
            }
        }
        marginRow.setMin(  .0,  2., 0 );
        marginRow.setMax( 1.0, -3., 0 );
        for ( DasRow r: rows ) {
            if ( r.getParentDevicePosition()==marginRow ) {
                int[] dpos= dposs.get( r );
                r.setDPosition( dpos[0], dpos[1] );
            }
        }
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
