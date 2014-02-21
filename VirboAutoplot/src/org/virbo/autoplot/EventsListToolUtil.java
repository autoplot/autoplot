/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.List;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.das2.datum.LoggerManager;
import org.das2.event.DataRangeSelectionEvent;
import org.das2.event.DataRangeSelectionListener;
import org.virbo.autoplot.bookmarks.Bookmark;
import org.virbo.autoplot.bookmarks.BookmarksException;
import org.virbo.autoplot.bookmarks.BookmarksManager;
import org.virbo.datasource.DataSetSelector;
import org.virbo.datasource.TimeRangeToolEventsList;
import org.xml.sax.SAXException;

/**
 *
 * @author jbf
 */
public class EventsListToolUtil {
    
    private static final Logger logger= LoggerManager.getLogger("autoplot.events");
    
    private static void deflts( DataSetSelector sel ) {
        
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

        org.virbo.autoplot.bookmarks.Util.loadRecent( "eventsRecent", sel, deft );
        
    }
    
    private static WeakHashMap<AutoplotUI,JDialog> instances= new WeakHashMap();
    private static WeakHashMap<AutoplotUI,TimeRangeToolEventsList> instances2= new WeakHashMap();
    
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
        dialog.setVisible(true);
    }
    
    public static void show( final AutoplotUI t, String uri ) {
        
        if ( !EventQueue.isDispatchThread() ) {
            throw new IllegalArgumentException("must be called from the event thread");
        }
        JDialog dialog= instances.get(t);
              
        if ( dialog==null ) {
            getEventsList( t );
            dialog= instances.get(t);
        }
        dialog.setVisible(true);
        instances2.get(t).getDataSetSelector().setValue(uri);
    }
    
    public static TimeRangeToolEventsList getEventsList( final AutoplotUI t ) {
        JDialog dialog= instances.get(t);
        if ( dialog==null ) {
            JDialog d= new JDialog( t, "Events List");
            d.setModal(false);
            
            final TimeRangeToolEventsList ll= new TimeRangeToolEventsList();
            Icon bookmarkIcon= new javax.swing.ImageIcon(EventsListToolUtil.class.getResource("/resources/purplebookmark.png") );

            ll.getDataSetSelector().replacePlayButton( bookmarkIcon, new AbstractAction("bookmarks") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    BookmarksManager man= new BookmarksManager( (Frame)SwingUtilities.getWindowAncestor(t), true );
                    man.setHidePlotButtons(true);
                    man.setPrefNode( "bookmarks", "autoplot.default.events",  "http://autoplot.org/data/events.xml" );
                    man.setVisible(true);
                    Bookmark book= man.getSelectedBookmark();
                    if ( book!=null ) {
                        ll.getDataSetSelector().setValue( ((Bookmark.Item)book).getUri() );
                    }
                }
            });
            ll.addDataRangeSelectionListener( new DataRangeSelectionListener() {
                @Override
                public void dataRangeSelected(DataRangeSelectionEvent e) {
                    t.applicationModel.dom.setTimeRange( e.getDatumRange() );
                }
            });
            
            deflts( ll.getDataSetSelector() );
            ll.getDataSetSelector().setValue("");
            
            d.setDefaultCloseOperation( JFrame.HIDE_ON_CLOSE );
            d.getContentPane().add( ll );
            d.pack();
            d.setLocationRelativeTo(t);
            
            instances.put( t, d );
            instances2.put( t, ll);
  
        }
        return instances2.get(t);
    }
}
