/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
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
        Enumeration<URL> urls = AutoPlotMatisse.class.getClassLoader().getResources("META-INF/build.txt");
        
        List<String> result= new ArrayList<String>();
        
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();

            String jar= url.toString();
            
            System.err.println(jar);
            
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

    public static void main( String[] args ) throws IOException {
        getBuildInfos();
    }
}
