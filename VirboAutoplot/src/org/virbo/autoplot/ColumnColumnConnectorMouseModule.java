/*
 * ZoomPanMouseModule.java
 *
 * Created on August 7, 2007, 8:53 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import edu.uiowa.physics.pw.das.datum.Datum;
import edu.uiowa.physics.pw.das.datum.DatumRange;
import edu.uiowa.physics.pw.das.datum.DatumRangeUtil;
import edu.uiowa.physics.pw.das.event.MouseModule;
import edu.uiowa.physics.pw.das.graph.DasAxis;
import edu.uiowa.physics.pw.das.graph.DasPlot;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

/**
 * provides zoom/pan on the connector object itself.  
 * TODO: verify that this is just Zoom/Pan, but the direction is reversed.
 * @author jbf
 */
public class ColumnColumnConnectorMouseModule extends MouseModule {
    
    DasAxis topAxis;
    DasAxis bottomAxis;
    
    Point p0;
    DatumRange topAxisRange0;
    DatumRange bottomAxisRange0;
    
    DasAxis panAxis=null; // this is the axis we're panning
    DatumRange panAxisRange0;
    DasAxis oppositeAxis=null;  // this is the axis we're dragging along
    
    public ColumnColumnConnectorMouseModule( DasAxis topAxis, DasAxis bottomAxis ) {
        this.topAxis= topAxis;
        this.bottomAxis= bottomAxis;
        super.setLabel( "Connector Zoom Pan" );
    }
    
    public void mouseWheelMoved(MouseWheelEvent e) {
        double nmin, nmax;
        if ( e.getWheelRotation()<0 ) {
            nmin= 0.20;
            nmax= 0.80;
        } else {
            nmin= -0.25;
            nmax= 1.25;
        }
        if ( panAxis!=null ) {
            DatumRange dr;
            if ( panAxis.isLog() ) {
                dr= DatumRangeUtil.rescaleLog( panAxis.getDatumRange(), nmin, nmax );
            } else {
                dr= DatumRangeUtil.rescale( panAxis.getDatumRange(), nmin, nmax );
            }
            panAxis.setDatumRange( dr );
        }
        super.mouseWheelMoved(e);
    }
    
    public void mouseReleased(MouseEvent e) {
        super.mouseReleased(e);
        if ( panAxis!=null ) {
            panAxisLock.unlock();
            panAxisLock= null;
        }
    }
    
    public void mouseDragged(MouseEvent e) {
        super.mouseDragged(e);
        Point p2= e.getPoint();
        if ( panAxis!=null ) {
            DatumRange dr;
            if ( panAxis.isLog() ) {
                Datum delta= oppositeAxis.invTransform( p0.getX() ).divide( oppositeAxis.invTransform( p2.getX() ) );
                dr= new DatumRange( panAxisRange0.min().divide(delta), panAxisRange0.max().divide(delta) );
            } else {
                Datum delta= oppositeAxis.invTransform( p0.getX() ).subtract( oppositeAxis.invTransform( p2.getX() ) );
                dr= new DatumRange( panAxisRange0.min().subtract(delta), panAxisRange0.max().subtract(delta) );
            }
            panAxis.setDatumRange( dr );
        }
    }

    DasAxis.Lock panAxisLock;
    
    public void mousePressed(MouseEvent e) {
        super.mousePressed(e);
        p0= e.getPoint();
        if ( p0.getY() < 20 ) {
            panAxis= bottomAxis;
            oppositeAxis= topAxis;
        } else {
            panAxis= topAxis;
            oppositeAxis= bottomAxis;
        }
        if ( panAxis!=null ) {
            panAxisRange0= panAxis.getDatumRange();
            panAxisLock= panAxis.mutatorLock();
            panAxisLock.lock();
        }
        
    }
    
    
}
