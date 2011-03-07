package org.virbo.datasource;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class for containing the elemental parts of a URI, and utility
 * routines for working with URIs.
 *
 * We need a working definition of well-formed and colloquial URIs:
 * = well-formed URIs =
 *   <vapScheme>:<fileResource>?<params>
 *   <vapScheme>:[<identifier>?]<params>
 *   * they are valid URIs: they contain no spaces, etc.
 * = colloquial URIs =
 *   * these are Strings that can be converted into URIs.
 *   * spaces in file names are converted into %20. 
 *   * spaces in parameter lists are converted into pluses.
 *   * pluses in parameter lists are converted into %2B.
 *   * note that if there are pluses but the URI is valid, then pluses may be left alone. 
 * This routine knows nothing about the data source that will interpret the
 * URI, so this needs to be established.
 * 
 * @author jbf
 */
public class URISplit {

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
     * contains the part indicating additional processing to be done on the dataset.  (Not implemented, but it's coming.
     */
    public String process;

    /**
     * position of the carot after modifications to the surl are made.  This
     * is with respect to surl, the URI for the datasource, without the "vap" scheme.
     */
    public int resourceUriCarotPos;
    /**
     * position of the carot after modifications to the surl are made.  This
     * is with respect to formatted URI, which probably includes the explicit "vap:" scheme.
     */
    public int formatCarotPos;

    /**
     * if true, then "vap:" for the vapScheme was implicitly added.  We always
     * format with the "vap:" added.
     */
    public boolean implicitVap= false;

    /**
     * add "file:/" to a resource string that appears to reference the local filesystem.
     * return the parsed string, or null if the string doesn't appear to be from a file.
     * @param surl
     * @param carotPos
     * @return
     */
    public static URISplit maybeAddFile(String surl, int carotPos) {
        URISplit result = new URISplit();

        if (surl.length() == 0) {
            surl = "file:///";
            carotPos = surl.length();
            result.surl = surl;
            result.vapScheme = "vap";
            result.implicitVap= true;
            result.resourceUriCarotPos = carotPos;
            result.formatCarotPos = carotPos + 4;
        }

        String scheme;  // identify a scheme, if any.  This might be vap+foo:, or http:
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
                URISplit resourceSplit = maybeAddFile(resourcePart, carotPos - (i0 + 1));  //TODO: jdbc and vap+inline
                if ( resourceSplit==null ) {
                    result.surl= resourcePart;
                    result.file= "";
                } else {
                    result.surl = resourceSplit.surl;
                    result.formatCarotPos = (carotPos > i0) ? resourceSplit.resourceUriCarotPos + (i0 + 1) : carotPos;
                    result.resourceUriCarotPos = result.formatCarotPos - (scheme.length() + 1); // with respect to resource part.
                }
            }

        } else {
            result.surl = surl;
            result.resourceUriCarotPos = carotPos;
        }

        if (scheme.equals("")) {
            boolean isFile= true;
            int iquery= surl.indexOf("?");
            if ( iquery==-1 ) {
                int ieq= surl.indexOf("=");
                //kludge in support for "ripples(30,30")
                int ch0= surl.length()>0 ? surl.charAt(0) : (char)0;
                int ch1= surl.length()>1 ? surl.charAt(1) : (char)0;
                boolean notSlashStart= ch0!='/' && ch0!='\\' && ch1!='/' && ch1!='\\';
                if ( notSlashStart || ( ieq>-1 && !(surl.charAt(0)=='/') ) ) {
                    isFile= false;
                }
            }

            if ( !isFile ) {
                return null;

            } else {
                result.surl = "file://";
                result.scheme= "file";
                result.resourceUriCarotPos += 7;
                if ((surl.charAt(0) == '/')) {
                    result.surl += surl;
                } else {
                    result.surl += ('/' + surl); // Windows c:
                    result.resourceUriCarotPos += 1;
                }
                int iq= result.surl.indexOf("?");
                if ( iq==-1 ) iq= result.surl.length();

                result.surl = result.surl.replaceAll("\\\\", "/"); //TODO: what if \ in query part?

                int spaceCount= charCount( result.surl, ' ', 0, result.surl.length() );
                result.surl = replaceAll( result.surl, " ", "%20", 0, iq );

                result.formatCarotPos+= spaceCount*2; //account for inserted characters.
                result.resourceUriCarotPos+= spaceCount*2;
            }
            
        }

