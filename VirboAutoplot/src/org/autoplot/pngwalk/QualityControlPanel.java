/*
 * QualityControlPanel.java
 *
 * Created on Apr 20, 2010, 9:43:06 AM
 */

package org.autoplot.pngwalk;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractButton;
import javax.swing.JFrame;
import javax.swing.JRadioButton;
import org.das2.util.filesystem.FileObject;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.KeyChain;
import org.das2.util.monitor.CancelledOperationException;
import org.virbo.datasource.DataSetURI;

/**
 *
 * @author ed
 */
public class QualityControlPanel extends javax.swing.JPanel {
    private JRadioButton nullRadioButton;
    private QualityControlRecord qcRecord;
    public static final String KEY_QUALITY_CONTROL_URI = "QualityControlURI";

    private static final Logger logger= Logger.getLogger("autoplot.pngwalk");
    
    /** Creates new form QualityControlPanel */
    public QualityControlPanel() {
        initComponents();

        // Since we're supporting JRE 1.5, we can't use ButtonGroup.clearSelection()
        // 1.5 and earlier don't allow you to clear a button group, so we hack it with an invisible button
        nullRadioButton = new JRadioButton();
        statusButtonGroup.add(nullRadioButton);  //add to button group, but not to UI
        nullRadioButton.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if ( qcRecord==null || e.getStateChange() == ItemEvent.SELECTED ) {
                    okButton.setEnabled(false);
                } else {
                    okButton.setEnabled(true);
                }
            }
        });
        statusButtonGroup.setSelected(nullRadioButton.getModel(), true);
        
    }

    transient PropertyChangeListener pc= new PropertyChangeListener() {

        public void propertyChange(PropertyChangeEvent evt) {
            if ( evt.getPropertyName().equals( WalkImageSequence.PROP_BADGE_CHANGE ) ) {
                int i= (Integer) evt.getNewValue();
                if ( i==walkImageSequence.getIndex() ) {
                    displayRecord( walkImageSequence.getQualityControlSequence().getQualityControlRecord(i) );
                }
            }
        }
        
    };

    /** Load the contents of the provided record into the UI.  Future UI changes
     * will affect the new record rather than any that was displayed before.
     * @param rec
     */
    public void displayRecord(QualityControlRecord rec) {
        if ( rec==null ) {
            previousCommentEditorPane.setText("");
            newCommentTextArea.setText("");
            statusButtonGroup.setSelected(okRadioButton.getModel(), false);
            setStatus( 0,0,0,0 );
            qcRecord= null;
            return;
        }
        previousCommentEditorPane.setText(rec.getCommentsHTML());
        newCommentTextArea.setText(rec.getNewCommentText());
        switch(rec.getStatus()) {
            case OK:
                statusButtonGroup.setSelected(okRadioButton.getModel(), true);
                break;
            case PROBLEM:
                statusButtonGroup.setSelected(problemRadioButton.getModel(), true);
                break;
            case IGNORE:
                statusButtonGroup.setSelected(ignoreRadioButton.getModel(), true);
                break;
            default:  //catches Status.UNKNOWN
                statusButtonGroup.setSelected(nullRadioButton.getModel(), true);
                break;
        }
        QualityControlSequence seq= walkImageSequence.getQualityControlSequence();
        int[] t= seq.getQCTotals();
        setStatus( t[0], t[1], t[2], t[3] );

        qcRecord = rec;
    }

    public void setStatus(int numOK, int numProblem, int numIgnore, int numUnknown) {

        String statustxt = String.format("%d OK | %d Prob | %d Ign | %d Unknown", numOK, numProblem, numIgnore, numUnknown);

        statusLabel.setText(statustxt);
        statusLabel.setToolTipText(statustxt);
    }

    /**
     * read sequence.properties and initialize.
     */
    private void initQualitySequeuce() {
        {
            InputStream in = null;
            try {
                Properties sequenceProperties;
                sequenceProperties = new Properties();
                String template= walkImageSequence.getTemplate();
                int i= WalkUtil.splitIndex(template);
                String path= template.substring(0,i);
                URI fsRoot = DataSetURI.getResourceURI(path);
                FileSystem tfs = FileSystem.create(fsRoot);
                FileObject propsFile = tfs.getFileObject("sequence.properties");
                if (propsFile.exists()) {
                    in = propsFile.getInputStream();
                    sequenceProperties.load(in);
                    in.close();
                }
                walkImageSequence.setQCFolder( DataSetURI.getResourceURI(sequenceProperties.getProperty(KEY_QUALITY_CONTROL_URI, path)) );

                for ( Enumeration<AbstractButton> e= statusButtonGroup.getElements(); e.hasMoreElements(); ) {
                    e.nextElement().setEnabled(false);
                }
                okButton.setEnabled(false);
                loginButton.setEnabled(true);
                
            } catch (FileNotFoundException ex) {
                logger.log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            } finally {
                try {
                    if ( in!=null ) in.close();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
        }

    }
    private void login() {
        if ( walkImageSequence.getQualityControlSequence()==null ) {
            URI uri= walkImageSequence.getQCFolder();
            try {
                URI uris = KeyChain.getDefault().resolveUserInfo(uri);
                walkImageSequence.initQualitySequence(uris);
                for ( Enumeration<AbstractButton> e= statusButtonGroup.getElements(); e.hasMoreElements(); ) {
                    e.nextElement().setEnabled(true);
                }
                okButton.setEnabled(true);
                loginButton.setEnabled(false);

            } catch (CancelledOperationException ex) {
                return;
            }
        }
    }
//
//    else if (e.getPropertyName().equals(WalkImage.PROP_BADGE_CHANGE)) {
//            switch( (QualityControlRecord.Status)e.getOldValue()) {
//                case OK:
//                    qcOK--;
//                    break;
//                case PROBLEM:
//                    qcProb--;
//                    break;
//                case IGNORE:
//                    qcIgn--;
//                    break;
//                case UNKNOWN:
//                    qcUnknown--;
//                    break;
//            }
//
//            switch( (QualityControlRecord.Status)e.getNewValue()) {
//                case OK:
//                    qcOK++;
//                    break;
//                case PROBLEM:
//                    qcProb++;
//                    break;
//                case IGNORE:
//                    qcIgn++;
//                    break;
//                case UNKNOWN:
//                    qcUnknown++;
//                    break;
//            }
//            pcs.firePropertyChange(PROP_BADGE_CHANGE, -1, displayImages.indexOf(e.getSource()));
//        }

    /* This is temporary for testing */
    public void setPreviousCommentText(String txt) {
        String htxt = "<html><body>" + txt + "<hr/></body></html>";
        previousCommentEditorPane.setText(htxt);
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        statusButtonGroup = new javax.swing.ButtonGroup();
        jLabel2 = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        okRadioButton = new javax.swing.JRadioButton();
        problemRadioButton = new javax.swing.JRadioButton();
        ignoreRadioButton = new javax.swing.JRadioButton();
        okButton = new javax.swing.JButton();
        loginButton = new javax.swing.JButton();
        sequencePropertiesHost = new javax.swing.JTextField();
        commentSplitPane = new javax.swing.JSplitPane();
        jScrollPane1 = new javax.swing.JScrollPane();
        previousCommentEditorPane = new javax.swing.JEditorPane();
        jPanel2 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        newCommentTextArea = new javax.swing.JTextArea();
        jPanel3 = new javax.swing.JPanel();
        statusLabel = new javax.swing.JLabel();

        jLabel2.setText("jLabel2");

        jPanel1.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        statusButtonGroup.add(okRadioButton);
        okRadioButton.setSelected(true);
        okRadioButton.setText("OK");
        okRadioButton.setToolTipText("Submit for further processing");
        okRadioButton.setEnabled(false);
        okRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okRadioButtonActionPerformed(evt);
            }
        });

        statusButtonGroup.add(problemRadioButton);
        problemRadioButton.setText("Problem");
        problemRadioButton.setToolTipText("Send back for reprocessing");
        problemRadioButton.setEnabled(false);
        problemRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                problemRadioButtonActionPerformed(evt);
            }
        });

        statusButtonGroup.add(ignoreRadioButton);
        ignoreRadioButton.setText("Ignore");
        ignoreRadioButton.setToolTipText("Do nothing further");
        ignoreRadioButton.setEnabled(false);
        ignoreRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ignoreRadioButtonActionPerformed(evt);
            }
        });

        okButton.setText("Save");
        okButton.setToolTipText("Save the record to disk");
        okButton.setEnabled(false);
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        loginButton.setText("Log In...");
        loginButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loginButtonActionPerformed(evt);
            }
        });

        sequencePropertiesHost.setEditable(false);
        sequencePropertiesHost.setFont(sequencePropertiesHost.getFont().deriveFont((sequencePropertiesHost.getFont().getStyle() | java.awt.Font.ITALIC), sequencePropertiesHost.getFont().getSize()-2));
        sequencePropertiesHost.setText("reading sequence.properties...");
        sequencePropertiesHost.setToolTipText("reading sequence.properties...");

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(okRadioButton)
                    .add(problemRadioButton)
                    .add(ignoreRadioButton))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 150, Short.MAX_VALUE)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(okButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 69, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(loginButton))
                .addContainerGap())
            .add(sequencePropertiesHost, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 320, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(sequencePropertiesHost, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(okButton)
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(okRadioButton)
                            .add(loginButton))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(problemRadioButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(ignoreRadioButton)))
                .addContainerGap(19, Short.MAX_VALUE))
        );

        commentSplitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        commentSplitPane.setResizeWeight(1.0);
        commentSplitPane.setContinuousLayout(true);

        previousCommentEditorPane.setContentType("text/html");
        previousCommentEditorPane.setEditable(false);
        // Set HTML renderer to use java system default font instead of Times New Roman
        java.awt.Font font = javax.swing.UIManager.getFont("Label.font");
        String bodyRule = "body { font-family: " + font.getFamily() + "; " +
        "font-size: " + font.getSize() + "pt; }";
        ((javax.swing.text.html.HTMLDocument)previousCommentEditorPane.getDocument()).getStyleSheet().addRule(bodyRule);
        jScrollPane1.setViewportView(previousCommentEditorPane);

        commentSplitPane.setTopComponent(jScrollPane1);

        jLabel1.setText("Enter your comments below:");

        newCommentTextArea.setColumns(20);
        newCommentTextArea.setLineWrap(true);
        newCommentTextArea.setWrapStyleWord(true);
        jScrollPane2.setViewportView(newCommentTextArea);

        org.jdesktop.layout.GroupLayout jPanel2Layout = new org.jdesktop.layout.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jScrollPane2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 324, Short.MAX_VALUE)
            .add(jPanel2Layout.createSequentialGroup()
                .add(jLabel1)
                .addContainerGap(141, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .add(jLabel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 17, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jScrollPane2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 130, Short.MAX_VALUE))
        );

        commentSplitPane.setRightComponent(jPanel2);

        jPanel3.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.LOWERED));
        jPanel3.setAutoscrolls(true);

        statusLabel.setText("Waiting for status count...");
        statusLabel.setMaximumSize(new java.awt.Dimension(1000, 17));
        statusLabel.setMinimumSize(new java.awt.Dimension(50, 17));

        org.jdesktop.layout.GroupLayout jPanel3Layout = new org.jdesktop.layout.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(statusLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 320, Short.MAX_VALUE)
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(statusLabel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
        );

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(commentSplitPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 324, Short.MAX_VALUE)
            .add(jPanel3, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(commentSplitPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel3, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void okRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okRadioButtonActionPerformed

    }//GEN-LAST:event_okRadioButtonActionPerformed

    private void problemRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_problemRadioButtonActionPerformed

    }//GEN-LAST:event_problemRadioButtonActionPerformed

    private void ignoreRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ignoreRadioButtonActionPerformed

    }//GEN-LAST:event_ignoreRadioButtonActionPerformed

    protected WalkImageSequence walkImageSequence = null;

    public synchronized WalkImageSequence getWalkImageSequece() {
        return walkImageSequence;
    }

    public synchronized void setWalkImageSequece(final WalkImageSequence walkImageSequence) {
        if ( this.walkImageSequence!=null ) {
            this.walkImageSequence.removePropertyChangeListener(pc);
        }
        if ( walkImageSequence==null ) {
            loginButton.setToolTipText( "" );
        } else {
            loginButton.setToolTipText( "Log in to site" );
            loginButton.setEnabled(true);
        }

        this.walkImageSequence = walkImageSequence;
        if ( walkImageSequence!=null ) {
            walkImageSequence.addPropertyChangeListener( pc );
        }
        displayRecord(null);

        sequencePropertiesHost.setText("reading sequence.properties...");
        sequencePropertiesHost.setToolTipText("");

        if ( walkImageSequence!=null ) {
            loginButton.setEnabled(false);
            Runnable run= new Runnable() {
                public void run() {
                    initQualitySequeuce();
                    URI uri= walkImageSequence.getQCFolder();
                    sequencePropertiesHost.setText(uri.toString());
                    sequencePropertiesHost.setToolTipText(uri.toString());
                    loginButton.setEnabled(true);
                    if ( uri.getScheme().equals("file") ) {  // log in automatically if it's not restricted
                       login();
                    }
                }
            };
            new Thread( run ).start();
        }
        
    }

    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        String text=  newCommentTextArea.getText();
        if ( ignoreRadioButton.isSelected() ) {
            qcRecord.setStatus(QualityControlRecord.Status.IGNORE);
        } else if ( problemRadioButton.isSelected() ) {
            qcRecord.setStatus(QualityControlRecord.Status.PROBLEM);
        } else if ( okRadioButton.isSelected() ) {
            qcRecord.setStatus(QualityControlRecord.Status.OK);
        }
        qcRecord.setNewCommentText(System.getProperty("user.name"), text );
        qcRecord.save();
        // re-initialize record display with updated content
        walkImageSequence.getQualityControlSequence().refreshQCTotals();
        displayRecord(qcRecord);
    }//GEN-LAST:event_okButtonActionPerformed

    private void loginButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loginButtonActionPerformed
        login();
    }//GEN-LAST:event_loginButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JSplitPane commentSplitPane;
    private javax.swing.JRadioButton ignoreRadioButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JButton loginButton;
    private javax.swing.JTextArea newCommentTextArea;
    private javax.swing.JButton okButton;
    private javax.swing.JRadioButton okRadioButton;
    private javax.swing.JEditorPane previousCommentEditorPane;
    private javax.swing.JRadioButton problemRadioButton;
    private javax.swing.JTextField sequencePropertiesHost;
    private javax.swing.ButtonGroup statusButtonGroup;
    private javax.swing.JLabel statusLabel;
    // End of variables declaration//GEN-END:variables

    public static void main(String[] args) {
        JFrame f = new JFrame();
        f.add(new QualityControlPanel());
        f.pack();
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setVisible(true);
    }
}
