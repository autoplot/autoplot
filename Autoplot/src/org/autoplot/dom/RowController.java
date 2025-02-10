
package org.autoplot.dom;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.ParseException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.DasNameException;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasDevicePosition;
import org.das2.graph.DasRow;
import org.das2.util.LoggerManager;

/**
 * Controller for Row objects, mostly keeping the DasRow in sync with the DOM.
 * @author jbf
 */
public class RowController extends DomNodeController {
    
    protected static final Logger logger= org.das2.util.LoggerManager.getLogger( "autoplot.dom.row" );
    
    Row row;
    DasRow dasRow;
    Canvas canvas;
    ApplicationController applicationController;
 
    RowController( ApplicationController applicationController, Row row ) {
        super(row);
        this.row= row;
        this.applicationController= applicationController;
        row.controller= this;
        createNameListener(  );
        row.addPropertyChangeListener( Row.PROP_PARENT, nameListener );
    }

    PropertyChangeListener nameListener;
    
    void createNameListener(  ) {
        nameListener= (PropertyChangeEvent evt) -> {
            LoggerManager.logPropertyChangeEvent(evt);
            
            if ( evt.getPropertyName().equals(Row.PROP_PARENT) ) {
                String newid= (String)evt.getNewValue();
                doSetParentRow( (String)evt.getOldValue(),newid);
            }
        };
    }
    
    private void doSetParentRow( String old, String newid ) {
        if ( newid.isEmpty() ) return;
        DomNode n= applicationController.getElementById(newid);
        if ( n==null ) throw new IllegalArgumentException("unable to find parent with id: "+newid);
        if ( !(n instanceof Row) ) {
            if ( old==null ) throw new IllegalArgumentException("unable to find Row for id: "+newid );
            RowController.this.row.setParent( old );
        }
        Row newrow= (Row)n;
        RowController.this.row.controller.dasRow.setParentRow(newrow.getController().getDasRow());        
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
                        if ( dd[0]==dasRow.getMinimum() && dd[1]==dasRow.getEmMinimum() && ((int)dd[2])==dasRow.getPtMinimum() ) {
                            logger.fine("suppressing change which would have no effect");
                        } else {
                            dasRow.setMin( dd[0], dd[1], (int) dd[2] );
                        }
                    } else if ( evt.getPropertyName().equals(Row.PROP_BOTTOM) ) {
                        if ( dd[0]==dasRow.getMaximum() && dd[1]==dasRow.getEmMaximum() && ((int)dd[2])==dasRow.getPtMinimum() ) {
                            logger.fine("suppressing change which would have no effect");
                        } else {
                            dasRow.setMax( dd[0], dd[1], (int) dd[2] );
                        }
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
        try {
            if ( this.row.getId().length()>0 ) dasRow.setDasName( this.row.getId() );
        } catch (DasNameException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        applicationController.bind( row, Row.PROP_TOP, dasRow, DasDevicePosition.PROP_MINLAYOUT );
        applicationController.bind( row, Row.PROP_BOTTOM, dasRow, DasDevicePosition.PROP_MAXLAYOUT );
        doSetParentRow(null,this.row.getParent());
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
        applicationController.unbind(row);
        row.removePropertyChangeListener( Row.PROP_PARENT, nameListener );
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
