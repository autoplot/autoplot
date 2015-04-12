/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.das2.DasApplication;
import org.das2.datum.LoggerManager;
import org.das2.util.AboutUtil;
import org.virbo.autoplot.scriptconsole.GuiExceptionHandler;
import org.virbo.datasource.AutoplotSettings;

/**
 * This utility regularly posts events on the event thread, and measures processing time.
 * This should never be more than 500ms (warnLevel below).
 * See org.das2.util.awt.LoggingEventQueue, which was a similar experiment from 2005.
 * This now monitors the event thread for hung events.
 *
 * Bugs found: http://sourceforge.net/p/autoplot/bugs/863/
 *
 * @author jbf
 */
public final class EventThreadResponseMonitor {

    private static final Logger logger= LoggerManager.getLogger("autoplot.splash");
    
    private long lastPost;
    private long response;
    private Thread eventQueue; // This is the event thread we are monitoring.

    private Map<String,Object> map; // various information about the process.
    
    private static final int TEST_CLEAR_EVENT_QUEUE_PERIOD_MILLIS = 100;
    private static final int WARN_LEVEL_MILLIS= 500; // acceptable millisecond delay in processing
    private static final int ERROR_LEVEL_MILLIS= 10000; // unacceptable delay in processing, and an error is submitted.
    private static final int WATCH_INTERVAL_MILLIS = 1000;

    public EventThreadResponseMonitor( ) {
        this.map= new HashMap();
    }

    public void start() {
        new Thread( createRunnable(), "eventThreadResponseMonitor"  ).start();
        new Thread( watchEventThreadRunnable(), "watchEventThread" ).start();
    }
    
    /**
     * add to the information.
     * @param key see GuiExceptionManager
     * @param value 
     */
    public void addToMap( String key, Object value ) {
        this.map.put( key, value );
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
            buf.append( evt ).append("\n");
            //buf.append("[ ").append(evt.getSource()).append("->").append(evt).append("]\n");
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
            @Override
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

                        String pending= dumpPendingEvents();
                        SwingUtilities.invokeAndWait( responseRunnable(pending) );
                        
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
    private Runnable responseRunnable( final String pending ) {
        return new Runnable() {
            @Override
            public void run() {
                response= System.currentTimeMillis();
                long levelms= response-lastPost;
                eventQueue= Thread.currentThread();
                if ( levelms>WARN_LEVEL_MILLIS ) {
                    logger.log(Level.FINE, "CURRENT EVENT QUEUE CLEAR TIME: {0} sec\n", levelms/1000);
                    if ( pending!=null ) {
                        logger.log(Level.FINE, "events pending:\n");
                        logger.log(Level.FINE, pending );
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
     * @return the Runnable
     */
    Runnable watchEventThreadRunnable() {
        return new Runnable() {
            @Override
            public void run() {
                AWTEvent currentEvent= null;
                String reportedEventId= "";      // toString showing the last reported error.
                long currentRequestStartTime= 0; // roughly the start time of the current request, when it looks like it is slow

                while (true) {
                    EventQueue instance= Toolkit.getDefaultToolkit().getSystemEventQueue();
                    AWTEvent test= instance.peekEvent(); // Ed says: one peekEvent for every getSystemEventQueue.
                    if ( currentEvent!=null && test==currentEvent ) { // we should have processed this event by now.
                        logger.log(Level.FINE, "====  long job to process ====");
                        logger.log(Level.FINE, test.toString() );
                        logger.log(Level.FINE, "====  end, long job to process ====");

                        String eventId= test.toString();
                        boolean hungProcess= System.currentTimeMillis()-currentRequestStartTime > ERROR_LEVEL_MILLIS;
                        if ( hungProcess && ! eventId.equals(reportedEventId) ) {

                            logger.log(Level.INFO, "PATHOLOGICAL EVENT QUEUE CLEAR TIME, WRITING REPORT TO autoplot_data/logs/...\n" );

                            Date now= new Date();
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
                            String timeStamp= sdf.format( now );

                            if ( !DasApplication.hasAllPermission() ) {
                                return;
                            }

                            String id= System.getProperty("user.name");

                            map.put( GuiExceptionHandler.USER_ID, id );
                            
                            try {
                                List<String> bis = AboutUtil.getBuildInfos();
                                map.put( GuiExceptionHandler.BUILD_INFO, bis );
                            } catch (IOException ex) {
                                logger.log(Level.SEVERE, ex.getMessage(), ex);
                            }
                            int appCount= AppManager.getInstance().getApplicationCount();
                            map.put( GuiExceptionHandler.APP_COUNT, appCount );
                                                                
                            String s= GuiExceptionHandler.formatReport( map, false, "Autoplot detected hang" );
                            int hash= eventQueue==null ? 1 : GuiExceptionHandler.hashCode( eventQueue.getStackTrace() );
                            
                            String fname= String.format("hang_%010d_%s_%s.xml", Integer.valueOf(hash), timeStamp, id.replaceAll(" ","_") );

                            File logdir= new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ), "log" );
                            if ( !logdir.exists() ) {
                                if ( !logdir.mkdirs() ) {
                                    return;
                                }
                            }

                            File f= new File( logdir, fname );
                            PrintWriter out=null;
                            try {
                                out= new PrintWriter( new FileOutputStream(f) );
                                out.write(s);
                            } catch ( IOException ex ) {
                                logger.log( Level.WARNING, null, ex );
                            } finally {
                                if ( out!=null ) out.close();
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
