/*
 * JythonScriptPanel.java
 *
 * Created on May 13, 2008, 11:24 AM
 */
package org.autoplot.scriptconsole;

import java.awt.Color;
import java.awt.Dimension;
import org.autoplot.jythonsupport.ui.EditorContextMenu;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.Element;
import org.das2.jythoncompletion.JythonCompletionProvider;
import org.das2.jythoncompletion.JythonCompletionTask;
import org.das2.jythoncompletion.JythonInterpreterProvider;
import org.das2.jythoncompletion.ui.CompletionImpl;
import org.das2.util.FileUtil;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.NullProgressMonitor;
import org.python.core.Py;
import org.python.core.PyDictionary;
import org.python.util.PythonInterpreter;
import org.autoplot.AppManager;
import org.autoplot.ApplicationModel;
import org.autoplot.AutoplotUI;
import org.autoplot.JythonUtil;
import org.autoplot.ScriptContext;
import org.autoplot.dom.ApplicationController;
import org.autoplot.datasource.DataSetSelector;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.jythonsupport.ui.EditorAnnotationsSupport;
import org.autoplot.jythonsupport.ui.EditorTextPane;

/**
 * GUI for editing and running Jython scripts.
 * @author  jbf
 */
public class JythonScriptPanel extends javax.swing.JPanel {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot");

    ApplicationModel model;
    ApplicationController applicationController;
    DataSetSelector selector;
    ScriptPanelSupport support;
    static final int CONTEXT_DATA_SOURCE = 1;
    static final int CONTEXT_APPLICATION = 0;
    private int context = 0;
    File runningScript= null; // the script being run.

    /**
     * true if the current file contains tabs.
     */
    boolean containsTabs= false;

    private transient DocumentListener dirtyListener; // this contains repeated code in ScriptPanelSupport  

    public void loadExampleUri( String uri ) {
        try {
            File ff= DataSetURI.getFile( uri, new NullProgressMonitor() );
            loadFile( ff );
        } catch (IOException ex) {
            Logger.getLogger(JythonScriptPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /** 
     * Creates new form JythonScriptPanel 
     * 
     * @param app the app with which we'll register the close callback.
     * @param selector the selector which might receive jyds URIs when "execute" is pressed.
     */
    public JythonScriptPanel( AutoplotUI app, final DataSetSelector selector) {
        initComponents();
        setMinimumSize( new Dimension(400,400) );
        
        jScrollPane2.getVerticalScrollBar().setUnitIncrement( 12 ); // TODO: should be line height.

        setContext(CONTEXT_APPLICATION);

        this.model = app.getApplicationModel();
        support = new ScriptPanelSupport(this, model, selector);

        this.applicationController= model.getDocumentModel().getController();
        
        this.selector = selector;

        this.textArea.setFont( Font.decode(JythonCompletionProvider.getInstance().settings().getEditorFont() ) );
        
        this.textArea.addCaretListener(new CaretListener() {
            @Override
            public void caretUpdate(CaretEvent e) {
                int pos = textArea.getCaretPosition();
                Element root = textArea.getDocument().getDefaultRootElement();
                int irow = root.getElementIndex(pos);
                int icol = pos - root.getElement(irow).getStartOffset();
                String text = "" + (1 + irow) + "," + (1 + icol);
                int isel = textArea.getSelectionEnd() - textArea.getSelectionStart();
                int iselRow0 = root.getElementIndex(textArea.getSelectionStart());
                int iselRow1 = root.getElementIndex(textArea.getSelectionEnd());
                if (isel > 0) {
                    if (iselRow1 > iselRow0) {
                        text = "[" + isel + "ch," + (1 + iselRow1 - iselRow0) + "lines]";
                    } else {
                        text = "[" + isel + "ch]";
                    }
                }

                caretPositionLabel.setText(text);
            }
        });


        dirtyListener= new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                setDirty(true);
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
                setDirty(true);
            }
            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        };

        this.textArea.getDocument().addDocumentListener(dirtyListener);
        this.textArea.addPropertyChangeListener( "document", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if ( evt.getOldValue()!=null ) {
                    ((Document)evt.getOldValue()).removeDocumentListener(dirtyListener);
                }
                ((Document)evt.getNewValue()).addDocumentListener(dirtyListener);
            }

        });

        this.textArea.getActionMap().put( ACTIONKEY_SAVE, new AbstractAction( ACTIONKEY_SAVE ) {
            @Override
            public void actionPerformed( ActionEvent e ) {
                LoggerManager.logGuiEvent(e);                
                try {
                    support.save();
                } catch (FileNotFoundException ex) {
                    model.getExceptionHandler().handle(ex);
                } catch (IOException ex) {
                    model.getExceptionHandler().handle(ex);
                }
            }
        });

