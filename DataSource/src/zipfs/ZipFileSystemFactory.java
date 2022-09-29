package zipfs;

import java.io.IOException;
import java.net.URI;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.FileSystem.FileSystemOfflineException;
import org.das2.util.filesystem.FileSystemFactory;

/**
 * creates a ZipFileSystem
 * @author ed
 */
public class ZipFileSystemFactory implements FileSystemFactory {

    public ZipFileSystemFactory() {
    }
 
    @Override
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
