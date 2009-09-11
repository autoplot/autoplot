package org.virbo.datasource;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class for containing the elemental parts of a URI, and utility
 * routines for working with URIs.
 * @author jbf
 */
public class URLSplit {

    /**
     * scheme for Autoplot, if provided.  e.g.  vap+cdf.  If not provided,
     * then "vap:" is implicit.
     */
    public String vapScheme;
    /**
     * scheme for resource, e.g. jdbc.mysql
     */
    public String scheme;
    /**
     * the complete, modified surl.   file:///home/jbf/mydata.qds
     * this is the resource name, and doesn't contain the vapScheme.
     */
    public String surl;
    /**
     * the resource that is handled by the DataSource.  This may be null if surl doesn't form a valid uri.
     * 
     */
    public URI resourceUri;
    /**
     * the resource uri up to the authority, e.g.  jbdc:mysql://192.168.0.203:3306
     */
    public String authority;
    /**
     * the resource uri including the path part.
     */
    public String path;
    /**
     * contains the resource string up to the query part.
     */
    public String file;
    public String ext;
    /**
     * contains the parameters part, a ampersand-delimited set of parameters. For example, column=field2&rank2.
     */
    public String params;
    /**
     * position of the carot after modifications to the surl are made.  This
     * is with respect to surl, the URI for the datasource, without the "vap" scheme.
     */
    public int resourceUriCarotPos;
    /**
     * position of the carot after modifications to the surl are made.  This
     * is with respect to formatted uri, which probably includes the explcit "vap:" scheme.
     */
    public int formatCarotPos;

    /**
     * add "file:/" to a resource string that appears to reference the local filesystem.
     * 
     * @param surl
     * @return surl, maybe with "file:/" prepended.
     */
    public static String maybeAddFile(String surl) {
        URLSplit result = maybeAddFile(surl, 0);
        return result.surl;
    }

