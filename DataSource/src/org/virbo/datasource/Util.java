/*
 * Util.java
 *
 * Created on November 6, 2007, 10:41 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.datasource;

import java.net.MalformedURLException;
import java.net.URL;

/**
 *
 * @author jbf
 */
public class Util {

    public static final URL FS_URL;
    static {
        try {
            FS_URL= new URL( "file:/" );
        } catch ( MalformedURLException ex ) {
            throw new RuntimeException(ex);
        }
    }
    
    private Util() {
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
        if ( s.startsWith("\"") ) {
            s= s.substring(1);
        }
        if ( s.endsWith("\"") ) {
            s= s.substring(0,s.length()-1);
        }
        return s;
    }
    
}
