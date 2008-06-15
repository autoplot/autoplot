/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource;

import java.awt.event.ActionEvent;
import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.filechooser.FileFilter;

/**
 *
 * @author jbf
 */
public class DataSetSelectorSupport {

    DataSetSelector ui;
    public final String PREF_LAST_OPEN_FOLDER = "last_open_folder";
    public final String PREF_RECENTLY_OPENED_FILES = "recently_opened_files";

    private JMenu recentMenu=null;
    
    DataSetSelectorSupport( DataSetSelector ui ) {
        this.ui= ui;
    }
    
    private static File userHome() {
        return new File( System.getProperty("user.home") );
    }
    
    /**
     * return the extension for the filename or url string.
     * @param surl URL string or filename.  
     * @return the extension, with the period. E.g. ".cdf",
     *    or null if an extension is not found.
     */
    private static String getExt( String surl ) throws IllegalArgumentException {
        int i= surl.lastIndexOf("?");
        if ( i==-1 ) i= surl.length();
        i= surl.lastIndexOf(".",i);
        if ( i==-1 ) return null; else return surl.substring(i);
    }
    
    public Action openLocalAction() {
        return new AbstractAction("Open Local...") {
            public void actionPerformed(ActionEvent e) {
                Preferences prefs= Preferences.userNodeForPackage(DataSetSelectorSupport.class);
                
                String currentDirectory= prefs.get(PREF_LAST_OPEN_FOLDER, userHome().toString()  );
                final HashMap exts= DataSourceRegistry.getInstance().dataSourcesByExt;
                
                JFileChooser chooser= new JFileChooser(currentDirectory);
                
                String tt= "vap";
                
                for ( Object ext: exts.keySet() ) {
                    tt+= ", "+ ext;
                }
                
                final String extStr= tt;
                
                FileFilter ff= new FileFilter() {
                    @Override
                    public boolean accept(File f) {
                        if ( f.isDirectory() ) return true;
                        String t= f.toString();
                        String ext= getExt(t);
                        return t.endsWith(".vap") || ( ext!=null && exts.containsKey(ext) ) ;
                    }

                    @Override
                    public String getDescription() {
                        return extStr;
                    }
                };
                      
                chooser.setFileFilter( ff );
                
                int result= chooser.showOpenDialog(ui);
                if ( result== JFileChooser.APPROVE_OPTION ) {
                    try {
                        prefs.put(PREF_LAST_OPEN_FOLDER, chooser.getSelectedFile().getParent().toString());
                        ui.setValue(chooser.getSelectedFile().toURI().toURL().toString());
                        ui.maybePlot();
                    } catch (MalformedURLException ex) {
                        Logger.getLogger(DataSetSelectorSupport.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                
            }
        };
    }

    
    protected void refreshRecentFilesMenu() {
        if ( recentMenu!=null ) {
            recentMenu.removeAll();
            if ( ui.getRecent()==null ) return;
            ArrayList<String> recent= new ArrayList<String>( ui.getRecent() );
            Collections.reverse(recent);
            for ( String s : recent ) {
                final String f= s;
                Action a= new AbstractAction( String.valueOf(f) ) {
                    public void actionPerformed( ActionEvent e ) {
                        ui.setValue(f);
                        ui.maybePlot();
                    }
                };
                recentMenu.add( a );
            }
        }        
    }
    
    JMenu recentMenu() {
        if ( recentMenu==null ) {
            recentMenu= new JMenu("Open recent");
            refreshRecentFilesMenu();            
        }
        return recentMenu;
    }
    
}
