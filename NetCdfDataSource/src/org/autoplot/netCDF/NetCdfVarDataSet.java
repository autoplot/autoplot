
package org.autoplot.netCDF;

import java.text.ParseException;
import java.util.logging.Logger;
import org.das2.datum.Units;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.das2.datum.LoggerManager;
import org.das2.datum.TimeParser;
import org.das2.datum.UnitsConverter;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.AbstractDataSet;
import org.das2.qds.DataSetOps;
import org.das2.qds.DataSetUtil;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.MetadataModel;
import org.das2.qds.ops.Ops;
import org.autoplot.metatree.IstpMetadataModel;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Range;
import ucar.nc2.Variable;
import ucar.nc2.Attribute;
import ucar.nc2.dataset.NetcdfDataset;

/**
 * wraps a netCDF variable (or HDF5 variable) to present it as a QDataSet.
 *
 * @author jbf
 */
public class NetCdfVarDataSet extends AbstractDataSet {

    Variable v;
    double[] data;
    int[] shape;

    private static final Logger logger= LoggerManager.getLogger("apdss.netcdf");

    public static NetCdfVarDataSet create( Variable variable, String constraint, NetcdfDataset ncfile, ProgressMonitor mon ) throws IOException {
        NetCdfVarDataSet result = new NetCdfVarDataSet(  );
        result.read(variable, ncfile, constraint, null, false, mon );
        return result;
    }
    
    private NetCdfVarDataSet(  )  {
        putProperty(QDataSet.QUBE, Boolean.TRUE);
    }

    private static String sliceConstraints( String constraints, int i ) {
        if ( constraints==null ) {
            return null;
        } else {
            if ( constraints.startsWith("[") && constraints.endsWith("]") ) {
                constraints= constraints.substring(1,constraints.length()-1);
            }
            String[] cc= constraints.split(",");
            if ( i>=cc.length ) {
                return null;
            } else if ( cc[i].equals(":") ) {
                return null;
            } else {
                return cc[i]; // TODO: this doesn't address depend variable that is rank 2, but this is not supported in NetCDF anyway.
            }
        }
    }
    /**
     * returns [ start, stop, stride ] or [ start, -1, -1 ] for slice.  This is
     * provided to reduce code and for uniform behavior.
     * 
     * See CdfJavaDataSource, which is where this was copied from.
     * @param constraint, such as "[0:100:2]" for even records between 0 and 100, non-inclusive.
     * @param recCount the number of records for when negative indeces are used.
     * @return [ start, stop, stride ] or [ start, -1, -1 ] for slice.
     * @throws java.text.ParseException
     */
     public static long[] parseConstraint(String constraint, long recCount) throws ParseException {
        long[] result = new long[]{0, recCount, 1};
        if (constraint == null) {
            return result;
        } else {
            if ( constraint.startsWith("[") && constraint.endsWith("]") ) {
                constraint= constraint.substring(1,constraint.length()-1);
            }
            try {
                String[] ss= constraint.split(":",-2);
                if ( ss.length>0 && ss[0].length()>0 ) {
                    result[0]= Integer.parseInt(ss[0]);
                    if ( result[0]<0 ) result[0]= recCount+result[0];
                }
                if ( ss.length>1 && ss[1].length()>0 ) {
                    result[1]= Integer.parseInt(ss[1]);
                    if ( result[1]<0 ) result[1]= recCount+result[1];
                }
                if ( ss.length>2 && ss[2].length()>0 ) {
                    result[2]= Integer.parseInt(ss[2]);
                }
                if ( ss.length==1 ) { // slice
                    result[1]= -1;
                    result[2]= -1;
                }
            } catch ( NumberFormatException ex ) {
                throw new ParseException("expected integer: "+ex.toString(),0);
            }
            return result;
        }
    }

     private int sliceCount( boolean [] slice, int idim ) {
         int result= 0;
         for ( int i=0; i<idim; i++ ) {
             if ( slice[i] ) result++;
         }
         return result;
     }

     private double[] unsigned( double[] data, long limit ) {
         for ( int i=0; i<data.length; i++ ) {
             double b= data[i];
             if ( b < 0 ) data[i]= b + limit;
         }
         return data;
     }
     
