/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot;

import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.datasource.DataSetSelector;
import org.das2.util.AboutUtil;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.datasource.TimeRangeEditor;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.WindowManager;
import org.autoplot.scriptconsole.AppScriptPanelSupport;
import org.das2.datum.DatumRange;

/**
 *
 * @author jbf
 */
public class Util {

    public static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot");

    private static String tagName(String svnURL, Map<String, String> abbrevs) {
        if ( svnURL==null ) return "untagged";
        String root= svnURL;
        int i = root.lastIndexOf('/');
        if (i == -1) {
            return root;
        }
        int i2= root.lastIndexOf('/',i-1);
        String dir = root.substring(i2+1,i);
        root = root.substring(0, i2+1);
        String abbrev = abbrevs.get(root);
        if (abbrev == null) {
            int j = abbrevs.size() + 1;
            abbrevs.put(root, "[" + j + "]");
            abbrev = abbrevs.get(root);
        }
        return abbrev + " "+ dir;
    }

    private static String popDollarValue(String propval) {
        int i = propval.indexOf('$');
        if (i == -1) {
            return "";
        }
        i = propval.indexOf(':', i + 1);
        int i2 = propval.indexOf('$', i + 1);
        if (i2 == -1) {
            return "";
        }
        return propval.substring(i + 1, i2).trim();
    }

    private static String svnTag( String urlprop, String revprop, String jarName ) {
        if ( urlprop==null || revprop==null ) return null;
        String suffix = "src/META-INF/build.txt";
        urlprop = popDollarValue(urlprop);
        revprop = popDollarValue(revprop);
        if ( urlprop.length()==0 || revprop.length()==0 ) return null;
        if (urlprop.endsWith(suffix)) {
            urlprop = urlprop.substring(0, urlprop.length() - suffix.length());
        }
        jarName= jarName + "/";
        if ( urlprop.endsWith(jarName) ) urlprop= urlprop.substring(0, urlprop.length() - jarName.length() ) ;
        
        return urlprop + "@" + revprop;
    }

