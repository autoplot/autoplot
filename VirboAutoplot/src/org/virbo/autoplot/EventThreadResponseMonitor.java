/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.Queue;
import javax.swing.SwingUtilities;

/**
 * This utility regularly posts events on the event thread, and measures processing time.
 * This should never be more than 500ms (warnLevel below).
 * See org.das2.util.awt.LoggingEventQueue, which was a similar experiment from 2005.
 * @author jbf
 */
public final class EventThreadResponseMonitor {

    long lastPost;
    long response;
    String pending;

    int testFrequency = 300;
    int warnLevel= 500; // acceptable millisecond delay in processing

    public EventThreadResponseMonitor() {
        
    }

    public void start() {
        new Thread( createRunnable(), "eventThreadResponseMonitor"  ).start();
    }

    public static synchronized String dumpPendingEvents() {
        StringBuilder buf= new StringBuilder();
        Queue queue= new LinkedList();
        AWTEvent evt;

        EventQueue instance= Toolkit.getDefaultToolkit().getSystemEventQueue();
        if ( instance.peekEvent()!=null ) {
            buf.append("---------------------------------------------------------------\n");
        }
        
        while ( instance.peekEvent()!=null ) {
            try {
                evt= instance.getNextEvent();
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
            buf.append("[ ").append(evt.getSource()).append("->").append(evt).append("]\n");
            queue.add(evt);
        }

        if ( instance.peekEvent()!=null ) buf.append("-----e--n--d-----------------------------------------------------");
        while ( queue.size() > 0 ) {
            instance.postEvent((AWTEvent)queue.remove());
        }
        return buf.toString();
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

                        //System.err.print( dumpPendingEvents() );

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
                    if ( pending!=null ) {
                        System.err.printf( "events pending:\n");
                        System.err.printf( pending );
                    }
                    
                } else {
                    //System.err.printf( "current event queue clear time: %5.3f sec\n", levelms/1000. );
                }

                //pending= dumpPendingEvents();
                //TODO: this would be the correct time to dumpPendingEvents.
            }
        };
    }

    public static void main( String[] args ) {
        new EventThreadResponseMonitor().start();
    }
}
