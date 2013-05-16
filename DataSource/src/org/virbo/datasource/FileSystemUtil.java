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
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.FileSystem.FileSystemOfflineException;
import org.das2.util.monitor.ProgressMonitor;

/**
 *
 * @author jbf
 */
public class FileSystemUtil {
    
    public static String getNameRelativeTo( FileSystem fs, String resource ) {
        String s= fs.getRootURI().toString();
        //TODO:
        //return DataSetURI.toUri(resource).relativize(fs.getRootURI()).toString();
        if ( resource.startsWith(s) ) return resource.substring(s.length()); else return resource;
    }

    /**
     * return true if the possibleParent is a valid folder tree root, and maybeChild exists within tree.
     * @param possibleParent
     * @param maybeChild a file or folder which may exist within possibleParent.
     * @return true if possibleParent is a folder containing
     */
    public static boolean isChildOf( File possibleParent, File maybeChild ) {
        possibleParent= possibleParent.getAbsoluteFile();
        if ( !possibleParent.exists() || !possibleParent.isDirectory() ) {
            // this cannot possibly be the parent
            return false;
        }
        maybeChild= maybeChild.getAbsoluteFile();
        URI parentURI = possibleParent.toURI(),
        childURI = maybeChild.toURI();
        return !parentURI.relativize(childURI).isAbsolute();
    }
    
    /**
     * checks to see if the context uri appears to represent an existing
     * data source.  false indicates that the resource is known to not exist.
     * true indicates that the resource does exist.
     *
     * @param context URI, such as http://server.org/data/asciitable.dat
     * @return
     */
    public static boolean resourceExists( String context ) throws FileSystemOfflineException, UnknownHostException, URISyntaxException {
        URISplit split= URISplit.parse(context);
        try {
            FileSystem fs= FileSystem.create( DataSetURI.toUri( split.path ) );
            if ( fs.getFileObject(split.file.substring(split.path.length())).exists() ) {
                return true;
            } else {
                return false;
            }
        } catch ( FileNotFoundException ex ) {
            return false;
        }
    }

    /**
     * have the filesystem download the resource, without having to worry about
     * creating a FileSystem just to get the one file.
     *
     * @param context URI, such as http://server.org/data/asciitable.dat
     * @return
     */
    public static File doDownload(String context,ProgressMonitor mon) throws FileSystemOfflineException, IOException, URISyntaxException  {
        URISplit split= URISplit.parse(context);
        FileSystem fs= FileSystem.create( DataSetURI.toUri( split.path ) );
        File result= fs.getFileObject(split.file.substring(split.path.length())).getFile(mon);
        return result;
    }

    /**
     * returns true if the resource is already in a local cache.
     * TODO: applet support, we want to avoid multiple downloads.
     * @param context
     * @return
     * @throws org.das2.util.filesystem.FileSystem.FileSystemOfflineException
     * @throws java.net.MalformedURLException
     */
    protected static boolean resourceIsLocal(String context) throws FileSystemOfflineException, UnknownHostException, URISyntaxException, FileNotFoundException {
        URISplit split= URISplit.parse(context);
        FileSystem fs= FileSystem.create( DataSetURI.toUri( split.path ) );
        if ( fs.getFileObject(split.file.substring(split.path.length())).isLocal() ) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * returns true if the string identifies a file resource (not a folder).
     * @param context
     * @return true if the resource is a file.
     * @throws org.das2.util.filesystem.FileSystem.FileSystemOfflineException
     * @throws UnknownHostException
     * @throws URISyntaxException
     * @throws FileNotFoundException 
     */
    protected static boolean resourceIsFile(String context) throws FileSystemOfflineException, UnknownHostException, URISyntaxException, FileNotFoundException {
        URISplit split= URISplit.parse(context);
        FileSystem fs= FileSystem.create( DataSetURI.toUri( split.path ) );
        if ( fs.getFileObject(split.file.substring(split.path.length())).isData() ) {
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
     * @param root the root of the tree to start searching.
     * @param shouldDelete return true if the file should be deleted.
     * @throws IllegalArgumentException if it is unable to delete a file
     * @return true if the operation was successful.
     */
    public static boolean deleteFilesInTree( File root, Check shouldDelete ) throws IllegalArgumentException {
        if (!root.exists()) {
            return true;
        }
        File[] children = root.listFiles();
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
