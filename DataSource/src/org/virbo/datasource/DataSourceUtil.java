/*
 * Util.java
 *
 * Created on November 6, 2007, 10:41 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.datasource;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.fsm.FileStorageModelNew;
import org.das2.util.TimeParser;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.FileSystem.FileSystemOfflineException;

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

    /**
     * from org.autoplot.pngwalk.WalkUtil
     * @param str
     * @param targets
     * @return
     */
    private static int firstIndexOf( String str, List<String> targets ) {
        int i0= Integer.MAX_VALUE;
        for ( String t: targets ) {
            int i= str.indexOf(t);
            if ( i>-1 && i<i0 ) i0= i;
        }
        return i0==Integer.MAX_VALUE ? -1 : i0;
    }

    /**
     * returns the last index of slash, splitting the FileSystem part from the template part.
     * @param surl
     * @return
     */
    private static int splitIndex(String surl) {
        int i= firstIndexOf( surl,Arrays.asList( "%Y","$Y","%y","$y",".*") );
        if ( i!=-1 ) {
            i = surl.lastIndexOf('/', i);
        } else {
            i = surl.lastIndexOf('/');
        }
        return i;
    }

    /**
     * return the aggregations we can find.  
     * If remove is true, then the input list will have all items
     * removed that are not part of an aggregation.
     * 
     * @param files
     * @param remove
     * @return
     */
    public static List<String> findAggregations( List<String> files, boolean remove ) {
        List<String> accountedFor= new ArrayList<String>();
        List<String> result= new ArrayList<String>();
        List<String> nonAgg= new ArrayList<String>();

        List<String> notAccountedFor;
        notAccountedFor= new LinkedList(files);

        while ( notAccountedFor.size()>0 ) {
            String surl= notAccountedFor.remove(0);

            String sagg = makeAggregation(surl);

            if (sagg==null || sagg.equals(surl)) {
                nonAgg.add(surl);
                continue;
            }

            DatumRange dr = null;
            // remove parameter
            sagg = URISplit.removeParam(sagg, "timerange");
            TimeParser tp = TimeParser.create(sagg);
            try {
                tp.parse(surl);
            } catch (ParseException ex) {
                continue;
            }
            dr = tp.getTimeRange();

            boolean okay= true;

            List<String> moveUs= new ArrayList();

            Pattern p= Pattern.compile( tp.getRegex() );

            for ( String s: notAccountedFor ) {
                if ( p.matcher(s).matches() ) {
                    try {
                        tp.parse(s);
                        dr = DatumRangeUtil.union(dr, tp.getTimeRange());
                        moveUs.add( s );
                    } catch (ParseException ex) {
                        // it's not part of the agg.
                    }
                }
            }

            notAccountedFor.removeAll(moveUs);
            accountedFor.addAll(moveUs);

            result.add( URISplit.putParam(sagg, "timerange", dr.toString()) );

        }

        if ( remove ) {
            files.removeAll(accountedFor);
        }
        
        return result;
    }

    /**
     * attempt to make an aggregation from the URLs.  If one cannot be created
     * (for example if the filenames are not consistent), then the original
     * URI is returned.
     *
     * @param surl
     * @param surls
     * @return
     */
    public static String makeAggregation( String surl, String[] surls ) {
        try {
            String sagg = makeAggregation(surl);
            if (sagg==null || sagg.equals(surl))
                return surl;
            DatumRange dr = null;
            // remove parameter
            sagg = URISplit.removeParam(sagg, "timerange");
            TimeParser tp = TimeParser.create(sagg);
            tp.parse(surl);
            dr = tp.getTimeRange();

            boolean okay= true;
            for (int i = 0; okay && i < surls.length; i++) {
                try {
                    tp.parse(surls[i]);
                    dr = DatumRangeUtil.union(dr, tp.getTimeRange());
                } catch (ParseException ex) {
                    okay= false;
                    Logger.getLogger(DataSourceUtil.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            if ( okay==false ) {
                return surl;
            } else {
                return URISplit.putParam(sagg, "timerange", dr.toString());
            }
            
        } catch (ParseException ex) {
            Logger.getLogger(DataSourceUtil.class.getName()).log(Level.SEVERE, null, ex);
            return surl;
        }


    }

    /**
     * attempt to create an equivalent URL that uses an aggregation template
     * instead of the explicit filename.
     * For example, file:/tmp/20091102.dat -> file:/tmp/%Y%m%d.dat?timerange=20091102
     * @param surl
     * @return
     */
    public static String makeAggregation( String surl ) {
        String yyyy= "/\\d{4}/";
        String yyyymmdd= "(?<!\\d)(\\d{8})(?!\\d)"; //"(\\d{8})";
        String yyyy_mm_dd= "\\d{4}([\\-_])\\d{2}\\1\\d{2}";
        String yyyy_jjj= "\\d{4}([\\-_])\\d{3}";
        String version= "([Vv])\\d{2}";
        String result= surl;

        String[] abs= new String[] { yyyymmdd, yyyy_mm_dd, yyyy_jjj };

        String timeRange=null;
        for ( int i= 0; i<abs.length; i++ ) {
            Matcher m= Pattern.compile(abs[i]).matcher(surl);
            if ( m.find() ) {
                timeRange= m.group(0);
            }
        }

        if ( timeRange==null ) return null;

        result= result.replaceFirst(yyyy_jjj, "\\$Y$1\\$j");
        result= result.replaceFirst(yyyymmdd, "\\$Y\\$m\\$d");
        result= result.replaceFirst(yyyy_mm_dd, "\\$Y$1\\$m$1\\$d" );
        result= result.replaceFirst(yyyy, "/\\$Y/");

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

    public static String strjoin(Collection<String> c, String delim) {
        StringBuffer result = new StringBuffer();
        for (String s : c) {
            if (result.length() > 0) {
                result.append(delim);
            }
            result.append(s);
        }
        return result.toString();
    }

    public static String strjoin( long[] dims, String delim ) {
        StringBuffer sdims= new StringBuffer();
        if ( dims.length>0 ) {
                sdims.append( dims[0] );
                for ( int i=1; i<dims.length; i++ ) {
                        sdims.append( delim +dims[i]);
                }
        }
        return sdims.toString();
    }

    public static String strjoin( int[] dims, String delim ) {
        StringBuffer sdims= new StringBuffer();
        if ( dims.length>0 ) {
                sdims.append( dims[0] );
                for ( int i=1; i<dims.length; i++ ) {
                        sdims.append( delim +dims[i]);
                }
        }
        return sdims.toString();
    }

    /**
     * transfers the data from one channel to another.
     * @param src
     * @param dest
     * @throws java.io.IOException
     */
    public static void transfer( ReadableByteChannel src, WritableByteChannel dest ) throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024);
        while (src.read(buffer) != -1) {
            // prepare the buffer to be drained
            buffer.flip();
            // write to the channel, may block
            dest.write(buffer);
            // If partial transfer, shift remainder down
            // If buffer is empty, same as doing clear()
            buffer.compact();
        }
        // EOF will leave buffer in fill state
        buffer.flip();
        // make sure the buffer is fully drained.
        while (buffer.hasRemaining()) {
            dest.write(buffer);
        }
        dest.close();
        src.close();
    }


    /**
     * returns [ start, stop, stride ] or [ start, -1, -1 ] for slice.  This is
     * provided to reduce code and for uniform behavior.
     * @param constraint, such as "[0:100:2]" for even records between 0 and 100, non-inclusive.
     * @return
     */
    public static long[] parseConstraint(String constraint, long recCount) throws ParseException {
        long[] result = new long[]{0, recCount, 1};
        final String INT="([+-]?+\\d*)";
        if (constraint == null) {
            return result;
        } else {
            if ( constraint.startsWith("[") && constraint.endsWith("]") ) {
                constraint= constraint.substring(1,constraint.length()-1);
            }
            try {
                String[] ss= constraint.split(":",-2);
                int [] ii= new int[ss.length];
                if ( ss.length>0 && ss[0].length()>0 ) {
                    result[0]= Integer.parseInt(ss[0]);
                    if ( result[0]<0 ) result[0]= recCount+result[0];
                }
                if ( ss.length>1 && ss[1].length()>0 ) {
                    result[1]= Integer.parseInt(ss[1]);
                    if ( result[1]<0 ) result[1]= recCount+result[1];
                }
                if ( ss.length>2 && ss[2].length()>0 ) {
                    result[2]= Integer.parseInt(ss[2]);
                }
                if ( ss.length==1 ) { // slice
                    result[1]= -1;
                    result[2]= -1;
                }
            } catch ( NumberFormatException ex ) {
                throw new ParseException("expected integer: "+ex.toString(),0);
            }
            return result;
        }
    }

    public static void main(String[] args ) {
        String surl= "http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hyd_h0/2000/po_h0_hyd_20000109_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX";
        System.err.println( makeAggregation(surl) );
                
    }
}
