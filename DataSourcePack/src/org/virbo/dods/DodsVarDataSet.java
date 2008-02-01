/*
 * DodsVarDataSet.java
 *
 * Created on January 29, 2007, 8:42 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.dods;

import dods.dap.BaseType;
import dods.dap.DArray;
import dods.dap.DArrayDimension;
import dods.dap.DString;
import edu.uiowa.physics.pw.das.datum.Units;
import java.text.ParseException;
import java.util.Enumeration;
import java.util.HashMap;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.WritableDataSet;

/**
 *
 * @author jbf
 */
public abstract class DodsVarDataSet implements WritableDataSet {
    
    DArray array;
    int rank;
    int[] dimSizes= new int[4];
    HashMap properties;
    
    /** Creates a new instance of DodsVarDataSet */
    public DodsVarDataSet( DArray array ) {
        this.array=array;
        int idim=0;
        for ( Enumeration enumm= array.getDimensions(); enumm.hasMoreElements(); ) {
            DArrayDimension dd= (DArrayDimension) enumm.nextElement();
            dimSizes[idim++]= dd.getSize();
        }
        rank= array.numDimensions();
        properties= new HashMap();
    }
    
    public String toString() {
        StringBuffer dimStr= new StringBuffer( ""+dimSizes[0] );
        for ( int i=1; i<rank; i++ ) dimStr.append(","+dimSizes[i]);
        String u= String.valueOf( properties.get(UNITS) );
        if ( u.equals("null") || u=="" ) u="dimensionless";
        return "dataSet["+dimStr.toString()+"] ("+u+")";
    }
    
    
    public int rank() {
        return rank;
    }
    
    abstract public double value(int i);
    abstract public double value(int i0, int i1);
    abstract public double value(int i0, int i1, int i2);
    abstract public double value(int i0, int i1, int i2, int i3 );
    
    public Object property(String name) {
        return properties.get(name);
    }
    
    public Object property(String name, int i) {
        return property(name);
    }
    
    public Object property(String name, int i0, int i1) {
        return property(name);
    }
    
    public Object property(String name, int i0, int i1, int i2 ) {
        return property(name);
    }
    
    public void putProperty( String name, Object value ) {
        properties.put( name, value );
    }
    
    public void putProperty(String name, int i, Object value ) {
        putProperty( name, value );
    }
    
    public void putProperty(String name, int i0, int i1, Object value ) {
        putProperty( name, value );
    }
    
    public int length() {
        return dimSizes[0];
    }
    
    public int length(int i) {
        return dimSizes[1];
    }
    
    public int length( int i0, int i1 ) {
        return dimSizes[2];
    }
    
    public static class Int32Array extends DodsVarDataSet {
        int[] back;
        double scaleFactor=1.0;
        double addOffset= 0.0;
        double validMin, validMax;
        
        public Int32Array( DArray array, HashMap properties ) {
            super(array);
            back= (int[]) array.getPrimitiveVector().getInternalStorage();
            if ( properties.get("add_offset")!=null ) addOffset= ((Double)properties.get("add_offset")).doubleValue();
            if ( properties.get("scale_factor")!=null ) scaleFactor= ((Double)properties.get("scale_factor")).doubleValue();
            if ( properties.get("valid_range")!=null ) {
                String s= (String)properties.get("valid_range");
                String[] ss= s.split(",");
                validMin= Double.parseDouble(ss[0]);
                validMax= Double.parseDouble(ss[1]);
            } else {
                validMin= Double.NEGATIVE_INFINITY;
                validMax= Double.POSITIVE_INFINITY;
            }
            this.properties= properties;
        }
        
        private final double doubleValue( int val ) {
            double r= val * scaleFactor + addOffset;
            return r >= validMin && r<=validMax ? r : Units.dimensionless.getFillDouble();
        }
        
        public double value(int i ) {
            return doubleValue(back[i]);
        }
        
        public double value(int i0, int i1 ) {
            return doubleValue(back[i0*dimSizes[1]+i1]);
        }
        
        public double value(int i0, int i1, int i2 ) {
            return doubleValue(back[i0*dimSizes[1]*dimSizes[2] + i1*dimSizes[2] + i2] );
        }
        
        public double value(int i0, int i1, int i2, int i3 ) {
            return doubleValue(back[ i0*dimSizes[1]*dimSizes[2]*dimSizes[3] + i1*dimSizes[1]*dimSizes[2] + i2*dimSizes[2] + i3 ] );
        }
    }
    
    public static class Int16Array extends DodsVarDataSet {
        short[] back;
        double scaleFactor=1.0;
        double addOffset= 0.0;
        double validMin, validMax;
        
        public Int16Array( DArray array, HashMap properties ) {
            super(array);
            back= (short[]) array.getPrimitiveVector().getInternalStorage();
            if ( properties.get("add_offset")!=null ) addOffset= ((Double)properties.get("add_offset")).doubleValue();
            if ( properties.get("scale_factor")!=null ) scaleFactor= ((Double)properties.get("scale_factor")).doubleValue();
            if ( properties.get("valid_range")!=null ) {
                String s= (String)properties.get("valid_range");
                String[] ss= s.split(",");
                validMin= Double.parseDouble(ss[0]);
                validMax= Double.parseDouble(ss[1]);
            } else {
                validMin= Double.NEGATIVE_INFINITY;
                validMax= Double.POSITIVE_INFINITY;
            }
            this.properties= properties;
        }
        
