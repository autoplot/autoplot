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
import java.util.Map;
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
    private JMenu recentMenu = null;

    DataSetSelectorSupport(DataSetSelector ui) {
        this.ui = ui;
    }

    private static File userHome() {
        return new File(System.getProperty("user.home"));
    }

    public Action openLocalAction() {
        return new AbstractAction("Open File...") {

            public void actionPerformed(ActionEvent e) {
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
                        
                        String ext = DataSetURL.getExt(t);
                        if ( ext!=null ) ext= "."+ext;
                        return t.endsWith(".vap") || (ext != null && exts.containsKey(ext));
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
                            if (f.isDirectory()) {
                                return true;
                            }
                            String t = f.toString();
                            String ext = DataSetURL.getExt(t);
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

                int result = chooser.showOpenDialog(ui);
                if (result == JFileChooser.APPROVE_OPTION) {
                    try {
                        prefs.put(PREF_LAST_OPEN_FOLDER, chooser.getSelectedFile().getParent().toString());
                        ui.setValue(chooser.getSelectedFile().toURI().toURL().toString());
                        ui.maybePlot(false);
                    } catch (MalformedURLException ex) {
                        Logger.getLogger(DataSetSelectorSupport.class.getName()).log(Level.SEVERE, null, ex);
                    }
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
        StringBuffer buf = new StringBuffer();
        buf.append("<html>");
        {
            buf.append("<h1>Plugins by Extension:</h1>");
            Map m = DataSourceRegistry.getInstance().dataSourcesByExt;
            for (Object k : m.keySet()) {
                buf.append("" + k + ": " + m.get(k) + "<br>");
            }
        }
        {
            buf.append("<h1>Plugins by Mime Type:</h1>");
            Map m = DataSourceRegistry.getInstance().dataSourcesByMime;
            for (Object k : m.keySet()) {
                buf.append("" + k + ": " + m.get(k) + "<br>");
            }
        }
        buf.append("</html>");
        return buf.toString();
    }

    JMenu recentMenu() {
        if (recentMenu == null) {
            recentMenu = new JMenu("Open Recent");
            refreshRecentFilesMenu();
        }
        return recentMenu;
    }
}
