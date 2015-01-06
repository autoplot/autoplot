/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * PngWalkTool.java
 *
 * Created on Apr 29, 2009, 3:17:56 AM
 */

package org.autoplot.pngwalk;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.das2.components.DataPointRecorder;
import org.das2.components.TearoffTabbedPane;
import org.das2.dataset.DataSetUpdateEvent;
import org.das2.dataset.DataSetUpdateListener;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.TimeParser;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.datum.format.TimeDatumFormatter;
import org.das2.event.DataPointSelectionEvent;
import org.das2.event.DataPointSelectionListener;
import org.das2.util.ArgumentList;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystem.FileSystemOfflineException;
import org.das2.util.monitor.NullProgressMonitor;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Binding;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.virbo.autoplot.AppManager;
import org.virbo.autoplot.AutoplotUI;
import org.virbo.autoplot.AutoplotUtil;
import org.virbo.autoplot.GuiSupport;
import org.virbo.autoplot.ScriptContext;
import org.virbo.autoplot.bookmarks.Bookmark;
import org.virbo.autoplot.bookmarks.BookmarksException;
import org.virbo.autoplot.bookmarks.BookmarksManager;
import org.virbo.autoplot.bookmarks.BookmarksManagerModel;
import org.virbo.autoplot.bookmarks.Util;
import org.virbo.autoplot.transferrable.ImageSelection;
import org.virbo.datasource.DataSetSelector;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.FileSystemUtil;
import org.virbo.datasource.URISplit;
import org.xml.sax.SAXException;

/**
 * GUI for browsing PNGWalks, or sets of PNG images.  These typically contain files named to make a time series, such as
 * product_$Y$m$d.png, but this can browse any set of images using wildcards.  This provides a number of views of the
 * images, such as a grid of thumbnails and the coverflow view which shows an image and the preceding and succeeding images.
 * This also contains a hook to get back into Autoplot, if product.vap (or vap named like the images) is found.
 * 
 * @author jbf
 */
public final class PngWalkTool extends javax.swing.JPanel {
    private static boolean ENABLE_QUALITY_CONTROL;
    private QualityControlPanel qcPanel=null;

    public static final String PREF_RECENT = "pngWalkRecent";
    
    /**
     * last location where image was exported
     */
    public static final String PREF_LAST_EXPORT= "pngWalkLastExport";

    public PngWalkView[] views;
    TearoffTabbedPane tabs;
    
    transient WalkImageSequence seq;
    
    JMenu navMenu;
    
    Pattern actionMatch=null;
    String actionCommand=null;

