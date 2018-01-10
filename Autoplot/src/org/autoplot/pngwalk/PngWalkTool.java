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

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfWriter;
import external.AnimatedGifDemo;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UnsupportedLookAndFeelException;
import org.das2.components.DasProgressPanel;
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
import org.das2.util.FileUtil;
import org.das2.util.ImageUtil;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystem.FileSystemOfflineException;
import org.das2.util.monitor.AlertNullProgressMonitor;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Binding;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.autoplot.AppManager;
import org.autoplot.AutoplotUI;
import org.autoplot.AutoplotUtil;
import org.autoplot.GuiSupport;
import org.autoplot.JythonUtil;
import org.autoplot.ScriptContext;
import org.autoplot.bookmarks.Bookmark;
import org.autoplot.bookmarks.BookmarksException;
import org.autoplot.bookmarks.BookmarksManager;
import org.autoplot.bookmarks.BookmarksManagerModel;
import org.autoplot.bookmarks.Util;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.transferrable.ImageSelection;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.DataSetSelector;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.FileSystemUtil;
import org.autoplot.datasource.URISplit;
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
    private static final String RESOURCES= "/org/autoplot/resources/";
    private static final Icon WARNING_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"warning-icon.png") );
    private static final Icon ERROR_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"error-icon.png") );
    private static final Icon BUSY_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"spinner.gif") );
    private static final Icon READY_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"indProgress0.png") );
    private static final Icon IDLE_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"idle-icon.png") );
        
    int returnTabIndex=0; // index of the tab we left to look at the single panel view.  TODO: account for tear off.

    transient DatumRange pendingGoto= null;  // after password is entered, then go to this range.

    private String product; // the product
    private String baseurl; // the base url
    
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
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
                logger.log( Level.WARNING, e.getMessage(), e );
            }
        }

        ENABLE_QUALITY_CONTROL = alm.getBooleanValue("qualityControl");

        String template = alm.getValue("template"); // One Slash!!
        //final String template=  "file:/home/jbf/temp/product_$Y$m$d.png" ; // One Slash!!
        //final String template=  "file:/net/spot3/home/jbf/fun/pics/2001minnesota/.*JPG" ;
        //final String template= "file:/home/jbf/public_html/voyager/VGPW_0201/BROWSE/V1/.*.PNG";
        //final String template= "file:///net/spot3/home/jbf/fun/pics/20080315_tenerife_masca_hike/IMG_.*.JPG";
        //final String template= "http://www.swpc.noaa.gov/ftpdir/lists/hpi/plots/pmap_$Y_$m_$d_...._S_.*_.*_.*_.*.gif";

        PngWalkTool pngWalkTool= start( template, null );

        pngWalkTool.processArguments(alm);

    }

    /**
     * returns a map containing data from the .pngwalk file.
     * <ul>
     * <li>product
     * <li>template
     * </ul>
     * @param template
     * @return the map
     */
    private static Map readPngwalkFile( String template ) {
        URISplit split= URISplit.parse(template);
        InputStream in=null;
        String product= "";
        String baseurl= "";
        try {
            Properties p= new Properties();
            if ( split.file==null ) {
                throw new IllegalArgumentException("template does not appear to be files: "+template);
            }
            File local= FileSystemUtil.doDownload( split.file, new NullProgressMonitor() );
            in= new FileInputStream( local );
            p.load( in );
            String vers= p.getProperty("version");
            if ( vers==null || vers.trim().length()==0 ) vers=""; else vers="_"+vers;
            baseurl= p.getProperty("baseurl","."); // baseurl is needed so that pngwalks can be used out-of-context, for example when a browser downloads the file and hands it off to Autoplot.
            if ( !baseurl.equals(".") ) {
                if ( !baseurl.endsWith("/") ) {
                    baseurl= baseurl + "/";
                }
                split.path=baseurl;
            }
            String t;
            if ( !p.getProperty("filePattern","").equals("") ) {
                // names were specified in the batch file.
                t= split.path + p.getProperty("filePattern","");
            } else {
                t= split.path + p.getProperty("product") + "_" + p.getProperty("timeFormat") +vers + ".png";
            }
            template= t;
            product= p.getProperty("product");
        } catch (FileSystemOfflineException | URISyntaxException ex) {
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
        Map<String,String> result= new HashMap();
        result.put( "template", template );
        result.put( "product", product );
        result.put( "baseurl", baseurl );
        return result;

    }

    private static void raiseApWindowSoon( final Window apWindow ) {
        Runnable run= new Runnable() {
            @Override
            public void run() {
                GuiSupport.raiseApplicationWindow((JFrame)apWindow);
                apWindow.toFront();
                apWindow.repaint();
            }
        };
        SwingUtilities.invokeLater(run);
    }
    
    private static String checkRelativeBaseurl( String baseurl, String template, String product ) {
        if ( baseurl.equals(".") ) {
            URISplit split= URISplit.parse(template);
            String f= split.path;
            int i= f.indexOf( "/"+product+".pngwalk" );
            if ( i==-1 ) {
                i= f.indexOf('*');
                if ( i>-1 ) i= f.lastIndexOf('/',i);
            }
            if ( i==-1 ) {
                if ( f.endsWith("/") ) {
                    baseurl= f;
                }
            }
            if ( i>-1 ) {
                baseurl= f.substring(0,i+1);
            }
        }
        return baseurl;
    }
    
    /**
     * initialize a new PNGWalkTool with the given template.  
     * @param template the template, such as http://autoplot.org/data/pngwalk/product_$Y$m$d.vap
     * @param parent null or a parent component to own this application.
     * @return a PngWalkTool, which is visible and packed.
     */
    public static PngWalkTool start( String template, final Window parent ) {

        final PngWalkTool tool = new PngWalkTool();
        tool.parentWindow= parent;

        if ( template!=null ) {
            URISplit split= URISplit.parse(template);
            if ( split.file.endsWith(".pngwalk") ) {
                Map<String,String> map= readPngwalkFile(template);
                tool.product= map.get("product");
                tool.baseurl= map.get("baseurl");
                tool.baseurl= checkRelativeBaseurl(tool.baseurl, template, tool.product );
                template= map.get("template");
            } else {
                tool.product= "";
                tool.baseurl= "";
            }
            tool.setTemplate(template);
        } else {
            tool.product= "";
            tool.baseurl= "";
        }

        String sdeft= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><bookmark-list version=\"1.1\">    " +
        "<bookmark-folder remoteUrl=\"http://autoplot.org/git/pngwalks.xml\">" +
        "<title>Demos</title>" +
        "<bookmark-list>" +
        "    <bookmark>" +
        "        <title>POLAR/VIS Images</title>" +
        "        <uri>pngwalk:http://vis.physics.uiowa.edu/survey/1996/04-apr/03/images/VIS_$Y_$m_$d_$H_$M_$S_EC.PNG</uri>" +
        "        <description>Images from the POLAR/VIS instrument</description>" +
        "    </bookmark>" +
        "    <bookmark>" +
        "        <title>RBSP Emfisis HFR-WFR Orbits</title>" +
        "        <uri>pngwalk:http://emfisis.physics.uiowa.edu/pngwalk/RBSP-A/HFR-WFR_orbit/product_$(o,id=rbspa-pp).png</uri>" +
        "    </bookmark>" +
        "    <bookmark>" +
        "        <title>RBSP-A MagEIS Combined Spectra</title>" +
        "        <uri>pngwalk:https://www.rbsp-ect.lanl.gov/data_pub/rbspa/ect/level2/combined-elec/rbspa_ect_L2-elec_$Y$m$d_v.1.0.0.png</uri>" +
        "    </bookmark>" +
        "</bookmark-list>" +
    "</bookmark-folder>" +
"</bookmark-list>";


        List<Bookmark> deft=null;
        try {
            deft = Bookmark.parseBookmarks(sdeft);

        } catch (BookmarksException | SAXException | IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }

        Util.loadRecent( "pngwalkRecent", tool.dataSetSelector1, deft );

        Runnable run= new Runnable() {
            public void run() {
                addFileEnabler(tool,parent);
            }
        };
        new Thread(run).start();

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

    private static void addFileEnabler( final PngWalkTool tool, final Window parent ) {
        PngWalkTool.ActionEnabler enabler= new PngWalkTool.ActionEnabler() {
            @Override
            public boolean isActionEnabled(String filename) {
                String template = tool.getTemplate();
                int i0 = -1;
                if ( i0==-1 ) i0= template.indexOf("_$Y");
                if ( i0==-1 ) i0= template.indexOf("_$o");
                if ( i0==-1 ) i0= template.indexOf("_$(o,");
                
                String productFile=null;
                
                if ( i0==-1 ) {
                    try {
                        File file = DataSetURI.getFile( filename, new AlertNullProgressMonitor("get image file") ); // assume it's local.
                        String json= ImageUtil.getJSONMetadata( file );
                        if ( json!=null ) {
                            if ( i0==-1 ) i0= template.indexOf('*'); 
                            if ( i0==-1 ) i0= template.indexOf("$x");
                        }
                        productFile= tool.baseurl + tool.product + ".vap";
                        
                    } catch ( IOException ex ) {
                        logger.log( Level.WARNING, null, ex );
                    }
                }

                if ( i0==-1 ) return false;
                
                if ( productFile==null ) {
                    productFile = template.substring(0, i0) + ".vap";
                }
                
                try {
                    return WalkUtil.fileExists(productFile);
                } catch (FileSystemOfflineException | URISyntaxException ex) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                    return false;
                }
            }
        };

        final String lap= "View in Autoplot";

        tool.addFileAction( enabler, new AbstractAction(lap) {
            @Override
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
                    
                    DatumRange jsonTimeRange=null;
                    
                    try {
                        File file = DataSetURI.getFile( s, new AlertNullProgressMonitor("get image file") ); // assume it's local.
                        String json= ImageUtil.getJSONMetadata( file );
                        if ( json!=null ) { 
                            jsonTimeRange= RichPngUtil.getXRange(json);
                        }
                    } catch ( IOException ex ) {
                        logger.log( Level.WARNING, null, ex );
                    }
                    
                    int i= template.indexOf('$');
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
                        if ( i2==template.length() || !( c=='Y' || c=='y' || c=='o' || c=='x' ) ) {
                            throw new IllegalArgumentException("template must start with $Y, $y or $(o,...)");
                        }   
                    }
                    
                    int i0 = template.indexOf("_$Y");
                    if ( i0==-1 ) i0= template.indexOf("_$y");
                    if ( i0==-1 ) i0= template.indexOf("_$o");
                    if ( i0==-1 ) i0= template.indexOf("_$(o,");
                        
                    if ( i0==-1 && jsonTimeRange!=null ) {
                        if ( i0==-1 ) i0= template.indexOf("_*"); 
                        if ( i0==-1 ) i0= template.indexOf("_$x");
                    }
                        
                    //Note, _$(m,Y=2000) is not supported.

                    //int i1 = template.indexOf(".png");
                    //if ( i1==-1 ) return;
                    //TimeParser tp= TimeParser.create( template.substring(i0 + 1, i1) );
                    //String timeRange = s.substring(i0 + 1, i1);

                    //kludge: LANL showed a bug where "user" was inserted into the URL.  Check for this.

                    if ( s.contains("//user@") && !template.contains("//user@") ) {
                        s= s.replace("//user@", "//" );
                    }

                    String timeRange;
                    if ( jsonTimeRange==null || !UnitsUtil.isTimeLocation(jsonTimeRange.getUnits()) ) { // get the timerange from the filename
                        TimeParser tp= TimeParser.create( template );
                        timeRange = s;
                        try {
                            DatumRange dr= tp.parse(timeRange).getTimeRange();
                            if ( tp.getValidRange().equals(dr) ) {
                                // the filename has no time constraint
                                //TODO: this could probably be resolved by parsing the batch file, if it is available.  The createPngwalk should copy the batch file into the pngwalk anyway.
                                timeRange= null;
                            } else {
                                timeRange= dr.toString().replaceAll(" ", "+");
                            }
                        } catch ( ParseException ex ) {
                            throw new RuntimeException(ex);
                        }
                    } else {
                        timeRange= jsonTimeRange.toString().replaceAll("\\s", "+");
                    }
                    
                    String productFile;
                    
                    if ( tool.product!=null && tool.product.length()>0 && tool.baseurl.length()>1 ) {
                        productFile = tool.baseurl + tool.product + ".vap";  //HERE IT IS
                    } else {
                        productFile = template.substring(0, i0) + ".vap";  
                    }
                    if ( timeRange!=null ) {
                        suri = productFile + "?timeRange=" + timeRange;
                    } else {
                        JOptionPane.showMessageDialog(ScriptContext.getViewWindow(), "unable to resolve time range from image metadata or filename.");
                        return;
                    }
                }

                Runnable run = new Runnable() {
                    @Override
                    public void run() {
                        ScriptContext.createGui();
                        Window apWindow= ScriptContext.getViewWindow();
                        if ( suri!=null ) {
                            raiseApWindowSoon(apWindow);
                            ScriptContext.plot(suri);
                        }
                        if ( parent==null ) {
                            apWindow.setVisible(true);
                        }
                    }
                };
                new Thread(run).start();
            }
        });
        
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
        } catch (IOException | URISyntaxException ex) {
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
     * @param parent dialog parent
     * @param ssrc the file
     */
    protected static void saveLocalCopy( Component parent, String ssrc ) {
        Preferences prefs = AutoplotSettings.settings().getPreferences(PngWalkTool.class);
        String srecent = prefs.get( PngWalkTool.PREF_RECENT, System.getProperty("user.home") );
        if ( ssrc==null ) {
            JOptionPane.showMessageDialog( parent, "No image is selected." );
            return;
        }
        File src;
        try {
            src = FileSystemUtil.doDownload(ssrc, new NullProgressMonitor()); // should be local
        } catch (IOException | URISyntaxException ex) {
            JOptionPane.showMessageDialog( parent, "<html>Unexpected error when downloading file<br>" + ssrc+"<br><br>"+ex.toString() );
            return;
        }
        
        JFileChooser chooser= new JFileChooser( srecent );
        
        JPanel accessoryPanel= new JPanel();
        accessoryPanel.setLayout( new BoxLayout(accessoryPanel,BoxLayout.Y_AXIS) );
        JCheckBox r60= new JCheckBox( "Reduce to 60%" );
        accessoryPanel.add(r60);
        
        chooser.setMultiSelectionEnabled(false);
        chooser.setAccessory(accessoryPanel);
        chooser.setSelectedFile( new File( chooser.getCurrentDirectory(), src.getName() ) );
        int r= chooser.showSaveDialog(parent);
        if ( r==JFileChooser.APPROVE_OPTION ) {
            prefs.put( PngWalkTool.PREF_RECENT, chooser.getSelectedFile().getParent() );
            try {
                if ( r60.isSelected() ) {
                    BufferedImage im= ImageIO.read(src);
                    int size= (int)Math.sqrt( im.getWidth()*im.getWidth() + im.getHeight()*im.getHeight() );
                    im= ImageResize.getScaledInstance( im, size*60/100 );
                    String ext= chooser.getSelectedFile().toString();
                    int i= ext.lastIndexOf(".");
                    ext= ext.substring(i+1);
                    ImageIO.write( im, ext, chooser.getSelectedFile() );
                            
                } else {
                    if ( ! org.autoplot.Util.copyFile( src, chooser.getSelectedFile()) ) {
                        JOptionPane.showMessageDialog( parent, "<html>Unable to save image to: <br>" + chooser.getSelectedFile() );
                    }
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog( parent, "<html>Unable to save image to: <br>" + chooser.getSelectedFile()+"<br><br>"+ex.toString() );
            }
        }
    }
    private Window parentWindow;
    private JCheckBoxMenuItem qcFilterMenuItem;

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
            @Override
            public void actionPerformed( ActionEvent e ) {
                LoggerManager.logGuiEvent(e);        
                saveLocalCopy(tool,tool.getSelectedFile());
            }
        } );
        fileMenu.add( new AbstractAction( "Show Autoplot" ) {
            @Override
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
            @Override
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
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);        
                if ( AppManager.getInstance().requestQuit() ) {
                    frame.dispose();
                    AppManager.getInstance().quit();
                }
            }
        } );

        result.add(fileMenu);

        JMenu navMenu= new JMenu("Navigate");
        navMenu.add( new AbstractAction( "Go To Date..." ) {
            @Override
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
                    } catch (RuntimeException ex ) {
                        tool.setStatus( "warning: "+ex.toString() );
                    }
                }

            }
        } );
        
        navMenu.add( new AbstractAction( "First" ) {
            @Override
            public void actionPerformed( ActionEvent e ) {
               LoggerManager.logGuiEvent(e);        
               tool.seq.setIndex( 0 );
            }
        } ).setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_HOME, 0 ));
        

        navMenu.add( new AbstractAction( "Previous Page" ) {
            @Override
            public void actionPerformed( ActionEvent e ) {
               LoggerManager.logGuiEvent(e);        
               tool.seq.setIndex( tool.prevPage(tool.seq.getIndex()) );
            }
        } ).setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_PAGE_UP, 0 ));
        

        navMenu.add( new AbstractAction( "Previous Interval" ) {
            @Override
            public void actionPerformed( ActionEvent e ) {
               LoggerManager.logGuiEvent(e);        
               tool.seq.setIndex( tool.prevInterval(tool.seq.getIndex()) );
            }
        } ).setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_UP, 0 ));

        navMenu.add( new AbstractAction( "Previous Item" ) {
            @Override
            public void actionPerformed( ActionEvent e ) {
               LoggerManager.logGuiEvent(e);        
               tool.seq.skipBy( -1 );
            }
        } ).setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_LEFT, 0 ));


        navMenu.add( new AbstractAction( "Next Item" ) {
            @Override
            public void actionPerformed( ActionEvent e ) {
               LoggerManager.logGuiEvent(e);        
               tool.seq.skipBy( 1 );
            }
        } ).setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_RIGHT, 0 ));

        navMenu.add( new AbstractAction( "Next Interval" ) {
            @Override
            public void actionPerformed( ActionEvent e ) {
               LoggerManager.logGuiEvent(e);        
               tool.seq.setIndex(tool.nextInterval(tool.seq.getIndex()) );
            }
        } ).setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_DOWN, 0 ));


        navMenu.add( new AbstractAction( "Next Page" ) {
            @Override
            public void actionPerformed( ActionEvent e ) {
               LoggerManager.logGuiEvent(e);        
               tool.seq.setIndex( tool.nextPage(tool.seq.getIndex()) );
            }
        } ).setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_PAGE_DOWN, 0 ));

        navMenu.add( new AbstractAction( "Last" ) {
            @Override
            public void actionPerformed( ActionEvent e ) {
               LoggerManager.logGuiEvent(e);        
               tool.seq.setIndex( tool.seq.size()-1 );
            }
        } ).setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_END, 0 ));
        
        result.add( navMenu );
        tool.navMenu= navMenu;
        
        navMenu.setEnabled(tool.seq!=null);
        
        final JMenu optionsMenu= new JMenu( "Options" );
        final JCheckBoxMenuItem persMi= new JCheckBoxMenuItem("Use Perspective",true);
        persMi.addActionListener( new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ((CoversWalkView)tool.views[4]).setPerspective(persMi.isSelected());
            }
        } );

        optionsMenu.add(persMi);

        ButtonGroup buttonGroup1 = new javax.swing.ButtonGroup();

        final JMenu thumbsizeMenu= new JMenu("Thumbnail Size" );
        final int[] sizes= new int[] { 50, 100, 200, 400 };
        for ( int i=0; i<sizes.length; i++ ) {
            final int fsize= sizes[i];
            final JCheckBoxMenuItem mi;
            mi= new JCheckBoxMenuItem(
               new AbstractAction(""+fsize+" px" ) {
                  @Override
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

        final JCheckBoxMenuItem qcmi= new JCheckBoxMenuItem("Show Only Quality Control Records",false);
        tool.qcFilterMenuItem= qcmi;
        
        final JMenuItem qc= new JMenuItem( new AbstractAction( "Start Quality Control Tool (QC)" ) {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);        
                if ( !PngWalkTool.isQualityControlEnabled() ) {
                    tool.startQC();
                }
            }
        });
        qc.setToolTipText("Start up the Quality Control tool that adds documentation to images.");
        optionsMenu.add( qc );
        
        qcmi.addActionListener( new AbstractAction(  ) {
            @Override
            public void actionPerformed(ActionEvent e) {
                if ( qcmi.isSelected() ) {
                    tool.seq.setQCFilter("op");
                } else {
                    tool.seq.setQCFilter("");
                }
            }
        } );
        qcmi.setToolTipText("show only QC records with Okay or Bad setting.");
        qcmi.setEnabled(false);
        
        optionsMenu.add(qcmi);
        
        final JMenuItem dg= new JMenuItem( new AbstractAction( "Start Digitizer" ) {
            @Override
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

        final JMenu toolsMenu= new JMenu("Tools");
        final JMenuItem writePdf= new JMenuItem( new AbstractAction( "Write to PDF..." ) {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);        
                tool.writePdf();
            }
        });
        writePdf.setToolTipText("Write the visible images to a PDF file with last QC annotation.");
        toolsMenu.add( writePdf );
        result.add( toolsMenu );

        final JMenuItem writeGif= new JMenuItem( new AbstractAction( "Write to Animated GIF..." ) {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);        
                tool.writeAnimatedGif();
            }
        });
        writeGif.setToolTipText("Write the visible images to an animated GIF file.");
        toolsMenu.add( writeGif );

        final JMenuItem writeHtml= new JMenuItem( new AbstractAction( "Write to HTML..." ) {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);        
                tool.writeHtml();
            }
        });
        writeHtml.setToolTipText("Write the visible images to an HTML file.");
        toolsMenu.add( writeHtml );
        
        result.add( toolsMenu );
        
        final JMenu bookmarksMenu= new JMenu("Bookmarks");
        final BookmarksManager man= new BookmarksManager(frame,true,"PNG Bookmarks");

        man.getModel().addPropertyChangeListener( BookmarksManagerModel.PROP_LIST, new PropertyChangeListener() {
            @Override
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
            @Override
            public void actionPerformed( ActionEvent ev ) {
                String template= dataSetSelector1.getValue();
                if ( template.endsWith(".pngwalk") ) {
                    Map<String,String> m= readPngwalkFile(template);
                    template= m.get("template");
                    product= m.get("product");
                    baseurl= m.get("baseurl");
                    baseurl= checkRelativeBaseurl(baseurl, template, product );
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
        
        ((SinglePngWalkView)views[2]).clickDigitizer.setViewer(this);
        
        final int SCROLLBAR_HEIGHT = (int) Math.round( new JScrollPane().getHorizontalScrollBar().getPreferredSize().getHeight() );

        final JSplitPane filmStripSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, views[1], views[2] );

        //p.setEnabled(false);  //prevents user manipulation
        filmStripSplitPane.setDividerLocation(getThumbnailSize()+ (int)(1.2 *SCROLLBAR_HEIGHT));
        views[1].addPropertyChangeListener( PngWalkView.PROP_THUMBNAILSIZE, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                filmStripSplitPane.setDividerLocation( (Integer)evt.getNewValue() + SCROLLBAR_HEIGHT );
            }
        });

        filmStripSplitPane.setMinimumSize( new Dimension(640,480) );
        filmStripSplitPane.setPreferredSize( new Dimension(640,480) );
        
        final JSplitPane coversSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, views[4], views[5] );
        coversSplitPane.setDividerLocation(getThumbnailSize()+ (int)(1.2 *SCROLLBAR_HEIGHT));
        views[4].addPropertyChangeListener( PngWalkView.PROP_THUMBNAILSIZE, new PropertyChangeListener() {
            @Override
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
        for (PngWalkView view : views) {
            view.getMouseTarget().addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if ( e.getClickCount()==2 && digitizer==null ) {
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
        for (PngWalkView view : views) {
            Binding b = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, view, BeanProperty.create("thumbnailSize"), this, BeanProperty.create("thumbnailSize"));
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
        @Override
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
            firePropertyChange( PROP_TIMERANGE, null, getTimeRange() );
            if (qcPanel != null && seq.getQualityControlSequence()!=null ) {
                qcPanel.displayRecord( seq.getQualityControlSequence().getQualityControlRecord( seq.getIndex() ));
            }
        }
    };

    /**
     * listen for status updates from other agents, relay the status for the view.
     */
    private transient PropertyChangeListener statusListener= new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
           setStatus((String)evt.getNewValue());
        }
    };

    /**
     *
     */
    private final transient PropertyChangeListener qcStatusListener = new PropertyChangeListener() {
        @Override
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
        
        URISplit split= URISplit.parse(template);
        Map<String,String> params= URISplit.parseParams(split.params);
        
        dataSetSelector1.setValue(template);
        
        WalkImageSequence oldseq= this.seq;

        URI uri= DataSetURI.getResourceURI(template);
        if ( uri==null ) {
            throw new IllegalArgumentException("Unable to parse: "+template);
        }
        String surl= DataSetURI.fromUri( uri );

        try {
            seq= new WalkImageSequence( surl );
            String tr= params.get("timerange");
            
            if ( tr!=null ) {
                try {
                    DatumRange trdr;
                    trdr= DatumRangeUtil.parseTimeRange(tr);
                    seq.setTimerange( trdr );
                } catch ( ParseException ex ) {
                    setMessage( ERROR_ICON, "unable to parse timerange" );
                }
            }
            
                
            setNavButtonsEnabled(true);
            if ( navMenu!=null ) navMenu.setEnabled(true);
            seq.setQCFilter("");
            if ( qcFilterMenuItem!=null ) {
                qcFilterMenuItem.setSelected(false);
            }
            if ( qcPanel!=null ) {
                qcPanel.setWalkImageSequece(seq);
            }
            
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
            @Override
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
                    @Override
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
        List<String> urls = new ArrayList<>();
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
            if ( p==null ) p= parentWindow;
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
     * This should not be confused with the &timerange= part of the URI.
     */
    transient DatumRange timeRange;
    public static final String PROP_TIMERANGE = "timeRange";

    /**
     * rfe https://sourceforge.net/p/autoplot/feature-requests/271/
     * @return the current timerange
     */
    public DatumRange getTimeRange() {
        DatumRange tr=null;
        tr= seq.imageAt( seq.getIndex() ).getDatumRange();
        if ( tr!=null ) {
            return tr;
        } else {
            return timeRange;
        }
    }

    /**
     * timerange roughly the focus timerange.  This property is introduced 
     * to allow for binding between pngwalks.
     * This should not be confused with the &timerange= part of the URI.
     * @param timeRange
     */
    public void setTimeRange(DatumRange timeRange) {
        boolean setting0= setting;
        setting= true;
        DatumRange old= this.timeRange;
        this.timeRange = timeRange;
        if ( seq!=null ) {
            if ( timeRange!=null ) seq.gotoSubrange(timeRange);
        }
        if ( setting0 ) firePropertyChange(PROP_TIMERANGE, old, timeRange );
        setting= false;
    }
    
    private transient QDataSet mousePressLocation = null;

    public static final String PROP_MOUSEPRESSLOCATION = "mousePressLocation";

    public QDataSet getMousePressLocation() {
        return mousePressLocation;
    }

    public void setMousePressLocation(QDataSet mousePressLocation) {
        QDataSet oldMousePressLocation = this.mousePressLocation;
        this.mousePressLocation = mousePressLocation;
        firePropertyChange(PROP_MOUSEPRESSLOCATION, oldMousePressLocation, mousePressLocation);
    }

    private transient QDataSet mouseReleaseLocation = null;

    public static final String PROP_MOUSERELEASELOCATION = "mouseReleaseLocation";

    public QDataSet getMouseReleaseLocation() {
        return mouseReleaseLocation;
    }

    public void setMouseReleaseLocation(QDataSet mouseReleaseLocation) {
        QDataSet oldMouseReleaseLocation = this.mouseReleaseLocation;
        this.mouseReleaseLocation = mouseReleaseLocation;
        firePropertyChange(PROP_MOUSERELEASELOCATION, oldMouseReleaseLocation, mouseReleaseLocation);
    }


    transient PropertyChangeListener seqTimeRangeListener= new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            setTimeRange((DatumRange)evt.getNewValue());
        }
    };

    boolean setting= false;

    transient PropertyChangeListener seqIndexListener= new PropertyChangeListener() {
        @Override
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

    /**
     * start the quality control if it is not started already.
     */
    public void startQC() {
        if ( !isQualityControlEnabled() ) {
            qcPanel= new QualityControlPanel();
            tabs.add( "Quality Control", qcPanel );
            if ( seq!=null ) {
                qcPanel.setWalkImageSequece(seq);
                seq.addPropertyChangeListener(WalkImageSequence.PROP_BADGE_CHANGE, qcStatusListener);
            }
            ENABLE_QUALITY_CONTROL= true;
        }
        qcFilterMenuItem.setEnabled(true);
    }

    protected DataPointRecorder digitizer= null;
    protected boolean digitizerRecording= true;
    protected char annoTypeChar= '|';
          
    /**
     * start the digitizer if it is not started already.
     */
    public void startDigitizer() {
        if ( digitizer==null ) {
            digitizer= new DataPointRecorder();
            digitizer.addDataSetUpdateListener(new DataSetUpdateListener() {
                @Override
                public void dataSetUpdated(DataSetUpdateEvent e) {
                    for (PngWalkView v : views) {
                       if ( v instanceof SinglePngWalkView ) {
                           v.repaint();
                        }
                    }
                }
            });
            digitizer.addDataPointSelectionListener(new DataPointSelectionListener() {
                @Override
                public void dataPointSelected(DataPointSelectionEvent e) {
                    String image= (e.getPlane("image").toString());
                    int i= seq.findIndex(image);
                    if ( i>-1 ) {
                        seq.setIndex(i);
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
            
            annoType.addItemListener(new ItemListener() {
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
            digitizerRecording= true;
        }
    }

    private boolean isDigitizerEnabled() {
        return digitizer!=null;
    }
    
    /**
     * provide access to the digitizer DataPointRecorder, so that points 
     * can be deleted programmatically.  
     * @return the DataPointRecorder.
     */
    public DataPointRecorder getDigitizerDataPointRecorder() {
        return digitizer;
    }

    
    /**
     * this can be used to disable recording of the points.  
     * @param enable true means record points, false means don't record.
     */
    public void setDigitizerRecording( boolean enable ) {
        this.digitizerRecording= enable;
    }
    
    public static interface ActionEnabler {
        boolean isActionEnabled( String filename );
    }

    /**
     * Enabler that returns true for local files.
     */
    public static final ActionEnabler LOCAL_FILE_ENABLER = new ActionEnabler() {
        @Override
        public boolean isActionEnabled( String filename ) {
            return DataSetURI.getResourceURI(filename).toString().startsWith("file:" );
        }
    };

    transient List<ActionEnabler> actionEnablers= new ArrayList<>();
    List<JButton> actionButtons= new ArrayList<>();

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
     * add a component that will get property change events and should respond
     * to property changes.  This allows scientists a way to connect actions to
     * the PNGWalk tool.
     * @param c null or a smallish JComponent that should be about the size of a button.
     * @param p null or the listener for the selected file and timerange.
     */
    public void addActionComponent( JComponent c, PropertyChangeListener p ) {
        if ( c!=null ) actionButtonsPanel.add(c);
        if ( p!=null ) {
            this.addPropertyChangeListener( PROP_SELECTED_NAME, p );
            this.addPropertyChangeListener( PROP_TIMERANGE, p );
            this.addPropertyChangeListener( PROP_MOUSEPRESSLOCATION, p );
            this.addPropertyChangeListener( PROP_MOUSERELEASELOCATION, p );
        }
        this.revalidate();
    }
    
    public void removeActionComponent( JComponent c, PropertyChangeListener p ) {
        if ( c!=null ) actionButtonsPanel.remove(c);
        if ( p!=null ) {
            this.removePropertyChangeListener( PROP_SELECTED_NAME, p );
            this.removePropertyChangeListener( PROP_TIMERANGE, p );            
            this.removePropertyChangeListener( PROP_MOUSEPRESSLOCATION, p );
            this.removePropertyChangeListener( PROP_MOUSERELEASELOCATION, p );
        }
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

    /**
     * return the currently selected image.
     * @return the currently selected image
     */
    public BufferedImage getSelectedImage() {
        return seq.currentImage().getImage();
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
    
    /**
     * provide a method for setting the QCStatus externally.
     * @param text message annotating the status change or commenting on status.
     * @param status the status
     */
    public void setQCStatus( String text, QualityControlRecord.Status status ) {
        if ( this.qcPanel==null ) {
            throw new IllegalArgumentException("QC Panel must be started");
        }
        this.qcPanel.setStatus(text, status);
        this.repaint();
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
        dataSetSelector1 = new org.autoplot.datasource.DataSetSelector();
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
            .add(org.jdesktop.layout.GroupLayout.TRAILING, pngsPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
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
                            .add(dataSetSelector1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .add(statusLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
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
            Map<String,String> m= readPngwalkFile(t);
            t= m.get("template");
            baseurl= checkRelativeBaseurl(baseurl, t, m.get("product") );
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
        timeFilterTextField.setText( range.toString() );
    }//GEN-LAST:event_useRangeCheckBoxItemStateChanged

    private void showMissingCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showMissingCheckBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_showMissingCheckBoxActionPerformed

    /**
     * we need to make this public.
     * @param propertyName
     * @param oldValue
     * @param newValue 
     */
    @Override
    public void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        super.firePropertyChange(propertyName, oldValue, newValue); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * provide means for scripts to add component to develop new applications.
     * @return the TearoffTabbedPane used.
     */
    public TearoffTabbedPane getTabs() {
        return tabs;
    }

    /**
     * return the container for the sequence of images, which contains the
     * current index and provides a method for jumping to other images.
     * @return the sequence.
     */
    public WalkImageSequence getSequence() {
        return this.seq;
    }
    
    
    private void writeToHtmlImmediately( ProgressMonitor monitor, File f, String summary ) throws FileNotFoundException {
            
        monitor.setTaskSize( this.seq.size() );
        monitor.started();
        
        URI base;
        if ( this.seq.getQCFolder()!=null ) {
            base= this.seq.getQCFolder();
        } else {
            int splitIndex=-1;
            if ( splitIndex==-1 ) splitIndex= WalkUtil.splitIndex( seq.getTemplate() );
            try {
                base= new URI( this.seq.getTemplate().substring(0,splitIndex) );
            } catch (URISyntaxException ex) {
                throw new RuntimeException(ex);
            }
        }
        
        try {
            for ( int i= 0; i<this.seq.size(); i++ ) {
                monitor.setTaskProgress(i);
                if ( monitor.isCancelled() ) break;

                BufferedImage im= this.seq.imageAt(i).getImage();
                while ( im==null ) {
                    try {
                        Thread.sleep(100);
                    } catch ( InterruptedException ex ) {
                        throw new RuntimeException(ex);
                    }
                    im = this.seq.imageAt(i).getImage();
                }
                try {
                    String n;
                    n= base.relativize( this.seq.imageAt(i).getUri() ).getPath();
                    ImageIO.write( im, "png", new File( f, n ) );
                    File qcFile= new File( this.seq.imageAt(i).getUri().getPath() + ".ok" );
                    if ( qcFile.exists() ) {
                        FileUtil.fileCopy( qcFile, new File( f, n+".ok" ) );
                    }
                    qcFile= new File( this.seq.imageAt(i).getUri().getPath() + ".problem" );
                    if ( qcFile.exists() ) {
                        FileUtil.fileCopy( qcFile, new File( f, n+".problem" ) );
                    }
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
        } finally {
            monitor.finished();
        }
        
        URL url= PngWalkTool.class.getResource("makeTutorialHtml.jy");
        final ProgressMonitor mon= DasProgressPanel.createFramed(SwingUtilities.getWindowAncestor(this),"write HTML");
        Map<String,String> params= new HashMap<>();
        params.put("dir",base.toString());
        params.put("qconly","true");
        params.put("outdir",f.toString());
        params.put("name",""); //TODO: what should this be?
        params.put("summary",summary);
        try {
            JythonUtil.invokeScriptSoon(url,null,params,false,false,mon);
        } catch (IOException ex) {
            Logger.getLogger(PngWalkTool.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
    
    /**
     * write the sequence to a PDF file, so that this can be used to produce
     * worksheets.
     * 
     */
    public void writeHtml() {
        JFileChooser choose= new JFileChooser();
        
        Preferences prefs= Preferences.userNodeForPackage(PngWalkTool.class);
        String fname= prefs.get( "writeToHtml", "/tmp/pngwalk/" );
        
        choose.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        choose.setSelectedFile( new File(fname) );
        
        final HtmlOutputOptions hoo= new HtmlOutputOptions();

        choose.setAccessory(hoo);
        
        if ( choose.showSaveDialog(PngWalkTool.this)==JFileChooser.APPROVE_OPTION ) {
            final File f= choose.getSelectedFile();
            prefs.put( "writeToHtml", f.toString() );
            final ProgressMonitor mon= DasProgressPanel.createFramed(SwingUtilities.getWindowAncestor(this),"write html");
            Runnable run= new Runnable() {
                public void run() {
                    try {
                        writeToHtmlImmediately( mon , f, hoo.getTitle() );
                    } catch (FileNotFoundException ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }                    
                }
            };
            new Thread(run).start();
        }
    }
    
    private void writeToPdfImmediately( ProgressMonitor monitor, File f ) throws FileNotFoundException {
        try {
            monitor.setTaskSize(this.seq.size());
            monitor.started();
            
            int imageNumber= 1;
            FileOutputStream out= new FileOutputStream( f );
                        
            Rectangle rect = new Rectangle( (int)(8.5*72), (int)11*72 );
            Document doc = new Document(rect, 0, 0, 0, 0 );
            doc.addCreator("autoplotPngwalkTool");
            doc.addCreationDate();
            
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();
                      
            QualityControlSequence qcseq= this.seq.getQualityControlSequence();
            
            for ( int i= 0; i<this.seq.size(); i++ ) {
                monitor.setTaskProgress(i);
                PdfContentByte cb = writer.getDirectContent();

                cb.saveState();

                BufferedImage im= this.seq.imageAt(i).getImage();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try {
                    while ( im==null ) {
                        try {
                            Thread.sleep(100);
                        } catch ( InterruptedException ex ) {
                            throw new RuntimeException(ex);
                        }
                        im= this.seq.imageAt(i).getImage();
                    }
                    ImageIO.write(im, "png", baos);
                    Image pdfImage= com.itextpdf.text.Image.getInstance(baos.toByteArray() );
                    int w= (int)(7.5*72);
                    int h= w * im.getHeight() / im.getWidth();
                    pdfImage.setAbsolutePosition(36,11*72-36-h);
                    pdfImage.scaleToFit(w,h);
                    
                    cb.addImage( pdfImage );
                    doc.add( pdfImage.rectangle(36,11*72-36-h) );
                    String caption;
                    if ( qcseq!=null ) {
                        QualityControlRecord r= qcseq.getQualityControlRecord(i);
                        if ( r!=null ) {
                            caption= String.format("%d. %s", imageNumber, r.getLastComment());
                        } else {
                            caption= String.format("%d.", imageNumber ); 
                        }
                    } else {
                        caption= String.format("%d.", imageNumber ); 
                    }
                    Paragraph p= new Paragraph();
                    p.add(caption);
                    doc.add(p);
                    
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
                
                cb.restoreState();
                
                doc.newPage();
                imageNumber++;
            }
                        
            doc.close();
            
        } catch (DocumentException ex) {
            logger.log(Level.SEVERE, null, ex);
        } finally {
            monitor.finished();
            
        }
    }
    
    /**
     * write the sequence to a PDF file, so that this can be used to produce
     * worksheets.
     * 
     */
    public void writePdf() {
        JFileChooser choose= new JFileChooser();

        Preferences prefs= Preferences.userNodeForPackage(PngWalkTool.class);
        String fname= prefs.get( "writeToPdf", "/tmp/pngwalk.pdf" );
        
        choose.setSelectedFile( new File(fname) );
        if ( choose.showSaveDialog(PngWalkTool.this)==JFileChooser.APPROVE_OPTION ) {
            final File f= choose.getSelectedFile();
            prefs.put( "writeToPdf", f.toString() );
            final ProgressMonitor mon= DasProgressPanel.createFramed(SwingUtilities.getWindowAncestor(this),"write pdf");
            Runnable run= new Runnable() {
                public void run() {
                    try {
                        writeToPdfImmediately( mon , f );
                        final JPanel panel= new javax.swing.JPanel();
                        panel.setLayout( new BoxLayout(panel,BoxLayout.Y_AXIS ));
                        panel.add( new javax.swing.JLabel("wrote file "+f) );
                        JButton b= new JButton("Open in Browser");
                        b.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                AutoplotUtil.openBrowser(f.toURI().toString());
                            }
                        });  
                        panel.add( b );
                        JOptionPane.showMessageDialog( PngWalkTool.this,panel );
                    } catch (FileNotFoundException ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }                    
                }
            };
            new Thread(run).start();

        }
    }
    
    /**
     * 
     * @param monitor
     * @param f
     * @param overrideDelays if null, then just use 100ms between frames, otherwise use this delay. "realTime", "10ms", "secondPerDay"
     * @throws FileNotFoundException 
     */
    private void writeToAnimatedGifImmediately( final ProgressMonitor monitor, File f, final String overrideDelays ) throws FileNotFoundException {
        try {
            monitor.setTaskSize(this.seq.size());
            monitor.started();
            
            final DatumRange baseRange= this.seq.imageAt(0).getDatumRange();
            final Datum baset;
            if ( baseRange!=null ) {
                baset= this.seq.imageAt(0).getDatumRange().min();
            } else {
                if ( overrideDelays!=null && !overrideDelays.endsWith("ms")) {
                    throw new IllegalArgumentException("template does not imply timeranges");
                }
                baset= null;
            }
            
            Iterator<BufferedImage> images= new Iterator() {
                int i=0;
                @Override
                public boolean hasNext() {
                    return i<PngWalkTool.this.seq.size();
                }

                @Override
                public Object next() {
                    BufferedImage im= seq.imageAt(i).getImage();
                    monitor.setTaskProgress(i);
                    while ( im==null ) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ex) {
                            throw new RuntimeException(ex);
                        }
                        im= seq.imageAt(i).getImage();
                    }
                    i=i+1;
                    return im;
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException("remove is not supported");
                }
                
            };
            
            Iterator<String> delays= new Iterator() {
                int i=0;
                Datum lastTime;
                
                @Override
                public boolean hasNext() {
                    return true;
                }

                @Override
                public String next() {
                    if ( i==0 ) {
                        lastTime= baset;
                    }
                    i=i+1;
                    if ( i==seq.size() ) i--;
                    String result;
                    if ( overrideDelays!=null ) {
                        switch (overrideDelays) {
                            case "realTime":
                                result= String.valueOf((int)( seq.imageAt(i).getDatumRange().min().subtract(lastTime).convertTo(Units.milliseconds).value()) );
                                lastTime= seq.imageAt(i).getDatumRange().min();
                                break;
                            case "secondPerDay":
                                result= String.valueOf((int) (seq.imageAt(i).getDatumRange().min().subtract(lastTime).convertTo(Units.milliseconds).value()/86400000) );
                                lastTime= seq.imageAt(i).getDatumRange().min();
                                break;
                            default:
                                try {
                                    result= String.valueOf((int) Units.milliseconds.parse(overrideDelays).value() );
                                } catch (ParseException ex) {
                                    throw new IllegalArgumentException( ex );
                                }   
                                break;
                        }
                    } else {
                        result= "100";
                    }
                    return result;
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException("remove is not supported");
                }
                
                
            };
            
            AnimatedGifDemo.saveAnimate( f, images, delays );
            
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        } finally {
            monitor.finished();
        }
    }
        
    /** 
     * Write the displayed images to an animated gif.
     */
    public void writeAnimatedGif() {
        JFileChooser choose= new JFileChooser();
        
        Preferences prefs= Preferences.userNodeForPackage(PngWalkTool.class);
        String fname= prefs.get( "writeToGif", "/tmp/pngwalk.gif" );
        
        choose.setSelectedFile( new File(fname) );
        final String[] opts= new String[] { "10ms", "200ms", "400ms", "800ms", "realTime", "secondPerDay" };
        JPanel p= new JPanel();
        p.setLayout( new BoxLayout(p,BoxLayout.Y_AXIS) );
        
        JComboBox jo= new JComboBox(opts);
        jo.setSelectedIndex(1);
        jo.setMaximumSize( new Dimension( 1000, 30 ) );
        p.add(new JLabel("Interslide-Delay:"));
        p.add(jo);
        p.add(Box.createGlue());
        
        choose.setAccessory(p);
        if ( choose.showSaveDialog(PngWalkTool.this)==JFileChooser.APPROVE_OPTION ) {
            final File f= choose.getSelectedFile();
            prefs.put( "writeToGif", f.toString() );
            final ProgressMonitor mon= DasProgressPanel.createFramed(SwingUtilities.getWindowAncestor(this),"write animated gif");
            final String fdelay= (String)jo.getSelectedItem();
            Runnable run= new Runnable() {
                public void run() {
                    try {
                        writeToAnimatedGifImmediately( mon , f, fdelay );
                        JPanel panel= new javax.swing.JPanel();
                        panel.setLayout( new BoxLayout(panel,BoxLayout.Y_AXIS ));
                        panel.add( new javax.swing.JLabel("wrote file "+f) );
                        JButton b= new JButton("Open in Browser");
                        b.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                AutoplotUtil.openBrowser(f.toURI().toString());
                            }
                        });   
                        panel.add( b );
                        JOptionPane.showMessageDialog( PngWalkTool.this,panel );
                    } catch (FileNotFoundException ex) {
                        Logger.getLogger(PngWalkTool.class.getName()).log(Level.SEVERE, null, ex);
                    }                    
                }
            };
            new Thread(run).start();

        }
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel actionButtonsPanel;
    private org.autoplot.datasource.DataSetSelector dataSetSelector1;
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
