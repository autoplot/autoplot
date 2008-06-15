/*
 * AutoplotUtil.java
 *
 * Created on April 1, 2007, 4:02 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import edu.uiowa.physics.pw.das.datum.Datum;
import edu.uiowa.physics.pw.das.datum.DatumRange;
import edu.uiowa.physics.pw.das.datum.DatumRangeUtil;
import edu.uiowa.physics.pw.das.datum.Units;
import edu.uiowa.physics.pw.das.datum.UnitsUtil;
import edu.uiowa.physics.pw.das.util.DasMath;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetIterator;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.WritableDataSet;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author jbf
 */
public class AutoplotUtil {

    private final static Logger log = Logger.getLogger("virbo.autoplot.AutoRangeDescriptor.autoRange");


    public static class AutoRangeDescriptor {

        DatumRange range;
        boolean log;
        double min;
        double max;
        double robustMin;
        double robustMax;
        double median;

        public String toString() {
            return "" + range + " " + (log ? "log" : "");
        }
    }

    /**
     * return indeces that order the data.  This is a bubble sort currently.
     * @param result an array of indeces indexing data.
     * untested.
     */
    private static int[] bubbleSort(double[] data, int[] result) {
        for (int i = 0; i < result.length - 1; i++) {
            for (int j = i + 1; j < result.length; j++) {
                if (data[result[i]] > data[result[j]]) {
                    int t = result[i];
                    result[j] = result[i];
                    result[i] = t;
                }
            }
        }
        return result;
    }

    /**
     * return the median of three numbers
     */
    private static double medianOfThree(double[] data) {
        double median;
        double max = Math.max(data[0], data[1]);
        if (data[2] > max) {
            median = max;
        } else { // data[2] is either the min or the median.  Either data[0] or data[1] is the max.
            if (data[0] == max) {
                median = Math.max(data[1], data[2]);  // implies data[0] is max
            } else {
                median = Math.max(data[0], data[2]);
            }
        }
        return median;
    }

    /**
     * return the min and max of 3-point medians
     * @param ds a rank N dataset.
     * @throws IllegalArgumentException if fewer than three points are found.
     */
    private static double[] median3Range(QDataSet ds) throws IllegalArgumentException {

        double[] d = new double[3]; // ordered points, rotating buffer
        int id; // index of the first point.
        int dc = 0; // number of valid points in d.

        Units u = (Units) ds.property(QDataSet.UNITS);

        int i;

        //TODO: use dataset iterator
        DataSetIterator iter = DataSetIterator.create(ds);

        while (iter.hasNext() && dc < 3) {
            double dd = iter.next();
            if (!Double.isNaN(dd) && (u == null || !u.isFill(dd))) {
                d[dc++] = dd;
            }
        }

        // TODO: find a compile-time exception for this error.
        if (dc < 3) {
            throw new IllegalArgumentException("need 3 valid points");
        }

        id = 0;

        double min = medianOfThree(d);
        double max = medianOfThree(d);
        while (iter.hasNext()) {
            double d1 = iter.next();

            if (!Double.isNaN(d1) && (u == null || !u.isFill(d1))) {
                d[id++] = d1;
                if (id == d.length) {
                    id = 0;
                }
                double median = medianOfThree(d);
                if (median < min) {
                    min = median;
                }
                if (median > max) {
                    max = median;
                }
            }
        }

        return new double[]{min, max};
    }

