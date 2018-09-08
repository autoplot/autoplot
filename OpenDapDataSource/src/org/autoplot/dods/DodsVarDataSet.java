/*
 * DodsVarDataSet.java
 *
 * Created on January 29, 2007, 8:42 AM
 *
 */
package org.autoplot.dods;

import opendap.dap.BaseType;
import opendap.dap.DArray;
import opendap.dap.DArrayDimension;
import opendap.dap.DString;
import org.das2.datum.Units;
import java.text.ParseException;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.das2.datum.EnumerationUnits;
import org.das2.util.LoggerManager;
import org.das2.qds.DDataSet;
import org.das2.qds.DataSetUtil;
import org.das2.qds.QDataSet;
import org.das2.qds.Slice0DataSet;
import org.das2.qds.TrimDataSet;
import org.das2.qds.WritableDataSet;

/**
 * Adapter from Dods DArray to look like QDataSet.
 * @author jbf
 */
public abstract class DodsVarDataSet implements WritableDataSet {

    private static final Logger logger= LoggerManager.getLogger("apdss.opendap");
    
    int rank;
    int[] dimSizes = new int[4];
    Map<String,Object> properties;

    /** 
     * Creates a new instance of DodsVarDataSet 
     * @param array the openDap array.
     */
    public DodsVarDataSet(DArray array) {
        int idim = 0;
        for (Enumeration enumm = array.getDimensions(); enumm.hasMoreElements();) {
            DArrayDimension dd = (DArrayDimension) enumm.nextElement();
            dimSizes[idim++] = dd.getSize();
        }
        rank = array.numDimensions();
        properties = new LinkedHashMap<>();
        if ( rank>1 ) properties.put( QDataSet.QUBE, Boolean.TRUE );
        properties.put( QDataSet.NAME, array.getName() );
    }

    @Override
    public String toString() {
        StringBuilder dimStr = new StringBuilder("" + dimSizes[0]);
        for (int i = 1; i < rank; i++) {
            dimStr.append(",").append(dimSizes[i]);
        }
        String u = String.valueOf(properties.get(UNITS));
        if ( "null".equals(u) || "".equals(u)) {
            u = "dimensionless";
        }
        return "dataSet[" + dimStr.toString() + "] (" + u + ")";
    }

    @Override
    public int rank() {
        return rank;
    }

    @Override
    public String svalue() {
        Units u= (Units)property( QDataSet.UNITS );
        if ( u==null ) {
            return String.valueOf( value() );
        } else {
            return u.createDatum(value()).toString();
        }
    }
    
    @Override
    abstract public double value(int i);

    @Override
    abstract public double value(int i0, int i1);

    @Override
    abstract public double value(int i0, int i1, int i2);

    @Override
    abstract public double value(int i0, int i1, int i2, int i3);

    @Override
    public void putValue( int i0, int i1, int i2, int i3, double v ) {
        throw new IllegalArgumentException("rank limit");
    }

    @Override
    public Object property(String name) {
        return properties.get(name);
    }

    @Override
    public Object property(String name, int i) {
        return property(name);
    }

    @Override
    public void putProperty(String name, Object value) {
        checkImmutable();
        properties.put(name, value);
    }

    @Override
    public void putProperty(String name, int i, Object value) {
        putProperty( name, value); //TODO: should probably be name__i
    }

    @Override
    public int length() {
        return dimSizes[0];
    }

    @Override
    public int length(int i) {
        return dimSizes[1];
    }

    @Override
    public int length(int i0, int i1) {
        return dimSizes[2];
    }

    @Override
    public int length(int i0, int i1, int i2 ) {
        return dimSizes[3];
    }


    @Override
    public <T> T capability(Class<T> clazz) {
        return null;
    }

    @Override
    public QDataSet slice(int i) {
        return new Slice0DataSet(this, i);
    }

    @Override
    public QDataSet trim(int start, int end) {
        return new TrimDataSet(this, start, end);
    }

        
    boolean immutable= false;
        
