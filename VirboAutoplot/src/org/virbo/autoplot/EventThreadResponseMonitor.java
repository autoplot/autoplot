/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import javax.swing.SwingUtilities;
import org.das2.DasApplication;
import org.virbo.datasource.AutoplotSettings;

/**
 * This utility regularly posts events on the event thread, and measures processing time.
 * This should never be more than 500ms (warnLevel below).
 * See org.das2.util.awt.LoggingEventQueue, which was a similar experiment from 2005.
 * @author jbf
 */
public final class EventThreadResponseMonitor {

    private long lastPost;
    private long response;
    private String pending;

    private static final int TEST_CLEAR_EVENT_QUEUE_PERIOD_MILLIS = 300;
    private static final int WARN_LEVEL_MILLIS= 500; // acceptable millisecond delay in processing
    private static final int ERROR_LEVEL_MILLIS= 10000; // unacceptable delay in processing, and an error is submitted.
    private static final int WATCH_INTERVAL_MILLIS = 1000;

    public EventThreadResponseMonitor() {
        
    }

    public void start() {
        new Thread( createRunnable(), "eventThreadResponseMonitor"  ).start();
        new Thread( watchEventThreadRunnable(), "watchEventThread" ).start();
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
                        long nextPost= lastPost+TEST_CLEAR_EVENT_QUEUE_PERIOD_MILLIS;
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

    /**
     * the response runnable simply measures how long it takes for an event to be processed by the event thread.
     * @return
     */
    Runnable responseRunnable() {
        return new Runnable() {
            public void run() {
                response= System.currentTimeMillis();
                long levelms= response-lastPost;

                if ( levelms>WARN_LEVEL_MILLIS ) {
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

    /**
     * the watchEventThreadRunnable watches the current event on the event thread, and if it doesn't change within a given
     * interval (WATCH_INTERVAL_MILLIS) start printing errors and eventually (ERROR_LEVEL_MILLIS) log an error because the thread appears to
     * be hung.
     * @return
     */

    Runnable watchEventThreadRunnable() {
        return new Runnable() {
            public void run() {
                AWTEvent currentEvent= null;
                String reportedEventId= "";      // toString showing the last reported error.
                long currentRequestStartTime= 0; // roughly the start time of the current request, when it looks like it is slow

                while (true) {
                    EventQueue instance= Toolkit.getDefaultToolkit().getSystemEventQueue();
                    AWTEvent test= instance.peekEvent();
                    if ( currentEvent!=null && test==currentEvent ) { // we should have processed this event by now.
                        System.err.println("====  long job to process ====");
                        System.err.println(test);
                        System.err.println("====  end, long job to process ====");

                        String eventId= test.toString();
                        boolean hungProcess= System.currentTimeMillis()-currentRequestStartTime > ERROR_LEVEL_MILLIS;
                        if ( hungProcess && ! eventId.equals(reportedEventId) ) {

                            System.err.printf( "PATHOLOGICAL EVENT QUEUE CLEAR TIME, WRITING REPORT...\n" );

                            Date now= new Date();
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
                            String timeStamp= sdf.format( now );

                            if ( !DasApplication.hasAllPermission() ) {
                                return;
                            }

                            String id= "anon";
                            id= System.getProperty("user.name");

                            String fname= "hang_"+ id.replaceAll(" ","_") + "_"+ timeStamp + ".txt";

                            File logdir= new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ), "log" );
                            if ( !logdir.exists() ) {
                                if ( !logdir.mkdirs() ) {
                                    throw new IllegalStateException("Unable to mkdir "+logdir);
                                }
                            }

                            File f= new File( logdir, fname );
                            Map<Thread,StackTraceElement[]> ttt= Thread.getAllStackTraces();

                            try {
                                PrintWriter out= new PrintWriter( new FileOutputStream(f) );

                                for ( Entry<Thread,StackTraceElement[]> tt : ttt.entrySet() ) {
                                    Thread t= tt.getKey();
                                    StackTraceElement[] stes= tt.getValue();
                                    out.println( t.getName() );
                                    for ( StackTraceElement ste : stes) {
                                        out.println("\tat " + ste);
                                    }
                                    out.println( "\n" );
                                }

                                out.close();
                                
                            } catch ( IOException ex ) {
                                ex.printStackTrace();
                                
                            }

                            reportedEventId= eventId;
                            
                            currentEvent= null;
                            
                        }
                    } else {
                        currentRequestStartTime= System.currentTimeMillis();

                    }

                    if ( test!=currentEvent ) {

                    }
                    currentEvent=  test;
                    try {
                        Thread.sleep(WATCH_INTERVAL_MILLIS);
                    } catch ( InterruptedException ex ) {
                    }
                }
            }
        };
    }

    public static void main( String[] args ) {
        new EventThreadResponseMonitor().start();
    }
}
