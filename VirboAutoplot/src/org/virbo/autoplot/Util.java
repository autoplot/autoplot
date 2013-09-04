/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.AboutUtil;
import org.virbo.datasource.DataSourceUtil;

/**
 *
 * @author jbf
 */
public class Util {

    public static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot");

    private static String tagName(String svnURL, Map<String, String> abbrevs) {
        if ( svnURL==null ) return "untagged";
        String root= svnURL;
        int i = root.lastIndexOf("/");
        if (i == -1) {
            return root;
        }
        int i2= root.lastIndexOf("/",i-1);
        String dir = root.substring(i2+1,i);
        root = root.substring(0, i2+1);
        String abbrev = abbrevs.get(root);
        if (abbrev == null) {
            int j = abbrevs.size() + 1;
            abbrevs.put(root, "[" + j + "]");
            abbrev = abbrevs.get(root);
        }
        return abbrev + " "+ dir;
    }

    private static String popDollarValue(String propval) {
        int i = propval.indexOf("$");
        if (i == -1) {
            return "";
        }
        i = propval.indexOf(":", i + 1);
        int i2 = propval.indexOf("$", i + 1);
        if (i2 == -1) {
            return "";
        }
        return propval.substring(i + 1, i2).trim();
    }

    private static String svnTag( String urlprop, String revprop, String jarName ) {
        if ( urlprop==null || revprop==null ) return null;
        String suffix = "src/META-INF/build.txt";
        urlprop = popDollarValue(urlprop);
        revprop = popDollarValue(revprop);
        if ( urlprop.length()==0 || revprop.length()==0 ) return null;
        if (urlprop.endsWith(suffix)) {
            urlprop = urlprop.substring(0, urlprop.length() - suffix.length());
        }
        jarName= jarName + "/";
        if ( urlprop.endsWith(jarName) ) urlprop= urlprop.substring(0, urlprop.length() - jarName.length() ) ;
        
        return urlprop + "@" + revprop;
    }

    /**
     * searches class path for META-INF/version.txt, returns nice strings
     * @return one line per jar
     */
    public static List<String> getBuildInfos() throws IOException {
        Enumeration<URL> urls = Util.class.getClassLoader().getResources("META-INF/build.txt");

        List<String> result = new ArrayList<String>();

        String buildTime = "???";
        java.net.URL buildURL = AboutUtil.class.getResource("/buildTime.txt");
        if (buildURL != null) {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(buildURL.openStream()));
                buildTime = reader.readLine();
                reader.close();
            } catch (IOException ex) {
                logger.log( Level.WARNING, null, ex );
            }
        }
        if ( buildTime!=null && !buildTime.equals("???") ) {
            result.add("build time: "+buildTime);
        }

        LinkedHashMap<String, String> abbrevs = new LinkedHashMap<String, String>();

        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();

            String jar = url.toString();

            int i = jar.indexOf(".jar");
            int i0 = jar.lastIndexOf("/", i - 1);

            String name;
            String jarname;
            if (i != -1) {
                name = jar.substring(i0 + 1, i + 4);
                jarname= name.substring(0,name.length()-4);
            } else {
                name = jar.substring(6);
                jarname= name;
            }

            Properties props = new Properties();
            props.load(url.openStream());

            String svnTag = svnTag( props.getProperty("build.svnurl"), 
                    props.getProperty("build.svnrevision"),
                    jarname );

            String tagName = tagName(svnTag, abbrevs);

            result.add(name + ": " + tagName + " (" + props.getProperty("build.timestamp") + " " + props.getProperty("build.user.name") + ")");

        }

        for ( Entry<String,String> val : abbrevs.entrySet()) {
            result.add("" + val.getValue() + " " + val.getKey() );
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
                success = success && ( !children[i].exists() || children[i].delete() );
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

    /**
     * copy a file.  This will probably always returns true or an exception, but check the status to be sure.
     * @param srcf the source file
     * @param dstf the destination file
     * @return true if successful.
     * @throws IOException 
     */
    public static boolean copyFile( File srcf, File dstf ) throws IOException {
        WritableByteChannel dest = Channels.newChannel(new FileOutputStream(dstf));
        ReadableByteChannel src = Channels.newChannel(new FileInputStream(srcf));
        DataSourceUtil.transfer(src, dest);
        return true;
    }
    
    /**
     * copy a branch of files and folders.
     * @param root
     * @param dst
     * @return true if successful.
     */
    public static boolean copyFileTree( File root, File dst ) {
        try {
            String roots= root.getCanonicalPath()+"/";
            String dsts= dst.getCanonicalPath()+"/";
            if ( dsts.startsWith(roots ) ) {
                throw new IllegalArgumentException("can't move files into child of original");
            }
            if ( roots.startsWith(dsts ) ) {
                throw new IllegalArgumentException("can't move files to parent of original");
            }
        } catch (IOException ex ) {
            throw new RuntimeException(ex);
        }

        if (!root.exists()) {
            return false; // doesn't exist
        }
        File[] children = root.listFiles();
        boolean success = true;

        if ( !dst.exists() && !dst.mkdirs() ) {
            throw new IllegalArgumentException( "unable to make directory "+dst );
        }
        for (int i = 0; i < children.length; i++) {
            if (children[i].isDirectory()) {
                success = success && copyFileTree( children[i],new File(dst,children[i].getName()) );
                if (!success) {
                    copyFileTree( children[i],new File(dst,children[i].getName()) );
                    throw new IllegalArgumentException("unable to move file " + children[i]);
                }
            } else {
                try {
                    success = success && Util.copyFile( children[i], new File( dst, children[i].getName() ) );
                    if (!success) {
                        throw new IllegalArgumentException("unable to move file " + children[i]);
                    }
                } catch ( IOException ex ) {
                    IllegalArgumentException ex2= new IllegalArgumentException("unable to move file " + children[i],ex);
                    throw ex2;
                }
            }
        }
        return success;
    }
    
    /**
     * remove empty branches from file tree.  This is like "rm -r $root"
     * @param root the root directory from which to start a search.
     * @param problems any files which could not be deleted are listed here.
     * @return true if successful.
     */
    public static boolean pruneFileTree( File root, List<String> problems ) {
        if (!root.exists()) {
            return false; // doesn't exist
        }
        File[] children = root.listFiles();
        boolean success = true;

        for (int i = 0; i < children.length; i++) {
            if (children[i].isDirectory()) {
                success = success && pruneFileTree( children[i], problems );
            }
        }
        
        children = root.listFiles();
        if ( children.length==0 ) {
            if ( !root.delete() ) {
                problems.add( "unable to delete "+root );
                return false;
            } else {
                return true;
            }
        } else {
            return true;
        }

    }
    

    public static String strjoin(Collection<String> c, String delim) {
        StringBuilder result = new StringBuilder();
        for (String s : c) {
            if (result.length() > 0) {
                result.append(delim);
            }
            result.append(s);
        }
        return result.toString();
    }

//    public static void main(String[] args) throws IOException {
//        copyFileTree( new File("/home/jbf/temp/foo"), new File("/home/jbf/temp/foo2/" ));
//        getBuildInfos();
//    }
}