    public static URLSplit maybeAddFile(String surl, int carotPos) {
        URLSplit result = new URLSplit();

        if (surl.length() == 0) {
            surl = "file:///";
            carotPos = surl.length();
            result.surl = surl;
            result.vapScheme = "vap";
            result.resourceUriCarotPos = carotPos;
            result.formatCarotPos = carotPos + 4;
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

        if (scheme.startsWith("vap")) {
            String resourcePart = surl.substring(i0 + 1);
            result.vapScheme = scheme;
            if (scheme.equals("vap+internal")) { // leave the resourcePart alone. TODO: jdbc and other non-file URIs.
                result.surl= resourcePart;
            } else {
                URLSplit resourceSplit = maybeAddFile(resourcePart, carotPos - (i0 + 1));
                result.surl = resourceSplit.surl;
                result.formatCarotPos = (carotPos > i0) ? resourceSplit.resourceUriCarotPos + (i0 + 1) : carotPos;
                result.resourceUriCarotPos = result.formatCarotPos - (scheme.length() + 1); // with respect to resource part.
            }

        } else {
            result.surl = surl;
            result.resourceUriCarotPos = carotPos;
        }

        if (scheme.equals("")) {
            result.surl = "file://";
            result.resourceUriCarotPos += 7;
            if ((surl.charAt(0) == '/')) {
                result.surl += surl;
            } else {
                result.surl += ('/' + surl); // Windows c:
                result.resourceUriCarotPos += 1;
            }
            result.surl = result.surl.replaceAll("\\\\", "/");
            result.surl = result.surl.replaceAll(" ", "+");
        }

        return result;
    }

    /**
     * split the url string into components.  This does not try to identify
     * the vap scheme, since that might require interaction with the server to
     * get mime type.  This inserts the scheme "file://" when the scheme is 
     * absent.
     * The string http://www.example.com/data/myfile.nc?myVariable is split into:
     *   scheme, http
     *   authority, http://www.example.com
     *   path, the directory with http://www.example.com/data/
     *   file, the file, http://www.example.com/data/myfile.nc
     *   ext, the extenion, .nc
     *   params, myVariable or null
     */
    public static URLSplit parse(String surl) {
        return parse(surl, 0, true);
    }

    /**
     * returns group 1 if there was a match, null otherwise.
     * @param s
     * @param regex
     * @return
     */
    private static String magikPop(String s, String regex) {
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(s);
        if (m.matches()) {
            return m.group(1);
        } else {
            return null;
        }
    }

    /**
     * interpret the scheme part to vapScheme and scheme.  If the resource URI is
     * valid, then this will be set as well.  surl may be modified.
     * @param result
     */
    private static void parseScheme(URLSplit result, boolean normalize) throws URISyntaxException {
        String surl = result.surl;
        int h = surl.indexOf(":"); // "c:" should be "file:///c:"

        String scheme = surl.substring(0, h);

        if (scheme.startsWith("vap")) {
            result.vapScheme = scheme;
            result.formatCarotPos = result.resourceUriCarotPos + scheme.length() + 1;
            result.surl = surl.substring(h + 1);
            result.scheme = magikPop(result.surl, "([a-zA-Z\\+]+)\\:.*");
            try {
                result.resourceUri = new URI(result.surl);
                result.scheme = result.resourceUri.getScheme();
            } catch (URISyntaxException ex) {
                // do nothing, this field may be null.
            }
        } else {
            if (scheme.contains(".")) {
                int j = scheme.indexOf(".");
                result.vapScheme = "vap+" + scheme.substring(0, j);
                result.surl = result.surl.substring(j + 1);
                if (result.resourceUriCarotPos > j) result.resourceUriCarotPos -= (j + 1);
                result.formatCarotPos = result.resourceUriCarotPos + result.vapScheme.length() + 1;
                result.scheme = magikPop(result.surl, "([a-zA-Z\\+]+)\\:.*");
                try {
                    result.resourceUri = new URI(result.surl);
                    result.scheme = result.resourceUri.getScheme();
                } catch (URISyntaxException ex) {
                    // do nothing, this field may be null.
                }
            } else {
                if (result.vapScheme == null && normalize ) {
                    result.vapScheme = "vap";
                    result.formatCarotPos = result.resourceUriCarotPos + 4;
                }
                result.surl = surl;
                result.scheme = magikPop(result.surl, "([a-zA-Z\\+]+)\\:.*");
                try {
                    result.resourceUri = new URI(uriEncode(surl));
                    result.scheme = result.resourceUri.getScheme();
                } catch (URISyntaxException ex) {
                    // do nothing, this field may be null.
                }
            }
        }
    }

    /**
     * split the url string into components, keeping track of the carot position
     * when characters are inserted.  This does not try to identify
     * the vap scheme, since that might require interaction with the server to
     * get mime type.  This inserts the scheme "file://" when the scheme is 
     * absent.
     * The string http://www.example.com/data/myfile.nc?myVariable is split into:
     *   vapScheme, vap+nc
     *   scheme, http
     *   authority, http://www.example.com
     *   path, the directory with http://www.example.com/data/
     *   file, the file, http://www.example.com/data/myfile.nc
     *   ext, the extenion, .nc
     *   params, myVariable or null
     * @param surl  the string to parse
     * @param resourceUriCarotPos the position of the carot, the relative position will be preserved through normalization in formatCarotPos
     * @param normalize normalize the surl by adding implicit "vap", etc.
     */
    public static URLSplit parse( String surl, int carotPos , boolean normalize) {
        URLSplit result = maybeAddFile(surl, carotPos);

        try {
            if ( result.vapScheme==null || !result.vapScheme.equals("vap+internal") ) {
                parseScheme(result, normalize);
            }
        } catch (URISyntaxException ex) {
            result.surl = uriEncode(result.surl); //TODO: move resourceUriCarotPos
            try {
                parseScheme(result, normalize);
            } catch (URISyntaxException ex1) {
                throw new RuntimeException(ex1);
            }
        }

        int i;

        String rsurl = result.surl;

        int iquery;
        // check for just one ?
        iquery = rsurl.indexOf("?");

        String file;

        file = result.resourceUri == null ? null : result.resourceUri.getPath();
        if (file == null) {
            if (iquery == -1) {
                file = rsurl;
            } else {
                file = rsurl.substring(0, iquery);
            }
        }

        String ext = null;
        if (file != null) {
            i = file.lastIndexOf(".");
            ext = i == -1 ? "" : file.substring(i);
        }

        String params = null;
        int fileEnd;

        if (file != null && iquery != -1) {
            fileEnd = iquery;
            params = rsurl.substring(iquery + 1);
        } else {
            iquery = rsurl.length();
            fileEnd = rsurl.length();
        }

        if (result.scheme != null) {
            int iauth = result.scheme.length() + 1;
            while (iauth < rsurl.length() && rsurl.charAt(iauth) == '/') {
                iauth++;
            }
            iauth = rsurl.indexOf('/', iauth);
            if (iauth == -1) iauth = rsurl.length();

            result.authority = rsurl.substring(0, iauth);
        }

        if (file != null) {
            i = rsurl.lastIndexOf("/", iquery);
            if (i == -1) {
                result.path = rsurl.substring(0, iquery);
                result.file = rsurl.substring(0, iquery);
                result.ext = ext;
            } else {
                String surlDir = rsurl.substring(0, i);
                result.path = surlDir + "/";
                result.file = rsurl.substring(0, fileEnd);
                result.ext = ext;
            }
        }
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
        LinkedHashMap<String, String> result = new LinkedHashMap<String, String>();
        if (params == null) {
            return result;
        }
        if (params.trim().equals("")) {
            return result;
        }

        params = URLSplit.uriDecode(params);

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
                    result.append("&" + key + "=" + uriEncode(value));
                } else {
                    result.append("&" + key);
                }
            }
        }
        return (result.length() == 0) ? "" : result.substring(1);
    }

    public static String format(URLSplit split) {
        String surl;
        String result = "";
        if ( split.vapScheme!=null ) result= result + split.vapScheme + ":";
        result= result + split.file;
        if (split.params != null) {
            result += "?" + split.params;
        }
        return result;
    }

    /**
     * convert " " to "+", etc, by looking for and encoding illegal characters.  
     * @param s
     * @return
     */
    public static String uriEncode(String surl) {
        surl = surl.replaceAll("%([^0-9])", "%25$1");
        surl = surl.replaceAll("<", "%3C");
        surl = surl.replaceAll(">", "%3E");
        surl = surl.replaceAll(" ", "+");
        surl = surl.replaceAll("\\%24", "\\$");
        return surl;
    }

    /**
     * convert "+" to " ", etc, by using URLDecoder and catching the UnsupportedEncodingException that will never occur.
     * Kludge to check for and
     * decode pluses (+) in an otherwise unencoded string, also we have to be careful for elements like %Y than are
     * not to be decoded.
     * @param s
     * @return
     */
    public static String uriDecode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (IllegalArgumentException ex) {
            return s.replaceAll("\\+", " ");
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }
    }

    public String toString() {
        return "\nvapScheme: " + vapScheme + "\nscheme: " + scheme + "\nresourceUri: " + resourceUri + "\npath: " + path + "\nfile: " + file + "\next: " + ext + "\nparams: " + params + "\nsurl: " + surl + "\ncarotPos: " + resourceUriCarotPos + "\nformatCarotPos: " + formatCarotPos;
    }
}
