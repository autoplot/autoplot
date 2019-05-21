
package org.das2.datasource;

import java.io.IOException;
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
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.CancelledOperationException;
import org.das2.client.Authenticator;
import org.das2.client.DasServer;
import org.das2.client.Key;
import org.das2.datum.CacheTag;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.dataset.DataSetAdapter;
import org.das2.datum.DatumRangeUtil;
import org.das2.stream.MIME;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystem;
import org.das2.qds.BundleDataSet;
import org.das2.qds.BundleDataSet.BundleDescriptor;
import org.das2.qds.DataSetOps;
import org.das2.qds.DataSetUtil;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.AbstractDataSource;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.capability.Caching;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.das2.DasException;
import org.das2.client.AccessDeniedException;
import org.das2.client.DasServerException;
import org.das2.qds.ops.Ops;
import org.das2.qstream.QDataSetStreamHandler;
import org.das2.util.CredentialsManager;

/**
 * DataSource for communicating with Das2servers.
 *
 * @author jbf
 */
public final class Das2ServerDataSource extends AbstractDataSource {

    private static final Map<String, String> keys = new HashMap();

    private Exception offlineException= null;
    
    private Logger loggerUrl= org.das2.util.LoggerManager.getLogger( "das2.url" );
    
    public Das2ServerDataSource(URI uri) throws ParseException {
        super(uri);
        if (!"no".equals(params.get("tsb"))) {
            addCapability(TimeSeriesBrowse.class, getTimeSeriesBrowse());
        }
        addCapability( Caching.class, new Caching() {
            @Override
            public boolean satisfies(String surl) {
                return false;
            }
            @Override
            public void resetURI(String surl) {
            }
            @Override
            public void reset() {
                offlineException= null;
            }
        });
        
        HashMap<String, String> params2 = new HashMap(params);
        params2.put("server", "dataset");

        if (params.get("dataset") == null) {
            String dataset = params.remove("arg_0");
            if (dataset != null) {
                params.put("dataset", dataset);
                params2.put("dataset", dataset);
                params2.remove("arg_0");
            }
        }

        String str = params.get("timerange");
        if (str != null) {
            str = str.replaceAll("\\+", " ");
            try {
                DatumRange tr = DatumRangeUtil.parseTimeRange(str);
                params2.put("start_time", tr.min().toString());
                params2.put("end_time", tr.max().toString());
                params2.remove("timerange");
            } catch (ParseException ex) {
                logger.log(Level.WARNING, "unable to parse timerange {0}", str);
            }
        } else {

        }

        Map<String, String> otherParams = new LinkedHashMap(params);
        otherParams.remove("start_time");
        otherParams.remove("end_time");
        otherParams.remove("resolution");
        otherParams.remove("dataset");
        otherParams.remove("tsb");
        otherParams.remove("timerange");
        otherParams.remove("_res");      // =0.0 means use native resolution
        otherParams.remove("intrinsic"); // =true means use native resolution
        otherParams.remove("item");
        otherParams.remove("interval");
        otherParams.remove("key");

        dsParams = (String) URISplit.formatParams(otherParams);

        if (params2.get("start_time") != null && params2.get("end_time") != null) {
            timeRange = new DatumRange(Units.us2000.parse(params2.get("start_time")), Units.us2000.parse(params2.get("end_time")));
        }

        resolution = null;
        if (params2.get("resolution") != null) {
            resolution = Units.seconds.parse(params2.get("resolution"));
        }

    }

    private static final Logger logger = LoggerManager.getLogger("apdss.das2server");

    DatumRange timeRange;
    Datum resolution;
    String dsParams;
    List<String> tcaDesc;
    Map dsdfParams = null;

    /**
     * attempt to unbundle the name, return null if the data set is not found.
     * @param ds
     * @param item
     * @return 
     */
    private static QDataSet tryUnbundle( QDataSet ds, String item ) {
        try {
            QDataSet ds1= Ops.unbundle( ds, item );
            return ds1;
        } catch ( IllegalArgumentException ex ) {
            return null;
        }
    }
    
