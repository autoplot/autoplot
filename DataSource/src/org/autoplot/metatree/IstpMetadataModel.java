/*
 * CdfFileDataSetDescriptor.java
 *
 * Created on August 12, 2005, 3:07 PM
 *
 *
 */
package org.autoplot.metatree;

import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import java.lang.reflect.Array;
import java.text.ParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.UnitsUtil;
import org.das2.util.LatexToGranny;
import org.das2.qds.ArrayDataSet;
import org.das2.qds.DataSetOps;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.MetadataModel;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.datasource.LogNames;
import org.das2.qds.DDataSet;
import org.das2.qds.FDataSet;
import org.das2.qds.ops.Ops;

/**
 * Metadata model for ISTP conventions.  For example, FIELDNAM is mapped to QDataSet.NAME, SCALEMIN is 
 * mapped to TYPICAL_MIN, etc.  When LaTeX fragments are found in axis titles 
 * {@code (s.contains("^{") || s.contains("_{"))}, then this is converted 
 * into Granny control strings.
 
 * 
 * @author Jeremy
 *
 */
public class IstpMetadataModel extends MetadataModel {

    private static Logger logger= Logger.getLogger( LogNames.APDSS );
    
    /**
     * Non-null, non-empty String if it is virtual.  The string will be like "compute_magnitude(B_xyz_gse)"
     */
    public static final String USER_PROP_VIRTUAL_FUNCTION= "FUNCTION";

    public static final String USER_PROP_VIRTUAL_COMPONENT_= "COMPONENT_";

    public static final Object VALUE_MIN= "MIN";
    public static final Object VALUE_MAX= "MAX";

    /**
     * returns the Entry that is convertible to double as a double.
     * @throws IllegalArgumentException for strings
     */
    private static double doubleValue(Object o, Units units, Object minmax ) {
        return doubleValue(o, units, Double.NaN, minmax ); 
    }

