/*
 * AutoplotUtil.java
 *
 * Created on April 1, 2007, 4:02 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.logging.Level;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.InconvertibleUnitsException;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.graph.DasColumn;
import org.das2.util.DasMath;
import org.das2.util.PersistentStateSupport;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.das2.datum.EnumerationUnits;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasColorBar;
import org.das2.graph.DasDevicePosition;
import org.das2.graph.DasPlot;
import org.das2.graph.DasRow;
import org.das2.graph.ImageVectorDataSetRenderer;
import org.das2.graph.PsymConnector;
import org.das2.graph.Renderer;
import org.das2.graph.SeriesRenderer;
import org.das2.graph.SpectrogramRenderer;
import org.virbo.autoplot.ApplicationModel.RenderType;
import org.virbo.autoplot.bookmarks.Bookmark;
import org.virbo.dataset.DRank0DataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QubeDataSetIterator;
import org.virbo.dataset.RankZeroDataSet;
import org.virbo.dataset.TableDataSetAdapter;
import org.virbo.dataset.VectorDataSetAdapter;
import org.virbo.dsops.Ops;
import org.virbo.dsutil.AutoHistogram;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author jbf
 */
public class AutoplotUtil {

    private final static Logger log = Logger.getLogger("virbo.autoplot.AutoRangeDescriptor.autoRange");
    /**
     * absolute length limit for plots.  This is used to limit the elements used in autoranging, etc.
     */
    public final static int DS_LENGTH_LIMIT = 10000000;

    /**
     * this is not used.  It is called from createIcon, which is not used.
     * @param c
     * @param ds
     * @param recyclable
     * @param cb
     * @return
     */
    static DasPlot createPlot(DasCanvas c, QDataSet ds, DasPlot recyclable, DasColorBar cb) {
        DasRow row = DasRow.create(c);
        DasColumn col = DasColumn.create(c);
        DasPlot result;
        if (recyclable != null) {
            result = recyclable;
        } else {
            result = DasPlot.createDummyPlot();
        }
        List<Renderer> recycleRends = Arrays.asList(result.getRenderers());

        ApplicationModel.RenderType type = AutoplotUtil.getRenderType(ds);
        List<Renderer> rends = AutoplotUtil.getRenderers(ds, type, recycleRends, cb);

        for (Renderer rend1 : rends) {
            result.addRenderer(rend1);
        }

        c.add(result, row, col);
        c.revalidate();
        c.validate();
        System.err.println(c.getBounds());
        System.err.println(row);
        result.resize();
        System.err.println(result.getBounds());
        return result;
    }

    /**
     * creates an icon for the dataset.  Presently this just grabs the autoplot canvas image,
     * but it should really create a small canvas and let das2 reduce the data before
     * rendering it.
     * @param model
     * @param surl
     * @return
     */
    public static ImageIcon createIcon(ApplicationModel model, String surl) {
        try {
            QDataSet ds = org.virbo.jythonsupport.Util.getDataSet(surl);
            DasCanvas c = new DasCanvas(320, 320);
            c.setSize(320, 320);
            DasPlot p = AutoplotUtil.createPlot(c, ds, null, null);
            p.getRow().setMinimum(0);
            p.getRow().setMaximum(1);
            p.getColumn().setMinimum(0);
            p.getColumn().setMaximum(1);
            JFrame f = new JFrame();
            f.getContentPane().add(c);
            BufferedImage image = (BufferedImage) c.getImage(320, 320);
            return scaleIcon(new ImageIcon(image), 64, 48);
        } catch (Exception e) {
            BufferedImage image;
            try {
                image = ImageIO.read(AutoplotUtil.class.getResource("/org/virbo/autoplot/resources/error-icon.png"));
            } catch (IOException ex) {
                Logger.getLogger(AutoplotUtil.class.getName()).log(Level.SEVERE, null, ex);
                throw new RuntimeException(ex);
            }
            return new ImageIcon(image);
        }
    }

    /**
     * create a new Icon that is a scaled instance of the first.  The image
     * should be a BufferedImage.
     * @param icon
     * @param w
     * @param h
     * @return
     */
    public static ImageIcon scaleIcon(ImageIcon icon, int w, int h) {
        double aspect = icon.getIconHeight() / (double) icon.getIconWidth();
        if (h == -1) {
            h = (int) (w * aspect);
        } else if (w == -1) {
            w = (int) (h / aspect);
        }
        BufferedImage image = (BufferedImage) icon.getImage();
        return new ImageIcon(scaleImage(image, w, h));
    }

