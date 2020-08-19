
/*
 * CreatePngWalkDialog.java
 *
 * Created on Jun 30, 2010, 9:09:46 AM
 */

package org.autoplot.pngwalk;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import org.autoplot.AutoplotUtil;
import org.autoplot.EventsListToolUtil;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.TimeRangeTool;

/**
 * Provide dialog to control make pngwalk code.
 * 
 * see http://sourceforge.net/tracker/index.php?func=detail&aid=2984095&group_id=199733&atid=970685
 * @author jbf
 */
public class CreatePngWalkDialog extends javax.swing.JPanel {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.pngwalk");
    
    /** Creates new form CreatePngWalkDialog */
    public CreatePngWalkDialog() {
        initComponents();
        EventsListToolUtil.deflts(eventsFileSelector);
        
        String home= java.lang.System.getProperty( "user.home" ) + java.lang.System.getProperty( "file.separator" );
        home= home.replaceAll("\\\\","/");
        outputFolderTf.setText( home + "pngwalk/" );

        setDefaults();
        checkExists();
    }

    private void setDefaults() {
        Preferences prefs= AutoplotSettings.settings().getPreferences( CreatePngWalkDialog.class );

        String file= prefs.get( "filenameRoot", flnRootTf.getText() );
        flnRootTf.setText(file);
        String home= prefs.get( "outputFolder", outputFolderTf.getText() );
        outputFolderTf.setText(home);
        String timeFormat= prefs.get("timeFormat", (String)timeFormatCB.getSelectedItem() );
        timeFormatCB.setSelectedItem(timeFormat);
        String timeRange= prefs.get("timeRange", timeRangeTf.getText() );
        timeRangeTf.setText( timeRange );
        boolean thumbs= prefs.getBoolean( "createThumbs", createThumbsCb.isSelected() );
        createThumbsCb.setSelected(thumbs);
        
        
        boolean useTimeRange= prefs.getBoolean( "useTimeRange", timeRangeRadioButton.isSelected() );
        timeRangeRadioButton.setSelected(useTimeRange);
        
        boolean useEventsFile= prefs.getBoolean( "useEventsFile", eventsFileRadioButton.isSelected() );
        eventsFileRadioButton.setSelected(useEventsFile);
        batchUriNameCB.setSelected(prefs.get( "batchUriName", "" ).equals("$o"));
        timeFormatCB.setEnabled( !batchUriNameCB.isSelected() );
        
        String eventsFile= prefs.get( "eventsFile", eventsFileSelector.getValue() );
        eventsFileSelector.setValue(eventsFile);

        autorangeCB.setSelected( prefs.getBoolean( "autorange", autorangeCB.isSelected() ) );
        autorangeFlagsCB.setSelected( prefs.getBoolean( "autorangeFlags", autorangeFlagsCB.isSelected() ) );
        rescaleComboBox.setSelectedItem( prefs.get( "rescalex", (String)rescaleComboBox.getSelectedItem() ) );
        updateCB.setSelected( prefs.getBoolean( "update", updateCB.isSelected() ) );
        runOnCopyCB.setSelected( prefs.getBoolean( "runOnCopy", runOnCopyCB.isSelected() ) );
        versionTextField.setText( prefs.get( "version", versionTextField.getText() ) );
        removeNoDataImagesCB.setSelected( prefs.getBoolean( "removeNoData", removeNoDataImagesCB.isSelected() ));
        pngFormatCB.setSelected( prefs.get("outputFormat", pngFormatCB.isSelected() ? "png": "pdf" ).equals("png") );
        pdfFormatCB.setSelected( prefs.get("outputFormat", pngFormatCB.isSelected() ? "png": "pdf" ).equals("pdf") );
    }