    static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.pngwalk");
    private static final String RESOURCES= "/org/virbo/autoplot/resources/";
    private static final Icon WARNING_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"warning-icon.png") );
    private static final Icon ERROR_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"error-icon.png") );
    private static final Icon BUSY_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"spinner.gif") );
    private static final Icon READY_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"indProgress0.png") );
    private static final Icon IDLE_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"idle-icon.png") );
        
    int returnTabIndex=0; // index of the tab we left to look at the single panel view.  TODO: account for tear off.

    transient DatumRange pendingGoto= null;  // after password is entered, then go to this range.

    public static void main(String[] args) {

        DataSetURI.init();  // for FtpFileSystem implementation

        System.err.println("autoplot pngwalk 20141111");
        final ArgumentList alm = new ArgumentList("PngWalkTool");
        alm.addOptionalSwitchArgument("nativeLAF", "n", "nativeLAF", alm.TRUE, "use the system look and feel (T or F)");
        alm.addOptionalSwitchArgument( "mode", "m", "mode", "filmStrip", "initial display mode: grid, filmStrip, covers, contextFlow, etc");
        alm.addOptionalSwitchArgument( "goto", "g", "goto", "", "start display at the beginning of this range, e.g. 2010-01-01" );
        alm.addBooleanSwitchArgument("qualityControl", "q", "qualityControl", "enable quality control review mode");
        String home= java.lang.System.getProperty( "user.home" ) + java.lang.System.getProperty( "file.separator" );
        String output= "file:" + home + "pngwalk" + java.lang.System.getProperty( "file.separator" )
                + "product_$Y$m$d.png";

        alm.addOptionalPositionArgument(0, "template",  output, "initial template to use.");
        alm.addOptionalSwitchArgument( "template", "t", "template", output, "initial template to use." );

        alm.process(args);

        if (alm.getBooleanValue("nativeLAF")) {
            logger.fine("nativeLAF");
            try {
                javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                logger.log( Level.WARNING, e.getMessage(), e );
            }
        }

        if (alm.getBooleanValue("qualityControl")) {
            ENABLE_QUALITY_CONTROL = true;
        } else {
            ENABLE_QUALITY_CONTROL = false;
        }

        String template = alm.getValue("template"); // One Slash!!
        //final String template=  "file:/home/jbf/temp/product_$Y$m$d.png" ; // One Slash!!
        //final String template=  "file:/net/spot3/home/jbf/fun/pics/2001minnesota/.*JPG" ;
        //final String template= "file:/home/jbf/public_html/voyager/VGPW_0201/BROWSE/V1/.*.PNG";
        //final String template= "file:///net/spot3/home/jbf/fun/pics/20080315_tenerife_masca_hike/IMG_.*.JPG";
        //final String template= "http://www.swpc.noaa.gov/ftpdir/lists/hpi/plots/pmap_$Y_$m_$d_...._S_.*_.*_.*_.*.gif";

        PngWalkTool pngWalkTool= start( template, null );

        pngWalkTool.processArguments(alm);

    }

    private static String readPngwalkFile( String template ) {
        URISplit split= URISplit.parse(template);
        InputStream in=null;
        try {
            Properties p= new Properties();
            File local= FileSystemUtil.doDownload( split.file, new NullProgressMonitor() );
            in= new FileInputStream( local );
            p.load( in );
            String vers= p.getProperty("version");
            if ( vers==null || vers.trim().length()==0 ) vers=""; else vers="_"+vers;
            String t= split.path + p.getProperty("product") + "_" + p.getProperty("timeFormat") +vers + ".png";
            template= t;
        } catch (FileSystemOfflineException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        } catch (URISyntaxException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        } catch (FileNotFoundException ex) {
            throw new IllegalArgumentException("File does not exist: "+template);
        } catch (IOException ex) {
            logger.log( Level.WARNING, ex.getMessage(), ex );
            throw new RuntimeException(ex);
        } finally {
            try {
                if ( in!=null ) in.close();
            } catch ( IOException ex ) { 
                logger.log( Level.WARNING, ex.getMessage(), ex );
            }
        }
        return template;

    }

    private static void raiseApWindowSoon( final Window apWindow ) {
        Runnable run= new Runnable() {
            public void run() {
                GuiSupport.raiseApplicationWindow((JFrame)apWindow);
                apWindow.toFront();
                apWindow.repaint();
            }
        };
        SwingUtilities.invokeLater(run);
    }
    
    public static PngWalkTool start( String template, final Window parent ) {

        final PngWalkTool tool = new PngWalkTool();

        if ( template!=null ) {
            if ( template.endsWith(".pngwalk") ) {
                template= readPngwalkFile(template);
            }
            tool.setTemplate(template);
        } 

        String sdeft= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><bookmark-list version=\"1.1\">    <bookmark-folder remoteUrl=\"http://virbo.org/meta/viewDataFile.jsp?docname=418DBD06-4CA9-4D8E-44EB-F548AE6DBB9C&amp;filetype=data\">" +
        "<title>Demos</title>" +
        "<bookmark-list>" +
        "    <bookmark>" +
        "        <title>Northern Auroral Images</title>" +
        "        <uri>pngwalk:http://www.swpc.noaa.gov/ftpdir/lists/hpi/plots/pmap_$Y_$m_$d_$H$M_N*.gif</uri>" +
        "        <description>North Auroral Image from Space Weather Prediction Center</description>" +
        "    </bookmark>" +
        "    <bookmark>" +
        "        <title>Southern Auroral Images</title>" +
        "        <uri>pngwalk:http://www.swpc.noaa.gov/ftpdir/lists/hpi/plots/pmap_$Y_$m_$d_$H$M_S*.gif</uri>" +
        "        <description>Southern Auroral Image from Space Weather Prediction Center</description>" +
        "    </bookmark>" +
        "    <bookmark>" +
        "        <title>RBSP Emfisis HFR-WFR Orbits</title>" +
        "        <uri>pngwalk:http://emfisis.physics.uiowa.edu/pngwalk/RBSP-A/HFR-WFR_orbit/product_$(o,id=rbspa-pp).png</uri>" +
        "    </bookmark>" +
        "    <bookmark>" +
        "        <title>RBSP-A MagEIS Combined Spectra</title>" +
        "        <uri>pngwalk:http://www.rbsp-ect.lanl.gov/data_pub/rbspa/ect/level2/combined-elec/rbspa_ect_L2-elec_$Y$m$d_v.1.0.0.png</uri>" +
        "    </bookmark>" +
        "</bookmark-list>" +
    "</bookmark-folder>" +
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

        Util.loadRecent( "pngwalkRecent", tool.dataSetSelector1, deft );


        PngWalkTool.ActionEnabler enabler= new PngWalkTool.ActionEnabler() {
            public boolean isActionEnabled(String filename) {
                String s = filename;
                String template = tool.getTemplate();
                int i0 = template.indexOf("_$Y");
                if ( i0==-1 ) i0= template.indexOf("_%Y");
                if ( i0==-1 ) i0= template.indexOf("_%o"); 
                if ( i0==-1 ) i0= template.indexOf("_%{o,");
                if ( i0==-1 ) i0= template.indexOf("_$o");
                if ( i0==-1 ) i0= template.indexOf("_$(o,");
                int i1 = s.indexOf(".png");
                if ( i1==-1 || i0==-1 ) return false;
                //String timeRange = s.substring(i0 + 1, i1);
                String productFile = template.substring(0, i0) + ".vap";
                try {
                    return WalkUtil.fileExists(productFile);
                } catch (FileSystemOfflineException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                    return false;
                } catch (URISyntaxException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                    return false;
                }
            }
        };

        final String lap= "View in Autoplot";

        tool.addFileAction( enabler, new AbstractAction(lap) {
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);                        
                final String suri;
                if ( tool.seq==null ) {
                    suri=null;
                } else {
                    String s = tool.getSelectedFile();
                    String template = tool.getTemplate();
                    if ( s.startsWith("file:/") && !s.startsWith("file:///") && template.startsWith("file:///") ) {
                        s= "file:///"+s.substring(6);
                    }
                    int i= template.indexOf("$");
                    if ( i!=-1 ) { // do a little testing
                        int i2= i+1;
                        if ( i2==template.length() ) {
                            throw new IllegalArgumentException("template must start with $Y, $y or $(o,...)");
                        }
                        char c= template.charAt(i2);
                        while ( i2<template.length() && ( Character.isDigit(c) || c=='(' ) ) {
                            i2++;
                            c= template.charAt(i2);
                        }
                        if ( i2==template.length() || !( c=='Y' || c=='y' || c=='o' ) ) {
                            throw new IllegalArgumentException("template must start with $Y, $y or $(o,...)");
                        }
                    }
                    int i0 = template.indexOf("_$Y");
                    if ( i0==-1 ) i0= template.indexOf("_%Y"); // I don't think this should happen now.
                    if ( i0==-1 ) i0= template.indexOf("_$y");
                    if ( i0==-1 ) i0= template.indexOf("_$o");
                    if ( i0==-1 ) i0= template.indexOf("_$(o,");
                    //Note, _$(m,Y=2000) is not supported.
                    
                    //int i1 = template.indexOf(".png");
                    //if ( i1==-1 ) return;
                    //TimeParser tp= TimeParser.create( template.substring(i0 + 1, i1) );
                    //String timeRange = s.substring(i0 + 1, i1);
                    
                    //kludge: LANL showed a bug where "user" was inserted into the URL.  Check for this.

                    if ( s.contains("//user@") && !template.contains("//user@") ) {
                        s= s.replace("//user@", "//" );
                    }

                    TimeParser tp= TimeParser.create( template );
                    String timeRange = s;
                    try {
                        DatumRange dr= tp.parse(timeRange).getTimeRange();
                        timeRange= dr.toString().replaceAll(" ", "+");
                    } catch ( ParseException ex ) {
                        throw new RuntimeException(ex);
                    }
                    String productFile=null;
                    productFile = template.substring(0, i0) + ".vap";
                    suri = productFile + "?timeRange=" + timeRange;
                }

                Runnable run = new Runnable() {
                    public void run() {
                        try {
                            ScriptContext.createGui();
                            Window apWindow= ScriptContext.getViewWindow();
                            if ( suri!=null ) ScriptContext.plot(suri);
                            if ( parent==null ) {
                                apWindow.setVisible(true);
                            }
                            raiseApWindowSoon(apWindow);
                        } catch (InterruptedException ex) {
                            logger.log(Level.SEVERE, ex.getMessage(), ex);
                        }
                    }
                };
                new Thread(run).start();
            }
        });

        JFrame frame = new JFrame("PNG Walk Viewer");
        frame.setIconImage( AutoplotUtil.getAutoplotIcon() );

        frame.setJMenuBar( createMenuBar(tool,frame) );

        AppManager.getInstance().addApplication(tool);
        
        frame.getContentPane().add(tool);

        frame.addWindowListener( AppManager.getInstance().getWindowListener(tool) );

        frame.pack();
        frame.setLocationRelativeTo(parent);
        
        frame.setVisible(true);

        return tool;
    }

    /**
     * copy image to the system clipboard.
     * @param parent
     * @param ssrc 
     */
    protected static void copyToClipboard( Component parent, String ssrc ) {
        if ( ssrc==null ) {
            JOptionPane.showMessageDialog( parent, "No image is selected." );
            return;
        }
        File src;
        try {
            src = FileSystemUtil.doDownload(ssrc, new NullProgressMonitor()); // should be local
        } catch (Exception ex) {
            JOptionPane.showMessageDialog( parent, "<html>Unexpected error when downloading file<br>" + ssrc+"<br><br>"+ex.toString() );
            return;
        }
        
        try {
            ImageSelection iss= new ImageSelection();
            iss.setImage( ImageIO.read( src ) );
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents( iss, ImageSelection.getNullClipboardOwner() );
        } catch (IOException ex) {
            JOptionPane.showMessageDialog( parent, "<html>Unable to read image<br>"+ex.getMessage() );
        }
   
    }
    
    /**
     * save a copy of the current selection to a local disk.
     */
    protected static void saveLocalCopy( Component parent, String ssrc ) {
        Preferences prefs = Preferences.userNodeForPackage(PngWalkTool.class);
        String srecent = prefs.get( PngWalkTool.PREF_RECENT, System.getProperty("user.home") );
        if ( ssrc==null ) {
            JOptionPane.showMessageDialog( parent, "No image is selected." );
            return;
        }
        File src;
        try {
            src = FileSystemUtil.doDownload(ssrc, new NullProgressMonitor()); // should be local
        } catch (Exception ex) {
            JOptionPane.showMessageDialog( parent, "<html>Unexpected error when downloading file<br>" + ssrc+"<br><br>"+ex.toString() );
            return;
        }

        JFileChooser chooser= new JFileChooser( srecent );
        chooser.setMultiSelectionEnabled(false);
        chooser.setSelectedFile( new File( chooser.getCurrentDirectory(), src.getName() ) );
        int r= chooser.showSaveDialog(parent);
        if ( r==JFileChooser.APPROVE_OPTION ) {
            prefs.put( PngWalkTool.PREF_RECENT, chooser.getSelectedFile().getParent() );
            try {
                if ( ! org.virbo.autoplot.Util.copyFile( src, chooser.getSelectedFile()) ) {
                    JOptionPane.showMessageDialog( parent, "<html>Unable to save image to: <br>" + chooser.getSelectedFile() );
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog( parent, "<html>Unable to save image to: <br>" + chooser.getSelectedFile()+"<br><br>"+ex.toString() );
            }
        }
    }

    /**
     * return the interval size (up/down)
     * @return 
     */
    int nextInterval( int index ) {
        Component c= tabs.getSelectedComponent();
        if ( c instanceof PngWalkView ) {
            PngWalkView v= (PngWalkView)c;
            return v.getNextInterval( index );
        } else if ( c instanceof JSplitPane ) {
            c= ((JSplitPane)c).getTopComponent();
            if ( c instanceof PngWalkView ) {
                PngWalkView v= (PngWalkView)c;
                return v.getNextInterval( index );
            } else {
                return index+7;
            }
        } else {
            return index+7;
        }
    }    

    /**
     * return the page size (page up/down)
     * @return 
     */
    int nextPage( int index) {
        Component c= tabs.getSelectedComponent();
        if ( c instanceof PngWalkView ) {
            PngWalkView v= (PngWalkView)c;
            return v.getNextPage( index );
        } else if ( c instanceof JSplitPane ) {
            c= ((JSplitPane)c).getTopComponent();
            if ( c instanceof PngWalkView ) {
                PngWalkView v= (PngWalkView)c;
                return v.getNextPage( index );
            } else {
                return index+7;
            }            
        } else {
            return index+28;
        }
    }
    
    /**
     * return the interval size (up/down)
     * @return 
     */
    int prevInterval( int index ) {
        Component c= tabs.getSelectedComponent();
        if ( c instanceof PngWalkView ) {
            PngWalkView v= (PngWalkView)c;
            return v.getPrevInterval( index );
        } else if ( c instanceof JSplitPane ) {
            c= ((JSplitPane)c).getTopComponent();
            if ( c instanceof PngWalkView ) {
                PngWalkView v= (PngWalkView)c;
                return v.getPrevInterval( index );
            } else {
                return index+7;
            }            
        } else {
            return index-7;
        }
    }    

    /**
     * return the page size (page up/down)
     * @return 
     */
    int prevPage( int index) {
        Component c= tabs.getSelectedComponent();
        if ( c instanceof PngWalkView ) {
            PngWalkView v= (PngWalkView)c;
            return v.getPrevPage( index );
        } else if ( c instanceof JSplitPane ) {
            c= ((JSplitPane)c).getTopComponent();
            if ( c instanceof PngWalkView ) {
                PngWalkView v= (PngWalkView)c;
                return v.getPrevPage( index );
            } else {
                return index+7;
            }
        } else {
            return index-28;
        }
    }

    private static JMenuBar createMenuBar( final PngWalkTool tool, final JFrame frame ) {
        JMenuBar result= new JMenuBar();
        JMenu fileMenu= new JMenu("File");

        fileMenu.add( new AbstractAction( "Save Local Copy..." ) {
            public void actionPerformed( ActionEvent e ) {
                LoggerManager.logGuiEvent(e);        
                saveLocalCopy(tool,tool.getSelectedFile());
            }
        } );
        fileMenu.add( new AbstractAction( "Show Autoplot" ) {
            public void actionPerformed(ActionEvent ae) {
               LoggerManager.logGuiEvent(ae);        
               AppManager appman= AppManager.getInstance();
               for ( int i=0; i< appman.getApplicationCount(); i++ ) {
                   if ( appman.getApplication(i) instanceof AutoplotUI  ) {
                       AutoplotUI.raiseApplicationWindow((AutoplotUI)appman.getApplication(i));
                       return;
                   }
               }
               if ( AppManager.getInstance().getApplicationCount()==1 ) {
                   ScriptContext.createGui();
                   Window apWindow= ScriptContext.getViewWindow();
                   raiseApWindowSoon(apWindow);
               }
            }
        } );
        fileMenu.add( new AbstractAction( "Close" ) {
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);        
                if ( AppManager.getInstance().getApplicationCount()==1 ) {
                    if ( JOptionPane.OK_OPTION==
                            JOptionPane.showConfirmDialog( tool,
                            "Quit application?", "Quit PNG Walk", JOptionPane.OK_CANCEL_OPTION ) ) {
                        frame.dispose();
                        AppManager.getInstance().closeApplication(tool);
                    }
                } else {
                    frame.dispose();
                    AppManager.getInstance().closeApplication(tool);
                }
            }
        } );

        fileMenu.add( new AbstractAction( "Quit" ) {
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);        
                if ( AppManager.getInstance().requestQuit() ) {
                    frame.dispose();
                    AppManager.getInstance().quit();
                }
            }
        } );

        result.add(fileMenu);

        BindingGroup bg= new BindingGroup();

        JMenu navMenu= new JMenu("Navigate");
        navMenu.add( new AbstractAction( "Go To Date..." ) {
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);        
                DatumRange dr= tool.seq.getTimeSpan();
                String str;
                if ( dr!=null ) {
                    str= JOptionPane.showInputDialog(tool,"Select date to display", TimeDatumFormatter.DAYS.format( TimeUtil.prevMidnight( dr.min() ) ) );
                } else {
                    JOptionPane.showMessageDialog( tool, "File times are not available" );
                    return;
                }
                if ( str!=null ) {
                    try {
                        DatumRange ds = DatumRangeUtil.parseTimeRange(str);
                        tool.seq.gotoSubrange(ds);
                    } catch (ParseException ex) {
                        try {
                            double d= Units.us2000.parse(str).doubleValue(Units.us2000);
                            tool.seq.gotoSubrange( DatumRange.newDatumRange( d, d, Units.us2000 ));
                        } catch (ParseException ex2) {
                            JOptionPane.showMessageDialog( tool, "parse error: "+ex2 );
                        }
                        return;
                    } catch (RuntimeException ex ) {
                        tool.setStatus( "warning: "+ex.toString() );
                    }
                }

            }
        } );
        
        navMenu.add( new AbstractAction( "First" ) {
            public void actionPerformed( ActionEvent e ) {
               LoggerManager.logGuiEvent(e);        
               tool.seq.setIndex( 0 );
            }
        } ).setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_HOME, 0 ));
        

        navMenu.add( new AbstractAction( "Previous Page" ) {
            public void actionPerformed( ActionEvent e ) {
               LoggerManager.logGuiEvent(e);        
               tool.seq.setIndex( tool.prevPage(tool.seq.getIndex()) );
            }
        } ).setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_PAGE_UP, 0 ));
        

        navMenu.add( new AbstractAction( "Previous Interval" ) {
            public void actionPerformed( ActionEvent e ) {
               LoggerManager.logGuiEvent(e);        
               tool.seq.setIndex( tool.prevInterval(tool.seq.getIndex()) );
            }
        } ).setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_UP, 0 ));

        navMenu.add( new AbstractAction( "Previous Item" ) {
            public void actionPerformed( ActionEvent e ) {
               LoggerManager.logGuiEvent(e);        
               tool.seq.skipBy( -1 );
            }
        } ).setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_LEFT, 0 ));


        navMenu.add( new AbstractAction( "Next Item" ) {
            public void actionPerformed( ActionEvent e ) {
               LoggerManager.logGuiEvent(e);        
               tool.seq.skipBy( 1 );
            }
        } ).setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_RIGHT, 0 ));

        navMenu.add( new AbstractAction( "Next Interval" ) {
            public void actionPerformed( ActionEvent e ) {
               LoggerManager.logGuiEvent(e);        
               tool.seq.setIndex(tool.nextInterval(tool.seq.getIndex()) );
            }
        } ).setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_DOWN, 0 ));


        navMenu.add( new AbstractAction( "Next Page" ) {
            public void actionPerformed( ActionEvent e ) {
               LoggerManager.logGuiEvent(e);        
               tool.seq.setIndex( tool.nextPage(tool.seq.getIndex()) );
            }
        } ).setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_PAGE_DOWN, 0 ));

        navMenu.add( new AbstractAction( "Last" ) {
            public void actionPerformed( ActionEvent e ) {
               LoggerManager.logGuiEvent(e);        
               tool.seq.setIndex( tool.seq.size()-1 );
            }
        } ).setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_END, 0 ));

        result.add( navMenu );
        tool.navMenu= navMenu;
        
        navMenu.setEnabled(tool.seq!=null);
        
        final JMenu optionsMenu= new JMenu( "Options" );
        JCheckBoxMenuItem persMi= new JCheckBoxMenuItem("Use Perspective");
        bg.addBinding( Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, tool.views[4], BeanProperty.create("perspective"), persMi, BeanProperty.create("selected") ) );
        bg.bind();
        optionsMenu.add(persMi);

        ButtonGroup buttonGroup1 = new javax.swing.ButtonGroup();

        final JMenu thumbsizeMenu= new JMenu("Thumbnail Size" );
        final int[] sizes= new int[] { 50, 100, 200, 400 };
        for ( int i=0; i<sizes.length; i++ ) {
            final int fsize= sizes[i];
            final JCheckBoxMenuItem mi;
            mi= new JCheckBoxMenuItem(
               new AbstractAction(""+fsize+" px" ) {
                  public void actionPerformed( ActionEvent e ) {
                      LoggerManager.logGuiEvent(e);        
                      tool.setThumbnailSize(fsize);
                  }
               }
            );
            buttonGroup1.add(mi);
            if ( tool.getThumbnailSize()==sizes[i] ) {
                buttonGroup1.setSelected( mi.getModel(), true );
            }
            thumbsizeMenu.add( mi );
        }
        optionsMenu.add( thumbsizeMenu );

        final JMenuItem qc= new JMenuItem( new AbstractAction( "Start QC" ) {
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);        
                if ( !tool.isQualityControlEnabled() ) {
                    tool.startQC();
                }
            }
        });
        qc.setToolTipText("Start up the Quality Control tool that adds documentation to images.");
        optionsMenu.add( qc );
        
        final JMenuItem dg= new JMenuItem( new AbstractAction( "Start Digitizer" ) {
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);        
                if ( !tool.isDigitizerEnabled() ) {
                    tool.startDigitizer();
                }
            }
        });
        dg.setToolTipText("Start up the Digitizer that receives pairs from the single view.  See http://autoplot.org/richPng.");
        optionsMenu.add( dg );
        
        result.add( optionsMenu );

        final JMenu bookmarksMenu= new JMenu("Bookmarks");
        final BookmarksManager man= new BookmarksManager(frame,true,"PNG Bookmarks");

        man.getModel().addPropertyChangeListener( BookmarksManagerModel.PROP_LIST, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                man.updateBookmarks( bookmarksMenu, tool.getSelector() );
            }
        });
        man.setVisible(false);
        man.setPrefNode("pngwalk","autoplot.default.pngwalk.bookmarks", "http://autoplot.org/data/pngwalk.demos.xml");

        man.updateBookmarks( bookmarksMenu, tool.getSelector() );

        result.add( bookmarksMenu );

        return result;
    }


    /** Creates new form PngWalkTool */
    public PngWalkTool() {
        initComponents();
        setNavButtonsEnabled(false);
        dataSetSelector1.setEnableDataSource(false);
        dataSetSelector1.setAcceptPattern("(?i).*(\\.gif|\\.png|\\.jpg|\\.pngwalk)");
        dataSetSelector1.setSuggestFiles(false); // only aggs.
        dataSetSelector1.addSuggestFile(".*\\.pngwalk");
        dataSetSelector1.registerActionTrigger( ".*\\.pngwalk", new AbstractAction("pngwalk") {
            public void actionPerformed( ActionEvent ev ) {
                String template= dataSetSelector1.getValue();
                if ( template.endsWith(".pngwalk") ) {
                   template= readPngwalkFile(template);
                }
                setTemplate(template);
            }
        });

        views= new PngWalkView[7];

        views[0]= new GridPngWalkView( null );
        views[1]= new RowPngWalkView( null );
        views[2]= new SinglePngWalkView( null );
        views[3]= new SinglePngWalkView( null );
        views[4]= new CoversWalkView( null );
        views[5]= new SinglePngWalkView( null );
        views[6]= new ContextFlowView(null);

        final int SCROLLBAR_HEIGHT = (int) Math.round( new JScrollPane().getHorizontalScrollBar().getPreferredSize().getHeight() );

        final JSplitPane filmStripSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, views[1], views[2] );

        //p.setEnabled(false);  //prevents user manipulation
        filmStripSplitPane.setDividerLocation(getThumbnailSize()+ (int)(1.2 *SCROLLBAR_HEIGHT));
        views[1].addPropertyChangeListener( PngWalkView.PROP_THUMBNAILSIZE, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                filmStripSplitPane.setDividerLocation( (Integer)evt.getNewValue() + SCROLLBAR_HEIGHT );
            }
        });

        filmStripSplitPane.setMinimumSize( new Dimension(640,480) );
        filmStripSplitPane.setPreferredSize( new Dimension(640,480) );
        
        final JSplitPane coversSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, views[4], views[5] );
        coversSplitPane.setDividerLocation(getThumbnailSize()+ (int)(1.2 *SCROLLBAR_HEIGHT));
        views[4].addPropertyChangeListener( PngWalkView.PROP_THUMBNAILSIZE, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                coversSplitPane.setDividerLocation( (Integer)evt.getNewValue() + SCROLLBAR_HEIGHT  );
            }
        });
        
        coversSplitPane.setMinimumSize( new Dimension(640,480) );
        coversSplitPane.setPreferredSize( new Dimension(640,480) );

        tabs= new TearoffTabbedPane();

        tabs.addTab( "Single", new JScrollPane( views[3] ) );
        tabs.addTab( "ContextFlow", views[6] );
        tabs.addTab( "Grid", views[0] );
        tabs.addTab( "Film Strip", filmStripSplitPane );
        tabs.addTab( "Covers", coversSplitPane );

        tabs.setSelectedIndex(3);
        
        // add listener to jump to and from the single image view.
        for ( int i=0; i<views.length; i++ ) {
            views[i].getMouseTarget().addMouseListener( new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if ( e.getClickCount()==2 ) {
                        int oldIndex= tabs.getSelectedIndex();
                        if ( oldIndex==0 ) {
                            tabs.setSelectedIndex( returnTabIndex );
                        } else {
                            tabs.setSelectedIndex(0);
                            returnTabIndex= oldIndex;
                        }
                    }
                }
            });
        }

        tabs.setFocusable(false);
        
        nextButton.requestFocus();

        if (isQualityControlEnabled()) {
            qcPanel = new QualityControlPanel();
            JSplitPane qcPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, tabs, qcPanel);
            qcPane.setResizeWeight(1.0);
            pngsPanel.add(qcPane);
            qcPanel.setWalkImageSequece(seq);
        } else {
            pngsPanel.add( tabs );
        }
        pngsPanel.revalidate();

        BindingGroup bc= new BindingGroup();
        for ( int i=0; i<views.length; i++ ) {
            Binding b= Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, views[i],
                    BeanProperty.create("thumbnailSize"), this, BeanProperty.create("thumbnailSize") );
            bc.addBinding( b );
        }
        bc.bind();
        
        addMouseWheelListener( new MouseWheelListener() {

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                 if ( seq!=null && seq.size()!=0 ) seq.skipBy(e.getWheelRotation());
            }

        });

        setStatus("ready");

    }

    private void processArguments( ArgumentList alm ) {
        String tab= alm.getValue("mode");
        if ( tab.equalsIgnoreCase("filmStrip" ) ) {
            tabs.setSelectedIndex(3);
        } else if ( tab.equalsIgnoreCase("single" ) ) {
            tabs.setSelectedIndex(0);
        } else if ( tab.equalsIgnoreCase("contextFlow") ) {
            tabs.setSelectedIndex(1);
        } else if ( tab.equalsIgnoreCase("grid" ) ) {
            tabs.setSelectedIndex(2);
        } else if ( tab.equalsIgnoreCase("film strip" ) ) {
            tabs.setSelectedIndex(3);
        } else if ( tab.equalsIgnoreCase("covers" ) ) {
            tabs.setSelectedIndex(4);
        }

        String show= alm.getValue("goto");
        if ( !show.equals("") && seq!=null ) {
            try {      
                if ( seq.getTimeSpan()!=null ) {
                    seq.gotoSubrange(DatumRangeUtil.parseTimeRange(show));
                } else {
                    this.pendingGoto= DatumRangeUtil.parseTimeRange(show);
                }
            } catch ( ParseException ex ) {
                throw new RuntimeException(ex);
            }
        } else {
            logger.fine("show was empty or seq was null");
        }
    }

    /**
     * respond to changes of the current index.
     */
    private transient PropertyChangeListener indexListener= new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            if ( seq==null ) {
                logger.fine("seq was null");
                return;
            }
            String item= seq.currentImage().getUri().toString();

            for ( int i=0; i<actionEnablers.size(); i++ ) {
                boolean actionEnabled= actionEnablers.get(i).isActionEnabled(item);
                actionButtons.get(i).setEnabled(actionEnabled);
                if ( actionEnabled ) {
                   actionButtons.get(i).setActionCommand(actionCommand+" "+item);
                }
            }
            firePropertyChange( PROP_SELECTED_NAME, null, seq.getSelectedName() );
            if (qcPanel != null && seq.getQualityControlSequence()!=null ) {
                qcPanel.displayRecord( seq.getQualityControlSequence().getQualityControlRecord( seq.getIndex() ));
            }
        }
    };

    /**
     * listen for status updates from other agents, relay the status for the view.
     */
    private transient PropertyChangeListener statusListener= new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
           setStatus((String)evt.getNewValue());
        }
    };

    /**
     *
     */
    private transient PropertyChangeListener qcStatusListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            if ( seq==null ) {
                logger.fine("seq was null");
                return;
            }
            if ( seq.getQualityControlSequence()!=null ) {
                int n[] = seq.getQualityControlSequence().getQCTotals();
                qcPanel.setStatus(n[0], n[1], n[2], n[3]);
            }
        }
    };

    private void setNavButtonsEnabled( boolean enabled ) {
        jumpToFirstButton.setEnabled(enabled);
        jumpToLastButton.setEnabled(enabled);
        prevButton.setEnabled(enabled);
        nextButton.setEnabled(enabled);
        nextSetButton.setEnabled(enabled);
        prevSetButton.setEnabled(enabled);
    }
    
    /**
     * set the template which the PNGWalk Tool will display.
     * @param template file template, like /tmp/$Y$m$d.png
     */
    public void setTemplate( String template ) {

        if ( template.contains("%") && !template.contains("$") ) {
            template= template.replaceAll("\\%","\\$");
            if ( template.contains("{") && !template.contains("(") ) {
                template= template.replaceAll("\\{","(");
                template= template.replaceAll("\\}",")");
            }
        }
        
        dataSetSelector1.setValue(template);

        WalkImageSequence oldseq= this.seq;

        URI uri= DataSetURI.getResourceURI(template);
        if ( uri==null ) {
            throw new IllegalArgumentException("Unable to parse: "+template);
        }
        String surl= DataSetURI.fromUri( uri );

        try {
            seq= new WalkImageSequence( surl );
            setNavButtonsEnabled(true);
            if ( navMenu!=null ) navMenu.setEnabled(true);
        } catch ( Exception ex ) {
            seq= null;
            setNavButtonsEnabled(false);
            if ( navMenu!=null ) navMenu.setEnabled(false);
            logger.log( Level.WARNING, ex.getMessage(), ex );
        }

        if ( oldseq!=null ) {
            oldseq.removePropertyChangeListener(WalkImageSequence.PROP_INDEX, indexListener );
            oldseq.removePropertyChangeListener(WalkImageSequence.PROP_STATUS, statusListener);
            if (ENABLE_QUALITY_CONTROL) oldseq.removePropertyChangeListener(WalkImageSequence.PROP_BADGE_CHANGE, qcStatusListener);
            oldseq.removePropertyChangeListener( PROP_TIMERANGE, seqTimeRangeListener );
            oldseq.removePropertyChangeListener( WalkImageSequence.PROP_INDEX, seqIndexListener );
        }
        if ( seq!=null ) {
            seq.addPropertyChangeListener( WalkImageSequence.PROP_INDEX, indexListener );
            seq.addPropertyChangeListener( WalkImageSequence.PROP_STATUS, statusListener );
            if (ENABLE_QUALITY_CONTROL) seq.addPropertyChangeListener(WalkImageSequence.PROP_BADGE_CHANGE, qcStatusListener);
            seq.addPropertyChangeListener( PROP_TIMERANGE, seqTimeRangeListener );
            seq.addPropertyChangeListener( WalkImageSequence.PROP_INDEX, seqIndexListener );
        }

        if ( template.length()==0 ) {
            setStatus("Enter the location of a pngwalk file by providing a template for the files, such as /tmp/$Y$m$d.png");
            return;
        }
        
        Runnable run= new Runnable() {
            public void run() {
                try {
                    seq.initialLoad();

                    if ( pendingGoto!=null )  {
                        seq.gotoSubrange(pendingGoto);
                        pendingGoto= null;
                    }

                } catch (java.io.IOException e) {
                    // This probably means the template was invalid. Don't set new sequence.
                    if ( !getStatus().startsWith("error") ) setStatus("error:"+e.getMessage());
                    throw new RuntimeException(e);
                }

                SwingUtilities.invokeLater( new Runnable() {
                    public void run() {
                        updateInitialGui();
                    }
                });
                
            }

        };

        new Thread(run).start();

    }

    /**
     * initial settings to be performed on the event thread.
     */
    private void updateInitialGui() {
        List<String> urls = new ArrayList<String>();
        List<String> recent = dataSetSelector1.getRecent();
        recent.removeAll( Collections.singleton( seq.getTemplate() ) );
        for (String b : recent) {
            urls.add( b );
        }
        urls.add( seq.getTemplate() );
        dataSetSelector1.setRecent(urls);

        useRangeCheckBox.setEnabled(seq.getTimeSpan() != null);

        // always clear subrange on new sequence
        useRangeCheckBox.setSelected(false);
        editRangeButton.setEnabled(false);
        timeFilterTextField.setEnabled(false);
        timeFilterTextField.setText("");

        if ( seq.size()==0 ) {
            Container p= SwingUtilities.getWindowAncestor(this);
            if ( this.getX()!=0 ) p= this; // for Linux, where the component isn't initialized yet.
            JOptionPane.showMessageDialog( p, "<html>Unable to find any images in sequence:<br>"+ seq.getTemplate() );
            return;
        }
        
        DatumRange tr=seq.currentImage().getDatumRange();
        if ( tr!=null ) setTimeRange( tr );

        showMissingCheckBox.setEnabled(seq.getTimeSpan() != null);
        if (seq.getTimeSpan() == null) {
            //Can't identify missing images if there's no date info in template
            showMissingCheckBox.setEnabled(false);
            showMissingCheckBox.setSelected(false);
        } else {
            seq.setShowMissing(showMissingCheckBox.isSelected());
        }
        for (PngWalkView v : views) {
            v.setSequence(seq);
        }
        if ( seq.size()==0 ) {
            setStatus("warning: no files found in "+seq.getTemplate() );
        } else {
            indexListener.propertyChange( null );
            if (qcPanel != null ) {
                qcPanel.setWalkImageSequece(seq);
                if ( seq.getIndex()<seq.size() ) {
                    if ( seq.getQualityControlSequence()!=null ) {
                        QualityControlRecord rec= seq.getQualityControlSequence().getQualityControlRecord(seq.getIndex());
                        qcPanel.displayRecord(rec);
                        int n[] = seq.getQualityControlSequence().getQCTotals();
                        qcPanel.setStatus(n[0], n[1], n[2], n[3]);
                    }
                } else {
                    qcPanel.setStatus(0,0,0,0);
                }
            }
        }
    }

    public String getTemplate() {
        return seq.getTemplate();
    }

    protected int thumbnailSize = 100;
    public static final String PROP_THUMBNAILSIZE = "thumbnailSize";

    public int getThumbnailSize() {
        return thumbnailSize;
    }

    public void setThumbnailSize(int thumbnailSize) {
        int oldThumbnailSize = this.thumbnailSize;
        this.thumbnailSize = thumbnailSize;
        firePropertyChange(PROP_THUMBNAILSIZE, oldThumbnailSize, thumbnailSize);
    }


    /**
     * roughly the timerange displayed and selected.  This is left loose to support binding.
     */
    transient DatumRange timeRange;
    public static final String PROP_TIMERANGE = "timeRange";

    public DatumRange getTimeRange() {
        return timeRange;
    }

    /**
     * timerange roughly the focus timerange.  This property is introduced to allow for binding between pngwalks.
     * @param timeRange
     */
    public void setTimeRange(DatumRange timeRange) {
        boolean setting0= setting;
        setting= true;
        DatumRange old= this.timeRange;
        this.timeRange = timeRange;
        if ( timeRange!=null ) seq.gotoSubrange(timeRange);
        if ( setting0 ) firePropertyChange(PROP_TIMERANGE, old, thumbnailSize);
        setting= false;
    }

    transient PropertyChangeListener seqTimeRangeListener= new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            setTimeRange((DatumRange)evt.getNewValue());
        }
    };

    boolean setting= false;

    transient PropertyChangeListener seqIndexListener= new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            boolean setting0= setting;
            setting= true;
            DatumRange dr= seq.currentImage().getDatumRange();
            if ( setting0 && dr!=null ) setTimeRange( dr );
            setting= false;
        }
    };

    transient protected String status = "initializing...";
    public static final String PROP_STATUS = "status";

    public String getStatus() {
        return status;
    }

    public void setStatus(String message) {
        String oldStatus = this.status;
        this.status = message;
        if ( message.startsWith("busy:" ) ) {
            setMessage( BUSY_ICON, message.substring(5).trim() );
            logger.finer(message);
        } else if ( message.startsWith("warning:" ) ) {
            setMessage( WARNING_ICON, message.substring(8).trim() );
            logger.warning(message);
        } else if ( message.startsWith("error:" ) ) {
            setMessage( ERROR_ICON, message.substring(6).trim() );
            logger.severe(message);
        } else {
            logger.fine(message);
            setMessage(message);
        }

        firePropertyChange(PROP_STATUS, oldStatus, message);
    }

    public void setMessage(String message) {
        this.statusLabel.setIcon( IDLE_ICON );
        this.statusLabel.setText(message);
    }

    public void setMessage( Icon icon, String message ) {
        if ( message==null ) message= "<null>"; // TODO: fix this later
        String myMess= message;
        if ( myMess.length()>100 ) myMess= myMess.substring(0,100)+"...";
        this.statusLabel.setIcon( icon );
        this.statusLabel.setText(myMess);
        this.statusLabel.setToolTipText(message);
    }

    public void startQC() {
        if ( !isQualityControlEnabled() ) {
            qcPanel= new QualityControlPanel();
            tabs.add( "Quality Control", qcPanel );
            qcPanel.setWalkImageSequece(seq);
            seq.addPropertyChangeListener(WalkImageSequence.PROP_BADGE_CHANGE, qcStatusListener);
            ENABLE_QUALITY_CONTROL= true;
        } else {
            throw new RuntimeException("Quality Control is already running.");
        }

    }

    protected DataPointRecorder digitizer= null;
    protected char annoTypeChar= '|';
            
    private void startDigitizer() {
        if ( digitizer==null ) {
            digitizer= new DataPointRecorder();
            digitizer.addDataSetUpdateListener( new DataSetUpdateListener() {
                @Override
                public void dataSetUpdated(DataSetUpdateEvent e) {
                    for (PngWalkView v : views) {
                       if ( v instanceof SinglePngWalkView ) {
                           v.repaint();
                        }
                    }
                }
            });
            digitizer.addDataPointSelectionListener( new DataPointSelectionListener() {
                @Override
                public void dataPointSelected(DataPointSelectionEvent e) {
                    Datum x= e.getX();
                    if ( UnitsUtil.isTimeLocation( x.getUnits() ) ) {
                        seq.gotoSubrange( new DatumRange( x,x ) );
                    }
                }
            });
            tabs.add( "Digitizer" , digitizer );
            for (PngWalkView v : views) {
               if ( v instanceof SinglePngWalkView ) {
                   ((SinglePngWalkView)v).clickDigitizer.setViewer(this);
               }
            }
            
            JComboBox annoType= new JComboBox( new String[] { "| vertical line", "+ cross hairs", ". dots" } );
            digitizer.addAccessory( annoType );
            
            annoType.addItemListener( new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    annoTypeChar= e.getItem().toString().charAt(0);
                    for (PngWalkView v : views) {
                       if ( v instanceof SinglePngWalkView ) {
                           v.repaint();
                        }
                    }
                }
                
            });
        }
    }

    private boolean isDigitizerEnabled() {
        return digitizer!=null;
    }

    public static interface ActionEnabler {
        boolean isActionEnabled( String filename );
    }

    /**
     * Enabler that returns true for local files.
     */
    public static final ActionEnabler LOCAL_FILE_ENABLER = new ActionEnabler() {
        public boolean isActionEnabled( String filename ) {
            return DataSetURI.getResourceURI(filename).toString().startsWith("file:" );
        }
    };

    transient List<ActionEnabler> actionEnablers= new ArrayList<ActionEnabler>();
    List<JButton> actionButtons= new ArrayList<JButton>();

    /**
     * Add a file action button to the GUI.  
     * @param match returns true when the action can be applied to the current image.
     * @param abstractAction the action.
     * @see #LOCAL_FILE_ENABLER which returns true for local files.
     */    
    public void addFileAction( ActionEnabler match, Action abstractAction ) {
        this.actionEnablers.add( match );
        JButton b= new JButton( abstractAction );
        this.actionButtons.add( b );
        actionButtonsPanel.add( b );
        this.revalidate();
    }
    
    /**
     * returns the current selection, which may be a URL on a remote site, or null if no sequence has been selected.
     * @return the current selection.
     */
    public String getSelectedFile() {
        if ( seq==null ) return null;
        return DataSetURI.fromUri( seq.currentImage().getUri() );
    }

    public static final String PROP_SELECTED_NAME = "selectedName";

    /**
     * return the name of the current selection, which is just the globbed or aggregated part of the names.
     * This is introduced to support tying two pngwalks together.
     * @return the name of the currently selected file.
     */
    public String getSelectedName() {
        if ( seq.size()>0 ) {
            return seq.getSelectedName();
        } else {
            return "";
        }
    }

    /**
     * set the name of the file to select, which is just the globber or aggregated part of the name.  For example, 
     * if getTemplate is file:/tmp/$Y$m$d.gif, then the setSelectedName might be 20141111.gif.  If the name is not found in the 
     * pngwalk, then this has no effect.
     * @param name the new name
     */
    public void setSelectedName(String name) {
        String oldName= getSelectedName();
        int i= seq.findIndex( name );
        if ( i!=-1 ) {
            seq.setIndex(i);
        }
        firePropertyChange( PROP_SELECTED_NAME, oldName, name );
    }

    DataSetSelector getSelector() {
        return this.dataSetSelector1;
    }

    /**
     * return true of the quality control panel is enabled.
     * @return return true of the quality control panel is enabled.
     */
    public static boolean isQualityControlEnabled() {
        return ENABLE_QUALITY_CONTROL;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        pngsPanel = new javax.swing.JPanel();
        timeFilterTextField = new javax.swing.JTextField();
        actionButtonsPanel = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        prevSetButton = new javax.swing.JButton();
        prevButton = new javax.swing.JButton();
        nextButton = new javax.swing.JButton();
        nextSetButton = new javax.swing.JButton();
        jumpToFirstButton = new javax.swing.JButton();
        jumpToLastButton = new javax.swing.JButton();
        dataSetSelector1 = new org.virbo.datasource.DataSetSelector();
        statusLabel = new javax.swing.JLabel();
        showMissingCheckBox = new javax.swing.JCheckBox();
        useRangeCheckBox = new javax.swing.JCheckBox();
        editRangeButton = new javax.swing.JButton();

        pngsPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        pngsPanel.setLayout(new java.awt.BorderLayout());

        timeFilterTextField.setToolTipText("Enter a time range, for example a year like \"2009\", or month \"2009 may\", or \"2009-01-01 to 2009-03-10\"\n");
        timeFilterTextField.setEnabled(false);
        timeFilterTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timeFilterTextFieldActionPerformed(evt);
            }
        });
        timeFilterTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                timeFilterTextFieldFocusLost(evt);
            }
        });

        actionButtonsPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));

        prevSetButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/resources/prevPrevPrev.png"))); // NOI18N
        prevSetButton.setToolTipText("Skip to previous interval");
        prevSetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                prevSetButtonActionPerformed(evt);
            }
        });

        prevButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/resources/prevPrev.png"))); // NOI18N
        prevButton.setToolTipText("previous");
        prevButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                prevButtonActionPerformed(evt);
            }
        });

        nextButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/resources/nextNext.png"))); // NOI18N
        nextButton.setToolTipText("next");
        nextButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nextButtonActionPerformed(evt);
            }
        });

        nextSetButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/resources/nextNextNext.png"))); // NOI18N
        nextSetButton.setToolTipText("Skip to next interval");
        nextSetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nextSetButtonActionPerformed(evt);
            }
        });

        jumpToFirstButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/resources/prevPrevPrevStop.png"))); // NOI18N
        jumpToFirstButton.setToolTipText("jump to first");
        jumpToFirstButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jumpToFirstButtonActionPerformed(evt);
            }
        });

        jumpToLastButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/resources/nextNextNextStop.png"))); // NOI18N
        jumpToLastButton.setToolTipText("jump to last");
        jumpToLastButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jumpToLastButtonActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .addContainerGap(112, Short.MAX_VALUE)
                .add(jumpToFirstButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(prevSetButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(prevButton)
                .add(39, 39, 39)
                .add(nextButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(nextSetButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jumpToLastButton))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(prevButton)
            .add(prevSetButton)
            .add(jumpToFirstButton)
            .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                .add(nextButton)
                .add(nextSetButton)
                .add(jumpToLastButton))
        );

        jPanel1Layout.linkSize(new java.awt.Component[] {nextButton, nextSetButton, prevButton, prevSetButton}, org.jdesktop.layout.GroupLayout.VERTICAL);

        dataSetSelector1.setToolTipText("Enter the location of the images as a wildcard (/tmp/*.png) or template (/tmp/$Y$m$d.png).  .png, .gif, and .jpg files are supported.");
        dataSetSelector1.setPromptText("Enter images filename template");
        dataSetSelector1.setValue("");
        dataSetSelector1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataSetSelector1ActionPerformed(evt);
            }
        });

        statusLabel.setText("starting application...");

        showMissingCheckBox.setText("Show Missing");
        showMissingCheckBox.setToolTipText("Insert placeholder images where there are gaps detected in the sequence");
        showMissingCheckBox.setEnabled(false);
        showMissingCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                showMissingCheckBoxItemStateChanged(evt);
            }
        });
        showMissingCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showMissingCheckBoxActionPerformed(evt);
            }
        });

        useRangeCheckBox.setText("Limit range to:");
        useRangeCheckBox.setToolTipText("Limit the time range of the images in the sequence.");
        useRangeCheckBox.setEnabled(false);
        useRangeCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                useRangeCheckBoxItemStateChanged(evt);
            }
        });

        editRangeButton.setText("Select...");
        editRangeButton.setEnabled(false);
        editRangeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editRangeButtonActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, pngsPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 1078, Short.MAX_VALUE)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 228, Short.MAX_VALUE)
                        .add(actionButtonsPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 463, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(layout.createSequentialGroup()
                        .addContainerGap()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(layout.createSequentialGroup()
                                .add(12, 12, 12)
                                .add(useRangeCheckBox)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(timeFilterTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 236, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                .add(12, 12, 12)
                                .add(editRangeButton)
                                .add(18, 18, 18)
                                .add(showMissingCheckBox))
                            .add(dataSetSelector1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 1054, Short.MAX_VALUE)))
                    .add(statusLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 1066, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(pngsPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 638, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(dataSetSelector1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 27, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(timeFilterTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(useRangeCheckBox)
                    .add(editRangeButton)
                    .add(showMissingCheckBox))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(actionButtonsPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(statusLabel))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void nextButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nextButtonActionPerformed
        LoggerManager.logGuiEvent(evt);
        seq.skipBy( 1 );
}//GEN-LAST:event_nextButtonActionPerformed

    private void prevButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_prevButtonActionPerformed
        LoggerManager.logGuiEvent(evt);
        seq.skipBy( -1 );
    }//GEN-LAST:event_prevButtonActionPerformed

    private void nextSetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nextSetButtonActionPerformed
        LoggerManager.logGuiEvent(evt);
        seq.setIndex( nextInterval( seq.getIndex() ) );
}//GEN-LAST:event_nextSetButtonActionPerformed

    private void prevSetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_prevSetButtonActionPerformed
        LoggerManager.logGuiEvent(evt);
        seq.setIndex( prevInterval( seq.getIndex() ) );
}//GEN-LAST:event_prevSetButtonActionPerformed

    private void timeFilterTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeFilterTextFieldActionPerformed
        try {
            LoggerManager.logGuiEvent(evt);
            timeFilterTextField.setBackground( dataSetSelector1.getBackground() );
            DatumRange range= DatumRangeUtil.parseTimeRange(timeFilterTextField.getText());
            seq.setActiveSubrange( range );
        } catch ( ParseException ex ) {
            timeFilterTextField.setBackground( Color.PINK );
        }

        //        canvas.setTimeRange( timeFilterTextField.getText() );
