
package org.autoplot.scriptconsole;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Event;
import java.awt.HeadlessException;
import java.beans.ExceptionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import org.autoplot.jythonsupport.JythonRefactory;
import org.das2.components.DasProgressPanel;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.python.core.FileUtil;
import org.python.core.Py;
import org.python.core.PyException;
import org.python.core.PyInteger;
import org.python.core.PyObject;
import org.python.core.PySyntaxError;
import org.python.core.PyTraceback;
import org.python.core.ThreadState;
import org.python.util.InteractiveInterpreter;
import org.python.util.PythonInterpreter;
import org.autoplot.ApplicationModel;
import org.autoplot.AutoplotUtil;
import org.autoplot.JythonUtil;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.dom.ApplicationController;
import org.autoplot.datasource.DataSetSelector;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.FileSystemUtil;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.jython.JythonDataSourceFactory;
import org.autoplot.jythonsupport.ui.EditorAnnotationsSupport;
import org.autoplot.jythonsupport.ui.ParametersFormPanel;

/**
 * Error annotations, saveAs, etc.
 * @see EditorAnnotationsSupport
 * @author jbf
 */
public class ScriptPanelSupport {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.jython");
    
    private static final int RECENT_FILES_COUNT = 100;

    File file;
    final ApplicationModel model;
    final ApplicationController applicationController;
    final DataSetSelector selector;
    final JythonScriptPanel panel;
    final EditorAnnotationsSupport annotationsSupport;
    private final String PREFERENCE_OPEN_FILE = "openFile";
    private InteractiveInterpreter interruptible;
    ThreadState ts;

