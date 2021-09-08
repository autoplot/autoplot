/*
 * FTPFileSystem.java
 *
 * Created on August 17, 2005, 3:33 PM
 *
 * This uses open-source code from ftp4j, found at https://sourceforge.net/projects/ftp4j/
 *
 */
package ftpfs;

import java.net.SocketException;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.filesystem.*;
import ftpfs.ftp.FtpBean;
import ftpfs.ftp.FtpException;
import ftpfs.ftp.FtpObserver;
import it.sauronsoftware.ftp4j.FTPFile;
import it.sauronsoftware.ftp4j.FTPListParseException;
import it.sauronsoftware.ftp4j.FTPListParser;
import it.sauronsoftware.ftp4j.listparsers.DOSListParser;
import it.sauronsoftware.ftp4j.listparsers.EPLFListParser;
import it.sauronsoftware.ftp4j.listparsers.MLSDListParser;
import it.sauronsoftware.ftp4j.listparsers.NetWareListParser;
import it.sauronsoftware.ftp4j.listparsers.UnixListParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import org.das2.util.monitor.CancelledOperationException;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.util.OsUtil;
import org.autoplot.datasource.DataSourceUtil;

/**
 * Alternate implementation of FTP that was originally introduced when the
 * built-in-ftp-based that comes with das2 didn't work.  This also provides
 * an upload capability.
 * 
 * @author Jeremy
 */
public final class FTPBeanFileSystem extends WebFileSystem {

    private static final Logger logger= Logger.getLogger("das2.filesystem.ftp");

