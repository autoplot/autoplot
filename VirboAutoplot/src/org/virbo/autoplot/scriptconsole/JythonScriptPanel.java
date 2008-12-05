/*
 * JythonScriptPanel.java
 *
 * Created on May 13, 2008, 11:24 AM
 */
package org.virbo.autoplot.scriptconsole;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import javax.beans.binding.Binding;
import javax.beans.binding.BindingContext;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Element;
import org.das2.jythoncompletion.JythonCompletionProvider;
import org.das2.jythoncompletion.JythonCompletionTask;
import org.das2.jythoncompletion.JythonInterpreterProvider;
import org.das2.jythoncompletion.ui.CompletionImpl;
import org.das2.util.monitor.NullProgressMonitor;
import org.python.core.PyDictionary;
import org.python.util.PythonInterpreter;
import org.virbo.autoplot.ApplicationModel;
import org.virbo.autoplot.JythonUtil;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.datasource.DataSetSelector;

/**
 *
 * @author  jbf
 */
public class JythonScriptPanel extends javax.swing.JPanel {

    File file;
    ApplicationModel model;
    ApplicationController applicationController;
    DataSetSelector selector;
    ScriptPanelSupport support;
    static final int CONTEXT_DATA_SOURCE = 1;
    static final int CONTEXT_APPLICATION = 0;
    private int context = 0;

    /** Creates new form JythonScriptPanel */
    public JythonScriptPanel( final ApplicationModel model, final DataSetSelector selector) {
        initComponents();
        
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

        this.textArea.getDocument().addDocumentListener(new DocumentListener() {

            public void insertUpdate(DocumentEvent e) {
                setDirty(true);
            }

            public void removeUpdate(DocumentEvent e) {
                setDirty(true);
            }

            public void changedUpdate(DocumentEvent e) {
            }
        });

        EditorContextMenu menu= new EditorContextMenu( this.textArea );
        menu.setDataSetSelector(selector);

        JythonCompletionProvider.getInstance().addPropertyChangeListener( JythonCompletionProvider.PROP_MESSAGE, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                applicationController.setStatus(JythonCompletionProvider.getInstance().getMessage());
            }
        });
        
        CompletionImpl impl = CompletionImpl.get();
        impl.startPopup(this.textArea);

    }

    protected void updateStatus() {
        fileNameLabel.setText( ( filename==null ? "" : filename ) + ( dirty ? " *" : "" ) ) ;
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
                    return JythonUtil.createInterpreter(true, false);
                }
            });
        } else if (context == CONTEXT_DATA_SOURCE) {
            this.textArea.putClientProperty(JythonCompletionTask.CLIENT_PROPERTY_INTERPRETER_PROVIDER, new JythonInterpreterProvider() {
                public PythonInterpreter createInterpreter() throws java.io.IOException {
                    PythonInterpreter interp = org.virbo.jythonsupport.JythonUtil.createInterpreter(false);
                    interp.set("monitor", new NullProgressMonitor());
                    interp.set("params", new PyDictionary());
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
        fileNameLabel = new javax.swing.JLabel();
        contextSelector = new javax.swing.JComboBox();
        caretPositionLabel = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        textArea = new org.virbo.autoplot.scriptconsole.EditorTextPane();

        savePlotButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/virbo/autoplot/go.png"))); // NOI18N
        savePlotButton.setText("execute");
        savePlotButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                savePlotButtonActionPerformed(evt);
            }
        });

        saveAsButton.setText("save as...");
        saveAsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveAsButtonActionPerformed(evt);
            }
        });

        openButton.setText("open...");
        openButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openButtonActionPerformed(evt);
            }
        });

        contextSelector.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "application context", "data source context" }));
        contextSelector.setToolTipText("select the context for the script: to create new datasets, or to control an application.");
        contextSelector.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                contextSelectorActionPerformed(evt);
            }
        });

        caretPositionLabel.setText("1,1");

        textArea.setFont(new java.awt.Font("Monospaced", 0, 13)); // NOI18N
        jScrollPane1.setViewportView(textArea);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(savePlotButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 124, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(saveAsButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(openButton)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 81, Short.MAX_VALUE)
                .add(contextSelector, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .add(fileNameLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 406, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(caretPositionLabel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 87, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 511, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(savePlotButton)
                    .add(saveAsButton)
                    .add(openButton)
                    .add(contextSelector, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 270, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(caretPositionLabel)
                    .add(fileNameLabel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 18, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
        );

        layout.linkSize(new java.awt.Component[] {openButton, saveAsButton, savePlotButton}, org.jdesktop.layout.GroupLayout.VERTICAL);

        layout.linkSize(new java.awt.Component[] {caretPositionLabel, fileNameLabel}, org.jdesktop.layout.GroupLayout.VERTICAL);

    }// </editor-fold>//GEN-END:initComponents
    private void savePlotButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_savePlotButtonActionPerformed
        support.executeScript();
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
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel caretPositionLabel;
    private javax.swing.JComboBox contextSelector;
    protected javax.swing.JLabel fileNameLabel;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JButton openButton;
    private javax.swing.JButton saveAsButton;
    private javax.swing.JButton savePlotButton;
    private org.virbo.autoplot.scriptconsole.EditorTextPane textArea;
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
        updateStatus();
        firePropertyChange(PROP_DIRTY, oldDirty, dirty);
    }

}
