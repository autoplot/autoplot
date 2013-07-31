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
 * Implemented to relieve all the annoying http problems we see at LANL.
 * @author jbf
 */
public class WGetFileSystemFactory implements FileSystemFactory {

    @Override
    public FileSystem createFileSystem(URI root) throws FileSystem.FileSystemOfflineException, UnknownHostException, FileNotFoundException {
        return WGetFileSystem.createWGetFileSystem(root);
    }
    
    
}