//        if ( !canvas.getTimeRange().equals(timeFilterTextField.getText() ) ) {
//            timeFilterTextField.setBackground( Color.PINK );
//        } else {
//            timeFilterTextField.setBackground( dataSetSelector1.getBackground() );
//        }
    }//GEN-LAST:event_timeFilterTextFieldActionPerformed

    private void timeFilterTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_timeFilterTextFieldFocusLost
//        canvas.setTimeRange( timeFilterTextField.getText() );
//        if ( !canvas.getTimeRange().equals(timeFilterTextField.getText() ) ) {
//            timeFilterTextField.setBackground( Color.PINK );
//        } else {
//            timeFilterTextField.setBackground( dataSetSelector1.getBackground() );
//        }
    }//GEN-LAST:event_timeFilterTextFieldFocusLost

    private void jumpToLastButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jumpToLastButtonActionPerformed
        LoggerManager.logGuiEvent(evt);
        seq.last();
    }//GEN-LAST:event_jumpToLastButtonActionPerformed

    private void jumpToFirstButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jumpToFirstButtonActionPerformed
        LoggerManager.logGuiEvent(evt);
        seq.first();
    }//GEN-LAST:event_jumpToFirstButtonActionPerformed

    private void dataSetSelector1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSetSelector1ActionPerformed
        LoggerManager.logGuiEvent(evt);
        String t= dataSetSelector1.getValue();
        if ( t.endsWith(".pngwalk") ) {
            t= readPngwalkFile(t);
        }
        setTemplate( t );
        nextButton.requestFocus();
    }//GEN-LAST:event_dataSetSelector1ActionPerformed

    private void showMissingCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_showMissingCheckBoxItemStateChanged
        seq.setShowMissing(evt.getStateChange()==java.awt.event.ItemEvent.SELECTED);
    }//GEN-LAST:event_showMissingCheckBoxItemStateChanged

    private void editRangeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editRangeButtonActionPerformed
        LoggerManager.logGuiEvent(evt);
        Frame myFrame = (java.awt.Frame)SwingUtilities.getWindowAncestor(this);
        SubrangeEditorDialog d = new SubrangeEditorDialog(myFrame, true);
        List<DatumRange> times= seq.getAllTimes();
        d.setTimeSpan(times);
        if(seq.isUseSubRange()) {
            List<DatumRange> sub = seq.getActiveSubrange();
            d.setStartIndex(times.indexOf(sub.get(0)));
            d.setEndIndex(times.indexOf(sub.get(sub.size()-1)));
        }
        d.setVisible(true);  //blocks until dialog closes

        if (d.isOkClicked()) {
            //System.err.printf("OK, start index is %d and end index is %d.%n", d.getStartIndex(), d.getEndIndex());
            seq.setActiveSubrange(d.getStartIndex(), d.getEndIndex());
            DatumRange range= new DatumRange( times.get(d.getStartIndex()).min(), times.get(d.getEndIndex()).max() );
            timeFilterTextField.setText( range.toString() );
        }
    }//GEN-LAST:event_editRangeButtonActionPerformed

    private void useRangeCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_useRangeCheckBoxItemStateChanged
        boolean enable= evt.getStateChange()==java.awt.event.ItemEvent.SELECTED;
        seq.setUseSubRange(enable);
        timeFilterTextField.setEnabled(enable);
        editRangeButton.setEnabled(enable);
        
        if (!enable) return;

        List<DatumRange> current = seq.getActiveSubrange();
        DatumRange range= DatumRangeUtil.union(current.get(0), current.get(current.size()-1));
        if ( range==null ) {
            timeFilterTextField.setText("error"); // shouldn't get here
        } else {
            timeFilterTextField.setText( range.toString() );
        }
    }//GEN-LAST:event_useRangeCheckBoxItemStateChanged

    private void showMissingCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showMissingCheckBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_showMissingCheckBoxActionPerformed



    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel actionButtonsPanel;
    private org.virbo.datasource.DataSetSelector dataSetSelector1;
    private javax.swing.JButton editRangeButton;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JButton jumpToFirstButton;
    private javax.swing.JButton jumpToLastButton;
    private javax.swing.JButton nextButton;
    private javax.swing.JButton nextSetButton;
    private javax.swing.JPanel pngsPanel;
    private javax.swing.JButton prevButton;
    private javax.swing.JButton prevSetButton;
    private javax.swing.JCheckBox showMissingCheckBox;
    private javax.swing.JLabel statusLabel;
    private javax.swing.JTextField timeFilterTextField;
    private javax.swing.JCheckBox useRangeCheckBox;
    // End of variables declaration//GEN-END:variables

}
