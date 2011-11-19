/*
 * FTPFileSystem.java
 *
 * Created on August 17, 2005, 3:33 PM
 *
 *
 */
package ftpfs;

import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.filesystem.*;
import ftpfs.ftp.FtpBean;
import ftpfs.ftp.FtpException;
import ftpfs.ftp.FtpObserver;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import org.das2.util.monitor.CancelledOperationException;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.virbo.datasource.DataSourceUtil;

/**
 * Alternate implementation of FTP that was originally introduced when the
 * built-in-ftp-based that comes with das2 didn't work.  This also provides
 * an upload capability.
 * 
 * @author Jeremy
 */
public class FTPBeanFileSystem extends WebFileSystem {

    FTPBeanFileSystem(URI root) throws FileSystemOfflineException {
        super(root, userLocalRoot(root) );
        try {
            this.listDirectory("/"); // list the root to see if it is offline.
        } catch (IOException ex) {
            //TODO: how to distinguist UnknownHostException to be offline?  avoid firing an event until I come up with a way.
            ex.printStackTrace();
            this.offline= true;
        }
        
    }

    private static File userLocalRoot( URI root ) {
        File local = FileSystem.settings().getLocalCacheDir();

        String userInfo= root.getUserInfo();
        if ( userInfo!=null && userInfo.contains(":") ) {
            userInfo= userInfo.substring(0,userInfo.indexOf(':') );
        }

        String s = root.getScheme() + "/" 
                + ( (userInfo!=null ) ? userInfo + "@" : "" )
                + root.getHost() + "/" + root.getPath(); 

        local = new File(local, s);

        local.mkdirs();
        return local;

    }

    /* dumb method looks for / in parent directory's listing */
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

