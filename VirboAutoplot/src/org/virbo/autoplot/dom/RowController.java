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
import org.das2.graph.DasDevicePosition;
import org.das2.graph.DasRow;

/**
 *
 * @author jbf
 */
public class RowController extends DomNodeController {
    Row row;
    DasRow dasRow;
    Canvas canvas;

    RowController( Row row ) {
        super(row);
        this.row= row;
        row.controller= this;
    }

    protected void createDasPeer( Canvas canvas, DasRow parent ) {
        DasCanvas c= canvas.controller.getDasCanvas();
        dasRow= DasRow.create( c, parent, row.getTop(), row.getBottom() );
        PropertyChangeListener list= new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ( evt.getPropertyName().equals( DasDevicePosition.PROP_DMAXIMUM ) ) {
                    row.setBottom( DasDevicePosition.formatLayoutStr(dasRow, false ) );
                } else if ( evt.getPropertyName().equals( DasDevicePosition.PROP_DMINIMUM ) ) {
                    row.setTop( DasDevicePosition.formatLayoutStr(dasRow, true) );
                }
            }
        };
        dasRow.addPropertyChangeListener(list);
        list= new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                try {
                    double[] dd= DasDevicePosition.parseFormatStr((String)evt.getNewValue());
                    if ( evt.getPropertyName().equals(Row.PROP_TOP) ) {
                        dasRow.setMin( dd[0], dd[1], (int) dd[2] );
                    } else if ( evt.getPropertyName().equals(Row.PROP_BOTTOM) ) {
                        dasRow.setMax( dd[0], dd[1], (int) dd[2] );
                    }
                    DasDevicePosition.parseLayoutStr( dasRow, row.getTop() + "," + row.getBottom() );
                } catch (ParseException ex) {
                    Logger.getLogger(RowController.class.getName()).warning("parse exception: "+ex);
                    row.setTop( DasDevicePosition.formatLayoutStr(dasRow, true) );
                    row.setBottom( DasDevicePosition.formatLayoutStr(dasRow, false ) );
                }
            }
        };
        row.addPropertyChangeListener(Row.PROP_BOTTOM,list);
        row.addPropertyChangeListener(Row.PROP_TOP,list);
        this.canvas= canvas;
    }

    public DasRow getDasRow() {
        return dasRow;
    }

    public Canvas getCanvas() {
        return canvas;
    }
}
