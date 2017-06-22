/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.tsds.datasource;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.das2.datum.LoggerManager;
import org.das2.datum.Units;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.buffer.BufferDataSet;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.TagGenDataSet;
import org.das2.qds.ops.Ops;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * This new transport for the TSDS server uses a dialect of ncml to describe
 * data.  Using a particular dialect allows a simple reader to be used without
 * having to include the full netcdf library.
 *
 * The public method "doRead" the entry point, and is given the location of one
 * of ncml+tsml data, and then internal references are followed to load the data
 * values as well as metadata.
 *
 * @author jbf
 */
public class TsmlNcml {

    private static final int RANK_LIMIT = 2;

    public static void main(String[] args) throws Exception {
        new TsmlNcml().doRead(
                new URL("http://timeseries.org/cgi-bin/get.cgi?StartDate=19890104&EndDate=19890104&ext=bin" +
                "&out=ncml&ppd=8&filter=4&param1=SourceAcronym_Subset1-1-v0"), null);
    }
    URL codebase = null;

    /**
     * read the ncml+tsml from the url, and follow references to read in the dataset.
     * @param url the location of ncml+tsml file.  This also defines the codebase for references within the file.
     * @param connect, if non-null, use this connection.  url must still be provided to define the codebase.
     * @return the data in a QDataSet.
     * @throws java.io.IOException
     * @throws javax.xml.parsers.ParserConfigurationException
     * @throws org.xml.sax.SAXException
     */
    public QDataSet doRead(URL url, URLConnection connect) throws IOException, ParserConfigurationException, SAXException {
        //URL url= TsmlNcml.class.getResource("/test/data.filter4.tsml.ncml.xml");
        codebase = url;

        InputStream in;
        if (connect != null) {
            in = connect.getInputStream();
        } else {
            in = url.openStream();
        }

        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        InputSource source = new InputSource(in);
        Document document = builder.parse(source);
        in.close();

        QDataSet result = null;
        NodeList kids = document.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n.getNodeName().equals("netcdf")) {
                result = netcdf(n);
            }
        }

        return result;

    }
    private static final Logger logger = LoggerManager.getLogger("apdss.tsds");

    private MutablePropertyDataSet aggregation(Node aggr) throws MalformedURLException, IOException {
        NodeList kids = aggr.getChildNodes();
        MutablePropertyDataSet result = null;

        LinkedHashMap<String, MutablePropertyDataSet> dss = new LinkedHashMap();

        MutablePropertyDataSet depend = null; // the dataset that has dependencies.
        String lastKey = null;
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n.getNodeName().equals("netcdf")) {
                MutablePropertyDataSet ds = netcdf(n);
                dss.put((String) ds.property(QDataSet.NAME), ds);
                lastKey = (String) ds.property(QDataSet.NAME);
                if (!ds.property("shape").equals(ds.property(QDataSet.NAME)))
                    depend = ds;
            }
        }

        if (depend != null) {
            String shape = (String) depend.property("shape");
            String[] shapes = shape.split("[, ]");
            for (int i = 0; i < shapes.length; i++) {
                Ops.dependsOn(depend, i, dss.get(shapes[i]));
            }
            return depend;
        } else {
            return dss.get(lastKey);
        }
    }

    private Units lookupUnits(String sunits) {
        if (sunits.contains("since")) {
            try {
                return Units.lookupTimeUnits(sunits);
            } catch (ParseException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            return Units.lookupUnits(sunits);
        }
    }

    protected MutablePropertyDataSet netcdf(Node node) throws MalformedURLException, IOException {
        Map<String, Object> props = new HashMap();
        NodeList nl = node.getChildNodes();
        MutablePropertyDataSet result = null;
        Map<String, Node> dimensions = new LinkedHashMap();
        NamedNodeMap attrs = node.getAttributes();
        String dataType = null;
        if (attrs.getNamedItem("location") != null) {
            result = location(node);
        } else {
            for (int i = 0; i < nl.getLength(); i++) {
                Node child = nl.item(i);
                if (child.getNodeName().equals("aggregation")) {
                    result = aggregation(child);
                } else if (child.getNodeName().equals("dimension")) {
                    dimensions.put(maybeGetAttr(child, "name"), child);
                } else if (child.getNodeName().equals("attribute")) {
                    String attName= ((Attr) child.getAttributes().getNamedItem("name")).getValue();
                    String attValue= ((Attr) child.getAttributes().getNamedItem("value")).getTextContent();
                    if (attName.equals("units")) {
                        props.put(QDataSet.UNITS, lookupUnits(attValue));
                    } else if (attName.equals("DataType")) {
                        dataType = attValue;
                    } else if (attName.equals("long_name")) { //TODO: this is really TITLE, according to COARDS
                        props.put( QDataSet.LABEL, attValue );
                    } else if (attName.equals("title") ) {
                        props.put( QDataSet.TITLE, attValue );
                    }
                } else if (child.getNodeName().equals("variable")) {
                    result = variable(child, dimensions, null);
                    String oldLabel= (String)result.property(QDataSet.LABEL);
                    if ( oldLabel==null ) result.putProperty( QDataSet.LABEL, ((Attr)child.getAttributes().getNamedItem("name")).getValue() );
                }
            }
        }
        if (dataType != null) {
            if (dataType.equals("vector")) {
                String[] componentLabels = new String[result.length(0)];
                for (int i = 0; i < componentLabels.length; i++) {
                    componentLabels[i] = "c" + i; // TODO: ask Bob how the components are labelled.
                }
                result.putProperty(QDataSet.DEPEND_1, Ops.labels(componentLabels)); //TODO: QDataSet scheme change coming COMPONENT.
            }
        }
        for ( Entry e : props.entrySet() ) {
            result.putProperty( (String)e.getKey(), e.getValue() );
        }
        return result;
    }

    private static int dimensionLength(Node dimension) {
        int n = Integer.parseInt(((Attr) dimension.getAttributes().getNamedItem("length")).getNodeValue());
        return n;
    }

    /**
     * return the attr value, or null if it's not found.
     * @param node
     * @param name
     * @return
     */
    private static String maybeGetAttr(Node node, String name) {
        Node niosp = node.getAttributes().getNamedItem(name);
        if (niosp == null) return null;
        else return niosp.getNodeValue();
    }

    protected MutablePropertyDataSet location(Node node) throws MalformedURLException, IOException {
        MutablePropertyDataSet result = null;

        String iosp = maybeGetAttr(node, "iosp");

        if ("org.timeseries.tsds".equals(iosp)) {
            result = tsdsLocation(node);
        }
        return result;
    }

    /**
     *
     * @param node
     * @param dimensions
     * @param values  if non-null, these are the values read in via iosp.
     * @return
     */
    protected MutablePropertyDataSet variable(Node node, Map<String, Node> dimensions, MutablePropertyDataSet values) {
        Map<String, Object> props = new HashMap();
        NodeList nl = node.getChildNodes();
        MutablePropertyDataSet result = values;
        for (int i = 0; i < nl.getLength(); i++) {
            Node child = nl.item(i);
            if (child.getNodeName().equals("attribute")) {
                String attName= ((Attr) child.getAttributes().getNamedItem("name")).getValue();
                String attValue= ((Attr) child.getAttributes().getNamedItem("value")).getTextContent();
                if (attName.equals("units")) {
                    props.put(QDataSet.UNITS, lookupUnits(attValue));
                } else if (attName.equals("long_name")) { //TODO: this is really TITLE, according to COARDS
                    props.put( QDataSet.LABEL, attValue );
                } else if (attName.equals("title") ) {
                    props.put( QDataSet.TITLE, attValue );
                }
            } else if (child.getNodeName().equals("values")) {
                Node increment = child.getAttributes().getNamedItem("increment");
                Double scale = Double.parseDouble(increment.getTextContent());
                Node start = child.getAttributes().getNamedItem("start");
                Double offset = Double.parseDouble(start.getTextContent());
                int n = dimensionLength(dimensions.get(maybeGetAttr(node, "shape")));
                result = new TagGenDataSet(n, scale, offset);
            }
        }
        for (Entry<String, Object> e : props.entrySet()) {
            result.putProperty(e.getKey(), e.getValue());
        }
        result.putProperty(QDataSet.NAME, maybeGetAttr(node, "name"));
        result.putProperty("shape", maybeGetAttr(node, "shape")); // used for aggregation to identify independent parameter

        return result;
    }

    /**
     * read in values in a different location using the org.timeseries.tsds IOServiceProvider.
     * The location should be URLEncoded or XML-escaped (&amp;) in the ncml document.
     * iospParam should identify the type and filter of the data, e.g. "double,filter4".
     * Currently just filter4 and filter0 are supported.
     *
     * @param node
     * @return
     * @throws java.net.MalformedURLException
     * @throws java.io.IOException
     */
    protected MutablePropertyDataSet tsdsLocation(Node node) throws MalformedURLException, IOException {
        Map<String, Node> dims = new LinkedHashMap();
        Node variable = null;
        NodeList kids = node.getChildNodes();

        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n.getNodeName().equals("dimension")) {
                dims.put(maybeGetAttr(n, "name"), n);
            } else if (n.getNodeName().equals("variable")) {
                variable = n;
            }
        }

        String shape = maybeGetAttr(variable, "shape");
        String[] shapes = shape.split("[, ]");

        int len1 = -1;
        if (dims.size() > 1) {
            len1 = dimensionLength(dims.get(shapes[1]));
        }
        Object type = BufferDataSet.DOUBLE;

        int size = (len1 != -1 ? len1 : 1) * dimensionLength(dims.get(shapes[0])) * BufferDataSet.byteCount(type);

        String surl = maybeGetAttr(node, "location");
        if (surl.contains("%2F%2F")) surl = URLDecoder.decode(surl, "US-ASCII"); // location can be either URL-encoded or use &amp;

        String s = maybeGetAttr(node, "iospParam");
        List<String> iospParam = Collections.emptyList();
        if (s != null) {
            iospParam = Arrays.asList(s.split(","));
        }

        MutablePropertyDataSet values;
        if (iospParam.contains("filter4")) {
            int points = dimensionLength(dims.get(shapes[0])) / 3;
            BufferDataSet data3 = (BufferDataSet) tsds(new URL(codebase, surl), size, len1, type, new NullProgressMonitor());

            MutablePropertyDataSet data = (BufferDataSet)data3.trim(0, points);  // we know that BufferDataSet.trim returns another BufferDataSet.
            BufferDataSet dataMin = (BufferDataSet)data3.trim(RANK_LIMIT * points, 3 * points);
            dataMin.putProperty(QDataSet.NAME, "binmin");
            BufferDataSet dataMax = (BufferDataSet)data3.trim(1 * points, RANK_LIMIT * points);
            dataMax.putProperty(QDataSet.NAME, "binmax");
            data.putProperty(QDataSet.DELTA_PLUS, Ops.subtract(dataMax, data));
            data.putProperty(QDataSet.DELTA_MINUS, Ops.subtract(data, dataMin));
            values = data;
        } else {
            values = tsds(new URL(codebase, surl), size, len1, type, new NullProgressMonitor());
        }
        return variable(variable, dims, values);
    }

    /**
     * Read in the binary table from the server.  size is the total size of the tsds stream.
     * @param url url location of the data.
     * @param size the total size of the stream
     * @param len1 length per record if rank 2, -1 if rank 1.
     * @param type  BufferDataSet.Float, etc.
     * @param mon
     * @return
     */
    protected MutablePropertyDataSet tsds(URL url, int size, int len1, Object type, ProgressMonitor mon) throws IOException {

        URLConnection connection = url.openConnection();

        InputStream in = connection.getInputStream();
        String encoding = connection.getContentEncoding();
        logger.finer("downloading " + connection.getURL());
        if (encoding != null && encoding.equalsIgnoreCase("gzip")) {
            logger.finer("got gzip encoding");
            in = new GZIPInputStream(in);
        } else if (encoding != null && encoding.equalsIgnoreCase("deflate")) {
            logger.finer("got deflate encoding");
            in = new InflaterInputStream(in, new Inflater(true));
        }

        ReadableByteChannel bin = Channels.newChannel(in);
        
        logger.finer( String.format( Locale.US, "allocating space for dataset (%9.1f KB)",(size/1000.)));

        ByteBuffer bbuf = ByteBuffer.allocate(size);
        int totalBytesRead = 0;
        int bytesRead = bin.read(bbuf);

        mon.setTaskSize(size);

        while (bytesRead >= 0 && (bytesRead + totalBytesRead) < size) {
            totalBytesRead += bytesRead;
            bytesRead = bin.read(bbuf);
            if (mon.isCancelled()) {
                break;
            }
            mon.setTaskProgress(totalBytesRead);
        }

        //TODO: check to see if there is extra stuff at the end.
        
        in.close();

        bbuf.flip();
        bbuf.order(ByteOrder.LITTLE_ENDIAN);

        if (len1 == -1) {
            int points = bbuf.limit() / BufferDataSet.byteCount(type);
            return BufferDataSet.makeDataSet(1, BufferDataSet.byteCount(type), 0, points, 1, 1, 1, bbuf, type);
        } else {
            int points = bbuf.limit() / len1 / BufferDataSet.byteCount(type);
            return BufferDataSet.makeDataSet(RANK_LIMIT, len1 * BufferDataSet.byteCount(type), 0, points, len1, 1, 1, bbuf, type);
        }
    }
}
