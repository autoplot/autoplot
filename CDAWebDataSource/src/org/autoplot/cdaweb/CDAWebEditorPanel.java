/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * CDAWebEditorPanel.java
 *
 * Created on Nov 19, 2010, 5:46:30 AM
 */

package org.autoplot.cdaweb;

import java.awt.BorderLayout;
import java.awt.Window;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.das2.components.DasProgressPanel;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.fsm.FileStorageModelNew;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.cdfdatasource.CdfDataSourceEditorPanel;
import org.virbo.datasource.AutoplotSettings;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSourceEditorPanel;
import org.virbo.datasource.URISplit;

/**
 *
 * @author jbf
 */
public class CDAWebEditorPanel extends javax.swing.JPanel implements DataSourceEditorPanel {

    /** Creates new form CDAWebEditorPanel */
    public CDAWebEditorPanel() {
        initComponents();
    }

    CdfDataSourceEditorPanel paramEditor;
    JComponent messageComponent=null;
    boolean haveAddedRecent= false;
    
    private void refreshDataSet( String ds, Map<String,String> args ) throws Exception {
        CdfDataSourceEditorPanel panel= new CdfDataSourceEditorPanel();

        String master= CDAWebDB.getInstance().getMasterFile(ds);

        String id= args.get("id");
        if ( id!=null ) {
            master= master + "?" + id;
        }
        panel.prepare( master, SwingUtilities.getWindowAncestor(this), DasProgressPanel.createFramed("getting master CDF") );
        panel.setURI( master );
        parameterPanel.add( panel, BorderLayout.CENTER );
        paramEditor= panel;

        try {
            String avail= CDAWebDB.getInstance().getTimeRange(ds);
            availableTextField.setText(avail);
            DatumRange dr= DatumRangeUtil.parseTimeRange( avail );
            String timeDflt= CDAWebDB.getInstance().getSampleTime(ds);
            DatumRange tr= DatumRangeUtil.parseTimeRange( timeDflt );
            String str= timeRangeTextField.getText().trim();
            if ( !str.equals("") ) {
                DatumRange tr1= DatumRangeUtil.parseTimeRange( str );
                if ( dr.intersects(tr1) ) {
                    tr= tr1;
                }
            }
            timeRangeTextField.setText(tr.toString());
        } catch ( ParseException ex ) {
            availableTextField.setText(ex.toString());
        }

    }

    public synchronized void refresh(String suri) {

        if ( !haveAddedRecent ) {
            addRecent();
            haveAddedRecent= true;
        }

        String ds= (String) dsidComboBox.getSelectedItem();
        if ( paramEditor!=null ) parameterPanel.remove( paramEditor );
        if ( messageComponent!=null ) parameterPanel.remove( messageComponent );

        URISplit split= URISplit.parse(suri);
        Map<String,String> args= URISplit.parseParams(split.params);

        if ( ds!=null ) {
            try {
                refreshDataSet( ds, args );
            } catch ( Exception ex ) {
                messageComponent= new JLabel("<html>Exception:<br>"+ex.toString().replaceAll("\n", "<br>") );
                parameterPanel.add( messageComponent, BorderLayout.NORTH );
                paramEditor= null;
            }
        } else {
            messageComponent= new JLabel("<html><em>No Data Set Selected</em></html>");
            parameterPanel.add( messageComponent, BorderLayout.NORTH );
        }
        parameterPanel.revalidate();
        
    }

