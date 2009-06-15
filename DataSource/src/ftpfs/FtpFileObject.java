/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ftpfs;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.FileSystem.DirectoryEntry;
import org.das2.util.filesystem.WebFileObject;
import org.das2.util.filesystem.WebFileSystem;

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
        File listing= new File( this.getLocalFile().getParent(), ".listing" );
        if ( listing.exists() ) {
            try {
                DirectoryEntry[] list = ftpfs.parseLsl(null, listing);
                int ii= this.getNameExt().lastIndexOf("/");
                String lookFor= this.getNameExt().substring(ii+1);
                for (int i = 0; i < list.length; i++) {
                    if (list[i].name.equals(lookFor)) {
                        return list[i].size;
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
            ftpfs.listDirectory( getParent().getNameExt() );
        }
        if ( listing.exists() ) {
            try {
                DirectoryEntry[] list = ftpfs.parseLsl(null, listing);
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
            FileSystem.getExceptionHandler().handleUncaught( new IOException("local listing file not found") );
            return false;
        }
    }


}
