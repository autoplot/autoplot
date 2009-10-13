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

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import org.das2.components.TearoffTabbedPane;
import org.das2.datum.DatumRange;
import org.das2.util.ArgumentList;
import org.virbo.autoplot.bookmarks.Bookmark;
import org.virbo.datasource.DataSetSelector;
import org.xml.sax.SAXException;
import org.das2.util.TimeParser;
import org.das2.util.filesystem.FileSystem.FileSystemOfflineException;
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

    PngWalkView[] views;
    TearoffTabbedPane tabs;
    
    WalkImageSequence seq;
    
    Pattern actionMatch=null;
    String actionCommand=null;

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
            Preferences prefs = Preferences.userNodeForPackage(PngWalkTool.class);
            String srecent = prefs.get( PngWalkTool.PREF_RECENT,"");
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
            }
        } );
        result.add(fileMenu);

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

        Preferences prefs = Preferences.userNodeForPackage(PngWalkTool.class);
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

        views= new PngWalkView[4];

        views[0]= new GridPngWalkView( null );
        views[1]= new RowPngWalkView( null );
        views[2]= new SinglePngWalkView( null );
        views[3]= new SinglePngWalkView( null );

        //views[1].setMinimumSize( new Dimension(100,100) );
        JSplitPane p = new JSplitPane(JSplitPane.VERTICAL_SPLIT, views[1], views[2] );
        p.setDividerLocation((int)(views[1].getPreferredSize().getHeight()));
        tabs= new TearoffTabbedPane();

        tabs.addTab( "Grid", views[0] );
        tabs.addTab( "Film Strip", p );
        tabs.addTab( "Single", new JScrollPane( views[3] ) );

        tabs.setSelectedIndex(1);
        
        // add listener to jump to and from the single image view.
        for ( int i=0; i<views.length; i++ ) {
            views[i].addMouseListener( new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if ( e.getClickCount()==2 ) {
                        int oldIndex= tabs.getSelectedIndex();
                        if ( oldIndex==2 ) {
                            tabs.setSelectedIndex( returnTabIndex );
                        } else {
                            tabs.setSelectedIndex(2);
                            returnTabIndex= oldIndex;
                        }
                    }
                }
            });
        }

        pngsPanel.add( tabs );
        pngsPanel.revalidate();

    }

    public void setTemplate( String template ) {
        dataSetSelector1.setValue(template);
        seq= new WalkImageSequence( template );
        seq.addPropertyChangeListener( new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                String item= seq.currentImage().getUri().toString();

                if ( actionEnabler!=null ) {
                    boolean actionEnabled= actionEnabler.isActionEnabled(item);
                    addFileActionButton.setEnabled(actionEnabled);
                    if ( actionEnabled ) {
                        addFileActionButton.setActionCommand(actionCommand+" "+item);
                    }
                }
            }
        });

        for ( PngWalkView v:views ) {
            v.setSequence( seq );
        }
    }

    public String getTemplate() {
        return seq.getTemplate();
    }

    static interface ActionEnabler {
        boolean isActionEnabled( String filename );
    }

    ActionEnabler actionEnabler;

    void addFileAction( ActionEnabler match, String actionCommand, AbstractAction abstractAction ) {
        this.actionEnabler= match;
        this.actionCommand= actionCommand;
        addFileActionButton.setAction(abstractAction);
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
        jLabel1 = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        prevSetButton = new javax.swing.JButton();
        prevButton = new javax.swing.JButton();
        scaleComboBox = new javax.swing.JComboBox();
        nextButton = new javax.swing.JButton();
        nextSetButton = new javax.swing.JButton();
        addFileActionButton = new javax.swing.JButton();
        jumpToFirstButton = new javax.swing.JButton();
        jumpToLastButton = new javax.swing.JButton();
        dataSetSelector1 = new org.virbo.datasource.DataSetSelector();

        pngsPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        pngsPanel.setLayout(new java.awt.BorderLayout());

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

        jLabel1.setText("Display Only:");
        jLabel1.setToolTipText("Enter a time range, such as \"2009\" or \"May 2009\" to limit the images displayed.");

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

        scaleComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "1-Up", "7-Up", "35-Up", "CoverFlow" }));
        scaleComboBox.setSelectedIndex(1);
        scaleComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                scaleComboBoxActionPerformed(evt);
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

        addFileActionButton.setText("----");

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
                .addContainerGap()
                .add(jumpToFirstButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(prevSetButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(prevButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(scaleComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 115, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(18, 18, 18)
                .add(nextButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(nextSetButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jumpToLastButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 337, Short.MAX_VALUE)
                .add(addFileActionButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 122, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                .add(prevButton)
                .add(nextButton)
                .add(nextSetButton)
                .add(scaleComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(prevSetButton)
                .add(addFileActionButton)
                .add(jumpToFirstButton)
                .add(jumpToLastButton))
        );

        jPanel1Layout.linkSize(new java.awt.Component[] {addFileActionButton, nextButton, nextSetButton, prevButton, prevSetButton, scaleComboBox}, org.jdesktop.layout.GroupLayout.VERTICAL);

        dataSetSelector1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataSetSelector1ActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(24, 24, 24)
                .add(jLabel1)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(timeFilterTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 236, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(458, Short.MAX_VALUE))
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(dataSetSelector1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 788, Short.MAX_VALUE)
                .addContainerGap())
            .add(org.jdesktop.layout.GroupLayout.TRAILING, pngsPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 812, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .add(pngsPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 421, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(dataSetSelector1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel1)
                    .add(timeFilterTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(12, 12, 12)
                .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void scaleComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_scaleComboBoxActionPerformed
        nextSetButton.setEnabled(true);
        prevSetButton.setEnabled(true);
        nextSetButton.setText(">>>");
        prevSetButton.setText("<<<");
        switch ( scaleComboBox.getSelectedIndex() ) {
            case 0:
                nextSetButton.setEnabled(false);
                prevSetButton.setEnabled(false);
                break;
            case 1:
                nextSetButton.setText("skip 7 >>>");
                prevSetButton.setText("<<< back 7");
                break;
            case 2:
                nextSetButton.setText("skip 35 >>>");
                prevSetButton.setText("<<< back 35");
                break;
            case 3:
                nextSetButton.setText("skip 7 >>>");
                prevSetButton.setText("<<< back 7");
                break;
            default:
                throw new IllegalArgumentException("bad index");

        }
    }//GEN-LAST:event_scaleComboBoxActionPerformed

    private void nextButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nextButtonActionPerformed
        seq.setIndex( seq.getIndex() + 1 );
}//GEN-LAST:event_nextButtonActionPerformed

    private void prevButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_prevButtonActionPerformed
        seq.setIndex( seq.getIndex()  - 1 );
    }//GEN-LAST:event_prevButtonActionPerformed

    private void nextSetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nextSetButtonActionPerformed
        seq.setIndex( seq.getIndex() + 7 );
}//GEN-LAST:event_nextSetButtonActionPerformed

    private void prevSetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_prevSetButtonActionPerformed
        seq.setIndex( seq.getIndex()  - 7 );
}//GEN-LAST:event_prevSetButtonActionPerformed

    private void timeFilterTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeFilterTextFieldActionPerformed
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



    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addFileActionButton;
    private org.virbo.datasource.DataSetSelector dataSetSelector1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JButton jumpToFirstButton;
    private javax.swing.JButton jumpToLastButton;
    private javax.swing.JButton nextButton;
    private javax.swing.JButton nextSetButton;
    private javax.swing.JPanel pngsPanel;
    private javax.swing.JButton prevButton;
    private javax.swing.JButton prevSetButton;
    private javax.swing.JComboBox scaleComboBox;
    private javax.swing.JTextField timeFilterTextField;
    // End of variables declaration//GEN-END:variables

}
