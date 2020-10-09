/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.inline;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.util.LoggerManager;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.autoplot.jythonsupport.Util;
import org.autoplot.jythonsupport.ui.DataMashUp;

/**
 * This was DefaultTimeSeriesBrowse until it became clear that it didn't properly
 * parse and reformat the URIs like
 * vap+inline:ds1=getDataSet('http://emfisis.physics.uiowa.edu/Flight/RBSP-A/L3/$Y/$m/$d/rbsp-a_magnetometer_4sec-sm_emfisis-L3_$Y$m$d_v$(v,sep).cdf?Mag&slice1=1&timerange=2014-02-23')&ds2=getDataSet('http://emfisis.physics.uiowa.edu/Flight/RBSP-A/L3/$Y/$m/$d/rbsp-a_magnetometer_4sec-sm_emfisis-L3_$Y$m$d_v$(v,sep).cdf?Mag&slice1=0&timerange=2014-02-23')&ds2&timerange=2014-02-23
 * These should have been escaped, but it's probably too late.
 * @author jbf
 */
public class InlineTimeSeriesBrowse implements TimeSeriesBrowse {
    public String uri;
    DatumRange timeRange;
    
    private static final Logger logger= LoggerManager.getLogger("apdss.util");
    
    protected InlineTimeSeriesBrowse() {
    }
    
    /**
     * create the TimeSeriesBrowse with the initial URI which may contain the
     * parameter "timerange."  The timerange may also be specified using the 
     * second parameter.
     * @param uri the URI
     * @param timerange if non-null, then reset with this timerange.
     * @return TimeSeriesBrowse implementation.
     * @throws ParseException 
     * @see org.das2.datum.DatumRangeUtil#parseTimeRange(java.lang.String) 
     */
    public static TimeSeriesBrowse create( String uri, String timerange ) throws ParseException {
        TimeSeriesBrowse tsb= new InlineTimeSeriesBrowse();
        tsb.setURI(uri);
        if ( timerange!=null ) tsb.setTimeRange( DatumRangeUtil.parseTimeRange(timerange) );
        return tsb;
    }
    
    @Override
    public void setURI(String suri) throws ParseException {
        logger.log(Level.FINE, "setURI {0}", suri );
        List<String> script= new ArrayList<>();
        String tr= InlineDataSourceFactory.getScript( suri, script );
        if ( tr!=null ) {
            timeRange= DatumRangeUtil.parseTimeRange(tr.replaceAll("\\+", " "));
        }
        this.uri= suri;
    }

    @Override
    public String getURI() {
        if ( uri==null ) {
            throw new NullPointerException("uri has not been set");
        }
        return this.uri;
    }

    @Override
    public void setTimeRange(DatumRange dr) {
        logger.log(Level.FINE, "setTimeRange {0}", dr);
        if ( uri!=null ) {
            String[] ascript;
            ascript= Util.guardedSplit( uri, '&', '\'', '\"' );
            List<String> script= new ArrayList( Arrays.asList(ascript) );
            boolean modified= false;
            for ( int i=0; i<script.size(); i++ ) {
                String line= script.get(i);
                if ( line.startsWith("timerange") ) {
                    line= "timerange="+dr.toString().replaceAll(" ","+");
                    modified= true;
                }
                script.set(i, line);
            }
            if ( DataMashUp.isDataMashupJythonInline( uri ) && !modified ) {
                int n= script.size();
                script.add( script.get(n-1) );
                script.set( n-1, "timerange="+dr.toString().replaceAll(" ","+") );
            }            
            String uri1= DataSourceUtil.strjoin( script, "&" );
            uri= uri1;
        }
        timeRange= dr;
    }

    @Override
    public DatumRange getTimeRange() {
        return timeRange;
    }

    @Override
    public void setTimeResolution(Datum d) {
        
    }

    @Override
    public Datum getTimeResolution() {
        return null;
    }

    public static boolean reject( Map map, List<String> problems ) {
        if (!map.containsKey("timerange")) {
            problems.add( TimeSeriesBrowse.PROB_NO_TIMERANGE_PROVIDED );
            return true;
        }
        String timeRange = ((String) map.get("timerange"));
        timeRange= timeRange.replaceAll("\\+"," ");
        if (timeRange.length() < 3) { // P2D is a valid timerange
            problems.add( TimeSeriesBrowse.PROB_NO_TIMERANGE_PROVIDED );
            return true;
        }
        try {
            DatumRange dr= DatumRangeUtil.parseTimeRange(timeRange);
            logger.log(Level.FINEST, "timeRange parses to {0}", dr);
        } catch ( ParseException ex ) {
            problems.add( TimeSeriesBrowse.PROB_PARSE_ERROR_IN_TIMERANGE);
            return true;
        }
        return false;
    }

    @Override
    public String blurURI() {
        if ( uri==null ) {
            throw new NullPointerException("uri has not been set");
        } else {
            String[] ascript= Util.guardedSplit( uri, '&', '\'', '\"' );
            List<String> script= new ArrayList( Arrays.asList(ascript) );
            int itr= -1; // should be the last line.
            for ( int i=0; i<script.size(); i++ ) {
                String line= script.get(i);
                if ( line.startsWith("timerange") ) {
                    itr= i;
                }
            }
            // script.remove(itr);  // the problem is the timerange= is what identifies this as a TSB.
            String uri1= DataSourceUtil.strjoin( script, "&" );
            return uri1;   
        }
    }
    
}
