/*
 * NetCdfVarDataSet.java
 *
 * Created on April 4, 2007, 12:22 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.netCDF;

import edu.uiowa.physics.pw.das.datum.Datum;
import edu.uiowa.physics.pw.das.datum.DatumRange;
import edu.uiowa.physics.pw.das.datum.TimeUtil;
import edu.uiowa.physics.pw.das.datum.Units;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.virbo.dataset.AbstractDataSet;
import org.virbo.dataset.QDataSet;
import ucar.nc2.Variable;
import ucar.nc2.Attribute;

/**
 * wraps a rank 1 netCDF variable to present it as a QDataSet.
 *
 * unit  "matlab datenum" are decimal days. 1 corresponds to 1-Jan-0000.
 *
 *
 * @author jbf
 */
public class NetCdfVarDataSet extends AbstractDataSet {
    Variable v;
    double[] data;
    double[][] data2;
    int[] shape;
    
    public NetCdfVarDataSet( Variable variable ) throws IOException {
        this.v= variable;
                
        ucar.ma2.Array a = v.read();
        data= (double[])a.get1DJavaArray( Double.class );
        
        shape= v.getShape();
        properties.put( QDataSet.NAME, variable.getName() );
        if ( shape.length>1 ) properties.put( QDataSet.QUBE, Boolean.TRUE );
        
        for ( int ir=0; ir<a.getRank(); ir++ ) {
            ucar.nc2.Dimension d= v.getDimension(ir);
            List l= d.getCoordinateVariables();
            if ( l.size()>1 ) throw new IllegalArgumentException("Huh?");
            for ( int i=0; i<l.size(); i++ ) {
                Variable dv= (Variable) l.get(0);
                if ( dv!=variable ) {
                    QDataSet depend0= new NetCdfVarDataSet( dv );
                    properties.put( "DEPEND_"+ir, depend0 );
                }
            }
        }
        
        Map attributes= new HashMap();
        
        List attrs= v.getAttributes();
        for ( Iterator i= attrs.iterator(); i.hasNext(); ) {
            Attribute attr= (Attribute) i.next();
            if ( !attr.isArray() ) {
                if ( attr.isString() ) {
                    attributes.put( attr.getName(), attr.getStringValue() );
                } else {
                    attributes.put( attr.getName(), String.valueOf( attr.getNumericValue() ) );
                }
            }
        }
        
        
        if ( attributes.containsKey("units") ) {
            String unitsString= (String)attributes.get("units");
            
            if ( unitsString.contains(" since ") ) {
                Units out= Units.t2000;
                
                String[] ss= unitsString.split(" since ");
                
                Units offsetUnits;
                double scale; // multiply by this to convert to seconds
                if ( ss[0].equals("seconds" ) ) {
                    offsetUnits= Units.seconds;
                    scale= 1.;
                } else if ( ss[0].equals("days") ) {
                    offsetUnits= Units.days;
                    scale= 86400.;
                } else {
                    throw new IllegalArgumentException("units not supported: "+ss[0]+" in "+ unitsString );
                }
                Datum base= TimeUtil.createValid(ss[1] );
                
                double offset= base.doubleValue( out );
                for ( int i=0; i<data.length; i++ ) {
                    data[i]= data[i] * scale + offset;
                }
                properties.put( QDataSet.UNITS, out );
                properties.put( QDataSet.MONOTONIC, Boolean.TRUE );
            }
            //result.put( QDataSet.UNITS, Units.us2000 );
        }
        
        if ( attributes.containsKey("_FillValue" ) ) {
            double fill= Double.parseDouble( (String) attributes.get("_FillValue") );
            for ( int i=0; i<data.length; i++ ) {
                if ( data[i]==fill ) data[i]= -1e31;
            }
        };
        
        //Map p= SPDFUtil.interpretProps( attributes );
        //Map p= NetcdfMetadataModel.interpretProps( attributes );
        //properties.putAll( p );
        
        DatumRange dr= (DatumRange) properties.get( QDataSet.VALID_RANGE );
        if ( dr!=null ) {
            Units u= dr.getUnits();
            double vmin= dr.min().doubleValue(u);
            double vmax= dr.max().doubleValue(u);
            for ( int i=0; i<data.length; i++ ) {
                if ( data[i]<=vmin || data[i]>=vmax ) data[i]= u.getFillDouble();
            }
        }
    }
    
    
    public int rank() {
        return v.getRank();
    }
    
    public double value(int i) {
        return data[i];
    }
    
    public double value( int i, int j ) {
        int index= j + shape[1] * i;
        return data[ index ];
    }
    
    public double value( int i, int j, int k ) {
        //int index= i + shape[0] * j + shape[0] * shape[1] * k;
        int index= k + shape[2] * j + shape[2] * shape[1] * i;
        if ( index>=data.length) {
            throw new IllegalArgumentException("how");
        }
        return data[index];
    }
    
    public int length() {
        return shape[0];
    }
    
    public int length( int dim ) {
        return shape[1];
    }
    
    public int length( int dim0, int dim1 ) {
        return shape[2];
    }
    
    
}
