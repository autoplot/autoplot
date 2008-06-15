/*
 * CdfFileDataSetDescriptor.java
 *
 * Created on August 12, 2005, 3:07 PM
 *
 *
 */
package org.virbo.cefdatasource;

import edu.uiowa.physics.pw.das.datum.DatumRange;
import edu.uiowa.physics.pw.das.datum.EnumerationUnits;
import edu.uiowa.physics.pw.das.datum.Units;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.WritableDataSet;
import org.virbo.datasource.MetadataModel;

/**
 *
 * @author Jeremy
 *
 */
public class CefMetadataModel extends MetadataModel {

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
                return units.parse((String) o).doubleValue(units);
            } catch (ParseException ex) {
                throw new IllegalArgumentException("unable to parse " + o);
            }
        } else {
            throw new RuntimeException("Unsupported Data Type: " + o.getClass().getName());
        }
    }

    private DatumRange getValidRange( Map attrs) {
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
    private DatumRange getRange( Map attrs) {
        DatumRange range;
        Units units = Units.dimensionless;

        if ("Epoch".equals(attrs.get("LABLAXIS")) && "ms".equals(attrs.get("UNITS"))) {
            units = Units.cdfEpoch;
        }
        double min=0, max=0;
        if (attrs.containsKey("SCALEMIN") && attrs.containsKey("SCALEMAX")) {
            max = doubleValue(attrs.get("SCALEMAX"), units);
            min = doubleValue(attrs.get("SCALEMIN"), units);
        } else {
            if (attrs.containsKey("SCALEMAX")) {
                max = doubleValue(attrs.get("SCALEMAX"), units);
                min = 0;
            } else {
                // I thought VALIDMAX is required, but maybe not
                if ( attrs.containsKey("VALIDMAX") ) {
                    max = doubleValue(attrs.get("VALIDMAX"), units);
                    min = doubleValue(attrs.get("VALIDMIN"), units);
                }
            }
        }
        if ( getScaleType(attrs).equals("log") && min <= 0 ) {
            min = max / 10000;
        }
        
        if ( min==max && max==0 ) {
            return null;
        } else {
            range = new DatumRange(min, max, units);
            return range;
        }
    }

    private String getScaleType( Map attrs) {
        String type = "linear";
        if (attrs.containsKey("SCALETYP")) {
            type = (String) attrs.get("SCALETYP");
        }
        return type.toLowerCase();
    }

    public Map<String, Object> properties( Map<String,Object> attrs ) {

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

        if (attrs.containsKey("COORDINATE_SYSTEM")) {
            String type = (String) attrs.get("COORDINATE_SYSTEM");
            int size = 3; // this will be derived from sizes attr.
            if (size == 3) {
                EnumerationUnits units = EnumerationUnits.create(type);
                WritableDataSet dep1 = DDataSet.createRank1(3);
                dep1.putValue(0, units.createDatum("X").doubleValue(units));
                dep1.putValue(1, units.createDatum("Y").doubleValue(units));
                dep1.putValue(2, units.createDatum("Z").doubleValue(units));
                dep1.putProperty(QDataSet.UNITS, units);
                dep1.putProperty(QDataSet.COORDINATE_FRAME, type);
                properties.put(QDataSet.DEPEND_1, dep1);
            }
        }

        try {
            DatumRange range = getRange(attrs);
            properties.put(QDataSet.TYPICAL_RANGE, range);

            properties.put( QDataSet.VALID_RANGE, getValidRange( attrs ) );

            properties.put(QDataSet.SCALE_TYPE, getScaleType(attrs));
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();

        }

        return properties;

    }

    @Override
    public String getLabel() {
        return "CEF";
    }
    
}
