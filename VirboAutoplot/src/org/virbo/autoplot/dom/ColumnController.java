/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.ParseException;
import java.util.logging.Logger;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasColumn;
import org.das2.graph.DasDevicePosition;

/**
 *
 * @author jbf
 */
public class ColumnController extends DomNodeController {

    Column column;
    DasColumn dasColumn;
    Canvas canvas;

    ColumnController( Column column ) {
        super(column);
        this.column= column;
        column.controller= this;
    }

    protected void createDasPeer( Canvas canvas, DasColumn parent ) {
        DasCanvas c= canvas.controller.getDasCanvas();
        dasColumn= DasColumn.create( c, parent, column.getLeft(), column.getRight() );
        PropertyChangeListener list= new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ( evt.getPropertyName().equals( DasDevicePosition.PROP_DMAXIMUM ) ) {
                    column.setRight( DasDevicePosition.formatLayoutStr(dasColumn, false ) );
                } else if ( evt.getPropertyName().equals( DasDevicePosition.PROP_DMINIMUM ) ) {
                    column.setLeft( DasDevicePosition.formatLayoutStr(dasColumn, true) );
                }
            }
        };
        dasColumn.addPropertyChangeListener(list);
        list= new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                try {
                    double[] dd= DasDevicePosition.parseFormatStr((String)evt.getNewValue());
                    if ( evt.getPropertyName().equals(Column.PROP_LEFT) ) {
                        dasColumn.setMin( dd[0], dd[1], (int) dd[2] );
                    } else if ( evt.getPropertyName().equals(Column.PROP_RIGHT) ) {
                        dasColumn.setMax( dd[0], dd[1], (int) dd[2] );
                    }
                    DasDevicePosition.parseLayoutStr( dasColumn, column.getLeft() + "," + column.getRight() );
                } catch (ParseException ex) {
                    Logger.getLogger(ColumnController.class.getName().toString()).warning("parse exception: "+ex);
                    column.setLeft( DasDevicePosition.formatLayoutStr(dasColumn, true) );
                    column.setRight( DasDevicePosition.formatLayoutStr(dasColumn, false ) );
                }
            }
        };
        column.addPropertyChangeListener(Column.PROP_LEFT,list);
        column.addPropertyChangeListener(Column.PROP_RIGHT,list);
        this.canvas= canvas;
    }

    public DasColumn getDasColumn() {
        return dasColumn;
    }

    public Canvas getCanvas() {
        return canvas;
    }
}
