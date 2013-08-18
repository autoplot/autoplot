/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.tsds.datasource;

import org.das2.datum.CacheTag;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.datum.format.DatumFormatter;
import org.das2.datum.format.TimeDatumFormatter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.das2.datum.TimeParser;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.binarydatasource.BufferDataSet;
import org.virbo.dataset.ArrayDataSet;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.capability.TimeSeriesBrowse;
import org.virbo.dsops.Ops;
import org.virbo.metatree.MetadataUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author jbf
 */
class TsdsDataSource extends AbstractDataSource {

    long t0 = System.currentTimeMillis();

    public TsdsDataSource(URI uri) {
        super(uri);
        try {
            addCability(TimeSeriesBrowse.class, getTimeSeriesBrowse());

            setTSBParameters();  // we don't yet know the parameter resolution, but the time extent is set.

            ProgressMonitor mon = new NullProgressMonitor();

            URL url0 = new URL("" + this.resourceURI + "?" + URISplit.formatParams(params));
            logger.log(Level.FINE, "tsds url= {0}", url0);

            if (params.get("out") == null) {
                if (params.isEmpty() ) {
                    // legacy
                } else {
                    exceptionFromConstruct = new IllegalArgumentException("url must contain out=");
                    return;
                }
            }

            mon.setProgressMessage("loading parameter metadata");
            LinkedHashMap<String, String> params3 = new LinkedHashMap<String, String>(params);

            params3.put("out", "tsml");
            params3.remove("ppd");

            String sparams = URISplit.formatParams(params3);
            sparams = sparams.replace("out=tsml", "out=tsml&ext=" + params.get("out"));

            logit("post first request in construct TsdsDataSource", t0);
            URL url3 = new URL("" + this.resourceURI + "?" + sparams);
            logger.log(Level.FINE, "opening {0}", url3);
            initialTsml(url3.openStream());

            if ( hasEndDate==false ) {
                params.put( "EndDate", TimeParser.create("$Y$m$d").format( TimeUtil.prevMidnight( timeRange.max().subtract( Units.days.createDatum(1)) ), null ) );
            }

            logit("read initial tsml", t0);
            haveInitialTsml = true;

            setTSBParameters();
            // limit the resolution accessible by this data source to that specified by the URL.
            parameterPpd = currentPpd;

        } catch (ParserConfigurationException ex) {
            Logger.getLogger(TsdsDataSource.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(TsdsDataSource.class.getName()).log(Level.SEVERE, null, ex);
            exceptionFromConstruct = ex;
        } catch (SAXException ex) {
            Logger.getLogger(TsdsDataSource.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    /**
     * current timeRange, which will be quantized to granule boundaries.
     */
    DatumRange timeRange;
    /**
     * current timeRange, which will be quantized to granule boundaries.
     */
    Datum resolution;
    /**
     * current points per day, should be short-circuit to timeRange.
     */
    int currentPpd = -1;
    private static final int SIZE_DOUBLE = 8;
    private static final Logger logger = Logger.getLogger("virbo.tsds.datasource");
    Document initialDocument;
    DatumRange parameterRange = null; // extent of the parameter.
    int parameterPpd = -1; // max resolution of the parameter.
    boolean haveInitialTsml = false;
    Exception exceptionFromConstruct = null;

    private boolean hasEndDate= false;

    private void logit(String string, long t0) {
        //System.err.println( "  ### "+string + ": "+ (System.currentTimeMillis()-t0) );
    }

    private DatumRange quantizeTimeRange(DatumRange timeRange) {
        timeRange = new DatumRange(TimeUtil.prevMidnight(timeRange.min()), TimeUtil.nextMidnight(timeRange.max()));
        return timeRange;
    }

    private int quantizePpd(Datum resolution) {
        int[] ppds = new int[]{1, 8, 24, 96, 144, 4320, 17280, 86400, 864000};
        if (resolution == null) {
            return 1;
        }
        double resdays = resolution.doubleValue(Units.days);
        double dppd = 1 / resdays;
        int ppd = ppds[ppds.length - 1];
        for (int i = 0; i < ppds.length && ppds[i] <= parameterPpd; i++) {
            if (ppds[i] > dppd) {
                ppd = ppds[i];
                return ppd;
            }
        }
        return parameterPpd;
    }

    private void setTSBParameters() {
        Map<String, String> params2 = new LinkedHashMap<String, String>(params);

        String str= params.get("timerange");

        if ( str==null ) {
            DatumRange dr0,dr1;
            if ( params2.isEmpty() ) { // this should be rejected now.  I'll delete this after Bob confirms that support for this can be removed.
                String suri= uri.toString();
                int i= suri.indexOf("to_");
                String sto= suri.substring(i+3,i+11);
                i= suri.indexOf("tf_");
                String stf= suri.substring(i+3,i+11);
                dr0 = DatumRangeUtil.parseTimeRangeValid(sto);
                dr1 = DatumRangeUtil.parseTimeRangeValid(stf);
            } else {
                dr0 = DatumRangeUtil.parseTimeRangeValid(params2.get("StartDate"));
                String sEndDate= params2.get("EndDate");
                if ( sEndDate==null ) {
                    dr1= dr0;
                    hasEndDate= false;
                } else {
                    dr1= DatumRangeUtil.parseTimeRangeValid(sEndDate);
                    hasEndDate= true;
                } // EndDate is no longer required to support near-realtime
            }
            timeRange = quantizeTimeRange(new DatumRange(dr0.min(), dr1.max()));
        } else {
            timeRange = quantizeTimeRange( DatumRangeUtil.parseTimeRangeValid(str) );
        }

        int ppd;
        String sppd = params2.get("ppd");
        if (sppd != null) {
            ppd = Integer.parseInt(sppd);
            if (ppd > parameterPpd) {
                currentPpd = parameterPpd;
            } else {
                currentPpd = ppd;
            }
            resolution = Units.days.createDatum(1.0).divide(currentPpd);
        } else {
            ppd = -1;
            currentPpd = -1;
            resolution = null;
        }

    }
    boolean inRequest = false;

    @Override
    @SuppressWarnings("unchecked")
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {
        logit("enter getDataSet", t0);
        if (inRequest) {
            logger.fine("came back again");

        } else {
            inRequest = true;
        }
        //http://www-pw.physics.uiowa.edu/das/das2Server
        //?dataset=das2_1/voyager1/pws/sa-4s-pf.new
        //&start_time=2004-01-01&end_time=2004-01-06&server=dataset&ascii=1

        // datasource uri:
        //     tsds.http://timeseries.org/cgi-bin/get.cgi?StartDate=20010101&EndDate=20010101&ext=bin&ppd=24&param1=SourceAcronym_Subset-1-v0
        //     tsds.http://timeseries.org/cgi-bin/get.cgi?StartDate=20010101&EndDate=20010101&ext=bin&out=tsml&ppd=24&param1=SourceAcronym_Subset-1-v0
        // translates to:
        //          http://timeseries.org/cgi-bin/get.cgi?StartDate=20010101&EndDate=20010101&ext=bin&ppd=24&param1=SourceAcronym_Subset-1-v0
        Map<String, String> params2 = new LinkedHashMap<String, String>(params);

        DatumFormatter df = new TimeDatumFormatter("%Y%m%d");

        int ppd;
        // quantum levels

        if (timeRange != null) {
            timeRange = quantizeTimeRange(timeRange);
            params2.put("StartDate", "" + df.format(timeRange.min()));
            params2.put("EndDate", "" + df.format(TimeUtil.prev(TimeUtil.DAY, timeRange.max())));
            params2.remove("timerange");
        } else {
            setTSBParameters();
        }

        if (currentPpd == -1) {
            params2.put("ppd", "1");
        } else {
            params2.put("ppd", "" + currentPpd);
        }


        mon.setTaskSize(-1);
        mon.started();

        if (!haveInitialTsml) {

            if (exceptionFromConstruct != null) {
                throw exceptionFromConstruct;
            }
            mon.setProgressMessage("loading parameter metadata");
            LinkedHashMap params3 = new LinkedHashMap(params2);
            params3.remove("ppd");
            params3.put("out", "tsml");
            URL url3 = new URL("" + this.resourceURI + "?" + URISplit.formatParams(params3));
            logger.log(Level.FINE, "opening {0}", url3);
            initialTsml(url3.openStream());
            haveInitialTsml = true;
            logit("got initial tsml", t0);
        }

        if (currentPpd == -1) {
            ppd = 1;
            params2.put("ppd", "" + ppd);
        } else {
            ppd = currentPpd;
        }


        URL url2 = new URL("" + this.resourceURI + "?" + URISplit.formatParams(params2));


        int points = (int) Math.ceil(timeRange.width().doubleValue(Units.days)) * ppd;
        int size = points * SIZE_DOUBLE;

        logit("making url2 connection", t0);
        logger.log(Level.FINE, "{0}", url2);
        HttpURLConnection connect = (HttpURLConnection) url2.openConnection();
        connect.connect();
        String type = connect.getContentType();

        if ( !(params.get("out").equals("ncml")) && type==null ) { // saw this in Hudson test 20120626 http://sarahandjeremy.net:8080/hudson/job/autoplot-test018/3564/console
            throw new IllegalArgumentException("unable to get content type from "+url2 );
        }

        logit("made url2 connection", t0);

        QDataSet result;
        if ( params.get("out").equals("ncml") ) {
            result= new TsmlNcml().doRead(url2,connect);
        } else if (type.startsWith("text/xml")) {
            result = tsml(connect.getInputStream(), mon);
            logit("done text/xml from url2", t0);
        } else {
            result = dataUrl(connect, size, points,-1, mon);
            logit("done dataUrl from url2", t0);
        }

        mon.finished();
        inRequest = false;

        return result;

    }

    final public TimeSeriesBrowse getTimeSeriesBrowse() {
        return new TimeSeriesBrowse() {

            public void setTimeRange(DatumRange dr) {
                System.out.println(dr);
                timeRange = quantizeTimeRange(dr);
                System.out.println(timeRange);
                System.out.println(timeRange.width());
            }

            public void setTimeResolution(Datum d) {
                resolution = d;
                if (resolution == null) {
                    currentPpd = -1;
                } else {
                    currentPpd = quantizePpd(resolution);
                    resolution = Units.days.createDatum(1.0).divide(currentPpd);
                }
            }

            public String getURI() {
                TimeParser tp = TimeParser.create("%Y%m%d");

                String sparams =
                    "StartDate=" + tp.format(timeRange.min(), null) + "&EndDate=" + tp.format(timeRange.max(), null) +
                    "&ppd=" + currentPpd +
                    "&ext=" + params.get("ext") +
                    "&out=" + params.get("out") +
                    "&param1=" + params.get("param1");

                return "vap+tsds:" + DataSetURI.fromUri( TsdsDataSource.this.resourceURI ) + "?" + sparams;
            }

            public String blurURI() {

                String sparams =
                    "&ext=" + params.get("ext") +
                    "&out=" + params.get("out") +
                    "&param1=" + params.get("param1");

                return "vap+tsds:" + DataSetURI.fromUri( TsdsDataSource.this.resourceURI ) + "?" + sparams;
                
            }
            
            public DatumRange getTimeRange() {
                return timeRange;
            }

            public Datum getTimeResolution() {
                return resolution;
            }

            public void setURI(String suri) throws ParseException {
                
            }
        };
    }

    /**
     * Read in the TSDS binary stream into wrap it to make a QDataSet.
     * @param in
     * @param size number of bytes to read.  The stream is consumed up to this point, or to the end of the stream.
     * @param points number of data points.
     * @param len1 >-1 indicates the length on rank2 dataset, -1 indicates rank 1.
     * @param ProgressMonitor in started state.  finished should not be called here.
     * @return
     * @throws java.io.IOException
     */
    private BufferDataSet dataUrl(HttpURLConnection connection, int size, int points, int len1, ProgressMonitor mon) throws IOException {

        InputStream in = connection.getInputStream();
        String encoding = connection.getContentEncoding();
        logger.log(Level.FINER, "downloading {0}", connection.getURL());
        if ( encoding!=null && encoding.equalsIgnoreCase("gzip")) {
            logger.finer("got gzip encoding");
            in = new GZIPInputStream(in);
        } else if ( encoding!=null && encoding.equalsIgnoreCase("deflate")) {
            logger.finer("got deflate encoding");
            in = new InflaterInputStream(in, new Inflater(true));
        }

        //TODO: check encoding
        ReadableByteChannel bin = Channels.newChannel(in);

        ByteBuffer bbuf = ByteBuffer.allocate(size);
        int totalBytesRead = 0;
        int bytesRead = bin.read(bbuf);

        mon.setTaskSize(size);

        while (bytesRead >= 0 && (bytesRead + totalBytesRead) < size) {
            totalBytesRead += bytesRead;
            bytesRead = bin.read(bbuf);
            if (mon.isCancelled()) {
                throw new InterruptedIOException("cancel read in TSDS");
            }
            mon.setTaskProgress(totalBytesRead);
        }

        in.close();

        bbuf.flip();
        bbuf.order(ByteOrder.LITTLE_ENDIAN);

        int expectedPoints= points; 
        points = bbuf.limit() / SIZE_DOUBLE;

        if ( points==0 && points<expectedPoints ) {
            throw new IOException( "No data returned from "+ connection.getURL() );
        }

        if ( len1==-1 ) {
            return new org.virbo.binarydatasource.Double( 1, SIZE_DOUBLE, 0, points, 1, 1, 1, bbuf );
        } else {
            return new org.virbo.binarydatasource.Double( 2, len1*SIZE_DOUBLE, 0, points, len1, 1, 1, bbuf );
        }
    }

    private QDataSet ttags(String sStartTime, int ppd, int points, String sTimePos) {
        Datum cadence = Units.days.createDatum(1).divide(ppd);
        Datum startTime = TimeUtil.createValid(sStartTime);
        Datum endTime = startTime.add(Units.days.createDatum((1. * points) / ppd));
        Datum t0_1 = startTime;
        if (sTimePos.equals("center")) {
            t0_1 = t0_1.add(cadence.divide(2));
        }

        try {
            DDataSet result = (DDataSet) ArrayDataSet.copy( double.class, Ops.timegen(String.valueOf(t0_1), cadence.getFormatter().format(cadence), points));
            DatumRange timeRange_1 = new DatumRange(startTime, endTime);
            result.putProperty(QDataSet.CACHE_TAG, new CacheTag(timeRange_1, cadence));
            return result;
        } catch (ParseException ex) {
            throw new RuntimeException(ex);
        }
    }


    /**
     * do the initial settings.
     * @param in
     * @throws javax.xml.parsers.ParserConfigurationException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    private void initialNcml(InputStream in) throws ParserConfigurationException, IOException, SAXException {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            InputSource source = new InputSource(in);
            initialDocument = builder.parse(source);
            in.close();

            XPathFactory factory = XPathFactory.newInstance();
            XPath xpath = factory.newXPath();

            String sStartTime = xpath.evaluate("//netcdf/StartDate/text()", initialDocument);
            String sEndTime = xpath.evaluate("//netcdf/EndDate/text()", initialDocument);

            String sppd = xpath.evaluate("//netcdf/IntervalsPerDay/text()", initialDocument);

            int ppd = Integer.parseInt(sppd);
            parameterPpd = ppd;

            DatumRange dr0 = DatumRangeUtil.parseTimeRangeValid(sStartTime);
            DatumRange dr1 = DatumRangeUtil.parseTimeRangeValid(sEndTime);

            parameterRange = new DatumRange(dr0.min(), dr1.max());

        } catch (XPathExpressionException ex) {
            Logger.getLogger(TsdsDataSource.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    /**
     * do the initial settings.
     * @param in
     * @throws javax.xml.parsers.ParserConfigurationException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    private void initialTsml(InputStream in) throws ParserConfigurationException, IOException, SAXException {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            InputSource source = new InputSource(in);
            initialDocument = builder.parse(source);
            in.close();

            XPathFactory factory = XPathFactory.newInstance();
            XPath xpath = factory.newXPath();

            String sStartTime = xpath.evaluate("//TSML/StartDate/text()", initialDocument);
            String sEndTime = xpath.evaluate("//TSML/EndDate/text()", initialDocument);

            String sppd = xpath.evaluate("//TSML/IntervalsPerDay/text()", initialDocument);

            int ppd = Integer.parseInt(sppd);
            parameterPpd = ppd;

            DatumRange dr0 = DatumRangeUtil.parseTimeRangeValid(sStartTime);
            DatumRange dr1 = DatumRangeUtil.parseTimeRangeValid(sEndTime);

            if ( hasEndDate==false ) {
                timeRange= new DatumRange( dr0.min(), dr1.max() );
            }
            parameterRange = new DatumRange(dr0.min(), dr1.max());

        } catch (XPathExpressionException ex) {
            Logger.getLogger(TsdsDataSource.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    /**
     * The resource is a TSML file, which is an xml description of a binary stream.  The
     * URL of the stream is found within the description, and this is loaded.  TSML
     * syntax description is used to parse the stream.
     * 
     * @param connect HTTPURLConnection 
     * @param mon ProgressMonitor in started state.  finished should not be called here.
     * @return 
     * @throws javax.xml.parsers.ParserConfigurationException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    private BufferDataSet tsml(InputStream in, ProgressMonitor mon) throws ParserConfigurationException, IOException, SAXException {
        try {

            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            InputSource source = new InputSource(in);
            Document document = builder.parse(source);
            in.close();

            XPathFactory factory = XPathFactory.newInstance();
            XPath xpath = factory.newXPath();

            String surl = xpath.evaluate("//TSML/DataURL/text()", document);

            String sunits = xpath.evaluate("//TSML/Unit/text()", document);

            String sStartTime = xpath.evaluate("//TSML/StartDate/text()", document);
            String sEndTime = xpath.evaluate("//TSML/EndDate/text()", document);

            String sppd = xpath.evaluate("//TSML/IntervalsPerDay/text()", document);

            int ppd = Integer.parseInt(sppd);
            if (parameterPpd == -1) {
                parameterPpd = ppd;

            }

            DatumRange dr0 = DatumRangeUtil.parseTimeRangeValid(sStartTime);
            DatumRange dr1 = DatumRangeUtil.parseTimeRangeValid(sEndTime);

            try {
                timeRange = new DatumRange(dr0.min(), dr1.max());
            } catch ( IllegalArgumentException ex ) {
                timeRange = new DatumRange(dr0.min(), dr1.max());
            }
            int points = (int) Math.ceil(timeRange.width().doubleValue(Units.days)) * ppd;
            int size = points * SIZE_DOUBLE;

            QDataSet ttags;

            String sTimePos = xpath.evaluate("//TSML/TimeStampPosition/text()", document);
            if (!sTimePos.equals("")) {
                ttags = ttags(sStartTime, ppd, points, sTimePos);
            } else {
                ttags = null;
            }

            String title = xpath.evaluate("//TSML/Name/text()", document);
            String name = xpath.evaluate("//TSML/DataKey/text()", document);
            name = name.replaceAll("-", "_"); // make c/java style identifier

            logit("done parse tsml", t0);

            boolean minMax = ppd < parameterPpd;
            boolean useFilter4= true;

            BufferDataSet data;

            if ( minMax && useFilter4 && surl.contains("-filter_0-") ) {
                String surl4= surl.replace("-filter_0-", "-filter_4-");
                mon.setProgressMessage("loading data and ranges");
                URL dataUrl = new URL(surl4);
                HttpURLConnection connect = (HttpURLConnection) dataUrl.openConnection();
                connect.setRequestProperty("Accept-Encoding", "gzip, deflate");
                logger.log(Level.FINE, "loading {0}", surl4);
                org.virbo.binarydatasource.Double data3 = (org.virbo.binarydatasource.Double)dataUrl(connect, 3*size, 3*points, -1, mon);
                logit("done loading mean", t0);

                data= (BufferDataSet)data3.trim( 0, points );
                BufferDataSet dataMin= (BufferDataSet)data3.trim( 2*points, 3*points );
                dataMin.putProperty( QDataSet.NAME, "binmin" );
                BufferDataSet dataMax= (BufferDataSet)data3.trim( 1*points, 2*points );
                dataMax.putProperty( QDataSet.NAME, "binmax" );
                data.putProperty(QDataSet.DELTA_PLUS, Ops.subtract(dataMax, data));
                data.putProperty(QDataSet.DELTA_MINUS, Ops.subtract(data, dataMin));
                
            } else {
                mon.setProgressMessage("loading mean");
                URL dataUrl = new URL(surl);
                HttpURLConnection connect = (HttpURLConnection) dataUrl.openConnection();
                connect.setRequestProperty("Accept-Encoding", "gzip, deflate"); 
                logger.log(Level.FINE, "loading {0}", dataUrl);
                data = dataUrl(connect, size, points,-1, mon);
                logit("done loading mean", t0);
            }
            
            if ( !useFilter4 && minMax && surl.contains("-filter_0-")) {
                HttpURLConnection connect;
                String sDataMax = surl.replace("-filter_0-", "-filter_2-");
                logger.log(Level.FINE, "loading {0}", sDataMax);
                mon.setProgressMessage("loading max");
                URL maxUrl = new URL(sDataMax);
                connect = (HttpURLConnection) maxUrl.openConnection();
                connect.setRequestProperty("Accept-Encoding", "gzip, deflate");
                BufferDataSet dataMax = dataUrl(connect, size, points,-1, mon);
                logit("done loading max", t0);
                dataMax.putProperty(QDataSet.NAME, "binmax");
                String sDataMin = surl.replace("-filter_0-", "-filter_3-");
                logger.log(Level.FINE, "loading {0}", sDataMin);
                mon.setProgressMessage("loading min");
                URL minUrl = new URL(sDataMin);
                connect = (HttpURLConnection) minUrl.openConnection();
                connect.setRequestProperty("Accept-Encoding", "gzip, deflate");
                BufferDataSet dataMin = dataUrl(connect, size, points,-1, mon);
                logit("done loading min", t0);
                dataMin.putProperty(QDataSet.NAME, "binmin");
                data.putProperty(QDataSet.DELTA_PLUS, Ops.subtract(dataMax, data));
                data.putProperty(QDataSet.DELTA_MINUS, Ops.subtract(data, dataMin));
            }

            data.putProperty(QDataSet.UNITS, SemanticOps.lookupUnits(sunits));
            data.putProperty(QDataSet.DEPEND_0, ttags);
            data.putProperty(QDataSet.NAME, name);
            data.putProperty(QDataSet.TITLE, title);

            return data;

        } catch (XPathExpressionException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public Map<String, Object> getMetadata(ProgressMonitor mon) throws Exception {
        Node n = initialDocument.getFirstChild();
        return MetadataUtil.toMetaTree(n);
    }

    @Override
    public String getURI() {
        return super.getURI();
    }
}