    /**
     * return the build time an an ISO8601 time, or "????" if it is unknown.
     * Note question marks are used intentionally so sloppy codes can assume
     * that ???? means the current version of the code built in a debugger.
     * @return ISO8601 time in $Y-$m-$dT$H:$MZ or "????"
     * @see org.autoplot.jythonsupport.Util#requireAutoplotScriptingVersion()
     */
    public static String getBuildTime() {
        String buildTime = "????";
        java.net.URL buildURL = AboutUtil.class.getResource("/buildTime.txt");
        if (buildURL != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(buildURL.openStream()))) {
                buildTime = reader.readLine();
            } catch (IOException ex) {
                logger.log( Level.WARNING, ex.getMessage(), ex );
            }
        }
        return buildTime;
    }
    
    /**
     * searches class path for META-INF/version.txt, returns nice strings
     * @return one line per jar
     * @throws java.io.IOException
     */
    public static List<String> getBuildInfos() throws IOException {
        Enumeration<URL> urls = Util.class.getClassLoader().getResources("META-INF/build.txt");

        List<String> result = new ArrayList<>();

        String buildTime= getBuildTime();
        result.add( "build time: " + buildTime );
                
        LinkedHashMap<String, String> abbrevs = new LinkedHashMap<>();

        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();

            String jar = url.toString();

            int i = jar.indexOf(".jar");
            int i0 = jar.lastIndexOf('/', i - 1);

            String name;
            String jarname;
            if (i != -1) {
                name = jar.substring(i0 + 1, i + 4);
                jarname= name.substring(0,name.length()-4);
            } else {
                name = jar.substring(6);
                jarname= name;
            }

            Properties props = new Properties();
            props.load(url.openStream());

            String svnTag = svnTag( props.getProperty("build.svnurl"), 
                    props.getProperty("build.svnrevision"),
                    jarname );

            String tagName = tagName(svnTag, abbrevs);

            result.add(name + ": " + tagName + " (" + props.getProperty("build.timestamp") + " " + props.getProperty("build.user.name") + ")");

        }

        for ( Entry<String,String> val : abbrevs.entrySet()) {
            result.add("" + val.getValue() + " " + val.getKey() );
        }

        return result;

    }

    /**
     * deletes all files and folders below root, and root, just as "rm -r" would.
     * TODO: check links
     * @param root the root of the tree.
     * @throws IllegalArgumentException if it is unable to delete a file
     * @return true if the operation was successful.
     * @see #pruneFileTree(java.io.File, java.util.List) which probably does the same thing.
     */
    public static boolean deleteFileTree(File root) throws IllegalArgumentException {
        if (!root.exists()) {
            return true;
        }
        if ( !root.canRead() ) throw new IllegalArgumentException("cannot read root: "+root );
        if ( !root.isDirectory() ) throw new IllegalArgumentException("root should be directory: " +root );
        File[] children = root.listFiles();
        if ( children==null ) return true;
        boolean success = true;
        for (File children1 : children) {
            if (children1.isDirectory()) {
                success = success && deleteFileTree(children1);
            } else {
                success = success && (!children1.exists() || children1.delete());
                if (!success) {
                    throw new IllegalArgumentException("unable to delete file " + children1);
                }
            }
        }
        success = success && (!root.exists() || root.delete());
        if (!success) {
            throw new IllegalArgumentException("unable to delete folder " + root);
        }
        return success;
    }

    /**
     * copy a file.  This will probably always returns true or an exception, but check the status to be sure.
     * @param srcf the source file
     * @param dstf the destination file
     * @return true if successful.
     * @throws IOException 
     */
    public static boolean copyFile( File srcf, File dstf ) throws IOException {
        WritableByteChannel dest = Channels.newChannel(new FileOutputStream(dstf));
        ReadableByteChannel src = Channels.newChannel(new FileInputStream(srcf));
        DataSourceUtil.transfer(src, dest);
        return true;
    }
    
    /**
     * copy a branch of files and folders.
     * @param root root folder.
     * @param dst destination folder.
     * @return true if successful.
     */
    public static boolean copyFileTree( File root, File dst ) {
        return copyFileTree(root, dst, 0, new NullProgressMonitor() );
    }

    /**
     * copy a branch of files and folders.
     * @param root root folder.
     * @param dst destination folder.
     * @param mon progress monitor, or null.
     * @return true if successful.
     */
    public static boolean copyFileTree( File root, File dst, ProgressMonitor mon ) {
        return copyFileTree(root, dst, 0, mon );
    }
    
    /**
     * copy a branch of files and folders.
     * @param root root folder.
     * @param dst destination folder.
     * @param depth so that progress depth can be limited.
     * @param mon progress monitor, or null.
     * @return true if successful.
     */
    public static boolean copyFileTree( File root, File dst, int depth, ProgressMonitor mon ) {
        try {
            String roots= root.getCanonicalPath()+"/";
            String dsts= dst.getCanonicalPath()+"/";
            if ( dsts.startsWith(roots ) ) {
                throw new IllegalArgumentException("can't move files into child of original");
            }
            if ( roots.startsWith(dsts ) ) {
                throw new IllegalArgumentException("can't move files to parent of original");
            }
        } catch (IOException ex ) {
            throw new RuntimeException(ex);
        }

        if (!root.exists()) {
            return false; // doesn't exist
        }
        if ( !root.canRead() ) throw new IllegalArgumentException("cannot read root: "+root );
        if ( !root.isDirectory() ) throw new IllegalArgumentException("root should be directory: " +root );
        File[] children = root.listFiles();
        boolean success = true;

        if ( !dst.exists() && !dst.mkdirs() ) {
            throw new IllegalArgumentException( "unable to make directory "+dst );
        }
        
        if ( mon!=null ) mon.setTaskSize(children.length*10);
        int i=0;
        for (File child : children) {
            if ( mon!=null ) {
                mon.setTaskProgress(i);
                if ( mon.isCancelled() ) return false;
            }
            if (child.isDirectory()) {
                if ( depth<3 && mon!=null ) {
                    success = success && copyFileTree( child, new File(dst, child.getName()), depth+1, 
                            mon.getSubtaskMonitor(i*10,(i+1)*10,child.getName()));
                } else {
                    success = success && copyFileTree( child, new File(dst, child.getName()), depth+1, 
                            null );
                }
                if (!success) {
                    copyFileTree(child, new File(dst, child.getName()));
                    throw new IllegalArgumentException("unable to move file " + child);
                }
            } else {
                try {
                    success = success && Util.copyFile(child, new File(dst, child.getName()));
                    if (!success) {
                        throw new IllegalArgumentException("unable to move file " + child);
                    }
                } catch (IOException ex) {
                    IllegalArgumentException ex2 = new IllegalArgumentException("unable to move file " + child, ex);
                    throw ex2;
                }
            }
            i=i+1;
        }
        return success;
    }
    
    /**
     * remove empty branches from file tree.  This is like "rm -r $root"
     * @param root the root directory from which to start a search.
     * @param problems any files which could not be deleted are listed here.
     * @return true if successful.
     * @see #deleteFileTree(java.io.File) which probably does the same thing.
     */
    public static boolean pruneFileTree( File root, List<String> problems ) {
        if (!root.exists()) {
            return false; // doesn't exist
        }
        if ( !root.canRead() ) throw new IllegalArgumentException("cannot read root: "+root );
        if ( !root.isDirectory() ) throw new IllegalArgumentException("root should be directory: " +root );
        File[] children = root.listFiles();
        if ( children==null ) return false;
        
        boolean success = true;

        for (File children1 : children) {
            if (children1.isDirectory()) {
                success = success && pruneFileTree(children1, problems);
            }
        }
        
        if ( children.length==0 ) {
            if ( !root.delete() ) {
                problems.add( "unable to delete "+root );
                return false;
            } else {
                return true;
            }
        } else {
            return true;
        }

    }
    

    /**
     * this will be replaced in Java 8.
     * @param c
     * @param delim
     * @return 
     */
    public static String strjoin(Collection<String> c, String delim) {
        StringBuilder result = new StringBuilder();
        for (String s : c) {
            if (result.length() > 0) {
                result.append(delim);
            }
            result.append(s);
        }
        return result.toString();
    }

