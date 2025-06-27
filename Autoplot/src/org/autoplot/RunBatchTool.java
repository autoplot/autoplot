
package org.autoplot;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
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
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
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
import org.autoplot.dom.ApplicationController;
import org.autoplot.jythonsupport.Param;
import org.autoplot.jythonsupport.ui.ParametersFormPanel;
import org.autoplot.jythonsupport.ui.Util;
import org.autoplot.pngwalk.PngWalkTool;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.qds.DataSetUtil;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;
import org.das2.util.DasPNGConstants;
import org.das2.util.FileUtil;
import org.das2.util.monitor.AlertNullProgressMonitor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Tool for running batches, generating inputs for jython scripts.
 * @see https://sourceforge.net/p/autoplot/feature-requests/545/
 * @author jbf
 */
public class RunBatchTool extends javax.swing.JPanel {

    private static final Logger logger= LoggerManager.getLogger("jython.batchmaster");
    
    private Application dom;
    
    private Object state; 
    
    private static final String STATE_READY= "ready";
    private static final String STATE_LOADING= "loading";
    
    private JSONObject results=null;
    private JSONObject resultsPending=null;
    private File resultsFile=null;
    
    private JLabel[] param1JLabels= null;
    
    /**
     * 1, 2, or more than 2 params.
     */
    private org.autoplot.jythonsupport.Param[] parameterDescriptions;
    
    private Map<JLabel,String> jobs= new HashMap<>();
    
    public static final int HTML_LINE_LIMIT = 50;
            
    private ProgressMonitor monitor=null; // non-null when process is going.
        
