/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.dom;

import java.util.List;
import org.autoplot.datasource.URISplit;

/**
 * Model for a source of data plus additional processing.
 * @author jbf
 */
public class DataSourceFilter extends DomNode {
    
    protected String uri = "";
    public static final String PROP_URI = "uri";

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        if ( uri==null ) {
            throw new IllegalArgumentException("uri cannot be set to null now, use \"\" instead");
        }
        if ( !uri.equals("") && !uri.contains("%{") ) { // kludge for %{PWD}...
            uri= URISplit.makeCanonical(uri);  //TODO: this is goofy.  The caller or controller should do this.
        }
        
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

    /**
     * filters are the same as the component name of the PlotElement, a string of pipe-delimited commands that operate on the data.
     */
    public static final String PROP_FILTERS= "filters";

    private String filters= "";

    public String getFilters() {
        return filters;
    }

    public void setFilters(String filters) {
        //if ( this.id.equals("data_0") && filters.equals("") ) {
        //    new Exception().printStackTrace(System.err);
        //}
        String old= this.filters;
        this.filters = filters;
        propertyChangeSupport.firePropertyChange( PROP_FILTERS, old, filters );
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
    

    @Override
    public void syncTo(DomNode n) {
        super.syncTo(n);
        if ( !( n instanceof DataSourceFilter ) ) throw new IllegalArgumentException("node should be a DataSourceFilter");                
        DataSourceFilter that= (DataSourceFilter)n;
        this.setFill(that.getFill());
        this.setFilters(that.getFilters());
        this.setValidRange(that.getValidRange());
        this.setUri(that.getUri());
    }

    @Override
    public void syncTo(DomNode n, List<String> exclude ) {
        super.syncTo(n,exclude);
        if ( !( n instanceof DataSourceFilter ) ) throw new IllegalArgumentException("node should be a DataSourceFilter");                
        DataSourceFilter that= (DataSourceFilter)n;
        this.setFill(that.getFill());
        this.setValidRange(that.getValidRange());        
        if ( !exclude.contains("filters") ) this.setFilters(that.getFilters());
        if ( !exclude.contains("uri" ) ) this.setUri(that.getUri());
    }

    @Override
    public List<Diff> diffs(DomNode node) {
        DataSourceFilter that= (DataSourceFilter)node;
        if ( !( node instanceof DataSourceFilter ) ) throw new IllegalArgumentException("node should be a DataSourceFilter");                
        
        List<Diff> result = super.diffs(node);
        
        boolean b;
        
        b = that.uri == null ? this.uri == null : that.uri.equals(this.uri);
        if (!b) result.add( new PropertyChangeDiff( "uri", that.uri, this.uri ) );
        
        b = that.validRange.equals(this.validRange);
        if (!b) result.add(new PropertyChangeDiff( "validRange", that.validRange , (this.validRange)));

        b = that.fill.equals(this.fill);
        if (!b) result.add(new PropertyChangeDiff( "fill", that.fill , (this.fill)));
        
        b = that.filters.equals(this.filters);
        if (!b) result.add(new PropertyChangeDiff( "filters", that.filters , (this.filters)));
        
        return result;
    }

    @Override
    public String toString() {
        return super.toString() + " ("+this.getUri()+")";
    }
        
}
