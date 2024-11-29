
package org.autoplot.datasource;

import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.das2.datum.DatumRange;

/**
 * Additional actions for the DataSetSelector.
 * @author jbf
 */
public class DataSetSelectorSupport {

    private static final Logger logger= Logger.getLogger("apdss.dss");

    DataSetSelector ui;
    
    private JMenu recentMenu = null;

    DataSetSelectorSupport(DataSetSelector ui) {
        this.ui = ui;
    }

    private static File userHome() {
        return new File(System.getProperty("user.home"));
    }
    
    /**
     * Show a file chooser component, and return the name of a .vap file.
     * @param parent parent component for focus.  If a dataSetSelector is
     * used then its timerange is used for the initial timerange.
     * This will now allow a non-local vap to be browsed as well.
     * @param initialSelection if non-null, then the initial selection.
     * @return the URI for the vap file, or null if cancel was pressed.
     */
    public static String browseLocalVap( java.awt.Component parent, String initialSelection) {
        Preferences prefs = AutoplotSettings.settings().getPreferences( AutoplotSettings.class);

        String currentDirectory = prefs.get( AutoplotSettings.PREF_LAST_OPEN_VAP_FOLDER, prefs.get(AutoplotSettings.PREF_LAST_OPEN_FOLDER, userHome().toString() ) );
        String currentFile=  prefs.get( AutoplotSettings.PREF_LAST_OPEN_VAP_FILE, "" );

        boolean isRemote= initialSelection!=null && ( initialSelection.startsWith("https:/") 
                || initialSelection.startsWith("http:/")
                || initialSelection.startsWith("ftp:/") 
                || initialSelection.startsWith("sftp:/") );
        boolean isLocal= initialSelection==null || initialSelection.isEmpty() || !isRemote;
        
        JFileChooser chooser;
        
        chooser= new JFileChooser();
        try {
            chooser.setCurrentDirectory( new File( currentDirectory ) );
        } catch ( SecurityException ex ) {
            logger.info("unable to set current directory");
        }
        if ( currentFile.length()>0 ) {
            try {
                chooser.setSelectedFile( new File( currentFile ) );
            } catch ( SecurityException ex ) {
                logger.info("unable to set current file");
            }
        }
        if ( initialSelection!=null && isLocal ) {
            URISplit split= URISplit.parse(initialSelection);
            if ( split.file!=null && "vap".equals(split.ext) ) {
                chooser.setSelectedFile( new File( split.file ));
            }
        }
        
        JPanel trPanel= new JPanel();
        trPanel.setLayout( new BoxLayout(trPanel, BoxLayout.Y_AXIS ));
        trPanel.setMaximumSize( new Dimension(230,9999) );
        trPanel.setPreferredSize( new Dimension(230,200) );
        ButtonGroup bg= new ButtonGroup();
        JCheckBox b1= new JCheckBox("Use timerange in .vap file");
        //b1.setAlignmentX(Component.LEFT_ALIGNMENT);
        bg.add(b1);
        final TimeRangeEditor t= new TimeRangeEditor();
        if ( parent!=null && parent instanceof DataSetSelector ) {
            DatumRange tr=  ((DataSetSelector)parent).getTimeRange() ;
            if ( tr!=null ) t.setRange(tr);
        }
        t.makeThinner();
        final JCheckBox b2= new JCheckBox("Reset the .vap timerange:");
        bg.add(b2);
        //b2.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        boolean notimerange= ( initialSelection==null || !initialSelection.contains("?timerange=") );
        b1.setSelected( notimerange );
        b2.setSelected( !notimerange );
        
        b1.setAlignmentX( 0.f );
        b2.setAlignmentX( 0.f );
        t.setAlignmentX( 0.f );
        
        trPanel.add(b1);
        trPanel.add( Box.createVerticalStrut(14) );
        trPanel.add(b2);
        trPanel.add(t);
        
        ActionListener enableTR= new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                t.setEnabled( b2.isSelected() );
            }
        };
        b1.addActionListener(enableTR);
        b2.addActionListener(enableTR);
        
        t.setEnabled(b2.isSelected());
        
        trPanel.add( Box.createVerticalGlue() );
        
        if ( chooser!=null ) {
            chooser.setAccessory(trPanel);
        
            FileFilter ff;
            ff = new FileNameExtensionFilter( ".vap files", "vap" );

            chooser.addChoosableFileFilter(ff);
            chooser.setFileFilter(ff);

            Window w= parent==null ? null : SwingUtilities.getWindowAncestor( parent);
            int result = chooser.showOpenDialog(w);
            if (result == JFileChooser.APPROVE_OPTION) {
                prefs.put(AutoplotSettings.PREF_LAST_OPEN_VAP_FOLDER, chooser.getSelectedFile().getParent() );
                if ( b2.isSelected() ) {
                    return chooser.getSelectedFile().toURI().toString() + "?timerange="+ t.getRange().toString().replaceAll("\\s+", "+");
                } else {
                    return chooser.getSelectedFile().toURI().toString();
                }
            } else {
                return null;
            }
        } else {
            if ( JOptionPane.showConfirmDialog( parent, trPanel, "New Time Range", JOptionPane.OK_CANCEL_OPTION )==JOptionPane.OK_OPTION ) {
                URISplit split= URISplit.parse( initialSelection );
                Map<String,String> params= URISplit.parseParams(split.params);
                if ( b2.isSelected() ) {
                    params.put( "timerange", t.getRange().toString().replaceAll("\\s","+") );
                } else {
                    params.remove("timerange");
                }
                if ( params.isEmpty() ) {
                    split.params= null;
                } else {
                    split.params= URISplit.formatParams(params);
                }
                return URISplit.format(split);
            } else {
                return null;
            }
            
        }

    }

    /**
     * Show a file chooser component, and return the name of a data or .vap file.
     * @param parent parent component for focus.
     * @return the URI for the vap file.
     */
    public static String browseLocal( java.awt.Component parent ) {
        Preferences prefs = AutoplotSettings.settings().getPreferences(DataSetSelectorSupport.class);

        String currentDirectory = prefs.get(AutoplotSettings.PREF_LAST_OPEN_FOLDER, userHome().toString());
        final HashMap<String,Object> exts = DataSourceRegistry.getInstance().dataSourcesByExt;
        
        JFileChooser chooser = new JFileChooser();
        try {
            chooser.setCurrentDirectory( new File(currentDirectory) );
        } catch ( SecurityException ex ) {
            logger.info("unable to set current directory because of security");
        }

        final boolean isAutoplotApp;
        if ( parent instanceof DataSetSelector ) {
            isAutoplotApp= ((DataSetSelector)parent).actionTriggers.containsKey("vapfile:(.*)"); //TODO: kludgy  
        } else {
            isAutoplotApp= true;
        }
        
        FileFilter ff;
        ff = new FileFilter() {

            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) {
                    return true;
                }
                String t = f.getName();
                String ext = DataSetURI.getExt(t);
                if ( ext!=null ) ext= "."+ext;
                return ( t.endsWith(".zip") || t.endsWith(".ZIP") ) 
                    || ( t.endsWith(".tar") || t.endsWith(".tgz") || t.endsWith(".tar.gz") )
                    || (ext != null && ( exts.containsKey(ext) ) || ( isAutoplotApp && t.endsWith(".vap") ));
            }

            @Override
            public String getDescription() {
                return "supported formats";
            }
        };

        chooser.addChoosableFileFilter(ff);
        FileFilter select = ff;
        
        ArrayList<String> s= new ArrayList( exts.keySet() );
        Collections.sort(s);
        
        HashSet<String> skip= new HashSet<>();
        skip.add(".cdfj");
        skip.add(".cdfn");
        skip.add(".csv0");
        
        for (String ext1 : s ) {
            if ( skip.contains(ext1) ) continue;
            DataSourceFactory factory= DataSourceRegistry.getInstance().getSource(ext1);
            if ( factory.isFileResource() ) {
                final String extf = (String) ext1;
                String desc= DataSourceRegistry.getInstance().describe( factory, extf );
                if ( desc.length()==0 || desc.startsWith(".") ) {
                    desc= "*"+ext1;
                } else {
                    desc= "*"+ext1+" " +desc;
                }
                ff = new FileNameExtensionFilter( desc, extf.substring(1) );
                chooser.addChoosableFileFilter(ff);
            }
        }
        
        if ( isAutoplotApp ) {
            ff = new FileNameExtensionFilter( "*.vap", "vap");
            chooser.addChoosableFileFilter(ff);
        }

        chooser.setFileFilter(select);

        int result = chooser.showOpenDialog(parent);
        if (result == JFileChooser.APPROVE_OPTION) {
            prefs.put(AutoplotSettings.PREF_LAST_OPEN_FOLDER, chooser.getSelectedFile().getParent() );
            return chooser.getSelectedFile().toURI().toString();
        } else {
            return null;
        }

    }

    /**
     * get "Add Plot from Local File..." action
     * @return the action
     */
    public Action openLocalAction() {
        return new AbstractAction("Add Plot from Local File...") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);                
                String result= browseLocal(ui);

                if (result != null ) {
                    ui.setValue(result);
                    ui.maybePlot( KeyEvent.ALT_MASK );
                    //ui.maybePlot(false);
                }

            }
        };
    }

    /**
     * get the "Open .vap File..." action
     * @return the action
     */
    public Action openLocalVapAction() {
        return new AbstractAction("Open .vap File...") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);
                String result= browseLocalVap(ui, ui.getValue() );

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
            ArrayList<String> recent = new ArrayList<>(ui.getRecent());
            Collections.reverse(recent);
            for (String s : recent) {
                final String f = s;
                Action a = new AbstractAction(String.valueOf(f)) {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        org.das2.util.LoggerManager.logGuiEvent(e);            
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
