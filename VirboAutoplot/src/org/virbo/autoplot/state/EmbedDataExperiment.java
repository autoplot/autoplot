/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.state;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.das2.util.monitor.NullProgressMonitor;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.DataSourceFilter;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.URISplit;

/**
 * Embed data and vap in a zip file.  Now that we have PWD, this will
 * be straight-forward
 * @author jbf
 */
public class EmbedDataExperiment {
    
    private static void writeToZip( ZipOutputStream out, String name, File f ) throws FileNotFoundException, IOException {
        ZipEntry e= new ZipEntry( name ) ;
        FileChannel ic = new FileInputStream(f).getChannel();
        out.putNextEntry(e);
        
        byte[] bbuf= new byte[2048];
        ByteBuffer buf= ByteBuffer.wrap(bbuf);
        int c;
        while ( (c=ic.read(buf))>0 ) {
            out.write( bbuf, 0, c);
            buf.flip();
        }
        out.closeEntry();
    }
    
    public static class NoCloseOutputStream extends OutputStream {
        OutputStream out;
        
        NoCloseOutputStream( OutputStream out ) {
            this.out= out;
        }
        
        @Override
        public void write(int b) throws IOException {
            out.write(b);
        }
        
    }
    
    public static void save( Application dom3, File f ) throws FileNotFoundException, IOException {
        // too bad I have to do this...  but it doesn't work otherwise...
        Application dom = dom3.getController().getApplicationModel().createState(false);

        FileOutputStream fout= new FileOutputStream(f);
        final ZipOutputStream out = new ZipOutputStream( fout );
        for ( DataSourceFilter dsf: dom.getDataSourceFilters() ) {
            String uri = dsf.getUri();
            URISplit split= URISplit.parse(uri);
            if ( split.resourceUri!=null ) {
                //TODO: aggregations...
                String name= split.resourceUri.toString().replaceAll("://","/");
                File file1= DataSetURI.getFile(split.resourceUri,new NullProgressMonitor());
                writeToZip( out, name, file1 );
                dsf.setUri("%{PWD}/"+name+"?"+split.params);
            }
        }
        ZipEntry e= new ZipEntry("default.vap");
        out.putNextEntry(e);
        StatePersistence.saveState( new NoCloseOutputStream(out), dom, "" );
        out.closeEntry();
        out.close();
    }
}
