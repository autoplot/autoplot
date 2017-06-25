/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.servlet;

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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.datasource.URISplit;

/**
 * Utilities for the servlets
 * @author jbf
 */
public class ServletUtil {

    private static final Logger logger= Logger.getLogger("autoplot.servlet");
    
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
    private static long whiteListLastModified= 0;
    
    /**
     * return the whitelist, checking no more than once per 5 seconds, and
     * creating the default file if one is not found.  
     * See HOME/autoplot_data/server/whitelist.txt
     * 
     * @return list of regular expressions to allow.
     * @throws java.io.IOException when the whitelist.txt cannot be written or read.
     */
    public static List<String> getWhiteList() throws IOException {
        long currentTimeMillis= System.currentTimeMillis();
        if ( currentTimeMillis-whiteListFresh<5000 ) return whiteList;
        File sd= getServletHome();
        File ff= new File( sd, "whitelist.txt" );
        if ( !ff.exists() ) {
            BufferedWriter w=null;
            try {
                w= new BufferedWriter( new FileWriter( ff ) );
                w.write("# list of whitelisted URIs regular expressions.  See http://autoplot.org/servlet_guide.\n");
                w.write("# http://autoplot.org/data.*  # uncomment to allow scripts from autoplot.org\n");                
                w.write("http://localhost(:\\d+)?/.*\n");
            } catch ( IOException ex ) {
                throw ex;
            } finally {
                if ( w!=null ) w.close();
            }
        }
        
        // the goal here is to avoid disk access which would slow down the server.  
        // whiteListFresh is the last time we checked the whitelist.
        long freshNow= ff.lastModified();
        if ( whiteList==null || freshNow!=whiteListLastModified  ) {
            synchronized ( ServletUtil.class ) { // Avoid synchronized block if the file is fresh
                if ( whiteList==null || freshNow!=whiteListLastModified  ) { 
                    List<String> local= new ArrayList(100);
                    BufferedReader r=null;
                    try {
                        logger.log(Level.FINE, "Reading whitelist from {0} ===", ff);
                        r= new BufferedReader( new FileReader( ff ) );
                        String s= r.readLine();
                        while ( s!=null ) {
                            logger.log(Level.FINE, "{0}", s);
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
                        logger.log(Level.FINE, "Done reading whitelist from {0} ===", ff);
                    } catch ( IOException ex ) {
                        throw ex; 
                    } finally {
                        if ( r!=null ) r.close();
                    }
                    whiteList= local;
                    whiteListFresh= freshNow;
                    whiteListLastModified= freshNow;
                } else {
                    logger.log(Level.FINE,"No need to read the whitelist, it hasn't been updated (synchronized block).");
                }
            }
        } else {
            logger.log(Level.FINE,"No need to read the whitelist, it hasn't been updated.");
        }
        return whiteList;
    }    
    
    /**
     * return true if the suri is whitelisted.
     * @param suri the uri.
     * @return true if the suri is whitelisted.
     * @throws IOException when the whitelist cannot be read.
     */
    public static boolean isWhitelisted(String suri) throws IOException {
        boolean whiteListed= false;
        URISplit split= URISplit.parse(suri);
        String ext= split.ext;
        if ( ext!=null ) ext= ext.substring(1); // remove . in .vap
        List<String> wl= getWhiteList();
        for ( String s: wl ) {
            if ( !whiteListed && Pattern.matches( s, suri ) ) {
                whiteListed= true;
                logger.log(Level.FINE, "uri is whitelisted, matching {0}", s);
            }
            if ( !whiteListed && ext!=null && !ext.equals("vap") && Pattern.matches( "vap\\+"+ext+":"+s, suri ) ) {
                whiteListed= true;
                logger.log(Level.FINE, "uri is whitelisted with implicit vap+ext, matching {0}", s);
            }
        }     
        return whiteListed;
    }
    
    /**
     * dump the whitelist to the logger at the given level.
     * @param level the level to log at.
     */
    public static void dumpWhitelistToLogger( Level level ) {
        if ( logger.isLoggable(level) ) {
            logger.log( level, "=== the whitelist ===" );
            for ( String s: whiteList ) {
                logger.log( level, s );
            }
            logger.log( level, "===" );
        }
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
