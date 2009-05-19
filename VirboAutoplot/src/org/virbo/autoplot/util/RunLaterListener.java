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
    private static int instanceCount=0;

    public RunLaterListener( String propertyName, PropertyChange node ) {
        instanceCount++;

        this.node= node;
        this.propertyName= propertyName;

        if ( propertyName!=null ) {
            this.node.addPropertyChangeListener( propertyName, this );
        } else {
            this.node.addPropertyChangeListener( this );
        }
        
    }

    public void propertyChange(PropertyChangeEvent evt) {
        invocationCount++;
        if ( invocationCount>1 ) {
            //throw new IllegalArgumentException("this doesn't work");
        }
        if ( propertyName!=null ) {
            node.removePropertyChangeListener(propertyName,this);
        } else {
            node.removePropertyChangeListener(this);
        }
        if ( invocationCount>1 ) {
            return;
        }
        run();
    }

    public abstract void run();

}
