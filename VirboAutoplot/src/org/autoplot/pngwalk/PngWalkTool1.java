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

import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import org.das2.components.TearoffTabbedPane;
import org.virbo.autoplot.bookmarks.Bookmark;
import org.virbo.datasource.DataSetSelector;
import org.xml.sax.SAXException;

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

    public static void main( String[] args ) {
        JFrame parent= new JFrame("PNG Walk Tool 1");

        PngWalkTool1 r= new PngWalkTool1();

        r.setTemplate( "file:///tmp/pngwalk/*.png" );
        
        parent.getContentPane().add(r);

        parent.pack();
        parent.setVisible(true);
        parent.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

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

        views= new PngWalkView[4];

        views[0]= new GridPngWalkView( null );
        views[1]= new RowPngWalkView( null );
        views[2]= new SinglePngWalkView( null );
        views[3]= new SinglePngWalkView( null );

        //views[1].setMinimumSize( new Dimension(100,100) );
        JSplitPane p = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane( views[1] ), views[2] );
        p.setDividerLocation((int)(views[1].getPreferredSize().getHeight()));
        tabs= new TearoffTabbedPane();

        tabs.addTab( "Grid", new JScrollPane( views[0] ) );
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
