/*
 * DataSetSelector.java
 *
 * Created on November 5, 2007, 6:04 AM
 */
package org.virbo.datasource;

import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Window;
import org.das2.DasApplication;
import org.das2.util.DasExceptionHandler;
import java.util.logging.Level;
import javax.swing.text.BadLocationException;
import org.das2.util.monitor.ProgressMonitor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BoundedRangeModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.das2.system.MonitorFactory;
import org.das2.system.RequestProcessor;
import org.virbo.datasource.DataSetURL.CompletionResult;

/**
 * Swing Component for selecting dataset URIs.  This provides hooks for completions.
 *
 * @author  jbf
 */
public class DataSetSelector extends javax.swing.JPanel {

    /** Creates new form DataSetSelector */
    public DataSetSelector() {
        initComponents();
        editor = ((JTextField) dataSetSelector.getEditor().getEditorComponent());
        addCompletionKeys();
        addAbouts();

        maybePlotTimer = new Timer(100, new ActionListener() {

            public void actionPerformed(ActionEvent ev) {
                // some DataSource constructors do not return in interactive time, so create a new thread for now.
                Runnable run = new Runnable() {

                    public void run() {
                        maybePlotImmediately();
                    }
                };
                RequestProcessor.invokeLater(run);
            }
        });
        maybePlotTimer.setRepeats(false);
    }
    boolean needToAddKeys = true;
    /**
     * current completions task
     */
    Runnable completionsRunnable = null;
    ProgressMonitor completionsMonitor = null;
    JPopupMenu completionsPopupMenu = null;
    JTextField editor;
    DataSetSelectorSupport support = new DataSetSelectorSupport(this);
    public static final String PROPERTY_MESSAGE = "message";
    Logger logger = Logger.getLogger("virbo.dataset.ui");
    MonitorFactory monitorFactory = null;
    Timer maybePlotTimer;
    int keyModifiers = 0;

    public JTextField getEditor() {
        return editor;
    }

    private ProgressMonitor getMonitor() {
        return getMonitor("Please Wait", "unidentified task in progress");
    }

    private ProgressMonitor getMonitor(String label, String desc) {
        if (monitorFactory == null) {
            return DasApplication.getDefaultApplication().getMonitorFactory().getMonitor(label, desc);
        } else {
            return monitorFactory.getMonitor(label, desc);
        }
    }

    public void setMonitorFactory(MonitorFactory factory) {
        this.monitorFactory = factory;
    }