    @Override
    public void makeImmutable() {
        immutable= true;
    }

    @Override
    public boolean isImmutable() {
        return immutable;
    }
        
    /**
     * here is the one place where we check immutable, and we can make this throw an exception
     * once things look stable.
     */
    protected final void checkImmutable() {
        if ( immutable ) {
            logger.warning("dataset has been marked as immutable, this will soon throw an exception");
        }
    }
    

    public static class Int32Array extends DodsVarDataSet {

        int[] back;
        double scaleFactor = 1.0;
        double addOffset = 0.0;
        double validMin, validMax;

        public Int32Array(DArray array, Map<String,Object> properties) {
            super(array);
            back = (int[]) array.getPrimitiveVector().getInternalStorage();
            if (properties.get("add_offset") != null) {
                addOffset = toDoubleValue( properties.get("add_offset"));
            }
            if (properties.get("scale_factor") != null) {
                scaleFactor = toDoubleValue( properties.get("scale_factor"));
            }
            validMin = Double.NEGATIVE_INFINITY;
            validMax = Double.POSITIVE_INFINITY;
            if (properties.get("VALID_MIN") != null) {
                validMin = toDoubleValue( properties.get("VALID_MIN") );
            }
            if (properties.get("VALID_MAX") != null) {
                validMax = toDoubleValue( properties.get("VALID_MAX") );
            }
            if (properties.get("valid_range") != null) {
                String s = (String) properties.get("valid_range");
                String[] ss = s.split(",");
                validMin = Double.parseDouble(ss[0]);
                validMax = Double.parseDouble(ss[1]);
            }
            this.properties.putAll( properties );
        }

        private double doubleValue(int val) {
            double r = val * scaleFactor + addOffset;
            return r >= validMin && r <= validMax ? r : Units.dimensionless.getFillDouble();
        }
        

        @Override
        public double value() {
            throw new IllegalArgumentException("rank 0 not supported");
        }

        @Override
        public double value(int i) {
            return doubleValue(back[i]);
        }

        @Override
        public double value(int i0, int i1) {
            return doubleValue(back[i0 * dimSizes[1] + i1]);
        }

        @Override
        public double value(int i0, int i1, int i2) {
            return doubleValue(back[i0 * dimSizes[1] * dimSizes[2] + i1 * dimSizes[2] + i2]);
        }

        @Override
        public double value(int i0, int i1, int i2, int i3) {
            return doubleValue(back[i0 * dimSizes[1] * dimSizes[2] * dimSizes[3] + i1 * dimSizes[1] * dimSizes[2] + i2 * dimSizes[2] + i3]);
        }
        
        private int putIntValue( double val ) {
            return (int)( ( val - addOffset ) / scaleFactor );
        }

        @Override
        public void putValue(double d) {
            throw new IllegalArgumentException("rank 0 not supported");
        }

        @Override
        public void putValue(int i0, double d) {
            checkImmutable();
            back[i0]= putIntValue(d);
        }

        @Override
        public void putValue(int i0, int i1, double d) {
            checkImmutable();
            back[i0 * dimSizes[1] + i1]= putIntValue(d);
        }

        @Override
        public void putValue(int i0, int i1, int i2, double d) {
            checkImmutable();
            back[i0 * dimSizes[1] * dimSizes[2] + i1 * dimSizes[2] + i2]= putIntValue(d);
        }
    }
    
    private static Double toDoubleValue( Object v ) {
        if ( v instanceof String ) {
            return Double.parseDouble((String)v);
        } else if ( v instanceof Number ) {
            return ((Number)v).doubleValue();
        } else {
            return null;
        }
    }
    
    public static class Int16Array extends DodsVarDataSet {

        short[] back;
        double scaleFactor = 1.0;
        double addOffset = 0.0;
        double validMin, validMax;
        Integer fillValue= null;

