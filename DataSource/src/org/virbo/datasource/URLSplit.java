package org.virbo.datasource;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Class for containing the elemental parts of a URI, and utility
 * routines for working with URIs.
 * @author jbf
 */
public class URLSplit {

    public String scheme;
    public String authority;
    public String path;
    public String file;
    public String ext;
    public String params;

    /**
     * add "file:/" to a resource string that appears to reference the local filesystem.
     * 
     * @param surl
     * @return surl, maybe with "file:/" prepended.
     */
    public static String maybeAddFile(String surl) {
        if (surl.length() == 0) {
            return "file:/";
        }
        String scheme;  // identify the scheme, if any.
        int i0 = surl.indexOf(":");
        if (i0 == -1) {
            scheme = "";
        } else if (i0 == 1) { // one letter scheme is assumed to be windows drive letter.
            scheme = "";
        } else {
            scheme = surl.substring(0, i0);
        }

        if (scheme.equals("")) {
            surl = "file://" + ((surl.charAt(0) == '/') ? surl : ('/' + surl)); // Windows c:
            surl = surl.replaceAll("\\\\", "/");
            surl = surl.replaceAll(" ", "%20");
        }

        return surl;
    }
    
    /**
     * split the url string (http://www.example.com/data/myfile.nc?myVariable) into:
     *   path, the directory with http://www.example.com/data/
     *   file, the file, http://www.example.com/data/myfile.nc
     *   ext, the extenion, .nc
     *   params, myVariable or null
     */
    public static URLSplit parse(String surl) {

        surl = maybeAddFile(surl);

        int h = surl.indexOf(":/");
        String scheme = surl.substring(0, h);

        URL url = null;
        try {
            if (scheme.contains(".")) {
                int j = scheme.indexOf(".");

                url = new URL(surl.substring(j + 1));
            } else {
                url = new URL(surl);
            }
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
            return null;
        }

        int i;
        
        String authority;
        if ( scheme.endsWith("file") ) {
            authority= null;
        } else {
            i= scheme.length()+":/".length();
            while ( i<surl.length() && surl.charAt(i)=='/' ) i++;
            i= surl.indexOf("/",i);
            authority= i==-1 ? surl : surl.substring(0,i);
        }
        
        String file = url.getPath();
        i = file.lastIndexOf(".");
        String ext = i == -1 ? "" : file.substring(i);

        String params = null;

        int fileEnd;
        // check for just one ?
        i = surl.indexOf("?");
        if (i != -1) {
            fileEnd = i;
            params = surl.substring(i + 1);
            i = surl.indexOf("?", i + 1);
            if (i != -1) {
                throw new IllegalArgumentException("too many ??'s!");
            }
        } else {
            fileEnd = surl.length();
        }

        i = surl.lastIndexOf("/");
        String surlDir = surl.substring(0, i);

        int i2 = surl.indexOf("://");

        URLSplit result = new URLSplit();
        result.scheme = scheme;
        result.authority= authority;
        result.path = surlDir + "/";
        result.file = surl.substring(0, fileEnd);
        result.ext = ext;
        result.params = params;

        return result;


    }
    
    private static int indexOf(String s, char ch, char ignoreBegin, char ignoreEnd) {
        int i = s.indexOf(ch);
        int i0 = s.indexOf(ignoreBegin);
        int i1 = s.indexOf(ignoreEnd);
        if (i != -1 && i0 < i && i < i1) {
            i = -1;
        }
        return i;
    }    

    /**
     *
     * split the parameters into name,value pairs. URLEncoded parameters are decoded, but the string may be decoded 
     * already.
     *
     * items without equals (=) are inserted as "arg_N"=name.
     */
    public static LinkedHashMap<String, String> parseParams(String params) {
        LinkedHashMap<String,String> result = new LinkedHashMap<String,String>();
        if (params == null) {
            return result;
        }
        if (params.trim().equals("")) {
            return result;
        }
        
        params= URLSplit.urlDecode(params);
        
        String[] ss = params.split("&");

        int argc = 0;

        for (int i = 0; i < ss.length; i++) {
            int j = indexOf(ss[i], '=', '(', ')');
            String name, value;
            if (j == -1) {
                name = ss[i];
                value = "";
                result.put("arg_" + (argc++), name);
            } else {
                name = ss[i].substring(0, j);
                value = ss[i].substring(j + 1);
                result.put(name, value);
            }
        }
        return result;
    }

    /**
     * spaces and other URI syntax elements are URL-encoded.
     * @param parms
     * @return
     */
    public static String formatParams(Map parms) {
        StringBuffer result = new StringBuffer("");
        for (Iterator i = parms.keySet().iterator(); i.hasNext();) {
            String key = (String) i.next();
            if (key.startsWith("arg_")) {
                if (!parms.get(key).equals("")) {
                    result.append("&" + parms.get(key));
                }
            } else {
                String value = (String) parms.get(key);
                if (value != null) {
                    result.append("&" + key + "=" + urlEncode(value) );
                } else {
                    result.append("&" + key);
                }
            }
        }
        return (result.length() == 0) ? "" : result.substring(1);
    }

    
    public static String format(URLSplit split) {
        String result = split.file;
        if (split.params != null) {
            result += "?" + split.params;
        }
        return result;
    }
    
    /**
     * convert " " to "%20", etc by using URLEncoder, maybe catching the UnsupportedEncodingException.
     * @param s
     * @return
     */
    public static String urlEncode( String s ) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * convert "%20" to " ", etc by using URLDecoder, maybe catching the UnsupportedEncodingException.
     * Kludge to check for and
     * decode pluses (+) in an otherwise unencoded string, also we have to be careful for elements like %Y than are
     * not to be decoded.
     * @param s
     * @return
     */
    public static String urlDecode( String s ) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch ( IllegalArgumentException ex ) {
            return s.replaceAll("\\+"," ");
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    public String toString() {
        return path + "\n" + file + "\n" + ext + "\n" + params;
    }
}
