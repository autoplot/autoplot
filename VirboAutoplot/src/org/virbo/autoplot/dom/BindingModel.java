/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 *
 * @author jbf
 */
public class BindingModel {

    public BindingModel( String context, String src, String srcProp, String dst, String dstProp ) {
        this.bindingContextId= context;
        this.srcId= src;
        this.srcProperty= srcProp;
        this.dstId= dst;
        this.dstProperty= dstProp;
    }

    protected String bindingContextId = "";

    public String getBindingContextId() {
        return bindingContextId;
    }


    protected String srcId = "";

    public String getSrcId() {
        return srcId;
    }

    protected String dstId="";

    public String getDstId() {
        return dstId;
    }



    protected String srcProperty;

    public String getSrcProperty() {
        return srcProperty;
    }



    protected String dstProperty;

    public String getDstProperty() {
        return dstProperty;
    }

    @Override
    public String toString() {
        return ""+ srcId + "."+ srcProperty + " to " + dstId + "." + dstProperty + "  ("+bindingContextId +")";
    }

    @Override
    public boolean equals(Object obj) {
        if ( obj==null || !(obj instanceof BindingModel) ) return false;
        BindingModel that= (BindingModel)obj;
        if (that.getSrcId().equals(this.getSrcId()) && that.getDstId().equals(this.getDstId()) 
                && that.getSrcProperty().equals(this.getSrcProperty()) && that.getDstProperty().equals(this.getDstProperty())) return true;
        if (that.getSrcId().equals(this.getDstId()) && that.getDstId().equals(this.getSrcId()) 
                && that.getSrcProperty().equals(this.getDstProperty()) && that.getDstProperty().equals(this.getSrcProperty())) return true;
        return false;
        
    }

    @Override
    /**
     * note equal objects must have the same hashcode.
     */
    public int hashCode() {
        return srcId.hashCode() + srcProperty.hashCode()  + dstId.hashCode() + dstProperty.hashCode();
    }
    
    private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

}
