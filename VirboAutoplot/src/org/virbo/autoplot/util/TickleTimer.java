/*
 * TickleTimer.java
 *
 * Created on July 28, 2006, 9:23 PM
 *
 */

package org.virbo.autoplot.util;

import java.beans.PropertyChangeListener;
import java.util.logging.Logger;
import javax.swing.event.ChangeEvent;

/**
 * TickleTimer is a timer that fires once it's been left alone for 
 * a while.  The idea is the keyboard can be pecked away and 
 * the change event will not be fired until the keyboard is idle.
 *
 * TODO: check relationship to java.util.Timer, which might subsume the functionality
 * @author Jeremy Faden
 */
public class TickleTimer {
    long tickleTime;
    long delay;
    PropertyChangeListener listener;
    boolean running;
    
    static final Logger log= Logger.getLogger("pvwave");
    
    /**
     * @param delay time in milliseconds to wait until firing off the change.  
     *   If delay is =<0, then events will be fired off immediately.
     * @param PropertyChangeListener provides the callback when the timer 
     *    runs out.  The listener is added as one of the bean's property
     *    change listeners.
     */
    public TickleTimer( long delay, PropertyChangeListener listener ) {
        this.tickleTime= System.currentTimeMillis();
        this.delay= delay;
        addPropertyChangeListener( listener );
        this.running= false;
    }
    private void startTimer() {
        running= true;
        if ( delay<=0 ) {
            newRunnable().run();
        } else {
            new Thread( newRunnable(), "tickleTimerThread" ).start();
        }
    }
    
    private Runnable newRunnable() {
        return new Runnable() {
            public void run() {
                long d=  System.currentTimeMillis() - tickleTime;
                while ( d < delay ) {
                    try {
                        log.finer("tickleTimer sleep "+(delay-d));
                        Thread.sleep( delay-d );
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                    d= System.currentTimeMillis() - tickleTime;
                }
                log.finer("tickleTimer fire after "+(d));
                propertyChangeSupport.firePropertyChange("running",true,false);
                running= false;
            }
        };
    }
    
    public synchronized void tickle(){
        tickleTime= System.currentTimeMillis();
        if ( !running ) startTimer();
    }

    /**
     * Utility field used by bound properties.
     */
    private java.beans.PropertyChangeSupport propertyChangeSupport =  new java.beans.PropertyChangeSupport(this);

    /**
     * Adds a PropertyChangeListener to the listener list.
     * @param l The listener to add.
     */
    public void addPropertyChangeListener(java.beans.PropertyChangeListener l) {
        propertyChangeSupport.addPropertyChangeListener(l);
    }

    /**
     * Removes a PropertyChangeListener from the listener list.
     * @param l The listener to remove.
     */
    public void removePropertyChangeListener(java.beans.PropertyChangeListener l) {
        propertyChangeSupport.removePropertyChangeListener(l);
    }

    /**
     * Getter for property running.
     * @return Value of property running.
     */
    public boolean isRunning() {
        return this.running;
    }

    /**
     * Setter for property running.
     * @param running New value of property running.
     * 
     * @throws PropertyVetoException if some vetoable listeners reject the new value
     */
    public void setRunning(boolean running) throws java.beans.PropertyVetoException {
        boolean oldRunning = this.running;
        this.running = running;
        propertyChangeSupport.firePropertyChange ("running", new Boolean (oldRunning), new Boolean (running));
    }
}
