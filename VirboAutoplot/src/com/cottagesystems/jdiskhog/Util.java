/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cottagesystems.jdiskhog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 *
 * @author jbf
 */
public class Util {

    /**
     * deletes all files and folders below root, and root, just as "rm -r" would.
     * TODO: check links
     * @throws IllegalArgumentException if it is unable to delete a file
     * @return true if the operation was successful.
     */
    public static boolean deleteFileTree(File root) throws IllegalArgumentException {
        if (!root.exists()) {
            return true;
        }
        File[] children = root.listFiles();
        boolean success = true;
        for (int i = 0; i < children.length; i++) {
            if (children[i].isDirectory()) {
                success = success && deleteFileTree(children[i]);
            } else {
                success = success && children[i].delete();
                if (!success) {
                    throw new IllegalArgumentException("unable to delete file " + children[i]);
                }
            }
        }
        success = success && (!root.exists() || root.delete());
        if (!success) {
            throw new IllegalArgumentException("unable to delete folder " + root);
        }
        return success;
    }

    public static void fileCopy( File src, File dst ) throws FileNotFoundException, IOException {
        if ( src.isDirectory() && dst.isDirectory() ) {
            File dst1= new File( dst, src.getName() );
            dst1.mkdir();
            dst= dst1;
            File[] files= src.listFiles();
            for ( File f:files ) {
                if ( f.isDirectory() ) {
                    dst1= new File( dst, f.getName() );
                    dst1.mkdir();
                } else {
                    dst1= dst;
                }
                fileCopy( f, dst1 );
            }
            return;
        } else if ( dst.isDirectory() ) {
            dst= new File( dst, src.getName() );
        }
        FileChannel ic = new FileInputStream(src).getChannel();
        FileChannel oc = new FileOutputStream(dst).getChannel();
        ic.transferTo(0, ic.size(), oc);
        ic.close();
        oc.close();
    }

    public static void main(String[] args) throws IOException {
        // verify handling of links
        File f = new File("/home/jbf/.wine/dosdevices/c:/windows/profiles/jbf/My Documents");
        System.err.println(f.exists());
        System.err.println(f);
        System.err.println(f.getCanonicalFile());
    }
}
