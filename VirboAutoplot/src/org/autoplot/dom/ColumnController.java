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
import java.util.logging.Logger;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasColumn;
import org.das2.graph.DasDevicePosition;
import org.das2.util.LoggerManager;

/**
 *
 * @author jbf
 */
public class ColumnController extends DomNodeController {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger( "autoplot.dom" );
    
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
        final List<String> minList= Arrays.asList( DasDevicePosition.PROP_MINIMUM, DasDevicePosition.PROP_EMMINIMUM, DasDevicePosition.PROP_PTMINIMUM );
        final List<String> maxList= Arrays.asList( DasDevicePosition.PROP_MAXIMUM, DasDevicePosition.PROP_EMMAXIMUM, DasDevicePosition.PROP_PTMAXIMUM );
        PropertyChangeListener list= new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                LoggerManager.logPropertyChangeEvent(evt);  
                if ( maxList.contains( evt.getPropertyName() ) ) {
                    column.setRight( DasDevicePosition.formatLayoutStr(dasColumn, false ) );
                } else if ( minList.contains( evt.getPropertyName() ) ) {
                    column.setLeft( DasDevicePosition.formatLayoutStr(dasColumn, true) );
                }
            }
        };
        dasColumn.addPropertyChangeListener(list);
        list= new PropertyChangeListener() {
            @Override            
            public void propertyChange(PropertyChangeEvent evt) {
                LoggerManager.logPropertyChangeEvent(evt);  
                try {
                    double[] dd= DasDevicePosition.parseLayoutStr((String)evt.getNewValue());
                    if ( evt.getPropertyName().equals(Column.PROP_LEFT) ) {
                        dasColumn.setMin( dd[0], dd[1], (int) dd[2] );
                    } else if ( evt.getPropertyName().equals(Column.PROP_RIGHT) ) {
                        dasColumn.setMax( dd[0], dd[1], (int) dd[2] );
                    }
                    //DasDevicePosition.parseLayoutStr( dasColumn, column.getLeft() + "," + column.getRight() );
                } catch (ParseException ex) {
                    logger.log(Level.WARNING, "parse exception: {0}", ex);
                    column.setLeft( DasDevicePosition.formatLayoutStr(dasColumn, true) );
                    column.setRight( DasDevicePosition.formatLayoutStr(dasColumn, false ) );
                }
            }
        };
        column.addPropertyChangeListener(Column.PROP_LEFT,list);
        column.addPropertyChangeListener(Column.PROP_RIGHT,list);
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
        if ( !this.column.left.equals(s1) ) return false;
        s1= DasDevicePosition.formatFormatStr( DasDevicePosition.parseLayoutStr(ss[1]) );
        if ( !this.column.right.equals(s1) ) return false;
        return true;
    }
    
    public DasColumn getDasColumn() {
        return dasColumn;
    }

    public Canvas getCanvas() {
        return canvas;
    }
}
