
package org.autoplot.datasource;

import java.awt.Color;
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
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.das2.datum.LoggerManager;

/**
 * decorate a comboBox so that it remembers recent entries.  This listens for ActionEvents from a JComboBox
 * and adds valid items to its droplist.  The recent entries are stored in the bookmarks folder in the file
 * "recent.PREF.txt" where PREF is a string assigned to this object identifying the theme, such as "timerange".
 * Specifically, the event is validated and recorded into the file, then the file is loaded, sorted, and saved
 * again.
 * 
 * @author jbf
 */
public class RecentComboBox extends JComboBox {

    private static final int RECENT_SIZE = 20;

    File bookmarksFolder= new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ), "bookmarks" );
    File recentFile;
    String preferenceNode= "";
    boolean dirty= false;

    public static final String PREF_NODE_TIMERANGE="timerange";
    
    private final static Logger logger= LoggerManager.getLogger("apdss.uri.recent");
    
    public RecentComboBox() {
        setEditable(true);
        addItemListener( new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if ( e.getStateChange()==ItemEvent.SELECTED ) {
                    if ( RecentComboBox.this.preferenceNode.length()>0 ) {
                        logger.finer("set dirty=true");
                        dirty= true;
                        //setBackground( Color.blue );
                    }
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
        this.preferenceNode= pref;
        recentFile= new File( bookmarksFolder, "recent."+pref+".txt" );
        dirty= false;
        //setBackground( Color.WHITE );
        Runnable run= new Runnable() {
            @Override
            public void run() {
                loadRecent();
            }
        };
        if ( SwingUtilities.isEventDispatchThread() ) {
            new Thread(run,"loadRecent-"+preferenceNode).start();
        } else {
            run.run();
        }
    }
    
    InputVerifier verifier= null;

    /**
     * allow filtering of invalid entries so they aren't recorded in history.
     * @param v
     */
    public void setVerifier( InputVerifier v ) {
        this.verifier= v;
    }

    /**
     * check if the human has made modifications to this value.
     * @return true if modifications have been made.
     */
    public boolean isDirty() {
        return this.dirty;
    }
    
    /**
     * to make it easier to convert GUIs with JTextFields to RecentComboBoxes, setText is available.
     * @param text 
     */
    public void setText( String text ) {
        logger.log(Level.FINER, "setText({0})", text);
        setSelectedItem(text);
        this.dirty= false;
        //setBackground( Color.WHITE );
    }
    
    /**
     * get the string value, which is also the getSelectedItem.
     * This will also push a changed value to the recent entries.
     * @return 
     */
    public String getText() {
        Object o= getSelectedItem();
        String s= o==null ? "" : o.toString();
        if ( s==null ) {
            return "";
        } else {
            if ( dirty ) {
                addToRecent(s);
            }
            logger.log(Level.FINER, "getText()->{0}", s);
            return s;
        }
    }
    
    /**
     * this loads the recent entries file (e.g. autoplot_data/bookmarks/recent.timerange.txt)
     * and should not be called on the event thread.
     */
    private void loadRecent() {
        logger.log(Level.FINER, "loadRecent()");
        List<String> items= new ArrayList( RECENT_SIZE+2 );
        try {
            if ( recentFile!=null && recentFile.exists() ) {
                boolean empty=true;
                try (BufferedReader r = new BufferedReader(new FileReader(recentFile))) {
                    String s= r.readLine();
                    while ( s!=null ) {
                        if ( verifier!=null ) {
                            if ( !verifier.verify(s) ) {
                                s= r.readLine();
                                continue;
                            }
                        }
                        empty= false;
                        items.add(s);
                        s= r.readLine();
                    }
                }
                if ( empty ) {
                    if ( !recentFile.delete() ) {
                        logger.fine("unable to remove empty file");
                    }
                }
            }

            Collections.reverse(items);
            
            //remove repeat items
            List nitems= new ArrayList( items.size() );
            for (String item : items) {
                if ( !nitems.contains(item) ) nitems.add(item);
            }
            items= nitems;
            final List<String> fitems= items;
                    
            int n= items.size();
            if ( n>RECENT_SIZE ) items= items.subList(0,RECENT_SIZE);

            Runnable run= new Runnable() {
                @Override
                public void run() {
                    Object s= getModel().getSelectedItem();
                    ComboBoxModel newModel= new DefaultComboBoxModel( fitems.toArray() );
                    newModel.setSelectedItem(s);
                    setModel( newModel );
                }
            };
            SwingUtilities.invokeLater(run);
            
            saveRecent(items); // possibly removing old items.
            
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    /**
     * kludge in way to add an item to the list of recent entries.
     * @param items items to insert (at the top of the list).
     */
    public void addAdditionalToRecentItems( List<String> items ) {
        ComboBoxModel mm= getModel();
        if ( mm instanceof DefaultComboBoxModel ) {
            DefaultComboBoxModel dmm= (DefaultComboBoxModel)mm;
            for ( int i=0; i<items.size(); i++ ) {
                String item = items.get(i);
                dmm.insertElementAt( item, i );
            }
        }
    }
    
    /**
     * save the recent items to the disk.  items.get(0) is the most recent item, 
     * and will be the last line of the recent file on the disk.
     * @param items
     */
    private void saveRecent( List<String> items ) {
        logger.log(Level.FINER, "saveRecent({0} items)", items.size());
        if ( recentFile==null || !bookmarksFolder.exists() ) {
            return; //not yet, we're initializing for the first time.
        }
        boolean empty= true;
        File recentFileTemp;
        try {
            recentFileTemp= File.createTempFile( "recent."+ this.preferenceNode, ".txt", bookmarksFolder );
        } catch (IOException ex) {
            logger.warning(ex.getMessage());
            return;
        }
        BufferedWriter w = null;
        try {
            w = new BufferedWriter(new FileWriter(recentFileTemp));
            items= new ArrayList(items);
            Collections.reverse(items);
            for ( String s:items ) {
                w.append( s, 0, s.length() );
                w.append("\n");
                empty= false;
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
        
        if ( recentFile.exists() && !recentFile.delete() ) {
            logger.log(Level.WARNING, "unable to delete recent file {0}", recentFile);
        } else {
            if ( empty ) {
                recentFileTemp.delete();
            } else {
                if ( !recentFileTemp.renameTo(recentFile) ) {
                    logger.log(Level.WARNING, "unable to overwrite file {0}", recentFile);
                }
            }
        }
    }
    
    /**
     * add the item to the list of recent entries.  This will reload the
     * recent file, probably firing events.
     * @param s the item
     */
    public void addToRecent( final String s ) {
        addToRecent(s,true);
    }
    
    /**
     * add the item to the list of recent entries. 
     * The recent file will have the most recent item at the end of the file.
     * @param s the item
     * @param reload if true then reload the file.
     */
    public void addToRecent( final String s, final boolean reload ) {
        logger.log(Level.FINE, "addToRecent({0})", s);
        if ( verifier!=null ) {
            if ( !verifier.verify(s) ) {
                return;
            }
        }

        Runnable run= new Runnable() {
            @Override
            public void run() {
                List<String> items= new ArrayList<>();
                if ( recentFile!=null ) {
                    try ( BufferedReader r= new BufferedReader(new FileReader(recentFile)) ) {
                        String l;
                        while ( (l=r.readLine())!=null ) {
                            items.add(items.size(),l);
                        }
                    } catch ( IOException ex ) {
                        logger.log( Level.WARNING, null, ex );
                    }
                }
                items.add(s); 
                Collections.reverse(items);
                saveRecent(items);
                if ( reload ) loadRecent();        
            }
        };
        if ( SwingUtilities.isEventDispatchThread() ) {
            new Thread( run, "addToRecent-"+preferenceNode ).start();
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

    public static void main( String[] args ) {
        JOptionPane.showConfirmDialog( null, new RecentComboBox() );
    }
}
