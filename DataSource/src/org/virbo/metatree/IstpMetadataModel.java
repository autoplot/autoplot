/*
 * CdfFileDataSetDescriptor.java
 *
 * Created on August 12, 2005, 3:07 PM
 *
 *
 */
package org.virbo.metatree;

import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import java.lang.reflect.Array;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.MetadataModel;
import org.virbo.datasource.DataSourceUtil;

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
        return doubleValue(o, units, Double.NaN);
    }

    /**
     * returns the Entry that is convertable to double as a double.
     * @throws IllegalArgumentException for strings
     */
    private double doubleValue(Object o, Units units, double deflt) {
        if (o == null) {
            return deflt;
        }
        if (o instanceof Float) {
            return ((Float) o).doubleValue();
        } else if (o instanceof Double) {
            return ((Double) o).doubleValue();
        } else if (o instanceof Short) {
            return ((Short) o).doubleValue();
        } else if (o instanceof Integer) {
            return ((Integer) o).doubleValue();
        } else if (o instanceof Byte) {
            return ((Byte) o).doubleValue();
        } else if (o instanceof String) {
            try {
                return units.parse(DataSourceUtil.unquote((String) o)).doubleValue(units);
            } catch (ParseException ex) {
                try {
                    return Double.parseDouble((String) o);
                } catch (NumberFormatException ex2) {
                    throw new IllegalArgumentException("unable to parse " + o);
                }
            }
        } else {
            Class c = o.getClass();
            if (c.isArray()) {
                return Array.getDouble(o, 0);
            } else {
                throw new RuntimeException("Unsupported Data Type: " + o.getClass().getName());
            }
        }
    }

    private DatumRange getValidRange(Map attrs, Units units) {
        double max = doubleValue(attrs.get("VALIDMAX"), units);
        double min = doubleValue(attrs.get("VALIDMIN"), units);
        return DatumRange.newDatumRange(min, max, units);
    }

    /**
     * returns the range of the data by looking for the SCALEMIN/SCALEMAX params,
     * or the required VALIDMIN/VALIDMAX parameters.  Checks for valid range when
     * SCALETYP=log.
     */
    private DatumRange getRange(Map attrs, Units units) {
        DatumRange range;

        double min, max;
        if (attrs.containsKey("SCALEMIN") && attrs.containsKey("SCALEMAX")) {
            max = doubleValue(attrs.get("SCALEMAX"), units);
            min = doubleValue(attrs.get("SCALEMIN"), units);
        } else {
            if (attrs.containsKey("SCALEMAX")) {
                max = doubleValue(attrs.get("SCALEMAX"), units);
                min = 0;
            } else {
                max = doubleValue(attrs.get("VALIDMAX"), units, Double.POSITIVE_INFINITY);
                min = doubleValue(attrs.get("VALIDMIN"), units, Double.NEGATIVE_INFINITY);
            }
        }
        if ("log".equals(getScaleType(attrs)) && min <= 0) {
            min = max / 10000;
        }
        range = new DatumRange(min, max, units);
        return range;
    }

    /**
     * return null or the scale type if found.
     * @param attrs
     * @return
     */
    private String getScaleType(Map attrs) {
        String type = null;
        if (attrs.containsKey("SCALETYP")) {
            type = (String) attrs.get("SCALETYP");
        }
        return type;
    }

    public Map<String, Object> properties(Map<String, Object> meta) {
        Map attrs = meta;

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

        for (int i = 0; i < 4; i++) {
            String key = "DEPEND_" + i;
            if (attrs.containsKey(key)) {
                Map<String, Object> props = (Map<String, Object>) attrs.get(key);
                properties.put(key, properties(props));
            }
        }

        Units units = Units.dimensionless;
        if (attrs.containsKey("UNITS")) {
            String sunits = (String) attrs.get("UNITS");

            try {
                units = MetadataUtil.lookupUnits(DataSourceUtil.unquote(sunits));
            } catch (IllegalArgumentException e) {
                units = Units.dimensionless;
            }

            boolean isEpoch = (units == Units.milliseconds) || "Epoch".equals(attrs.get(QDataSet.NAME)) || "Epoch".equalsIgnoreCase(DataSourceUtil.unquote((String) attrs.get("LABLAXIS")));
            if (isEpoch) {
                units = Units.cdfEpoch;
                properties.put(QDataSet.LABEL, "");
            } else {
                String label = (String) attrs.get("LABLAXIS");
                if (label == null) {
                    label = sunits;
                } else {
                    if (!sunits.equals("")) {
                        label += " (" + sunits + ")";
                    }
                }
                properties.put(QDataSet.LABEL, label);
            }
            properties.put(QDataSet.UNITS, units);
        }

        try {

            DatumRange range = getRange(attrs, units);
            if ( ! attrs.containsKey("COMPONENT_0") ) { // Themis kludge
                properties.put(QDataSet.TYPICAL_MIN, range.min().doubleValue(units));
                properties.put(QDataSet.TYPICAL_MAX, range.max().doubleValue(units));
            
                range = getValidRange(attrs, units);
                properties.put(QDataSet.VALID_MIN, range.min().doubleValue(units));
                properties.put(QDataSet.VALID_MAX, range.max().doubleValue(units));
            } 

            properties.put(QDataSet.SCALE_TYPE, getScaleType(attrs));
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();

        }

        return properties;

    }

    @Override
    public String getLabel() {
        return "ISTP-CDF";
    }
}
