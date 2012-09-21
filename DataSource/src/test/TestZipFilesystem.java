/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.FileSystem.FileSystemOfflineException;
import zipfs.ZipFileSystemFactory;

/**
 *
 * @author jbf
 */
public class TestZipFilesystem {
    public static void main(String[] args ) throws FileSystemOfflineException, UnknownHostException, URISyntaxException, IOException {
        FileSystem.registerFileSystemFactory("zip",new ZipFileSystemFactory());
        String ff= "ftp://ftp.virbo.org/obrien/scatha/cdf/scatha_high_res/shr79037v01.cdf.zip/";
        FileSystem fs= FileSystem.create( new URI( ff ) );
        String[] ss= fs.listDirectory("/");
        System.err.println(""+ss); // logger okay

    }
}