    public static AutoRangeDescriptor autoRange(QDataSet ds, Map properties) {

        log.fine("enter autoRange");

        Units u = (Units) ds.property(QDataSet.UNITS);
        if (u == null) {
            u = Units.dimensionless;
        }

        AutoRangeDescriptor result = new AutoRangeDescriptor();

        double[] dd;

        boolean mono = Boolean.TRUE.equals(ds.property(QDataSet.MONOTONIC));

        if (mono) {
            double cadence = DataSetUtil.guessCadence(ds);
            if (ds.length() > 1) {
                dd = new double[]{ds.value(0) - cadence, ds.value(ds.length() - 1) + cadence};
            } else {
                if (UnitsUtil.isTimeLocation(u)) {
                    dd = new double[]{0, Units.days.createDatum(1).doubleValue(u.getOffsetUnits())};
                } else {
                    dd = new double[]{0, 1};
                }
            }
        } else {
            // find min and max of three-point medians
            try {
                MomentDescriptor moment = moment(ds);
                dd = robustRange(ds, moment);
            // dd = median3Range(ds);
            } catch (IllegalArgumentException ex) {
                if (UnitsUtil.isTimeLocation(u)) {
                    dd = new double[]{0, Units.days.createDatum(1).doubleValue(u.getOffsetUnits())};
                } else {
                    dd = new double[]{0, 1};
                }
            }
        }

        double median;
        int total;
        if (dd[0] == dd[1]) {
            if (dd[0] == 0) {
                dd[0] = -1;
                dd[1] = +1;
            } else if (dd[0] > 0) {
                dd[0] = 0;
            } else {
                dd[1] = 0;
            }
            median = (dd[0] + dd[1]) / 2;
            total = ds.length(); // only non-zero is checked.
        } else {
            // find the median by looking at the histogram.  If the dataset should be log, then the data will bunch up in the lowest bins.
            QDataSet hist = DataSetOps.histogram(ds, dd[0], dd[1] + (dd[1] - dd[0]) * 0.01, (dd[1] - dd[0]) / 100);
            total = 0;
            for (int i = 0; i < hist.length(); i++) {
                total += hist.value(i);
            }
            median = u.getFillDouble();
            int total50 = 0;
            for (int i = 0; i < hist.length(); i++) {
                total50 += hist.value(i);
                if (total50 >= total / 2) {
                    median = ((QDataSet) hist.property(QDataSet.DEPEND_0)).value(i);
                    break;
                }
            }
        }

        if (total < 3) {
            result.min = dd[0];
            result.max = dd[1];
            result.range = DatumRange.newDatumRange(dd[0], dd[1], u);

        } else {
            result.min = dd[0];
            result.max = dd[1];
            result.median = median;
            result.robustMin = dd[0];
            result.robustMax = dd[1];

            // lin/log logic: in which space is ( median - min5 ) more equal to ( max5 - median )?  Also, max5 / min5 > 1e3
            double clin = (result.robustMax - result.median) / (result.median - result.robustMin);
            if (clin > 1.0) {
                clin = 1 / clin;
            }
            double clog = (result.robustMax / result.median) / (result.median / result.robustMin);
            if (clog > 1.0) {
                clog = 1 / clog;
            }

            if (clog > clin && result.robustMax / result.robustMin > 1e2) {
                result.log = true;
            }

            if (UnitsUtil.isRatioMeasurement(u) || UnitsUtil.isIntervalMeasurement(u)) {
                if (result.log) {
                    result.range = DatumRange.newDatumRange(DasMath.exp10(Math.floor(DasMath.log10(result.robustMin))),
                            DasMath.exp10(Math.ceil(DasMath.log10(result.robustMax))), u);
                } else {
                    result.range = DatumRangeUtil.rescale(DatumRange.newDatumRange(result.robustMin, result.robustMax, u), -0.05, 1.05);
                }
            } else {
                result.range = DatumRange.newDatumRange(result.robustMin, result.robustMax, u);
            }

        }

        if (properties != null) {
            String log = (String) properties.get(QDataSet.SCALE_TYPE);
            if (log != null) {
                result.log = log.equals("log");
            }
            DatumRange range = (DatumRange) properties.get(QDataSet.TYPICAL_RANGE);

            // see if the typical range is consistent with range seen.  If the
            // typical range won't hide the data's structure, then use it.
            if (range != null && range.getUnits().isConvertableTo(result.range.getUnits() )) {
                double d1, d2;
                if (result.log) {
                    Datum dd1= result.range.min().ge( range.min() ) ? result.range.min() : range.min();
                    Datum dd2= result.range.max().ge( range.min() ) ? result.range.max() : range.min();
                    d1 = DatumRangeUtil.normalizeLog(range, dd1 );
                    d2 = DatumRangeUtil.normalizeLog(range, dd2 );
                } else {
                    d1 = DatumRangeUtil.normalize(range, result.range.min());
                    d2 = DatumRangeUtil.normalize(range, result.range.max());
                }
                if ( d2-d1 > 0.1 ) {
                    result.range = range;
                }
            }
        }

        log.fine("exit autoRange");

        return result;
    }