        return result;
    }

    private static int charCount( String src, char find, int start, int end ) {
        int count=0;
        for ( int i=start; i<end; i++ ) {
            if ( src.charAt(i)==find ) count++;
        }
        return count;
    }

    /**
     * replace all characters in the given range.
     * @param src
     * @param regex
     * @param replacement
     * @param start
     * @param end
     * @return
     */
    private static String replaceAll( String src, String regex, String replacement, int start, int end ) {
        String prefix= src.substring(0,start);
        String middle= src.substring(start,end);
        String suffix= src.substring(end);
        return prefix + middle.replaceAll(regex, replacement) + suffix;
    }

    /**
     * added to avoid widespread use of parse(uri.toString).  This way its all being done with same code,
     * and keep the URI abstraction.
     * @param uri
     * @return
     */
    public static URISplit parse( URI uri ) {
        return parse( DataSetURI.fromUri(uri), 0, true );
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
    public static URISplit parse(String surl) {
        return parse(surl, 0, true);
    }

    /**
     * convenient method to remove a parameter (or parameters) from the list of parameters
     * @param surl
     * @param parm
     * @return
     */
    public static String removeParam( String surl, String ... parm ) {
        URISplit split= URISplit.parse(surl);
        Map <String,String> params= URISplit.parseParams( split.params );
        for ( String p: parm ) {
            params.remove(p);
        }
        split.params= URISplit.formatParams(params);
        if ( params.size()==0 ) split.params=null;
        if ( !surl.startsWith(split.vapScheme) ) split.vapScheme=null;
        return split.format(split);
    }


    /**
     * convenient method for adding or replacing a parameter to the URI.
     * @param surl
     * @param name
     * @param value
     * @return
     */
    public static String putParam( String surl, String name, String value ) {
        URISplit split= URISplit.parse(surl);
        Map <String,String> params= URISplit.parseParams( split.params );
        params.put( name, value );
        split.params= URISplit.formatParams(params);
        if ( !surl.startsWith(split.vapScheme) ) split.vapScheme=null;
        return split.format(split);
    }

    /**
     * convenient method for getting a parameter in the URI.  
     * @param surl
     * @param name parameter name.
     * @param deft default value if the parameter is not found.
     * @return
     */
    public static String getParam( String surl, String name, String deft ) {
        URISplit split= URISplit.parse(surl);
        Map <String,String> params= URISplit.parseParams( split.params );
        String val= params.get( name );
        if ( val==null ) val= deft;
        return val;
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
    private static void parseScheme(URISplit result, boolean normalize) throws URISyntaxException {
        String surl = result.surl;
        int h = surl.indexOf(":"); // "c:" should be "file:///c:"

        String scheme = surl.substring(0, h);

        if (scheme.startsWith("vap")) {
            result.vapScheme = scheme;
            result.formatCarotPos = result.resourceUriCarotPos + scheme.length() + 1;
            result.surl = surl.substring(h + 1);
            result.scheme = magikPop(result.surl, "([a-zA-Z\\+]+)\\:.*");
            int iq= result.surl.indexOf("?");
            if ( iq==-1 ) iq= result.surl.length();
            try {
                result.resourceUri = new URI(uriEncode(result.surl.substring(0,iq)));
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
                int iq= result.surl.indexOf("?");
                if ( iq==-1 ) iq= surl.length();
                try {
                    result.resourceUri = new URI(uriEncode(result.surl.substring(0,iq)));
                    result.scheme = result.resourceUri.getScheme();
                } catch (URISyntaxException ex) {
                    // do nothing, this field may be null.
                }
            } else {
                if (result.vapScheme == null && normalize ) {
                    result.vapScheme = "vap";
                    result.implicitVap= true;
                    result.formatCarotPos = result.resourceUriCarotPos + 4;
                }
                result.surl = surl;
                result.scheme = magikPop(result.surl, "([a-zA-Z\\+]+)\\:.*");
                int iq= result.surl.indexOf("?");
                if ( iq==-1 ) iq= surl.length();
                try {
                    result.resourceUri = new URI(uriEncode(result.surl.substring(0,iq)));
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
    public static URISplit parse( String surl, int carotPos , boolean normalize) {

        if ( surl.startsWith("file:/") && surl.endsWith(":") && surl.length()<11 && surl.charAt(surl.length()-3)=='/' ) { // kludge for file:///c:<CAROT> on Windows.
            if ( carotPos==surl.length() ) carotPos++;
            surl= surl+"/";
        }

        URISplit result = maybeAddFile(surl, carotPos);

        if ( "vap+internal".equals(result.vapScheme) ) result.file=""; // non-files will get "" for the file, and this should too.
        try {
            if ( result.vapScheme==null || result.file==null ) {
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
        int ieq= rsurl.indexOf("=");

        String file;

        file = result.resourceUri == null ? null : result.resourceUri.getPath();
        if (file == null) {
            if (iquery == -1) {
                if ( ieq==-1 ) {
                    file= rsurl;
                } else {
                    file= null;
                }
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
        int fileEnd=-1;

            //int ipipe= file.indexOf("|");
            //if ( ipipe>-1 ) {
            //    result.process= file.substring(ipipe);
            //    file= file.substring(0,ipipe);
            //} else {
            //    result.process= "";
            //}
        if (file != null && iquery != -1) {
            fileEnd = iquery;
            params = rsurl.substring(iquery + 1);
        } else {
            if ( ieq>-1 ) {
                iquery = 0;
                params= rsurl;
            } else {
                iquery = rsurl.length();
                fileEnd = rsurl.length();
            }
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

        if ( "".equals(result.file) ) result.file=null;
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

        params = URISplit.uriDecode(params);
//        if ( params.contains("+") && params.contains(" ") ) {  // this may be a problem.  We know spaces are not encoded as pluses.
//            System.err.println("params appear to be decoded already");
//        } else {
//            if ( params.contains("+") && !params.contains("%20") ) { // legacy
//               params = params.replaceAll("+", " " );
//            }
//            params = URISplit.uriDecode(params);
//            //params = params.replaceAll("\\+", " "); // in the parameters, plus (+) is the same as space ( ).
//        }

        String[] ss = params.split("&");

        int argc = 0;

        for (int i = 0; i < ss.length; i++) {
            int j = indexOf(ss[i], '=', '(', ')');
            String name, value;
            if (j == -1) {
                name = ss[i];
                value = "";
                name = name.replaceAll("%3D", "=" ); // https://sourceforge.net/tracker/?func=detail&aid=3049295&group_id=199733&atid=970682
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
                    result.append("&" + key + "=" + uriEncode(value)); //result.append("&" + key + "=" + uriEncode(value).replaceAll("%20", "+" ));
                } else {
                    result.append("&" + key);
                }
            }
        }
        return (result.length() == 0) ? "" : result.substring(1);
    }

    public static String format(URISplit split) {
        String result = "";
        if ( split.vapScheme!=null ) result= result + split.vapScheme + ":";
        if ( split.file==null && split.params!=null ) {
            result= result + split.params;
        } else if ( split.file!=null ) {
            result= result + split.file;
            if (split.params != null) {
                result += "?" + split.params;
            }
        }
        return result;
    }

    /**
     * We need a standard way to detect if a string has already been URL encoded.
     * The problem is we want valid URIs that are also readable, so just using
     * simple encode/decode logic is not practical.
     *
     * This means:
     * - no spaces
     * - contains %[0-9][0-9]
     * @param surl
     * @return
     */
    public static boolean isUriEncoded( String surl ) {
        boolean result= false;
        // check for illegal characters.
        if ( surl.contains(" ") ) result= false;
        // check for encoded characters.
        if ( Pattern.compile("%[0-9][0-9]").matcher(surl).find() ) result= true;
        return result;
    }

    /**
     * convert " " to "%20", etc, by looking for and encoding illegal characters.
     * We can't just aggressively convert...
     * @param s
     * @return
     */
    public static String uriEncode(String surl) {
        if ( isUriEncoded(surl) ) return surl;
        surl = surl.replaceAll("%([^0-9])", "%25$1");  //%Y, %j, etc
        surl = surl.replaceAll("\\%24", "\\$"); // What's this--seems backward.  We like $'s in URIs...

        surl = surl.replaceAll(" ", "%20" );
        //surl = surl.replaceAll("#", "%23" );
        //surl = surl.replaceAll("%", "%25" ); // see above
        //surl = surl.replaceAll("&", "%26" );
        //surl = surl.replaceAll("\\+", "%2B" );
        //surl = surl.replaceAll("/", "%2F" );
        //surl = surl.replaceAll(":", "%3A" );
        //surl = surl.replaceAll(";", "%3B" );
        surl = surl.replaceAll("<", "%3C");
        surl = surl.replaceAll(">", "%3E");
        //surl = surl.replaceAll("\\?", "%3F" );

        return surl;
    }

    /**
     * convert "+" to " ", etc, by using URLDecoder and catching the UnsupportedEncodingException that will never occur.
     * We have to be careful for elements like %Y than are
     * not to be decoded.
     * TODO: we need to use standard escape/unescape code, possibly changing %Y to $Y beforehand.
     * @param s
     * @return
     */
    public static String uriDecode(String s) {
        if ( !isUriEncoded(s) ) return s;
        String surl= s;
//        if ( surl.contains("+") && !surl.contains("%20") ) { // legacy
//            surl = surl.replaceAll("+", " " );
//        }
        surl = surl.replaceAll("%20", " " );
        //surl = surl.replaceAll("%23", "#" );
        surl = surl.replaceAll("%25", "%" );
        //surl = surl.replaceAll("%26", "&" );
        surl = surl.replaceAll("%2B", "+" );
        //surl = surl.replaceAll("%2F", "/" );
        //surl = surl.replaceAll("%3A", ":" );
        //surl = surl.replaceAll("%3B", ";" );
        surl = surl.replaceAll("%3C", "<" );
        surl = surl.replaceAll("%3E", ">" );
        //surl = surl.replaceAll("%3F", "?" );

        return surl;
    }

    public String toString() {
        return "\nvapScheme: " + vapScheme + "\nscheme: " + scheme + "\nresourceUri: " + resourceUri + "\npath: " + path + "\nfile: " + file + "\next: " + ext + "\nparams: " + params + "\nsurl: " + surl + "\ncarotPos: " + resourceUriCarotPos + "\nformatCarotPos: " + formatCarotPos;
    }
}