        this.textArea.getActionMap().put( ACTIONKEY_EXECUTE, new AbstractAction( ACTIONKEY_EXECUTE ) {
            @Override
            public void actionPerformed( ActionEvent e ) {
                executeButtonActionPerformed(e);
            }
        });
        
        AppManager.getInstance().addCloseCallback( app, "jythonScriptPanel", new AppManager.CloseCallback() {
            @Override
            public boolean checkClose() {
                if ( isDirty() && isVisible() && textArea.getDocument().getLength()>0 ) { 
                    int resp= JOptionPane.showConfirmDialog( JythonScriptPanel.this, "Script Editor contains unsaved changes.  Save these changes?" );
                    switch (resp) {
                        case JOptionPane.CANCEL_OPTION:       
                            return false;
                        case JOptionPane.OK_OPTION:
                            try {
                                return support.save() == JOptionPane.OK_OPTION;
                            } catch ( IOException ex ) {
                                return false;
                            }
                        case JOptionPane.NO_OPTION:
                            return true;
                        default:
                            break;
                    }
                    return false;
                } else {
                    return true;
                }
            }
        });

        
        this.textArea.getInputMap().put(KeyStroke.getKeyStroke( KeyEvent.VK_S,Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() ), ACTIONKEY_SAVE);
        this.textArea.getInputMap().put(KeyStroke.getKeyStroke( KeyEvent.VK_F6, 0 ), ACTIONKEY_EXECUTE);
        this.textArea.getInputMap().put(KeyStroke.getKeyStroke( KeyEvent.VK_F6, InputEvent.SHIFT_DOWN_MASK ), ACTIONKEY_EXECUTE );
        
        EditorContextMenu menu= new EditorContextMenu( this.textArea );

