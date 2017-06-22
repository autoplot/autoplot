/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.datasource.capability;

import java.beans.PropertyChangeListener;

/**
 * Updating allows the data sources to notify clients that a different dataset
 * will be returned if they call getDataSet again.  Implementations should
 * take into account that excessive update notifications may be ignored.
 *
 * Updates should be made by calling the registered listeners' propertyChange
 * method, with an appropriate PropertyChangeEvent:
 *
 * listener.propertyChange( new PropertyChangeEvent( this, Updating.PROP_DATASET, null, null ) )
 * to force the client to post a new request, or
 *    new PropertyChangeEvent( this, "dataSet", null, ds )
 * if the dataset is trivially available.
 *
 * Developers should consider using java.beans.PropertyChangeSupport to
 * implement this capability, so that it's firePropertyChange method can be used.
 *
 * @author jbf
 */
public interface Updating {
    public static final String PROP_DATASET= "dataSet";
    void addPropertyChangeListener( PropertyChangeListener listener );
    void removePropertyChangeListener( PropertyChangeListener listener );
}