    /**
     * Creates new form BatchMaster
     * @param dom
     */
    public RunBatchTool( final Application dom ) {
        initComponents();

        writeFilenameCB.getEditor().getEditorComponent().addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                checkNumberOfParams();
            }
        });
        
        Preferences prefs= Preferences.userNodeForPackage(RunBatchTool.class );
        String s= prefs.get( "lastTemplate", null );
        if ( s!=null ) {
            writeFilenameCB.setSelectedItem(s);
        }
        
        generateButton1.setEnabled(false);
        generateButton2.setEnabled(false);
        generateMenuItem1.setEnabled(false);
        generateMenuItem2.setEnabled(false);
        this.dom= dom;
        this.state= STATE_READY;
        
        /**
         * register the browse trigger to the same action, because we always browse.
         */
        dataSetSelector1.registerBrowseTrigger("(.*)\\.jy(\\?.*)?", new AbstractAction( "Review Script" ) {
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
                    if ( JOptionPane.OK_OPTION==JythonUtil.showScriptDialog(RunBatchTool.this, 
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
                doPlayButton();
            }
        });
        
        dataSetSelector1.setPromptText("Enter the name of a Jython script");
        //dataSetSelector1.setRecent( Collections.singletonList() );
        
        List<String> recentUris= new ArrayList<>(20);
        recentUris.add( "http://autoplot.org/data/script/examples/parameters.jy" );
        if ( dom.getController()!=null ) { // support testing.
            Pattern p= Pattern.compile(".*\\.jy(\\?.*)?");
            Map<String,String> recentJy= dom.getController().getApplicationModel().getRecent(p,20);
            recentJy.entrySet().forEach((recentItem) -> {
                recentUris.add( recentItem.getKey() );
            });
        }
        dataSetSelector1.setRecent( recentUris );
        
        param1ScrollPane.getVerticalScrollBar().setUnitIncrement(param1ScrollPane.getFont().getSize());
        param2ScrollPane.getVerticalScrollBar().setUnitIncrement(param2ScrollPane.getFont().getSize());
        
        
    }
    
    /**
     * check that the number of parameters matches the number of wildcards.
     * This is disabled unless property 
     * autoplot.option.runbatch.validate is set to "T"
     */
    private void checkNumberOfParams() {
        if ( "T".equals(System.getProperty("autoplot.option.runbatch.validate","T")) ) {
            String s= writeFilenameCB.getEditor().getItem().toString();
            int fields1= s.split("\\$|\\%",-2).length-1;
            String pp1= param1NameCB.getSelectedItem()!=null ?
                param1NameCB.getSelectedItem().toString().trim() :
                "";
            int npp1= pp1.length()==0 ? 0 : ( pp1.split("\\;",-2).length );
            String pp2= param2NameCB.getSelectedItem()!=null ?
                param2NameCB.getSelectedItem().toString().trim() :
                "";
            int npp2= pp2.length()==0 ? 0 : ( pp2.split("\\;",-2).length );

            if ( npp1 + npp2 != fields1 && writeCheckBox.isSelected() ) {
                writeFilenameCB.getEditor().getEditorComponent().setBackground( Color.YELLOW );
            } else {
                if ( writeFilenameCB.getEditor().getEditorComponent().getForeground()==Color.WHITE ) {
                    writeFilenameCB.getEditor().getEditorComponent().setBackground( Color.BLACK );
                } else {
                    writeFilenameCB.getEditor().getEditorComponent().setBackground( Color.WHITE );
                }
            }
        }
    }
    
    /**
     * do the stuff to do when the play button is pressed.
     */
    private void doPlayButton() {
        state= STATE_LOADING;                  
        try {
            String scriptName= dataSetSelector1.getValue();
            URISplit split= URISplit.parse(scriptName);
            if ( !split.file.endsWith(".jy") ) {
                JOptionPane.showMessageDialog(RunBatchTool.this, "script must end in .jy: "+scriptName );
                return;
            }

            pwd= split.path;

            //Map<String,String> params= URISplit.parseParams(split.params);  //TODO: support these.
            Map<String,Object> env= new HashMap<>();

            DasProgressPanel monitor= DasProgressPanel.createFramed(SwingUtilities.getWindowAncestor(RunBatchTool.this), "download script");
            File scriptFile= DataSetURI.getFile( split.file, monitor );
            String script= readScript( scriptFile );

            env.put( "dom", dom );
            env.put( "PWD", split.path );

            Map<String,org.autoplot.jythonsupport.Param> parms= Util.getParams( env, script, URISplit.parseParams(split.params), new NullProgressMonitor() );

            String[] items= new String[parms.size()+2];
            int i=0;
            items[0]="";
            for ( Entry<String,org.autoplot.jythonsupport.Param> p: parms.entrySet() ) {
                items[i+1]= p.getKey();
                i=i+1;
            }
            items[parms.size()+1]= "Select Multiple...";
            ComboBoxModel m1= new DefaultComboBoxModel(Arrays.copyOfRange(items,1,items.length));
            param1NameCB.setModel(m1);
            generateButton1.setEnabled( items.length>1 );
            generateMenuItem1.setEnabled( items.length>1 );
            ComboBoxModel m2= new DefaultComboBoxModel(items);
            param2NameCB.setModel(m2);

            param1Values.setText("");
            param2Values.setText("");
            
            switchToEditableList();

            messageLabel.setText("Load up those parameters and hit Go!");
            param1ScrollPane.getViewport().setView(param1Values);


        } catch (IOException ex) {
            Logger.getLogger(RunBatchTool.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            state= STATE_READY;
        }
        
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
        generateMenuItem1 = new javax.swing.JMenuItem();
        loadUriMenuItem = new javax.swing.JMenuItem();
        loadFromFileMI = new javax.swing.JMenuItem();
        pasteMenuItem = new javax.swing.JMenuItem();
        timeRangesPanel = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        timeRangeComboBox = new javax.swing.JComboBox<>();
        timeFormatComboBox = new javax.swing.JComboBox<>();
        jLabel3 = new javax.swing.JLabel();
        jPopupMenu2 = new javax.swing.JPopupMenu();
        generateMenuItem2 = new javax.swing.JMenuItem();
        loadUriMenuItem2 = new javax.swing.JMenuItem();
        loadFromFileMI2 = new javax.swing.JMenuItem();
        pasteMenuItem2 = new javax.swing.JMenuItem();
        menuBar = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        OpenMenuItem = new javax.swing.JMenuItem();
        SaveAsMenuItem = new javax.swing.JMenuItem();
        exportResultsMenuItem = new javax.swing.JMenuItem();
        helpMenu = new javax.swing.JMenu();
        showHelpMenuItem = new javax.swing.JMenuItem();
        jPanel1 = new javax.swing.JPanel();
        postRunPopupMenu = new javax.swing.JPopupMenu();
        copyScriptUri = new javax.swing.JMenuItem();
        rerunScriptMenuItem = new javax.swing.JMenuItem();
        copyValueMenuItem = new javax.swing.JMenuItem();
        goButton = new javax.swing.JButton();
        param1ScrollPane = new javax.swing.JScrollPane();
        param1Values = new javax.swing.JTextArea();
        param2ScrollPane = new javax.swing.JScrollPane();
        param2Values = new javax.swing.JTextArea();
        dataSetSelector1 = new org.autoplot.datasource.DataSetSelector();
        messageLabel = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        param1NameCB = new javax.swing.JComboBox<>();
        param2NameCB = new javax.swing.JComboBox<>();
        closeButton = new javax.swing.JButton();
        generateButton1 = new javax.swing.JButton();
        generateButton2 = new javax.swing.JButton();
        writeCheckBox = new javax.swing.JCheckBox();
        writeFilenameCB = new javax.swing.JComboBox<>();
        progressPanel = new javax.swing.JPanel();
        editParamsButton = new javax.swing.JButton();
        pngWalkToolButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();

        jList2.setModel(new javax.swing.AbstractListModel<String>() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public String getElementAt(int i) { return strings[i]; }
        });
        jList2.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jList2MouseClicked(evt);
            }
        });
        jScrollPane2.setViewportView(jList2);

        generateMenuItem1.setText("Generate...");
        generateMenuItem1.setToolTipText("Generate items for list");
        generateMenuItem1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                generateMenuItem1ActionPerformed(evt);
            }
        });
        jPopupMenu1.add(generateMenuItem1);

        loadUriMenuItem.setText("Load Events File...");
        loadUriMenuItem.setToolTipText("Load a list of time ranges from an events file.");
        loadUriMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadUriMenuItemActionPerformed(evt);
            }
        });
        jPopupMenu1.add(loadUriMenuItem);

        loadFromFileMI.setText("Load from File...");
        loadFromFileMI.setToolTipText("Load lines from file into this text area");
        loadFromFileMI.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadFromFileMIActionPerformed(evt);
            }
        });
        jPopupMenu1.add(loadFromFileMI);

        pasteMenuItem.setText("Paste");
        pasteMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pasteMenuItemActionPerformed(evt);
            }
        });
        jPopupMenu1.add(pasteMenuItem);

        jLabel2.setText("Time Range:");

        timeRangeComboBox.setEditable(true);
        timeRangeComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Jun 2000", "2000", "2000-01-01/03-01", "2000-2016" }));

        timeFormatComboBox.setEditable(true);
        timeFormatComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "$Y-$m-$d", "$Y", "$Y-$m", "$Y_$j", "$Y-$m-$dT$H/PT1H", "$Y-$m-$dT$(H;delta=6)/PT6H", "$Y-$m-$dT$H$M/PT1M", "$Y-$m-$dT$H$M$S/PT1S", "$(o;id=rbspa-pp)" }));
        timeFormatComboBox.setToolTipText("Use the droplist to select from options, and make edits if necessary.");

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

        generateMenuItem2.setText("Generate...");
        generateMenuItem2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                generateMenuItem2ActionPerformed(evt);
            }
        });
        jPopupMenu2.add(generateMenuItem2);

        loadUriMenuItem2.setText("Load Events File...");
        loadUriMenuItem2.setToolTipText("Load a list of time ranges from an events file.");
        loadUriMenuItem2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadUriMenuItem2ActionPerformed(evt);
            }
        });
        jPopupMenu2.add(loadUriMenuItem2);

        loadFromFileMI2.setText("Load from File");
        loadFromFileMI2.setToolTipText("Load lines from file into this text area");
        loadFromFileMI2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadFromFileMI2ActionPerformed(evt);
            }
        });
        jPopupMenu2.add(loadFromFileMI2);

        pasteMenuItem2.setText("Paste");
        pasteMenuItem2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pasteMenuItem2ActionPerformed(evt);
            }
        });
        jPopupMenu2.add(pasteMenuItem2);

        fileMenu.setText("File");

        OpenMenuItem.setText("Open batch file...");
        OpenMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                OpenMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(OpenMenuItem);

        SaveAsMenuItem.setText("Save Batch File As...");
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

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 100, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 100, Short.MAX_VALUE)
        );

        copyScriptUri.setText("Copy Script URI");
        copyScriptUri.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copyScriptUriActionPerformed(evt);
            }
        });
        postRunPopupMenu.add(copyScriptUri);

        rerunScriptMenuItem.setText("Re-Run Script");
        rerunScriptMenuItem.setToolTipText("Re run the script with these arguments");
        rerunScriptMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rerunScriptMenuItemActionPerformed(evt);
            }
        });
        postRunPopupMenu.add(rerunScriptMenuItem);

        copyValueMenuItem.setText("Copy Value to Clipboard");
        copyValueMenuItem.setToolTipText("");
        copyValueMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copyValueMenuItemActionPerformed(evt);
            }
        });
        postRunPopupMenu.add(copyValueMenuItem);

        goButton.setText("Go!");
        goButton.setToolTipText("Run the batch processes, holding shift to run independent processes in parallel.");
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
        param1ScrollPane.setViewportView(param1Values);

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
        param2ScrollPane.setViewportView(param2Values);

        messageLabel.setText("Load up those parameters and hit Go!");

        jLabel1.setText("<html>This tool generates inputs for scripts, running through a series of inputs.  First load the script with the green \"play\" button, then specify the parameter name and values to assign, and optionally a second parameter.  Each value of the second parameter is run for each value of the first.  Use the inspect button to set values for any other parameters. Right-click within the values areas to generate values.");
        jLabel1.setVerticalAlignment(javax.swing.SwingConstants.TOP);

        param1NameCB.setEditable(true);
        param1NameCB.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { " " }));
        param1NameCB.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                param1NameCBItemStateChanged(evt);
            }
        });
        param1NameCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                param1NameCBActionPerformed(evt);
            }
        });

        param2NameCB.setEditable(true);
        param2NameCB.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { " " }));
        param2NameCB.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                param2NameCBItemStateChanged(evt);
            }
        });
        param2NameCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                param2NameCBActionPerformed(evt);
            }
        });

        closeButton.setText("Close");
        closeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeButtonActionPerformed(evt);
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
        writeCheckBox.setToolTipText("After each iteration, write the file, where each $x is replaced with the parameter value.  The number \nof $x fields must match the number of parameters controlled.  Note the script name and its arguments\nare embedded within each .vap, and the pngwalk tool can be used to relaunch the script for any run.");
        writeCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                writeCheckBoxActionPerformed(evt);
            }
        });

        writeFilenameCB.setEditable(true);
        writeFilenameCB.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "/tmp/ap/$x.png", "/tmp/ap/$x_$x.png", "/tmp/ap/$x_$x.png", "/tmp/ap/$x_$x_$x.png", "/tmp/ap/$x_$x_$x_$x.png", "/tmp/ap/$x.pdf", "/tmp/ap/$x_$x.pdf", "/tmp/ap/%s_%s.pdf", "/tmp/ap/%s_%s_%02d.png", " ", " " }));

        org.jdesktop.beansbinding.Binding binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, writeCheckBox, org.jdesktop.beansbinding.ELProperty.create("${selected}"), writeFilenameCB, org.jdesktop.beansbinding.BeanProperty.create("enabled"));
        bindingGroup.addBinding(binding);

        writeFilenameCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                writeFilenameCBActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout progressPanelLayout = new javax.swing.GroupLayout(progressPanel);
        progressPanel.setLayout(progressPanelLayout);
        progressPanelLayout.setHorizontalGroup(
            progressPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 497, Short.MAX_VALUE)
        );
        progressPanelLayout.setVerticalGroup(
            progressPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 51, Short.MAX_VALUE)
        );

        editParamsButton.setText("Edit Parameter Values");
        editParamsButton.setEnabled(false);
        editParamsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editParamsButtonActionPerformed(evt);
            }
        });

        pngWalkToolButton.setText("PNG Walk Tool");
        pngWalkToolButton.setToolTipText("Open template in the PNG Walk Tool");
        pngWalkToolButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pngWalkToolButtonActionPerformed(evt);
            }
        });

        cancelButton.setText("Cancel");
        cancelButton.setToolTipText("cancel task.  Note tasks must be checking for cancel to terminate immediately.");
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(dataSetSelector1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(param1NameCB, javax.swing.GroupLayout.PREFERRED_SIZE, 246, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(generateButton1))
                            .addComponent(param1ScrollPane))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(param2ScrollPane)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(param2NameCB, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(generateButton2))))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(writeCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(writeFilenameCB, javax.swing.GroupLayout.PREFERRED_SIZE, 238, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pngWalkToolButton))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(messageLabel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(progressPanel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 74, Short.MAX_VALUE)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addComponent(cancelButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(goButton, javax.swing.GroupLayout.PREFERRED_SIZE, 62, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(closeButton))
                            .addComponent(editParamsButton, javax.swing.GroupLayout.Alignment.TRAILING))))
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
                    .addComponent(param1ScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 279, Short.MAX_VALUE)
                    .addComponent(param2ScrollPane))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(writeCheckBox, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(writeFilenameCB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(pngWalkToolButton)))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(22, 22, 22)
                        .addComponent(editParamsButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(goButton)
                            .addComponent(closeButton)
                            .addComponent(cancelButton)))
                    .addGroup(layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(messageLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(progressPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );

        bindingGroup.bind();
    }// </editor-fold>//GEN-END:initComponents

    private void goButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_goButtonActionPerformed
        if ( !goButton.isEnabled() ) {
            return;
        }
        goButton.setEnabled(false);
        messageLabel.setText("Setting up to run jobs...");
        Runnable run= () -> {
            try {
                String scriptName= dataSetSelector1.getValue();
                dom.getController().getApplicationModel().addRecent(scriptName);
                Preferences prefs= Preferences.userNodeForPackage( RunBatchTool.class );
                int threadCount= prefs.getInt(PREF_THREAD_COUNT,8);
                if ( true ) {
                    String warning="<html><p>Multiple processes can run at the same time, generally<br>"
                            + "the number of threads should equal the number of CPU cores, beyond that<br>"
                            + "performance will probably not scale.  Note older versions of<br>"
                            + "Autoplot, before v2025a_6, did not support this fully.<br><br>"
                        + "Proceed?</p></html>";
                    JPanel p= new JPanel( );
                    p.setLayout( new BoxLayout( p, BoxLayout.Y_AXIS ) );
                    JLabel l= new JLabel(warning);
                    l.setAlignmentX( JLabel.LEFT_ALIGNMENT );
                    l.setHorizontalAlignment( SwingConstants.LEFT );
                    p.add( l );
                    
                    JPanel p2= new JPanel();
                    p2.setLayout( new BoxLayout( p2, BoxLayout.X_AXIS ) );
                    JTextField tf= new JFormattedTextField( threadCount );
                    p2.add( new JLabel("Number of threads:") );
                    p2.add( tf );
                    int size= tf.getFont().getSize();
                    tf.setMaximumSize( new Dimension( size*5, size*2 ) );
                    tf.setPreferredSize( new Dimension( size*5, size*2 ) );

                    p2.setAlignmentX( JPanel.LEFT_ALIGNMENT  );
                    
                    p.add( p2 );
                        
                    if ( JOptionPane.OK_OPTION==JOptionPane.showConfirmDialog( param1NameCB, p, 
                        "Multi-Thread warning", JOptionPane.OK_CANCEL_OPTION ) ) {
                        threadCount= Integer.parseInt(tf.getText());
                        prefs.putInt(PREF_THREAD_COUNT, threadCount );
                        doIt( threadCount );
                    } else {
                        goButton.setEnabled(true);
                    }
                } else {
                    doIt();
                }
            } catch (IOException ex) {
                messageLabel.setText(ex.getMessage());
            }
        };
        new Thread(run,"runBatch").start();
    }//GEN-LAST:event_goButtonActionPerformed
    private static final String PREF_THREAD_COUNT = "threadCount";

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

    private void generateMenuItem1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_generateMenuItem1ActionPerformed
        doGenerate( param1NameCB, param1Values );
    }//GEN-LAST:event_generateMenuItem1ActionPerformed

    private void param2ValuesMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_param2ValuesMouseClicked
        if ( evt.isPopupTrigger() ) {
            jPopupMenu2.show( evt.getComponent(), evt.getX(), evt.getY() );
        }
    }//GEN-LAST:event_param2ValuesMouseClicked

    private void generateMenuItem2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_generateMenuItem2ActionPerformed
        doGenerate( param2NameCB, param2Values );
    }//GEN-LAST:event_generateMenuItem2ActionPerformed

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

    private void closeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButtonActionPerformed
        Window w=SwingUtilities.getWindowAncestor(this);
        if ( ! ( w instanceof JDialog ) ) {
            logger.warning("untested code might leave hidden windows...");
        }
        ProgressMonitor mon= this.monitor;
        if ( mon!=null ) {
            mon.cancel();
        }
        
        w.setVisible(false);
    }//GEN-LAST:event_closeButtonActionPerformed

    private void loadUriMenuItemAction(JTextArea paramValues) {
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
                paramValues.setText(ss.toString());
            } catch (Exception ex) {
                Logger.getLogger(RunBatchTool.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }                                               

    private void loadUriMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadUriMenuItemActionPerformed
        loadUriMenuItemAction(param1Values);
    }//GEN-LAST:event_loadUriMenuItemActionPerformed

    private void generateButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_generateButton2ActionPerformed
        doGenerate( param2NameCB, param2Values );
    }//GEN-LAST:event_generateButton2ActionPerformed

    private void generateButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_generateButton1ActionPerformed
        doGenerate( param1NameCB, param1Values );
    }//GEN-LAST:event_generateButton1ActionPerformed

    private void param1NameCBItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_param1NameCBItemStateChanged
        if ( evt.getStateChange()==ItemEvent.SELECTED ) {
            if ( param1NameCB.getSelectedIndex()==param1NameCB.getItemCount()-1 ) {
                doSelectMultiple(param1NameCB,param1NameCB.getSelectedItem());
                return;
            }
            boolean present= param1NameCB.getSelectedItem().toString().trim().length()>0;
            generateButton1.setEnabled( present );
            generateMenuItem1.setEnabled( present );
            param1ScrollPane.getViewport().setView(param1Values);
            param2ScrollPane.getViewport().setView(param2Values);
            messageLabel.setText("Load up those parameters and hit Go!");
        }
    }//GEN-LAST:event_param1NameCBItemStateChanged

    private void param2NameCBItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_param2NameCBItemStateChanged
        if ( evt.getStateChange()==ItemEvent.SELECTED ) {
            if ( param2NameCB.getSelectedIndex()==param2NameCB.getItemCount()-1 ) {
                doSelectMultiple(param2NameCB,param2NameCB.getSelectedItem());
                return;
            }
            boolean present= param2NameCB.getSelectedItem().toString().trim().length()>0;
            generateButton2.setEnabled( present );
            generateMenuItem2.setEnabled( present );
        }
    }//GEN-LAST:event_param2NameCBItemStateChanged

    private void OpenMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_OpenMenuItemActionPerformed
        JFileChooser chooser= new JFileChooser();
        chooser.setFileFilter( new FileNameExtensionFilter( "Batch Parameters", "batch") );
        chooser.setDialogType( JFileChooser.OPEN_DIALOG );
        Preferences prefs= Preferences.userNodeForPackage(RunBatchTool.class );
        String s= prefs.get("batch",null);
        if ( s!=null ) {
            chooser.setSelectedFile(new File(s));
        }
        if ( JFileChooser.APPROVE_OPTION==chooser.showOpenDialog( this ) ) {
            final File f= chooser.getSelectedFile();
            prefs.put("batch", f.toString() );
            Runnable run= () -> {
                try {
                    loadBatchFile( f );
                } catch (IOException|JSONException ex) {
                    JOptionPane.showMessageDialog(RunBatchTool.this, "Unable to open file. "+ex.getMessage() );
                }
            };
            new Thread(run).start();
        }
    }//GEN-LAST:event_OpenMenuItemActionPerformed

    private void SaveAsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SaveAsMenuItemActionPerformed
        JFileChooser chooser= new JFileChooser();
        chooser.setFileFilter( new FileNameExtensionFilter( "Batch Parameters", "batch") );
        chooser.setDialogType( JFileChooser.OPEN_DIALOG );
        Preferences prefs= Preferences.userNodeForPackage(RunBatchTool.class );
        String s= prefs.get("batch",null);
        if ( s!=null ) {
            chooser.setSelectedFile(new File(s));
        }
        if ( JFileChooser.APPROVE_OPTION==chooser.showSaveDialog( this ) ) {
            File ff= chooser.getSelectedFile();
            if ( !ff.getName().endsWith(".batch") ) {
                ff= new File( ff.getAbsolutePath()+".batch");
            }
            final File f= ff;
            
            prefs.put("batch", f.toString() );
            Runnable run= () -> {
                try {
                    saveFile( f );
                } catch (IOException|JSONException ex) {
                    JOptionPane.showMessageDialog(RunBatchTool.this, "Unable to save file. "+ex.getMessage() );
                }
            };
            new Thread(run).start();
        }
    }//GEN-LAST:event_SaveAsMenuItemActionPerformed

    private void exportResultsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportResultsMenuItemActionPerformed
        JFileChooser chooser= new JFileChooser();
        chooser.addChoosableFileFilter( new FileNameExtensionFilter( "CSV Files", "csv") );
        chooser.addChoosableFileFilter( new FileNameExtensionFilter( "JSON Files", "json") );
        chooser.setDialogType( JFileChooser.SAVE_DIALOG );
        Preferences prefs= Preferences.userNodeForPackage(RunBatchTool.class );
        String s= prefs.get("export",null);
        if ( s!=null ) {
            chooser.setSelectedFile(new File(s));
        }
        if ( JFileChooser.APPROVE_OPTION==chooser.showSaveDialog( this ) ) {
            File ff= chooser.getSelectedFile();
            if ( !(ff.getName().endsWith(".csv")||ff.getName().endsWith(".json")) ) {
                ff= new File( ff.getAbsolutePath()+".csv");
            }
            final File f= ff;
            resultsFile= f;
            
            if ( results==null ) {
                String msg= "Output will be written to "+f+".pending and moved after the run.";
                JOptionPane.showMessageDialog(RunBatchTool.this, msg );
                return;
            }
            prefs.put("export", f.toString() );
            String message0= messageLabel.getText();
            Runnable run= () -> {
                try {
                    if ( results==null ) {
                        exportResults(f,resultsPending);
                        JOptionPane.showMessageDialog(RunBatchTool.this, "pending results saved to "+f );
                    } else {
                        exportResults( f,results );
                        JOptionPane.showMessageDialog(RunBatchTool.this, "data saved to "+f );
                    }
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(RunBatchTool.this, "Unable to save file. "+ex.getMessage() );
                } catch (JSONException ex) {
                    JOptionPane.showMessageDialog(RunBatchTool.this, "Unable to save file because of JSON exception "+ex.getMessage() );
                }
                messageLabel.setText(message0);
            };
            messageLabel.setText("writing to "+ff + "...");
            new Thread(run).start();
        }
    }//GEN-LAST:event_exportResultsMenuItemActionPerformed

    private void showHelpMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showHelpMenuItemActionPerformed
        AutoplotUtil.openBrowser("https://github.com/autoplot/documentation/blob/main/md/batch.md");
    }//GEN-LAST:event_showHelpMenuItemActionPerformed

    
    private void loadFromFileMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadFromFileMIActionPerformed
        doLoadFromFile(param1Values);
    }//GEN-LAST:event_loadFromFileMIActionPerformed

    private void loadFromFileMI2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadFromFileMI2ActionPerformed
        doLoadFromFile(param2Values);
    }//GEN-LAST:event_loadFromFileMI2ActionPerformed

    private void pasteMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pasteMenuItemActionPerformed
        try {
            String pasteMe= (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            param1Values.setText(pasteMe);
        } catch (UnsupportedFlavorException | IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_pasteMenuItemActionPerformed

    private void pasteMenuItem2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pasteMenuItem2ActionPerformed
        try {
            String pasteMe= (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            param2Values.setText(pasteMe);
        } catch (UnsupportedFlavorException | IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_pasteMenuItem2ActionPerformed

    private void editParamsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editParamsButtonActionPerformed
        switchToEditableList();
        editParamsButton.setEnabled(false);
    }//GEN-LAST:event_editParamsButtonActionPerformed

    private void pngWalkToolButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pngWalkToolButtonActionPerformed
        String template= writeFilenameCB.getSelectedItem().toString();
        template= convertStringFormatToUriTemplate( template );
        PngWalkTool.start( template, SwingUtilities.getWindowAncestor(this) );
    }//GEN-LAST:event_pngWalkToolButtonActionPerformed

    private void jList2MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jList2MouseClicked
        jPopupMenu2.show( this, evt.getX(), evt.getY() );
    }//GEN-LAST:event_jList2MouseClicked

    private void writeFilenameCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_writeFilenameCBActionPerformed
        checkNumberOfParams();
    }//GEN-LAST:event_writeFilenameCBActionPerformed

    private void copyScriptUriActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyScriptUriActionPerformed
        JLabel p= getSelectedLabel();
        String uri= jobs.get(p);
        if ( uri!=null ) {
            System.err.println(uri);
            StringSelection stringSelection= new StringSelection( uri );
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents( stringSelection, null );
            messageLabel.setText("URI copied to system clipboard.");
        } else {
            messageLabel.setText("Unable to find script URI.");
            System.err.println("internal error...");
        }
        
    }//GEN-LAST:event_copyScriptUriActionPerformed

    private void rerunScriptMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rerunScriptMenuItemActionPerformed
        JLabel jLabel1= getSelectedLabel();
        
        if ( !param2NameCB.getSelectedItem().toString().trim().equals("") ) {
            JOptionPane.showMessageDialog( this, "Rerun is not supported with two arguments");
            return;
        }
        String argName= param1NameCB.getSelectedItem().toString();
        String argValue= jLabel1.getText();
        
        String scriptName= dataSetSelector1.getValue();

        URISplit split= URISplit.parse(scriptName);
        pwd= split.path;
        
        Map<String,Object> env= new HashMap<>();
        env.put("dom",this.dom);
        env.put("PWD",pwd);
            
        try {
            final File scriptFile= DataSetURI.getFile( split.file, new AlertNullProgressMonitor() );
            String script= readScript( scriptFile );
            Map<String,String> params= URISplit.parseParams( split.params );
            Map<String,org.autoplot.jythonsupport.Param> parms= Util.getParams( env, script, params, new NullProgressMonitor() );
            Runnable run= () -> {
                doOneJob( jLabel1, scriptFile, parms, params, argName, argValue, new NullProgressMonitor() );
            };
            jLabel1.setIcon( ICON_WORKING );
            new Thread( run, "run-batch-0" ).start();
                
        } catch (IOException ex) {
            Logger.getLogger(RunBatchTool.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        
    }//GEN-LAST:event_rerunScriptMenuItemActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        ProgressMonitor mon= this.monitor;
        if ( mon!=null ) {
            mon.cancel();
        }
        cancelButton.setEnabled(false);
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void copyValueMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyValueMenuItemActionPerformed
        JLabel p= getSelectedLabel();
        String argValue= p.getText();
        System.err.println(argValue);
        StringSelection stringSelection= new StringSelection( argValue );
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents( stringSelection, null );
        messageLabel.setText("Copied to system clipboard: "+argValue );
    }//GEN-LAST:event_copyValueMenuItemActionPerformed

    private void loadUriMenuItem2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadUriMenuItem2ActionPerformed
        loadUriMenuItemAction(param2Values);
    }//GEN-LAST:event_loadUriMenuItem2ActionPerformed

    private void param1NameCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_param1NameCBActionPerformed
        checkNumberOfParams();
    }//GEN-LAST:event_param1NameCBActionPerformed

    private void param2NameCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_param2NameCBActionPerformed
        checkNumberOfParams();
    }//GEN-LAST:event_param2NameCBActionPerformed

    private void writeCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_writeCheckBoxActionPerformed
        checkNumberOfParams();
    }//GEN-LAST:event_writeCheckBoxActionPerformed

    private void doLoadFromFile( JTextArea paramValues ) {
        JFileChooser chooser= new JFileChooser();
        chooser.setFileFilter( new FileNameExtensionFilter( "Text Files", "txt") );
        chooser.setDialogType( JFileChooser.OPEN_DIALOG );
        Preferences prefs= Preferences.userNodeForPackage(RunBatchTool.class );
        String s= prefs.get("textfile",null);
        if ( s!=null ) {
            chooser.setSelectedFile(new File(s));
        }
        if ( JFileChooser.APPROVE_OPTION==chooser.showOpenDialog( this ) ) {
            readFromFile(chooser,paramValues);
            prefs.put("textfile",chooser.getSelectedFile().toString());
        }
    }
    
    private void readFromFile(JFileChooser chooser, final JTextArea paramValues ) {
        final StringBuilder b= new StringBuilder();
        try {    
            try ( BufferedReader read= new BufferedReader( new FileReader(chooser.getSelectedFile()) ) ) {
                String l= read.readLine();
                while ( l!=null ) {
                    if ( l.trim().length()>0 ) {
                        b.append(l).append("\n");
                    }
                    l= read.readLine();
                }
            }
        } catch ( IOException ex ) {
            logger.log( Level.WARNING, null, ex );
        }
        Runnable run= () -> {
            paramValues.setText(b.toString());
        };
        SwingUtilities.invokeLater(run);
            
    }

    private void exportResults( File f, JSONObject results ) throws IOException, JSONException {
        if ( results==null ) {
            return;
        }
        if ( f.getName().endsWith(".json") ) {
            String sresults= results.toString(3);
            FileUtil.writeStringToFile( f, sresults );
        } else {
            appendResultsPendingCSV( f, results, results.getJSONArray("results"), 0, results.getJSONArray("results").length() );
        }
    }
    
    private void loadBatchFile( File f ) throws IOException, JSONException {
        if ( SwingUtilities.isEventDispatchThread() ) throw new IllegalArgumentException("don't call from event thread");
        String src= FileUtil.readFileToString(f);
        JSONObject jo= new JSONObject(src);
        final Map<String,String> params= new HashMap();
        String scriptName1= jo.getString("script");
        scriptName1= scriptName1.replaceAll("\\%\\{PWD\\}",f.getParentFile().getCanonicalPath() );
        final String scriptName= scriptName1;
        
        params.put( "script", scriptName );
        Runnable run= () -> {
            RunBatchTool.this.dataSetSelector1.setValue(scriptName);
            doPlayButton();
        };
        try {
            SwingUtilities.invokeAndWait(run);
        } catch (InterruptedException | InvocationTargetException ex) {
            logger.log(Level.SEVERE, null, ex);
        } 
        
        params.put( "param1", jo.getString("param1"));
        params.put( "param2", jo.getString("param2"));
        params.put( "param1Values", jo.getString("param1Values"));
        params.put( "param2Values", jo.getString("param2Values"));                
        run= () -> {
            RunBatchTool.this.param1NameCB.setSelectedItem(params.get("param1"));
            RunBatchTool.this.param2NameCB.setSelectedItem(params.get("param2"));
            RunBatchTool.this.param1Values.setText(params.get("param1Values"));
            RunBatchTool.this.param2Values.setText(params.get("param2Values"));
        };
        try {
            SwingUtilities.invokeAndWait(run);
        } catch (InterruptedException | InvocationTargetException ex) {
            Logger.getLogger(RunBatchTool.class.getName()).log(Level.SEVERE, null, ex);
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
    
    /**
     * return the lines generated by the GUI, or null.
     * @param pd
     * @return 
     */
    private String[] doGenerateOne( org.autoplot.jythonsupport.Param pd ) {
        String[] ss=null; // will be generated values
        if ( pd.type=='T' || ( pd.type=='S' && UnitsUtil.isTimeLocation(((DatumRange)pd.deft).getUnits()) ) ) {
            try {
                if ( AutoplotUtil.showConfirmDialog( this, timeRangesPanel, "Generate Time Ranges", JOptionPane.OK_CANCEL_OPTION )==JOptionPane.OK_OPTION ) {
                    String template= timeFormatComboBox.getSelectedItem().toString();
                    Pattern p= Pattern.compile("\\$\\(o[,;]id=([a-zA-Z\\-_]+)\\)");
                    Matcher m= p.matcher(template);
                    if ( m.matches() ) {
                        String id= m.group(1);
                        template= "orbit:"+id+":"+template;
                    }
                    ss= ScriptContext.generateTimeRanges(template, timeRangeComboBox.getSelectedItem().toString() );
                }
            } catch (ParseException ex) {
                Logger.getLogger(RunBatchTool.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else if ( pd.enums!=null ) {
            final JPanel panel= new JPanel();
            panel.setLayout( new BoxLayout( panel, BoxLayout.Y_AXIS ) );
            String label= pd.label;
            if ( pd.doc!=null ) label= "<html>"+label+", <i>"+pd.doc+"</i>";
            panel.add( new JLabel( label ) );
            List<String> labels= (List<String>)pd.constraints.get( Param.CONSTRAINT_LABELS );
            for ( int i=0; i<pd.enums.size(); i++ ) {
                String ll= pd.enums.get(i).toString();
                if ( labels!=null ) ll=ll+": "+labels.get(i);
                JCheckBox checkBox= new JCheckBox(ll);
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
                            String t= ((JCheckBox)c).getText();
                            int icolon= t.indexOf(": ");
                            if ( icolon>-1 ) {
                                t= t.substring(0,icolon);
                            }
                            theList.add(t);
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
                step.setText( "1" ); 
                isInt= true;
            } else {
                max.setText( String.valueOf( ((Number)pd.deft).doubleValue() + 10. ) );
                step.setText( "0.1" ); 
                isInt= false;
            }
            if ( pd.constraints.containsKey("min") ) {
                min.setText( String.valueOf( pd.constraints.get("min") ) );
            }
            if ( pd.constraints.containsKey("max") ) {
                max.setText( String.valueOf( pd.constraints.get("max") ) );
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
        } else if ( pd.type=='L' || pd.type=='M' || ( pd.type=='A' && "file".equals(pd.constraints.get("stringType"))) ) {
            String deft= String.valueOf(pd.deft);
            File f= null;
            try {
                URISplit split= URISplit.parse(deft);
                if ( split.path!=null && split.path.startsWith("file:") ) {
                    f= new File( split.path.substring(5) );
                }
            } catch ( IllegalArgumentException ex ) {
            }
            String lastItem= ""; //ta.getText().trim();
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
                if ( pd.type=='L' ) {
                    for ( int i=0; i<ff.length; i++ ) {
                        ss[i]= "file:"+ff[i].toString();
                    }
                } else if ( pd.type=='M' || ( pd.type=='A' && "file".equals(pd.constraints.get("stringType")) ) ) {
                    for ( int i=0; i<ff.length; i++ ) {
                        ss[i]= ff[i].toString();
                    }
                }
            }
        } else {
            JTextArea ta= new JTextArea( 5, 20 );
            JPanel p= new JPanel();
            p.setLayout( new BoxLayout( p, BoxLayout.Y_AXIS ) );
            p.add( new JLabel("GUI is not available, manually enter values:") );
            p.add( ta );
            if ( JOptionPane.OK_OPTION==JOptionPane.showConfirmDialog( this, p, "Manually enter", JOptionPane.OK_CANCEL_OPTION ) ) {
                ss= ta.getText().split("\n");
            } else {
                return null;
            }
        }
        return ss;

    }
    
    /**
     * create a sequence of dialogs and then run through all permutations of 
     * the inputs.  TODO: Redo this with each variable's generator in line,
     * all in one GUI.
     * @param cb
     * @param ta 
     */
    private void doGenerateMulti( JComboBox cb, JTextArea ta ) {
        String p= cb.getSelectedItem().toString();
        String[] pps= maybeSplitMultiParam( p );
        char splitChar= p.charAt(pps[0].length());
        String[][] rs1= new String[pps.length][];
        for ( int i=0; i<pps.length; i++ ) {
            p= pps[i].trim();
            try {
                org.autoplot.jythonsupport.Param pd= getParamDescription( p );
                rs1[i]= doGenerateOne(pd);
                if ( rs1[i]==null ) {
                    return;
                }
            }catch (IOException ex) {
                JOptionPane.showMessageDialog( this, "bad parameter name" );
            }
        }
        StringBuilder sb= new StringBuilder();
        if ( pps.length==2 ) {
            for (String item0 : rs1[0]) {
                for (String item1 : rs1[1]) {
                    sb.append(item0);
                    sb.append( splitChar );
                    sb.append(item1);
                    sb.append( "\n" );
                }
            }
        } else if ( pps.length==3 ) {
            for (String item0 : rs1[0]) {
                for (String item1 : rs1[1]) {
                    for (String item2 : rs1[2]) {
                        sb.append(item0);
                        sb.append( splitChar );
                        sb.append(item1);
                        sb.append( splitChar );
                        sb.append(item2);
                        sb.append( "\n" );
                    }
                }
            }   
        }
        ta.setText( sb.toString() );
        messageLabel.setText("Load up those parameters and hit Go!");
        switchToEditableList();
    }
    
        
    private void doGenerate( JComboBox cb, JTextArea ta ) {
        if ( cb.getSelectedItem()==null ) return;
        String p= cb.getSelectedItem().toString();
        p= p.trim();
        if ( p.length()>0 ) {
            try {
                String[] pps= maybeSplitMultiParam( p );
                if ( pps!=null ) {
                    doGenerateMulti( cb, ta );
                    return;
                }
                org.autoplot.jythonsupport.Param pd= getParamDescription( p );
                if ( pd==null ) return; // shouldn't happen
                String[] ss= doGenerateOne(pd);
                if ( ss==null ) {
                    logger.fine("cancelled");
                } else {
                    StringBuilder b= new StringBuilder();
                    for ( String s: ss ) b.append(s).append("\n");
                    ta.setText( b.toString() );
                    messageLabel.setText("Load up those parameters and hit Go!");
                    switchToEditableList();
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
    private org.autoplot.jythonsupport.Param getParamDescription( String name ) throws IOException {
        
        String scriptName= dataSetSelector1.getValue();
        URISplit split= URISplit.parse(scriptName);
        if ( !split.file.endsWith(".jy") ) {
            JOptionPane.showMessageDialog(RunBatchTool.this, "script must end in .jy: "+scriptName );
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
    private static void setParam( InteractiveInterpreter interp, String pwd, org.autoplot.jythonsupport.Param paramDescription, 
            String paramName, String f1 ) throws IOException {
        if ( paramDescription==null ) {
            throw new IllegalArgumentException("expected to see parameter description!");
        }
        switch (paramDescription.type) {
            case 'U':
            case 'R':
                if ( f1.startsWith("'") && f1.endsWith("'") && f1.length()>1 ) {
                    f1= f1.substring(1,f1.length()-1);
                }
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
                interp.exec("autoplot2023.params[\'"+paramName+"\']=_apuri"); // JythonRefactory okay
                break;
            case 'L': 
                interp.exec("autoplot2023.params[\'"+paramName+"\']=URL(\'"+f1+"\')"); // JythonRefactory okay
                break;
            case 'M':
                interp.exec("from java.io import File");
                interp.exec("autoplot2023.params[\'"+paramName+"\']=File(\'"+f1+"\')"); // JythonRefactory okay
                break;
            case 'A':
                if ( f1.startsWith("'") && f1.endsWith("'") && f1.length()>1 ) {
                    f1= f1.substring(1,f1.length()-1);
                }
                interp.exec("autoplot2023.params[\'"+paramName+"\']=\'"+f1+"\'");// JythonRefactory okay
                break;
            case 'T':
                try {
                    DatumRange timeRange= DatumRangeUtil.parseTimeRange(f1);
                    interp.set("_apdr", timeRange );
                    interp.exec("autoplot2023.params[\'"+paramName+"\']=_apdr");// JythonRefactory okay
                } catch (ParseException ex) {
                    Logger.getLogger(RunBatchTool.class.getName()).log(Level.SEVERE, null, ex);
                }   break;
            default:
                interp.exec("autoplot2023.params[\'"+paramName+"\']="+f1);// JythonRefactory okay
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
     * @param uri
     * @param dom if non-null, use this application for the image.
     * @return the name of the file used.
     * @throws IOException 
     */
    private String doWrite( String f1, String f2, String uri, Application dom) throws IOException {
        if ( !writeCheckBox.isSelected() ) {
            return null;
        }
                
        String template= writeFilenameCB.getSelectedItem().toString();
        Preferences prefs= Preferences.userNodeForPackage( RunBatchTool.class );
        prefs.put( "lastTemplate", template );

        template= template.replaceAll("\\$x","%s");
        
        f1= f1.replaceAll("/", "_");
        f2= f2.replaceAll("/", "_");
        f1= f1.replaceAll(" ", "_");
        f2= f2.replaceAll(" ", "_");
        f1= f1.replaceAll(":", "_"); // times
        f2= f2.replaceAll(":", "_");
        
        List<String> argList= new ArrayList<>();
        if ( f1.contains(";") ) {
            String[] ss= f1.split("\\;",-2);
            for ( String s: ss ) {
                argList.add(s);
            }
        } else {
            if ( f1.trim().length()>0 ) {
                argList.add(f1);
            }
        }
        if ( f2.contains(";") ) {
            String[] ss= f2.split("\\;",-2);
            for ( String s: ss ) {
                argList.add(s);
            }
        } else {
            if ( f2.trim().length()>0 ) {
                argList.add(f2);
            }
        }
        
        // now the tricky part will be to pull out all the fields from the template.
        String[] ss= template.split("\\%");
        
        boolean packArgments=false;
        if ( argList.size() != ss.length-1 ) {
            if ( ss.length==3 ) {
                packArgments= true;
            } else {
                throw new IllegalArgumentException("template and number of parameters don't match");
            }
        }

        Object[] args= new Object[argList.size()];
        for ( int i=0; i<argList.size(); i++ ) {
            if ( i>ss.length ) {
                System.err.println("Do somethng here");
            }
            String spec;
            if ( packArgments ) {
                spec= "x";
            } else {
                spec= ss[i+1];
            }
            int idx= 0; // find the first letter
            char c= spec.length()>0 ? spec.charAt(0) : ' ';
            while ( idx<spec.length() && ( c=='-' || c=='.' || Character.isDigit(c) ) ) {
                idx++;
                c= spec.length()>0 ? spec.charAt(idx) : ' ';
            }
            if ( idx==spec.length() ) {
                throw new IllegalArgumentException("expected to see non-digit in template after %");
            }
            char letter= spec.charAt(idx);
            if ( letter=='s' ) {
                args[i]= argList.get(i);
            } else {
                switch (letter) {
                    case 'd':
                        args[i]= Integer.parseInt(argList.get(i));
                        break;
                    case 'f':
                    case 'e':
                        args[i]= Double.parseDouble(argList.get(i));
                        break;
                    default:
                        args[i]= argList.get(i);
                        break;
                }
            }
        }
                
        String s;
        if ( packArgments ) {
            s= String.format( template, f1, f2 );
        } else {
            s= String.format( template, args );
        }
        
        s= s.replaceAll(" ","_"); 

        if ( s.endsWith(".png") ) {
            BufferedImage bufferedImage = dom.getController().getScriptContext().writeToBufferedImage(); 
            Map<String,String> metadata= new LinkedHashMap<>();
            metadata.put( "ScriptURI",uri );
            if ( dom!=null ) {
                metadata.put( DasPNGConstants.KEYWORD_PLOT_INFO, 
                    dom.getController().getApplicationModel().canvas.getImageMetadata() );
            }
            dom.getController().getScriptContext().writeToPng(bufferedImage,s,metadata);
        } else if ( s.endsWith(".pdf") ) {
            dom.getController().getScriptContext().writeToPdf(s);
        } 
        return s;

    }
    
    private static final Icon ICON_QUEUED= new ImageIcon(RunBatchTool.class.getResource("/resources/grey.gif"));
    private static final Icon ICON_WORKING= new ImageIcon(RunBatchTool.class.getResource("/resources/blue_anime.gif"));
    private static final Icon ICON_OKAY= new ImageIcon(RunBatchTool.class.getResource("/resources/blue.gif"));
    private static final Icon ICON_PROB= new ImageIcon(RunBatchTool.class.getResource("/resources/red.gif"));    

    private void switchToEditableList() {
        messageLabel.setText("Load up those parameters and hit Go!");
        param1ScrollPane.getViewport().setView(param1Values);
        param2ScrollPane.getViewport().setView(param2Values);
    }
    
    private void selectRecord( int irec ) {
        for ( int i=0; i<param1JLabels.length; i++ ) {
            param1JLabels[i].setBackground(Color.white);
            param1JLabels[i].setBorder(BorderFactory.createEmptyBorder());
        }
        if ( irec>-1 ) {
            param1JLabels[irec].setBackground(Color.GRAY);
            param1JLabels[irec].setBorder(BorderFactory.createLineBorder(Color.black));
        }
    }
    
    private JPanel switchListToIconLabels( List<JLabel> jobs1, String[] ff1 ) {
        JPanel p= new JPanel();
            
        p.setLayout( new BoxLayout(p,BoxLayout.Y_AXIS) );
        for ( String f: ff1 ) {
            JLabel l= new JLabel(f);
            l.setIcon(ICON_QUEUED);
            p.add( l );
            jobs1.add(l);
        }

        JScrollPane scrollp= new JScrollPane(p);
        scrollp.getVerticalScrollBar().setUnitIncrement( scrollp.getFont().getSize());
        scrollp.setPreferredSize( new Dimension(640,640));
        scrollp.setMaximumSize( new Dimension(640,640));
            
        messageLabel.setText("Running jobs, mouse over to view tooltip containing standard output.");
        return p;
    }
    
    /**
     * make an HTML rendering of the text, possibly truncating it to 50 lines.
     * @param txt
     * @return 
     */
    private static String htmlize( String txt ) {
        String[] ss= txt.split("\n");
        StringBuilder sb= new StringBuilder("<html>\n");
        int iline=0;
        for ( String s: ss ) {
            sb.append( s );
            sb.append( "<br>\n");
            iline++;
            if ( iline>HTML_LINE_LIMIT ) {
                sb.append("&npsp;&nbsp;(").append(ss.length-HTML_LINE_LIMIT).append(" more lines...)<br>");
                break;
            }
        }
        sb.append("</html>");
        return sb.toString();
    }

    /**
     * make an HTML rendering of the text, possibly truncating it to 50 lines.
     * @param txt
     * @return 
     */
    private static String htmlize( String txt, String stderr ) {
        StringBuilder sb= new StringBuilder("<html>\n");

        int iline=0;
        
        if ( txt.trim().length()>0 ) {
            String[] ss= txt.split("\n");
            for ( String s: ss ) {
                sb.append( s );
                sb.append( "<br>\n");
                iline++;
                if ( iline>HTML_LINE_LIMIT ) {
                    sb.append("&npsp;&nbsp;(").append(ss.length-HTML_LINE_LIMIT).append(" more lines...)<br>");
                    break;
                }
            }
            if ( stderr.trim().length()>0 ) {
                sb.append("<b>stderr:</b><br>");
            }
                
        }
        
        if ( stderr.trim().length()>0 ) {
            String[] ss= stderr.split("\n");        
            for ( String s: ss ) {
                sb.append( s );
                sb.append( "<br>\n");
                iline++;
                if ( iline>HTML_LINE_LIMIT ) {
                    sb.append("&npsp;&nbsp;(").append(ss.length-HTML_LINE_LIMIT).append(" more lines...)<br>");
                    break;
                }
            }            
        }
        
        sb.append("</html>");
        return sb.toString();
    }
    
    /**
     * if the parameter name contains a split character then return the names.
     * This is just so we can experiment with the feature.
     * @param param
     * @return null or the names
     */
    private static String[] maybeSplitMultiParam( String param ) {
        if ( param.contains("|") ) {
            return param.split( "\\|", -2 );
        } else if ( param.contains(",") ) {
            return param.split( ",", -2 );
        } else if ( param.contains(";") ) {
            return param.split( ";", -2 );
        } else {
            return null;
        }
    }
    
    /**
     * run the batch process.  
     * @throws IOException 
     */
    public void doIt() throws IOException {
        doIt(0);
    } 
    
    /**
     * 
     * @param jobLabel
     * @param scriptFile
     * @param parms
     * @param params
     * @param paramName
     * @param paramValue
     * @param monitor monitor for the job
     * @return 
     */
    private JSONObject doOneJob( JLabel jobLabel, 
        File scriptFile,
        Map<String,Param> parms, 
        Map<String,String> params, 
        String paramName, 
        String paramValue, 
        final ProgressMonitor monitor ) {
        
        URISplit split= URISplit.parse(scriptFile.toString());
        
        paramValue= paramValue.trim();
        paramName= paramName.trim();

        JSONObject runResults= new JSONObject();

        try {
            this.dom.getController().getScriptContext();
            
            ApplicationModel appmodel = new ApplicationModel();
            appmodel.addDasPeersToAppAndWait();

            Application myDom= appmodel.getDom();
            ScriptContext2023 scriptContext= new ScriptContext2023();
            myDom.getController().setScriptContext( scriptContext );
        
            if ( !scriptContext.isModelInitialized() ) {
                scriptContext.setApplicationModel(appmodel);
                scriptContext.setView(null);
                scriptContext._setDefaultApp(dom.getController().maybeGetApplicatonGUI());
            }
            
            myDom.getController().getScriptContext();
            
            ProgressMonitor myMonitor= new NullProgressMonitor() {
                @Override
                public boolean isCancelled() {
                    return monitor.isCancelled();
                }
            }; // subtask would reset indeterminate.
            
            InteractiveInterpreter interp = JythonUtil.createInterpreter( true, false, myDom, myMonitor );
            interp.exec(JythonRefactory.fixImports("import autoplot2023")); 
            Map<String,Object> scriptParams= new LinkedHashMap<>();
            scriptParams.putAll( params );

            if ( monitor.isCancelled() ) {
                return null;
            }

            jobLabel.setIcon(ICON_WORKING);
            interp.set( "PWD", split.path );
            String[] paramNames= maybeSplitMultiParam( paramName );

            if ( paramNames!=null ) {
                char splitc= paramName.charAt(paramNames[0].length());
                String[] paramValues= paramValue.trim().split("\\"+splitc);
                for ( int j= 0; j<paramNames.length; j++ ) {
                    String p= paramNames[j].trim();
                    String v= paramValues[j].trim();
                    if ( !parms.containsKey(p) ) {
                        if ( p.trim().length()==0 ) {
                            throw new IllegalArgumentException("param1Name not set");
                        } else {
                            throw new IllegalArgumentException("param not found: " + p );
                        }
                    }
                    setParam( interp, pwd, parms.get(p), p, v );
                    runResults.put( p, v );
                    scriptParams.put( p, v );
                }
            } else {
                if ( !parms.containsKey(paramName) ) {
                    if ( paramName.length()==0 ) {
                        throw new IllegalArgumentException("param1Name not set");
                    }
                }
                // set all the default values, and values set for all runs
                for ( Entry<String,String> e: params.entrySet() ) {
                    String pname= e.getKey();
                    if ( parms.get(pname)!=null ) { //TODO: When does this happen?  See file:/Users/jbf/Desktop/git/dev/demos/2025/20250130/brokenParamDescription.jy?resourceURI='https://pds-ppi.igpp.ucla.edu/data/JNO-J_SW-JAD-5-CALIBRATED-V1.0/DATA/2018/2018091/ELECTRONS/JAD_L50_HRS_ELC_TWO_DEF_2018091_V01.LBL'&doplot=False
                        setParam( interp, pwd, parms.get(pname), pname, e.getValue() );
                    }
                }
                setParam( interp, pwd, parms.get(paramName), paramName, paramValue );
                runResults.put(paramName,paramValue);
                scriptParams.put(paramName,paramValue);
            }

            if ( param2NameCB.getSelectedItem().toString().trim().length()==0 ) {
                long t0= System.currentTimeMillis();
                ByteArrayOutputStream outbaos= new ByteArrayOutputStream();
                try {
                    param1ScrollPane.scrollRectToVisible( jobLabel.getBounds() );
                    interp.setOut(outbaos);
                    interp.execfile( JythonRefactory.fixImports( new FileInputStream(scriptFile),scriptFile.getName()), scriptFile.getName() );
                    String uri= URISplit.format( "script", split.resourceUri.toString(), scriptParams );
                    if ( writeCheckBox.isSelected() ) {
                        runResults.put("writeFile", doWrite(paramValue, "", uri, myDom ) );
                    }
                    jobLabel.setIcon(ICON_OKAY);
                    jobs.put( jobLabel, uri );
                } catch ( IOException | JSONException | RuntimeException ex ) {
                    String msg= ex.toString();
                    runResults.put("result",msg);
                    jobLabel.setIcon(ICON_PROB);
                } finally {
                    outbaos.close();
                    runResults.put("stdout", new String(outbaos.toByteArray(),"US-ASCII") );
                    runResults.put("executionTime", System.currentTimeMillis()-t0);                            
                    System.out.println(runResults.getString("stdout"));
                }
                if ( jobLabel.getIcon()==ICON_OKAY ) {
                    jobLabel.setToolTipText( htmlize(runResults.getString("stdout")) );
                } else {
                    jobLabel.setToolTipText( htmlize(runResults.getString("stdout"),runResults.getString("result")));
                }


                JSONObject copy = new JSONObject(runResults, JSONObject.getNames(runResults));

                return copy;

            } else {
                throw new IllegalArgumentException("only one argument for multi-threaded version");
            }
                
        } catch ( IOException | RuntimeException | JSONException ex) {
            Logger.getLogger(RunBatchTool.class.getName()).log(Level.SEVERE, null, ex);
            jobLabel.setIcon(ICON_PROB);
            jobLabel.setToolTipText(htmlize(ex.toString()));
            String[] names= JSONObject.getNames(runResults);
            if ( names!=null ) {
                JSONObject copy = new JSONObject( runResults,names );
                return copy;
            } else {
                return runResults;
            }
        }

    }
        
    /**
     * experiment to try multi-threaded approach to running multiple processes at once.
     * @param threadCount number of concurrent threads.
     * @throws IOException 
     */
    public void doItMultiThreadOneArgument( int threadCount ) throws IOException {
        final DasProgressPanel monitor= DasProgressPanel.createComponent( "" );
        progressPanel.add( monitor.getComponent() );
        this.monitor= monitor;
        monitor.started();

        jobs.clear();
        final List<JLabel> jobs1= new ArrayList<>();
        final List<JLabel> jobs2= new ArrayList<>();
        
        editParamsButton.setEnabled(true);

        final AtomicInteger threadCounter= new AtomicInteger(0);
        
        ThreadFactory tf= (Runnable r) -> new Thread( r, "run-batch-"+threadCounter.incrementAndGet());
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadCount,tf);
        //ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(8);
        //ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool(tf);
        
        String scriptName= dataSetSelector1.getValue();

        URISplit split= URISplit.parse(scriptName);
        pwd= split.path;

        if ( !split.file.endsWith(".jy") ) {
            JOptionPane.showMessageDialog( this, "script must end in .jy: "+scriptName );
            return;
        }
        
        {
            String[] ff1= param1Values.getText().split("\n");
            JPanel p= switchListToIconLabels( jobs1, ff1 );

            for ( int i=0; i<jobs1.size(); i++ ) {
                final int fi= i;
                jobs1.get(i).addMouseListener(getJobsMouseListener( fi, jobs1, jobs2 ) );
            }

            param1JLabels= jobs1.toArray( new JLabel[jobs1.size()] );
            param1ScrollPane.getViewport().setView(p);   
        }
               
        if ( param2Values.getText().trim().length()>0 ) {
            String[] ff1= param2Values.getText().split("\n");
            JPanel p= switchListToIconLabels( jobs2, ff1 );
            param2ScrollPane.getViewport().setView(p);   
        } 
        
        try {

            String[] ff1= param1Values.getText().split("\n");

            monitor.setTaskSize(ff1.length);
            monitor.started();
            
            Map<String,String> splitParams= URISplit.parseParams(split.params);
            Map<String,Object> env= new HashMap<>();
            env.put("dom",this.dom);
            env.put("PWD",pwd);

            final File scriptFile= DataSetURI.getFile( split.file, monitor.getSubtaskMonitor("download script") );
            String script= readScript( scriptFile );
            
            Map<String,org.autoplot.jythonsupport.Param> parms= Util.getParams( env, script, splitParams, new NullProgressMonitor() );

            InteractiveInterpreter interp = JythonUtil.createInterpreter( true, false, this.dom, null );
            interp.exec(JythonRefactory.fixImports("import autoplot2023")); 
            
            ParametersFormPanel pfp= new org.autoplot.jythonsupport.ui.ParametersFormPanel();
            pfp.doVariables( env, scriptFile, splitParams, null );
            splitParams.entrySet().forEach((ent) -> {
                try {
                    pfp.getFormData().implement( interp, ent.getKey(), ent.getValue() );
                } catch (ParseException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            });

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
            
            param1= cleanupMultiParam( param1 );
            param2= cleanupMultiParam( param2 );
            
            JSONArray paramsJson= new JSONArray();
            paramsJson.put(0,param1);
            if ( param2.length()>0 ) {
                paramsJson.put(1,param2);
            }
            jo.put("params", paramsJson );
            
            monitor.setTaskSize( ff1.length );
            monitor.started();
            monitor.setTaskProgress(0);
            
            int icount=0;
            int i1=0;
            int exportResultsWritten=0;
            final AtomicInteger I1= new AtomicInteger(0);
            
            for ( String f1 : ff1 ) {
                final String final_f1= f1;
                final String final_param1= param1;
                final Map<String,String> final_params= splitParams;
                final JLabel jobLabel= jobs1.get(i1);
                final JSONObject frunResults= new JSONObject();
                ja.put(i1,frunResults);  // note runResults must be mutable
                                
                Runnable runOne= () -> {
                    JSONObject runResults= doOneJob( jobLabel, scriptFile, parms, final_params, final_param1, final_f1, monitor.getSubtaskMonitor(final_f1) );
                    Iterator i= runResults.keys();
                    while ( i.hasNext() ) {
                        String k= (String) i.next();
                        try {
                            frunResults.put( k, runResults.get(k) );
                        } catch (JSONException ex) {
                            logger.log(Level.SEVERE, null, ex);
                        }
                    }
                    if ( monitor.isFinished() ) {
                        logger.fine("monitor reports being finished though it shouldn't have been.");
                    } else {
                        monitor.setTaskProgress(I1.incrementAndGet());
                    }
                };
                executor.execute(runOne);
                i1=i1+1;
                
            }
            
            long lastWrite= System.currentTimeMillis();
            
            while ( true ) {
                if ( executor.getActiveCount()==0 && I1.intValue()==ff1.length ) {
                    break;
                }
                if ( resultsFile!=null && ( ( System.currentTimeMillis()-lastWrite )>10000 ) ) { // write to pending file every ten seconds.
                    if ( resultsFile.getName().endsWith(".json") ) {
                        
                    } else {
                        File pendingResultsFile= new File( resultsFile.getAbsolutePath()+".pending" );
                        int completed= I1.intValue();
                        int count= completed - exportResultsWritten;
                        
                        appendResultsPendingCSV( pendingResultsFile, jo, ja, exportResultsWritten, count);
                        messageLabel.setText( "wrote records "+exportResultsWritten+"-"+completed + " to " + resultsFile.getAbsolutePath()+".pending");
                        exportResultsWritten= completed;
                    }
                    lastWrite= System.currentTimeMillis();
                }
                
                JSONObject pendingResults= new JSONObject( jo.toString() );
                pendingResults.put( "results", new JSONArray( ja.toString() ) );
            }
                
            jo.put( "results", ja );
            results= jo;
            
            if ( resultsFile!=null ) {
                appendResultsPendingCSV( resultsFile, jo, ja, 0, ja.length() );
                messageLabel.setText( "completed, wrote ("+ja.length()+") to " + resultsFile.getAbsolutePath());
                
            }
        } catch (JSONException ex) {
            logger.log(Level.SEVERE, null, ex);
            
        } finally {
            if ( !messageLabel.getText().startsWith("completed, wrote") ) {
                messageLabel.setText("Jobs are complete, click above to edit.");
            }
            messageLabel.setToolTipText("");
            if ( !monitor.isFinished() ) monitor.finished();
            this.monitor=null;
            
            goButton.setEnabled(true);
        }
        
    }
    
    /**
     * run the batch process.  
     * @param multiThread if greater than 0, allow multiple threads to work simultaneously.
     * @throws IOException 
     */
    public void doIt( int multiThread ) throws IOException {
        
        jobs.clear();
        
        String pp2= param2NameCB.getSelectedItem()!=null ?
                    param2NameCB.getSelectedItem().toString().trim() :
                    "";
        if ( pp2.equals("") && multiThread>0 ) {    
            doItMultiThreadOneArgument(multiThread);
            return;
        }
    
        final DasProgressPanel monitor= DasProgressPanel.createComponent( "" );
        progressPanel.add( monitor.getComponent() );
        this.monitor= monitor;

        final List<JLabel> jobs1= new ArrayList<>();
        final List<JLabel> jobs2= new ArrayList<>();
        
        editParamsButton.setEnabled(true);
        
        closeButton.setEnabled(false);
        cancelButton.setEnabled(true);
                
        {
            String[] ff1= param1Values.getText().split("\n");
            JPanel p= switchListToIconLabels( jobs1, ff1 );
            //p.addMouseListener( getPostPopupMouseAdapter() );
            for ( int i=0; i<jobs1.size(); i++ ) {
                final int fi= i;
                jobs1.get(i).addMouseListener( getJobsMouseListener( fi, jobs1, jobs2 ) );
            }
            param1JLabels= jobs1.toArray( new JLabel[jobs1.size()] );
            param1ScrollPane.getViewport().setView(p);   
        }
               
        if ( param2Values.getText().trim().length()>0 ) {
            String[] ff1= param2Values.getText().split("\n");
            JPanel p= switchListToIconLabels( jobs2, ff1 );
            param2ScrollPane.getViewport().setView(p);   
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
            
            Map<String,org.autoplot.jythonsupport.Param> parms= Util.getParams( env, script, params, new NullProgressMonitor() );

            int parameterCount= 0;
            String params1= String.valueOf( param1NameCB.getSelectedItem() );
            String params2= String.valueOf( param2NameCB.getSelectedItem() );

            List<Param> parameterDescriptionsList= new ArrayList<>();
            
            if ( params1.contains(";") ) {
                String[] ss= params1.split("\\;",-2);
                parameterCount+=ss.length;
                for ( int i=0; i<ss.length; i++ ) {
                    parameterDescriptionsList.add(parms.get(ss[i]));
                }
            } else {
                parameterCount+=1;
                parameterDescriptionsList.add(parms.get(params1));
            }
            
            if ( params2.contains(";") ) {
                String[] ss= params2.split("\\;",-2);
                parameterCount+=ss.length;
                for ( int i=0; i<ss.length; i++ ) {
                    parameterDescriptionsList.add(parms.get(ss[i]));
                }
            } else {
                parameterCount+=1;
                parameterDescriptionsList.add(parms.get(params2));
            }
            parameterDescriptions= parameterDescriptionsList.toArray( new Param[parameterDescriptionsList.size()] );
            
            InteractiveInterpreter interp;

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
            
            param1= cleanupMultiParam( param1 );
            param2= cleanupMultiParam( param2 );
            
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
            
            long lastWrite= System.currentTimeMillis();
            
            for ( String f1 : ff1 ) {
                JSONObject runResults= new JSONObject();
                Map<String,Object> scriptParams= new LinkedHashMap<>();
                scriptParams.putAll( params );
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

                    jobs1.get(i1).setIcon(ICON_WORKING);
                    
                    String uri=null;
                    
                    if ( param2NameCB.getSelectedItem().toString().trim().length()==0 ) {
                        
                        long t0= System.currentTimeMillis();
                        
                        interp= createInterpretter(env, scriptFile, params, split.path );
    
                        doSetParameter( param1NameCB, f1, parms, interp, pwd, runResults, scriptParams);
                        
                        ByteArrayOutputStream outbaos= new ByteArrayOutputStream();
                        try {
                            param1ScrollPane.scrollRectToVisible( jobs1.get(i1).getBounds() );
                            interp.setOut(outbaos);
                            uri= URISplit.format( "script", split.resourceUri.toString(), scriptParams );
                            interp.execfile( JythonRefactory.fixImports( new FileInputStream(scriptFile),scriptFile.getName()), scriptFile.getName() );
                            if ( writeCheckBox.isSelected() ) {
                                Application myDom= (Application)env.get("dom");
                                runResults.put("writeFile", doWrite(f1.trim(), "", uri, myDom ) );
                            }
                            jobs1.get(i1).setIcon(ICON_OKAY);
                        } catch ( IOException | JSONException | RuntimeException ex ) {
                            String msg= ex.toString();
                            runResults.put("result",msg);
                            jobs1.get(i1).setIcon(ICON_PROB);
                        } finally {
                            outbaos.close();
                            runResults.put("stdout", new String(outbaos.toByteArray(),"US-ASCII") );
                            runResults.put("executionTime", System.currentTimeMillis()-t0);                            
                            System.out.println(runResults.getString("stdout"));
                            jobs.put( jobs1.get(i1), uri );                            
                        }
                        if ( jobs1.get(i1).getIcon()==ICON_OKAY ) {
                            jobs1.get(i1).setToolTipText( htmlize(runResults.getString("stdout")) );
                        } else {
                            jobs1.get(i1).setToolTipText( htmlize(runResults.getString("stdout"),runResults.getString("result")));
                        }
                        
                        
                        JSONObject copy = new JSONObject(runResults, JSONObject.getNames(runResults));
                        ja.put( icount, copy );
                        icount++;                        
                        
                    } else {
                        String[] ff2= param2Values.getText().split("\n");

                        for ( int i2=0; i2<jobs2.size(); i2++ ) {
                            jobs2.get(i2).setIcon(ICON_QUEUED);
                        }
                        
                        int i2=0;
                        String problemMessage= null;
                        
                        for ( String f2: ff2 ) {
                            if ( f2.trim().length()==0 ) continue;
                            if ( monitor.isCancelled() ) {
                                break;
                            }
                            long t0= System.currentTimeMillis();
                            
                            interp= createInterpretter( env, scriptFile, params, split.path );

                            doSetParameter( param1NameCB, f1, parms, interp, pwd, runResults, scriptParams);
                    
                            ByteArrayOutputStream outbaos= new ByteArrayOutputStream();                            
                            try {                                
                                doSetParameter( param2NameCB, f2, parms, interp, pwd, runResults, scriptParams );
                                
                                jobs2.get(i2).setIcon( ICON_WORKING );
                                interp.setOut(outbaos);
                                uri= URISplit.format( "script", split.resourceUri.toString(), scriptParams );
                                interp.execfile( JythonRefactory.fixImports( new FileInputStream(scriptFile), scriptFile.getName()), scriptFile.getName() );
                                if ( writeCheckBox.isSelected() ) {
                                    Application myDom= (Application)env.get("dom");
                                    runResults.put("writeFile", doWrite(f1.trim(),f2.trim(), uri, myDom ) );
                                }
                                jobs2.get(i2).setIcon(ICON_OKAY);
                                runResults.put("result","");
                            } catch ( IOException | JSONException | RuntimeException ex ) {
                                String msg= ex.toString();
                                runResults.put("result",msg);
                                jobs1.get(i1).setIcon(ICON_PROB);
                                jobs2.get(i2).setIcon(ICON_PROB); // this will only show briefly, but it is still helpful.
                                problemMessage= msg;
                            } finally {
                                runResults.put("stdout", new String(outbaos.toByteArray(),"US-ASCII") );
                                runResults.put("executionTime", System.currentTimeMillis()-t0);
                                jobs.put( jobs1.get(i1), uri );
                                jobs.put( jobs2.get(i2), uri );
                                outbaos.close();
                                System.out.println(runResults.getString("stdout"));
                            }
                            if ( jobs1.get(i1).getIcon()!=ICON_PROB ) {
                                jobs2.get(i2).setToolTipText( htmlize(runResults.getString("stdout")) );
                            } else {
                                String s= htmlize(runResults.getString("stdout"),runResults.getString("result"));
                                jobs2.get(i2).setToolTipText( s );
                                jobs1.get(i1).setToolTipText( s );
                            }
                            JSONObject copy = new JSONObject(runResults, JSONObject.getNames(runResults));
                            ja.put( icount, copy );
                            i2=i2+1;
                            icount++;
                        }
                        if ( problemMessage==null ) {
                            jobs1.get(i1).setIcon(ICON_OKAY);
                            jobs1.get(i1).setToolTipText(null);
                        }
                    }
                } catch (IOException | RuntimeException | JSONException ex) {
                    Logger.getLogger(RunBatchTool.class.getName()).log(Level.SEVERE, null, ex);
                    jobs1.get(i1).setIcon(ICON_PROB);
                    jobs1.get(i1).setToolTipText(htmlize(ex.toString()));
                }
                i1=i1+1;
                
                if ( resultsFile!=null && ( ( System.currentTimeMillis()-lastWrite )>30000 ) ) {
                    if ( resultsFile.getName().endsWith(".json") ) {
                        
                    } else {
                        File pendingResultsFile= new File( resultsFile.getAbsolutePath()+".pending" );
                        int nrec= ja.length() - exportResultsWritten;
                        appendResultsPendingCSV( pendingResultsFile, jo, ja, exportResultsWritten, ja.length()-exportResultsWritten );
                        messageLabel.setText( "wrote ("+nrec+" more) to " + resultsFile.getAbsolutePath()+".pending");
                    }
                    exportResultsWritten= icount;
                    lastWrite= System.currentTimeMillis();
                }
                
                resultsPending= new JSONObject( jo.toString() );
                resultsPending.put( "results", new JSONArray( ja.toString() ) );
            }
            
            jo.put( "results", ja );
            results= jo;
            
            if ( resultsFile!=null ) {
                appendResultsPendingCSV( resultsFile, jo, ja, 0, ja.length() );
                messageLabel.setText( "completed, wrote ("+ja.length()+") to " + resultsFile.getAbsolutePath());
            }
            
        } catch (JSONException ex) {
            logger.log(Level.SEVERE, null, ex);
            
        } finally {
            if ( !messageLabel.getText().startsWith("completed, wrote (") ) {
                messageLabel.setText("Jobs are complete, click \"Edit Parameter Values\" to edit.");
            }
            if ( !monitor.isFinished() ) monitor.finished();
            this.monitor=null;
            
            cancelButton.setEnabled(false);
            closeButton.setEnabled(true);
            goButton.setEnabled(true);
        }
    }

    private static void doSetParameter( JComboBox cb, String paramValue, Map<String, Param> parms, InteractiveInterpreter interp, String pwd, JSONObject runResults, Map<String, Object> scriptParams) throws JSONException, IOException, IllegalArgumentException {
        
        String paramName= cb.getSelectedItem().toString().trim();
        String[] paramNames= maybeSplitMultiParam( paramName );
        
        if ( paramNames!=null ) {
            char splitc= paramName.charAt(paramNames[0].length());
            String[] paramValues= paramValue.trim().split("\\"+splitc);
            for ( int j= 0; j<paramNames.length; j++ ) {
                String p= paramNames[j].trim();
                String v= paramValues[j].trim();
                if ( !parms.containsKey(p) ) {
                    if ( p.trim().length()==0 ) {
                        throw new IllegalArgumentException("param1Name not set");
                    } else {
                        throw new IllegalArgumentException("param not found: " + p );
                    }
                }
                setParam( interp, pwd, parms.get(p), p, v );
                runResults.put( p, v );
                scriptParams.put( p, v );
            }
        } else {
            if ( !parms.containsKey(paramName) ) {
                if ( paramName.trim().length()==0 ) {
                    throw new IllegalArgumentException("param1Name not set");
                }
            }
            setParam( interp, pwd, parms.get(paramName), paramName, paramValue.trim() );
            runResults.put(paramName,paramValue.trim());
            scriptParams.put(paramName,paramValue.trim());
        }
    }
        
    private MouseListener getJobsMouseListener( final int fi, final List<JLabel> jobs1, final List<JLabel> jobs2 ) {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if ( e.isPopupTrigger() ) {
                    selectRecord(fi);
                    RunBatchTool.this.selectedLabel= jobs1.get(fi);
                    postRunPopupMenu.show( e.getComponent(), e.getX(), e.getY() );
                    return;
                }
                selectRecord(fi);
                RunBatchTool.this.selectedLabel= jobs1.get(fi);
                String param1= (String)RunBatchTool.this.param1NameCB.getSelectedItem();
                if ( RunBatchTool.this.results!=null ) {
                    String s= jobs1.get(fi).getText();
                    List<JSONObject> thisRow= new ArrayList<>();
                    try {
                        JSONObject jo= RunBatchTool.this.results;
                        JSONArray ja= jo.getJSONArray("results");
                        for ( int j=0; j<ja.length(); j++ ) {
                            JSONObject jo1= ja.getJSONObject(j);
                            if ( jo1.getString(param1).equals(s) ) {
                                thisRow.add( jo1 );
                            }
                        }
                        if ( jobs2.size()==thisRow.size() ) {
                            for ( int i=0; i<thisRow.size(); i++ ) {
                                JSONObject runResults= thisRow.get(i);
                                String except= thisRow.get(i).getString("result");
                                if ( except.length()>0 ) {
                                    jobs2.get(i).setIcon(ICON_PROB);
                                } else {
                                    jobs2.get(i).setIcon(ICON_OKAY);
                                }
                                if ( jobs2.get(i).getIcon()==ICON_OKAY ) {
                                    jobs2.get(i).setToolTipText( htmlize(runResults.getString("stdout")) );
                                } else {
                                    jobs2.get(i).setToolTipText( htmlize(runResults.getString("stdout"),runResults.getString("result")));
                                }
                            }
                        } else {
                            logger.fine("Nothing to do.");
                        }

                    } catch (JSONException ex) {
                        Logger.getLogger(RunBatchTool.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if ( e.isPopupTrigger() ) {
                    selectRecord(fi);
                    RunBatchTool.this.selectedLabel= jobs1.get(fi);
                    postRunPopupMenu.show( e.getComponent(), e.getX(), e.getY() );
                    return;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if ( e.isPopupTrigger() ) {
                    selectRecord(fi);
                    RunBatchTool.this.selectedLabel= jobs1.get(fi);
                    postRunPopupMenu.show( e.getComponent(), e.getX(), e.getY() );
                    return;
                }
            }

        };
    }
        
    public static void main( String[] args ) {
        JDialog dia= new JDialog();
        dia.setResizable(true);
        RunBatchTool mmm= new RunBatchTool(new Application());
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
    private javax.swing.JButton closeButton;
    private javax.swing.JMenuItem copyScriptUri;
    private javax.swing.JMenuItem copyValueMenuItem;
    private org.autoplot.datasource.DataSetSelector dataSetSelector1;
    private javax.swing.JButton editParamsButton;
    private javax.swing.JMenuItem exportResultsMenuItem;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JButton generateButton1;
    private javax.swing.JButton generateButton2;
    private javax.swing.JMenuItem generateMenuItem1;
    private javax.swing.JMenuItem generateMenuItem2;
    private javax.swing.JButton goButton;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JList<String> jList2;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPopupMenu jPopupMenu1;
    private javax.swing.JPopupMenu jPopupMenu2;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JMenuItem loadFromFileMI;
    private javax.swing.JMenuItem loadFromFileMI2;
    private javax.swing.JMenuItem loadUriMenuItem;
    private javax.swing.JMenuItem loadUriMenuItem2;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JLabel messageLabel;
    private javax.swing.JComboBox<String> param1NameCB;
    private javax.swing.JScrollPane param1ScrollPane;
    private javax.swing.JTextArea param1Values;
    private javax.swing.JComboBox<String> param2NameCB;
    private javax.swing.JScrollPane param2ScrollPane;
    private javax.swing.JTextArea param2Values;
    private javax.swing.JMenuItem pasteMenuItem;
    private javax.swing.JMenuItem pasteMenuItem2;
    private javax.swing.JButton pngWalkToolButton;
    private javax.swing.JPopupMenu postRunPopupMenu;
    private javax.swing.JPanel progressPanel;
    private javax.swing.JMenuItem rerunScriptMenuItem;
    private javax.swing.JMenuItem showHelpMenuItem;
    private javax.swing.JComboBox<String> timeFormatComboBox;
    private javax.swing.JComboBox<String> timeRangeComboBox;
    private javax.swing.JPanel timeRangesPanel;
    private javax.swing.JCheckBox writeCheckBox;
    private javax.swing.JComboBox<String> writeFilenameCB;
    private org.jdesktop.beansbinding.BindingGroup bindingGroup;
    // End of variables declaration//GEN-END:variables

    private static void appendResultsPendingCSV( 
            File pendingFile, 
            JSONObject results, 
            JSONArray resultsArray, 
            int recordsWrittenAlready, int count ) throws FileNotFoundException, IOException {
        
        boolean header= recordsWrittenAlready==0;
        
        synchronized (RunBatchTool.class) {
            
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

                int stop= recordsWrittenAlready + count;
                for ( int i=recordsWrittenAlready; i<stop; i++ ) {
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
                    String resultString= jo.optString("result","");
                    int inl= resultString.indexOf("\n");
                    if ( inl>=0 ) inl= resultString.indexOf("\n",inl+1);
                    if ( inl>=0 ) inl= resultString.indexOf("\n",inl+1);
                    if ( inl>=0 ) resultString= resultString.substring(0,inl).replaceAll("\n"," ").replaceAll(",","");
                    record.append(",").append(resultString);
                    out.println( record.toString() );
                }
                out.flush();

            } catch (JSONException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * remove extraneous spaces
     * @param param1 " myparm" or "a; b; c"
     * @return "myparm" or "a;b;c"
     */
    private String cleanupMultiParam(String param1) {
        String[] ss= maybeSplitMultiParam(param1);
        if ( ss==null ) {
            return param1.trim();
        } else {
            char ch= param1.charAt(ss[0].length());
            for ( int i=0; i<ss.length; i++ ) {
                ss[i]= ss[i].trim();
            }
            return String.join( String.valueOf(ch), ss );
        }
    }

    private void doSelectMultiple(JComboBox<String> param1NameCB, Object selectedItem) {
        JPanel p= new JPanel();
        List<JCheckBox> paramsCB= new ArrayList<>();
        p.setLayout( new BoxLayout( p, BoxLayout.Y_AXIS ) );
        int istart;
        if ( param1NameCB.getModel().getElementAt(0).trim().length()==0 ) {
            istart=1;
        } else {
            istart=0;
        }
        for ( int i= istart; i<param1NameCB.getModel().getSize()-1; i++ ) {
            String param=param1NameCB.getModel().getElementAt(i);
            JCheckBox cb= new JCheckBox( param );
            p.add( cb );
            paramsCB.add( cb );
        }
        if ( JOptionPane.OK_OPTION==
            JOptionPane.showConfirmDialog(param1NameCB,p,"Multiple Parameters",JOptionPane.OK_CANCEL_OPTION ) ) {
            StringBuilder b= new StringBuilder();
            for ( int i=0; i<paramsCB.size(); i++ ) {
                if ( paramsCB.get(i).isSelected() ) {
                    if ( b.length()>0 ) b.append(";");
                    b.append( paramsCB.get(i).getText() );
                }
            }
            Runnable run= () -> {
                param1NameCB.setSelectedItem(b.toString());
            };
            SwingUtilities.invokeLater(run);
            
        }
    }

    private JLabel selectedLabel;
    
    private JLabel getSelectedLabel() {
        return this.selectedLabel;
    }

    /**
     * create the interpretter with the script settings.  This will be further modified 
     * to each state in the list.
     * @param env the environment, like PWD and dom.
     * @param scriptFile the script we are running
     * @param params the constant parameters for the script.
     * @return the interpretter
     * @throws IOException 
     */
    private InteractiveInterpreter createInterpretter(
        Map<String,Object> env, File scriptFile, Map<String,String> params, String pwd ) throws IOException {
        
        Application dom= (Application)env.get("dom");
        
        InteractiveInterpreter interp = JythonUtil.createInterpreter( true, false, dom, null );
        interp.exec(JythonRefactory.fixImports("import autoplot2023"));   

        ParametersFormPanel pfp= new org.autoplot.jythonsupport.ui.ParametersFormPanel();
        pfp.doVariables( env, scriptFile, params, null );
        params.entrySet().forEach((ent) -> {
            try {
                pfp.getFormData().implement( interp, ent.getKey(), ent.getValue() );
            } catch (ParseException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        });
                           
        interp.set( "monitor", new NullProgressMonitor() {
            @Override
            public boolean isCancelled() {
                return monitor.isCancelled();
            }
        }); // subtask would reset indeterminate.
        
        interp.set( "PWD", pwd );

        return interp;

    }

    /**
     * replace %d etc with $x
     * @param template like data_%05d.png
     * @return uri template like data_$x.png
     */
    private String convertStringFormatToUriTemplate(String template) {
                
        String[] ss= template.split("\\%");
        StringBuilder uriTemplate= new StringBuilder(ss[0]);
        for ( int i=1; i<ss.length; i++ ) {
            String spec= ss[i];
            int firstLetter= 0;
            while ( firstLetter<spec.length() 
                && ( spec.charAt(firstLetter)=='-' || spec.charAt(firstLetter)=='.' || Character.isDigit(spec.charAt(firstLetter)) ) ) {
                firstLetter++;
            }
            uriTemplate.append("$x");
            uriTemplate.append(spec.substring(firstLetter+1));
        }
        return uriTemplate.toString();

    }


}
