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
    public static final String PREF_LAST_OPEN_FOLDER = "last_open_folder";
    public static final String PREF_RECENTLY_OPENED_FILES = "recently_opened_files";
    private JMenu recentMenu = null;

    DataSetSelectorSupport(DataSetSelector ui) {
        this.ui = ui;
    }

    private static File userHome() {
        return new File(System.getProperty("user.home"));
    }

    public static String browseLocal( java.awt.Component parent ) {
        Preferences prefs = Preferences.userNodeForPackage(DataSetSelectorSupport.class);

        String currentDirectory = prefs.get(PREF_LAST_OPEN_FOLDER, userHome().toString());
        final HashMap exts = DataSourceRegistry.getInstance().dataSourcesByExt;

        JFileChooser chooser = new JFileChooser(currentDirectory);

        FileFilter ff;
        ff = new FileFilter() {

            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) {
                    return true;
                }
                String t = f.toString();
                if (t==null ) {
                    System.err.println("here is that bad state on windows.  bug http://sourceforge.net/tracker/?func=detail&aid=3038977&group_id=199733&atid=970682");
                    t= "" + f;
                    return false;
                }
                try {
                    String ext = DataSetURI.getExt(t);
                    if ( ext!=null ) ext= "."+ext;
                    return t.endsWith(".vap") || ( t.endsWith(".zip") || t.endsWith(".ZIP") ) || (ext != null && exts.containsKey(ext));
                } catch ( NullPointerException ex ) {
                    ex.printStackTrace();
                    return false;
                }
            }

            @Override
            public String getDescription() {
                return "supported formats and .vap files";
            }
        };


        chooser.addChoosableFileFilter(ff);
        FileFilter select = ff;

        for (Object ext1 : exts.keySet()) {
            final String extf = (String) ext1;
            ff = new FileFilter() {

                @Override
                public boolean accept(File f) {
                    if ( f.toString()==null ) return false;
                    if (f.isDirectory()) {
                        return true;
                    }
                    String t = f.toString();
                    String ext = DataSetURI.getExt(t);
                    if ( ext!=null ) ext= "."+ext;
                    return (ext != null && extf.equals(ext));
                }

                @Override
                public String getDescription() {
                    return "*" + extf;
                }
            };
            chooser.addChoosableFileFilter(ff);
        }

        chooser.setFileFilter(select);

        int result = chooser.showOpenDialog(parent);
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                prefs.put(PREF_LAST_OPEN_FOLDER, chooser.getSelectedFile().getParent().toString());
                return chooser.getSelectedFile().toURI().toURL().toString();
            } catch (MalformedURLException ex) {
                Logger.getLogger(DataSetSelectorSupport.class.getName()).log(Level.SEVERE, null, ex);
                ex.printStackTrace();
                return null;
            }
        } else {
            return null;
        }

    }

    public Action openLocalAction() {
        return new AbstractAction("Open File...") {

            public void actionPerformed(ActionEvent e) {
                String result= browseLocal(ui);

                if (result != null ) {
                    ui.setValue(result);
                    ui.maybePlot(false);
                }

            }
        };
    }

    protected void refreshRecentFilesMenu() {
        if (recentMenu != null) {
            recentMenu.removeAll();
            if (ui.getRecent() == null) {
                return;
            }
            ArrayList<String> recent = new ArrayList<String>(ui.getRecent());
            Collections.reverse(recent);
            for (String s : recent) {
                final String f = s;
                Action a = new AbstractAction(String.valueOf(f)) {

                    public void actionPerformed(ActionEvent e) {
                        ui.setValue(f);
                        ui.maybePlot(false);
                    }
                };
                recentMenu.add(a);
            }
        }
    }

    public static String getPluginsText() {
        return DataSourceRegistry.getPluginsText();
    }

    JMenu recentMenu() {
        if (recentMenu == null) {
            recentMenu = new JMenu("Open Recent");
            refreshRecentFilesMenu();
        }
        return recentMenu;
    }

    protected void browseSourceTypes() {
        
    }
}
