/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.das2.datasource;

import org.das2.client.DataSetStreamHandler;
import org.das2.dataset.DataSet;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.Units;
import org.das2.stream.StreamDescriptor;
import org.das2.stream.StreamException;
import org.das2.util.DasProgressMonitorInputStream;
import org.das2.util.StreamTool;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.DataSetAdapter;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.URLSplit;
import org.virbo.datasource.capability.TimeSeriesBrowse;

/**
 *
 * @author jbf
 */
class Das2ServerDataSource extends AbstractDataSource {

    public Das2ServerDataSource(URL url) {
        super(url);
        if ( !"no".equals( params.get("tsb") ) ) {
            addCability( TimeSeriesBrowse.class, getTimeSeriesBrowse() );
        }
        HashMap params2 = new HashMap(params);
        params2.put("server", "dataset");
        timeRange= DatumRangeUtil.parseTimeRangeValid( params2.get("start_time") + " to "+ params2.get("end_time" ) );
        resolution= null;
    }
    
    DatumRange timeRange;
    Datum resolution;
    String dsParams;

    @Override
    public QDataSet getDataSet( ProgressMonitor mon ) throws Exception {
        //http://www-pw.physics.uiowa.edu/das/das2Server
        //?dataset=das2_1/voyager1/pws/sa-4s-pf.new
        //&start_time=2004-01-01&end_time=2004-01-06&server=dataset&ascii=1

        Map params2 = new LinkedHashMap();
        Map otherParams= new LinkedHashMap( params );
        otherParams.remove("start_time");
        otherParams.remove("end_time");
        otherParams.remove("resolution");
        otherParams.remove("dataset");
        otherParams.remove("tsb");

        dsParams= (String)  URLSplit.formatParams(otherParams);

        params2.put("server", "dataset");
        if ( timeRange!=null ) {
            params2.put("start_time", URLEncoder.encode(timeRange.min().toString()) );
            params2.put("end_time", URLEncoder.encode(timeRange.max().toString()) );
        } 
        if ( resolution!=null ) {
            params2.put("resolution", ""+resolution.doubleValue(Units.seconds) );
        }
        params2.put("dataset", URLEncoder.encode(params.get("dataset") ) );
        params2.put("params", URLEncoder.encode(dsParams) );
        URL url2 = new URL("" + this.resourceURL + "?" + URLSplit.formatParams(params2));

        System.err.println("opening "+url2);
        InputStream in = url2.openStream();

        final DasProgressMonitorInputStream mpin = new DasProgressMonitorInputStream(in, mon);

        ReadableByteChannel channel = Channels.newChannel(mpin);

        DataSetStreamHandler handler = new DataSetStreamHandler(new HashMap(), mon) {

            @Override
            public void streamDescriptor(StreamDescriptor sd) throws StreamException {
                super.streamDescriptor(sd);
            //if (super.taskSize != -1) {
            //    mpin.setEnableProgressPosition(false);
            //}
            }
        };

        StreamTool.readStream(channel, handler);
        DataSet ds = handler.getDataSet();

        if ( ds==null ) {
            throw new RuntimeException("failed to get dataset, without explanation!  (Possibly no records)");
        }

        if ( timeRange==null ) timeRange= DatumRangeUtil.parseTimeRange( params2.get("start_time") + " to "+ params2.get("end_time" ) );
        return DataSetAdapter.create(ds);
    }

    public TimeSeriesBrowse getTimeSeriesBrowse() {
        return new TimeSeriesBrowse() {
            public void setTimeRange(DatumRange dr) {
                timeRange= dr;
            }

            public void setTimeResolution(Datum d) {
                resolution= d;
            }

            public URL getURL() {
                String sparams= "dataset="+params.get( "dataset" ) + "&start_time=" + URLEncoder.encode( timeRange.min().toString() )
                        + "&end_time=" + URLEncoder.encode( timeRange.max().toString() )
                        + "&resolution=" + resolution.doubleValue(Units.seconds);
                if ( dsParams!=null )  sparams+= "&" + dsParams;
                try {
                    return new URL( "" + resourceURL + "?" + sparams);
                } catch (MalformedURLException ex) {
                    throw new RuntimeException(ex);
                }
            }

            public DatumRange getTimeRange() {
                return timeRange;
            }

            public Datum getTimeResolution() {
                return resolution;
            }
        };
    }

    @Override
    public String getURL() {
        // TODO: Cheesy.  ApplicationModel shouldn't call getURL when TimeSeriesBrowse exists.
        TimeSeriesBrowse tsb= getCapability( TimeSeriesBrowse.class );
        if ( tsb!=null ) {
            return getCapability( TimeSeriesBrowse.class ).getURL().toString();
        } else {
            return super.getURL();
        }
    }



}
