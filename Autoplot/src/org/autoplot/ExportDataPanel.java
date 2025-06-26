
/*
 * ExportDataPanel.java
 *
 * Created on Feb 2, 2010, 9:03:23 AM
 */
package org.autoplot;

import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.filechooser.FileFilter;
import org.autoplot.datasource.AutoplotSettings;
import org.das2.util.LoggerManager;
import org.autoplot.dom.Application;
import org.autoplot.dom.DataSourceController;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFormat;
import org.autoplot.datasource.DataSourceFormatEditorPanel;
import org.autoplot.datasource.DataSourceRegistry;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.datasource.FileSystemUtil;
import org.autoplot.datasource.URISplit;
import org.das2.components.DasProgressPanel;
import org.das2.util.monitor.AlertNullProgressMonitor;

/**
 * GUI for specifying how data should be output to a file.
 * @author jbf
 */
public class ExportDataPanel extends javax.swing.JPanel {

    private QDataSet originalDataSet;
    private QDataSet processedDataSet;
    
    private static final Logger logger= LoggerManager.getLogger("autoplot.export");
            
    /** Creates new form ExportDataPanel */
    public ExportDataPanel() {
        initComponents();
        warningMessageLabel.setText(" "); // clients may call setTsb, which will reset this.
    }

    /**
     * create an Action to export the data from the data source.  It is assumed that
     * the data source will be a trivial data source wrapping some DataSet.  Only
     * getDataSet with a null monitor is called.
     * @param parent the component providing the context for the operation.
     * @param source the source of the data, only getDataSet is called.
     * @return 
     */
    public static Action createExportDataAction( final Component parent, final DataSource source ) {
        return new AbstractAction("Export Data...") {
            @Override
            public void actionPerformed(ActionEvent e) {
                org.das2.util.LoggerManager.logGuiEvent(e);
                final QDataSet ds;
                try {
                    ds = source.getDataSet( new AlertNullProgressMonitor("retrieve data") );
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, null, ex);
                    return;
                }
                ExportDataPanel edp= new ExportDataPanel();
                Preferences prefs= AutoplotSettings.settings().getPreferences(AutoplotUI.class);
                String currentFileString = prefs.get("ExportDataCurrentFile", "");
                String currentExtString = prefs.get("ExportDataCurrentExt", ".txt");
                if ( !currentExtString.equals("") ) {
                    edp.getFormatDL().setSelectedItem(currentExtString);
                }
                if ( !currentFileString.equals("") ) {
                    URISplit split= URISplit.parse(currentFileString);
                    edp.getFilenameTF().setText(split.file);
                    edp.getFormatDL().setSelectedItem( "." + split.ext );
                    if ( currentFileString.contains("/") && ( currentFileString.startsWith("file:") || currentFileString.startsWith("/") ) ) {
                        edp.setFile( currentFileString );
                        if ( split.params!=null && edp.getDataSourceFormatEditorPanel()!=null ) {
                            edp.getDataSourceFormatEditorPanel().setURI(currentFileString);
                        }
                    }
                }                                
                edp.setDataSet(ds);
                if ( AutoplotUtil.showConfirmDialog2( parent, edp, "Export Data", JOptionPane.OK_CANCEL_OPTION )==JOptionPane.OK_OPTION ) {
                    final String opts= edp.getDataSourceFormatEditorPanel()==null ? null : edp.getDataSourceFormatEditorPanel().getURI();            
                    String name= edp.getFilename();
                    if ( opts!=null ) {
                        URISplit splitopts= URISplit.parse(opts); //TODO: it's a shame that we have repeat code, see GuiSupport.java line 676.
                        if ( splitopts.params!=null && splitopts.params.length()==0 ) {
                            splitopts.params= null;
                        }
                        URISplit splits= URISplit.parse(edp.getFilename());
                        splitopts.file= splits.file;
                        String s= URISplit.format(splitopts); 
                        name= DataSourceUtil.unescape(s);
                    }
                    String ext= edp.getExtension();
                    final DataSourceFormat format = DataSourceRegistry.getInstance().getFormatByExt(ext); //OKAY
                    if (format == null) {
                        JOptionPane.showMessageDialog(parent, "No formatter for extension: " + ext);
                        return;
                    }
                    final String f= name;
                    prefs.put("ExportDataCurrentFile", name );
                    prefs.put("ExportDataCurrentExt", ext );
                    try {
                        format.formatData( f, ds, DasProgressPanel.createFramed("export slice data") );
                        JPanel panel= new JPanel();
                        panel.setLayout( new BoxLayout( panel, BoxLayout.Y_AXIS ) );
                        panel.add( new JLabel( "<html>Data formatted to<br>" + f ) );
                        panel.add( new JButton( new AbstractAction("Copy filename to clipboard") {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                StringSelection stringSelection = new StringSelection( f );
                                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                                clipboard.setContents(stringSelection, new ClipboardOwner() {
                                    @Override
                                    public void lostOwnership(Clipboard clipboard, Transferable contents) {
                                    }
                                } );
                            }
                        } ) );
                        JOptionPane.showMessageDialog(parent, panel );
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(parent, "Exception while formatting: " + ex.getMessage() );
                    }
                }
            }
        };
    }

    public void setDataSet(Application model) {
        DataSourceController dsc = model.getController().getDataSourceFilter().getController();

        originalDataSet= dsc.getFillDataSet();
        if ( originalDataSet!= null) {
            String name = (String) originalDataSet.property(QDataSet.NAME);
            if ( name==null ) name= "data";
            String f= new File(".").getAbsoluteFile().getParent();
            f= f.replaceAll("\\\\", "/");
            f= f+"/"+name.toLowerCase();
            filenameTF.setText(f);
            originalDataB.setToolTipText( String.format( "<html>%s<br>%s</html>", originalDataB.getToolTipText(), originalDataSet ) );
        }
        
        processedDataSet= model.getController().getPlotElement().getController().getDataSet();
        if ( processedDataSet!=null ) {
            if ( !processedDataSet.equals(originalDataSet ) ) {
                processedDataB.setToolTipText( String.format( "<html>%s<br>%s</html>", processedDataB.getToolTipText(), processedDataSet ) );
            } else {
                processedDataB.setToolTipText( String.format( "<html>%s</html>", "No processing is done to the dataset before plotting" ) );
                processedDataB.setEnabled(false);
            }
        } else {
            processedDataB.setEnabled(false);
        }
    }
    
    /**
     * set the dataset to export.
     * @param ds the dataset.
     */
    public void setDataSet( QDataSet ds ) {
        originalDataSet= ds;
        processedDataB.setEnabled(false);
        processedWithinXRangeB.setEnabled(false);
        
        List<String> exts = DataSourceRegistry.getInstance().getFormatterExtensions();
        Collections.sort(exts);
        getFormatDL().setModel( new DefaultComboBoxModel(exts.toArray()) );
        getFormatDL().setRenderer( new DefaultListCellRenderer() {
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
        if ( !currentExtString.equals("") ) {
            getFormatDL().setSelectedItem(currentExtString);
        }        
        if ( !currentFileString.equals("") ) {
            URISplit split= URISplit.parse(currentFileString);
            getFilenameTF().setText(split.file);
            getFormatDL().setSelectedItem( "." + split.ext );
            if ( currentFileString.contains("/") ) {
                setFile( currentFileString );
                if ( split.params!=null && getDataSourceFormatEditorPanel()!=null ) {
                    getDataSourceFormatEditorPanel().setURI(currentFileString);
                }
            }
        }
        
    }

    /**
     * return the filename selected by the GUI.  This will append the correct 
     * extension.
     * @return the filename.
     */
    public String getFilename() {
        String name = getFilenameTF().getText();
        String ext = (String) getFormatDL().getSelectedItem();

        List<String> exts = DataSourceRegistry.getInstance().getFormatterExtensions();

        if (name.startsWith("file://")) {
            name = name.substring(7);
        }
        if (name.startsWith("file:")) {
            name = name.substring(5);
        }
        String osName = System.getProperty("os.name", "applet");
        if (osName.startsWith("Windows") && name.startsWith("/") && name.length() > 3 && name.charAt(2) == ':') {
            name = name.substring(1); // Windows gets file:///c:/foo.wav
        }

        URISplit split = URISplit.parse(name);
        if (split.file == null) {
            try {
                name = new File(FileSystemUtil.getPresentWorkingDirectory(), name).toString();
                split = URISplit.parse(name);
                if (split.file == null) {
                    throw new IllegalArgumentException("Can't use this filename: " + name);
                }
            } catch (IOException ex) {
                Logger.getLogger(ExportDataPanel.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        if (!split.file.endsWith(ext)) {
            String s = split.file;
            boolean addExt = true;
            for (String ext1 : exts) {
                if (s.endsWith(ext1)) {
                    addExt = false;
                    ext = ext1;
                }
            }
            if (addExt) {
                split.file = s + ext;
            }
            //name= URISplit.format(split);
        }
        
        // mimic JChooser logic.
        String s1= split.file;
        if ( s1.startsWith("file://") ) {
            s1= s1.substring(7);
        }
        if ( s1.startsWith("file:") ) {
            s1= s1.substring(5);
        }

        Preferences prefs= AutoplotSettings.settings().getPreferences(AutoplotUI.class);
        prefs.put("ExportDataCurrentFile", s1 );
        prefs.put("ExportDataCurrentExt", ext );
        
        return s1;
    }
    
    /**
     * return the extension selected
     * @return the extension selected
     */
    public String getExtension() {
        return (String)getFormatDL().getSelectedItem();
    }
    
    /**
     * return the processed data within the plot element.
     * @return 
     */
    public boolean isFormatPlotElement() {
        return processedDataB.isSelected() && processedWithinXRangeB.isEnabled();
    }

    /**
     * format the visible data, trimming to xaxis bounds.
     * @return
     */
    public boolean isFormatPlotElementAndTrim() {
        return processedWithinXRangeB.isSelected() && processedWithinXRangeB.isEnabled();
    }
    
    /**
     * format the original data for the plot element.
     * @return
     */    
    public boolean isOriginalData() {
        return originalDataB.isSelected() && originalDataB.isEnabled();
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        jLabel2 = new javax.swing.JLabel();
        processedDataB = new javax.swing.JRadioButton();
        originalDataB = new javax.swing.JRadioButton();
        formatDL = new javax.swing.JComboBox();
        jLabel1 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        filenameTF = new javax.swing.JTextField();
        chooseFileB = new javax.swing.JButton();
        additionalOptionsButton = new javax.swing.JButton();
        processedWithinXRangeB = new javax.swing.JRadioButton();
        jPanel1 = new javax.swing.JPanel();
        jLabel4 = new javax.swing.JLabel();
        warningMessageLabel = new javax.swing.JLabel();

        jLabel2.setText("Select Output Format:");

        buttonGroup1.add(processedDataB);
        processedDataB.setText("Processed Data or Component");
        processedDataB.setToolTipText("Data as displayed, including slice and other operations.  This is the loaded data, and may extend past plot boundaries.\n");

        buttonGroup1.add(originalDataB);
        originalDataB.setSelected(true);
        originalDataB.setText("Original Data");
        originalDataB.setToolTipText("Data read in from data source");

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

        jLabel1.setText("Select Data to Export:");

        jLabel3.setText("Filename:");

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

        buttonGroup1.add(processedWithinXRangeB);
        processedWithinXRangeB.setText("Processed Data within X Axis Range");
        processedWithinXRangeB.setToolTipText("Processed data, but also trim to the data to the X axis bounds.\n");

        jLabel4.setText("<html><i>Data from the selected plot element can be exported to a format by data sources that provide a method to export data.");
        jLabel4.setVerticalAlignment(javax.swing.SwingConstants.TOP);

        warningMessageLabel.setText("<html><i>(warning message)");

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jLabel4, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
            .add(warningMessageLabel)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(jLabel4, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 51, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(warningMessageLabel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(12, 12, 12)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(formatDL, 0, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(layout.createSequentialGroup()
                        .add(filenameTF)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(chooseFileB))))
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .addContainerGap()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(originalDataB)
                            .add(processedDataB)
                            .add(processedWithinXRangeB)))
                    .add(jLabel3)
                    .add(additionalOptionsButton)
                    .add(jLabel2)
                    .add(jLabel1))
                .add(0, 258, Short.MAX_VALUE))
            .add(jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jLabel1)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(originalDataB)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(processedDataB)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(processedWithinXRangeB)
                .add(18, 18, 18)
                .add(jLabel2)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(formatDL, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 33, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jLabel3)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(filenameTF, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(chooseFileB))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(additionalOptionsButton))
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
            String t=  getFilenameTF().getText();
            if ( t.contains("/" ) && t.startsWith("file:") ) {
                editorPanel.setURI( t );
            } else {
                t= URISplit.makeCanonical(t);
                editorPanel.setURI( t );
            }
        } else {
            editorPanel= null;
        }

        if (  updateProcessing ) {
            DataSourceFormat form= DataSourceRegistry.getInstance().getFormatByExt(ss);
            if ( form!=null ) {
                originalDataB.setEnabled( originalDataSet!=null && form.canFormat( originalDataSet ) );
                processedDataB.setEnabled( processedDataSet!=null && form.canFormat( processedDataSet ) );
                processedWithinXRangeB.setEnabled( processedDataSet!=null && form.canFormat( processedDataSet ) );
            } else {
                originalDataB.setEnabled( true );
                processedDataB.setEnabled( true );
                processedWithinXRangeB.setEnabled( true );
            }
            if ( originalDataSet.equals(processedDataSet) ) {
                processedDataB.setEnabled(false);
            }
            if ( !originalDataB.isEnabled() && originalDataB.isSelected() && processedDataB.isEnabled() ) {
                processedDataB.setSelected(true);
            } 
            if ( !processedDataB.isEnabled() && processedDataB.isSelected() && processedWithinXRangeB.isEnabled() ) {
                processedWithinXRangeB.setSelected(true);
            }
            additionalOptionsButton.setEnabled( editorPanel!=null );
        }
        
    }
    
    private void formatDLItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_formatDLItemStateChanged
        String ss=  (String) evt.getItem();
        updateEditorPanel(ss,evt.getStateChange()==ItemEvent.SELECTED);
    }//GEN-LAST:event_formatDLItemStateChanged

    private void additionalOptionsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_additionalOptionsButtonActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);                
        javax.swing.JPanel j= editorPanel.getPanel();
        AutoplotUtil.showConfirmDialog( this, j, "Additional Options", JOptionPane.OK_CANCEL_OPTION );
    }//GEN-LAST:event_additionalOptionsButtonActionPerformed

    DataSourceFormatEditorPanel editorPanel=null;

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton additionalOptionsButton;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JButton chooseFileB;
    private javax.swing.JTextField filenameTF;
    private javax.swing.JComboBox formatDL;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JRadioButton originalDataB;
    private javax.swing.JRadioButton processedDataB;
    private javax.swing.JRadioButton processedWithinXRangeB;
    private javax.swing.JLabel warningMessageLabel;
    // End of variables declaration//GEN-END:variables

    /**
     * @return the filenameTF
     */
    public javax.swing.JTextField getFilenameTF() {
        return filenameTF;
    }

    /**
     * @return the formatDL
     */
    public javax.swing.JComboBox getFormatDL() {
        return formatDL;
    }

    public DataSourceFormatEditorPanel getDataSourceFormatEditorPanel() {
        return editorPanel;
    }

    /**
     * set the current file.
     * @param currentFileString 
     */
    public void setFile(String currentFileString) {
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
     * indicate that the data is from a TSB and will be reloaded.
     * @param b 
     */
    void setTsb(boolean b) {
        if ( b ) {
            warningMessageLabel.setText("<html><i>Exporting data at native resolution.</i></html>");
            warningMessageLabel.setToolTipText("<html><i>This data comes from a reader that can return data at multiple resolutions.  Data will be reread at native resolution before writing output.</i></html>");
        } 
    }

}
