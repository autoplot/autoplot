/*
 * SPDFUtil.java
 *
 * Created on February 2, 2007, 10:54 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.autoplot.dods;

import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import org.das2.qds.QDataSet;

/**
 * static methods for helping to use the SPDF dods server.
 *
 * @author jbf
 */
public class SPDFUtil {
    /**
     * set up the DodsAdapter based on the metadata returned from the dods server,
     * interpreting the metadata provided by the SPDF server.
     */
    public static void interpretAttributes( DodsAdapter da ) throws IOException {
        MetaDataScraper meta= new MetaDataScraper( );
        
        meta.parseURL( new URL( da.getSource().toString()+".html" ) );
        
        Map m= interpretProps( meta.getAttr(da.getVariable()) );
        
        if ( m.containsKey(QDataSet.UNITS) ) {
            da.setUnits( (Units) m.get(QDataSet.UNITS) );
        }
        
        if ( m.containsKey(QDataSet.VALID_MIN ) || m.containsKey(QDataSet.VALID_MAX) ) {
            Double min= (Double) m.get(QDataSet.VALID_MIN );
            Double max= (Double) m.get(QDataSet.VALID_MAX );
            if ( min==null ) min= Double.NEGATIVE_INFINITY;
            if ( max==null ) max= Double.POSITIVE_INFINITY;
            da.setValidRange( min, max );
        }
        
        StringBuffer constraint= new StringBuffer("?");
        
        constraint.append( da.getVariable() );
        
        int[] ii= meta.getRecDims( da.getVariable() );
        for ( int i=0; i<ii.length; i++ ) {
            constraint.append( "[0:1:"+ii[i]+"]" );
        }
        
        da.putAllProperties( m );
        
        for ( int i=0; i<3; i++ ) {
            String dkey= "DEPEND_"+i+"_ID";
            if ( m.containsKey( dkey ) ) {
                String var= (String) m.get(dkey);
                da.setDependName( i, var );
                
                int[] ii2= meta.getRecDims(var);
                constraint.append(",").append( var ).append( "[0:1:"+ii2[0]+"]" );
                
                Map m0= interpretProps( meta.getAttr(var) );
                if ( m0.containsKey(QDataSet.UNITS) ) {
                    da.setDimUnits( i,  (Units) m0.get(QDataSet.UNITS) );
                }
                
                da.setDimProperties( i, m0 );
            }
        }
        
        
        da.setConstraint( constraint.toString() );
    }
    
    
    /**
     * return the properties in a canonical way.  Presumably this will be done for other data sources, to unify the metadata for use with das2.
     * For keys supported, see QDataSet.
     * 
     * 
     * @return a Map of the properties.
     */
    public static Map interpretProps( Map attr ) {
        
        attr= new HashMap( attr ); // make a copy to be courteous.
        
        Map result= new HashMap();
        
        Units units;
        try {
            units= Units.getByName( (String)attr.remove( "UNITS" ) );
        } catch ( IllegalArgumentException e ) {
            units= Units.dimensionless;
        }
        
        if ( units==Units.milliseconds && ( "Epoch".equalsIgnoreCase( (String)attr.get("LABLAXIS") ) ) ) {
            units= Units.cdfEpoch;
        }
        
        result.put( QDataSet.UNITS, units );
        
        double v0, v1;
        
        try {
            v0= getDouble( attr.remove("SCALEMIN"), units );
            v1= getDouble(  attr.remove("SCALEMAX"), units );
            result.put( "TYPICAL_RANGE", new DatumRange( v0, v1, units ) );
        } catch ( NullPointerException e ) {
            // do nothing;
        }
        
        try {
            v0= getDouble(attr.remove("VALIDMIN"), units );
            v1=getDouble( attr.remove("VALIDMAX"), units );
            result.put( "VALID_RANGE", new DatumRange( v0, v1, units ) );
        }catch ( NullPointerException e ) {
            // do nothing;
        }
        
        String s= (String) attr.remove( "LABLAXIS" );
        if ( s!=null ) {
            result.put( "LABEL", s );
        }
        
        try {
            v0= getDouble( attr.remove( "FILLVAL" ), units );
            result.put( "FILL", new Double(v0) );
        } catch ( NullPointerException e ) {
            //do nothing
        }
        
        s= (String) attr.remove("DEPEND_0" );
        if ( s!=null ) {
            result.put( "DEPEND_0_ID", s );
        }

        s= (String) attr.remove("DEPEND_1" );
        if ( s!=null ) {
            result.put( "DEPEND_1_ID", s );
        }

        s= (String) attr.remove("DEPEND_2" );
        if ( s!=null ) {
            result.put( "DEPEND_2_ID", s );
        }

        // throw in the rest for human consumption.
        result.putAll( attr );
        
        return result;
    }

    /**
     * @throws NullPointerException
     */
    private static double getDouble( Object o, Units units ) {
        if ( o==null ) throw new NullPointerException("o was null" );
        if ( o instanceof Float ) {
            return ((Float)o).doubleValue();
        } else if ( o instanceof Double ) {
            return ((Double)o).doubleValue();
        } else if ( o instanceof Short ) {
            return ((Short)o).doubleValue();
        } else if ( o instanceof String ) {
            try {
                return units.parse((String)o).doubleValue(units);
            } catch (ParseException ex) {
                throw new IllegalArgumentException("unable to parse "+o);
            }
        } else {
            throw new RuntimeException( "Unsupported Data Type: "+o.getClass().getName() );
        }
    }
}