        menu.addExampleAction( new AbstractAction("makePngWalk.jy") {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);
                loadExample( "/scripts/pngwalk/makePngWalk.jy" );
            }
        });
        menu.addExampleAction( new AbstractAction("addDigitizer.jy") {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);
                loadExample( "/scripts/addDigitizer.jy" );
            }
        });
        menu.addExampleAction( new AbstractAction("splineDemo.jy") {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);
                loadExample( "/scripts/splineDemo.jy" );
            }
        });

        menu.addExampleAction( new AbstractAction("More Jython Scripts...") {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);
                DataSourceUtil.openBrowser( "http://apps-pw.physics.uiowa.edu/hudson/job/autoplot-release/ws/autoplot/VirboAutoplot/src/scripts/" );
            }
        });

        menu.addExampleAction( new AbstractAction("mashup.jyds") {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);
                loadExample( "/mashup.jyds" );
            }
        });

        menu.addExampleAction( new AbstractAction("rheology.jyds") {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);
                loadExample( "/rheology.jyds" );
            }
        });

        menu.addExampleAction( new AbstractAction("More Jython Data Source Scripts...") {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);
                DataSourceUtil.openBrowser( "http://apps-pw.physics.uiowa.edu/hudson/job/autoplot-release/ws/autoplot/JythonDataSource/src/" );
            }
        });

        menu.setDataSetSelector(selector);

        JythonCompletionProvider.getInstance().addPropertyChangeListener( JythonCompletionProvider.PROP_MESSAGE, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                applicationController.setStatus(JythonCompletionProvider.getInstance().getMessage());
            }
        });

        support.addPropertyChangeListener(ScriptPanelSupport.PROP_INTERRUPTABLE,new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if ( evt.getNewValue()==null ) {
                    //interruptButton.setIcon(
                    //        new javax.swing.ImageIcon(
                    //        getClass().getResource(
                    //        "/org/autoplot/resources/stop-icon-disabled.png")) );
                    interruptButton.setEnabled(false);
                    executeButton.setEnabled(true);
                } else {
                    //interruptButton.setIcon(
                    //        new javax.swing.ImageIcon(
                    //        getClass().getResource(
                    //        "/org/autoplot/resources/stop-icon.png")) );
                    interruptButton.setEnabled(true);
                    executeButton.setEnabled(false);
                }
            }
        } );

        CompletionImpl impl = CompletionImpl.get();
        impl.startPopup(this.textArea);
    }
    
    private static final String ACTIONKEY_SAVE = "save";
    private static final String ACTIONKEY_EXECUTE = "execute";
    
    /**
     * load in an example, replacing the current editor text.
     * @param resourceFile the name of a file loaded with
     *    EditorContextMenu.class.getResource(resourceFile);
     */
    private void loadExample( String resourceFile ) {
        try {
            URL url = EditorContextMenu.class.getResource(resourceFile);
            if (this.isDirty()) {
                if ( this.support.saveAs()==JFileChooser.CANCEL_OPTION ) {
                    return;
                }
            }
            this.support.loadInputStream(url.openStream());
            if ( resourceFile.endsWith(".jy") ) {
                this.setContext(CONTEXT_APPLICATION);
            } else {
                this.setContext(CONTEXT_DATA_SOURCE);
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    protected void updateStatus() {
        if ( filename==null ) {
            fileNameTextField.setText( "" + ( dirty ? " *" : "" ) + ( containsTabs ? " TAB" : "" ) );
            getEditorPanel().setEditable(true);
        } else {
            File lfile= new File(filename);
            boolean writable= lfile.canWrite() && !FileUtil.isParent( FileSystem.settings().getLocalCacheDir(), lfile );
            getEditorPanel().setEditable(writable);
            fileNameTextField.setText( filename + ( writable ? "" : " (read only)" ) + ( dirty ? " *" : "" ) + ( containsTabs ? " TAB" : "" ) ) ;
        }
    }

    int getContext() {
        return context;
    }

    protected final void setContext(int context) {
        this.context = context;
        this.contextSelector.setSelectedIndex(context);
        if (context == CONTEXT_APPLICATION) {
            this.textArea.putClientProperty(JythonCompletionTask.CLIENT_PROPERTY_INTERPRETER_PROVIDER, new JythonInterpreterProvider() {
                @Override
                public PythonInterpreter createInterpreter() throws java.io.IOException {
                    PythonInterpreter interp = JythonUtil.createInterpreter(true, false);
                    interp.set("dom", model.getDocumentModel() );
                    interp.set("params", new PyDictionary());
                    interp.set("resourceURI", Py.None );
                    return interp;
                }
            });
        } else if (context == CONTEXT_DATA_SOURCE) {
            this.textArea.putClientProperty(JythonCompletionTask.CLIENT_PROPERTY_INTERPRETER_PROVIDER, new JythonInterpreterProvider() {
                @Override
                public PythonInterpreter createInterpreter() throws java.io.IOException {
                    PythonInterpreter interp = JythonUtil.createInterpreter(false,false);
                    interp.set("params", new PyDictionary());
                    interp.set("resourceURI", Py.None );
                    return interp;
                }
            });
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        executeButton = new javax.swing.JButton();
        saveAsButton = new javax.swing.JButton();
        openButton = new javax.swing.JButton();
        contextSelector = new javax.swing.JComboBox();
        caretPositionLabel = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        textArea = new org.autoplot.jythonsupport.ui.EditorTextPane();
        newScriptButton = new javax.swing.JButton();
        interruptButton = new javax.swing.JButton();
        fileNameTextField = new javax.swing.JTextField();

        executeButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/autoplot/go.png"))); // NOI18N
        executeButton.setText("Execute");
        executeButton.setToolTipText("<html>Execute script.  <br>Alt modifier enters editor GUI.  <br>Ctrl modifier attempts to trace program location.  <br>Shift modifier will being up parameters gui.");
        executeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                executeButtonActionPerformed(evt);
            }
        });

        saveAsButton.setText("Save As...");
        saveAsButton.setToolTipText("Save the buffer to a local file.");
        saveAsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveAsButtonActionPerformed(evt);
            }
        });

        openButton.setText("Open...");
        openButton.setToolTipText("Open the local file to the buffer.");
        openButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openButtonActionPerformed(evt);
            }
        });

        contextSelector.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Application Context", "Data Source Context" }));
        contextSelector.setToolTipText("<html>select the context for the script: to create new datasets (data source context), or to control an application (application context)</html>\n");
        contextSelector.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                contextSelectorActionPerformed(evt);
            }
        });

        caretPositionLabel.setText("1,1");
        caretPositionLabel.setToolTipText("row,column; or the number of characters and lines selected.");

        textArea.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                textAreaFocusGained(evt);
            }
        });
        jScrollPane2.setViewportView(textArea);

        newScriptButton.setText("New");
        newScriptButton.setToolTipText("Reset the buffer to a new file.");
        newScriptButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newScriptButtonActionPerformed(evt);
            }
        });

        interruptButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/resources/stop.png"))); // NOI18N
        interruptButton.setText("Stop");
        interruptButton.setToolTipText("Interrupt running script");
        interruptButton.setEnabled(false);
        interruptButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                interruptButtonActionPerformed(evt);
            }
        });

        fileNameTextField.setEditable(false);
        fileNameTextField.setFont(fileNameTextField.getFont().deriveFont(fileNameTextField.getFont().getSize()-2f));
        fileNameTextField.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(executeButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 124, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(interruptButton)
                .add(7, 7, 7)
                .add(saveAsButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(openButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(newScriptButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(contextSelector, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(fileNameTextField)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(caretPositionLabel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 99, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .add(jScrollPane2)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(executeButton)
                    .add(contextSelector, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(saveAsButton)
                    .add(openButton)
                    .add(newScriptButton)
                    .add(interruptButton))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jScrollPane2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 280, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(caretPositionLabel)
                    .add(fileNameTextField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
        );

        layout.linkSize(new java.awt.Component[] {executeButton, interruptButton, newScriptButton, openButton, saveAsButton}, org.jdesktop.layout.GroupLayout.VERTICAL);

    }// </editor-fold>//GEN-END:initComponents
    private void executeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_executeButtonActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);                
        if ( dirty && support.file!=null ) {
            try {
                support.save(); 
            } catch ( IOException ex ) {
                
            }
        }
        System.err.println("== Executing Script ==");
        ScriptContext.setWindow(model);
        if ( support.file!=null ) this.setRunningScript(support.file);
        support.executeScript( evt.getModifiers() );
    }//GEN-LAST:event_executeButtonActionPerformed

    private void saveAsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveAsButtonActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);                
        support.saveAs();
}//GEN-LAST:event_saveAsButtonActionPerformed

    private void openButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openButtonActionPerformed
        org.das2.util.LoggerManager.logGuiEvent(evt);                
        support.open();
}//GEN-LAST:event_openButtonActionPerformed

