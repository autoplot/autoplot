/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.state;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.NullProgressMonitor;
import org.autoplot.dom.Application;
import org.autoplot.dom.DataSourceFilter;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.URISplit;
import org.autoplot.dom.Plot;
import org.das2.qstream.SimpleStreamFormatter;
import org.das2.qstream.StreamException;
import org.das2.qstream.StreamTool;

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
    
    private static void writeToZip( ZipOutputStream out, String name, InputStream in ) throws FileNotFoundException, IOException {
        ZipEntry e= new ZipEntry( name ) ;
        ReadableByteChannel ic=null;
        try {
            ic = Channels.newChannel(in);
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
     * this is a kludgy, where there is no way to see if a uri has a resource
     * because das2server URIs have the address of the server as the resource.
     * TODO: The data source should really be the one who answers the question.
     * @param split the pre-parsed uri.
     * @return true if the URI has a resource that needs to be embedded.
     */
    private static boolean hasNoResource( URISplit split ) {
        return split.resourceUri==null || "vap+das2server".equalsIgnoreCase(split.vapScheme);
    }
    
    /**
     * return a list of all the resources used in the DOM.
     * TODO: this might run a script to resolve the resources it loads.
     * @param dom
     * @return 
     */
    private static Set<URI> getResources( Application dom ) {
        Set<URI> result= new HashSet();
        for ( DataSourceFilter dsf: dom.getDataSourceFilters() ) {
            String suri = dsf.getUri();
            maybeAddResource( suri, dom, result );
        }
        for ( Plot p: dom.getPlots() ) {
            String s= p.getTicksURI();
            maybeAddResource( s, dom, result );
        }
        String s= dom.getEventsListUri();
        maybeAddResource( s, dom, result );
        return result;
    }

    private static boolean maybeAddResource(String suri, Application dom, Set<URI> result) {
        if ( suri.trim().length()==0 ) return false;
        URISplit split= URISplit.parse(suri);
        if (split.resourceUri!=null) {
            URI uri= makeCanonical( split.resourceUri );
            if (hasNoResource( split )) {
                return false;
            }
            if ( DataSetURI.isAggregating( uri.toString() ) ) {
                try {
                    String [] rr= DataSetURI.unaggregate( uri.toString(), dom.getTimeRange() );
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
        return true;
    }
    
    /**
     * make a relative name from the resource URI.  This should not contain
     * the "vap+cdf:" part!
     * @param uri the resource location, like "http://autoplot.org/data/autoplot.cdf"
     * @return the name, like "http/autoplot.org/data/autoplot.cdf"
     */
    private static String makeRelativeName( String commonPath, URI uri ) {
        String name;
        if ( uri.getScheme().equals("file")) {
            name= uri.getPath();
            if ( name.startsWith(commonPath) ) {
                name= uri.getScheme() + "/"+ name.substring(commonPath.length());
            } else {
                name= uri.toString().replaceAll(":///","/");
                name= name.replaceAll(":/", "/" );
            }
        } else {
           name= uri.toString().replaceAll("://","/");     
        }   
        name= name.replaceAll("//","/");
        return name;
    }
    
    /**
     * return true if the URI is only resolved on the local machine.
     * @param uri the uri
     * @return true if the URI is only resolved on the local machine.
     */
    public static boolean isLocal( URI uri ) {
        return uri.getScheme().equals("file");
    }
    
    /**
     * save the application, but embed data file resources within the 
     * zip, along with the .vap.  The vap is saved with the name default.vap.
     * When the data source contains a dataset that was created internally (with
     * the Jython plot command, for example), it will be formatted as a QStream and 
     * embedded within the vap.
     * 
     * @param dom3 the state to save.
     * @param f the zip file output name.
     * @throws FileNotFoundException
     * @throws IOException 
     */
    public static void save( Application dom3, File f ) throws FileNotFoundException, IOException {
        save( dom3, f, false );
    }    
    /**
     * save the application, but embed data file resources within the 
     * zip, along with the .vap.  The vap is saved with the name default.vap.
     * When the data source contains a dataset that was created internally (with
     * the Jython plot command, for example), it will be formatted as a QStream and 
     * embedded within the vap.
     * 
     * @param dom3 the state to save.
     * @param f the zip file output name.
     * @param onlyLocal if true, then don't embed data from remote references.
     * @throws FileNotFoundException
     * @throws IOException 
     */
    public static void save( Application dom3, File f, boolean onlyLocal ) throws FileNotFoundException, IOException {        
        // too bad I have to do this...  but it doesn't work otherwise...
        Application dom = dom3.getController().getApplicationModel().createState(false);
        QDataSet[] datasets= new QDataSet[dom.getDataSourceFilters().length];
        for ( int i=0; i<datasets.length; i++ ) {
            if ( dom3.getDataSourceFilters(i).getUri().equals("vap+internal:") ) {
                datasets[i]= dom3.getDataSourceFilters(i).getController().getDataSet();
            }
        }
        
        // try to find common path for local file references, so local references aren't embedded.
        Set<URI> uris= getResources(dom);
        
        if ( onlyLocal ) {
            Set<URI> localUris= new HashSet<>();
            for ( URI uri : uris ) {
                if ( isLocal(uri) ) {
                    localUris.add(uri);
                }
            }
            uris= localUris;
        }
        
        String commonPath= null;
        for ( URI uri: uris ) {
            if ( uri.getScheme().equals("file") ) {
                if ( commonPath==null ) {
                    commonPath= uri.getPath();
                } else {
                    String path= uri.getPath();
                    int i;
                    for ( i=0; i<Math.min(commonPath.length(),path.length()); i++ ) {
                        if ( path.charAt(i)!=commonPath.charAt(i) ) {
                            break;
                        }
                    }
                    commonPath= commonPath.substring(0,i);
                }
            }
        }
        if ( commonPath!=null && !commonPath.endsWith("/") ) { // bug 1669
            int i= commonPath.lastIndexOf('/');
            if ( i>-1 ) {
                commonPath= commonPath.substring(0,i+1);
            }
        }
        
        FileOutputStream fout= new FileOutputStream(f);
        ZipOutputStream out=null;
        try {
            out= new ZipOutputStream( fout );
            for ( URI uri: uris ) { // resolve aggregations, etc.
                String name= makeRelativeName(commonPath,uri);
                File file1= DataSetURI.getFile(uri,new NullProgressMonitor());
                writeToZip( out, name, file1 );
            }
            int nameGenCount= 0; // for automatically named data files.
            int dsfCount= 0;
            for ( DataSourceFilter dsf: dom.getDataSourceFilters() ) {
                String uri = dsf.getUri();
                URISplit split= URISplit.parse(uri);
                if ( uri.trim().length()>0 && !hasNoResource(split) ) {
                    String name= makeRelativeName(commonPath,split.resourceUri);
                    split.file= "%{PWD}/"+name;
                    dsf.setUri( URISplit.format(split) );
                } else if ( uri.equals("vap+internal:") ) {
                    QDataSet ds= datasets[dsfCount];
                    logger.log(Level.FINE, "automatically embedding internal data as qds: {0}", ds);
                    File tmpFile= File.createTempFile("autoplot", ".qds");
                    String fname= "internal/data"+nameGenCount+".qds";
                    SimpleStreamFormatter ff= new SimpleStreamFormatter();
                    //PipedOutputStream pout= new PipedOutputStream();
                    //PipedInputStream pin= new PipedInputStream(pout);
                    OutputStream pout= new FileOutputStream(tmpFile);
                    InputStream pin=null;
                    try {
                        ff.format( ds, pout, false );
                        pout.close();
                        pin= new FileInputStream(tmpFile);
                        writeToZip( out, fname, pin );
                        if ( !tmpFile.delete() ) {
                            logger.log(Level.WARNING, "unable to delete temp file: {0}", tmpFile);
                        }
                        dsf.setUri( "%{PWD}/"+fname);
                    } catch ( StreamException ex ) {
                        logger.log( Level.WARNING, null, ex );
                    } finally {
                        pout.close();
                        if ( pin!=null ) pin.close();
                    }
                    nameGenCount++;
                }
                dsfCount++;
            }
            for ( Plot p: dom.getPlots() ) {
                String uri = p.getTicksURI();
                URISplit split= URISplit.parse(uri);
                if ( uri.trim().length()>0 && !hasNoResource(split) ) {
                    String name= makeRelativeName(commonPath,split.resourceUri);
                    split.file= "%{PWD}/"+name;
                    p.setTicksURI( URISplit.format(split) );
                } 
            }
            {
                String uri = dom.getEventsListUri();
                URISplit split= URISplit.parse(uri);
                if ( uri.trim().length()>0 && !hasNoResource(split) ) {
                    String name= makeRelativeName(commonPath,split.resourceUri);
                    split.file= "%{PWD}/"+name;
                    dom.setEventsListUri( URISplit.format(split) );
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
