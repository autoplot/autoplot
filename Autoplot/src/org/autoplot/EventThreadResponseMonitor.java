
package org.autoplot;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.das2.DasApplication;
import org.das2.datum.LoggerManager;
import org.das2.util.AboutUtil;
import org.autoplot.scriptconsole.GuiExceptionHandler;
import org.autoplot.datasource.AutoplotSettings;

/**
 * This utility regularly posts events on the event thread, and measures processing time.
 * This should never be more than 500ms (warnLevel below).
 * See org.das2.util.awt.LoggingEventQueue, which was a similar experiment from 2005.
 * This now monitors the event thread for hung events.
 *
 * Bugs found: https://sourceforge.net/p/autoplot/bugs/863/
 *
 * If the file HOME/autoplot_data/log/request_dump.txt is found, this will trigger a heap dump
 * and the file will be deleted.
 * 
 * @author jbf
 */
public final class EventThreadResponseMonitor {

    private static final Logger logger= LoggerManager.getLogger("autoplot.splash");
    
    private long lastPost; // roughly the start time of the current request, when it looks like it is slow
    private long response;
    private Thread eventQueue; // This is the event thread we are monitoring.

    private final Map<String,Object> map; // various information about the process.
    
    private static final int TEST_CLEAR_EVENT_QUEUE_PERIOD_MILLIS = 300;
    private static final int WARN_LEVEL_MILLIS= 500; // acceptable millisecond delay in processing
    private static final int ERROR_LEVEL_MILLIS= 10000; // unacceptable delay in processing, and an error is submitted.
    private static final int WATCH_INTERVAL_MILLIS = 1000;
    