    public static BufferedImage scaleImage(BufferedImage image, int w, int h) {
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = (Graphics2D) result.getGraphics();
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setTransform(AffineTransform.getScaleInstance(w / (double) image.getWidth(), h / (double) image.getHeight()));
        g2.drawImage(image, 0, 0, null);
        return result;
    }

    public static List<String> getUrls(List<Bookmark> recent) {
        List<String> urls = new ArrayList<String>(recent.size());

        for (Bookmark b : recent) {
            if (b instanceof Bookmark.Item) {
                urls.add(((Bookmark.Item) b).getUrl());
            }
        }
        return urls;
    }

    public static class AutoRangeDescriptor {

        public DatumRange range;
        public boolean log;
        double robustMin;
        double robustMax;
        double median;

        public String toString() {
            return "" + range + " " + (log ? "log" : "");
        }
    }

    private static DatumRange getRange(Double min, Double max, Units units) {
        if (units != null && UnitsUtil.isTimeLocation(units)) {
            if (min == null) min = Units.mj1958.convertDoubleTo(units, -100000);
            if (max == null) max = Units.mj1958.convertDoubleTo(units, 100000);
        } else {
            if (min == null) min = Double.NEGATIVE_INFINITY;
            if (max == null) max = Double.POSITIVE_INFINITY;
            if (units == null) units = Units.dimensionless;
        }
        return new DatumRange(min, max, units);
    }

    private static DatumRange makeDimensionless(DatumRange dr) {
        Units u = dr.getUnits();
        return new DatumRange(dr.min().doubleValue(u),
                dr.max().doubleValue(u),
                Units.dimensionless);
    }