        public Int16Array(DArray array, Map<String,Object> properties) {
            super(array);
            back = (short[]) array.getPrimitiveVector().getInternalStorage();
            if (properties.get("add_offset") != null) {
                addOffset = toDoubleValue(properties.get("add_offset"));
            }
            if (properties.get("scale_factor") != null) {
                scaleFactor = toDoubleValue( properties.get("scale_factor"));
            }
            if ( properties.get("_FillValue")!=null ) {
                try {
                    fillValue= Integer.parseInt(String.valueOf(properties.get("_FillValue")));
                } catch ( NumberFormatException ex ) {
                    logger.info( ex.toString() );
                }
            }
            validMin = Double.NEGATIVE_INFINITY;
            validMax = Double.POSITIVE_INFINITY;
            if (properties.get("VALID_MIN") != null) {
                validMin = toDoubleValue( properties.get("VALID_MIN") );
            }
            if (properties.get("VALID_MAX") != null) {
                validMax = toDoubleValue( properties.get("VALID_MAX") );
            }
            if ( properties.get("valid_min") !=null ) {
                validMin = toDoubleValue( properties.get("valid_min") );
            }
            if ( properties.get("valid_max") !=null ) {
                validMin = toDoubleValue( properties.get("valid_max") );
            }
            if (properties.get("valid_range") != null) {
                String s = (String) properties.get("valid_range");
                String[] ss = s.split(",");
                validMin = Double.parseDouble(ss[0]);
                validMax = Double.parseDouble(ss[1]);
            }
            this.properties.putAll( properties );
            if ( validMin!=Double.NEGATIVE_INFINITY ) this.putProperty( QDataSet.VALID_MIN, validMin );
            if ( validMax!=Double.POSITIVE_INFINITY ) this.putProperty( QDataSet.VALID_MAX, validMax );
        }

        private double doubleValue(short val) {
            if ( fillValue!=null && val==fillValue ) {
                return Units.dimensionless.getFillDouble();
            }
            double r = val * scaleFactor + addOffset;
            return r;
        }

        @Override
        public double value() {
            throw new IllegalArgumentException("rank 0 not supported");
        }
        
        @Override
        public double value(int i) {
            return doubleValue(back[i]);
        }

        @Override
        public double value(int i0, int i1) {
            return doubleValue(back[i0 * dimSizes[1] + i1]);
        }

        @Override
        public double value(int i0, int i1, int i2) {
            return doubleValue(back[i0 * dimSizes[1] * dimSizes[2] + i1 * dimSizes[2] + i2]);
        }

        @Override
        public double value(int i0, int i1, int i2, int i3) {
            return doubleValue(back[i0 * dimSizes[1] * dimSizes[2] * dimSizes[3] + i1 * dimSizes[1] * dimSizes[2] + i2 * dimSizes[2] + i3]);
        }
         
        private short putIntValue( double val ) {
            return (short)( ( val - addOffset ) / scaleFactor );
        }

        @Override
        public void putValue(double d) {
            throw new IllegalArgumentException("rank 0 not supported");
        }

        @Override
        public void putValue(int i0, double d) {
            checkImmutable();            
            back[i0]= putIntValue(d);
        }

        @Override
        public void putValue(int i0, int i1, double d) {
            checkImmutable();            
            back[i0 * dimSizes[1] + i1]= putIntValue(d);
        }

        @Override
        public void putValue(int i0, int i1, int i2, double d) {
            checkImmutable();            
            back[i0 * dimSizes[1] * dimSizes[2] + i1 * dimSizes[2] + i2]= putIntValue(d);
        }
    }

    public static class FloatArray extends DodsVarDataSet {

        float[] back;

