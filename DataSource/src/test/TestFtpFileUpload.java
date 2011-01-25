/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test;

import ftpfs.FTPBeanFileSystem;
import ftpfs.FTPBeanFileSystemFactory;
import ftpfs.FtpFileObject;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Date;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.FileSystem.FileSystemOfflineException;

/**
 *
 * @author jbf
 */
public class TestFtpFileUpload {
    public static void main( String[] args ) throws FileSystemOfflineException, UnknownHostException, URISyntaxException, IOException {
        FileSystem.registerFileSystemFactory( "ftp", new FTPBeanFileSystemFactory() );
        FileSystem fs= FileSystem.create( new URI( "ftp://papco@mrfrench.lanl.gov/autoplot/") );
        FtpFileObject fo= (FtpFileObject) fs.getFileObject( "testUpload.txt" );
        OutputStream out= fo.getOutputStream(false);

        PrintStream pw= new PrintStream(out);
        pw.println( new Date() );
        pw.close();
        
    }
}
