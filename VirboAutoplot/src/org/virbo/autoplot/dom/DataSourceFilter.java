/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.capability.Caching;
import org.virbo.datasource.capability.TimeSeriesBrowse;

/**
 * Model for a source of data plus additional processing.
 * @author jbf
 */
public class DataSourceFilter extends DomNode {
    
    protected DataSource dataSource = null;
    public static final String PROP_DATASOURCE = "dataSource";
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
    
    protected TimeSeriesBrowse tsb = null;
    public static final String PROP_TSB = "tsb";

    public TimeSeriesBrowse getTsb() {
        return tsb;
    }

    public void _setTsb(TimeSeriesBrowse tsb) {
        TimeSeriesBrowse oldTsb = this.tsb;
        this.tsb = tsb;
        propertyChangeSupport.firePropertyChange(PROP_TSB, oldTsb, tsb);
    }
    protected String tsbSuri = null;
    public static final String PROP_TSBSURI = "tsbSuri";

    public String getTsbSuri() {
        return tsbSuri;
    }

    public void _setTsbSuri(String tsbSuri) {
        String oldTsbSuri = this.tsbSuri;
        this.tsbSuri = tsbSuri;
        propertyChangeSupport.firePropertyChange(PROP_TSBSURI, oldTsbSuri, tsbSuri);
    }
    
    protected Caching caching = null;
    public static final String PROP_CACHING = "caching";

    public Caching getCaching() {
        return caching;
    }

    public void _setCaching(Caching caching) {
        Caching oldCaching = this.caching;
        this.caching = caching;
        propertyChangeSupport.firePropertyChange(PROP_CACHING, oldCaching, caching);
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

    public DataSource _getDataSource() {
        return dataSource;
    }

    public void _setDataSource(DataSource dataSource) {
        DataSource oldDataSource = this.dataSource;
        this.dataSource = dataSource;
        propertyChangeSupport.firePropertyChange(PROP_DATASOURCE, oldDataSource, dataSource);
    }
    /**
     * the dataset loaded from the data source.
     */
    protected QDataSet dataSet = null;
    public static final String PROP_DATASET = "dataSet";

    public QDataSet getDataSet() {
        return dataSet;
    }

    public void _setDataSet(QDataSet dataSet) {
        QDataSet oldDataSet = this.dataSet;
        this.dataSet = dataSet;
        propertyChangeSupport.firePropertyChange(PROP_DATASET, oldDataSet, dataSet);
    }
    /**
     * fill dataset is a copy of the loaded dataset, with fill data applied.  If dataset has mutable properties,
     * then the fillDataSet will be the same as the dataset, and the dataset's properties are modified.
     */
    protected QDataSet fillDataSet = null;
    public static final String PROP_FILLDATASET = "fillDataSet";

    public QDataSet getFillDataSet() {
        return fillDataSet;
    }

    public void _setFillDataSet(QDataSet fillDataSet) {
        QDataSet oldFillDataSet = this.fillDataSet;
        this.fillDataSet = fillDataSet;
        propertyChangeSupport.firePropertyChange(PROP_FILLDATASET, oldFillDataSet, fillDataSet);
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
    private List<String> depnames = Arrays.asList(new String[]{"first", "second", "last"});
    public static final String PROP_DEPNAMES = "depnames";

    public List<String> getDepnames() {
        return this.depnames;
    }

    public void setDepnames(List<String> newdepnames) {
        List<String> olddepnames = depnames;
        this.depnames = newdepnames;
        if (!newdepnames.equals(olddepnames)) {
            propertyChangeSupport.firePropertyChange(PROP_DEPNAMES, olddepnames, newdepnames);
        }
    }

    protected Map<String, Object> properties = null;
    public static final String PROP_PROPERTIES = "properties";

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void _setProperties(Map<String, Object> properties) {
        Map<String, Object> oldProperties = this.properties;
        this.properties = properties;
        propertyChangeSupport.firePropertyChange(PROP_PROPERTIES, oldProperties, properties);
    }
    
    protected Map<String, Object> fillProperties = null;
    public static final String PROP_FILLPROPERTIES = "fillProperties";

    public Map<String, Object> getFillProperties() {
        return fillProperties;
    }

    public void _setFillProperties(Map<String, Object> fillProperties) {
        Map<String, Object> oldFillProperties = this.fillProperties;
        this.fillProperties = fillProperties;
        propertyChangeSupport.firePropertyChange(PROP_FILLPROPERTIES, oldFillProperties, fillProperties);
    }

    protected String reduceDataSetString = null;
    public static final String PROP_REDUCEDATASETSTRING = "reduceDataSetString";

    public String getReduceDataSetString() {
        return reduceDataSetString;
    }

    public void _setReduceDataSetString(String reduceDataSetString) {
        String oldReduceDataSetString = this.reduceDataSetString;
        this.reduceDataSetString = reduceDataSetString;
        propertyChangeSupport.firePropertyChange(PROP_REDUCEDATASETSTRING, oldReduceDataSetString, reduceDataSetString);
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
