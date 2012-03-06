/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.jythonsupport.ui;

import java.beans.PropertyChangeSupport;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import org.das2.util.DasExceptionHandler;
import org.das2.util.filesystem.FileSystem;
import org.python.core.PyException;
import org.python.core.PyInteger;
import org.python.core.PySyntaxError;
import org.virbo.datasource.FileSystemUtil;

/**
 *
 * @author jbf
 */
public class ScriptPanelSupport {

    EditorTextPane editor;
    final EditorAnnotationsSupport annotationsSupport;
    JLabel fileNameLabel;

    public ScriptPanelSupport( final EditorTextPane editor ) {
        this.editor= editor;
        this.editor.getDocument().addDocumentListener(new DocumentListener() {

            public void insertUpdate(DocumentEvent e) {
                setDirty(true);
            }

            public void removeUpdate(DocumentEvent e) {
                setDirty(true);
            }

            public void changedUpdate(DocumentEvent e) {
            }
        });

        this.annotationsSupport = editor.getEditorAnnotationsSupport();
    }

    protected boolean dirty = false;
    public static final String PROP_DIRTY = "dirty";

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        boolean oldDirty = this.dirty;
        this.dirty = dirty;
        updateFileNameLabel();
        propertyChangeSupport.firePropertyChange(PROP_DIRTY, oldDirty, dirty);
    }
    protected File file = null;
    public static final String PROP_FILE = "file";

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        File oldFile = this.file;
        this.file = file;
        updateFileNameLabel();
        propertyChangeSupport.firePropertyChange(PROP_FILE, oldFile, file);
    }


    private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    protected void save( File file ) throws FileNotFoundException, IOException {
        if ( file==null || FileSystemUtil.isChildOf( FileSystem.settings().getLocalCacheDir(), file ) ) {
            saveAs();
            return;
        }
        OutputStream out = null;
        try {
            out = new FileOutputStream(file);
            String text = editor.getText();
            out.write(text.getBytes());
            setDirty(false);
        } finally {
            out.close();
        }
    }

    public void loadFile(File file) throws IOException, FileNotFoundException {
        InputStream r= new FileInputStream(file);
        loadInputStream( r );
        setFile(file);
        setDirty(false);
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
            Document d = editor.getDocument();
            d.remove(0, d.getLength());
            d.insertString(0, buf.toString(), null);
            setDirty(false);
        } catch (BadLocationException ex) {
            throw new RuntimeException(ex);
        } finally {
            if (r != null) r.close();
        }
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

    public int getSaveFile() throws IOException {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(getFileFilter());
        if (file != null && ! FileSystemUtil.isChildOf( FileSystem.settings().getLocalCacheDir(), file ) ) {
            chooser.setSelectedFile(file);
        }
        int r = chooser.showSaveDialog(editor);
        if (r == JFileChooser.APPROVE_OPTION) {
            file = chooser.getSelectedFile();
            if (!(file.toString().endsWith(".jy") || file.toString().endsWith(".py") || file.toString().endsWith(".jyds"))) {
                file = new File(file.toString() + ".jy");
                //TODO NOW: .jyds
            }
        }
        return r;
    }

    protected void saveAs() {
        OutputStream out = null;
        try {
            if ( getSaveFile() == JFileChooser.APPROVE_OPTION) {
                out = new FileOutputStream(file);
                String text = editor.getText();
                out.write(text.getBytes());
                setDirty(false);
                setFile(file);
            }

        } catch (IOException iOException) {
            DasExceptionHandler.handle(iOException);  //TODO: service registry
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

    public void addCaretLabel(final JLabel caretPositionLabel) {

       editor.addCaretListener(new CaretListener() {

            public void caretUpdate(CaretEvent e) {
                int pos = editor.getCaretPosition();
                Element root = editor.getDocument().getDefaultRootElement();
                int irow = root.getElementIndex(pos);
                int icol = pos - root.getElement(irow).getStartOffset();
                String text = "" + (1 + irow) + "," + (1 + icol);
                int isel = editor.getSelectionEnd() - editor.getSelectionStart();
                int iselRow0 = root.getElementIndex(editor.getSelectionStart());
                int iselRow1 = root.getElementIndex(editor.getSelectionEnd());
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

    }

    public void addFileLabel( final JLabel fileNameLabel ) {
        this.fileNameLabel= fileNameLabel;
        updateFileNameLabel();
    }

    private void updateFileNameLabel() {
        if ( this.fileNameLabel!=null ) {
            fileNameLabel.setText( String.valueOf( this.getFile() ) + " " + ( this.isDirty() ? "*" : "" ) );
        }
    }
}

