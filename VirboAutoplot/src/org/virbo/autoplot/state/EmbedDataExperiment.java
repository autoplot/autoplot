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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.NullProgressMonitor;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.DataSourceFilter;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.URISplit;

/**
 * Embed data and vap in a zip file.  Now that we have PWD, this will
 * be straight-forward.
 * @author jbf
 */
public class EmbedDataExperiment {
    
    private static final Logger logger= LoggerManager.getLogger("autoplot");
    
    private static void writeToZip( ZipOutputStream out, String name, File f ) throws FileNotFoundException, IOException {
        ZipEntry e= new ZipEntry( name ) ;
        FileChannel ic=null;
        try {
            ic = new FileInputStream(f).getChannel();
            out.putNextEntry(e);

            byte[] bbuf= new byte[2048];
            ByteBuffer buf= ByteBuffer.wrap(bbuf);
            int c;
            while ( (c=ic.read(buf))>0 ) {
                out.write( bbuf, 0, c);
                buf.flip();
            }
            out.closeEntry();
        } finally {
            if ( ic!=null ) ic.close();
        }
    }
    
    /**
     * stream that forwards data on to another stream (out) that 
     * is not closed when this stream is closed.
     */
    private static class NoCloseOutputStream extends OutputStream {
        OutputStream out;
        
        NoCloseOutputStream( OutputStream out ) {
            this.out= out;
        }
        
        @Override
        public void write(int b) throws IOException {
            out.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }
    }
    
    /**
     * remove double slashes from file part of URI.
     * @param uri
     * @return 
     */
    private static URI makeCanonical( URI uri ) {
        try {
            String path= uri.getPath();
            String [] pp= path.split("/");
            StringBuilder np= new StringBuilder();
            for ( String p: pp ) {
                if ( p.length()>0 ) {
                    np.append("/");
                    np.append(p);
                }
            }
            path= np.toString();
            return new URI( uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), path, uri.getQuery(), uri.getFragment() );
        } catch (URISyntaxException ex) {
            Logger.getLogger(EmbedDataExperiment.class.getName()).log(Level.SEVERE, null, ex);
            return uri;
        }
    }
    
    /**
     * return a list of all the resources used in the DOM.
     * @param dom
     * @return 
     */
    private static Set<URI> getResources( Application dom ) {
        Set<URI> result= new HashSet();
        for ( DataSourceFilter dsf: dom.getDataSourceFilters() ) {
            String suri = dsf.getUri();
            URISplit split= URISplit.parse(suri);
            if ( split.resourceUri!=null ) {
                URI uri= makeCanonical( split.resourceUri );
                if ( DataSetURI.isAggregating( uri.toString() ) ) {
                    try {
                        String [] rr= DataSetURI.unaggregate( uri.toASCIIString(), dom.getTimeRange() );
                        for ( String r: rr ) {
                            try {
                                result.add( new URI( r ) );
                            } catch (URISyntaxException ex) {
                                logger.log(Level.SEVERE, ex.getMessage(), ex);
                            }
                        }
                    } catch (FileSystem.FileSystemOfflineException ex) {
                        logger.log(Level.SEVERE, ex.getMessage(), ex);
                    } catch (UnknownHostException ex) {
                        logger.log(Level.SEVERE, ex.getMessage(), ex);
                    } catch (IOException ex) {
                        logger.log(Level.SEVERE, ex.getMessage(), ex);
                    }
                } else {
                    result.add( uri );
                }
            }
        }
        return result;
    }
    
    /**
     * save the application, but embed data file resources within the 
     * zip, along with the .vap.  The vap is saved with the name default.vap.
     * 
     * @param dom3 the state to save.
     * @param f the zip file output name.
     * @throws FileNotFoundException
     * @throws IOException 
     */
    public static void save( Application dom3, File f ) throws FileNotFoundException, IOException {
        // too bad I have to do this...  but it doesn't work otherwise...
        Application dom = dom3.getController().getApplicationModel().createState(false);

        FileOutputStream fout= new FileOutputStream(f);
        ZipOutputStream out=null;
        try {
            out= new ZipOutputStream( fout );
            for ( URI uri: getResources(dom) ) {
                String name;
                if ( uri.getScheme().equals("file")) {
                   name= uri.toString().replaceAll(":///","/");
                   name= name.replaceAll(":/", "/" );
                } else {
                   name= uri.toString().replaceAll("://","/");     
                }
                name= name.replaceAll("//","/");
                File file1= DataSetURI.getFile(uri,new NullProgressMonitor());
                writeToZip( out, name, file1 );
            }
            for ( DataSourceFilter dsf: dom.getDataSourceFilters() ) {
                String uri = dsf.getUri();
                URISplit split= URISplit.parse(uri);
                if ( split.resourceUri!=null ) {
                    String name;
                    if ( split.resourceUri.getScheme().equals("file")) {
                        name= split.resourceUri.toString().replaceAll(":///","/");
                        name= name.replaceAll(":/", "/" );
                    } else {
                        name= uri.replaceAll("://","/");  
                    }
                    name= name.replaceAll("//","/");
                    split.file= "%{PWD}/"+name;
                    dsf.setUri( URISplit.format(split) );
                }
            }
            ZipEntry e= new ZipEntry("default.vap");
            out.putNextEntry(e);
            StatePersistence.saveState( new NoCloseOutputStream(out), dom, "" );
        } finally {
            if ( out!=null ) {
                try {
                    out.closeEntry();
                } finally {
                    out.close();
                }
            }
            fout.close();
        }
    }
}
