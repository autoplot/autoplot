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

    protected String bindingContextId = null;
    public static final String PROP_BINDINGCONTEXTID = "bindingContextId";

    public String getBindingContextId() {
        return bindingContextId;
    }

    public void setBindingContextId(String bindingContextId) {
        String oldBindingContextId = this.bindingContextId;
        this.bindingContextId = bindingContextId;
        propertyChangeSupport.firePropertyChange(PROP_BINDINGCONTEXTID, oldBindingContextId, bindingContextId);
    }

    protected String srcId = null;
    public static final String PROP_SOURCEID = "srcId";

    public String getSrcId() {
        return srcId;
    }

    public void setSrcId(String sourceId) {
        String oldSourceId = this.srcId;
        this.srcId = sourceId;
        propertyChangeSupport.firePropertyChange(PROP_SOURCEID, oldSourceId, sourceId);
    }



    protected String dstId;
    public static final String PROP_DESTID = "dstId";

    public String getDstId() {
        return dstId;
    }

    public void setDstId(String destId) {
        String oldDestId = this.dstId;
        this.dstId = destId;
        propertyChangeSupport.firePropertyChange(PROP_DESTID, oldDestId, destId);
    }


    protected String srcProperty;
    public static final String PROP_SRCPROPERTY = "srcProperty";

    public String getSrcProperty() {
        return srcProperty;
    }

    public void setSrcProperty(String srcProperty) {
        String oldSrcProperty = this.srcProperty;
        this.srcProperty = srcProperty;
        propertyChangeSupport.firePropertyChange(PROP_SRCPROPERTY, oldSrcProperty, srcProperty);
    }


    protected String dstProperty;
    public static final String PROP_DSTPROPERTY = "dstProperty";

    public String getDstProperty() {
        return dstProperty;
    }

    public void setDstProperty(String dstProperty) {
        String oldDstProperty = this.dstProperty;
        this.dstProperty = dstProperty;
        propertyChangeSupport.firePropertyChange(PROP_DSTPROPERTY, oldDstProperty, dstProperty);
    }


    public String toString() {
        return ""+ srcId + "."+ srcProperty + " <--> " + dstId + "." + dstProperty + "  ("+bindingContextId +")";
    }

    private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

}
