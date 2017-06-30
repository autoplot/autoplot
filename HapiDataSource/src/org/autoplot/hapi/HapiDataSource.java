
package org.autoplot.hapi;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.ParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import org.das2.qds.buffer.BufferDataSet;
import org.das2.dataset.NoDataInIntervalException;
import org.das2.datum.CacheTag;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystemUtil;
import org.das2.util.filesystem.HtmlUtil;
import org.das2.util.filesystem.HttpUtil;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.das2.qds.ArrayDataSet;
import org.das2.qds.DDataSet;
import org.das2.qds.DataSetUtil;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.das2.qds.SparseDataSetBuilder;
import org.das2.qds.WritableDataSet;
import org.autoplot.datasource.AbstractDataSource;
import org.autoplot.datasource.DefaultTimeSeriesBrowse;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.das2.datum.TimeUtil;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.DataSetBuilder;
import org.das2.qstream.TransferType;

/**
 * HAPI data source uses transactions with HAPI servers to collect data.
 * @author jbf
 */
public class HapiDataSource extends AbstractDataSource {

    protected final static Logger logger= LoggerManager.getLogger("apdss.hapi");
    
    TimeSeriesBrowse tsb;
    
    public HapiDataSource(URI uri) {
        super(uri);
        tsb= new DefaultTimeSeriesBrowse();
        String str= params.get( URISplit.PARAM_TIME_RANGE );
        if ( str!=null ) {
            try {
                tsb.setURI(uri.toString());
            } catch (ParseException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
        addCapability( TimeSeriesBrowse.class, tsb );
    }

    private static QDataSet getJSONBins( JSONObject binsObject ) throws JSONException {
        JSONArray bins=null;
        if ( binsObject.has("values") ) {
            logger.fine("using deprecated bins");
            bins= binsObject.getJSONArray("values");
        } else if ( binsObject.has("centers") ) {
            bins= binsObject.getJSONArray("centers");
        }
        
        JSONArray ranges= null;
        if ( binsObject.has("ranges") ) {
            ranges= binsObject.getJSONArray("ranges");
        }
        
        int len;
        if ( ranges==null && bins==null ) {
            throw new IllegalArgumentException("ranges or centers must be specified");
        } else {
            len= ranges==null ? bins.length() : ranges.length();
        }
        
        DDataSet result= DDataSet.createRank1(len);
        DDataSet max= DDataSet.createRank1(len);
        DDataSet min= DDataSet.createRank1(len);
        boolean hasMin= false;
        boolean hasMax= false;
        boolean hasCenter= false;
        if ( len==0 ) {
            throw new IllegalArgumentException("bins must have ranges or centers specified");
        } else {
            if ( bins!=null ) {
                hasCenter= true;
                Object o= bins.get(0);
                if ( o instanceof Number ) {
                    for ( int j=0; j<len; j++ ) {
                        result.putValue( j, bins.getDouble(j) );
                    }
                } else if ( o instanceof JSONObject ) {
                    for ( int j=0; j<len; j++ ) {       
                        JSONObject jo= bins.getJSONObject(j);
                        result.putValue(j,jo.getDouble("center"));
                        if ( hasMin || jo.has("min") ) {
                            hasMin= true;
                            min.putValue(j,jo.getDouble("min"));
                        }
                        if ( hasMax || jo.has("max") ) {
                            hasMax= true;
                            max.putValue(j,jo.getDouble("max"));
                        }
                    }
                }
            }
            if ( ranges!=null ) {
                for ( int j=0; j<len; j++ ) {  
                    JSONArray ja1= ranges.getJSONArray(j);
                    hasMax= true;
                    hasMin= true;
                    min.putValue(j,ja1.getDouble(0));
                    max.putValue(j,ja1.getDouble(1));
                }
            }
        }

        String sunits= binsObject.getString("units");
        if ( sunits!=null ) {
            Units u= Units.lookupUnits(sunits);
            result.putProperty( QDataSet.UNITS, u );
            if ( hasMin && hasMax ) {
                min.putProperty( QDataSet.UNITS, u );
                max.putProperty( QDataSet.UNITS, u );
            }
        }
        
        if ( hasCenter ) {
            if ( hasMin && hasMax ) {
                result.putProperty( QDataSet.BIN_MIN, min );
                result.putProperty( QDataSet.BIN_MAX, max );
            } else if ( hasMin || hasMax ) {
                logger.warning("need both min and max for bins.");
            }
        } else {
            result= (DDataSet)ArrayDataSet.copy( double.class, Ops.bundle( min, max ) );
            result.putProperty( QDataSet.BINS_1, QDataSet.VALUE_BINS_MIN_MAX );
        }
        
        if ( binsObject.has("name") ) {
            result.putProperty( QDataSet.NAME, binsObject.getString("name") );
        }
        
        if ( binsObject.has("description") ) {
            result.putProperty( QDataSet.TITLE, binsObject.getString("description") );
        }
        
        return result;
    }
    
    public static final double FILL_VALUE= -1e38;
    
    private JSONObject getInfo( ) throws MalformedURLException, IOException, JSONException {
        URI server = this.resourceURI;
        String id= getParam("id","" );
        if ( id.equals("") ) throw new IllegalArgumentException("missing id");
        id= URLDecoder.decode(id,"UTF-8");
        URL url= HapiServer.getInfoURL(server.toURL(), id);
        StringBuilder builder= new StringBuilder();
        logger.log(Level.FINE, "getDocument {0}", url.toString());
        try ( BufferedReader in= new BufferedReader( new InputStreamReader( url.openStream() ) ) ) {
            String line= in.readLine();
            while ( line!=null ) {                
                builder.append(line);
                line= in.readLine();
            }
        }
        String s= builder.toString();
        if ( s.length()==0 ) {
            throw new JSONException("JSON response from info request is empty: "+url);
        }
        JSONObject o= new JSONObject(s);
        return o;
    }
    
    private static class ParamDescription {
        boolean hasFill= false;
        double fillValue= -1e38;
        Units units= Units.dimensionless;
        String name= "";
        String description= "";
        String type= "";
        int[] size= new int[0]; // array of scalars
        int length= 0; // length in bytes when transferring with binary.
        QDataSet[] depend= null;
        String[] dependName= null; // for time-varying depend1 (not in HAPI1.1)
        String renderType=null; // may contain hint for renderer, such as nnspectrogram
        private ParamDescription( String name ) {
            this.name= name;
        }
        @Override
        public String toString() {
            return this.name;
        }
    }
    
    /**
     * To assist in getting the CDAWeb HAPI server going, handle a few differences
     * like:<ul>
     * <li>parameters are returned within the JSON code.
     * <li>Epoch is needed.
     * </ul>
     * @param monitor
     * @return
     * @throws Exception 
     */
    private QDataSet getDataSetCDAWeb( ProgressMonitor monitor) throws Exception {
        URI server = this.resourceURI;
        String id= getParam("id","" );
        if ( id.equals("") ) throw new IllegalArgumentException("missing id");
        id= URLDecoder.decode( id,"UTF-8" );

        String pp= getParam("parameters","");
        if ( !pp.equals("") && !pp.startsWith("Epoch,") ) {
            pp= "Epoch,"+pp;
        }
        
        if ( id.equals("") ) throw new IllegalArgumentException("missing id");
        id= URLDecoder.decode(id,"UTF-8");
        
        DatumRange tr; // TSB = DatumRangeUtil.parseTimeRange(timeRange);
        tr= tsb.getTimeRange();
        
        URL url= HapiServer.getDataURL( server.toURL(), id, tr, pp );
        url= new URL( url.toString()+"&include=header&format=json1" );
        
        monitor.started();
        monitor.setProgressMessage("server is preparing data");
         
        long t0= System.currentTimeMillis() - 100; // -100 so it updates after receiving first record.
       
        int lineNum=0;
        
        StringBuilder builder= new StringBuilder();
        logger.log(Level.FINE, "getDocument {0}", url.toString());
        HttpURLConnection httpConnect=  ((HttpURLConnection)url.openConnection());
        httpConnect= (HttpURLConnection) HtmlUtil.checkRedirect( httpConnect );
        try ( BufferedReader in= new BufferedReader( new InputStreamReader( httpConnect.getInputStream() ) ) ) {
            String line= in.readLine();
            lineNum++;
            while ( line!=null ) {
                if ( System.currentTimeMillis()-t0 > 100 ) {
                    monitor.setProgressMessage("reading line "+lineNum);
                    t0= System.currentTimeMillis();
                }
                builder.append(line);
                line= in.readLine();
            }
        } catch ( IOException ex ) {
            ByteArrayOutputStream baos= new ByteArrayOutputStream();
            FileSystemUtil.copyStream( httpConnect.getErrorStream(), baos, new NullProgressMonitor() );
            String s= baos.toString("UTF-8");
            if ( s.contains("No data available") ) {
                logger.log(Level.FINE, "No data available, server responded with {0}: {1}", new Object[]{httpConnect.getResponseCode(), httpConnect.getResponseMessage()});
                throw new NoDataInIntervalException("No data available");
            } else {
                if ( s.length()<256 ) {
                    throw new IOException( ex.getMessage() + ": "+s );
                } else {
                    throw ex;
                }
            }
        }
        
        httpConnect.disconnect();
        
        JSONObject o= new JSONObject(builder.toString());
        
        JSONObject doc= o;
        
        ParamDescription[] pds= getParameterDescriptions(doc);
        
        monitor.setProgressMessage("parsing data");
        
        int[] nfields= new int[pds.length];
        for ( int i=0; i<pds.length; i++ ) {
            if ( pds[i].size.length==0 || ( pds[i].size.length==1 && pds[i].size[0]==1 ) ) {
                nfields[i]= 1;
            } else {
                nfields[i]= DataSetUtil.product(pds[i].size);
            }
        }
        
        boolean[] timeVary= new boolean[pds.length];
        
        //https://cdaweb.gsfc.nasa.gov/registry/hdp/hapi/data.xql?id=spase%3A%2F%2FVSPO%2FNumericalData%2FRBSP%2FB%2FEMFISIS%2FGEI%2FPT0.015625S&time.min=2012-10-09T00%3A00%3A00Z&time.max=2012-10-09T00%3A10%3A00Z&parameters=Magnitude
        QDataSet result= null;
        int ipd=0;
        for ( ParamDescription pd: pds ) {       
            JSONArray param;
            try {
                param= doc.getJSONArray(pd.name);
            } catch ( JSONException ex ) {
                timeVary[ipd]= false;
                continue; // DEPEND_1, etc...
            }
            timeVary[ipd]= true;
            Units u= pd.units;
            if ( nfields[ipd]>1 ) {
                int nf= nfields[ipd];
                DDataSet column= DDataSet.createRank2(param.length(),nfields[ipd]);
                for ( int i=0; i<param.length(); i++ ) {
                    JSONObject jo= param.getJSONObject(i);
                    JSONArray joa= jo.getJSONArray("elements");
                    for ( int j=0; j<nf; j++ ) {
                        column.putValue( i, j, u.parse( joa.getString(j) ).doubleValue(u) );
                    }
                }
                if ( pd.hasFill ) column.putProperty( QDataSet.FILL_VALUE, pd.fillValue );
                column.putProperty( QDataSet.TITLE, pd.description );
                column.putProperty( QDataSet.UNITS, pd.units );
                for ( int j=0; j<nf; j++ ) {
                    result= Ops.bundle( result, Ops.slice1(column,j) );
                }
            } else {
                DDataSet column= DDataSet.createRank1(param.length());
                for ( int i=0; i<param.length(); i++ ) {
                    column.putValue( i, u.parse(param.getString(i) ).doubleValue(u) );
                }
                if ( pd.hasFill ) column.putProperty( QDataSet.FILL_VALUE, pd.fillValue );
                column.putProperty( QDataSet.TITLE, pd.description );
                column.putProperty( QDataSet.UNITS, pd.units );
                result= Ops.bundle( result, column );
            }
            ipd++;
        }
        
        monitor.finished();
        
        int ntimeVary= 0;
        for ( boolean b: timeVary ){
            if ( b ) ntimeVary++;
        }
        ParamDescription[] newPds= new ParamDescription[ntimeVary];
        int k=0;
        for ( int j=0; j<pds.length; j++ ) {
            if ( timeVary[j] ) newPds[k++]= pds[j];
        }
        
        // the data are sometimes delivered backwards 
        int[] sort= null;
//        if ( UnitsUtil.isTimeLocation( newPds[ntimeVary-1].units ) ) {
//            sort= new int[result.length()];
//            for ( int j=0; j<ntimeVary; j++ ) {
//                sort[j]= ntimeVary-1-j;
//            }
//        }
        
        result = repackage(result,newPds,sort);
        
        return result;
    }
    
    @Override
    public QDataSet getDataSet(ProgressMonitor monitor) throws Exception {
        URI server = this.resourceURI;
        
        String format= getParam("format","csv");
        
        {
            String serverStr= server.toString();
            if ( format.equals("json1") ||
                    ( serverStr.startsWith("http://cdaweb") && serverStr.endsWith( "gsfc.nasa.gov/registry/hdp/hapi") ) )  {
                return getDataSetCDAWeb(monitor);
            }
        }
        
        monitor.setTaskSize(100);
        monitor.started();
        
        monitor.setProgressMessage("reading info");
        
        String id= getParam("id","" );  // the name of the set of identifiers.
        if ( id.equals("") ) throw new IllegalArgumentException("missing id");
        id= URLDecoder.decode( id,"UTF-8" );

        String pp= getParam("parameters","");
        
        JSONObject info= getInfo();
        monitor.setProgressMessage("got info");
        monitor.setTaskProgress(20);
        
        ParamDescription[] pds= getParameterDescriptions(info);
        
        DatumRange tr; // TSB = DatumRangeUtil.parseTimeRange(timeRange);
        tr= tsb.getTimeRange();
        
        if ( info.has("cadence") ) {
            try {
                int[] ii= DatumRangeUtil.parseISO8601Duration(info.getString("cadence"));
                Datum t= TimeUtil.toDatumDuration(ii);
                tr= new DatumRange( tr.min().subtract(t), tr.max().add(t) );
            } catch ( ParseException ex ) {
                logger.log(Level.WARNING, "unable to parse cadence as ISO8601 duration: {0}", info.getString("cadence"));
            }
        }
        
        JSONArray parametersArray= info.getJSONArray("parameters");
        int nparam= parametersArray.length(); // this is the actual number sent.
        if ( pp.length()>0 ) {
            String[] pps= pp.split(",");
            Map<String,Integer> map= new HashMap();
            for ( int i=0; i<nparam; i++ ) {
                map.put( parametersArray.getJSONObject(i).getString("name"), i ); // really--should name/id are two names for the same thing...
            }
            if ( !pps[0].equals(parametersArray.getJSONObject(0).getString("name")) ) { // add Time if it wasn't specified.
                throw new IllegalArgumentException("first parameter must be \"" + parametersArray.getJSONObject(0).getString("name") + "\"" );
            }
            nparam= pps.length;
            ParamDescription[] subsetPds= new ParamDescription[pps.length];
            for ( int ip=0; ip<pps.length; ip++ ) {
                int i= map.get(pps[ip]);
                subsetPds[ip]= pds[i];
            }
            //TODO: the parameters must also be sorted by position in stream.
            pds= subsetPds;   
        }
        
        URL url= HapiServer.getDataURL( server.toURL(), id, tr, pp );
        
        if ( !format.equals("csv") ) {
            url= new URL( url+"&format="+format );
        }
        
        logger.log(Level.FINE, "getDataSet {0}", url.toString());
        
        int[] nfields= new int[nparam];
        for ( int i=0; i<nparam; i++ ) {
            if ( pds[i].size.length==0 || ( pds[i].size.length==1 && pds[i].size[0]==1 ) ) {
                nfields[i]= 1;
            } else {
                nfields[i]= DataSetUtil.product(pds[i].size);
            }
        }
        int totalFields= DataSetUtil.sum(nfields);

        QDataSet ds;
        switch (format) {
            case "binary":
                ds= getDataSetViaBinary(totalFields, monitor, url, pds, tr, nparam, nfields);
                break;
            case "json":
                ds= getDataSetViaJSON(totalFields, monitor, url, pds, tr, nparam, nfields);
                break;
            default:
                ds= getDataSetViaCsv(totalFields, monitor, url, pds, tr, nparam, nfields);
                break;
        }
        
        if ( ds.length()==0 ) {
            monitor.finished();
            throw new NoDataInIntervalException("no records found");
        }
        
        ds = repackage(ds,pds,null);
        
        // install a cacheTag.  The following code assumes depend_0 is mutable.
        QDataSet xds= (QDataSet) ds.property(QDataSet.DEPEND_0);
        if ( xds==null && ( UnitsUtil.isTimeLocation( SemanticOps.getUnits(ds) ) ) ) {
            xds= ds;
        }
        if ( xds!=null ) {
            ((MutablePropertyDataSet)xds).putProperty(QDataSet.CACHE_TAG, new CacheTag(tr,null) );
        }
        
        monitor.setTaskProgress(100);
        monitor.finished();
        
        return ds;
        
    }

    private QDataSet getDataSetViaBinary(int totalFields, ProgressMonitor monitor, URL url, ParamDescription[] pds, DatumRange tr, int nparam,
            int[] nfields) throws IllegalArgumentException, Exception, IOException {
        DataSetBuilder builder = new DataSetBuilder(2, 100, totalFields);
        monitor.setProgressMessage("reading data");
        monitor.setTaskProgress(20);
        long t0 = System.currentTimeMillis() - 100; // -100 so it updates after receiving first record.
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        connection.setRequestProperty("Accept-Encoding", "gzip");
        connection.connect();
        boolean gzip = "gzip".equals(connection.getContentEncoding());

        int recordLengthBytes = 0;
        TransferType[] tts = new TransferType[pds.length];

        for (int i = 0; i < pds.length; i++) {
            if (pds[i].type.startsWith("time")) {
                recordLengthBytes += Integer.parseInt(pds[i].type.substring(4));
                tts[i] = TransferType.getForName(pds[i].type, Collections.singletonMap(QDataSet.UNITS, (Object)pds[i].units));
            } else if (pds[i].type.startsWith("string")) {
                recordLengthBytes += pds[i].length;
                final Units u= pds[i].units;
                final int length= pds[i].length;
                final byte[] bytes= new byte[length];
                tts[i] = new TransferType() {
                    @Override
                    public void write(double d, ByteBuffer buffer) {
                        
                    }

                    @Override
                    public double read(ByteBuffer buffer) {
                        buffer.get(bytes);
                        //buf2.get(bytes);
                        String s= new String( bytes );
                        Datum d= ((EnumerationUnits)u).createDatum(s);
                        return d.doubleValue(u);
                    }

                    @Override
                    public int sizeBytes() {
                        return length;
                    }

                    @Override
                    public boolean isAscii() {
                        return false;
                    }

                    @Override
                    public String name() {
                        return "string"+length;
                    }
                    
                };
                
            } else {
                Object type= pds[i].type;
                recordLengthBytes += BufferDataSet.byteCount(type) * DataSetUtil.product(pds[i].size);
                tts[i] = TransferType.getForName( type.toString(), Collections.singletonMap(QDataSet.UNITS, (Object)pds[i].units) );
            }
            if ( tts[i]==null ) {
                throw new IllegalArgumentException("unable to identify transfer type for \""+pds[i].type+"\"");
            }
        }

        totalFields= DataSetUtil.sum(nfields);
        double[] result = new double[totalFields];

        try (InputStream in = gzip ? new GZIPInputStream(connection.getInputStream()) : connection.getInputStream()) {
            ByteBuffer buf = TransferType.allocate( recordLengthBytes,ByteOrder.LITTLE_ENDIAN );
            byte[] bytes = buf.array();
            int bytesRead = in.read(bytes);
            while (bytesRead != -1) {
                while ( bytesRead<recordLengthBytes ) {
                    int b= in.read( bytes, bytesRead, recordLengthBytes-bytesRead );
                    if ( b==-1 ) throw new InterruptedIOException("expected "+recordLengthBytes+" bytes to complete a record" );
                    bytesRead+= b;
                }
                int ifield = 0;
                for (int i = 0; i < pds.length; i++) {
                    for (int j = 0; j < nfields[i]; j++) {
                        result[ifield] = tts[i].read(buf);
                        ifield++;
                    }
                }
                if (ifield != totalFields) {
                    logger.log(Level.WARNING, "expected {0} got {1}", new Object[]{totalFields, ifield});
                }

                // copy the data from double array into the builder.
                ifield= 0;
                Datum xx;
                xx = pds[0].units.createDatum(result[0]);
                if (System.currentTimeMillis() - t0 > 100) {
                    monitor.setProgressMessage("reading " + xx);
                    t0 = System.currentTimeMillis();
                    double d = DatumRangeUtil.normalize(tr, xx);
                    monitor.setTaskProgress(20 + (int) (75 * d));
                }
                builder.putValue(-1, ifield, xx);
                ifield++;
                for (int i = 1; i < nparam; i++) {  // nparam is number of parameters, which may have multiple fields.
                    for (int j = 0; j < nfields[i]; j++) {
                        builder.putValue(-1, ifield, result[ifield]);
                        //TODO: fill?
                        ifield++;
                    }
                }
                builder.nextRecord();

                buf.flip();
                bytesRead = in.read(bytes);
            }

        } catch (IOException e) {
            logger.log( Level.WARNING, e.getMessage(), e );
            monitor.finished();
            throw new IOException(String.valueOf(connection.getResponseCode()) + ":" + connection.getResponseMessage());

        } catch (Exception e) {
            logger.log( Level.WARNING, e.getMessage(), e );
            monitor.finished();
            throw e;
        } finally {
            connection.disconnect();
        }
        monitor.setTaskProgress(95);
        QDataSet ds = builder.getDataSet();
        return ds;
    }

    /**
     * read data embedded within a JSON response.  This current reads in the entire JSON document,
     * but the final version should use a streaming JSON library.
     * @param monitor
     * @return the dataset.
     * @throws Exception 
     */
    private QDataSet getDataSetViaJSON( int totalFields, ProgressMonitor monitor, URL url, ParamDescription[] pds, DatumRange tr, int nparam, int[] nfields) throws IllegalArgumentException, Exception, IOException {
        
        monitor.started();
        monitor.setProgressMessage("server is preparing data");
        
        long t0= System.currentTimeMillis() - 100; // -100 so it updates after receiving first record.
       
        int lineNum=0;
        
        StringBuilder builder= new StringBuilder();
        logger.log(Level.FINE, "getDocument {0}", url.toString());
        HttpURLConnection httpConnect=  ((HttpURLConnection)url.openConnection());
        httpConnect= (HttpURLConnection) HttpUtil.checkRedirect(httpConnect);
        try ( BufferedReader in= new BufferedReader( new InputStreamReader( httpConnect.getInputStream() ) ) ) {
            String line= in.readLine();
            lineNum++;
            while ( line!=null ) {
                if ( System.currentTimeMillis()-t0 > 100 ) {
                    monitor.setProgressMessage("reading line "+lineNum);
                    t0= System.currentTimeMillis();
                }
                //if ( line.startsWith("{ \"data\" :") ) { // TODO: kludge for Jon's server
                //    in.readLine();
                //    line= in.readLine();
                //}
                builder.append(line);
                line= in.readLine();
            }
        } catch ( IOException ex ) {
            ByteArrayOutputStream baos= new ByteArrayOutputStream();
            FileSystemUtil.copyStream( httpConnect.getErrorStream(), baos, new NullProgressMonitor() );
            String s= baos.toString("UTF-8");
            if ( s.contains("No data available") ) {
                logger.log(Level.FINE, "No data available, server responded with {0}: {1}", new Object[]{httpConnect.getResponseCode(), httpConnect.getResponseMessage()});
                throw new NoDataInIntervalException("No data available");
            } else {
                if ( s.length()<256 ) {
                    throw new IOException( ex.getMessage() + ": "+s );
                } else {
                    throw ex;
                }
            }
        }
        httpConnect.disconnect();

        monitor.setProgressMessage("parsing data");
                
        JSONObject jo= new JSONObject(builder.toString());
        JSONArray data= jo.getJSONArray("data");
        
        DataSetBuilder build= new DataSetBuilder( 2, data.length(), totalFields );
                
        for ( int i=0; i<data.length(); i++ ) {
            
            int ipd=0;
            int ifield=0;
            JSONArray record= data.getJSONArray(i);
            
            for ( ParamDescription pd: pds ) {
                Units u= pd.units;
                if ( nfields[ipd]>1 ) {
                    JSONArray fields= record.getJSONArray(ipd);
                    int nf= nfields[ipd];
                    int lastField= nf+ifield;
                    for ( ; ifield<lastField; ifield++ ) {
                        build.putValue( -1, ifield, pd.units.parse( fields.getString(ipd) ) );
                    }
                } else {
                    build.putValue( -1, ifield, pd.units.parse( record.getString(ipd) ) );
                }
                ifield+= nfields[ipd];
                ipd++;
            }
            build.nextRecord();
        }
                
        QDataSet result;
                
        result= build.getDataSet();
        
        return result;
    }
    
    /**
     * TODO: commas within quotes.  remove extra whitespace.
     * @param line
     * @return 
     */
    private String[] lineSplit( String line ) {
        String[] ss= line.split(",");
        for ( int i=0; i<ss.length; i++ ) {
            String s= ss[i].trim();
            if ( s.startsWith("\"") && s.endsWith("\"") ) {
                s= s.substring(1,s.length()-1);
            }
            ss[i]= s;
        }
        return ss;
    }
    
    private QDataSet getDataSetViaCsv(int totalFields, ProgressMonitor monitor, URL url, ParamDescription[] pds, DatumRange tr, int nparam, int[] nfields) throws IllegalArgumentException, Exception, IOException {
        DataSetBuilder builder= new DataSetBuilder(2,100,totalFields);
        monitor.setProgressMessage("reading data");
        monitor.setTaskProgress(20);
        long t0= System.currentTimeMillis() - 100; // -100 so it updates after receiving first record.
        HttpURLConnection connection= (HttpURLConnection)url.openConnection();
        connection.setRequestProperty( "Accept-Encoding", "gzip" );
        connection= (HttpURLConnection)HttpUtil.checkRedirect(connection);
        connection.connect();
        boolean gzip= "gzip".equals( connection.getContentEncoding() );
        try ( BufferedReader in= new BufferedReader( new InputStreamReader( gzip ? new GZIPInputStream( connection.getInputStream() ) : connection.getInputStream() ) ) ) {
            String line= in.readLine();
            while ( line!=null ) {
                String[] ss= lineSplit(line);
                if ( ss.length!=totalFields ) {
                    logger.log(Level.WARNING, "expected {0} fields, got {1}", new Object[]{totalFields, ss.length});
                    throw new IllegalArgumentException( String.format( "expected %d fields, got %d", new Object[]{totalFields, ss.length} ) );
                }
                int ifield=0;
                Datum xx;
                try {
                    xx= pds[ifield].units.parse(ss[ifield]);
                    if ( System.currentTimeMillis()-t0 > 100 ) {
                        monitor.setProgressMessage("reading "+xx);
                        t0= System.currentTimeMillis();
                        double d= DatumRangeUtil.normalize( tr, xx );
                        monitor.setTaskProgress( 20 + (int)( 75 * d ) );
                    }
                } catch ( ParseException ex ) {
                    line= in.readLine();
                    continue;
                }
                builder.putValue( -1, ifield, xx );
                ifield++;
                for ( int i=1; i<nparam; i++ ) {  // nparam is number of parameters, which may have multiple fields.
                    for ( int j=0; j<nfields[i]; j++ ) {
                        try {
                            String s= ss[ifield];
                            if ( pds[i].units instanceof EnumerationUnits ) {
                                builder.putValue( -1, ifield, ((EnumerationUnits)pds[i].units).createDatum(s).doubleValue(pds[i].units) );
                            } else {
                                builder.putValue( -1, ifield, pds[i].units.parse(s) );
                            }
                        } catch ( ParseException ex ) {
                            builder.putValue( -1, ifield, pds[i].fillValue );
                            pds[i].hasFill= true;
                        }
                        ifield++;
                    } 
                }
                builder.nextRecord();
                line= in.readLine();
            }
        } catch ( IOException e ) {
            logger.log( Level.WARNING, e.getMessage(), e );
            monitor.finished();
            throw new IOException( String.valueOf(connection.getResponseCode())+": "+connection.getResponseMessage() );
            
        } catch ( Exception e ) {
            logger.log( Level.WARNING, e.getMessage(), e );
            monitor.finished();
            throw e;
        } finally {
            connection.disconnect();
        }
        monitor.setTaskProgress(95);
        QDataSet ds= builder.getDataSet();
        return ds;
    }

    private ParamDescription[] getParameterDescriptions(JSONObject doc) throws IllegalArgumentException, ParseException, JSONException {
        JSONArray parameters= doc.getJSONArray("parameters");
        int nparameters= parameters.length();
        ParamDescription[] pds= new ParamDescription[nparameters];
        for ( int i=0; i<nparameters; i++ ) {
            
            final JSONObject jsonObjecti = parameters.getJSONObject(i);
            
            String name= jsonObjecti.getString("name"); // the name of one of the parameters.
            
            if ( name==null ) {
                name="name"+i;
                logger.log(Level.WARNING, "name not found for {0}th parameter", i );
            }
            
            pds[i]= new ParamDescription( name );
            
            String type;
            if ( jsonObjecti.has("type") ) {
                type= jsonObjecti.getString("type");
                if ( type==null ) type="";
            } else {
                type= "";
            }
            if ( type.equals("") ) {
                logger.log(Level.FINE, "type is not defined: {0}", name);
            }
            if ( type.equalsIgnoreCase("isotime") ) {
                if ( !type.equals("isotime") ) {
                    logger.log(Level.WARNING, "isotime should not be capitalized: {0}", type);
                }
                pds[i].units= Units.us2000;
                if ( jsonObjecti.has("length") ) {
                    pds[i].type= "time"+jsonObjecti.getInt("length");
                    pds[i].length= jsonObjecti.getInt("length");
                } else {
                    logger.log(Level.FINE, "server doesn''t report length for \"{0}\", assuming 24 characters, and that it doesn''t matter", name);
                    pds[i].type= "time24";
                }
                
            } else {
                pds[i].type= type;
                if ( jsonObjecti.has("units") ) {
                    String sunits= jsonObjecti.getString("units");
                    if ( sunits!=null ) {
                        pds[i].units= Units.lookupUnits(sunits);
                    }
                } else {
                    pds[i].units= Units.dimensionless;
                }
                
                if ( type.equals("String") ) {
                    type="string";
                    logger.warning("String used for type instead of string (lower case)");
                }

                if ( type.equals("string") ) {
                    pds[i].units= EnumerationUnits.create(name);
                }
                
                if ( jsonObjecti.has("fill") ) {
                    String sfill= jsonObjecti.getString("fill");
                    if ( sfill!=null ) {
                        if ( type.equals("string") ) {
                            pds[i].fillValue= ((EnumerationUnits)pds[i].units).createDatum( sfill ).doubleValue( pds[i].units );
                            pds[i].hasFill= true;
                        } else {
                            pds[i].fillValue= pds[i].units.parse( sfill ).doubleValue( pds[i].units );
                            pds[i].hasFill= true;
                        }
                    }
                } else {
                    pds[i].fillValue= FILL_VALUE; // when a value cannot be parsed, but it is not identified.
                }
                if ( jsonObjecti.has("description") ) {
                    pds[i].description= jsonObjecti.getString("description");
                    if ( pds[i].description==null ) pds[i].description= "";
                } else {
                    pds[i].description= ""; // when a value cannot be parsed, but it is not identified.
                }

                if ( jsonObjecti.has("length") ) {
                    pds[i].length= jsonObjecti.getInt("length");
                }

                if ( jsonObjecti.has("size") ) {
                    Object o= jsonObjecti.get("size");
                    if ( !(o instanceof JSONArray) ) {
                        if ( o.getClass()==Integer.class ) {
                            pds[i].size= new int[] { ((Integer)o) };
                            logger.log( Level.WARNING, "size should be an int array, found int: {0}", name);
                        } else if ( o.getClass()==String.class ) {
                            pds[i].size= new int[] { Integer.parseInt( (String)o ) };
                            logger.log( Level.WARNING, "size should be an int array, found String: {0}", name);
                        } else {
                            throw new IllegalArgumentException( String.format( "size should be an int array: %s", name ) );
                        }
                    } else {
                        JSONArray a= (JSONArray)o;
                        pds[i].size= new int[a.length()];
                        for ( int j=0; j<a.length(); j++ ) {
                            pds[i].size[j]= a.getInt(j);
                        }
                    }
                    if ( jsonObjecti.has("bins") ) {
                        o= jsonObjecti.get("bins");
                        if ( o instanceof JSONArray ) {
                            JSONArray ja= (JSONArray)o;
                            pds[i].depend= new QDataSet[ja.length()];
                            pds[i].dependName= new String[ja.length()];
                            for ( int j=0; j<ja.length(); j++ ) {
                                JSONObject bins= ja.getJSONObject(j);
                                if ( bins.has("parameter") ) {  // deprecated, see binsParameter below.  TODO: revisit this.
                                    int n= DataSetUtil.product(pds[i].size);
                                    pds[i].depend[j]= Ops.findgen(n);
                                    pds[i].dependName[j]= bins.getString("parameter");
                                } else if ( bins.has("ranges") ) {
                                    QDataSet dep= getJSONBins(ja.getJSONObject(j));
                                    pds[i].depend[j]= dep;
                                    pds[i].renderType= QDataSet.VALUE_RENDER_TYPE_NNSPECTROGRAM;
                                } else if ( bins.has("centers") ) {
                                    QDataSet dep= getJSONBins(ja.getJSONObject(j));
                                    pds[i].depend[j]= dep;
                                } else {
                                    int n= pds[i].size[j];
                                    pds[i].depend[j]= Ops.findgen(n);
                                }
                            }
                        } else {
                            logger.warning("bins should be an array");
                            JSONObject bins= jsonObjecti.getJSONObject("bins"); 
                            if ( pds[i].depend==null ) pds[i].depend= new QDataSet[1];
                            if ( pds[i].dependName==null ) pds[i].dependName= new String[1];
                            if ( bins.has("parameter") ) { // this will be implemented later.
                                int n= DataSetUtil.product(pds[i].size);
                                pds[i].depend[0]= Ops.findgen(n);
                                pds[i].dependName[0]= bins.getString("parameter");
                            } else if ( bins.has("values") ) {
                                QDataSet dep1= getJSONBins(bins);
                                pds[i].depend[0]= dep1;
                            } else {
                                int n= DataSetUtil.product(pds[i].size);
                                pds[i].depend[0]= Ops.findgen(n);
                            }
                        }
                    } else if ( jsonObjecti.has("binsParameter") ) {
                        o= jsonObjecti.get("binsParameter");
                        if ( o instanceof JSONArray ) {
                            JSONArray ja= (JSONArray)o;
                            pds[i].depend= new QDataSet[ja.length()];
                            pds[i].dependName= new String[ja.length()];
                            for ( int j=0; j<ja.length(); j++ ) {
                                String s= ja.getString(j);
                                int n= DataSetUtil.product(pds[i].size);
                                pds[i].depend[j]= Ops.findgen(n);
                                pds[i].dependName[j]= s;
                            }
                        }
                    } 
                }
            }
        }
        return pds;
    }

    /**
     * 
     * @param ds
     * @param pds
     * @param sort if non-null, resort the data with these indeces.
     * @return 
     */
    private QDataSet repackage(QDataSet ds, ParamDescription[] pds, int[] sort ) {
        int nparameters= ds.length(0);
        
        boolean combineRank2Depend1= pds.length==3 && pds[1].dependName!=null;
        
        QDataSet depend0= Ops.copy( Ops.slice1( ds,0 ) ); //TODO: this will be unnecessary after debugging.
        if ( ds.length(0)==2 ) {
            ds= Ops.copy( Ops.slice1( ds, 1 ) );
            ds= Ops.putProperty( ds, QDataSet.DEPEND_0, depend0 );
            ds= Ops.putProperty( ds, QDataSet.NAME, Ops.safeName(pds[1].name) );
            ds= Ops.putProperty( ds, QDataSet.LABEL, pds[1].name );
            ds= Ops.putProperty( ds, QDataSet.TITLE, pds[1].description );
            ds= Ops.putProperty( ds, QDataSet.UNITS, pds[1].units );
            if ( pds[1].hasFill ) {
                ds= Ops.putProperty( ds, QDataSet.FILL_VALUE, pds[1].fillValue );
            }
        } else if ( pds.length==2 ) {
            ds= Ops.copy( Ops.trim1( ds, 1, ds.length(0) ) );
            if ( pds[1].size.length>1 ) {
                ds= Ops.reform( ds, ds.length(), pds[1].size );
            }
            ds= Ops.putProperty( ds, QDataSet.DEPEND_0, depend0 );
            ds= Ops.putProperty( ds, QDataSet.NAME, Ops.safeName(pds[1].name) );
            ds= Ops.putProperty( ds, QDataSet.LABEL, pds[1].name );
            ds= Ops.putProperty( ds, QDataSet.TITLE, pds[1].description );
            ds= Ops.putProperty( ds, QDataSet.UNITS, pds[1].units );
            if ( pds[1].hasFill ) {
                ds= Ops.putProperty( ds, QDataSet.FILL_VALUE, pds[1].fillValue );
            }
            //if ( pds[1].depend1!=null ) {
            //    ds= Ops.putProperty( ds, QDataSet.DEPEND_1, pds[1].depend1 );
            //}
            if ( pds[1].depend!=null ) {
                for ( int j=0; j<pds[1].size.length; j++ ) {
                    ds= Ops.putProperty( ds, "DEPEND_"+(j+1), pds[1].depend[j] );
                }
            }
            if ( pds.length==2 && QDataSet.VALUE_RENDER_TYPE_NNSPECTROGRAM.equals( pds[1].renderType ) ) {
                ds= Ops.putProperty( ds, QDataSet.RENDER_TYPE, pds[1].renderType );
            }
        } else if ( pds.length==1 ) {
            return depend0;
            
        } else if ( combineRank2Depend1 ) {
            // we need to remove Epoch to DEPEND_0.
            SparseDataSetBuilder[] sdsbs= new SparseDataSetBuilder[pds.length];
            int ifield=1;
            int length1=ds.length(0); // this should be overwritten
            for ( int i=1; i<pds.length; i++ ) {
                int nfields1= DataSetUtil.product(pds[i].size);
                SparseDataSetBuilder sdsb= new SparseDataSetBuilder(2);
                sdsb.setLength(nfields1);
                int startIndex= sort==null ? ifield-1 : sort[ifield]-1;
                if ( nfields1>1 ) {
                    //bdsb.putProperty( QDataSet.ELEMENT_DIMENSIONS, ifield-1, pds[i].size ); // not supported yet.
                    sdsb.putProperty( QDataSet.ELEMENT_NAME, startIndex, Ops.safeName( pds[i].name ) );
                    sdsb.putProperty( QDataSet.ELEMENT_LABEL, startIndex, pds[i].name );        
                    for ( int j=0; j<pds[i].size.length; j++ ) {
                        sdsb.putValue( startIndex, j, pds[i].size[j] );
                    }
                    if ( pds[i].depend!=null ) {
                        if ( pds[i].size.length!=pds[i].depend.length ) throw new IllegalArgumentException("pds[i].size.length!=pds[i].depend.length");
                        for ( int j=0; j<pds[i].size.length; j++ ) {
                            if ( pds[i].dependName[j]!=null ) {
                                // wait
                            } else {
                                sdsb.putProperty( "DEPEND_"+(j+1), startIndex, pds[i].depend[j]);
                            }
                        }
                    }
                    //sdsb.putValue( QDataSet.ELEMENT_DIMENSIONS, ifield-1, pds[i].size );                    
                }
                for ( int j=0; j<nfields1; j++ ) {
                    if ( nfields1>1 ) {
                        sdsb.putProperty( QDataSet.START_INDEX, startIndex + j, startIndex );
                        sdsb.putProperty( QDataSet.LABEL, startIndex + j, pds[i].name +" ch"+j );                    
                        sdsb.putProperty( QDataSet.NAME, startIndex + j, Ops.safeName(pds[i].name)+"_"+j );
                    } else {
                        sdsb.putProperty( QDataSet.LABEL, startIndex + j, pds[i].name );                    
                        sdsb.putProperty( QDataSet.NAME, startIndex + j, Ops.safeName(pds[i].name) ); 
                    }
                    
                    sdsb.putProperty( QDataSet.TITLE, startIndex + j, pds[i].description );
                    sdsb.putProperty( QDataSet.UNITS, startIndex + j, pds[i].units );
                    if ( pds[i].hasFill ) {
                        sdsb.putProperty( QDataSet.FILL_VALUE, startIndex + j,  pds[i].fillValue );
                    }
                    if ( nfields1>1 ) {
                        sdsb.putProperty( QDataSet.START_INDEX, startIndex + j, startIndex );
                    }                    
                    ifield++;
                }
                length1=  nfields1;
                sdsbs[i]= sdsb;
            }
            
            int start= 1;
            WritableDataSet wds= Ops.copy( Ops.trim1( ds, start, start+length1 ) );
            start= start+length1;
            wds.putProperty( QDataSet.DEPEND_0, depend0 );
            wds.putProperty( QDataSet.BUNDLE_1, sdsbs[1].getDataSet() );
            
            for ( int i=1; i<pds.length; i++ ) { // only works for rank2!!!
                if ( pds[i].dependName!=null ) {
                    for (String dependName : pds[i].dependName) {
                        if (dependName != null) {
                            int k;
                            for (k=1; k<pds.length; k++) {
                                if (pds[k].name.equals(dependName)) {
                                    break;
                                }
                            }
                            if ( k<pds.length ) {
                                WritableDataSet depds= Ops.copy( Ops.trim1( ds, start, start+length1 ) );
                                depds.putProperty( QDataSet.DEPEND_0, depend0 );
                                depds.putProperty( QDataSet.BUNDLE_1, sdsbs[k].getDataSet() );    
                                start= start+length1;
                                wds.putProperty( "DEPEND_"+i, depds );
                            }
                        }
                    }
                }
            }
            
            ds= wds;            
            
        } else {
            // we need to remove Epoch to DEPEND_0.
            SparseDataSetBuilder sdsb= new SparseDataSetBuilder(2);
            sdsb.setLength(nparameters-1);
            int ifield=1;
            for ( int i=1; i<pds.length; i++ ) {
                int nfields1= DataSetUtil.product(pds[i].size);
                int startIndex= sort==null ? ifield-1 : sort[ifield]-1;
                if ( nfields1>1 ) {
                    //bdsb.putProperty( QDataSet.ELEMENT_DIMENSIONS, ifield-1, pds[i].size ); // not supported yet.
                    sdsb.putProperty( QDataSet.ELEMENT_NAME, startIndex, Ops.safeName( pds[i].name ) );
                    sdsb.putProperty( QDataSet.ELEMENT_LABEL, startIndex, pds[i].name );        
                    for ( int j=0; j<pds[i].size.length; j++ ) {
                        sdsb.putValue( startIndex, j, pds[i].size[j] );
                    }
                    if ( pds[i].depend!=null ) {
                        if ( pds[i].size.length!=pds[i].depend.length ) throw new IllegalArgumentException("pds[i].size.length!=pds[i].depend.length");
                        for ( int j=0; j<pds[i].size.length; j++ ) {
                            sdsb.putProperty( "DEPEND_"+(j+1), startIndex, pds[i].depend[j]);
                        }
                    }
                    //sdsb.putValue( QDataSet.ELEMENT_DIMENSIONS, ifield-1, pds[i].size );                    
                }
                for ( int j=0; j<nfields1; j++ ) {
                    if ( nfields1>1 ) {
                        sdsb.putProperty( QDataSet.START_INDEX, startIndex + j, startIndex );
                        sdsb.putProperty( QDataSet.LABEL, startIndex + j, pds[i].name +" ch"+j );                    
                        sdsb.putProperty( QDataSet.NAME, startIndex + j, Ops.safeName(pds[i].name)+"_"+j );
                    } else {
                        sdsb.putProperty( QDataSet.LABEL, startIndex + j, pds[i].name );                    
                        sdsb.putProperty( QDataSet.NAME, startIndex + j, Ops.safeName(pds[i].name) ); 
                    }
                    
                    sdsb.putProperty( QDataSet.TITLE, startIndex + j, pds[i].description );
                    sdsb.putProperty( QDataSet.UNITS, startIndex + j, pds[i].units );
                    if ( pds[i].hasFill ) {
                        sdsb.putProperty( QDataSet.FILL_VALUE, startIndex + j,  pds[i].fillValue );
                    }
                    if ( nfields1>1 ) {
                        sdsb.putProperty( QDataSet.START_INDEX, startIndex + j, startIndex );
                    }                    
                    ifield++;
                }
            }
            
            ds= Ops.copy( Ops.trim1( ds, 1, ds.length(0) ) );
            ds= Ops.putProperty( ds, QDataSet.DEPEND_0, depend0 );
            ds= Ops.putProperty( ds, QDataSet.BUNDLE_1, sdsb.getDataSet() );
        }
        return ds;
    }
    
}
