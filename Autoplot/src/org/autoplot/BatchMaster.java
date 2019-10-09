
package org.autoplot;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.autoplot.bookmarks.Bookmark;
import org.autoplot.datasource.DataSetSelector;
import org.autoplot.jythonsupport.JythonRefactory;
import org.das2.components.DasProgressPanel;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.python.util.InteractiveInterpreter;
import org.autoplot.dom.Application;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.URISplit;
import org.autoplot.jythonsupport.JythonUtil.Param;
import org.autoplot.jythonsupport.ui.ParametersFormPanel;
import org.autoplot.jythonsupport.ui.Util;
import org.das2.datum.Units;
import org.das2.qds.DataSetUtil;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;
import org.das2.util.FileUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Tool for running batches, generating inputs for jython scripts.
 * @see https://sourceforge.net/p/autoplot/feature-requests/545/
 * @author jbf
 */
public class BatchMaster extends javax.swing.JPanel {

    private static final Logger logger= LoggerManager.getLogger("jython.batchmaster");
    
    private Application dom;
    
    private Object state; 
    
    private static final String STATE_READY= "ready";
    private static final String STATE_LOADING= "loading";
    
    private JSONObject results=null;
    private JSONObject resultsPending=null;
    private File resultsFile=null;
    
    /**
     * Creates new form BatchMaster
     * @param dom
     */
    public BatchMaster( final Application dom ) {
        initComponents();
        generateButton1.setEnabled(false);
        this.dom= dom;
        this.state= STATE_READY;
        
        /**
         * register the browse trigger to the same action, because we always browse.
         */
        dataSetSelector1.registerBrowseTrigger( "(.*)\\.jy(\\?.*)?", new AbstractAction( "Review Script" ) {
            @Override
            public void actionPerformed( ActionEvent ev ) {
                org.das2.util.LoggerManager.logGuiEvent(ev);                    
                state= STATE_LOADING;
                String s= dataSetSelector1.getValue();
                Map<String,String> args;
                try {
                    URISplit split= URISplit.parse(s);        //bug 1408--note runScript doesn't account for changes made to the GUI.
                    args= URISplit.parseParams(split.params);
                    Map<String,Object> env= new HashMap<>();
                    env.put( "dom", dom );
                    env.put( "PWD", split.path );
                    File scriptFile= DataSetURI.getFile(s,new NullProgressMonitor());
                    if ( JOptionPane.OK_OPTION==JythonUtil.showScriptDialog( 
                            BatchMaster.this, 
                            env, 
                            scriptFile, 
                            args, 
                            enabled, 
                            split.resourceUri ) ) {
                        split.params= URISplit.formatParams(args);
                        dataSetSelector1.setValue( URISplit.format( split ) );
                        
                    }
                } catch ( IOException ex ) { 
                    throw new RuntimeException(ex);
                } finally {
                    state= STATE_READY;
                }
            }
        });
        
        dataSetSelector1.registerActionTrigger( "(.*)\\.jy(\\?.*)?", new AbstractAction( "Review Script" ) {
            @Override
            public void actionPerformed( ActionEvent ev ) {
                org.das2.util.LoggerManager.logGuiEvent(ev); 
                state= STATE_LOADING;                  
                try {
                    String scriptName= dataSetSelector1.getValue();
                    URISplit split= URISplit.parse(scriptName);
                    if ( !split.file.endsWith(".jy") ) {
                        JOptionPane.showMessageDialog( BatchMaster.this, "script must end in .jy: "+scriptName );
                        return;
                    }

                    pwd= split.path;

                    //Map<String,String> params= URISplit.parseParams(split.params);  //TODO: support these.
                    Map<String,Object> env= new HashMap<>();

                    DasProgressPanel monitor= DasProgressPanel.createFramed( SwingUtilities.getWindowAncestor(BatchMaster.this), "download script");
                    File scriptFile= DataSetURI.getFile( split.file, monitor );
                    String script= readScript( scriptFile );

                    env.put( "dom", dom );
                    env.put( "PWD", split.path );
                    
                    Map<String,org.autoplot.jythonsupport.JythonUtil.Param> parms= Util.getParams( env, script, URISplit.parseParams(split.params), new NullProgressMonitor() );

                    String[] items= new String[parms.size()+1];
                    int i=0;
                    items[0]="";
                    for ( Entry<String,org.autoplot.jythonsupport.JythonUtil.Param> p: parms.entrySet() ) {
                        items[i+1]= p.getKey();
                        i=i+1;
                    }
                    ComboBoxModel m1= new DefaultComboBoxModel(Arrays.copyOfRange(items,1,items.length));
                    param1NameCB.setModel(m1);
                    generateButton1.setEnabled( items.length>1 );
                    ComboBoxModel m2= new DefaultComboBoxModel(items);
                    param2NameCB.setModel(m2);
                    
                    param1Values.setText("");
                    param2Values.setText("");
                    
                    messageLabel.setText("Load up those parameters and hit Go!");
                    jScrollPane3.getViewport().setView(param1Values);
                    
                    
                } catch (IOException ex) {
                    Logger.getLogger(BatchMaster.class.getName()).log(Level.SEVERE, null, ex);
                } finally {
                    state= STATE_READY;
                }
            }
        });
        
        dataSetSelector1.setPromptText("Enter the name of a Jython script");
        //dataSetSelector1.setRecent( Collections.singletonList() );
        
        List<String> recentUris= new ArrayList<>(20);
        recentUris.add( "http://autoplot.org/data/script/examples/parameters.jy" );
        if ( dom.getController()!=null ) { // support testing.
            Pattern p= Pattern.compile(".*\\.jy(\\?.*)?");
            Map<String,String> recentJy= dom.getController().getApplicationModel().getRecent(p,20);
            for ( Entry<String,String> recentItem : recentJy.entrySet() ) {
                recentUris.add( recentItem.getKey() );
            }
        }
        dataSetSelector1.setRecent( recentUris );
        
        jScrollPane3.getVerticalScrollBar().setUnitIncrement(jScrollPane3.getFont().getSize());
        jScrollPane1.getVerticalScrollBar().setUnitIncrement(jScrollPane1.getFont().getSize());
        
        
    }
    
