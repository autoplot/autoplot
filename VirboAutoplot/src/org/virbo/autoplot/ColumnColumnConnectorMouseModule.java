/*
 * ZoomPanMouseModule.java
 *
 * Created on August 7, 2007, 8:53 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.event.MouseModule;
import org.das2.graph.DasAxis;
import org.das2.graph.DasPlot;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.SwingUtilities;
import org.das2.datum.UnitsUtil;

/**
 * provides zoom/pan on the connector object itself.  
 * TODO: verify that this is just Zoom/Pan, but the direction is reversed.
 * @author jbf
 */
public class ColumnColumnConnectorMouseModule extends MouseModule {

    DasPlot topPlot;
    DasPlot bottomPlot;
   // DasPlot panPlot;  // the plot that is panning
    //DasPlot oppositePlot; // this plot we're dragging along
    //DasAxis topAxis;
    //DasAxis bottomAxis;
    Point p0;  // initial mouse press
    //DatumRange topAxisRange0;
    //DatumRange bottomAxisRange0;
    DasAxis panAxis = null; // this is the axis we're panning
    DatumRange panAxisRange0;
    DasAxis oppositeAxis = null;  // this is the axis we're dragging along
    DasAxis.Lock panAxisLock;
    // -- vertical panning --
    DasAxis panAxisV = null; // this is the axis we're panning
    DatumRange panAxisRange0V;
    DasAxis oppositeAxisV = null;  // this is the axis we're dragging along
    DasAxis.Lock panAxisLockV;

    public ColumnColumnConnectorMouseModule(DasPlot topPlot, DasPlot bottomPlot) {
        this.topPlot = topPlot;
        this.bottomPlot = bottomPlot;
        super.setLabel("Connector Zoom Pan");
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        double nmin, nmax;
        if (e.getWheelRotation() < 0) {
            nmin = 0.20;
            nmax = 0.80;
        } else {
            nmin = -0.25;
            nmax = 1.25;
        }
        if (panAxis != null) {
            DatumRange dr;
            if (panAxis.isLog()) {
                dr = DatumRangeUtil.rescaleLog(panAxis.getDatumRange(), nmin, nmax);
            } else {
                dr = DatumRangeUtil.rescale(panAxis.getDatumRange(), nmin, nmax);
            }
            panAxis.setDatumRange(dr);
        }
        if (panAxisV != null) {
            DatumRange dr;
            if (panAxisV.isLog()) {
                dr = DatumRangeUtil.rescaleLog(panAxisV.getDatumRange(), nmin, nmax);
            } else {
                dr = DatumRangeUtil.rescale(panAxisV.getDatumRange(), nmin, nmax);
            }
            panAxisV.setDatumRange(dr);
        }
        super.mouseWheelMoved(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        super.mouseReleased(e);
        if (panAxis != null) {
            panAxisLock.unlock();
            panAxisLock = null;
            panAxis= null;
        } 
        if (panAxisV != null) {
            panAxisLockV.unlock();
            panAxisLockV = null;
            panAxisV= null;
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        super.mouseDragged(e);
        Point p2 = e.getPoint();
        if (panAxis != null) {
            if ( !panAxis.getUnits().isConvertableTo(oppositeAxis.getUnits()) ) return;
            DatumRange dr;
            if (panAxis.isLog()) {
                Datum delta = oppositeAxis.invTransform(p0.getX()).divide(oppositeAxis.invTransform(p2.getX()));
                dr = new DatumRange(panAxisRange0.min().divide(delta), panAxisRange0.max().divide(delta));
            } else {
                Datum delta = oppositeAxis.invTransform(p0.getX()).subtract(oppositeAxis.invTransform(p2.getX()));
                dr = new DatumRange(panAxisRange0.min().subtract(delta), panAxisRange0.max().subtract(delta));
            }
            panAxis.setDatumRange(dr);
        }
        if (panAxisV != null) {
            if ( !panAxisV.getUnits().isConvertableTo(oppositeAxisV.getUnits()) ) return;
            DatumRange dr;
            if (panAxisV.isLog()) {
                if ( UnitsUtil.isTimeLocation( panAxisV.getUnits()) ) {
                    logger.fine("log of time axis--shouldn't happen");
                    return;
                }
                Datum delta = oppositeAxisV.invTransform(p0.getY()).divide(oppositeAxisV.invTransform(p2.getY()));
                dr = new DatumRange(panAxisRange0V.min().divide(delta), panAxisRange0V.max().divide(delta));
            } else {
                Datum delta = oppositeAxisV.invTransform(p0.getY()).subtract(oppositeAxisV.invTransform(p2.getY()));
                dr = new DatumRange(panAxisRange0V.min().subtract(delta), panAxisRange0V.max().subtract(delta));
            }
            panAxisV.setDatumRange(dr);
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        super.mousePressed(e);
        p0 = e.getPoint();
        if (p0.getY() < 20) {
            panAxis = bottomPlot.getXAxis();
            oppositeAxis = topPlot.getXAxis();
        } else {
            Point p= e.getPoint();
            p= SwingUtilities.convertPoint( e.getComponent(), p, bottomPlot.getCanvas() );
            boolean doHoriz= topPlot.getXAxis().getUnits().isConvertableTo( bottomPlot.getXAxis().getUnits() )
                    && topPlot.getXAxis().getDatumRange().contains( bottomPlot.getXAxis().invTransform( p.getX() ) );
            boolean doVert= topPlot.getYAxis().getUnits().isConvertableTo( bottomPlot.getYAxis().getUnits() )
                    && topPlot.getYAxis().getDatumRange().contains( bottomPlot.getYAxis().invTransform( p.getY() ) );
            if ( doHoriz ) {
                panAxis = topPlot.getXAxis();
                oppositeAxis = bottomPlot.getXAxis();
            } 
            if ( doVert ) {
                panAxisV = topPlot.getYAxis();
                oppositeAxisV = bottomPlot.getYAxis();                
            }
        }
        if (panAxis != null) {
            panAxisRange0 = panAxis.getDatumRange();
            panAxisLock = panAxis.mutatorLock();
            panAxisLock.lock();
        }
        if ( panAxisV !=null ) {
            panAxisRange0V = panAxisV.getDatumRange();
            panAxisLockV = panAxisV.mutatorLock();
            panAxisLockV.lock();            
        }

    }
    
}
