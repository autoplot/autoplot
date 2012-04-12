/*
 * AutoplotUtil.java
 *
 * Created on April 1, 2007, 4:02 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.HeadlessException;
import java.awt.Image;
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
import java.util.prefs.Preferences;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JRootPane;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.das2.datum.DomainDivider;
import org.das2.datum.DomainDividerUtil;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.TimeLocationUnits;
import org.das2.datum.TimeUtil;
import org.das2.datum.UnitsConverter;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasColorBar;
import org.das2.graph.DasDevicePosition;
import org.das2.graph.DasPlot;
import org.das2.graph.DasRow;
import org.das2.graph.DefaultPlotSymbol;
import org.das2.graph.DigitalRenderer;
import org.das2.graph.EventsRenderer;
import org.das2.graph.ImageVectorDataSetRenderer;
import org.das2.graph.PitchAngleDistributionRenderer;
import org.das2.graph.PsymConnector;
import org.das2.graph.RGBImageRenderer;
import org.das2.graph.Renderer;
import org.das2.graph.SeriesRenderer;
import org.das2.graph.SpectrogramRenderer;
import org.das2.graph.VectorPlot;
import org.virbo.autoplot.bookmarks.Bookmark;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.Axis;
import org.virbo.autoplot.dom.DataSourceFilter;
import org.virbo.autoplot.dom.Options;
import org.virbo.autoplot.dom.OptionsPrefsController;
import org.virbo.autoplot.dom.Plot;
import org.virbo.autoplot.dom.PlotElement;
import org.virbo.autoplot.dom.PlotElementController;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DRank0DataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QubeDataSetIterator;
import org.virbo.dataset.RankZeroDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.URISplit;
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

    private static final Logger logger= Logger.getLogger( "virbo.autoplot.AutoplotUtil" );
    
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

        RenderType type = AutoplotUtil.guessRenderType(ds);

        Renderer rend1= maybeCreateRenderer( type, recycleRends.get(0), cb, false);

        if ( cb!=null && RenderTypeUtil.needsColorbar(type) ) cb.setVisible( true );  //okay, only since this is not used.

        result.addRenderer(rend1);

        c.add(result, row, col);
        c.revalidate();
        c.validate();
        System.err.println("c.getBounts(): "+c.getBounds());
        System.err.println("row: "+row);
        result.resize();
        System.err.println("result.getBounds(): " +result.getBounds());
        return result;
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
                urls.add(((Bookmark.Item) b).getUri());
            }
        }
        return urls;
    }

    /**
     * Replace filename references within the DOM, and reset xrange.  This was the often-used ReplaceFile script
     * @param dom
     */
    public static void replaceFile( Component parent, Application dom ) {

        if ( dom.getDataSourceFilters(0).getUri()==null ) {
           JOptionPane.showMessageDialog( parent, "Nothing plotted" );
        } else {

            DataSourceFilter dsf= dom.getDataSourceFilters(0);
           URISplit split= URISplit.parse( dsf.getUri() );

           String oldf= split.file;

           String newf= JOptionPane.showInputDialog( parent,
            "<html>Current file is<br>"+oldf+"<br><br>Enter New Filename: ",
             oldf );

           if ( newf!=null ) {

                dom.getOptions().setAutoranging(false);

                for ( DataSourceFilter i: dom.getDataSourceFilters() ) {
                    String oldf1= i.getUri();
                    String newf1= oldf1.replace( oldf, newf );
                    i.setUri( newf1 );
                    
               }
                dom.getController().waitUntilIdle();
                dom.getOptions().setAutoranging(true);
                dom.getController().waitUntilIdle();

                dom.getController().setDataSourceFilter(dsf);
                
                resetZoomX(dom); // assumes focus is set

            }
        }
    }

    public static class AutoRangeDescriptor {

        public DatumRange range;
        public boolean log;
        private double robustMin;
        private double robustMax;
        private double median;

        public String toString() {
            return "" + range + " " + (log ? "log" : "");
        }
    }

    private static DatumRange getRange(Number min, Number max, Units units) {
        if (units != null && UnitsUtil.isTimeLocation(units)) {
            if (min == null) min = Units.mj1958.convertDoubleTo(units, -100000);
            if (max == null) max = Units.mj1958.convertDoubleTo(units, 100000);
        } else {
            if (min == null) min = Double.NEGATIVE_INFINITY;
            if (max == null) max = Double.POSITIVE_INFINITY;
            if (units == null) units = Units.dimensionless;
        }
        if ( UnitsUtil.isTimeLocation(units) ) {
            TimeLocationUnits tu= (TimeLocationUnits) units;
            if ( ! tu.isValid(min.doubleValue() ) ) min= tu.validMin();
            if ( ! tu.isValid(max.doubleValue() ) ) max= tu.validMax();
            return new DatumRange( min.doubleValue(), max.doubleValue(), units );
        } else {
            try {
                return new DatumRange(min.doubleValue(), max.doubleValue(), units);
            } catch ( IllegalArgumentException ex ) {
                System.err.println("here here");
                throw ex;
            }
        }
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
            Number tmin = (Number) properties.get(QDataSet.TYPICAL_MIN);
            Number tmax = (Number) properties.get(QDataSet.TYPICAL_MAX);
            DatumRange range = getRange(
                    (Number) properties.get(QDataSet.TYPICAL_MIN),
                    (Number) properties.get(QDataSet.TYPICAL_MAX),
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
                result.range = DatumRange.newDatumRange(Math.pow(10,Math.floor(Math.log10(result.robustMin))),
                        Math.pow(10,Math.ceil(Math.log10(result.robustMax))), u);
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

    public static QDataSet toDataSet( AutoRangeDescriptor ard ) {
        DDataSet result= DDataSet.createRank1(2);
        Units u= ard.range.getUnits();
        if ( u==null ) u= Units.dimensionless;
        result.putValue(0,ard.range.min().doubleValue(u));
        result.putValue(1,ard.range.max().doubleValue(u));
        result.putProperty( QDataSet.BINS_0, "min,max" );
        if (ard.log) result.putProperty( QDataSet.SCALE_TYPE, "log" );
        if ( u!=Units.dimensionless ) result.putProperty(QDataSet.UNITS,u);
        return result;
    }

    public static boolean resetZoomY( Application dom ) {
        Plot plot= dom.getController().getPlot();

        boolean result= true;
        Axis axis= dom.getController().getPlot().getYaxis();

        List<PlotElement> pes= dom.getController().getPlotElementsFor(plot);

        DatumRange range= null;
        for ( PlotElement pe: pes ) {
            if ( pe.isActive()==false ) continue;
            QDataSet ds= pe.getController().getDataSet();
            if ( ds!=null ) {
                ds= SemanticOps.trim( ds, plot.getXaxis().getRange(), null );
                PlotElement pcopy1= (PlotElement)pe.copy();
                PlotElementController.doAutoranging(pcopy1, Collections.singletonMap( QDataSet.SCALE_TYPE, (Object)( axis.isLog() ? "log" : "linear" ) ), ds, true ); // :) cast to Object!
                if ( range==null ) {
                    range= pcopy1.getPlotDefaults().getYaxis().getRange();
                } else {
                    range= DatumRangeUtil.union( range, pcopy1.getPlotDefaults().getYaxis().getRange() );
                }
            }
        }
        if ( range!=null ) axis.setRange(range);

        return result;
    }

    public static boolean resetZoomX( Application dom ) {
        Plot plot= dom.getController().getPlot();

        boolean result= true;
        Axis axis= dom.getController().getPlot().getXaxis();

        List<PlotElement> pes= dom.getController().getPlotElementsFor(plot);

        DatumRange range= null;
        for ( PlotElement pe: pes ) {
            if ( pe.isActive()==false ) continue;
            QDataSet ds= pe.getController().getDataSet();
            if ( ds!=null ) {
                ds= SemanticOps.trim( ds, null, plot.getYaxis().getRange() );
                PlotElement pcopy1= (PlotElement)pe.copy(); // TODO: something ain't right below...
                PlotElementController.doAutoranging(pcopy1, Collections.singletonMap( QDataSet.SCALE_TYPE, (Object)( axis.isLog() ? "log" : "linear" ) ), ds, true ); // :) cast to Object!
                if ( range==null ) {
                    range= pcopy1.getPlotDefaults().getXaxis().getRange();
                } else {
                    range= DatumRangeUtil.union( range, pcopy1.getPlotDefaults().getXaxis().getRange() );
                }
            }
        }
        if ( range!=null ) axis.setRange(range);

        return result;
    }


    public static boolean resetZoomZ( Application dom ) {
        Plot plot= dom.getController().getPlot();

        boolean result= true;
        Axis axis= dom.getController().getPlot().getZaxis();

        List<PlotElement> pes= dom.getController().getPlotElementsFor(plot);

        DatumRange range= null;
        for ( PlotElement pe: pes ) {
            if ( pe.isActive()==false ) continue;
            if ( !RenderTypeUtil.needsColorbar(pe.getRenderType()) ) continue;
            QDataSet ds= pe.getController().getDataSet();
            if ( ds!=null ) {
                ds= SemanticOps.trim( ds, plot.getXaxis().getRange(), plot.getYaxis().getRange() );
                PlotElement pcopy1= (PlotElement)pe.copy();
                PlotElementController.doAutoranging(pcopy1, Collections.singletonMap( QDataSet.SCALE_TYPE, (Object)( axis.isLog() ? "log" : "linear" ) ), ds, true ); // :) cast to Object!
                if ( range==null ) {
                    range= pcopy1.getPlotDefaults().getZaxis().getRange();
                } else {
                    range= DatumRangeUtil.union( range, pcopy1.getPlotDefaults().getZaxis().getRange() );
                }
            }
        }
        if ( range!=null ) axis.setRange(range);

        return result;
    }

    public static AutoRangeDescriptor autoRange( QDataSet ds, Map properties ) {
        return autoRange( ds, properties, false );
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
     * @param ds The dataset, a non-bundle, to be autoranged.
     * @param properties Additional constraints for properties, such as SCALE_TYPE
     * @param ignoreDsProps Don't check ds for TYPICAL_MIN and SCALE_TYPE.  MONOTONIC is never ignored.
     * @return
     */
    public static AutoRangeDescriptor autoRange(QDataSet ds, Map properties, boolean ignoreDsProps) {

        log.fine("enter autoRange "+ds);

        Units u = (Units) ds.property(QDataSet.UNITS);
        if (u == null) {
            if ( ds.property(QDataSet.JOIN_0)!=null ) {
                if ( ds.length()==0 ) throw new IllegalArgumentException("dataset is empty");
                u = (Units) ds.property(QDataSet.UNITS,0);
            } 
            if ( u==null ) {
                u = Units.dimensionless;
            }
        }

        AutoRangeDescriptor result = new AutoRangeDescriptor();

        // handle ordinal units by simply returning the range.
        if ( UnitsUtil.isOrdinalMeasurement(u) || UnitsUtil.isNominalMeasurement(u) ) {
            QDataSet ext= Ops.extent(ds);
            result.range= DataSetUtil.asDatumRange(ext,true);
            result.robustMin= result.range.min().doubleValue(u);
            result.robustMax= result.range.max().doubleValue(u);
            return result;
        }


        double[] dd;

        boolean mono = Boolean.TRUE.equals(ds.property(QDataSet.MONOTONIC));
        if ( null != ds.property(QDataSet.CADENCE) ) {
            if ( DataSetUtil.isMonotonic(ds) ) {
                mono= true;
            }
        }
        
        // these are from the dataset metadata.
        AutoRangeDescriptor typical= null;

        // the autoranging will be in log space only if the data are not time locations.
        boolean isLog= "log".equals(ds.property(QDataSet.SCALE_TYPE)) && !UnitsUtil.isTimeLocation(u);

        if ( !ignoreDsProps ) {
            Number typicalMin= (Number)ds.property(QDataSet.TYPICAL_MIN);
            Number typicalMax= (Number)ds.property(QDataSet.TYPICAL_MAX);
            if ( typicalMin!=null ) {
                typical= new AutoRangeDescriptor();
                typical.range= new DatumRange( typicalMin.doubleValue(), typicalMax.doubleValue(), u );
                typical.log= isLog;
            }
        }

        if ( properties!=null && "log".equals(properties.get(QDataSet.SCALE_TYPE)) && !UnitsUtil.isTimeLocation(u) ) {
            isLog= true;
        }

        if ( typical==null && SemanticOps.isJoin(ds) ) {
            result.range= null;
            result.robustMax= -1* Double.MAX_VALUE;
            result.robustMin= Double.MAX_VALUE;

            Units units=null;
            UnitsConverter uc= UnitsConverter.IDENTITY;
            for ( int i=0; i<ds.length(); i++ ) {
                AutoRangeDescriptor r1= autoRange( ds.slice(i), properties, false );
                if ( units==null ) {
                    units= r1.range.getUnits();
                } else {
                    uc= r1.range.getUnits().getConverter(units);
                }
                result.range= result.range==null ? r1.range : DatumRangeUtil.union( result.range, r1.range );
                if ( r1.log ) result.log= true;
            }
            result.robustMin= result.range.min().doubleValue(result.range.getUnits());
            result.robustMax= result.range.max().doubleValue(result.range.getUnits());
            return result;
        }

        if (mono) {
            RankZeroDataSet cadence = DataSetUtil.guessCadenceNew(ds, null);
            QDataSet wds= DataSetUtil.weightsDataSet(ds); // use weights rather than checking for fill and valid range.  The weights datset will reflect this information.
            if (cadence == null || cadence.value() > Double.MAX_VALUE / 100)
                cadence = DRank0DataSet.create(0.);
            if (ds.length() > 1) {
                int firstValid=0;
                while ( firstValid<wds.length() && wds.value(firstValid)==0 ) firstValid++;
                if ( firstValid==wds.length() ) throw new IllegalArgumentException("data contains no valid measurements");
                int lastValid=wds.length()-1;
                while ( lastValid>=0 && wds.value(lastValid)==0 ) lastValid--;
                if ( ( lastValid-firstValid+1 ) == 0 ) {
                    System.err.println("special case where monotonic dataset contains no valid data");
                    if (UnitsUtil.isTimeLocation(u)) {
                        dd = new double[]{0, Units.days.createDatum(1).doubleValue(u.getOffsetUnits())};
                    } else {
                        dd = new double[]{0, 1};
                    }
                } else {
                    double min = Math.min(ds.value(firstValid), ds.value(lastValid));
                    double max = Math.max(ds.value(firstValid), ds.value(lastValid));
                    double dcadence = Math.abs(cadence.value());
                    if ( isLog ) {
                        Units cu = (Units) cadence.property(QDataSet.UNITS);
                        if ( cu==null ) cu= Units.dimensionless;
                        if ( UnitsUtil.isRatiometric(cu) ) {
                            double factor = (cu.convertDoubleTo(Units.percentIncrease, dcadence) + 100) / 100.;
                            dd = new double[]{min / factor, max * factor};
                        } else {
                            if ( cu.isConvertableTo(u.getOffsetUnits() ) ) { // TODO: we need separate code to make datasets valid
                                dcadence= cu.convertDoubleTo( u.getOffsetUnits(), dcadence );
                                dd = new double[]{min - dcadence, max + dcadence};
                                if ( dd[0]<0 ) {
                                    dd[0]= min / 2.; // this is a fall-back mode
                                }
                            } else {
                                dd = new double[]{min, max};
                            }
                        }
                    } else {
                        dd = new double[]{min - dcadence, max + dcadence};
                    }
                }
            } else if ( ds.length()==1 ) {
                dd = simpleRange(ds);
                if (UnitsUtil.isTimeLocation(u)) {
                    dd = new double[]{ dd[0], dd[0]+Units.days.createDatum(1).doubleValue(u.getOffsetUnits()) };
                } else {
                    dd = new double[]{ dd[0], dd[0]+1};
                }
            } else {
                dd = simpleRange(ds);
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
                logger.log(Level.FINEST, "simpleRange(ds)= {0} - {1}", new Object[]{dd[0], dd[1]});
                if ( Units.dimensionless.isFill(dd[0]) ) dd[0]= dd[0] / 100; // kludge for LANL_1991_080_H0_SOPA_ESP_19920308_V01.cdf?FEDO
                if ( Units.dimensionless.isFill(dd[1]) ) dd[1]= dd[1] / 100;
            } catch (IllegalArgumentException ex) {
                if (UnitsUtil.isTimeLocation(u)) {
                    dd = new double[]{0, Units.days.createDatum(1).doubleValue(u.getOffsetUnits())};
                } else {
                    dd = new double[]{0, 1};
                }
            }
        }

        // bad things happen if we have time locations that don't vary, so here's some special code to avoid that.
        if ( UnitsUtil.isTimeLocation(u) && dd[0]==dd[1] ) {  // round out to a day if the times are the same.
            Units du= u.getOffsetUnits();
            double d= Units.days.convertDoubleTo( du, 1. );
            dd[0]= Math.floor( dd[0] / d ) * d;
            dd[1]= dd[0] + d;
        }


        double median;
        int total;
        double positiveMin;
        boolean isHist= false;

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
            positiveMin= ( dd[0] + ( dd[1]-dd[0] ) * 0.1 ); //???
            total = ds.length(); // only non-zero is checked.
        } else {
            // find the median by looking at the histogram.  If the dataset should be log, then the data will bunch up in the lowest bins.
            isHist= "stairSteps".equals( ds.property( QDataSet.RENDER_TYPE) ); // nasty bit of code
            QDataSet hist = DataSetOps.histogram(ds, dd[0], dd[1] + (dd[1] - dd[0]) * 0.01, (dd[1] - dd[0]) / 100);
            positiveMin= ((Double) hist.property("positiveMin")).doubleValue();
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
            result.robustMin = dd[0];
            result.robustMax = dd[1];

            if ( UnitsUtil.isTimeLocation(u) ) {
                double dmin= TimeUtil.createTimeDatum( 1000, 1, 1, 0, 0, 0, 0 ).doubleValue(u); // years from 1000A.D.
                double dmax= TimeUtil.createTimeDatum( 9000, 1, 1, 0, 0, 0, 0 ).doubleValue(u); // years to 9000A.D.
                if ( result.robustMin>dmax ) result.robustMin= dmax;
                if ( result.robustMin<dmin ) result.robustMin= dmin;
                if ( result.robustMax>dmax ) result.robustMax= dmax;
                if ( result.robustMax<dmin ) result.robustMax= dmin;
            }

            result.range = DatumRange.newDatumRange(result.robustMin, result.robustMax, u);

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

            // lin/log logic: in which space is ( median - nomMin ) more equal to ( nomMax - median )?  Also, nomMax / nomMin > 1e3
            double clin = (nomMax - result.median) / (result.median - nomMin);
            if (clin > 1.0) {
                clin = 1 / clin;
            }
            if ( result.median>0 && !org.das2.datum.UnitsUtil.isTimeLocation(u) ) {
                double clog = (nomMax / result.median) / Math.abs(result.median / nomMin);
                if (clog > 1.0) {
                    clog = 1 / clog;
                }

                if (clog > clin && nomMax / nomMin > 1e2) {
                    isLog = true;
                }
            }

            if ( !isHist && result.median==0 && nomMin==0 && nomMax/positiveMin>1e3 ) {  // this is where they are bunched up at zero.
                isLog= true;
                result.robustMin= positiveMin/10;
            }

            if ( UnitsUtil.isTimeLocation(u) ) {
                double dmin= TimeUtil.createTimeDatum( 1000, 1, 1, 0, 0, 0, 0 ).doubleValue(u); // years from 1000A.D.
                double dmax= TimeUtil.createTimeDatum( 9000, 1, 1, 0, 0, 0, 0 ).doubleValue(u); // years to 9000A.D.
                if ( result.robustMin>dmax ) result.robustMin= dmax;
                if ( result.robustMin<dmin ) result.robustMin= dmin;
                if ( result.robustMax>dmax ) result.robustMax= dmax;
                if ( result.robustMax<dmin ) result.robustMax= dmin;
            } 
            result.range = DatumRange.newDatumRange(result.robustMin, result.robustMax, u);

        }

        result.log = isLog;

        // interpret properties, looking for hints about scale type and ranges.
        if (properties != null) {

            Number tmin = (Number) properties.get(QDataSet.TYPICAL_MIN);
            Number tmax = (Number) properties.get(QDataSet.TYPICAL_MAX);

            Units uu=  (Units) properties.get(QDataSet.UNITS);
            if ( uu==null ) uu= Units.dimensionless;

            Datum ftmin=  uu.createDatum( tmin==null ? -1 * Double.MAX_VALUE : tmin );

            if ( isLog && tmin!=null && tmin.doubleValue()<=0 ) {
//                tmin= new Double( result.range.min().doubleValue(result.range.getUnits()) );
//                if ( tmin.doubleValue()<0 ) {
                    tmin= new Double( tmax.doubleValue() / 1e4 ); // this used to happen in IstpMetadataModel
//                }
            }

            DatumRange range = getRange( tmin, tmax, uu );
            
            // see if the typical extent is consistent with extent seen.  If the
            // typical extent won't hide the data's structure, then use it.
            if ((tmin != null || tmax != null)) {
                double d1, d2;
                if (result.log) {
                    if ( ftmin.doubleValue(uu)<=0 ) ftmin= uu.createDatum(1e-38);
                    Datum limit= ftmin;
                    try {
                        Datum dd1 = result.range.min().ge(limit) ? result.range.min() : limit; // these represent the range seen, guard against min
                        Datum dd2 = result.range.max().ge(limit) ? result.range.max() : limit;
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
                    if ( d2>1.2 && d2<2.0 ) { // see if we can save TYPICAL_MIN by doubling range
                        range= DatumRangeUtil.rescaleLog( range, 0, 2 );
                        d2= d2/2;
                        d1= d1/2;
                        logger.fine("adjusting TYPICAL_MAX from metadata, multiply by 2.0");
                    }
                    if ( d1<-4 && d2>0  ) { //often with log we get "1 count" averages that are very small (demo2: po_h0_hyd_$Y$m$d_v01.cdf)
                        logger.fine("rejecting statistical range because min is too small.");
                        result.range = range;
                        result.robustMin= range.min().doubleValue(result.range.getUnits());
                        result.robustMax= range.max().doubleValue(result.range.getUnits());
                        d1= 0;
                        d2= 1;
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
                    if ( d2>1.2 && d2<2.0 ) { // see if we can save TYPICAL_MIN by doubling range //TODO: I don't understand this...
                        range= DatumRangeUtil.rescale( range, 0, 2 );
                        d2= d2/2;
                        d1= d1/2;
                        logger.fine("adjusting TYPICAL_MAX from metadata, multiply by 2.0");
                    }
                }
                if (d2 - d1 > 0.1    // the stats range occupies 10% of the typical range
                        && d2 > 0.   // and the stats max is greater than the typical range min()
                        && d2 < 1.14  // and the top isn't clipping data badly  //TODO: we really need to be more robust about this.  hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ION_DIFFERENTIAL_ENERGY_FLUX&timerange=20000109 was failing because a small number of points was messing this up.
                        && d1 > -0.1 // and the bottom isn't clipping data badly
                        && d1 < 1.   // and the stats min is less then the typical range max().
                        && uu.isConvertableTo( u ) ) {  // and we ARE talking about the same thing
                    result.range = range;
                    // just use the metadata settings.
                    logger.fine("using TYPICAL_MIN, TYPICAL_MAX from metadata");
                    return result; // DANGER--EXIT POINT
                } else {
                    logger.fine("TYPICAL_MIN, TYPICAL_MAX from metadata rejected because it clipped or squished the data.");
                }
            }
        }

        // round out to frame the data with empty space, so that the data extent is known.
        if (UnitsUtil.isRatioMeasurement(u) || UnitsUtil.isIntervalMeasurement(u)) {
            if (result.log) {
                if (result.robustMax <= 0.0) result.robustMax = 1000;
                if (result.robustMin <= 0.0) result.robustMin = result.robustMax / 1e3;
                Datum min= u.createDatum(result.robustMin);
                Datum max= u.createDatum(result.robustMax );
                DomainDivider div= DomainDividerUtil.getDomainDivider(
                        min, max, true );
                while ( div.boundaryCount( min, max ) > 40 ) {
                    div= div.coarserDivider(false);
                }
                while ( div.boundaryCount( min, max ) < 20 ) {
                    div= div.finerDivider(true);
                }
                result.range = new DatumRange( div.rangeContaining(min).min(), div.rangeContaining(max).max() );
            } else if ( UnitsUtil.isTimeLocation(u) ) {
                if ( result.range.min().doubleValue( Units.us2000 ) > -6.3113480E15 ) {  //TODO: Julian has yr1800 limit.
                    DomainDivider div= DomainDividerUtil.getDomainDivider( result.range.min(), result.range.max() );
                    while ( div.boundaryCount( result.range.min(), result.range.max() ) > 40 ) {
                        div= div.coarserDivider(false);
                    }
                    while ( div.boundaryCount( result.range.min(), result.range.max() ) < 20 ) {
                        div= div.finerDivider(true);
                    }
                    result.range = new DatumRange(
                            div.rangeContaining(result.range.min()).min(),
                            div.rangeContaining(result.range.max()).max() );
                }

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

        if ( typical!=null ) {
            if ( result.log && typical.log ) {
                if ( typical.range.min().doubleValue(typical.range.getUnits() )<=0 ) typical.range= new DatumRange( result.range.min(), typical.range.max() );
                if ( result.range.intersects( typical.range ) ) {
                    double overlap= DatumRangeUtil.normalizeLog( result.range, typical.range.max() )
                            - DatumRangeUtil.normalizeLog( result.range, typical.range.min() );
                    if ( overlap>0.01 && overlap<100 ) return typical;
                }
            } else {
                if ( typical.log==false ) {
                    if ( result.range.intersects( typical.range ) ) {
                        double overlap=
                                DatumRangeUtil.normalize( result.range, typical.range.max() )
                                - DatumRangeUtil.normalize( result.range, typical.range.min() );
                        if ( overlap>0.01 && overlap<100 ) return typical;
                    }
                }
            }
        }
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
                //TODO: this shouldn't be difficult, just get the AppletContext.
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
        QDataSet wds= DataSetUtil.weightsDataSet(ds);
        QubeDataSetIterator it = new QubeDataSetIterator(ds);
        double[] result = new double[]{Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        int i = 0;

        while (i < DS_LENGTH_LIMIT && it.hasNext()) {
            it.next();
            i++;
            if ( it.getValue(wds)==0 ) continue;
            double maxv= it.getValue(max);
            if ( Double.isInfinite( maxv ) ) continue;
            if (it.getValue(wmin) > 0.)
                result[0] = Math.min(result[0], it.getValue(min));
            if (it.getValue(wmax) > 0.)
                result[1] = Math.max(result[1], maxv );
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
     * Note an old version of this would make fill canonical, now this simply resets the
     * VALID_MIN, VALID_MAX, and FILL_VALUE properties.
     *
     * Old values of vmin, vmax, and fill are ignored.
     * 
     */
    public static void applyFillValidRange(MutablePropertyDataSet ds, double vmin, double vmax, double fill) {

        Number ovmin = (Number) ds.property(QDataSet.VALID_MIN);
        Number ovmax = (Number) ds.property(QDataSet.VALID_MAX);

        boolean needToCopy = false;
        // if the old valid range contains the new range, then we simply reset the range.
        if (ovmax != null && ovmax.doubleValue() < vmax) needToCopy = true;
        if (ovmin != null && ovmin.doubleValue() > vmin) needToCopy = true;

        Number oldFill = (Number) ds.property(QDataSet.FILL_VALUE);

        if (oldFill != null && Double.isNaN(fill) == false && oldFill.doubleValue() != fill)
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

        for (int i = 0; i < QDataSet.MAX_RANK; i++) {
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

        // grab at least the label and units from here
        if ( SemanticOps.isJoin(ds) && ds.length()>0 && ds.rank()==3 ) {
            QDataSet j1= (QDataSet)ds.slice(0).property(QDataSet.DEPEND_1);
            if ( j1!=null ) {
                Map<String,Object> h1= (Map<String, Object>) result.get(QDataSet.DEPEND_1);
                if ( h1==null ) h1= new HashMap();
                Object v1;
                v1= j1.property(QDataSet.LABEL);
                if ( v1!=null ) h1.put( QDataSet.LABEL, v1 );
                v1= j1.property(QDataSet.UNITS);
                if ( v1!=null ) h1.put( QDataSet.UNITS, v1 );
                result.put( QDataSet.DEPEND_1, h1 );
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

    /**
     * See DataSource org.virbo.datasource.DataSourceUtil.guessRenderType, which will become the official version.
     * See http://autoplot.org/developer.guessRenderType
     * @param fillds
     * @return
     */
    public static RenderType guessRenderType(QDataSet fillds) {
        RenderType spec;

        RenderType specPref= RenderType.spectrogram;
        Options o= new Options();
        Preferences prefs= Preferences.userNodeForPackage( o.getClass() );  //TODO: because this is static?
        boolean nn= prefs.getBoolean(Options.PROP_NEARESTNEIGHBOR,o.isNearestNeighbor());
        if ( nn ) {
            specPref = RenderType.nnSpectrogram;
        }

        String srenderType= (String) fillds.property(QDataSet.RENDER_TYPE);
        if ( srenderType!=null ) {
            if ( srenderType.equals("time_series") ) {
                if (fillds.length() > 80000) {
                    spec = RenderType.hugeScatter;
                } else {
                    spec = RenderType.series;
                }
                return spec;
            }
            try {
                spec = RenderType.valueOf(srenderType);
                return spec;
            } catch (IllegalArgumentException e) {
                int i= srenderType.indexOf(">");
                if ( i>-1 ) {
                    try {
                        srenderType= srenderType.substring(0,i);
                        if ( srenderType.equals("spectrogram") ) {
                            // allow user preference here.
                            spec= specPref;
                        } else {
                            spec = RenderType.valueOf(srenderType.substring(0,i));
                        }
                        return spec;
                    } catch (IllegalArgumentException e2) {
                        System.err.println("unable to resolve render type for: "+srenderType + " in " +fillds );
                        //e.printStackTrace();  // okay.  we didn't recognize the render type
                    }
                }
            }
        }

        QDataSet dep1 = (QDataSet) fillds.property(QDataSet.DEPEND_1);
        QDataSet plane0 = (QDataSet) fillds.property(QDataSet.PLANE_0);
        QDataSet bundle1= (QDataSet) fillds.property(QDataSet.BUNDLE_1);

        if ( fillds.property( QDataSet.JOIN_0 )!=null ) {
            if ( fillds.length()==0 ) {
                return RenderType.series;
            }
            dep1 = (QDataSet) fillds.property(QDataSet.DEPEND_1,0);
            plane0 = (QDataSet) fillds.property(QDataSet.PLANE_0,0);
            bundle1= (QDataSet) fillds.property(QDataSet.BUNDLE_1,0);
        }

        if (fillds.rank() >= 2) {
            if ( bundle1!=null || (dep1 != null && isVectorOrBundleIndex(dep1) ) ) {
                if (fillds.length() > 80000) {
                    spec = RenderType.hugeScatter;
                } else {
                    spec = RenderType.series;
                }
                if ( bundle1!=null ) {
                    if ( bundle1.length()==3 && bundle1.property(QDataSet.DEPEND_0,2)!=null ) { // bad kludge
                        spec= RenderType.colorScatter;
                    } else if (bundle1.length() == 3 && bundle1.property(QDataSet.DEPENDNAME_0, 2) != null) { // bad kludge
                        spec= RenderType.colorScatter;
                    } else if ( bundle1.length()==3 && bundle1.property(QDataSet.CONTEXT_0,2)!=null ) {  // this is more consistent with PlotElementController code.
                        spec= RenderType.colorScatter;
                    } else if ( bundle1.length()==3 || bundle1.length()==4 ) {
                        Units u0= (Units) bundle1.property(QDataSet.UNITS,0);
                        if ( u0==null ) u0= Units.dimensionless;
                        Units u1= (Units) bundle1.property(QDataSet.UNITS,1);
                        if ( u1==null ) u1= Units.dimensionless;
                        Units u3= (Units) bundle1.property(QDataSet.UNITS,bundle1.length()-1);
                        if ( u3!=null && UnitsUtil.isOrdinalMeasurement(u3) && u0.getOffsetUnits().isConvertableTo(u1) ) {
                            spec= RenderType.eventsBar;
                        }
                    } else {
                        Units u3= (Units) bundle1.property(QDataSet.UNITS,bundle1.length()-1);
                        if ( u3!=null && UnitsUtil.isOrdinalMeasurement(u3) ) {
                            spec= RenderType.eventsBar;
                        }
                    }
                }
            } else {
                if ( dep1==null && fillds.rank()==2 && fillds.length()>3 && fillds.length(0)<4 ) { // Vector quantities without labels. [3x3] is a left a matrix.
                    spec = RenderType.series;
                } else {
                    spec = specPref;
                }
            }
        } else if ( fillds.rank()==0 || fillds.rank()==1 && SemanticOps.isBundle(fillds) ) {
            spec= RenderType.digital;

        } else if ( SemanticOps.getUnits(fillds) instanceof EnumerationUnits ) {
            QDataSet dep0= (QDataSet) fillds.property(QDataSet.DEPEND_0);
            if ( dep0==null ) {
                spec= RenderType.digital;
            } else {
                spec= RenderType.eventsBar;
            }
        } else {
            if (fillds.length() > 80000) {
                spec = RenderType.hugeScatter;
            } else {
                spec = RenderType.series;
            }

            if (plane0 != null) {
                Units u = (Units) plane0.property(QDataSet.UNITS);
                if (u==null) u= Units.dimensionless;
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
     * @param justRenderType if true, then just set the render type, other code will configure it.
     *    If true, presumably bindings will set the state.
     * @return
     */
    public static Renderer maybeCreateRenderer(
            RenderType renderType,
            Renderer recyclable, 
            DasColorBar colorbar, 
            boolean justRenderType ) {
        boolean conf= !justRenderType;

        if (renderType == RenderType.spectrogram) {
            SpectrogramRenderer result;
            if (recyclable != null && recyclable instanceof SpectrogramRenderer) {
                result= (SpectrogramRenderer) recyclable;
                if ( conf ) result.setRebinner(SpectrogramRenderer.RebinnerEnum.binAverage);
            } else {
                result = new SpectrogramRenderer(null, colorbar);
                result.setDataSetLoader(null);
            }
            if ( conf ) result.setRebinner( SpectrogramRenderer.RebinnerEnum.binAverage );
            return result;
        } else if (renderType == RenderType.nnSpectrogram) {
            SpectrogramRenderer result;
            if (recyclable != null && recyclable instanceof SpectrogramRenderer) {
                result= (SpectrogramRenderer) recyclable;
                if ( conf ) result.setRebinner(SpectrogramRenderer.RebinnerEnum.nearestNeighbor);
            } else {
                result = new SpectrogramRenderer(null, colorbar);
                result.setDataSetLoader(null);
                if ( conf ) result.setRebinner(SpectrogramRenderer.RebinnerEnum.nearestNeighbor);
                return result;
            }
            result.setRebinner( SpectrogramRenderer.RebinnerEnum.nearestNeighbor );
            return result;
        } else if (renderType == RenderType.hugeScatter) {
            if (recyclable != null && recyclable instanceof ImageVectorDataSetRenderer) {
                return recyclable;
            } else {
                ImageVectorDataSetRenderer result = new ImageVectorDataSetRenderer(null);
                result.setEnvelope(1); 
                result.setDataSetLoader(null);
                return result;
            }
        } else if ( renderType==RenderType.digital ) {
            if (recyclable != null && recyclable instanceof DigitalRenderer) {
                return recyclable;
            } else {
                Renderer result = new DigitalRenderer();
                result.setDataSetLoader(null);
                return result;
            }
        } else if ( renderType==RenderType.image ) {
            if (recyclable != null && recyclable instanceof RGBImageRenderer ) {
                return recyclable;
            } else {
                Renderer result = new RGBImageRenderer();
                result.setDataSetLoader(null);
                return result;
            }
        } else if ( renderType==RenderType.eventsBar ) {
            if (recyclable != null && recyclable instanceof EventsRenderer ) {
                return recyclable;
            } else {
                Renderer result = new EventsRenderer();
                result.setDataSetLoader(null);
                return result;
            }
        } else if ( renderType==RenderType.vectorPlot ) {
            if (recyclable != null && recyclable instanceof VectorPlot ) {
                return recyclable;
            } else {
                Renderer result = new VectorPlot();
                result.setDataSetLoader(null);
                return result;
            }
        } else if ( renderType==RenderType.pitchAngleDistribution ) {
            if (recyclable != null && recyclable instanceof PitchAngleDistributionRenderer ) {
                return recyclable;
            } else {
                PitchAngleDistributionRenderer result = new PitchAngleDistributionRenderer(colorbar);
                result.setDataSetLoader(null);
                return result;
            }
//        } else if ( renderType==RenderType.image ) {
//            if (recyclable != null && recyclable instanceof ImageRenderer) {
//                return recyclable;
//            } else {
//                Renderer result = new ImageRenderer();
//                result.setDataSetLoader(null);
//                colorbar.setVisible(false);
//                return result;
//            }
        } else {
            SeriesRenderer result;
            if (recyclable != null && recyclable instanceof SeriesRenderer) {
                result = (SeriesRenderer) recyclable;
            } else {
                result = new SeriesRenderer();
                result.setDataSetLoader(null);
            }

            if ( justRenderType ) return result;

            if (renderType == RenderType.colorScatter) {
                result.setColorBar(colorbar);
                result.setColorByDataSetId(QDataSet.PLANE_0); //schema: this should be the name of the dataset, or PLANE_x
            } else {
                result.setColorByDataSetId(""); //schema
            }

            if (renderType == RenderType.series) {
                result.setPsymConnector(PsymConnector.SOLID);
                result.setHistogram(false);
                result.setFillToReference(false);

            } else if (renderType == RenderType.scatter) {
                result.setPsymConnector(PsymConnector.NONE);
                result.setPsym( DefaultPlotSymbol.CIRCLES );
                result.setFillToReference(false);

            } else if (renderType == RenderType.colorScatter) {
                result.setPsymConnector(PsymConnector.NONE);
                result.setPsym( DefaultPlotSymbol.CIRCLES );
                result.setSymSize(3);
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

    public static Image getAutoplotIcon() {
        return new ImageIcon(AutoplotUtil.class.getResource("logoA16x16.png")).getImage();
    }

    public static Icon cancelIcon() {
        return new ImageIcon( AutoplotUtil.class.getResource("/com/cottagesystems/jdiskhog/resources/cancel14.png" ) );
    }

    private static int styleFromMessageType(int messageType) {
        switch (messageType) {
        case JOptionPane.ERROR_MESSAGE:
            return JRootPane.ERROR_DIALOG;
        case JOptionPane.QUESTION_MESSAGE:
            return JRootPane.QUESTION_DIALOG;
        case JOptionPane.WARNING_MESSAGE:
            return JRootPane.WARNING_DIALOG;
        case JOptionPane.INFORMATION_MESSAGE:
            return JRootPane.INFORMATION_DIALOG;
        case JOptionPane.PLAIN_MESSAGE:
        default:
            return JRootPane.PLAIN_DIALOG;
        }
    }

   private JDialog createDialog(Component parentComponent, String title,
            int style)
            throws HeadlessException {

        final JDialog dialog;

//        Window window = JOptionPane.getWindowForComponent(parentComponent);
//        if (window instanceof Frame) {
//            dialog = new JDialog((Frame)window, title, true);
//        } else {
//            dialog = new JDialog((Dialog)window, title, true);
//        }
// 	if (window instanceof SwingUtilities.SharedOwnerFrame) {
//	    WindowListener ownerShutdownListener =
//		(WindowListener)SwingUtilities.getSharedOwnerFrameShutdownListener();
// 	    dialog.addWindowListener(ownerShutdownListener);
// 	}
//        initDialog(dialog, style, parentComponent);
//        return dialog;
        return null;
    }

    /**
     * wrapper for displaying messages.  This will eventually use the Autoplot icon, etc.
     * This should be called, not JOptionPane.showMessageDialog(...)
     * @param parent
     * @param message, String or Component for the message.
     * @param title
     * @param messageType, like JOptionPane.ERROR_MESSAGE, JOptionPane.INFORMATION_MESSAGE, JOptionPane.WARNING_MESSAGE, JOptionPane.QUESTION_MESSAGE, or JOptionPane.PLAIN_MESSAGE
     */
    public static void showMessageDialog( Component parentComponent, Object message, String title, int messageType ) {
        //JOptionPane.showMessageDialog( parent, message, title, messageType );
        //JOptionPane.showOptionDialog( parent, message, title, JOptionPane.DEFAULT_OPTION, messageType, null, null, null);

        JOptionPane.showOptionDialog( parentComponent, message, title, JOptionPane.DEFAULT_OPTION, messageType, null, null, null);

//        JOptionPane             pane = new JOptionPane( message, JOptionPane.DEFAULT_OPTION, messageType, null, null, null);
//        int style = styleFromMessageType(messageType);
//        JDialog dialog = pane.createDialog(parentComponent, title, style);
//
//        pane.setInitialValue(initialValue);
//        pane.setComponentOrientation(((parentComponent == null) ?
//	    getRootFrame() : parentComponent).getComponentOrientation());
//
//        int style = styleFromMessageType(messageType);
//        JDialog dialog = pane.createDialog(parentComponent, title, style);
//
//        pane.selectInitialValue();
//        dialog.show();
//        dialog.dispose();
//
//        Object        selectedValue = pane.getValue();
//
//        if(selectedValue == null)
//            return CLOSED_OPTION;
//        if(options == null) {
//            if(selectedValue instanceof Integer)
//                return ((Integer)selectedValue).intValue();
//            return CLOSED_OPTION;
//        }
//        for(int counter = 0, maxCounter = options.length;
//            counter < maxCounter; counter++) {
//            if(options[counter].equals(selectedValue))
//                return counter;
//        }
    }

}
