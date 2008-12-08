/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.util.ArrayList;
import java.util.List;
import org.virbo.datasource.URLSplit;

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
        if ( suri!=null ) {
            URLSplit split= URLSplit.parse(suri);
            suri= URLSplit.format(split); // make canonical
        }
        
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

    public void syncTo(DomNode n, List<String> exclude ) {
        DataSourceFilter that= (DataSourceFilter)n;
        this.setFill(that.getFill());
        this.setValidRange(that.getValidRange());        
        this.setSliceDimension(that.getSliceDimension());
        this.setSliceIndex(that.getSliceIndex());
        this.setTranspose( that.isTranspose() );
        if ( !exclude.contains("suri" ) ) this.setSuri(that.getSuri());
    }

    public List<Diff> diffs(DomNode node) {
        DataSourceFilter that= (DataSourceFilter)node;
        
        List<Diff> result = new ArrayList<Diff>();
        
        boolean b;
        
        b = (that.suri == this.suri || (that.suri != null && that.suri.equals(this.suri)));
        if (!b) result.add( new PropertyChangeDiff( "suri", that.suri, this.suri ) );
        
        b = that.validRange.equals(this.validRange);
        if (!b) result.add(new PropertyChangeDiff( "validRange", that.validRange , (this.validRange)));

        b = that.fill.equals(this.fill);
        if (!b) result.add(new PropertyChangeDiff( "fill", that.fill , (this.fill)));
        
        b= that.sliceDimension==this.sliceDimension;
        if ( !b ) result.add(new PropertyChangeDiff( "sliceDimension", that.sliceDimension ,this.sliceDimension ));

        b= that.sliceIndex==this.sliceIndex;
        if ( !b ) result.add(new PropertyChangeDiff( "sliceIndex", that.sliceIndex ,this.sliceIndex ));

        b= that.transpose==this.transpose;
        if ( !b ) result.add(new PropertyChangeDiff( "transpose", that.transpose ,this.transpose ));
        
        return result;
    }

        
}
