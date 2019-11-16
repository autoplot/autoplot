
package org.autoplot.servlet;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.datasource.FileSystemUtil;
import org.autoplot.datasource.URISplit;
import org.das2.util.FileUtil;

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
            try (BufferedWriter w = new BufferedWriter( new FileWriter( ff ) )) {
                w.write("# map from regex to local reference.  See http://autoplot.org/servlet_guide.");
                w.newLine();
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
                    Map<String,String> idMapLocal= new LinkedHashMap<>();
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
     * return true if the suri is whitelisted, meaning we trust that 
     * content from this address will not harm the server.
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

    public static class SecurityResponse {
        boolean whiteListed;
        String suri;
        String id;
    }
    
    /**
     * This checks the whitelist for the URI, and also inserts headers into the response.
     * @param response
     * @param id null or the id, which is mapped to a URI.
     * @param suri null or the uri.
     * @param vap null or the vap
     * @return true if the URI is whitelisted
     * @throws UnknownHostException
     * @throws IOException 
     */
    public static SecurityResponse checkSecurity( HttpServletResponse response, String id, String suri, String vap ) throws UnknownHostException, IOException {
        // To support load balancing, insert the actual host that resolved the request
        String host= java.net.InetAddress.getLocalHost().getCanonicalHostName();
        response.setHeader( "X-Served-By", host );
        response.setHeader("X-Server-Version", ServletInfo.version);
        if ( suri!=null ) {
            response.setHeader( "X-Autoplot-URI", suri );
        }
        if ( id!=null ) {
            response.setHeader( "X-Autoplot-ID", id );
        }

        // id lookups.  The file id.txt is a flat file with hash comments,
        // with each record containing a regular expression with groups, 
        // then a map with group ids.
        if ( id!=null ) {
            suri= null;
            Map<String,String> ids= ServletUtil.getIdMap();
            for ( Map.Entry<String,String> e : ids.entrySet() ) {
                Pattern p= Pattern.compile(e.getKey());
                Matcher m= p.matcher(id);
                if ( m.matches() ) {
                    suri= e.getValue();
                    for ( int i=1; i<m.groupCount()+1; i++ ) {
                        String r= m.group(i);
                        if ( r.contains("..") ) {
                            throw new IllegalArgumentException(".. (up directory) is not allowed in id.");
                        }
                        suri= suri.replaceAll( "\\$"+i, r ); // I know there's a better way to do this.
                    }
                    if ( suri.contains("..") ) {
                        throw new IllegalArgumentException(".. (up directory) is not allowed in the result of id: "+suri);
                    }
                }
            }
            if ( suri==null ) {
                throw new IllegalArgumentException("unable to resolve id="+id);
            }
        }

        boolean whiteListed= false;
        if ( suri!=null ) {
            whiteListed= ServletUtil.isWhitelisted(suri);
            if ( !whiteListed ) {
                logger.log(Level.FINE, "uri is not whitelisted: {0}", suri);                    
                ServletUtil.dumpWhitelistToLogger(Level.FINE);
            }
        }
        if ( vap!=null ) {
            whiteListed= ServletUtil.isWhitelisted(vap);
            if ( !whiteListed ) {
                logger.log(Level.FINE, "vap is not whitelisted: {0}", vap);
                ServletUtil.dumpWhitelistToLogger(Level.FINE);
            }
            //TODO: there may be a request that the URIs within the vap are 
            //verified to be whitelisted.  This is not done.
        }

        // Allow a little caching.  See https://devcenter.heroku.com/articles/increasing-application-performance-with-http-cache-headers
        // public means multiple browsers can use the same cache, maybe useful for workshops and seems harmless.
        // max-age means the result is valid for the next 10 seconds.  
        response.setHeader( "Cache-Control", "public, max-age=10" );  
        DateFormat httpDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        httpDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        response.setHeader( "Expires", httpDateFormat.format( new Date( System.currentTimeMillis()+10000 ) ) );

        SecurityResponse result= new SecurityResponse();
        result.whiteListed= whiteListed;
        result.suri= suri;
        result.id= id;
        
        return result;
    }
    
    /**
     * this is the part that throws the exception if security violation occurs.
     * @param sr 
     */
    public static void securityCheckPart2( SecurityResponse sr ) {
        File data= new File( ServletUtil.getServletHome(), "data" );
        if ( !data.exists() ) {
            if ( !data.mkdirs() ) {
                throw new IllegalArgumentException("Unable to make servlet data directory");
            }
        }
        String suri= URISplit.makeAbsolute( data.getAbsolutePath(), sr.suri );

        URISplit split = URISplit.parse(suri);                

        if ( sr.id==null ) { // id!=null indicates that the surl was generated within the server.
            if ( sr.whiteListed ) {

            } else {
                if ( FileSystemUtil.isLocalResource(sr.suri) ) {
                    File p= new File(data.getAbsolutePath());
                    File f= new File(split.file.substring(7));
                    if ( FileUtil.isParent( p, f ) ) {
                        logger.log(Level.FINE, "file within autoplot_data/server/data folder is allowed");
                        logger.log(Level.FINE, "{0}", suri);
                    } else {
                        // See http://autoplot.org/developer.servletSecurity for more info.
                        logger.log(Level.FINE, "{0}", suri);
                        throw new IllegalArgumentException("local resources cannot be served, except via local vap file.  ");

                    }
                } else {
                    if ( split.file!=null && split.file.contains("jyds") || ( split.vapScheme!=null && split.vapScheme.equals("jyds") ) ) {
                        File sd= ServletUtil.getServletHome();
                        File ff= new File( sd, "whitelist.txt" );
                        logger.log(Level.FINE, "non-local .jyds scripts are not allowed.");
                        logger.log(Level.FINE, "{0}", suri);
                        throw new IllegalArgumentException("non-local .jyds scripts are not allowed.  Administrators may wish to whitelist this data, see "+ff+", which does not include a match for "+suri); //TODO: this server file reference should be removed.
                    }
                }

                if ( split.vapScheme!=null && split.vapScheme.equals("vap+inline") && split.surl.contains("getDataSet") ) { // this list could go on forever...
                    throw new IllegalArgumentException("vap+inline URI cannot contain getDataSet.");
                }
            }
        }        
    }
    
    //private static String contact= null;
    
    /**
     * return the contact info for the server
     * @return 
     */
    public static synchronized String getServletContact() {
        String contact=null;
        if ( contact==null ) {
            try {
                File servletHome = getServletHome();
                File contactInfo= new File( servletHome, "contact.txt" );
                if ( contactInfo.exists() ) {
                    String s= FileUtil.readFileToString(contactInfo);
                    String[] ss= s.split("\n");
                    contact= ss[0];                    
                } else {
                    FileUtil.writeStringToFile( contactInfo, "???" );
                    contact= "???";
                }
            } catch (IOException ex) {
                contact= "???";
            }
        } 
        return contact;
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
