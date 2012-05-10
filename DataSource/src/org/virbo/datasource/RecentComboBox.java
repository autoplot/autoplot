/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource;

import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;

/**
 * decorate a comboBox so that it remembers recent entries.
 * @author jbf
 */
public class RecentComboBox extends JComboBox {

    private static final int RECENT_SIZE = 20;

    File bookmarksFolder= new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ), "bookmarks" );
    File recentFile;

    public RecentComboBox( String pref ) {
        super();
        setEditable(true);
        setModel( new DefaultComboBoxModel( new String[] { "" } ) );
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
        List<String> items= new LinkedList();
        try {
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

            Collections.reverse(items);
            
            //remove repeat items
            List nitems= new ArrayList(items.size());
            for ( int i=0; i<items.size(); i++ ) {
                String item= items.get(i);
                if ( !nitems.contains(item) ) nitems.add(item);
            }
            items= nitems;
            
            int n= items.size();
            if ( n>RECENT_SIZE ) items= items.subList(n-RECENT_SIZE,n);
            
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
        BufferedWriter w = null;
        try {
            w = new BufferedWriter(new FileWriter(recentFile,false));
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
        BufferedWriter w = null;
        try {
            synchronized (this) {
                w = new BufferedWriter(new FileWriter(recentFile,true));
                String s = getSelectedItem().toString();
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
