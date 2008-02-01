/*
 * FTPBeanFileSystemFactory.java
 *
 * Created on November 15, 2007, 9:39 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ftpfs;

import edu.uiowa.physics.pw.das.util.fileSystem.FileSystem;
import edu.uiowa.physics.pw.das.util.fileSystem.FileSystemFactory;
import java.net.URL;

/**
 *
 * @author jbf
 */
public class FTPBeanFileSystemFactory implements FileSystemFactory {
    
    /** Creates a new instance of FTPBeanFileSystemFactory */
    public FTPBeanFileSystemFactory() {
    }

    public FileSystem createFileSystem(URL root) throws FileSystem.FileSystemOfflineException {
        return new FTPBeanFileSystem(root);
    }
    
}
