/*
 * FTPFileSystem.java
 *
 * Created on August 17, 2005, 3:33 PM
 *
 *
 */
package ftpfs;

import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.monitor.NullProgressMonitor;
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
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Jeremy
 */
public class FTPBeanFileSystem extends WebFileSystem {

    FTPBeanFileSystem(URL root) {
        super(root, localRoot(root));
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

    private String[] parseLsl(String dir, File listing) throws IOException {
        InputStream in = new FileInputStream(listing);

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));

        String aline = reader.readLine();

        boolean done = aline == null;

        String types = "d-";

        long bytesRead = 0;
        //long totalSize;
        //long sumSize=0;

        List result = new ArrayList(20);
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

                    result.add(name + (isFolder ? "/" : ""));

                //sumSize= sumSize + size;

                }

                aline = reader.readLine();
                lineNum++;

                done = aline == null;

            } // if (aline.length)

        } // while
        
        reader.close();
        //TODO: finally clause

        return (String[]) result.toArray(new String[result.size()]);
    }

    public String[] listDirectory(String directory) {
        directory = toCanonicalFolderName(directory);

        try {
            new File(localRoot, directory).mkdirs();
            File listing = new File(localRoot, directory + ".listing");
            if (!listing.canRead()) {

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
                FileWriter fw = new FileWriter(listing);
                fw.write(ss);
                fw.close();

            }
            listing.deleteOnExit();
            return parseLsl(directory, listing);

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

    protected void downloadFile(String filename, java.io.File targetFile, File partFile, final ProgressMonitor mon) throws java.io.IOException {
        FileOutputStream out = null;
        InputStream is = null;
        try {
            filename = toCanonicalFilename(filename);
            URL url = new URL(getRootURL(), filename.substring(1));

            String[] ss = FileSystem.splitUrl(url.toString());

            try {
                FtpBean bean = new FtpBean();
                bean.ftpConnect(url.getHost(), "ftp");
                bean.setDirectory(ss[2].substring(ss[1].length()));

                // TODO: list directories with getDirectoryContent() iterator.  cache the results of the listing by formatting
                //    .listing file.
                //this.getFileObject(filename).getSize();
                mon.setTaskSize(-1);
                mon.started();
                final long t0 = System.currentTimeMillis();

                FtpObserver observer = new FtpObserver() {

                    int totalBytes = 0;

                    public void byteRead(int bytes) {
                        totalBytes += bytes;
                        if (mon.isCancelled()) {
                            throw new RuntimeException("ftp download cancelled");
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
                mon.finished();
            } catch (RuntimeException ex) {
                throw new IOException(ex.getMessage());
            } catch (FtpException ex) {
                throw new IOException(ex.getMessage());

            }
            partFile.renameTo(targetFile);
        } catch (IOException e) {
            if (out != null) {
                out.close();
            }
            if (is != null) {
                is.close();
            }
            partFile.delete();
            throw e;
        }

    }
}
