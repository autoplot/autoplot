/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.dom;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeListenerProxy;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author jbf
 */
public class DebugPropertyChangeSupport extends PropertyChangeSupport {

    static long t0= System.currentTimeMillis();
    
    String myBean;
    public long t= System.currentTimeMillis() - t0;
    
    List<String> propNames= new ArrayList();

    public DebugPropertyChangeSupport( Object bean ) {
        super(bean);
        myBean= bean.toString();
    }

    @Override
    public synchronized void addPropertyChangeListener(PropertyChangeListener listener) {
        //TODO: danger--remove this from production code.
        if ( Arrays.asList(getPropertyChangeListeners()).contains( listener ) ) {
            return;
        }
        super.addPropertyChangeListener(listener);
        if ( listener!=null ) propNames.add( listener.toString() );
    }

    @Override
    public synchronized void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        super.addPropertyChangeListener(propertyName, listener);
        if ( listener!=null ) propNames.add( listener.toString()+ " " + propertyName );
    }

    @Override
    public synchronized void removePropertyChangeListener(PropertyChangeListener listener) {
        super.removePropertyChangeListener(listener);
        if ( listener!=null ) propNames.remove( listener.toString() );
    }

    @Override
    public synchronized void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        super.removePropertyChangeListener(propertyName, listener);
        //TODO: possible bug: sometimes with TSBs listener is null.
        if ( listener!=null ) propNames.remove( listener.toString()+ " " + propertyName );
    }


    @Override
    public String toString() {
        PropertyChangeListener[] listeners= getPropertyChangeListeners();
        StringBuilder result= new StringBuilder(super.toString());
        for ( int i=0; i<listeners.length; i++ ) {
            if ( listeners[i] instanceof PropertyChangeListenerProxy ) {
                PropertyChangeListenerProxy proxy= (PropertyChangeListenerProxy)listeners[i];
                result.append("\n").append(proxy.getListener()).append(" (property ").append(proxy.getPropertyName()).append(")");
            } else {
                result.append("\n").append(listeners[i]);
            }
        }
        return result.toString();
    }
    
    /**
     * return a list of the explicit properties we are listening to.
     * @return 
     */
    public synchronized String[] getPropNames() {
        return propNames.toArray( new String[propNames.size()] );
    }
    
}
