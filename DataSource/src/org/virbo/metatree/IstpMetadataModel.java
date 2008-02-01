/*
 * CdfFileDataSetDescriptor.java
 *
 * Created on August 12, 2005, 3:07 PM
 *
 *
 */
package org.virbo.metatree;

import edu.uiowa.physics.pw.das.datum.DatumRange;
import edu.uiowa.physics.pw.das.datum.Units;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import javax.swing.tree.TreeModel;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.MetadataModel;
import org.virbo.datasource.Util;

/**
 *
 * @author Jeremy
 *
 */
public class IstpMetadataModel extends MetadataModel {

    /**
     * returns the Entry that is convertable to double as a double.
     * @throws IllegalArgumentException for strings
     */
    private double doubleValue(Object o, Units units) {
        if (o instanceof Float) {
            return ((Float) o).doubleValue();
        } else if (o instanceof Double) {
            return ((Double) o).doubleValue();
        } else if (o instanceof Short) {
            return ((Short) o).doubleValue();
        } else if (o instanceof String) {
            try {
                return units.parse(Util.unquote((String) o)).doubleValue(units);
            } catch (ParseException ex) {
                throw new IllegalArgumentException("unable to parse " + o);
            }
        } else {
            throw new RuntimeException("Unsupported Data Type: " + o.getClass().getName());
        }
    }

    private DatumRange getValidRange(HashMap attrs) {
        Units units = Units.dimensionless;
        double max = doubleValue(attrs.get("VALIDMAX"), units);
        double min = doubleValue(attrs.get("VALIDMIN"), units);
        return DatumRange.newDatumRange(min, max, units);
    }

    /**
     * returns the range of the data by looking for the SCALEMIN/SCALEMAX params,
     * or the required VALIDMIN/VALIDMAX parameters.  Checks for valid range when
     * SCALETYP=log.
     */
    private DatumRange getRange(HashMap attrs) {
        DatumRange range;
        Units units = Units.dimensionless;

        if ("Epoch".equals(attrs.get("LABLAXIS")) && "ms".equals(attrs.get("UNITS"))) {
            units = Units.cdfEpoch;
        }
        double min, max;
        if (attrs.containsKey("SCALEMIN") && attrs.containsKey("SCALEMAX")) {
            max = doubleValue(attrs.get("SCALEMAX"), units);
            min = doubleValue(attrs.get("SCALEMIN"), units);
        } else {
            if (attrs.containsKey("SCALEMAX")) {
                max = doubleValue(attrs.get("SCALEMAX"), units);
                min = 0;
            } else {
                max = doubleValue(attrs.get("VALIDMAX"), units);
                min = doubleValue(attrs.get("VALIDMIN"), units);
            }
        }
        if (getScaleType(attrs).equals("log") && min <= 0) {
            min = max / 10000;
        }
        range = new DatumRange(min, max, units);
        return range;
    }

    private String getScaleType(HashMap attrs) {
        String type = "linear";
        if (attrs.containsKey("SCALETYP")) {
            type = (String) attrs.get("SCALETYP");
        }
        return type;
    }

    private Units lookup( String units ) {
        return Units.getByName(units);
    }
    
    
    public Map<String, Object> properties(TreeModel meta) {
        int nchild = meta.getChildCount(meta.getRoot());
        HashMap attrs = new HashMap();
        for (int i = 0; i < nchild; i++) {
            String ss = String.valueOf(meta.getChild(meta.getRoot(), i));
            int ii = ss.indexOf("=");
            attrs.put(ss.substring(0, ii), ss.substring(ii + 1).trim());
        }

        HashMap<String, Object> properties = new HashMap<String, Object>();

        if (attrs.containsKey("LABLAXIS")) {
            properties.put(QDataSet.LABEL, attrs.get("LABLAXIS"));
        }

        if (attrs.containsKey("CATDESC")) {
            properties.put(QDataSet.TITLE, attrs.get("CATDESC"));
        }

        if (attrs.containsKey("DISPLAY_TYPE")) {
            String type = (String) attrs.get("DISPLAY_TYPE");
            properties.put(QDataSet.RENDER_TYPE, type);
        }

        if (attrs.containsKey("DEPEND_0")) {
            properties.put(QDataSet.DEPEND_0, attrs.get("DEPEND_0"));
        }

        if (attrs.containsKey("UNITS")) {
            String sunits = (String)attrs.get("UNITS");
            Units units;
            try {
                units = lookup( Util.unquote(sunits) );
            } catch (IllegalArgumentException e) {
                units = Units.dimensionless;
            }

            if (units == Units.milliseconds && ("Epoch".equalsIgnoreCase( Util.unquote( (String) attrs.get("LABLAXIS")))) ) {
                units = Units.cdfEpoch;
            }
            properties.put( QDataSet.UNITS, units );
        }

        try {
            DatumRange range = getRange(attrs);
            properties.put(QDataSet.TYPICAL_RANGE, range);

            properties.put(QDataSet.VALID_RANGE, getValidRange(attrs));

            properties.put(QDataSet.SCALE_TYPE, getScaleType(attrs));
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();

        }

        return properties;

    }
}
