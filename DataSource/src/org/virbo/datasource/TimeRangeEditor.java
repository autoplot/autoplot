/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * TimeRangeEditor.java
 *
 * Created on Jun 4, 2011, 9:33:57 AM
 */

package org.virbo.datasource;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.text.DefaultEditorKit;
import org.das2.DasApplication;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.UnitsUtil;
import org.virbo.datasource.ui.PromptComboBoxEditor;

/**
 *  
 * @author jbf
 */
public class TimeRangeEditor extends javax.swing.JPanel {

    public TimeRangeEditor() {
        initComponents();
        recentComboBox.setPreferenceNode("timerange");
        recentComboBox.setEditor( new PromptComboBoxEditor("Time range to view (e.g. 2010-01-01)") );
        recentComboBox.setToolTipText("Recently entered time ranges");
        ((JComponent)recentComboBox.getEditor().getEditorComponent()).setToolTipText("Time Range, right-click for examples");
        recentComboBox.addFocusListener( new FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent evt) {
                parseRange();
            }
        });
        jPanel1.add( recentComboBox, BorderLayout.CENTER );
        recentComboBox.addActionListener( new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                parseRange();
            }
        });

        recentComboBox.setVerifier( new RecentComboBox.InputVerifier() {
            public boolean verify(String text) {
                try {
                    DatumRangeUtil.parseTimeRange(text);
                    return true;
                } catch (ParseException e) {
                    return false;
                }
            }
        });

        revalidate();
        addMousePopupListener();
    }

    DatumRange range= DatumRangeUtil.parseTimeRangeValid( "2010-01-01" );

    public static final String PROP_USE_DOY= "useDoy";
    public static final String PROP_RANGE= "range";

    /**
     * use DOY instead of Y-M-D
     */
    boolean useDoy=false;

    public boolean isUseDoy() {
        return useDoy;
    }

    public void setUseDoy(boolean useDoy) {
        boolean old= this.useDoy;
        this.useDoy = useDoy;
        DatumRangeUtil.useDoy= useDoy;
        firePropertyChange( PROP_USE_DOY,old,useDoy);
    }

    public DatumRange getRange() {
        return range;
    }

    public void setRange( DatumRange value ) {
        DatumRange oldValue= this.range;
        this.range= value;
        if (oldValue != value && oldValue != null && !oldValue.equals(value)) {
            super.firePropertyChange( PROP_RANGE, oldValue, value);
        }
        if ( value==noOneListening ) {
            this.recentComboBox.setSelectedItem("");
        } else {
            this.recentComboBox.setSelectedItem( value.toString() );
        }
    }

    DatumRange noOneListening= range;

    /**
     * special marker object indicates that the "no one listening" message should be shown.
     * @param dr
     */
    public void setNoOneListeningRange( DatumRange dr ) {
        this.noOneListening= dr;
    }

    private void parseRange() {
        DatumRange dr;
        DatumRange value= this.range;

        String text= (String)recentComboBox.getSelectedItem();
        if ( text.equals("") ) return;
        
        try {
            String rangeString= text;
            dr= DatumRangeUtil.parseTimeRange(rangeString);
            setRange(dr);
        } catch ( ParseException e ) {
            //timeRangeTextField.setText( range.toString() ); // I think we can just leave the value there.
            if ( UnitsUtil.isTimeLocation(value.getUnits()) ) { // go ahead and handle non-times.
                showErrorUsage( text, "<html>" +e.getMessage() );
            } else {
                //showErrorUsage( text, "unable to parse range" );
            }
        } catch ( IllegalArgumentException e ) {
            if ( value!=null ) {
                setRange( value ); // cause reformat of old Datum
                if ( e.getMessage().contains("min > max") ) {
                    showErrorUsage( text, "min cannot be greater than max" );
                } else {
                    showErrorUsage( text, e.getMessage() );
                }
            }
        }

        return;
    }

    /**
     * prevent displaying same message so many times...
     */
    private String lastErrorText= null;
    private long lastErrorTime= 0;

    private void showErrorUsage( String text, String why ) {
        if ( !DasApplication.getDefaultApplication().isHeadless() ) {
            if ( text!=null && text.equals(lastErrorText)
                    && (System.currentTimeMillis()-lastErrorTime)<5000 ) {
                return;
            }
            if ( why!=null ) {
                JOptionPane.showMessageDialog( this, "<html>Unable to accept \""+text+"\"<br>"+why+"<html>" );
            } else {
                JOptionPane.showMessageDialog( this, "<html>Unable to accept \""+text+"\"</html>" );
            }
            lastErrorText= text;
            lastErrorTime= System.currentTimeMillis();
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        prevButton = new javax.swing.JButton();
        nextButton = new javax.swing.JButton();
        browseButton = new javax.swing.JButton();
        jPanel1 = new javax.swing.JPanel();
        recentComboBox = new org.virbo.datasource.RecentComboBox();

        setPreferredSize(new java.awt.Dimension(384, 39));

        prevButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/virbo/datasource/prevPrev.png"))); // NOI18N
        prevButton.setToolTipText("Scan to the previous interval");
        prevButton.setMaximumSize(new java.awt.Dimension(34, 20));
        prevButton.setMinimumSize(new java.awt.Dimension(34, 20));
        prevButton.setPreferredSize(new java.awt.Dimension(34, 20));
        prevButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                prevButtonActionPerformed(evt);
            }
        });

        nextButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/virbo/datasource/nextNext.png"))); // NOI18N
        nextButton.setToolTipText("Scan to the next interval");
        nextButton.setMaximumSize(new java.awt.Dimension(34, 24));
        nextButton.setMinimumSize(new java.awt.Dimension(34, 24));
        nextButton.setPreferredSize(new java.awt.Dimension(34, 24));
        nextButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nextButtonActionPerformed(evt);
            }
        });

        browseButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/virbo/datasource/fileMag.png"))); // NOI18N
        browseButton.setToolTipText("Edit data source");
        browseButton.setEnabled(false);
        browseButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        browseButton.setMaximumSize(new java.awt.Dimension(20, 20));
        browseButton.setMinimumSize(new java.awt.Dimension(20, 20));
        browseButton.setPreferredSize(new java.awt.Dimension(20, 20));
        browseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseButtonActionPerformed(evt);
            }
        });

        jPanel1.setLayout(new java.awt.BorderLayout());

        recentComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        jPanel1.add(recentComboBox, java.awt.BorderLayout.CENTER);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 256, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(prevButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 40, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(nextButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 34, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(browseButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 30, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
        );

        layout.linkSize(new java.awt.Component[] {nextButton, prevButton}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE, false)
                .add(browseButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 27, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(nextButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING, false)
                .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(org.jdesktop.layout.GroupLayout.LEADING, prevButton, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        layout.linkSize(new java.awt.Component[] {browseButton, nextButton, prevButton}, org.jdesktop.layout.GroupLayout.VERTICAL);

    }// </editor-fold>//GEN-END:initComponents

    private void nextButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nextButtonActionPerformed
        setRange( range.next() );
    }//GEN-LAST:event_nextButtonActionPerformed

    private void prevButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_prevButtonActionPerformed
        setRange( range.previous() );
    }//GEN-LAST:event_prevButtonActionPerformed

    DataSetSelector peer;

    public void setDataSetSelectorPeer( DataSetSelector peer ) {
        this.peer= peer;
        browseButton.setEnabled(true);
    }

    private void browseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browseButtonActionPerformed
        if ( peer!=null ) {
            String surl = ((String) peer.getEditor().getText()).trim();//TODO:check

            boolean wasRejected= false;
            DataSourceEditorPanel edit = null;
            try {
                edit = DataSourceEditorPanelUtil.getDataSourceEditorPanel(DataSetURI.getURIValid(surl));
                if ( edit!=null && edit.reject(surl) ) {
                    edit= null;
                    wasRejected= true;
                }
            } catch (URISyntaxException ex) {
                Logger.getLogger(DataSetSelector.class.getName()).log(Level.SEVERE, null, ex);
                edit= null;
            } catch ( Exception ex ) {
                Logger.getLogger(DataSetSelector.class.getName()).log(Level.SEVERE, null, ex);
                edit= null;
            }
            if ( edit==null ) {
                JOptionPane.showMessageDialog( prevButton, "this type has no editor", "no editor", JOptionPane.OK_OPTION );
            } else {
                peer.browseSourceType();
            }
        }
    }//GEN-LAST:event_browseButtonActionPerformed

    public static void main( String[] args ) {
        TimeRangeEditor p= new TimeRangeEditor();
        p.addPropertyChangeListener( p.PROP_RANGE, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                System.err.println(evt.getOldValue()+" -> "+ evt.getNewValue() );
            }

        } );
        JFrame f= new JFrame();
        f.setContentPane( p );
        f.pack();
        f.setVisible(true);
        f.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton browseButton;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JButton nextButton;
    private javax.swing.JButton prevButton;
    private org.virbo.datasource.RecentComboBox recentComboBox;
    // End of variables declaration//GEN-END:variables

    public PropertyChangeListener getUriFocusListener() {
        return new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                browseButton.setToolTipText( "<html>Edit data source<br>"+evt.getNewValue().toString()+"</html>" );
            }
        };
    }

    private String alternatePeer;
    private String alternatePeerCard;

    public void setAlternatePeer(String label, String card ) {
        this.alternatePeer= label;
        this.alternatePeerCard= card;
    }

    
    private void addMousePopupListener() {
        recentComboBox.getEditor().getEditorComponent().addMouseListener( new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                if ( e.isPopupTrigger() ) showPopup(e);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if ( e.isPopupTrigger() ) showPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if ( e.isPopupTrigger() ) showPopup(e);
            }

        });

    }
    private void showPopup( MouseEvent e ) {
        getPopupMenu().show( this, e.getX(), e.getY() );
    }

    private JMenuItem exampleTime( final String s, final String toolTip ) {
        JMenuItem mi= new JMenuItem( new AbstractAction(s) {
            public void actionPerformed( ActionEvent e ) {
                recentComboBox.setSelectedItem(s);
            }
        });
        mi.setToolTipText(toolTip);
        return mi;
    }

    private JPopupMenu getPopupMenu() {
        JPopupMenu result= new JPopupMenu();
        JMenuItem cutItem = result.add(new DefaultEditorKit.CutAction());
        cutItem.setText("Cut");
        JMenuItem copyItem = result.add(new DefaultEditorKit.CopyAction());
        copyItem.setText("Copy");
        JMenuItem pasteItem = result.add(new DefaultEditorKit.PasteAction());
        pasteItem.setText("Paste");

        JMenu examplesMenu= new JMenu("Examples");
        examplesMenu.add( exampleTime( "2010 Jan", "Month of January" ) );
        examplesMenu.add( exampleTime( "2010-01-01", "January 1, 2010" ) );
        examplesMenu.add( exampleTime( "2010-01-01/2010-01-04", "ISO8601 range" ) );
        examplesMenu.add( exampleTime( "P5D", "Last 5 Days to now" ) );
        examplesMenu.add( exampleTime( "orbit:rbspa-pp:30", "Orbit 30 from rbspa-pp orbits file" ) );
        examplesMenu.add( exampleTime( "orbit:http://das2.org/wiki/index.php/Orbits/rbspa-pp:30", "Orbit 30 from any orbits file" ) );

        result.add( examplesMenu );
        
        if ( this.alternatePeerCard!=null ) {
            result.add( new JSeparator() );
            result.add( new AbstractAction( alternatePeer ) {
                public void actionPerformed(ActionEvent ev) {
                    Container trp= TimeRangeEditor.this.getParent();
                    ((CardLayout)trp.getLayout()).show( trp, alternatePeerCard );
                }
            } );
        }


        return result;

    }


}