    ScriptPanelSupport(final JythonScriptPanel panel, final ApplicationModel model, final DataSetSelector selector) {
        this.model = model;
        this.applicationController = model.getDocumentModel().getController();
        this.selector = selector;
        this.panel = panel;
        this.annotationsSupport = panel.getEditorPanel().getEditorAnnotationsSupport();

        applicationController.addPropertyChangeListener(ApplicationController.PROP_FOCUSURI, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if ( panel.runningScript==null ) {
                    maybeDisplayDataSourceScript();
                }
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
                logger.fine("editor is dirty, not showing script.");
                return false;
            }
            
            if ( this.panel.getRunningScript()!=null ) {
                logger.fine("editor is busy running a script.");
                return false;
            }
            
            if ( split.params!=null ) {
                Map<String,String> params= URISplit.parseParams(split.params);
                if ( params.containsKey("script") ) {
                    sfile= params.get("script");
                } else {
                    sfile= split.resourceUri.toString();
                }
            } else {
                sfile= split.resourceUri.toString();
            }
            final URI fsfile= DataSetURI.getURI(sfile);
            if ( panel.getFilename()!=null ) {
                URI u1= DataSetURI.getURI(panel.getFilename());
                String f1= URISplit.parse(u1).file;
                URI u2= DataSetURI.getURI(sfile);
                String f2= URISplit.parse(u2).file;
                if ( f1.startsWith("file:" ) && f2.startsWith("file:") ) {
                    File ff1= new File( f1.substring(5) ); //TODO: Windows
                    File ff2= new File( f2.substring(5) );
                    if ( ff1.equals(ff2) ) {
                        return true;
                    }
                }
                File ff3= DataSetURI.getCacheFilename( fsfile );
                if ( f1.startsWith("file:") && ff3!=null ) {
                    File ff1= new File( f1.substring(5) ); //TODO: Windows
                    if ( ff3.equals(ff1) ) {
                        return true;
                    }
                }
            }
            
            //TODO: why can't we have a DasProgressPanel on any component?
            Runnable run= new Runnable() {
                public void run() {
                    try {
                        file = DataSetURI.getFile( fsfile, new NullProgressMonitor() );
                        loadFile(file);
                        panel.setContext(JythonScriptPanel.CONTEXT_DATA_SOURCE);
                        panel.setFilename(file.toString());    
                    } catch (IOException ex) {
                        logger.log(Level.SEVERE, ex.getMessage(), ex);
                    }
                }
            };
            try {
                panel.getEditorPanel().getEditorKit();
                MutableAttributeSet att= new SimpleAttributeSet();
                StyleConstants.setItalic( att, true);
                panel.getEditorPanel().getDocument().remove( 0, panel.getEditorPanel().getDocument().getLength() );
                panel.getEditorPanel().getDocument().insertString( 0, "loading "+fsfile, att );
            } catch ( BadLocationException ex ) {
                
            }
            new Thread( run, "load script thread" ).start();
            
        } catch (NullPointerException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            return false;
        } catch (URISyntaxException ex ) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            return false;
        }

        return true;
    }

    /**
     * get the save file name with a save file dialog.
     * @return response code, such as JFileChooser.APPROVE_OPTION or JFileChooser.CANCEL_OPTION
     * @throws IOException 
     * @see javax.swing.JFileChooser#showSaveDialog(java.awt.Component) 
     */
    public int getSaveFile() throws IOException {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(getFileFilter());
        final JCheckBox cb= new JCheckBox("rename");
        cb.setEnabled(true);
        cb.setToolTipText("rename file, deleting old name \""+file+"\"");
//        chooser.addPropertyChangeListener( JFileChooser.SELECTED_FILE_CHANGED_PROPERTY, new PropertyChangeListener() {
//            @Override
//            public void propertyChange(PropertyChangeEvent evt) {
//                System.err.println("eh?");
//                if ( evt.getOldValue()!=null ) { 
//                    System.err.println("evt name change"+evt.getOldValue()+"-->");
//                    System.err.println("evt name change"+evt.getNewValue());
//                    cb.setEnabled(true);
//                }
//            }
//        }); // This works inconsistently.
        chooser.setAccessory(cb);
        if (file != null && ! FileSystemUtil.isChildOf( FileSystem.settings().getLocalCacheDir(), file ) ) {
            chooser.setSelectedFile(file);
        } else {
            if ( FileSystemUtil.isChildOf( FileSystem.settings().getLocalCacheDir(), file ) ) {
                String home = System.getProperty("user.home");
                File ff = new File( home + File.separator + file.getName() );
                chooser.setSelectedFile(ff);
            } else {
                chooser.setSelectedFile(file);
            }
            Preferences prefs = AutoplotSettings.settings().getPreferences(ScriptPanelSupport.class);
            String openFile= prefs.get(PREFERENCE_OPEN_FILE, "");
            if ( !openFile.equals("") && !FileSystemUtil.isChildOf( FileSystem.settings().getLocalCacheDir(), new File(openFile) )  ) {
                File dir= new File(openFile).getParentFile();
                chooser.setCurrentDirectory( dir );
            }
        }
        if ( file==null ) {
            Preferences prefs = AutoplotSettings.settings().getPreferences(ScriptPanelSupport.class);
            String openFile = prefs.get(PREFERENCE_OPEN_FILE, "");
            if ( !openFile.equals("") ) {
                chooser.setCurrentDirectory( new File(openFile).getParentFile() );
            }
        }
        int r = chooser.showSaveDialog(panel);
        if (r == JFileChooser.APPROVE_OPTION) {
            File oldFile= file;
            file = chooser.getSelectedFile();
            if (!(file.toString().endsWith(".jy") || file.toString().endsWith(".py") || file.toString().endsWith(".jyds"))) {
                if (panel.getContext() == JythonScriptPanel.CONTEXT_DATA_SOURCE) {
                    file = new File(file.toString() + ".jyds");
                } else {
                    file = new File(file.toString() + ".jy");
                }
            }
            if ( oldFile!=null ) {
                if ( cb.isSelected() && !oldFile.equals(file) ) {
                    if ( ! oldFile.delete() ) {
                        JOptionPane.showMessageDialog( panel, "unable to delete old file: "+oldFile );
                    }
                }
            }
        }
        return r;
    }

    /**
     * save the editor contents to a file.  Note this used to return JOptionPane.OK_OPTION, but this is also 0.
     * @return JFileChooser.APPROVE_OPTION or JFileChooser.CANCEL_OPTION if the operator cancelled the saveAs operation.
     * @throws FileNotFoundException
     * @throws IOException 
     */
    protected int save() throws FileNotFoundException, IOException {
        if ( file==null || file.toString().contains( FileSystem.settings().getLocalCacheDir().toString()) ) {
            logger.fine("file is null ");
            if ( panel.isDirty() ) {
                return saveAs();
            }
            return JFileChooser.APPROVE_OPTION;
        }
        OutputStream out = null;
        try {
            if ( !( file.exists() && file.canWrite() || file.getParentFile().canWrite() ) ) throw new IOException("unable to write to file: "+file);
            if ( watcher!=null ) {
                try {
                    logger.fine("closing watcher");
                    watcher.close();
                } catch ( IOException ex ) {
                    logger.log( Level.WARNING, ex.getMessage(), ex );
                }
            } else {
                logger.fine("logger was null");
            }
            out = new FileOutputStream(file);
            String text = panel.getEditorPanel().getText();
            out.write(text.getBytes());
            panel.setDirty(false);
            
            final File ffile= file;
            Runnable run= new Runnable() {
                public void run() {
                    try {
                        logger.fine("pausing before restarting watcher");
                        Thread.sleep(10000);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(ScriptPanelSupport.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    try {
                        logger.fine("restarting watcher");
                        restartWatcher(ffile);
                    } catch (IOException ex) {
                        Logger.getLogger(ScriptPanelSupport.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            };
            new Thread(run).start();
            
        } finally {
            if ( out!=null ) out.close();
        }
        return JFileChooser.APPROVE_OPTION;
    }
    
    WatchService watcher;
    
    private void watcherRunnable( final WatchService watch, final Path fpath ) {
        Runnable run= new Runnable() {
            @Override
            public void run() {
                Path parent= fpath.getParent();
                logger.log(Level.FINER, "start watch event loop on {0}", new Object[]{parent});
                while ( true ) {
                    try {
                        WatchKey key= watcher.take();
                        for ( WatchEvent e : key.pollEvents() ) {
                            
                            WatchEvent<Path> ev = (WatchEvent<Path>) e;
                            Path name = ev.context();
                            logger.log(Level.FINER, "watch event {0} {1}", new Object[]{ev.kind(), ev.context()});
                            if ( parent.resolve(name).equals(fpath) ) {
                                
                                String newContents;
                                
                                try ( InputStream in = new FileInputStream( ScriptPanelSupport.this.file ) ) {
                                    newContents= new String( FileUtil.readBytes( in ) );
                                    String currentf= panel.getEditorPanel().getText();
                                    currentf= currentf.trim(); // there's a strange bug where newContents has a newline at the end that current doesn't.
                                    newContents= newContents.trim();
                                    if ( currentf.equals(newContents) ) {
                                        logger.fine("timestamp changed but contents are the same.");
                                        break;
                                    }
                                    
                                } catch ( IOException ex ) {
                                    break;
                                }
                                
                                if ( JOptionPane.OK_OPTION== 
                                    JOptionPane.showConfirmDialog( panel, 
                                    "File changed on disk.  Do you want to reload?", 
                                    "File Changed on Disk",
                                    JOptionPane.OK_CANCEL_OPTION ) ) {
                                    try {
                                        loadFile( ScriptPanelSupport.this.file );
                                    } catch (IOException ex) {
                                        logger.log(Level.SEVERE, null, ex);
                                    }
                                } else {
                                    ScriptPanelSupport.this.panel.setDirty(true);
                                }
                            }
                        }   
                        if ( !key.reset() ) {
                            logger.log(Level.FINER, "watch key could not be reset: {0}", key);
                            return;
                        }
                    } catch ( ClosedWatchServiceException ex ) {
                        logger.log(Level.FINER, "watch service was closed: {0}", watch);
                        return;
                        
                    } catch (InterruptedException ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                }
            }            
        };
        new Thread(run,"fileWatcherRunnable").start();
    }
    
    private void restartWatcher( File file ) throws IOException {
        logger.entering( "org.autoplot.scriptconsole", "restartWatcher {0}", file );
        if ( watcher!=null ) {
            watcher.close();
        } 
        watcher = FileSystems.getDefault().newWatchService();
        Path fpath= file.toPath();
        Path parent= fpath.getParent();
        try {
            parent.register( watcher, StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE );
            //parent.register( watcher, StandardWatchEventKinds.OVERFLOW );
            watcherRunnable( watcher, file.toPath() );
        } catch ( ClosedWatchServiceException ex ) {
            logger.fine("watch service was closed");
        }

        logger.exiting("org.autoplot.scriptconsole", "restartWatcher {0}", file );
    }
    
    protected void loadFile(File file) throws IOException, FileNotFoundException {
        try (InputStream r = new FileInputStream(file)) {
            this.file= file;
            panel.setFilename( file.toString() );
            loadInputStream( r );
            if (file.toString().endsWith(".jyds")) {
                panel.setContext(JythonScriptPanel.CONTEXT_DATA_SOURCE);
            } else {
                panel.setContext(JythonScriptPanel.CONTEXT_APPLICATION);
            }
        }
        restartWatcher(file);
    }

    protected void loadInputStream( InputStream in ) throws IOException {
        BufferedReader r = null;
        panel.containsTabs= false;
        try {
            StringBuilder buf = new StringBuilder();
            r = new BufferedReader(new InputStreamReader(in));
            String s = r.readLine();
            int tabWarn= 3;
            int lineNum= 1;
            while (s != null) {
                if ( s.contains("\t") ) {
                    panel.containsTabs= true;
                    if ( tabWarn>0 ) {
                        logger.log(Level.FINE, "line {0} contains tabs: {1}", new Object[] { lineNum, s } );
                        tabWarn--;
                    }
                }
                buf.append(s).append("\n");
                s = r.readLine();
                lineNum++;
            }
            final String fs= buf.toString();
            Runnable run= new Runnable() {
                @Override
                public void run() {
                    try {
                        annotationsSupport.clearAnnotations();
                        Document d = panel.getEditorPanel().getDocument();
                        d.remove(0, d.getLength());
                        d.insertString(0, fs, null);
                        panel.setDirty(false);
                    } catch ( NullPointerException ex ) {
                        try {
                            Document d = panel.getEditorPanel().getDocument();
                            d.remove(0, d.getLength());
                            d.insertString(0, fs, null);
                            panel.setDirty(false);
                        } catch ( BadLocationException ex2 ) {
                            
                        }
                    } catch ( BadLocationException ex ) {
                        
                    }
                }
            };
            SwingUtilities.invokeLater(run);
        } finally {
            if (r != null) r.close();
        }
    }

    private boolean uriFilesEqual(String surl1, String surl2) throws URISyntaxException {
        int i1 = surl1.indexOf('?');
        if (i1 == -1) {
            i1 = surl1.length();
        }
        URI uri1 = DataSetURI.getURI(surl1.substring(0, i1));
        int i2 = surl2.indexOf('?');
        if (i2 == -1) {
            i2 = surl2.length();
        }
        URI uri2 = DataSetURI.getURI(surl2.substring(0, i2));
        if ( uri1==null ) return false;
        return uri1.equals(uri2);
    }

    /**
     * march through the stack trace looking for any Jython references.
     * @param ex the exception
     */
    public void annotateError( Throwable ex ) {
        int line; // the line number                    
        StackTraceElement[] ses= ex.getStackTrace();
        if ( file!=null ) {
            for ( StackTraceElement se: ses ) {
                if ( se!=null && se.getFileName()!=null && se.getFileName().endsWith(file.getName()) && se.getLineNumber()>-1 ) {
                    line= se.getLineNumber();
                    annotationsSupport.annotateLine( line, "error", ex.toString(), null );
                }
            }
        }
    }
    
    /**
     * Annotate the error.  
     * @param ex the exception
     * @param offset line offset from beginning of file where execution began.
     * @param interp null or the interp for further queries.
     */
    public void annotateError(PyException ex, int offset, final PythonInterpreter interp) {
        if (ex instanceof PySyntaxError) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            int lineno = offset + ((PyInteger) ex.value.__getitem__(1).__getitem__(1)).getValue();
            //String filename= String.valueOf( (ex.value.__getitem__(1).__getitem__(3)) );
            //int col = ((PyInteger) ex.value.__getitem__(1).__getitem__(2)).getValue();
            annotationsSupport.annotateLine(lineno, "error", ex.toString(),interp);
        } else {
            //logger.log(Level.SEVERE, ex.getMessage(), ex);
            PyObject otraceback= ex.traceback;
            int line=0;
            int count=0; // just in case limit to three, because of recursion, etc.
            //PyFrame theFrame= null;
            while ( otraceback instanceof PyTraceback && count<3 ) {
                PyTraceback traceback= ((PyTraceback)otraceback);
//                if ( theFrame==null ) { //TODO: check that we don't switch files.  this code doesn't work...
//                    theFrame= traceback.tb_frame;
//                } else {
//                    if ( theFrame.f_code._code!=traceback.tb_frame.f_code ) {
//                        break;
//                    }
//                }
                if ( traceback.tb_frame==null ) { // this happens with invokeLater and Java exception
                    PyObject o= ex.value;
                    if ( o!=null ) {
                        if ( o instanceof PyObject ) {
                            // error popup should follow.
                        } else {
                            Exception e= (Exception)o.__tojava__(Exception.class);
                            if ( e!=null ) {
                                annotateError(e);
                            }
                        }
                    }
                    otraceback= traceback.tb_next;
                } else { // typical
                    String fn= traceback.tb_frame.f_code.co_filename;
                    if ( fn!=null && ( fn.equals("<iostream>") || fn.equals("<string>") || ( file!=null && fn.equals( file.getName() ) ) ) ) { 
                        annotationsSupport.annotateLine(offset + traceback.tb_lineno, "error", ex.toString(),interp);
                        line=  traceback.tb_lineno-1;
                        otraceback= traceback.tb_next;
                        count++;
                    } else {
                        otraceback= traceback.tb_next;
                    }
                }
            }
            //System.err.println("***");
            //System.err.println("line="+line);
            
            if ( line<0 ) {
                logger.warning("no trace information available for error "+ex.getMessage());
                line=0;
            }
            final int fline= line;
            final JEditorPane textArea= panel.getEditorPanel();
            SwingUtilities.invokeLater( new Runnable() { public void run() {
                Element element= textArea.getDocument().getDefaultRootElement().getElement(Math.max(0,fline-5)); // 5 lines of context.
                if ( element!=null ) textArea.setCaretPosition(element.getStartOffset()); 
                SwingUtilities.invokeLater( new Runnable() { public void run() {
                    Element element= textArea.getDocument().getDefaultRootElement().getElement(fline); // 5 lines of context.
                    if ( element!=null ) textArea.setCaretPosition(element.getStartOffset()); 
                } } );
            } } );
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
     * @param mode =0 normal.  =2=CTRL_MASK= trace.  ALT_MASK is enter editor.  SHIFT_MASK brings up parameters GUI.
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
                                @Override
                                public void exceptionThrown(Exception e) {
                                    if (e instanceof PyException) {
                                        PyException ex= (PyException)e;
                                        annotateError(ex, 0, null );
                                    }
                                }
                            });
                        }

                    } catch (URISyntaxException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                boolean updateSurl = false;
                if ( file == null || ( panel.isDirty() && FileSystemUtil.isChildOf( FileSystem.settings().getLocalCacheDir(), file ) ) ) {
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
                
                panel.setRunningScript(null);

            } else if (panel.getContext() == JythonScriptPanel.CONTEXT_APPLICATION) {
                applicationController.setStatus("busy: executing application script");
                Runnable run = new Runnable() {
                    @Override
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
                                
                                EditorAnnotationsSupport.setExpressionLookup(annotationsSupport.getForInterp(interp));
                
                                interp.set("dom", model.getDocumentModel() );
                                interp.set("monitor", mon );
                                if ( file!=null ) {
                                    URISplit split= URISplit.parse(file.toString());
                                    interp.set( "PWD", split.path );   
                                }
                                setInterruptible( interp );
                                ts= Py.getThreadState();
                                boolean dirty0 = panel.isDirty();
                                annotationsSupport.clearAnnotations();
                                panel.setDirty(dirty0);
                                if ( ( ( mode & Event.CTRL_MASK ) == Event.CTRL_MASK ) ) { // trace
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
                                            interp.exec(JythonRefactory.fixImports(s));
                                        } catch (PyException ex) {
                                            throw ex;
                                        }
                                        i0 = i1;
                                        offset += 1;
                                        System.err.println(s);
                                    }
                                    annotationsSupport.clearAnnotations();
                                } else if ( ( ( mode & Event.SHIFT_MASK ) == Event.SHIFT_MASK ) || ( ( mode & Event.ALT_MASK ) == Event.ALT_MASK ) ) {
                                    JPanel p= new JPanel();
                                    Map<String,String> vars= new HashMap();
                                    ParametersFormPanel pfp= new org.autoplot.jythonsupport.ui.ParametersFormPanel();
                                    Map<String,Object> env= new HashMap();
                                    env.put("dom",interp.get("dom") );
                                    env.put("PWD",interp.get("PWD") );
                                    ParametersFormPanel.FormData fd=  pfp.doVariables( env, panel.getEditorPanel().getText(), vars, p );
                                    if ( fd.count>0 ) {
                                        JScrollPane pane= new JScrollPane(p);
                                        
                                        if ( AutoplotUtil.showConfirmDialog2( panel, pane, "edit parameters", JOptionPane.OK_CANCEL_OPTION )==JOptionPane.OK_OPTION ) {
                                            ParametersFormPanel.resetVariables( fd, vars );
                                            String parseExcept= null;
                                            for ( Entry<String,String> v: vars.entrySet() ) {
                                                try {
                                                    fd.implement( interp, v.getKey(), v.getValue() );
                                                } catch ( ParseException ex ) {
                                                    parseExcept= v.getKey();
                                                }
                                            }
                                            if ( parseExcept!=null ) {
                                                JOptionPane.showMessageDialog( panel, "ParseException in parameter "+parseExcept );
                                            } else {
                                                interp.exec(JythonRefactory.fixImports(panel.getEditorPanel().getText()));
                                            }
                                        }
                                    } else {
                                        // no parameters
                                        interp.exec(JythonRefactory.fixImports(panel.getEditorPanel().getText()));
                                    }
                                } else {
                                    boolean experiment= System.getProperty("jythonDebugger","false").equals("true");
                                    if ( experiment ) {
                                        final DebuggerConsole dc= DebuggerConsole.getInstance(panel);
                                        dc.setInterp(interp);
                                        interp.setOut(getOutput(dc));
                                        EditorAnnotationsSupport.setExpressionLookup( new EditorAnnotationsSupport.ExpressionLookup() {
                                            @Override
                                            public PyObject lookup( final String expr ) {
                                                return dc.setEval(expr);
                                                //return dc.getEval();                                                
                                            }
                                        });
                                    }
                                    String code= panel.getEditorPanel().getText();
                                    if ( file!=null ) {
                                        char[] cc= code.toCharArray();
                                        boolean warning= false;
                                        for ( char c: cc ) {
                                            if ( c>128 ) warning= true;
                                        }
                                        if ( warning ) {
                                            System.err.println("code contains data that will not be represented properly!");
                                        }
                                        try (InputStream in = new ByteArrayInputStream( code.getBytes() )) {
                                            interp.execfile(JythonRefactory.fixImports(in),file.getName());
                                        }
                                    } else {
                                        interp.exec(JythonRefactory.fixImports(code));
                                    }
                                }
                                setInterruptible( null );
                                if ( !mon.isFinished() ) mon.finished(); // bug1251: in case script didn't call finished
                                applicationController.setStatus("done executing script");
                            } catch (IOException ex) {
                                if ( !mon.isFinished() ) mon.finished();
                                logger.log(Level.WARNING, ex.getMessage(), ex);
                                applicationController.setStatus("error: I/O exception: " + ex.toString());
                            } catch (PyException ex) {
                                if ( !mon.isFinished() ) mon.finished();
                                annotateError(ex, offset, interp );
                                //logger.log(Level.WARNING, ex.getMessage(), ex );
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
                            if ( !mon.isFinished() ) mon.finished();  // bug1251: in case script didn't call finished
                            setInterruptible( null );
                            panel.setRunningScript(null);
                        }

                    }
                };
                new Thread(run,"sessionRunScriptThread").start();
            }

        } catch (IOException iOException) {
            model.getExceptionHandler().handle(iOException);
            panel.setRunningScript(null);
        }

    }

    
    protected class MyOutputStream extends FilterOutputStream {
        
        OutputStream sink;
        DebuggerConsole dc;
        
        public MyOutputStream(OutputStream out,DebuggerConsole dc) {
            super(out);
            this.sink= out;
            this.dc= dc;
        }

        StringBuilder currentLine= new StringBuilder();
        
        /**
         * standard mode output.
         */
        private final Object STATE_OPEN="OPEN";
        /**
         * collect the prompt so that it is not echoed.
         */
        private final Object STATE_FORM_PDB_PROMPT="PROMPT";
        /**
         * collect the prompt so that it is not echoed.
         */
        private final Object STATE_RETURN_INIT_PROMPT="RETURN";
        /**
         * pdb output encountered.
         */
        private final Object STATE_PDB="PDB";
        
        /**
         * pdb response
         */
        protected final Object STATE_FORM_PDB_RESPONSE="RESPONSE";
        
        Object state= STATE_OPEN;
        
        
        @Override
        public void write(byte[] b) throws IOException {
            for ( int i=0; i<b.length; i++ ) {
               this.write(b[i]);
            }
        }

        public void writeNew( int b ) throws IOException {
            if ( dc!=null ) dc.print(String.valueOf((char)b));
            if ( state==STATE_OPEN ) {
                if ( b>=0 ) currentLine.append((char)b);
                if ( currentLine.length()==1 && currentLine.substring(0,1).equals("(") ) {
                    state= STATE_FORM_PDB_PROMPT;
                } else if ( currentLine.length()==1 && currentLine.substring(0,1).equals(">") ) {
                    state= STATE_FORM_PDB_RESPONSE;
                } else if ( currentLine.length()>10 && currentLine.substring(0,10).equals("--Return--") ) {
                    state= STATE_RETURN_INIT_PROMPT;
                } else if ( currentLine.length()>0 && ( b==10 || b==13 ) ) {
                    currentLine= new StringBuilder();
                }
            } else if ( state==STATE_RETURN_INIT_PROMPT ) {
                if ( b>=0 ) currentLine.append((char)b);
                if ( currentLine.length()==1 && currentLine.substring(0,1).equals("(") ) {
                    state= STATE_FORM_PDB_PROMPT;
                } else if ( currentLine.length()>0 && ( b==10 || b==13 ) ) {
                    currentLine= new StringBuilder();
                }
            } else if ( state==STATE_FORM_PDB_PROMPT ) {
                if ( b>=0 ) currentLine.append((char)b);
                if ( currentLine.length()>=6 ) {
                    if ( currentLine.substring(0,5).equals("(Pdb) ") ) {
                        state= STATE_PDB;
                    } else {
                        sink.write(currentLine.toString().getBytes());
                        state= STATE_OPEN;
                    }
                } else if ( currentLine.length()>0 && ( b==10 || b==13 ) ) { // newlines should clear the currentLine
                    sink.write(currentLine.toString().getBytes());
                    currentLine= new StringBuilder();
                    state= STATE_OPEN;
                }
            } else if ( state==STATE_FORM_PDB_RESPONSE ) {
                if ( b>=0 && b!=10 && b!=13 ) currentLine.append((char)b);  
                
                Pattern p= Pattern.compile("\\>? \\S+\\((\\d+)\\)\\S+\\(\\)");
                Matcher m= p.matcher(currentLine);
                if ( m.matches() ) {
                    String linenum= m.group(1);
                    annotationsSupport.clearAnnotations();
                    int[] pos= annotationsSupport.getLinePosition(Integer.parseInt(linenum));
                    annotationsSupport.annotateChars( pos[0], pos[1], "programCounter", "pc", interruptible );
                    state= STATE_OPEN;
                    currentLine= new StringBuilder();
                } else {
                    
                    if ( b==13 || b==10 ) {
                        state= STATE_OPEN;
                        sink.write(currentLine.toString().getBytes());
                        currentLine= new StringBuilder();
                        
                    }
                }
                
            } else if ( state==STATE_PDB ) { // the beginning of the currentLine is (Pdb) and we want a terminator
                Pattern p= Pattern.compile("\\(Pdb\\) (.*)>? <string>\\((\\d+)\\)\\?\\(\\)\\s*");
                Pattern p2= Pattern.compile("\\(Pdb\\) (.*)--Return--.*>? <string>\\((\\d+)\\)\\?\\(\\)\\s*.*");
                
                int l= currentLine.length();
                if ( b>=0 && b!=10 && b!=13 ) currentLine.append((char)b);  //TODO: why is my regex not working when newlines get in there?
                if ( l>2 && currentLine.substring(l-2,l).equals("()") ) {
                    Matcher m= p.matcher(currentLine);
                    if ( m.matches() ) {
                        String linenum= m.group(2);
                        annotationsSupport.clearAnnotations();
                        int[] pos= annotationsSupport.getLinePosition(Integer.parseInt(linenum));
                        annotationsSupport.annotateChars( pos[0], pos[1], "programCounter", "pc", interruptible );
                        String userOutput= m.group(1);
                        if ( userOutput.length()>0 ) {
                            sink.write(userOutput.getBytes());
                            sink.write("\n".getBytes());
                        }
                        state= STATE_OPEN;
                        currentLine= new StringBuilder();
                    } else {
                        Matcher m2= p2.matcher(currentLine);
                        if ( m2.matches() ) {
                            annotationsSupport.clearAnnotations();
                            String userOutput= m2.group(1);
                            if ( userOutput.length()>0 ) {
                                sink.write(userOutput.getBytes());
                                sink.write("\n".getBytes());
                            }
                            state= STATE_OPEN;
                            currentLine= new StringBuilder();
                        } else {
                            state= STATE_OPEN;
                            currentLine= new StringBuilder();
                        }
                    }
                }
            }
            
        }
       
        @Override
        public void write(int b) throws IOException {

            if ( dc!=null ) dc.print(String.valueOf((char)b),state);
            
            if ( state==STATE_OPEN ) {
                if ( b>=0 ) currentLine.append((char)b);
                if ( currentLine.length()==1 && currentLine.substring(0,1).equals("(") ) {
                    state= STATE_FORM_PDB_PROMPT;
                } else if ( currentLine.length()>10 && currentLine.substring(0,10).equals("--Return--") ) {
                    state= STATE_RETURN_INIT_PROMPT;
                    dc.started();
                    dc.next();  // jump out of pdb
                } else if ( currentLine.length()>0 && ( b==10 || b==13 ) ) {
                    currentLine= new StringBuilder();
                }
            } else if ( state==STATE_RETURN_INIT_PROMPT ) {
                if ( b>=0 ) currentLine.append((char)b);
                if ( currentLine.length()==1 && currentLine.substring(0,1).equals("(") ) {
                    state= STATE_FORM_PDB_PROMPT;
                } else if ( currentLine.length()>0 && ( b==10 || b==13 ) ) {
                    currentLine= new StringBuilder();
                }
            } else if ( state==STATE_FORM_PDB_PROMPT ) {
                if ( b>=0 ) currentLine.append((char)b);
                if ( currentLine.length()>=5 ) {
                    if ( currentLine.substring(0,5).equals("(Pdb)") ) {
                        state= STATE_PDB;
                    } else {
                        sink.write(currentLine.toString().getBytes());
                        state= STATE_OPEN;
                    }
                } else if ( currentLine.length()>0 && ( b==10 || b==13 ) ) { // newlines should clear the currentLine
                    sink.write(currentLine.toString().getBytes());
                    currentLine= new StringBuilder();
                    state= STATE_OPEN;
                }
            } else if ( state==STATE_PDB ) { // the beginning of the currentLine is (Pdb) and we want a terminator
                Pattern p= Pattern.compile("\\(Pdb\\) (.*)> .*\\.jy\\((\\d+)\\).*\\(\\)\\s*"); // TODO: replace ).* with more precise value.
                Pattern p2= Pattern.compile("\\(Pdb\\) (.*)--Return--.*");
                
                int l= currentLine.length();
                if ( b>=0 && b!=10 && b!=13 ) currentLine.append((char)b);  //TODO: why is my regex not working when newlines get in there?
                if ( l>2 && currentLine.substring(l-2,l).equals("()") ) {
                    Matcher m= p.matcher(currentLine);
                    if ( m.matches() ) {
                        String linenum= m.group(2);
                        annotationsSupport.clearAnnotations();
                        int[] pos= annotationsSupport.getLinePosition(Integer.parseInt(linenum));
                        annotationsSupport.annotateChars( pos[0], pos[1], "programCounter", "pc", interruptible );
                        String userOutput= m.group(1);
                        if ( userOutput!=null && userOutput.length()>0 ) {
                            sink.write(userOutput.getBytes());
                            sink.write("\n".getBytes());
                        }
                        state= STATE_OPEN;
                        currentLine= new StringBuilder();
                    } else {
                        Matcher m2= p2.matcher(currentLine);
                        if ( m2.matches() ) {
                            annotationsSupport.clearAnnotations();
                            String userOutput= m2.group(1);
                            if ( userOutput.length()>0 ) {
                                sink.write(userOutput.getBytes());
                                sink.write("\n".getBytes());
                            }
                            state= STATE_OPEN;
                            currentLine= new StringBuilder();
                            dc.finished();
                            dc.next();
                        } else {
                            state= STATE_OPEN;
                            currentLine= new StringBuilder();
                        }
                    }
                }
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            //String s= new String( b, off, len );
            for ( int i=off; i<len; i++ ) {
                this.write(b[i]);
            }
        }
    } 
    
    
   /**
     * create special output stream for script panel
     * @return 
     */
    private OutputStream getOutput(DebuggerConsole dc) {
        return new MyOutputStream(System.out, dc);
    }
    
    private FileFilter getFileFilter() {
        return new FileFilter() {

            @Override
            public boolean accept(File f) {
                if ( f.toString()==null ) return false; //file.toString sometimes returns null on Windows.
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
     * @return JFileChooser.APPROVE_OPTION or JFileChooser.CANCEL_OPTION
     */
    protected int saveAs() {
        OutputStream out = null;
        int result= JFileChooser.CANCEL_OPTION;
        try {
            result= getSaveFile();
            if (result == JFileChooser.APPROVE_OPTION) {
                if ( watcher!=null ) {
                    try {
                        watcher.close();
                    } catch ( IOException ex ) {
                        logger.log( Level.WARNING, ex.getMessage(), ex );
                    }
                }
                out = new FileOutputStream(file);
                String text = panel.getEditorPanel().getText();
                out.write(text.getBytes());
                panel.setDirty(false);
                panel.setFilename(file.toString());
                restartWatcher(file);
                
                Preferences prefs = AutoplotSettings.settings().getPreferences(ScriptPanelSupport.class);
                prefs.put(PREFERENCE_OPEN_FILE, file.toString() );
                
                if ( file.toString().endsWith(".jyds") ) {
                    panel.setContext( JythonScriptPanel.CONTEXT_DATA_SOURCE );
                } else {
                    panel.setContext( JythonScriptPanel.CONTEXT_APPLICATION );
                }

            }

        } catch (IOException iOException) {
            model.getExceptionHandler().handle(iOException);
            
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
        return result;
    }

    protected void newScript() {
        if (panel.isDirty()) {
            int result = AutoplotUtil.showConfirmDialog(panel,"save edits first?", "new script", JOptionPane.YES_NO_CANCEL_OPTION );
            if (result == JOptionPane.CANCEL_OPTION) {
                return;
            }
            if ( result==JOptionPane.OK_OPTION ) {
                if ( saveAs()==JOptionPane.CANCEL_OPTION ) return;
            }
        }
        if ( file!=null && watcher!=null ) {
            try {
                watcher.close();
            } catch (IOException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
        try {
            Document d = panel.getEditorPanel().getDocument();
            d.remove(0, d.getLength());
            panel.containsTabs= false;
            panel.setDirty(false);
            panel.setFilename(null);
            annotationsSupport.clearAnnotations();
            this.file= null;
        } catch (BadLocationException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }
    
    private JComponent getRecentAccessory( final String filter, final int limit, final JFileChooser c ) {
        JPanel recentPanel= new JPanel(new BorderLayout());

        final String msgWait= "Getting Recent...                    ..";
        DefaultListModel waitModel= new DefaultListModel();
        waitModel.add(0,msgWait);
        
        final JList p= new JList(waitModel);
        p.setFont( p.getFont().deriveFont(10.f) );
        
        Runnable run= new Runnable() {
            @Override
            public void run() {
                Map<String,String> recent= model.getRecent(filter,10*limit);

                final DefaultListModel mm= new DefaultListModel();
                
                List<String> ss= new ArrayList( recent.keySet() );
                int count=0;
                for ( int i=ss.size()-1; i>0; i-- ) {
                    String s= ss.get(i);
                    if ( s.startsWith("script:") ) s= s.substring(7);
                    if ( s.startsWith("vap+jyds:") ) s= s.substring(9);
                    if ( s.startsWith("vap+jy:") ) s= s.substring(7);
                    int iscript= s.indexOf("script=");
                    if ( iscript>-1 ) {
                        s= s.substring(iscript+"script=".length());
                    }
                    if ( s.startsWith("file:") ) {
                        if ( s.startsWith("file://") ) s= s.substring(7);
                        if ( s.startsWith("file:") ) s= s.substring(5);
                        int iq= s.indexOf('?');
                        if ( iq>-1 ) {
                            s= s.substring(0,iq);
                        }
                        if ( mm.contains(s) ) {
                            mm.removeElement(s);
                        }
                        mm.addElement(s);
                        count++;
                        if ( count==limit ) break;
                    }
                }
                
                Runnable run= new Runnable() {
                    @Override
                    public void run() {
                        p.setModel( mm );
                    }
                };
                SwingUtilities.invokeLater(run);
            }
        } ;
        new Thread(run).start();
        p.addListSelectionListener( new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if ( !e.getValueIsAdjusting() ){
                    if ( p.getSelectedValue().equals(msgWait) ) {
                        
                    } else {
                        String s=  (String)p.getSelectedValue();
                        File ff= new File(s); //TODO: still can't figure out mac bug where select doesn't work
                        c.setSelectedFile( ff );
                    }
                }
            }
        } );
        
        
        JScrollPane scrollPane= new JScrollPane(p);
        //p.setPreferredSize( new Dimension(300,300) );
        //p.setMinimumSize( new Dimension(300,300) );
        scrollPane.setPreferredSize( new Dimension(300,200) );
        scrollPane.setMinimumSize( new Dimension(300,100) );
        scrollPane.setMaximumSize( new Dimension(300,200) );       
        
        recentPanel.add( new JLabel("Recently used local ("+filter+") files:"), BorderLayout.NORTH );
        recentPanel.add( scrollPane, BorderLayout.CENTER );
        return recentPanel;
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

            Preferences prefs = AutoplotSettings.settings().getPreferences(ScriptPanelSupport.class);
            String openFile = prefs.get(PREFERENCE_OPEN_FILE, "");

            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode( JFileChooser.FILES_ONLY );
            chooser.setFileFilter(getFileFilter());
            if (file != null) {
                chooser.setSelectedFile(file);
            }
            if (openFile.length() > 0) {
                // this hangs on Java 1.7.0_17 Linux Centos 6:
                chooser.getFileSystemView().isParent(chooser.getCurrentDirectory(),new File(openFile));
                File fopenFile= new File(openFile);
                chooser.setSelectedFile(fopenFile);
                // I have to hope that it was an NFS problem we were having.  I don't see the problem on Ubuntu or Macs.
            }
            
            chooser.setAccessory( getRecentAccessory("*.jy*", RECENT_FILES_COUNT,chooser) );
            chooser.revalidate();
            
            int r = chooser.showOpenDialog(panel);
            if (r == JFileChooser.APPROVE_OPTION) {
                file = chooser.getSelectedFile();
                prefs.put(PREFERENCE_OPEN_FILE, file.toString());
                loadFile(file);
                panel.setFilename(file.toString());
                annotationsSupport.clearAnnotations();
            }

        } catch (IOException ex) {
            model.getExceptionHandler().handle(ex);
        }
    }

    /**
     * interrupt the running script.
     */
    void interrupt() {
        InteractiveInterpreter interp= getInterruptible();
        if ( interp!=null ) {
            interp.interrupt( ts );
        }
    }

    public static final String PROP_INTERRUPTABLE= "interruptable";
    
    /**
     * return the interruptible InteractiveInterpreter that runs the script.
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

    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

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

