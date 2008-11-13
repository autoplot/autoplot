/*
 * Util.java
 *
 * Created on November 6, 2007, 10:41 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.datasource;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author jbf
 */
public class DataSourceUtil {

    public static final URL FS_URL;
    static {
        try {
            FS_URL= new URL( "file:/" );
        } catch ( MalformedURLException ex ) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * remove escape sequences like %20 to create a human-editable string
     * @param s
     * @return
     */
    public static String unescape(String s) {
        try {
            s = URLDecoder.decode(s, "UTF-8");
            return s;
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    private DataSourceUtil() {
    }
    
    /**
     * interprets spec within the context of URL context.
     * @param context the context for the spec.  null may be used to
     *    indicate no context.
     * @param spec if spec is a fully specified URL, then it is used, otherwise
     *    it is appended to context.  If spec refers to the name of a file,
     *    but doesn't start with "file:/", "file:/" is appended.
     */
    public static URL newURL( URL context, String spec ) throws MalformedURLException {
        if ( context==null || spec.contains("://") || ( spec.startsWith("file:/") ) ) {
            if ( !( spec.startsWith("file:/") || spec.startsWith("ftp://") || spec.startsWith("http://") || spec.startsWith("https://") ) ) {
                spec= "file://"+ ( ( spec.charAt(0)=='/' ) ? spec : ( '/' + spec ) ); // Windows c:
            }
            return new URL(spec);
            
        } else {
            String contextString= context.toString();
            if ( !contextString.endsWith("/") ) contextString+= "/";
            if ( spec.startsWith("/") ) spec= spec.substring(1);
            return new URL( contextString + spec );
        }
    }
    
    /**
     * presents the spec for the url within a context.
     */
    public static String urlWithinContext( URL context, String url ) {
        String result;
        if ( url.startsWith( context.toString() ) ) {
            result= url.substring( context.toString().length() );
        } else {
            result= url;
        }
        return result;
    }
    
    /**
     * remove quotes from string, which pops up a lot in metadata
     */
    public static String unquote( String s ) {
        if ( s==null ) return null;
        if ( s.startsWith("\"") ) {
            s= s.substring(1);
        }
        if ( s.endsWith("\"") ) {
            s= s.substring(0,s.length()-1);
        }
        return s;
    }
    
    public static String makeAggregation( String surl ) {
        String yyyy= "/\\d{4}/";
        String yyyymmdd= "(?<!\\d)(\\d{8})(?!\\d)"; //"(\\d{8})";
        String version= "([Vv])\\d{2}";
        String result= surl;
        
        String timeRange;
        Matcher m= Pattern.compile(yyyymmdd).matcher(surl);
        if ( m.find() ) {
            timeRange= m.group(0);
        } else {
            return null;
        }

        result= result.replaceFirst(yyyy, "/\\$Y/");               
        result= result.replaceFirst(yyyymmdd, "\\$Y\\$m\\$d");
                
        result= result.replaceFirst(version, "$1..");
        
        return result 
                + ( result.contains("?") ? "&" : "?" )
                + "timerange="+timeRange;
    }
    
    /** 
     * make a valid Java identifier from the label.  Data sources may wish
     * to allow labels to be used to identify data sources, and this contains
     * the standard logic.  Strings are replaced with underscores, invalid
     * chars removed, etc.
     * @param label
     * @return valid Java identifier.
     */
    public static String toJavaIdentifier( String label ) {
	StringBuffer buf= new StringBuffer(label.length());
	for ( int i=0; i<label.length(); i++ ) {
	    char ch= label.charAt(i);
	    if ( Character.isJavaIdentifierPart(ch) ) {
		buf.append(ch);
	    } else if ( ch==' ' ) {
		buf.append("_");
	    }
	}
	return buf.toString();
    }
    
    public static void main(String[] args ) {
        String surl= "http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hyd_h0/2000/po_h0_hyd_20000109_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX";
        System.err.println( makeAggregation(surl) );
                
    }
}