     /**
      * return a time parser object for the string.  If it is 17 digits
      * use "$Y$j$H$M$S$(subsec,places=3)", etc.
      * @param s string like "1997223000009297&lt;NULL&gt;"
      * @return TimeParser or null.
      */
     private TimeParser guessTimeParser( String s ) {
        TimeParser tp=null;
        int digitCount=-1;
        for ( int ich=0; ich<s.length(); ich++ ) {
            if ( !Character.isDigit(s.charAt(ich) ) ) {
                if ( digitCount==-1 ) digitCount= ich; // http://amda-dev.irap.omp.eu/BASE/DATA/WND/SWE/swe19970812.nc?Time has null char at 17.
            } else {
                if ( digitCount>-1 ) return null; // we found a non-digit preceeding a digit, so this isn't a block of digits like expected.
            }
        }
        switch (digitCount) {
            case 16:
                tp= TimeParser.create("$Y$j$H$M$S$(subsec,places=3)");
                break;
            case 17:
                tp= TimeParser.create("$Y$m$d$H$M$S$(subsec,places=3)");
                break;
            default:
        }
        return tp;
     }
     
    /**
     * Read the NetCDF data.
     * @param variable the NetCDF variable.
     * @param ncfile the NetCDF file.
     * @param constraints null, or string like "[0:10]"  Note it's allowed for the constraint to not have [] because this is called recursively.
     * @param mm if non-null, a metadata model, like IstpMetadataModel, is asserted.  If null, then any variable containing DEPEND_0 implies IstpMetadataModel.
     * @param mon
     * @throws IOException
     */
    private void read( Variable variable, NetcdfDataset ncfile, String constraints, MetadataModel mm, boolean isDepend, ProgressMonitor mon)  throws IOException {
        this.v= variable;
        if ( !mon.isStarted() ) mon.started(); //das2 bug: monitor blinks if we call started again here
        mon.setProgressMessage( "reading "+v.getNameAndDimensions() );

        if ( mm==null ) {
            long t0= System.currentTimeMillis();
            List<Variable> vvs=ncfile.getVariables();
            for ( Variable vv: vvs ) {
                if ( vv.findAttribute("DEPEND_0" )!=null ) {
                    mm= new IstpMetadataModel();
                }
            }
            logger.log(Level.FINER, "look for DEPEND_0 (ms):{0}", (System.currentTimeMillis()-t0));
        }
                
        logger.finer("v.getShape()");
        shape= v.getShape();
        boolean[] slice= new boolean[shape.length];

        ucar.ma2.Array a;
        if ( constraints!=null ) {
            if ( constraints.startsWith("[") && constraints.endsWith("]") ) {
                constraints= constraints.substring(1,constraints.length()-1);
            }
            try {

                String[] cc= constraints.split(",");
                List<Range> ranges= new ArrayList( v.getRanges() );
                for ( int i=0; i<cc.length; i++ ) {
                    long[] ir= parseConstraint( cc[i],ranges.get(i).last()+1 );
                    if ( ir[1]==-1 ) {
                        ranges.set( i, new Range((int)ir[0],(int)ir[0]) );
                        shape[i]= 1;
                        slice[i]= true;
                    } else {
                        ranges.set( i, new Range( (int)ir[0], (int)ir[1]-1, (int)ir[2] ) );
                        shape[i]= (int)( (ir[1]-ir[0])/ir[2] );
                    }
                }
                
                logger.finer("v.read()");
                a= v.read(ranges);

            } catch ( ParseException ex ) {
                throw new RuntimeException(ex);
            } catch ( InvalidRangeException ex ) {
                throw new RuntimeException(ex);
            }
        } else {
            logger.finer("v.read()");
            a= v.read();

        }

        char[] cdata=null;
        try {
            if ( a.getElementType()==char.class ) { // NASA/Goddard formats times as ISO8601 times.
                cdata= (char[])a.get1DJavaArray( char.class );
            } else if ( a.isUnsigned() && ( a.getElementType()==byte.class || a.getElementType()==short.class || a.getElementType()==int.class ) ) {
                data= (double[])a.get1DJavaArray( Double.class ); // TODO: reimplement with BufferDataSet which handles unsigned.
                if ( a.getElementType()==byte.class ) {
                    data= unsigned( data, 256L );
                } else if ( a.getElementType()==short.class ) {
                    data= unsigned( data, 256L*256L );
                } else if ( a.getElementType()==int.class ) {
                    data= unsigned( data, 256L*256L*256L*256L );
                }
            } else {
                data= (double[])a.get1DJavaArray( Double.class );
            }
        } catch ( ClassCastException ex ) {
            throw new IllegalArgumentException("data cannot be converted to numbers",ex);
        }
       
        properties.put( QDataSet.NAME, Ops.safeName(variable.getName()) );
        if ( shape.length>1 ) properties.put( QDataSet.QUBE, Boolean.TRUE );

        if ( v.getParentStructure()!=null ) { //TODO: this is probably wrong for structure of rank 2 data.
            shape= new int[] { data.length };
            slice= new boolean[shape.length];
        }
        
        boolean isCoordinateVariable= false;
        
        for ( int ir=0; ir<a.getRank(); ir++ ) {
            if ( !slice[ir] ) {
                logger.log(Level.FINER, "v.getDimension({0})", ir);
                ucar.nc2.Dimension d= v.getDimension(ir);

                if ( d!=null ) {
                    logger.log(Level.FINER, "ncfile.findVariable({0})", d.getName());
                    Variable cv = ncfile.findVariable(d.getName());
                    if ((cv != null) && cv.isCoordinateVariable()) {
                        logger.log(Level.FINE, "dimension '{0}' is coordinate variable, adding DEPEND", cv.getName());
                        Variable dv= cv;
                        if ( dv!=variable && dv.getRank()==1 ) {
                            mon.setProgressMessage( "reading "+dv.getNameAndDimensions() );
                            QDataSet dependi= create( dv, sliceConstraints(constraints,ir), ncfile, new NullProgressMonitor() );
                            properties.put( "DEPEND_"+(ir-sliceCount(slice,ir) ), dependi );
                        } else if ( dv!=variable && dv.getRank()==2 && dv.getDataType()==DataType.CHAR ) { // assume they are times.
                            mon.setProgressMessage( "reading "+dv.getNameAndDimensions() );
                            QDataSet dependi= create( dv, sliceConstraints(constraints,ir), ncfile, new NullProgressMonitor() );
                            properties.put( "DEPEND_"+(ir-sliceCount(slice,ir) ), dependi );
                        } else {
                            isCoordinateVariable= true;
                        }
                    } 
                }
            }
        }
        
        Map<String,Object> attributes= new HashMap();

        mon.setProgressMessage("reading attributes");

        logger.finer("v.getAttributes()");
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
                    u = Units.lookupTimeUnits(unitsString);
                } catch (ParseException ex) {
                    throw new RuntimeException(ex);
                }
                
