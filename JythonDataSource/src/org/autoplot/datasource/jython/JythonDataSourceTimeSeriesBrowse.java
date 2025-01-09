
package org.autoplot.datasource.jython;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.text.ParseException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.autoplot.datasource.LogNames;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.autoplot.jythonsupport.Util;
import org.autoplot.jythonsupport.ui.DataMashUp;

/**
 * container for the state simply manages the timerange argument.
 * @author jbf
 */
public class JythonDataSourceTimeSeriesBrowse implements TimeSeriesBrowse {

    private static final Logger logger= Logger.getLogger( LogNames.APDSS_JYDS );
    
    DatumRange timeRange;
    String uri;
    JythonDataSource jds;

    JythonDataSourceTimeSeriesBrowse(String uri) {
        this.uri = uri;
    }

    protected void setJythonDataSource( JythonDataSource jds ) {
        this.jds= jds;
    }

    @Override
    public void setTimeRange(DatumRange dr) {
        if ( false ) { // see bug https://sourceforge.net/p/autoplot/bugs/1584/
            try {
                if ( true || dr.intersects( DatumRangeUtil.parseTimeRange("2010-01-01" ) ) ) {
                    System.err.println("here");
                }
            } catch (ParseException ex) {
                Logger.getLogger(JythonDataSourceTimeSeriesBrowse.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        if ( jds!=null ) {
            if ( this.timeRange==null || !( this.timeRange.equals(dr)) ) {
                synchronized ( jds ) {
                    if ( jds.interp!=null ) {
                        logger.fine("TSB resetting interpretter and caching");
                        jds.interp= null; // no caching...  TODO: this probably needs work.  For example, if we zoom in.
                    }
                }
            }
        }
        this.timeRange = dr;
        URISplit split = URISplit.parse(uri);
        Map<String, String> params = URISplit.parseParams(split.params);
        params.put(JythonDataSource.PARAM_TIMERANGE, dr.toString());
        split.params = URISplit.formatParams(params);
        this.uri = URISplit.format(split);
    }

    @Override
    public DatumRange getTimeRange() {
        return this.timeRange;
    }

    @Override
    public void setTimeResolution(Datum d) {
        // do nothing.
    }

    @Override
    public Datum getTimeResolution() {
        return null;
    }

    /**
     * get the URI, bluring it if we discover it didn't need time series browse.
     * TODO: This is experimental.
     * @return 
     */
    @Override
    public String getURI() {
        return uri;
    }

    @Override
    public String blurURI() {
        URISplit split= URISplit.parse(this.uri);
        Map<String,String> params= URISplit.parseParams(split.params);
        params.remove(JythonDataSource.PARAM_TIMERANGE);
        split.params= URISplit.formatParams(params);
        return URISplit.format(split);
    }
    
    @Override
    public void setURI(String suri) throws ParseException {
        this.uri = suri;
        DatumRange tr = URISplit.parseTimeRange(uri);
        if (tr != null) {
            this.timeRange = tr;
        }
    }

    /**
     * allow scripts to implement TimeSeriesBrowse if they check for the parameter "timerange"
     * TODO: this should be reimplemented with a syntax tree.
     * @param uri the URI
     * @param jythonScript the script corresponding to 
     * @return the TSB, or null.
     * @throws java.io.IOException
     * @throws java.text.ParseException
     */
    protected static JythonDataSourceTimeSeriesBrowse checkForTimeSeriesBrowse( String uri, File jythonScript ) throws IOException, ParseException {
        BufferedReader reader=null;
        JythonDataSourceTimeSeriesBrowse tsb1=null;
        try {
            reader = new LineNumberReader( new FileReader( jythonScript ) );

            String line= reader.readLine();
            String timeRangeRegex= "\\'([^']*)?\\'";
            Pattern s= Pattern.compile(".*getParam\\(\\s*\\'timerange\\',\\s*"+timeRangeRegex+"\\s*(,\\s*\\'.*\\')?\\s*\\).*"); 
            while ( line!=null ) {
                String[] ss= Util.guardedSplit( line, '#', '\'', '\"' );
                line= ss[0];
                Matcher m= s.matcher(line);
                if ( m.matches() ) {
                    tsb1= new JythonDataSourceTimeSeriesBrowse(uri);
                    String str= m.group(1); // the default value
                    URISplit split= URISplit.parse(uri);
                    Map<String,String> params= URISplit.parseParams(split.params);
                    String stimerange= params.get( JythonDataSource.PARAM_TIMERANGE );
                    if ( stimerange!=null && stimerange.length()>0 ) {
                        str= params.get( JythonDataSource.PARAM_TIMERANGE );
                        if ( str.startsWith("'") && str.endsWith("'") && str.length()>1 ) {
                            str= str.substring(1,str.length()-1);
                        }
                    }
                    DatumRange tr= DatumRangeUtil.parseTimeRange(str);
                    tsb1.setTimeRange(tr);
                } else if ( line.contains("timerange") && line.contains("getParam(") ) {
                    // There was a strange test here which seemed unnecessary.  TODO: remove this branch
                }
                line= reader.readLine();
            }
        } finally {
            if ( reader!=null ) reader.close();
        }
        return tsb1;

    }
}