        public FloatArray(DArray array, Map<String,Object> properties) {
            super(array);
            back = (float[]) array.getPrimitiveVector().getInternalStorage();
            validMin = Double.NEGATIVE_INFINITY;
            validMax = Double.POSITIVE_INFINITY;
            if (properties.get("VALID_MIN") != null) {
                validMin = toDoubleValue( properties.get("VALID_MIN") );
            }
            if (properties.get("VALID_MAX") != null) {
                validMax = toDoubleValue( properties.get("VALID_MAX") );
            }
            if (properties.get("valid_range") != null) {
                String s = (String) properties.get("valid_range");
                String[] ss = s.split(",");
                validMin = Double.parseDouble(ss[0]);
                validMax = Double.parseDouble(ss[1]);
            }
            this.properties.putAll(properties);
        }
        double validMin, validMax;

        private double doubleValue(float val) {
            double r = (double) val;
            return r >= validMin && r <= validMax ? r : Units.dimensionless.getFillDouble();
        }
        
        private float putFloatValue( double val ) {
            return (float)val;
        }

        @Override
        public double value() {
            throw new IllegalArgumentException("rank 0 not supported");
        }
        
        @Override
        public double value(int i) {
            return doubleValue(back[i]);
        }

        @Override
        public double value(int i0, int i1) {
            return doubleValue(back[i0 * dimSizes[1] + i1]);
        }

        @Override
        public double value(int i0, int i1, int i2) {
            return doubleValue(back[i0 * dimSizes[1] * dimSizes[2] + i1 * dimSizes[2] + i2]);
        }

        @Override
        public double value(int i0, int i1, int i2, int i3) {
            int index = i0 * dimSizes[1] * dimSizes[2] * dimSizes[3] + i1 * dimSizes[2] * dimSizes[3] + i2 * dimSizes[3] + i3;
            return doubleValue(back[index]);
        }

        @Override
        public void putValue(double d) {
            throw new IllegalArgumentException("rank 0 not supported");
        }

        @Override
        public void putValue(int i0, double d) {
            checkImmutable();
            back[i0]= putFloatValue(d);
        }

        @Override
        public void putValue(int i0, int i1, double d) {
            checkImmutable();
            back[i0 * dimSizes[1] + i1]= putFloatValue(d);
        }

        @Override
        public void putValue(int i0, int i1, int i2, double d) {
            checkImmutable();
            back[i0 * dimSizes[1] * dimSizes[2] + i1 * dimSizes[2] + i2]= putFloatValue(d);
        }
    }

    public static class DoubleArray extends DodsVarDataSet {

        double[] back;

        public DoubleArray(DArray array, Map<String,Object> properties) {
            super(array);
            back = (double[]) array.getPrimitiveVector().getInternalStorage();
            this.properties.putAll(properties);
        }

        @Override
        public double value() {
            throw new IllegalArgumentException("rank 0 not supported");
        }
        
        @Override
        public double value(int i) {
            return back[i];
        }

        @Override
        public double value(int i0, int i1) {
            return back[i0 * dimSizes[1] + i1];
        }

        @Override
        public double value(int i0, int i1, int i2) {
            return back[i0 * dimSizes[1] * dimSizes[2] + i1 * dimSizes[2] + i2];
        }

        @Override
        public double value(int i0, int i1, int i2, int i3) {
            return back[i0 * dimSizes[1] * dimSizes[2] * dimSizes[3] + i1 * dimSizes[1] * dimSizes[2] + i2 * dimSizes[2] + i3];
        }

        @Override
        public void putValue(double d) {
            throw new IllegalArgumentException("rank 0 not supported");
        }

        @Override
        public void putValue(int i0, double d) {
            checkImmutable();
            back[i0]= d;
        }

        @Override
        public void putValue(int i0, int i1, double d) {
            checkImmutable();
            back[i0 * dimSizes[1] + i1]= d;
        }

        @Override
        public void putValue(int i0, int i1, int i2, double d) {
            checkImmutable();
            back[i0 * dimSizes[1] * dimSizes[2] + i1 * dimSizes[2] + i2]= d;
        }
    }

    public static class NominalStringArray extends DodsVarDataSet {

