/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.das2.datasource;

import java.text.ParseException;
import java.util.logging.Level;
import org.das2.client.DataSetStreamHandler;
import org.das2.dataset.DataSet;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.das2.stream.StreamDescriptor;
import org.das2.stream.StreamException;
import org.das2.stream.StreamTool;
import org.das2.util.DasProgressMonitorInputStream;
import java.io.InputStream;
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
import org.das2.datum.CacheTag;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.AbstractDataSet;
import org.das2.dataset.DataSetAdapter;
import org.das2.util.LoggerManager;
import org.virbo.dataset.BundleDataSet.BundleDescriptor;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.capability.TimeSeriesBrowse;
import org.virbo.qstream.QDataSetStreamHandler;

/**
 *
 * @author jbf
 */
class Das2ServerDataSource extends AbstractDataSource {

    public Das2ServerDataSource(URI uri) throws ParseException {
        super(uri);
        if ( !"no".equals( params.get("tsb") ) ) {
            addCability( TimeSeriesBrowse.class, getTimeSeriesBrowse() );
        }
        HashMap<String,String> params2 = new HashMap(params);
        params2.put("server", "dataset");

        timeRange= new DatumRange( Units.us2000.parse( params2.get("start_time") ), Units.us2000.parse( params2.get("end_time") ) );

        resolution= null;
    }

    private static final Logger logger= LoggerManager.getLogger("apdss.das2server");

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

        mon.started();
        mon.setProgressMessage("sending request");
        
        Map<String,String> params2 = new LinkedHashMap();
        Map<String,String> otherParams= new LinkedHashMap( params );
        otherParams.remove("start_time");
        otherParams.remove("end_time");
        otherParams.remove("resolution");
        otherParams.remove("dataset");
        otherParams.remove("tsb");

        String item= (String) otherParams.remove("item");
        String interval= (String)otherParams.remove("interval");
        String key1= (String) otherParams.remove("key");

        dsParams= (String)  URISplit.formatParams(otherParams);

        params2.put("server", "dataset");
        if ( timeRange!=null ) {
            params2.put("start_time", URLEncoder.encode(timeRange.min().toString(), "US-ASCII") );
            params2.put("end_time", URLEncoder.encode(timeRange.max().toString(), "US-ASCII") );
        } else {
            throw new IllegalArgumentException("timeRange is null");
        }

        // optional explicit resolution
        String sresolution= params.get("_res");