    /**
     * get the menu bar, which is typically added to the JDialog which will 
     * contain this component.
     * 
     * @return the menu bar.
     */
    public JMenuBar getMenuBar() {
        return menuBar;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        bindingGroup = new org.jdesktop.beansbinding.BindingGroup();

        jScrollPane2 = new javax.swing.JScrollPane();
        jList2 = new javax.swing.JList<>();
        jPopupMenu1 = new javax.swing.JPopupMenu();
        jMenuItem1 = new javax.swing.JMenuItem();
        loadUriMenuItem = new javax.swing.JMenuItem();
        timeRangesPanel = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        timeRangeComboBox = new javax.swing.JComboBox<>();
        timeFormatComboBox = new javax.swing.JComboBox<>();
        jLabel3 = new javax.swing.JLabel();
        jPopupMenu2 = new javax.swing.JPopupMenu();
        jMenuItem2 = new javax.swing.JMenuItem();
        menuBar = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        OpenMenuItem = new javax.swing.JMenuItem();
        SaveAsMenuItem = new javax.swing.JMenuItem();
        exportResultsMenuItem = new javax.swing.JMenuItem();
        helpMenu = new javax.swing.JMenu();
        showHelpMenuItem = new javax.swing.JMenuItem();
        goButton = new javax.swing.JButton();
        jScrollPane3 = new javax.swing.JScrollPane();
        param1Values = new javax.swing.JTextArea();
        jScrollPane1 = new javax.swing.JScrollPane();
        param2Values = new javax.swing.JTextArea();
        dataSetSelector1 = new org.autoplot.datasource.DataSetSelector();
        messageLabel = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        param1NameCB = new javax.swing.JComboBox<>();
        param2NameCB = new javax.swing.JComboBox<>();
        cancelButton = new javax.swing.JButton();
        generateButton1 = new javax.swing.JButton();
        generateButton2 = new javax.swing.JButton();
        writeCheckBox = new javax.swing.JCheckBox();
        writeFilenameCB = new javax.swing.JComboBox<>();

        jList2.setModel(new javax.swing.AbstractListModel<String>() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public String getElementAt(int i) { return strings[i]; }
        });
        jScrollPane2.setViewportView(jList2);

