/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import java.lang.reflect.InvocationTargetException;
import javax.swing.SwingUtilities;

/**
 * This utility regularly posts events on the event thread, and measures processing time.
 * This should never be more than 500ms (warnLevel below).
 * @author jbf
 */
public final class EventThreadResponseMonitor {

    long lastPost;
    long response;

    int testFrequency = 300;
    int warnLevel= 500; // acceptable millisecond delay in processing

    public EventThreadResponseMonitor() {
        
    }

    public void start() {
        new Thread( createRunnable(), "eventThreadResponseMonitor"  ).start();
    }
    
    Runnable createRunnable() {
        lastPost= System.currentTimeMillis();
        return new Runnable() {
            public void run() {
                while ( true ) {
                    try {
                        long nextPost= lastPost+testFrequency;
                        long sleep= nextPost - System.currentTimeMillis();
                        while ( sleep > 0 ) {
                            Thread.sleep( sleep );
                            sleep= nextPost - System.currentTimeMillis();
                        }
                        lastPost= System.currentTimeMillis();
                        SwingUtilities.invokeAndWait( responseRunnable() );
                    } catch ( InterruptedException ex ) {

                    } catch ( InvocationTargetException ex ) {

                    }
                }
            }
        };
    }

    Runnable responseRunnable() {
        return new Runnable() {
            public void run() {
                response= System.currentTimeMillis();
                long levelms= response-lastPost;

                if ( levelms>warnLevel ) {
                    System.err.printf( "CURRENT EVENT QUEUE CLEAR TIME: %5.3f sec\n", levelms/1000. );
                    //TODO: try to identify what caused delay
                } else {
                    //System.err.printf( "current event queue clear time: %5.3f sec\n", levelms/1000. );
                }
            }
        };
    }

    public static void main( String[] args ) {
        new EventThreadResponseMonitor().start();
    }
}