    @Override
    public synchronized QDataSet getDataSet(final ProgressMonitor mon) throws Exception {
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

        Map<String, String> params2 = new LinkedHashMap();
        Map<String, String> otherParams = new LinkedHashMap(params);
        otherParams.remove("start_time");
        otherParams.remove("end_time");
        otherParams.remove("resolution");
        otherParams.remove("dataset");
        otherParams.remove("tsb");
        otherParams.remove("timerange");
        otherParams.remove("_res");      // =0.0 means use native resolution
        otherParams.remove("intrinsic"); // =true means use native resolution
        otherParams.remove("useOldD2sParser");
        
        String item = (String) otherParams.remove("item");
        String interval = (String) otherParams.remove("interval");
        String key1 = (String) otherParams.remove("key");

        dsParams = (String) URISplit.formatParams(otherParams);

        params2.put("server", "dataset");
        if (timeRange != null) {
            params2.put("start_time", URLEncoder.encode(timeRange.min().toString(), "US-ASCII"));
            params2.put("end_time", URLEncoder.encode(timeRange.max().toString(), "US-ASCII"));
        } else {
            throw new IllegalArgumentException("timeRange is null");
        }

        // optional explicit resolution
        String sresolution = params.get("_res");
        if (sresolution != null) {
            params2.remove("_res");
        }
        if ("true".equals(params.get("intrinsic"))) {
            sresolution = "0";
            params2.remove("intrinsic");
        }

        if (sresolution != null) {
            if (sresolution.trim().length() == 0 || sresolution.equals("0")) {
                resolution = null;
            } else {
                resolution = Units.seconds.parse(sresolution);
            }
        }

        if (resolution != null) {
            params2.put("resolution", "" + resolution.doubleValue(Units.seconds));
        } else {
            logger.fine("resolution is not available, loading at intrinsic resolution");
        }
        String dataset = params.get("dataset");
        if (dataset == null) {
            dataset = params.get("arg_0");
        }

        if (dataset == null) {
            throw new IllegalArgumentException("dataset is not specified");
        }

        mon.setProgressMessage("request " + dataset);

        if (interval != null) { // TCAs use interval parameter
            logger.finer("dataset is a TCA, so do not use resolution");
            // this is dicey.  interval is now replaced with a value based on the
            // resolution.

            double dsec;
            if (resolution == null) {
                dsec = Double.parseDouble(interval);
            } else {
                dsec = resolution.doubleValue(Units.seconds);
            }

            int iinterval = (int) dsec;
            if (iinterval < 1) {
                iinterval = 1;
            }

            params2.put("interval", URLEncoder.encode(String.valueOf(iinterval), "US-ASCII"));
            params2.remove("resolution");
        } else {
            logger.finer("dataset is not a TCA, interval parameter is null");
        }

        params2.put("dataset", URLEncoder.encode(dataset, "US-ASCII"));
        if (dsParams.length() > 0) {
            if (dsParams.contains("+-") && !dsParams.startsWith("+")) {
                params2.put("params", dsParams); // somebody already encoded it.
            } else {
                params2.put("params", URLEncoder.encode(dsParams, "US-ASCII"));
            }
        }
        URL url2 = new URL("" + this.resourceURI + "?" + URISplit.formatParams(params2));

        //if ( interval!=null && tcaDesc==null ) {
        if (true) {
            if (dsdfParams == null) {
                String dsdfURL = this.resourceURI + "?server=dsdf&dataset=" + dataset;
                URL url3 = new URL(dsdfURL);
                logger.log(Level.FINE, "opening {0}", url3);
                  
                InputStream in = getInputStream(url3, dataset);

                ReadableByteChannel channel = Channels.newChannel(in);

                final Map map = new LinkedHashMap();

                
                DataSetStreamHandler handler = new DataSetStreamHandler(new HashMap(), mon) {
                    @Override
                    public void streamDescriptor(StreamDescriptor sd) throws StreamException {
                        super.streamDescriptor(sd);
                        map.putAll(sd.getProperties());
                    }
                };
                try {
                    StreamTool.readStream(channel, handler);
                } catch (StreamException ex) {
                    if (ex.getMessage().equals("noSuchDataSet")) {
                        throw new StreamException("noSuchDataSet: " + dataset);
                    } else {
                        throw new StreamException(ex.getMessage() + "\ndsdf request was\n" + url3);
                    }
                } finally {
                    channel.close();
                }

                dsdfParams = map;
            }

            tcaDesc = new ArrayList<>();

            int iplane = 0;
            String label = (String) dsdfParams.get("label");
            while (label != null) {
                tcaDesc.add(label);
                iplane++;
                label = (String) dsdfParams.get("plane_" + iplane + ".label");
            }

            String groupAccess = (String) dsdfParams.get("groupAccess");
            if (groupAccess != null && groupAccess.trim().length() > 0) {
                if (key1 == null) {
                    //keys.clear();
                    String k = this.resourceURI.toString() + "?" + params.get("dataset");
                    String t = keys.get(k);
                    //String t= null; // TODO: See if we can keep track of keys for jython scripts.  See sftp://jbf@klunk/home/jbf/project/cassini/production/devel/autoplot/jyds/cassini.jyds?timerange=2014-12-10+21:18+to+23:29&bg=F
                    if (t == null) {
                        Authenticator authenticator;
                        authenticator = new Authenticator(DasServer.create(this.resourceURI.toURL()), groupAccess);
                        Key key2 = authenticator.authenticate();
                        if (key2 != null) {
                            params2.put("key", key2.toString());
                            url2 = new URL("" + this.resourceURI + "?" + URISplit.formatParams(params2));
                            keys.put(k, key2.toString());
                        }
                    } else {
                        params2.put("key", t);
                        url2 = new URL("" + this.resourceURI + "?" + URISplit.formatParams(params2));
                    }
                } else {
                    params2.put("key", key1);
                    url2 = new URL("" + this.resourceURI + "?" + URISplit.formatParams(params2));
                }
            }

        }

        boolean qds = "1".equals(dsdfParams.get("qstream"));

        logger.log(Level.FINE, "opening {0} {1}", new Object[]{qds ? "as qstream" : "as das2stream", url2});

        // Allow response bodies that are Das2 streams or QStreams to be processed
        // normally even when the HTTP Status code indicates an error.  This is to handle
        // errors that are packaged properly.
        InputStream in;
        try {
            if ( offlineException!=null  ) {
                throw offlineException;
            }
            in = getInputStream(url2, dataset);
        } catch ( AccessDeniedException ex ) {
            offlineException= ex;
            throw offlineException;
        }

        final DasProgressMonitorInputStream mpin = new DasProgressMonitorInputStream(in, mon);

        ReadableByteChannel channel = Channels.newChannel(mpin);

        QDataSet result1;

        mon.setProgressMessage("reading " + dataset);

        String techContact = (String) dsdfParams.get("techContact");
        if (techContact == null) {
            techContact = "";
        } else {
            techContact = "\nTechnical Contact: " + techContact;
        }

        boolean useOldDas2SteamParser= getParam( "useOldD2sParser", "F" ).equals("T");
        
        if (qds) {

            try {
                org.das2.qstream.QDataSetStreamHandler eh = new org.das2.qstream.QDataSetStreamHandler();
                org.das2.qstream.StreamTool.readStream(channel, eh);

                result1 = eh.getDataSet();

                // check if we can flatten rank 2 join that comes from aggregation
                if (QDataSetStreamHandler.isFlattenableJoin(result1)) {
                    result1 = eh.flattenJoin(result1);
                }

            } catch (org.das2.qstream.StreamException ex) {
                Throwable cause = ex.getCause();
                if (!mon.isFinished()) {
                    mon.finished(); // the stream reader probably called it already.
                }
                if (cause != null && (cause instanceof java.io.InterruptedIOException)) {
                    logger.log(Level.WARNING, ex.getMessage(), ex);
                    //TODO CancelledOperationException
                    throw (java.io.InterruptedIOException) ex.getCause();
                } else if (cause != null && (cause instanceof org.das2.dataset.NoDataInIntervalException)) {
                    throw (org.das2.dataset.NoDataInIntervalException) ex.getCause();
                } else if (ex.getMessage().contains("Empty response from reader")) {
                    throw new org.das2.dataset.NoDataInIntervalException(ex.getMessage() + techContact);
                } else if (ex.getMessage().contains("No data found")) {
                    throw new org.das2.dataset.NoDataInIntervalException(ex.getMessage());
                } else {
                    throw new StreamException(ex.getMessage() + "\ndataset request was\n" + url2 + techContact);
                }
            }
        } else if ( useOldDas2SteamParser ) {
            DataSetStreamHandler handler = new DataSetStreamHandler(new HashMap(), mon) {
                @Override
                public void streamDescriptor(StreamDescriptor sd) throws StreamException {
                    super.streamDescriptor(sd);
                    if (mon.getTaskSize() != -1) { // progress messages are on the stream.
                        mpin.setEnableProgressPosition(false);
                    }
                }
            };

            try {
                StreamTool.readStream(channel, handler);
            } catch (StreamException ex) {
                mon.finished();
                Throwable cause = ex.getCause();
                if (ex.getCause() != null && (ex.getCause() instanceof java.io.InterruptedIOException)) {
                    logger.log(Level.INFO, ex.getMessage(), ex);
                    if (ex.getMessage().contains("Operation cancelled")) { // TODO: nasty getMessage...
                        throw new CancelledOperationException(techContact);
                    } else {
                        throw (java.io.InterruptedIOException) ex.getCause();
                    }
                } else if (cause != null && (cause instanceof org.das2.dataset.NoDataInIntervalException)) {
                    throw (org.das2.dataset.NoDataInIntervalException) ex.getCause();
                } else if (ex.getMessage().contains("Empty response from reader")) {
                    throw new org.das2.dataset.NoDataInIntervalException(ex.getMessage() + " " + techContact);
                } else if (ex.getMessage().contains("No data found")) {
                    throw new org.das2.dataset.NoDataInIntervalException(ex.getMessage());
                } else {
                    ex = new StreamException(ex.getMessage() + "\ndataset request was \n" + url2 + " " + techContact);
                    logger.log(Level.INFO, ex.getMessage(), ex);
                    throw ex;
                }
            }
            
            DataSet ds = handler.getDataSet();

            if (ds == null) {
                return null;
            }

            if (ds.getXLength() == 0) {
                throw new RuntimeException("empty dataset returned");
            }

            MutablePropertyDataSet result;
            if (item == null || item.equals("")) {
                result = DataSetAdapter.create(ds); //TODO: danger if it has TCA planes, it will return bundle.  Probably not what we want.
            } else {
                DataSet das2ds;
                das2ds = ds.getPlanarView(item);
                //TODO: there's a bug where item=x shows where the 0th item label is always used.
                if (das2ds == null) {
                    if (item.contains(",")) {
                        BundleDataSet bds = null;
                        String[] ss = item.split(",");
                        for (String s : ss) {
                            das2ds = ds.getPlanarView(s);
                            if (das2ds == null) {
                                int iitem = Integer.parseInt(s);
                                if (iitem == 0) {
                                    das2ds = ds.getPlanarView("");
                                } else {
                                    das2ds = ds.getPlanarView("plane_" + iitem);
                                }
                            }
                            QDataSet bds1 = DataSetAdapter.create(das2ds);
                            bds = (BundleDataSet) Ops.bundle(bds, bds1);
                        }
                        result = bds;
                    } else {
                        try {
                            int iitem = Integer.parseInt(item);
                            if (iitem == 0) {
                                das2ds = ds.getPlanarView("");
                            } else {
                                das2ds = ds.getPlanarView("plane_" + iitem);
                            }
                            if (das2ds == null) {
                                String[] ss = ds.getPlaneIds();
                                das2ds = ds.getPlanarView(ss[iitem]);
                            }
                            if (das2ds == null) {
                                throw new IllegalArgumentException("no such plane, looking for " + item);
                            }
                            result = DataSetAdapter.create(das2ds); // fragile                
                        } catch (NumberFormatException ex) {
                            throw new IllegalArgumentException("unable to find component \"" + item + "\"");
                        }
                    }
                } else {
                    result = DataSetAdapter.create(das2ds); // fragile
                }
            }
            
            if (tcaDesc != null && tcaDesc.size() > 0) {
                MutablePropertyDataSet mpds;
                mpds= Ops.maybeCopy(result);
                if (item == null || item.equals("") || item.equals("0")) {
                    QDataSet bds = (QDataSet) result.property(QDataSet.BUNDLE_1);
                    if (bds != null && bds instanceof BundleDescriptor) {
                        BundleDescriptor bds1 = (BundleDescriptor) bds;
                        for (int i = 0; i < bds1.length(); i++) {
                            bds1.putProperty(QDataSet.LABEL, i, tcaDesc.get(i));
                        }
                    } else {
                        mpds.putProperty(QDataSet.LABEL, tcaDesc.get(0));
                    }
                } else {
                    if (!item.contains(",")) {
                        mpds.putProperty(QDataSet.LABEL, tcaDesc.get(Integer.parseInt(item)));
                    }
                }
                result= mpds;
            }            
            
            result1= result;
            
        } else {
            
            org.das2.client.QDataSetStreamHandler handler= new org.das2.client.QDataSetStreamHandler() {
                @Override
                public void streamDescriptor(StreamDescriptor sd) throws StreamException {
                    super.streamDescriptor(sd);
                    if (mon.getTaskSize() != -1) { // progress messages are on the stream.
                        mpin.setEnableProgressPosition(false);
                    }
                }
            };
            handler.setMonitor(mon);

            try {
                StreamTool.readStream(channel, handler);
            } catch (StreamException ex) {
                mon.finished();
                Throwable cause = ex.getCause();
                if (ex.getCause() != null && (ex.getCause() instanceof java.io.InterruptedIOException)) {
                    logger.log(Level.INFO, ex.getMessage(), ex);
                    if (ex.getMessage().contains("Operation cancelled")) { // TODO: nasty getMessage...
                        throw new CancelledOperationException(techContact);
                    } else {
                        throw (java.io.InterruptedIOException) ex.getCause();
                    }
                } else if (cause != null && (cause instanceof org.das2.dataset.NoDataInIntervalException)) {
                    throw (org.das2.dataset.NoDataInIntervalException) ex.getCause();
                } else if (ex.getMessage().contains("Empty response from reader")) {
                    throw new org.das2.dataset.NoDataInIntervalException(ex.getMessage() + " " + techContact);
                } else if (ex.getMessage().contains("No data found")) {
                    throw new org.das2.dataset.NoDataInIntervalException(ex.getMessage());
                } else {
                    ex = new StreamException(ex.getMessage() + "\ndataset request was \n" + url2 + " " + techContact);
                    logger.log(Level.INFO, ex.getMessage(), ex);
                    throw ex;
                }
            }

            if (!mon.isFinished()) {
                mon.finished(); // I don't believe the das2stream reader calls finished.
            }
            
            QDataSet ds = handler.getDataSet();

            if (ds == null) {
                return null;
            }

            if (ds.length() == 0) {
                throw new RuntimeException("empty dataset returned");
            }

            QDataSet result;
            if (item == null || item.equals("")) {
                result = ds; 
            } else {
                QDataSet das2ds;
                das2ds = tryUnbundle(ds,item);
                //TODO: there's a bug where item=x shows where the 0th item label is always used.
                if (das2ds == null) {
                    if (item.contains(",")) {
                        BundleDataSet bds = null;
                        String[] ss = item.split(",");
                        for (String s : ss) {
                            das2ds = tryUnbundle(ds,s);
                            if (das2ds == null) {
                                int iitem = Integer.parseInt(s);
                                if (iitem == 0) {
                                    das2ds = tryUnbundle( ds,"");//TODO: check on this
                                } else {
                                    das2ds = tryUnbundle(ds, "plane_" + iitem );
                                }
                            }
                            QDataSet bds1 = das2ds;
                            bds = (BundleDataSet) Ops.bundle(bds, bds1);
                        }
                        result = bds;
                    } else {
                        try {
                            int iitem = Integer.parseInt(item);
                            if (iitem == 0) {
                                das2ds = tryUnbundle( ds, "" ); //TODO: check on this
                            } else {
                                das2ds = tryUnbundle( ds, "plane_" + iitem);
                            }
                            if (das2ds == null) {
                                das2ds = Ops.unbundle( ds, iitem );
                            }
                            if (das2ds == null) {
                                throw new IllegalArgumentException("no such plane, looking for " + item);
                            }
                            result = das2ds;
                        } catch (NumberFormatException ex) {
                            throw new IllegalArgumentException("unable to find component \"" + item + "\"");
                        }
                    }
                } else {
                    result = das2ds;
                }
            }

            if (tcaDesc != null && tcaDesc.size() > 0) {
                MutablePropertyDataSet mpds;
                mpds= Ops.maybeCopy(result);
                if (item == null || item.equals("") || item.equals("0")) {
                    QDataSet bds = (QDataSet) result.property(QDataSet.BUNDLE_1);
                    if (bds != null && bds instanceof BundleDescriptor) {
                        BundleDescriptor bds1 = (BundleDescriptor) bds;
                        for (int i = 0; i < bds1.length(); i++) {
                            bds1.putProperty(QDataSet.LABEL, i, tcaDesc.get(i));
                        }
                    } else {
                        mpds.putProperty(QDataSet.LABEL, tcaDesc.get(0));
                    }
                } else {
                    if (!item.contains(",")) {
                        mpds.putProperty(QDataSet.LABEL, tcaDesc.get(Integer.parseInt(item)));
                    }
                }
                result= mpds;
            }
            result1 = result;

        }

        logger.fine("  done. ");

        try {
            String prop = QDataSet.DEPEND_0;
            QDataSet dep = (QDataSet) result1.property(prop);
            if (dep == null) {
                prop = QDataSet.JOIN_0;
                Object o = result1.property(prop);
                if (o instanceof QDataSet) {
                    dep = (QDataSet) o;
                }
            }
            if (dep != null && dep.property(QDataSet.CACHE_TAG) == null) {
                CacheTag ct;
                if (SemanticOps.isBundle(result1)) {
                    QDataSet bounds = SemanticOps.bounds(dep);
                    ct = new CacheTag(DataSetUtil.asDatumRange(bounds.slice(1), true), resolution);
                } else {
                    QDataSet bounds = SemanticOps.bounds(result1);
                    ct = new CacheTag(DataSetUtil.asDatumRange(bounds.slice(0), true), resolution);
                }
                MutablePropertyDataSet dep2 = DataSetOps.makePropertiesMutable(dep);
                dep2.putProperty(QDataSet.CACHE_TAG, ct);
                MutablePropertyDataSet result2 = DataSetOps.makePropertiesMutable(result1);
                result2.putProperty(prop, dep2);
                return result2;
            }
        } catch (IllegalArgumentException ex) {
            logger.log(Level.WARNING, ex.getMessage(), ex);
        }

        if (!mon.isFinished()) {
            mon.finished();
        }
        return result1;

    }
    
