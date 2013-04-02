/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.datasource;

import java.util.HashMap;
import java.util.Map;

/**
 * Brought in as things are removed from the das2 threads, and a number of
 * processes are assumed to be run just once.
 * 
 * @author jbf
 */
public class ThreadManager {
    
    private Map<String,Thread> active;
    
    private static ThreadManager instance;
    
    ThreadManager() {
        active= new HashMap<String, Thread>();
    }
    
    public static synchronized ThreadManager getInstance() {
        if ( instance==null ) {
            instance= new ThreadManager();
        }
        return instance;
    }
    
    private Runnable wrapRunnable( final Runnable run, final String t ) {
        return new Runnable() {
            public void run() {
                run.run();
                synchronized (this) {
                    active.remove(t);
                }
            }
        };
    }
    public synchronized boolean run( Runnable run, String t ) {
        if ( active.get(t)==null ) {
            Thread wrun= new Thread( wrapRunnable(run,t), t );
            active.put( t, wrun );
            wrun.start();
            return true;
        } else {
            return false;
        }
    }
}
