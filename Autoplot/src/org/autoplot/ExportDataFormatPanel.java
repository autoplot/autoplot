
package org.autoplot;

import java.awt.Component;
import java.awt.event.ItemEvent;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSourceFormat;
import org.autoplot.datasource.DataSourceFormatEditorPanel;
import org.autoplot.datasource.DataSourceRegistry;
import org.autoplot.datasource.URISplit;
import org.das2.qds.QDataSet;
import org.das2.util.LoggerManager;

/**
 * Extract just the GUI that is used to specify how a dataset will be exported.
 * This will surely be confused with the ExportDataPanel, which also specifies
 * which dataset should be used.
 * @author jbf
 */
public class ExportDataFormatPanel extends javax.swing.JPanel {

    private static final Logger logger= LoggerManager.getLogger("autoplot.export");
    /**
     * Creates new form ExportDataFormatPanel
     */
    public ExportDataFormatPanel() {
        initComponents();
    }

    private QDataSet dataSet = null;

    public static final String PROP_DATASET = "dataSet";

    public QDataSet getDataSet() {
        return dataSet;
    }

    public void setDataSet(QDataSet dataset) {
        QDataSet oldDataset = this.dataSet;
        this.dataSet = dataset;
        
        List<String> exts = DataSourceRegistry.getInstance().getFormatterExtensions();
        Collections.sort(exts);
        
        List<String> newExts= new ArrayList<>(exts.size());
        
        for ( String s: exts ) {
            DataSourceFormat dsf= DataSourceRegistry.getInstance().getFormatByExt(s);
            if ( dataset==null || dsf.canFormat(dataset) ) {
                newExts.add(s);
            }
        }
        
        exts= newExts;
        
        formatDL.setModel( new DefaultComboBoxModel(exts.toArray()) );
        formatDL.setRenderer( new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                String ext= String.valueOf(value);
                DataSourceFormat format= DataSourceRegistry.getInstance().getFormatByExt(ext);
                Component parent= super.getListCellRendererComponent( list, value, index, isSelected, cellHasFocus );
                if ( parent instanceof JLabel ) {
                    if ( format!=null ) {
                        ((JLabel)parent).setText( value.toString() + " " + format.getDescription() );
                    }
                }
                return parent;
            }
        });
        
        Preferences prefs= AutoplotSettings.settings().getPreferences(AutoplotUI.class);
        String currentFileString = prefs.get("ExportDataCurrentFile", "");
        String currentExtString = prefs.get("ExportDataCurrentExt", ".txt");
        if ( !currentExtString.equals("") && exts.contains(currentExtString) ) {
            formatDL.setSelectedItem(currentExtString);
        }        
        if ( !currentFileString.equals("") ) {
            URISplit split= URISplit.parse(currentFileString);
            filenameTF.setText(split.file);
            formatDL.setSelectedItem( "." + split.ext );
            if ( currentFileString.contains("/") ) {
                setFile( currentFileString );
                if ( split.params!=null && editorPanel!=null ) {
                    editorPanel.setURI(currentFileString);
                }
            }
        }
        
        firePropertyChange(PROP_DATASET, oldDataset, dataset);
    }
    
    public String getURI() {
        URISplit split;
        if ( editorPanel!=null ) {
            split= URISplit.parse(editorPanel.getURI());
        } else {
            split= URISplit.parse(filenameTF.getText());
        }
        split.file= filenameTF.getText();
        String ext= formatDL.getSelectedItem().toString();
        if ( !split.file.endsWith(ext) ) {
            split.file+= ext;
        }
        String result= URISplit.format(split);
        Preferences prefs= AutoplotSettings.settings().getPreferences(AutoplotUI.class);
        prefs.put("ExportDataCurrentFile", result );
        prefs.put("ExportDataCurrentExt", ext );
        return result;
    }
    
    DataSourceFormatEditorPanel editorPanel=null;
    
    private void setFile(String currentFileString) {
        URISplit split= URISplit.parse(currentFileString);
        this.filenameTF.setText(split.file);
        formatDLActionPerformed(null);
        if ( editorPanel!=null && currentFileString.startsWith("file:") ) {
            editorPanel.setURI(currentFileString);
        }
        try {
            updateEditorPanel( split.ext, true );
        } catch ( RuntimeException ex ) {
            
        }
    }
    
    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this
     * code. The content of this method is always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        filenameTF = new javax.swing.JTextField();
        chooseFileB = new javax.swing.JButton();
        additionalOptionsButton = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();
        formatDL = new javax.swing.JComboBox();
        jLabel3 = new javax.swing.JLabel();

        filenameTF.setText("data");

        chooseFileB.setText("Select...");
        chooseFileB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chooseFileBActionPerformed(evt);
            }
        });

        additionalOptionsButton.setText("Additional options for output format...");
        additionalOptionsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                additionalOptionsButtonActionPerformed(evt);
            }
        });

        jLabel2.setText("Select Output Format:");

        formatDL.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        formatDL.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                formatDLItemStateChanged(evt);
            }
        });
        formatDL.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                formatDLActionPerformed(evt);
            }
        });

        jLabel3.setText("Filename:");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(12, 12, 12)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(formatDL, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(filenameTF)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(chooseFileB))))
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel3)
                    .addComponent(additionalOptionsButton)
                    .addComponent(jLabel2))
                .addGap(0, 151, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(formatDL, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(filenameTF, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(chooseFileB))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(additionalOptionsButton))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void chooseFileBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chooseFileBActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);

        JFileChooser chooser = new JFileChooser();

        List<String> exts = DataSourceRegistry.getInstance().getFormatterExtensions();
        FileFilter deflt = null;
        for (String ext : exts) {
            final String ex = ext;

            FileFilter ff = new FileFilter() {

                @Override
                public boolean accept(File f) {
                    return f.getName().endsWith(ex) || f.isDirectory();
                }

                @Override
                public String getDescription() {
                    return "*" + ex; // DANGER: this is parsed below
                }
            };
            if (ext.equals(".qds")) {
                deflt = ff;
            }
            chooser.addChoosableFileFilter(ff);
        }

        chooser.setFileFilter(deflt);
        String deft= filenameTF.getText();
        try {
            URL url = new URL( URISplit.parse(deft).file );
            chooser.setSelectedFile( new File( url.getFile() ) );
        } catch ( MalformedURLException e ) {
            logger.log( Level.WARNING, null, e );
        }

        int r = chooser.showSaveDialog(this);
        if (r == JFileChooser.APPROVE_OPTION) {
            String s = chooser.getSelectedFile().toString();

            String ext = DataSetURI.getExt(s);
            if (ext == null) {
                ext = "";
            }

            DataSourceFormat format = DataSourceRegistry.getInstance().getFormatByExt(ext);
            if (format == null) {
                if (chooser.getFileFilter().getDescription().startsWith("*.")) {
                    ext = chooser.getFileFilter().getDescription().substring(1);
                    format = DataSourceRegistry.getInstance().getFormatByExt(ext);  // OKAY: we're just verifying that we can use this.
                    if (format == null) {
                        JOptionPane.showMessageDialog(this, "No formatter for extension: " + ext);
                        return;
                    } else {
                        s = s + ext;
                    }
                } else {
                    JOptionPane.showMessageDialog(this, "No formatter for extension: " + ext);
                    return;
                }
            }
            filenameTF.setText( s );
            formatDL.setSelectedItem("."+ext);

        }
    }//GEN-LAST:event_chooseFileBActionPerformed

    /**
     * update the editor options popup.
     * @param ss the extension, passed to DataSourceRegistry.getInstance().getDataSourceFormatEditorByExt(ss), etc.
     * @param updateProcessing update the subset, etc.
     */
    private void updateEditorPanel( String ss, boolean updateProcessing ) {
        Object oeditorPanel= DataSourceRegistry.getInstance().getDataSourceFormatEditorByExt(ss);
        if ( oeditorPanel!=null ) {
            if ( oeditorPanel instanceof String ) {
                editorPanel= (DataSourceFormatEditorPanel)DataSourceRegistry.getInstanceFromClassName( (String)oeditorPanel );
                if ( editorPanel==null ) throw new IllegalArgumentException("unable to create instance: "+oeditorPanel);
            } else {
                editorPanel= (DataSourceFormatEditorPanel)oeditorPanel;
            }
            String t=  filenameTF.getText();
            if ( t.contains("/" ) && t.startsWith("file:") ) {
                editorPanel.setURI( t );
            }
        } else {
            editorPanel= null;
        }

        if (  updateProcessing ) {
            additionalOptionsButton.setEnabled( editorPanel!=null );
        }
        
    }

    
    private void additionalOptionsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_additionalOptionsButtonActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);
        javax.swing.JPanel j= editorPanel.getPanel();
        AutoplotUtil.showConfirmDialog( this, j, "Additional Options", JOptionPane.OK_CANCEL_OPTION );
    }//GEN-LAST:event_additionalOptionsButtonActionPerformed

    private void formatDLItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_formatDLItemStateChanged
        String ss=  (String) evt.getItem();
        if ( evt.getStateChange()==ItemEvent.DESELECTED ) {
            String s= filenameTF.getText();
            if ( s.endsWith(ss) ) {
                filenameTF.setText(s.substring(0,s.length()-ss.length()));
            }
        }
        updateEditorPanel(ss,evt.getStateChange()==ItemEvent.SELECTED);
    }//GEN-LAST:event_formatDLItemStateChanged

    private void formatDLActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_formatDLActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);

        String ff= filenameTF.getText();
        if ( ! ff.endsWith( (String)formatDL.getSelectedItem() ) ) {
            int ii2= ff.lastIndexOf('/');
            if ( ii2==-1 ) ii2= 0;
            int ii= ff.lastIndexOf('.');
            if ( ii>-1 && ii>ii2 ) {
                filenameTF.setText(ff.substring(0,ii) + formatDL.getSelectedItem() );
            }
        }
    }//GEN-LAST:event_formatDLActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton additionalOptionsButton;
    private javax.swing.JButton chooseFileB;
    private javax.swing.JTextField filenameTF;
    private javax.swing.JComboBox formatDL;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    // End of variables declaration//GEN-END:variables
}
