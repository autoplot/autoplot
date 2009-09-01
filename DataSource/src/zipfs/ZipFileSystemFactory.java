package zipfs;

import java.io.IOException;
import java.net.URI;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.FileSystem.FileSystemOfflineException;
import org.das2.util.filesystem.FileSystemFactory;

/**
 *
 * @author ed
 */
public class ZipFileSystemFactory implements FileSystemFactory {

    public ZipFileSystemFactory() {
    }
 
    public FileSystem createFileSystem(URI root) throws FileSystem.FileSystemOfflineException {
        FileSystem zfs = null;
        try {
            zfs = new ZipFileSystem(root);
        } catch (IOException ex) {
            throw new FileSystemOfflineException(ex);
        }
        return zfs;
    }

}
