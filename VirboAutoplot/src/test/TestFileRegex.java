/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test;

import java.io.File;
import java.net.URI;
import org.das2.datum.DatumRangeUtil;
import org.das2.fsm.FileStorageModel;
import org.das2.util.filesystem.FileSystem;

/**
 * This demos a problem where we can't change filesystem types mid-way through
 * the stream, when creating FileStorageModel.  Note there is
 * no ZipFileSystem when we print the list of parent models.
 *
 * @author jbf
 */
public class TestFileRegex {
    public static void main( String[] args ) throws Exception {
        //String ss= "ftp://virbo.org/POES/n15/$Y/poes_n15_$Y$m$d.cdf.zip/poes_n15_$Y$m$d.cdf?minute&timerange=1998-07-01";
        //String ss= "ftp://virbo.org/POES/n15/1998/poes_n15_19980701.cdf.zip/poes_n15_19980701.cdf?minute";

        FileSystem fs = FileSystem.create( new URI( "ftp://virbo.org/POES/n15" ) );
        FileStorageModel fsm= FileStorageModel.create( fs, "%Y/poes_n15_%Y%m%d.cdf.zip/poes_n15_%Y%m%d.cdf" );

        FileStorageModel t= fsm;
        
        System.err.println("Here is the bug, we never use ZipFileSystem:");
        while ( t!=null ) {
            System.err.println( t.getFileSystem().getClass().toString() + "\t" + t  );
            t= t.getParent();
        }


        File[] files= fsm.getBestFilesFor( DatumRangeUtil.parseTimeRangeValid("1998-07-01"));
        for ( File f: files ) {
            System.err.println(f.toString());
        }
    }
}
