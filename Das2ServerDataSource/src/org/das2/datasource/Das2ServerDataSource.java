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
import org.das2.stream.StreamTool;
import org.das2.util.DasProgressMonitorInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.das2.client.Authenticator;
import org.das2.client.DasServer;
import org.das2.client.Key;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.AbstractDataSet;
import org.das2.dataset.DataSetAdapter;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.capability.TimeSeriesBrowse;

/**
 *
 * @author jbf
 */
class Das2ServerDataSource extends AbstractDataSource {

    public Das2ServerDataSource(URI uri) {
        super(uri);
        if ( !"no".equals( params.get("tsb") ) ) {
            addCability( TimeSeriesBrowse.class, getTimeSeriesBrowse() );
        }
        HashMap params2 = new HashMap(params);
        params2.put("server", "dataset");
        timeRange= DatumRangeUtil.parseTimeRangeValid( params2.get("start_time") + " to "+ params2.get("end_time" ) );
        resolution= null;
    }

    Logger logger= Logger.getLogger("das2serverDataSource");

    DatumRange timeRange;
    Datum resolution;
    String dsParams;
    List<String> tcaDesc;
    String key;
    Map dsdfParams=null;

    @Override
    public synchronized QDataSet getDataSet( final ProgressMonitor mon ) throws Exception {
        //http://www-pw.physics.uiowa.edu/das/das2Server
        //?dataset=das2_1/voyager1/pws/sa-4s-pf.new
        //&start_time=2004-01-01&end_time=2004-01-06&server=dataset&ascii=1

        //This is a TCA request, which requests data at a given interval:
        //http://www-pw.physics.uiowa.edu/das/das2Server
        //?dataset=voyager1%2Ftca%2Fv1ephemSun
        //&start_time=1980-11-12T05%3A00%3A00.000Z
        //&end_time=1980-11-12T06%3A25%3A01.000Z
        //&server=dataset
        //&interval=300.0    // interval in seconds
        //&ascii=1'

        Map params2 = new LinkedHashMap();
        Map otherParams= new LinkedHashMap( params );
        otherParams.remove("start_time");
        otherParams.remove("end_time");
        otherParams.remove("resolution");
        otherParams.remove("dataset");
        otherParams.remove("tsb");

        String item= (String) otherParams.remove("item");
        String interval= (String)otherParams.remove("interval");

        dsParams= (String)  URISplit.formatParams(otherParams);

        params2.put("server", "dataset");
        if ( timeRange!=null ) {
            params2.put("start_time", URLEncoder.encode(timeRange.min().toString()) );
            params2.put("end_time", URLEncoder.encode(timeRange.max().toString()) );
        } 
        if ( resolution!=null ) {
            params2.put("resolution", ""+resolution.doubleValue(Units.seconds) );
        }
        String dataset= params.get("dataset");
        if ( dataset==null ) {
            dataset= params.get("arg_0");
        }

        if ( interval!=null ) { // TCAs use interval parameter
            params2.put("interval",URLEncoder.encode(interval, "US-ASCII"));
            params2.remove("resolution");
        }
        
        params2.put("dataset", URLEncoder.encode(dataset) );
        params2.put("params", URLEncoder.encode(dsParams.replaceAll("\\+", " " )) );
        URL url2 = new URL("" + this.resourceURI + "?" + URISplit.formatParams(params2));

        //if ( interval!=null && tcaDesc==null ) {
        if ( true ) {
            if ( dsdfParams==null ) {
                String dsdfURL= this.resourceURI + "?server=dsdf&dataset=" + dataset;
                URL url3= new URL( dsdfURL );
                logger.fine("opening "+url3);
                InputStream in = url3.openStream();

                ReadableByteChannel channel = Channels.newChannel(in);

                final Map map= new LinkedHashMap();

                DataSetStreamHandler handler = new DataSetStreamHandler( new HashMap(), mon ) {
                    @Override
                    public void streamDescriptor(StreamDescriptor sd) throws StreamException {
                        super.streamDescriptor(sd);
                        map.putAll(sd.getProperties());
                    }
                };
                StreamTool.readStream(channel, handler);
                channel.close();
                dsdfParams= map;
            }

            tcaDesc=new ArrayList<String>();

            int iplane=0;
            String label= (String)dsdfParams.get("label");
            while ( label!=null ) {
                tcaDesc.add(label);
                iplane++;
                label= (String)dsdfParams.get("plane_"+iplane+".label");
            }

            String groupAccess= (String)dsdfParams.get("groupAccess" );
            if ( groupAccess!=null && groupAccess.trim().length()>0 ) {
                if ( key==null ) {
                    Authenticator authenticator;
                    authenticator= new Authenticator( DasServer.create(this.resourceURI.toURL()),groupAccess);
                    Key key2= authenticator.authenticate();
                    if ( key2!=null ) {
                        params2.put("key", key2.toString() );
                        url2= new URL("" + this.resourceURI + "?" + URISplit.formatParams(params2));
                    }
                    key= key2.toString();
                } else {
                    params2.put("key", key );
                    url2= new URL("" + this.resourceURI + "?" + URISplit.formatParams(params2));
                }
            }
            
        }

        boolean qds= "1".equals( dsdfParams.get("qstream") );

        logger.fine( "opening "+ ( qds ? "as qstream " : "as das2stream " ) + url2 );
        InputStream in = url2.openStream();

        final DasProgressMonitorInputStream mpin = new DasProgressMonitorInputStream(in, mon);

        ReadableByteChannel channel = Channels.newChannel(mpin);

        QDataSet result1;

        if ( qds ) {

            try {
                org.virbo.qstream.QDataSetStreamHandler eh= new org.virbo.qstream.QDataSetStreamHandler();
                org.virbo.qstream.StreamTool.readStream( channel,eh );

                result1= eh.getDataSet();

            } catch ( org.virbo.qstream.StreamException ex ) {
                if ( ex.getCause()!=null && ( ex.getCause() instanceof java.io.InterruptedIOException ) ) {
                    ex.printStackTrace();
                    //TODO CancelledOperationException
                    throw (java.io.InterruptedIOException)ex.getCause();
                } else {
                    ex.printStackTrace();
                    throw ex;
                }
            }
            
        } else {
            DataSetStreamHandler handler = new DataSetStreamHandler(new HashMap(), mon) {

                @Override
                public void streamDescriptor(StreamDescriptor sd) throws StreamException {
                    super.streamDescriptor(sd);
                    if ( mon.getTaskSize() != -1) { // progress messages are on the stream.
                       mpin.setEnableProgressPosition(false);
                    }
                }
            };

            try {
                StreamTool.readStream(channel, handler);
            } catch ( StreamException ex ) {
                if ( ex.getCause()!=null && ( ex.getCause() instanceof java.io.InterruptedIOException ) ) {
                    ex.printStackTrace();
                    //TODO CancelledOperationException
                    throw (java.io.InterruptedIOException)ex.getCause();
                } else {
                    ex.printStackTrace();
                    throw ex;
                }
            }

            mon.finished(); // just to be sure;

            DataSet ds = handler.getDataSet();

            if ( ds==null ) {
                throw new RuntimeException("failed to get dataset, without explanation!  (Possibly no records)");
            }

            AbstractDataSet result;
            if ( item==null || item.equals("") || item.equals("0") ) {
                result= DataSetAdapter.create(ds); //TODO: danger if it has TCA planes, it will return bundle.  Probably not what we want.
            } else {
                DataSet das2ds;
                das2ds= ds.getPlanarView( item );
                if ( das2ds==null ) {
                    int iitem= Integer.parseInt(item);
                    das2ds= ds.getPlanarView( "plane_"+iitem );
                }
                if ( das2ds==null ) throw new IllegalArgumentException("no such plane, looking for " + item  );
                result= DataSetAdapter.create( das2ds ); // fragile
            }

            if ( tcaDesc!=null && tcaDesc.size()>0 ) {
                if ( item==null ) {
                    result.putProperty( QDataSet.LABEL, tcaDesc.get(0) );
                } else {
                    result.putProperty( QDataSet.LABEL, tcaDesc.get( Integer.parseInt(item) ) );
                }
            }
            result1= result;

        }

        if ( timeRange==null ) timeRange= DatumRangeUtil.parseTimeRange( params2.get("start_time") + " to "+ params2.get("end_time" ) );

        logger.fine("  done. ");
        return result1;
    }

    public TimeSeriesBrowse getTimeSeriesBrowse() {
        return new TimeSeriesBrowse() {
            public void setTimeRange(DatumRange dr) {
                timeRange= dr;
            }

            public void setTimeResolution(Datum d) {
                resolution= d;
            }

            public String getURI() {
                try {
                    String stime= URLEncoder.encode( timeRange.min().toString(), "US-ASCII" );
                    String etime= URLEncoder.encode( timeRange.max().toString(), "US-ASCII" );
                    String sparams= "dataset="+params.get( "dataset" )
                            + "&start_time=" + stime
                            + "&end_time=" + etime
                            + "&resolution=" + resolution.doubleValue(Units.seconds);
                    if ( dsParams!=null )  sparams+= "&" + dsParams;
                    return "vap+das2Server:" + resourceURI + "?" + sparams;
                } catch ( UnsupportedEncodingException ex ) {
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
    public String getURI() {
        return super.getURI();
    }

    @Override
    public String toString() {
        return this.resourceURI + "?" + params.get( "dataset" );
    }


}