    public DirectoryEntry[] parseLsl(String dir, File listing) throws IOException {
        InputStream in = new FileInputStream(listing);

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));

        String aline = reader.readLine();

        boolean done = aline == null;

        String types = "d-";

        long bytesRead = 0;
        //long totalSize;
        //long sumSize=0;

        List<DirectoryEntry> result = new ArrayList<DirectoryEntry>(20);
        int lineNum = 1;

        int sizePos= 31;
        int modifiedPos= 42;

        while (!done) {
            //System.err.println(""+lineNum+": "+ aline);
            bytesRead = bytesRead + aline.length() + 1;

            aline = aline.trim();

            if (aline.length() == 0) {
                done = true;
            } else {

                char type = aline.charAt(0);
                if (type == 't') {
                    if (aline.indexOf("total") == 0) {
                        //totalSize= Long.parseLong( aline.substring( 5 ).trim() );
                    }
                }

                if (types.indexOf(type) != -1) {
                    int i = aline.lastIndexOf(' ');
                    //String name = aline.substring(i + 1);
                    //long size = 0;
                    //try {
                    //size= Long.parseLong( aline.substring( 31, 31+11 ) ); // tested on: linux server
                    //} catch ( NumberFormatException e ) {
                    //}

                    //boolean isFolder = type == 'd';

                    DirectoryEntry item = new DirectoryEntry();
                    item.name = aline.substring(i + 1);
                    try {
                        item.size = Long.parseLong(aline.substring(sizePos, sizePos + 11).trim());
                    } catch ( NumberFormatException ex ) {
                        item.size = Long.parseLong(aline.substring(sizePos, sizePos + 10).trim());
                    }
                    item.type = type == 'd' ? 'd' : 'f';
                    item.modified = parseTime1970(aline.substring(modifiedPos, modifiedPos+12), Calendar.getInstance());

                    result.add(item);

                    //sumSize= sumSize + size;

                }

                aline = reader.readLine();
                lineNum++;

                done = aline == null;

            } // if (aline.length)

        } // while

        reader.close();
        //TODO: finally clause

        return (DirectoryEntry[]) result.toArray(new DirectoryEntry[result.size()]);
    }

    protected void resetListCache( String directory ) {
        directory = toCanonicalFolderName(directory);

        listings.remove(directory);
        new File(localRoot, directory + ".listing").delete();
        
    }

    public static final int LISTING_TIMEOUT_MS = 20000;
    private final Map<String,String[]> listings= Collections.synchronizedMap( new HashMap() );
    private final Map<String,Long> listingFreshness= Collections.synchronizedMap( new HashMap() );

    public void resetListingCache() {
        synchronized (listings) {
            this.listings.clear();
            this.listingFreshness.clear();
        }
    }

    public boolean isListingCached( String directory ) {
        directory = HttpFileSystem.toCanonicalFilename(directory);
        if ( !listings.containsKey(directory) ) {
            return false;
        } else {
            return ((Long)listingFreshness.get(directory))-System.currentTimeMillis() > 0 ;
        }
    }

    public synchronized final String[] listDirectory(String directory) throws IOException {
        directory = toCanonicalFolderName(directory);

        String[] result;
        if ( isListingCached(directory) ) {
            result= listings.get(directory);
            if ( result!=null ) {
                logger.log(Level.FINE, "using cached listing for {0}", directory);
                //Arrays.copyOf(result, result.length) when we switch to Java 1.6
                String[] resultc= new String[result.length];
                System.arraycopy( result, 0, resultc, 0, result.length );
                return resultc;
            }
        }

        boolean successOrCancel= false;

        if ( this.isOffline() ) {
            File f= new File(localRoot, directory);
            if ( !f.exists() ) throw new FileSystemOfflineException("unable to list "+f+" when offline");
            String[] listing = f.list();
            return listing;
        }

        URL url= getRootURL();
        String userInfo=null;

        while ( !successOrCancel ) {
            try {
                new File(localRoot, directory).mkdirs();
                File listing = new File(localRoot, directory + ".listing");

                FtpBean bean = new FtpBean();
                try {
                    userInfo= KeyChain.getDefault().getUserInfo(url);
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

                } catch (NullPointerException ex) {
                    ex.printStackTrace();
                    IOException ex2= new IOException("Unable to make connection to " + getRootURL().getHost());
                    ex2.initCause(ex);
                    throw ex2;
                } catch (CancelledOperationException ex ) {
                    throw new FileSystemOfflineException("user cancelled credentials");
                }

                String ss = bean.getDirectoryContentAsString();
                FileWriter fw = new FileWriter(listing);
                fw.write(ss);
                fw.close();

                successOrCancel= true;
                
                DirectoryEntry[] des = parseLsl(directory, listing);
                result = new String[des.length];
                for (int i = 0; i < des.length; i++) {
                    result[i] = des[i].name + (des[i].type == 'd' ? "/" : "");
                }
                listings.put(directory, result);
                listingFreshness.put( directory, System.currentTimeMillis()+LISTING_TIMEOUT_MS );
                String[] resultc= new String[result.length];
                System.arraycopy( result, 0, resultc, 0, result.length );

                listing.delete();
                
                return resultc;
                
            } catch (FtpException e) {
                if ( e.getMessage().startsWith("530" ) ) { // invalid login
                    if ( userInfo==null ) {
                        userInfo="user:pass";
                        url= new URL( url.getProtocol() + "://"+ userInfo + "@" + url.getHost() + url.getFile() );
                    }
                    KeyChain.getDefault().clearUserPassword(url);
                    // loop for them to try again.
                } else {
                    throw new IOException(e.getMessage()); //JAVA5
                }
            }
        }
        return( new String[] { "should not get here" } ); // we should not be able to reach this point
    }

    protected void uploadFile( String filename, File srcFile, final ProgressMonitor mon ) throws IOException {
        logger.log(Level.FINE, "ftpfs uploadFile({0})", filename);

        FileOutputStream out = null;
        InputStream is = null;
        
        filename = toCanonicalFilename(filename);
        URL url = new URL(getRootURL(), filename.substring(1));

        String[] ss = FileSystem.splitUrl(url.toString());

        try {
            FtpBean bean = new FtpBean();

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

                public void byteRead(int bytes) {
                    totalBytes += bytes;
                    if (mon.isCancelled()) {
                        throw new RuntimeException(new InterruptedIOException("transfer cancelled by user"));
                    }
                    long dt = System.currentTimeMillis() - t0;
                    mon.setTaskProgress(totalBytes);
                    mon.setProgressMessage(totalBytes / 1000 + "KB read at " + (totalBytes / dt) + " KB/sec");

                }

                public void byteWrite(int bytes) {
                    totalBytes += bytes;
                    mon.setTaskProgress(totalBytes);
                    if (mon.isCancelled()) {
                        throw new RuntimeException(new InterruptedIOException("transfer cancelled by user"));
                    }
                    long dt = System.currentTimeMillis() - t0;
                    mon.setTaskProgress(totalBytes);
                    mon.setProgressMessage(totalBytes / 1000 + "KB written at " + (totalBytes / dt) + " KB/sec");
                }
            };

            bean.putBinaryFile( srcFile.getAbsolutePath(), lfilename, observer );

            // update the local cache
            FtpFileObject fo= (FtpFileObject)getFileObject(filename);
            resetListCache( fo.getParent().getNameExt() );
            listDirectory( fo.getParent().getNameExt() );
            bean.close();

        } catch (RuntimeException ex) {
            ex.printStackTrace();
            if (ex.getCause() instanceof IOException) {
                throw (IOException) ex.getCause();
            } else {
                throw new IOException(ex.toString());
            }
        } catch (FtpException ex) {
            throw new IOException(ex.getMessage());

        } catch ( CancelledOperationException ex ) {
            throw new IOException(ex.getMessage());
        }


    }

    protected void downloadFile( String filename, File targetFile, File partFile, final ProgressMonitor mon) throws java.io.IOException {

        Lock lock= getDownloadLock( filename, targetFile, mon );

        if ( lock==null ) return;

        logger.log(Level.FINE, "ftpfs downloadFile({0})", filename);
        
        FileOutputStream out = null;
        InputStream is = null;
        try {
            filename = toCanonicalFilename(filename);
            URL url = new URL(getRootURL(), filename.substring(1));

            String[] ss = FileSystem.splitUrl(url.toString());


            url= getRootURL();
            String userInfo= null;
            boolean done= false;
            while ( !done ) {
                try {
                    FtpBean bean = new FtpBean();

                    userInfo= KeyChain.getDefault().getUserInfo(url);
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
                        FileOutputStream out2 = new FileOutputStream(listingFile);
                        out2.write(listing.getBytes());
                        out2.close();
                    }

                    long size = this.getFileObject(filename).getSize();
                    mon.setTaskSize(size);
                    mon.started();
                    final long t0 = System.currentTimeMillis();

                    FtpObserver observer = new FtpObserver() {

                        int totalBytes = 0;

                        public void byteRead(int bytes) {
                            totalBytes += bytes;
                            if (mon.isCancelled()) {
                                throw new RuntimeException(new InterruptedIOException("transfer cancelled by user"));
                            }
                            long dt = Math.max( 1, System.currentTimeMillis() - t0 );
                            mon.setTaskProgress(totalBytes);
                            mon.setProgressMessage(totalBytes / 1000 + "KB read at " + (totalBytes / dt) + " KB/sec");

                        }

                        public void byteWrite(int bytes) {
                            totalBytes += bytes;
                            mon.setTaskProgress(totalBytes);
                        }
                    };
                    bean.getBinaryFile(ss[3].substring(ss[2].length()), partFile.toString(), observer);
                    bean.close();
                    done= true;
                    
                } catch (RuntimeException ex) {
                    ex.printStackTrace();
                    if (ex.getCause() instanceof IOException) {
                        throw (IOException) ex.getCause();
                    } else {
                        IOException tex= new IOException(ex.toString()); // TODO Java 1.6 will fix this
                        tex.initCause(ex);
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
                        throw new IOException(ex.getMessage()); //JAVA5
                    }

                } catch ( CancelledOperationException ex ) {
                    throw new FileSystemOfflineException("user cancelled credentials");
                }
            }

            if (copyFile(partFile, targetFile)) {
                partFile.delete();
            }

        } catch (IOException e) {
            if (out != null) {
                out.close();
            }
            if (is != null) {
                is.close();
            }
            partFile.delete();
            throw e;
            
        } finally {
            mon.finished();
            lock.unlock();
        }
        
    }

    @Override
    public FileObject getFileObject(String filename) {
        return new FtpFileObject(this, filename, new Date(System.currentTimeMillis()));
    }

    boolean delete(FtpFileObject aThis) throws IOException {
        FtpBean bean = new FtpBean();

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
        } catch (IOException iOException) {
            try {
                bean.close();
            } catch (FtpException ex) {
                Logger.getLogger(FTPBeanFileSystem.class.getName()).log(Level.SEVERE, null, ex);
            }
            return false;
        } catch (FtpException ftpException) {
            try {
                bean.close();
            } catch (FtpException ex) {
                Logger.getLogger(FTPBeanFileSystem.class.getName()).log(Level.SEVERE, null, ex);
            }
            return false;
        }
    }
}
