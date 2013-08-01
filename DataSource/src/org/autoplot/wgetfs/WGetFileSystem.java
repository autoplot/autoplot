/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.wgetfs;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.HtmlUtil;
import org.das2.util.filesystem.WebFileSystem;
import org.das2.util.monitor.CancelledOperationException;
import org.das2.util.monitor.ProgressMonitor;

/**
 * wget-based filesystem uses unix wget command.
 * @author jbf
 */
public class WGetFileSystem extends WebFileSystem {

    String wget= "wget";
    
    public WGetFileSystem(URI root, File localRoot) {
        super(root, localRoot);
    }
    
    public static WGetFileSystem createWGetFileSystem( URI root ) {
        return new WGetFileSystem( root, localRoot(root) );
    }

    @Override
    protected void downloadFile(String filename, File f, File partfile, ProgressMonitor monitor) throws IOException {
        String[] cmd = new String[] { wget, "-O", partfile.toString(), getRootURL().toString() + filename };
        
        ProcessBuilder pb= new ProcessBuilder( Arrays.asList(cmd) );
        Process p= pb.start();
        
        try {
            p.waitFor();
            if ( p.exitValue()!=0 ) {
                partfile.delete();
                throw new IOException("wget returned with exit code "+p.exitValue() );
            }
        } catch ( InterruptedException ex ) {
            throw new IOException(ex);
        }

        partfile.renameTo(f);
        
    }

    @Override
    public boolean isDirectory(String filename) throws IOException {
        File f = new File(localRoot, filename);
        if (f.exists()) {
            return f.isDirectory();
        } else {
            if (filename.endsWith("/")) {
                return true;
            } else {
                File parentFile = f.getParentFile();
                String parent = getLocalName(parentFile);
                if (!parent.endsWith("/")) {
                    parent = parent + "/";
                }

                String[] list = listDirectory(parent);
                String lookFor;
                if (filename.startsWith("/")) {
                    lookFor = filename.substring(1) + "/";
                } else {
                    lookFor = filename + "/";
                }
                for (int i = 0; i < list.length; i++) {
                    if (list[i].equals(lookFor)) {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    @Override
    public String[] listDirectory(String directory) throws IOException {
                
        directory = toCanonicalFolderName(directory);

        Map<String,DirectoryEntry> result;
        if ( isListingCached(directory) ) {
            logger.log(Level.FINE, "using cached listing for {0}", directory);

            File listing= listingFile(directory);
            
            URL[] list=null;
            FileInputStream fin=null;
            try {
                fin= new FileInputStream(listing);
                list = HtmlUtil.getDirectoryListing(getURL(directory), fin );
            } catch (CancelledOperationException ex) {
                throw new IllegalArgumentException(ex); // shouldn't happen since it's local
            } finally {
                if ( fin!=null ) fin.close();
            }
            
            result = new LinkedHashMap(list.length);
            int n = directory.length();
            for (int i = 0; i < list.length; i++) {
                URL url = list[i];
                DirectoryEntry de1= new DirectoryEntry();
                de1.modified= Long.MAX_VALUE; // HTTP is somewhat expensive to get dates and sizes, so put in Long.MAX_VALUE to indicate need to load.
                de1.name= getLocalName(url).substring(n);
                de1.type= 'f'; //TODO: directories mis-marked?
                de1.size= Long.MAX_VALUE;
                result.put(de1.name,de1);
            }

            result= addRoCacheEntries( directory, result );

            cacheListing( directory, result.values().toArray( new DirectoryEntry[result.size()] ) );

            return FileSystem.getListing( result );
        }
        
        boolean successOrCancel= false;

        if ( this.isOffline() ) {
            File f= new File(localRoot, directory);
            if ( !f.exists() ) throw new FileSystemOfflineException("unable to list "+f+" when offline");
            String[] listing = f.list();
            return listing;
        }
        
    
        File listingFile= listingFile(directory);
        String[] cmd = new String[] { wget, "-O", listingFile.toString(), getRootURL().toString() + directory };
        
        ProcessBuilder pb= new ProcessBuilder( Arrays.asList(cmd) );
        Process p= pb.start();
        
        try {
            p.waitFor();
            if ( p.exitValue()!=0 ) {
                listingFile.delete();
                throw new IOException("wget returned with exit code "+p.exitValue() );
            }
        } catch ( InterruptedException ex ) {
            throw new IOException(ex);
        }
        
        InputStream in= new FileInputStream( listingFile( directory ) );
        
        try {
            URL[] list = HtmlUtil.getDirectoryListing(getURL(directory), in );
            result = new LinkedHashMap();
            int n = directory.length();
            for (int i = 0; i < list.length; i++) {
                URL url = list[i];
                DirectoryEntry de1= new DirectoryEntry();
                de1.modified= Long.MAX_VALUE;
                de1.name= getLocalName(url).substring(n);
                de1.type= 'f';
                de1.size= Long.MAX_VALUE;
                result.put(de1.name,de1);
            }

            result= addRoCacheEntries( directory, result );
            cacheListing( directory, result.values().toArray( new DirectoryEntry[result.size()] ) );

            return FileSystem.getListing(result);
            
        } catch ( CancelledOperationException ex ) {
            throw new IllegalArgumentException("will not happen...");
            
        }
    }
    
}
