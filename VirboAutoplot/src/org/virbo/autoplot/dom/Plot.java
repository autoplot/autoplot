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
public class Plot extends DomNode {

    public Plot() {
    }
        
    protected Axis xaxis = new Axis();
    public static final String PROP_XAXIS = "xaxis";

    public Axis getXaxis() {
        return xaxis;
    }

    public void setXaxis(Axis xaxis) {
        Axis oldXaxis = this.xaxis;
        this.xaxis = xaxis;
        propertyChangeSupport.firePropertyChange(PROP_XAXIS, oldXaxis, xaxis);
    }
    protected Axis yaxis = new Axis();
    public static final String PROP_YAXIS = "yaxis";

    public Axis getYaxis() {
        return yaxis;
    }

    public void setYaxis(Axis yaxis) {
        Axis oldYaxis = this.yaxis;
        this.yaxis = yaxis;
        propertyChangeSupport.firePropertyChange(PROP_YAXIS, oldYaxis, yaxis);
    }
    protected Axis zaxis = new Axis();
    public static final String PROP_ZAXIS = "zaxis";

    public Axis getZaxis() {
        return zaxis;
    }

    public void setZaxis(Axis zaxis) {
        Axis oldZaxis = this.zaxis;
        this.zaxis = zaxis;
        propertyChangeSupport.firePropertyChange(PROP_ZAXIS, oldZaxis, zaxis);
    }
    
    
    protected String title = "";
    /**
     * title for the plot. 
     */
    public static final String PROP_TITLE = "title";

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        String oldTitle = this.title;
        this.title = title;
        propertyChangeSupport.firePropertyChange(PROP_TITLE, oldTitle, title);
    }

    protected boolean isotropic = false;

    public static final String PROP_ISOTROPIC = "isotropic";

    public boolean isIsotropic() {
        return isotropic;
    }

    public void setIsotropic(boolean isotropic) {
        boolean oldIsotropic = this.isotropic;
        this.isotropic = isotropic;
        propertyChangeSupport.firePropertyChange(PROP_ISOTROPIC, oldIsotropic, isotropic);
    }
    
    protected String rowId;
    public static final String PROP_ROWID = "rowId";

    public String getRowId() {
        return rowId;
    }

    public void setRowId(String rowId) {
        String oldRowId = this.rowId;
        this.rowId = rowId;
        propertyChangeSupport.firePropertyChange(PROP_ROWID, oldRowId, rowId);
    }

    protected String columnId;
    public static final String PROP_COLUMNID = "columnId";

    public String getColumnId() {
        return columnId;
    }

    public void setColumnId(String columnId) {
        String oldColumnId = this.columnId;
        this.columnId = columnId;
        propertyChangeSupport.firePropertyChange(PROP_COLUMNID, oldColumnId, columnId);
    }

    
    protected PlotController controller;

    public PlotController getController() {
        return controller;
    }

    @Override
    public DomNode copy() {
        Plot result= (Plot) super.copy();
        result.controller= null;
        result.xaxis= (Axis) result.xaxis.copy();
        result.yaxis= (Axis) result.yaxis.copy();
        result.zaxis= (Axis) result.zaxis.copy();
        return result;
    }
    
    
    public void syncTo(DomNode n) {
        super.syncTo(n);
        Plot that = (Plot) n;
        this.setTitle( that.getTitle() );
        this.setIsotropic( that.isIsotropic() );
        this.xaxis.syncTo(that.getXaxis());
        this.yaxis.syncTo(that.getYaxis());
        this.zaxis.syncTo(that.getZaxis());
    }

    @Override
    public List<DomNode> childNodes() {
        ArrayList<DomNode> result = new ArrayList<DomNode>();
        result.add(xaxis);
        result.add(yaxis);
        result.add(zaxis);
        
        return result;
    }

    public List<Diff> diffs(DomNode node) {

        Plot that = (Plot) node;
        List<Diff> result = new ArrayList<Diff>();

        boolean b;
        
        b=  that.title.equals(this.title) ;
        if ( !b ) result.add( new PropertyChangeDiff( "title", that.title, this.title ) );
        
        result.addAll( DomUtil.childDiffs("xaxis", this.getXaxis().diffs(that.getXaxis()) ) );
        result.addAll( DomUtil.childDiffs("yaxis", this.getYaxis().diffs(that.getYaxis()) ) );
        result.addAll( DomUtil.childDiffs("zaxis", this.getZaxis().diffs(that.getZaxis()) ) );
        return result;
    }
}
