/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.wgetfs;

import java.io.FileNotFoundException;
import java.net.URI;
import java.net.UnknownHostException;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.FileSystemFactory;

/**
 * Implemented to relieve all the annoying http problems we see at LANL.  If
 * the system is a Mac, then curl is used, otherwise wget is used.  Either
 * way, if the system environment property AP_WGET, WGET, AP_CURL, CURL is set
 * then the implied executable is used.
 * 
 * @author jbf
 */
public class WGetFileSystemFactory implements FileSystemFactory {

    /**
     * support curl too, since it is on macs by default.
     */
    protected static boolean useCurl= true;
    
    /**
     * the executable to spawn.  See createFileSystem for use of AP_WGET or AP_CURL environment variables.
     */
    protected static String exe=null;
    
    @Override
    public FileSystem createFileSystem(URI root) throws FileSystem.FileSystemOfflineException, UnknownHostException, FileNotFoundException {
        synchronized ( FileSystem.class ) {
            if ( exe==null ) {
                useCurl= false;
                exe= System.getProperty("AP_WGET");
                if ( exe==null || exe.length()==0 ) {
                    exe= System.getProperty("AP_CURL");
                    if ( exe!=null && exe.length()>0 ) useCurl= true;
                } 
                if ( exe==null || exe.length()==0 ) {
                    if ( System.getProperty("os.name").startsWith("Mac") ) {
                        useCurl= true;
                        exe= "curl";
                    } else {
                        useCurl= false;
                        exe= "wget";
                    }
                }
            }
        }
        
        return WGetFileSystem.createWGetFileSystem(root);
    }
    
    
}
