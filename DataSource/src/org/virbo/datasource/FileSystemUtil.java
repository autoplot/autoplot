/*
 * FileSystemUtil.java
 *
 * Created on December 13, 2007, 6:59 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.datasource;

import edu.uiowa.physics.pw.das.util.fileSystem.FileSystem;

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
