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
import org.das2.DasNameException;
import org.das2.graph.DasAxis;
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
    ApplicationController applicationController;

    ColumnController( ApplicationController applicationController, Column column ) {
        super(column);
        this.column= column;
        this.applicationController= applicationController;
        column.controller= this;
        createNameListener(  );
        column.addPropertyChangeListener( Row.PROP_PARENT, nameListener );
    }

    PropertyChangeListener nameListener;
    
    void createNameListener(  ) {
        nameListener= (PropertyChangeEvent evt) -> {
            LoggerManager.logPropertyChangeEvent(evt);
            
            if ( evt.getPropertyName().equals(Row.PROP_PARENT) ) {
                String newid= (String)evt.getNewValue();
                doSetParentColumn( (String)evt.getOldValue(), newid );
            }
        };
    }
    
    private void doSetParentColumn( String old, String newid ) {
        if ( newid.isEmpty() ) return;
        DomNode n= applicationController.getElementById(newid);
        if ( n==null ) throw new IllegalArgumentException("unable to find parent with id: "+newid);
        if ( !(n instanceof Column) ) {
            if ( old==null ) throw new IllegalArgumentException("unable to find Column for id: "+newid );
            ColumnController.this.column.setParent( old );
        }
        Column newcolumn= (Column)n;
        ColumnController.this.column.controller.dasColumn.setParentColumn(newcolumn.getController().getDasColumn());        
    }
        
    PropertyChangeListener dasColumnPosListener;
    
    PropertyChangeListener createDasColumnPosListener( final List<String> minList, final List<String> maxList ) {
        dasColumnPosListener= new PropertyChangeListener( ) {
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
        return dasColumnPosListener;
    }
      
    PropertyChangeListener columnPosListener;
    
    PropertyChangeListener createColomnPosListener( ) {
        columnPosListener= new PropertyChangeListener() {
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
        return columnPosListener;
    }
    
    protected void createDasPeer( Canvas canvas, DasColumn parent ) {
        DasCanvas c= canvas.controller.getDasCanvas();
        dasColumn= DasColumn.create( c, parent, column.getLeft(), column.getRight() );
        try {
            if ( this.column.getId().length()>0 ) dasColumn.setDasName( this.column.getId() );
        } catch (DasNameException ex) {
            logger.log(Level.SEVERE, null, ex);
        }                
        applicationController.bind( column, Column.PROP_LEFT, dasColumn, DasDevicePosition.PROP_MINLAYOUT );
        applicationController.bind( column, Column.PROP_RIGHT, dasColumn, DasDevicePosition.PROP_MAXLAYOUT );
        doSetParentColumn(null,this.column.getParent());
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
    
    public void removeBindings() {
        applicationController.unbind(column);
        column.removePropertyChangeListener( Column.PROP_PARENT, nameListener );
    }
    
    public void removeReferences() {
        //column= null;
        //dasColumn= null;
        //canvas= null;
    }
    
    public DasColumn getDasColumn() {
        return dasColumn;
    }

    public Canvas getCanvas() {
        return canvas;
    }
}
