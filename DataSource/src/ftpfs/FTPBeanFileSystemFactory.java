/*
 * FTPBeanFileSystemFactory.java
 *
 * Created on November 15, 2007, 9:39 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ftpfs;

import java.net.URI;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.FileSystemFactory;

/**
 *
 * @author jbf
 */
public class FTPBeanFileSystemFactory implements FileSystemFactory {
    
    /** Creates a new instance of FTPBeanFileSystemFactory */
    public FTPBeanFileSystemFactory() {
    }

    public FileSystem createFileSystem(URI root) throws FileSystem.FileSystemOfflineException {
        return new FTPBeanFileSystem(root);
    }
    
}
