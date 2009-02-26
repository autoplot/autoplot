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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

/**
 *
 * @author jbf
 */
public class Util {

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
        Enumeration<URL> urls = AutoPlotUI.class.getClassLoader().getResources("META-INF/build.txt");

        List<String> result = new ArrayList<String>();

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

            String cvsTagName = props.getProperty("build.tag");
            String svnTag = svnTag( props.getProperty("build.svnurl"), 
                    props.getProperty("build.svnrevision"),
                    jarname );
            
            String tagName = tagName(svnTag, abbrevs);
            String version;
            if (cvsTagName == null || cvsTagName.length() <= 9) {
                version = "untagged_version";
            } else {
                version = cvsTagName.substring(6, cvsTagName.length() - 2);
            }

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

    public static void main(String[] args) throws IOException {
        getBuildInfos();
    }
}
