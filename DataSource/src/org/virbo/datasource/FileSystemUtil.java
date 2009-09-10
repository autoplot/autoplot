/*
 * FileSystemUtil.java
 *
 * Created on December 13, 2007, 6:59 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.datasource;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
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
        //return new URI(resource).relativize(fs.getRootURI()).toString();
        if ( resource.startsWith(s) ) return resource.substring(s.length()); else return resource;
    }

    /**
     * checks to see if the context uri appears to represent an existing
     * data source.  false indicates that the resource is known to not exist.
     * true indicates that the resource does exist.
     *
     * @param context URI, such as http://server.org/data/asciitable.dat
     * @return
     */
    public static boolean resourceExists( String context ) throws FileSystemOfflineException, URISyntaxException {
        URLSplit split= URLSplit.parse(context);
        FileSystem fs= FileSystem.create( new URI( split.path ) );
        if ( fs.getFileObject(split.file.substring(split.path.length())).exists() ) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * have the filesystem download the resource.
     *
     * @param context URI, such as http://server.org/data/asciitable.dat
     * @return
     */
    public static void doDownload(String context,ProgressMonitor mon) throws FileSystemOfflineException, IOException, URISyntaxException  {
        URLSplit split= URLSplit.parse(context);
        FileSystem fs= FileSystem.create( new URI( split.path ) );
        fs.getFileObject(split.file.substring(split.path.length())).getFile(mon);
    }

    /**
     * returns true if the resource is already in a local cache.
     * TODO: applet support, we want to avoid multiple downloads.
     * @param context
     * @return
     * @throws org.das2.util.filesystem.FileSystem.FileSystemOfflineException
     * @throws java.net.MalformedURLException
     */
    static boolean resourceIsLocal(String context) throws FileSystemOfflineException, URISyntaxException {
        URLSplit split= URLSplit.parse(context);
        FileSystem fs= FileSystem.create( new URI( split.path ) );
        if ( fs.getFileObject(split.file.substring(split.path.length())).isLocal() ) {
            return true;
        } else {
            return false;
        }
    }

    static boolean resourceIsFile(String context) throws FileSystemOfflineException, URISyntaxException {
        URLSplit split= URLSplit.parse(context);
        FileSystem fs= FileSystem.create( new URI( split.path ) );
        if ( fs.getFileObject(split.file.substring(split.path.length())).isData() ) {
            return true;
        } else {
            return false;
        }
    }


    
}
