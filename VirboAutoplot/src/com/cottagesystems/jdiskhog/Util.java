/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.cottagesystems.jdiskhog;

import java.io.File;
import java.io.IOException;

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
    
    public static void main( String[] args ) throws IOException {
        // verify handling of links
        File f= new File( "/home/jbf/.wine/dosdevices/c:/windows/profiles/jbf/My Documents");
        System.err.println(f.exists());
        System.err.println(f);
        System.err.println(f.getCanonicalFile());
    }
}
