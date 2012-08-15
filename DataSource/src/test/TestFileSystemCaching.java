/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test;

import ftpfs.FTPBeanFileSystemFactory;
import org.das2.util.filesystem.FileObject;
import org.das2.util.filesystem.FileSystem;
//import org.das2.util.filesystem.KeyChain;

/**
 *
 * @author jbf
 */
public class TestFileSystemCaching {
    public static void main( String[] args ) throws Exception {

        FileSystem.registerFileSystemFactory( "ftp", new FTPBeanFileSystemFactory() );
        
        FileSystem fs= FileSystem.create( "ftp://jbf@localhost/tmp/testData/");
        FileObject fo= fs.getFileObject( "data_2010_03_02_v1.00.qds" );
        System.err.println( fo.getClass() );
        System.err.println( fo.lastModified() );
        System.err.println( fo.getSize() );

       //KeyChain.getDefault().writeKeysFile();

        fs.listDirectory("/a");
        fo= fs.getFileObject("/a/afile.txt");
        System.err.println( fo.lastModified() );
        System.err.println( fo.getSize() );

        fs.listDirectory("/a");
        fo= fs.getFileObject("/a/afile.txt");
        System.err.println( fo.lastModified() );
        System.err.println( fo.getSize() );

        fs= FileSystem.create( "http://autoplot.org/data/");
        fo= fs.getFileObject( "autoplot.cdf" );
        System.err.println( fo.getClass() );
        System.err.println( fo.lastModified() );
        System.err.println( fo.getSize() );


    }
}