    public void writeDefaults() {
        Preferences prefs= AutoplotSettings.settings().getPreferences( CreatePngWalkDialog.class );
        prefs.put( "filenameRoot", flnRootTf.getText().trim() );
        File ff= getOutputFolder( getOutputFolderTf().getText().trim() );
        prefs.put( "outputFolder", DataSetURI.fromFile(ff).substring(7) );
        prefs.put( "timeFormat", ((String)timeFormatCB.getSelectedItem()).trim() );
        prefs.put( "timeRange", timeRangeTf.getText().trim() );
        prefs.putBoolean( "createThumbs", createThumbsCb.isSelected() );

        prefs.putBoolean( "autorange", autorangeCB.isSelected() );
        prefs.putBoolean( "autorangeFlags", autorangeFlagsCB.isSelected() );
        prefs.put( "rescalex", ((String)rescaleComboBox.getSelectedItem()).trim() );
        prefs.putBoolean( "update", updateCB.isSelected() );
        prefs.putBoolean( "removeNoData", removeNoDataImagesCB.isSelected() );
        prefs.putBoolean( "runOnCopy", runOnCopyCB.isSelected() );
        prefs.put( "version", ((String)versionTextField.getText().trim()) );

        prefs.putBoolean( "useTimeRange", timeRangeRadioButton.isSelected() );
        prefs.putBoolean( "useEventsFile", eventsFileRadioButton.isSelected() );
        prefs.put( "eventsFile", eventsFileSelector.getValue() );
        prefs.put( "batchUriName", batchUriNameCB.isSelected() ? "$o" : "" );
        
        prefs.put( "outputFormat", pngFormatCB.isSelected() ? "png" : "pdf" );
        
        try {
            prefs.flush();
        } catch (BackingStoreException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    private File getOutputFolder( String sff ) {
        if ( sff.startsWith("file://") ) sff= sff.substring(7);
        if ( sff.startsWith("file:") ) sff= sff.substring(5);
        File ff= new File( sff );
        return ff;
    }
    
    public CreatePngWalk.Params getParams() {
        CreatePngWalk.Params params = new CreatePngWalk.Params();
        File ff= getOutputFolder( getOutputFolderTf().getText().trim() );
        params.outputFolder = DataSetURI.fromFile( ff ).substring(7);
        if ( !( params.outputFolder.endsWith("/") ) ) {
            params.outputFolder= params.outputFolder + "/";
        }
        params.timeRangeStr = getTimeRangeTf().getText().trim();
        params.product = getFlnRootTf().getText().trim();
        params.timeFormat = ((String)getTimeFormatCB().getSelectedItem()).trim();
        params.createThumbs = getCreateThumbsCb().isSelected();

        params.autorange= autorangeCB.isSelected(); 
        params.autorangeFlags= autorangeFlagsCB.isSelected(); 
        
        params.rescalex= ((String)rescaleComboBox.getSelectedItem()).trim();
        params.update= updateCB.isSelected();
        params.runOnCopy= runOnCopyCB.isSelected();
        params.removeNoData= removeNoDataImagesCB.isSelected();
        params.version= versionTextField.getText().trim();

        String s= eventsFileSelector.getValue();
        if ( s.startsWith("(") && s.endsWith(")") ) {
            params.batchUri= "";
        } else {
            params.batchUri= eventsFileSelector.getValue();
        }
        
        params.useBatchUri= eventsFileRadioButton.isSelected();
        if ( batchUriNameCB.isSelected() ) {
            params.batchUriName="$o";
        } else {
            params.batchUriName="";
            if ( params.timeFormat.length()<2 ) {
                 throw new IllegalArgumentException("Time Format must be at least two characters in length");
            }
        }
        
        params.outputFormat= pngFormatCB.isSelected() ? "png" : "pdf";
                
        return params;
    }

    private void checkExists() {
        String sf= outputFolderTf.getText();
        File f= new File( sf );
        overwriteCB.setEnabled(f.exists());
        if ( !f.exists() ) {
            overwriteCB.setSelected(false);
        }
    }

    public JTextField getFlnRootTf() {
        return flnRootTf;
    }

    public JTextField getOutputFolderTf() {
        return outputFolderTf;
    }

    public JTextField getTimeRangeTf() {
        return timeRangeTf;
    }

    public JComboBox getTimeFormatCB() {
        return timeFormatCB;
    }

    public JCheckBox getCreateThumbsCb() {
        return createThumbsCb;
    }

    public JCheckBox getOverwriteCb() {
        return overwriteCB;
    }

    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        bindingGroup = new org.jdesktop.beansbinding.BindingGroup();

        buttonGroup1 = new javax.swing.ButtonGroup();
        buttonGroup2 = new javax.swing.ButtonGroup();
        jLabel1 = new javax.swing.JLabel();
        flnRootTf = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        outputFolderTf = new javax.swing.JTextField();
        pickFolderButton = new javax.swing.JButton();
        jLabel3 = new javax.swing.JLabel();
        timeRangeTf = new javax.swing.JTextField();
        createThumbsCb = new javax.swing.JCheckBox();
        overwriteCB = new javax.swing.JCheckBox();
        jLabel5 = new javax.swing.JLabel();
        versionTextField = new javax.swing.JTextField();
        updateCB = new javax.swing.JCheckBox();
        jLabel6 = new javax.swing.JLabel();
        rescaleComboBox = new javax.swing.JComboBox();
        autorangeCB = new javax.swing.JCheckBox();
        timeFormatCB = new javax.swing.JComboBox();
        timeRangeToolButton = new javax.swing.JButton();
        timeRangeRadioButton = new javax.swing.JRadioButton();
        eventsFileRadioButton = new javax.swing.JRadioButton();
        eventsFileSelector = new org.autoplot.datasource.DataSetSelector();
        pngFormatCB = new javax.swing.JRadioButton();
        pdfFormatCB = new javax.swing.JRadioButton();
        jLabel4 = new javax.swing.JLabel();
        batchUriNameCB = new javax.swing.JCheckBox();
        runOnCopyCB = new javax.swing.JCheckBox();
        autorangeFlagsCB = new javax.swing.JCheckBox();
        removeNoDataImagesCB = new javax.swing.JCheckBox();

        jLabel1.setText("Filename Root:");
        jLabel1.setToolTipText("Stem to identify result within folder.");

        flnRootTf.setText("product");
        flnRootTf.setToolTipText("Stem used to ensure unique filenames");

        jLabel2.setText("Output Folder:");

        outputFolderTf.setText("/folder/");
        outputFolderTf.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                outputFolderTfKeyReleased(evt);
            }
            public void keyTyped(java.awt.event.KeyEvent evt) {
                outputFolderTfKeyTyped(evt);
            }
        });

        pickFolderButton.setText("Pick");
        pickFolderButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pickFolderButtonActionPerformed(evt);
            }
        });

        jLabel3.setText("Time Format:");
        jLabel3.setToolTipText("Time format for each png, used to infer cadence of sequence");

        timeRangeTf.setText("2010");

        org.jdesktop.beansbinding.Binding binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, timeRangeRadioButton, org.jdesktop.beansbinding.ELProperty.create("${selected}"), timeRangeTf, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        createThumbsCb.setSelected(true);
        createThumbsCb.setText("Create Thumbs");
        createThumbsCb.setToolTipText("create thumbs subfolders for performance");

        overwriteCB.setText("Overwrite");
        overwriteCB.setToolTipText("overwrite existing pngwalk");

        jLabel5.setText("Version (Optional):");
        jLabel5.setToolTipText("Add this version id to files");

        versionTextField.setText(" ");

        updateCB.setText("Update Sequence");
        updateCB.setToolTipText("Only update the sequence, skipping images already computed");

        jLabel6.setText("Rescale (Optional):");
        jLabel6.setToolTipText("Rescale before making image, possibly showing surrounding context.");

        rescaleComboBox.setEditable(true);
        rescaleComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "0%,100%", "0%-1hr,100%+1hr", "-300%,400%" }));

        autorangeCB.setText("Autorange Each");
        autorangeCB.setToolTipText("Autorange in Y and Z each image of the sequence");

        timeFormatCB.setEditable(true);
        timeFormatCB.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "$Y$m$d", "$Y", "$Y$(m,span=3)", "$Y$m", "$Y_$j", "$Y$m$d_$H", "$Y$m$d_$H$M", "$Y$m$d_$H$M$S", "$Y$m$d_$H$M$S.$(subsec,places=3)", "$(o,id=rbspa-pp)", "$(o,id=http://das2.org/Orbits/marsx.dat)", " ", " " }));

        timeRangeToolButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/autoplot/datasource/calendar.png"))); // NOI18N
        timeRangeToolButton.setToolTipText("Use time range tool");

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, timeRangeRadioButton, org.jdesktop.beansbinding.ELProperty.create("${selected}"), timeRangeToolButton, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        timeRangeToolButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timeRangeToolButtonActionPerformed(evt);
            }
        });

        buttonGroup1.add(timeRangeRadioButton);
        timeRangeRadioButton.setSelected(true);
        timeRangeRadioButton.setText("Generate intervals to cover time range:");
        timeRangeRadioButton.setToolTipText("Generate intervals based on the output filename.  For example 2010 with $Y$m$d is covered by 2010-01-01, 2010-01-02, ..., 2010-12-31");

        buttonGroup1.add(eventsFileRadioButton);
        eventsFileRadioButton.setText("Use events file that contains list of times:");
        eventsFileRadioButton.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                eventsFileRadioButtonItemStateChanged(evt);
            }
        });

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, eventsFileRadioButton, org.jdesktop.beansbinding.ELProperty.create("${selected}"), eventsFileSelector, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        buttonGroup2.add(pngFormatCB);
        pngFormatCB.setText("PNG");
        pngFormatCB.setToolTipText("Write PNG files");

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, createThumbsCb, org.jdesktop.beansbinding.ELProperty.create("${enabled}"), pngFormatCB, org.jdesktop.beansbinding.BeanProperty.create("selected"));
        bindingGroup.addBinding(binding);

        pngFormatCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pngFormatCBActionPerformed(evt);
            }
        });

        buttonGroup2.add(pdfFormatCB);
        pdfFormatCB.setText("PDF");
        pdfFormatCB.setToolTipText("Write PDF files");

        jLabel4.setText("Output Format:");

        batchUriNameCB.setText("Events file specifies product names");
        batchUriNameCB.setToolTipText("<html>The events file contains the file name, so for example instead of product_$Y$m$d,\n<br>just use the last column when generating the filename.\n<br><tt>2000-01-09T06:50:41.155Z\t2000-01-09T13:46:34.224Z\ta/o1\t</tt>\n");

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, eventsFileRadioButton, org.jdesktop.beansbinding.ELProperty.create("${selected}"), batchUriNameCB, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        batchUriNameCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                batchUriNameCBActionPerformed(evt);
            }
        });

        runOnCopyCB.setSelected(true);
        runOnCopyCB.setText("Run in background");
        runOnCopyCB.setToolTipText("Run the PNG Walk on a copy in the background.  Note only properties conveyed by a .vap file will appear on the produced images.");

        autorangeFlagsCB.setText("Check Autorange flag");
        autorangeFlagsCB.setToolTipText("Check each axis' autorange property, true indicates the axis should be reset.");

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, autorangeCB, org.jdesktop.beansbinding.ELProperty.create("${selected}"), autorangeFlagsCB, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        removeNoDataImagesCB.setText("Remove No Data Images");
        removeNoDataImagesCB.setToolTipText("Check that the image produced will actually contain displayed data, and skip those that don't.");

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(layout.createSequentialGroup()
                                .add(12, 12, 12)
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                    .add(overwriteCB)
                                    .add(layout.createSequentialGroup()
                                        .add(outputFolderTf)
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                        .add(pickFolderButton))))
                            .add(layout.createSequentialGroup()
                                .add(jLabel2)
                                .add(0, 0, Short.MAX_VALUE)))
                        .add(45, 45, 45))
                    .add(layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(layout.createSequentialGroup()
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                    .add(jLabel1)
                                    .add(jLabel3)
                                    .add(layout.createSequentialGroup()
                                        .add(12, 12, 12)
                                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                                            .add(org.jdesktop.layout.GroupLayout.LEADING, timeFormatCB, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                            .add(org.jdesktop.layout.GroupLayout.LEADING, flnRootTf))))
                                .add(25, 25, 25))
                            .add(layout.createSequentialGroup()
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                    .add(layout.createSequentialGroup()
                                        .add(createThumbsCb)
                                        .add(35, 35, 35)
                                        .add(jLabel4)
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                        .add(pngFormatCB)
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                        .add(pdfFormatCB))
                                    .add(eventsFileRadioButton)
                                    .add(timeRangeRadioButton)
                                    .add(layout.createSequentialGroup()
                                        .add(12, 12, 12)
                                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                            .add(eventsFileSelector, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                            .add(layout.createSequentialGroup()
                                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                                    .add(batchUriNameCB)
                                                    .add(layout.createSequentialGroup()
                                                        .add(timeRangeTf, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 306, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                                        .add(timeRangeToolButton)))
                                                .add(0, 0, Short.MAX_VALUE)))))
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)))
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(autorangeCB, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .add(layout.createSequentialGroup()
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                    .add(updateCB)
                                    .add(jLabel5)
                                    .add(jLabel6)
                                    .add(runOnCopyCB)
                                    .add(layout.createSequentialGroup()
                                        .add(12, 12, 12)
                                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                            .add(rescaleComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 174, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                            .add(versionTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 74, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                            .add(autorangeFlagsCB)))
                                    .add(removeNoDataImagesCB))
                                .add(0, 0, Short.MAX_VALUE))))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(jLabel2)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(outputFolderTf, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(pickFolderButton))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(overwriteCB)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel1)
                    .add(jLabel5))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(flnRootTf, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(versionTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel3)
                    .add(jLabel6))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(timeFormatCB, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(rescaleComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(autorangeCB)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(timeRangeRadioButton)
                    .add(autorangeFlagsCB))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                            .add(timeRangeToolButton)
                            .add(timeRangeTf, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                        .add(7, 7, 7)
                        .add(eventsFileRadioButton)
                        .add(1, 1, 1)
                        .add(eventsFileSelector, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(3, 3, 3)
                        .add(batchUriNameCB)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                .add(pngFormatCB)
                                .add(pdfFormatCB)
                                .add(jLabel4))
                            .add(createThumbsCb)))
                    .add(layout.createSequentialGroup()
                        .add(updateCB)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(runOnCopyCB)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(removeNoDataImagesCB)))
                .addContainerGap(53, Short.MAX_VALUE))
        );

        bindingGroup.bind();
    }// </editor-fold>//GEN-END:initComponents

    private void pickFolderButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pickFolderButtonActionPerformed
        JFileChooser chooser= new JFileChooser( getOutputFolderTf().getText() );
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int r= chooser.showSaveDialog(this);
        if ( r==JFileChooser.APPROVE_OPTION ) {
            outputFolderTf.setText( chooser.getSelectedFile().toString() );
        }
    }//GEN-LAST:event_pickFolderButtonActionPerformed

    private void outputFolderTfKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_outputFolderTfKeyTyped
        checkExists();
    }//GEN-LAST:event_outputFolderTfKeyTyped

    private void outputFolderTfKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_outputFolderTfKeyReleased
        checkExists();
    }//GEN-LAST:event_outputFolderTfKeyReleased

    private void timeRangeToolButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeRangeToolButtonActionPerformed
        TimeRangeTool t=new TimeRangeTool();
        t.setSelectedRange( timeRangeTf.getText() );//TODO: goofy
        if ( JOptionPane.OK_OPTION==AutoplotUtil.showConfirmDialog( this, t, "Select time range", JOptionPane.OK_CANCEL_OPTION ) ) {
            String str= t.getSelectedRange();
            timeRangeTf.setText(str);
        }

    }//GEN-LAST:event_timeRangeToolButtonActionPerformed

    private void pngFormatCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pngFormatCBActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_pngFormatCBActionPerformed

    private void batchUriNameCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_batchUriNameCBActionPerformed
        timeFormatCB.setEnabled( !batchUriNameCB.isSelected() );
    }//GEN-LAST:event_batchUriNameCBActionPerformed

    private void eventsFileRadioButtonItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_eventsFileRadioButtonItemStateChanged
        if ( !eventsFileRadioButton.isSelected() ) {
            batchUriNameCB.setSelected(false);
            timeFormatCB.setEnabled(true);
        }
    }//GEN-LAST:event_eventsFileRadioButtonItemStateChanged

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox autorangeCB;
    private javax.swing.JCheckBox autorangeFlagsCB;
    private javax.swing.JCheckBox batchUriNameCB;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.ButtonGroup buttonGroup2;
    private javax.swing.JCheckBox createThumbsCb;
    private javax.swing.JRadioButton eventsFileRadioButton;
    private org.autoplot.datasource.DataSetSelector eventsFileSelector;
    private javax.swing.JTextField flnRootTf;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JTextField outputFolderTf;
    private javax.swing.JCheckBox overwriteCB;
    private javax.swing.JRadioButton pdfFormatCB;
    private javax.swing.JButton pickFolderButton;
    private javax.swing.JRadioButton pngFormatCB;
    private javax.swing.JCheckBox removeNoDataImagesCB;
    private javax.swing.JComboBox rescaleComboBox;
    private javax.swing.JCheckBox runOnCopyCB;
    private javax.swing.JComboBox timeFormatCB;
    private javax.swing.JRadioButton timeRangeRadioButton;
    private javax.swing.JTextField timeRangeTf;
    private javax.swing.JButton timeRangeToolButton;
    private javax.swing.JCheckBox updateCB;
    private javax.swing.JTextField versionTextField;
    private org.jdesktop.beansbinding.BindingGroup bindingGroup;
    // End of variables declaration//GEN-END:variables

}