        private final double doubleValue( short val ) {
            double r= val * scaleFactor + addOffset;
            return r >= validMin && r<=validMax ? r : Units.dimensionless.getFillDouble();
        }
        
        public double value(int i ) {
            return doubleValue(back[i]);
        }
        
        public double value(int i0, int i1 ) {
            return doubleValue(back[i0*dimSizes[1]+i1]);
        }
        
        public double value(int i0, int i1, int i2 ) {
            return doubleValue(back[i0*dimSizes[1]*dimSizes[2]+i1*dimSizes[2]+i2] );
        }
        
        public double value(int i0, int i1, int i2, int i3 ) {
            return doubleValue(back[ i0*dimSizes[1]*dimSizes[2]*dimSizes[3] + i1*dimSizes[1]*dimSizes[2] + i2*dimSizes[2] + i3 ] );
        }
    }
    
    public static class FloatArray extends DodsVarDataSet {
        float[] back;
        public FloatArray( DArray array, HashMap properties ) {
            super(array);
            back= (float[]) array.getPrimitiveVector().getInternalStorage();
            if ( properties.get("valid_range")!=null ) {
                String s= (String)properties.get("valid_range");
                String[] ss= s.split(",");
                validMin= Double.parseDouble(ss[0]);
                validMax= Double.parseDouble(ss[1]);
            } else {
                validMin= Double.NEGATIVE_INFINITY;
                validMax= Double.POSITIVE_INFINITY;
            }
            this.properties= properties;
        }
        
        double validMin, validMax;
        
        private final double doubleValue( float val ) {
            double r= (double)val;
            return r >= validMin && r<=validMax ? r : Units.dimensionless.getFillDouble();
        }
        
        public double value(int i ) {
            return doubleValue(back[i]);
        }
        
        public double value(int i0, int i1 ) {
            return doubleValue(back[i0*dimSizes[1]+i1]);
        }
        
        public double value(int i0, int i1, int i2 ) {
            return doubleValue(back[i0*dimSizes[1]*dimSizes[2]+i1*dimSizes[2]+i2]);
        }
        
        public double value(int i0, int i1, int i2, int i3 ) {
            int index= i0*dimSizes[1]*dimSizes[2]*dimSizes[3] + i1*dimSizes[2]*dimSizes[3] + i2*dimSizes[3] + i3;
            return doubleValue( back[ index ] );
        }
    }
    
    public static class DoubleArray extends DodsVarDataSet {
        double[] back;
        public DoubleArray( DArray array, HashMap properties ) {
            super(array);
            back= (double[]) array.getPrimitiveVector().getInternalStorage();
            this.properties= properties;
        }
        
        public double value(int i ) {
            return back[i];
        }
        
        public double value(int i0, int i1 ) {
            return back[i0*dimSizes[1]+i1];
        }
        
        public double value(int i0, int i1, int i2 ) {
            return back[i0*dimSizes[1]*dimSizes[2]+i1*dimSizes[2]+i2];
        }
        
        public double value(int i0, int i1, int i2, int i3 ) {
            return back[ i0*dimSizes[1]*dimSizes[2]*dimSizes[3] + i1*dimSizes[1]*dimSizes[2] + i2*dimSizes[2] + i3 ];
        }
    }
    
    public static class EpochStringArray extends DodsVarDataSet {
        BaseType[] back;
        Units u;
        public EpochStringArray( DArray array, HashMap properties ) {
            super(array);
            back= (BaseType[]) array.getPrimitiveVector().getInternalStorage();
            this.properties= properties;
            u=  Units.us2000;
            properties.put( QDataSet.UNITS, u );
        }
        
        public double value(int i ) {
            try {
                return u.parse( ((DString)back[i]).getValue() ).doubleValue(u);
            } catch (ParseException ex) {
                throw new RuntimeException(ex);
            }
        }
        
        public double value(int i0, int i1 )  {
            throw new IllegalArgumentException("not supported");
        }
        
        public double value(int i0, int i1, int i2 ) {
            throw new IllegalArgumentException("not supported");
        }
        
        public double value(int i0, int i1, int i2, int i3 ) {
            throw new IllegalArgumentException("not supported");
        }
    }
    
    public void putValue( int i, double d ) {}
    public void putValue( int i0, int i1, double d ) {}
    public void putValue( int i0, int i1, int i2, double d ) {}
    
    static DodsVarDataSet newDataSet( DArray z, HashMap properties ) {
        Object o=  z.getPrimitiveVector().getInternalStorage();
        if ( o instanceof double[] ) {
            return new DodsVarDataSet.DoubleArray( z, properties ) ;
        } else if ( o instanceof float[] ) {
            return new DodsVarDataSet.FloatArray( z, properties ) ;
        } else if ( o instanceof short[] ) {
            return new DodsVarDataSet.Int16Array( z, properties ) ;
        } else if ( o instanceof int[] ) {
            return new DodsVarDataSet.Int32Array( z, properties ) ;
        } else if ( o instanceof BaseType[] ) {
            BaseType[] bta= (BaseType[])o;
            Object bt1= bta[0];
            if ( bt1 instanceof DString ) {
                return new DodsVarDataSet.EpochStringArray( z, properties );
            } else {
                throw new IllegalArgumentException("not supported: "+o);
            }
        } else {
            throw new IllegalArgumentException("not supported: "+o);
        }
    }
    
    
}