        BaseType[] back;
        EnumerationUnits u;

        public NominalStringArray(DArray array, Map<String,Object> properties) {
            super(array);
            back = (BaseType[]) array.getPrimitiveVector().getInternalStorage();
            this.properties.putAll(properties);
            u = new EnumerationUnits("dods");
            for (int i = 0; i < back.length; i++) {
                DString bt1 = (DString) back[i];
                u.createDatum(bt1.getValue());
            }
            this.properties.put(QDataSet.UNITS, u);
        }

        @Override
        public double value() {
            throw new IllegalArgumentException("rank 0 not supported");
        }
        
        @Override
        public double value(int i) {
            return u.createDatum(((DString) back[i]).getValue()).doubleValue(u);
        }

        @Override
        public double value(int i0, int i1) {
            throw new IllegalArgumentException("not supported");
        }

        @Override
        public double value(int i0, int i1, int i2) {
            throw new IllegalArgumentException("not supported");
        }

        @Override
        public double value(int i0, int i1, int i2, int i3) {
            throw new IllegalArgumentException("not supported");
        }

        @Override
        public void putValue(double d) {
            throw new IllegalArgumentException("rank 0 not supported");
        }

        @Override
        public void putValue(int i0, double d) {
            checkImmutable();            
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void putValue(int i0, int i1, double d) {
            checkImmutable();            
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void putValue(int i0, int i1, int i2, double d) {
            checkImmutable();            
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    public static class EpochStringArray extends DodsVarDataSet {

        BaseType[] back;
        Units u;

        public EpochStringArray(DArray array, Map<String,Object> properties) {
            super(array);
            back = (BaseType[]) array.getPrimitiveVector().getInternalStorage();
            this.properties.putAll( properties );
            u = Units.us2000;
            this.properties.put(QDataSet.UNITS, u);
        }

        @Override
        public double value() {
            throw new IllegalArgumentException("rank 0 not supported");
        }
        
        @Override
        public double value(int i) {
            try {
                return u.parse(((DString) back[i]).getValue()).doubleValue(u);
            } catch (ParseException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public double value(int i0, int i1) {
            throw new IllegalArgumentException("not supported");
        }

        @Override
        public double value(int i0, int i1, int i2) {
            throw new IllegalArgumentException("not supported");
        }

        @Override
        public double value(int i0, int i1, int i2, int i3) {
            throw new IllegalArgumentException("not supported");
        }

        @Override
        public void putValue(double d) {
            throw new IllegalArgumentException("rank 0 not supported");
        }

        @Override
        public void putValue(int i0, double d) {
            checkImmutable();            
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void putValue(int i0, int i1, double d) {
            checkImmutable();            
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void putValue(int i0, int i1, int i2, double d) {
            checkImmutable();            
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    protected static DodsVarDataSet newDataSet(DArray z, Map<String,Object> properties) {
        Object o = z.getPrimitiveVector().getInternalStorage();
        if (o instanceof double[]) {
            DDataSet.wrap((double[])o);
            return new DodsVarDataSet.DoubleArray(z, properties);
        } else if (o instanceof float[]) {
            return new DodsVarDataSet.FloatArray(z, properties);
        } else if (o instanceof short[]) {
            return new DodsVarDataSet.Int16Array(z, properties);
        } else if (o instanceof int[]) {
            return new DodsVarDataSet.Int32Array(z, properties);
        } else if (o instanceof BaseType[]) {
            BaseType[] bta = (BaseType[]) o;
            Object bt1 = bta[0];
            if (bt1 instanceof DString) {
                if (properties.get("UNITS") == null) {
                    return new DodsVarDataSet.NominalStringArray(z, properties);
                } else {
                    return new DodsVarDataSet.EpochStringArray(z, properties);
                }
            } else {
                throw new IllegalArgumentException("not supported: " + o);
            }
        } else {
            throw new IllegalArgumentException("not supported: " + o);
        }
    }
}