    /**
     * Get an input stream or don't.  Handles all the HTTP stuff stuch as 
     * redirection and authentication if this is an http URL.  Would be okay
     * to put special handling for other urls as well such as sftp.
     * @param url
     * @param sDataSetId used if the credentials dialog is needed.
     * @return
     * @throws IOException
     * @throws DasException 
     */
    private InputStream getInputStream(URL url, String sDataSetId) 
		 throws IOException, DasException{
		InputStream in = null;
		
		int nRedirects = 0;
		String sLocId = null;
		String sBasicHash = null;
		CredentialsManager cm = CredentialsManager.getMannager();
		
		while(true){
            loggerUrl.log(Level.FINE, "open {0}", url);
			URLConnection conn = url.openConnection();
			
			if(sBasicHash != null)
				conn.setRequestProperty("Authorization", "Basic " + sBasicHash);
			
			conn.setConnectTimeout(FileSystem.settings().getConnectTimeoutMs());
			conn.setReadTimeout(FileSystem.settings().getReadTimeoutMs());
			
			if(! (conn instanceof HttpURLConnection) ){
				in = conn.getInputStream();
				break;
			}
			HttpURLConnection httpConn = (HttpURLConnection) conn;
			int nStatus = httpConn.getResponseCode();
			
			if((nStatus / 100) == 2){  // Some version of okay
				in = conn.getInputStream();
				break;
			}
			
			if((nStatus / 100) == 3){  // Redirected
				String sLoc = httpConn.getHeaderField("Location");
				if(sLoc == null)
					throw new DasServerException("Redirection response missing location header");
				
				if(nRedirects > 20)
					throw new DasServerException("Client has been redirected more than 20 times");
				
				// Update the URL and continue
				url = new URL(sLoc);
				++nRedirects;
				continue;
			}
			
			if(nStatus / 100 == 5 ){  // Some kind of Server Error
				// You're not going to go to space today but maybe the message is a valid 
				// das2 error stream
				String sMime = httpConn.getContentType();

				// if this isn't a Das2 stream or QStream, go ahead and throw
				if(!MIME.isDataStream(sMime)){
					throw new DasServerException(String.format(
						"Server error encountered accessing URL %s", url.toString()
					));
				}
				else{
					in = httpConn.getErrorStream();
				}
				break;
			}
			
			if(nStatus == 401){
				// If hash was sent before, it obviously didn't work so invalidate the entry
				if(sLocId != null) cm.invalidate(sLocId);
				
				//URL keyChainUrl= new URL( url2.getProtocol() + 
				//     "://user@" + url2.getHost() + url2.getPath() + 
				//     "/" + params2.get("dataset" ) );
				//String userInfo= KeyChain.getDefault().getUserInfo(keyChainUrl);
				
				String readAccessGroup;
				String auth = httpConn.getHeaderField("WWW-Authenticate");
				Pattern p = Pattern.compile("Basic realm=\"(.*)\"");
				Matcher m = p.matcher(auth);
				if(m.matches())
					readAccessGroup = m.group(1);
				else
					readAccessGroup = "das2 server";

				sLocId = this.resourceURI.toURL().toString() + "|" + readAccessGroup;
				//String sLocId = "planet.physics.uiowa.edu/das/das2Server|voyager1/pwi/SpecAnalyzer-4s-Efield";
				
				if(!cm.hasCredentials(sLocId)){
					DasServer svr = DasServer.create(url);
					String sDesc = String.format(
						"<html><h3>%s</h3><hr>Server: <b>%s</b><br>Data Set: <b>%s</b>",
						svr.getName(), svr.getHost(), sDataSetId
					);
					cm.setDescription(sLocId, sDesc, svr.getLogo());
				}
				sBasicHash = cm.getHttpBasicHash(sLocId);
				if(sBasicHash == null){
					throw new AccessDeniedException(
						"User credentials are not available for URL: " + url
					);
				}
				
				//String sHashRaw=  cm.getHttpBasicHashRaw(sLocId);
				//KeyChain.getDefault().setUserInfo( keyChainUrl, sHashRaw );
				continue;
			}
			
			if((nStatus == 400)||(nStatus == 404))
				throw new DasServerException("Query error in request for URL: " + url);
			
			if(nStatus == 403)
				throw new AccessDeniedException("Access denied for URL:" + url);
			
			
			// Some other response, call it a server error
			throw new DasServerException(String.format(
				"Completly unexpected server error encountered accessing URL %s, status "+
				"code %d received from server.", url.toString(), nStatus
			));
		}
		
		return in;
	}
 
