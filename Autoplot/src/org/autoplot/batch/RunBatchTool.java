/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JPanel.java to edit this template
 */
package org.autoplot.batch;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import javax.swing.JComponent;
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
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.autoplot.AutoplotUtil;
import org.autoplot.JythonUtil;
import org.autoplot.ScriptContext;
import org.autoplot.bookmarks.Bookmark;
import org.autoplot.datasource.DataSetSelector;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.WindowManager;
import org.autoplot.dom.Application;
import org.autoplot.jythonsupport.Param;
import org.autoplot.jythonsupport.ui.Util;
import org.autoplot.pngwalk.PngWalkTool;
import org.das2.components.DasProgressPanel;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.fsm.FileStorageModel;
import org.das2.qds.DataSetUtil;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;
import org.das2.util.FileUtil;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.AlertNullProgressMonitor;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author jbf
 */
public class RunBatchTool extends javax.swing.JPanel {

    private static final Logger logger= LoggerManager.getLogger("jython.batchmaster");
    
    private Application dom;
    private String pwd;
        
    private Object state; 
    
    private static final String STATE_READY= "ready";
    private static final String STATE_LOADING= "loading";
    
    private JSONObject results=null;
    private JSONObject resultsPending=null;
    private File resultsFile=null;
    
    private JLabel[] param1JLabels= null;
    private JLabel[] param2JLabels= null;
    
    private int selectedIndex1;
    
    private JSONObject mainBatchJSONObject= null;
    private File mainBatchFile= null;
            
    private JLabel lastActiveLabel= null;
    
    
    /**
     * 1, 2, or more than 2 params.
     */
    private org.autoplot.jythonsupport.Param[] parameterDescriptions;
    
    private final Map<JLabel,String> jobs= new HashMap<>();
    
    public static final int HTML_LINE_LIMIT = 50;
            
    private ProgressMonitor monitor=null; // non-null when process is going.
        
    private final Preferences prefs;
        
    private static final Icon ICON_QUEUED= new ImageIcon(RunBatchTool.class.getResource("/resources/grey.gif"));
    private static final Icon ICON_WORKING= new ImageIcon(RunBatchTool.class.getResource("/resources/blue_anime.gif"));
    private static final Icon ICON_OKAY= new ImageIcon(RunBatchTool.class.getResource("/resources/blue.gif"));
    private static final Icon ICON_PROB= new ImageIcon(RunBatchTool.class.getResource("/resources/red.gif"));    
    
