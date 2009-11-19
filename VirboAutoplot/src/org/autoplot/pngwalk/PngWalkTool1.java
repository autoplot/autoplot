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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.Action;
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
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.das2.components.TearoffTabbedPane;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.util.ArgumentList;
import org.virbo.autoplot.bookmarks.Bookmark;
import org.virbo.datasource.DataSetSelector;
import org.xml.sax.SAXException;
import org.das2.util.TimeParser;
import org.das2.util.filesystem.FileSystem.FileSystemOfflineException;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Binding;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.virbo.autoplot.AutoPlotUI;
import org.virbo.autoplot.ScriptContext;
import org.virbo.autoplot.bookmarks.BookmarksManager;
import org.virbo.autoplot.bookmarks.BookmarksManagerModel;
import org.virbo.datasource.DataSetURI;

/**
 *
 * @author jbf
 */
public class PngWalkTool1 extends javax.swing.JPanel {
    public static final String PREF_RECENT = "pngWalkRecent";

    public PngWalkView[] views;
    TearoffTabbedPane tabs;
    
    WalkImageSequence seq;
    
    Pattern actionMatch=null;
    String actionCommand=null;

    static Logger logger= Logger.getLogger("org.autoplot.pngwalk");
    private static String RESOURCES= "/org/virbo/autoplot/resources/";
    public static final Icon WARNING_ICON= new ImageIcon( AutoPlotUI.class.getResource(RESOURCES+"warning-icon.png") );
    public static final Icon ERROR_ICON= new ImageIcon( AutoPlotUI.class.getResource(RESOURCES+"error-icon.png") );
    public static final Icon BUSY_ICON= new ImageIcon( AutoPlotUI.class.getResource(RESOURCES+"spinner.gif") );
    public static final Icon READY_ICON= new ImageIcon( AutoPlotUI.class.getResource(RESOURCES+"indProgress0.png") );
    public static final Icon IDLE_ICON= new ImageIcon( AutoPlotUI.class.getResource(RESOURCES+"idle-icon.png") );
        
    int returnTabIndex=0; // index of the tab we left to look at the single panel view.  TODO: account for tear off.


