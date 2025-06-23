
/*
 * PngWalkTool.java
 *
 * Created on Apr 29, 2009, 3:17:56 AM
 */

package org.autoplot.pngwalk;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPHeaderCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import external.AnimatedGifDemo;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
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
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
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
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.das2.components.DasProgressPanel;
import org.das2.components.DataPointRecorder;
import org.das2.components.TearoffTabbedPane;
import org.das2.dataset.DataSetUpdateEvent;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.TimeParser;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.datum.format.TimeDatumFormatter;
import org.das2.event.DataPointSelectionEvent;
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
import org.autoplot.ScriptContext2023;
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
import org.autoplot.datasource.TimeRangeTool;
import org.autoplot.datasource.URISplit;
import org.autoplot.dom.Application;
import org.autoplot.dom.Plot;
import org.das2.graph.Painter;
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

    private static final String DEFAULT_BOOKMARKS = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><bookmark-list version=\"1.1\">    " +
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
            "        <uri>pngwalk:https://emfisis.physics.uiowa.edu/pngwalk/RBSP-A/HFR-WFR_orbit/product_$(o,id=rbspa-pp).png</uri>" +
            "    </bookmark>" +
            "    <bookmark>" +
            "        <title>RBSP-A MagEIS Combined Spectra</title>" +
            "        <uri>pngwalk:https://www.rbsp-ect.lanl.gov/data_pub/rbspa/ect/level2/combined-elec/rbspa_ect_L2-elec_$Y$m$d_v.1.0.0.png</uri>" +
            "    </bookmark>" +
            "</bookmark-list>" +
            "</bookmark-folder>" +
            "</bookmark-list>";

    
    public PngWalkView[] views;
    TearoffTabbedPane tabs;
    
    WalkImageSequence seq;
    
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
    private String version; // the version, if used.
    private String qcturl;  // the url for quality control data.
    private String pwd=null; // the location of the .pngwalk file, if used, or null.
    private String vapfile=null;
    
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

        if ( !alm.process(args) ) {
            System.exit( alm.getExitCode() );
        }

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
     * <li>baseurl - the location of the png images
     * <li>product - the base used to create file names &lt;product&gt;_$Y$m$d.png
     * <li>template - the template for files, like product_$Y$m$d.png
     * <li>pwd - the location of the .pngwalk file.
     * <li>qcturl - optional location of the quality control files, "" if not specified.
     * </ul>
     * @param template
     * @return the map
     */
    private static Map readPngwalkFile( String template ) {
        URISplit split= URISplit.parse(template);
        InputStream in=null;
        String product= "";
        String baseurl= "";
        String pwd="";
        String qcturl= "";
        String vapfile= "";
        String version= "";
        
        try {
            Properties p= new Properties();
            if ( split.file==null ) {
                throw new IllegalArgumentException("template does not appear to be files: "+template);
            }
            final File local = DataSetURI.getFile( split.resourceUri, new NullProgressMonitor() ); 

            in= new FileInputStream( local );
            p.load( in );
            String vers= p.getProperty("version");
            if ( vers==null || vers.trim().length()==0 ) vers=""; else vers="_"+vers;
            
            pwd= split.path; // pwd is the location of the pngwalk file, which could be different than the template.
            
            product= p.getProperty("product");

            pwd= p.getProperty("pwd",pwd);  // so that the .pngwalk file can be used out-of-context.
            
            baseurl= p.getProperty("baseurl","."); // baseurl is needed so that pngwalks can be used out-of-context, for example when a browser downloads the file and hands it off to Autoplot.
            if ( !baseurl.startsWith(".") ) {
                if ( !baseurl.endsWith("/") ) {
                    baseurl= baseurl + "/";
                }
                split.path= baseurl;
            } else {
                split.path= checkRelativeBaseurl( baseurl, pwd, product );
            }
            
            qcturl= p.getProperty("qcturl",""); // allow the qc data to come from a different place
            
            String t;
            if ( !p.getProperty("filePattern","").equals("") ) {
                // names were specified in the batch file.
                t= split.path + p.getProperty("filePattern","");
            } else {
                t= split.path + p.getProperty("product") + "_" + p.getProperty("timeFormat") +vers + ".png";
            }
            template= t;
            vapfile= p.getProperty("vapfile","");
            version= vers;
            
        } catch (FileSystemOfflineException ex) {
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
        result.put( "qcturl", qcturl );
        result.put( "pwd", pwd );
        result.put( "vapfile", vapfile );
        result.put( "version", version );
        return result;

    }

    private static void raiseApWindowSoon( final Window apWindow ) {
        Runnable run= () -> {
            GuiSupport.raiseApplicationWindow((JFrame)apWindow);
            apWindow.toFront();
            apWindow.repaint();
        };
        SwingUtilities.invokeLater(run);
    }
    
    /**
     * 
     * @param baseurl  -- possibly relative location, only "./" and ../dir/" supported.
     * @param template
     * @param product
     * @return 
     */
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
        } else if ( baseurl.startsWith("../") ) { // ../fgmjuno/
            URISplit split= URISplit.parse(template);
            String f= split.path;
            int i= f.lastIndexOf("/",f.length()-2);
            f= f.substring(0,i) + baseurl.substring(2);
            i= f.indexOf( "/"+product+".pngwalk" );
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
    
    private void loadPngwalkFile( String file ) {
        Map<String,String> map= readPngwalkFile(file);
        product= map.get("product");
        baseurl= map.get("baseurl");
        qcturl= map.get("qcturl");
        pwd= map.get("pwd");
        vapfile= map.get("vapfile");
        version= map.get("version");
        baseurl= checkRelativeBaseurl( baseurl, file, product );
        boolean doStartQC=false;
        if ( !"".equals(map.get("qcturl")) ) {
            qcturl= checkRelativeBaseurl( qcturl, pwd, product );
            doStartQC= true;
        } else {
            qcturl= pwd;
        }
        vapfile= checkRelativeBaseurl( vapfile, pwd, product );
        String template= map.get("template");  
        this.setTemplate(template); 
        if ( doStartQC ) {
            this.startQC();
        }
        boolean addToRecent=true;
        if ( addToRecent ) {
            SwingUtilities.invokeLater( new Runnable() {
                @Override
                public void run() {
                    dataSetSelector1.addToRecent(file);
                }
            });
        }
        
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

        String sdeft= DEFAULT_BOOKMARKS;

        List<Bookmark> deft=null;
        try {
            deft = Bookmark.parseBookmarks(sdeft);

        } catch (BookmarksException | SAXException | IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }

        Util.loadRecent( "pngwalkRecent", tool.dataSetSelector1, deft );
        
        if ( template!=null ) {
            URISplit split= URISplit.parse(template);
            if ( split.file.endsWith(".pngwalk") ) {
                tool.loadPngwalkFile( template );
            } else {
                tool.product= "";
                tool.baseurl= "";
                tool.pwd= split.path;
                tool.setTemplate(template);
            }
            
        } else {
            tool.product= "";
            tool.baseurl= "";
        }

        Runnable run= () -> {
            addFileEnabler(tool,parent);
        };
        new Thread(run).start();

        JFrame frame = new JFrame("PNG Walk Tool");
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
        PngWalkTool.ActionEnabler enabler= (String filename) -> {
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
            
            try {
                File file = DataSetURI.getFile( filename, new AlertNullProgressMonitor("get image file") ); // assume it's local.
                String scriptURI= ImageUtil.getScriptURI(file);
                if ( scriptURI!=null ) {
                    return true;
                }
            } catch (FileSystemOfflineException ex) {
                Logger.getLogger(PngWalkTool.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(PngWalkTool.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            if ( productFile==null && i0>-1 ) {
                productFile = template.substring(0, i0) + ".vap";
            }
            
            try {
                if ( productFile!=null && !WalkUtil.fileExists(productFile) ) {
                    if ( template.startsWith(tool.baseurl) ) {
                        String vv= tool.pwd +  tool.product + ".vap";
                        if ( vv!=null ) {
                            if ( WalkUtil.fileExists(vv) ) {
                                return true;
                            } else {
                                if ( tool.version!=null && tool.version.length()>0 ) {
                                    vv= tool.pwd +  tool.product + tool.version + ".vap";
                                    return WalkUtil.fileExists(vv);
                                } else {
                                    return false;
                                }
                            }
                        }
                    }
                } 
                return WalkUtil.fileExists(productFile);
            } catch (FileSystemOfflineException | URISyntaxException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
                return false;
            }
        };

        final String lap= "View in Autoplot";

        tool.addFileAction( enabler, new AbstractAction(lap) {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);                        
                String suri=null;
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
                    
                    // look in script and offer to run the script.
                    try {
                        File file = DataSetURI.getFile( s, new AlertNullProgressMonitor("get image file") ); // assume it's local.
                        String scriptURI= ImageUtil.getScriptURI( file );
                        if ( scriptURI!=null ) {
                            suri= scriptURI;
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
                    
                    if ( suri==null ) {
                        String productFile;

                        if ( tool.product!=null && tool.product.length()>0 && tool.baseurl.length()>1 ) {
                            productFile = tool.baseurl + tool.product + ".vap";  //HERE IT IS
                        } else {
                            productFile = template.substring(0, i0) + ".vap";  
                        }
                        
                        try {
                            if ( !WalkUtil.fileExists(productFile) ) {
                                String productFile2 = tool.pwd + tool.product + ".vap";
                                if ( WalkUtil.fileExists(productFile2) ) {
                                    productFile= productFile2;
                                } else {
                                    productFile2 = tool.pwd + tool.product + tool.version + ".vap";
                                    if ( WalkUtil.fileExists(productFile2) ) {
                                        productFile= productFile2;
                                    }
                                }
                            }
                        } catch (FileSystemOfflineException | URISyntaxException ex) {
                            logger.log(Level.SEVERE, null, ex);
                        }
                        
                        if ( timeRange!=null ) {
                            suri = productFile + "?timeRange=" + timeRange;
                        } else {
                            JOptionPane.showMessageDialog( parent, "unable to resolve time range from image metadata or filename.");
                            return;
                        }
                    }
                }

                final String fsuri= suri;
                
                Runnable run = () -> {
                    Window apWindow;
                    Application dom;
                    AutoplotUI autoplot;
                    ScriptContext2023 scriptContext;
                    if ( parent instanceof AutoplotUI ) {
                        apWindow= parent;
                        autoplot= ((AutoplotUI)apWindow);
                        dom= autoplot.getDom();
                        scriptContext= dom.getController().getScriptContext();
                    } else {
                        scriptContext= new ScriptContext2023();
                        scriptContext.createGui();
                        apWindow= scriptContext.getViewWindow();
                        autoplot= scriptContext.getApplication();
                        dom= autoplot.getDom();
                    }
                    if ( fsuri!=null ) {
                        raiseApWindowSoon(apWindow);
                        if ( fsuri.startsWith("script:") ) {
                            autoplot.runScriptTools(fsuri);
                            return;
                        } else {
                            scriptContext.plot(fsuri);
                        }
                    }
                    // go through and check for the axis autorange flag, and autorange if necessary.
                    for ( int i=0; i<dom.getPlots().length; i++ ) {
                        Plot p= dom.getPlots(i);
                        if ( p.getYaxis().isAutoRange() ) {
                            AutoplotUtil.resetZoomY(dom,p);
                        }
                        if ( p.getZaxis().isAutoRange() ) {
                            AutoplotUtil.resetZoomZ(dom,p);
                        }
                    }
                    if ( parent==null ) {
                        apWindow.setVisible(true);
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
     * Write a pngwalk file describing the current pngwalk.
     * http://autoplot.org/PNG_Walks
     * @param parent
     * @param ssrc the focus file 
     * @throws java.io.IOException
     */
    protected static void savePngwalkFile( PngWalkTool parent, String ssrc ) throws IOException {
        Preferences prefs = AutoplotSettings.settings().getPreferences(PngWalkTool.class);
        String srecent = prefs.get( PngWalkTool.PREF_RECENT, System.getProperty("user.home") );
        JFileChooser chooser= new JFileChooser( srecent );
        chooser.setFileFilter( new FileNameExtensionFilter( "pngwalk files", "pngwalk" ) );
        chooser.setMultiSelectionEnabled(false);
        if ( JFileChooser.APPROVE_OPTION==chooser.showSaveDialog(parent) ) {
            File f= chooser.getSelectedFile();
            if ( !f.getName().endsWith(".pngwalk") ) {
                f= new File( f.getAbsolutePath() + ".pngwalk" );
            }
            prefs.put( PngWalkTool.PREF_RECENT, f.getAbsolutePath() );
            try ( PrintWriter w= new PrintWriter(f) ) {
                if ( parent.baseurl.length()==0 ) {
                    String t= parent.getTemplate();
                    int i= WalkUtil.splitIndex( t );
                    w.println( "baseurl="+t.substring(0,i+1));
                    String filePattern= t.substring(i+1);
                    w.println( "filePattern="+filePattern);
                } else {
                    w.println( "baseurl="+parent.baseurl);
                    if ( parent.product!=null ) {
                        w.println( "product="+parent.product);
                    }
                    String s= parent.getTemplate();
                    if ( parent.product!=null && s.endsWith(".png") ) {
                        s= s.substring(0,s.length()-4);
                    }
                    w.println( "timeFormat="+s );
                }
                String pwd= chooser.getSelectedFile().getParent();
                w.println( "pwd="+ pwd );
                if ( isQualityControlEnabled() ) {
                    if ( parent.getQCTUrl()!=null ) {
                        w.println( "qcturl="+parent.getQCTUrl() );
                    } else {
                        w.println( "qcturl="+"file://"+pwd );
                    }
                }
            }
            parent.setStatus(".pngwalk file saved: "+f);
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
                if ( !src.exists() ) throw new IllegalArgumentException("Image file no longer exists: "+src);
                if ( r60.isSelected() ) {
                    BufferedImage im= ImageIO.read(src);
                    int size= (int)Math.sqrt( im.getWidth()*im.getWidth() + im.getHeight()*im.getHeight() );
                    im= ImageResize.getScaledInstance( im, size*60/100 );
                    String ext= chooser.getSelectedFile().toString();
                    int i= ext.lastIndexOf('.');
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
    private List<AbstractButton> qcFilterMenuItems= new ArrayList<>();

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

        fileMenu.add( new AbstractAction( "Save Local Copy of Image..." ) {
            @Override
            public void actionPerformed( ActionEvent e ) {
                LoggerManager.logGuiEvent(e);        
                saveLocalCopy(tool,tool.getSelectedFile());
            }
        } );
        fileMenu.add( new AbstractAction( "Save .pngwalk File..." ) {
            @Override
            public void actionPerformed( ActionEvent e ) {
                LoggerManager.logGuiEvent(e);  
                try {
                    savePngwalkFile(tool,tool.getSelectedFile());
                } catch ( IOException ex ) {
                    throw new RuntimeException(ex);
                }
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
                   ScriptContext2023 scriptContext= new ScriptContext2023();
                   scriptContext.createGui();
                   Window apWindow= scriptContext.getViewWindow();
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
                        if ( AppManager.getInstance().closeApplication(tool) ) {
                            frame.dispose();
                        }
                    }
                } else {
                    if ( AppManager.getInstance().closeApplication(tool) ) {
                        frame.dispose();
                    }
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
        persMi.addActionListener((ActionEvent e) -> {
            ((CoversWalkView)tool.views[4]).setPerspective(persMi.isSelected());
        });

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

        final JMenu qcFiltersMenu= new JMenu("QC Filters");
        ButtonGroup bg= new ButtonGroup();
        
        final JCheckBoxMenuItem qcmi= new JCheckBoxMenuItem("Show Only Quality Control Records",false);
        tool.qcFilterMenuItems.add( qcFiltersMenu );
        
        qcmi.addActionListener( new AbstractAction(  ) {
            @Override
            public void actionPerformed(ActionEvent e) {
                if ( qcmi.isSelected() ) {
                    tool.seq.setQCFilter("op");
                }
            }
        } );
        qcmi.setToolTipText("show only QC records with Okay or Problem setting.");
        tool.qcFilterMenuItems.add( qcmi);
        bg.add(qcmi);
        qcFiltersMenu.add(qcmi);
        
        final JCheckBoxMenuItem qcmi2= new JCheckBoxMenuItem("Show Okay Records",false);
        
        qcmi2.addActionListener( new AbstractAction(  ) {
            @Override
            public void actionPerformed(ActionEvent e) {
                if ( qcmi2.isSelected() ) {
                    tool.seq.setQCFilter("o");
                }
            }
        } );
        qcmi2.setToolTipText("show only QC records with Okay setting.");
        tool.qcFilterMenuItems.add( qcmi2);
        bg.add(qcmi2);
        qcFiltersMenu.add(qcmi2);
        
        final JCheckBoxMenuItem qcmi3= new JCheckBoxMenuItem("Show Problem Records",false);
        
        qcmi3.addActionListener( new AbstractAction(  ) {
            @Override
            public void actionPerformed(ActionEvent e) {
                if ( qcmi3.isSelected() ) {
                    tool.seq.setQCFilter("p");
                }
            }
        } );
        qcmi3.setToolTipText("show only QC records with Problem setting.");
        tool.qcFilterMenuItems.add(qcmi3);
        bg.add(qcmi3);
        qcFiltersMenu.add(qcmi3);
        
        final JCheckBoxMenuItem qcmi5= new JCheckBoxMenuItem("Don't show problem records",false);
        
        qcmi5.addActionListener( new AbstractAction(  ) {
            @Override
            public void actionPerformed(ActionEvent e) {
                if ( qcmi5.isSelected() ) {
                    tool.seq.setQCFilter("uio");
                }
            }
        } );
        qcmi5.setToolTipText("Don't show QC records with Problem setting.");
        tool.qcFilterMenuItems.add(qcmi5);
        bg.add(qcmi5);
        qcFiltersMenu.add(qcmi5);
        
        final JCheckBoxMenuItem qcmi4= new JCheckBoxMenuItem("Show All Records",false);
           
        qcmi4.addActionListener( new AbstractAction(  ) {
            @Override
            public void actionPerformed(ActionEvent e) {
                if ( qcmi4.isSelected() ) {
                    tool.seq.setQCFilter("");
                }
            }
        } );
        qcmi4.setToolTipText("show all records.");
        tool.qcFilterMenuItems.add(qcmi4);
        bg.add(qcmi4);
        qcmi4.setSelected(true);
        qcFiltersMenu.add(qcmi4);
        optionsMenu.add(qcFiltersMenu);
        
        for ( AbstractButton b: tool.qcFilterMenuItems ) {
            b.setEnabled( tool.qcPanel!=null );
        }
        
        result.add( optionsMenu );

        final JMenu toolsMenu= new JMenu("Tools");
        
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
        toolsMenu.add( qc );
        
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
        toolsMenu.add( dg );
        
        toolsMenu.add( new JSeparator() );
        
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
        
        final JMenuItem writeCsv= new JMenuItem( new AbstractAction( "Write to CSV..." ) {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);        
                tool.writeCsv();
            }
        });
        writeCsv.setToolTipText("Write the visible images to a CSV file.");
        toolsMenu.add( writeCsv );
        
        
        result.add( toolsMenu );
        
        final JMenuItem writeContactSheet= new JMenuItem( new AbstractAction( "Write to PNG Contact Sheet..." ) {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);        
                tool.writeContactSheet();
            }
        });
        writeContactSheet.setToolTipText("Write the visible thumbnails to PNG file.");
        toolsMenu.add( writeContactSheet );
        
        result.add( toolsMenu );
        
        final JMenu bookmarksMenu= new JMenu("Bookmarks");
        final BookmarksManager man= new BookmarksManager(frame,true,"PNG Bookmarks");

        man.getModel().addPropertyChangeListener(BookmarksManagerModel.PROP_LIST, (PropertyChangeEvent evt) -> {
            man.updateBookmarks( bookmarksMenu, tool.getSelector() );
        });
        man.setVisible(false);
        man.setPrefNode("pngwalk","autoplot.default.pngwalk.bookmarks", "http://autoplot.org/data/pngwalk.demos.xml");

        man.updateBookmarks( bookmarksMenu, tool.getSelector() );

        result.add( bookmarksMenu );
        
        final JMenu helpMenu= new JMenu( "Help" );
        final JMenuItem helpContentsMI= new JMenuItem( new AbstractAction( "Help Contents..." ) {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);        
                String surl = "http://autoplot.org/PNGWalks";
                AutoplotUtil.openBrowser(surl);
            }
        });
        helpContentsMI.setToolTipText("Help page for the PNG Walk Tool.");
        helpMenu.add( helpContentsMI );
        
        result.add( helpMenu );
        
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
                    loadPngwalkFile( template );
                } else {
                    setTemplate(template);
                }
            }
        });

        views= new PngWalkView[7];

        views[0]= new GridPngWalkView( null );
        views[1]= new RowPngWalkView( null );
        views[2]= new SinglePngWalkView( null, this );
        views[3]= new SinglePngWalkView( null, this );
        views[4]= new CoversWalkView( null );
        views[5]= new SinglePngWalkView( null, this );
        views[6]= new ContextFlowView(null);
        
        final int SCROLLBAR_HEIGHT = (int) Math.round( new JScrollPane().getHorizontalScrollBar().getPreferredSize().getHeight() );

        final JSplitPane filmStripSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, views[1], views[2] );

        //p.setEnabled(false);  //prevents user manipulation
        filmStripSplitPane.setDividerLocation(getThumbnailSize()+ (int)(1.2 *SCROLLBAR_HEIGHT));
        views[1].addPropertyChangeListener(PngWalkView.PROP_THUMBNAILSIZE, (PropertyChangeEvent evt) -> {
            filmStripSplitPane.setDividerLocation( (Integer)evt.getNewValue() + SCROLLBAR_HEIGHT );
        });

        filmStripSplitPane.setMinimumSize( new Dimension(640,480) );
        filmStripSplitPane.setPreferredSize( new Dimension(640,480) );
        
        final JSplitPane coversSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, views[4], views[5] );
        coversSplitPane.setDividerLocation(getThumbnailSize()+ (int)(1.2 *SCROLLBAR_HEIGHT));
        views[4].addPropertyChangeListener(PngWalkView.PROP_THUMBNAILSIZE, (PropertyChangeEvent evt) -> {
            coversSplitPane.setDividerLocation( (Integer)evt.getNewValue() + SCROLLBAR_HEIGHT  );
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
            qcPanel = new QualityControlPanel(this);
            JSplitPane qcPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, tabs, qcPanel);
            qcPane.setResizeWeight(1.0);
            pngsPanel.add(qcPane);
            qcPanel.setWalkImageSequence(seq);
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
        
        addMouseWheelListener( (MouseWheelEvent e) -> {
            if ( seq!=null && seq.size()!=0 ) seq.skipBy(e.getWheelRotation());
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
            String item= DataSetURI.fromUri(seq.currentImage().getUri());

            for ( int i=0; i<actionEnablers.size(); i++ ) {
                if ( actionEnablers.get(i)!=null ) {
                    boolean actionEnabled= actionEnablers.get(i).isActionEnabled(item);
                    actionButtons.get(i).setEnabled(actionEnabled);
                    if ( actionEnabled ) {
                       actionButtons.get(i).setActionCommand(actionCommand+" "+item);
                    }
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
    private transient PropertyChangeListener statusListener= (PropertyChangeEvent evt) -> {
        setStatus((String)evt.getNewValue());
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
            if ( qcFilterMenuItems!=null ) {
                for ( AbstractButton b: qcFilterMenuItems ) {
                    b.setEnabled( qcPanel!=null );
                }
            }
            if ( qcPanel!=null ) {
                qcPanel.setWalkImageSequence(seq);
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
        
        Runnable run= () -> {
            try {
                seq.initialLoad();
                
                if ( pendingGoto!=null )  {
                    seq.gotoSubrange(pendingGoto);
                    pendingGoto= null;
                }
                
            } catch (java.io.IOException e) {
                // This probably means the template was invalid. Don't set new sequence.
                if ( !getStatus().startsWith("error") ) setStatus("error:"+e.getMessage());
                Container p= SwingUtilities.getWindowAncestor(this);
                if ( p==null ) p= parentWindow;
                if ( this.getX()!=0 ) p= this; // for Linux, where the component isn't initialized yet.
                JOptionPane.showMessageDialog( p, "<html>Unable to find directory for: <br>"+ seq.getTemplate() );
                
                return;
            }

            SwingUtilities.invokeLater( new Runnable() {
                @Override
                public void run() {
                    updateInitialGui();
                }
            });
        };

        new Thread(run).start();

    }

    /**
     * initial settings to be performed on the event thread.
     */
    private void updateInitialGui() {

        dataSetSelector1.addToRecent( seq.getTemplate() );

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
                qcPanel.setWalkImageSequence(seq);
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

    /**
     * get the template used to describe the files in the pngwalk.
     * @return the template used to describe the files in the pngwalk.
     */
    public String getTemplate() {
        return seq.getTemplate();
    }
    
    /**
     * return the present working directory of the .pngwalk file (if used).
     * @return the present working directory of the .pngwalk file (if used).
     */
    public String getPwd() {
        return pwd;
    }

    /**
     * return the path for the quality control data.
     * @return 
     */
    public String getQCTUrl() {
        return qcturl;
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
        try {
            DatumRange tr= seq.imageAt( seq.getIndex() ).getDatumRange();
            if ( tr!=null ) {
                return tr;
            } else {
                return timeRange;
            }
        } catch ( Exception ex ) {
            // this happens when the sequence is initializing on another thread.
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

    private MouseAdapter imageMouseAdapter = null;

    public static final String PROP_IMAGEMOUSEADAPTER = "imageMouseAdapter";

    public MouseAdapter getImageMouseAdapter() {
        return imageMouseAdapter;
    }

    /**
     * add a mouse event handler, which will get events in the coordinate frame
     * of the image.  This can be set to null to clear the adapter.
     * @param imageMouseAdapter 
     */
    public void setImageMouseAdapter(MouseAdapter imageMouseAdapter) {
        MouseAdapter oldImageMouseAdapter = this.imageMouseAdapter;
        this.imageMouseAdapter = imageMouseAdapter;
        firePropertyChange(PROP_IMAGEMOUSEADAPTER, oldImageMouseAdapter, imageMouseAdapter);
    }

    transient PropertyChangeListener seqTimeRangeListener= (PropertyChangeEvent evt) -> {
        setTimeRange((DatumRange)evt.getNewValue());
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
        setMessage( IDLE_ICON, message );
    }

    public void setMessage( Icon icon, String message ) {
        if ( message==null ) message= "<null>"; // TODO: fix this later
        String myMess= message;
        if ( myMess.length()>100 ) myMess= myMess.substring(0,100)+"...";
        String fMyMessag= myMess;
        String fMessage= message;
        Runnable run= () -> {
            statusLabel.setIcon( icon );
            statusLabel.setText(fMyMessag);
            statusLabel.setToolTipText(fMessage);
        };
        SwingUtilities.invokeLater(run);
    }

    /**
     * start the quality control if it is not started already.
     */
    public void startQC() {
        if ( !isQualityControlEnabled() ) {
            qcPanel= new QualityControlPanel(this);
            tabs.add( "Quality Control", qcPanel );
            if ( seq!=null ) {
                qcPanel.setWalkImageSequence(seq);
                seq.addPropertyChangeListener(WalkImageSequence.PROP_BADGE_CHANGE, qcStatusListener);
            }
            ENABLE_QUALITY_CONTROL= true;
        }
        for ( AbstractButton b: qcFilterMenuItems ) {
            b.setEnabled(true);
        }
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
            digitizer.addDataSetUpdateListener((DataSetUpdateEvent e) -> {
                for (PngWalkView v : views) {
                    if ( v instanceof SinglePngWalkView ) {
                        v.repaint();
                    }
                }
            });
            digitizer.addDataPointSelectionListener((DataPointSelectionEvent e) -> {
                String image= (e.getPlane("image").toString());
                int i= seq.findIndex(image);
                if ( i>-1 ) {
                    seq.setIndex(i);
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
            
            annoType.addItemListener((ItemEvent e) -> {
                annoTypeChar= e.getItem().toString().charAt(0);
                for (PngWalkView v : views) {
                    if ( v instanceof SinglePngWalkView ) {
                        v.repaint();
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

    private void writeContactSheet() {
        Component ttt= tabs.getTabByTitle("Grid");
        if ( ttt instanceof GridPngWalkView ) {
            try {
                Preferences prefs= Preferences.userNodeForPackage(PngWalkTool.class);
                String fname= prefs.get( "writeToContactSheet", "/tmp/contactSheet.png" );
                JFileChooser chooser= new JFileChooser(fname);
                if ( !fname.equals("/tmp/contactSheet.png") ) {
                    chooser.setSelectedFile( new File(fname) );
                }
                chooser.setFileFilter( new FileNameExtensionFilter( "PNG Files", "png") );
                if ( chooser.showSaveDialog(this)==JFileChooser.APPROVE_OPTION ) {
                    File f= chooser.getSelectedFile();
                    if ( !f.getName().endsWith(".png") ) {
                        f= new File( f.getParentFile(), f.getName()+".png" );
                    }
                    writeContactSheet( f );
                    prefs.put( "writeToContactSheet", f.toString() );
                }
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
                JOptionPane.showMessageDialog( parentWindow, "Error while creating contact sheet");
            }
        }
    }

    /**
     * write the current Grid view to a single PNG file.
     * @param f
     * @throws IOException 
     */
    public void writeContactSheet( File f ) throws IOException {
        Component ttt= tabs.getTabByTitle("Grid");
        if ( ttt instanceof GridPngWalkView ) {
            BufferedImage im= ((GridPngWalkView)ttt).paintContactSheet();
            ImageIO.write( im, "png", f );
            setMessage("Wrote to "+f);
        }
    }
    
    public static interface ActionEnabler {
        boolean isActionEnabled( String filename );
    }

    /**
     * Enabler that returns true for local files.
     */
    public static final ActionEnabler LOCAL_FILE_ENABLER =
            (String filename) -> DataSetURI.getResourceURI(filename).toString().startsWith("file:" );

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
    
    List<Painter> decorators= new LinkedList<>();
    
    /**
     * add a decorator to the PngWalkTool, which is drawn on single-image
     * views.  Note this is draw in the coordinate system of the image, pixel
     * coordinates with the origin (0,0) at the top left.
     * @param p 
     */
    public void addTopDecorator( Painter p ) {
        if ( !decorators.contains(p) ) {
            decorators.add( p );
        }
        repaint();
    }
    
    /**
     * remove a decorator to the PngWalkTool, which is drawn on single-image
     * views.  If the decorator is not found, no error is thrown.
     * @param p 
     */
    public void removeTopDecorator( Painter p ) {
        decorators.remove( p );
        repaint();
    }
    
    /**
     * remove all decorators from the PngWalkTool.
     */
    public void removeTopDecorators() {
        decorators.clear( );
        repaint();
    }
    
    /**
     * returns true if there are any top decorators.
     * @return true if there are any decorators.
     */
    public boolean hasTopDecorators() {
        return ! decorators.isEmpty();
    }
    /**
     * set a new component for the bottom left panel, where by default the 
     * navigation panel resides.
     * @param c 
     */
    public void setBottomLeftPanel( JComponent c ) {
        bottomLeftPanel.removeAll();
        if ( c!=null ) bottomLeftPanel.add( c, BorderLayout.CENTER );
        revalidate();
    }
    
    /**
     * remove all components from the bottom left panel.
     */
    public void clearBottomLeftPanel() {
        bottomLeftPanel.removeAll();
    }
    
    /**
     * get a reference to the navigation panel.  To restore the normal layout,
     * use setBottomLeftPanel( getNavigationPanel() ).
     * @return the navigation panel.
     */
    public JPanel getNavigationPanel() {
        return navigationPanel;
    }
    
    /**
     * returns the current selection, which may be a URL on a remote site, or null if no sequence has been selected.
     * @return the current selection or null if the sequence is not loaded or empty.
     */
    public String getSelectedFile() {
        if ( seq==null ) return null;
        if ( seq.size()==0 ) return null;
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
        actionButtonsPanel = new javax.swing.JPanel();
        dataSetSelector1 = new org.autoplot.datasource.DataSetSelector();
        statusLabel = new javax.swing.JLabel();
        bottomLeftPanel = new javax.swing.JPanel();
        navigationPanel = new javax.swing.JPanel();
        timeFilterTextField = new javax.swing.JTextField();
        showMissingCheckBox = new javax.swing.JCheckBox();
        useRangeCheckBox = new javax.swing.JCheckBox();
        jPanel1 = new javax.swing.JPanel();
        prevSetButton = new javax.swing.JButton();
        prevButton = new javax.swing.JButton();
        nextButton = new javax.swing.JButton();
        nextSetButton = new javax.swing.JButton();
        jumpToFirstButton = new javax.swing.JButton();
        jumpToLastButton = new javax.swing.JButton();
        editRangeButton = new javax.swing.JButton();

        pngsPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        pngsPanel.setLayout(new java.awt.BorderLayout());

        actionButtonsPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));

        dataSetSelector1.setToolTipText("Enter the location of the images as a wildcard (/tmp/*.png) or template (/tmp/$Y$m$d.png).  .png, .gif, and .jpg files are supported.");
        dataSetSelector1.setPromptText("Enter images filename template");
        dataSetSelector1.setValue("");
        dataSetSelector1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataSetSelector1ActionPerformed(evt);
            }
        });

        statusLabel.setText("starting application...");

        bottomLeftPanel.setLayout(new java.awt.BorderLayout());

        timeFilterTextField.setToolTipText("Enter a time range, for example a year like \"2009\", or month \"2009 may\", or \"2009-01-01 to 2009-03-10\"\n");
        timeFilterTextField.setEnabled(false);
        timeFilterTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                timeFilterTextFieldFocusLost(evt);
            }
        });
        timeFilterTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timeFilterTextFieldActionPerformed(evt);
            }
        });

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

        editRangeButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/autoplot/resources/calendar.png"))); // NOI18N
        editRangeButton.setToolTipText("Time Range Tool");
        editRangeButton.setEnabled(false);
        editRangeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editRangeButtonActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout navigationPanelLayout = new org.jdesktop.layout.GroupLayout(navigationPanel);
        navigationPanel.setLayout(navigationPanelLayout);
        navigationPanelLayout.setHorizontalGroup(
            navigationPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, navigationPanelLayout.createSequentialGroup()
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(navigationPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(navigationPanelLayout.createSequentialGroup()
                        .add(18, 18, 18)
                        .add(useRangeCheckBox)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(timeFilterTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 236, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(12, 12, 12)
                        .add(editRangeButton)
                        .add(18, 18, 18)
                        .add(showMissingCheckBox))
                    .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
        navigationPanelLayout.setVerticalGroup(
            navigationPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, navigationPanelLayout.createSequentialGroup()
                .add(navigationPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(timeFilterTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(useRangeCheckBox)
                    .add(editRangeButton)
                    .add(showMissingCheckBox))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        bottomLeftPanel.add(navigationPanel, java.awt.BorderLayout.CENTER);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, pngsPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 932, Short.MAX_VALUE)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(dataSetSelector1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(statusLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(layout.createSequentialGroup()
                        .add(bottomLeftPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(actionButtonsPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(pngsPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 639, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(dataSetSelector1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 27, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(bottomLeftPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(actionButtonsPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 57, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
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
        LoggerManager.logGuiEvent(evt);
        updateTimeRangeFilter( );
    }//GEN-LAST:event_timeFilterTextFieldActionPerformed

    public void updateTimeRangeFilter() {
        try {
            timeFilterTextField.setBackground( dataSetSelector1.getBackground() );
            DatumRange range= DatumRangeUtil.parseTimeRange(timeFilterTextField.getText());
            seq.setActiveSubrange( range );
        } catch ( ParseException ex ) {
            timeFilterTextField.setBackground( Color.PINK );
        }
    }

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
            loadPngwalkFile(t);
        } else {
            setTemplate( t );
        }
        nextButton.requestFocus();
    }//GEN-LAST:event_dataSetSelector1ActionPerformed

    private void showMissingCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_showMissingCheckBoxItemStateChanged
        seq.setShowMissing(evt.getStateChange()==java.awt.event.ItemEvent.SELECTED);
    }//GEN-LAST:event_showMissingCheckBoxItemStateChanged

    private void editRangeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editRangeButtonActionPerformed
        LoggerManager.logGuiEvent(evt);
        TimeRangeTool t= new TimeRangeTool();
        List<DatumRange> times;
        if ( seq.isUseSubRange() ) {
            t.setSelectedRange( timeFilterTextField.getText() );
        } else {
            times = seq.getAllTimes();
            DatumRange tr= times.get(0);
            for ( DatumRange tr1: times ) {
                tr= tr.union(tr1);
            }
            t.setSelectedRange( timeFilterTextField.getText() );
        }
        if ( JOptionPane.OK_OPTION==JOptionPane.showConfirmDialog( parentWindow, t, "Subrange", JOptionPane.OK_CANCEL_OPTION ) ) {
            try {
                DatumRange range= DatumRangeUtil.parseDatumRange( t.getSelectedRange() );
                timeFilterTextField.setText( range.toString() );
                updateTimeRangeFilter();
            } catch (ParseException ex) {
                Logger.getLogger(PngWalkTool.class.getName()).log(Level.SEVERE, null, ex);
            }
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
    
    
    /**
     * 
     * @param monitor
     * @param f the output folder.
     * @param summary summary title for each slide.
     * @throws FileNotFoundException 
     */
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
        
        if ( !f.exists() ) {
            if ( !f.mkdirs() ) {
                logger.log(Level.WARNING, "unable to create folder: {0}", f);
            }
        }
        
        
        boolean writeInSitu= base.relativize(f.toURI() ).toString().trim().length()==0;
                    
        try {
            if ( !writeInSitu ) {
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
            }
        } finally {
            monitor.finished();
        }
        
        try {
            URL url= new URL("https://github.com/autoplot/scripts/makeTutorialHtml.jy");
            File nf= DataSetURI.getFile(url,new NullProgressMonitor()); // Note GitHub filesystem.
            final ProgressMonitor mon= DasProgressPanel.createFramed(SwingUtilities.getWindowAncestor(this),"write HTML");
            Map<String,String> params= new HashMap<>();
            params.put("dir",base.toString()+"/");
            params.put("qconly", this.seq.getQCFilter().equals("") ? "F" : "T" );
            String sd= f.toString();
            if ( !sd.endsWith("/") && !sd.endsWith("\\") ) {
                sd= sd+"/";
            }
            params.put("outdir",sd);
            params.put("name",""); //TODO: what should this be?
            params.put("summary",summary);
            try {
                Application dom= null;
                if ( parentWindow instanceof AutoplotUI ) {
                    AutoplotUI parent= (AutoplotUI)parentWindow;
                    dom = parent.getDom();
                }
                JythonUtil.invokeScriptSoon(nf.toURI(),dom,params,false,false,mon);
            } catch (IOException ex) {
                Logger.getLogger(PngWalkTool.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch ( MalformedURLException ex ) {
            throw new IllegalArgumentException(ex);
        } catch (IOException ex) {
            Logger.getLogger(PngWalkTool.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
    
    /**
     * write the sequence to a HTML file, so that this can be used to produce
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
            Runnable run= () -> {
                try {
                    writeToHtmlImmediately( mon , f, hoo.getTitle() );
                } catch (FileNotFoundException ex) {
                    throw new RuntimeException(ex);
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
            
            Font lightGreyFont= new Font();
            lightGreyFont.setColor( BaseColor.LIGHT_GRAY );
            
            Chunk lineChunk= new Chunk("________________________________"+
                            "_________________________________", lightGreyFont );
            
            logger.log(Level.FINE, "writeToPdf {0} {1} pages", new Object[]{f.getName(), this.seq.size()});
            
            for ( int i= 0; i<this.seq.size(); i++ ) {
                monitor.setTaskProgress(i);
                if ( monitor.isCancelled() ) {
                    break;
                }
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
                    logger.log(Level.FINER, "Page {0} of {1} image {2}x{3}", new Object[]{ imageNumber, this.seq.size(), im.getHeight(), im.getWidth()});
                    //if ( im.getHeight()>800 ) {
                    //    im= ImageUtil.getScaledInstance( im, 800, 800 * 600, true );
                    //}
                    ImageIO.write(im, "png", baos);
                    Image pdfImage= com.itextpdf.text.Image.getInstance(baos.toByteArray() );
                    int w= (int)(7.5*72);
                    int h= w * im.getHeight() / im.getWidth();
                    pdfImage.setAbsolutePosition(36,11*72-36-h);
                    pdfImage.scaleToFit(w,h);
                    
                    PdfPTable table= new PdfPTable(1);
                    
                    table.getDefaultCell().setBorder( Rectangle.NO_BORDER );
                    table.getDefaultCell().setPaddingLeft( 48 );
                    table.getDefaultCell().setPaddingRight( 24 );
                    
                    table.setWidthPercentage(100);
                    
                    PdfPCell cell;
                    
                    cell= new PdfPHeaderCell();
                    cell.setFixedHeight(72);
                    cell.setPaddingLeft( 48 );
                    cell.setPaddingRight( 24 );
                    cell.setHorizontalAlignment(  Element.ALIGN_RIGHT );
                    cell.setVerticalAlignment( Element.ALIGN_BOTTOM );
                    
                    Paragraph p;
                    p= new Paragraph();
                    p.setAlignment( Element.ALIGN_RIGHT );
                    p.add( String.format("%d of %d", imageNumber, this.seq.size() ) );
                    
                    cell.addElement( p );
                    cell.setBorder( Rectangle.NO_BORDER );
                    cell.setPaddingLeft( 48 );
                    cell.setPaddingRight( 48 );
                    table.addCell( cell );
                    
                    cell = new PdfPCell( pdfImage );
                    cell.setBorder( Rectangle.NO_BORDER );
                    cell.setPaddingLeft( 48 );
                    cell.setPaddingRight( 24 );
                    
                    table.addCell( cell );
                    
                    String caption;
                    if ( qcseq!=null ) {
                        QualityControlRecord r= qcseq.getQualityControlRecord(i);
                        if ( r!=null ) {
                            caption= r.getLastComment();
                        } else {
                            caption= ""; 
                        }
                    } else {
                        caption= ""; 
                    }
                    logger.log(Level.FINER, "caption: {0}", caption);
                    
                    p= new Paragraph();
                    p.add( caption );
                    cell = new PdfPCell( p );
                    cell.setBorder( Rectangle.NO_BORDER );
                    cell.setPaddingLeft( 48 );
                    cell.setPaddingRight( 48 );

                    table.addCell(cell);

                    cell = new PdfPCell( p );
                    cell.addElement( new Paragraph(" ") );
                    for ( int j=0;j<10; j++ ) {
                        p= new Paragraph(lineChunk);
                        cell.addElement( p );
                    }
                    cell.setBorder( Rectangle.NO_BORDER );
                    cell.setPaddingLeft( 48 );
                    cell.setPaddingRight( 48 );
                    table.addCell(cell);
                    
                    Chunk nameChunk= new Chunk( this.seq.imageAt(i).uriString, lightGreyFont );
                    cell= new PdfPCell( new Paragraph(nameChunk) );
                    cell.setBorder( Rectangle.NO_BORDER );
                    cell.setPaddingLeft( 48 );
                    cell.setPaddingRight( 48 );
                    table.addCell( cell );
                    
                    doc.add( table );
                    
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
        choose.setFileFilter( new FileNameExtensionFilter("pdf files", "pdf" ));
        if ( choose.showSaveDialog(PngWalkTool.this)==JFileChooser.APPROVE_OPTION ) {
            final File f= choose.getSelectedFile();
            prefs.put( "writeToPdf", f.toString() );
            final ProgressMonitor mon= DasProgressPanel.createFramed(SwingUtilities.getWindowAncestor(this),"write pdf");
            Runnable run= () -> {
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
                    } );
                    panel.add( b );
                    JButton b2= new JButton("Copy filename to clipboard");
                    b2.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            GuiSupport.setClipboard( f.toURI().toString() );
                        }
                    });
                    panel.add( b2 );
                    JOptionPane.showMessageDialog( PngWalkTool.this,panel );
                } catch (FileNotFoundException ex) {
                    logger.log(Level.SEVERE, null, ex);                    
                }
            };
            new Thread(run).start();

        }
    }
    
    /**
     * write the sequence to a HTML file, so that this can be used to produce
     * worksheets.
     * 
     */
    public void writeCsv() {
        JFileChooser choose= new JFileChooser();
        
        Preferences prefs= Preferences.userNodeForPackage(PngWalkTool.class);
        String fname= prefs.get( "writeToCsv", "/tmp/pngwalk.csv" );
        
        choose.setFileSelectionMode(JFileChooser.FILES_ONLY);
        choose.setSelectedFile( new File(fname) );
        choose.setFileFilter( new FileNameExtensionFilter("csv files", "csv" ));
                
        if ( choose.showSaveDialog(PngWalkTool.this)==JFileChooser.APPROVE_OPTION ) {
            final File f= choose.getSelectedFile();
            prefs.put( "writeToCsv", f.toString() );
            final ProgressMonitor mon= DasProgressPanel.createFramed(SwingUtilities.getWindowAncestor(this),"write csv");
            Runnable run= () -> {
                try {
                    writeToCsvImmediately( mon , f );
                } catch (FileNotFoundException ex) {
                    logger.log(Level.SEVERE, null, ex);                    
                }
                final JPanel panel= new javax.swing.JPanel();
                panel.setLayout( new BoxLayout(panel,BoxLayout.Y_AXIS ));
                panel.add( new javax.swing.JLabel("wrote file "+f) );
                JButton b= new JButton("Open in Browser");
                b.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        AutoplotUtil.openBrowser(f.toURI().toString());
                    }
                } );
                panel.add( b );
                JButton b2= new JButton("Copy filename to clipboard");
                b2.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        GuiSupport.setClipboard( f.toURI().toString() );
                    }
                });
                panel.add( b2 );
                JOptionPane.showMessageDialog( PngWalkTool.this,panel );
            };
            new Thread(run).start();
        }
    }
    
    private void writeToCsvImmediately( ProgressMonitor monitor, File f ) throws FileNotFoundException {
        try ( PrintWriter pout= new PrintWriter(f) ) {
            monitor.setTaskSize(this.seq.size());
            monitor.started();
                      
            QualityControlSequence qcseq= this.seq.getQualityControlSequence();
                                    
            logger.log(Level.FINE, "writeToCsv {1}", new Object[]{f.getName()});
            
            pout.println( "start,stop,label,filename,lastQCMessage,QCStatus");
            
            for ( int i= 0; i<this.seq.size(); i++ ) {
                monitor.setTaskProgress(i);
                if ( monitor.isCancelled() ) {
                    break;
                }
 
                WalkImage wi= this.seq.imageAt(i);
                String s= wi.getUri().toString();
                URI rel= this.seq.getBaseUri().relativize(wi.getUri());
                
                DatumRange dr= wi.getDatumRange();
                String smin= dr==null ? "" : dr.min().toString();
                String smax= dr==null ? "" : dr.max().toString();
                String scaption= wi.getCaption();
                if ( scaption.contains(" ") || scaption.contains(",") ) {
                    scaption = "\"" + scaption + "\"";
                }
                String filename= rel.toString(); 
                if ( filename.contains(" ") || filename.contains(",") ) {
                    filename= "\"" + filename + "\"";
                }
                QualityControlRecord qcr= qcseq==null ? null : qcseq.getQualityControlRecord(i);
                String lastComment = qcr==null ? "" : qcr.getLastComment();
                if ( lastComment.trim().length()>0 ) {
                    int nl= lastComment.indexOf("\n");
                    if ( nl>-1 ) lastComment= lastComment.substring(0,nl);
                    lastComment= "\""+lastComment+"\"";
                }
                String status = qcr==null ? "" : qcr.getStatus().toString();
                if ( status.equals("Unknown") ) status="";
                                        
                String line= String.format("%s,%s,%s,%s,%s,%s",smin,smax,scaption,filename,lastComment,status);
                
                pout.println(line);

            }
                        
        } finally {
            monitor.finished();
            
        }
    }
    
    /**
     * 
     * @param monitor
     * @param f
     * @param overrideDelays if null, then just use 100ms between frames, otherwise use this delay. "realTime", "10ms", "secondPerDay"
     * @param r60, if true, then reduce the image to 60% of its original size.
     * @throws FileNotFoundException 
     */
    private void writeToAnimatedGifImmediately( final ProgressMonitor monitor, File f, final String overrideDelays, final boolean r60 ) throws FileNotFoundException {
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
                    return i<PngWalkTool.this.seq.size() && !monitor.isCancelled() ;
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
                    
                    if ( r60 ) {
                        int size= (int)Math.sqrt( im.getWidth()*im.getWidth() + im.getHeight()*im.getHeight() );
                        im= ImageResize.getScaledInstance( im, size*60/100 );
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
                    throw new IllegalArgumentException("use images.next");
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
                                result= String.valueOf((int) Math.ceil( seq.imageAt(i).getDatumRange().min().subtract(lastTime).convertTo(Units.milliseconds).value()/10. ) );
                                lastTime= seq.imageAt(i).getDatumRange().min();
                                break;
                            case "secondPerDay":
                                result= String.valueOf((int) Math.ceil( seq.imageAt(i).getDatumRange().min().subtract(lastTime).convertTo(Units.milliseconds).value()/864000) );
                                lastTime= seq.imageAt(i).getDatumRange().min();
                                break;
                            default:
                                try {
                                    result= String.valueOf((int) Math.ceil( Units.milliseconds.parse(overrideDelays).value()/10 ) );
                                } catch (ParseException ex) {
                                    throw new IllegalArgumentException( ex );
                                }   
                                break;
                        }
                    } else {
                        result= "1";
                    }
                    return result;
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException("remove is not supported");
                }
                
                
            };
            
            logger.log(Level.FINE, "writing to {0}", f);
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
    public void writeAnimatedGif( ) {
        JFileChooser choose= new JFileChooser();
        
        Preferences prefs= Preferences.userNodeForPackage(PngWalkTool.class);
        String fname= prefs.get( "writeToGif", "/tmp/pngwalk.gif" );
        
        choose.setSelectedFile( new File(fname) );
        final String[] opts= new String[] { "10ms", "50ms", "200ms", "400ms", "800ms", "1000ms", "1200ms", "realTime", "secondPerDay" };
        JPanel p= new JPanel();
        p.setLayout( new BoxLayout(p,BoxLayout.Y_AXIS) );
        
        JComboBox jo= new JComboBox(opts);
        jo.setSelectedIndex(1);
        jo.setMaximumSize( new Dimension( 1000, 30 ) );
        jo.setEditable(true);
        
        p.add(new JLabel("Interslide-Delay:"));
        p.add(jo);
        final JCheckBox r60= new JCheckBox( "Reduce to 60%" );
        p.add(r60);
        p.add(Box.createGlue());
                
        choose.setAccessory(p);
        if ( choose.showSaveDialog(PngWalkTool.this)==JFileChooser.APPROVE_OPTION ) {
            final File f= choose.getSelectedFile();
            prefs.put( "writeToGif", f.toString() );
            final ProgressMonitor mon= DasProgressPanel.createFramed(SwingUtilities.getWindowAncestor(this),"write animated gif");
            final String fdelay= (String)jo.getSelectedItem();
            Runnable run= () -> {
                try {
                    writeToAnimatedGifImmediately( mon, f, fdelay, r60.isSelected() );
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
                    JButton b2= new JButton("Copy filename to clipboard");
                    b2.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            GuiSupport.setClipboard( f.toURI().toString() );
                        }
                    });
                    panel.add( b2 );
                    JOptionPane.showMessageDialog( PngWalkTool.this,panel );
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(PngWalkTool.class.getName()).log(Level.SEVERE, null, ex);                    
                }
            };
            new Thread(run).start();

        }
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel actionButtonsPanel;
    private javax.swing.JPanel bottomLeftPanel;
    private org.autoplot.datasource.DataSetSelector dataSetSelector1;
    private javax.swing.JButton editRangeButton;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JButton jumpToFirstButton;
    private javax.swing.JButton jumpToLastButton;
    private javax.swing.JPanel navigationPanel;
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
