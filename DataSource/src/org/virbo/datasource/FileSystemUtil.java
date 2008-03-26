/*
 * FileSystemUtil.java
 *
 * Created on December 13, 2007, 6:59 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.datasource;

import org.das2.util.filesystem.FileSystem;

/**
 *
 * @author jbf
 */
public class FileSystemUtil {
    
    public static String getNameRelativeTo( FileSystem fs, String resource ) {
        String s= fs.getRootURL().toString();
        if ( resource.startsWith(s) ) return resource.substring(s.length()); else return resource;
    }
}