        jMenuItem1.setText("Generate...");
        jMenuItem1.setToolTipText("Generate items for list");
        jMenuItem1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem1ActionPerformed(evt);
            }
        });
        jPopupMenu1.add(jMenuItem1);

        loadUriMenuItem.setText("Load Events File...");
        loadUriMenuItem.setToolTipText("Load a list of time ranges from an events file.");
        loadUriMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadUriMenuItemActionPerformed(evt);
            }
        });
        jPopupMenu1.add(loadUriMenuItem);

        jLabel2.setText("Time Range:");

        timeRangeComboBox.setEditable(true);
        timeRangeComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Jun 2000", "2000", "2000-01-01/03-01", "2000-2016" }));

        timeFormatComboBox.setEditable(true);
        timeFormatComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "$Y-$m-$d", "$Y", "$Y-$(m,span=3)", "$Y-$m", "$Y_$j", "$Y-$m-$dT$H/PT1H", "$Y-$m-$dT$H$M/PT1M", "$Y-$m-$dT$H$M$S/PT1S", " " }));

        jLabel3.setText("Time Format:");

        javax.swing.GroupLayout timeRangesPanelLayout = new javax.swing.GroupLayout(timeRangesPanel);
        timeRangesPanel.setLayout(timeRangesPanelLayout);
        timeRangesPanelLayout.setHorizontalGroup(
            timeRangesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(timeRangesPanelLayout.createSequentialGroup()
                .addGroup(timeRangesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(timeRangeComboBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(timeRangesPanelLayout.createSequentialGroup()
                        .addGroup(timeRangesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel3)
                            .addComponent(jLabel2))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(timeFormatComboBox, javax.swing.GroupLayout.Alignment.TRAILING, 0, 220, Short.MAX_VALUE))
                .addContainerGap())
        );
        timeRangesPanelLayout.setVerticalGroup(
            timeRangesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(timeRangesPanelLayout.createSequentialGroup()
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(timeRangeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(timeFormatComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jMenuItem2.setText("Generate...");
        jMenuItem2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem2ActionPerformed(evt);
            }
        });
        jPopupMenu2.add(jMenuItem2);

        fileMenu.setText("File");

        OpenMenuItem.setText("Open batch file...");
        OpenMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                OpenMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(OpenMenuItem);

        SaveAsMenuItem.setText("Save As...");
        SaveAsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SaveAsMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(SaveAsMenuItem);

        exportResultsMenuItem.setText("Export Results...");
        exportResultsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportResultsMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(exportResultsMenuItem);

        menuBar.add(fileMenu);

        helpMenu.setText("Help");

        showHelpMenuItem.setText("Show Help Manual in Browser");
        showHelpMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showHelpMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(showHelpMenuItem);

        menuBar.add(helpMenu);

        goButton.setText("Go!");
        goButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                goButtonActionPerformed(evt);
            }
        });

        param1Values.setColumns(20);
        param1Values.setRows(5);
        param1Values.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                param1ValuesMousePressed(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                param1ValuesMouseReleased(evt);
            }
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                param1ValuesMouseClicked(evt);
            }
        });
        jScrollPane3.setViewportView(param1Values);

        param2Values.setColumns(20);
        param2Values.setRows(5);
        param2Values.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                param2ValuesMousePressed(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                param2ValuesMouseReleased(evt);
            }
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                param2ValuesMouseClicked(evt);
            }
        });
        jScrollPane1.setViewportView(param2Values);

        messageLabel.setText("Load up those parameters and hit Go!");

        jLabel1.setText("<html>This tool generates inputs for scripts, running through a series of inputs.  First load the script with the green \"play\" button, then specify the parameter name and values to assign, and optionally a second parameter.  Each value of the second parameter is run for each value of the first.  Use the inspect button to set values for any other parameters. Right-click within the values areas to generate values.");
        jLabel1.setVerticalAlignment(javax.swing.SwingConstants.TOP);

        param1NameCB.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { " " }));
        param1NameCB.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                param1NameCBItemStateChanged(evt);
            }
        });

        param2NameCB.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { " " }));
        param2NameCB.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                param2NameCBItemStateChanged(evt);
            }
        });

        cancelButton.setText("Cancel");
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        generateButton1.setText("Generate...");
        generateButton1.setToolTipText("Generate items for list");
        generateButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                generateButton1ActionPerformed(evt);
            }
        });

        generateButton2.setText("Generate...");
        generateButton2.setToolTipText("Generate items for list");
        generateButton2.setEnabled(false);
        generateButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                generateButton2ActionPerformed(evt);
            }
        });

        writeCheckBox.setText("Write:");
        writeCheckBox.setToolTipText("After each iteration, write the file, where $x is replaced");

        writeFilenameCB.setEditable(true);
        writeFilenameCB.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "/tmp/ap/$x.png", "/tmp/ap/$x_$x.png", "/tmp/ap/$x.pdf", "/tmp/ap/$x_$x.pdf", " " }));

        org.jdesktop.beansbinding.Binding binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, writeCheckBox, org.jdesktop.beansbinding.ELProperty.create("${selected}"), writeFilenameCB, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addContainerGap(544, Short.MAX_VALUE)
                        .addComponent(cancelButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(goButton, javax.swing.GroupLayout.PREFERRED_SIZE, 62, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(dataSetSelector1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 682, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(param1NameCB, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(generateButton1))
                            .addGroup(layout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(jScrollPane3)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane1)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(param2NameCB, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(generateButton2))))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(messageLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 401, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(writeCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(writeFilenameCB, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {generateButton1, generateButton2});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(dataSetSelector1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(param1NameCB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(param2NameCB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(generateButton1)
                    .addComponent(generateButton2))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 228, Short.MAX_VALUE)
                    .addComponent(jScrollPane1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(messageLabel)
                    .addComponent(writeCheckBox)
                    .addComponent(writeFilenameCB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(goButton)
                    .addComponent(cancelButton))
                .addContainerGap())
        );

        bindingGroup.bind();
    }// </editor-fold>//GEN-END:initComponents

    private void goButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_goButtonActionPerformed
        if ( !goButton.isEnabled() ) {
            return;
        }
        goButton.setEnabled(false);
        Runnable run= new Runnable() {
            @Override
            public void run() {
                try {
                    String scriptName= dataSetSelector1.getValue();
                    dom.getController().getApplicationModel().addRecent(scriptName);
                    doIt();
                } catch (IOException ex) {
                    messageLabel.setText(ex.getMessage());
                }
            }
        };
        new Thread(run,"runBatch").start();
    }//GEN-LAST:event_goButtonActionPerformed

    private void param1ValuesMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_param1ValuesMouseClicked
        if ( evt.isPopupTrigger() ) {
            jPopupMenu1.show( evt.getComponent(), evt.getX(), evt.getY() );
        }
    }//GEN-LAST:event_param1ValuesMouseClicked

    private void param1ValuesMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_param1ValuesMousePressed
        if ( evt.isPopupTrigger() ) {
            jPopupMenu1.show( evt.getComponent(), evt.getX(), evt.getY() );
        }
    }//GEN-LAST:event_param1ValuesMousePressed

    private void param1ValuesMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_param1ValuesMouseReleased
        if ( evt.isPopupTrigger() ) {
            jPopupMenu1.show( evt.getComponent(), evt.getX(), evt.getY() );
        }
    }//GEN-LAST:event_param1ValuesMouseReleased

    private void jMenuItem1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem1ActionPerformed
        doGenerate( param1NameCB, param1Values );
    }//GEN-LAST:event_jMenuItem1ActionPerformed

    private void param2ValuesMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_param2ValuesMouseClicked
        if ( evt.isPopupTrigger() ) {
            jPopupMenu2.show( evt.getComponent(), evt.getX(), evt.getY() );
        }
    }//GEN-LAST:event_param2ValuesMouseClicked

    private void jMenuItem2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem2ActionPerformed
        doGenerate( param2NameCB, param2Values );
    }//GEN-LAST:event_jMenuItem2ActionPerformed

    private void param2ValuesMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_param2ValuesMousePressed
        if ( evt.isPopupTrigger() ) {
            jPopupMenu2.show( evt.getComponent(), evt.getX(), evt.getY() );
        }
    }//GEN-LAST:event_param2ValuesMousePressed

    private void param2ValuesMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_param2ValuesMouseReleased
        if ( evt.isPopupTrigger() ) {
            jPopupMenu2.show( evt.getComponent(), evt.getX(), evt.getY() );
        }
    }//GEN-LAST:event_param2ValuesMouseReleased

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        Window w=SwingUtilities.getWindowAncestor(this);
        if ( ! ( w instanceof JDialog ) ) {
            logger.warning("untested code might leave hidden windows...");
        }
        w.setVisible(false);
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void loadUriMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadUriMenuItemActionPerformed
        DataSetSelector eventsDataSetSelector= new DataSetSelector();
        
        List<Bookmark> deft= new ArrayList<>();
        deft.add( new Bookmark.Item("http://autoplot.org/autoplot/data/event/simpleEvent.txt") );
        org.autoplot.bookmarks.Util.loadRecent( "eventsRecent", eventsDataSetSelector, deft );
        
        if ( JOptionPane.OK_OPTION==AutoplotUtil.showConfirmDialog(this, eventsDataSetSelector, "Load Events", JOptionPane.OK_CANCEL_OPTION ) ) {
            try {
                QDataSet ds= org.autoplot.jythonsupport.Util.getDataSet(eventsDataSetSelector.getValue());
                ds= Ops.createEvents(ds);
                Units tu= ((Units)((QDataSet)ds.property(QDataSet.BUNDLE_1)).property(QDataSet.UNITS,0));
                StringBuilder ss= new StringBuilder();
                for ( int i=0; i<ds.length(); i++ ) {
                    QDataSet tr= ds.slice(i).trim(0,2);
                    tr= Ops.putProperty( tr, QDataSet.UNITS, tu );
                    ss.append( DataSetUtil.asDatumRange( tr ).toString() ).append("\n");
                }
                param1Values.setText(ss.toString());
            } catch (Exception ex) {
                Logger.getLogger(BatchMaster.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }//GEN-LAST:event_loadUriMenuItemActionPerformed

    private void generateButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_generateButton2ActionPerformed
        doGenerate( param2NameCB, param2Values );
    }//GEN-LAST:event_generateButton2ActionPerformed

    private void generateButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_generateButton1ActionPerformed
        doGenerate( param1NameCB, param1Values );
    }//GEN-LAST:event_generateButton1ActionPerformed

    private void param1NameCBItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_param1NameCBItemStateChanged
        if ( evt.getStateChange()==ItemEvent.SELECTED ) {
            generateButton1.setEnabled( param1NameCB.getSelectedItem().toString().trim().length()>0 );
            jScrollPane3.getViewport().setView(param1Values);
            messageLabel.setText("Load up those parameters and hit Go!");
        }
    }//GEN-LAST:event_param1NameCBItemStateChanged

    private void param2NameCBItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_param2NameCBItemStateChanged
        if ( evt.getStateChange()==ItemEvent.SELECTED ) {
            generateButton2.setEnabled( param2NameCB.getSelectedItem().toString().trim().length()>0 );
        }
    }//GEN-LAST:event_param2NameCBItemStateChanged

    private void OpenMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_OpenMenuItemActionPerformed
        JFileChooser chooser= new JFileChooser();
        chooser.setFileFilter( new FileNameExtensionFilter( "Batch Parameters", "batch") );
        chooser.setDialogType( JFileChooser.OPEN_DIALOG );
        Preferences prefs= Preferences.userNodeForPackage( BatchMaster.class );
        String s= prefs.get("batch",null);
        if ( s!=null ) {
            chooser.setSelectedFile(new File(s));
        }
        if ( JFileChooser.APPROVE_OPTION==chooser.showOpenDialog( this ) ) {
            final File f= chooser.getSelectedFile();
            prefs.put("batch", f.toString() );
            Runnable run= new Runnable() {
                public void run() {
                    try {
                        loadFile( f );
                    } catch (IOException|JSONException ex) {
                        JOptionPane.showMessageDialog( BatchMaster.this, "Unable to open file. "+ex.getMessage() );
                    }
                }
            };
            new Thread(run).start();
        }
    }//GEN-LAST:event_OpenMenuItemActionPerformed

    private void SaveAsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SaveAsMenuItemActionPerformed
        JFileChooser chooser= new JFileChooser();
        chooser.setFileFilter( new FileNameExtensionFilter( "Batch Parameters", "batch") );
        chooser.setDialogType( JFileChooser.OPEN_DIALOG );
        Preferences prefs= Preferences.userNodeForPackage( BatchMaster.class );
        String s= prefs.get("batch",null);
        if ( s!=null ) {
            chooser.setSelectedFile(new File(s));
        }
        if ( JFileChooser.APPROVE_OPTION==chooser.showOpenDialog( this ) ) {
            File ff= chooser.getSelectedFile();
            if ( !ff.getName().endsWith(".batch") ) {
                ff= new File( ff.getAbsolutePath()+".batch");
            }
            final File f= ff;
            
            prefs.put("batch", f.toString() );
            Runnable run= new Runnable() {
                public void run() {
                    try {
                        saveFile( f );
                    } catch (IOException|JSONException ex) {
                        JOptionPane.showMessageDialog( BatchMaster.this, "Unable to save file. "+ex.getMessage() );
                    }
                }
            };
            new Thread(run).start();
        }
    }//GEN-LAST:event_SaveAsMenuItemActionPerformed

    private void exportResultsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportResultsMenuItemActionPerformed
        JFileChooser chooser= new JFileChooser();
        chooser.setFileFilter( new FileNameExtensionFilter( "CSV Files", "csv") );
        chooser.setDialogType( JFileChooser.OPEN_DIALOG );
        Preferences prefs= Preferences.userNodeForPackage( BatchMaster.class );
        String s= prefs.get("export",null);
        if ( s!=null ) {
            chooser.setSelectedFile(new File(s));
        }
        if ( JFileChooser.APPROVE_OPTION==chooser.showSaveDialog( this ) ) {
            File ff= chooser.getSelectedFile();
            if ( !ff.getName().endsWith(".csv") ) {
                ff= new File( ff.getAbsolutePath()+".csv");
            }
            final File f= ff;
            resultsFile= f;
            
            if ( results==null ) {
                String msg= "Output will be written to "+f+".pending and moved after the run.";
                JOptionPane.showMessageDialog( BatchMaster.this, msg );
                return;
            }
            prefs.put("export", f.toString() );
            Runnable run= new Runnable() {
                @Override
                public void run() {
                    try {
                        exportResults( f );
                        JOptionPane.showMessageDialog( BatchMaster.this, "data saved to "+f );
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog( BatchMaster.this, "Unable to save file. "+ex.getMessage() );
                    } catch (JSONException ex) {
                        JOptionPane.showMessageDialog( BatchMaster.this, "Unable to save file because of JSON exception "+ex.getMessage() );
                    }
                }
            };
            new Thread(run).start();
        }
    }//GEN-LAST:event_exportResultsMenuItemActionPerformed

    private void showHelpMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showHelpMenuItemActionPerformed
        AutoplotUtil.openBrowser("http://autoplot.org/batch");
    }//GEN-LAST:event_showHelpMenuItemActionPerformed

    private void exportResults( File f ) throws IOException, JSONException {
        if ( results==null ) {
            return;
        }
        exportResultsPending( f, results, results.getJSONArray("results"), 0 );
    }
    
    private void loadFile( File f ) throws IOException, JSONException {
        String src= FileUtil.readFileToString(f);
        JSONObject jo= new JSONObject(src);
        final Map<String,String> params= new HashMap();
        params.put( "script", jo.getString("script") );
        params.put( "param1", jo.getString("param1"));
        params.put( "param2", jo.getString("param2"));
        params.put( "param1Values", jo.getString("param1Values"));
        params.put( "param2Values", jo.getString("param2Values"));                
        Runnable run= new Runnable() {
            @Override
            public void run() {
                BatchMaster.this.param1NameCB.setSelectedItem(params.get("param1"));
                BatchMaster.this.param2NameCB.setSelectedItem(params.get("param2"));
                BatchMaster.this.param1Values.setText(params.get("param1Values"));
                BatchMaster.this.param2Values.setText(params.get("param2Values"));                
            }
        };
        try {
            SwingUtilities.invokeAndWait(run);
        } catch (InterruptedException | InvocationTargetException ex) {
            Logger.getLogger(BatchMaster.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private void saveFile( File f ) throws IOException, JSONException {
        JSONObject jo= new JSONObject();
        jo.put( "script", this.dataSetSelector1.getValue() );
        jo.put( "param1", this.param1NameCB.getSelectedItem().toString() );
        jo.put( "param2", this.param2NameCB.getSelectedItem().toString() );
        jo.put( "param1Values", this.param1Values.getText() );
        jo.put( "param2Values", this.param2Values.getText() );
        String src= jo.toString(4);
        FileUtil.writeStringToFile(f,src);
    }
    
    
    private void doGenerate( JComboBox cb, JTextArea ta ) {
        if ( cb.getSelectedItem()==null ) return;
        String p= cb.getSelectedItem().toString();
        p= p.trim();
        if ( p.length()>0 ) {
            try {
                org.autoplot.jythonsupport.JythonUtil.Param pd= getParamDescription( p );
                if ( pd==null ) return; // shouldn't happen
                String[] ss=null; // will be generated values
                if ( pd.type=='T' ) {
                    try {
                        if ( AutoplotUtil.showConfirmDialog( this, timeRangesPanel, "Generate Time Ranges", JOptionPane.OK_CANCEL_OPTION )==JOptionPane.OK_OPTION ) {
                            ss= ScriptContext.generateTimeRanges( timeFormatComboBox.getSelectedItem().toString(), timeRangeComboBox.getSelectedItem().toString() );
                        }
                    } catch (ParseException ex) {
                        Logger.getLogger(BatchMaster.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else if ( pd.enums!=null ) {
                    final JPanel panel= new JPanel();
                    panel.setLayout( new BoxLayout( panel, BoxLayout.Y_AXIS ) );
                    String label= pd.label;
                    if ( pd.doc!=null ) label= "<html>"+label+", <i>"+pd.doc+"</i>";
                    panel.add( new JLabel( label ) );
                    for ( int i=0; i<pd.enums.size(); i++ ) {
                        JCheckBox checkBox= new JCheckBox(pd.enums.get(i).toString());
                        checkBox.setSelected(true);
                        panel.add( checkBox );
                    }
                    AbstractAction a= new AbstractAction("clear all") {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            for ( Component c: panel.getComponents() ) {
                                if ( c instanceof JCheckBox ) {
                                    ((JCheckBox)c).setSelected(false);
                                }
                            }
                        }   
                    };
                    panel.add( new JButton(a) );
                    JScrollPane scrollPane= new JScrollPane(panel);
                    scrollPane.setPreferredSize( new Dimension( 300, 400 ) );
                    scrollPane.setMaximumSize( new Dimension( 300, 400 ) );
                    scrollPane.getVerticalScrollBar().setUnitIncrement(panel.getFont().getSize());
                    
                    if ( AutoplotUtil.showConfirmDialog( this, scrollPane, "Select from Values", JOptionPane.OK_CANCEL_OPTION )==JOptionPane.OK_OPTION ) {
                        List<String> theList= new ArrayList<>();
                        for ( Component c: panel.getComponents() ) {
                            if ( c instanceof JCheckBox ) {
                                if ( ( (JCheckBox) c).isSelected() ) {
                                    theList.add(((JCheckBox)c).getText());
                                }
                            }
                        }
                        ss= theList.toArray( new String[theList.size()] );
                    }
                } else if ( pd.type=='F' ) {
                    JPanel panel= new JPanel();
                    panel.setLayout( new BoxLayout( panel, BoxLayout.Y_AXIS ) );
                    String label= pd.label;
                    if ( pd.doc!=null ) label= "<html>"+label+", <i>"+pd.doc+"</i>";
                    panel.add( new JLabel( label ) );
                    JTextField min= new JTextField( "" );
                    JTextField max= new JTextField( "" );
                    JTextField step= new JTextField( "" );
                    boolean isInt;
                    min.setText( String.valueOf( pd.deft ) );
                    if ( pd.deft instanceof Integer ) {
                        max.setText( String.valueOf( ((Integer)pd.deft) + 4 ) );
                        step.setText( "1" ); 
                        isInt= true;
                    } else {
                        max.setText( String.valueOf( ((Number)pd.deft).doubleValue() + 10. ) );
                        step.setText( "0.1" ); 
                        isInt= false;
                    }
                    panel.add( new JLabel( "Minimum: " ) );
                    panel.add( min );
                    panel.add( new JLabel( "Maximum: " ) );
                    panel.add( max );
                    panel.add( new JLabel( "Step Size: " ) );
                    panel.add( step );
                    while ( AutoplotUtil.showConfirmDialog( this, panel, "Select range", JOptionPane.OK_CANCEL_OPTION )==JOptionPane.OK_OPTION ) {
                        List<String> theList= new ArrayList<>();
                        double dmin= Double.parseDouble(min.getText());
                        double dmax= Double.parseDouble(max.getText());
                        double dstep= Double.parseDouble(step.getText());
                        if ( dstep<=0 ) continue;
                        if ( dmax<dmin ) continue;
                        int ni= (int)(Math.round((dmax-dmin)/dstep))+1;
                        int digits= (int)( Math.floor( Math.log10(dstep) ) );
                        double dfac= 1;
                        String spec;
                        if ( digits<0 ) {
                            spec= "%."+(-digits)+"f";
                        } else {
                            spec= "%.0f";
                        }
                        for ( int i=0; i<ni; i++ ) {
                            double x= ( dmin + dstep * i ) * dfac;         
                            theList.add( isInt ? String.valueOf( (int)Math.round(x) ) : String.format(spec,x) );
                        }
                        ss= theList.toArray( new String[theList.size()] );
                        break;
                    }
                } else if ( pd.type=='R' ) {
                    String deft= String.valueOf(pd.deft);
                    File f= null;
                    try {
                        URISplit split= URISplit.parse(deft);
                        if ( split.path!=null && split.path.startsWith("file:") ) {
                            f= new File( split.path.substring(5) );
                        }
                    } catch ( IllegalArgumentException ex ) {
                    }
                    String lastItem= ta.getText().trim();
                    if ( lastItem.length()>0  ) {
                        int i= lastItem.lastIndexOf('\n');
                        lastItem= lastItem.substring(i+1);
                        URISplit split= URISplit.parse(lastItem);
                        if ( split.path!=null && split.path.startsWith("file:") ) {
                            f= new File( split.path.substring(5) );
                        }
                    }
                    JFileChooser cf= new JFileChooser();
                    if ( f!=null ) cf.setCurrentDirectory(f);
                    cf.setMultiSelectionEnabled(true);
                    if ( cf.showOpenDialog(this)==JFileChooser.APPROVE_OPTION ) {
                        File[] ff= cf.getSelectedFiles();
                        ss= new String[ff.length];
                        for ( int i=0; i<ff.length; i++ ) {
                            ss[i]= "file:"+ff[i].toString();
                        }
                    }
                } else if ( pd.type=='L' ) {
                    String deft= String.valueOf(pd.deft);
                    File f= null;
                    try {
                        URISplit split= URISplit.parse(deft);
                        if ( split.path!=null && split.path.startsWith("file:") ) {
                            f= new File( split.path.substring(5) );
                        }
                    } catch ( IllegalArgumentException ex ) {
                    }
                    String lastItem= ta.getText().trim();
                    if ( lastItem.length()>0  ) {
                        int i= lastItem.lastIndexOf('\n');
                        lastItem= lastItem.substring(i+1);
                        URISplit split= URISplit.parse(lastItem);
                        if ( split.path!=null && split.path.startsWith("file:") ) {
                            f= new File( split.path.substring(5) );
                        }
                    }
                    JFileChooser cf= new JFileChooser();
                    if ( f!=null ) cf.setCurrentDirectory(f);
                    cf.setMultiSelectionEnabled(true);
                    if ( cf.showOpenDialog(this)==JFileChooser.APPROVE_OPTION ) {
                        File[] ff= cf.getSelectedFiles();
                        ss= new String[ff.length];
                        for ( int i=0; i<ff.length; i++ ) {
                            ss[i]= "file:"+ff[i].toString();
                        }
                    }
                } else {
                    JOptionPane.showMessageDialog( this, "Parameter type isn't supported." );
                    return;
                }
                if ( ss!=null ) {
                    StringBuilder b= new StringBuilder();
                    for ( String s: ss ) b.append(s).append("\n");
                    ta.setText( b.toString() );
                    messageLabel.setText("Load up those parameters and hit Go!");
                    jScrollPane3.getViewport().setView(param1Values);
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog( this, "bad parameter name" );
            }
        }        
    }
    /**
     * return null or the parameter.
     * @param name the name
     * @return the Param or null.
     * @throws IOException 
     */
    private org.autoplot.jythonsupport.JythonUtil.Param getParamDescription( String name ) throws IOException {
        
        String scriptName= dataSetSelector1.getValue();
        URISplit split= URISplit.parse(scriptName);
        if ( !split.file.endsWith(".jy") ) {
            JOptionPane.showMessageDialog( BatchMaster.this, "script must end in .jy: "+scriptName );
            return null;
        }

        pwd= split.path;

        Map<String,String> params= URISplit.parseParams(split.params);  //TODO: support these.
        Map<String,Object> env= new HashMap<>();

        //DasProgressPanel monitor= DasProgressPanel.createFramed( SwingUtilities.getWindowAncestor(BatchMaster.this), "download script");
        File scriptFile= DataSetURI.getFile( split.file, new NullProgressMonitor() );
        String script= readScript( scriptFile );
        
        env.put("dom",this.dom);
        env.put("PWD",pwd);
                                
        Map<String,Param> parms= Util.getParams( env, script, params, new NullProgressMonitor() );

        Param p= parms.get(name);
        
        return p;        
        
    }
    
    /**
     * TODO: this is not complete!
     * @param interp
     * @param paramDescription
     * @param paramName
     * @param f1
     * @throws IOException 
     */
    private void setParam( InteractiveInterpreter interp, org.autoplot.jythonsupport.JythonUtil.Param paramDescription, 
            String paramName, String f1 ) throws IOException {
        if ( paramDescription==null ) {
            throw new IllegalArgumentException("expected to see parameter description!");
        }
        switch (paramDescription.type) {
            case 'U':
            case 'R':
                URI uri;
                try {
                    URISplit split= URISplit.parse(f1);
                    if ( split.path==null ) {
                        uri= new URI( pwd + f1 );
                    } else {
                        uri= new URI(f1);
                    }
                } catch ( URISyntaxException ex ) {
                    throw new IOException(ex);
                }   interp.set("_apuri", uri );
                interp.exec("autoplot2017.params[\'"+paramName+"\']=_apuri"); // JythonRefactory okay
                break;
            case 'L': 
                interp.exec("autoplot2017.params[\'"+paramName+"\']=URL(\'"+f1+"\')"); // JythonRefactory okay
                break;
            case 'A':
                interp.exec("autoplot2017.params[\'"+paramName+"\']=\'"+f1+"\'");// JythonRefactory okay
                break;
            case 'T':
                try {
                    DatumRange timeRange= DatumRangeUtil.parseTimeRange(f1);
                    interp.set("_apdr", timeRange );
                    interp.exec("autoplot2017.params[\'"+paramName+"\']=_apdr");// JythonRefactory okay
                } catch (ParseException ex) {
                    Logger.getLogger(BatchMaster.class.getName()).log(Level.SEVERE, null, ex);
                }   break;
            default:
                interp.exec("autoplot2017.params[\'"+paramName+"\']="+f1);// JythonRefactory okay
                break;
        }
        
    }
    
    private String pwd;
    
    /**
     * read the script into a string
     * @param f
     * @return
     * @throws FileNotFoundException
     * @throws IOException 
     */
    private String readScript( File f ) throws FileNotFoundException, IOException {
        StringBuilder build= new StringBuilder();
        BufferedReader r;
        r = new BufferedReader( new FileReader(f) );
        try {    
            String line= r.readLine();
            while ( line!=null ) {
                build.append(line).append("\n");
                line= r.readLine();
            }
        } finally {
            r.close();
        }        
        return build.toString();
    }
    
    /**
     * write the current canvas to a file.
     * @param f1
     * @param f2
     * @return the name of the file used.
     * @throws IOException 
     */
    private String doWrite( String f1, String f2 ) throws IOException {
        f1= f1.replaceAll("/", "_");
        f2= f2.replaceAll("/", "_");
        if ( writeCheckBox.isSelected() ) {
            String template= writeFilenameCB.getSelectedItem().toString();
            String[] ss= template.split("\\$x",-2);
            StringBuilder f= new StringBuilder(ss[0]);
            if ( ss.length>1 ) {
                f.append(f1).append(ss[1]);
            }
            if ( ss.length>2 ) {
                f.append(f2.trim()).append(ss[2]);
            }
            for ( int i=3; i<ss.length; i++ ) {
                f.append( ss[i] );
            }
            String s= f.toString();
            if ( s.endsWith(".png") ) {
                ScriptContext.writeToPng(s);
            } else if ( s.endsWith(".pdf") ) {
                ScriptContext.writeToPdf(s);
            } 
            return s;
        } else {
            return null;
        }
    }
    
    /**
     * run the batch process.  The
     * @throws IOException 
     */
    public void doIt() throws IOException {
        final ProgressMonitor monitor= DasProgressPanel.createFramed( SwingUtilities.getWindowAncestor(this), "Run Batch");

        Icon queued= new ImageIcon(BatchMaster.class.getResource("/resources/grey.gif"));
        Icon working= new ImageIcon(BatchMaster.class.getResource("/resources/blue_anime.gif"));
        Icon okay= new ImageIcon(BatchMaster.class.getResource("/resources/blue.gif"));
        Icon prob= new ImageIcon(BatchMaster.class.getResource("/resources/red.gif"));    
        
        List<JLabel> jobs;
        
        {
            String[] ff1= param1Values.getText().split("\n");
            JPanel p= new JPanel();
            jobs= new ArrayList<>();
            p.setLayout( new BoxLayout(p,BoxLayout.Y_AXIS) );
            for ( String f: ff1 ) {
                JLabel l= new JLabel(f);
                l.setIcon(queued);
                p.add( l );
                jobs.add(l);
            }
            
            p.addMouseListener( new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    messageLabel.setText("Load up those parameters and hit Go!");
                    jScrollPane3.getViewport().setView(param1Values);
                }
                @Override
                public void mouseClicked(MouseEvent e) {
                    messageLabel.setText("Load up those parameters and hit Go!");
                    jScrollPane3.getViewport().setView(param1Values);
                }
            });

            JScrollPane scrollp= new JScrollPane(p);
            scrollp.getVerticalScrollBar().setUnitIncrement( scrollp.getFont().getSize());
            scrollp.setPreferredSize( new Dimension(640,640));
            scrollp.setMaximumSize( new Dimension(640,640));
            
            messageLabel.setText("Running jobs, click on labels above to edit.");
            jScrollPane3.getViewport().setView(p);
            
        }
        
        try {
            String scriptName= dataSetSelector1.getValue();
            
            URISplit split= URISplit.parse(scriptName);
            pwd= split.path;
            
            if ( !split.file.endsWith(".jy") ) {
                JOptionPane.showMessageDialog( this, "script must end in .jy: "+scriptName );
                return;
            }

            String[] ff1= param1Values.getText().split("\n");

            monitor.setTaskSize(ff1.length);
            monitor.started();
            
            Map<String,String> params= URISplit.parseParams(split.params);
            Map<String,Object> env= new HashMap<>();
            env.put("dom",this.dom);
            env.put("PWD",pwd);

            File scriptFile= DataSetURI.getFile( split.file, monitor.getSubtaskMonitor("download script") );
            String script= readScript( scriptFile );
            
            Map<String,org.autoplot.jythonsupport.JythonUtil.Param> parms= Util.getParams( env, script, params, new NullProgressMonitor() );

            InteractiveInterpreter interp = JythonUtil.createInterpreter( true, false );
            interp.exec(JythonRefactory.fixImports("import autoplot2017")); 
            
            ParametersFormPanel pfp= new org.autoplot.jythonsupport.ui.ParametersFormPanel();
            pfp.doVariables( env, scriptFile, params, null );
            for ( Entry<String,String> ent: params.entrySet() ) {
                try {
                    pfp.getFormData().implement( interp, ent.getKey(), ent.getValue() );
                } catch (ParseException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }

            if ( writeCheckBox.isSelected() ) {
                String template= writeFilenameCB.getSelectedItem().toString();
                if ( !( template.endsWith(".pdf") || template.endsWith(".png") ) ) {
                    AutoplotUtil.showConfirmDialog( this, "write template must end in .pdf or .png", "Write Template Error", JOptionPane.OK_OPTION );
                    return;
                }
            }
            
            JSONObject jo= new JSONObject();
            JSONArray ja= new JSONArray();
            
            jo.put( "results", ja );
             
            
            String param1= param1NameCB.getSelectedItem()!=null ? 
                    param1NameCB.getSelectedItem().toString().trim() :
                    "";
            String param2= param2NameCB.getSelectedItem()!=null ?
                    param2NameCB.getSelectedItem().toString().trim() :
                    "";
            
            JSONArray paramsJson= new JSONArray();
            paramsJson.put(0,param1);
            if ( param2.length()>0 ) {
                paramsJson.put(1,param2);
            }
            jo.put("params", paramsJson );
            
            monitor.setTaskSize( ff1.length );
            int icount=0;
            int i1=0;
            int exportResultsWritten=0;
            
            for ( String f1 : ff1 ) {
                JSONObject runResults= new JSONObject();
                try {
                    if ( monitor.isCancelled() ) {
                        break;
                    }
                    monitor.setProgressMessage(f1);
                    monitor.setTaskProgress(monitor.getTaskProgress()+1);

                    if ( f1.trim().length()==0 ) {
                        i1++;
                        continue;
                    }

                    jobs.get(i1).setIcon(working);
                    //interp.set( "monitor", monitor.getSubtaskMonitor(f1) );
                    interp.set( "monitor", new NullProgressMonitor() {
                        @Override
                        public boolean isCancelled() {
                            return monitor.isCancelled();
                        }
                    }); // subtask would reset indeterminate.
                    interp.set( "dom", this.dom );
                    interp.set( "PWD", split.path );
                    String paramName= param1NameCB.getSelectedItem().toString();
                    if ( !parms.containsKey(paramName) ) {
                        if ( paramName.trim().length()==0 ) {
                            throw new IllegalArgumentException("param1Name not set");
                        }
                    }
                    setParam( interp, parms.get(paramName), paramName, f1 );
                    runResults.put(paramName,f1);
                    
                    if ( param2NameCB.getSelectedItem().toString().trim().length()==0 ) {
                        long t0= System.currentTimeMillis();
                        try {
                            interp.execfile( JythonRefactory.fixImports( new FileInputStream(scriptFile),scriptFile.getName()), scriptFile.getName() );
                            runResults.put("executionTime", System.currentTimeMillis()-t0);
                            if ( writeCheckBox.isSelected() ) {
                                runResults.put( "writeFile", doWrite( f1, "" ) );
                            }
                            jobs.get(i1).setIcon(okay);
                            jobs.get(i1).setToolTipText(null);
                        } catch ( IOException | JSONException ex ) {
                            runResults.put("executionTime", System.currentTimeMillis()-t0);                            
                            String msg= ex.toString();
                            runResults.put("result",msg);
                            jobs.get(i1).setIcon(prob);
                            jobs.get(i1).setToolTipText(ex.toString());
                        }
                        JSONObject copy = new JSONObject(runResults, JSONObject.getNames(runResults));
                        ja.put( icount, copy );
                        icount++;                        
                        
                    } else {
                        String[] ff2= param2Values.getText().split("\n");
                        int i2=0;
                        String problemMessage= null;
                        for ( String f2: ff2 ) {
                            if ( f2.trim().length()==0 ) continue;
                            if ( monitor.isCancelled() ) {
                                break;
                            }
                            long t0= System.currentTimeMillis();
                            try {
                                paramName= param2NameCB.getSelectedItem().toString();
                                runResults.put(paramName,f2);
                                setParam( interp, parms.get(paramName), paramName, f2 );
                                interp.execfile(  JythonRefactory.fixImports(new FileInputStream(scriptFile)), scriptFile.getName() );
                                runResults.put("executionTime", System.currentTimeMillis()-t0);
                                i2=i2+f2.length()+1;
                                if ( writeCheckBox.isSelected() ) {
                                    runResults.put( "writeFile", doWrite( f1,f2 ) );
                                }
                                runResults.put("result","");
                            } catch ( IOException | JSONException | RuntimeException ex ) {
                                runResults.put("executionTime", System.currentTimeMillis()-t0);
                                String msg= ex.toString();
                                runResults.put("result",msg);
                                jobs.get(i1).setIcon(prob);
                                jobs.get(i1).setToolTipText(ex.toString());
                                problemMessage= msg;
                            }
                            JSONObject copy = new JSONObject(runResults, JSONObject.getNames(runResults));
                            ja.put( icount, copy );
                            icount++;
                        }
                        if ( problemMessage==null ) {
                            jobs.get(i1).setIcon(okay);
                            jobs.get(i1).setToolTipText(null);
                        }
                    }
                } catch (IOException | RuntimeException | JSONException ex) {
                    Logger.getLogger(BatchMaster.class.getName()).log(Level.SEVERE, null, ex);
                    jobs.get(i1).setIcon(prob);
                    jobs.get(i1).setToolTipText(ex.toString());
                }
                i1=i1+1;
                
                if ( resultsFile!=null ) {
                    File pendingResultsFile= new File( resultsFile.getAbsolutePath()+".pending" );
                    exportResultsPending( pendingResultsFile, jo, ja, exportResultsWritten );
                    exportResultsWritten= icount;
                }
                
                JSONObject pendingResults= new JSONObject( jo.toString() );
                pendingResults.put( "results", new JSONArray( ja.toString() ) );
            }
            
            jo.put( "results", ja );
            results= jo;
            
        } catch (JSONException ex) {
            logger.log(Level.SEVERE, null, ex);
            
        } finally {
            
            messageLabel.setText("Jobs are complete, click above to edit.");
            monitor.finished();
            goButton.setEnabled(true);
        }
    }
    
    public static void main( String[] args ) {
        JDialog dia= new JDialog();
        dia.setResizable(true);
        BatchMaster mmm= new BatchMaster(new Application());
        dia.setContentPane( mmm );
        dia.setJMenuBar( mmm.getMenuBar() );
        mmm.param1NameCB.setSelectedItem("ie");
        mmm.dataSetSelector1.setValue("/home/jbf/ct/autoplot/script/demos/paramTypes.jy");
        mmm.param1Values.setText("1\n2\n3\n");
        dia.pack();
        dia.setVisible(true);
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem OpenMenuItem;
    private javax.swing.JMenuItem SaveAsMenuItem;
    private javax.swing.JButton cancelButton;
    private org.autoplot.datasource.DataSetSelector dataSetSelector1;
    private javax.swing.JMenuItem exportResultsMenuItem;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JButton generateButton1;
    private javax.swing.JButton generateButton2;
    private javax.swing.JButton goButton;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JList<String> jList2;
    private javax.swing.JMenuItem jMenuItem1;
    private javax.swing.JMenuItem jMenuItem2;
    private javax.swing.JPopupMenu jPopupMenu1;
    private javax.swing.JPopupMenu jPopupMenu2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JMenuItem loadUriMenuItem;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JLabel messageLabel;
    private javax.swing.JComboBox<String> param1NameCB;
    private javax.swing.JTextArea param1Values;
    private javax.swing.JComboBox<String> param2NameCB;
    private javax.swing.JTextArea param2Values;
    private javax.swing.JMenuItem showHelpMenuItem;
    private javax.swing.JComboBox<String> timeFormatComboBox;
    private javax.swing.JComboBox<String> timeRangeComboBox;
    private javax.swing.JPanel timeRangesPanel;
    private javax.swing.JCheckBox writeCheckBox;
    private javax.swing.JComboBox<String> writeFilenameCB;
    private org.jdesktop.beansbinding.BindingGroup bindingGroup;
    // End of variables declaration//GEN-END:variables

    private static void exportResultsPending( File pendingFile, JSONObject results, JSONArray resultsArray, int recordsWrittenAlready ) throws FileNotFoundException, IOException {
        
        boolean header= recordsWrittenAlready==0;
        
        try (PrintWriter out = new PrintWriter( new FileWriter( pendingFile, true ) ) ) {
            
            if ( resultsArray.length()==0 ) {
                logger.warning("no records in results");
                return;
            }

            JSONObject jo= resultsArray.getJSONObject(0);
            boolean hasOutputFile= jo.has("writeFile");
            JSONArray params= results.getJSONArray("params");

            StringBuilder record;
                
            if ( header ) {
                record= new StringBuilder();
                record.append("jobNumber");

                for ( int j=0; j<params.length(); j++ ) {
                    record.append(",");
                    record.append(params.get(j));
                }
                record.append(",").append("executionTime(ms)");
                if ( hasOutputFile ) {
                    record.append(",").append("writeFile");
                }
                record.append(",").append("exception");

                out.println(record.toString());

            }
            
            for ( int i=recordsWrittenAlready; i<resultsArray.length(); i++ ) {
                jo= resultsArray.getJSONObject(i);
                record= new StringBuilder();
                record.append(i);
                for ( int j=0; j<params.length(); j++ ) {
                    record.append(",");
                    record.append( jo.get(params.getString(j)) );
                }
                record.append(",").append(jo.get("executionTime"));
                if ( hasOutputFile ) {
                    record.append(",").append(jo.get("writeFile"));
                }
                record.append(",").append(jo.get("result"));
                out.println( record.toString() );
            }
        } catch (JSONException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
}
