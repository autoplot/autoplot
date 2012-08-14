/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ftpfs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.FileSystem.DirectoryEntry;
import org.das2.util.filesystem.WebFileObject;
import org.das2.util.filesystem.WebFileSystem;
import org.das2.util.filesystem.WriteCapability;
import org.das2.util.monitor.NullProgressMonitor;

/**
 *
 * @author jbf
 */
public class FtpFileObject extends WebFileObject {
    
    FTPBeanFileSystem ftpfs;
    
    protected FtpFileObject( WebFileSystem wfs, String pathname, Date modifiedDate ) {
        super( wfs, pathname, modifiedDate );
        this.ftpfs= (FTPBeanFileSystem)wfs;
    }

    @Override
    public long getSize() {
        boolean tinyGzFib= true; // return the .gz file size, assuming the server will send it.
        File listing= new File( this.getLocalFile().getParent(), ".listing" );
        if ( listing.exists() ) {
            try {
                DirectoryEntry[] list = ftpfs.parseLslNew(null, listing);
                int ii= this.getNameExt().lastIndexOf("/");
                String lookFor= this.getNameExt().substring(ii+1);
                String lookForGz= this.getNameExt().substring(ii+1) + ".gz";
                for (int i = 0; i < list.length; i++) {
                    if (list[i].name.equals(lookFor) ) {
                        return list[i].size;
                    } else if ( tinyGzFib && list[i].name.equals(lookForGz) ) {
                        System.err.println("approximating size of gzipped file "+list[i].name+ "when it is uncompressed");
                        return 2000*Math.round(list[i].size/2000.) * 5; // approx
                    }
                }
                return -1;
            } catch (IOException ex) {
                Logger.getLogger(FtpFileObject.class.getName()).log(Level.SEVERE, null, ex);
                return -1;
            }
        } else {
            return -1;
        }
        
    }

    @Override
    public boolean exists() {
        if ( getLocalFile()!=null && getLocalFile().exists() ) {
            return true;
        }

        File listing= new File( this.getLocalFile().getParent(), ".listing" );
        if ( !listing.exists() ) {
            try {
                ftpfs.listDirectory(getParent().getNameExt());
            } catch (IOException ex) {
                Logger.getLogger(FtpFileObject.class.getName()).log(Level.SEVERE, null, ex);
                return false;
            }
        }
        if ( listing.exists() ) {
            try {
                DirectoryEntry[] list = ftpfs.parseLslNew(null, listing);
                int ii= this.getNameExt().lastIndexOf("/");
                String lookFor= this.getNameExt().substring(ii+1);
                for (int i = 0; i < list.length; i++) {
                    if (list[i].name.equals(lookFor)) {
                        return true;
                    }
                }
                return false;
            } catch (IOException ex) {
                Logger.getLogger(FtpFileObject.class.getName()).log(Level.SEVERE, null, ex);
                return false;
            }
        } else {
            if ( !ftpfs.isOffline() ) {
                FileSystem.getExceptionHandler().handleUncaught( new IOException("local listing file (.listing) not found") );
            }
            return false;
        }
    }

    WriteCapability write= new WriteCapability() {
        public OutputStream getOutputStream() throws IOException {
            return FtpFileObject.this.getOutputStream(false);
        }
        public boolean canWrite() {
            return true; //TODO: implement this
        }
        public synchronized boolean delete() throws IOException {
            // we need to remove cache of listing
            File listingFile= new File( getLocalFile().getParent(), ".listing" );
            if ( listingFile.exists() ) {
                ftpfs.resetListCache( getParent().getNameExt() );
            }
            File localFile= getLocalFile();
            if ( localFile.exists() ) {
                if ( !localFile.delete() ) {
                    throw new IOException( "unable to delete local file "+localFile );
                }
            }
            return FtpFileObject.this.ftpfs.delete(FtpFileObject.this);
        }
    };

    @Override
    public <T> T getCapability(Class<T> clazz) {
        if ( clazz==WriteCapability.class ) {
            return (T) write;
        } else {
            return super.getCapability(clazz);
        }
    }

    /**
     * returns an output stream that writes to a local file in the file cache, then
     * sends over the result when it is closed.  So this will not work with applets,
     * but this is no big deal.
     *
     * Note that the file MUST BE CLOSED.  This is where it is uploaded to the FTP server.
     * 
     * @param append Append to the remote file.  We append to the local copy before uploading it.
     * @return
     */
    public OutputStream getOutputStream( boolean append ) throws IOException {
        try {
            getFile( new NullProgressMonitor() );
        } catch ( IOException ex ) {

        }
        if ( !append ) {
            if ( exists() ) throw new IllegalArgumentException("file exists in file system already!");
        }

        try {
            return new FileOutputStream(getLocalFile(),append) {
                @Override
                public void close() throws IOException {
                    super.close();
                    ftpfs.uploadFile( getNameExt(), getLocalFile(), new NullProgressMonitor() );
                }
            };
        } catch ( IOException ex ) {
            throw ex;
        }

    }

}
