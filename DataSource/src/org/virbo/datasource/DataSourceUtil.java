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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
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
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.datum.TimeParser;
import org.das2.datum.UnitsUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.capability.TimeSeriesBrowse;
import org.virbo.dsops.Ops;

/**
 *
 * @author jbf
 */
public class DataSourceUtil {

    /**
     * remove escape sequences like %20 to create a human-editable string
     * This contains a kludge that looks for single spaces that are the result of
     * cut-n-pasting on Linux.  If there is a space and a "%3A", then single spaces
     * are removed.
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
            return s;
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }
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

            DatumRange dr = null;
            // remove parameter
            sagg = URISplit.removeParam(sagg, "timerange");
            TimeParser tp;
            try {
                tp= TimeParser.create(sagg);
                tp.parse(surl);
            } catch (ParseException ex) {
                continue;
            } catch ( IllegalArgumentException ex ) {
                ex.printStackTrace(); 
                continue; // bad format code "N" from "file:///c:/WINDOWS/$NtUninstallKB2079403$/"
            }
            dr = tp.getTimeRange();
            DatumRange dr1= dr; // keep track of the first one to measure continuity.

            boolean okay= true;

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
            if ( moveUs.size()>4 && sagg.contains("$d") ) {
                String sagg1= sagg.replace("$d","01");
                TimeParser tp1= TimeParser.create(sagg1);
                boolean fail= false;
                for ( int i=0; i<moveUs.size(); i++ ) {
                    try {
                        DatumRange drtest= tp1.parse(moveUs.get(i)).getTimeRange();
                        if ( ! tp1.format(drtest).equals(moveUs.get(i)) ) {
                            fail= true;
                            break;
                        }
                    } catch ( ParseException ex ) {
                        fail= true;
                        break;
                    }
                }
                if ( fail==false ) {
                    sagg= sagg1;
                }
            }

            // more than one file, and then five files or fairly continuous.
            if ( loose || moveUs.size()>0 && ( moveUs.size()>4 || nc<((1+moveUs.size())*2)  ) ) { // reject small aggregations
                notAccountedFor.removeAll(moveUs);
                accountedFor.addAll(moveUs);
                result.add( URISplit.putParam(sagg, "timerange", dr.toString()) );
            } else {
                notAccountedFor.removeAll(moveUs);
            }

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
     * return the replacement or null.  remove the used items.
     * @param s
     * @param search
     * @return
     */
    private static String replaceLast( String s, List<String> search, List<String> replaceWith, List<Integer> resolution ) {
        Map<String,Integer> found= new HashMap();
        int last= -1;
        String flast= null;
        String frepl= null;
        int best= -1;
        int n= search.size();

        while (true ) {
            for ( int i=0; i<n; i++ ) {
                if ( search.get(i)==null ) continue; // search.get(i)==null means that search is no longer elagable.
                Matcher m= Pattern.compile(search.get(i)).matcher(s);
                int idx= -1;
                while ( m.find() ) idx= m.start();
                if ( idx>-1 ) {
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
                s= s.substring(0,last) + s.substring(last).replaceAll(flast, frepl);
                int res= resolution.get(best);
                int count=0;
                for ( int i=0; i<n; i++ ) {
                    if ( resolution.get(i)>res ) {
                        count++;
                        search.set(i,null);
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
        }
    }
    /**
     * attempt to create an equivalent URL that uses an aggregation template
     * instead of the explicit filename.
     * For example, file:/tmp/20091102.dat -> file:/tmp/$Y$m$d.dat?timerange=20091102
     * @param surl
     * @return the string with aggregations ($Y.dat) instead of filename (1999.dat)
     */
    public static String makeAggregation( String surl ) {
        String yyyy= "/(19|20)\\d{2}/";

        String yyyymmdd= "(?<!\\d)(19|20)(\\d{6})(?!\\d)"; //"(\\d{8})";
        String yyyyjjj= "(?<!\\d)(19|20)\\d{2}\\d{3}(?!\\d)";
        String yyyymm= "(?<!\\d)(19|20)\\d{2}\\d{2}(?!\\d)";
        String yyyy_mm_dd= "(?<!\\d)(19|20)\\d{2}([\\-_/])\\d{2}\\2\\d{2}(?!\\d)";
        String yyyy_jjj= "(?<!\\d)(19|20)\\d{2}([\\-_/])\\d{3}(?!\\d)";
        String yyyymmdd_HH= "(?<!\\d)(19|20)(\\d{6})(\\-)\\d{2}(?!\\d)"; //"(\\d{8})";

        //String version= "([Vv])\\d{2}";

        String result= surl;

        String[] abs= new String[] { yyyymmdd_HH, yyyy_mm_dd, yyyy_jjj, yyyymmdd, yyyyjjj, yyyymm };

        String timeRange=null;
        for ( int i= 0; i<abs.length; i++ ) {
            Matcher m= Pattern.compile(abs[i]).matcher(surl);
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

        List<String> search= new ArrayList( Arrays.asList( yyyymmdd_HH, yyyy_jjj, yyyymmdd, yyyyjjj, yyyymm, yyyy_mm_dd, yyyy ) );
        List<String> replac= new ArrayList( Arrays.asList( "\\$Y\\$m\\$d-\\$H", "\\$Y$2\\$j", "\\$Y\\$m\\$d","\\$Y\\$j","\\$Y\\$m", "\\$Y$2\\$m$2\\$d","/\\$Y/" ) );
        List<Integer> resol= new ArrayList( Arrays.asList( hour, day, day, day, month, day, year ) );
        String s= replaceLast( result, 
                search,
                replac,
                resol );

        try {
            TimeParser tp= TimeParser.create(s);
            timeRange= tp.parse(surl).getTimeRange().toString();
            //s= s.replaceFirst(version, "$1\\$2v"); //TODO: version causes problems elsewhere, see line 189.
            result= s;
            
            return result
                    + ( result.contains("?") ? "&" : "?" )
                    + "timerange="+timeRange;
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
     * returns [ start, stop, stride ] or [ start, -1, -1 ] for slice.  This is
     * provided to reduce code and for uniform behavior.
     * See CdfJavaDataSource, which is where this was copied from.
     * @param constraint, such as "[0:100:2]" for even records between 0 and 100, non-inclusive.
     * @return
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
            return result;
        }
    }

    /**
     * See VirboAutoplot org.virbo.autoplot.AutoplotUtil.guessRenderType.
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
                        if ( u3!=null && UnitsUtil.isOrdinalMeasurement(u3) && u0.getOffsetUnits().isConvertableTo(u1) ) {
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
     * returns true if the stream appears to be html.  Right now the test is
     * for "<htm" "<HTM" or "<!doc" "<!DOC".
     *
     * @param magic
     */
    public static boolean isHtmlStream( String magic ) {
         if ( magic.toLowerCase().startsWith("<!doc") || magic.toLowerCase().startsWith("<html")) {
            return true;
         } else {
             return false;
         }
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
     * returns a variable name generated from the URI.  This was written for Jython scripting.  A future
     * version of this might attempt to load the resource, and use the name from the result.
     * @param uri
     * @return
     */
    public static String guessNameFor( String uri ) {
        URISplit split= URISplit.parse(uri);
        Map<String,String> args= URISplit.parseParams(split.params);
        String name="ds"; //default name
        if ( args.containsKey(URISplit.PARAM_ARG_0) ) {
            name= Ops.safeName( args.get(URISplit.PARAM_ARG_0) );
        } else if ( args.containsKey(URISplit.PARAM_ID) ) {
            name= Ops.safeName( args.get(URISplit.PARAM_ID) );
        } else if ( args.containsKey("column") ) {
            name= Ops.safeName( args.get("column") );
        }
        // identifiers starting with upper case are going to bug me.  reset the case. UPPER->upper and UpperCase->upperCase
        if ( name.length()>1 && Character.isUpperCase(name.charAt(0)) && Character.isUpperCase(name.charAt(1) ) ) {
            name= name.toLowerCase();
        } else if ( name.length()>0 && Character.isUpperCase(name.charAt(0) ) ) {
            name= name.substring(0,1).toLowerCase() + name.substring(1);
        }
        
        return name;
    }

    public static void main(String[] args ) {
        String surl= "http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hyd_h0/2000/po_h0_hyd_20000109_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX";
        System.err.println( makeAggregation(surl) );
                
    }
}
