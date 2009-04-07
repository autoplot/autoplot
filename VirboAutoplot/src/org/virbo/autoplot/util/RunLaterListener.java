/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.util;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * maybe a handy class to have something run later.
 * @author jbf
 */
public abstract class RunLaterListener implements PropertyChangeListener, Runnable {

    public interface PropertyChange {
        public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener);
        public void addPropertyChangeListener(PropertyChangeListener listener);
        public void removePropertyChangeListener( PropertyChangeListener list );
        public void removePropertyChangeListener( String propertyName, PropertyChangeListener list );
    }
    
    PropertyChange node;
    String propertyName;

    int invocationCount=0;

    public RunLaterListener( String propertyName, PropertyChange node ) {
        if ( propertyName!=null ) {
            node.addPropertyChangeListener( propertyName, this );
        } else {
            node.addPropertyChangeListener( this );
        }
        this.node= node;
        this.propertyName= propertyName;
    }

    public void propertyChange(PropertyChangeEvent evt) {
        if ( propertyName!=null ) {
            node.removePropertyChangeListener(propertyName,this);
        } else {
            node.removePropertyChangeListener(this);
        }
        invocationCount++;
        if ( invocationCount>1 ) {
            throw new IllegalArgumentException("this doesn't work");
        }
        run();
    }

    public abstract void run();

}
