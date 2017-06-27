/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.util.List;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.das2.datum.LoggerManager;
import org.das2.event.DataRangeSelectionEvent;
import org.das2.event.DataRangeSelectionListener;
import org.autoplot.bookmarks.Bookmark;
import org.autoplot.bookmarks.BookmarksException;
import org.autoplot.datasource.DataSetSelector;
import org.autoplot.datasource.TimeRangeToolEventsList;
import org.autoplot.datasource.WindowManager;
import org.xml.sax.SAXException;

/**
 *
 * @author jbf
 */
public class EventsListToolUtil {
    
    private static final Logger logger= LoggerManager.getLogger("autoplot.events");
    
    public static void deflts( DataSetSelector sel ) {
        
        final String sdeft= "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
"<bookmark-list version=\"1.1\">\n" +
"    <bookmark>\n" +
"        <title>RBSP-A EMFISIS Waveform Events</title>\n" +
"        <uri>http://emfisis.physics.uiowa.edu/events/rbsp-a/burst/rbsp-a_burst_times_$Y$m$d.txt?eventListColumn=field3&amp;column=field0&amp;timerange=2013-03-03</uri>\n" +
"    </bookmark>\n" +
"    <bookmark>\n" +
"        <title>RBSP-B EMFISIS Waveform Events</title>\n" +
"        <uri>http://emfisis.physics.uiowa.edu/events/rbsp-b/burst/rbsp-b_burst_times_$Y$m$d.txt?eventListColumn=field3&amp;column=field0&amp;timerange=2013-03-03</uri>\n" +
"    </bookmark>\n" +
"</bookmark-list>";


        List<Bookmark> deft=null;
        try {
            deft = Bookmark.parseBookmarks(sdeft);

        } catch (BookmarksException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        } catch (SAXException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }

        org.autoplot.bookmarks.Util.loadRecent( "eventsRecent", sel, deft );
        
    }
    
    private static final WeakHashMap<AutoplotUI,JDialog> instances= new WeakHashMap();
    private static final WeakHashMap<AutoplotUI,TimeRangeToolEventsList> instances2= new WeakHashMap();
    
    /**
     * this must be called on the event thread.
     * @param t 
     */
    public static void show( final AutoplotUI t ) {
        
        if ( !EventQueue.isDispatchThread() ) {
            throw new IllegalArgumentException("must be called from the event thread");
        }
        JDialog dialog= instances.get(t);
              
        if ( dialog==null ) {
            getEventsList( t );
            dialog= instances.get(t);
        }
        String uri= t.getDocumentModel().getEventsListUri();
        if ( uri!=null && uri.length()>0 ) {
            instances2.get(t).getDataSetSelector().setValue(uri);
            instances2.get(t).getDataSetSelector().maybePlot(0);
        }
        
        dialog.setVisible(true);
    }
    
    /**
     * set the location of the events list we should use.  <code>show(t)</code> 
     * should be called to show the GUI.
     * @param t the app
     * @param uri the location of the events list.
     */
    public static void setEventsListURI( final AutoplotUI t, String uri ) {
        
        if ( !EventQueue.isDispatchThread() ) {
            throw new IllegalArgumentException("must be called from the event thread");
        }
        
        JDialog dialog= instances.get(t);
        if ( dialog==null ) {
            getEventsList( t ); // create events list GUI.
            //dialog= instances.get(t);
        }
        //dialog.setVisible(true);
        
        instances2.get(t).getDataSetSelector().setValue(uri);
        instances2.get(t).getDataSetSelector().maybePlot(0);
        
    }
    
    /**
     * find the GUI for this application, creating one if necessary.
     * @param t the app.
     * @return the single TimeRangeToolEventsList for the app.
     */
    public static TimeRangeToolEventsList getEventsList( final AutoplotUI t ) {
        JDialog dialog= instances.get(t);
        if ( dialog==null ) {
            final JDialog d= new JDialog( t, "Events List");
            d.setName("eventsListTool");
            d.setModal(false);
            
            final TimeRangeToolEventsList ll= new TimeRangeToolEventsList();
            
            ll.addDataRangeSelectionListener( new DataRangeSelectionListener() {
                @Override
                public void dataRangeSelected(DataRangeSelectionEvent e) {
                    t.applicationModel.dom.setTimeRange( e.getDatumRange() );
                }
            });
            
            ll.getDataSetSelector().addActionListener( new ActionListener() {
                @Override
                public void actionPerformed( ActionEvent ev ) {
                    t.getDocumentModel().setEventsListUri( ll.getDataSetSelector().getValue() );
                }
            });
            
            deflts( ll.getDataSetSelector() );
            ll.getDataSetSelector().setValue("");
            
            d.setDefaultCloseOperation( JFrame.HIDE_ON_CLOSE );
            d.getContentPane().add( ll );
            d.pack();
            d.setLocationRelativeTo(t);
            
            WindowManager.getInstance().recallWindowSizePosition(d);
       
            d.addWindowListener( new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    super.windowClosing(e); //To change body of generated methods, choose Tools | Templates.
                    WindowManager.getInstance().recordWindowSizePosition(d);
                }
            } );
            
            instances.put( t, d );
            instances2.put( t, ll);
  
        }
        
        return instances2.get(t);
    }
}
