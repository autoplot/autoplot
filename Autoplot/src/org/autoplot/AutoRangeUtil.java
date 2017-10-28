/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.autoplot.AutoplotUtil.DS_LENGTH_LIMIT;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.DomainDivider;
import org.das2.datum.DomainDividerUtil;
import org.das2.datum.InconvertibleUnitsException;
import org.das2.datum.TimeLocationUnits;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsConverter;
import org.das2.datum.UnitsUtil;
import org.das2.qds.DDataSet;
import org.das2.qds.DRank0DataSet;
import org.das2.qds.DataSetOps;
import org.das2.qds.DataSetUtil;
import org.das2.qds.JoinDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.QubeDataSetIterator;
import org.das2.qds.RankZeroDataSet;
import org.das2.qds.SemanticOps;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.AutoHistogram;
import org.das2.util.LoggerManager;

/**
 *
 * @author jbf
 */
public class AutoRangeUtil {
    
    private final static Logger logger = org.das2.util.LoggerManager.getLogger("qdataset.ops.autorange");
    
    private static void setRange( DDataSet range, DatumRange drange, boolean log ) {
        range.putProperty( QDataSet.UNITS, drange.getUnits() );
        range.putValue( 0,drange.min().doubleValue( drange.getUnits() ) );
        range.putValue( 1,drange.max().doubleValue( drange.getUnits() ) );
        if ( log ) range.putProperty( QDataSet.SCALE_TYPE, "log" );
    }
        
    /**
     * return simple extent by only including points consistent with adjacent points.
     * also considers delta_plus, delta_minus properties.
     * TODO: /home/jbf/ct/autoplot/script/study/rfe445_speed/verifyExtentSimpleRange.jy showed that this was 25% slower than extent.
     * TODO: this is almost 800% slower than study445FastRange (above), which shows DataSetIterator is slow.
     * 
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
        } else {
            delta=  (QDataSet) ds.property(QDataSet.BIN_PLUS);
            if ( delta!=null ) {
                max = Ops.add(ds, delta);
            }
        }

        delta = (QDataSet) ds.property(QDataSet.DELTA_MINUS);
        if (delta != null) {
            min = Ops.subtract(ds, delta);
        } else {
            delta=  (QDataSet) ds.property(QDataSet.BIN_MINUS);
            if ( delta!=null ) {
                min = Ops.subtract(ds, delta);
            }
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
     * legacy class for describing the results of the autorange routine.
     * Note that QDataSet bounding cubes provide the same functionality.
     */
    public static class AutoRangeDescriptor {

        public DatumRange range;
        public boolean log;
        private double robustMin;
        private double robustMax;
        private double median;

        @Override
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

