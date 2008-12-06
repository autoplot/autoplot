/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSource;

/**
 * Model for a source of data plus additional processing.
 * @author jbf
 */
public class DataSourceFilter extends DomNode {
    
    protected String suri = null;
    public static final String PROP_SURI = "suri";

    public String getSuri() {
        return suri;
    }

    public void setSuri(String suri) {
        String oldSuri = this.suri;
        this.suri = suri;
        propertyChangeSupport.firePropertyChange(PROP_SURI, oldSuri, suri);
    }
    

    public static String PROP_VALID_RANGE= "validRange";
    
    private String validRange = "";

    public String getValidRange() {
        return this.validRange;
    }

    public void setValidRange(String validRange) {
        Object oldVal= this.validRange;
        this.validRange = validRange;
        propertyChangeSupport.firePropertyChange( PROP_VALID_RANGE, oldVal, validRange );
    }
    
    public static String PROP_FILL= "fill";
    
    private String fill = "";

    public String getFill() {
        return this.fill;
    }

    public void setFill(String fill) {
        String oldFill= this.fill;
        this.fill = fill;
        propertyChangeSupport.firePropertyChange( PROP_FILL, oldFill, fill );
    }    
    
    
    private int sliceDimension = 2;
    public static final String PROP_SLICEDIMENSION = "sliceDimension";

    public int getSliceDimension() {
        return this.sliceDimension;
    }

    public void setSliceDimension(int newsliceDimension) {
        if (newsliceDimension < 0 || newsliceDimension > 2) {
            return;
        }
        int oldsliceDimension = sliceDimension;
        if (oldsliceDimension != newsliceDimension) {
            this.sliceIndex = 0;
            this.sliceDimension = newsliceDimension;
            propertyChangeSupport.firePropertyChange(PROP_SLICEDIMENSION, oldsliceDimension, newsliceDimension);
        }
    }
    private int sliceIndex = 1;
    public static final String PROP_SLICEINDEX = "sliceIndex";

    /**
     * Get the value of sliceIndex
     *
     * @return the value of sliceIndex
     */
    public int getSliceIndex() {
        return this.sliceIndex;
    }

    /**
     * Set the value of sliceIndex
     *
     * @param newsliceIndex new value of sliceIndex
     */
    public void setSliceIndex(int newsliceIndex) {
        int oldsliceIndex = sliceIndex;
        this.sliceIndex = newsliceIndex;
        propertyChangeSupport.firePropertyChange(PROP_SLICEINDEX, oldsliceIndex, newsliceIndex);
    }

    private boolean transpose = false;
    public static final String PROP_TRANSPOSE = "transpose";

    public void setTranspose(boolean val) {
        boolean oldVal = this.transpose;
        this.transpose = val;
        //updateFill(true, true);
        propertyChangeSupport.firePropertyChange(PROP_TRANSPOSE, oldVal, val);
    }

    public boolean isTranspose() {
        return this.transpose;
    }
    
    
    DataSourceController controller;
    
    public DataSourceController getController() {
        return controller;
    }

    @Override
    public DomNode copy() {
        DataSourceFilter result= (DataSourceFilter) super.copy();
        result.controller= null;
        return result;
    }
    
    
    /**  dom node stuff ******************/
    

    public void syncTo(DomNode n) {
        DataSourceFilter that= (DataSourceFilter)n;
        this.setFill(that.getFill());
        this.setValidRange(that.getValidRange());        
        this.setSliceDimension(that.getSliceDimension());
        this.setSliceIndex(that.getSliceIndex());
        this.setTranspose( that.isTranspose() );
        this.setSuri(that.getSuri());
    }

    public Map<String, String> diffs(DomNode node) {
        DataSourceFilter that= (DataSourceFilter)node;
        LinkedHashMap<String,String> result= new  LinkedHashMap<String,String>();
        boolean b;
        
        b = (that.suri == this.suri || (that.suri != null && that.suri.equals(this.suri)));
        if (!b) result.put("suri", DomUtil.abbreviateRight(that.suri, 20) + " to " + DomUtil.abbreviateRight(this.suri, 20));
        
        b = that.validRange.equals(this.validRange);
        if (!b) result.put("validRange", that.validRange + " to " + (this.validRange));

        b = that.fill.equals(this.fill);
        if (!b) result.put("fill", that.fill + " to " + (this.fill));
        
        b= that.sliceDimension==this.sliceDimension;
        if ( !b ) result.put("sliceDimension", that.sliceDimension + " to " +this.sliceDimension );

        b= that.sliceIndex==this.sliceIndex;
        if ( !b ) result.put("sliceIndex", that.sliceIndex + " to " +this.sliceIndex );

        b= that.transpose==this.transpose;
        if ( !b ) result.put("transpose", that.transpose + " to " +this.transpose );
        
        return result;
    }

        
}
