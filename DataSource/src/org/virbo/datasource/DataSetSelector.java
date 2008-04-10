/*
 * DataSetSelector.java
 *
 * Created on November 5, 2007, 6:04 AM
 */
package org.virbo.datasource;

import edu.uiowa.physics.pw.das.DasApplication;
import edu.uiowa.physics.pw.das.util.DasExceptionHandler;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.filesystem.FileSystem;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

/**
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

    /**
     * if the dataset requires parameters that aren't provided, then
     * show completion list.  Otherwise, fire off event.
     */
    public void maybePlot() {
        String surl = getValue();
        if (surl.equals("")) {
            return;
        }

        for (String actionTriggerRegex : this.actionTriggers.keySet()) {
            if (Pattern.matches(actionTriggerRegex, surl)) {
                Action action = actionTriggers.get(actionTriggerRegex);
                action.actionPerformed(new ActionEvent(this, 123, "dataSetSelect"));
                return;
            }
        }

        try {
            if (surl.endsWith("/")) {
                int carotpos = surl.length();
                setMessage("ends with /, filesystem completions");
                showCompletions(surl, carotpos);
            } else if (surl.endsWith("/..")) { // pop up one directory

                int carotpos = surl.lastIndexOf("/..");
                carotpos = surl.lastIndexOf("/", carotpos - 1);
                if (carotpos != -1) {
                    dataSetSelector.getEditor().setItem(surl.substring(0, carotpos + 1));
                }
            } else {
                DataSourceFactory f = DataSetURL.getDataSourceFactory(DataSetURL.getURL(surl), new NullProgressMonitor());
                if (f.reject(surl)) {
                    if (!surl.contains("?")) {
                        surl += "?";
                    }
                    setValue(surl);
                    int carotpos = surl.indexOf("?") + 1;
                    setMessage("url ambiguous, getting inspecting resource for parameters");
                    showCompletions(surl, carotpos);
                } else {
                    ActionEvent e = new ActionEvent(this, 123, "dataSetSelect");
                    fireActionListenerActionPerformed(e);
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
     * show the initial parameters completions for the type, or the
     * editor, once that's introduced.
     */
    private void browseSourceType() {
        String surl = (String) dataSetSelector.getEditor().getItem();
        int carotpos = surl.indexOf("?");
        if (carotpos == -1) {
            carotpos = surl.length();
        } else {
            carotpos += 1;
        }
        surl = surl.substring(0, carotpos);
        showCompletions(surl, carotpos);
    }

    private JPopupMenu fillPopupNew(final List<String> completions, final String labelprefix) {

        JPopupMenu popupMenu = new JPopupMenu();

        JMenu subMenu = null;

        int i = 0;
        while (i < completions.size()) {
            int stopAt = Math.min(i + 30, completions.size());
            while (i < stopAt) {
                final String comp1 = completions.get(i);
                String label = completions.get(i);
                if (label.startsWith(labelprefix)) {
                    label = label.substring(labelprefix.length());
                }
                Action a = new AbstractAction(label) {

                    public void actionPerformed(ActionEvent ev) {
                        dataSetSelector.setSelectedItem(comp1);
                    }
                };
                if (subMenu == null) {
                    popupMenu.add(a);
                } else {
                    subMenu.add(a);
                }
                i++;
            }
            if (i < completions.size()) {
                JMenu nextSubMenu = new JMenu("more");
                if (subMenu == null) {
                    popupMenu.add(nextSubMenu);
                } else {
                    subMenu.add(nextSubMenu);
                }
                subMenu = nextSubMenu;
            }
        }
        if (completions.size() == 0) {
            popupMenu.add("<html><em>(empty)</em></html>");
        }
        return popupMenu;
    }

    private JPopupMenu fillPopup(final List<String> completions, final String labelprefix) {

        JPopupMenu popupMenu = new JPopupMenu();

        for (int i = 0; i < Math.min(30, completions.size()); i++) {
            final String comp1 = completions.get(i);
            String label = completions.get(i);
            if (label.startsWith(labelprefix)) {
                label = label.substring(labelprefix.length());
            }
            popupMenu.add(new AbstractAction(label) {

                public void actionPerformed(ActionEvent ev) {
                    dataSetSelector.setSelectedItem(comp1);
                }
            });
        }
        if (completions.size() > 20) {
            popupMenu.add("<html><em>(list truncated)</em></html>");
        }
        if (completions.size() == 0) {
            popupMenu.add("<html><em>(empty)</em></html>");
        }
        return popupMenu;
    }

    private void showCompletions() {
        final String surl = (String) dataSetSelector.getEditor().getItem();
        int carotpos = ((JTextField) dataSetSelector.getEditor().getEditorComponent()).getCaretPosition();
        setMessage("getting completions");
        showCompletions(surl, carotpos);
        setMessage("done getting completions");
    }

    private void showCompletions(final String surl, final int carotpos) {
        DataSetURL.URLSplit split = DataSetURL.parse(surl);
        if (surl.contains("?") || DataSourceRegistry.getInstance().dataSourcesByExt.containsKey(split.ext)) {
            showFactoryCompletions(surl, carotpos);

        } else {
            showFileSystemCompletions(surl, carotpos);

        }

    }

    private void showFileSystemCompletions(final String surl, final int carotpos) {

        if (completionsRunnable != null) {
            completionsMonitor.cancel();
            completionsRunnable = null;
        }

        completionsMonitor = DasApplication.getDefaultApplication().getMonitorFactory().getMonitor("getting completions", "getting completions by delegate");
        completionsRunnable = new Runnable() {

            public void run() {

                DataSetURL.URLSplit split = DataSetURL.parse(surl);
                String prefix = split.file.substring(split.path.length());
                String surlDir = split.path;

                ProgressMonitor mon = DasApplication.getDefaultApplication().getMonitorFactory().getMonitor("getting completions", "getting remote listing");

                FileSystem fs = null;
                String[] s;
                try {
                    fs = FileSystem.create(new URL(split.path));

                    s = fs.listDirectory("/");

                } catch (MalformedURLException ex) {
                    setMessage(ex.getMessage());
                    ex.printStackTrace();
                    return;

                } catch ( IOException ex ) {
                    setMessage(ex.getMessage());
                    ex.printStackTrace();
                    return;
                    
                }

                boolean foldCase = Boolean.TRUE.equals(fs.getProperty(fs.PROP_CASE_INSENSITIVE));
                if (foldCase) {
                    prefix = prefix.toLowerCase();
                }

                List<String> completions = new ArrayList<String>(s.length);
                for (int j = 0; j < s.length; j++) {
                    String scomp = foldCase ? s[j].toLowerCase() : s[j];
                    if (scomp.startsWith(prefix)) {
                        if (s[j].endsWith("contents.html")) {
                            s[j] = s[j].substring(0, s[j].length() - "contents.html".length());
                        } // kludge for dods

                        completions.add(surlDir + s[j]);
                    }
                }

                final String labelPrefix = surlDir;
                completionsPopupMenu = fillPopupNew(completions, labelPrefix);


                SwingUtilities.invokeLater(new Runnable() {

                    public void run() {
                        try {
                            int xpos2 = editor.getGraphics().getFontMetrics().stringWidth(labelPrefix);
                            BoundedRangeModel model = editor.getHorizontalVisibility();

                            final double xpos = xpos2 - model.getValue();

                            completionsPopupMenu.show(dataSetSelector, (int) xpos, dataSetSelector.getHeight());
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

        completionsMonitor = DasApplication.getDefaultApplication().getMonitorFactory().getMonitor("getting completions", "getting completions by delegate");
        completionsRunnable = new Runnable() {

            public void run() {

                String[] completions;
                try {
                    completions = DataSetURL.getCompletions2(surl, carotpos, completionsMonitor);
                    setMessage("");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    setMessage("" + ex.getClass().getName() + " " + ex.getMessage());
                    return;
                }

                int i = surl.indexOf('?');
                final String labelPrefix = (i == -1) ? "" : surl.substring(0, i + 1);

                completionsPopupMenu = fillPopupNew(Arrays.asList(completions), labelPrefix);

                SwingUtilities.invokeLater(new Runnable() {

                    public void run() {
                        double xpos;
                        int xpos2 = editor.getGraphics().getFontMetrics().stringWidth(labelPrefix);
                        BoundedRangeModel model = editor.getHorizontalVisibility();
                        xpos = xpos2 - model.getValue();
                        completionsPopupMenu.show(dataSetSelector, (int) xpos, dataSetSelector.getHeight());
                        completionsRunnable = null;

                    }
                });

            }
        };

        new Thread(completionsRunnable, "completionsThread").start();
    }

    /**
     * THIS MUST BE CALLED AFTER THE COMPONENT IS ADDED.  This is so ENTER
     * works properly
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
                maybePlot();
            }
        });

        dataSetSelector.setActionMap(map);
        final JTextField tf = (JTextField) dataSetSelector.getEditor().getEditorComponent();
        tf.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                dataSetSelector.setSelectedItem(tf.getText());
                maybePlot();
            }
        });

        InputMap imap = SwingUtilities.getUIInputMap(dataSetSelector, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_MASK), "complete");
        imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_MASK), "plot");

        needToAddKeys = false;
    }
    private Action ABOUT_PLUGINS_ACTION = new AbstractAction("About Plugins") {

        public void actionPerformed(ActionEvent e) {
            StringBuffer buf = new StringBuffer();
            buf.append("<html>");
            {
                buf.append("<h1>Plugins by Extension:</h1>");
                Map m = DataSourceRegistry.getInstance().dataSourcesByExt;
                for (Object k : m.keySet()) {
                    buf.append("" + k + ": " + m.get(k) + "<br>");
                }
            }
            {
                buf.append("<h1>Plugins by Mime Type:</h1>");
                Map m = DataSourceRegistry.getInstance().dataSourcesByMime;
                for (Object k : m.keySet()) {
                    buf.append("" + k + ": " + m.get(k) + "<br>");
                }
            }
            buf.append("</html>");

            JOptionPane.showMessageDialog(DataSetSelector.this, buf.toString());
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
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        browseButton = new javax.swing.JButton();
        plotItButton = new javax.swing.JButton();
        dataSetSelector = new javax.swing.JComboBox();

        browseButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/virbo/datasource/file.png")));
        browseButton.setToolTipText("browse for resource");
        browseButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        browseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseButtonActionPerformed(evt);
            }
        });

        plotItButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/virbo/datasource/go.png")));
        plotItButton.setToolTipText("plot this URL");
        plotItButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
        plotItButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                plotItButtonActionPerformed(evt);
            }
        });

        dataSetSelector.setEditable(true);
        dataSetSelector.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "http://cdaweb.gsfc.nasa.gov/cgi-bin/opendap/nph-dods/istp_public/data/genesis/3dl2_gim/2003/genesis_3dl2_gim_20030501_v01.cdf.dds?Proton_Density", "file://C:/iowaCitySales2004-2006.latlong.xls?column=M[1:]", "file://c:/Documents and Settings/jbf/My Documents/xx.d2s", "L:/fun/realEstate/to1960.latlon.xls?column=C[1:]&depend0=H[1:]", "L:/fun/realEstate/to1960.latlon.xls?column=M[1:]&depend0=N[1:]&plane0=C[1:]", "L:/ct/virbo/autoplot/data/610008002FE00410.20060901.das2Stream", "P:/poes/poes_n15_20060212.nc?proton-6_dome_16_MeV", "L:/ct/virbo/autoplot/data/asciiTab.dat", "L:/ct/virbo/autoplot/data/2490lintest90005.dat" }));
        dataSetSelector.setToolTipText("enter data source URL");
        dataSetSelector.setMinimumSize(new java.awt.Dimension(20, 20));
        dataSetSelector.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataSetSelectorActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(dataSetSelector, 0, 243, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(plotItButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 23, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(browseButton))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                .add(dataSetSelector, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(plotItButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 25, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(browseButton))
        );
    }// </editor-fold>//GEN-END:initComponents
    private void dataSetSelectorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSetSelectorActionPerformed
        // this is not used because focus lost causes event fire.  Instead we listen to the JTextField.
    }//GEN-LAST:event_dataSetSelectorActionPerformed

    private void plotItButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_plotItButtonActionPerformed
        maybePlot();
    }//GEN-LAST:event_plotItButtonActionPerformed

    private void browseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browseButtonActionPerformed
        String context = (String) dataSetSelector.getSelectedItem();

        DataSetURL.URLSplit split = DataSetURL.parse(context);


        if (context.contains("?") || DataSourceRegistry.getInstance().dataSourcesByExt.get(split.ext) != null) {
            browseSourceType();

        } else {
            try {
                URL url = Util.newURL(new URL("file://"), context);
                if (url.getProtocol().equals("file")) {
                    JFileChooser chooser = new JFileChooser(url.getPath());
                    int result = chooser.showOpenDialog(this);
                    if (result == JFileChooser.APPROVE_OPTION) {
                        dataSetSelector.setSelectedItem(chooser.getSelectedFile().toString());
                    }
                } else {
                    showCompletions();
                }
            } catch (MalformedURLException e) {
                DasExceptionHandler.handle(e);
            }
        }
    }//GEN-LAST:event_browseButtonActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton browseButton;
    private javax.swing.JComboBox dataSetSelector;
    private javax.swing.JButton plotItButton;
    // End of variables declaration//GEN-END:variables
    /**
     * Utility field used by bound properties.
     */
    private java.beans.PropertyChangeSupport propertyChangeSupport = new java.beans.PropertyChangeSupport(this);

    /**
     * Adds a PropertyChangeListener to the listener list.
     * @param l The listener to add.
     */
    public void addPropertyChangeListener(java.beans.PropertyChangeListener l) {
        propertyChangeSupport.addPropertyChangeListener(l);
    }

    /**
     * Removes a PropertyChangeListener from the listener list.
     * @param l The listener to remove.
     */
    public void removePropertyChangeListener(java.beans.PropertyChangeListener l) {
        propertyChangeSupport.removePropertyChangeListener(l);
    }

    /**
     * Getter for property value.
     * @return Value of property value.
     */
    public String getValue() {
        return (String) this.dataSetSelector.getSelectedItem();
    }

    /**
     * Setter for property value.
     * @param value New value of property value.
     */
    public void setValue(String value) {
        String oldValue = getValue();
        this.dataSetSelector.setSelectedItem(value);
        this.dataSetSelector.repaint();
        propertyChangeSupport.firePropertyChange("value", oldValue, value);
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
        propertyChangeSupport.firePropertyChange("browseTypeExt", oldBrowseTypeExt, browseTypeExt);
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
        return this.recent;
    }

    /**
     * Setter for property recent.
     * @param recent New value of property recent.
     */
    public void setRecent(List<String> recent) {
        List<String> oldRecent = this.recent;
        this.recent = recent;
        dataSetSelector.setModel(new DefaultComboBoxModel(recent.toArray()));
        support.refreshRecentFilesMenu();
        propertyChangeSupport.firePropertyChange("recent", oldRecent, recent);
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
        propertyChangeSupport.firePropertyChange(PROPERTY_MESSAGE, oldMessage, message);
    }
    HashMap<String, Action> actionTriggers = new LinkedHashMap<String, Action>();

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