    /**
     * return the bounding qube for the given render type.  This was stolen from Test022.
     * @param dataSet
     * @param renderType
     * @return bounding cube[3,2]
     * @throws Exception
     */
    public static QDataSet bounds(QDataSet dataSet, RenderType renderType) throws Exception {
        DDataSet xrange = DDataSet.createRank1(2);
        DDataSet yrange = DDataSet.createRank1(2);
        DDataSet zrange = DDataSet.createRank1(2);
        JoinDataSet result = new JoinDataSet(2);
        result.join(xrange);
        result.join(yrange);
        result.join(zrange);
        Map props = new HashMap();
        if (renderType == RenderType.spectrogram || renderType == RenderType.nnSpectrogram) {
            QDataSet xds = (QDataSet) dataSet.property(QDataSet.DEPEND_0);
            if (xds == null) {
                if (dataSet.property(QDataSet.JOIN_0) != null) {
                    JoinDataSet ds = new JoinDataSet(2);
                    for (int i = 0; i < dataSet.length(); i++) {
                        ds.join((QDataSet) dataSet.property(QDataSet.DEPEND_0, i));
                    }
                    xds = ds;
                } else {
                    xds = DataSetUtil.indexGenDataSet(dataSet.length());
                }
            }
            QDataSet yds = (QDataSet) dataSet.property(QDataSet.DEPEND_1);
            Map<String, Object> yprops = (Map) props.get(QDataSet.DEPEND_1);
            if (yds == null) {
                if (dataSet.property(QDataSet.JOIN_0) != null) {
                    JoinDataSet ds = new JoinDataSet(2);
                    for (int i = 0; i < dataSet.length(); i++) {
                        //QDataSet qds= dataSet.slice(i); //TODO: this needs work.
                        //String f= new File("foo.qds").getAbsolutePath();
                        //ScriptContext.formatDataSet( dataSet, f );
                        ds.join((QDataSet) dataSet.property(QDataSet.DEPEND_1, i));
                    }
                    yds = ds;
                } else if (dataSet.rank() > 1) {
                    yds = DataSetUtil.indexGenDataSet(dataSet.length(0)); //TODO: QUBE assumed
                } else {
                    yds = DataSetUtil.indexGenDataSet(10); // later the user will get a message "renderer cannot plot..."
                    yprops = null;
                }
            }
            AutoRangeDescriptor xdesc = AutoRangeUtil.autoRange(xds, (Map) props.get(QDataSet.DEPEND_0));
            AutoRangeDescriptor ydesc = AutoRangeUtil.autoRange(yds, yprops);
            //QDataSet hist= getDataSourceFilter().controller.getHistogram();
            AutoRangeDescriptor desc;
            desc = AutoRangeUtil.autoRange(dataSet, props);
            setRange(zrange, desc.range, desc.log);
            setRange(xrange, xdesc.range, xdesc.log);
            setRange(yrange, ydesc.range, ydesc.log);
        } else {
            AutoRangeDescriptor ydesc;
            QDataSet depend0;
            if (SemanticOps.isBundle(dataSet)) {
                ydesc = AutoRangeUtil.autoRange(DataSetOps.unbundle(dataSet, 1), props);
                depend0 = DataSetOps.unbundle(dataSet, 0);
            } else {
                ydesc = AutoRangeUtil.autoRange(dataSet, props);
                depend0 = (QDataSet) dataSet.property(QDataSet.DEPEND_0);
            }
            setRange(yrange, ydesc.range, ydesc.log);
            QDataSet xds = depend0;
            if (xds == null) {
                xds = DataSetUtil.indexGenDataSet(dataSet.length());
            }
            AutoRangeDescriptor xdesc = AutoRangeUtil.autoRange(xds, (Map) props.get(QDataSet.DEPEND_0));
            setRange(xrange, xdesc.range, xdesc.log);
            if (renderType == RenderType.colorScatter) {
                AutoRangeDescriptor zdesc;
                if (dataSet.property(QDataSet.BUNDLE_1) != null) {
                    zdesc = AutoRangeUtil.autoRange((QDataSet) DataSetOps.unbundle(dataSet, 2), null);
                } else {
                    QDataSet plane0 = (QDataSet) dataSet.property(QDataSet.PLANE_0);
                    zdesc = AutoRangeUtil.autoRange(plane0, (Map) props.get(QDataSet.PLANE_0));
                }
                setRange(zrange, zdesc.range, zdesc.log);
            }
        }
        for (int i = 0; i < result.length(); i++) {
            Units u = (Units) result.property(QDataSet.UNITS, i);
            if (u != null) {
                DatumRange dr = DatumRange.newDatumRange(result.value(i, 0), result.value(i, 1), u);
                logger.log(Level.FINER, "{0}: {1}", new Object[]{i, dr});
            } else {
                logger.log(Level.FINER, "{0}: {1},{2}", new Object[]{i, result.value(i, 0), result.value(i, 1)});
            }
        }
        return result;
    }

