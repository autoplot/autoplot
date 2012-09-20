/*
 * JythonScriptPanel.java
 *
 * Created on May 13, 2008, 11:24 AM
 */
package org.virbo.autoplot.scriptconsole;

import org.virbo.jythonsupport.ui.EditorContextMenu;
import java.awt.Event;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
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
import org.python.core.Py;
import org.python.core.PyDictionary;
import org.python.util.PythonInterpreter;
import org.virbo.autoplot.ApplicationModel;
import org.virbo.autoplot.JythonUtil;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.datasource.DataSetSelector;
import org.virbo.jythonsupport.ui.EditorTextPane;

/**
 *
 * @author  jbf
 */
public class JythonScriptPanel extends javax.swing.JPanel {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot");

    File file;
    ApplicationModel model;
    ApplicationController applicationController;
    DataSetSelector selector;
    ScriptPanelSupport support;
    static final int CONTEXT_DATA_SOURCE = 1;
    static final int CONTEXT_APPLICATION = 0;
    private int context = 0;

    private DocumentListener dirtyListener; // this contains repeated code in ScriptPanelSupport

    /** Creates new form JythonScriptPanel */
    public JythonScriptPanel( final ApplicationModel model, final DataSetSelector selector) {
        initComponents();
        jScrollPane2.getVerticalScrollBar().setUnitIncrement( 12 ); // TODO: should be line height.

        setContext(CONTEXT_APPLICATION);
        
        support = new ScriptPanelSupport(this, model, selector);

        this.model = model;
        this.applicationController= model.getDocumentModel().getController();
        
        this.selector = selector;

        this.textArea.addCaretListener(new CaretListener() {

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

            public void insertUpdate(DocumentEvent e) {
                setDirty(true);
            }

            public void removeUpdate(DocumentEvent e) {
                setDirty(true);
            }

            public void changedUpdate(DocumentEvent e) {
            }
        };

        this.textArea.getDocument().addDocumentListener(dirtyListener);
        this.textArea.addPropertyChangeListener( "document", new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                if ( evt.getOldValue()!=null ) {
                    ((Document)evt.getOldValue()).removeDocumentListener(dirtyListener);
                }
                ((Document)evt.getNewValue()).addDocumentListener(dirtyListener);
            }

        });

        this.textArea.getActionMap().put( "save", new AbstractAction( "save" ) {
            public void actionPerformed( ActionEvent e ) {
                try {
                    support.save();
                } catch (FileNotFoundException ex) {
                    model.getExceptionHandler().handle(ex);
                } catch (IOException ex) {
                    model.getExceptionHandler().handle(ex);
                }
            }
        });

        this.textArea.getInputMap().put( KeyStroke.getKeyStroke( KeyEvent.VK_S,Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() ), "save" );

        EditorContextMenu menu= new EditorContextMenu( this.textArea );

        menu.addExampleAction( new AbstractAction("makePngWalk.jy") {
            public void actionPerformed(ActionEvent e) {
                loadExample( "/scripts/pngwalk/makePngWalk.jy" );
            }
        });

        menu.setDataSetSelector(selector);

