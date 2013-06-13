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
import java.awt.EventQueue;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.das2.components.DasProgressPanel;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.system.RequestProcessor;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.cdf.CdfJavaDataSourceEditorPanel;
import org.virbo.datasource.AutoplotSettings;
import org.virbo.datasource.DataSourceEditorPanel;
import org.virbo.datasource.TimeRangeTool;
import org.virbo.datasource.URISplit;

/**
 *
 * @author jbf
 */
public class CDAWebEditorPanel extends javax.swing.JPanel implements DataSourceEditorPanel {

    private static final Logger logger= LoggerManager.getLogger("apdss.cdaweb");
    
    /** Creates new form CDAWebEditorPanel */
    public CDAWebEditorPanel() {
        initComponents();
        this.addComponentListener( new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                refresh(getURI());
            }
        });
    }

    public static final String PARAM_FILTER= "filter"; // for convenience, carry filter around

    CdfJavaDataSourceEditorPanel paramEditor;
    JComponent messageComponent=null;
    boolean haveAddedRecent= false;
    private static final String MSG_NO_DATASET = "<html><em>No dataset selected, pick initial dataset...</em></html>";
    private String currentDs= "";
    private String filter="";
    private String id="";

    private boolean pickDs() {
        Window win = SwingUtilities.getWindowAncestor(this);
        JFrame frame = null;
        if ( win!=null ) {
            Window own = win.getOwner();
            if (own instanceof JFrame) {
                frame = (JFrame) own;
            }
        }
        CDAWebDataSetIdDialog t = new CDAWebDataSetIdDialog(frame, true);
        t.setLocationRelativeTo(this);
        t.setTitle("Pick Dataset");
        t.setFilter(filter);
        t.refresh();
        t.setResizable(true);
        t.setVisible(true);
        if (t.isCancelled()) {
            return false;
        }
        filter= t.getFilter();
        dsidComboBox.setSelectedItem(t.getSelectedItem());
        final String uri = getURI();
        Runnable run= new Runnable() {
            public void run() {
                refresh(uri);
            }
        };
        new Thread(run).start();
        return true;
    }

    private void refreshDefaultTime( String ds, DatumRange dr ) {
        try {
            String timeDflt= CDAWebDB.getInstance().getSampleTime(ds);
            DatumRange tr= DatumRangeUtil.parseTimeRange( timeDflt );
            String str= timeRangeTextField.getText().trim();
            if ( !str.equals("") ) {
                try {
                    DatumRange tr1 = DatumRangeUtil.parseTimeRange(str);
                    DatumRange accept = DatumRangeUtil.union(dr, tr);
                    if (DatumRangeUtil.rescale(accept, -0.1, 1.1).intersects(tr1)) {
                        // fuzz up, because I found ACE/SWE data outside the valid range
                        tr = tr1;
                    }
                } catch (ParseException ex) {
                    availableTextField.setText(ex.toString());
                }
            }
            timeRangeTextField.setText(tr.toString());
        } catch (ParseException ex) {
            availableTextField.setText(ex.toString());
        } catch ( IOException ex ) {
            availableTextField.setText(ex.toString());
        }
    }

    /**
     * this should be called on the event thread.
     * @param ds
     * @param args
     * @throws Exception
     */
    private void refreshDataSet( CdfJavaDataSourceEditorPanel panel, final String ds, Map<String,String> args ) throws Exception {
        try {
            if ( ! CDAWebDB.getInstance().getServiceProviderIds().containsKey(ds) ) {
                messageComponent= new JLabel("<html>Service provider \""+ ds +"\" not found in ftp/cdaweb.gsfc.nasa.gov/pub/cdaweb/all.xml");
                descriptionLabel.setText("");
                timeRangeTextField.setText("");
                paramEditor= null;
                return;
            }
            String avail= CDAWebDB.getInstance().getTimeRange(ds);
            availableTextField.setText(avail);
            final DatumRange dr= DatumRangeUtil.parseTimeRange( avail );
            Runnable run= new Runnable() {
                public void run() {
                    refreshDefaultTime(ds,dr);
                }
            };
            RequestProcessor.invokeLater(run);

        } catch ( ParseException ex ) {
            availableTextField.setText(ex.toString());
        }

    }

    /**
     * Do not call from event thread.
     * @param ds null, or the current dataset.
     * @param args
     * @throws IOException
     * @throws Exception
     */
    private synchronized void doRefreshDataSet( final String ds, final Map<String,String> args ) throws IOException, Exception {

        Window w= SwingUtilities.getWindowAncestor(this);
        DasProgressPanel mon;
        if ( w==null ) {
            mon= DasProgressPanel.createFramed("getting master CDF");  //TODO: this message no longer appears
        } else {
            mon= DasProgressPanel.createFramed(w,"getting master CDF");
        }
        currentDs= ds;

        final String fmaster;
        if ( ds!=null && ds.length()>0 ) {
            String master= CDAWebDB.getInstance().getMasterFile(ds,mon); // do the download off the event thread.
            fmaster= master;
        } else {
            fmaster= null;
        }

        Runnable run= new Runnable() {
            public synchronized void run() {
                if ( ds!=null && ds.length()>0 ) {
                    try {
                        final CdfJavaDataSourceEditorPanel panel= new CdfJavaDataSourceEditorPanel();

                        String master= fmaster;

                        String id= args.get("id");
                        if ( id==null ) id= CDAWebEditorPanel.this.id; //kludge
                        if ( id!=null ) {
                            master= master + "?" + id;
                        }

                        boolean status;
                        status= panel.prepare( master, SwingUtilities.getWindowAncestor(CDAWebEditorPanel.this), new NullProgressMonitor() );
                        panel.setURI( master );

                        if ( !status ) {
                            messageComponent= new JLabel("<html>CDF file subpanel prepare method failed");
                            paramEditor= null;
                        } else {
//logger.fine( "messageComponent="+messageComponent );
                            if ( messageComponent!=null ) parameterPanel.remove(messageComponent);
                            parameterPanel.add( panel, BorderLayout.CENTER );

                            panel.setShowAdvancedSubpanel(false);
                            paramEditor= panel;
                            parameterPanel.revalidate();
                            messageComponent= null;
//logger.fine( " after count=" + parameterPanel.getComponentCount() );
                            refreshDataSet( panel, ds, args );
                        }
                    } catch ( Exception ex ) {
                        messageComponent= new JLabel("<html>Exception:<br>"+ex.toString().replaceAll("\n", "<br>") );
                        paramEditor= null;
                    }
                } else {
                    messageComponent= new JLabel(MSG_NO_DATASET);
                }
                if ( messageComponent!=null ) { // show message if necessary
                    parameterPanel.removeAll();
                    parameterPanel.add( messageComponent, BorderLayout.NORTH );
                }
                parameterPanel.revalidate();
            }
        };
        SwingUtilities.invokeLater(run);

    }

    public synchronized void refresh(String suri) {

        if ( EventQueue.isDispatchThread() ) {
            CDAWebDataSource.logger.warning("TODO: refresh should not be called from the event thread");
        }
        if ( !haveAddedRecent ) {
            addRecent();
            haveAddedRecent= true;
        }

        final String ds= (String) dsidComboBox.getSelectedItem();

        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                if ( paramEditor!=null ) parameterPanel.remove( paramEditor );
                if ( messageComponent!=null ) parameterPanel.remove( messageComponent );

                messageComponent= new JLabel("<html><em><br>&nbsp;Loading file...</em></html>"); // this causes problem when droplist is used.
                parameterPanel.add( messageComponent, BorderLayout.NORTH );
                parameterPanel.validate();
            }
        } );

        URISplit split= URISplit.parse(suri);
        final Map<String,String> args= URISplit.parseParams(split.params);

        //messageComponent= null;

        if ( ds!=null && ds.length()>0 ) {
            String desc= CDAWebDB.getInstance().getServiceProviderIds().get(ds);
            descriptionLabel.setText( "<html><small>"+desc+"</small></html>");
        } else {
            descriptionLabel.setText( "<html><small> </small></html>");
        }
        
        try {
            if ( ( ds==null || ds.length()==0 ) ? currentDs != null : true ) {
                doRefreshDataSet(ds,args);
            } else {
                messageComponent= new JLabel("<html><em><br>&nbsp;No dataset selected.</em></html>"); // this causes problem when droplist is used.
                parameterPanel.add( messageComponent, BorderLayout.NORTH );
                parameterPanel.validate();    
            }
        } catch ( Exception ex ) {
            ex.printStackTrace();
            final String msg= ex.toString();
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    if ( paramEditor!=null ) parameterPanel.remove( paramEditor );
                    if ( messageComponent!=null ) parameterPanel.remove( messageComponent );
                    messageComponent= new JLabel("<html><em><br>"+msg+"</em></html>");
                    parameterPanel.add( messageComponent, BorderLayout.NORTH );
                    parameterPanel.validate();
                }
            } );
        }
    }

    private void addRecent() {
        String val= (String) dsidComboBox.getSelectedItem();
        BufferedReader r = null;
        try {
            File home = new File(AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA));
            File book = new File(home, "bookmarks");
            File hist = new File(book, "history.txt");
            long t0= System.currentTimeMillis();
            logger.log(Level.FINE, "reading recent datasources from {0}", hist.toString());
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
            Object[] items= dss.toArray();
            Arrays.sort(items);
            dsidComboBox.setModel( new DefaultComboBoxModel( items ) );
            dsidComboBox.setSelectedItem(val);
            t0= System.currentTimeMillis() - t0 ;
            logger.log( Level.FINE, "done in {0} millis\n", t0 );
            
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
            dsidComboBox.setModel( new DefaultComboBoxModel( new String[] { "error in parsing history.txt" } ) );
        } finally {
            try {
                if ( r!=null ) r.close();
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
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
        jButton1 = new javax.swing.JButton();
        descriptionLabel = new javax.swing.JLabel();

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

        jButton1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/autoplot/cdaweb/calendar.png"))); // NOI18N
        jButton1.setToolTipText("Time Range Tool");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout timeRangePanelLayout = new org.jdesktop.layout.GroupLayout(timeRangePanel);
        timeRangePanel.setLayout(timeRangePanelLayout);
        timeRangePanelLayout.setHorizontalGroup(
            timeRangePanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(timeRangePanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(timeRangePanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(timeRangePanelLayout.createSequentialGroup()
                        .add(12, 12, 12)
                        .add(availableTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .add(timeRangePanelLayout.createSequentialGroup()
                        .add(jLabel3)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(timeRangeTextField)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jButton1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 25, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        timeRangePanelLayout.setVerticalGroup(
            timeRangePanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(timeRangePanelLayout.createSequentialGroup()
                .addContainerGap()
                .add(timeRangePanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(timeRangePanelLayout.createSequentialGroup()
                        .add(timeRangePanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(jLabel3)
                            .add(timeRangeTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                        .add(0, 0, Short.MAX_VALUE))
                    .add(jButton1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(availableTextField)
                .addContainerGap())
        );

        descriptionLabel.setText("Description of dataset goes here");

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(timeRangePanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(parameterPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(jLabel1)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(dsidComboBox, 0, 334, Short.MAX_VALUE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(pickDsButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 78, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(descriptionLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
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
                .add(descriptionLabel)
                .add(4, 4, 4)
                .add(parameterPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 195, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(timeRangePanel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void pickDsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pickDsButtonActionPerformed
        pickDs();
    }//GEN-LAST:event_pickDsButtonActionPerformed

    private void timeRangeTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeRangeTextFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_timeRangeTextFieldActionPerformed

    private void dsidComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dsidComboBoxActionPerformed
        parameterPanel.removeAll();
        messageComponent= new JLabel("<html><em>Resetting...</em></html>"); // this causes problem when droplist is used.
        parameterPanel.add( messageComponent, BorderLayout.NORTH );
        parameterPanel.revalidate();
        parameterPanel.repaint();
        Runnable run= new Runnable() {
            public void run() {
                refresh(getURI());
            }
        };
        RequestProcessor.invokeLater(run);
    }//GEN-LAST:event_dsidComboBoxActionPerformed

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        TimeRangeTool tt= new TimeRangeTool();
        JTextField tf= timeRangeTextField;
        tt.setSelectedRange(tf.getText());
        int r= JOptionPane.showConfirmDialog( this, tt, "Select Time Range", JOptionPane.OK_CANCEL_OPTION );
        if ( r==JOptionPane.OK_OPTION) {
            tf.setText(tt.getSelectedRange());
        }
    }//GEN-LAST:event_jButton1ActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel availableTextField;
    private javax.swing.JLabel descriptionLabel;
    private javax.swing.JComboBox dsidComboBox;
    private javax.swing.JButton jButton1;
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

    private void initialize( String uri ) {
        URISplit split= URISplit.parse(uri);
        Map<String,String> args= URISplit.parseParams(split.params);
        this.dsidComboBox.setSelectedItem( args.get( CDAWebDataSource.PARAM_DS ) );
        String timeRange= args.get( CDAWebDataSource.PARAM_TIMERANGE );
        if ( timeRange!=null ) this.timeRangeTextField.setText( timeRange.replaceAll("\\+", " " ) );

    }

    public void setURI(String uri) {
        initialize(uri);
        URISplit split= URISplit.parse(uri);
        Map<String,String> args= URISplit.parseParams(split.params);
        String filter1= args.get( CDAWebEditorPanel.PARAM_FILTER );
        if ( filter1!=null ) {
            this.filter= filter1;
        }
        this.id= args.get( "id" );
        
        if ( args.get( CDAWebDataSource.PARAM_DS )==null ) {
            Runnable run= new Runnable() {
                public void run() {
                    try {
                        while ( !CDAWebEditorPanel.this.isShowing() ) {
                            Thread.sleep(1000);
                        }
                    } catch (InterruptedException ex) {
                       logger.log(Level.SEVERE, null, ex);
                    }
                    SwingUtilities.invokeLater( new Runnable() {
                        public void run() {
                            messageComponent= new JLabel(MSG_NO_DATASET);
                            parameterPanel.removeAll();
                            parameterPanel.add( messageComponent, BorderLayout.NORTH );
                            parameterPanel.revalidate();
                            if ( !pickDs() ) {
                                //logger.fine("no dataset picked.");
                            }
                        }
                    } );
                }
            };
            new Thread( run ).start();
        }
        
    }

    public String getURI() {
        String id=null;
        String slice1= "";
        if ( paramEditor!=null ) {
            id= paramEditor.getURI();
            URISplit split= URISplit.parse(id);
            Map<String,String> args= URISplit.parseParams(split.params);
            id= args.get("arg_0");
            slice1= args.get("slice1");
        }
        if ( id!=null ) this.id= id;
        if ( id==null && this.id!=null ) id=this.id;
        if ( id==null ) id="";
        
        String timeRange= timeRangeTextField.getText();
        if ( timeRange==null ) { // TODO: I don't think this should ever be null.
            logger.warning("here the timeRange is null");
            timeRange= "";
        }
        
        timeRange= timeRange.replaceAll(" ", "+");

        String result= "vap+cdaweb:ds="+dsidComboBox.getSelectedItem()+"&id="+id;
        if ( filter.length()>0 ) {
            result+= "&filter="+filter;
        }
        if ( slice1!=null && slice1.length()>0 ) {
            result+= "&slice1="+slice1;
        }
        return result +"&timerange="+timeRange;
    }

    public boolean prepare(String uri, Window parent, ProgressMonitor mon) throws Exception {
        initialize(uri);

        try {
           CDAWebDB.getInstance().maybeRefresh(mon);
           refresh(uri);
        } catch ( IOException ex ) {
           throw ex;  // do nothing for now.
        }
        return true;
    }

    public boolean reject(String uri) throws Exception {
        return false;
    }

    private boolean expert= true;

    public void setExpertMode( boolean expert ) {
        this.expert= expert;
    }

    public void markProblems(List<String> problems) {

    }
}
