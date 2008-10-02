/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.tsds.datasource;

import org.das2.dataset.CacheTag;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.datum.format.DatumFormatter;
import org.das2.datum.format.TimeDatumFormatter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.text.ParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.das2.util.TimeParser;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.binarydatasource.BufferDataSet;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURL;
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

    public TsdsDataSource(URL url) {
        super(url);
        try {
            addCability(TimeSeriesBrowse.class, getTimeSeriesBrowse());

            setTSBParameters();  // we don't yet know the parameter resolution, but the time extent is set.
            
            ProgressMonitor mon = new NullProgressMonitor();

            mon.setProgressMessage("loading parameter metadata");
            LinkedHashMap<String, String> params3 = new LinkedHashMap<String, String>(params);
            params3.put("out", "tsml");
            params3.remove("ppd");

            URL url3 = new URL("" + this.resourceURL + "?" + DataSetURL.formatParams(params3));
            initialTsml(url3.openStream());

            haveInitialTsml = true;

            setTSBParameters();
            // limit the resolution accessible by this data source to that specified by the URL.
            parameterPpd = currentPpd;

        } catch (ParserConfigurationException ex) {
            Logger.getLogger(TsdsDataSource.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(TsdsDataSource.class.getName()).log(Level.SEVERE, null, ex);
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
    Document document;
    DatumRange parameterRange = null; // extent of the parameter.
    int parameterPpd = -1; // max resolution of the parameter.
    boolean haveInitialTsml = false;

    private DatumRange quantize(DatumRange timeRange) {
        timeRange = new DatumRange(TimeUtil.prevMidnight(timeRange.min()), TimeUtil.nextMidnight(timeRange.max()));
        return timeRange;
    }

    private int quantizePpd(Datum resolution) {
        int[] ppds = new int[]{1, 12, 144, 1440, 17280, 86400, 864000};
        if (resolution == null) return 1;
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

        DatumRange dr0 = DatumRangeUtil.parseTimeRangeValid(params2.get("StartDate"));
        DatumRange dr1 = DatumRangeUtil.parseTimeRangeValid(params2.get("EndDate"));
        timeRange = quantize(new DatumRange(dr0.min(), dr1.max()));

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

        if (inRequest) {
            System.err.println("came back again");

        } else {
            inRequest = true;
        }
        //http://www-pw.physics.uiowa.edu/das/das2Server
        //?dataset=das2_1/voyager1/pws/sa-4s-pf.new
        //&start_time=2004-01-01&end_time=2004-01-06&server=dataset&ascii=1

        // datasource url:
        //     tsds.http://timeseries.org/cgi-bin/get.cgi?StartDate=20010101&EndDate=20010101&ext=bin&ppd=24&param1=SourceAcronym_Subset-1-v0
        //     tsds.http://timeseries.org/cgi-bin/get.cgi?StartDate=20010101&EndDate=20010101&ext=bin&out=tsml&ppd=24&param1=SourceAcronym_Subset-1-v0
        // translates to:
        //          http://timeseries.org/cgi-bin/get.cgi?StartDate=20010101&EndDate=20010101&ext=bin&ppd=24&param1=SourceAcronym_Subset-1-v0
        Map<String, String> params2 = new LinkedHashMap<String, String>(params);

        DatumFormatter df = new TimeDatumFormatter("%Y%m%d");

        int ppd;
        // quantum levels

        if (timeRange != null) {
            System.err.println(timeRange.min().toString());
            System.err.println(timeRange.max().toString());
            System.err.println(timeRange.toString());
            timeRange = quantize(timeRange);
            params2.put("StartDate", "" + df.format(timeRange.min()));
            params2.put("EndDate", "" + df.format(TimeUtil.prev(TimeUtil.DAY, timeRange.max())));
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
            mon.setProgressMessage("loading parameter metadata");
            LinkedHashMap params3 = new LinkedHashMap(params2);
            params3.remove("ppd");
            params3.put("out", "tsml");
            URL url3 = new URL("" + this.resourceURL + "?" + DataSetURL.formatParams(params3));
            initialTsml(url3.openStream());
            haveInitialTsml = true;
        }

        if (currentPpd == -1) {
            ppd = 1;
            params2.put("ppd", "" + ppd);
        } else {
            ppd = currentPpd;
        }


        URL url2 = new URL("" + this.resourceURL + "?" + DataSetURL.formatParams(params2));


        int points = (int) Math.ceil(timeRange.width().doubleValue(Units.days)) * ppd;
        int size = points * SIZE_DOUBLE;

        logger.info("" + url2);
        HttpURLConnection connect = (HttpURLConnection) url2.openConnection();
        connect.connect();
        String type = connect.getContentType();

        BufferDataSet result;
        if (type.startsWith("text/xml")) {
            result = tsml(connect.getInputStream(), mon);
        } else {
            result = dataUrl(connect.getInputStream(), size, points, mon);
        }

        mon.finished();
        inRequest = false;

        return result;

    }

    public TimeSeriesBrowse getTimeSeriesBrowse() {
        return new TimeSeriesBrowse() {

            public void setTimeRange(DatumRange dr) {
                System.out.println(dr);
                timeRange = quantize(dr);
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

            public URL getURL() {
                try {
                    return new URL(TsdsDataSource.this.getURL());
                } catch (MalformedURLException ex) {
                    Logger.getLogger(TsdsDataSource.class.getName()).log(Level.SEVERE, null, ex);
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

    /**
     * Read in the TSDS binary stream into wrap it to make a QDataSet.
     * @param in
     * @param size number of bytes to read.  The stream is consumed up to this point, or to the end of the stream.
     * @param points number of data points.
     * @param ProgressMonitor in started state.  finished should not be called here.
     * @return
     * @throws java.io.IOException
     */
    private BufferDataSet dataUrl(InputStream in, int size, int points, ProgressMonitor mon) throws IOException {

        ReadableByteChannel bin = Channels.newChannel(in);

        ByteBuffer bbuf = ByteBuffer.allocate(size);
        int totalBytesRead = 0;
        int bytesRead = bin.read(bbuf);

        mon.setTaskSize(size);

        while (bytesRead >= 0 && (bytesRead + totalBytesRead) < size) {
            totalBytesRead += bytesRead;
            bytesRead = bin.read(bbuf);
            if (mon.isCancelled()) break;
            mon.setTaskProgress(totalBytesRead);
        }

        in.close();

        bbuf.flip();
        bbuf.order(ByteOrder.LITTLE_ENDIAN);

        DoubleBuffer dbuf = bbuf.asDoubleBuffer();

        points = dbuf.limit();
        return new org.virbo.binarydatasource.Double(1, points, 1, 0, 1, 1, 0, dbuf);
    }

    private QDataSet ttags(String sStartTime, int ppd, int points, String sTimePos) {
        Datum cadence = Units.days.createDatum(1).divide(ppd);
        Datum startTime = TimeUtil.createValid(sStartTime);
        Datum endTime = startTime.add(Units.days.createDatum((1. * points) / ppd));
        Datum t0 = startTime;
        if (sTimePos.equals("center"))
            t0 = t0.add(cadence.divide(2));

        try {
            DDataSet result = DDataSet.copy(Ops.timegen(String.valueOf(t0), String.valueOf(cadence), points));
            DatumRange timeRange = new DatumRange(startTime, endTime);
            result.putProperty(QDataSet.CACHE_TAG, new CacheTag(timeRange, cadence));
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
    private void initialTsml(InputStream in) throws ParserConfigurationException, IOException, SAXException {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            InputSource source = new InputSource(in);
            document = builder.parse(source);
            in.close();

            XPathFactory factory = XPathFactory.newInstance();
            XPath xpath = factory.newXPath();

            String sStartTime = xpath.evaluate("//TSML/StartDate/text()", document);
            String sEndTime = xpath.evaluate("//TSML/EndDate/text()", document);

            String sppd = xpath.evaluate("//TSML/IntervalsPerDay/text()", document);

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
            document = builder.parse(source);
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

            timeRange = new DatumRange(dr0.min(), dr1.max());
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
            String name = xpath.evaluate("//TSML/NameShort/text()", document);

            System.err.println(surl);
            URL dataUrl = new URL(surl);

            mon.setProgressMessage("loading mean");
            logger.fine("loading " + dataUrl);
            BufferDataSet data = dataUrl(dataUrl.openStream(), size, points, mon);

            boolean minMax = true;
            if (minMax && surl.contains("-filter_0-")) {
                String sDataMax = surl.replace("-filter_0-", "-filter_2-");
                //System.err.println(sDataMax);
                logger.fine("loading " + sDataMax);
                mon.setProgressMessage("loading max");
                BufferDataSet dataMax = dataUrl(new URL(sDataMax).openStream(), size, points, mon);
                String sDataMin = surl.replace("-filter_0-", "-filter_3-");
                //System.err.println(sDataMin);
                mon.setProgressMessage("loading min");
                BufferDataSet dataMin = dataUrl(new URL(sDataMin).openStream(), size, points, mon);
                data.putProperty(QDataSet.DELTA_PLUS, Ops.subtract(dataMax, data));
                data.putProperty(QDataSet.DELTA_MINUS, Ops.subtract(data, dataMin));
            }
            data.putProperty(QDataSet.UNITS, MetadataUtil.lookupUnits(sunits));
            data.putProperty(QDataSet.DEPEND_0, ttags);
            data.putProperty(QDataSet.NAME, name);
            data.putProperty(QDataSet.TITLE, title);

            return data;

        } catch (XPathExpressionException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public Map<String, Object> getMetaData(ProgressMonitor mon) throws Exception {
        Node n = document.getFirstChild();
        return MetadataUtil.toMetaTree(n);
    }

    @Override
    public String getURL() {
        TimeParser tp = TimeParser.create("%Y%m%d");

        String sparams = "param1=" + params.get("param1") 
                + "&StartDate=" + tp.format(timeRange.min(), null) 
                + "&EndDate=" + tp.format(timeRange.max(), null) +
                "&ppd=" + currentPpd;
        
        return this.resourceURL.toString() + "?" + sparams;
       
    }
}