        JythonCompletionProvider.getInstance().addPropertyChangeListener( JythonCompletionProvider.PROP_MESSAGE, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                applicationController.setStatus(JythonCompletionProvider.getInstance().getMessage());
            }
        });

        support.addPropertyChangeListener(support.PROP_INTERRUPTABLE,new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ( evt.getNewValue()==null ) {
                    //interruptButton.setIcon(
                    //        new javax.swing.ImageIcon(
                    //        getClass().getResource(
                    //        "/org/virbo/autoplot/resources/stop-icon-disabled.png")) );
                    interruptButton.setEnabled(false);
                    savePlotButton.setEnabled(true);
                } else {
                    //interruptButton.setIcon(
                    //        new javax.swing.ImageIcon(
                    //        getClass().getResource(
                    //        "/org/virbo/autoplot/resources/stop-icon.png")) );
                    interruptButton.setEnabled(true);
                    savePlotButton.setEnabled(false);
                }
            }
        } );

        CompletionImpl impl = CompletionImpl.get();
        impl.startPopup(this.textArea);
    }

    /**
     * load in an example, replacing the current editor text.
     * @param resourceFile the name of a file loaded with
     *    EditorContextMenu.class.getResource(resourceFile);
     */
    private void loadExample( String resourceFile ) {
        try {
            URL url = EditorContextMenu.class.getResource(resourceFile);
            if (this.isDirty()) {
                if ( this.support.saveAs()==JOptionPane.CANCEL_OPTION ) {
                    return;
                }
            }
            this.support.loadInputStream(url.openStream());
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    protected void updateStatus() {
        if ( filename==null ) {
            fileNameTextField.setText( "" + ( dirty ? " *" : "" ));
        } else {
            File lfile= new File(filename);
            getEditorPanel().setEditable(lfile.canWrite());
            fileNameTextField.setText( filename + ( lfile.canWrite() ? "" : " (read only)" ) + ( dirty ? " *" : "" ) ) ;
        }
    }

    int getContext() {
        return context;
    }

    void setContext(int context) {
        int oldContext= this.context;
        if ( oldContext!=context ) {
            this.file= null;
        }
        this.context = context;
        this.contextSelector.setSelectedIndex(context);
        if (context == CONTEXT_APPLICATION) {
            this.textArea.putClientProperty(JythonCompletionTask.CLIENT_PROPERTY_INTERPRETER_PROVIDER, new JythonInterpreterProvider() {
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

        savePlotButton = new javax.swing.JButton();
        saveAsButton = new javax.swing.JButton();
        openButton = new javax.swing.JButton();
        contextSelector = new javax.swing.JComboBox();
        caretPositionLabel = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        textArea = new org.virbo.jythonsupport.ui.EditorTextPane();
        newScriptButton = new javax.swing.JButton();
        interruptButton = new javax.swing.JButton();
        fileNameTextField = new javax.swing.JTextField();

        savePlotButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/virbo/autoplot/go.png"))); // NOI18N
        savePlotButton.setText("Execute");
        savePlotButton.setToolTipText("Execute script.  Ctrl modifier attempts to trace program location.  ");
        savePlotButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                savePlotButtonActionPerformed(evt);
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

        textArea.setFont(new java.awt.Font("Monospaced", 0, 13));
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
                .add(savePlotButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 124, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(interruptButton)
                .add(7, 7, 7)
                .add(saveAsButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(openButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(newScriptButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 44, Short.MAX_VALUE)
                .add(contextSelector, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(fileNameTextField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 490, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(caretPositionLabel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 81, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .add(jScrollPane2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 583, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(savePlotButton)
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

        layout.linkSize(new java.awt.Component[] {interruptButton, newScriptButton, openButton, saveAsButton, savePlotButton}, org.jdesktop.layout.GroupLayout.VERTICAL);

    }// </editor-fold>//GEN-END:initComponents
    private void savePlotButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_savePlotButtonActionPerformed
        if ( dirty && support.file!=null ) {
            try {
                support.save(); 
            } catch ( IOException ex ) {
                
            }
        }
        if ( ( evt.getModifiers() & Event.CTRL_MASK ) ==Event.CTRL_MASK ) {
            support.executeScript(true);
        } else {
            support.executeScript();
        }
    }//GEN-LAST:event_savePlotButtonActionPerformed

    private void saveAsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveAsButtonActionPerformed
        support.saveAs();
}//GEN-LAST:event_saveAsButtonActionPerformed

    private void openButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openButtonActionPerformed
        support.open();
}//GEN-LAST:event_openButtonActionPerformed

private void contextSelectorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_contextSelectorActionPerformed
    setContext(contextSelector.getSelectedIndex());

}//GEN-LAST:event_contextSelectorActionPerformed

private void textAreaFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_textAreaFocusGained
    CompletionImpl impl = CompletionImpl.get();
    impl.startPopup(textArea);
}//GEN-LAST:event_textAreaFocusGained

private void newScriptButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newScriptButtonActionPerformed
    support.newScript();
}//GEN-LAST:event_newScriptButtonActionPerformed

private void interruptButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_interruptButtonActionPerformed
    support.interrupt();
}//GEN-LAST:event_interruptButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel caretPositionLabel;
    private javax.swing.JComboBox contextSelector;
    private javax.swing.JTextField fileNameTextField;
    private javax.swing.JButton interruptButton;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JButton newScriptButton;
    private javax.swing.JButton openButton;
    private javax.swing.JButton saveAsButton;
    private javax.swing.JButton savePlotButton;
    private org.virbo.jythonsupport.ui.EditorTextPane textArea;
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
     * @param file
     * @return
     */
    public boolean loadFile( File file ) throws IOException {
        if ( isDirty() ) {
            return false;
        } else {
            support.loadFile(file);
            return true;
        }
    }

}
