/*
 * CdfFileDataSetDescriptor.java
 *
 * Created on August 12, 2005, 3:07 PM
 *
 *
 */
package org.virbo.cefdatasource;

import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import org.virbo.dataset.QDataSet;
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
    private Double doubleValue(Object o, Units units) {
        if ( o==null ) return null;
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

    /**
     * return the valid range found in the metadata, or null.
     * @param attrs
     * @param units
     * @return
     */
    private DatumRange getValidRange( Map attrs, Units units ) {
        Double max = doubleValue(attrs.get("VALIDMAX"), units);
        Double min = doubleValue(attrs.get("VALIDMIN"), units);
        if ( max==null || min==null ) return null;
        return DatumRange.newDatumRange(min, max, units);
    }

    /**
     * returns the range of the data by looking for the SCALEMIN/SCALEMAX params,
     * or the required VALIDMIN/VALIDMAX parameters.  Checks for valid range when
     * SCALETYP=log.
     */
    private DatumRange getRange( Map attrs, Units units ) {
        DatumRange range;
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

        if ( attrs.containsKey("FILLVAL") ) { 
            String sfill= (String) attrs.get("FILLVAL");
            properties.put(QDataSet.FILL_VALUE,Double.parseDouble(sfill) );
        }

        Units units= Units.dimensionless;
        try {
            DatumRange range = getRange(attrs,units);
	    if ( range!=null ) {
                properties.put(QDataSet.TYPICAL_MIN, range.min().doubleValue(units) );
                properties.put(QDataSet.TYPICAL_MAX, range.max().doubleValue(units) );
            }
            range= getValidRange(attrs,units);
            if ( range!=null ) {
                properties.put(QDataSet.VALID_MIN, range.min().doubleValue(units) );
                properties.put(QDataSet.VALID_MAX, range.max().doubleValue(units) );
            }
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
