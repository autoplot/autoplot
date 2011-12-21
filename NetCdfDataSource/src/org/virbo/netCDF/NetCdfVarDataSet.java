/*
 * NetCdfVarDataSet.java
 *
 * Created on April 4, 2007, 12:22 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.netCDF;

import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.Units;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.das2.datum.TimeParser;
import org.das2.datum.UnitsConverter;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.AbstractDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.dsops.Ops;
import org.virbo.metatree.IstpMetadataModel;
import ucar.nc2.Variable;
import ucar.nc2.Attribute;
import ucar.nc2.dataset.NetcdfDataset;

/**
 * wraps a rank 1 netCDF variable to present it as a QDataSet.
 *
 * @author jbf
 */
public class NetCdfVarDataSet extends AbstractDataSet {
    Variable v;
    double[] data;
    int[] shape;

    public static NetCdfVarDataSet create( Variable variable , NetcdfDataset ncfile, ProgressMonitor mon ) throws IOException {
        NetCdfVarDataSet result = new NetCdfVarDataSet(  );
        result.read( variable, ncfile, mon );
        return result;
    }

    private NetCdfVarDataSet(  )  {
        
    }

    //TODO: NetCDF has a variable.slice().
    
    private void read( Variable variable , NetcdfDataset ncfile, ProgressMonitor mon )  throws IOException {
        this.v= variable;
        if ( !mon.isStarted() ) mon.started(); //das2 bug: monitor blinks if we call started again here
        mon.setProgressMessage( "reading "+v.getNameAndDimensions() );
        ucar.ma2.Array a = v.read();

        char[] cdata=null;
        try {
            if ( a.getElementType()==char.class ) { // NASA/Goddard formats times as ISO8601 times.
                cdata= (char[])a.get1DJavaArray( char.class );
            } else {
                data= (double[])a.get1DJavaArray( Double.class );
            }
        } catch ( ClassCastException ex ) {
            throw new IllegalArgumentException("data cannot be converted to numbers",ex);
        }
       
        shape= v.getShape();
        properties.put( QDataSet.NAME, Ops.safeName(variable.getName()) );
        if ( shape.length>1 ) properties.put( QDataSet.QUBE, Boolean.TRUE );
        
        boolean isCoordinateVariable= false;
        
        for ( int ir=0; ir<a.getRank(); ir++ ) {
            ucar.nc2.Dimension d= v.getDimension(ir);

            Variable cv = ncfile.findVariable(d.getName());
            if ((cv != null) && cv.isCoordinateVariable()) {
                Variable dv= cv;
                if ( dv!=variable && dv.getRank()==1 ) {
                    mon.setProgressMessage( "reading "+dv.getNameAndDimensions() );
                    QDataSet dependi= create( dv , ncfile, new NullProgressMonitor() );
                    properties.put( "DEPEND_"+ir, dependi );
                } else {
                    isCoordinateVariable= true;
                }
            }
        }
        
        Map<String,Object> attributes= new HashMap();

        mon.setProgressMessage("reading attributes");

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
                Units u;
                try {
                    u = SemanticOps.lookupTimeUnits(unitsString);
                } catch (ParseException ex) {
                    throw new RuntimeException(ex);
                }
                
                properties.put( QDataSet.UNITS, u );
                properties.put( QDataSet.MONOTONIC, Boolean.TRUE );
            }
        }
        
        if ( data==null && attributes.containsKey("VAR_TYPE") && shape.length==2 ) { // NASA/Goddard translation service formats Times as strings, check for this.
            data= new double[shape[0]];
            String ss= new String(cdata);
            for ( int i=0; i<shape[0]; i++ ) {
                int n= i*shape[1];
                String s= ss.substring( n, n+shape[1] );
                try {
                    data[i] = Units.us2000.parse(s).doubleValue(Units.us2000);
                } catch (ParseException ex) {
                    data[i]= Units.us2000.getFillDouble();
                }
            }
            properties.put(QDataSet.UNITS,Units.us2000);
            shape= new int[] { shape[0] };
        } else {
            data= (double[])a.get1DJavaArray( Double.class ); // whoops, it wasn't NASA/Goddard data after all.
        }

        if ( attributes.containsKey("_FillValue" ) ) {
            double fill= Double.parseDouble( (String) attributes.get("_FillValue") );
            for ( int i=0; i<data.length; i++ ) {
                if ( data[i]==fill ) data[i]= -1e31;  //TODO: this is probably not necessary now.
            }
        }


        if ( attributes.containsKey("VAR_TYPE") ) { // LANL want to create HDF5 files with ISTP metadata
            properties.put( QDataSet.METADATA_MODEL, QDataSet.VALUE_METADATA_MODEL_ISTP );
            Map<String,Object> istpProps= new IstpMetadataModel().properties(attributes);
            if ( properties.get( QDataSet.UNITS )==Units.us2000 ) {
                UnitsConverter uc= UnitsConverter.getConverter(Units.cdfEpoch, Units.us2000 );
                if ( istpProps.containsKey(QDataSet.VALID_MIN) ) istpProps.put( QDataSet.VALID_MIN, uc.convert( (Number)istpProps.get(QDataSet.VALID_MIN ) ) );
                if ( istpProps.containsKey(QDataSet.VALID_MAX) ) istpProps.put( QDataSet.VALID_MAX, uc.convert( (Number)istpProps.get(QDataSet.VALID_MAX ) ) );
                if ( istpProps.containsKey(QDataSet.TYPICAL_MIN) ) istpProps.put( QDataSet.TYPICAL_MIN, uc.convert( (Number)istpProps.get(QDataSet.TYPICAL_MIN ) ) );
                if ( istpProps.containsKey(QDataSet.TYPICAL_MAX) ) istpProps.put( QDataSet.TYPICAL_MAX, uc.convert( (Number)istpProps.get(QDataSet.TYPICAL_MAX ) ) );
                istpProps.put(QDataSet.UNITS,Units.us2000);
            }
            properties.putAll(istpProps);

            for ( int i=0; i<QDataSet.MAX_RANK; i++ ) {
                String s= (String) attributes.get("DEPEND_"+i);
                if ( s!=null ) {
                    Variable dv= ncfile.findVariable(s);
                    if ( dv!=null && dv!=variable ) {
                        QDataSet dependi= create( dv , ncfile, new NullProgressMonitor() );
                        properties.put( "DEPEND_"+i, dependi );
                    }
                }
            }
        }
        
        if ( isCoordinateVariable ) {
            properties.put( QDataSet.CADENCE, DataSetUtil.guessCadenceNew(this,null) );
        }
        mon.finished();
        
    }
    
    
    public int rank() {
        return shape.length;
    }
    
    @Override
    public double value(int i) {
        return data[i];
    }
    
    @Override
    public double value( int i, int j ) {
        int index= j + shape[1] * i;
        return data[ index ];
    }
    
    @Override
    public double value( int i, int j, int k ) {
        //int index= i + shape[0] * j + shape[0] * shape[1] * k;
        int index= k + shape[2] * j + shape[2] * shape[1] * i;
        if ( index>=data.length) {
            throw new IllegalArgumentException("how");
        }
        return data[index];
    }

    @Override
    public double value( int i, int j, int k, int l ) {
        int index= l + shape[3] * k  + shape[3] * shape[2] * j + shape[3] * shape[2] * shape[1] * i;
        if ( index>=data.length) {
            throw new IllegalArgumentException("how");
        }
        return data[index];
    }
    
    @Override
    public int length() {
        return shape[0];
    }
    
    @Override
    public int length( int dim ) {
        return shape[1];
    }
    
    @Override
    public int length( int dim0, int dim1 ) {
        return shape[2];
    }
    
    @Override
    public int length( int dim0, int dim1, int dim2 ) {
        return shape[3];
    }
    
}
