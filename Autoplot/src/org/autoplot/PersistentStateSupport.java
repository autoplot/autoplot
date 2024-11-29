
package org.autoplot;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.components.DasProgressPanel;
import org.das2.dasml.SerializeUtil;
import org.das2.dasml.DOMBuilder;
import org.das2.graph.DasCanvas;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
//import org.apache.xml.serialize.OutputFormat;
//import org.apache.xml.serialize.XMLSerializer;
import org.das2.util.filesystem.FileSystem;
import org.autoplot.dom.Application;
import org.autoplot.state.EmbedDataExperiment;
import org.autoplot.datasource.AutoplotSettings;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author Jeremy
 */
public class PersistentStateSupport {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.dom");

    /**
     * the extension of the format, like .vap.
     */
    String ext;
    
    private String currentFile;
    JMenu openRecentMenu;
    SerializationStrategy strategy;
    Component component;
    
    private JMenuItem saveMenuItem;
    private JLabel currentFileLabel;
    private List recentFiles;
    
    /** state has been modified and needs to be saved */
    private boolean dirty;
    
    public static final String PROPERTY_OPENING="opening";
    public static final String PROPERTY_SAVING="saving";
    public static final String PROPERTY_DIRTY="dirty";
    public static final String PROPERTY_CURRENT_FILE="currentFile";
    
    private static final String PREF_DIR = "PersistentStateSupportDir";
    private static final String PREF_FILE = "PersistentStateSupport";

    public interface SerializationStrategy {
        // give me a document to serialize
        public Element serialize( Document document, ProgressMonitor monitor ) throws IOException;
        
        // here's a document you gave me
        public void deserialize( Document doc, ProgressMonitor monitor );
    }
    
    private static SerializationStrategy getCanvasStrategy( final DasCanvas canvas ) {
        return new SerializationStrategy() {
            @Override
            public Element serialize(Document document, ProgressMonitor monitor) {
                DOMBuilder builder= new DOMBuilder( canvas );
                Element element= builder.serialize( document, DasProgressPanel.createFramed("Serializing Canvas") );
                return element;
            }
            
            @Override
            public void deserialize(Document document, ProgressMonitor monitor) {
                Element element= document.getDocumentElement();
                SerializeUtil.processElement(element,canvas );
            }
        };
    }
    
    
    /**
     *  Provides a means for saving the application persistently, undo/redo support (TODO).
     *  canvas is the canvas to be serialized, extension identifies the application.  Note that
     *  internal changes to das may break saved files.
     * @param canvas used for dialogs
     * @param extension
     */
    public PersistentStateSupport( DasCanvas canvas, String extension ) {
        this( canvas, getCanvasStrategy( canvas ), extension );
        
    }
    
    private void refreshRecentFilesMenu() {
        if ( openRecentMenu!=null ) {
            openRecentMenu.removeAll();
            for ( int i=0; i<recentFiles.size(); i++ ) {
                final File f= (File) recentFiles.get(i);
                Action a= new AbstractAction( String.valueOf(f) ) {
                    @Override
                    public void actionPerformed( ActionEvent e ) {
                        open(f);
                    }
                };
                openRecentMenu.add( a );
            }
        }
    }
    
    private void setRecentFiles( String code ) {
        recentFiles= new ArrayList();
        if ( code.equals("") ) return;
        String[] ss= code.split("::");
        for (String s : ss) {
            File f = new File(s);
            if ( !recentFiles.contains(f) ) {
                recentFiles.add( f );
            }
        }
        refreshRecentFilesMenu();
    }
    
    private String getRencentFilesString() {
        if (recentFiles.isEmpty() ) {
            return "";
        } else {
            StringBuilder result= new StringBuilder( String.valueOf( recentFiles.get(0) ) );
            for ( int i=1; i<recentFiles.size(); i++ ) {
                result.append("::").append( String.valueOf(recentFiles.get(i)) );
            }
            return result.toString();
        }
    }
    