    private void addRecent() {
        String val= (String) dsidComboBox.getSelectedItem();
        BufferedReader r = null;
        try {
            File home = new File(AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA));
            File book = new File(home, "bookmarks");
            File hist = new File(book, "history.txt");
            long t0= System.currentTimeMillis();
            System.err.println("reading recent datasources from " + hist.toString() );
            if ( !hist.exists() ) return;
            r = new BufferedReader(new FileReader(hist));
            String s = r.readLine();
            LinkedHashSet dss = new LinkedHashSet();
            while (s != null) {
                if ( s.length()>25 && s.substring(25).startsWith("vap+cdaweb")) {
                    int i = s.indexOf("ds=");
                    if (i != -1) {
                        int j = s.indexOf("&", i);
                        if (j == -1) {
                            j = s.length();
                        }
                        String ds = s.substring(i+3, j);
                        dss.add(ds);
                    }
                }
                s= r.readLine();
            }
            dsidComboBox.setModel( new DefaultComboBoxModel( dss.toArray() ) );
            dsidComboBox.setSelectedItem(val);
            t0= System.currentTimeMillis() - t0 ;
            System.err.printf("done in %d millis\n", t0 );
            
        } catch (IOException ex) {
            Logger.getLogger(CDAWebEditorPanel.class.getName()).log(Level.SEVERE, null, ex);
            dsidComboBox.setModel( new DefaultComboBoxModel( new String[] { "error in parsing history.txt" } ) );
        } finally {
            try {
                r.close();
            } catch (IOException ex) {
                Logger.getLogger(CDAWebEditorPanel.class.getName()).log(Level.SEVERE, null, ex);
            }
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

        jLabel1 = new javax.swing.JLabel();
        dsidComboBox = new javax.swing.JComboBox();
        pickDsButton = new javax.swing.JButton();
        parameterPanel = new javax.swing.JPanel();
        timeRangePanel = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        timeRangeTextField = new javax.swing.JTextField();
        availableTextField = new javax.swing.JLabel();

        jLabel1.setText("Dataset:");

        dsidComboBox.setEditable(true);
        dsidComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { " ", "recent items will go here" }));
        dsidComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dsidComboBoxActionPerformed(evt);
            }
        });

        pickDsButton.setText("Pick...");
        pickDsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pickDsButtonActionPerformed(evt);
            }
        });

        parameterPanel.setLayout(new java.awt.BorderLayout());

        jLabel3.setText("Time Range: ");

        timeRangeTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timeRangeTextFieldActionPerformed(evt);
            }
        });

        availableTextField.setText("data availability");

        org.jdesktop.layout.GroupLayout timeRangePanelLayout = new org.jdesktop.layout.GroupLayout(timeRangePanel);
        timeRangePanel.setLayout(timeRangePanelLayout);
        timeRangePanelLayout.setHorizontalGroup(
            timeRangePanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(timeRangePanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(timeRangePanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(timeRangePanelLayout.createSequentialGroup()
                        .add(12, 12, 12)
                        .add(availableTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 398, Short.MAX_VALUE))
                    .add(timeRangePanelLayout.createSequentialGroup()
                        .add(jLabel3)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(timeRangeTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 315, Short.MAX_VALUE)))
                .addContainerGap())
        );
        timeRangePanelLayout.setVerticalGroup(
            timeRangePanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(timeRangePanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(timeRangePanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel3)
                    .add(timeRangeTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(availableTextField)
                .addContainerGap())
        );

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(jLabel1)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(dsidComboBox, 0, 271, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(pickDsButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 66, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
            .add(parameterPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 434, Short.MAX_VALUE)
            .add(timeRangePanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel1)
                    .add(dsidComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(pickDsButton))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(parameterPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 216, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(timeRangePanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void pickDsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pickDsButtonActionPerformed
        Window win= SwingUtilities.getWindowAncestor(this);
        Window own= win.getOwner();

        JFrame frame=null;
        if ( own instanceof JFrame ) frame= (JFrame)own;
        CDAWebDataSetIdDialog t= new CDAWebDataSetIdDialog( frame, true );
        t.setLocationRelativeTo(this);
        
        t.setTitle("Pick Dataset");
        t.refresh();

        t.setResizable(true);
        t.setVisible(true);
        if (t.isCancelled()) {
            return;
        }

        dsidComboBox.setSelectedItem( t.getSelectedItem() );
        String uri= getURI();
        refresh(uri);
        
    }//GEN-LAST:event_pickDsButtonActionPerformed

    private void timeRangeTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeRangeTextFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_timeRangeTextFieldActionPerformed

    private void dsidComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dsidComboBoxActionPerformed
        refresh(getURI());
    }//GEN-LAST:event_dsidComboBoxActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel availableTextField;
    private javax.swing.JComboBox dsidComboBox;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JPanel parameterPanel;
    private javax.swing.JButton pickDsButton;
    private javax.swing.JPanel timeRangePanel;
    private javax.swing.JTextField timeRangeTextField;
    // End of variables declaration//GEN-END:variables

    public JPanel getPanel() {
        return this;
    }

    public void setURI(String uri) {
        URISplit split= URISplit.parse(uri);
        Map<String,String> args= URISplit.parseParams(split.params);
        this.dsidComboBox.setSelectedItem( args.get( CDAWebDataSource.PARAM_DS ) );
        String timeRange= args.get( CDAWebDataSource.PARAM_TIMERANGE );
        if ( timeRange!=null ) this.timeRangeTextField.setText( timeRange );
    }

    public String getURI() {
        String id=null;
        if ( paramEditor!=null ) {
            id= paramEditor.getURI();
            URISplit split= URISplit.parse(id);
            Map<String,String> args= URISplit.parseParams(split.params);
            id= args.get("arg_0");
        }
        if ( id==null ) id="";
        String timeRange= timeRangeTextField.getText();
        timeRange= timeRange.replaceAll(" ", "+");
        return "vap+cdaweb:ds="+dsidComboBox.getSelectedItem()+"&id="+id+"&timerange="+timeRange;
    }

    public boolean prepare(String uri, Window parent, ProgressMonitor mon) throws Exception {
        setURI(uri);

        CDAWebDB.getInstance().maybeRefresh(mon);
        refresh(uri);
       
        return true;
    }

    public boolean reject(String uri) throws Exception {
        return false;
    }

}
