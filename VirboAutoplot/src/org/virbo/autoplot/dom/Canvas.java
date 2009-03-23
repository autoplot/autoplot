/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author jbf
 */
public class Canvas extends DomNode {

    protected CanvasController controller;
    public static final String PROP_CONTROLLER = "controller";

    public Canvas() {
        //childListener
    }
    
    
    private java.awt.Dimension size = new java.awt.Dimension(640, 480);
    public static final String PROP_SIZE = "size";

    public java.awt.Dimension getSize() {
        return this.size;
    }

    public void setSize(java.awt.Dimension newCanvasSize) {
        java.awt.Dimension oldcanvasSize = size;
        this.size = newCanvasSize;
        propertyChangeSupport.firePropertyChange(PROP_SIZE, oldcanvasSize, newCanvasSize);
    }
    
    
    
    protected boolean fitted = true;
    /**
     * boolean property indicates that the canvas is resizable.
     */
    public static final String PROP_FITTED = "fitted";

    public boolean isFitted() {
        return fitted;
    }

    public void setFitted(boolean fitted) {
        boolean oldfitted = this.fitted;
        this.fitted = fitted;
        propertyChangeSupport.firePropertyChange(PROP_FITTED, oldfitted, fitted);
    }
    
    /**
     * outer column for the canvas
     */
    protected String column = "5em,100%-3em";

    public static final String PROP_COLUMN = "column";

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        String oldColumn = this.column;
        this.column = column;
        propertyChangeSupport.firePropertyChange(PROP_COLUMN, oldColumn, column);
    }
    
    
    /**
     * outer row for the canvas.
     */
    public static final String PROP_ROW = "row";

    protected String row = "2em,100%-3em";
    
    public String getRow() {
        return row;
    }

    public void setRow(String row1) {
        String oldRow1 = this.row;
        this.row = row1;
        propertyChangeSupport.firePropertyChange(PROP_ROW, oldRow1, row1);
    }

    
    public CanvasController getController() {
        return controller;
    }

    protected void setController(CanvasController controller) {
        CanvasController oldController = this.controller;
        this.controller = controller;
        propertyChangeSupport.firePropertyChange(PROP_CONTROLLER, oldController, controller);
    }

    @Override
    public DomNode copy() {
        Canvas that= (Canvas)super.copy();
        that.controller= null;
        return that;
    }
    
    

    public void syncTo(DomNode n) {
        super.syncTo(n);
        Canvas that= (Canvas)n;
        this.setFitted(that.isFitted());
        this.setSize(that.getSize());
        this.setRow(that.getRow());
        this.setColumn(that.getColumn());        
    }

    public List<Diff> diffs(DomNode node) {
        List<Diff> result = new ArrayList<Diff>();
        Canvas that= (Canvas)node;
        boolean b;
        b=  that.fitted==this.fitted ;
        if ( !b ) result.add( new PropertyChangeDiff( "fitted", that.fitted , this.fitted ) );

        b=  that.size.equals(this.size) ;
        if ( !b ) result.add( new PropertyChangeDiff( "size", that.size , this.size ) );

        b=  that.row.equals(this.row) ;
        if ( !b ) result.add( new PropertyChangeDiff( "row", that.row , this.row ) );

        b=  that.column.equals(this.column) ;
        if ( !b ) result.add( new PropertyChangeDiff( "column", that.column , this.column ) );

        return result;
    }


}
