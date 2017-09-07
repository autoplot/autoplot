
package org.autoplot.util;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import org.das2.system.RequestProcessor;

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
    boolean immediatelyAfter;

    int invocationCount=0;
    //private static int instanceCount=0;

    /**
     *
     * @param propertyName the property name we listen to.
     * @param node the object that we listen to.
     * @param immediatelyAfter run this on the change notification thread.  If false, a new thread is used to run this.
     */
    public RunLaterListener( String propertyName, PropertyChange node, boolean immediatelyAfter ) {
        //instanceCount++;

        this.node= node;
        this.propertyName= propertyName;
        this.immediatelyAfter= immediatelyAfter;

        if ( propertyName!=null ) {
            this.node.addPropertyChangeListener( propertyName, this );
        } else {
            this.node.addPropertyChangeListener( this );
        }
        
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        invocationCount++;
        //if ( invocationCount>1 ) {
            //throw new IllegalArgumentException("this doesn't work");
        //}
        if ( propertyName!=null ) {
            node.removePropertyChangeListener(propertyName,this);
        } else {
            node.removePropertyChangeListener(this);
        }
        if ( invocationCount>1 ) {
            return;
        }
        if ( immediatelyAfter ) {
            run();
        } else {
            RequestProcessor.invokeLater(this);
        }
        
    }

    @Override
    public abstract void run();

}
