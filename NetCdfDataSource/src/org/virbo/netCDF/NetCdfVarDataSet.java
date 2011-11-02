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
import org.das2.datum.Units;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.AbstractDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.dsops.Ops;
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

        try {
            data= (double[])a.get1DJavaArray( Double.class );
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
                    QDataSet depend0= create( dv , ncfile, new NullProgressMonitor() );
                    properties.put( "DEPEND_"+ir, depend0 );
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
        
        if ( attributes.containsKey("_FillValue" ) ) {
            double fill= Double.parseDouble( (String) attributes.get("_FillValue") );
            for ( int i=0; i<data.length; i++ ) {
                if ( data[i]==fill ) data[i]= -1e31;
            }
        }
        
        if ( isCoordinateVariable ) {
            properties.put( QDataSet.CADENCE, DataSetUtil.guessCadenceNew(this,null) );
        }
        mon.finished();
        
    }
    
    
    public int rank() {
        return v.getRank();
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