        if ( sresolution!=null ) {
            if ( sresolution.trim().length()==0 || sresolution.equals("0") ) {
                resolution= null;
            } else {
                resolution= Units.seconds.parse(sresolution);
            }
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
        
        params2.put("dataset", URLEncoder.encode(dataset, "US-ASCII") );
        if ( dsParams.length()>0 ) {
            params2.put("params", dsParams );
        }
        URL url2 = new URL("" + this.resourceURI + "?" + URISplit.formatParams(params2));

        //if ( interval!=null && tcaDesc==null ) {
        if ( true ) {
            if ( dsdfParams==null ) {
                String dsdfURL= this.resourceURI + "?server=dsdf&dataset=" + dataset;
                URL url3= new URL( dsdfURL );
                logger.log(Level.FINE, "opening {0}", url3);
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
                if ( key1==null ) {
                    Authenticator authenticator;
                    authenticator= new Authenticator( DasServer.create(this.resourceURI.toURL()),groupAccess);
                    Key key2= authenticator.authenticate();
                    if ( key2!=null ) {
                        params2.put("key", key2.toString() );
                        url2= new URL("" + this.resourceURI + "?" + URISplit.formatParams(params2));
                    }
                } else {
                    params2.put("key", key1 );
                    url2= new URL("" + this.resourceURI + "?" + URISplit.formatParams(params2));
                }
            }
            
        }

        logger.fine( String.valueOf(url2) );

        boolean qds= "1".equals( dsdfParams.get("qstream") );

        logger.log( Level.FINE, "opening {0} {1}", new Object[]{ qds ? "as qstream" : "as das2stream", url2 });
        InputStream in = url2.openStream();

        final DasProgressMonitorInputStream mpin = new DasProgressMonitorInputStream(in, mon);

        ReadableByteChannel channel = Channels.newChannel(mpin);

        QDataSet result1;

        if ( qds ) {

            try {
                org.virbo.qstream.QDataSetStreamHandler eh= new org.virbo.qstream.QDataSetStreamHandler();
                org.virbo.qstream.StreamTool.readStream( channel,eh );

                result1= eh.getDataSet();

                // check if we can flatten rank 2 join that comes from aggregation
                if ( QDataSetStreamHandler.isFlattenableJoin(result1) ) {
                    result1= eh.flattenJoin(result1);
                }

            } catch ( org.virbo.qstream.StreamException ex ) {
                Throwable cause= ex.getCause();
                if ( cause!=null && ( cause instanceof java.io.InterruptedIOException ) ) { 
                    ex.printStackTrace();
                    //TODO CancelledOperationException
                    throw (java.io.InterruptedIOException)ex.getCause();
                } else if ( cause!=null && ( cause instanceof org.das2.dataset.NoDataInIntervalException )) {
                    throw (org.das2.dataset.NoDataInIntervalException)ex.getCause();
                } else if ( ex.getMessage().contains("Empty response from reader")  ) {
                    throw new org.das2.dataset.NoDataInIntervalException(ex.getMessage());
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

            if ( ds.getXLength()==0 ) {
                throw new RuntimeException("empty dataset returned");
            }
            
            AbstractDataSet result;
            if ( item==null || item.equals("") ) {
                result= DataSetAdapter.create(ds); //TODO: danger if it has TCA planes, it will return bundle.  Probably not what we want.
            } else {
                DataSet das2ds;
                das2ds= ds.getPlanarView( item );
                if ( das2ds==null ) {
                    int iitem= Integer.parseInt(item);
                    if ( iitem==0 ) {
                        das2ds= ds.getPlanarView( "" );
                    } else {
                        das2ds= ds.getPlanarView( "plane_"+iitem );
                    }
                }
                if ( das2ds==null ) throw new IllegalArgumentException("no such plane, looking for " + item  );
                result= DataSetAdapter.create( das2ds ); // fragile
            }

            if ( tcaDesc!=null && tcaDesc.size()>0 ) {
                if ( item==null || item.equals("") || item.equals("0") ) {
                    QDataSet bds= (QDataSet)result.property(QDataSet.BUNDLE_1);
                    if ( bds!=null && bds instanceof BundleDescriptor ) {
                        BundleDescriptor bds1= (BundleDescriptor)bds;
                        for ( int i=0; i<bds1.length(); i++ ) {
                            bds1.putProperty( QDataSet.LABEL, i, tcaDesc.get(i) );
                        }
                    } else {
                        result.putProperty( QDataSet.LABEL, tcaDesc.get(0) );
                    }
                } else {
                    result.putProperty( QDataSet.LABEL, tcaDesc.get( Integer.parseInt(item) ) );
                }
            }
            result1= result;

        }


        if ( timeRange==null ) timeRange= new DatumRange( Units.us2000.parse( params2.get("start_time") ), Units.us2000.parse( params2.get("end_time") ) );

        logger.fine("  done. ");

        try {
            String prop= QDataSet.DEPEND_0;
            QDataSet dep= (QDataSet) result1.property( prop );
            if ( dep==null ) {
                prop= QDataSet.JOIN_0;
                Object o= result1.property( prop );
                if ( o instanceof QDataSet ) {
                    dep= (QDataSet) o;
                }
            }
            if ( dep!=null && dep.property( QDataSet.CACHE_TAG )== null ) {
                CacheTag ct;
                if ( SemanticOps.isBundle(result1) ) {
                    QDataSet bounds= SemanticOps.bounds(dep);
                    ct= new CacheTag( DataSetUtil.asDatumRange( bounds.slice(1), true ), resolution );
                } else {
                    QDataSet bounds= SemanticOps.bounds(result1);
                    ct= new CacheTag( DataSetUtil.asDatumRange( bounds.slice(0), true ), resolution );
                }
                MutablePropertyDataSet dep2= DataSetOps.makePropertiesMutable(dep);
                dep2.putProperty( QDataSet.CACHE_TAG, ct );
                MutablePropertyDataSet result2= DataSetOps.makePropertiesMutable(result1);
                result2.putProperty( prop, dep2 );
                return result2;
            }
        } catch ( IllegalArgumentException ex ) {
            ex.printStackTrace();
        }
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
                String stime= timeRange.min().toString().replace(" ", "+");
                String etime= timeRange.max().toString().replace(" ", "+");
                String sparams= "dataset="+params.get( "dataset" )
                        + "&start_time=" + stime
                        + "&end_time=" + etime;
                if ( resolution!=null ) {
                        sparams+= "&resolution=" + resolution.doubleValue(Units.seconds);
                } else {
                    logger.fine("no resolution specified");
                }
                if ( dsParams!=null )  sparams+= "&" + dsParams;
                return "vap+das2Server:" + resourceURI + "?" + sparams;
            }

            public DatumRange getTimeRange() {
                return timeRange;
            }

            public Datum getTimeResolution() {
                return resolution;
            }

            public void setURI(String suri) throws ParseException {
                URISplit split= URISplit.parse(uri);
                Map<String,String> params= URISplit.parseParams(split.params);
                String startTime= params.remove( "start_time" );
                String endTime= params.get( "end_time" );
                String sresolution= params.get("resolution");
                if ( startTime!=null && endTime!=null ) {
                    timeRange= new DatumRange( Units.us2000.parse(startTime), Units.us2000.parse(endTime) );
                }
                if ( sresolution!=null ) {
                    resolution= Units.seconds.parse(sresolution);
                }
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
