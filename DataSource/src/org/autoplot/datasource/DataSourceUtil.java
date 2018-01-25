/*
 * Util.java
 *
 * Created on November 6, 2007, 10:41 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.autoplot.datasource;

import java.awt.Component;
import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.util.LoggerManager;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.datum.TimeParser;
import org.das2.datum.UnitsUtil;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.DataSetOps;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.das2.qds.ops.Ops;
//import org.autoplot.qstream.SimpleStreamFormatter;
//import org.autoplot.qstream.StreamException;

/**
 *
 * @author jbf
 */
public class DataSourceUtil {

    private static final Logger logger= LoggerManager.getLogger("apdss.util");

    /** 
     * used in Autoplot's Application object and in the DataSetSelector.
     */
    public static final DatumRange DEFAULT_TIME_RANGE= DatumRangeUtil.parseTimeRangeValid( "2010-01-01" );
        
    /**
     * remove escape sequences like %20 to create a human-editable string
     * This contains a kludge that looks for single spaces that are the result of
     * cut-n-pasting on Linux.  If there is a space and a "%3A", then single spaces
     * are removed.
     * <code>&amp;amp;</code> is replaced with <code>&</code>.
     * @param s
     * @return
     */
    public static String unescape(String s) {
        try {
            if ( s.contains(" ") && s.contains("%3A") ) {
                //copy and paste on linux sometimes inserts a space, so take these out.
                s= s.replaceAll(" ", "");
            }
            s = URLDecoder.decode(s, "UTF-8");
            s = s.replaceAll("\\&amp;","&");
            if ( s.startsWith("vap ")) {
                s= "vap+"+s.substring(4);
            }
            return s;
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * Carefully remove pluses from URIs that mean to interpret pluses
     * as spaces.  Note this is not done automatically because some data sources
     * need the pluses, like vap+inline:ripples(20)+linspace(0.,10.,20).
     * This should be done carefully, because we realize that some pluses may
     * intentionally exist in URIs, such as &where=energy.gt(1e+3).  While this
     * is discouraged, it will inevitably happen.  
     * 
     * <table>
     * <tr><td>&where=energy.gt(1e+3)</td><td>&where=energy.gt(1e+3)</td><tr>
     * <tr><td>&where=energy.within(1e+3+to+1e+5)</td><td>&where=energy.gt(1e+3 to 1e+5)</td><tr>
     * </table>
     * @param s the parameter string, such as 
     * @return 
     */
    public static String unescapeParam( String s ) {
        String[] ss= s.split("\\+\\D");
        if ( ss.length==1 ) return s;
        // return String.join( " ",ss ); // Oh, for Java 8 I can't wait. (Note I realized later that it couldn't be used anyway.
        StringBuilder b= new StringBuilder(ss[0]);
        int ich= ss[0].length()+1; // we need to preserve the \D
        for ( int i=1; i<ss.length; i++ ) {
            b.append(" ").append(s.charAt(ich)).append(ss[i]);
            ich= ich + ss[i].length()+1;
        }
        return b.toString();
    }
    
    /**
     * interprets spec within the context of URL context.
     * @param context the context for the spec.  null may be used to
     *    indicate no context.
     * @param spec if spec is a fully specified URL, then it is used, otherwise
     *    it is appended to context.  If spec refers to the name of a file,
     *    but doesn't start with "file:/", "file:/" is appended.
     * @return the URL.
     * @throws java.net.MalformedURLException 
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
     * @param context the context.
     * @param url the URL.
     * @return the part made relative, if possible, to context.
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
     * @param s the string.
     * @return the string without quotes.
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

    public static List<String> findAggregations( List<String> files, boolean remove ) {
        return findAggregations( files, remove, false );
    }

    /**
     * return the aggregations we can find.
     * If remove is true, then the input list will have all items
     * removed that are not part of an aggregation.
     *
     * @param files
     * @param remove remove the files that are accounted for by the aggregation.
     * @param loose only only one file to qualify for an aggregation.  We need this to support case where we know it's from an agg.
     * @return list of aggregations found.
     */
    public static List<String> findAggregations( List<String> files, boolean remove, boolean loose ) {
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
            } else {
                accountedFor.add(surl);
            }

            DatumRange dr;
            // remove parameter
            sagg = URISplit.removeParam(sagg, "timerange");
            TimeParser tp;
            try {
                tp= TimeParser.create(sagg,"v", TimeParser.IGNORE_FIELD_HANDLER );
                tp.parse(surl);
            } catch (ParseException ex) {
                continue;
            } catch ( IllegalArgumentException ex ) {
                logger.log( Level.SEVERE, ex.getMessage(), ex );
                continue; // bad format code "N" from "file:///c:/WINDOWS/$NtUninstallKB2079403$/"
            }
            dr = tp.getTimeRange();
            DatumRange dr1= dr; // keep track of the first one to measure continuity.

            List<String> moveUs= new ArrayList();

            Pattern p= Pattern.compile( tp.getRegex() );

            for ( String s: notAccountedFor ) {
                if ( p.matcher(s).matches() ) {
                    try {
                        tp.parse(s);
                        dr = DatumRangeUtil.union(dr, tp.getTimeRange() );
                        moveUs.add( s );
                    } catch (ParseException ex) {
                        // it's not part of the agg.
                    }
                }
            }

            double nc= dr.width().divide(dr1.width()).doubleValue(Units.dimensionless); // number of intervals estimate

            // see if we can make the agg more specific, in particular, handling $Y$m01.dat when $Y$m$d was detected.  Of course we have to guess
            // here, since we are not going to look inside the files. 
            // I'm disabling this for now.  It doesn't work and needs to be revisited.  
//            if ( moveUs.size()>4 && sagg.contains("$d") ) {
//                String sagg1= sagg.replace("$d","01");
//                TimeParser tp1= TimeParser.create(sagg,"v", TimeParser.IGNORE_FIELD_HANDLER );
//                boolean fail= false;
//                for ( int i=0; i<moveUs.size(); i++ ) {
//                    try {
//                        DatumRange drtest= tp1.parse(moveUs.get(i)).getTimeRange();
//                        if ( tp1.format(drtest).equals(moveUs.get(i)) ) {
//                            fail= true;
//                            break;
//                        }
//                    } catch ( ParseException ex ) {
//                        fail= true;
//                        break;
//                    }
//                }
//                if ( fail==false ) {
//                    sagg= sagg1;
//                }
//            }

            // more than one file, and then five files or fairly continuous.
            if ( loose || moveUs.size()>0 && ( moveUs.size()>4 || nc<((1+moveUs.size())*2)  ) ) { // reject small aggregations
                notAccountedFor.removeAll(moveUs);
                accountedFor.addAll(moveUs);
                result.add( URISplit.putParam(sagg, "timerange", dr.toString()) );
            } else {
                notAccountedFor.removeAll(moveUs);
            }

        }
        
        logger.log(Level.FINER, "found {0}.", nonAgg.size());

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
            DatumRange dr;
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
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
            if ( okay==false ) {
                return surl;
            } else {
                return URISplit.putParam(sagg, "timerange", dr.toString());
            }
            
        } catch (ParseException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            return surl;
        }


    }

    /**
     * return the replacement or null.  remove the used items.  This will not match anything 
     * after the question mark, if there is one.
     * @param s the URI.
     * @param search
     * @param replaceWith 
     * @param resolution 
     * @return the string with 2014 replaced with $Y, etc.
     */
    private static String replaceLast( String s, List<String> search, List<String> replaceWith, List<Integer> resolution ) {
        Map<String,Integer> found= new HashMap();
        int last= -1;
        String flast= null;
        String frepl= null;
        int best= -1;
        int n= search.size();

        int limit= s.indexOf('?');
        if ( limit==-1 ) limit=s.length();
        
        DatumRange dr= null;
        
        while (true ) {
            for ( int i=0; i<n; i++ ) {
                if ( search.get(i)==null ) continue; // search.get(i)==null means that search is no longer eligible.
                Matcher m= Pattern.compile(search.get(i)).matcher(s);
                int idx= -1;
                while ( m.find() ) idx= m.start();
                if ( idx>-1 && idx<limit ) {
                    found.put( search.get(i), idx );
                    if ( idx>last ) {
                        last= idx;
                        flast= search.get(i);
                        frepl= replaceWith.get(i);
                        best= i;
                    }
                }
            }
            if ( best>-1 ) {
                String date= s.substring(last);
                String ch= date.substring(4,5); // get the $2 char.  Assumes all are $Y
                assert frepl!=null;
                String stp= frepl.replaceAll("\\\\",""); 
                stp= stp.replaceAll("\\$2",ch);
                stp= stp.replaceAll("\\$3",ch);
                TimeParser tp= TimeParser.create( stp );
                DatumRange dr1=null;
                try {
                    dr1= tp.parse(date).getTimeRange();
                } catch ( ParseException ex ) {
                    
                }
                
                if ( dr1!=null && ( dr==null || dr1.intersects(dr) ) ) {
                    dr= dr1;
                    s= s.substring(0,last) + s.substring(last).replaceAll(flast, frepl);
                    int res= resolution.get(best);
                    int count=0;
                    for ( int j=0; j<n; j++ ) {
                        if ( resolution.get(j)>res ) {
                            count++;
                            search.set(j,null);
                        }
                    }
                    if ( count==search.size() ) {
                        return s;
                    } else {
                        best= -1;
                        last= -1; //search for courser resolutions
                    }
                } else {
                    return s;
                }
            } else {
                return s;
            }
        }
    }
    
    /**
     * something which returns a new URI given an old one.
     */
    public interface URIMap {
        public String map(String uri);
    }
    
    private static final Map<String,URIMap> makeAggSchemes= new HashMap<>();
    
    /**
     * register a map which might modify a URI so that it uses aggregation. 
     * This was introduced for "vap+inline" URIs which must be taken apart and
     * then each of the getDataSet calls is aggregated.
     * @param scheme the scheme where this should be used, e.g. "vap+inline"
     * @param map the map, which might return the input URI or an aggregated one.
     */
    public static void addMakeAggregationForScheme( String scheme, URIMap map ) {
        makeAggSchemes.put(scheme,map);
    }
            
    /**
     * attempt to create an equivalent URL that uses an aggregation template
     * instead of the explicit filename.  This also return null when things go wrong.
     * For example, file:/tmp/20091102.dat -> file:/tmp/$Y$m$d.dat?timerange=20091102
     * Also, look for version numbers.  If multiple periods are found, then use $(v,sep) otherwise use numeric $v.
     *<blockquote><pre><small>{@code
     *y= makeAggregation("file:/tmp/20091102.dat")       // file:/tmp/$Y$m$d.dat?timerange=2009-11-02
     *x= makeAggregation("file:/tmp/20091102T02.dat");   // file:/tmp/$Y$m$dT$H.dat?timerange=2009-11-02 2:00 to 3:00
     *}</small></pre></blockquote>
     * @param surl the URI.
     * @return null or the string with aggregations ($Y.dat) instead of filename (1999.dat), or the original filename.
     */
    public static String makeAggregation( String surl ) {
        
        URISplit split= URISplit.parse(surl);
        if ( split.file==null ) {
            return surl;
        }
            
        if ( split.vapScheme!=null ) {
            URIMap map= makeAggSchemes.get(split.vapScheme);
            if ( map!=null ) {
                return map.map(surl);
            }
        }
                
        String yyyy= "/(19|20)\\d{2}/";

        String yyyymmdd= "(?<!\\d)(19|20)(\\d{6})(?!\\d)"; //"(\\d{8})";
        String yyyyjjj= "(?<!\\d)(19|20)\\d{2}\\d{3}(?!\\d)";
        String yyyymm= "(?<!\\d)(19|20)\\d{2}\\d{2}(?!\\d)";
        String yyyy_mm_dd= "(?<!\\d)(19|20)\\d{2}([\\-_/])\\d{2}\\2\\d{2}(?!\\d)";
        String yyyy_jjj= "(?<!\\d)(19|20)\\d{2}([\\-_/])\\d{3}(?!\\d)";
        String yyyymmdd_HH= "(?<!\\d)(19|20)(\\d{6})(\\D)\\d{2}(?!\\d)"; //"(\\d{8})"; 20140204T15
        String yyyymmdd_HHMM= "(?<!\\d)(19|20)(\\d{6})(\\D)\\d{2}\\d{2}(?!\\d)"; //"(\\d{8})"; 20140204T1515

        //DANGER: code assumes starts with 4-digit year and then a delimiter, or no delimiter.  See replaceLast
        
        String version= "([Vv])\\d{2}";                // $v
        String vsep= "([Vv])(\\d+\\.\\d+(\\.\\d+)+)";  // $(v,sep)

        String[] abs= new String[] { yyyymmdd_HHMM, yyyymmdd_HH, yyyy_mm_dd, yyyy_jjj, yyyymmdd, yyyyjjj, yyyymm };

        String timeRange=null;
        for ( String ab : abs ) {
            Matcher m = Pattern.compile(ab).matcher(surl);
            if ( m.find() ) {
                timeRange= m.group(0);
                break; // we found something
            }
        }

        if ( timeRange==null ) return null;

        int day= TimeUtil.DAY;
        int year= TimeUtil.YEAR;
        int month= TimeUtil.MONTH;
        int hour= TimeUtil.HOUR;
        int minute= TimeUtil.MINUTE;

        List<String> search= new ArrayList( Arrays.asList( yyyymmdd_HHMM, yyyymmdd_HH, yyyy_jjj, yyyymmdd, yyyyjjj, yyyymm, yyyy_mm_dd, yyyy ) );
        List<String> replac= new ArrayList( Arrays.asList( "\\$Y\\$m\\$d$3\\$H\\$M", "\\$Y\\$m\\$d$3\\$H", "\\$Y$2\\$j", "\\$Y\\$m\\$d","\\$Y\\$j","\\$Y\\$m", "\\$Y$2\\$m$2\\$d","/\\$Y/" ) );
        List<Integer> resol= new ArrayList( Arrays.asList( minute, hour, day, day, day, month, day, year ) );
        
        // it looks like to have $Y$m01 resolution, we would need to have a flag to only accept the aggregation if the more general one is not needed for other files.
        
        String s;
        try {
            s= replaceLast( split.file, 
                search,
                replac,
                resol );
        } catch ( IllegalArgumentException ex ) {
            logger.log( Level.FINE, ex.getMessage(), ex );
            return null;
        }
        
        try {
            TimeParser tp= TimeParser.create(s);
            timeRange= tp.parse( split.file ).getTimeRange().toString();
            //s= s.replaceFirst(version, "$1\\$2v"); //TODO: version causes problems elsewhere, see line 189.  Why?

            Matcher m;
            m= Pattern.compile(vsep).matcher(s);
            if ( m.find() ) {
                s= s.replaceFirst( m.group(), Matcher.quoteReplacement(m.group(1)+"$(v,sep)") );
            }
            m= Pattern.compile(version).matcher(s);
            if ( m.find() ) {
                s= s.replaceFirst( m.group(), Matcher.quoteReplacement(m.group(1)+"$v") );
            }
            
            split.file= s;
            Map<String,String> params= URISplit.parseParams(split.params);
            if ( !params.containsKey("timerange") ) {
                params.put( "timerange", timeRange );
                split.params= URISplit.formatParams(params);
            }

            String result= URISplit.format(split);
            
            return result;
            
        } catch ( IllegalArgumentException ex ) {
            return null; // I had the file in my directory: "file:///home/jbf/das2Server?dataset=juno%2Fwaves%2Fflight%2Fsurvey.dsdf;start_time=$Y-$m-$dT15:00:00.000Z;end_time=$Y-$m-$dT19:00:00.000Z;params=EINT;server=dataset"
        } catch ( ParseException ex ) {
            return null;
        }
        
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
	StringBuilder buf= new StringBuilder(label.length());
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

    /**
     * return true if the string is a java identifier.
     * @param label
     * @return
     */
    public static boolean isJavaIdentifier( String label ) {
        if ( label.length()==0 || !Character.isJavaIdentifierStart(label.charAt(0)) ) return false;
        for ( int i=1; i<label.length(); i++ ) {
            if ( !Character.isJavaIdentifierPart(label.charAt(i) ) ) return false;
        }
        return true;
    }

    private static volatile Pattern doublePattern=null; // http://www.cs.umd.edu/~pugh/java/memoryModel/DoubleCheckedLocking.html
    
    /**
     * from java.lang.Double javadoc, this tests if a number is a double.
     * @param myString
     * @return true if the number is a double.
     */
    public static boolean isJavaDouble( String myString ) {
        if ( doublePattern==null ) {
            synchronized (DataSourceUtil.class) {
                if (doublePattern == null) {
                    final String Digits = "(\\p{Digit}+)";
                    final String HexDigits = "(\\p{XDigit}+)";
                    // an exponent is 'e' or 'E' followed by an optionally 
                    // signed decimal integer.
                    final String Exp = "[eE][+-]?" + Digits;
                    final String fpRegex =
                            ("[\\x00-\\x20]*" + // Optional leading "whitespace"
                            "[+-]?(" + // Optional sign character
                            "NaN|" + // "NaN" string
                            "Infinity|"
                            + // "Infinity" string
                            // A decimal floating-point string representing a finite positive
                            // number without a leading sign has at most five basic pieces:
                            // Digits . Digits ExponentPart FloatTypeSuffix
                            // 
                            // Since this method allows integer-only strings as input
                            // in addition to strings of floating-point literals, the
                            // two sub-patterns below are simplifications of the grammar
                            // productions from the Java Language Specification, 2nd 
                            // edition, section 3.10.2.
                            // Digits ._opt Digits_opt ExponentPart_opt FloatTypeSuffix_opt
                            "(((" + Digits + "(\\.)?(" + Digits + "?)(" + Exp + ")?)|"
                            + // . Digits ExponentPart_opt FloatTypeSuffix_opt
                            "(\\.(" + Digits + ")(" + Exp + ")?)|"
                            + // Hexadecimal strings
                            "(("
                            + // 0[xX] HexDigits ._opt BinaryExponent FloatTypeSuffix_opt
                            "(0[xX]" + HexDigits + "(\\.)?)|"
                            + // 0[xX] HexDigits_opt . HexDigits BinaryExponent FloatTypeSuffix_opt
                            "(0[xX]" + HexDigits + "?(\\.)" + HexDigits + ")"
                            + ")[pP][+-]?" + Digits + "))"
                            + "[fFdD]?))"
                            + "[\\x00-\\x20]*");// Optional trailing "whitespace"
                    doublePattern= Pattern.compile(fpRegex);
                }
            }
        }
            
        return doublePattern.matcher(myString).matches();
        
    }
    
    public static String strjoin(Collection<String> c, String delim) {
        StringBuilder result = new StringBuilder();
        for (String s : c) {
            if (result.length() > 0) {
                result.append(delim);
            }
            result.append(s);
        }
        return result.toString();
    }

    public static String strjoin( long[] dims, String delim ) {
        StringBuilder sdims= new StringBuilder();
        if ( dims.length>0 ) {
                sdims.append( dims[0] );
                for ( int i=1; i<dims.length; i++ ) {
                        sdims.append( delim ) .append( dims[i] );
                }
        }
        return sdims.toString();
    }

    public static String strjoin( int[] dims, String delim ) {
        StringBuilder sdims= new StringBuilder();
        if ( dims.length>0 ) {
                sdims.append( dims[0] );
                for ( int i=1; i<dims.length; i++ ) {
                        sdims.append( delim ).append( dims[i]);
                }
        }
        return sdims.toString();
    }

    /**
     * transfers the data from one channel to another.  src and dest are
     * closed after the operation is complete.
     * @param src
     * @param dest
     * @throws java.io.IOException
     */
    public static void transfer( ReadableByteChannel src, WritableByteChannel dest ) throws IOException {
        try {
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
        } finally {
            dest.close();
            src.close();
        }
    }


    /**
     * transfers the data from one channel to another.  src and dest are
     * closed after the operation is complete.
     * @param src
     * @param dest
     * @throws java.io.IOException
     */
    public static void transfer( InputStream src, OutputStream dest ) throws IOException {
        final byte[] buffer = new byte[ 16 * 1024 ];

        int i= src.read(buffer);
        while ( i != -1) {
            dest.write(buffer,0,i);
            i= src.read(buffer);
        }
        dest.close();
        src.close();
    }

    /**
     * returns the [ start, stop, stride ] or [ start, -1, -1 ] for slice, but also
     * supports slice notations like [:,1]. This is
     * provided to reduce code and for uniform behavior.
     * 
     * Examples:
     * <ul>
     * <li>[::1,:]
     * <li>[:,2]
     * </ul>
     * @param constraint, such as "[0:100:2]" for even records between 0 and 100, non-inclusive.
     * @param qubeDims the dimension of the data.
     * @return the [startRecord,stopRecordExclusive,stride]
     * @throws java.text.ParseException when the constraint cannot be parsed.
     */
    public static Map<Integer,long[]> parseConstraint(String constraint, long[] qubeDims ) throws ParseException {
        String[] ss;
        if ( constraint==null ) {
            ss= new String[] { null, null, null }; 
        } else {
            if ( constraint.startsWith("[") && constraint.endsWith("]") ) {
                constraint= constraint.substring(1,constraint.length()-1);
            }
            ss= constraint.split("\\,",-2);
        }
        Map<Integer,long[]> result= new HashMap<>();
        int ndim= Math.min(ss.length,qubeDims.length);
        for ( int i=0; i<ndim; i++ ) {
            long[] r= parseConstraint( ss[i], qubeDims[i] );
            result.put( i, r );
        }
        return result;
    }
    
    /**
     * returns [ start, stop, stride ] or [ start, -1, -1 ] for slice.  This is
     * provided to reduce code and for uniform behavior.
     * See CdfJavaDataSource, which is where this was copied from.
     * @param constraint, such as "[0:100:2]" for even records between 0 and 100, non-inclusive.
     * @param recCount the number of records available for this variable
     * @return the [startRecord,stopRecordExclusive,stride]
     * @throws java.text.ParseException when the constraint cannot be parsed.
     */
    public static long[] parseConstraint(String constraint, long recCount) throws ParseException {
        long[] result = new long[]{0, recCount, 1};
        if (constraint == null) {
            return result;
        } else {
            if ( constraint.startsWith("[") && constraint.endsWith("]") ) {
                constraint= constraint.substring(1,constraint.length()-1);
            }
            try {
                String[] ss= constraint.split(":",-2);
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
            if ( result[0]>recCount ) result[0]= recCount;
            if ( result[1]>recCount ) result[1]= recCount;
            return result;
        }
    }

    /**
     * @see Autoplot org.autoplot.AutoplotUtil.guessRenderType.
     * @param fillds
     * @return
     */
    public static String guessRenderType(QDataSet fillds) {
        String spec;

        String specPref= "spectrogram";

        String srenderType= (String) fillds.property(QDataSet.RENDER_TYPE);
        if ( srenderType!=null && srenderType.length()>0 ) {
            return srenderType;
        }

        QDataSet dep1 = (QDataSet) fillds.property(QDataSet.DEPEND_1);
        QDataSet plane0 = (QDataSet) fillds.property(QDataSet.PLANE_0);
        QDataSet bundle1= (QDataSet) fillds.property(QDataSet.BUNDLE_1);

        if ( fillds.property( QDataSet.JOIN_0 )!=null ) {
            if ( fillds.length()==0 ) {
                return "series";
            }
            dep1 = (QDataSet) fillds.property(QDataSet.DEPEND_1,0);
            plane0 = (QDataSet) fillds.property(QDataSet.PLANE_0,0);
            bundle1= (QDataSet) fillds.property(QDataSet.BUNDLE_1,0);
        }

        if (fillds.rank() >= 2) {
            if ( bundle1!=null || (dep1 != null && SemanticOps.isBundle(fillds) || Ops.isLegacyBundle(fillds) ) ) {
                if (fillds.length() > 80000) {
                    spec = "hugeScatter";
                } else {
                    spec = "series";
                }
                if ( bundle1!=null ) {
                    if ( bundle1.length()==3 && bundle1.property(QDataSet.DEPEND_0,2)!=null ) { // bad kludge
                        spec= "colorScatter";
                    } else if ( bundle1.length()==3 || bundle1.length()==4 ) {
                        Units u0= (Units) bundle1.property(QDataSet.UNITS,0);
                        if ( u0==null ) u0= Units.dimensionless;
                        Units u1= (Units) bundle1.property(QDataSet.UNITS,1);
                        if ( u1==null ) u1= Units.dimensionless;
                        Units u3= (Units) bundle1.property(QDataSet.UNITS,bundle1.length()-1);
                        if ( u3!=null && UnitsUtil.isOrdinalMeasurement(u3) && u0.getOffsetUnits().isConvertibleTo(u1) ) {
                            spec= "eventsBar";
                        }
                    }
                }
            } else {
                if ( dep1==null && fillds.rank()==2 && fillds.length()>3 && fillds.length(0)<4 ) { // Vector quantities without labels. [3x3] is a left a matrix.
                    spec = "series";
                } else {
                    spec = specPref;
                }
            }
        } else if ( fillds.rank()==0 || fillds.rank()==1 && SemanticOps.isBundle(fillds) ) {
            spec= "digital";

        } else {
            if (fillds.length() > 80000) {
                spec = "hugeScatter";
            } else {
                spec = "series";
            }

            if (plane0 != null) {
                Units u = (Units) plane0.property(QDataSet.UNITS);
                if (u==null) u= Units.dimensionless;
                if (u != null && (UnitsUtil.isRatioMeasurement(u) || UnitsUtil.isIntervalMeasurement(u))) {
                    spec = "colorScatter";
                }
            }
        }

        return spec;
    }

    /**
     * open the URL in a browser.   Borrowed from http://www.centerkey.com/java/browser/.  
     * See also openBrowser in Autoplot,
     * which this replaces.
     * 
     * Java 6 introduced standard code for doing this.  The old code is still 
     * used in case there's a problem.
     *
     * @param url the URL
     */
    public static void openBrowser(String url) {
        try {
            java.net.URI target= DataSetURI.getResourceURI(url);
            Desktop.getDesktop().browse( target );
            return;
            
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch ( UnsupportedOperationException ex ) {
            logger.log(Level.SEVERE, null, ex); // Linux, for example.
        }
        
        final String errMsg = "Error attempting to launch web browser";
        String osName;
        try {
            osName =System.getProperty("os.name", "applet" );
        } catch (SecurityException ex) {
            osName= "applet";
        }
        try {
            if (osName.startsWith("Mac OS")) {
                Class fileMgr = Class.forName("com.apple.eio.FileManager");
                Method openURL = fileMgr.getDeclaredMethod("openURL", new Class[]{String.class});
                openURL.invoke(null, new Object[]{url});
            } else if (osName.startsWith("Windows")) {
                Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
            } else if (osName.equals("applet")) {
                throw new RuntimeException("applets can't start browser yet");
                //TODO: this shouldn't be difficult, just get the AppletContext.
            } else { //assume Unix or Linux
                String[] browsers = {"firefox", "opera", "konqueror", "epiphany", "mozilla", "netscape"};
                String browser = null;
                for (int count = 0; count < browsers.length && browser == null; count++) {
                    if (Runtime.getRuntime().exec(new String[]{"which", browsers[count]}).waitFor() == 0) {
                        browser = browsers[count];
                    }
                }
                if (browser == null) {
                    throw new Exception("Could not find web browser");
                } else {
                    Runtime.getRuntime().exec(new String[]{browser, url});
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, errMsg + ":\n" + e.getLocalizedMessage());
        }
    }

    /**
     * returns true if the text appears to be html.  Right now the test is
     * for "<htm" "<HTM" or "<!doc" "<!DOC".
     *
     * @param text the text.
     * @return true if the stream appears to be html.
     */
    public static boolean isHtmlStream( String text ) {
        return text.toLowerCase().startsWith("<!doc") || text.toLowerCase().startsWith("<html");
    }

    /**
     * for IDL, where I can't look up a class
     * @param dss
     * @return
     */
    public static TimeSeriesBrowse getTimeSeriesBrowse( DataSource dss ) {
        return dss.getCapability( TimeSeriesBrowse.class );
    }
    
    /**
     * We've loaded the data, but it needs to be trimmed to exactly what the TSB requests, because a time axis
     * is not visible.  This was introduced to support where TSB returns data that needs to be trimmed.
     * @param tsbData
     * @param timeRange the range where the data was requested.
     * @return the trimmed data
     * @see https://sourceforge.net/p/autoplot/bugs/1559/
     */
    public static QDataSet trimScatterToTimeRange(QDataSet tsbData, DatumRange timeRange) {
        if ( tsbData==null ) return null;
        // find the timetags
        QDataSet time= null;
        //TODO: rank 3 joins 
        QDataSet dep0= (QDataSet) tsbData.property(QDataSet.DEPEND_0);
        if ( dep0!=null ) {
            time= (QDataSet) dep0.property(QDataSet.DEPEND_0);
        } else {
            if ( SemanticOps.isBundle(tsbData) ) {
                time= Ops.unbundle( tsbData, 0 );
            }
        }
        if ( time!=null && UnitsUtil.isTimeLocation( SemanticOps.getUnits(time) ) && time.rank()==1 ) {
            QDataSet w= Ops.where( Ops.within( time, timeRange ) );
            tsbData= DataSetOps.applyIndex( tsbData, w );
            return tsbData;
            
        } else {
            return tsbData;
        }
    }
    

    /**
     * With the URI, establish if it has time series browse and set the timerange
     * to the given timerange if it does.  For example, modify the bookmark so that the
     * timerange is the current axis timerange before using it.
     * @param uri An Autoplot URI, which must resolve to a DataSource.
     * @param timeRange the timerange to use.  If this is null or a non-timerange, then the URI is returned unchanged.
     * @param mon the monitor
     * @return A URI that would yield data from the same dataset but for a different time.
     * @throws java.net.URISyntaxException
     * @throws java.io.IOException
     * @throws java.text.ParseException
     */
    public static String setTimeRange( String uri, DatumRange timeRange, ProgressMonitor mon ) throws URISyntaxException, IOException, ParseException {
        if ( timeRange==null ) {
            logger.fine("timeRange is null");
            return uri;
        }
        if ( !UnitsUtil.isTimeLocation( timeRange.getUnits() ) ) {
            logger.fine("timeRange is not UTC time range");
            return uri;
        }
        DataSourceFactory f = DataSetURI.getDataSourceFactory( DataSetURI.getURI(uri), mon );
        TimeSeriesBrowse tsb= f.getCapability( TimeSeriesBrowse.class );
        if ( tsb!=null ) {
            tsb.setURI(uri);
            tsb.setTimeRange(timeRange);
            uri= tsb.getURI();
            logger.log( Level.FINER, "resetting timerange to {0}: {1}", new Object[]{timeRange, uri});
        } else {
            logger.finer("uri is not a TimeSeriesBrowse");
        }

        return uri;
    }
    
    
    /**
     * returns a variable name generated from the URI.  This was written for Jython scripting.  A future
     * version of this might attempt to load the resource, and use the name from the result.
     * @param uri
     * @return
     */
    public static String guessNameFor( String uri ) {
        URISplit split= URISplit.parse(uri);
        Map<String,String> args= URISplit.parseParams(split.params);
        String name="ds"; //default name
        String altName=null;
        if ( args.containsKey(URISplit.PARAM_ARG_0) ) {
            altName= Ops.safeName( args.get(URISplit.PARAM_ARG_0) );
        } else if ( args.containsKey(URISplit.PARAM_ID) ) {
            altName= Ops.safeName( args.get(URISplit.PARAM_ID) );
        } else if ( args.containsKey("column") ) {
            altName= Ops.safeName( args.get("column") );
        }
        if ( altName!=null && altName.length()<30 ) {
            name= altName;
        }
        // identifiers starting with upper case are going to bug me.  reset the case. UPPER->upper and UpperCase->upperCase
        if ( name.length()>1 && Character.isUpperCase(name.charAt(0)) && Character.isUpperCase(name.charAt(1) ) ) {
            name= name.toLowerCase();
        } else if ( name.length()>0 && Character.isUpperCase(name.charAt(0) ) ) {
            name= name.substring(0,1).toLowerCase() + name.substring(1);
        }
        
        return name;
    }
    
    /**
     * return a one-line string representation of the exception.  This was introduced
     * when a NullPointerException was represented as "null", and it was somewhat
     * unclear about what was going on.
     * @param ex an exception
     * @return a 1-line string representation of the error, for the end user.
     */
    public static String getMessage( Exception ex ) {
        if ( ex==null ) {
            throw new IllegalArgumentException("Expected exception, but got null");
        }
        if ( ex instanceof NullPointerException ) {
            return ex.toString();
        } else {
            if ( ex.getMessage()==null ) {
                return ex.toString();
            } else if ( ex.getMessage().length()<5 ) {
                return ex.toString() + ": " +ex.getMessage();
            } else {
                return ex.getMessage();
            }
        }
    }

    public static void main(String[] args ) {
        String surl= "ftp://virbo.org/LANL/LANL1991/SOPA+ESP/H0/LANL_1991_080_H0_SOPA_ESP_19920308_V01.cdf?L";
        //String surl= "http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hyd_h0/2000/po_h0_hyd_20000109_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX";
        System.err.println( makeAggregation(surl) ); //logger okay
        System.err.println( makeAggregation(surl) ); //logger okay
    }

    /**
     * this will make the exception available.  (Someday. TODO: where is this used?)
     * @param parent
     * @param msg
     * @param title
     * @param messageType
     * @param causeBy
     */
    public static void showMessageDialog( Component parent, String msg, String title, int messageType, Exception causeBy ) {
        JOptionPane.showMessageDialog( parent, msg, title, messageType );
    }
    
    /**
     * Matlab uses net.sf.saxon.xpath.XPathEvaluator by default, so we explicitly look for the Java 6 one.
     * @return com.sun.org.apache.xpath.internal.jaxp.XPathFactoryImpl, probably.
     */
    public static XPathFactory getXPathFactory() {
        XPathFactory xpf;
        try {
            xpf= XPathFactory.newInstance( XPathFactory.DEFAULT_OBJECT_MODEL_URI, "com.sun.org.apache.xpath.internal.jaxp.XPathFactoryImpl", null );
        } catch (XPathFactoryConfigurationException ex) {
            xpf= XPathFactory.newInstance();
            logger.log( Level.INFO, "using default xpath implementation: {0}", xpf.getClass());
        }
        return xpf;
    }
//    /**
//     * Used for debugging, this dumps the data out to a das2stream.
//     * @param ds
//     * @param f 
//     */
//    public static void dumpToFile( QDataSet ds, String f ) {
//        try {
//            SimpleStreamFormatter fo= new SimpleStreamFormatter();
//            OutputStream fout= new FileOutputStream(f);
//            try {
//                fo.format( ds, fout, true);
//            } finally {
//                fout.close();
//            }
//        } catch (StreamException ex) {
//            ex.printStackTrace();
//        } catch ( IOException ex ) {
//            ex.printStackTrace();
//        }
//    }
}
