/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test;

import ftpfs.FTPBeanFileSystemFactory;
import java.net.URI;
import java.util.Arrays;
import org.das2.util.filesystem.FileSystem;

/**
 * Seth logs into lanl by logging into his ftp server with papco@stevens.lanl.gov, and it forwards the commands.  We need
 * to support "user@host" for the username.
 * @author jbf
 */
public class TestFtpForward {
    public static void main( String[] args ) throws Exception {

        FileSystem fs= FileSystem.create( new URI( "http://jbf@foo:@sarahandjeremy.net/~jbf/lanl/vap/") );
        String[] ss= fs.listDirectory("/");
        System.err.println(Arrays.asList(ss));

        FileSystem.registerFileSystemFactory( "ftp", new FTPBeanFileSystemFactory() );
        fs= FileSystem.create( new URI( "ftp://jbf@foo:@192.168.0.205/temp/") );
        ss= fs.listDirectory("/");

    }
}