    /**
     * returns the Entry that is convertible to double as a double.
     * When there is an array, throw IllegalArgumentException.
     * Note this is used in CdfDataSource and other projects.
     * @param o the datum in double, int, String, array, etc.
     * @param units the units of the datum, result is returned in these units.
     * @param deflt the default value
     * @param minmax VALUE_MIN or VALUE_MAX or null.
     * @return the double or 
     * @throws IllegalArgumentException for strings
     */
    public static double doubleValue(Object o, Units units, double deflt, Object minmax ) {
        if (o == null) {
            return deflt;
        }
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        } else if (o instanceof String) {
            String s = (String) o;
            if (s.startsWith("CDF_PARSE_EPOCH(")) {  // hack for Onera CDFs
                try {
                    // hack for Onera CDFs
                    return units.parse(s.substring(16, s.length() - 1)).doubleValue(units);
                } catch (ParseException ex) {
                    logger.log(Level.FINE, "unable to parse {0}", o);
                    return deflt;
                }
            } else {
                try {
                    return units.parse(DataSourceUtil.unquote((String) o)).doubleValue(units);
                } catch (ParseException ex) {
                    try {
                        return Double.parseDouble((String) o);
                    } catch (NumberFormatException ex2) {
                        logger.log(Level.FINE, "unable to parse {0}", o);
                        return deflt;
                    }
                }
            }
        } else {
            Class c = o.getClass();
            if (c.isArray()) {
                if (units == Units.cdfEpoch && Array.getLength(o) == 2) { // kludge for Epoch16
                    double cdfEpoch = Array.getDouble(o, 0) * 1000 + Array.getDouble(o, 1) / 1e9;
                    Units.cdfEpoch.createDatum(cdfEpoch);
                    return cdfEpoch;
                } else {
                    double v= Array.getDouble(o, 0);
                    int n= Array.getLength(o);
                    for ( int i=1; i<n; i++ ) {
                        if ( minmax==VALUE_MAX ) {
                            v= Math.max( v, Array.getDouble(o,i) );
                        } else if ( minmax==VALUE_MIN ) {
                            v= Math.min( v, Array.getDouble(o,i) );
                        } else {
                            throw new IllegalArgumentException("object is array: "+o+", and minmax is not set");
                        }
                    }
                    return v;
                }
            } else {
                throw new RuntimeException("Unsupported Data Type: " + o.getClass().getName());
            }
        }
    }

    /**
     * Return the range from VALIDMIN to VALIDMAX, as an array of two numbers.  Note I found
     * as soon as I wrote this, that the CDF library has already converted them to doubles.  This is too
     * bad, and should be fixed at some point.  If the unit is an ordinal unit 
     * (see LABL_PTR_1), then return null.
     * Note QDataSet only allows times from 1000AD to 9000AD when Units are TimeLocationUnits.
     * Note this is used in CdfDataSource and other projects.  
     * @param attrs the ISTP attributes
     * @param units the units for this variable, used to interpret doubles.
     * @return the range.
     */
    private static Number[] getValidRangeDs(Map attrs, Units units) {
        Number min= (Number) attrs.get("VALIDMIN");
        Number max= (Number) attrs.get("VALIDMAX");
        
        if ( UnitsUtil.isTimeLocation(units) ) {
            DatumRange vrange= new DatumRange( 3.15569952E13, 2.840126112E14, Units.cdfEpoch ); // approx 1000AD to 9000AD
            if ( vrange.min().doubleValue(units)>min.doubleValue() ) min= vrange.min().doubleValue(units);
            if ( vrange.max().doubleValue(units)<max.doubleValue() ) max= vrange.max().doubleValue(units);
            if ( vrange.min().doubleValue(units)>max.doubleValue() ) max= vrange.max().doubleValue(units); //vap+cdaweb:ds=IM_HK_FSW&id=BF_DramMbeCnt&timerange=2005-12-18
        }
        if ( UnitsUtil.isNominalMeasurement(units) ) {
            logger.fine("valid range not used for ordinal units");
            return null;
        } else {
            return new Number[] { min, max };
        }
    }

    
    /**
     * Return the range from VALIDMIN to VALIDMAX.  If the unit is an ordinal unit (see LABL_PTR_1), then return null.
     * Note QDataSet only allows times from 1000AD to 9000AD when Units are TimeLocationUnits.
     * Note this is used in CdfDataSource and other projects.
     * @param attrs the ISTP attributes
     * @param units the units for this variable, used to interpret doubles.
     * @return the range.
     */
    public static DatumRange getValidRange(Map attrs, Units units) {
        double max = doubleValue(attrs.get("VALIDMAX"), units, Double.MAX_VALUE, VALUE_MAX );
        double min = doubleValue(attrs.get("VALIDMIN"), units, -1e29, VALUE_MIN ); //TODO: remove limitation
        if ( units.isFill(min) ) min= min / 100; // kludge because DatumRanges cannot contain fill.
        if ( UnitsUtil.isTimeLocation(units) ) {
            DatumRange vrange= new DatumRange( 3.15569952E13, 2.840126112E14, Units.cdfEpoch ); // approx 1000AD to 9000AD
            if ( vrange.min().doubleValue(units)>min ) min= vrange.min().doubleValue(units);
            if ( vrange.max().doubleValue(units)<max ) max= vrange.max().doubleValue(units);
            if ( vrange.min().doubleValue(units)>max ) max= vrange.max().doubleValue(units); //vap+cdaweb:ds=IM_HK_FSW&id=BF_DramMbeCnt&timerange=2005-12-18
        }
        if ( UnitsUtil.isNominalMeasurement(units) ) {
            logger.fine("valid range not used for ordinal units");
            return null;
        } else {
            if ( min<max ) {
                return DatumRange.newDatumRange(min, max, units);
            } else {
                String n= (String)attrs.get("NAME");
                if ( n!=null ) {
                    logger.log(Level.WARNING, "VALIDMIN and VALIDMAX has min value greater than max value: {0} > {1} for {2}", new Object[]{min, max, n});
                } else {
                    logger.log(Level.WARNING, "VALIDMIN and VALIDMAX has min value greater than max value: {0} > {1}", new Object[]{min, max});
                }
                return null;
            }
        }
    }

    /**
     * returns the range of the data by looking for the SCALEMIN/SCALEMAX params,
     * Checks for valid range when SCALETYP=log.
     * Note QDataSet only allows times from 1000AD to 9000AD when Units are TimeLocationUnits.
     */
    private static DatumRange getRange(Map attrs, Units units) {
        DatumRange range;

        double min, max;
        if (attrs.containsKey("SCALEMIN") && attrs.containsKey("SCALEMAX")) {
            max = doubleValue(attrs.get("SCALEMAX"), units, VALUE_MAX );
            min = doubleValue(attrs.get("SCALEMIN"), units, VALUE_MIN );
        } else {
            if (attrs.containsKey("SCALEMAX")) {
                max = doubleValue(attrs.get("SCALEMAX"), units, VALUE_MAX );
                min = 0; //TODO: really, this doesn't cause problems?
            } else if ( attrs.containsKey("SCALEMIN") && "log".equalsIgnoreCase( (String)attrs.get("SCALETYP") ) ){
                min= doubleValue(attrs.get("SCALEMIN"), units, VALUE_MIN );
                if ( min<=0 ) return null;
                double possibleMax= doubleValue(attrs.get("VALIDMAX"), units, VALUE_MAX );
                if ( possibleMax/min>1e1 && possibleMax/min<1e3 ) {
                    max= possibleMax;
                } else {
                    max= min * 1e3;
                }
            } else {
                // bug 1063 Don't use CDF valid range for typical range 
                logger.finer("SCALEMIN and SCALEMAX are missing");
                return null;
            }
        }
        if ( UnitsUtil.isRatioMeasurement(units) && units.isFill(min) ) min= min / 100 ;  // kludge because DatumRanges cannot contain -1e31
        if ( max<min ) max= Double.MAX_VALUE; //vap+cdaweb:ds=I2_AV_AME&id=ampl&timerange=1978-01-23+7:28:21+to+7:28:22
        if ( UnitsUtil.isTimeLocation(units) ) {
            DatumRange vrange= new DatumRange( 3.15569952E13, 2.840126112E14, Units.cdfEpoch ); // approx 1000AD to 9000AD
            if ( vrange.min().doubleValue(units)>min ) min= vrange.min().doubleValue(units);
            if ( vrange.max().doubleValue(units)<max ) max= vrange.max().doubleValue(units);
            if ( vrange.min().doubleValue(units)>max ) max= vrange.max().doubleValue(units); //vap+cdaweb:ds=IM_HK_FSW&id=BF_DramMbeCnt&timerange=2005-12-18
        }
         if ( UnitsUtil.isNominalMeasurement(units) ) {
            logger.fine("range not used for ordinal units");
            return null;
        } else {
            if ( min<max ) {
                return DatumRange.newDatumRange(min, max, units);
            } else {
                if ( Double.isFinite(min) && Double.isFinite(max) ) {
                    logger.log(Level.WARNING, "SCALEMIN and SCALEMAX has min value greater than max value: {0} > {1}", new Object[]{min, max});
                } else {
                    logger.log(Level.FINE, "SCALEMIN and SCALEMAX are NaN and NaN" );
                }
                return null;
            }
        }
    }

    /**
     * return null or the scale type if found.
     * @param attrs
     * @return
     */
    private static String getScaleType(Map attrs) {
        String type = null;
        if (attrs.containsKey("SCALETYP") && attrs.get("SCALETYP") instanceof String ) { // CAA STAFF
            type = String.valueOf( attrs.get("SCALETYP") ).toLowerCase();
        }
        return type;
    }

    /**
     * Interpret the ISTP metadata into QDataSet properties.
     * @param meta
     * @return 
     */
    @Override
    public Map<String, Object> properties(Map<String, Object> meta) {
        return properties( meta, true );
    }

    /**
     *
     * @param meta ISTP metadata from CDF files.
     * @param doRecurse if true, then allow recursion for other properties.
     * @return
     */
    private Map<String, Object> properties(Map<String, Object> meta, boolean doRecurse ) {

        Map attrs;
        if ( meta==null ) {
            logger.fine("null attributes, not expected to be seen");
            attrs= Collections.emptyMap();
        } else {
            attrs= new HashMap(meta);
        }

        String name= String.valueOf(attrs.get("FIELDNAM"));
        
        Map<String,Object> user= new LinkedHashMap<>();

        Map<String, Object> properties = new LinkedHashMap<>();

        String title= "";
        String s;
        s= (String)attrs.get("Source_name");
        if (  s!=null ) {
            int i= s.indexOf('>');
            if ( i>-1 ) {
                title= title + s.substring(0,i).trim();
            }
        }
        
        s= (String)attrs.get("Descriptor");
        if (  s!=null ) {
            int i= s.indexOf('>');
            if ( i>-1 ) {
                if ( title.length()>0 ) title= title+"/";
                title= title + s.substring(0,i).trim();
            }
        }

        if ( title.trim().length()>0 ) title= title+"  "; // add two spaces to delimit S/C name and instrument from description
        s= (String)attrs.get("CATDESC");
        if ( s!=null ) {
            if ( LatexToGranny.isLatex(s) ) {
                s= LatexToGranny.latexToGranny(s);
            }
            title= title + s.trim();
        }

        if ( title.trim().length()>0 ) properties.put( QDataSet.TITLE, title.trim() );
        
        s= (String)attrs.get("VAR_NOTES");
        if ( s!=null ) {
            properties.put( QDataSet.DESCRIPTION, s );
        }
        
        if (attrs.containsKey("DISPLAY_TYPE")) {
            String type = (String) attrs.get("DISPLAY_TYPE");
            int i= type.indexOf('>');
            String stype= i==-1 ? type : type.substring(0,i);
            if ( !stype.equals(stype.toLowerCase() ) ) {
                logger.log(Level.FINE, "DISPLAY_TYPE should be lower case ({0})", type);
            }
            if ( stype.equalsIgnoreCase("spectrogram") ) {
                type= "spectrogram";
            } else if ( stype.equalsIgnoreCase("time_series" ) || type.equalsIgnoreCase("stack_plot") ) {
                type= "time_series"; // TODO: this will be "series" after reduction is put in.
            }
            properties.put(QDataSet.RENDER_TYPE, type);
        }

        if (attrs.containsKey("VIRTUAL") ) {
            String v= (String) attrs.get("VIRTUAL");
            String function= (String)attrs.get("FUNCTION");
            user.put( IstpMetadataModel.USER_PROP_VIRTUAL_FUNCTION, function );
            for ( int i=0; i<4; i++ ) {
                if ( attrs.get("COMPONENT_"+i)!=null ) {
                    user.put( IstpMetadataModel.USER_PROP_VIRTUAL_COMPONENT_ + i, attrs.get("COMPONENT_"+i) );
                } else {
                    break;
                }
            }
            
        }

        Units units=null;
        String sunits= "";
        if (attrs.containsKey("UNITS")) {
            sunits = String.valueOf( attrs.get("UNITS") );
            if ( LatexToGranny.isLatex(sunits) ) {
                sunits= LatexToGranny.latexToGranny(sunits);
            }
        } else {
            logger.log(Level.FINE, "UNITS are missing for {0}", name );
        }
        
        if ( sunits.equals("") && attrs.containsKey("UNIT_PTR_VALUE") ) {
            QDataSet ss= (QDataSet)attrs.get("UNIT_PTR_VALUE");
            if ( ss.rank()==1 ) {
                double s0= ss.value(0);
                boolean canUse= true;
                for ( int i=1; i<ss.length(); i++ ) {
                    double s1= ss.value(1);
                    if ( s1!=s0 ) {
                        logger.fine("unable to use units because of implementation");
                        canUse= false;
                    }
                }
                if ( canUse ) {
                    Units eu= SemanticOps.getUnits(ss);
                    sunits= eu.createDatum(s0).toString();
                }
            } else {
                logger.fine("unable to use units because of rank");
            }
        }
        
        sunits= sunits.trim();
        if ( LatexToGranny.isLatex(sunits) ) {
            sunits= LatexToGranny.latexToGranny(sunits);
        }
        
        String siConversion= (String)attrs.get("SI_conversion");
        if ( siConversion!=null ) {
            if ( siConversion.endsWith("seconds") ) {
                int i= siConversion.indexOf(">");
                double d= Double.parseDouble( siConversion.substring(0,i) );
                if ( d==1e-9 ) {
                    units= Units.cdfTT2000;
                    sunits= units.toString();
                }
            }
        }
        
        if ( units==null ) {
            try {
                units = Units.lookupUnits(DataSourceUtil.unquote(sunits));
            } catch (IllegalArgumentException e) {
                units = Units.dimensionless;
            }
        }

        // we need to distinguish between ms and epoch times.
        boolean isMillis=false;
        Object ovalidMax= attrs.get("VALIDMAX");
        Object ovalidMin= attrs.get("VALIDMIN");
        if ( ovalidMax!=null && ovalidMin!=null 
                && ovalidMax instanceof Number && ovalidMin instanceof Number
                && units==Units.milliseconds ) {
            double validMax= ((Number)ovalidMax).doubleValue();
            double validMin= ((Number)ovalidMin).doubleValue();
            isMillis= validMin<validMax && validMin < 1e8 && validMax < 1e12 ; // java cdf would get zeros for these  rbsp-b_HFR-waveform_emfisis-L1_20110405154808_v1.1.1.cdf?HFRsamples
        }

        Object ofv= attrs.get( "FILLVAL" );
        double dv= doubleValue( ofv, units, Double.NaN, IstpMetadataModel.VALUE_MIN );
        if ( !Double.isNaN(dv) ) {
            properties.put(QDataSet.FILL_VALUE, dv );
        }

        Object olablaxis= attrs.get("LABLAXIS");
        if ( olablaxis!=null && !(olablaxis instanceof String) ) {
            logger.log(Level.WARNING, "LABLAXIS should be type String: {0}", olablaxis);
        }
        String label = olablaxis==null ? null : String.valueOf( attrs.get("LABLAXIS") );
            
        boolean isEpoch = ( units == Units.milliseconds && !isMillis ) 
                || "Epoch".equals(attrs.get(QDataSet.NAME)) 
                || "Epoch".equalsIgnoreCase(DataSourceUtil.unquote( String.valueOf(attrs.get("LABLAXIS"))) );
        if (isEpoch) {
            if ( ofv!=null && ofv instanceof Long ) {
                units= Units.cdfTT2000;
                properties.put(QDataSet.FILL_VALUE, ofv );
            } else {
                if ( UnitsUtil.isTimeLocation(units) ) {
                    // do nothing, assume that they know what they are doing.  
                    // (Steven's NetCDF file, https://sourceforge.net/p/autoplot/feature-requests/724/).
                } else {
                    units = Units.cdfEpoch;
                }
            }
        } else {
            Object oslice1= attrs.get("slice1");
            if ( oslice1!=null && !(oslice1 instanceof String ) ) {
                logger.warning("internal error, slice1 should be string");
            }
            String sslice1= (String) oslice1;
            if ( sslice1!=null ) {
                int islice= Integer.parseInt(sslice1);
                Object o = (Object) attrs.get("slice1_labels");
                if ( ! ( o instanceof QDataSet ) ) {
                    if ( o!=null ) {
                        logger.log(Level.WARNING, "slice1_labels property of {0} should be a QDataSet", name);
                    }
                } else {
                    if ( attrs.get("DEPEND_2")==null ) {                        
                        QDataSet lablDs= (QDataSet) attrs.get("slice1_labels");
                        if ( lablDs!=null ) { // TODO: I think this is trivially true.
                            label= lablDs.slice(islice).svalue();
                        }
                    }
                }
            } 
            Object oslice2= attrs.get("slice2");
            String sslice2= (String) oslice2;
            if ( sslice2!=null ) {
                int islice= Integer.parseInt(sslice2);
                Object o = (Object) attrs.get("slice2_labels");
                if ( ! ( o instanceof QDataSet ) ) {
                    if ( o!=null ) {
                        logger.log(Level.WARNING, "slice2_labels property of {0} should be a QDataSet", name);
                    }
                } else {
                    if ( attrs.get("DEPEND_3")==null ) {                        
                        QDataSet lablDs= (QDataSet) attrs.get("slice2_labels");
                        if ( lablDs!=null ) { // TODO: I think this is trivially true.
                            label= lablDs.slice(islice).svalue();
                        }
                    }
                }
            }
            if ( label!=null ) {
                if ( LatexToGranny.isLatex(label) ) {
                    label= LatexToGranny.latexToGranny(label);
                }
            }
            if (label == null) {
                label = sunits;
            } else {
                if (!sunits.equals("")) {
                    label= label.trim();
                    label += " (" + sunits + ")";
                }
            }
            properties.put(QDataSet.LABEL, label);
        }
        
        properties.put(QDataSet.UNITS, units);

        if ( UnitsUtil.isTimeLocation(units) && !doRecurse && properties.containsKey(QDataSet.LABEL) ) {
            properties.remove(QDataSet.LABEL);
            properties.remove(QDataSet.TITLE);
        }
        
        try {

            DatumRange range = getRange(attrs, units);
            if (!attrs.containsKey("COMPONENT_0")) { // Themis kludge
                if ( range!=null ) properties.put(QDataSet.TYPICAL_MIN, range.min().doubleValue(units));
                if ( range!=null ) properties.put(QDataSet.TYPICAL_MAX, range.max().doubleValue(units));

                range = getValidRange(attrs, units);
                if ( range!=null ) {
                    properties.put(QDataSet.VALID_MIN, range.min().doubleValue(units));
                    properties.put(QDataSet.VALID_MAX, range.max().doubleValue(units));
                }
                
//                Number[] rangeDs = getValidRangeDs(attrs, units);
//                if ( rangeDs!=null ) {
//                    properties.put(QDataSet.VALID_MIN, rangeDs[0]);
//                    properties.put(QDataSet.VALID_MAX, rangeDs[1]);
//                }

                if ( ofv!=null && ofv instanceof Number ) {
                    Number fillVal= (Number) ofv;
                    double fillVald= fillVal.doubleValue();
                    if( range!=null && fillVald>=range.min().doubleValue(units) && fillVald<=range.max().doubleValue(units) ) {
                        properties.put( QDataSet.FILL_VALUE, fillVal );
                    }
                } else if ( ofv!=null && ofv.getClass().isArray() ) {
                    // try to reduce it to one number.
                    Number fillVal= (Number) Array.get(ofv,0);
                    int n= Array.getLength(ofv);
                    for ( int i=1; i<n; i++ ) {
                        if ( ! Array.get(ofv,i).equals(fillVal) ) {
                            fillVal= Double.NaN;
                        }
                    }
                    double fillVald= fillVal.doubleValue();
                    if( range!=null && fillVald>=range.min().doubleValue(units) && fillVald<=range.max().doubleValue(units) ) {
                        properties.put( QDataSet.FILL_VALUE, fillVal );
                    }
                    
                }

            }

            properties.put(QDataSet.SCALE_TYPE, getScaleType(attrs));
        } catch (IllegalArgumentException ex) {
            logger.log( Level.SEVERE, ex.getMessage(), ex );

        }

        if ( doRecurse ) {
            for (int i = 0; i < QDataSet.MAX_RANK; i++) {
                String key = "DEPEND_" + i;
                Object o= attrs.get(key);
                if ( o==null ) continue;
                if ( !( o instanceof Map ) ) {
                    //new RuntimeException("String where Map was expected").printStackTrace();
                    //TODO: track this down: vap+cdf:https://cdaweb.gsfc.nasa.gov/istp_public/data/fast/ies/1998/fa_k0_ies_19980102_v02.cdf?ion_0
                    continue;
                }
                Map<String, Object> props = (Map<String, Object>) o;
                for ( int j=0; j<QDataSet.MAX_RANK; j++ ) {
                    if ( props.containsKey("DEPEND_"+j ) ) {
                        props.remove("DEPEND_"+j); // remove DEPEND property from DEPEND property.
                    }
                }
                properties.put(key, properties(props,false));
            }
        }

        if ( !user.isEmpty() ) {
            properties.put( QDataSet.USER_PROPERTIES, user );
        }
        
        return properties;

    }

    @Override
    public String getLabel() {
        return "ISTP-CDF";
    }
    
    /**
     * RBSP/ECT/MAGEIS has files where typically the energy labels are
     * constant, but they must be rank 2 because they can vary.  Seth 
     * wishes the Energy labels be shown when they are constant, and this 
     * is a quick check to detect the case.  The data can also contain
     * fill records and channels that contain all fill.
     * @param depDs 
     * @return the rank 1 dataset or null.
     */
    public static MutablePropertyDataSet maybeReduceRank2(MutablePropertyDataSet depDs) {
        QDataSet wds= SemanticOps.weightsDataSet(depDs);
        int j= -1;
        
        int i0= 0;
        
        ArrayDataSet resulta= ArrayDataSet.copy( depDs.slice(0) ); // get all the properties of the dataset.
        
        for ( j=i0; j<wds.length(0); j++ ) {
            QDataSet ex;
            ArrayDataSet test= ArrayDataSet.copy( DataSetOps.slice1(depDs,j) );
            test.putProperty( QDataSet.BIN_PLUS, null );
            test.putProperty( QDataSet.BIN_MINUS, null );
            test.putProperty( QDataSet.DELTA_PLUS, null );
            test.putProperty( QDataSet.DELTA_MINUS, null );
            ex= Ops.extent( test );
            if ( ex.value(0)!=ex.value(1) ) {
                //DataSourceUtil.dumpToFile( test, "/tmp/foo.qds" );
                return null;
            } else {
                resulta.putValue( j, ex.value(0) );
            }
        }
        
        return resulta;
    }

}
