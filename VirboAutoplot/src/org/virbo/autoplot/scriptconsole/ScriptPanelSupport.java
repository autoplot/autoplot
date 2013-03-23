/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.scriptconsole;

import java.beans.PropertyChangeSupport;
import org.virbo.jythonsupport.ui.EditorAnnotationsSupport;
import java.awt.HeadlessException;
import java.beans.ExceptionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.das2.components.DasProgressPanel;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.NullProgressMonitor;
import org.virbo.autoplot.ApplicationModel;
import org.virbo.datasource.DataSetSelector;
import org.virbo.datasource.DataSetURI;
import org.das2.util.filesystem.WebFileSystem;
import org.das2.util.monitor.ProgressMonitor;
import org.python.core.Py;
import org.python.core.PyException;
import org.python.core.PyInteger;
import org.python.core.PySyntaxError;
import org.python.core.ThreadState;
import org.python.util.InteractiveInterpreter;
import org.python.util.PythonInterpreter;
import org.virbo.autoplot.JythonUtil;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.datasource.FileSystemUtil;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.jython.JythonDataSourceFactory;

/**
 *
 * @author jbf
 */
public class ScriptPanelSupport {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot");

    File file;
    final ApplicationModel model;
    final ApplicationController applicationController;
    final DataSetSelector selector;
    final JythonScriptPanel panel;
    final EditorAnnotationsSupport annotationsSupport;
    private String PREFERENCE_OPEN_FILE = "openFile";
    private InteractiveInterpreter interruptible;
    ThreadState ts;

