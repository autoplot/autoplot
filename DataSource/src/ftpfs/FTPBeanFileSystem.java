/*
 * FTPFileSystem.java
 *
 * Created on August 17, 2005, 3:33 PM
 *
 *
 */
package ftpfs;

import java.text.ParseException;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.filesystem.*;
import ftpfs.ftp.FtpBean;
import ftpfs.ftp.FtpException;
import ftpfs.ftp.FtpListResult;
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
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.system.MutatorLock;

/**
 *
 * @author Jeremy
 */
public class FTPBeanFileSystem extends WebFileSystem {

    String userHost;

    FTPBeanFileSystem(URL root) {
        super(root, localRoot(root));
        userHost= root.getUserInfo();
    }

    /* dumb method looks for / in parent directory's listing */
    public boolean isDirectory(String filename) {
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
        logger.finer( "ftpBeanFilesystem copyFile(" + partFile + ","+ targetFile );
        WritableByteChannel dest = Channels.newChannel(new FileOutputStream(targetFile));
        ReadableByteChannel src = Channels.newChannel(new FileInputStream(partFile));
        final ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024);
        while (src.read(buffer) != -1) {
            // prepare the buffer to be drained
            buffer.flip();
            // write to the channel, may block
            dest.write(buffer);
            // If partial transfer, shift remainder down
            // If buffer is empty, same as doing clear()
            buffer.compact();
        }
        // EOF will leave buffer in fill state
        buffer.flip();
        // make sure the buffer is fully drained.
        while (buffer.hasRemaining()) {
            dest.write(buffer);
        }
        dest.close();
        src.close();
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
                    String name = aline.substring(i + 1);
                    long size = 0;
                    //try {
                    //size= Long.parseLong( aline.substring( 31, 31+11 ) ); // tested on: linux server
                    //} catch ( NumberFormatException e ) {
                    //}

                    boolean isFolder = type == 'd';

                    DirectoryEntry item = new DirectoryEntry();
                    item.name = aline.substring(i + 1);
                    try {
                        item.size = Long.parseLong(aline.substring(31, 31 + 11).trim());
                    } catch ( NumberFormatException ex ) {
                        item.size = Long.parseLong(aline.substring(31, 31 + 10).trim());
                    }
                    item.type = type == 'd' ? 'd' : 'f';
                    item.modified = parseTime1970(aline.substring(42, 54), Calendar.getInstance());

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

    public String[] listDirectory(String directory) {
        directory = toCanonicalFolderName(directory);

        try {
            new File(localRoot, directory).mkdirs();
            File listing = new File(localRoot, directory + ".listing");
            if (!listing.canRead()) {

                FtpBean bean = new FtpBean();
                try {
                    if ( userHost!=null ) {
                        String[] userHostArr= userHost.split(":");
                        bean.ftpConnect(getRootURL().getHost(), userHostArr[0], userHostArr[1]);
                        String cwd= bean.getDirectory();
                        bean.setDirectory( cwd + getRootURL().getPath() + directory.substring(1) );
                    } else {
                        bean.ftpConnect(getRootURL().getHost(), "ftp");
                        String cwd= bean.getDirectory();
                        bean.setDirectory( cwd + getRootURL().getPath() + directory.substring(1));
                    }
                } catch (NullPointerException ex) {
                    throw new IOException("Unable to make connection to " + getRootURL().getHost());
                }

                FtpObserver observer = new FtpObserver() {

                    int totalBytes = 0;

                    public void byteRead(int bytes) {
                        totalBytes += bytes;
                    //mon.setTaskProgress( totalBytes );
                    }

                    public void byteWrite(int bytes) {
                        totalBytes += bytes;
                    //mon.setTaskProgress( totalBytes );
                    }
                };

                String ss = bean.getDirectoryContentAsString();
                FileWriter fw = new FileWriter(listing);
                fw.write(ss);
                fw.close();

            }
            listing.deleteOnExit();

            DirectoryEntry[] des = parseLsl(directory, listing);
            String[] result = new String[des.length];
            for (int i = 0; i < des.length; i++) {
                result[i] = des[i].name + (des[i].type == 'd' ? "/" : "");
            }
            return result;

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (FtpException e) {
            return new String[]{""};
        }
    }

    public String[] listDirectoryOld(String directory) {
        try {
            directory = toCanonicalFolderName(directory);

            FtpBean bean = new FtpBean();
            bean.ftpConnect(getRootURL().getHost(), "ftp");
            bean.setDirectory(getRootURL().getPath() + directory.substring(1));

            FtpObserver observer = new FtpObserver() {

                int totalBytes = 0;

                public void byteRead(int bytes) {
                    totalBytes += bytes;
                //mon.setTaskProgress( totalBytes );
                }

                public void byteWrite(int bytes) {
                    totalBytes += bytes;
                //mon.setTaskProgress( totalBytes );
                }
            };

            String ss = bean.getDirectoryContentAsString();

            FtpListResult list = bean.getDirectoryContent();

            ArrayList result = new ArrayList();

            while (list.next()) {
                String s = list.getName();
                result.add(s);
            }

            return (String[]) result.toArray(new String[result.size()]);
        } catch (IOException e) {
            return new String[]{""};
        } catch (FtpException e) {
            return new String[]{""};
        }

    }

    protected void downloadFile( String filename, File targetFile, File partFile, final ProgressMonitor mon) throws java.io.IOException {

        MutatorLock lock= getDownloadLock( filename, targetFile, mon );

        if ( lock==null ) return;

        logger.fine("ftpfs downloadFile(" + filename + ")");
        
        FileOutputStream out = null;
        InputStream is = null;
        try {
            filename = toCanonicalFilename(filename);
            URL url = new URL(getRootURL(), filename.substring(1));

            String[] ss = FileSystem.splitUrl(url.toString());

            try {
                FtpBean bean = new FtpBean();
                bean.ftpConnect(url.getHost(), "ftp");
                String cwd= bean.getDirectory();
                bean.setDirectory( cwd + ss[2].substring(ss[1].length()));

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
                        long dt = System.currentTimeMillis() - t0;
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
                mon.finished();
            } catch (RuntimeException ex) {
                ex.printStackTrace();
                if (ex.getCause() instanceof IOException) {
                    throw (IOException) ex.getCause();
                } else {
                    throw new IOException(ex);
                }
            } catch (FtpException ex) {
                throw new IOException(ex.getMessage());

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

            lock.unlock();
        }
        
    }

    @Override
    public FileObject getFileObject(String filename) {
        return new FtpFileObject(this, filename, new Date(System.currentTimeMillis()));
    }
}
