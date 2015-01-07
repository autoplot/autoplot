/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.virbo.datasource.AutoplotSettings;

/**
 * Utilities for the servlets
 * @author jbf
 */
public class ServletUtil {

    public static int getIntParameter(HttpServletRequest request, String name, int dval) {
        String s = request.getParameter(name);
        if (s == null) return dval;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return dval;
        }
    }

    public static String getStringParameter(HttpServletRequest request, String name, String dval) {
        String s = request.getParameter(name);
        if (s == null) return dval;
        return s;

    }
   
    private static Map<String,String> idMap=null;
    private static long idMapFresh= 0;
    
    /**
     * return the id map, checking no more than once per 5 seconds, and
     * creating an empty file if one is not found.  
     * See HOME/autoplot_data/server/
     * 
     * @return map from one string to another.
     * @throws java.io.IOException
     */
    public static Map<String,String> getIdMap() throws IOException {
        long currentTimeMillis= System.currentTimeMillis();
        if ( currentTimeMillis-idMapFresh < 5000 ) return idMap;
        File sd= getServletHome();
        File ff= new File( sd, "ids.txt" );
        if ( !ff.exists() ) {
            try {
                BufferedWriter w= new BufferedWriter( new FileWriter( ff ) );
                w.write("# map from regex to local reference.  See http://autoplot.org/servlet_guide.");
                w.newLine();
                w.close();
            } catch ( IOException ex ) {
                throw ex;
            }
            idMapFresh= currentTimeMillis;
            idMap= new HashMap();
            return idMap;
        }
        long idMapFreshNow= ff.lastModified();
        if ( idMap==null || idMapFreshNow>idMapFresh ) {
            synchronized ( ServletUtil.class ) {
                if ( idMap==null || idMapFreshNow>idMapFresh ) { 
                    Map<String,String> idMapLocal= new LinkedHashMap<String, String>();
                    try {
                        BufferedReader r= new BufferedReader( new FileReader( ff ) );
                        String s= r.readLine();
                        while ( s!=null ) {
                            int i= s.indexOf("#");
                            if ( i>-1 ) s= s.substring(0,i);
                            s= s.trim();
                            if ( s.length()>0 ) {
                                String[] ss= s.split("\\s+");
                                if ( ss.length!=2 ) {
                                    System.err.println("skipping malformed line: "+s);
                                } else {
                                    idMapLocal.put(ss[0],ss[1]);
                                }
                            }
                            s= r.readLine();
                        }
                    } catch ( IOException ex ) {
                        throw ex; 
                    }
                    idMap= idMapLocal;
                    idMapFresh= currentTimeMillis;
                }
            }
        }
        return idMap;
    }
    
    private static List<String> whiteList=null;
    private static long whiteListFresh= 0;
    
    /**
     * return the id map, checking no more than once per 5 seconds, and
     * creating an empty file if one is not found.  
     * See HOME/autoplot_data/server/whitelist.txt
     * 
     * @return map whose keys are the whitelist
     * @throws java.io.IOException
     */
    public static List<String> getWhiteList() throws IOException {
        long currentTimeMillis= System.currentTimeMillis();
        if ( currentTimeMillis-whiteListFresh<5000 ) return whiteList;
        File sd= getServletHome();
        File ff= new File( sd, "whitelist.txt" );
        if ( !ff.exists() ) {
            try {
                BufferedWriter w= new BufferedWriter( new FileWriter( ff ) );
                w.write("# list of whitelisted URIs regular expressions.  See http://autoplot.org/servlet_guide.");
                w.newLine();
                w.close();
            } catch ( IOException ex ) {
                throw ex;
            }
            whiteListFresh= currentTimeMillis;
            whiteList= Collections.emptyList();
            return whiteList;
        }
        long freshNow= ff.lastModified();
        if ( whiteList==null || freshNow-whiteListFresh>5000 ) {
            synchronized ( ServletUtil.class ) {
                if ( whiteList==null || freshNow-whiteListFresh>5000 ) { 
                    List<String> local= new ArrayList<String>(100);
                    try {
                        BufferedReader r= new BufferedReader( new FileReader( ff ) );
                        String s= r.readLine();
                        while ( s!=null ) {
                            int i= s.indexOf("#");
                            if ( i>-1 ) s= s.substring(0,i);
                            s= s.trim();
                            if ( s.length()>0 ) {
                                String[] ss= s.split("\\s+");
                                if ( ss.length!=1 ) {
                                    System.err.println("skipping malformed line: "+s);
                                } else {
                                    local.add(ss[0]);
                                }
                            }
                            s= r.readLine();
                        }
                    } catch ( IOException ex ) {
                        throw ex; 
                    }
                    whiteList= local;
                    whiteListFresh= currentTimeMillis;
                }
            }
        }
        return whiteList;
    }    
    
    public static File getServletHome() {
        File apd= new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ) );
        if ( !apd.exists() ) {
            if ( !apd.mkdirs() ) {
                throw new IllegalArgumentException("unable to make autoplot directory: "+apd);
            }
        }
        File sd= new File( apd, "server" );
        if ( !sd.exists() ) {
            if ( !sd.mkdirs() ) {
                throw new IllegalArgumentException("unable to make server directory: "+sd);
            }
        }
        return sd;
    }
}