private void contextSelectorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_contextSelectorActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);      
    setContext(contextSelector.getSelectedIndex());
}//GEN-LAST:event_contextSelectorActionPerformed

private void textAreaFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_textAreaFocusGained
    CompletionImpl impl = CompletionImpl.get();
    impl.startPopup(textArea);
}//GEN-LAST:event_textAreaFocusGained

private void newScriptButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newScriptButtonActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);                
    support.newScript();
}//GEN-LAST:event_newScriptButtonActionPerformed

private void interruptButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_interruptButtonActionPerformed
    org.das2.util.LoggerManager.logGuiEvent(evt);                
    support.interrupt();
}//GEN-LAST:event_interruptButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel caretPositionLabel;
    private javax.swing.JComboBox contextSelector;
    private javax.swing.JButton executeButton;
    private javax.swing.JTextField fileNameTextField;
    private javax.swing.JButton interruptButton;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JButton newScriptButton;
    private javax.swing.JButton openButton;
    private javax.swing.JButton saveAsButton;
    private org.autoplot.jythonsupport.ui.EditorTextPane textArea;
    // End of variables declaration//GEN-END:variables

    public EditorTextPane getEditorPanel() {
        return textArea;
    }
    
    
    protected String filename = null;

    public static final String PROP_FILENAME = "filename";

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        String oldFilename = this.filename;
        this.filename = filename;
        updateStatus();
        firePropertyChange(PROP_FILENAME, oldFilename, filename);
    }

    protected boolean dirty = false;

    public static final String PROP_DIRTY = "dirty";

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        boolean oldDirty = this.dirty;
        this.dirty = dirty;
        if ( oldDirty!=dirty ) updateStatus();
        firePropertyChange(PROP_DIRTY, oldDirty, dirty);
    }

    /**
     * allow clients to tell this to load a file.  
     * @param file the .jy or .jyds file.
     * @return false if the editor is dirty, true if the file is loaded.
     * @throws java.io.IOException
     */
    public boolean loadFile( File file ) throws IOException {
        if ( isDirty() ) {
            return false;
        } else {
            support.loadFile(file);
            return true;
        }
    }

    /**
     * provide access to the annotations support, so that errors
     * can be marked on the editor.  This may cause bugs as there
     * are issues like ensuring the exception marked belongs to the code,
     * and it should not be used without reservation.
     *
     * @return the annotations support.
     */
    public EditorAnnotationsSupport getAnnotationsSupport() {
        return support.annotationsSupport;
    }
    
    /**
     * set the current script that is running.  This will prevent 
     * automatic loads from occurring.
     * @param f 
     */
    public void setRunningScript( File f ) {
        this.runningScript= f;        
    }
    
    /**
     * return the name of the script that the panel is busy running, or null.
     * When this is non-null, don't load other scripts.
     * @return null or the running script.  
     */
    public File getRunningScript() {
        return this.runningScript;
    }
}