                properties.put( QDataSet.UNITS, u );
                properties.put( QDataSet.MONOTONIC, Boolean.TRUE );
            }
        }

        if ( data==null ) {
            if ( cdata==null ) {
                throw new RuntimeException("Either data or cdata should be defined at this point");
            }
            //20110101T00:00 is 14 chars long.  "2011-Jan-01T00:00:00.000000000     " is 35 chars long. (LANL has padding after the times to make it 35 characters long.)
            if ( shape.length==2 && shape[1]>=14 && shape[1]<=35 ) { // NASA/Goddard translation service formats Times as strings, check for this.
                logger.fine("parsing times formatted in char arrays");
                data= new double[shape[0]];
                String ss= new String(cdata);
                TimeParser tp= null;
                boolean tryGuessTimeParser= true;
                for ( int i=0; i<shape[0]; i++ ) {
                    int n= i*shape[1];
                    String s= ss.substring( n, n+shape[1] );
                    try {
                        if ( tp!=null ) {
                            data[i]= tp.parse(s).getTime(Units.us2000);
                        } else {
                            data[i] = Units.us2000.parse(s).doubleValue(Units.us2000);
                        }
                    } catch (ParseException ex) {
                        if ( tryGuessTimeParser ) {
                            tryGuessTimeParser= false;
                            tp= guessTimeParser(s);
                            if ( tp==null ) {
                                data[i]= Units.us2000.getFillDouble();
                            } else {
                                try {
                                    data[i]= tp.parse(s).getTime(Units.us2000);
                                } catch ( ParseException ex2 ) {
                                    data[i]= Units.us2000.getFillDouble();
                                }
                            }
                        } else {
                            data[i]= Units.us2000.getFillDouble();
                        }
                    }
                }
                properties.put(QDataSet.UNITS,Units.us2000);
                shape= new int[] { shape[0] };
            } else {
                data= (double[])a.get1DJavaArray( Double.class ); // whoops, it wasn't NASA/Goddard data after all.
            }
        }

        if ( attributes.containsKey("_FillValue" ) ) {
            double fill= Double.parseDouble( (String) attributes.get("_FillValue") );
            properties.put( QDataSet.FILL_VALUE, fill );
        }


        if ( ( mm!=null && mm instanceof IstpMetadataModel ) ||  attributes.containsKey("VAR_TYPE") || attributes.containsKey("DEPEND_0") ) { // LANL want to create HDF5 files with ISTP metadata
            logger.log(Level.FINE, "variable '{0}' has VAR_TYPE or DEPEND_0 attribute, use ISTP metadata", v.getName());
            properties.put( QDataSet.METADATA_MODEL, QDataSet.VALUE_METADATA_MODEL_ISTP );            
            mm= new IstpMetadataModel();
            Map<String,Object> istpProps= mm.properties(attributes);
            if ( properties.get( QDataSet.UNITS )==Units.us2000 ) {
                UnitsConverter uc= UnitsConverter.getConverter(Units.cdfEpoch, Units.us2000 );
                if ( istpProps.containsKey(QDataSet.VALID_MIN) ) istpProps.put( QDataSet.VALID_MIN, uc.convert( (Number)istpProps.get(QDataSet.VALID_MIN ) ) );
                if ( istpProps.containsKey(QDataSet.VALID_MAX) ) istpProps.put( QDataSet.VALID_MAX, uc.convert( (Number)istpProps.get(QDataSet.VALID_MAX ) ) );
                if ( istpProps.containsKey(QDataSet.TYPICAL_MIN) ) istpProps.put( QDataSet.TYPICAL_MIN, uc.convert( (Number)istpProps.get(QDataSet.TYPICAL_MIN ) ) );
                if ( istpProps.containsKey(QDataSet.TYPICAL_MAX) ) istpProps.put( QDataSet.TYPICAL_MAX, uc.convert( (Number)istpProps.get(QDataSet.TYPICAL_MAX ) ) );
                istpProps.put(QDataSet.UNITS,Units.us2000);
            }
            properties.putAll(istpProps);

            for ( int ir=0; ir<a.getRank(); ir++ ) {
                String s= (String) attributes.get("DEPEND_"+ir);
                if ( s!=null ) {
                    logger.log(Level.FINER, "ncfile.findVariable({0})" , s );
                    Variable dv= ncfile.findVariable(s);
                    if ( dv!=null && dv!=variable ) {
                        
                        NetCdfVarDataSet result1 = new NetCdfVarDataSet(  );
                        result1.read( dv, ncfile, sliceConstraints(constraints,ir), mm, true, new NullProgressMonitor() );
                        QDataSet dependi= result1;
                        
                        properties.put( "DEPEND_"+(ir-sliceCount(slice,ir)), dependi );
                    }
                }
            }
            
                String[] vvs= new String[] { "DELTA_PLUS_VAR", "DELTA_MINUS_VAR" };
                for ( String vv: vvs ) {
                    if ( attributes.containsKey(vv ) ) {
                        
                        String s= (String)attributes.get(vv);
                        logger.log(Level.FINER, "{0} ({1})" , new Object[] { vv, s } );

                        Variable dv= ncfile.findVariable(s);
                        if ( dv!=null && dv!=variable ) {
                            String[] ss= vv.split("_");
                            NetCdfVarDataSet result1 = new NetCdfVarDataSet(  );
                            result1.read( dv, ncfile, sliceConstraints(constraints,0), mm, true, new NullProgressMonitor() );
                            QDataSet dependi= result1;

                            String qdatasetPropName;
                            if ( isDepend ) {
                                qdatasetPropName= "BIN_"+ss[1];
                            } else {
                                qdatasetPropName= "DELTA_"+ss[1];
                            }
                            properties.put( qdatasetPropName, dependi );
                        }
                    }
                }
        }

        // perform the slices
        ArrayList<Integer> newShape= new ArrayList(shape.length);
        for ( int i=0; i<shape.length; i++ ) {
            if ( !slice[i] ) {
                newShape.add( shape[i] );
            }
        }
        shape= new int[newShape.size()];
        for ( int i=0; i<newShape.size(); i++ ) shape[i]= newShape.get(i);

        // LANL produces data with obvious fill values, but no documentation for them.  Go ahead and specify that -1e90 is the valid min.
        if ( properties.get(QDataSet.FILL_VALUE)==null && properties.get(QDataSet.VALID_MIN)==null ) {
            properties.put( QDataSet.VALID_MIN, -1e90 );
        }

        if ( isCoordinateVariable ) {
            properties.put( QDataSet.CADENCE, DataSetUtil.guessCadenceNew(this,null) );
        }
        mon.finished();
        
    }
    
    @Override
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
        if ( index>=data.length) {
            throw new IllegalArgumentException("index out of bounds");
        }
        return data[ index ];
    }
    
    @Override
    public double value( int i, int j, int k ) {
        //int index= i + shape[0] * j + shape[0] * shape[1] * k;
        int index= k + shape[2] * j + shape[2] * shape[1] * i;
        if ( index>=data.length) {
            throw new IllegalArgumentException("index out of bounds");
        }
        return data[index];
    }

    @Override
    public double value( int i, int j, int k, int l ) {
        int index= l + shape[3] * k  + shape[3] * shape[2] * j + shape[3] * shape[2] * shape[1] * i;
        if ( index>=data.length) {
            throw new IllegalArgumentException("index out of bounds");
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

    @Override
    public QDataSet trim(int start, int end) {
        return super.trim(start, end); // TODO: introduce offset so we don't need to copy.
    }

    @Override
    public QDataSet slice(int i) {
        return super.slice(i);
    }
    
    
}