    private static final File LOG_DIR= new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ), "log" );
    private static final File REQUEST_DUMP_FILE= new File( LOG_DIR, "request_dump.txt" );
            
    ScheduledThreadPoolExecutor exec;
    
    AWTEvent currentEvent= null;    
    String reportedEventId= "";      // toString showing the last reported error.
    boolean pleasePostEvent= true;   // another event ought to be posted.
    
    PrintStream outs;
   
    public EventThreadResponseMonitor( ) {
        this.map= new HashMap();
        lastPost= System.currentTimeMillis();
        outs= null;
    }

    public void start() {
        //new Thread( createRunnable(), "eventThreadResponseMonitor"  ).start();
        //new Thread( watchEventThreadRunnable(), "watchEventThread" ).start();
        logger.info("Starting EventThreadResponseMonitor, which should have a trivial effect on performance.");
        logger.log(Level.INFO, "Warnings will be written to {0}", new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ), "log" ));
        exec= new ScheduledThreadPoolExecutor(1);
        exec.scheduleAtFixedRate( maybeCreateEventThreadRunnable(), 4000, TEST_CLEAR_EVENT_QUEUE_PERIOD_MILLIS, TimeUnit.MILLISECONDS );
        exec.scheduleAtFixedRate( checkEventThreadRunnable(), 4000, WATCH_INTERVAL_MILLIS, TimeUnit.MILLISECONDS );
    }
    
    /**
     * set to the name of a file where event thread response times will be logged.
     * This should be a folder local to the machine.
     * @param f 
     */
    public void setLogFile( File f ) {
        if ( outs!=null ) {
            outs.close();
        }
        if ( f!=null ) {
            try {
                outs= new PrintStream( new FileOutputStream( f ) );
            } catch (FileNotFoundException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }
            
    /**
     * add to the information.
     * @param key see GuiExceptionManager
     * @param value 
     */
    public void addToMap( String key, Object value ) {
        this.map.put( key, value );
    }

    /**
     * show all the events on the event thread by unqueuing and requeuing them.  This
     * should not be used in production use.
     * @return String representation
     */
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

    /**
     * This runnable posts the marker runnable on to the event thread.  This runnable's 
     * lifetime will be that of the application...
     * 
     * @return the runnable.
     */
    private Runnable maybeCreateEventThreadRunnable() {
        return () -> {
            synchronized ( EventThreadResponseMonitor.this ) {
                if ( pleasePostEvent ) {
                    pleasePostEvent= false;
                    lastPost= System.currentTimeMillis();
                    SwingUtilities.invokeLater( responseRunnable("") );
                }
            }
        };
    }

    /**
     * the response runnable simply measures how long it takes for an event to be processed by the event thread.
     * This is the runnable that is put onto the event thread.
     * @return a Runnable.
     */
    private Runnable responseRunnable( final String pending ) {
        return () -> {
            response= System.currentTimeMillis();
            long levelms= response-lastPost;
            if ( outs!=null ) {
                outs.println( String.format( "%16d %d", response, levelms ) );
            }
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
            pleasePostEvent= true;
            logger.log(Level.FINER, "eventQueue clear time: {0}", levelms);
            //pending= dumpPendingEvents();
            //TODO: this would be the correct time to dumpPendingEvents.
        };
    }

    /**
     * check that a request has been made by touching the file REQUEST_DUMP_FILE.
     * @return 
     */
    private boolean checkRequestDump() {
        if ( !LOG_DIR.exists() ) return false;
        return REQUEST_DUMP_FILE.exists();                    
    }
    
    /**
     * the watchEventThreadRunnable watches the current event on the event thread, and if it doesn't change within a given
     * interval (WATCH_INTERVAL_MILLIS) start printing errors and eventually (ERROR_LEVEL_MILLIS) log an error because the thread appears to
     * be hung.
     * @return the Runnable
     */
    private Runnable checkEventThreadRunnable() {
        return () -> {
            EventQueue instance= Toolkit.getDefaultToolkit().getSystemEventQueue();
            AWTEvent test= instance.peekEvent(); // Ed says: one peekEvent for every getSystemEventQueue.
            boolean dumpRequested= checkRequestDump();
                    
            if ( dumpRequested || ( currentEvent!=null && test==currentEvent ) ) { // we should have processed this event by now.
                logger.log(Level.FINE, "====  long job to process ====");
                logger.log(Level.FINE, test==null ? "NULL" : test.toString() );
                logger.log(Level.FINE, "====  end, long job to process ====");
                
                if ( dumpRequested ) {
                    if ( !REQUEST_DUMP_FILE.delete() ) {
                        logger.log(Level.WARNING, "unable to delete {0}", REQUEST_DUMP_FILE);
                        dumpRequested= false; // don't write out an xml file every four seconds.
                    } else {
                        logger.log(Level.INFO, "found {0}, dumping", REQUEST_DUMP_FILE);
                    }
                }
                
                
                String eventId= test==null ? "000" : test.toString();
                boolean hungProcess= System.currentTimeMillis()-lastPost > ERROR_LEVEL_MILLIS;
                if ( dumpRequested || ( hungProcess && ! eventId.equals(reportedEventId) ) ) {
                    
                    if ( dumpRequested ) {
                        logger.log(Level.INFO, "DUMP REQUESTED, WRITING REPORT TO autoplot_data/log/...\n" );
                    } else {
                        logger.log(Level.INFO, "PATHOLOGICAL EVENT QUEUE CLEAR TIME, WRITING REPORT TO autoplot_data/log/...\n" );
                    }
                    
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
                    
                    String fname= String.format("hang_%010d_%s_%s.xml", hash, timeStamp, id.replaceAll(" ","_") );
                    
                    File logdir= LOG_DIR;
                    if ( !logdir.exists() ) {
                        if ( !logdir.mkdirs() ) {
                            return;
                        }
                    }
                    
                    File f= new File( logdir, fname );
                    try (PrintWriter out = new PrintWriter( new FileOutputStream(f) )) {
                        out.write(s);
                    } catch ( IOException ex ) {
                        logger.log( Level.WARNING, null, ex );
                    }
                    if ( f.setReadable( false, false ) ) {                        
                        if ( !f.setReadable( true ) ) {
                            logger.info("unable to set read permissions to owner only");
                        }
                    }
                    if ( f.setWritable( false, false ) ) {                        
                        if ( !f.setWritable( true ) ) {
                            logger.info("unable to set read permissions to owner only");
                        }
                    }
                    
                    reportedEventId= eventId;
                    
                    currentEvent= null;
                    
                }
            }
            
            currentEvent=  test;
        };
    }

    public static void main( String[] args ) {
        new EventThreadResponseMonitor().start();
    }
}