    public static AutoRangeDescriptor autoRange(QDataSet hist, QDataSet ds, Map properties) {

        log.fine("enter autoRange "+ds );

        Units u = (Units) ds.property(QDataSet.UNITS);
        if (u == null) {
            u = Units.dimensionless;
        }

        AutoRangeDescriptor result = new AutoRangeDescriptor();

        double[] dd;

        boolean mono = Boolean.TRUE.equals(ds.property(QDataSet.MONOTONIC)) || null != ds.property(QDataSet.CADENCE);

        long total = (Long) ((Map) hist.property(QDataSet.USER_PROPERTIES)).get(AutoHistogram.USER_PROP_TOTAL);

        double median = Double.NaN;

        if (mono) {
            RankZeroDataSet cadence = DataSetUtil.guessCadenceNew(ds, null);
            if (cadence == null || cadence.value() > Double.MAX_VALUE / 100)
                cadence = DRank0DataSet.create(0.);
            if (ds.length() > 1) {
                double min = Math.min(ds.value(0), ds.value(ds.length() - 1));
                double max = Math.max(ds.value(0), ds.value(ds.length() - 1));
                double dcadence = Math.abs(cadence.value());
                if ("log".equals(cadence.property(QDataSet.SCALE_TYPE))) {
                    Units cu = (Units) cadence.property(QDataSet.UNITS);
                    double factor = (cu.convertDoubleTo(Units.percentIncrease, dcadence) + 100) / 100.;
                    dd = new double[]{min / factor, max * factor};
                } else {
                    dd = new double[]{min - dcadence, max + dcadence};
                }
            } else {
                if (UnitsUtil.isTimeLocation(u)) {
                    dd = new double[]{0, Units.days.createDatum(1).doubleValue(u.getOffsetUnits())};
                } else {
                    dd = new double[]{0, 1};
                }
            }
            median = (dd[0] + dd[1]) / 2;
        } else {
            dd = new double[]{Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
            QDataSet dep0 = (QDataSet) hist.property(QDataSet.DEPEND_0);
            QDataSet cadence = DataSetUtil.guessCadenceNew(dep0, null);
            int tot = 0;
            for (int i = 0; i < hist.length(); i++) {
                tot += hist.value(i);
                if (dd[0] == Double.NEGATIVE_INFINITY && hist.value(i) > 0) {
                    dd[0] = dep0.value(i);
                }
                if (hist.value(i) > 0) {
                    dd[1] = dep0.value(i) + cadence.value();  // TODO: log10
                }
                if (tot >= total / 2) {
                    median = dep0.value(i);
                }
            }
        }

        if (total < 3) {
            result.median = median;
            result.range = DatumRange.newDatumRange(dd[0], dd[1], u);
            result.robustMin = dd[0];
            result.robustMax = dd[1];

        } else {
            result.median = median;
            result.robustMin = dd[0];
            result.robustMax = dd[1];

            double nomMin, nomMax;
            if (mono) {
                nomMin = ds.value(0);
                nomMax = ds.value(ds.length() - 1);
            } else {
                nomMin = dd[0];
                nomMax = dd[1];
            }

            // lin/log logic: in which space is ( median - min5 ) more equal to ( max5 - median )?  Also, max5 / min5 > 1e3
            double clin = (nomMax - result.median) / (result.median - nomMin);
            if (clin > 1.0) {
                clin = 1 / clin;
            }
            double clog = (nomMax / result.median) / Math.abs(result.median / nomMin);
            if (clog > 1.0) {
                clog = 1 / clog;
            }

            if (clog > clin && nomMax / nomMin > 1e2) {
                result.log = true;
            }

            result.range = DatumRange.newDatumRange(result.robustMin, result.robustMax, u);

        }

        if ("log".equals(ds.property(QDataSet.SCALE_TYPE))) {
            result.log = true;
        }

        // interpret properties, looking for hints about scale type and ranges.
        if (properties != null) {
            String log1 = (String) properties.get(QDataSet.SCALE_TYPE);
            if (log1 != null) {
                result.log = log1.equals("log");
            }
            Double tmin = (Double) properties.get(QDataSet.TYPICAL_MIN);
            Double tmax = (Double) properties.get(QDataSet.TYPICAL_MAX);
            DatumRange range = getRange(
                    (Double) properties.get(QDataSet.TYPICAL_MIN),
                    (Double) properties.get(QDataSet.TYPICAL_MAX),
                    (Units) properties.get(QDataSet.UNITS));
            // see if the typical extent is consistent with extent seen.  If the
            // typical extent won't hide the data's structure, then use it.
            if ((tmin != null || tmax != null)) {
                double d1, d2;
                if (result.log) {
                    try {
                        Datum dd1 = result.range.min().ge(range.min()) ? result.range.min() : range.min();
                        Datum dd2 = result.range.max().ge(range.min()) ? result.range.max() : range.min();
                        d1 = DatumRangeUtil.normalizeLog(range, dd1);
                        d2 = DatumRangeUtil.normalizeLog(range, dd2);
                    } catch (InconvertibleUnitsException ex) {
                        range = makeDimensionless(range);
                        result.range = makeDimensionless(result.range);
                        Datum dd1 = result.range.min().ge(range.min()) ? result.range.min() : range.min();
                        Datum dd2 = result.range.max().ge(range.min()) ? result.range.max() : range.min();
                        d1 = DatumRangeUtil.normalizeLog(range, dd1);
                        d2 = DatumRangeUtil.normalizeLog(range, dd2);
                    }
                } else {
                    try {
                        d1 = DatumRangeUtil.normalize(range, result.range.min());
                        d2 = DatumRangeUtil.normalize(range, result.range.max());
                    } catch (InconvertibleUnitsException ex) {
                        range = makeDimensionless(range);
                        result.range = makeDimensionless(result.range);
                        d1 = DatumRangeUtil.normalize(range, result.range.min());
                        d2 = DatumRangeUtil.normalize(range, result.range.max());
                    }
                }
                if (d2 - d1 > 0.1 // the stats range occupies 10% of the typical range
                        && d2 > 0. // and the stats max is greater than the typical range min()
                        && d1 < 1.) {  // and the stats min is less then the typical range max().
                    result.range = range;
                    // just use the metadata settings.

                    return result; // DANGER--EXIT POINT

                }
            }

        }
        
         // round out to frame the data with empty space, so that the data extent is known.
        if (UnitsUtil.isRatioMeasurement(u) || UnitsUtil.isIntervalMeasurement(u)) {
            if (result.log) {
                if (result.robustMin <= 0.0)
                    result.robustMin = result.robustMax / 1e3;
                result.range = DatumRange.newDatumRange(DasMath.exp10(Math.floor(DasMath.log10(result.robustMin))),
                        DasMath.exp10(Math.ceil(DasMath.log10(result.robustMax))), u);
            } else {
                result.range = DatumRange.newDatumRange(result.robustMin, result.robustMax, u);
                if (result.robustMin < result.robustMax)
                    result.range = DatumRangeUtil.rescale(result.range, -0.05, 1.05);
                if (result.robustMin == 0 && result.robustMax == 0)
                    result.range = DatumRange.newDatumRange(-0.1, 1.0, u);
            }
        } else {
            result.range = DatumRange.newDatumRange(result.robustMin, result.robustMax, u);
        }

        log.fine("exit autoRange");

        return result;
    }

    /**
     * This early implementation of autoRange calculates the range of the
     * data, then locates the median to establish a linear or log scale type.
     * Very early on it tried to establish a robust range as well that would
     * exclude outliers.
     *
     * This should be rewritten to use the recently-implemented AutoHistogram,
     * which does an efficient, self-configuring, one-pass histogram of the data
     * that more effectively identifies the data range and outliers.
     *
     * @param ds
     * @param properties
     * @return
     */
    public static AutoRangeDescriptor autoRange(QDataSet ds, Map properties) {

        log.fine("enter autoRange "+ds);

        Units u = (Units) ds.property(QDataSet.UNITS);
        if (u == null) {
            u = Units.dimensionless;
        }

        AutoRangeDescriptor result = new AutoRangeDescriptor();

        double[] dd;

        boolean mono = Boolean.TRUE.equals(ds.property(QDataSet.MONOTONIC)) || null != ds.property(QDataSet.CADENCE);

        if (mono) {
            RankZeroDataSet cadence = DataSetUtil.guessCadenceNew(ds, null);
            QDataSet wds= DataSetUtil.weightsDataSet(ds);
            if (cadence == null || cadence.value() > Double.MAX_VALUE / 100)
                cadence = DRank0DataSet.create(0.);
            if (ds.length() > 1) {
                int firstValid=0;
                while ( firstValid<wds.length() && wds.value(firstValid)==0 ) firstValid++;
                int lastValid=wds.length()-1;
                while ( lastValid>=0 && wds.value(lastValid)==0 ) lastValid--;
                double min = Math.min(ds.value(firstValid), ds.value(lastValid));
                double max = Math.max(ds.value(firstValid), ds.value(lastValid));
                double dcadence = Math.abs(cadence.value());
                if ("log".equals(cadence.property(QDataSet.SCALE_TYPE))) {
                    Units cu = (Units) cadence.property(QDataSet.UNITS);
                    double factor = (cu.convertDoubleTo(Units.percentIncrease, dcadence) + 100) / 100.;
                    dd = new double[]{min / factor, max * factor};
                } else {
                    dd = new double[]{min - dcadence, max + dcadence};
                }
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
                dd = simpleRange(ds);
                if ( Units.dimensionless.isFill(dd[0]) ) dd[0]= dd[0] / 10; // kludge for LANL_1991_080_H0_SOPA_ESP_19920308_V01.cdf?FEDO
                if ( Units.dimensionless.isFill(dd[1]) ) dd[1]= dd[1] / 10;
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
            result.median = median;
            result.range = DatumRange.newDatumRange(dd[0], dd[1], u);
            result.robustMin = dd[0];
            result.robustMax = dd[1];

        } else {
            result.median = median;
            result.robustMin = dd[0];
            result.robustMax = dd[1];

            double nomMin, nomMax;
            if (mono) {
                nomMin = ds.value(0);
                nomMax = ds.value(ds.length() - 1);
            } else {
                nomMin = dd[0];
                nomMax = dd[1];
            }

            // lin/log logic: in which space is ( median - min5 ) more equal to ( max5 - median )?  Also, max5 / min5 > 1e3
            double clin = (nomMax - result.median) / (result.median - nomMin);
            if (clin > 1.0) {
                clin = 1 / clin;
            }
            double clog = (nomMax / result.median) / Math.abs(result.median / nomMin);
            if (clog > 1.0) {
                clog = 1 / clog;
            }

            if (clog > clin && nomMax / nomMin > 1e2) {
                result.log = true;
            }

            result.range = DatumRange.newDatumRange(result.robustMin, result.robustMax, u);

        }

        if ("log".equals(ds.property(QDataSet.SCALE_TYPE))) {
            result.log = true;
        }

        // interpret properties, looking for hints about scale type and ranges.
        if (properties != null) {
            String log1 = (String) properties.get(QDataSet.SCALE_TYPE);
            if (log1 != null) {
                result.log = log1.equals("log");
            }
            Double tmin = (Double) properties.get(QDataSet.TYPICAL_MIN);
            Double tmax = (Double) properties.get(QDataSet.TYPICAL_MAX);
            DatumRange range = getRange(
                    (Double) properties.get(QDataSet.TYPICAL_MIN),
                    (Double) properties.get(QDataSet.TYPICAL_MAX),
                    (Units) properties.get(QDataSet.UNITS));
            // see if the typical extent is consistent with extent seen.  If the
            // typical extent won't hide the data's structure, then use it.
            if ((tmin != null || tmax != null)) {
                double d1, d2;
                if (result.log) {
                    try {
                        Datum dd1 = result.range.min().ge(range.min()) ? result.range.min() : range.min();
                        Datum dd2 = result.range.max().ge(range.min()) ? result.range.max() : range.min();
                        d1 = DatumRangeUtil.normalizeLog(range, dd1);
                        d2 = DatumRangeUtil.normalizeLog(range, dd2);
                    } catch (InconvertibleUnitsException ex) {
                        range = makeDimensionless(range);
                        result.range = makeDimensionless(result.range);
                        Datum dd1 = result.range.min().ge(range.min()) ? result.range.min() : range.min();
                        Datum dd2 = result.range.max().ge(range.min()) ? result.range.max() : range.min();
                        d1 = DatumRangeUtil.normalizeLog(range, dd1);
                        d2 = DatumRangeUtil.normalizeLog(range, dd2);
                    }
                } else {
                    try {
                        d1 = DatumRangeUtil.normalize(range, result.range.min());
                        d2 = DatumRangeUtil.normalize(range, result.range.max());
                    } catch (InconvertibleUnitsException ex) {
                        range = makeDimensionless(range);
                        result.range = makeDimensionless(result.range);
                        d1 = DatumRangeUtil.normalize(range, result.range.min());
                        d2 = DatumRangeUtil.normalize(range, result.range.max());
                    }
                }
                if (d2 - d1 > 0.1 // the stats range occupies 10% of the typical range
                        && d2 > 0. // and the stats max is greater than the typical range min()
                        && d1 < 1.) {  // and the stats min is less then the typical range max().
                    result.range = range;
                    // just use the metadata settings.

                    return result; // DANGER--EXIT POINT

                }
            }
        }

        // round out to frame the data with empty space, so that the data extent is known.
        if (UnitsUtil.isRatioMeasurement(u) || UnitsUtil.isIntervalMeasurement(u)) {
            if (result.log) {
                if (result.robustMin <= 0.0)
                    result.robustMin = result.robustMax / 1e3;
                result.range = DatumRange.newDatumRange(DasMath.exp10(Math.floor(DasMath.log10(result.robustMin))),
                        DasMath.exp10(Math.ceil(DasMath.log10(result.robustMax))), u);
            } else {
                result.range = DatumRange.newDatumRange(result.robustMin, result.robustMax, u);
                if (result.robustMin < result.robustMax)
                    result.range = DatumRangeUtil.rescale(result.range, -0.05, 1.05);
                if (result.robustMin == 0 && result.robustMax == 0)
                    result.range = DatumRange.newDatumRange(-0.1, 1.0, u);
            }
        } else {
            result.range = DatumRange.newDatumRange(result.robustMin, result.robustMax, u);
        }

        log.fine("exit autoRange");

        return result;
    }

    /**
     * open the URL in a browser.   Borrowed from http://www.centerkey.com/java/browser/.
     */
    public static void openBrowser(String url) {
        final String errMsg = "Error attempting to launch web browser";
        String osName = AutoplotUtil.getProperty("os.name", "applet");
        try {
            if (osName.startsWith("Mac OS")) {
                Class fileMgr = Class.forName("com.apple.eio.FileManager");
                Method openURL = fileMgr.getDeclaredMethod("openURL", new Class[]{String.class});
                openURL.invoke(null, new Object[]{url});
            } else if (osName.startsWith("Windows")) {
                Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
            } else if (osName.equals("applet")) {
                throw new RuntimeException("applets can't start browser yet");

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

    /**
     * return simple extent by only including points consistent with adjacent points.
     * also considers delta_plus, delta_minus properties.
     * @param ds rank N dataset
     * @return double[min,max].
     */
    private static double[] simpleRange(QDataSet ds) {
        QDataSet max = ds;
        QDataSet min = ds;
        Units u = (Units) ds.property(QDataSet.UNITS);
        if (u == null) u = Units.dimensionless;

        QDataSet delta;
        delta = (QDataSet) ds.property(QDataSet.DELTA_PLUS);
        if (delta != null) {
            max = Ops.add(ds, delta);
        }

        delta = (QDataSet) ds.property(QDataSet.DELTA_MINUS);
        if (delta != null) {
            min = Ops.subtract(ds, delta);
        }

        QDataSet wmin = DataSetUtil.weightsDataSet(min);
        QDataSet wmax = DataSetUtil.weightsDataSet(max);
        QubeDataSetIterator it = new QubeDataSetIterator(ds);
        double[] result = new double[]{Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        int i = 0;
        while (i < DS_LENGTH_LIMIT && it.hasNext()) {
            it.next();
            i++;
            if (it.getValue(wmin) > 0.)
                result[0] = Math.min(result[0], it.getValue(min));
            if (it.getValue(wmax) > 0.)
                result[1] = Math.max(result[1], it.getValue(max));
        }

        if (result[0] == Double.POSITIVE_INFINITY) {  // no valid data!
            if (UnitsUtil.isTimeLocation(u)) {
                result[0] = Units.t2000.convertDoubleTo(u, 0.);
                result[1] = Units.t2000.convertDoubleTo(u, 86400); // avoid bug where rounding error in formatting of newDatumRange(0,1,t2000) resulted in invalid datm
            } else {
                result[0] = 0.;
                result[1] = 1.;
            }
        }
        return result;
    }

    /**
     * Rewrite the dataset so that fill values are set by the valid extent and fill
     * controls.  The user can override these values, so make sure the values that came
     * with the dataset are observed as well.
     *
     * Old values of vmin, vmax, and fill are ignored.
     * 
     */
    public static void applyFillValidRange(MutablePropertyDataSet ds, double vmin, double vmax, double fill) {

        Double ovmin = (Double) ds.property(QDataSet.VALID_MIN);
        Double ovmax = (Double) ds.property(QDataSet.VALID_MAX);

        boolean needToCopy = false;
        // if the old valid range contains the new range, then we simply reset the range.
        if (ovmax != null && ovmax < vmax) needToCopy = true;
        if (ovmin != null && ovmin > vmin) needToCopy = true;

        Double oldFill = (Double) ds.property(QDataSet.FILL_VALUE);

        if (oldFill != null && Double.isNaN(fill) == false && oldFill != fill)
            needToCopy = true;

        // always clobber old fill values.  This allows for fill data itself to be plotted.
        needToCopy = false;

        if (needToCopy == false) {
            if (vmin > (-1 * Double.MAX_VALUE))
                ds.putProperty(QDataSet.VALID_MIN, vmin);
            if (vmax < Double.MAX_VALUE)
                ds.putProperty(QDataSet.VALID_MAX, vmax);
            if (!Double.isNaN(fill)) ds.putProperty(QDataSet.FILL_VALUE, fill);

        } else {
            /*Units u = (Units) ds.property(QDataSet.UNITS);

            if (u == null) {
            u = Units.dimensionless;
            }

            QubeDataSetIterator it= new QubeDataSetIterator(ds);
            while ( it.hasNext() ) {
            it.next();
            double d = it.getValue(ds);
            if (d == fill || d < vmin || d > vmax) {
            it.putValue(ds, u.getFillDouble());
            }
            }

            ds.putProperty(QDataSet.UNITS, u);*/
        }
    }

    /**
     * extract the properties from the dataset into the same format as metadata model returns.
     * @param ds
     * @param spec
     * @return
     */
    public static Map<String, Object> extractProperties(QDataSet ds) {

        Map<String, Object> result = DataSetUtil.getProperties(ds);

        Object v;

        for (int i = 0; i < 4; i++) {
            final String key = "DEPEND_" + i;
            if ((v = ds.property(key)) != null) {
                result.put(key, extractProperties((QDataSet) v));
            }
        }

        for (int i = 0; i < QDataSet.MAX_PLANE_COUNT; i++) {
            final String key = "PLANE_" + i;
            if ((v = ds.property(key)) != null) {
                result.put(key, extractProperties((QDataSet) v));
            } else {
                break;
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
    public static Map<String, Object> mergeProperties(Map<String, Object> properties, Map<String, Object> deflt) {
        if (deflt == null)
            return properties;
        HashMap<String, Object> result = new HashMap<String, Object>(deflt);
        for (Entry<String, Object> entry : properties.entrySet()) {
            Object val = entry.getValue();
            String key = entry.getKey();
            if (val instanceof Map) {
                result.put(key, mergeProperties((Map<String, Object>) val, (Map<String, Object>) deflt.get(key)));
            } else {
                result.put(key, val);
            }
        }
        return result;
    }

    public static PersistentStateSupport getPersistentStateSupport(final AutoPlotUI parent, final ApplicationModel applicationModel) {
        final PersistentStateSupport stateSupport = new PersistentStateSupport(parent, null, "vap") {

            protected void saveImpl(File f) throws IOException {
                applicationModel.doSave(f);
                applicationModel.addRecent(f.toURI().toString());
                parent.setStatus("saved " + f);
            }

            protected void openImpl(final File file) throws IOException {
                applicationModel.doOpen(file);
                parent.setStatus("opened " + file);
            }
        };

        stateSupport.addPropertyChangeListener(new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent ev) {
                String label;
                if (stateSupport.isCurrentFileOpened()) {
                    label = stateSupport.getCurrentFile() + " " + (stateSupport.isDirty() ? "*" : "");
                    parent.setMessage(label);
                }
            }
        });

        return stateSupport;
    }

    /**
     * support restricted security environment by checking permissions before 
     * checking property.
     * @param name
     * @param deft
     * @return
     */
    public static String getProperty(String name, String deft) {
        try {
            return System.getProperty(name, deft);
        } catch (SecurityException ex) {
            return deft;
        }
    }

    /**
     * set the device position, using spec string like "+5em,80%-5em"
     */
    public static void setDevicePosition(DasDevicePosition row, String spec) throws ParseException {
        int i = spec.indexOf(",");
        if (i == -1)
            throw new IllegalArgumentException("spec must contain one comma");
        double[] ddmin = DasDevicePosition.parseFormatStr(spec.substring(0, i));
        double[] ddmax = DasDevicePosition.parseFormatStr(spec.substring(i + 1));
        row.setMinimum(ddmin[0]);
        row.setEmMinimum(ddmin[1]);
        row.setPtMinimum((int) ddmin[2]);
        row.setMaximum(ddmax[0]);
        row.setEmMaximum(ddmax[1]);
        row.setPtMaximum((int) ddmax[2]);
    }

    public static String formatDevicePosition(DasDevicePosition pos) {
        return DasDevicePosition.formatLayoutStr(pos, true) + ", " + DasDevicePosition.formatLayoutStr(pos, false);
    }

    private static boolean isVectorOrBundleIndex(QDataSet dep1) {
        boolean result = false;
        Units dep1Units = (Units) dep1.property(QDataSet.UNITS);
        if (dep1Units != null && dep1Units instanceof EnumerationUnits) {
            result = true;
        }

        if (dep1.property(QDataSet.COORDINATE_FRAME) != null) {
            result = true;
        }

        return result;
    }

    public static RenderType getRenderType(QDataSet fillds) {
        RenderType spec;

        QDataSet dep1 = (QDataSet) fillds.property(QDataSet.DEPEND_1);
        QDataSet plane0 = (QDataSet) fillds.property(QDataSet.PLANE_0);

        if (fillds.rank() >= 2) {
            if (dep1 != null && isVectorOrBundleIndex(dep1)) {
                if (fillds.length() > 200000) {
                    spec = RenderType.hugeScatter;
                } else {
                    spec = RenderType.series;
                }
            } else {
                spec = RenderType.spectrogram;
            }
        } else {
            if (fillds.length() > 200000) {
                spec = RenderType.hugeScatter;
            } else {
                spec = RenderType.series;
            }

            if (plane0 != null) {
                Units u = (Units) plane0.property(QDataSet.UNITS);
                if (u != null && (UnitsUtil.isRatioMeasurement(u) || UnitsUtil.isIntervalMeasurement(u))) {
                    spec = RenderType.colorScatter;
                }
            }
        }

        return spec;
    }

    /**
     * return a renderer that is configured for this renderType.
     * @param renderType
     * @param recyclable
     * @param colorbar
     * @return
     */
    public static Renderer maybeCreateRenderer(RenderType renderType,
            Renderer recyclable, DasColorBar colorbar) {
        if (renderType == RenderType.spectrogram) {
            if (recyclable != null && recyclable instanceof SpectrogramRenderer) {
                return recyclable;
            } else {
                Renderer result = new SpectrogramRenderer(null, colorbar);
                result.setDataSetLoader(null);
                colorbar.setVisible(true);
                return result;
            }
        } else if (renderType == RenderType.hugeScatter) {
            if (recyclable != null && recyclable instanceof ImageVectorDataSetRenderer) {
                return recyclable;
            } else {
                Renderer result = new ImageVectorDataSetRenderer(null);
                result.setDataSetLoader(null);
                colorbar.setVisible(false);
                return result;
            }
        } else {
            SeriesRenderer result;
            if (recyclable != null && recyclable instanceof SeriesRenderer) {
                result = (SeriesRenderer) recyclable;
            } else {
                result = new SeriesRenderer();
                result.setDataSetLoader(null);
            }

            if (renderType == RenderType.colorScatter) {
                result.setColorBar(colorbar);
                result.setColorByDataSetId(QDataSet.PLANE_0); //schema
                colorbar.setVisible(true);
            } else {
                result.setColorByDataSetId(""); //schema
                colorbar.setVisible(false);
            }

            if (renderType == RenderType.series) {
                result.setPsymConnector(PsymConnector.SOLID);
                result.setHistogram(false);
                result.setFillToReference(false);

            } else if (renderType == RenderType.scatter) {
                result.setPsymConnector(PsymConnector.NONE);
                result.setFillToReference(false);

            } else if (renderType == RenderType.colorScatter) {
                result.setPsymConnector(PsymConnector.NONE);
                result.setFillToReference(false);

            } else if (renderType == RenderType.stairSteps) {
                result.setPsymConnector(PsymConnector.SOLID);
                result.setFillToReference(true);
                result.setHistogram(true);

            } else if (renderType == RenderType.fillToZero) {
                result.setPsymConnector(PsymConnector.SOLID);
                result.setFillToReference(true);
                result.setHistogram(false);

            }

            return result;
        }

    }

    /**
     * return the renderers that should be used to render the data.  More than one renderer can be returned 
     * to support plotting vector components.  This is not used.
     * 
     * The renderer will have the dataset set.
     * @param ds
     * @param type
     * @param recyclable Reuse these if possible to reduce jitter.  May be null.
     * @return
     */
    public static List<Renderer> getRenderers(QDataSet ds, RenderType renderType,
            List<Renderer> recyclable, DasColorBar colorbar) {
        if (recyclable == null) recyclable = Collections.emptyList();
        if (renderType == RenderType.spectrogram) {
            if (recyclable != null && recyclable.size() == 1 && recyclable.get(0) instanceof SpectrogramRenderer) {
                recyclable.get(0).setDataSet(TableDataSetAdapter.create(ds));
                return recyclable;
            } else {
                Renderer result = new SpectrogramRenderer(null, colorbar);
                result.setDataSetLoader(null);
                colorbar.setVisible(true);
                result.setDataSet(TableDataSetAdapter.create(ds));
                return Collections.singletonList(result);
            }
        } else {
            List<Renderer> result;
            if (ds.rank() == 1) {
                SeriesRenderer result1;
                if (recyclable != null && recyclable.size() == 1 && recyclable.get(0) instanceof SeriesRenderer) {
                    result = recyclable;
                    result1 = (SeriesRenderer) result.get(0);
                } else {
                    result1 = new SeriesRenderer();
                    result1.setDataSetLoader(null);
                    result = Collections.singletonList((Renderer) result1);
                }
                result.get(0).setDataSet(VectorDataSetAdapter.create(ds));

                if (renderType == RenderType.colorScatter) {
                    result1.setColorBar(colorbar);
                    result1.setColorByDataSetId(QDataSet.PLANE_0); //schema
                    colorbar.setVisible(true);
                } else {
                    result1.setColorByDataSetId(""); //schema
                    colorbar.setVisible(false);
                }
            } else {
                int dim = ds.length(0);
                Color color = Color.black; // TODO: this will change.
                result = new ArrayList<Renderer>();
                for (int i = 0; i < dim; i++) {
                    SeriesRenderer rend1 = new SeriesRenderer();
                    rend1.setDataSetLoader(null);
                    float[] colorHSV = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
                    if (colorHSV[2] < 0.7f) {
                        colorHSV[2] = 0.7f;
                    }
                    if (colorHSV[1] < 0.7f) {
                        colorHSV[1] = 0.7f;
                    }
                    rend1.setColor(Color.getHSBColor(i / 6.f, colorHSV[1], colorHSV[2]));
                    rend1.setFillColor(Color.getHSBColor(i / 6.f, colorHSV[1], colorHSV[2]));
                    rend1.setDataSet(VectorDataSetAdapter.create(DataSetOps.slice1(ds, i)));
                    result.add(rend1);
                }
                colorbar.setVisible(false);
            }

            for (Renderer rend1 : result) {
                SeriesRenderer seriesRend = (SeriesRenderer) rend1;
                if (renderType == RenderType.series) {

                    seriesRend.setPsymConnector(PsymConnector.SOLID);
                    seriesRend.setHistogram(false);
                    seriesRend.setFillToReference(false);

                } else if (renderType == RenderType.scatter) {
                    seriesRend.setPsymConnector(PsymConnector.NONE);
                    seriesRend.setFillToReference(false);

                } else if (renderType == RenderType.colorScatter) {
                    seriesRend.setPsymConnector(PsymConnector.NONE);
                    seriesRend.setFillToReference(false);

                } else if (renderType == RenderType.stairSteps) {
                    seriesRend.setPsymConnector(PsymConnector.SOLID);
                    seriesRend.setFillToReference(true);
                    seriesRend.setHistogram(true);

                } else if (renderType == RenderType.fillToZero) {
                    seriesRend.setPsymConnector(PsymConnector.SOLID);
                    seriesRend.setFillToReference(true);
                    seriesRend.setHistogram(false);

                }
            }
            return result;
        }
    }
}