    /**
     * this is a copy of the other autorange, lacking some of its hacks.  TODO: why?
     * This is not used.
     * @param hist
     * @param ds
     * @param properties
     * @return
     * @see #autoRange(org.das2.qds.QDataSet, java.util.Map, boolean)
     */
    public static AutoRangeDescriptor autoRange(QDataSet hist, QDataSet ds, Map properties) {
        Logger logger1 = LoggerManager.getLogger("qdataset.ops.autorange");
        logger1.log(Level.FINE, "enter autoRange {0}", ds);
        logger1.entering("org.virbo.autoplot.AutoplotUtil", "autoRange");
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
            if (cadence == null || cadence.value() > Double.MAX_VALUE / 100) {
                cadence = DRank0DataSet.create(0.0);
            }
            if (ds.length() > 1) {
                double min = Math.min(ds.value(0), ds.value(ds.length() - 1));
                double max = Math.max(ds.value(0), ds.value(ds.length() - 1));
                double dcadence = Math.abs(cadence.value());
                if ("log".equals(cadence.property(QDataSet.SCALE_TYPE))) {
                    Units cu = (Units) cadence.property(QDataSet.UNITS);
                    double factor = (cu.convertDoubleTo(Units.percentIncrease, dcadence) + 100) / 100.0;
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
                    dd[1] = dep0.value(i) + cadence.value(); // TODO: log10
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
            double nomMin;
            double nomMax;
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
            if (clog > clin && nomMax / nomMin > 100.0) {
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
            DatumRange range = getRange((Number) properties.get(QDataSet.TYPICAL_MIN), (Number) properties.get(QDataSet.TYPICAL_MAX), (Units) properties.get(QDataSet.UNITS));
            // see if the typical extent is consistent with extent seen.  If the
            // typical extent won't hide the data's structure, then use it.
            if (tmin != null || tmax != null) {
                double d1;
                double d2;
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
                 && d2 > 0.0 // and the stats max is greater than the typical range min()
                 && d1 < 1.0) {
                    // and the stats min is less then the typical range max().
                    result.range = range;
                    // just use the metadata settings.
                    logger1.exiting("org.virbo.autoplot.AutoplotUtil", "autoRange");
                    return result; // DANGER--EXIT POINT
                }
            }
        }
        // round out to frame the data with empty space, so that the data extent is known.
        if (UnitsUtil.isRatioMeasurement(u) || UnitsUtil.isIntervalMeasurement(u)) {
            if (result.log) {
                if (result.robustMin <= 0.0) {
                    result.robustMin = result.robustMax / 1000.0;
                }
                result.range = DatumRange.newDatumRange(Math.pow(10, Math.floor(Math.log10(result.robustMin))), Math.pow(10, Math.ceil(Math.log10(result.robustMax))), u);
            } else {
                result.range = DatumRange.newDatumRange(result.robustMin, result.robustMax, u);
                if (result.robustMin < result.robustMax) {
                    result.range = DatumRangeUtil.rescale(result.range, -0.05, 1.05);
                }
                if (result.robustMin == 0 && result.robustMax == 0) {
                    result.range = DatumRange.newDatumRange(-0.1, 1.0, u);
                }
            }
        } else {
            result.range = DatumRange.newDatumRange(result.robustMin, result.robustMax, u);
        }
        logger1.exiting("org.virbo.autoplot.AutoplotUtil", "autoRange");
        return result;
    }

    /**
     * Autorange using the dataset properties
     * @param ds the dataset, a non-bundle, to be autoranged.
     * @param properties Additional constraints for properties, such as SCALE_TYPE
     * @return  the range.
     * @see #autoRange(org.das2.qds.QDataSet, java.util.Map, boolean)
     */
    public static AutoRangeDescriptor autoRange(QDataSet ds, Map properties) {
        return autoRange(ds, properties, false);
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
     * TODO: This needs to be reworked. https://sourceforge.net/p/autoplot/bugs/1318/
     *
     * @param ds The dataset, a non-bundle, to be autoranged.
     * @param properties Additional constraints for properties, such as SCALE_TYPE
     * @param ignoreDsProps Don't check ds for TYPICAL_MIN and SCALE_TYPE.  MONOTONIC is never ignored.
     * @return the range.
     */
    public static AutoRangeDescriptor autoRange(QDataSet ds, Map properties, boolean ignoreDsProps) {
        Logger logger1 = LoggerManager.getLogger("qdataset.ops.autorange");
        logger1.entering("org.virbo.autoplot.AutoplotUtil", "autoRange", ds);
        Units u = (Units) ds.property(QDataSet.UNITS);
        if (u == null) {
            if (ds.property(QDataSet.JOIN_0) != null) {
                if (ds.length() == 0) {
                    throw new IllegalArgumentException("dataset is empty");
                }
                u = (Units) ds.property(QDataSet.UNITS, 0);
            }
            if (u == null) {
                u = Units.dimensionless;
            }
        }
        AutoRangeDescriptor result = new AutoRangeDescriptor();
        // handle ordinal units by simply returning the range.
        if (UnitsUtil.isOrdinalMeasurement(u) || UnitsUtil.isNominalMeasurement(u)) {
            QDataSet ext = Ops.extent(ds);
            result.range = DataSetUtil.asDatumRange(ext, true);
            result.robustMin = result.range.min().doubleValue(u);
            result.robustMax = result.range.max().doubleValue(u);
            logger1.exiting("org.virbo.autoplot.AutoplotUtil", "autoRange", ds);
            return result;
        }
        double[] dd; // two-element array that is the min and max of the data.
        boolean mono = Boolean.TRUE.equals(ds.property(QDataSet.MONOTONIC));
        if ( mono ) {
            mono= DataSetUtil.isMonotonicAndIncreasingQuick(ds);
        }
        if (null != ds.property(QDataSet.CADENCE)) {
            if (DataSetUtil.isMonotonic(ds)) {
                mono = true;
            }
        }
        if (ds.rank() != 1) {
            mono = false; //TODO: bins scheme
        }
        // these are from the dataset metadata.
        AutoRangeDescriptor typical = null;
        // the autoranging will be in log space only if the data are not time locations.
        boolean isLog = "log".equals(ds.property(QDataSet.SCALE_TYPE)) && !UnitsUtil.isTimeLocation(u);
        boolean isLin = "linear".equals(ds.property(QDataSet.SCALE_TYPE)) || UnitsUtil.isTimeLocation(u);
        if (!ignoreDsProps) {
            Number typicalMin = (Number) ds.property(QDataSet.TYPICAL_MIN);
            Number typicalMax = (Number) ds.property(QDataSet.TYPICAL_MAX);
            if (typicalMin != null && typicalMax != null) {
                // TODO: support just typicalMin or typicalMax...
                typical = new AutoRangeDescriptor();
                typical.range = new DatumRange(typicalMin.doubleValue(), typicalMax.doubleValue(), u);
                typical.log = isLog;
            }
        }
        if (properties != null && "log".equals(properties.get(QDataSet.SCALE_TYPE)) && !UnitsUtil.isTimeLocation(u)) {
            isLog = true;
        }
        if (typical == null && SemanticOps.isJoin(ds)) {
            result.range = null;
            result.robustMax = -1 * Double.MAX_VALUE;
            result.robustMin = Double.MAX_VALUE;
            Units units = null;
            UnitsConverter uc = UnitsConverter.IDENTITY;
            for (int i = 0; i < ds.length(); i++) {
                AutoRangeDescriptor r1 = autoRange(ds.slice(i), properties, false);
                if (units == null) {
                    units = r1.range.getUnits();
                } else {
                    uc = r1.range.getUnits().getConverter(units);
                }
                result.range = result.range == null ? r1.range : DatumRangeUtil.union(result.range, r1.range);
                if (r1.log) {
                    result.log = true;
                }
            }
            result.robustMin = result.range.min().doubleValue(result.range.getUnits());
            result.robustMax = result.range.max().doubleValue(result.range.getUnits());
            logger1.exiting("org.virbo.autoplot.AutoplotUtil", "autoRange", ds);
            return result;
        }
        if (mono && ds.rank() == 1) {
            //TODO: support bins scheme
            RankZeroDataSet cadence = DataSetUtil.guessCadenceNew(ds, null);
            QDataSet wds = DataSetUtil.weightsDataSet(ds); // use weights rather than checking for fill and valid range.  The weights datset will reflect this information.
            if (cadence == null || cadence.value() > Double.MAX_VALUE / 100) {
                cadence = DRank0DataSet.create(0.0);
            }
            if (ds.length() > 1) {
                int firstValid = 0;
                while (firstValid < wds.length() && wds.value(firstValid) == 0) {
                    firstValid++;
                }
                if (firstValid == wds.length()) {
                    throw new IllegalArgumentException("data contains no valid measurements");
                }
                int lastValid = wds.length() - 1;
                while (lastValid >= 0 && wds.value(lastValid) == 0) {
                    lastValid--;
                }
                if ((lastValid - firstValid + 1) == 0) {
                    logger1.fine("special case where monotonic dataset contains no valid data");
                    if (UnitsUtil.isTimeLocation(u)) {
                        dd = new double[]{0, Units.days.createDatum(1).doubleValue(u.getOffsetUnits())};
                    } else {
                        dd = new double[]{0, 1};
                    }
                } else {
                    double min = Math.min(ds.value(firstValid), ds.value(lastValid));
                    double max = Math.max(ds.value(firstValid), ds.value(lastValid));
                    double dcadence = Math.abs(cadence.value());
                    if (isLog) {
                        Units cu = (Units) cadence.property(QDataSet.UNITS);
                        if (cu == null) {
                            cu = Units.dimensionless;
                        }
                        if (UnitsUtil.isRatiometric(cu)) {
                            double factor = (cu.convertDoubleTo(Units.percentIncrease, dcadence) + 100) / 100.0;
                            dd = new double[]{min / factor, max * factor};
                        } else {
                            if (cu.isConvertibleTo(u.getOffsetUnits())) {
                                // TODO: we need separate code to make datasets valid
                                dcadence = cu.convertDoubleTo(u.getOffsetUnits(), dcadence);
                                dd = new double[]{min - dcadence, max + dcadence};
                                if (dd[0] < 0) {
                                    dd[0] = min / 2.0; // this is a fall-back mode
                                }
                            } else {
                                dd = new double[]{min, max};
                            }
                        }
                    } else {
                        dd = new double[]{min - dcadence, max + dcadence};
                        try {
                            logger1.log(Level.FINEST, "range of monotonic set by min to max, extended by cadence: {0}", DatumRange.newDatumRange(dd[0], dd[1], u));
                        } catch (RuntimeException ex) {
                            // don't muck up the production release with unforeseen runtime exception.  TODO: remove me.
                        }
                    }
                }
            } else if (ds.length() == 1) {
                dd = simpleRange(ds);
                //QDataSet ddds= study445FastRange(ds);
                //dd = new double[] { ddds.value(0), ddds.value(1) };
                if (UnitsUtil.isTimeLocation(u)) {
                    dd = new double[]{dd[0], dd[0] + Units.days.createDatum(1).doubleValue(u.getOffsetUnits())};
                } else {
                    dd = new double[]{dd[0], dd[0] + 1};
                }
            } else {
                //dd = simpleRange(ds);
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
                //QDataSet ddds= study445FastRange(ds);
                //dd = new double[] { ddds.value(0), ddds.value(1) };
                logger1.log(Level.FINEST, "simpleRange(ds)= {0} - {1}", new Object[]{dd[0], dd[1]});
                if (Units.dimensionless.isFill(dd[0])) {
                    dd[0] = dd[0] / 100; // kludge for LANL_1991_080_H0_SOPA_ESP_19920308_V01.cdf?FEDO
                }
                if (Units.dimensionless.isFill(dd[1])) {
                    dd[1] = dd[1] / 100; // work around 2009 bug where DatumRanges cannot contain -1e31.
                }
            } catch (IllegalArgumentException ex) {
                logger.log(Level.WARNING, ex.getMessage(), ex);
                if (UnitsUtil.isTimeLocation(u)) {
                    dd = new double[]{0, Units.days.createDatum(1).doubleValue(u.getOffsetUnits())};
                } else {
                    dd = new double[]{0, 1};
                }
            }
        }
        // bad things happen if we have time locations that don't vary, so here's some special code to avoid that.
        if (UnitsUtil.isTimeLocation(u) && dd[0] == dd[1]) {
            // round out to a day if the times are the same.
            if (dd[0] <= -1.0E29) {
                throw new IllegalArgumentException("timetags are all invalid ");
            }
            Units du = u.getOffsetUnits();
            double d = Units.days.convertDoubleTo(du, 1.0);
            dd[0] = Math.floor(dd[0] / d) * d;
            dd[1] = dd[0] + d;
        }
        double median;
        int total;
        double positiveMin;
        boolean isHist = false;
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
            positiveMin = (dd[0] + (dd[1] - dd[0]) * 0.1); //???
            total = ds.length(); // only non-zero is checked.
        } else {
            // find the median by looking at the histogram.  If the dataset should be log, then the data will bunch up in the lowest bins.
            isHist = "stairSteps".equals(ds.property(QDataSet.RENDER_TYPE)); // nasty bit of code
            double binSize = (dd[1] - dd[0]) * 0.01;
            QDataSet hist = DataSetOps.histogram(ds, dd[0] - binSize / 2, dd[1] + binSize / 2, (dd[1] - dd[0]) / 100);
            positiveMin = ((Double) hist.property("positiveMin"));
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
            if (UnitsUtil.isTimeLocation(u)) {
                double dmin = TimeUtil.createTimeDatum(1000, 1, 1, 0, 0, 0, 0).doubleValue(u); // years from 1000A.D.
                double dmax = TimeUtil.createTimeDatum(9000, 1, 1, 0, 0, 0, 0).doubleValue(u); // years to 9000A.D.
                if (result.robustMin > dmax) {
                    result.robustMin = dmax;
                }
                if (result.robustMin < dmin) {
                    result.robustMin = dmin;
                }
                if (result.robustMax > dmax) {
                    result.robustMax = dmax;
                }
                if (result.robustMax < dmin) {
                    result.robustMax = dmin;
                }
            }
            result.range = DatumRange.newDatumRange(result.robustMin, result.robustMax, u);
        } else {
            result.median = median;
            result.robustMin = dd[0];
            result.robustMax = dd[1];
            double nomMin;
            double nomMax;
            if (mono) {
                // nomMin= dd[0]; nomMax=dd[1]   //TODO: the following two lines assume there is no fill and it is monotonically increasing.
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
            if (!isLin && result.median > 0 && !UnitsUtil.isTimeLocation(u)) {
                double clog = (nomMax / result.median) / Math.abs(result.median / nomMin);
                if (clog > 1.0) {
                    clog = 1 / clog;
                }
                if (clog > clin && nomMax / nomMin > 100.0) {
                    isLog = true;
                }
            }
            //double normalMedianLog = ( result.median / positiveMin ) / ( nomMax / positiveMin );
            if (!isLin && !isHist && result.median == 0 && nomMin == 0 && nomMax / positiveMin > 1000.0) {
                // this is where they are bunched up at zero.
                isLog = true;
                result.robustMin = positiveMin / 10;
            }
            if (UnitsUtil.isTimeLocation(u)) {
                double dmin = TimeUtil.createTimeDatum(1000, 1, 1, 0, 0, 0, 0).doubleValue(u); // years from 1000A.D.
                double dmax = TimeUtil.createTimeDatum(9000, 1, 1, 0, 0, 0, 0).doubleValue(u); // years to 9000A.D.
                if (result.robustMin > dmax) {
                    result.robustMin = dmax;
                }
                if (result.robustMin < dmin) {
                    result.robustMin = dmin;
                }
                if (result.robustMax > dmax) {
                    result.robustMax = dmax;
                }
                if (result.robustMax < dmin) {
                    result.robustMax = dmin;
                }
            }
            result.range = DatumRange.newDatumRange(result.robustMin, result.robustMax, u);
        }
        result.log = isLog;
        // interpret properties, looking for hints about scale type and ranges.
        if (properties != null) {
            Number tmin = (Number) properties.get(QDataSet.TYPICAL_MIN);
            Number tmax = (Number) properties.get(QDataSet.TYPICAL_MAX);
            Units uu = (Units) properties.get(QDataSet.UNITS);
            if (uu == null) {
                uu = Units.dimensionless;
            }
            if (UnitsUtil.isIntervalOrRatioMeasurement(uu)) {
                Datum ftmin = uu.createDatum(tmin == null ? -1 * Double.MAX_VALUE : tmin);
                if (isLog && tmin != null && tmin.doubleValue() <= 0) {
                    //                tmin= new Double( result.range.min().doubleValue(result.range.getUnits()) );
                    //                if ( tmin.doubleValue()<0 ) {
                    tmin = tmax.doubleValue() / 10000.0; // this used to happen in IstpMetadataModel
                    //                }
                }
                DatumRange range = getRange(tmin, tmax, uu);
                // see if the typical extent is consistent with extent seen.  If the
                // typical extent won't hide the data's structure, then use it.
                if (tmin != null && tmax != null) {
                    double d1;
                    double d2;
                    if (result.log) {
                        if (ftmin.doubleValue(uu) <= 0) {
                            ftmin = uu.createDatum(1.0E-38);
                        }
                        Datum limit = ftmin;
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
                        if (d2 > 1.2 && d2 < 2.0) {
                            // see if we can save TYPICAL_MIN by doubling range
                            logger1.log(Level.FINE, "TYPICAL_MAX rejected because max ({0}) outside the value of TYPICAL range ({1})", new Object[]{result.range.max(), range});
                            range = DatumRangeUtil.rescaleLog(range, 0, 1.333);
                            DatumRange range2 = DatumRangeUtil.rescaleLog(range, 0, 2);
                            d2 = d2 / 1.333;
                            d1 = d1 / 1.333;
                            logger1.fine("adjusting TYPICAL_MAX from metadata, multiply by 1.2");
                            if (d2 > 1.2 && d2 < 2.0) {
                                // do what we used to do.
                                range = range2;
                                d2 = d2 * 1.333 / 2;
                                d1 = d1 * 1.333 / 2;
                                logger1.fine("adjusting TYPICAL_MAX from metadata, multiply by 2.0");
                            }
                        }
                        if (d1 < -4 && d2 > 0) {
                            //often with log we get "1 count" averages that are very small (demo2: po_h0_hyd_$Y$m$d_v01.cdf)
                            logger1.fine("rejecting statistical range because min is too small.");
                            result.range = range;
                            result.robustMin = range.min().doubleValue(result.range.getUnits());
                            result.robustMax = range.max().doubleValue(result.range.getUnits());
                            d1 = 0;
                            d2 = 1;
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
                        if (d2 > 1.2 && d2 < 2.0) {
                            // see if we can save TYPICAL_MIN by doubling range //TODO: I don't understand this...
                            range = DatumRangeUtil.rescale(range, 0, 2);
                            d2 = d2 / 2;
                            d1 = d1 / 2;
                            logger1.fine("adjusting TYPICAL_MAX from metadata, multiply by 2.0");
                        }
                    }
                    if (d2 - d1 > 0.1 // the stats range occupies 10% of the typical range
                     && d2 > 0.0 // and the stats max is greater than the typical range min()
                     && d2 < 1.14 // and the top isn't clipping data badly  //TODO: we really need to be more robust about this.  hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ION_DIFFERENTIAL_ENERGY_FLUX&timerange=20000109 was failing because a small number of points was messing this up.
                     && d1 > -0.1 // and the bottom isn't clipping data badly
                     && d1 < 1.0 // and the stats min is less then the typical range max().
                     && uu.isConvertibleTo(u)) {
                        // and we ARE talking about the same thing
                        result.range = range;
                        // just use the metadata settings.
                        logger1.fine("using TYPICAL_MIN, TYPICAL_MAX from metadata");
                        logger1.exiting("org.virbo.autoplot.AutoplotUtil", "autoRange", ds);
                        return result; // DANGER--EXIT POINT
                    } else {
                        logger1.log(Level.FINE, "TYPICAL_MIN={0} and TYPICAL_MAX={1} from metadata rejected because it clipped or squished the data {2}", new Object[]{tmin.toString(), tmax.toString(), result.range});
                    }
                }
            }
        }
        // round out to frame the data with empty space, so that the data extent is known.
        if (UnitsUtil.isIntervalOrRatioMeasurement(u)) {
            if (result.log) {
                if (result.robustMax <= 0.0 || Double.isNaN(result.robustMax)) {
                    result.robustMax = 1000;
                }
                if (result.robustMin <= 0.0 || Double.isNaN(result.robustMin)) {
                    result.robustMin = result.robustMax / 1000.0;
                }
                Datum min = u.createDatum(result.robustMin);
                Datum max = u.createDatum(result.robustMax);
                DomainDivider div = DomainDividerUtil.getDomainDivider(min, max, true);
                while (div.boundaryCount(min, max) > 40) {
                    div = div.coarserDivider(false);
                }
                while (div.boundaryCount(min, max) < 20) {
                    div = div.finerDivider(true);
                }
                result.range = new DatumRange(div.rangeContaining(min).min(), div.rangeContaining(max).max());
            } else if (UnitsUtil.isTimeLocation(u)) {
                if (result.range.min().doubleValue(Units.us2000) > -6.311348E15) {
                    //TODO: Julian has yr1800 limit.
                    if (result.range.width().value() == 0.0) {
                        result.range = new DatumRange(result.range.min(), result.range.min().add(Units.seconds.createDatum(1)));
                    } else {
                        DomainDivider div = DomainDividerUtil.getDomainDivider(result.range.min(), result.range.max());
                        while (div.boundaryCount(result.range.min(), result.range.max()) > 40) {
                            div = div.coarserDivider(false);
                        }
                        while (div.boundaryCount(result.range.min(), result.range.max()) < 20) {
                            div = div.finerDivider(true);
                        }
                        result.range = new DatumRange(div.rangeContaining(result.range.min()).min(), div.rangeContaining(result.range.max()).max());
                    }
                }
            } else {
                result.range = DatumRange.newDatumRange(result.robustMin, result.robustMax, u);
                if (result.robustMin < result.robustMax) {
                    result.range = DatumRangeUtil.rescale(result.range, -0.05, 1.05);
                }
                if (result.robustMin == 0 && result.robustMax == 0) {
                    result.range = DatumRange.newDatumRange(-0.1, 1.0, u);
                }
            }
        } else {
            result.range = DatumRange.newDatumRange(result.robustMin, result.robustMax, u);
        }
        logger1.exiting("org.virbo.autoplot.AutoplotUtil", "autoRange", ds);
        if (typical != null) {
            if (result.log && typical.log) {
                if (typical.range.min().doubleValue(typical.range.getUnits()) <= 0) {
                    typical.range = new DatumRange(result.range.min(), typical.range.max());
                }
                if (result.range.intersects(typical.range)) {
                    double overlap = DatumRangeUtil.normalizeLog(result.range, typical.range.max()) - DatumRangeUtil.normalizeLog(result.range, typical.range.min());
                    if (overlap > 0.01 && overlap < 100) {
                        return typical;
                    }
                }
            } else {
                if (typical.log == false) {
                    if (result.range.intersects(typical.range)) {
                        double overlap = DatumRangeUtil.normalize(result.range, typical.range.max()) - DatumRangeUtil.normalize(result.range, typical.range.min());
                        if (overlap > 0.01 && overlap < 100) {
                            return typical;
                        }
                    }
                }
            }
        }
        return result;
    }
    
}
