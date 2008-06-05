/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

/**
 *
 * @author jbf
 */
public class Util {

    /**
     * searches class path for META-INF/version.txt, returns nice strings
     * @return one line per jar
     */
    public static List<String> getBuildInfos() throws IOException {
        Enumeration<URL> urls = AutoPlotUI.class.getClassLoader().getResources("META-INF/build.txt");
        
        List<String> result= new ArrayList<String>();
        
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();

            String jar= url.toString();
                        
            int i= jar.indexOf(".jar");
            int i0= jar.lastIndexOf("/",i-1);
            
            String name;
            if ( i!=-1 ) {
                name= jar.substring(i0+1,i+4);
            } else {
                name= jar.substring(6);
            }
            
            Properties props= new Properties();
            props.load( url.openStream() );
            
            String cvsTagName = props.getProperty("build.tag");
            String version;
            if ( cvsTagName==null || cvsTagName.length() <= 9) {
                version = "untagged_version";
            } else {
                version = cvsTagName.substring(6, cvsTagName.length() - 2);
            }
            
            result.add( name + ": "+version+"("+props.getProperty("build.timestamp")+" "+props.getProperty("build.user.name")+")" );

        }
        return result;
    
    }
    
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
    

    public static String strjoin(Collection<String> c, String delim) {
        StringBuffer result = new StringBuffer();
        for (String s : c) {
            if (result.length() > 0) {
                result.append(delim);
            }
            result.append(s);
        }
        return result.toString();
    }
    
    public static void main( String[] args ) throws IOException {
        getBuildInfos();
    }
}
