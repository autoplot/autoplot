/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource;

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
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
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JTextField;

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

    public RecentComboBox() {
        setEditable(true);
    }

    /**
     * associate the history with a file.  This should be called immediately after creating the object.
     * @param pref
     */
    public void setPreferenceNode( String pref ) {
        recentFile= new File( bookmarksFolder, "recent."+pref+".txt" );
        loadRecent();
    }

    public interface InputVerifier {
        public boolean verify( String value );
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
            }

            Collections.reverse(items);
            
            //remove repeat items
            List nitems= new ArrayList( items.size() );
            for ( int i=0; i<items.size(); i++ ) {
                String item= items.get(i);
                if ( !nitems.contains(item) ) nitems.add(item);
            }
            items= nitems;
            
            int n= items.size();
            if ( n>RECENT_SIZE ) items= items.subList(0,RECENT_SIZE);

            setModel( new DefaultComboBoxModel( items.toArray() ) );
            saveRecent(items);
        } catch (IOException ex) {
            Logger.getLogger(RecentComboBox.class.getName()).log(Level.SEVERE, null, ex);
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
                Logger.getLogger(RecentComboBox.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);

        String s = getSelectedItem().toString();

        if ( verifier!=null ) {
            if ( !verifier.verify(s) ) {
                return;
            }
        }
        
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
                Logger.getLogger(RecentComboBox.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        loadRecent();
    }



}