    /**
     * 
     * @param parent used for dialogs, and if an AutoplotUI, then status messages are returned.
     * @param strategy plug-in for converting to XML
     * @param extension the extention without a period (e.g. vap).
     */
    public PersistentStateSupport( Component parent, SerializationStrategy strategy, String extension ) {
        this.strategy= strategy;
        this.ext= "."+extension;
        this.component= parent;
        Preferences prefs= AutoplotSettings.settings().getPreferences(PersistentStateSupport.class);
        setDirectory( "" );
        String recentFileString= prefs.get( PREF_FILE+ext+"_recent", "" );
        setRecentFiles( recentFileString );
    }
    
    /**
     * Create an action which will trigger the save as dialog.  This is
     * used to create a "save as" button.
     * @return the action
     */
    public Action createSaveAsAction() {
        return new AbstractAction("Save As...") {
            @Override
            public void actionPerformed( ActionEvent e ) {
                saveAs();
            }
        };
    }

    /**
     * Save the state to the file selected by a JFileChooser.
     * @return same as jFileChooser.showSaveDialog(), for example:
     *    JFileChooser.CANCEL_OPTION   means the option was canceled.
     *    JFileChooser.APPROVE_OPTION  means the file was saved.
     *    Note this is not the same as  JOptionPane.CANCEL_OPTION!
     */
    public int saveAs() {
        JFileChooser chooser= new JFileChooser();
        if ( getDirectory()!=null && getDirectory().length()>0 ) chooser.setCurrentDirectory( new File( getDirectory() ) );
        if ( getCurrentFile()!=null ) {
            File child= new File( getCurrentFile() );
            File parent= FileSystem.settings().getLocalCacheDir();
            try {
                if ( child.getCanonicalPath().startsWith(parent.getCanonicalPath())) {
                    child= new File( getDirectory(), child.getName() );
                }
                chooser.setSelectedFile( child );
            } catch (IOException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }

        VapSchemeChooser vapVersion= new VapSchemeChooser();
        chooser.setAccessory( vapVersion );
        
        chooser.setFileFilter( new FileNameExtensionFilter( "*"+ext, ext.substring(1) ) );
        int result= chooser.showSaveDialog(this.component);
        if ( result==JFileChooser.APPROVE_OPTION ) {
            String lext= ext;
        
            if ( vapVersion.isEmbedData() ) {
                lext= ".zip";
            }
        
            File f= chooser.getSelectedFile();
            if ( !f.getName().endsWith(lext) ) f= new File( f.getPath()+lext );
            setCurrentFile(f.toString());
            setCurrentFileOpened(true);
            if ( saveMenuItem!=null ) saveMenuItem.setText("Save");
            if ( currentFileLabel!=null ) currentFileLabel.setText( String.valueOf( getCurrentFile()) );
            addToRecent(getCurrentFile());

            String v= vapVersion.getScheme();
            Map<String,Object> optionsMap= new HashMap<>();
            if ( vapVersion.isEmbedData() ) optionsMap.put(EMBED_DATA, true );
            if ( vapVersion.isOnlyEmbedLocal() ) optionsMap.put(ONLY_EMBED_LOCAL, true );
            if ( vapVersion.isLocalPwdReferences() ) optionsMap.put(LOCAL_PWD_REFERENCES, true );
            
            if ( v.length()>0 ) {
                save(new File( getCurrentFile()), v, optionsMap );
            } else {
                save(new File( getCurrentFile()), "", optionsMap );
            }
        }

        return result;
        
    }
    public static final String EMBED_DATA = "embedData";
    public static final String ONLY_EMBED_LOCAL = "onlyEmbedLocal";
    public static final String LOCAL_PWD_REFERENCES = "localPwdReferences";
    
    /**
     * This is typically overriden, but an implementation is provided.
     * @param f the file
     * @param scheme the scheme, as in v1.7
     * @param options additional options, which are ignored in this implementation.
     * @throws java.lang.Exception
     */
    protected void saveImpl( File f, String scheme, Map<String,Object> options ) throws Exception {
        try (OutputStream out = new FileOutputStream( f )) {

            Document document= DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

            Element element= strategy.serialize( document, DasProgressPanel.createFramed("Serializing") );

            document.appendChild( element );

            DOMImplementationLS ls = (DOMImplementationLS)
                            document.getImplementation().getFeature("LS", "3.0");
            LSOutput output = ls.createLSOutput();
            output.setEncoding("UTF-8");
            output.setByteStream(out);
            LSSerializer serializer = ls.createLSSerializer();

            try {
                if (serializer.getDomConfig().canSetParameter("format-pretty-print", Boolean.TRUE)) {
                    serializer.getDomConfig().setParameter("format-pretty-print", Boolean.TRUE);
                }
            } catch (Error e) {
                // Ed's nice trick for finding the implementation
                //String name = serializer.getClass().getSimpleName();
                //java.net.URL u = serializer.getClass().getResource(name+".class");
                //System.err.println(u);
                e.printStackTrace();
            }
            serializer.write(document, output);
        }
    }
    
    /**
     * <ul>
     * <li> embedData -- Boolean.TRUE means create a zip and embed the data
     * <li> onlyEmbedLocal -- Boolean.TRUE means only embed local files, because remote files can be loaded on other computers.
     * </ul>
     * @param file the file
     * @param scheme the version of .vap to create
     * @param saveOptions map containing options
     */
    private void save( final File file, final String scheme, Map<String,Object> saveOptions ) {
        setSaving(true);
        Runnable run= () -> {
            try {
                File f= file;
                if ( component instanceof AutoplotUI ) {
                    ((AutoplotUI)component).setStatus("busy: writing to "+file);
                }
                
                if ( saveOptions.containsKey(EMBED_DATA) ) {
                    if ( !f.getName().endsWith("zip") ) f= new File( f.getPath()+"zip" );
                } else {
                    if ( !f.getName().endsWith(ext) ) f= new File( f.getPath()+ext );
                }
                
                if ( f.exists() ) {
                    if ( !f.canWrite() ) {
                        throw new IOException("Unable to write to file: "+f );
                    }
                } else {
                    String osName = AutoplotUtil.getProperty("os.name", "applet");
                    if ( !f.getParentFile().canWrite() && !osName.startsWith("Windows") ) { //bug 3099076
                        throw new IOException("Unable to write to directory: "+f.getParentFile() );
                    }
                }
                
                boolean embedData= Boolean.TRUE.equals(saveOptions.get(EMBED_DATA) );
                if ( embedData ) {
                    if ( component instanceof AutoplotUI ) {
                        Application dom= ((AutoplotUI)component).getDom();
                        EmbedDataExperiment.save(dom, f, Boolean.TRUE.equals(saveOptions.get(ONLY_EMBED_LOCAL)) );
                    } else {
                        throw new IllegalArgumentException("Unable to embed data, please submit this error so the problem can be corrected");
                    }
                } else {
                    saveImpl(f,scheme,saveOptions);
                }
                
                Preferences prefs= AutoplotSettings.settings().getPreferences( AutoplotSettings.class);
                prefs.put( AutoplotSettings.PREF_LAST_OPEN_VAP_FILE, new File( getCurrentFile() ).getAbsolutePath() );
                prefs.put( AutoplotSettings.PREF_LAST_OPEN_VAP_FOLDER, new File( getCurrentFile() ).getParent() );
                setSaving( false );
                setDirty( false );
                if ( component instanceof AutoplotUI ) {
                    ((AutoplotUI)component).setStatus("wrote "+file);
                }
                update();
            } catch ( IOException ex ) {
                String mess;
                if ( ex.getMessage().equals("") ) mess= ex.toString(); else mess= ex.getMessage();
                JOptionPane.showMessageDialog( PersistentStateSupport.this.component, mess, "I/O Error", JOptionPane.WARNING_MESSAGE );
            } catch ( ParserConfigurationException ex ) {
                throw new RuntimeException(ex);
            } catch ( Exception ex) {
                throw new RuntimeException(ex);
            }
        };
        new Thread( run, "PersistentStateSupport.save" ).start();
    }
    
    public Action createSaveAction() {
        return new AbstractAction("Save") {
            @Override
            public void actionPerformed( ActionEvent e ) {
                if ( getCurrentFile()==null ) {
                    saveAs();
                } else {
                    try {
                        File child = new File(getCurrentFile());
                        File parent = FileSystem.settings().getLocalCacheDir();
                        if (child.getCanonicalPath().startsWith(parent.getCanonicalPath())) {
                            child = new File(getDirectory(), child.getName());
                            setCurrentFile(child.toString());
                            saveAs();
                        } else {
                            save(new File(getCurrentFile()),"", Collections.emptyMap());
                        }
                    } catch (IOException ex) {
                        logger.log(Level.SEVERE, ex.getMessage(), ex);
                    }
                }
            }
        };
    }
    
    public JMenuItem createSaveMenuItem() {
        saveMenuItem= new JMenuItem(createSaveAction());
        if (getCurrentFile()!=null ) {
            saveMenuItem.setText("Save");
        }
        return saveMenuItem;
    }
    
    public JMenu createOpenRecentMenu() {
        JMenu menu= new JMenu("Open Recent");
        menu.add( String.valueOf(getCurrentFile()) );
        openRecentMenu= menu;
        refreshRecentFilesMenu();
        return menu;
    }
    
    public JLabel createCurrentFileLabel() {
        currentFileLabel= new JLabel( "                  " );
        return currentFileLabel;
    }
    
    private static Document readDocument( File file ) throws IOException, ParserConfigurationException, SAXException {
        InputStream in= new FileInputStream(file);
        InputSource source = new InputSource();
        source.setCharacterStream(new InputStreamReader(in));
        DocumentBuilder builder;
        DocumentBuilderFactory domFactory= DocumentBuilderFactory.newInstance();
        builder = domFactory.newDocumentBuilder();
        builder.setErrorHandler(null);
        Document document= builder.parse(source);
        return document;
    }
    
    private void addToRecent( String file ) {
        if ( recentFiles.contains(file) ) return;
        recentFiles.add(0,file);
        while (recentFiles.size()>7) {
            recentFiles.remove(7);
        }
        Preferences prefs= Preferences.userNodeForPackage(PersistentStateSupport.class);
        prefs.put( PREF_FILE+ext+"_recent", getRencentFilesString() );
        refreshRecentFilesMenu();
    }
    
    public Action createOpenAction() {
        return new AbstractAction("Open...") {
            @Override
            public void actionPerformed( ActionEvent ev ) {
                try {
                    JFileChooser chooser = new JFileChooser();
                    if ( getCurrentFile()!=null ) 
                        chooser.setCurrentDirectory(new File(getCurrentFile()).getParentFile());
                    chooser.setFileFilter( new FileNameExtensionFilter( "*"+ext, ext.substring(1) ) );
                    int result = chooser.showOpenDialog(component);
                    if (result == JFileChooser.APPROVE_OPTION) {
                        open( chooser.getSelectedFile() );
                        addToRecent(getCurrentFile());
                        if ( saveMenuItem!=null ) saveMenuItem.setText("Save");
                    }
                } catch ( Exception e ) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
    
    /**
     * Open the file.  This uses a provided strategy to read in the file.  This is
     * often overriden.  If open fails, throw an exception.
     * @param file the file to open
     * @throws java.lang.Exception
     */
    protected void openImpl( File file ) throws Exception {
        Document document= readDocument( file );
        strategy.deserialize( document, DasProgressPanel.createFramed("deserializing") );
    }
    
    private void open( final File file ) {
        setOpening( true );
        Runnable run = new Runnable() {
            @Override
            public void run() {
                try {
                    if ( !file.exists() ) {
                        JOptionPane.showMessageDialog(component,"File not found: "+file, "File not found", JOptionPane.WARNING_MESSAGE );
                        return;
                    }
                    openImpl(file);
                    setOpening( false );
                    setDirty(false);
                    setCurrentFile(file.toString());
                    setCurrentFileOpened(true);
                    update();
                } catch ( IOException e ) {
                    throw new RuntimeException(e);
                } catch ( ParserConfigurationException e ) {
                    throw new RuntimeException(e);
                } catch ( SAXException e ) {
                    throw new RuntimeException(e);
                } catch ( Exception e ) {
                    throw new RuntimeException(e);
                }
            }
        };
        new Thread( run, "PersistentStateSupport.open" ).start();
    }
    
    /**
     * reset the current file so it is null.  We call this for example when
     * we are finished with the file and would start a new file without a name.
     */
    public void close() {
        setCurrentFile(null);
    }

    public void markDirty() {
        this.setDirty( true );
        update();
    }
    
    private void update() {
        if ( currentFileLabel!=null ) this.currentFileLabel.setText( getCurrentFile() + ( dirty ? " *" : "" ) );
    }
    /** Creates a new instance of PersistentStateSupport */
    public PersistentStateSupport() {
    }

    /**
     * Utility field used by bound properties.
     */
    private final java.beans.PropertyChangeSupport propertyChangeSupport =  new java.beans.PropertyChangeSupport(this);

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
     * Getter for property dirty.
     * @return Value of property dirty.
     */
    public boolean isDirty() {
        return this.dirty;
    }

    /**
     * Setter for property dirty.
     * @param dirty New value of property dirty.
     */
    public void setDirty(boolean dirty) {
        boolean oldDirty = this.dirty;
        this.dirty = dirty;
        propertyChangeSupport.firePropertyChange ( PROPERTY_DIRTY, oldDirty, dirty);
    }

    protected String directory = null;
    public static final String PROP_DIRECTORY = "directory";

    public final String getDirectory() {
        //propertyChangeSupport.firePropertyChange ( PROPERTY_CURRENT_FILE, oldFile, currentFile );
        if ( directory==null || directory.equals("") ) {
            Preferences prefs= Preferences.userNodeForPackage(PersistentStateSupport.class);
            String f= prefs.get( PREF_FILE+ext+"_recent", "" );
            if ( f.length()>0 ) {
                String[] ss= f.split("::");
                f= ss[0];
                File ff= new File(f);
                directory= ff.getParent();
            }
        }
        return directory;
    }

    public final void setDirectory(String directory) {
        String oldDirectory = this.directory;
        this.directory = directory;
        propertyChangeSupport.firePropertyChange(PROP_DIRECTORY, oldDirectory, directory);
    }

    /**
     * return the current file, which will be a reference to the local file system.
     * @return
     */
    public String getCurrentFile() {
        if ( currentFile==null ) return null;
        if ( currentFile.startsWith("file://") ) { 
            return currentFile.substring(7);
        } else if ( currentFile.startsWith("file:") ) {
            return currentFile.substring(5);
        } else {
            return currentFile;
        }
    }

    /**
     * set the current file, which could be file:///home/... or /home/...
     * if null, then no file is currently opened.
     * @param currentFile
     */
    public void setCurrentFile(String currentFile) {
        String oldFile = this.currentFile;
        this.currentFile = currentFile;
        if ( currentFile==null ) {
            setCurrentFileOpened(false);
        }
        propertyChangeSupport.firePropertyChange ( PROPERTY_CURRENT_FILE, oldFile, currentFile );
    }

    /**
     * Holds value of property loading.
     */
    private boolean opening;

    /**
     * Property loading is true when a load operation is being performed.
     * @return Value of property loading.
     */
    public boolean isOpening() {
        return this.opening;
    }

    /**
     * Holds value of property saving.
     */
    private boolean saving;

    /**
     * Property saving is true when a save operation is being performed.
     * @return Value of property saving.
     */
    public boolean isSaving() {
        return this.saving;
    }

    private void setOpening(boolean b) {
        boolean old= this.opening;
        this.opening= b;
        propertyChangeSupport.firePropertyChange( PROPERTY_OPENING, Boolean.valueOf(old), Boolean.valueOf(b) );
                
    }


    private void setSaving(boolean b) {
        boolean old= this.saving;
        this.saving= b;
        propertyChangeSupport.firePropertyChange( PROPERTY_SAVING, Boolean.valueOf(old), Boolean.valueOf(b) );
                
    }

    /**
     * Holds value of property currentFileOpened.
     */
    private boolean currentFileOpened;

    /**
     * Property currentFileOpened indicates if the current file has ever been opened.  This
     * is to handle the initial state where the current file is set, but should not be
     * displayed because it has not been opened.
     * @return Value of property currentFileOpened.
     */
    public boolean isCurrentFileOpened() {
        return this.currentFileOpened;
    }

    /**
     * Setter for property currentFileOpened.
     * @param currentFileOpened New value of property currentFileOpened.
     */
    public void setCurrentFileOpened(boolean currentFileOpened) {
        boolean oldCurrentFileOpened = this.currentFileOpened;
        this.currentFileOpened = currentFileOpened;
        propertyChangeSupport.firePropertyChange ("currentFileOpened", oldCurrentFileOpened, currentFileOpened);
    }

}
