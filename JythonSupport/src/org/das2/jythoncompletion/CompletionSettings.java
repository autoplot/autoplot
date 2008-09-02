/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.das2.jythoncompletion;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 *
 * @author jbf
 */
public class CompletionSettings {

    protected String docHome = "http://www.autoplot.org/javadoc/javadoc/";
    public static final String PROP_DOCHOME = "docHome";

    public String getDocHome() {
        return docHome;
    }

    public void setDocHome(String docHome) {
        String oldDocHome = docHome;
        this.docHome = docHome;
        propertyChangeSupport.firePropertyChange(PROP_DOCHOME, oldDocHome, docHome);
    }
    private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

}