    private void maybePlotImmediately() {
        String surl = getValue();
        if (surl.equals("")) {
            logger.finest("empty value, returning");
            return;
        }

        if (surl.startsWith("vap+internal:")) {
            firePlotDataSetURL();
            return;
        }

        for (String actionTriggerRegex : actionTriggers.keySet()) {
            if (Pattern.matches(actionTriggerRegex, surl)) {
                logger.finest("matches action trigger");
                Action action = actionTriggers.get(actionTriggerRegex);
                action.actionPerformed(new ActionEvent(this, 123, "dataSetSelect"));
                return;
            }
        }

        try {

            if ( surl.endsWith("/") || surl.contains("/?") || surl.endsWith(".zip") || surl.contains(".zip?") ) {
                int carotpos= editor.getCaretPosition();
                //int carotpos = surl.contains("?") surl.length();
                setMessage("Getting filesystem completions.");
                showCompletions(surl, carotpos);

            } else if (surl.endsWith("/..")) { // pop up one directory
                int carotpos = surl.lastIndexOf("/..");
                carotpos = surl.lastIndexOf("/", carotpos - 1);
                if (carotpos != -1) {
                    setValue(surl.substring(0, carotpos + 1));
                    dataSetSelector.getEditor().setItem(surl.substring(0, carotpos + 1));
                    maybePlotImmediately();
                }
            } else {
                try {
                    DataSourceFactory f = DataSetURL.getDataSourceFactory(DataSetURL.getURI(surl), getMonitor());
                    if (f == null) {
                        throw new RuntimeException("unable to identify data source for URI, try \"about:plugins\"");
                    }
                    setMessage("check to see if uri looks acceptable");
                    String surl1 = URLSplit.uriDecode(DataSetURL.getResourceURI(surl).toString());
                    if (f.reject(surl1, getMonitor())) {
                        if (!surl.contains("?")) {
                            surl += "?";
                        }
                        setValue(surl);
                        setMessage("busy: url ambiguous, inspecting resource for parameters");
                        browseSourceType();
                    //int carotpos = surl.indexOf("?") + 1;

                    //showCompletions(surl, carotpos);
                    } else {
                        setMessage("resolving uri to data set as " + DataSourceRegistry.getInstance().getExtensionFor(f));
                        firePlotDataSetURL();
                    }
                } catch (DataSetURL.NonResourceException ex) { // see if it's a folder.
                    int carotpos = surl.length();
                    setMessage("no extension or mime type, try filesystem completions");
                    showCompletions(surl, carotpos);
                } catch (IllegalArgumentException ex) {
                    setMessage(ex.getMessage());
                    firePlotDataSetURL();
                } catch (URISyntaxException ex) {
                    setMessage(ex.getMessage());
                    firePlotDataSetURL();
                }
            }
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
            setMessage(ex.getMessage());
        } catch (IOException ex) {
            ex.printStackTrace();
            setMessage(ex.getMessage());
        }

    }

    /**
     * if the dataset requires parameters that aren't provided, then
     * show completion list.  Otherwise, fire off event.
     */
    public void maybePlot(boolean allowModifiers) {
        logger.fine("go " + getValue() + "");
        if (!allowModifiers) {
            keyModifiers = 0;
        }
        maybePlotTimer.restart();
    }

    private void firePlotDataSetURL() {
        List<String> r = new ArrayList<String>(getRecent());
        String value = getValue();
        if (r.contains(value)) {
            r.remove(value); // move to top of the list by remove then add.
        }
        r.add(value);
        setRecent(r);
        ActionEvent e = new ActionEvent(this, 123, "dataSetSelect", keyModifiers);
        fireActionListenerActionPerformed(e);

    }

    /**
     * show the initial parameters completions for the type, or the
     * editor, once that's introduced.
     */
    private void browseSourceType() {
        String surl = (String) dataSetSelector.getEditor().getItem();

        DataSourceEditorPanel edit=null;
        try {
            edit = DataSetURL.getDataSourceEditorPanel( DataSetURL.getURI(surl) );
        } catch (URISyntaxException ex) {
            Logger.getLogger(DataSetSelector.class.getName()).log(Level.SEVERE, null, ex);
        }

        if (edit != null) {
            edit.setUrl(surl);
            DataSourceEditorDialog dialog;
            Window window = SwingUtilities.getWindowAncestor(this);
            String title = "Editing " + surl;
            if (window instanceof Frame) {
                dialog = new DataSourceEditorDialog((Frame) window, edit.getPanel(), true);
            } else if (window instanceof Dialog) {  // TODO: Java 1.6 ModalityType.
                dialog = new DataSourceEditorDialog((Dialog) window, edit.getPanel(), true);
            } else {
                throw new RuntimeException("parent windowAncestor type is not supported.");
            }
            dialog.setVisible(true);
            dialog.setTitle(title);

            if (!dialog.isCancelled()) {
                dataSetSelector.setSelectedItem(edit.getUrl());
                keyModifiers = dialog.getModifiers();
                maybePlot(true);
            }

        } else {
            int carotpos = surl.indexOf("?");
            if (carotpos == -1) {
                carotpos = surl.length();
            } else {
                carotpos += 1;
            }
            surl = surl.substring(0, carotpos);
            showCompletions(surl, carotpos);
        }
    }

