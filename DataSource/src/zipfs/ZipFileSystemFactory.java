package zipfs;

import java.io.IOException;
import java.net.URL;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.FileSystemFactory;

/**
 *
 * @author ed
 */
public class ZipFileSystemFactory implements FileSystemFactory {

    public ZipFileSystemFactory() {
    }
 
    public FileSystem createFileSystem(URL root) throws FileSystem.FileSystemOfflineException {
        FileSystem zfs = null;
        try {
            zfs = new ZipFileSystem(root);
        } catch (IOException ex) {
            // Have to catch this here because FileSystemFactory.createFileSystem doesn't throw IOException
            System.err.println("Error encountered opening zip file:");
            ex.printStackTrace();
        }
        return zfs;
    }

}
