/*
 * FileSystemUtil.java
 *
 * Created on December 13, 2007, 6:59 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.datasource;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.logging.Logger;
import org.das2.util.FileUtil;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.FileSystem.FileSystemOfflineException;
import org.das2.util.monitor.ProgressMonitor;

/**
 * More abstract filesystem functions.
 * @author jbf
 */
public class FileSystemUtil {
    
    /**
     * return the resource name within a filesystem
     * @param fs the filesystem, e.g. http://autoplot.org/data/
     * @param resource http://autoplot.org/data/autoplot.dat
     * @return autoplot.dat
     */
    private static final Logger logger= LoggerManager.getLogger("apdss.util.fsutil");
    
    public static String getNameRelativeTo( FileSystem fs, String resource ) {
        String s= fs.getRootURI().toString();
        //TODO:
        //return DataSetURI.toUri(resource).relativize(fs.getRootURI()).toString();
        if ( resource.startsWith(s) ) return resource.substring(s.length()); else return resource;
    }

    /**
     * return true if the possibleParent is a valid folder tree root, and maybeChild exists within tree.
     * @param possibleParent parent file.
     * @param maybeChild a file or folder which may exist within possibleParent.
     * @return true if possibleParent is a folder containing
     */
    public static boolean isChildOf( File possibleParent, File maybeChild ) {
        return FileUtil.isParent(possibleParent, maybeChild);
    }
    
    /**
     * checks to see if the resource uri appears to represent an existing
     * data source.  false indicates that the resource is known to not exist.
     * true indicates that the resource does exist.
     *
     * @param suri URI, such as http://server.org/data/asciitable.dat
     * @return true of the resource exists and can be downloaded.
     */
    public static boolean resourceExists( String suri ) throws FileSystemOfflineException, UnknownHostException, URISyntaxException {
        URISplit split= URISplit.parse(suri);
        try {
            FileSystem fs= FileSystem.create( DataSetURI.toUri( split.path ) );
            if ( fs.getFileObject(split.file.substring(split.path.length())).exists() ) {
                return true;
            } else {
                return false;
            }
        } catch ( IllegalArgumentException ex ) {
            return false;
        } catch ( FileNotFoundException ex ) {
            return false;
        }
    }

    /**
     * have the filesystem download the resource, without having to worry about
     * creating a FileSystem just to get the one file.
     *
     * @param suri URI, such as http://server.org/data/asciitable.dat
     * @param mon progress monitor
     * @return
     */
    public static File doDownload(String suri,ProgressMonitor mon) throws FileSystemOfflineException, IOException, URISyntaxException  {
        URISplit split= URISplit.parse(suri);
        FileSystem fs= FileSystem.create( DataSetURI.toUri( split.path ) );
        File result= fs.getFileObject(split.file.substring(split.path.length())).getFile(mon);
        return result;
    }

    /**
     * returns true if the resource is already in a local cache.
     * @param suri the URI containing a file resource.
     * @return
     * @throws org.das2.util.filesystem.FileSystem.FileSystemOfflineException
     */
    public static boolean resourceIsLocal(String suri) throws FileSystemOfflineException, UnknownHostException, FileNotFoundException {
        URISplit split= URISplit.parse(suri);
        FileSystem fs= FileSystem.create( DataSetURI.toUri( split.path ) );
        return fs.getFileObject(split.file.substring(split.path.length())).isLocal();
    }

    /**
     * returns true if the string identifies a file resource (not a folder).
     * @param suri
     * @return true if the resource is a file.
     * @throws org.das2.util.filesystem.FileSystem.FileSystemOfflineException
     * @throws UnknownHostException
     * @throws URISyntaxException
     * @throws FileNotFoundException 
     */
    protected static boolean resourceIsFile(String suri) throws FileSystemOfflineException, UnknownHostException, URISyntaxException, FileNotFoundException {
        URISplit split= URISplit.parse(suri);
        FileSystem fs= FileSystem.create( DataSetURI.toUri( split.path ) );
        if ( fs.getFileObject(split.file.substring(split.path.length())).isData() ) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * return true if the resource is local, and can therefore be trusted.  
     * This was introduced to secure the server, where an Autoplot there could be 
     * used to access local resources.  This returns true for file:/ references.
     * Note this will also return true for sftp since the reference 
     * may utilize keys private to the server.  Note too that a .jyds script
     * that is not local could attempt to use local resources.  For this reason
     * there is JythonDataSourceFactory.hasLocalReferences.
     * 
     * "upload" is another magic element.  Paths containing upload are considered 
     * remote.
     * 
     * @param file an Autoplot URI.
     * @return true if the uri is a reference to a local resource.
     */
    public static boolean isLocalResource(String file) {
        if ( file.trim().length()==0 ) return false;
        URISplit split= URISplit.parse(file);
        if ( split.path==null ) return false;
        URI resource= DataSetURI.toUri( split.path );
        String scheme= resource.getScheme();
        if ( scheme.equals("file") || scheme.equals("sftp") ) {
            if ( resource.getPath().contains( FileSystem.settings().getLocalCacheDir().toString() ) ) {
                logger.info( "path within local cache dir is considered non-local" );
                return false;
            }
            if ( resource.getPath().contains("upload") ) {
                logger.info( "path containing upload is considered non-local" );
                return false;
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Interface to be used with deleteFilesInTree.  
     */
    public static interface Check {
        /**
         * simply return true or false.  With deleteFilesInTree, true would
         * indicate that the file should be deleted.
         * @param f a file.
         * @return boolean, to be interpreted by another code.
         */
        boolean check( File f );
    }
    
    /**
     * deletes all files where shouldDelete returns true and empty 
     * folders below root, and root.
     * @param root the root of the tree to start searching.  If root does not exist, return true!
     * @param shouldDelete return true if the file should be deleted.
     * @throws IllegalArgumentException if it is unable to delete a file
     * @return true if the operation was successful.
     */
    public static boolean deleteFilesInTree( File root, Check shouldDelete ) throws IllegalArgumentException {
        if (!root.exists()) return true;
        if (!root.canRead()) throw new IllegalArgumentException("cannot read folder: "+root );
        File[] children = root.listFiles(); // root is known to exist.
        boolean success = true;
        boolean notEmpty= children.length>0;
        for (int i = 0; i < children.length; i++) {
            if (children[i].isDirectory()) {
                success = success && deleteFilesInTree(children[i],shouldDelete);
            } else {
                if ( shouldDelete.check(children[i]) ) {
                    success = success && ( !children[i].exists() || children[i].delete() );
                    if (!success) {
                        throw new IllegalArgumentException("unable to delete file " + children[i]);
                    }
                }
            }
        }
        
        if ( notEmpty && root.listFiles().length==0 ) {
            success = success && (!root.exists() || root.delete());
        } else if (  root.listFiles().length==0 ) {
            success = success && (!root.exists() || root.delete());
        }
        
        if (!success) {
            throw new IllegalArgumentException("unable to delete folder " + root);
        }
        
        return success;
    }
    
}
