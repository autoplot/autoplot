/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import org.das2.datum.LoggerManager;

/**
 * decorate a comboBox so that it remembers recent entries.  This listens for ActionEvents from a JComboBox
 * and adds valid items to its droplist.  The recent entries are stored in the bookmarks folder in the file
 * "recent.PREF.txt" where PREF is a string assigned to this object identifying the theme, such as "timerange".
 * Specifically, the event is validated and recorded into the file, then the file is loaded, sorted and saved
 * again.
 * 
 * @author jbf
 */
public class RecentComboBox extends JComboBox {

    private static final int RECENT_SIZE = 20;

    File bookmarksFolder= new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ), "bookmarks" );
    File recentFile;

    private final static Logger logger= LoggerManager.getLogger("apdss.uri");
    
    public RecentComboBox() {
        setEditable(true);
        addItemListener( new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if ( e.getStateChange()==ItemEvent.SELECTED ) {

                    //TODO: too bad this doesn't work properly!
//                    String item= (String) e.getItem();
//                    List<String> items= new ArrayList( RECENT_SIZE+2 );
//                    ComboBoxModel model= RecentComboBox.this.getModel();
//                    for ( int i=0; i<model.getSize(); i++ ) {
//                        items.add( (String) model.getElementAt(i) );
//                    }
//                    items.remove( item );
//                    items.add(0,item);
//                    setModel( new DefaultComboBoxModel( items.toArray() ) );
//                    saveRecent(items);
                }
            }
        } );
    }

    /**
     * associate the history with a file.  This should be called immediately after creating the object.
     * @param pref
     */
    public void setPreferenceNode( String pref ) {
        recentFile= new File( bookmarksFolder, "recent."+pref+".txt" );
        loadRecent();
    }
    
    InputVerifier verifier= null;

    /**
     * allow filtering of invalid entries so they aren't recorded in history.
     * @param v
     */
    public void setVerifier( InputVerifier v ) {
        this.verifier= v;
    }

    private synchronized void loadRecent() {
        List<String> items= new ArrayList( RECENT_SIZE+2 );
        try {
            if ( recentFile.exists() ) {
                BufferedReader r = new BufferedReader(new FileReader(recentFile));
                try {
                    String s= r.readLine();
                    while ( s!=null ) {
                        if ( verifier!=null ) {
                            if ( !verifier.verify(s) ) {
                                s= r.readLine();
                                continue;
                            }
                        }
                        items.add(s);
                        s= r.readLine();
                    }
                } finally {
                    r.close();
                }
            }

            Collections.reverse(items);
            
            //remove repeat items
            List nitems= new ArrayList( items.size() );
            for ( int i=0; i<items.size(); i++ ) {
                String item= items.get(i);
                if ( !nitems.contains(item) ) nitems.add(item);
            }
            items= nitems;
            final List<String> fitems= items;
                    
            int n= items.size();
            if ( n>RECENT_SIZE ) items= items.subList(0,RECENT_SIZE);

            Runnable run= new Runnable() {
                public void run() {
                    setModel( new DefaultComboBoxModel( fitems.toArray() ) );
                }
            };
            SwingUtilities.invokeLater(run);
            
            saveRecent(items);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    /**
     * save the recent items to the disk.  items.get(0) is the most recent item, and will be saved last on the disk.
     * @param items
     */
    private synchronized void saveRecent( List<String> items ) {
        if ( !recentFile.getParentFile().exists() ) {
            return; //not yet, we're initializing for the first time.
        }
        BufferedWriter w = null;
        try {
            w = new BufferedWriter(new FileWriter(recentFile));
            items= new ArrayList(items);
            Collections.reverse(items);
            for ( String s:items ) {
                w.append( s, 0, s.length() );
                w.append("\n");
            }
            w.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } finally {
            try {
                if ( w!=null ) w.close();
            } catch (IOException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
    }

    public void addToRecent( final String s ) {
        if ( verifier!=null ) {
            if ( !verifier.verify(s) ) {
                return;
            }
        }

        Runnable run= new Runnable() {
            public void run() {
                
                BufferedWriter w = null;
                try {
                    synchronized (this) {
                        if ( recentFile.exists() ) {
                            w = new BufferedWriter(new FileWriter(recentFile,true));
                        } else {
                            w = new BufferedWriter(new FileWriter(recentFile));
                        }
                        w.append(s, 0, s.length());
                        w.append("\n");
                        w.close();
                    }
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                } finally {
                    try {
                        if ( w!=null ) w.close();
                    } catch (IOException ex) {
                        logger.log(Level.SEVERE, ex.getMessage(), ex);
                    }
                }
                loadRecent();        
            }
        };
        if ( SwingUtilities.isEventDispatchThread() ) {
            logger.fine("this shouldn't happen on event thread.");
            run.run();
        } else {
            run.run();
        }
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        org.das2.util.LoggerManager.logGuiEvent(e);        
        super.actionPerformed(e);

        String s = getSelectedItem().toString();

        addToRecent(s);
        
    }



}
