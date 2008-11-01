package org.virbo.datasource;

import java.net.MalformedURLException;
import java.net.URL;

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

    public static String format(URLSplit split) {
        String result = split.file;
        if (split.params != null) {
            result += "?" + split.params;
        }
        return result;
    }
    
    public String toString() {
        return path + "\n" + file + "\n" + ext + "\n" + params;
    }
}
