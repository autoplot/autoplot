/*
 * CdfUtil.java
 *
 * Created on July 24, 2007, 12:56 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.cdfdatasource;

import java.util.logging.Level;
import org.das2.datum.DatumRange;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.Units;
import gsfc.nssdc.cdf.Attribute;
import gsfc.nssdc.cdf.CDF;
import gsfc.nssdc.cdf.CDFConstants;
import gsfc.nssdc.cdf.CDFData;
import gsfc.nssdc.cdf.CDFException;
import gsfc.nssdc.cdf.Entry;
import gsfc.nssdc.cdf.Variable;
import gsfc.nssdc.cdf.util.CDFUtils;
import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Logger;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.BDataSet;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.FDataSet;
import org.virbo.dataset.IDataSet;
import org.virbo.dataset.LDataSet;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.SDataSet;
import org.virbo.dataset.WritableDataSet;
import org.virbo.datasource.DataSourceUtil;
import org.virbo.dsops.Ops;

/**
 * static methods supporting CdfFileDataSource
 *
 * @author jbf
 */
public class CdfUtil {
    private static final String VAR_TYPE_DATA = "data";

    private final static Logger logger = Logger.getLogger("virbo.cdfdatasource");

    private static void flatten(double[][] data, double[] back, int offset, int nx, int ny) {
        for (int i = 0; i < nx; i++) {
            double[] dd = data[i];
            System.arraycopy(dd, 0, back, offset + i * ny, ny);
        }
    }

    private static void flatten(float[][] data, float[] back, int offset, int nx, int ny) {
        for (int i = 0; i < nx; i++) {
            float[] dd = data[i];
            System.arraycopy(dd, 0, back, offset + i * ny, ny);
        }
    }

    private static void flatten(long[][] data, long[] back, int offset, int nx, int ny) {
        for (int i = 0; i < nx; i++) {
            long[] dd = data[i];
            System.arraycopy(dd, 0, back, offset + i * ny, ny);
        }
    }

    private static void flatten(int[][] data, int[] back, int offset, int nx, int ny) {
        for (int i = 0; i < nx; i++) {
            int[] dd = data[i];
            System.arraycopy(dd, 0, back, offset + i * ny, ny);
        }
    }

    private static void flatten(short[][] data, short[] back, int offset, int nx, int ny) {
        for (int i = 0; i < nx; i++) {
            short[] dd = data[i];
            System.arraycopy(dd, 0, back, offset + i * ny, ny);
        }
    }

    private static void flatten(byte[][] data, byte[] back, int offset, int nx, int ny) {
        for (int i = 0; i < nx; i++) {
            byte[] dd = data[i];
            System.arraycopy(dd, 0, back, offset + i * ny, ny);
        }
    }

    private static void flatten(double[][][] data, double[] back, int offset, int nx, int ny, int nz) {
        offset = 0;
        for (int i = 0; i < nx; i++) {
            double[][] ff = data[i];
            flatten(ff, back, offset, ny, nz);
            offset += ny * nz;
        }
    }

    private static void flatten(float[][][] data, float[] back, int offset, int nx, int ny, int nz) {
        offset = 0;
        for (int i = 0; i < nx; i++) {
            float[][] ff = data[i];
            flatten(ff, back, offset, ny, nz);
            offset += ny * nz;
        }
    }

    private static void flatten(long[][][] data, long[] back, int offset, int nx, int ny, int nz) {
        offset = 0;
        for (int i = 0; i < nx; i++) {
            long[][] ff = data[i];
            flatten(ff, back, offset, ny, nz);
            offset += ny * nz;
        }
    }

    private static void flatten(int[][][] data, int[] back, int offset, int nx, int ny, int nz) {
        offset = 0;
        for (int i = 0; i < nx; i++) {
            int[][] ff = data[i];
            flatten(ff, back, offset, ny, nz);
            offset += ny * nz;
        }
    }

    private static void flatten(short[][][] data, short[] back, int offset, int nx, int ny, int nz) {
        offset = 0;
        for (int i = 0; i < nx; i++) {
            short[][] ff = data[i];
            flatten(ff, back, offset, ny, nz);
            offset += ny * nz;
        }
    }

    private static void flatten(byte[][][] data, byte[] back, int offset, int nx, int ny, int nz) {
        offset = 0;
        for (int i = 0; i < nx; i++) {
            byte[][] ff = data[i];
            flatten(ff, back, offset, ny, nz);
            offset += ny * nz;
        }
    }

    private static void flatten(double[][][][] data, double[] back, int offset, int nx, int ny, int nz, int nzz ) {
        offset = 0;
        for (int i = 0; i < nx; i++) {
            double[][][] ff = data[i];
            flatten(ff, back, offset, ny, nz, nzz);
            offset += ny * nz;
        }
    }

    private static void flatten(float[][][][] data, float[] back, int offset, int nx, int ny, int nz, int nzz) {
        offset = 0;
        for (int i = 0; i < nx; i++) {
            float[][][] ff = data[i];
            flatten(ff, back, offset, ny, nz, nzz);
            offset += ny * nz;
        }
    }

    private static void flatten(long[][][][] data, long[] back, int offset, int nx, int ny, int nz, int nzz) {
        offset = 0;
        for (int i = 0; i < nx; i++) {
            long[][][] ff = data[i];
            flatten(ff, back, offset, ny, nz, nzz);
            offset += ny * nz;
        }
    }

    private static void flatten(int[][][][] data, int[] back, int offset, int nx, int ny, int nz, int nzz) {
        offset = 0;
        for (int i = 0; i < nx; i++) {
            int[][][] ff = data[i];
            flatten(ff, back, offset, ny, nz, nzz);
            offset += ny * nz;
        }
    }