//    public static void main(String[] args) throws IOException {
//        copyFileTree( new File("/home/jbf/temp/foo"), new File("/home/jbf/temp/foo2/" ));
//        getBuildInfos();
//    }
    
    /**
     * this will add the Scheme and other named fonts found in the /resources/ folder.
     */
    public static void addFonts() {
        Class c= Util.class;
   
        String[] ss= new String[] { "/resources/Roboto-Regular.ttf", 
            "/resources/ArchitectsDaughter.ttf",
            "/resources/xkcd-script.ttf",
            "/resources/scheme_bk.otf" };
        for ( String s: ss ) {
            try ( InputStream in = c.getResourceAsStream(s) ) {
                if ( in!=null ) {
                    Font font = Font.createFont(Font.TRUETYPE_FONT, in );
                    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                    ge.registerFont(font);
                }
            } catch (FontFormatException | IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }
    
    
    /**
     * Use a native browser to pick a file.  Use the system property fileChooserNative=true
     * @param parent parent component for focus.  If a dataSetSelector is
     * used then its timerange is used for the initial timerange.
     * This will now allow a non-local vap to be browsed as well.
     * @param initialSelection if non-null, then the initial selection.
     * @return the URI for the vap file, or null if cancel was pressed.
     * @return 
     */
    public static String browseLocalJyNative( java.awt.Component parent, String initialSelection) {
        Preferences prefs = AutoplotSettings.settings().getPreferences(AppScriptPanelSupport.class);
        String PREFERENCE_OPEN_FILE = "openFile";
        
        String currentFile=  prefs.get( PREFERENCE_OPEN_FILE, "" );
        String currentDirectory = null;
        if ( !currentFile.equals("") ) {
            currentDirectory= new File(currentFile).getParentFile().getAbsolutePath();
        }
        
        FileDialog chooser=
                new java.awt.FileDialog( (Frame)(SwingUtilities.getWindowAncestor(parent)), 
                        "Open .vap file", java.awt.FileDialog.LOAD );
        chooser.setName("openVapNative");
        chooser.setDirectory(currentDirectory);
        chooser.setFile(currentFile);
        //WindowManager.getInstance().recallWindowSizePosition(chooser);
        FileFilter ff;
        ff = new FileNameExtensionFilter( ".jy files", "jy" );

        chooser.setFilenameFilter((File dir, String name) -> ff.accept( new File( dir, name ) ));
        chooser.setVisible(true);

        String result=null;

        chooser.dispose();
        String file = chooser.getFile();
        if ( file!=null ) {
            String dir = chooser.getDirectory();
            prefs.put(AutoplotSettings.PREF_LAST_OPEN_VAP_FOLDER, dir );
            try {
                result= new File( dir, file ).getCanonicalPath();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
        return result;

    }

    /**
     * Show a file chooser component, and return the name of a .vap file.
     * @param parent parent component for focus.  If a dataSetSelector is
     * used then its timerange is used for the initial timerange.
     * This will now allow a non-local vap to be browsed as well.
     * @param initialSelection if non-null, then the initial selection.
     * @return the URI for the vap file, or null if cancel was pressed.
     */
    public static String browseLocalJy( java.awt.Component parent, String initialSelection) {
        Preferences prefs = AutoplotSettings.settings().getPreferences(AppScriptPanelSupport.class);
        File userHome= new File(System.getProperty("user.home"));
        String PREFERENCE_OPEN_FILE = "openFile";
        String openFile= prefs.get(PREFERENCE_OPEN_FILE, "");
                
        String currentDirectory = null;
        if ( !openFile.equals("") ) {
            currentDirectory= new File(openFile).getParentFile().getAbsolutePath();
        }
        String currentFile=  prefs.get( PREFERENCE_OPEN_FILE, "" );

        boolean isRemote= initialSelection!=null && ( initialSelection.startsWith("https:/") 
                || initialSelection.startsWith("http:/")
                || initialSelection.startsWith("ftp:/") 
                || initialSelection.startsWith("sftp:/") );
        boolean isLocal= initialSelection==null || initialSelection.isEmpty() || !isRemote;
        
        JFileChooser chooser;

        chooser= new JFileChooser();

        WindowManager.getInstance().recallWindowSizePosition(chooser);

        try {
            if ( currentDirectory!=null ) {
                chooser.setCurrentDirectory( new File( currentDirectory ) );
            }
        } catch ( SecurityException ex ) {
            logger.info("unable to set current directory");
        }

        if ( currentFile.length()>0 ) {
            try {
                chooser.setSelectedFile( new File( currentFile ) );
            } catch ( SecurityException ex ) {
                logger.info("unable to set current file");
            }
        }
        if ( initialSelection!=null && isLocal ) {
            URISplit split= URISplit.parse(initialSelection);
            if ( split.file!=null && "jy".equals(split.ext) ) {
                chooser.setSelectedFile( new File( split.file ));
            }
        }
        
        FileFilter ff;
        ff = new FileNameExtensionFilter( ".jy files", "jy" );

        chooser.addChoosableFileFilter(ff);
        chooser.setFileFilter(ff);

        Window w= parent==null ? null : SwingUtilities.getWindowAncestor( parent);
        int result = chooser.showOpenDialog(w);
        if (result == JFileChooser.APPROVE_OPTION) {
            prefs.put(AutoplotSettings.PREF_LAST_OPEN_VAP_FOLDER, chooser.getSelectedFile().getParent() );
            WindowManager.getInstance().recordWindowSizePosition(chooser);
            return chooser.getSelectedFile().toURI().toString();
        } else {
            return null;
        }
        
    }    
}
