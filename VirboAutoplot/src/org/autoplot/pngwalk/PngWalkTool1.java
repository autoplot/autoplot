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
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.das2.components.TearoffTabbedPane;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.util.ArgumentList;
import org.virbo.autoplot.bookmarks.Bookmark;
import org.virbo.datasource.DataSetSelector;
import org.xml.sax.SAXException;
import org.das2.datum.TimeParser;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.datum.format.TimeDatumFormatter;
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
import org.virbo.autoplot.ScriptContext;
import org.virbo.autoplot.bookmarks.Util;
import org.virbo.autoplot.bookmarks.BookmarksManager;
import org.virbo.autoplot.bookmarks.BookmarksManagerModel;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.FileSystemUtil;
import org.virbo.datasource.URISplit;
import sun.security.krb5.internal.SeqNumber;

/**
 *
 * @author jbf
 */
public class PngWalkTool1 extends javax.swing.JPanel {
    private static boolean ENABLE_QUALITY_CONTROL;
    private QualityControlPanel qcPanel=null;

    public static final String PREF_RECENT = "pngWalkRecent";

    public PngWalkView[] views;
    TearoffTabbedPane tabs;
    
    WalkImageSequence seq;
    
    Pattern actionMatch=null;
    String actionCommand=null;

    static Logger logger= Logger.getLogger("org.autoplot.pngwalk");
    private static String RESOURCES= "/org/virbo/autoplot/resources/";
    public static final Icon WARNING_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"warning-icon.png") );
    public static final Icon ERROR_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"error-icon.png") );
    public static final Icon BUSY_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"spinner.gif") );
    public static final Icon READY_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"indProgress0.png") );
    public static final Icon IDLE_ICON= new ImageIcon( AutoplotUI.class.getResource(RESOURCES+"idle-icon.png") );
        
    int returnTabIndex=0; // index of the tab we left to look at the single panel view.  TODO: account for tear off.

    DatumRange pendingGoto= null;  // after password is entered, then go to this range.

    public static void main(String[] args) {

        DataSetURI.init();  // for FtpFileSystem implementation

        System.err.println("this is pngwalk 20110114");
        final ArgumentList alm = new ArgumentList("PngWalkTool1");
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
                e.printStackTrace();
            }
        }

        if (alm.getBooleanValue("qualityControl"))
            ENABLE_QUALITY_CONTROL = true;
        else
            ENABLE_QUALITY_CONTROL = false;

        String template = alm.getValue("template"); // One Slash!!
        //final String template=  "file:/home/jbf/temp/product_$Y$m$d.png" ; // One Slash!!
        //final String template=  "file:/net/spot3/home/jbf/fun/pics/2001minnesota/.*JPG" ;
        //final String template= "file:/home/jbf/public_html/voyager/VGPW_0201/BROWSE/V1/.*.PNG";
        //final String template= "file:///net/spot3/home/jbf/fun/pics/20080315_tenerife_masca_hike/IMG_.*.JPG";
        //final String template= "http://www.swpc.noaa.gov/ftpdir/lists/hpi/plots/pmap_$Y_$m_$d_...._S_.*_.*_.*_.*.gif";

        PngWalkTool1 pngWalkTool= start( template, null );

        pngWalkTool.processArguments(alm);

    }

    private static String readPngwalkFile( String template ) {
        URISplit split= URISplit.parse(template);
        try {
            Properties p= new Properties();
            File local= FileSystemUtil.doDownload( split.file, new NullProgressMonitor() );
            p.load( new FileInputStream( local ) );
            String t= split.path + p.getProperty("product") + "_" + p.getProperty("timeFormat") + ".png";
            template= t;
        } catch (FileSystemOfflineException ex) {
            Logger.getLogger(PngWalkTool1.class.getName()).log(Level.SEVERE, null, ex);
        } catch (URISyntaxException ex) {
            Logger.getLogger(PngWalkTool1.class.getName()).log(Level.SEVERE, null, ex);
        } catch (FileNotFoundException ex) {
            throw new IllegalArgumentException("File does not exist: "+template);
        } catch (IOException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
        return template;

    }
    public static PngWalkTool1 start( String template, final Window parent ) {

        final PngWalkTool1 tool = new PngWalkTool1();

        if ( template!=null ) {
            if ( template.endsWith(".pngwalk") ) {
                template= readPngwalkFile(template);
            }
            tool.setTemplate(template);
        } 

        String home= java.lang.System.getProperty( "user.home" ) + java.lang.System.getProperty( "file.separator" );

        String output= "file:" + home + "pngwalk" + java.lang.System.getProperty( "file.separator" )
                + "product_$Y$m$d.png";

        String sdeft= "<bookmark-list>  <bookmark>     <title>User Bookdefault</title>"
                + "     <url>"+output+"</url>  </bookmark></bookmark-list>";

        List<Bookmark> deft=null;
        try {
            deft = Bookmark.parseBookmarks(sdeft);

        } catch (SAXException ex) {
            Logger.getLogger(PngWalkTool1.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(PngWalkTool1.class.getName()).log(Level.SEVERE, null, ex);
        }

        Util.loadRecent( "pngwalkRecent", tool.dataSetSelector1, deft );


        PngWalkTool1.ActionEnabler enabler= new PngWalkTool1.ActionEnabler() {
            public boolean isActionEnabled(String filename) {
                String s = filename;
                String template = tool.getTemplate();
                int i0 = template.indexOf("_$Y");
                if ( i0==-1 ) i0= template.indexOf("_%Y");
                int i1 = s.indexOf(".png");
                if ( i1==-1 || i0==-1 ) return false;
                //String timeRange = s.substring(i0 + 1, i1);
                String productFile = template.substring(0, i0) + ".vap";
                try {
                    return WalkUtil.fileExists(productFile);
                } catch (FileSystemOfflineException ex) {
                    Logger.getLogger(DemoPngWalk.class.getName()).log(Level.SEVERE, null, ex);
                    return false;
                } catch (URISyntaxException ex) {
                    Logger.getLogger(DemoPngWalk.class.getName()).log(Level.SEVERE, null, ex);
                    return false;
                }
            }
        };

        final String lap= "View in Autoplot";

        tool.addFileAction( enabler, "autoplot", new AbstractAction(lap) {
            public void actionPerformed(ActionEvent e) {
                String productFile=null;
                final String suri;
                if ( tool.seq==null ) {
                    productFile=null;
                    suri=null;
                } else {
                    String s = tool.getSelectedFile();
                    String template = tool.getTemplate();
                    if ( s.startsWith("file:/") && !s.startsWith("file:///") && template.startsWith("file:///") ) {
                        s= "file:///"+s.substring(6);
                    }
                    int i0 = template.indexOf("_$Y");
                    if ( i0==-1 ) i0= template.indexOf("_%Y");
                    //int i1 = template.indexOf(".png");
                    //if ( i1==-1 ) return;
                    //TimeParser tp= TimeParser.create( template.substring(i0 + 1, i1) );
                    //String timeRange = s.substring(i0 + 1, i1);
                    TimeParser tp= TimeParser.create( template );
                    String timeRange = s;
                    try {
                        DatumRange dr= tp.parse(timeRange).getTimeRange();
                        timeRange= dr.toString().replaceAll(" ", "+");
                    } catch ( ParseException ex ) {
                        throw new RuntimeException(ex);
                    }
                    productFile = template.substring(0, i0) + ".vap";
                    suri = productFile + "?timeRange=" + timeRange;
                }

                Runnable run = new Runnable() {
                    public void run() {
                        try {
                            ScriptContext.createGui();
                            if ( suri!=null ) ScriptContext.plot(suri);
                            Window apWindow= ScriptContext.getViewWindow();
                            if ( parent==null ) {
                                apWindow.setVisible(true);
                            }
                            apWindow.toFront();
                            apWindow.repaint();
                        } catch (InterruptedException ex) {
                            Logger.getLogger(DemoPngWalk.class.getName()).log(Level.SEVERE, null, ex);
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

    private static JMenuBar createMenuBar( final PngWalkTool1 tool, final JFrame f ) {
        JMenuBar result= new JMenuBar();
        JMenu fileMenu= new JMenu("File");

        fileMenu.add( new AbstractAction( "Close" ) {
            public void actionPerformed(ActionEvent e) {
                if ( AppManager.getInstance().getApplicationCount()==1 ) {
                    if ( JOptionPane.OK_OPTION==
                            JOptionPane.showConfirmDialog( tool,
                            "Quit application?", "Quit PNG Walk", JOptionPane.OK_CANCEL_OPTION ) ) {
                        f.dispose();
                        AppManager.getInstance().closeApplication(tool);
                    }
                } else {
                    f.dispose();
                    AppManager.getInstance().closeApplication(tool);
                }
            }
        } );
        fileMenu.add( new AbstractAction( "Quit" ) {
            public void actionPerformed(ActionEvent e) {
                f.dispose();
                AppManager.getInstance().quit();
            }
        } );
        result.add(fileMenu);

        BindingGroup bg= new BindingGroup();

        JMenu navMenu= new JMenu("Navigate");
        navMenu.add( new AbstractAction( "Go To Date..." ) {
            public void actionPerformed(ActionEvent e) {
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

        navMenu.add( new AbstractAction( "Previous Item" ) {
            public void actionPerformed( ActionEvent e ) {
               tool.seq.skipBy( -1 );
            }
        } ).setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_LEFT, 0 ));


        navMenu.add( new AbstractAction( "Next Item" ) {
            public void actionPerformed( ActionEvent e ) {
               tool.seq.skipBy( 1 );
            }
        } ).setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_RIGHT, 0 ));

        result.add( navMenu );
        
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
        
        result.add( optionsMenu );

        final JMenu bookmarksMenu= new JMenu("Bookmarks");
        final BookmarksManager man= new BookmarksManager(f,true);

        man.getModel().addPropertyChangeListener( BookmarksManagerModel.PROP_LIST, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                man.updateBookmarks( bookmarksMenu, tool.getSelector() );
            }
        });
        man.setVisible(false);
        man.setPrefNode("pngwalk");

        man.updateBookmarks( bookmarksMenu, tool.getSelector() );

        result.add( bookmarksMenu );

        return result;
    }


    /** Creates new form PngWalkTool */
    public PngWalkTool1() {
        initComponents();
        dataSetSelector1.setEnableDataSource(false);
        dataSetSelector1.setAcceptPattern("(?i).*(\\.gif|\\.png|\\.jpg)");
        dataSetSelector1.setSuggestFiles(false); // only aggs.
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

        final int SCROLLBAR_HEIGHT = 20;

        views[1].setMinimumSize( new Dimension(100,100) );
        views[4].setMinimumSize( new Dimension(100,100) );
        views[3].setPreferredSize( new Dimension(640,480) );
        final JSplitPane p = new JSplitPane(JSplitPane.VERTICAL_SPLIT, views[1], views[2] );
        //p.setEnabled(false);  //prevents user manipulation
        p.setDividerLocation(getThumbnailSize()+ SCROLLBAR_HEIGHT);
        views[1].addPropertyChangeListener( PngWalkView.PROP_THUMBNAILSIZE, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                p.setDividerLocation( (Integer)evt.getNewValue() + SCROLLBAR_HEIGHT );
            }
        });
        
        final JSplitPane p2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, views[4], views[5] );
        p.setDividerLocation(getThumbnailSize()+ SCROLLBAR_HEIGHT);
        views[4].addPropertyChangeListener( PngWalkView.PROP_THUMBNAILSIZE, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                p2.setDividerLocation( (Integer)evt.getNewValue() + SCROLLBAR_HEIGHT  );
            }
        });


        tabs= new TearoffTabbedPane();

        tabs.addTab( "Single", new JScrollPane( views[3] ) );
        tabs.addTab( "ContextFlow", views[6] );
        tabs.addTab( "Grid", views[0] );
        tabs.addTab( "Film Strip", p );
        tabs.addTab( "Covers", p2 );

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
        if ( !show.equals("") ) {
            try {      
                if ( seq.getTimeSpan()!=null ) {
                    seq.gotoSubrange(DatumRangeUtil.parseTimeRange(show));
                } else {
                    this.pendingGoto= DatumRangeUtil.parseTimeRange(show);
                }
            } catch ( ParseException ex ) {
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * respond to changes of the current index.
     */
    private transient PropertyChangeListener indexListener= new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            String item= seq.currentImage().getUri().toString();

            for ( int i=0; i<actionEnablers.size(); i++ ) {
                boolean actionEnabled= actionEnablers.get(i).isActionEnabled(item);
                actionButtons.get(i).setEnabled(actionEnabled);
                if ( actionEnabled ) {
                   actionButtons.get(i).setActionCommand(actionCommand+" "+item);
                }
            }
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
            if ( seq.getQualityControlSequence()!=null ) {
                int n[] = seq.getQualityControlSequence().getQCTotals();
                qcPanel.setStatus(n[0], n[1], n[2], n[3]);
            }
        }
    };

    public void setTemplate( String template ) {

        if ( template.contains("$") && !template.contains("%") ) {
            template= template.replaceAll("\\$","%");
        }
        
        dataSetSelector1.setValue(template);

        WalkImageSequence oldseq= this.seq;

        String surl= DataSetURI.fromUri( DataSetURI.getResourceURI(template) );
        try {
            seq= new WalkImageSequence( surl );
        } catch ( Exception ex ) {
            seq= null;
            ex.printStackTrace();
        }

        if ( oldseq!=null ) {
            oldseq.removePropertyChangeListener(WalkImageSequence.PROP_INDEX, indexListener );
            oldseq.removePropertyChangeListener(WalkImageSequence.PROP_STATUS, statusListener);
            if (ENABLE_QUALITY_CONTROL) oldseq.removePropertyChangeListener(WalkImageSequence.PROP_BADGE_CHANGE, qcStatusListener);
        }
        if ( seq!=null ) {
            seq.addPropertyChangeListener( WalkImageSequence.PROP_INDEX, indexListener );
            seq.addPropertyChangeListener( WalkImageSequence.PROP_STATUS, statusListener );
            if (ENABLE_QUALITY_CONTROL) seq.addPropertyChangeListener(WalkImageSequence.PROP_BADGE_CHANGE, qcStatusListener);
        }

        Runnable run= new Runnable() {
            public void run() {
                try {
                    seq.initialLoad();

                    if ( pendingGoto!=null )  {
                        seq.gotoSubrange(pendingGoto);
                        pendingGoto= null;
                    }
                    
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
                        setStatus("warning: Done listing "+seq.getTemplate()+", and no files were found");
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
                } catch (java.io.IOException e) {
                    // This probably means the template was invalid. Don't set new sequence.
                    if ( !getStatus().startsWith("error") ) setStatus("error:"+e.getMessage());
                }
            }
        };

        new Thread(run).start();

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

    protected String status = "initializing...";
    public static final String PROP_STATUS = "status";

    public String getStatus() {
        return status;
    }

    public void setStatus(String message) {
        String oldStatus = this.status;
        this.status = message;
        if ( message.startsWith("busy:" ) ) {
            setMessage( BUSY_ICON, message.substring(5).trim() );
            logger.info(message);
        } else if ( message.startsWith("warning:" ) ) {
            setMessage( WARNING_ICON, message.substring(8).trim() );
            logger.warning(message);
        } else if ( message.startsWith("error:" ) ) {
            setMessage( ERROR_ICON, message.substring(6).trim() );
            logger.severe(message);
        } else {
            logger.info(message);
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

    public static interface ActionEnabler {
        boolean isActionEnabled( String filename );
    }

    public static final ActionEnabler LOCAL_FILE_ENABLER = new ActionEnabler() {
        public boolean isActionEnabled( String filename ) {
            return DataSetURI.getResourceURI(filename).toString().startsWith("file:" );
        }
    };

    List<ActionEnabler> actionEnablers= new ArrayList<ActionEnabler>();
    List<String> actionCommands= new ArrayList<String>();
    List<JButton> actionButtons= new ArrayList<JButton>();
    
    void addFileAction( ActionEnabler match, String actionCommand, Action abstractAction ) {
        this.actionEnablers.add( match );
        this.actionCommands.add( actionCommand );
        JButton b= new JButton( abstractAction );
        this.actionButtons.add( b );
        actionButtonsPanel.add( b );
    }

    String getSelectedFile() {
        return DataSetURI.fromUri( seq.currentImage().getUri() );
    }

    DataSetSelector getSelector() {
        return this.dataSetSelector1;
    }

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

        prevSetButton.setText("<<<");
        prevSetButton.setToolTipText("Skip 7");
        prevSetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                prevSetButtonActionPerformed(evt);
            }
        });

        prevButton.setText("<");
        prevButton.setToolTipText("previous");
        prevButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                prevButtonActionPerformed(evt);
            }
        });

        nextButton.setText(">");
        nextButton.setToolTipText("next");
        nextButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nextButtonActionPerformed(evt);
            }
        });

        nextSetButton.setText(">>>");
        nextSetButton.setToolTipText("Skip 7");
        nextSetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nextSetButtonActionPerformed(evt);
            }
        });

        jumpToFirstButton.setText("|<");
        jumpToFirstButton.setToolTipText("jump to first");
        jumpToFirstButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jumpToFirstButtonActionPerformed(evt);
            }
        });

        jumpToLastButton.setText(">|");
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
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(jumpToFirstButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(prevSetButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(prevButton)
                .add(139, 139, 139)
                .add(nextButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(nextSetButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jumpToLastButton))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                .add(prevButton)
                .add(nextButton)
                .add(nextSetButton)
                .add(prevSetButton)
                .add(jumpToFirstButton)
                .add(jumpToLastButton))
        );

        jPanel1Layout.linkSize(new java.awt.Component[] {nextButton, nextSetButton, prevButton, prevSetButton}, org.jdesktop.layout.GroupLayout.VERTICAL);

        dataSetSelector1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataSetSelector1ActionPerformed(evt);
            }
        });

        statusLabel.setText("starting application...");

        showMissingCheckBox.setSelected(true);
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
            .add(layout.createSequentialGroup()
                .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 30, Short.MAX_VALUE)
                .add(actionButtonsPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 463, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
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
                    .add(dataSetSelector1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 846, Short.MAX_VALUE))
                .addContainerGap())
            .add(layout.createSequentialGroup()
                .add(statusLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 858, Short.MAX_VALUE)
                .addContainerGap())
            .add(org.jdesktop.layout.GroupLayout.TRAILING, pngsPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 870, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(pngsPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 636, Short.MAX_VALUE)
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
                    .add(jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 30, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(statusLabel))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void nextButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nextButtonActionPerformed
        seq.skipBy( 1 );
}//GEN-LAST:event_nextButtonActionPerformed

    private void prevButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_prevButtonActionPerformed
        seq.skipBy( -1 );
    }//GEN-LAST:event_prevButtonActionPerformed

    private void nextSetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nextSetButtonActionPerformed
        seq.skipBy( 7 );
}//GEN-LAST:event_nextSetButtonActionPerformed

    private void prevSetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_prevSetButtonActionPerformed
        seq.skipBy( -7 );
}//GEN-LAST:event_prevSetButtonActionPerformed

    private void timeFilterTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeFilterTextFieldActionPerformed
        try {
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
        seq.last();
    }//GEN-LAST:event_jumpToLastButtonActionPerformed

    private void jumpToFirstButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jumpToFirstButtonActionPerformed
        seq.first();
    }//GEN-LAST:event_jumpToFirstButtonActionPerformed

    private void dataSetSelector1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSetSelector1ActionPerformed
        String t= dataSetSelector1.getValue();
        if ( t.endsWith(".pngwalk") ) {
            t= readPngwalkFile(t);
        }
        setTemplate( t );
    }//GEN-LAST:event_dataSetSelector1ActionPerformed

    private void showMissingCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_showMissingCheckBoxItemStateChanged
        seq.setShowMissing(evt.getStateChange()==java.awt.event.ItemEvent.SELECTED);
    }//GEN-LAST:event_showMissingCheckBoxItemStateChanged

    private void editRangeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editRangeButtonActionPerformed
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