    protected FtpBean getFtpBean() {
        FtpBean bean = new FtpBean();
        try {
            bean.setSocketTimeout(FileSystem.settings().getConnectTimeoutMs());
        } catch (SocketException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
        bean.setPassiveModeTransfer(true);
        if ( getRootURL().getPort()>-1 ) {
            bean.setPort(  getRootURL().getPort() ); 
        }
        return bean;
    }

    FTPBeanFileSystem( URI root ) throws FileSystemOfflineException, FileSystemOfflineException, IOException {
        super(root, userLocalRoot(root) );
        if ( FileSystem.settings().isOffline() ) {
            this.setOffline(true);
        }
        try {
            this.listDirectory("/"); // list the root to see if it is offline.
        } catch (java.net.ConnectException ex) {
            FileNotFoundException ex2= new FileNotFoundException( ex.getLocalizedMessage() );
            throw ex2;
        } catch (IOException ex) {
            if ( ex.getMessage().startsWith("550") ) {
                throw new FileNotFoundException( "550 not found: "+root.toString() );
            } else {
                logger.log(Level.INFO,"exception when listing the first time, going offline",ex);
                this.offline= true;
            }
        }
        
    }

    /**
     * identify where the local cache will be.  Note the File does not
     * yet exist until the data is read.
     * @param rooturi
     * @return local File indicating where the root will be.
     * @throws IOException 
     */
    private static File userLocalRoot( URI rooturi ) throws IOException {
        String auth= rooturi.getAuthority(); 
        if ( auth==null ) {
             throw new MalformedURLException("URL doesn't contain authority, check for ///");
        }
        String[] ss= auth.split("@");

        String userInfoNoPassword= null;
        String host;
        if ( ss.length>3 ) {
            throw new IllegalArgumentException("user info section can contain at most two at (@) symbols");
        } else if ( ss.length==3 ) {//bugfix 3299977.  Seth's proxy server.
            if ( ss[1].endsWith(":") ) {
                ss[1]= ss[1].substring(0,ss[1].length()-1);
            }
            userInfoNoPassword= ss[0] + "@" + ss[1];
            host= ss[2];
        } else if ( ss.length==1 ) {
            userInfoNoPassword= null;
            host= ss[0];
        } else {
            userInfoNoPassword= ss[0]; // TODO: there's a problem here, that we need to know the username, if there is one, to create the cache.
            host= rooturi.getHost();
        }

        // pop off the password
        int icolon= userInfoNoPassword==null ? -1 : userInfoNoPassword.indexOf(':');
        if ( icolon>-1 ) {
            assert userInfoNoPassword!=null;
            userInfoNoPassword= userInfoNoPassword.substring(0,icolon);
        }

        File local = FileSystem.settings().getLocalCacheDir();

        String s = rooturi.getScheme() + "/"
                + ( (userInfoNoPassword!=null ) ? userInfoNoPassword + "@" : "" )
                + host + rooturi.getPath();

        local = new File(local, s);

        return local;

    }

    /* dumb method looks for / in parent directory's listing */
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

    private boolean copyFile(File partFile, File targetFile) throws IOException {
        logger.log( Level.FINER, "ftpBeanFilesystem copyFile({0},{1}", new Object[]{partFile, targetFile});
        WritableByteChannel dest = Channels.newChannel(new FileOutputStream(targetFile));
        ReadableByteChannel src = Channels.newChannel(new FileInputStream(partFile));
        DataSourceUtil.transfer(src, dest);
        return true;

    }

    private long parseTime1970(String time, Calendar context) {
        try {
            return (long) TimeUtil.toDatum(TimeUtil.parseTime(time)).doubleValue(Units.t1970);
        } catch (ParseException ex) {
            try {
                return (long) TimeUtil.toDatum(TimeUtil.parseTime("" + context.get(Calendar.YEAR) + " " + time)).doubleValue(Units.t1970);
            } catch (ParseException ex1) {
                return -1;
            }
        }
    }

    FTPListParser parser= null;
    private static final List<FTPListParser> listParsers;
    static {
        listParsers= new ArrayList(5);
        listParsers.add(new UnixListParser() );
        listParsers.add(new DOSListParser());
        listParsers.add(new EPLFListParser());
        listParsers.add(new NetWareListParser());
        listParsers.add(new MLSDListParser());
    }
    /**
     * use open source library that supports several formats for list result.
     * @param dir
     * @param listing
     * @return
     * @throws IOException
     */
    protected DirectoryEntry[] parseLslNew( String dir, File listing ) throws IOException {

        logger.log(Level.FINE, "parseLslNew {0}", dir);

        FTPFile[] ret=null;
        List<String> llist= new ArrayList<>(370);
        
        try (BufferedReader reader = new BufferedReader( new FileReader( listing ) )) {
            String aline = reader.readLine();
            while ( aline!=null ) {
                llist.add(aline);
                aline = reader.readLine();
            }
        
        }
        
        String[] list= llist.toArray( new String[llist.size()] );

        // open source taken from it.sauronsoftware.ftp4j.FTPClient.java
        // Is there any already successful parser?
        if (parser != null) {
                // Yes, let's try with it.
                try {
                        ret = parser.parse(list);
                } catch (FTPListParseException e) {
                        // That parser doesn't work anymore.
                        parser = null;
                }
        }
        // Is there an available result?
        if (ret == null) {
            // Try to parse the list with every available parser.
            for (FTPListParser aux : listParsers) {
                try {
                    // Let's try!
                    ret = aux.parse(list);
                    // This parser smells good!
                    parser = aux;
                    // Leave the loop.
                    break;
                } catch (FTPListParseException e) {
                    // Let's try the next one.
                    //TODO: questionMark in Unix filesystem!
                    logger.log( Level.FINE, e.getMessage(), e );
                }
            }
        }
        // end  it.sauronsoftware.ftp4j.FTPClient.java portion

        if ( ret==null ) {
            throw new IOException("unable to parse FTP listing, because the format is not recognized");
        }
        
        DirectoryEntry[] result= new DirectoryEntry[ret.length];
        for ( int i=0; i<result.length; i++ ) {
            DirectoryEntry de1= new DirectoryEntry();
            de1.modified= ret[i].getModifiedDate().getTime();
            de1.name= ret[i].getName();
            de1.size= ret[i].getSize();
            de1.type= ret[i].getType()==FTPFile.TYPE_FILE ? 'f' : 'd';
            result[i]= de1;
        }
        return result;
    }

    @Override
    public synchronized final String[] listDirectory(String directory) throws IOException {
        directory = toCanonicalFolderName(directory);

        DirectoryEntry[] result;
        if ( isListingCached(directory) ) {
            logger.log(Level.FINE, "using cached listing for {0}", directory);

            File listing= listingFile(directory);
            DirectoryEntry[] des = parseLslNew(directory, listing);

            cacheListing(directory, des );

            return FileSystem.getListing(des);
        }

        boolean successOrCancel= false;

        if ( this.isOffline() ) {
            File f= new File(localRoot, directory);
            if ( !f.exists() ) throw new FileSystemOfflineException("unable to list "+f+" when offline");
            String[] listing = f.list();
            return listing; //TODO: remove local file '.listing'
        }

        URL url= getRootURL();
        String userInfo=null;

        while ( !successOrCancel ) {
            try {
                File newDir= new File(localRoot, directory);
                File listing;
                File listingt;
                FtpBean bean = getFtpBean();
                try {
                    userInfo= KeyChain.getDefault().getUserInfo(url);
                    if ( userInfo==null ) {
                        String surl= url.toExternalForm();
                        int i0=surl.indexOf("://")+3;
                        surl= surl.substring(0,i0)+"user:pass@"+surl.substring(i0);
                        url= new URL( surl );
                        userInfo= KeyChain.getDefault().getUserInfo(url);
                    }
                    if ( userInfo!=null ) {
                        String[] userHostArr= userInfo.split(":");
                        if ( userHostArr.length==1 ) {
                            userHostArr= new String[] { userHostArr[0], "pass" };
                        } else if ( userHostArr.length==0 ) {
                            userHostArr= new String[] { "user", "pass" };
                        }
                        bean.ftpConnect(url.getHost(), userHostArr[0], userHostArr[1]);
                    } else {
                        bean.ftpConnect(url.getHost(), "ftp");
                    }
                    String cwd= bean.getDirectory(); // URI should not contain remote root.  // will allow for this.
                    bean.setDirectory( cwd + getRootURL().getPath() + directory.substring(1) );

                    FileSystemUtil.maybeMkdirs(newDir);
                    listing = new File(localRoot, directory + ".listing");
                    listingt = new File(localRoot, directory + ".listing.temp");
                    
                } catch (NullPointerException ex) {
                    logger.log( Level.SEVERE, "Unable to make connection to " + getRootURL().getHost(), ex );
                    throw new IOException("Unable to make connection to " + getRootURL().getHost(), ex);
                } catch (CancelledOperationException ex ) {
                    throw new FileSystemOfflineException("user cancelled credentials");
                }

                String ss = bean.getDirectoryContentAsString();
                try (FileWriter fw = new FileWriter(listingt)) {
                    fw.write(ss);
                }

                //Windows7 cannot clobber the old filename.  Delete it manually.  TODO: locks...
                if ( listing.exists() ) {
                    if ( !listing.delete() ) {
                        throw new IllegalArgumentException("unable to delete old listing file "+listing);
                    }
                }
                if ( ! listingt.renameTo(listing) ) {
                    throw new IllegalArgumentException("unable to rename file "+listingt+ " to "+ listing );
                }
                //successOrCancel= true;
                
                result = parseLslNew(directory, listing);

                cacheListing(directory, result );

                return FileSystem.getListing(result);
                
            } catch (FtpException e) {
                if ( e.getMessage().startsWith("530" ) ) { // invalid login
                    if ( userInfo==null ) {
                        userInfo="user:pass";
                        if ( url.getPort()>-1 ) {
                            url= new URL( url.getProtocol() + "://"+ userInfo + "@" + url.getHost() + ":" + url.getPort() + url.getFile() );
                        } else {
                            url= new URL( url.getProtocol() + "://"+ userInfo + "@" + url.getHost() + url.getFile() );
                        }
                    }
                    KeyChain.getDefault().clearUserPassword(url);
                    // loop for them to try again.
                } else if ( e.getMessage().startsWith("550") ) {
                    logger.log(Level.FINE, "create directory{0}", new File(localRoot, directory).toString());
                    throw new IOException( e.getMessage()+": "+directory);
                } else {
                    throw new IOException( e ); 
                }
            }
        }
        return( new String[] { "should not get here" } ); // we should not be able to reach this point
    }

    protected void uploadFile( String filename, File srcFile, final ProgressMonitor mon ) throws IOException {
        logger.log(Level.FINE, "ftpfs uploadFile({0})", filename);

        filename = toCanonicalFilename(filename);
        URL url = new URL(getRootURL(), filename.substring(1));

        String[] ss = FileSystem.splitUrl(url.toString());

        try {
            FtpBean bean = getFtpBean();

            String fname= ss[2].substring(ss[1].length()); // the name within the filesystem

            String userInfo= KeyChain.getDefault().getUserInfo(getRootURL());
            if ( userInfo!=null ) {
                String[] userHostArr= userInfo.split(":");
                bean.ftpConnect(getRootURL().getHost(), userHostArr[0], userHostArr[1]);
            } else {
                bean.ftpConnect(getRootURL().getHost(), "ftp");
            }
            
            String cwd= bean.getDirectory();
            bean.setDirectory( cwd + fname );

            String lfilename= ss[3].substring(ss[2].length());

            long size = srcFile.length();

            mon.setTaskSize(size);
            mon.started();
            final long t0 = System.currentTimeMillis();

            FtpObserver observer = new FtpObserver() {

                int totalBytes = 0;

                @Override                
                public boolean byteRead(int bytes) {
                    totalBytes += bytes;
                    if (mon.isCancelled()) {
                        return false;
                    }
                    long dt = System.currentTimeMillis() - t0;
                    mon.setTaskProgress(totalBytes);
                    mon.setProgressMessage(totalBytes / 1000 + "KB read at " + (totalBytes / dt) + " KB/sec");
                    return true;
                }

                @Override
                public boolean byteWrite(int bytes) {
                    totalBytes += bytes;
                    mon.setTaskProgress(totalBytes);
                    if (mon.isCancelled()) {
                        return false;
                    }
                    long dt = System.currentTimeMillis() - t0;
                    mon.setTaskProgress(totalBytes);
                    mon.setProgressMessage(totalBytes / 1000 + "KB written at " + (totalBytes / dt) + " KB/sec");
                    return true;
                }
            };

            bean.putBinaryFile( srcFile.getAbsolutePath(), lfilename, observer );

            // update the local cache
            FtpFileObject fo= (FtpFileObject)getFileObject(filename);
            resetListCache( fo.getParent().getNameExt() );
            listDirectory( fo.getParent().getNameExt() );
            bean.close();
            
        } catch (RuntimeException ex) {

            logger.log( Level.SEVERE, ex.getMessage(), ex );
            if (ex.getCause() instanceof IOException) {
                throw (IOException) ex.getCause();
            } else {
                throw new IOException(ex.toString());
            }
        } catch (FtpException | CancelledOperationException ex) {
            throw new IOException(ex.getMessage());

        } finally {
            mon.finished();
        }


    }

    @Override
    protected Map<String,String> downloadFile( String filename, File targetFile, final File partFile, final ProgressMonitor mon) throws java.io.IOException {

        Lock lock= getDownloadLock( filename, targetFile, mon );

        if ( lock==null ) return Collections.EMPTY_MAP;

        logger.log(Level.FINE, "ftpfs downloadFile({0})", filename);
        
        try {
            filename = toCanonicalFilename(filename);
            URL url = new URL(getRootURL(), filename.substring(1));

            String[] ss = FileSystem.splitUrl(url.toString());

            boolean isgz= false;
            
            url= getRootURL();
            String userInfo= null;
            boolean done= false;
            while ( !done ) {
                try {
                    final FtpBean bean = getFtpBean();

                    userInfo= KeyChain.getDefault().getUserInfo(url);
                    if ( userInfo==null ) {
                        String surl= url.toExternalForm();
                        int i0=surl.indexOf("://")+3;
                        surl= surl.substring(0,i0)+"user:pass@"+surl.substring(i0);
                        url= new URL( surl );
                        userInfo= KeyChain.getDefault().getUserInfo(url);
                    }
                    if ( userInfo!=null ) {
                        String[] userHostArr= userInfo.split(":");
                        if ( userHostArr.length==1 ) {
                           userHostArr= new String[] { userHostArr[0], "pass" };
                        } else if ( userHostArr.length==0 ) {
                           userHostArr= new String[] { "user", "pass" };
                        }
                        bean.ftpConnect(getRootURL().getHost(), userHostArr[0], userHostArr[1]);
                        String cwd= bean.getDirectory();
                        bean.setDirectory( cwd + ss[2].substring(ss[1].length()) );
                    } else {
                        bean.ftpConnect(getRootURL().getHost(), "ftp");
                        String cwd= bean.getDirectory();
                        bean.setDirectory( cwd + ss[2].substring(ss[1].length()) );
                    }

                    File listingFile = new File(targetFile.getParentFile(), ".listing");
                    if (!listingFile.exists()) {
                        String listing = bean.getDirectoryContentAsString();
                        try (FileOutputStream out2 = new FileOutputStream(listingFile)) {
                            out2.write(listing.getBytes("US-ASCII"));
                        }
                    }

                    long size = this.getFileObject(filename).getSize();
                    mon.setTaskSize(size);
                    mon.started();
                    final long t0 = System.currentTimeMillis();

                    FtpObserver observer = new FtpObserver() {

                        int totalBytes = 0;

                        @Override
                        public boolean byteRead(int bytes) {
                            totalBytes += bytes;
                            if (mon.isCancelled()) {
                                return false;
                            }
                            long dt = Math.max( 1, System.currentTimeMillis() - t0 );
                            mon.setTaskProgress(totalBytes);
                            mon.setProgressMessage(totalBytes / 1000 + "KB read at " + (totalBytes / dt) + " KB/sec");
                            return true;
                        }

                        @Override
                        public boolean byteWrite(int bytes) {
                            totalBytes += bytes;
                            mon.setTaskProgress(totalBytes);
                            return true;
                        }
                    };
                    try {
                        bean.getBinaryFile(ss[3].substring(ss[2].length()), partFile.toString(), observer);
                    } catch ( FtpException ex ) {
                        bean.getBinaryFile(ss[3].substring(ss[2].length())+".gz", partFile.toString(), observer);
                        FileSystemUtil.gunzip( partFile, targetFile );
                        isgz= true;
                    }
                    bean.close();
                    done= true;
                    
                } catch (RuntimeException ex) {
                    logger.log( Level.SEVERE, ex.getMessage(), ex );
                    Throwable cause= ex.getCause();
                    if (cause instanceof IOException) {
                        throw (IOException) cause;
                    } else {
                        IOException tex= new IOException(ex);
                        throw tex;
                    }
                } catch (FtpException ex) {
                    if ( ex.getMessage().startsWith("530" ) ) { // invalid login
                        if ( userInfo==null ) {
                            userInfo="user:pass";
                            url= new URL( url.getProtocol() + "://"+ userInfo + "@" + url.getHost() + url.getFile() );
                        }
                        KeyChain.getDefault().clearUserPassword(url);
                        // loop for them to try again.
                    } else {
                        throw new IOException(ex);
                    }

                } catch ( CancelledOperationException ex ) {
                    throw new FileSystemOfflineException("user cancelled credentials");
                } finally {    
                    mon.finished();
                }
            }

            if ( isgz ) {
                return Collections.EMPTY_MAP;
            }
            
            if ( targetFile.length()==partFile.length() ) {
                if ( OsUtil.contentEquals(targetFile, partFile ) ) {
                    logger.fine("another thread must have downloaded file.");
                    if ( !partFile.delete() ) {
                        throw new IllegalArgumentException("unable to delete "+partFile );
                    }
                    return Collections.EMPTY_MAP;
                }
            }
            
            if ( copyFile(partFile, targetFile) ) {
                logger.fine( String.format( "%s: deleting %s", Thread.currentThread(), partFile ) );
                synchronized ( FTPBeanFileSystem.class ) {
                    if ( partFile.exists() && ! partFile.delete() ) {
                        throw new IllegalArgumentException("unable to delete file "+partFile );
                    }
                }
            }

        } catch (IOException e) {
            synchronized ( FTPBeanFileSystem.class ) {
                logger.fine( String.format( "%s: deleting %s", Thread.currentThread(), partFile ) );
                if ( partFile.exists() && !partFile.delete() ) {
                    throw new IllegalArgumentException("unable to delete file "+partFile);
                }
            }
            throw e;
            
        } finally {
            mon.finished();
            lock.unlock();
        }
        return Collections.EMPTY_MAP;
        
    }


    @Override
    public FileObject getFileObject(String filename) {
        DirectoryEntry result=null;
        try {
            result= maybeUpdateDirectoryEntry( filename, false );
        } catch ( IOException ex ) {
            logger.log(Level.SEVERE,ex.getMessage(),ex);// shouldn't happen when force=false.
        }
        if ( result==null && this.isOffline() ) {
            File localfile= new File( getLocalRoot(), filename );
            Date t= new Date(System.currentTimeMillis());
            if ( localfile.exists() ) {
                t= new Date( localfile.lastModified() );
            }
            return new FtpFileObject(this, filename, t );
        } else if ( result==null ) {
            return new FtpFileObject(this, filename, new Date(0) ); //flag to retrieve if necessary
        } else {
            return new FtpFileObject(this, filename, new Date( result.modified ) );
        }

    }

    boolean delete(FtpFileObject aThis) throws IOException {
        FtpBean bean = getFtpBean();

        String userInfo;
        try {
            userInfo = KeyChain.getDefault().getUserInfo(getRootURL());
        } catch (CancelledOperationException ex) {
            IOException result= new IOException(ex.toString());
            throw result; // I'd expect we would have hit an IOException already.
        }

        String filename = toCanonicalFilename(aThis.getNameExt() );
        URL url = new URL(getRootURL(), filename.substring(1));

        String[] ss = FileSystem.splitUrl(url.toString());

        try {
            if (userInfo != null) {
                String[] userHostArr = userInfo.split(":");
                bean.ftpConnect(getRootURL().getHost(), userHostArr[0], userHostArr[1]);
                String cwd = bean.getDirectory();
                bean.setDirectory(cwd + ss[2].substring(ss[1].length()));
            } else {
                bean.ftpConnect(getRootURL().getHost(), "ftp");
                String cwd = bean.getDirectory();
                bean.setDirectory(cwd + ss[2].substring(ss[1].length()));
            }
            bean.fileDelete(ss[3].substring(ss[2].length()));
            bean.close();
            return true;
        } catch (IOException | FtpException iOException) {
            try {
                bean.close();
            } catch (FtpException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
            return false;
        }
    }

    protected static final Logger getLogger() {
        return logger;
    }
}
