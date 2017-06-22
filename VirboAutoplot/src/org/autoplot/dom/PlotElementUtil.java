/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.dom;

import java.text.ParseException;
import java.util.Map;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.qds.QDataSet;

/**
 * static utilities the plot element controller uses.
 * @author jbf
 */
public class PlotElementUtil {

    /**
     * describe the slice.
     * @param defaultName
     * @param dep
     * @param index
     * @return
     */
    protected static String describe(String defaultName, QDataSet dep, int index) {
        String name = defaultName;

        String name1 = (String) dep.property(QDataSet.NAME);
        if (name1 != null) name = name1;

        Units u = (Units) dep.property(QDataSet.UNITS);
        if (u == null) u = Units.dimensionless;

        String svalue;
        if ( index<0 || index>=dep.length() ) {
            svalue="(index out of bounds)";
        } else {
            Datum value = u.createDatum(dep.value(index));
            svalue= String.valueOf(value);
        }
        

        if (UnitsUtil.isTimeLocation(u)) {
            return "time=" + svalue; // needed for the replace kludge
        } else {
            return name + "=" + svalue;
        }

    }
    
    /**
     * check to make sure properties units are consistent with dataset units,
     * otherwise we'll have problems with units conversions.  The map
     * properties may have its property units changed to make them consistent.
     * @param properties
     * @param dataset
     */
    protected static void unitsCheck(Map properties, QDataSet dataset) {
        Units u0 = (Units) dataset.property(QDataSet.UNITS);
        Units u1 = (Units) properties.get(QDataSet.UNITS);
        if (u0 == null || u1 == null || !u0.isConvertibleTo(u1)) {
            properties.put(QDataSet.UNITS, u0);
        }
        for (int i = 0; i < QDataSet.MAX_RANK; i++) {
            QDataSet dep = (QDataSet) dataset.property("DEPEND_" + i);
            Map depprops = (Map) properties.get("DEPEND_" + i);
            if (dep != null && depprops != null) {
                unitsCheck(depprops, dep);
            }
        }
    }
    
    /**
     * @return double[3], vmin, vmax, fill.
     */
    public static double[] parseFillValidRangeInternal(String validRange, String sfill) throws ParseException {
        double[] result = new double[3];
        if (!"".equals(validRange.trim())) {
            DatumRange vrange = DatumRangeUtil.parseDatumRange(validRange, Units.dimensionless);
            result[0] = vrange.min().doubleValue(Units.dimensionless);
            result[1] = vrange.max().doubleValue(Units.dimensionless);
        } else {
            result[0] = -1 * Double.MAX_VALUE;
            result[1] = Double.MAX_VALUE;
        }

        if (!"".equals(sfill.trim())) {
            result[2] = Double.parseDouble(sfill);
        } else {
            result[2] = Double.NaN;
        }

        return result;
    }
        
}