    private void showCompletions() {
        final String surl = (String) dataSetSelector.getEditor().getItem();
        int carotpos = ((JTextField) dataSetSelector.getEditor().getEditorComponent()).getCaretPosition();
        setMessage("busy: getting completions");
        showCompletions(surl, carotpos);

    }

    private void showCompletions(final String surl, final int carotpos) {
        URLSplit split = URLSplit.parse(surl, carotpos);
        if (split.carotPos > split.file.length() && DataSourceRegistry.getInstance().hasSourceByExt(DataSetURL.getExt(surl))) {
            showFactoryCompletions(URLSplit.format(split), split.formatCarotPos);

        } else {

            int firstSlashAfterHost = split.authority == null ? 0 : split.authority.length();
            if (split.carotPos <= firstSlashAfterHost) {
                showHostCompletions(URLSplit.format(split), split.formatCarotPos);
            } else {
                showFileSystemCompletions(URLSplit.format(split), split.formatCarotPos);
            }

        }

    }

    /**
     * create completions on hostnames based on cached resources.
     * @param surl
     * @param carotpos
     */
    private void showHostCompletions(final String surl, final int carotpos) {

        if (completionsRunnable != null) {
            completionsMonitor.cancel();
            completionsRunnable = null;
        }

        completionsMonitor = getMonitor();
        completionsMonitor.setLabel("getting completions");
        completionsRunnable = new Runnable() {

            public void run() {
                ProgressMonitor mon = getMonitor();

                List<CompletionResult> completions = null;

                URLSplit split = DataSetURL.parse(surl);
                String surlDir = split.path;

                final String labelPrefix = surlDir;

                try {
                    completions = DataSetURL.getHostCompletions(surl, carotpos, mon);
                } catch (IOException ex) {
                    setMessage(ex.toString());
                    JOptionPane.showMessageDialog(DataSetSelector.this, "<html>I/O Exception occurred:<br>" + ex.getLocalizedMessage() + "</html>", "I/O Exception", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                CompletionsList.CompletionListListener listener = new CompletionsList.CompletionListListener() {

                    public void itemSelected(CompletionResult s1) {
                        dataSetSelector.setSelectedItem(s1.completion);
                        if (s1.maybePlot) {
                            maybePlot(false);
                        }
                    }
                };

                completionsPopupMenu = CompletionsList.fillPopupNew(completions, labelPrefix, new JPopupMenu(), listener);

                setMessage("done getting completions");

                SwingUtilities.invokeLater(new Runnable() {

                    public void run() {
                        try {
                            int xpos2 = editor.getGraphics().getFontMetrics().stringWidth(labelPrefix);
                            BoundedRangeModel model = editor.getHorizontalVisibility();

                            int xpos = xpos2 - model.getValue();
                            xpos = Math.min(model.getExtent(), xpos);

                            completionsPopupMenu.show(dataSetSelector, xpos, dataSetSelector.getHeight());
                            completionsRunnable = null;
                        } catch (NullPointerException ex) {
                            ex.printStackTrace(); // TODO: look into this

                        }
                    }
                });

            }
        };

        new Thread(completionsRunnable, "completionsThread").start();
    }

    private void showFileSystemCompletions(final String surl, final int carotpos) {

        if (completionsRunnable != null) {
            completionsMonitor.cancel();
            completionsRunnable = null;
        }

        completionsMonitor = getMonitor();
        completionsMonitor.setLabel("getting completions");
        completionsRunnable = new Runnable() {

            public void run() {
                ProgressMonitor mon = getMonitor();

                List<CompletionResult> completions = null;

                final String labelPrefix = surl.substring(0, carotpos);

                try {
                    completions = DataSetURL.getFileSystemCompletions(surl, carotpos, mon);
                } catch (IOException ex) {
                    setMessage(ex.toString());
                    JOptionPane.showMessageDialog(DataSetSelector.this, "<html>I/O Exception occurred:<br>" + ex.getLocalizedMessage() + "</html>", "I/O Exception", JOptionPane.WARNING_MESSAGE);
                    return;
                } catch (URISyntaxException ex) {
                    setMessage(ex.toString());
                    JOptionPane.showMessageDialog(DataSetSelector.this, "<html>URI Syntax Exception occurred:<br>" + ex.getLocalizedMessage() + "</html>", "I/O Exception", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                CompletionsList.CompletionListListener listener = new CompletionsList.CompletionListListener() {

                    public void itemSelected(CompletionResult s1) {
                        dataSetSelector.setSelectedItem(s1.completion);
                        if (s1.maybePlot) {
                            maybePlot(false);
                        }
                    }
                };

                completionsPopupMenu = CompletionsList.fillPopupNew(completions, labelPrefix, new JPopupMenu(), listener);

                setMessage("done getting completions");

                SwingUtilities.invokeLater(new Runnable() {

                    public void run() {
                        try {

                            int xpos2 = editor.getGraphics().getFontMetrics().stringWidth(labelPrefix);
                            BoundedRangeModel model = editor.getHorizontalVisibility();

                            int xpos = xpos2 - model.getValue();
                            xpos = Math.min(model.getExtent(), xpos);

                            completionsPopupMenu.show(dataSetSelector, xpos, dataSetSelector.getHeight());
                            completionsRunnable = null;
                        } catch (NullPointerException ex) {
                            ex.printStackTrace(); // TODO: look into this

                        }
                    }
                });

            }
        };

        new Thread(completionsRunnable, "completionsThread").start();
    }

    private void showFactoryCompletions(final String surl, final int carotpos) {

        if (completionsRunnable != null) {
            System.err.println("cancel existing completion task");
            completionsMonitor.cancel();
            completionsRunnable = null;
        }

        completionsMonitor = getMonitor();
        completionsMonitor.setLabel("getting completions");
        completionsRunnable = new Runnable() {

            public void run() {

                List<DataSetURL.CompletionResult> completions2;
                try {
                    completions2 = DataSetURL.getFactoryCompletions(surl, carotpos, completionsMonitor);
                    setMessage("done getting completions");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    setMessage("" + ex.getClass().getName() + " " + ex.getMessage());
                    return;
                }

                int i = surl.indexOf('?');
                final String labelPrefix = (i == -1) ? "" : surl.substring(0, i + 1);

                CompletionsList.CompletionListListener listener = new CompletionsList.CompletionListListener() {

                    public void itemSelected(CompletionResult s1) {
                        dataSetSelector.setSelectedItem(s1.completion);
                        if (s1.maybePlot) {
                            maybePlot(false);
                        }
                    }
                };

                completionsPopupMenu = CompletionsList.fillPopupNew(completions2, labelPrefix, new JPopupMenu(), listener);

                SwingUtilities.invokeLater(new Runnable() {

                    public void run() {
                        try {
                            double xpos;
                            //int n = editor.getCaretPosition();
                            int n = Math.min(carotpos, editor.getText().length());
                            String t = editor.getText(0, n);
                            int xpos2 = editor.getGraphics().getFontMetrics().stringWidth(t);

                            BoundedRangeModel model = editor.getHorizontalVisibility();
                            xpos = xpos2 - model.getValue();
                            xpos = Math.min(model.getExtent(), xpos);
                            completionsPopupMenu.show(dataSetSelector, (int) xpos, dataSetSelector.getHeight());
                            completionsRunnable = null;
                        } catch (BadLocationException ex) {
                            Logger.getLogger(DataSetSelector.class.getName()).log(Level.SEVERE, null, ex);
                        }

                    }
                });

            }
        };

        new Thread(completionsRunnable, "completionsThread").start();
    }

    /**
     * THIS MUST BE CALLED AFTER THE COMPONENT IS ADDED.  
     * This is so ENTER works properly.
     */
    public void addCompletionKeys() {

        JComponent c = SwingUtilities.getRootPane(this);

        ActionMap map = dataSetSelector.getActionMap();
        map.put("complete", new AbstractAction("completionsPopup") {

            public void actionPerformed(ActionEvent ev) {
                showCompletions();
            }
        });

        map.put("plot", new AbstractAction("plotUrl") {

            public void actionPerformed(ActionEvent ev) {
                setValue(getEditor().getText());
                keyModifiers = ev.getModifiers();
                maybePlot(true);
            }
        });

        dataSetSelector.setActionMap(map);
        final JTextField tf = (JTextField) dataSetSelector.getEditor().getEditorComponent();
        tf.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                dataSetSelector.setSelectedItem(tf.getText());
                keyModifiers = e.getModifiers();
                maybePlot(true);
            }
        });

        InputMap imap = SwingUtilities.getUIInputMap(dataSetSelector, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_MASK), "complete");
        imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_MASK), "plot");
        imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_MASK), "plot");
        needToAddKeys = false;
    }
    private Action ABOUT_PLUGINS_ACTION = new AbstractAction("About Plugins") {

        public void actionPerformed(ActionEvent e) {
            String about = support.getPluginsText();

            JOptionPane.showMessageDialog(DataSetSelector.this, about);
        }
    };

    public void addAbouts() {
        final String regex = "about:(.*)";
        registerActionTrigger(regex, new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                String ss = DataSetSelector.this.getValue();
                Pattern p = Pattern.compile(regex);
                Matcher m = p.matcher(ss);
                if (!m.matches()) {
                    throw new IllegalArgumentException("huh?");
                }
                String arg = m.group(1);
                if (arg.equals("plugins")) {
                    ABOUT_PLUGINS_ACTION.actionPerformed(e);
                }
            }
        });
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        browseButton = new javax.swing.JButton();
        plotItButton = new javax.swing.JButton();
        dataSetSelector = new javax.swing.JComboBox();

        browseButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/virbo/datasource/file.png"))); // NOI18N
        browseButton.setToolTipText("show options popup");
        browseButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        browseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseButtonActionPerformed(evt);
            }
        });

        plotItButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/virbo/datasource/go.png"))); // NOI18N
        plotItButton.setToolTipText("plot this URL");
        plotItButton.setMaximumSize(new java.awt.Dimension(20, 20));
        plotItButton.setMinimumSize(new java.awt.Dimension(20, 20));
        plotItButton.setPreferredSize(new java.awt.Dimension(20, 20));
        plotItButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                plotItButtonActionPerformed(evt);
            }
        });

        dataSetSelector.setEditable(true);
        dataSetSelector.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "(application will put recent items here)" }));
        dataSetSelector.setToolTipText("enter data source URL");
        dataSetSelector.setMinimumSize(new java.awt.Dimension(20, 20));
        dataSetSelector.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                dataSetSelectorMouseClicked(evt);
            }
        });
        dataSetSelector.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent evt) {
                dataSetSelectorPopupMenuCanceled(evt);
            }
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent evt) {
                dataSetSelectorPopupMenuWillBecomeInvisible(evt);
            }
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent evt) {
            }
        });
        dataSetSelector.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                dataSetSelectorItemStateChanged(evt);
            }
        });
        dataSetSelector.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataSetSelectorActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(dataSetSelector, 0, 320, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(plotItButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 26, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(browseButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 20, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
        );

        layout.linkSize(new java.awt.Component[] {browseButton, plotItButton}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                        .add(plotItButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(browseButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 19, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(dataSetSelector, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        layout.linkSize(new java.awt.Component[] {browseButton, dataSetSelector, plotItButton}, org.jdesktop.layout.GroupLayout.VERTICAL);

        browseButton.getAccessibleContext().setAccessibleDescription("inspect contents of file or directory");
    }// </editor-fold>//GEN-END:initComponents
    private void dataSetSelectorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSetSelectorActionPerformed
        // this is not used because focus lost causes event fire.  Instead we listen to the JTextField.
    }//GEN-LAST:event_dataSetSelectorActionPerformed

    private void plotItButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_plotItButtonActionPerformed
        keyModifiers = evt.getModifiers();
        setValue(getEditor().getText());
        maybePlot(true);
    }//GEN-LAST:event_plotItButtonActionPerformed

    private void browseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browseButtonActionPerformed
        String context = (String) dataSetSelector.getSelectedItem();

        String ext = DataSetURL.getExt(context);
        if ( ( !context.contains("/?") && context.contains("?") ) || DataSourceRegistry.getInstance().dataSourcesByExt.get(ext) != null) {
            browseSourceType();

        } else {
            try {
                URLSplit split= URLSplit.parse(context);
                URL url= new URL( split.file );
                if (url.getProtocol().equals("file")) {
                    File f = new File(url.getPath());
                    if ( f.exists() && f.isFile() ) {
                        browseSourceType();
                    } else {
                        JFileChooser chooser = new JFileChooser(url.getPath());
                        int result = chooser.showOpenDialog(this);
                        if (result == JFileChooser.APPROVE_OPTION) {
                            String suri= DataSetURL.newUri( context,chooser.getSelectedFile().toString() );
                            dataSetSelector.setSelectedItem(suri);
                        }
                    }
                } else {
                    showCompletions();
                }
            } catch (MalformedURLException e) {
                DasExceptionHandler.handle(e);
            }
        }
    }//GEN-LAST:event_browseButtonActionPerformed

private void dataSetSelectorItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_dataSetSelectorItemStateChanged
    if (doItemStateChange && evt.getStateChange() == ItemEvent.SELECTED) {
        maybePlot(false);
    }
}//GEN-LAST:event_dataSetSelectorItemStateChanged
    private boolean popupCancelled = false;

private void dataSetSelectorPopupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent evt) {//GEN-FIRST:event_dataSetSelectorPopupMenuWillBecomeInvisible
    if (popupCancelled == false) {
        maybePlot(false);
    }
    popupCancelled = false;
}//GEN-LAST:event_dataSetSelectorPopupMenuWillBecomeInvisible