    /**
     * open the URL in a browser.   Borrowed from http://www.centerkey.com/java/browser/.
     */
    public static void openBrowser(String url) {
        final String errMsg = "Error attempting to launch web browser";
        String osName = System.getProperty("os.name");
        try {
            if (osName.startsWith("Mac OS")) {
                Class fileMgr = Class.forName("com.apple.eio.FileManager");
                Method openURL = fileMgr.getDeclaredMethod("openURL", new Class[]{String.class});
                openURL.invoke(null, new Object[]{url});
            } else if (osName.startsWith("Windows")) {
                Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
            } else { //assume Unix or Linux
                String[] browsers = {"firefox", "opera", "konqueror", "epiphany", "mozilla", "netscape"};
                String browser = null;
                for (int count = 0; count < browsers.length && browser == null; count++) {
                    if (Runtime.getRuntime().exec(new String[]{"which", browsers[count]}).waitFor() == 0) {
                        browser = browsers[count];
                    }
                }
                if (browser == null) {
                    throw new Exception("Could not find web browser");
                } else {
                    Runtime.getRuntime().exec(new String[]{browser, url});
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, errMsg + ":\n" + e.getLocalizedMessage());
        }
    }

    public static class MomentDescriptor {

        double[] moment;
        Units units;
        int rank;
        int invalidCount;
        int validCount;
    }

    /**
     * performs the moment (mean,variance,etc) on the dataset.
     * @returns MomentDescriptor
     */
    public static MomentDescriptor moment(QDataSet ds) {

        MomentDescriptor result = new MomentDescriptor();

        result.rank = ds.rank();
        result.moment = new double[2];

        Units u = (Units) ds.property(QDataSet.UNITS);
        if (u == null) {
            u = Units.dimensionless;
        }
        int validCount = 0;
        int invalidCount = 0;

        double approxMean = 0.;

        DataSetIterator iter = DataSetIterator.create(ds);
        while (iter.hasNext()) {
            double d = iter.next();
            if (!u.isValid(d)) {
                invalidCount++;
            } else {
                validCount++;
                approxMean += d;
            }
        }

        if (validCount > 0) {
            approxMean /= validCount; // approximate--suseptible to number error.
        }

        result.invalidCount = invalidCount;
        result.validCount = validCount;

        double mean = 0;
        double stddev = 0;

        if (validCount > 0) {
            iter = DataSetIterator.create(ds);
            while (iter.hasNext()) {
                double d = iter.next();
                if (u.isValid(d)) {
                    mean += (d - approxMean);
                    stddev += Math.pow(d - approxMean, 2);
                }
            }

            mean /= validCount;
            mean += approxMean;

            result.moment[0] = mean;

            if (validCount > 1) {
                stddev /= (validCount - 1); // this will be very close to result, even though correction should be made since approxMean != mean.
                stddev = Math.sqrt(stddev);
                result.moment[1] = stddev;
            } else {
                result.moment[1] = u.getFillDouble();
            }

        } else {
            result.moment[0] = u.getFillDouble();
        }

        return result;
    }

    public static Document readDoc(InputStream is) throws SAXException, IOException, ParserConfigurationException {
        DocumentBuilder builder;
        builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        InputSource source = new InputSource(new InputStreamReader(is));
        Document document = builder.parse(source);

//        DOMParser parser = new org.apache.xerces.parsers.DOMParser();
//        
//        Reader in = new BufferedReader(new InputStreamReader(is));
//        InputSource input = new org.xml.sax.InputSource(in);
//        
//        parser.parse(input);
//        
//        Document document = parser.getDocument();
        return document;
    }

    private static double[] robustRange(QDataSet ds, MomentDescriptor moment) throws IllegalArgumentException {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;

        Units u = (Units) ds.property(QDataSet.UNITS);
        if (u == null) {
            u = Units.dimensionless;
        }

        DataSetIterator iter = DataSetIterator.create(ds);

        // four std dev's from the mean.
        double stdmin = moment.moment[0] - moment.moment[1] * 4;
        double stdmax = moment.moment[0] + moment.moment[1] * 4;

        while (iter.hasNext()) {
            double d = iter.next();
            if (u.isValid(d) && d >= stdmin && d <= stdmax) {
                min = min > d ? d : min;
                max = max < d ? d : max;
            }
        }

        if (min == Double.POSITIVE_INFINITY) {
            throw new IllegalArgumentException("no valid data found");
        } else {
            return new double[]{min, max};
        }
    }
    
    /**
     * rewrite the dataset so that fill values are set by the valid range and fill
     * controls.
     */
    public static WritableDataSet applyFillValidRange( QDataSet ds, double vmin, double vmax, double fill ) {
        WritableDataSet result = DDataSet.copy(ds);
        Units u = (Units) ds.property(QDataSet.UNITS);

        if (u == null) {
            u = Units.dimensionless;
        }

        if (ds.rank() == 1) {
            for (int i = 0; i < ds.length(); i++) {
                double d = ds.value(i);
                if (d == fill || d <= vmin || d >= vmax) {
                    result.putValue(i, u.getFillDouble());
                }
            }
        } else if (ds.rank() == 2) {
            for (int i0 = 0; i0 < ds.length(); i0++) {
                for (int i1 = 0; i1 < ds.length(i0); i1++) {
                    double d = ds.value(i0, i1);
                    if (d == fill || d <= vmin || d >= vmax) {
                        result.putValue(i0, i1, u.getFillDouble());
                    }
                }
            }
        } else {
            for (int i0 = 0; i0 < ds.length(); i0++) {
                for (int i1 = 0; i1 < ds.length(i0); i1++) {
                    for (int i2 = 0; i2 < ds.length(i0, i1); i2++) {
                        double d = ds.value(i0, i1, i2);
                        if (d == fill || d <= vmin || d >= vmax) {
                            result.putValue(i0, i1, i2, u.getFillDouble());
                        }
                    }
                }
            }
        }

        result.putProperty(QDataSet.UNITS, u);

        return result;
    }
    
    /**
     * extract the properties from the dataset into the same format as metadata model returns.
     * @param ds
     * @param spec
     * @return
     */
    public static Map<String,Object> extractProperties( QDataSet ds ) {
        
        Map<String,Object> result= DataSetUtil.getProperties(ds);

        Object v;
        
        for ( int i=0; i<4; i++  ) {
            final String key = "DEPEND_" + i;
            if ((v = ds.property(key)) != null) {
                result.put( key, extractProperties( (QDataSet)v ) ) ;
            }            
        }
        
        for ( int i=0; i<QDataSet.MAX_PLANE_COUNT; i++ ) {
            final String key = "PLANE_" + i;
            if ((v = ds.property(key)) != null) {
                result.put( key, extractProperties( (QDataSet)v ) ) ;
            }            
            
        }
        
        return result;
    }    
    
    /**
     * combine the two properties trees, using values from the first when both contain the same property.
     * @param properties
     * @param deflt
     * @return
     */
    public static Map<String,Object> mergeProperties( Map<String, Object> properties, Map<String,Object> deflt ) {
        if ( deflt==null ) return properties;
        HashMap<String,Object> result= new HashMap<String,Object>(deflt);
        for ( String key: properties.keySet() ) {
            Object val= properties.get(key);
            if ( val instanceof Map ) {
                result.put(key, mergeProperties( (Map<String,Object>)val, (Map<String,Object>)deflt.get(key) ) );
            } else {
                result.put(key, val);
            }
        }
        return result;
    }
    
}
