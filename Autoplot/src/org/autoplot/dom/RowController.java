/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.dom;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasDevicePosition;
import org.das2.graph.DasRow;
import org.das2.util.LoggerManager;

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

    PropertyChangeListener dasRowPosListener;
    
    PropertyChangeListener createDasRowPosListener( final List<String> minList, final List<String> maxList ) {
        dasRowPosListener= new PropertyChangeListener( ) {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                LoggerManager.logPropertyChangeEvent(evt);  
                if ( maxList.contains( evt.getPropertyName() ) ) {
                    row.setBottom( DasDevicePosition.formatLayoutStr(dasRow, false ) );
                } else if ( minList.contains( evt.getPropertyName() ) ) {
                    row.setTop( DasDevicePosition.formatLayoutStr(dasRow, true) );
                }
            }
        };
        return dasRowPosListener;
    }
      
    PropertyChangeListener rowPosListener;
    
    PropertyChangeListener createRowPosListener( ) {
        rowPosListener= new PropertyChangeListener() {
            @Override            
            public void propertyChange(PropertyChangeEvent evt) {
                LoggerManager.logPropertyChangeEvent(evt);                
                try {
                    double[] dd= DasDevicePosition.parseLayoutStr((String)evt.getNewValue());
                    if ( evt.getPropertyName().equals(Row.PROP_TOP) ) {
                        dasRow.setMin( dd[0], dd[1], (int) dd[2] );
                    } else if ( evt.getPropertyName().equals(Row.PROP_BOTTOM) ) {
                        dasRow.setMax( dd[0], dd[1], (int) dd[2] );
                    }
                    //DasDevicePosition.parseLayoutStr( dasRow, row.getTop() + "," + row.getBottom() );
                } catch (ParseException ex) {
                    logger.log(Level.WARNING, "parse exception: {0}", ex);
                    row.setTop( DasDevicePosition.formatLayoutStr(dasRow, true) );
                    row.setBottom( DasDevicePosition.formatLayoutStr(dasRow, false ) );
                }
            }
        };
        return rowPosListener;
    }
    
    protected void createDasPeer( Canvas canvas, DasRow parent ) {
        DasCanvas c= canvas.controller.getDasCanvas();
        dasRow= DasRow.create( c, parent, row.getTop(), row.getBottom() );

        final List<String> minList= Arrays.asList( DasDevicePosition.PROP_MINIMUM, DasDevicePosition.PROP_EMMINIMUM, DasDevicePosition.PROP_PTMINIMUM );
        final List<String> maxList= Arrays.asList( DasDevicePosition.PROP_MAXIMUM, DasDevicePosition.PROP_EMMAXIMUM, DasDevicePosition.PROP_PTMAXIMUM );

        dasRowPosListener= createDasRowPosListener( minList, maxList);
        dasRow.addPropertyChangeListener(dasRowPosListener);

        rowPosListener= createRowPosListener();
        row.addPropertyChangeListener(Row.PROP_BOTTOM,rowPosListener);
        row.addPropertyChangeListener(Row.PROP_TOP,rowPosListener);
        this.canvas= canvas;
    }
    
    /**
     * returns true if the spec is the same.
     * @param spec spec like "30%+1em,60%-4em"
     * @return true if they are equal.
     * @throws ParseException 
     */
    public boolean isLayoutEqual( String spec ) throws ParseException {
        String[] ss= spec.split(",");
        String s1= DasDevicePosition.formatFormatStr( DasDevicePosition.parseLayoutStr(ss[0]) );
        if ( !this.row.top.equals(s1) ) return false;
        s1= DasDevicePosition.formatFormatStr( DasDevicePosition.parseLayoutStr(ss[1]) );
        if ( !this.row.bottom.equals(s1) ) return false;
        return true;
    }    

    public void removeBindings() {
        dasRow.removePropertyChangeListener(dasRowPosListener);
        row.removePropertyChangeListener(Column.PROP_LEFT,rowPosListener);
        row.removePropertyChangeListener(Column.PROP_RIGHT,rowPosListener);
    }
    
    public void removeReferences() {
        //row= null;
        //dasRow= null;
        //canvas= null;
    }
    
    public DasRow getDasRow() {
        return dasRow;
    }

    public Canvas getCanvas() {
        return canvas;
    }
}