    /**
     * Creates new form RunBatchTool
     * @param dom
     */
    public RunBatchTool( final Application dom ) {
        initComponents();
        messageLabel.setPreferredSize( new Dimension(messageLabel.getFont().getSize()*50,messageLabel.getFont().getSize()) );
        messageLabel.setMaximumSize( messageLabel.getPreferredSize() );        
        this.registerKeyboardAction((ActionEvent e) -> {
            org.das2.util.LoggerManager.logGuiEvent(e);
            JDialog dia= (JDialog) SwingUtilities.getWindowAncestor(cancelButton);
            if ( cancelButton.isEnabled() ) {
                dia.setVisible(false);
                dia.dispose();
            }
        }, KeyStroke.getKeyStroke( KeyEvent.VK_ESCAPE, 0 ), JComponent.WHEN_IN_FOCUSED_WINDOW );           

        writeFilenameCB.getEditor().getEditorComponent().addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                checkNumberOfParams();
            }
        });
        
        prefs= Preferences.userNodeForPackage(org.autoplot.RunBatchTool.class );
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
                    if ( JOptionPane.OK_OPTION==JythonUtil.showScriptDialog(org.autoplot.batch.RunBatchTool.this, 
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
        recentUris.add( "https://github.com/autoplot/dev/blob/master/demos/2019/20190726/demoParams.jy" );
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
        
        timeRangeComboBox.setSelectedItem( prefs.get("lastTimeRange", "2000-Jan" ) );
        timeFormatComboBox.setSelectedItem( prefs.get("lastTimeFormat", "$Y-$m-$d" ) );
        
        activeFocusCB.addActionListener((ActionEvent ae) -> {
            if ( activeFocusCB.isSelected() ) {
                if ( lastActiveLabel!=null ) {
                    JLabel jobLabel= lastActiveLabel;
                    JViewport vp = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, jobLabel);
                    try {
                        Rectangle r = SwingUtilities.convertRectangle(jobLabel.getParent(), jobLabel.getBounds(), vp);
                        vp.scrollRectToVisible(r);
                    } catch ( java.lang.Error e ) {
                        // this can be ignored.
                    }
                }
            }
        });
        
    }
    
    /**
     * set up the GUI to monitor the batch directory.  The GUI will show
     * all the jobs and their current status.
     * @param d the directory containing main.batch file.
     * @throws JSONException
     * @throws IOException 
     */
    public void setBatchDirectory( File d ) throws JSONException, IOException {
        
        File f;
        if ( d.isFile() ) {
            f= d;
        } else {
            f= new File( d, "main.batch");
        }
        
        String batchFileJson= FileUtil.readFileToString(f);
        JSONObject jo= new JSONObject(batchFileJson);       
        this.mainBatchJSONObject= jo;
        this.mainBatchFile= f;
        
        final List<JLabel> jobs1= new ArrayList<>();
        final List<JLabel> jobs2= new ArrayList<>();

        JSONArray a;
        JPanel p;

        a = jo.getJSONArray("param1Values");
        String[] ff1= new String[a.length()];
        for ( int i=0; i<a.length(); i++ ) {
            ff1[i]= a.getString(i);
        }
        p = switchListToIconLabels(1, ff1);
        param1ScrollPane.getViewport().setView(p);
        
        a = jo.getJSONArray("param2Values");
        String[] ff2= new String[a.length()];
        for ( int i=0; i<a.length(); i++ ) {
            ff2[i]= a.getString(i);
        }
        p= switchListToIconLabels(2, ff2);
        param2ScrollPane.getViewport().setView(p);

        selectedIndex1=0;
        updateListIcons(2);
        updateListIcons(1);
        
        param1NameCB.setSelectedItem( jo.getString("param1") );
        param2NameCB.setSelectedItem( jo.getString("param2") );
        
        dataSetSelector1.setValue( jo.getString("script") );
        
        this.revalidate();
    }
    
    public void update() {
        updateListIcons(2);
        updateListIcons(1);
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
        lastActiveLabel= null;
        state= STATE_LOADING;                  
        try {
            String scriptName= dataSetSelector1.getValue();
            URISplit split= URISplit.parse(scriptName);
            if ( !split.file.endsWith(".jy") ) {
                JOptionPane.showMessageDialog(org.autoplot.batch.RunBatchTool.this, "script must end in .jy: "+scriptName );
                return;
            }

            pwd= split.path;

            //Map<String,String> params= URISplit.parseParams(split.params);  //TODO: support these.
            Map<String,Object> env= new HashMap<>();

            DasProgressPanel monitor= DasProgressPanel.createFramed(SwingUtilities.getWindowAncestor(org.autoplot.batch.RunBatchTool.this), "download script");
            File scriptFile= DataSetURI.getFile( split.file, monitor );
            String script= FileUtil.readFileToString(scriptFile);

            env.put( "dom", dom );
            env.put( "PWD", split.path );

            Map<String,org.autoplot.jythonsupport.Param> parms= Util.getParams( env, script, URISplit.parseParams(split.params), new NullProgressMonitor() );

            String[] items= new String[parms.size()+2];
            int i=0;
            items[0]="";
            for ( Map.Entry<String,org.autoplot.jythonsupport.Param> p: parms.entrySet() ) {
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
            
           // switchToEditableList();

            messageLabel.setText("Load up those parameters and hit Go!");
            param1ScrollPane.getViewport().setView(param1Values);


        } catch (IOException ex) {
            Logger.getLogger(org.autoplot.RunBatchTool.class.getName()).log(Level.SEVERE, null, ex);
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
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content of
     * this method is always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

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
        jLabel1 = new javax.swing.JLabel();
        dataSetSelector1 = new org.autoplot.datasource.DataSetSelector();
        param1NameCB = new javax.swing.JComboBox<>();
        generateButton1 = new javax.swing.JButton();
        param1ScrollPane = new javax.swing.JScrollPane();
        param1Values = new javax.swing.JTextArea();
        param2ScrollPane = new javax.swing.JScrollPane();
        param2Values = new javax.swing.JTextArea();
        param2NameCB = new javax.swing.JComboBox<>();
        generateButton2 = new javax.swing.JButton();
        progressPanel = new javax.swing.JPanel();
        messageLabel = new javax.swing.JLabel();
        activeFocusCB = new javax.swing.JCheckBox();
        writeCheckBox = new javax.swing.JCheckBox();
        writeFilenameCB = new javax.swing.JComboBox<>();
        deleteDirectoryButton = new javax.swing.JButton();
        pngWalkToolButton = new javax.swing.JButton();
        editParamsButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        goButton = new javax.swing.JButton();
        closeButton = new javax.swing.JButton();

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

        jLabel1.setText("<html>This tool generates inputs for scripts, running through a series of inputs.  First load the script with the green \"play\" button, then specify the parameter name and values to assign, and optionally a second parameter.  Each value of the second parameter is run for each value of the first.  Use the inspect button to set values for any other parameters. Right-click within the values areas to generate values.");
        jLabel1.setVerticalAlignment(javax.swing.SwingConstants.TOP);

        param1NameCB.setEditable(true);
        param1NameCB.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "(application will put recent items here)" }));
        dataSetSelector1.setToolTipText("Enter data source address");
        dataSetSelector1.setMaximumSize(new java.awt.Dimension(2000, 27));
        dataSetSelector1.setMinimumSize(new java.awt.Dimension(100, 27));
        dataSetSelector1.setPreferredSize(new java.awt.Dimension(300, 27));
        /*dataSetSelector1.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent evt) {
                dataSetSelector1PopupMenuCanceled(evt);
            }
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent evt) {
                dataSetSelector1PopupMenuWillBecomeInvisible(evt);
            }
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent evt) {
            }
	    });*/

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

        generateButton1.setText("Generate...");
        generateButton1.setToolTipText("Generate items for list");
        generateButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                generateButton1ActionPerformed(evt);
            }
        });

        param1Values.setColumns(20);
        param1Values.setRows(5);
        param1Values.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                param1ValuesMouseClicked(evt);
            }
            public void mousePressed(java.awt.event.MouseEvent evt) {
                param1ValuesMousePressed(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                param1ValuesMouseReleased(evt);
            }
        });
        param1ScrollPane.setViewportView(param1Values);

        param2Values.setColumns(20);
        param2Values.setRows(5);
        param2Values.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                param2ValuesMouseClicked(evt);
            }
            public void mousePressed(java.awt.event.MouseEvent evt) {
                param2ValuesMousePressed(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                param2ValuesMouseReleased(evt);
            }
        });
        param2ScrollPane.setViewportView(param2Values);

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

        generateButton2.setText("Generate...");
        generateButton2.setToolTipText("Generate items for list");
        generateButton2.setEnabled(false);
        generateButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                generateButton2ActionPerformed(evt);
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

        messageLabel.setText("Load up those parameters and hit Go!");

        activeFocusCB.setText("Active focus");
        activeFocusCB.setEnabled(false);

        writeCheckBox.setText("Write:");
        writeCheckBox.setToolTipText("After each iteration, write the file, where each $x is replaced with the parameter value.  The number \nof $x fields must match the number of parameters controlled.  Note the script name and its arguments\nare embedded within each .vap, and the pngwalk tool can be used to relaunch the script for any run.");
        writeCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                writeCheckBoxActionPerformed(evt);
            }
        });

        writeFilenameCB.setEditable(true);
        writeFilenameCB.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "/tmp/ap/$x.png", "/tmp/ap/$x_$x.png", "/tmp/ap/$x_$x.png", "/tmp/ap/$x_$x_$x.png", "/tmp/ap/$x_$x_$x_$x.png", "/tmp/ap/$x.pdf", "/tmp/ap/$x_$x.pdf", "/tmp/ap/%s_%s.pdf", "/tmp/ap/%s_%s_%02d.png", " ", " " }));
        writeFilenameCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                writeFilenameCBActionPerformed(evt);
            }
        });

        deleteDirectoryButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/resources/trashcan.png"))); // NOI18N
        deleteDirectoryButton.setToolTipText("Delete files in directory");
        deleteDirectoryButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteDirectoryButtonActionPerformed(evt);
            }
        });

        pngWalkToolButton.setText("PNG Walk Tool");
        pngWalkToolButton.setToolTipText("Open template in the PNG Walk Tool");
        pngWalkToolButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pngWalkToolButtonActionPerformed(evt);
            }
        });

        editParamsButton.setText("Edit Parameter Values");
        editParamsButton.setEnabled(false);
        editParamsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editParamsButtonActionPerformed(evt);
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

        goButton.setText("Go!");
        goButton.setToolTipText("Run the batch processes, holding shift to run independent processes in parallel.");
        goButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                goButtonActionPerformed(evt);
            }
        });

        closeButton.setText("Close");
        closeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(dataSetSelector1, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(param1NameCB, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(generateButton1))
                            .addComponent(param1ScrollPane))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(param2NameCB, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(generateButton2))
                            .addComponent(param2ScrollPane)))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(activeFocusCB)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(writeCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(writeFilenameCB, javax.swing.GroupLayout.PREFERRED_SIZE, 238, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(deleteDirectoryButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pngWalkToolButton))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(messageLabel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(progressPanel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
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
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(dataSetSelector1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(param1NameCB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(param1ScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 327, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(param2NameCB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(generateButton2)
                            .addComponent(generateButton1))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(param2ScrollPane)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(writeCheckBox)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(writeFilenameCB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(pngWalkToolButton)
                        .addComponent(deleteDirectoryButton, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(activeFocusCB))
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
    }// </editor-fold>//GEN-END:initComponents

    private void dataSetSelector1PopupMenuCanceled(javax.swing.event.PopupMenuEvent evt) {//GEN-FIRST:event_dataSetSelector1PopupMenuCanceled
        //popupCancelled = true;
    }//GEN-LAST:event_dataSetSelector1PopupMenuCanceled

    private void dataSetSelector1PopupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent evt) {//GEN-FIRST:event_dataSetSelector1PopupMenuWillBecomeInvisible
//        if (popupCancelled == false) {
//            if ( (keyModifiers&KeyEvent.ALT_MASK ) == KeyEvent.ALT_MASK ) {
//                browseSourceType();
//            } else {
//                maybePlot(true);
//            }
//        }
//        popupCancelled = false;
    }//GEN-LAST:event_dataSetSelector1PopupMenuWillBecomeInvisible

    private void param1NameCBItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_param1NameCBItemStateChanged
//        if ( evt.getStateChange()==ItemEvent.SELECTED ) {
//            if ( param1NameCB.getSelectedIndex()==param1NameCB.getItemCount()-1 ) {
//                doSelectMultiple(param1NameCB,param1NameCB.getSelectedItem());
//                return;
//            }
//            boolean present= param1NameCB.getSelectedItem().toString().trim().length()>0;
//            generateButton1.setEnabled( present );
//            generateMenuItem1.setEnabled( present );
//            param1ScrollPane.getViewport().setView(param1Values);
//            param2ScrollPane.getViewport().setView(param2Values);
//            messageLabel.setText("Load up those parameters and hit Go!");
//        }
    }//GEN-LAST:event_param1NameCBItemStateChanged

    private void param1NameCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_param1NameCBActionPerformed
        checkNumberOfParams();
    }//GEN-LAST:event_param1NameCBActionPerformed

    private void generateButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_generateButton1ActionPerformed
        //doGenerate( param1NameCB, param1Values );
    }//GEN-LAST:event_generateButton1ActionPerformed

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

    private void param2ValuesMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_param2ValuesMouseClicked
        if ( evt.isPopupTrigger() ) {
            jPopupMenu2.show( evt.getComponent(), evt.getX(), evt.getY() );
        }
    }//GEN-LAST:event_param2ValuesMouseClicked

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

    private void param2NameCBItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_param2NameCBItemStateChanged
        //if ( evt.getStateChange()==ItemEvent.SELECTED ) {
        //    if ( param2NameCB.getSelectedIndex()==param2NameCB.getItemCount()-1 ) {
        //        doSelectMultiple(param2NameCB,param2NameCB.getSelectedItem());
        //        return;
        //    }
        //    boolean present= param2NameCB.getSelectedItem().toString().trim().length()>0;
        //    generateButton2.setEnabled( present );
        //    generateMenuItem2.setEnabled( present );
        //}
    }//GEN-LAST:event_param2NameCBItemStateChanged

    private void param2NameCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_param2NameCBActionPerformed
        checkNumberOfParams();
    }//GEN-LAST:event_param2NameCBActionPerformed

    private void generateButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_generateButton2ActionPerformed
        //doGenerate( param2NameCB, param2Values );
    }//GEN-LAST:event_generateButton2ActionPerformed

    private void writeCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_writeCheckBoxActionPerformed
        checkNumberOfParams();
    }//GEN-LAST:event_writeCheckBoxActionPerformed

    private void writeFilenameCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_writeFilenameCBActionPerformed
        checkNumberOfParams();
    }//GEN-LAST:event_writeFilenameCBActionPerformed

    private void deleteDirectoryButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteDirectoryButtonActionPerformed
        String s= writeFilenameCB.getSelectedItem().toString();
        int i= FileStorageModel.splitIndex(s);
        int i2= s.indexOf("%");
        if ( i2>-1 && ( i==-1 || i2<i ) ) {
            i2= s.lastIndexOf("/",i2);
            i=i2;
        }
        if ( i>-1 ) {
            File dir= new File( s.substring(0,i) );
            if ( JOptionPane.showConfirmDialog(this,"<html>Delete files and directory<br>"+dir+"?","Delete Directory",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION ) {
                if ( !FileUtil.deleteFileTree(dir) ) {
                    JOptionPane.showMessageDialog(this,"Unable to delete directory");
                }
            }
        } else {
            JOptionPane.showMessageDialog(this,"Unable to find directory (looking for forward slashes)");
        }
    }//GEN-LAST:event_deleteDirectoryButtonActionPerformed

    private void pngWalkToolButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pngWalkToolButtonActionPerformed
        String template= writeFilenameCB.getSelectedItem().toString();
        template= convertStringFormatToUriTemplate( template );
        PngWalkTool.start( template, SwingUtilities.getWindowAncestor(this) );
    }//GEN-LAST:event_pngWalkToolButtonActionPerformed

    private void editParamsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editParamsButtonActionPerformed
        //switchToEditableList();
        editParamsButton.setEnabled(false);
        goButton.setEnabled(true);
    }//GEN-LAST:event_editParamsButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        ProgressMonitor mon= this.monitor;
        if ( mon!=null ) {
            mon.cancel();
        }
        cancelButton.setEnabled(false);
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void goButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_goButtonActionPerformed
        if ( !goButton.isEnabled() ) {
            return;
        }
        goButton.setEnabled(false);
        activeFocusCB.setEnabled(true);
        messageLabel.setText("Setting up to run jobs...");
        Runnable run= () -> {
            try {
                String scriptName= dataSetSelector1.getValue();
                dom.getController().getApplicationModel().addRecent(scriptName);
                Preferences prefs= RunBatchTool.this.prefs;
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

                    if ( JOptionPane.OK_OPTION==WindowManager.showConfirmDialog( param1NameCB, p,
                        "Multi-Thread warning", JOptionPane.OK_CANCEL_OPTION ) ) {
                    threadCount= Integer.parseInt(tf.getText());
                    prefs.putInt(PREF_THREAD_COUNT, threadCount );
                    //doIt( threadCount );
                } else {
                    goButton.setEnabled(true);
                }
            } else {
                //doIt();
            }
        } catch (Exception ex) { //IOException
            messageLabel.setText(ex.getMessage());
        }
        };
        new Thread(run,"runBatch").start();
    }//GEN-LAST:event_goButtonActionPerformed

    private static final String PREF_THREAD_COUNT = "threadCount";
    
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
        deft.add( new Bookmark.Item("https://autoplot.org/data/event/simpleEvent.txt") );
        org.autoplot.bookmarks.Util.loadRecent( "eventsRecent", eventsDataSetSelector, deft );
        
        if ( JOptionPane.OK_OPTION==WindowManager.showConfirmDialog(this, eventsDataSetSelector, "Load Events", JOptionPane.OK_CANCEL_OPTION ) ) {
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
    
    private void jList2MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jList2MouseClicked
        jPopupMenu2.show( this, evt.getX(), evt.getY() );
    }//GEN-LAST:event_jList2MouseClicked

    private void generateMenuItem1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_generateMenuItem1ActionPerformed
        doGenerate( param1NameCB, param1Values );
    }//GEN-LAST:event_generateMenuItem1ActionPerformed

    private void loadUriMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadUriMenuItemActionPerformed
        //loadUriMenuItemAction(param1Values);
    }//GEN-LAST:event_loadUriMenuItemActionPerformed

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

    
    private void loadFromFileMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadFromFileMIActionPerformed
        doLoadFromFile(param1Values);
    }//GEN-LAST:event_loadFromFileMIActionPerformed

    private void pasteMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pasteMenuItemActionPerformed
        try {
            String pasteMe= (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            param1Values.setText(pasteMe);
        } catch (UnsupportedFlavorException | IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_pasteMenuItemActionPerformed

    private void generateMenuItem2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_generateMenuItem2ActionPerformed
        doGenerate( param2NameCB, param2Values );
    }//GEN-LAST:event_generateMenuItem2ActionPerformed

    private void loadUriMenuItem2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadUriMenuItem2ActionPerformed
        loadUriMenuItemAction(param2Values);
    }//GEN-LAST:event_loadUriMenuItem2ActionPerformed

    private void loadFromFileMI2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadFromFileMI2ActionPerformed
        doLoadFromFile(param2Values);
    }//GEN-LAST:event_loadFromFileMI2ActionPerformed

    private void pasteMenuItem2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pasteMenuItem2ActionPerformed
        try {
            String pasteMe= (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            param2Values.setText(pasteMe);
        } catch (UnsupportedFlavorException | IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_pasteMenuItem2ActionPerformed

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
//                try {
//                    //saveFile( f );
//                } catch (IOException|JSONException ex) {
//                    JOptionPane.showMessageDialog(RunBatchTool.this, "Unable to save file. "+ex.getMessage() );
//                }
            };
            new Thread(run).start();
        }
    }//GEN-LAST:event_SaveAsMenuItemActionPerformed

    public void loadBatchFile( File f ) throws IOException, JSONException {
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
        
        Object o= jo.get("param1Values");
        if ( o instanceof String ) {
            params.put( "param1Values", (String)o );
        } else if ( o instanceof JSONArray ) {
            JSONArray ja= (JSONArray)o;
            StringBuilder sb= new StringBuilder();
            for ( int i=0; i<ja.length(); i++ ) {
                if ( i>0 ) sb.append("\n");
                sb.append(ja.getString(i));
            }
            params.put( "param1Values", sb.toString() );
        } else {
            throw new IllegalArgumentException("bad format in file "+f);
        }
        
        o= jo.get("param2Values");
        if ( o instanceof String ) {
            params.put( "param2Values", (String)o );
        } else if ( o instanceof JSONArray ) {
            JSONArray ja= (JSONArray)o;
            StringBuilder sb= new StringBuilder();
            for ( int i=0; i<ja.length(); i++ ) {
                if ( i>0 ) sb.append("\n");
                sb.append(ja.getString(i));
            }
            params.put( "param2Values", sb.toString() );
        } else {
            throw new IllegalArgumentException("bad format in file "+f);
        }
        
        
        run= () -> {
            RunBatchTool.this.param1NameCB.setSelectedItem(params.get("param1"));
            RunBatchTool.this.param2NameCB.setSelectedItem(params.get("param2"));
            RunBatchTool.this.param1Values.setText(params.get("param1Values"));
            RunBatchTool.this.param2Values.setText(params.get("param2Values"));
        };
        try {
            SwingUtilities.invokeAndWait(run);
        } catch (InterruptedException | InvocationTargetException ex) {
            Logger.getLogger(org.autoplot.RunBatchTool.class.getName()).log(Level.SEVERE, null, ex);
        }
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
        //switchToEditableList();
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
        String script= FileUtil.readFileToString(scriptFile);
        
        env.put("dom",this.dom);
        env.put("PWD",pwd);
                                
        Map<String,Param> parms= Util.getParams( env, script, params, new NullProgressMonitor() );

        Param p= parms.get(name);
        
        return p;        
        
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
                if ( pd.constraints.containsKey("format") ) {
                    String format= (String)pd.constraints.get("format");
                    timeFormatComboBox.setSelectedItem(format);
                }
                if ( pd.constraints.containsKey("min") && pd.constraints.containsKey("max") ) {
                    try {
                        String minMax= Ops.datumRange( pd.constraints.get("min")+"/"+pd.constraints.get("max") ).toString();
                        timeRangeComboBox.setSelectedItem(minMax);
                    } catch ( IllegalArgumentException ex ) {
                        ex.printStackTrace();
                    }
                }
                if ( AutoplotUtil.showConfirmDialog( this, timeRangesPanel, "Generate Time Ranges", JOptionPane.OK_CANCEL_OPTION )==JOptionPane.OK_OPTION ) {
                    String timeRange= timeRangeComboBox.getSelectedItem().toString();
                    String template= timeFormatComboBox.getSelectedItem().toString();
                    prefs.put("lastTimeRange", timeRange);
                    prefs.put("lastTimeFormat", template);
                    Pattern p= Pattern.compile("\\$\\(o[,;]id=([a-zA-Z\\-_]+)\\)");
                    Matcher m= p.matcher(template);
                    if ( m.matches() ) {
                        String id= m.group(1);
                        template= "orbit:"+id+":"+template;
                    }
                    ss= ScriptContext.generateTimeRanges(template, timeRange );
                }
            } catch (ParseException ex) {
                Logger.getLogger(org.autoplot.RunBatchTool.class.getName()).log(Level.SEVERE, null, ex);
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
            int h= panel.getFont().getSize()*2;
            panel.setLayout( new BoxLayout( panel, BoxLayout.Y_AXIS ) );
            panel.setAlignmentX(Component.LEFT_ALIGNMENT);
            String label= pd.label;
            if ( pd.doc!=null ) label= "<html>"+label+", <i>"+pd.doc+"</i>";
            panel.add( new JLabel( label ) );
            JTextField min= new JTextField( "" );
            min.setAlignmentX(Component.LEFT_ALIGNMENT);
            min.setMaximumSize( new Dimension(h*10,h));
            JTextField max= new JTextField( "" );
            max.setAlignmentX(Component.LEFT_ALIGNMENT);
            max.setMaximumSize( new Dimension(h*10,h));
            JTextField step= new JTextField( "" );
            step.setAlignmentX(Component.LEFT_ALIGNMENT);
            step.setMaximumSize( new Dimension(h*10,h));
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
    
    private void switchToEditableList() {
        messageLabel.setText("Load up those parameters and hit Go!");
        param1ScrollPane.getViewport().setView(param1Values);
        param2ScrollPane.getViewport().setView(param2Values);
    }
    
    private void updateListIcons( int paramNumber) {
            
        JLabel[] jobs;
        if ( paramNumber==1 ) {
            jobs= this.param1JLabels;
        } else {
            jobs= this.param2JLabels;
        }

        int numParam2= RunBatchTool.this.param2JLabels.length;

        File mainBatch = this.mainBatchFile;
        
        File[] ff;

        ff = new File( mainBatch.getParentFile(), "complete" ).listFiles();
        if ( ff!=null ) {
            for ( File f: ff ) {
                int num= Integer.parseInt( f.getName() );
                int num1= Math.floorDiv( num, numParam2 );
                if ( paramNumber==1 ) {
                    jobs[num1].setIcon(ICON_OKAY);
                } else {
                    if ( num1==selectedIndex1 ) {
                        int num2= Math.floorMod( num, numParam2 );
                        jobs[num2].setIcon(ICON_OKAY);
                    }
                }
            }
        }

        ff = new File( mainBatch.getParentFile(), "jobs" ).listFiles();
        if ( ff!=null ) {
            for ( File f: ff ) {
                int num= Integer.parseInt( f.getName() );
                int num1= Math.floorDiv( num, numParam2 );
                if ( paramNumber==1 ) {
                    jobs[num1].setIcon(ICON_QUEUED);
                } else {
                    if ( num1==selectedIndex1 ) {
                        int num2= Math.floorMod( num, numParam2 );
                        jobs[num2].setIcon(ICON_QUEUED);
                    }
                }
            }
        }

        ff = new File( mainBatch.getParentFile(), "pending" ).listFiles();
        if ( ff!=null ) {
            for ( File f: ff ) {
                int num= Integer.parseInt( f.getName() );
                int num1= Math.floorDiv( num, numParam2 );
                if ( paramNumber==1 ) {
                    jobs[num1].setIcon(ICON_WORKING);
                } else {
                    if ( num1==selectedIndex1 ) {
                        int num2= Math.floorMod( num, numParam2 );
                        jobs[num2].setIcon(ICON_WORKING);
                    }
                }
            }
        }

        ff = new File( mainBatch.getParentFile(), "exceptions" ).listFiles();
        if ( ff!=null ) {
            for ( File f: ff ) {
                int num= Integer.parseInt( f.getName() );
                int num1= Math.floorDiv( num, numParam2 );
                if ( paramNumber==1 ) {
                    jobs[num1].setIcon(ICON_PROB);
                } else {
                    if ( num1==selectedIndex1 ) {
                        int num2= Math.floorMod( num, numParam2 );
                        jobs[num2].setIcon(ICON_PROB);
                    }
                }
            }
        }        

    }
            
    /**
     * 
     * @param jobs1 list to put the JLabels into.
     * @param paramNumber 1 for the left param, 2 for the right
     * @param ff1
     * @return 
     */
    private JPanel switchListToIconLabels( final int paramNumber, String[] ff1 ) {
        JPanel p= new JPanel();
            
        JLabel[] jobs1= new JLabel[ff1.length];
        if ( paramNumber==1 ) {
            this.param1JLabels= jobs1;
        } else {
            this.param2JLabels= jobs1;
        }
        
        p.setLayout( new BoxLayout(p,BoxLayout.Y_AXIS) );
        int index=0;
        for ( String f: ff1 ) {
            final int findex= index;
            JLabel l= new JLabel(f);
            l.setIcon(ICON_QUEUED);
            p.add( l );
            jobs1[index]=l;
            l.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if ( paramNumber==1 ) {
                        param1JLabels[selectedIndex1].setBorder( null );
                        selectedIndex1= findex;
                        updateListIcons(2);
                        param1JLabels[selectedIndex1].setBorder( BorderFactory.createLineBorder(Color.black) );
                    }
                }
            });
            index++;
        }

        JScrollPane scrollp= new JScrollPane(p);
        scrollp.getVerticalScrollBar().setUnitIncrement( scrollp.getFont().getSize());
        scrollp.setPreferredSize( new Dimension(640,640));
        scrollp.setMaximumSize( new Dimension(640,640));
            
        messageLabel.setText(RUNNING_LABEL_MOUSEOVER);
        return p;
    }    
    private static final String RUNNING_LABEL_MOUSEOVER = "Running jobs, mouse over to view tooltip containing standard output.";
    
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
    
    private void exportResultsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportResultsMenuItemActionPerformed

    }//GEN-LAST:event_exportResultsMenuItemActionPerformed

    private void showHelpMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showHelpMenuItemActionPerformed
        AutoplotUtil.openBrowser("https://github.com/autoplot/documentation/blob/main/md/batch.md");
    }//GEN-LAST:event_showHelpMenuItemActionPerformed

    private JLabel selectedLabel;
    
    private JLabel getSelectedLabel() {
        return this.selectedLabel;
    }
    
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
            StringSelection stringSelection= new StringSelection( "Internal error..." );
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents( stringSelection, null );
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
            String script= FileUtil.readFileToString(scriptFile);
            Map<String,String> params= URISplit.parseParams( split.params );
            Map<String,org.autoplot.jythonsupport.Param> parms= Util.getParams( env, script, params, new NullProgressMonitor() );
            Runnable run= () -> {
                //doOneJob( jLabel1, scriptFile, parms, params, argName, argValue, new NullProgressMonitor() );
            };
            jLabel1.setIcon( ICON_WORKING );
            new Thread( run, "run-batch-0" ).start();

        } catch (IOException ex) {
            Logger.getLogger(RunBatchTool.class.getName()).log(Level.SEVERE, null, ex);
        }

    }//GEN-LAST:event_rerunScriptMenuItemActionPerformed

    private void copyValueMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyValueMenuItemActionPerformed
        JLabel p= getSelectedLabel();
        String argValue= p.getText();
        System.err.println(argValue);
        StringSelection stringSelection= new StringSelection( argValue );
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents( stringSelection, null );
        messageLabel.setText("Copied to system clipboard: "+argValue );
    }//GEN-LAST:event_copyValueMenuItemActionPerformed


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
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem OpenMenuItem;
    private javax.swing.JMenuItem SaveAsMenuItem;
    private javax.swing.JCheckBox activeFocusCB;
    private javax.swing.JButton cancelButton;
    private javax.swing.JButton closeButton;
    private javax.swing.JMenuItem copyScriptUri;
    private javax.swing.JMenuItem copyValueMenuItem;
    private org.autoplot.datasource.DataSetSelector dataSetSelector1;
    private javax.swing.JButton deleteDirectoryButton;
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
    // End of variables declaration//GEN-END:variables
}