    ScriptPanelSupport(final JythonScriptPanel panel, final ApplicationModel model, final DataSetSelector selector) {
        this.model = model;
        this.applicationController = model.getDocumentModel().getController();
        this.selector = selector;
        this.panel = panel;
        this.annotationsSupport = panel.getEditorPanel().getEditorAnnotationsSupport();

        applicationController.addPropertyChangeListener(ApplicationController.PROP_FOCUSURI, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                maybeDisplayDataSourceScript();
            }
        });

    }

    /**
     * @return true if the source was displayed.
     * @throws java.awt.HeadlessException
     * @throws java.lang.NullPointerException
     */
    private boolean maybeDisplayDataSourceScript() throws HeadlessException, NullPointerException {
        URISplit split;
        try {
            String sfile = applicationController.getFocusUri();
            if (sfile == null) {
                return false;
            }
            split = URISplit.parse(sfile);
            if (!( URISplit.implicitVapScheme(split).endsWith("jyds") ) ) {
                return false;
            }
            if (panel.isDirty()) {
                int result = JOptionPane.showConfirmDialog(panel,
                        "save edits before loading\n" + sfile + ",\nor cancel script loading?", "loading new script", JOptionPane.OK_CANCEL_OPTION);
                if (result == JOptionPane.OK_CANCEL_OPTION) {
                    return false;
                }
                if ( saveAs()==JOptionPane.CANCEL_OPTION ) {
                    return false;
                }
            }
            if ( split.params!=null ) {
                Map<String,String> params= URISplit.parseParams(split.params);
                if ( params.containsKey("script") ) {
                    sfile= params.get("script");
                }
            }
            file = DataSetURI.getFile(DataSetURI.getURL(sfile), new NullProgressMonitor());
            loadFile(file);
            panel.setContext(JythonScriptPanel.CONTEXT_DATA_SOURCE);
            panel.setFilename(file.toString());
        } catch (NullPointerException ex) {
            logger.log(Level.SEVERE, null, ex);
            return false;
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
            return false;
        }

        return true;
    }

    public int getSaveFile() throws IOException {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(getFileFilter());
        if (file != null && ! FileSystemUtil.isChildOf( FileSystem.settings().getLocalCacheDir(), file ) ) {
            chooser.setSelectedFile(file);
        }
        if ( file==null ) {
            Preferences prefs = Preferences.userNodeForPackage(ScriptPanelSupport.class);
            String openFile = prefs.get(PREFERENCE_OPEN_FILE, "");
            if ( !openFile.equals("") ) {
                chooser.setCurrentDirectory( new File(openFile).getParentFile() );
            }
        }
        int r = chooser.showSaveDialog(panel);
        if (r == JFileChooser.APPROVE_OPTION) {
            file = chooser.getSelectedFile();
            if (!(file.toString().endsWith(".jy") || file.toString().endsWith(".py") || file.toString().endsWith(".jyds"))) {
                if (panel.getContext() == JythonScriptPanel.CONTEXT_DATA_SOURCE) {
                    file = new File(file.toString() + ".jyds");
                } else {
                    file = new File(file.toString() + ".jy");
                }
            }
        }
        return r;
    }

    protected void save() throws FileNotFoundException, IOException {
        if ( file==null ) {
            saveAs();
            return;
        }
        OutputStream out = null;
        try {
            if ( !( file.exists() && file.canWrite() || file.getParentFile().canWrite() ) ) throw new IOException("unable to write to file: "+file);
            out = new FileOutputStream(file);
            String text = panel.getEditorPanel().getText();
            out.write(text.getBytes());
            panel.setDirty(false);
        } finally {
            if ( out!=null ) out.close();
        }
    }

    protected void loadFile(File file) throws IOException, FileNotFoundException {
        InputStream r= null;
        try {
            r= new FileInputStream(file);
            this.file= file;
            panel.setFilename( file.toString() );
            loadInputStream( r );
            if (file.toString().endsWith(".jyds")) {
                panel.setContext(JythonScriptPanel.CONTEXT_DATA_SOURCE);
            } else {
                panel.setContext(JythonScriptPanel.CONTEXT_APPLICATION);
            }
        } finally {
            if ( r!=null ) r.close();
        }
    }

    protected void loadInputStream( InputStream in ) throws IOException {
        BufferedReader r = null;
        try {
            StringBuilder buf = new StringBuilder();
            r = new BufferedReader(new InputStreamReader(in));
            String s = r.readLine();
            while (s != null) {
                buf.append(s).append("\n");
                s = r.readLine();
            }
            final String fs= buf.toString();
            Runnable run= new Runnable() {
                public void run() {
                    try {
                        Document d = panel.getEditorPanel().getDocument();
                        d.remove(0, d.getLength());
                        d.insertString(0, fs, null);
                        panel.setDirty(false);
                    } catch ( BadLocationException ex ) {
                        
                    }
                }
            };
            SwingUtilities.invokeLater(run);;
        } finally {
            if (r != null) r.close();
        }
    }

    private boolean uriFilesEqual(String surl1, String surl2) throws URISyntaxException {
        int i1 = surl1.indexOf("?");
        if (i1 == -1) {
            i1 = surl1.length();
        }
        URI uri1 = DataSetURI.getURI(surl1.substring(0, i1));
        int i2 = surl2.indexOf("?");
        if (i2 == -1) {
            i2 = surl2.length();
        }
        URI uri2 = DataSetURI.getURI(surl2.substring(0, i2));
        if ( uri1==null ) return false;
        return uri1.equals(uri2);
    }

    /**
     *
     * @param ex
     * @param offset line offset from beginning of file where execution began.
     * @throws javax.swing.text.BadLocationException
     */
    private void annotateError(PyException ex, int offset, final PythonInterpreter interp) {
        if (ex instanceof PySyntaxError) {
            logger.log(Level.SEVERE, null, ex);
            int lineno = offset + ((PyInteger) ex.value.__getitem__(1).__getitem__(1)).getValue();
            //int col = ((PyInteger) ex.value.__getitem__(1).__getitem__(2)).getValue();
            annotationsSupport.annotateLine(lineno, "error", ex.toString(),interp);
        } else {
            logger.log(Level.SEVERE, null, ex);
            annotationsSupport.annotateLine(offset + ex.traceback.tb_lineno, "error", ex.toString(),interp);
        }
    }

    protected void executeScript() {
        executeScript(0);
    }

    /**
     * allow execute with trace.  This never worked effectively.
     * @param trace
     */
    protected void executeScript(final boolean trace) {
        executeScript( trace ? java.awt.Event.CTRL_MASK : 0 );
    }

    /**
     * Execute the script.  For context data source, this means putting the URI in the data set selector and telling it to plot.
     * @param mode =0 normal.  =2=CTRL_MASK= trace.  ALT_MASK is enter editor.
     */
    protected void executeScript(final int mode) {

        try {
            if (panel.getContext() == JythonScriptPanel.CONTEXT_DATA_SOURCE) {
                if (file != null) {
                    URI uri;
                    try {
                        uri = new URI("vap+jyds:" + file.toURI().toString()); // bug 3055130 okay
                        JythonDataSourceFactory factory = (JythonDataSourceFactory) DataSetURI.getDataSourceFactory( uri, new NullProgressMonitor());
                        if (factory != null) {
                            factory.addExeceptionListener(new ExceptionListener() {

                                public void exceptionThrown(Exception e) {
                                    if (e instanceof PyException) {
                                        annotateError((PyException) e, 0, null );
                                    }
                                }
                            });
                        }

                    } catch (URISyntaxException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                boolean updateSurl = false;
                if (file == null || FileSystemUtil.isChildOf( FileSystem.settings().getLocalCacheDir(), file ) ) {
                    if (getSaveFile() == JFileChooser.APPROVE_OPTION) {
                        updateSurl = true;
                    } else {
                        return;
                    }
                }

                if (file != null) {
                    try {
                        if (!uriFilesEqual(selector.getValue(), file.toURI().toString())) {
                            updateSurl = true;
                        }
                    } catch (URISyntaxException ex) {
                        updateSurl = true;
                    }

                    if (panel.isDirty() && ( file.exists() && file.canWrite() || file.getParentFile().canWrite() ) ) {
                        save();
                    }

                    if (updateSurl) {
                        selector.setValue("vap+jyds:"+file.toURI().toString());
                    }

                    annotationsSupport.clearAnnotations();
                    selector.maybePlot(mode);

                    if ( updateSurl ) {
                        panel.setFilename(file.toString());
                    }
                }

            } else if (panel.getContext() == JythonScriptPanel.CONTEXT_APPLICATION) {
                applicationController.setStatus("busy: executing application script");
                Runnable run = new Runnable() {

                    public void run() {
                        int offset = 0;
                        //ProgressMonitor mon= //DasProgressPanel.createComponentPanel(model.getCanvas(),"running script");
                        ProgressMonitor mon= DasProgressPanel.createFramed( SwingUtilities.getWindowAncestor(panel), "running script");
                        
                        try {
                            if (file != null && ( file.exists() && file.canWrite() || file.getParentFile().canWrite() ) ) {
                                save();
                                applicationController.getApplicationModel().addRecent("script:"+file.toURI().toString());
                            }
                            InteractiveInterpreter interp = null;
                            try {
                                interp= JythonUtil.createInterpreter(true, false);
                                interp.set("dom", model.getDocumentModel() );
                                interp.set("monitor", mon );
                                setInterruptible( interp );
                                ts= Py.getThreadState();
                                boolean dirty0 = panel.isDirty();
                                annotationsSupport.clearAnnotations();
                                panel.setDirty(dirty0);
                                if ( mode==2 ) { // trace
                                    String text = panel.getEditorPanel().getText();
                                    int i0 = 0;
                                    while (i0 < text.length()) {
                                        int i1 = text.indexOf("\n", i0);
                                        while (i1 < text.length() - 1 && Character.isWhitespace(text.charAt(i1 + 1))) {
                                            i1 = text.indexOf("\n", i1 + 1);
                                        }
                                        String s;
                                        if (i1 != -1) {
                                            i1 = i1 + 1;
                                            s = text.substring(i0, i1);
                                        } else {
                                            s = text.substring(i0);
                                        }
                                        try {
                                            annotationsSupport.clearAnnotations();
                                            annotationsSupport.annotateChars( i0, i1, "programCounter", "pc", interp );
                                            interp.exec(s);
                                        } catch (PyException ex) {
                                            throw ex;
                                        }
                                        i0 = i1;
                                        offset += 1;
                                        System.err.println(s);
                                    }
                                    annotationsSupport.clearAnnotations();
                                } else {
                                    interp.exec(panel.getEditorPanel().getText());
                                }
                                setInterruptible( null );
                                mon.finished();
                                applicationController.setStatus("done executing script");
                            } catch (IOException ex) {
                                mon.finished();
                                logger.log(Level.SEVERE, null, ex);
                                applicationController.setStatus("error: I/O exception: " + ex.toString());
                            } catch (PyException ex) {
                                mon.finished();
                                annotateError(ex, offset, interp );
                                ex.printStackTrace();
                                applicationController.setStatus("error: " + ex.toString());
                            }
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        } catch ( Error ex ) {
                            if ( !ex.getMessage().contains("Python interrupt") ) {
                                throw ex;
                            } else {
                                applicationController.setStatus("script interrupted");
                            }
                        } finally {
                            mon.finished();
                            setInterruptible( null );
                        }

                    }
                };
                new Thread(run,"sessionRunScriptThread").start();
            }

        } catch (IOException iOException) {
            model.getExceptionHandler().handle(iOException);
        }

    }

    private FileFilter getFileFilter() {
        return new FileFilter() {

            @Override
            public boolean accept(File f) {
                if ( f.toString()==null ) return false;
                return (f.isDirectory() || f.toString().endsWith(".jy") || f.toString().endsWith(".py") || f.toString().endsWith(".jyds"));
            }

            @Override
            public String getDescription() {
                return "python and jython scripts";
            }
        };
    }

    /**
     * returns JFileChooser.APPROVE_OPTION or JFileChooser.CANCEL_OPTION
     * @return
     */
    protected int saveAs() {
        OutputStream out = null;
        int result= JFileChooser.CANCEL_OPTION;
        try {
            boolean updateSurl = false;
            result= getSaveFile();
            if (result == JFileChooser.APPROVE_OPTION) {
                updateSurl = panel.getContext() == JythonScriptPanel.CONTEXT_DATA_SOURCE;
                out = new FileOutputStream(file);
                String text = panel.getEditorPanel().getText();
                out.write(text.getBytes());
                panel.setDirty(false);
                panel.setFilename(file.toString());

                Preferences prefs = Preferences.userNodeForPackage(ScriptPanelSupport.class);
                prefs.put(PREFERENCE_OPEN_FILE, file.toString() );

                //if (updateSurl) {
                //    model.setDataSourceURL(file.toString());
                //    model.getDataSourceFilterController().update(true, true);
                //}

            }

        } catch (IOException iOException) {
            model.getExceptionHandler().handle(iOException);
            
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
        return result;
    }

    protected void newScript() {
        if (panel.isDirty()) {
           int result = JOptionPane.showConfirmDialog(panel,
                "save edits first?", "new script", JOptionPane.YES_NO_CANCEL_OPTION );
                if (result == JOptionPane.OK_CANCEL_OPTION) {
                    return;
                }
                if ( result==JOptionPane.OK_OPTION ) {
                    if ( saveAs()==JOptionPane.CANCEL_OPTION ) return;
                }
            }
        try {
            Document d = panel.getEditorPanel().getDocument();
            d.remove(0, d.getLength());
            panel.setDirty(false);
            panel.setFilename(null);
            this.file= null;
        } catch (BadLocationException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    protected void open() {
        try {
            if (this.file == null) {
                String sfile = selector.getValue();
                URISplit split = null;
                if (sfile != null) {
                    split = URISplit.parse(sfile);
                }
                if (split == null || !( split.file!=null && ( split.file.endsWith(".py") || split.file.endsWith(".jy") ) ) ) {
                    file = null;
                } else {
                    file = DataSetURI.getFile(DataSetURI.getURL(sfile), new NullProgressMonitor());
                }
            }

            Preferences prefs = Preferences.userNodeForPackage(ScriptPanelSupport.class);
            String openFile = prefs.get(PREFERENCE_OPEN_FILE, "");

            JFileChooser chooser = new JFileChooser();
            if (openFile.length() > 0) {
                chooser.setSelectedFile(new File(openFile));
            }
            chooser.setFileFilter(getFileFilter());
            if (file != null) {
                chooser.setSelectedFile(file);
            }
            int r = chooser.showOpenDialog(panel);
            if (r == JFileChooser.APPROVE_OPTION) {
                file = chooser.getSelectedFile();
                prefs.put(PREFERENCE_OPEN_FILE, file.toString());
                loadFile(file);
                panel.setFilename(file.toString());
            }

        } catch (IOException ex) {
            model.getExceptionHandler().handle(ex);
        }
    }

    void interrupt() {
        if ( getInterruptible()!=null ) {
            getInterruptible().interrupt( ts );
        }
    }

    public static final String PROP_INTERRUPTABLE= "interruptable";
    /**
     * @return the interruptible
     */
    public InteractiveInterpreter getInterruptible() {
        return interruptible;
    }

    private void setInterruptible( InteractiveInterpreter interruptable ) {
        InteractiveInterpreter old= this.interruptible;
        this.interruptible= interruptable;
        propertyChangeSupport.firePropertyChange( PROP_INTERRUPTABLE, old, interruptable );
    }

    private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    public void addPropertyChangeListener(String name, PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(name, listener);
    }

    public void removePropertyChangeListener(String name,PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(name,listener);
    }

}

