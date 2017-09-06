
package org.autoplot.wgetfs;

import java.awt.EventQueue;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.datum.LoggerManager;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.HtmlUtil;
import org.das2.util.filesystem.WebFileSystem;
import org.das2.util.monitor.CancelledOperationException;
import org.das2.util.monitor.ProgressMonitor;

/**
 * wget-based filesystem uses unix wget command.  This was immediately
 * extended to add support for curl, which comes with macs.
 * @author jbf
 */
public class WGetFileSystem extends WebFileSystem {
    
    private static final Logger logger= LoggerManager.getLogger("das2.filesystem.wget" );
    
    public WGetFileSystem(URI root, File localRoot) {
        super(root, localRoot);
        if ( WGetFileSystemFactory.exe==null ) {
            throw new IllegalArgumentException("This must be constructed with the factory.");
        }        
        try { 
            this.protocol= new WGetWebProtocol( root.toURL() );
        } catch (MalformedURLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
    
    public static WGetFileSystem createWGetFileSystem( URI root ) {
        return new WGetFileSystem( root, localRoot(root) );
    }

    private long interpretLong( String s ) {
        if ( s.endsWith("K") ) {
            double mant= Long.parseLong(s.substring(0,s.length()-1));
            return (long)(mant*1000);
        } else if ( s.endsWith("k") ) { //curl
            double mant= Long.parseLong(s.substring(0,s.length()-1));
            return (long)(mant*1000);
        } else if ( s.endsWith("M") ) {
            double mant= Double.parseDouble(s.substring(0,s.length()-1));
            return (long)(mant*1000000);
        } else {
            long mant= Long.parseLong(s);
            return mant;
        }
    }
    
    /**
     * For wget, look for "Length:" and progress updates.  For curl, look for
     * "00 13.5M  100 13.5M    0     0  1125k      0  0:00:12  0:00:12 --:--:-- 1125k"
     * @param line
     * @param monitor 
     */
    private void interpretProgress( String line, String filename, ProgressMonitor monitor ) {
        if ( WGetFileSystemFactory.useCurl ) {
            if ( !monitor.isStarted() ) {
                String[] ss= line.split("\\s+");    
                if ( ss.length==13 ) {
                    try { 
                        long l= interpretLong(ss[2]);
                        if ( l>0 && !ss[11].startsWith("--") ) {
                            monitor.setTaskSize( l );
                            monitor.setProgressMessage("curl "+filename);
                            monitor.started();                    
                        }
                    } catch ( NumberFormatException ex ) {
                        // do nothing, that was a header...
                    }
                }
            } else {
                String[] ss= line.split("\\s+");
                if ( ss.length==13 ) {
                    monitor.setTaskProgress( interpretLong(ss[4]) );
                }
            }
        } else {
            if ( !monitor.isStarted() ) {
                if ( line.startsWith("Length:" ) ) {
                    int term= line.indexOf(' ',8);
                    monitor.setTaskSize( interpretLong( line.substring(8,term) ) );
                    monitor.setProgressMessage("wget "+filename);
                    monitor.started();
                }
            } else {
                Pattern prog= Pattern.compile("\\s*(([0-9]+)([MK])?)");
                Matcher m= prog.matcher(line);
                if ( m.find() && m.start()==0 && monitor.isStarted()  ) {
                    monitor.setTaskProgress( interpretLong( m.group(1) ) );
                }
            }
        }
    }
    
    @Override
    protected void downloadFile(String filename, File f, File partfile, ProgressMonitor monitor) throws IOException {
        String[] cmd;
        if ( WGetFileSystemFactory.useCurl ) {
            cmd= new String[] { WGetFileSystemFactory.exe, "-o", partfile.toString(), getRootURL().toString() + filename };
        } else {
            cmd= new String[] { WGetFileSystemFactory.exe, "-O", partfile.toString(), getRootURL().toString() + filename };
        }
        
        logger.log(Level.FINE, "cmd: {0} {1} {2} {3}", new Object[]{cmd[0], cmd[1], cmd[2], cmd[3]});
        ProcessBuilder pb= new ProcessBuilder( Arrays.asList(cmd) );
        Process p= pb.start();
        
        try (BufferedReader err = new BufferedReader( new InputStreamReader( p.getErrorStream() ) )) {
            String line= err.readLine();
            while ( line!=null ) {
                interpretProgress( line, filename, monitor );
                //System.err.println(line);
                Thread.sleep(200);
                line= err.readLine();
                if ( monitor.isCancelled() ) {
                    p.destroy();
                    if ( partfile.exists() ) {
                        if ( !partfile.delete() ) {
                            logger.log(Level.WARNING, "unable to delete file: {0}", partfile);
                        }
                    }
                    throw new InterruptedException("user cancel");
                }
            }
            p.waitFor();
            if ( p.exitValue()!=0 ) {
                if ( !partfile.delete() ) {
                    logger.log(Level.WARNING, "unable to delete file: {0}", partfile);
                }
                throw new IOException( cmd[0] +" returned with exit code "+p.exitValue() );
            }
            
        } catch ( InterruptedException ex ) {
            throw new IOException(ex);
        } finally {
            monitor.finished();
        }

        if ( !partfile.renameTo(f) ) {
            logger.log(Level.WARNING, "unable to rename file {0} to {1}", new Object[]{partfile, f});
        }
        
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
                for (String list1 : list) {
                    if (list1.equals(lookFor)) {
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

        if ( EventQueue.isDispatchThread() ) {
            logger.warning("listDirectory called on event thread");
        }
        
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
            
            assert list!=null;
            result = new LinkedHashMap(list.length);
            int n = directory.length();
            for (URL url : list) {
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
        
        if ( this.isOffline() ) {
            File f= new File(localRoot, directory);
            if ( !f.exists() ) throw new FileSystemOfflineException("unable to list "+f+" when offline");
            String[] listing = f.list();
            return listing;
        }
        
    
        File listingFile= listingFile(directory);
        String[] cmd;
        if ( WGetFileSystemFactory.useCurl ) {
            cmd= new String[] { WGetFileSystemFactory.exe, "-o", listingFile.toString(), getRootURL().toString() + directory };            
        } else {
            cmd= new String[] { WGetFileSystemFactory.exe, "-O", listingFile.toString(), getRootURL().toString() + directory };
        }
        
        logger.log(Level.FINE, "cmd: {0} {1} {2} {3}", new Object[]{cmd[0], cmd[1], cmd[2], cmd[3]});
        
        ProcessBuilder pb= new ProcessBuilder( Arrays.asList(cmd) );
        Process p= pb.start();
        
        try {
            p.waitFor();
            if ( p.exitValue()!=0 ) {
                if ( !listingFile.delete() ) {
                    logger.log(Level.WARNING, "unable to delete listing file: {0}", listingFile);
                }
                throw new IOException("wget returned with exit code "+p.exitValue() );
            }
        } catch ( InterruptedException ex ) {
            throw new IOException(ex);
        }
        
        InputStream in= new FileInputStream( listingFile( directory ) );
        result= new LinkedHashMap();
        try {
            if ( WGetFileSystemFactory.useCurl && getRootURL().getProtocol().equals("ftp") ) {            
                try (BufferedReader bin = new BufferedReader( new InputStreamReader(in) )) {
                    String line= bin.readLine();
                    while ( line!=null ) {
                        String[] ss= line.split("\\s+");
                        if ( ss.length>8 ) {
                            boolean dir= line.charAt(0)=='d';
                            DirectoryEntry de1= new DirectoryEntry();
                            de1.modified= Long.MAX_VALUE;  //danger not used 
                            String name= ss[8];
                            if ( name.startsWith("/") ) name= name.substring(1);                                
                            de1.name= directory + name + ( dir?"/":"" );
                            de1.type= dir ? 'd': 'f' ;
                            de1.size= Long.MAX_VALUE;  //not used
                            result.put(de1.name,de1);
                        } else {
                            System.err.println("here line 268");
                        }
                        line= bin.readLine();
                    }
                }
                //System.err.println(result);
                //System.err.println(result);
            } else {

                URL[] list = HtmlUtil.getDirectoryListing(getURL(directory), in );
                int n = directory.length();
                for (URL url : list) {
                    DirectoryEntry de1= new DirectoryEntry();
                    de1.modified= Long.MAX_VALUE;
                    de1.name= getLocalName(url).substring(n);
                    de1.type= 'f';
                    de1.size= Long.MAX_VALUE;
                    result.put(de1.name,de1);
                }

            }
        } catch ( CancelledOperationException ex ) {
            //TODO: what?
            logger.log( Level.WARNING, ex.getMessage(), ex );
        } finally {
            in.close();
        }

        result= addRoCacheEntries( directory, result );
        cacheListing( directory, result.values().toArray( new DirectoryEntry[result.size()] ) );

        return FileSystem.getListing(result);
            
        
    }
    
}
