/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.scriptconsole;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import org.das2.util.monitor.NullProgressMonitor;
import org.virbo.autoplot.ApplicationModel;
import org.virbo.datasource.DataSetSelector;
import org.virbo.datasource.DataSetURL;
import org.das2.util.filesystem.WebFileSystem;
import org.python.util.PythonInterpreter;
import org.virbo.autoplot.JythonUtil;

/**
 *
 * @author jbf
 */
public class ScriptPanelSupport {

    File file;
    final ApplicationModel model;
    final DataSetSelector selector;
    final JythonScriptPanel panel;

    ScriptPanelSupport(final JythonScriptPanel panel, final ApplicationModel model, final DataSetSelector selector) {
        this.model = model;
        this.selector = selector;
        this.panel = panel;
        model.addPropertyChangeListener(new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(ApplicationModel.PROPERTY_DATASOURCE)) {
                    try {
                        String sfile = model.getDataSourceURL();
                        if (sfile==null ) return;
                        DataSetURL.URLSplit split = DataSetURL.parse(sfile);
                        if (!(split.file.endsWith(".py") || split.file.endsWith(".jy"))) {
                            return;
                        }
                        file = DataSetURL.getFile(DataSetURL.getURL(sfile), new NullProgressMonitor());
                        StringBuffer buf = new StringBuffer();
                        BufferedReader r = new BufferedReader(new FileReader(file));
                        String s = r.readLine();
                        while (s != null) {
                            buf.append(s + "\n");
                            s = r.readLine();
                        }
                        panel.getEditorPanel().setText(buf.toString());
                        panel.setContext(JythonScriptPanel.CONTEXT_DATA_SOURCE);
                        panel.fileNameLabel.setText(sfile.toString());
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

    protected void executeScript() {

        if (panel.getContext() == JythonScriptPanel.CONTEXT_DATA_SOURCE) {

            boolean updateSurl = false;
            try {
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
                    OutputStream out = new FileOutputStream(file);
                    String text = panel.getEditorPanel().getText();
                    out.write(text.getBytes());
                    out.close();

                    if (updateSurl) {
                        selector.setValue(file.toURI().toString());
                    }
                    selector.maybePlot();

                    panel.fileNameLabel.setText(file.toString());
                }
            } catch (IOException iOException) {
                model.getCanvas().getApplication().getExceptionHandler().handle(iOException);
            }
        } else if (panel.getContext() == JythonScriptPanel.CONTEXT_APPLICATION) {
            Runnable run = new Runnable() {

                public void run() {
                    try {
                        PythonInterpreter interp = JythonUtil.createInterpreter(true, false);
                        interp.exec(panel.getEditorPanel().getText());
                        
                    } catch (IOException ex) {
                        Logger.getLogger(ScriptPanelSupport.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            };
            new Thread(run).start();
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
                updateSurl = panel.getContext()==JythonScriptPanel.CONTEXT_DATA_SOURCE;
                OutputStream out = new FileOutputStream(file);
                String text = panel.getEditorPanel().getText();
                out.write(text.getBytes());
                out.close();
                if (updateSurl) {
                    model.setDataSourceURL(file.toString());
                } else {
                    model.update(true,true);
                }
                panel.fileNameLabel.setText(file.toString());
            }

        } catch (IOException iOException) {
            model.getCanvas().getApplication().getExceptionHandler().handle(iOException);
        }
    }

    protected void open() {
        try {
            String sfile = selector.getValue();
            DataSetURL.URLSplit split = DataSetURL.parse(sfile);
            if (!(split.file.endsWith(".py") || split.file.endsWith(".jy"))) {
                file = null;
            } else {
                file = DataSetURL.getFile(DataSetURL.getURL(sfile), new NullProgressMonitor());
            }


            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter( getFileFilter() );
            if (file != null) {
                chooser.setSelectedFile(file);
            }
            int r = chooser.showOpenDialog(panel);
            if (r == JFileChooser.APPROVE_OPTION) {
                file = chooser.getSelectedFile();

                BufferedReader read = new BufferedReader(new FileReader(file));
                StringBuffer buf = new StringBuffer();
                String s = read.readLine();
                while (s != null) {
                    buf.append(s).append("\n");
                    s = read.readLine();
                }
                        
                panel.getEditorPanel().setText(buf.toString());
                panel.fileNameLabel.setText(file.toString());
            }

        } catch (IOException ex) {
            model.getCanvas().getApplication().getExceptionHandler().handle(ex);
        }
    }
}