    public static void main(String[] args) {

        DataSetURI.init();  // FtpFileSystem implementation

        System.err.println("this is pngwalk 20091007");
        final ArgumentList alm = new ArgumentList("AutoPlotUI");
        alm.addBooleanSwitchArgument("nativeLAF", "n", "nativeLAF", "use the system look and feel");
        alm.addOptionalPositionArgument(0, "template",  "file:/tmp/pngwalk/product_$Y$m$d.png", "initial template to use.");

        alm.process(args);

        if (alm.getBooleanValue("nativeLAF")) {
            try {
                javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        String template = alm.getValue("template"); // One Slash!!
        //final String template=  "file:/home/jbf/temp/product_$Y$m$d.png" ; // One Slash!!
        //final String template=  "file:/net/spot3/home/jbf/fun/pics/2001minnesota/.*JPG" ;
        //final String template= "file:/home/jbf/public_html/voyager/VGPW_0201/BROWSE/V1/.*.PNG";
        //final String template= "file:///net/spot3/home/jbf/fun/pics/20080315_tenerife_masca_hike/IMG_.*.JPG";
        //final String template= "http://www.swpc.noaa.gov/ftpdir/lists/hpi/plots/pmap_$Y_$m_$d_...._S_.*_.*_.*_.*.gif";

        start( template, null );

    }

    public static PngWalkTool1 start( String template, Window parent ) {

        final PngWalkTool1 tool = new PngWalkTool1();

        if ( template!=null ) {
            tool.setTemplate(template);
        } else {
            Preferences prefs = Preferences.userNodeForPackage(PngWalkTool1.class);
            String srecent = prefs.get( PngWalkTool1.PREF_RECENT,"");
            if ( srecent.equals("") ) {
                tool.setTemplate("file:/tmp/pngwalk/product_$Y$m$d.png");
            } else {
                try {
                    List<Bookmark> books = Bookmark.parseBookmarks(srecent);
                    tool.setTemplate( ((Bookmark.Item)books.get(0)).getUrl() );
                } catch (SAXException ex) {
                    Logger.getLogger(DemoPngWalk.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IOException ex) {
                    Logger.getLogger(DemoPngWalk.class.getName()).log(Level.SEVERE, null, ex);
                }
                tool.setTemplate("file:/tmp/pngwalk/product_$Y$m$d.png");
            }

        }

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

        final int op= parent==null ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE;

        tool.addFileAction( enabler, "autoplot", new AbstractAction("Launch Autoplot") {
            public void actionPerformed(ActionEvent e) {
                String s = tool.getSelectedFile();
                String template = tool.getTemplate();
                int i0 = template.indexOf("_$Y");
                if ( i0==-1 ) i0= template.indexOf("_%Y");
                int i1 = s.indexOf(".png");
                if ( i1==-1 ) return;
                TimeParser tp= TimeParser.create( template.substring(i0 + 1, i1) );
                String timeRange = s.substring(i0 + 1, i1);
                try {
                    DatumRange dr= tp.parse(timeRange).getTimeRange();
                    timeRange= dr.toString().replaceAll(" ", "+");
                } catch ( ParseException ex ) {
                    throw new RuntimeException(ex);
                }
                String productFile = template.substring(0, i0) + ".vap";

                final String suri = productFile + "?timeRange=" + timeRange;

                Runnable run = new Runnable() {
                    public void run() {
                        try {
                            ScriptContext.createGui();
                            ScriptContext.plot(suri);
                            ((JFrame)ScriptContext.getViewWindow()).setDefaultCloseOperation(op);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(DemoPngWalk.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                };
                new Thread(run).start();
            }
        });

        tool.addFileAction( PngWalkTool1.LOCAL_FILE_ENABLER, "problem", new AbstractAction("Problem...") {
            public void actionPerformed( ActionEvent e ) {
                String s = tool.getSelectedFile();
                String problemFile= s + ".problem";
                File pf;
                try {
                    pf = new File(new URI(problemFile));
                } catch (URISyntaxException ex) {
                    Logger.getLogger(PngWalkTool1.class.getName()).log(Level.SEVERE, null, ex);
                    return;
                }
                JTextArea ta= new JTextArea(10,50);
                if ( pf.exists() ) {
                    try {
                        String ss = WalkUtil.readFile(pf);
                        ta.setText(ss);
                    } catch (IOException ex) {
                        Logger.getLogger(PngWalkTool1.class.getName()).log(Level.SEVERE, null, ex);
                        throw new RuntimeException(ex);
                    }
                }

                if ( JOptionPane.showConfirmDialog( tool, new JScrollPane(ta), "Edit Problem File", JOptionPane.OK_CANCEL_OPTION )==JOptionPane.OK_OPTION ) {
                    try {
                        WalkUtil.writeFile(pf, ta.getText());
                    } catch (IOException ex) {
                        Logger.getLogger(PngWalkTool1.class.getName()).log(Level.SEVERE, null, ex);
                        throw new RuntimeException(ex);
                    }
                }
            }
        });

        tool.addFileAction( PngWalkTool1.LOCAL_FILE_ENABLER, "okay", new AbstractAction("Okay...") {
            public void actionPerformed( ActionEvent e ) {
                String s = tool.getSelectedFile();
                String problemFile= s + ".okay";
                File pf;
                try {
                    pf = new File(new URI(problemFile));
                } catch (URISyntaxException ex) {
                    Logger.getLogger(PngWalkTool1.class.getName()).log(Level.SEVERE, null, ex);
                    return;
                }
                JTextArea ta= new JTextArea(10,50);
                if ( pf.exists() ) {
                    try {
                        String ss = WalkUtil.readFile(pf);
                        ta.setText(ss);
                    } catch (IOException ex) {
                        Logger.getLogger(PngWalkTool1.class.getName()).log(Level.SEVERE, null, ex);
                        throw new RuntimeException(ex);
                    }
                }

                if ( JOptionPane.showConfirmDialog( tool, new JScrollPane(ta), "Edit Okay File", JOptionPane.OK_CANCEL_OPTION )==JOptionPane.OK_OPTION ) {
                    try {
                        WalkUtil.writeFile(pf, ta.getText());
                    } catch (IOException ex) {
                        Logger.getLogger(PngWalkTool1.class.getName()).log(Level.SEVERE, null, ex);
                        throw new RuntimeException(ex);
                    }
                }
            }
        });

        JFrame frame = new JFrame("PNG Walk Tool");

        if ( parent==null ) {
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        } else {
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        }

        frame.setJMenuBar( createMenuBar(tool,frame) );

        frame.getContentPane().add(tool);


        frame.pack();

        frame.setVisible(true);

        return tool;
    }

    private static JMenuBar createMenuBar( final PngWalkTool1 tool, final JFrame f ) {
        JMenuBar result= new JMenuBar();
        JMenu fileMenu= new JMenu("File");
        fileMenu.add( new AbstractAction( f.getDefaultCloseOperation()==JFrame.EXIT_ON_CLOSE ? "Exit" : "Close" ) {
            public void actionPerformed(ActionEvent e) {
                f.dispose();
                if (f.getDefaultCloseOperation()==JFrame.EXIT_ON_CLOSE) System.exit(0);
            }
        } );
        result.add(fileMenu);

        BindingGroup bg= new BindingGroup();

        final JMenu optionsMenu= new JMenu( "Options" );
        JCheckBoxMenuItem persMi= new JCheckBoxMenuItem("Use Perspective");
        bg.addBinding( Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, tool.views[4], BeanProperty.create("perspective"), persMi, BeanProperty.create("selected") ) );
        bg.bind();
        optionsMenu.add(persMi);

        final JMenu thumbsizeMenu= new JMenu("Thumbnail Size" );
        final int[] sizes= new int[] { 50, 100, 200, 400 };
        for ( int i=0; i<sizes.length; i++ ) {
            final int fsize= sizes[i];
            thumbsizeMenu.add( new AbstractAction(""+fsize+" px" ) {
               public void actionPerformed( ActionEvent e ) {
                  tool.setThumbnailSize(fsize);
               }
            });
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

        Preferences prefs = Preferences.userNodeForPackage(PngWalkTool1.class);
        String srecent = prefs.get(PREF_RECENT,"");

        if ( !srecent.equals("") ) {
            try {
                List<String> urls = new ArrayList<String>();
                List<Bookmark> recent = Bookmark.parseBookmarks(srecent);
                for (Bookmark b : recent) {
                    urls.add(((Bookmark.Item) b).getUrl());
                }
                dataSetSelector1.setRecent(urls);
                if (urls.size() > 1) {
                    dataSetSelector1.setValue(urls.get(urls.size() - 1));
                }
                dataSetSelector1.setRecent(urls);
            } catch (SAXException ex) {
                Logger.getLogger(PngWalkTool1.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(PngWalkTool1.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        dataSetSelector1.addPropertyChangeListener( DataSetSelector.PROP_RECENT, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                Preferences prefs = Preferences.userNodeForPackage(PngWalkTool1.class);
                List<String> srecent= dataSetSelector1.getRecent();
                List<Bookmark> recent = new ArrayList<Bookmark>();
                for ( String s : srecent ) {
                    recent.add( new Bookmark.Item( s ) );
                }
                prefs.put( PREF_RECENT, Bookmark.formatBooks( recent ) );
            }
        } );

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
        final JSplitPane p = new JSplitPane(JSplitPane.VERTICAL_SPLIT, views[1], views[2] );
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

        pngsPanel.add( tabs );
        pngsPanel.revalidate();

        BindingGroup bc= new BindingGroup();
        for ( int i=0; i<views.length; i++ ) {
            Binding b= Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, views[i],
                    BeanProperty.create("thumbnailSize"), this, BeanProperty.create("thumbnailSize") );
            bc.addBinding( b );
        }
        bc.bind();

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

    public void setTemplate( String template ) {
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
        }
        if ( seq!=null ) {
            seq.addPropertyChangeListener( WalkImageSequence.PROP_INDEX, indexListener );
            seq.addPropertyChangeListener( WalkImageSequence.PROP_STATUS, statusListener );
        }

        Runnable run= new Runnable() {
            public void run() {
                try {
                    seq.initialLoad();
                    useRangeCheckBox.setEnabled(seq.getTimeSpan() != null);

                    // always clear subrange on new sequence
                    useRangeCheckBox.setSelected(false);
                    editRangeButton.setEnabled(false);
                    timeFilterTextField.setEnabled(false);
                    timeFilterTextField.setText("");
                    
                    showMissingCheckBox.setEnabled(seq.getTimeSpan() != null);
                    for (PngWalkView v : views) {
                        v.setSequence(seq);
                    }
                } catch (java.io.IOException e) {
                    // This probably means the template was invalid. Don't set new sequence.
                    setStatus(e.getMessage());
                }
            }
        };

        new Thread(run).start();

    }

    public String getTemplate() {
        return seq.getTemplate();
    }

    protected int thumbnailSize = 200;
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
        return seq.currentImage().getUri().toString();
    }

    DataSetSelector getSelector() {
        return this.dataSetSelector1;
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
        prevSetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                prevSetButtonActionPerformed(evt);
            }
        });

        prevButton.setText("<");
        prevButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                prevButtonActionPerformed(evt);
            }
        });

        nextButton.setText(">");
        nextButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nextButtonActionPerformed(evt);
            }
        });

        nextSetButton.setText(">>>");
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
        showMissingCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                showMissingCheckBoxItemStateChanged(evt);
            }
        });

        useRangeCheckBox.setText("Limit range to:");
        useRangeCheckBox.setToolTipText("Limit the time range of the images in the sequence.");
        useRangeCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                useRangeCheckBoxItemStateChanged(evt);
            }
        });

        editRangeButton.setText("Select...");
        editRangeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editRangeButtonActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(statusLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 870, Short.MAX_VALUE)
            .add(layout.createSequentialGroup()
                .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(18, 18, 18)
                .add(actionButtonsPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 463, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
            .add(org.jdesktop.layout.GroupLayout.TRAILING, pngsPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 870, Short.MAX_VALUE)
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
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .add(pngsPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 539, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(dataSetSelector1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 27, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(timeFilterTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(useRangeCheckBox)
                    .add(editRangeButton)
                    .add(showMissingCheckBox))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 30, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(actionButtonsPanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 30, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(statusLabel))
        );

        layout.linkSize(new java.awt.Component[] {actionButtonsPanel, jPanel1}, org.jdesktop.layout.GroupLayout.VERTICAL);

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
        setTemplate( dataSetSelector1.getValue() );
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
        
        List<DatumRange> current = seq.getActiveSubrange();
        DatumRange range= DatumRangeUtil.union(current.get(0), current.get(current.size()-1));
        if ( range==null ) {
            timeFilterTextField.setText("error"); // shouldn't get here
        } else {
            timeFilterTextField.setText( range.toString() );
        }
    }//GEN-LAST:event_useRangeCheckBoxItemStateChanged



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