private void dataSetSelectorMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_dataSetSelectorMouseClicked
}//GEN-LAST:event_dataSetSelectorMouseClicked

private void dataSetSelectorPopupMenuCanceled(javax.swing.event.PopupMenuEvent evt) {//GEN-FIRST:event_dataSetSelectorPopupMenuCanceled
    System.err.println("popup cancelled");
    popupCancelled = true;
}//GEN-LAST:event_dataSetSelectorPopupMenuCanceled
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton browseButton;
    private javax.swing.JComboBox dataSetSelector;
    private javax.swing.JButton plotItButton;
    // End of variables declaration//GEN-END:variables

    /**
     * Getter for property value.
     * @return Value of property value.
     */
    public String getValue() {
        return ((String) this.dataSetSelector.getSelectedItem()).trim();
    }
    private boolean doItemStateChange = false;

    /**
     * Setter for property value.
     * @param value New value of property value.
     */
    public void setValue(String value) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        doItemStateChange = false;
        this.dataSetSelector.setSelectedItem(value);
        this.dataSetSelector.repaint();
    //doItemStateChange = true;
    }
    /**
     * Holds value of property browseTypeExt.
     */
    private String browseTypeExt;

    /**
     * Getter for property browseTypeExt.
     * @return Value of property browseTypeExt.
     */
    public String getBrowseTypeExt() {
        return this.browseTypeExt;
    }

    /**
     * Setter for property browseTypeExt.
     * @param browseTypeExt New value of property browseTypeExt.
     */
    public void setBrowseTypeExt(String browseTypeExt) {
        String oldBrowseTypeExt = this.browseTypeExt;
        this.browseTypeExt = browseTypeExt;
        firePropertyChange("browseTypeExt", oldBrowseTypeExt, browseTypeExt);
    }
    /**
     * Utility field holding list of ActionListeners.
     */
    private transient java.util.ArrayList actionListenerList;

    /**
     * Registers ActionListener to receive events.
     * @param listener The listener to register.
     */
    public synchronized void addActionListener(java.awt.event.ActionListener listener) {
        if (actionListenerList == null) {
            actionListenerList = new java.util.ArrayList();
        }
        actionListenerList.add(listener);
    }

    /**
     * Removes ActionListener from the list of listeners.
     * @param listener The listener to remove.
     */
    public synchronized void removeActionListener(java.awt.event.ActionListener listener) {
        if (actionListenerList != null) {
            actionListenerList.remove(listener);
        }
    }

    /**
     * Notifies all registered listeners about the event.
     *
     * @param event The event to be fired
     */
    private void fireActionListenerActionPerformed(java.awt.event.ActionEvent event) {
        java.util.ArrayList list;
        synchronized (this) {
            if (actionListenerList == null) {
                return;
            }
            list = (java.util.ArrayList) actionListenerList.clone();
        }
        for (int i = 0; i < list.size(); i++) {
            ((java.awt.event.ActionListener) list.get(i)).actionPerformed(event);
        }
    }
    /**
     * Holds value of property recent.
     */
    private List<String> recent;

    /**
     * Getter for property recent.
     * @return Value of property recent.
     */
    public List<String> getRecent() {
        if (this.recent == null) {
            recent = new ArrayList<String>();
        }
        return this.recent;
    }

    /**
     * Setter for property recent.
     * @param recent New value of property recent.
     */
    public void setRecent(List<String> recent) {
        List<String> oldRecent = this.recent;
        this.recent = recent;
        Object value = getValue();
        ArrayList<String> r = new ArrayList<String>(recent);
        Collections.reverse(r);
        dataSetSelector.setModel(new DefaultComboBoxModel(r.toArray()));
        if (recent.contains(value)) {
            //dataSetSelector.setSelectedItem(value); causes event to fire
        }
        support.refreshRecentFilesMenu();
        firePropertyChange("recent", oldRecent, recent);
    }
    /**
     * Holds value of property message.
     */
    private String message;

    /**
     * Getter for property message.
     * @return Value of property message.
     */
    public String getMessage() {
        return this.message;
    }

    /**
     * Setter for property message.
     * @param message New value of property message.
     */
    public void setMessage(String message) {
        String oldMessage = this.message;
        this.message = message;
        firePropertyChange(PROPERTY_MESSAGE, oldMessage, message);
    }
    HashMap<String, Action> actionTriggers = new LinkedHashMap<String, Action>();
    protected boolean plotItButtonVisible = true;
    public static final String PROP_PLOTITBUTTONVISIBLE = "plotItButtonVisible";

    public boolean isPlotItButtonVisible() {
        return plotItButtonVisible;
    }

    public void setPlotItButtonVisible(boolean plotItButtonVisible) {
        boolean oldPlotItButtonVisible = this.plotItButtonVisible;
        this.plotItButtonVisible = plotItButtonVisible;
        this.plotItButton.setVisible(plotItButtonVisible);
        firePropertyChange(PROP_PLOTITBUTTONVISIBLE, oldPlotItButtonVisible, plotItButtonVisible);
    }

    /**
     * This is how we allow .vap files to be in the datasetSelector.  We register
     * a pattern for which an action is invoked.
     */
    public void registerActionTrigger(String regex, Action action) {
        actionTriggers.put(regex, action);
    }

    public Action getOpenLocalAction() {
        return support.openLocalAction();
    }

    public JMenu getRecentMenu() {
        return support.recentMenu();
    }
}
