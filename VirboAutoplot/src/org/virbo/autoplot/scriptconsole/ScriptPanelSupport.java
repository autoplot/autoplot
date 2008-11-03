/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.scriptconsole;

import java.beans.ExceptionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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
import org.virbo.datasource.URLSplit;
import org.virbo.datasource.jython.JythonDataSourceFactory;

/**
 *
 * @author jbf
 */
public class ScriptPanelSupport {

    File file;
    final ApplicationModel model;
    final DataSetSelector selector;
    final JythonScriptPanel panel;
    final EditorAnnotationsSupport annotationsSupport;
    private String PREFERENCE_OPEN_FILE= "openFile";

    ScriptPanelSupport(final JythonScriptPanel panel, final ApplicationModel model, final DataSetSelector selector) {
        this.model = model;
        this.selector = selector;
        this.panel = panel;
        this.annotationsSupport = panel.getEditorPanel().getEditorAnnotationsSupport();

        model.addPropertyChangeListener(new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(ApplicationModel.PROPERTY_DATASOURCE)) {
                    URLSplit split;
                    try {
                        String sfile = model.getDataSourceURL();
                        if (sfile == null) {
                            return;
                        }
                        split= URLSplit.parse(sfile);
                        if (!(split.file.endsWith(".py") || split.file.endsWith(".jy"))) {
                            return;
                        }

                        if (panel.isDirty()) {
                            int result = JOptionPane.showConfirmDialog(panel, "save edits before loading " + file + "?", "Save work", JOptionPane.OK_CANCEL_OPTION);
                            if (result == JOptionPane.OK_CANCEL_OPTION) {
                                return;
                            }
                            saveAs();
                        }
                        file = DataSetURL.getFile(DataSetURL.getURL(sfile), new NullProgressMonitor());
                        loadFile(file);

                        panel.setContext(JythonScriptPanel.CONTEXT_DATA_SOURCE);
                        panel.setFilename(sfile.toString());
                    } catch ( NullPointerException ex ) {
                        throw ex;
                    } catch (IOException ex) {
                        Logger.getLogger(JythonScriptPanel.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }
            }
        });

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
            if (!(file.toString().endsWith(".jy") || file.toString().endsWith(".py"))) {
                file = new File(file.toString() + ".jy");
            }
        }
        return r;
    }

    private void save() throws FileNotFoundException, IOException {
        OutputStream out = new FileOutputStream(file);
        String text = panel.getEditorPanel().getText();
        out.write(text.getBytes());
        out.close();
        panel.setDirty(false);
    }

    private void loadFile(File file) throws IOException, FileNotFoundException {
        try {
            StringBuffer buf = new StringBuffer();
            BufferedReader r = new BufferedReader(new FileReader(file));
            String s = r.readLine();
            while (s != null) {
                buf.append(s + "\n");
                s = r.readLine();
            }
            Document d = panel.getEditorPanel().getDocument();
            d.remove(0, d.getLength());
            d.insertString(0, buf.toString(), null);
            panel.setFilename(file.toString());
            panel.setDirty(false);
        } catch (BadLocationException ex) {
            throw new RuntimeException(ex);
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

    private void annotateError(PyException ex) throws BadLocationException {
        if (ex instanceof PySyntaxError) {
            Logger.getLogger(ScriptPanelSupport.class.getName()).log(Level.SEVERE, null, ex);
            int lineno = ((PyInteger) ex.value.__getitem__(1).__getitem__(1)).getValue();
            int col = ((PyInteger) ex.value.__getitem__(1).__getitem__(2)).getValue();
            annotationsSupport.annotateLine(lineno, "error", ex.toString());
        } else {
            Logger.getLogger(ScriptPanelSupport.class.getName()).log(Level.SEVERE, null, ex);
            annotationsSupport.annotateLine(ex.traceback.tb_lineno, "error", ex.toString());
        }
    }

    protected void executeScript() {

        try {
            if (panel.getContext() == JythonScriptPanel.CONTEXT_DATA_SOURCE) {
                if (file != null) {
                    JythonDataSourceFactory factory = (JythonDataSourceFactory) DataSetURL.getDataSourceFactory(file.toURI(), new NullProgressMonitor());
                    if (factory != null) {
                        factory.addExeceptionListener(new ExceptionListener() {

                            public void exceptionThrown(Exception e) {
                                if (e instanceof PyException) {
                                    try {
                                        annotateError((PyException) e);
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
                    save();

                    if (updateSurl) {
                        selector.setValue(file.toURI().toString());
                    }

                    annotationsSupport.clearAnnotations();
                    selector.maybePlot();

                    panel.setFilename(file.toString());
                }

            } else if (panel.getContext() == JythonScriptPanel.CONTEXT_APPLICATION) {
                Runnable run = new Runnable() {

                    public void run() {
                        try {
                            if (file != null) {
                                save();
                            }
                            try {
                                PythonInterpreter interp = JythonUtil.createInterpreter(true, false);
                                boolean dirty0 = panel.isDirty();
                                annotationsSupport.clearAnnotations();
                                panel.setDirty(dirty0);
                                interp.exec(panel.getEditorPanel().getText());
                            } catch (IOException ex) {
                                Logger.getLogger(ScriptPanelSupport.class.getName()).log(Level.SEVERE, null, ex);

                            } catch (PyException ex) {
                                annotateError(ex);
                                ex.printStackTrace();
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
            model.getCanvas().getApplication().getExceptionHandler().handle(iOException);
        }

    }

    private FileFilter getFileFilter() {
        return new FileFilter() {

            @Override
            public boolean accept(File f) {
                return (f.isDirectory() || f.toString().endsWith(".jy") || f.toString().endsWith(".py"));
            }

            @Override
            public String getDescription() {
                return "python and jython scripts";
            }
        };
    }

    protected void saveAs() {
        try {
            boolean updateSurl = false;
            if (getSaveFile() == JFileChooser.APPROVE_OPTION) {
                updateSurl = panel.getContext() == JythonScriptPanel.CONTEXT_DATA_SOURCE;
                OutputStream out = new FileOutputStream(file);
                String text = panel.getEditorPanel().getText();
                out.write(text.getBytes());
                out.close();
                panel.setDirty(false);
                panel.setFilename(file.toString());
                if (updateSurl) {
                    model.setDataSourceURL(file.toString());
                } else {
                    model.update(true, true);
                }

            }

        } catch (IOException iOException) {
            model.getCanvas().getApplication().getExceptionHandler().handle(iOException);
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

            Preferences prefs= Preferences.userNodeForPackage(ScriptPanelSupport.class);
            String openFile= prefs.get( PREFERENCE_OPEN_FILE, "" );
            
            JFileChooser chooser = new JFileChooser();
            if ( openFile.length()>0 ) {
                chooser.setSelectedFile( new File(openFile) );
            }
            chooser.setFileFilter(getFileFilter());
            if (file != null) {
                chooser.setSelectedFile(file);
            }
            int r = chooser.showOpenDialog(panel);
            if (r == JFileChooser.APPROVE_OPTION) {
                file = chooser.getSelectedFile();
                prefs.put( PREFERENCE_OPEN_FILE, file.toString() );
                loadFile(file);
                panel.setFilename(file.toString());
            }

        } catch (IOException ex) {
            model.getCanvas().getApplication().getExceptionHandler().handle(ex);
        }
    }
}

