/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.util.List;

/**
 * Model for a source of data plus additional processing.
 * @author jbf
 */
public class DataSourceFilter extends DomNode {
    
    protected String uri = null;
    public static final String PROP_URI = "uri";

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        
        String oldUri = this.uri;
        this.uri = uri;
        propertyChangeSupport.firePropertyChange(PROP_URI, oldUri, uri);
    }
    

    public static final String PROP_VALID_RANGE= "validRange";
    
    private String validRange = "";

    public String getValidRange() {
        return this.validRange;
    }

    public void setValidRange(String validRange) {
        Object oldVal= this.validRange;
        this.validRange = validRange;
        propertyChangeSupport.firePropertyChange( PROP_VALID_RANGE, oldVal, validRange );
    }
    
    public static final String PROP_FILL= "fill";
    
    private String fill = "";

    public String getFill() {
        return this.fill;
    }

    public void setFill(String fill) {
        String oldFill= this.fill;
        this.fill = fill;
        propertyChangeSupport.firePropertyChange( PROP_FILL, oldFill, fill );
    }    
    
    
    private int sliceDimension = 0;
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

    private int sliceIndex = -1;
    /**
     * index to slice the dataset in the dataSourceFilter.  This is to support
     * legacy behavior.  -1 now indicates that no slicing should be done, and
     * this is the default.
     */
    public static final String PROP_SLICEINDEX = "sliceIndex";


    public int getSliceIndex() {
        return this.sliceIndex;
    }

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
    
    

        
}
