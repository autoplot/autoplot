/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.layout;

import edu.uiowa.physics.pw.das.graph.DasCanvas;
import edu.uiowa.physics.pw.das.graph.DasCanvasComponent;
import edu.uiowa.physics.pw.das.graph.DasColumn;
import edu.uiowa.physics.pw.das.graph.DasDevicePosition;
import edu.uiowa.physics.pw.das.graph.DasRow;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * utility methods for adjusting canvas layout.
 * @author jbf
 */
public class LayoutUtil {
    /**
     * resets the layout on the canvas so that labels are not clipped (somewhat).
     * Child row and columns are inspected as well, and it's assumed that adjusting
     * this row and column, that everyone will be correctly adjuected.
     * 
     * We calculate bounds on each component dependent on the row and column, then
     * the region outside the canvas determines how much the row and column should
     * be brought in.
     * 
     * @param canvas
     * @param c
     * @param r 
     */
    public static void autolayout( DasCanvas canvas, DasRow r, DasColumn c ) {
        // horizontal
        double em= c.getEmSize();
        
        int xmin= 90000;
        int xmax= -90000;
        int ymin= 90000;
        int ymax= -90000;
        
        Rectangle bounds;
        for ( DasCanvasComponent cc : canvas.getCanvasComponents() ) {
            if ( cc.getColumn()==c && cc.isVisible() ) {
                bounds= cc.getBounds();
                xmin= Math.min( xmin, bounds.x );
                xmax= Math.max( xmax, bounds.x + bounds.width );
            }
            if ( cc.getRow()==r && cc.isVisible() ) {
                bounds= cc.getBounds();
                ymin= Math.min( ymin, bounds.y );
                ymax= Math.max( ymax, bounds.y + bounds.height );
            }
        }
     
        bounds= getChildBounds( c );
        if ( bounds!=null ) {
            xmin= Math.min( xmin, bounds.x );
            xmax= Math.max( xmax, bounds.x + bounds.width );
            ymin= Math.min( ymin, bounds.y );
            ymax= Math.max( ymax, bounds.y + bounds.height );
        }
         
        bounds= getChildBounds( r );
        if ( bounds!=null ) {
            xmin= Math.min( xmin, bounds.x );
            xmax= Math.max( xmax, bounds.x + bounds.width );
            ymin= Math.min( ymin, bounds.y );
            ymax= Math.max( ymax, bounds.y + bounds.height );
        }
        
        double MARGIN_LEFT_RIGHT_EM= 1;
        
        boolean ALLOW_EXCESS_SPACE= true;
        
        int old, need;
        old= c.getDMinimum();
        need= old - xmin;
        if ( ALLOW_EXCESS_SPACE && need < 0 ) need= 0;
        c.setMinimum(0);
        c.setEmMinimum( need / em + MARGIN_LEFT_RIGHT_EM );
        c.setPtMinimum(0);
        
        old= c.getDMaximum();
        need= xmax - old;
        if ( ALLOW_EXCESS_SPACE && need < 0 ) need= 0;
        c.setMaximum(1.0);
        c.setEmMaximum( -need / em - MARGIN_LEFT_RIGHT_EM );
        c.setPtMaximum(0);
        
        old= r.getDMinimum();
        need= old - ymin;
        if ( ALLOW_EXCESS_SPACE && need < 0 ) need= 0;
        r.setMinimum(0);
        r.setEmMinimum( need / em );
        r.setPtMinimum(0);
        
        old= r.getDMaximum();
        need= ymax - old;
        if ( ALLOW_EXCESS_SPACE && need < 0 ) need= 0;        
        r.setMaximum(1.0);
        r.setEmMaximum( -need / em );
        r.setPtMaximum(0);
    }
    
    /**
     * 
     * @param col
     * @return
     */
    public static List<DasDevicePosition> getChildColumns( DasDevicePosition col, boolean doCol ) {
        DasCanvas canvas= col.getParent();
        List<DasDevicePosition> result= new ArrayList<DasDevicePosition>();
        for ( DasCanvasComponent cc : canvas.getCanvasComponents() ) {
            DasDevicePosition ccol= doCol ? cc.getColumn() : cc.getRow();
            if ( cc.getColumn().getParentDevicePosition()==col ) {
                result.add( cc.getColumn() );
            }
        }
        return result;
    }
    
    /**
     * look for attached columns, get the bounds of all attachments.
     * @param col
     * @return the bounds of all children, or null.
     */
    public static Rectangle getChildBounds( DasColumn col ) {
        DasCanvas canvas= col.getParent();
        Rectangle rect= null;
        List<DasDevicePosition> result= new ArrayList<DasDevicePosition>();
        for ( DasCanvasComponent cc : canvas.getCanvasComponents() ) {
            DasDevicePosition ccol= cc.getColumn();
            if ( cc.getColumn().getParentDevicePosition()==col && cc.isVisible() ) {
                if ( rect==null ) {
                    rect= cc.getBounds() ;
                } else {
                    rect.add( cc.getBounds() );
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
    public static Rectangle getChildBounds( DasRow row ) {
        DasCanvas canvas= row.getParent();
        Rectangle rect= null;
        List<DasDevicePosition> result= new ArrayList<DasDevicePosition>();
        for ( DasCanvasComponent cc : canvas.getCanvasComponents() ) {
            DasDevicePosition ccol= cc.getColumn();
            if ( cc.getRow().getParentDevicePosition()==row && cc.isVisible() ) {
                if ( rect==null ) {
                    rect= cc.getBounds() ;
                } else {
                    rect.add( cc.getBounds() );
                }
            }
        }
        return rect;
    }
    
}