    public final TimeSeriesBrowse getTimeSeriesBrowse() {
        return new TimeSeriesBrowse() {
            @Override
            public void setTimeRange(DatumRange dr) {
                logger.log(Level.FINE, "setTimeRange to {0} ({1})", new Object[] { dr, dr.width().toString() } );
                timeRange = dr;
            }

            @Override
            public void setTimeResolution(Datum d) {
                logger.log(Level.FINE, "setTimeResolution to {0}", d);
                resolution = d;
            }

            @Override
            public String getURI() {
                Map<String, String> c = new LinkedHashMap(params);
                String stime = timeRange.min().toString().replace(" ", "+");
                String etime = timeRange.max().toString().replace(" ", "+");
                c.put("start_time", stime);
                c.put("end_time", etime);
                if (resolution != null) {
                    double resSec = resolution.doubleValue(Units.seconds);
                    resSec = Math.round(resSec * 1000) / 1000.;
                    c.put("resolution", String.valueOf(resSec));
                } else {
                    logger.fine("no resolution specified");
                }
                if (params.containsKey("interval")) {
                    c.put("interval", params.get("interval"));
                }
                String sparams = URISplit.formatParams(c);
                //if ( dsParams!=null && dsParams.trim().length()>0 )  sparams+= "&" + dsParams; //TODO: Double-load was caused by extra & at the end.  It's silly to have it so sensitive.
                return "vap+das2server:" + resourceURI + "?" + sparams;
            }

            @Override
            public String blurURI() {
                String sparams = "dataset=" + params.get("dataset");
                if (params.containsKey("interval")) { // this is important, because TSB will not update this.
                    sparams += "&interval=" + params.get("interval");
                }
                if (dsParams != null && dsParams.trim().length() > 0) {
                    sparams += "&" + dsParams;
                }

                return "vap+das2server:" + resourceURI + "?" + sparams;
            }

            @Override
            public DatumRange getTimeRange() {
                return timeRange;
            }

            @Override
            public Datum getTimeResolution() {
                return resolution;
            }

            @Override
            public void setURI(String suri) throws ParseException {
                URISplit split = URISplit.parse(uri);
                Map<String, String> params = URISplit.parseParams(split.params);
                String startTime = params.remove("start_time");
                String endTime = params.get("end_time");
                String sresolution = params.get("resolution");
                if (startTime != null && endTime != null) {
                    timeRange = new DatumRange(Units.us2000.parse(startTime), Units.us2000.parse(endTime));
                }
                if (sresolution != null) {
                    resolution = Units.seconds.parse(sresolution);
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
        return this.resourceURI + "?" + params.get("dataset");
    }

}
