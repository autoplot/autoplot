/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.scriptconsole;

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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.das2.util.monitor.NullProgressMonitor;
import org.virbo.autoplot.ApplicationModel;
import org.virbo.datasource.DataSetSelector;
import org.virbo.datasource.DataSetURL;
import org.das2.util.filesystem.WebFileSystem;
import org.python.core.PyException;
import org.python.core.PyInteger;
import org.python.core.PySyntaxError;
import org.python.util.PythonInterpreter;
import org.virbo.autoplot.JythonUtil;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.datasource.URLSplit;
import org.virbo.datasource.jython.JythonDataSourceFactory;

/**
 *
 * @author jbf
 */
public class ScriptPanelSupport {

    File file;
    final ApplicationModel model;
    final ApplicationController applicationController;
    final DataSetSelector selector;
    final JythonScriptPanel panel;
    final EditorAnnotationsSupport annotationsSupport;
    private String PREFERENCE_OPEN_FILE = "openFile";

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
        URLSplit split;
        try {
            String sfile = applicationController.getFocusUri();
            if (sfile == null) {
                return false;
            }
            split = URLSplit.parse(sfile);
            if (!(split.vapScheme.endsWith("jyds") || split.file.endsWith(".jyds"))) {
                return false;
            }
            if (panel.isDirty()) {
                int result = JOptionPane.showConfirmDialog(panel,
                        "save edits before loading\n" + sfile + ",\nor cancel script loading?", "loading new script", JOptionPane.OK_CANCEL_OPTION);
                if (result == JOptionPane.OK_CANCEL_OPTION) {
                    return false;
                }
                saveAs();
            }
            file = DataSetURL.getFile(DataSetURL.getURL(sfile), new NullProgressMonitor());
            loadFile(file);
            panel.setContext(JythonScriptPanel.CONTEXT_DATA_SOURCE);
            panel.setFilename(sfile);
        } catch (NullPointerException ex) {
            Logger.getLogger(JythonScriptPanel.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        } catch (IOException ex) {
            Logger.getLogger(JythonScriptPanel.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }

        return true;
    }

    public int getSaveFile() throws IOException {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(getFileFilter());
        if (file != null && !file.getCanonicalPath().startsWith(WebFileSystem.getDownloadDirectory().toString())) {
            chooser.setSelectedFile(file);
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
            out = new FileOutputStream(file);
            String text = panel.getEditorPanel().getText();
            out.write(text.getBytes());
            panel.setDirty(false);
        } finally {
            out.close();
        }
    }

    protected void loadFile(File file) throws IOException, FileNotFoundException {
        InputStream r= new FileInputStream(file);
        loadInputStream( r );
        if (file.toString().endsWith(".jyds")) {
            panel.setContext(JythonScriptPanel.CONTEXT_DATA_SOURCE);
        } else {
            panel.setContext(JythonScriptPanel.CONTEXT_APPLICATION);
        }
    }

    protected void loadInputStream( InputStream in ) throws IOException {
        BufferedReader r = null;
        try {
            StringBuffer buf = new StringBuffer();
            r = new BufferedReader(new InputStreamReader(in));
            String s = r.readLine();
            while (s != null) {
                buf.append(s + "\n");
                s = r.readLine();
            }
            Document d = panel.getEditorPanel().getDocument();
            d.remove(0, d.getLength());
            d.insertString(0, buf.toString(), null);
            panel.setDirty(false);
        } catch (BadLocationException ex) {
            throw new RuntimeException(ex);
        } finally {
            if (r != null) r.close();
        }
    }

    private boolean uriFilesEqual(String surl1, String surl2) throws URISyntaxException {
        int i1 = surl1.indexOf("?");
        if (i1 == -1) {
            i1 = surl1.length();
        }
        URI uri1 = DataSetURL.getURI(surl1.substring(0, i1));
        int i2 = surl2.indexOf("?");
        if (i2 == -1) {
            i2 = surl2.length();
        }
        URI uri2 = DataSetURL.getURI(surl2.substring(0, i2));
        return uri1.equals(uri2);
    }

    /**
     *
     * @param ex
     * @param offset line offset from beginning of file where execution began.
     * @throws javax.swing.text.BadLocationException
     */
    private void annotateError(PyException ex, int offset) throws BadLocationException {
        if (ex instanceof PySyntaxError) {
            Logger.getLogger(ScriptPanelSupport.class.getName()).log(Level.SEVERE, null, ex);
            int lineno = offset + ((PyInteger) ex.value.__getitem__(1).__getitem__(1)).getValue();
            //int col = ((PyInteger) ex.value.__getitem__(1).__getitem__(2)).getValue();
            annotationsSupport.annotateLine(lineno, "error", ex.toString());
        } else {
            Logger.getLogger(ScriptPanelSupport.class.getName()).log(Level.SEVERE, null, ex);
            annotationsSupport.annotateLine(offset + ex.traceback.tb_lineno, "error", ex.toString());
        }
    }

    protected void executeScript() {
        executeScript(false);
    }

    protected void executeScript(final boolean trace) {

        try {
            if (panel.getContext() == JythonScriptPanel.CONTEXT_DATA_SOURCE) {
                if (file != null) {
                    URI uri;
                    try {
                        uri = new URI("vap+jyds:" + file.toURI().toString());
                    } catch (URISyntaxException ex) {
                        throw new RuntimeException(ex);
                    }
                    JythonDataSourceFactory factory = (JythonDataSourceFactory) DataSetURL.getDataSourceFactory( uri, new NullProgressMonitor());
                    if (factory != null) {
                        factory.addExeceptionListener(new ExceptionListener() {

                            public void exceptionThrown(Exception e) {
                                if (e instanceof PyException) {
                                    try {
                                        annotateError((PyException) e, 0);
                                    } catch (BadLocationException ex) {
                                        Logger.getLogger(ScriptPanelSupport.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                }
                            }
                        });
                    }
                }

                boolean updateSurl = false;
                if (file == null || file.getCanonicalPath().startsWith(WebFileSystem.getDownloadDirectory().toString())) {
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

                    if (panel.isDirty()) {
                        save();
                    }

                    if (updateSurl) {
                        selector.setValue(file.toURI().toString());
                    }

                    annotationsSupport.clearAnnotations();
                    selector.maybePlot(false);

                    panel.setFilename(file.toString());
                }

            } else if (panel.getContext() == JythonScriptPanel.CONTEXT_APPLICATION) {
                applicationController.setStatus("busy: executing application script");
                Runnable run = new Runnable() {

                    public void run() {
                        int offset = 0;
                        try {
                            if (file != null) {
                                save();
                            }
                            try {
                                PythonInterpreter interp = JythonUtil.createInterpreter(true, false);
                                interp.set("dom", model.getDocumentModel() );
                                boolean dirty0 = panel.isDirty();
                                annotationsSupport.clearAnnotations();
                                panel.setDirty(dirty0);
                                if (trace) {
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
                                            interp.exec(s);
                                        } catch (PyException ex) {
                                            throw ex;
                                        }
                                        i0 = i1;
                                        offset += 1;
                                        System.err.println(s);
                                    }
                                } else {
                                    interp.exec(panel.getEditorPanel().getText());
                                }
                                applicationController.setStatus("done executing script");
                            } catch (IOException ex) {
                                Logger.getLogger(ScriptPanelSupport.class.getName()).log(Level.SEVERE, null, ex);
                                applicationController.setStatus("error: I/O exception: " + ex.toString());
                            } catch (PyException ex) {
                                annotateError(ex, offset);
                                ex.printStackTrace();
                                applicationController.setStatus("error: " + ex.toString());
                            }
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        } catch (BadLocationException ex2) {
                            throw new RuntimeException(ex2);
                        }

                    }
                };
                new Thread(run).start();
            }

        } catch (IOException iOException) {
            model.getExceptionHandler().handle(iOException);
        }

    }

    private FileFilter getFileFilter() {
        return new FileFilter() {

            @Override
            public boolean accept(File f) {
                return (f.isDirectory() || f.toString().endsWith(".jy") || f.toString().endsWith(".py") || f.toString().endsWith(".jyds"));
            }

            @Override
            public String getDescription() {
                return "python and jython scripts";
            }
        };
    }

    protected void saveAs() {
        OutputStream out = null;
        try {
            boolean updateSurl = false;
            if (getSaveFile() == JFileChooser.APPROVE_OPTION) {
                updateSurl = panel.getContext() == JythonScriptPanel.CONTEXT_DATA_SOURCE;
                out = new FileOutputStream(file);
                String text = panel.getEditorPanel().getText();
                out.write(text.getBytes());
                panel.setDirty(false);
                panel.setFilename(file.toString());
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
                Logger.getLogger(ScriptPanelSupport.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    protected void open() {
        try {
            if (this.file == null) {
                String sfile = selector.getValue();
                URLSplit split = null;
                if (sfile != null) {
                    split = URLSplit.parse(sfile);
                }
                if (split == null || !(split.file.endsWith(".py") || split.file.endsWith(".jy"))) {
                    file = null;
                } else {
                    file = DataSetURL.getFile(DataSetURL.getURL(sfile), new NullProgressMonitor());
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
}