    private static void flatten(short[][][][] data, short[] back, int offset, int nx, int ny, int nz, int nzz) {
        offset = 0;
        for (int i = 0; i < nx; i++) {
            short[][][] ff = data[i];
            flatten(ff, back, offset, ny, nz, nzz);
            offset += ny * nz;
        }
    }

    private static void flatten(byte[][][][] data, byte[] back, int offset, int nx, int ny, int nz, int nzz) {
        offset = 0;
        for (int i = 0; i < nx; i++) {
            byte[][][] ff = data[i];
            flatten(ff, back, offset, ny, nz, nzz);
            offset += ny * nz;
        }
    }

    private static WritableDataSet wrapRank2(long varType, Object odata, Variable variable) throws RuntimeException {
        WritableDataSet result;
        if (varType == Variable.CDF_REAL4 || varType == Variable.CDF_FLOAT) {
            float[][] data = (float[][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            float[] back = new float[nx * ny];
            flatten(data, back, 0, nx, ny);
            result = FDataSet.wrap(back, nx, ny);
        } else if (varType == Variable.CDF_REAL8 || varType == Variable.CDF_DOUBLE) {
            double[][] data = (double[][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            double[] back = new double[nx * ny];
            flatten(data, back, 0, nx, ny);
            result = DDataSet.wrap(back, nx, ny);
        } else if (varType == Variable.CDF_EPOCH) {
            double[] data = (double[]) odata; // kludge for CAA, which returns [1,900]
            result = DDataSet.wrap(data);

        } else if ( varType == Variable.CDF_UINT4 ) {
            long[][] data = (long[][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            long[] back = new long[nx * ny];
            flatten(data, back, 0, nx, ny);
            result = LDataSet.wrap(back, nx, ny);

        } else if (varType == Variable.CDF_INT4 ||  varType == Variable.CDF_UINT2 ) {
            int[][] data = (int[][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            int[] back = new int[nx * ny];
            flatten(data, back, 0, nx, ny);
            result = IDataSet.wrap(back, nx, ny);
        } else if (varType == Variable.CDF_INT2  || varType == Variable.CDF_UINT1) {
            short[][] data = (short[][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            short[] back = new short[nx * ny];
            flatten(data, back, 0, nx, ny);
            result = SDataSet.wrap(back, nx, ny);
        } else if (varType == Variable.CDF_INT1 || varType == Variable.CDF_BYTE) {
            byte[][] data = (byte[][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            byte[] back = new byte[nx * ny];
            flatten(data, back, 0, nx, ny);
            result = BDataSet.wrap(back, nx, ny);

        } else if (varType == Variable.CDF_CHAR || varType==Variable.CDF_UCHAR ) {
            EnumerationUnits units = EnumerationUnits.create(variable.getName());
            String[] sdata = (String[]) odata;
            double[] back = new double[sdata.length];
            for (int i = 0; i < sdata.length; i++) {
                back[i] = units.createDatum(sdata[i]).doubleValue(units);
            }
            result = DDataSet.wrap(back);
            result.putProperty(QDataSet.UNITS, units);
        } else {
            throw new RuntimeException("Unsupported Data Type " + variable.getDataType() + " java type " + odata.getClass());
        }
        return result;
    }

    private static WritableDataSet wrapRank3(long varType, Object odata, Variable variable) throws RuntimeException {
        WritableDataSet result;
        if (varType == Variable.CDF_REAL8 || varType == Variable.CDF_DOUBLE) {
            double[][][] data = (double[][][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            int nz = data[0][0].length;
            double[] back = new double[nx * ny * nz];
            flatten(data, back, 0, nx, ny, nz);
            result = DDataSet.wrap(back, new int[]{nx, ny, nz});
        } else if (varType == Variable.CDF_REAL4 || varType == Variable.CDF_FLOAT) {
            float[][][] data = (float[][][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            int nz = data[0][0].length;
            float[] back = new float[nx * ny * nz];
            flatten(data, back, 0, nx, ny, nz);
            result = FDataSet.wrap(back, nx, ny, nz);
        } else if ( varType == Variable.CDF_UINT4 ) {
            long[][][] data = (long[][][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            int nz = data[0][0].length;
            long[] back = new long[nx * ny * nz];
            flatten(data, back, 0, nx, ny, nz);
            result = LDataSet.wrap(back, nx, ny, nz);
        } else if (varType == Variable.CDF_INT4 ||  varType == Variable.CDF_UINT2 ) {
            int[][][] data = (int[][][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            int nz = data[0][0].length;
            int[] back = new int[nx * ny * nz];
            flatten(data, back, 0, nx, ny, nz);
            result = IDataSet.wrap(back, nx, ny, nz);
        } else if (varType == Variable.CDF_INT2 || varType == Variable.CDF_UINT2 || varType == Variable.CDF_UINT1) {
            short[][][] data = (short[][][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            int nz = data[0][0].length;
            short[] back = new short[nx * ny * nz];
            flatten(data, back, 0, nx, ny, nz);
            result = SDataSet.wrap(back, nx, ny, nz);
        } else if (varType == Variable.CDF_INT1 || varType == Variable.CDF_BYTE) {
            byte[][][] data = (byte[][][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            int nz = data[0][0].length;
            byte[] back = new byte[nx * ny * nz];
            flatten(data, back, 0, nx, ny, nz);
            result = BDataSet.wrap(back, nx, ny, nz);

        } else {
            throw new RuntimeException("Unsupported Data Type " + variable.getDataType() + " java type " + odata.getClass());
        }
        return result;
    }

    private static WritableDataSet wrapRank4(long varType, Object odata, Variable variable) throws RuntimeException {
        WritableDataSet result;
        if (varType == Variable.CDF_REAL8 || varType == Variable.CDF_DOUBLE) {
            double[][][][] data = (double[][][][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            int nz = data[0][0].length;
            int nzz = data[0][0][0].length;
            double[] back = new double[nx * ny * nz * nzz ];
            flatten(data, back, 0, nx, ny, nz, nzz );
            result = DDataSet.wrap(back, new int[]{nx, ny, nz, nzz});
        } else if (varType == Variable.CDF_REAL4 || varType == Variable.CDF_FLOAT) {
            float[][][][] data = (float[][][][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            int nz = data[0][0].length;
            int nzz = data[0][0][0].length;
            float[] back = new float[nx * ny * nz * nzz];
            flatten(data, back, 0, nx, ny, nz, nzz );
            result = FDataSet.wrap(back, new int[] { nx, ny, nz, nzz } );
        } else if (varType ==  Variable.CDF_UINT4) {
            long[][][][] data = (long[][][][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            int nz = data[0][0].length;
            int nzz = data[0][0][0].length;
            long[] back = new long[nx * ny * nz * nzz];
            flatten(data, back, 0, nx, ny, nz, nzz);
            result = LDataSet.wrap(back,  new int[] { nx, ny, nz, nzz } );
        } else if (varType == Variable.CDF_INT4 || varType == Variable.CDF_UINT2) {
            int[][][][] data = (int[][][][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            int nz = data[0][0].length;
            int nzz = data[0][0][0].length;
            int[] back = new int[nx * ny * nz * nzz];
            flatten(data, back, 0, nx, ny, nz, nzz);
            result = IDataSet.wrap(back,  new int[] { nx, ny, nz, nzz } );
        } else if (varType == Variable.CDF_INT2 || varType == Variable.CDF_UINT1) {
            short[][][][] data = (short[][][][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            int nz = data[0][0].length;
            int nzz = data[0][0][0].length;
            short[] back = new short[nx * ny * nz * nzz ];
            flatten(data, back, 0, nx, ny, nz, nzz);
            result = SDataSet.wrap(back, new int[] { nx, ny, nz, nzz } );
        } else if (varType == Variable.CDF_INT1 || varType == Variable.CDF_BYTE) {
            byte[][][][] data = (byte[][][][]) odata;
            int nx = data.length;
            int ny = data[0].length;
            int nz = data[0][0].length;
            int nzz = data[0][0][0].length;
            byte[] back = new byte[nx * ny * nz * nzz];
            flatten(data, back, 0, nx, ny, nz, nzz);
            result = BDataSet.wrap(back,  new int[] { nx, ny, nz, nzz } );

        } else {
            throw new RuntimeException("Unsupported Data Type " + variable.getDataType() + " java type " + odata.getClass());
        }
        return result;
    }

    /**
     * Creates a new instance of CdfUtil
     */
    public CdfUtil() {
    }

    /**
     * returns the Entry that is convertable to double as a double.
     * @throws NumberFormatException for strings Double.parseDouble
     */
    private static double doubleValue(Object o) {
        if (o instanceof Float) {
            return ((Float) o).doubleValue();
        } else if (o instanceof Double) {
            return ((Double) o).doubleValue();
        } else if (o instanceof Integer) {
            return ((Integer) o).doubleValue();
        } else if (o instanceof Short) {
            return ((Short) o).doubleValue();
        } else if (o instanceof String) {
            return Double.parseDouble((String) o);
        } else {
            throw new RuntimeException("Unsupported Data Type: " + o.getClass().getName());
        }
    }

    /**
     * returns the range of the data by looking for the SCALEMIN/SCALEMAX params,
     * or the required VALIDMIN/VALIDMAX parameters
     */
    public static DatumRange getRange(HashMap attrs) {
        DatumRange range;
        if (attrs.containsKey("SCALEMIN") && attrs.containsKey("SCALEMAX")) {
            range = new DatumRange(doubleValue(attrs.get("SCALEMIN")),
                    doubleValue(attrs.get("SCALEMAX")), Units.dimensionless);
        } else {
            range = new DatumRange(doubleValue(attrs.get("VALIDMIN")),
                    doubleValue(attrs.get("VALIDMAX")), Units.dimensionless);
        }
        return range;
    }

    public static String getScaleType(HashMap attrs) {
        String type = "linear";
        if (attrs.containsKey("SCALETYP")) {
            type = (String) attrs.get("SCALETYP");
        }
        return type;
    }

    private static WritableDataSet wrapSingle(long varType, String name, Object o) {
        WritableDataSet result;

        if (varType == Variable.CDF_EPOCH) {
            result = DDataSet.wrap(new double[]{(Double) o});
            result.putProperty(QDataSet.UNITS, Units.cdfEpoch);
            result.putProperty(QDataSet.VALID_MIN, 1.); // kludge for Timas, which has zeros.

        } else if (varType == Variable.CDF_EPOCH16) {
            throw new UnsupportedOperationException("single Epoch16 not supported, send us the file");

        } else if (varType == Variable.CDF_TIME_TT2000 ) {
            result= LDataSet.wrap( new long[] { (Long) o } );
            result.putProperty(QDataSet.UNITS, Units.cdfTT2000 );

        } else if (o instanceof Number) {
            result = DDataSet.wrap(new double[]{((Number) o).doubleValue()});
        } else if (varType == Variable.CDF_CHAR || varType==Variable.CDF_UCHAR) {
            EnumerationUnits units = EnumerationUnits.create(name);
            String sdata = (String) o;
            double[] back = new double[1];
            back[0] = units.createDatum(sdata).doubleValue(units);

            result = DDataSet.wrap(back);
            result.putProperty(QDataSet.UNITS, units);
        } else {

            throw new RuntimeException("Unsupported Data Type " + varType + " java type " + o.getClass());
        }

        return result;
    }

    public static MutablePropertyDataSet wrapCdfHyperDataHacked(
            Variable variable, long recStart, long recCount, long recInterval, ProgressMonitor mon ) throws CDFException {

        if ( mon==null ) mon= new org.das2.util.monitor.NullProgressMonitor();

        long varType = variable.getDataType();
        long[] dimIndeces = new long[]{0};

        long[] dimSizes = variable.getDimSizes();
        int dims;
        if (dimSizes == null) {
            dims = 0;
        } else {
            dims = dimSizes.length;
        }

        long[] dimCounts;
        long[] dimIntervals;

        if (dims == 0) {
            dimCounts = new long[]{0};
            dimIntervals = new long[]{0};
        } else if (dims == 1) {
            dimCounts = new long[]{dimSizes[0]};
            dimIntervals = new long[]{1};
        } else if (dims == 2) {
            dimIndeces = new long[]{0, 0};
            dimCounts = new long[]{dimSizes[0], dimSizes[1]};
            dimIntervals = new long[]{1, 1};
        } else if (dims == 3) {
            dimIndeces = new long[]{0, 0, 0};
            dimCounts = new long[]{dimSizes[0], dimSizes[1], dimSizes[2]};
            dimIntervals = new long[]{1, 1, 1};
        } else {
            if (recCount != -1) {
                throw new IllegalArgumentException("rank 5 not implemented");
            } else {
                dimCounts = new long[]{dimSizes[0]};
                dimIntervals = new long[]{1};
            }
        }
        int recSizeCount= 1;
        if ( dimSizes!=null ) {
            for ( int i=0; i<dimSizes.length; i++ ) {
                recSizeCount*= dimSizes[i];
            }
        }

        if ( recCount==-1 && recStart>0 && variable.getMaxWrittenRecord()==0 ) { // another kludge for Rockets, where depend was assigned variance
            recStart= 0;
        }

        long rc= Math.max(1, recCount);

        Object odata;
        boolean breakUp= ( ( varType == Variable.CDF_REAL4 ||varType == Variable.CDF_FLOAT ) && recCount*recSizeCount>10000000 && recInterval==1 );
        if ( breakUp ) {
            logger.info("breaking up into smaller reads to save memory");
            odata= new float[(int)(recSizeCount*rc)];
            long blockSize= Math.max( 20, 10000000 / recSizeCount ); // in records. target blockSize of 10Mb, but read in at least 20 recs each time.
            int nread= (int)(rc/blockSize);
            mon.started();
            mon.setTaskSize(nread+1);
            for ( int i=0; i<nread; i++ ) {
                mon.setTaskProgress(i);
                CDFData cdfData= variable.getHyperDataObject( recStart + i*blockSize, blockSize, recInterval, dimIndeces, dimCounts, dimIntervals );
                float[] odata1= (float[])cdfData.getRawData(); // this is my hack
                System.arraycopy( odata1, 0, (float[])odata, (int)(recSizeCount*i*blockSize), (int)(recSizeCount*blockSize) );
            }
            // read the remainder
            long nremain= rc - ( recStart + nread*blockSize );
            if ( nremain>0 ) {
                mon.setTaskProgress(nread);
                CDFData cdfData= variable.getHyperDataObject( recStart + nread*blockSize, nremain, recInterval, dimIndeces, dimCounts, dimIntervals );
                float[] odata1= (float[])cdfData.getRawData(); // this is my hack
                System.arraycopy( odata1, 0, (float[])odata, (int)(recSizeCount*nread*blockSize), (int)(recSizeCount*nremain) );
            }
            mon.finished();
        } else {
            CDFData cdfData= variable.getHyperDataObject( recStart, rc, recInterval, dimIndeces, dimCounts, dimIntervals );
            odata= cdfData.getRawData(); // this is my hack

            if ( ! odata.getClass().isArray() ) {
                Object o2= null;
                if ( odata.getClass()==Double.class ) {
                    o2= Array.newInstance( double.class, 1 );
                } else if ( odata.getClass()==Float.class ) {
                    o2= Array.newInstance( float.class, 1 );
                } else if ( odata.getClass()==Long.class ) {
                    o2= Array.newInstance( long.class, 1 );
                } else if ( odata.getClass()==Integer.class ) {
                    o2= Array.newInstance( int.class, 1 );
                } else if ( odata.getClass()==Short.class ) {
                    o2= Array.newInstance( short.class, 1 );
                } else if ( odata.getClass()==Byte.class ) {
                    o2= Array.newInstance( byte.class, 1 );
                }
                if ( o2!=null ) {
                    System.err.println("handling rank 0 value by making 1-element array");
                    System.err.println("  in "+variable );        
                    Array.set( o2, 0, odata );
                    odata= o2;
                }
            }
        }
        
        WritableDataSet result;

        if ( dims==0 ) dimSizes= new long[0]; // to simplify code

        int[] qube;
        if ( recCount==-1 ) {
            qube= new int[ dimSizes.length ];
            for ( int i=0; i<dimSizes.length; i++ ) {
                qube[i]= (int)dimSizes[i];
            }
        } else {
            qube= new int[ 1+ dimSizes.length ];
            for ( int i=0; i<dimSizes.length; i++ ) {
                qube[1+i]= (int)dimSizes[i];
            }
            qube[0]= (int)recCount;
        }
        
        if (varType == Variable.CDF_REAL4 || varType == Variable.CDF_FLOAT) {
            result = FDataSet.wrap((float[]) odata, qube );

        } else if (varType == Variable.CDF_REAL8 || varType == Variable.CDF_DOUBLE) {
            result = DDataSet.wrap((double[]) odata, qube);

        } else if (varType == Variable.CDF_UINT4 ) {
            result = LDataSet.wrap((long[]) odata, qube);
        
        } else if (varType == Variable.CDF_INT4 || varType == Variable.CDF_UINT2) {
            result = IDataSet.wrap((int[]) odata, qube);

        } else if (varType == Variable.CDF_INT2 || varType == Variable.CDF_UINT1) {
            result = SDataSet.wrap((short[]) odata, qube);

        } else if (varType == Variable.CDF_INT1 || varType == Variable.CDF_BYTE) {
            result = BDataSet.wrap((byte[]) odata, qube);

        } else if (varType == Variable.CDF_CHAR || varType == Variable.CDF_UCHAR ) {
            EnumerationUnits units = EnumerationUnits.create(variable.getName());
            String[] sdata;
            if ( odata instanceof byte[] && qube.length==1 ) { //http://cdaweb.gsfc.nasa.gov/istp_public/data/image/euv/2005/im_k0_euv_20050129_v01.cdf?IMAGE[0]
                int nn= Array.getLength(odata)/qube[0];
                sdata= new String[qube[0]];
                for ( int ii=0; ii<qube[0]; ii++ ) {
                    sdata[ii]= new String( (byte[])odata, ii*nn, nn );
                }
            } else {
                sdata = (String[]) odata;
            }
            double[] back = new double[sdata.length];
            for (int i = 0; i < sdata.length; i++) {
                back[i] = units.createDatum(sdata[i]).doubleValue(units);
            }
            result = DDataSet.wrap(back, qube);
            result.putProperty(QDataSet.UNITS, units);

        } else if (varType == Variable.CDF_EPOCH) {
            if ( qube.length==2 && qube[1]==1 ) {// kludge for c4_cp_fgm_spin_20030102_v01.cdf?B_vec_xyz_gse__C4_CP_FGM_SPIN
                qube= new int[] { qube[0] };
            }
            result = DDataSet.wrap((double[]) odata, qube);
            result.putProperty(QDataSet.UNITS, Units.cdfEpoch);
            result.putProperty(QDataSet.VALID_MIN, 5.68025568E13 ); // 1800-01-01T00:00
            result.putProperty(QDataSet.VALID_MAX, 6.94253376E13 ); // 2200-01-01T00:00
            //result.putProperty(QDataSet.VALID_MIN, 1.); // kludge for Timas, which has zeros.

        } else if (varType == Variable.CDF_EPOCH16) {
            // adapt to das2 by translating to Units.us2000, which should be good enough.
            // note when this is not good enough, new units types can be introduced, along with conversions.
            double[] data = (double[]) odata;
            double[] dresult = new double[data.length / 2];
            for (int i = 0; i < dresult.length; i++) {
                double t2000 = data[i * 2] - 6.3113904e+10; // seconds since midnight 2000
                dresult[i] = t2000 * 1e6 + data[i * 2 + 1] / 1000000.;
            }
            result = DDataSet.wrap(dresult, qube);
            result.putProperty(QDataSet.UNITS, Units.us2000);

        } else if (varType == Variable.CDF_TIME_TT2000 ) {
            long[] data = (long[]) odata;
            result = LDataSet.wrap( data, qube);
            result.putProperty(QDataSet.UNITS, Units.cdfTT2000 );

        } else {

            throw new RuntimeException("Unsupported Data Type " + variable.getDataType() + " java type " + odata.getClass());
        }
        return result;
    }
    
    /**
     * wraps response from CDFVariable.getHyperData() into QDataSet.  The response object
     * should be float[], float[][], double[], double[][], etc.  recStart, recCount, recInterval
     * control subsetting in the zeroth dimension.
     * @param reccount reccount -1 indicates read the one and only record and do a reform.
     */
    public static MutablePropertyDataSet wrapCdfHyperData(Variable variable, long recStart, long recCount, long recInterval) throws CDFException {
        long varType = variable.getDataType();
        long[] dimIndeces = new long[]{0};

        long[] dimSizes = variable.getDimSizes();
        int dims;
        if (dimSizes == null) {
            dims = 0;
        } else {
            dims = dimSizes.length;
        }

        long[] dimCounts;
        long[] dimIntervals;

        if (dims == 0) {
            dimCounts = new long[]{0};
            dimIntervals = new long[]{0};
        } else if (dims == 1) {
            dimCounts = new long[]{dimSizes[0]};
            dimIntervals = new long[]{1};
        } else if (dims == 2) {
            dimIndeces = new long[]{0, 0};
            dimCounts = new long[]{dimSizes[0], dimSizes[1]};
            dimIntervals = new long[]{1, 1};
        } else if (dims == 3) {
            dimIndeces = new long[]{0, 0, 0};
            dimCounts = new long[]{dimSizes[0], dimSizes[1], dimSizes[2]};
            dimIntervals = new long[]{1, 1, 1};
        } else {
            if (recCount != -1) {
                throw new IllegalArgumentException("rank 5 not implemented");
            } else {
                dimCounts = new long[]{dimSizes[0]};
                dimIntervals = new long[]{1};
            }
        }

        Object odata;
        odata= variable.getHyperData(recStart, Math.max(1, recCount), recInterval, dimIndeces, dimCounts, dimIntervals);

        MutablePropertyDataSet result;

        int rank = 1;

        if ( !odata.getClass().isArray() && recCount==-1 ) {
            rank= 0;
            result= DataSetUtil.asDataSet( ((Number)odata).doubleValue() );
            if (varType == Variable.CDF_EPOCH) {
                result.putProperty(QDataSet.UNITS, Units.cdfEpoch);
                result.putProperty(QDataSet.VALID_MIN, 1.); // kludge for Timas, which has zeros.
            }
            return result;
        }
        
        Object element = Array.get(odata, 0);
        if (element.getClass().isArray()) {
            Object element2 = Array.get(element, 0);
            if (element2.getClass().isArray()) {
                Object element3 = Array.get(element2, 0);
                if (element3.getClass().isArray()) {
                    rank = 4;
                } else {
                    rank = 3;
                }
            } else {
                rank = 2;
            }
        }

        if (recCount == -1 && rank == 4) {
            result = wrapRank4(varType, Array.get(odata, 0), variable);
        }

        if (recCount == 1 || (recCount == -1 && rank == 2)) {
            if (!odata.getClass().isArray()) {
                return wrapSingle(varType, variable.getName(), odata);
            }
        }

        if (rank == 1) {
            if (varType == CDFConstants.CDF_REAL4 || varType == CDFConstants.CDF_FLOAT) {
                result = FDataSet.wrap((float[]) odata);

            } else if (varType == CDFConstants.CDF_REAL8 || varType == CDFConstants.CDF_DOUBLE) {
                result = DDataSet.wrap((double[]) odata);

            } else if (varType == CDFConstants.CDF_UINT4) {
                result = LDataSet.wrap((long[]) odata);

            } else if (varType == CDFConstants.CDF_INT4 || varType == CDFConstants.CDF_UINT2) {
                result = IDataSet.wrap((int[]) odata);

            } else if (varType == CDFConstants.CDF_INT2 || varType == CDFConstants.CDF_UINT1) {
                result = SDataSet.wrap((short[]) odata);

            } else if (varType == CDFConstants.CDF_INT1 || varType == CDFConstants.CDF_BYTE) {
                result = BDataSet.wrap((byte[]) odata);

            } else if (varType == CDFConstants.CDF_CHAR || varType == CDFConstants.CDF_UCHAR ) {
                EnumerationUnits units = EnumerationUnits.create(variable.getName());
                String[] sdata = (String[]) odata;
                double[] back = new double[sdata.length];
                for (int i = 0; i < sdata.length; i++) {
                    back[i] = units.createDatum( sdata[i] ).doubleValue(units);
                }
                result = DDataSet.wrap(back);
                result.putProperty(QDataSet.UNITS, units);

            } else if (varType == CDFConstants.CDF_EPOCH) {
                result = DDataSet.wrap((double[]) odata);
                result.putProperty(QDataSet.UNITS, Units.cdfEpoch);
                result.putProperty(QDataSet.VALID_MIN, 1.); // kludge for Timas, which has zeros.

            } else if (varType == CDFConstants.CDF_EPOCH16) {
                // adapt to das2 by translating to Units.us2000, which should be good enough.
                // note when this is not good enough, new units types can be introduced, along with conversions.
                double[] data = (double[]) odata;
                double[] dresult = new double[data.length / 2];
                for (int i = 0; i < dresult.length; i++) {
                    double t2000 = data[i * 2] - 6.3113904e+10; // seconds since midnight 2000
                    dresult[i] = t2000 * 1e6 + data[i * 2 + 1] / 1000000.;
                }
                result = DDataSet.wrap(dresult);
                result.putProperty(QDataSet.UNITS, Units.us2000);

            } else if (varType == CDFConstants.CDF_TIME_TT2000 ) {
                result = LDataSet.wrap((long[]) odata);
                result.putProperty(QDataSet.UNITS, Units.cdfTT2000 );

            } else {

                throw new RuntimeException("Unsupported Data Type " + variable.getDataType() + " java type " + odata.getClass());
            }

        } else if (rank == 2) {
            result = wrapRank2(varType, odata, variable);

        } else if (rank == 3) {
            result = wrapRank3(varType, odata, variable);

        } else {
            result = wrapRank4(varType, odata, variable);
        }

        return result;
    }


    public static Map<String, String> getPlottable(CDF cdf, boolean dataOnly, int rankLimit) throws CDFException {
        return getPlottable(cdf, dataOnly, rankLimit, false);
    }

    public static boolean hasEntry( Attribute bAttr, Variable var ) {
        if ( bAttr==null ) return false;
        try {
            bAttr.getEntry(var);
            return true;
        } catch (CDFException ex) {
            return false;
        }
    }

    //container for description of depend
    private static class DepDesc {
        Variable dep;
        Variable labl;
        long nrec;
        boolean rank2;
    }

    /**
     * factor out common code that gets the properties for each dimension.
     * @param cdf
     * @param var
     * @param rank
     * @param dims
     * @param dim
     * @param warn
     * @return
     */
    private static DepDesc getDepDesc( CDF cdf, Variable var, int rank, long[] dims, int dim, List<String> warn ) {
        Attribute bAttr=null;
        try {
            bAttr = cdf.getAttribute("DEPEND_"+dim);  // check for PB5, Vectors
        } catch (CDFException e) {
        }

        Attribute blAttr=null;
        try {
           blAttr = cdf.getAttribute("LABL_PTR_1");
        } catch (CDFException e) {
        }

        DepDesc result= new DepDesc();
        result.nrec= -1;

        try {
            if (bAttr != null && rank>1 && hasEntry( bAttr, var) ) {  // check for metadata for DEPEND_1
                logger.log(Level.FINE, "get attribute {0} entry for {1}", new Object[]{bAttr.getName(), var.getName()});
                Entry yEntry = bAttr.getEntry(var);
                result.dep = cdf.getVariable(String.valueOf(yEntry.getData()));
                result.nrec = result.dep.getMaxWrittenRecord()+1;
                if (result.nrec == 1) {
                    result.nrec = result.dep.getDimSizes()[0];
                }
                if ( result.dep.getDimSizes().length>0 && result.dep.getMaxWrittenRecord()>0 && result.dep.getRecVariance() ) {
                            result.rank2= true;
                            result.nrec = result.dep.getDimSizes()[0];
                            warn.add( "NOTE: " + result.dep.getName() + " is record varying" );
                } else {
                    if ( result.dep.getRecVariance() ) {
                        //TODO: some sanity check here?
                    } else {
                        if ( dims.length>(dim-1) && (result.nrec)!=dims[dim-1] ) {
                            warn.add("depend"+dim+" length ("+result.nrec+") is inconsistent with length ("+dims[dim-1]+")" );
                        }
                    }
                }
            }
        } catch (CDFException e) {
            //e.printStackTrace();
            warn.add( "problem with " + bAttr.getName() + ": " + e.getMessage() );
        }

        try {
            //TODO: if there is DEPEND_i, then there is no check on LABEL.
            if ( result.nrec==-1 && blAttr != null && rank>1 && hasEntry( blAttr, var)  ) {  // check for metadata for LABL_PTR_1
                logger.log(Level.FINE, "get attribute {0} entry for {1}", new Object[]{blAttr.getName(), var.getName()});
                Entry yEntry = blAttr.getEntry(var);
                result.labl = cdf.getVariable(String.valueOf(yEntry.getData()));
                result.nrec = result.labl.getMaxWrittenRecord()+1;
                if (result.nrec == 1) {
                    result.nrec = result.labl.getDimSizes()[0];
                }
                if ( result.labl.getRecVariance() ) {
                    //TODO: some sanity check here?
                } else {
                    if ( dims.length>(dim-1) && (result.nrec)!=dims[dim-1] ) {
                        warn.add("LABL_PTR_"+dim+" length ("+result.nrec+") is inconsistent with length ("+dims[dim-1]+")" );
                    }
                }
            }
        } catch (CDFException e) {
            warn.add( "problem with " + blAttr.getName() + ": " + e.getMessage() );
        }
        return result;
    }

    /**
     * keys are the names of the variables. values are descriptions.
     * @param cdf
     * @param dataOnly
     * @param rankLimit
     * @return map of parameter name to short description
     * @throws gsfc.nssdc.cdf.CDFException
     */
    public static Map<String, String> getPlottable(CDF cdf, boolean dataOnly, int rankLimit, boolean deep) throws CDFException {

        Map<String, String> result = new LinkedHashMap<String, String>();
        Map<String, String> dependent= new LinkedHashMap<String, String>();

        boolean isMaster= cdf.getName().contains("MASTERS"); // don't show of Epoch=0, just "Epoch"

        logger.fine("getting CDF variables");
        Vector v = cdf.getVariables();
        logger.log(Level.FINE, "got {0} variables", v.size());

        Attribute aAttr = null;

        Attribute catDesc = null;
        Attribute varNotes= null;
        Attribute virtual= null;
        Attribute function= null;
        Attribute varType= null;

        logger.fine("getting CDF attributes");
        try {
            aAttr = cdf.getAttribute("DEPEND_0");
        } catch (CDFException ex) {
        }
        try {
            catDesc = cdf.getAttribute("CATDESC");
        } catch (CDFException e) {
        }
        try {
            varNotes= cdf.getAttribute("VAR_NOTES");
        } catch (CDFException e) {
        }
        try {
            virtual= cdf.getAttribute("VIRTUAL");
        } catch (CDFException e) {
        }
        try {
            function= cdf.getAttribute("FUNCTION");
        } catch (CDFException e) {
        }
        try {
            varType= cdf.getAttribute("VAR_TYPE");
        } catch (CDFException e) {
        }

        int skipCount=0;
        for (int i=0; i<v.size(); i++ ) {
            Variable var = (Variable) v.get(i);
            if ( dataOnly ) {
                if ( varType==null ) {
                    skipCount++;
                } else {
                   if ( hasEntry( varType, var ) ) {
                       Entry varTypeEntry= varType.getEntry( var );
                       if ( !( String.valueOf( varTypeEntry.getData() ).equals( VAR_TYPE_DATA ) ) ) {
                           skipCount++;
                       }
                   }
                }
            }
        }
        if ( skipCount==v.size() ) {
            System.err.println( "turning off dataOnly because it rejects everything");
            dataOnly= false;
        }


        for (int i = 0; i < v.size(); i++) {
            Variable var = (Variable) v.get(i);
            if (var.getDataType() == Variable.CDF_CHAR || var.getDataType()==Variable.CDF_UCHAR ) {
                continue;
            }

            List<String> warn= new ArrayList();

            long maxRec = var.getMaxWrittenRecord();

            int rank;
            long[] dims = var.getDimSizes();
            if (dims == null) {
                rank = 1;
            } else {
                rank = dims.length + 1;
            }

            if (rank > rankLimit) {
                continue;
            }

            boolean isVirtual= false;
            if ( virtual!=null ) {
                try {
                    Entry entry = virtual.getEntry(var);
                    if ( String.valueOf(entry.getData()).equals("TRUE") ) {
                       if ( function!=null ) {
                            String sfunction= String.valueOf( function.getEntry(var).getData() );
                            if ( CdfVirtualVars.isSupported( sfunction ) ) {
                                isVirtual= true;
                           }
                        } else {
                           isVirtual= false;
                        }
                    }
                } catch ( CDFException ex ) {
                    //not a virtual variable
                }
            }

//            if ( maxRec==0 && ( dims==null || dims.length<1 || dims[0]==1 ) && !isVirtual ) {
//                logger.fine("skipping "+var.getName()+" because maxWrittenRecord is 0");
//                continue;
//            }

            if ( var.getName().equals("Time_PB5") ) {
                logger.log(Level.FINE, "skipping {0} because we always skip Time_PB5", var.getName());
                continue;
            }

            if ( false ) {
                result.put(var.getName(), null);
            } else {

                if ( dataOnly && varType!=null ) {
                    if ( hasEntry( varType, var ) ) {
                        Entry varTypeEntry= varType.getEntry( var );
                        if ( !( String.valueOf( varTypeEntry.getData() ).equals( VAR_TYPE_DATA ) ) ) {
                            continue;
                        }
                    }
                }

                Variable xDependVariable = null;
                long xMaxRec = -1;
                String scatDesc = null;
                String svarNotes = null;

                try {
                    if ( virtual!=null ) {
                        logger.log(Level.FINE, "get attribute {0} entry for {1}", new Object[]{virtual.getName(), var.getName()});
                        Entry entry = virtual.getEntry(var);
                        if ( String.valueOf(entry.getData()).equals("TRUE") ) {
                            if ( !isVirtual ) { // maybe some virtual functions are not supported.
                                continue;
                            }
                        }
                    }
                } catch (CDFException e) {
                    //e.printStackTrace();
                }
                try {
                    if (aAttr != null) {  // check for metadata for DEPEND_0
                        logger.log(Level.FINE, "get attribute {0} entry for {1}", new Object[]{aAttr.getName(), var.getName()});
                        Entry xEntry = aAttr.getEntry(var);
                        xDependVariable = cdf.getVariable(String.valueOf(xEntry.getData()));
                        xMaxRec = xDependVariable.getMaxWrittenRecord();
                        if ( xMaxRec!=maxRec && !isVirtual && var.getRecVariance() ) {
                            if ( maxRec==-1 ) maxRec+=1; //why?
                            warn.add("depend0 length is inconsistent with length ("+(maxRec+1)+")" );
                            //TODO: warnings are incorrect for Themis data.
                        }
                    }
                } catch (CDFException e) {
                    //e.printStackTrace();
                    warn.add( "problem with " + aAttr.getName() + ": " + e.getMessage() );
                }

                DepDesc dep1desc= getDepDesc( cdf, var, rank, dims, 1, warn );

                DepDesc dep2desc= getDepDesc( cdf, var, rank, dims, 2, warn );

                DepDesc dep3desc= getDepDesc( cdf, var, rank, dims, 3, warn );

                if (deep) {
                    try {
                        if (catDesc != null) {
                            logger.log(Level.FINE, "get attribute {0} entry for {1}", new Object[]{catDesc.getName(), var.getName()});
                            if ( hasEntry( catDesc, var ) ) {
                                Entry entry = catDesc.getEntry(var);
                                scatDesc = String.valueOf(entry.getData());
                            } else {
                                scatDesc = "";
                            }
                        }
                        if (varNotes!=null ) {
                            logger.log(Level.FINE, "get attribute {0} entry for {1}", new Object[]{varNotes.getName(), var.getName()});
                            if ( hasEntry( varNotes, var) ) {
                                Entry entry = varNotes.getEntry(var);
                                svarNotes = String.valueOf(entry.getData());
                            } else {
                                svarNotes = "";
                            }
                        }
                    } catch (CDFException e) {
                        warn.add( e.getMessage() );
                    }
                }

                String desc = "" + var.getName();
                if (xDependVariable != null) {
                    desc += "(" + xDependVariable.getName();
                    if ( xMaxRec>=0 || !isMaster ) { // small kludge for CDAWeb, where we expect masters to be empty.
                         desc+= "=" + (xMaxRec + 1);
                    }
                    if ( dep1desc.dep != null) {
                        desc += "," + dep1desc.dep.getName() + "=" + dep1desc.nrec + ( dep1desc.rank2 ? "*": "" );
                        if ( dep2desc.dep != null) {
                            desc += "," + dep2desc.dep.getName() + "=" + dep2desc.nrec + ( dep2desc.rank2 ? "*": "" );
                            if (dep3desc.dep != null) {
                                desc += "," + dep3desc.dep.getName() + "=" + dep3desc.nrec + ( dep3desc.rank2 ? "*": "" );
                            }
                        }
                    } else if ( rank>1 ) {
                        desc += ","+DataSourceUtil.strjoin( dims, ",");
                    }
                    desc += ")";
                }
                if ( hasEntry( virtual, var ) ) {
                    if ( "true".equalsIgnoreCase( String.valueOf( virtual.getEntry(var).getData() ) ) ) {
                        desc += " (Virtual)";
                    }
                }


                if (deep) {
                    StringBuilder descbuf = new StringBuilder("<html><b>" + desc + "</b><br>");

                    String recDesc= CDFUtils.getStringDataType(var);
                    if ( dims!=null ) {
                        recDesc= recDesc+"["+ DataSourceUtil.strjoin( dims, ",") + "]";
                    }
                    if (maxRec != xMaxRec)
                        descbuf.append("").append(maxRec + 1).append(" records of ").append(recDesc).append("<br>");
                    if (scatDesc != null)
                        descbuf.append("").append(scatDesc).append("<br>");
                    if (svarNotes !=null ) {
                        descbuf.append("<br><p><small>").append(svarNotes).append("<small></p>");
                    }

                    //if ( vdescr!=null && vdescr.length()>0 ) {
                    //    descbuf.append("<br>virtual variable implemented by ").append(vdescr).append("<br>");
                    //}

                    for ( String s: warn ) {
                        if ( s.startsWith("NOTE") ) {
                            descbuf.append("<br>").append(s);
                        } else {
                            descbuf.append("<br>WARNING: ").append(s);
                        }
                    }
                    
                    descbuf.append("</html>");
                    if ( xDependVariable!=null ) {
                        dependent.put(var.getName(), descbuf.toString());
                    } else {
                        result.put(var.getName(), descbuf.toString());
                    }
                } else {
                    if ( xDependVariable!=null ) {
                        dependent.put(var.getName(), desc);
                    } else {
                        result.put(var.getName(), desc);
                    }
                }

            }
        } // for

        logger.fine("done, get plottable ");

        dependent.putAll(result);

        return dependent;
    }

}
