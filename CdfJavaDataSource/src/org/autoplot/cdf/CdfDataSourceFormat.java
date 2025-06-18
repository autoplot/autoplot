
package org.autoplot.cdf;

import gov.nasa.gsfc.spdf.cdfj.CDFDataType;
import gov.nasa.gsfc.spdf.cdfj.CDFException;
import gov.nasa.gsfc.spdf.cdfj.CDFReader;
import gov.nasa.gsfc.spdf.cdfj.CDFWriter;
import gov.nasa.gsfc.spdf.cdfj.ReaderFactory;
import gov.nasa.gsfc.spdf.cdfj.SupportedTypes;
import java.lang.reflect.Array;
import org.autoplot.datasource.DataSourceUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsConverter;
import org.das2.datum.UnitsUtil;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.QDataSet;
import org.das2.qds.QubeDataSetIterator;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.DataSourceFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.LoggerManager;
import org.das2.qds.DataSetOps;
import org.das2.qds.SemanticOps;
import org.das2.qds.examples.Schemes;
import org.das2.qds.ops.Ops;
import org.das2.util.monitor.NullProgressMonitor;

/**
 * Format the QDataSet into CDF tables, using Nand Lal's library.
 * Datasets will be assigned names if they don't have a NAME property.
 * If the append=T parameter is set, then variables should have names.
 * if the bundle=T parameter is set, then bundles should be unbundled into separate variables.
 *
 * @author jbf
 */
public class CdfDataSourceFormat implements DataSourceFormat {

    Map<QDataSet,String> names;
    Map<String,QDataSet> namesRev;

    private static final Logger logger= LoggerManager.getLogger("apdss.cdf");
    
    public CdfDataSourceFormat() {
        names= new HashMap<>();
        namesRev= new HashMap<>();
    }

    //@Override
    public boolean streamData(Map<String, String> params, Iterator<QDataSet> data, OutputStream out) throws Exception {
        return false;
    }

    private synchronized String nameFor(QDataSet dep0) {
        String name= names.get(dep0);
        
        if ( name!=null ) {
            return name;
        } else {
            logger.log(Level.FINE, "new variable: {0}", dep0);
        }
        
        name = (String) dep0.property(QDataSet.NAME);
        if ( namesRev.containsKey(name) ) {
            int i= 1;
            while ( namesRev.containsKey(name+"_"+i) ) {
                i=i+1;
            }
            name= name + "_"+ i;
        }
        
        Units units = (Units) dep0.property(QDataSet.UNITS);
        if (name == null) {
            if ( units!=null && UnitsUtil.isTimeLocation(units)) {
                name = "Epoch";
            } else {
                name = "Variable_" + namesRev.size();
            }
        }
        
        names.put(dep0, name);
        namesRev.put(name, dep0);
        
        return name;
    }

    @Override
    public void formatData( String uri, QDataSet data, ProgressMonitor mon) throws Exception {

        mon.started();
        CDFWriter cdf;
        
        try {
            URISplit split= URISplit.parse( uri );
            java.util.Map<String, String> params= URISplit.parseParams( split.params );

            File ffile= new File( split.resourceUri.getPath() );

            boolean append= "T".equals( params.get("append") );
            boolean insert= "T".equals( params.get("insert") );
            boolean useRowMajority= "row".equals(params.get("majority") ); // note default is column, which might have been a poor choice.
            
            if ( !append && !insert ) {
                logger.log(Level.FINE, "create CDF file {0}", ffile);
                logger.log(Level.FINE, "call cdf= new CDFWriter( false )");
                cdf = new CDFWriter( useRowMajority );
            } else if ( insert ) {
                //CDFReader read= ReaderFactory.getReader( ffile.toString() );
                String name= (String)data.property(QDataSet.NAME);
                if ( name==null ) name= params.get("arg_0");
                if ( name==null ) {
                    throw new IllegalArgumentException("dataset must have name found within CDF file.");
                }
                int itype= 21;
                
                String type= CdfUtil.getStringDataType(itype);
                switch (type) {
                    case "CDF_CHAR":
                        type="string";
                        break;
                    case "CDF_REAL8":
                    case "CDF_DOUBLE":
                        type="double";
                        break;
                    case "CDF_FLOAT":
                    case "CDF_REAL4":
                        type="float";
                        break;
                    case "CDF_UINT1":
                        type="short";
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported type: "+type+", must be CHAR, REAL8, REAL4, DOUBLE, FLOAT, or UINT1.");
                }
                params.put("type",type);
                CDFReader read= ReaderFactory.getReader( ffile.toString() );
                logger.log(Level.FINE, "call cdf= new CDFWriter( {0}, false )", ffile.toString() );
                cdf = new CDFWriter( ffile.toString(), read.rowMajority() ); // read in the old file first
                
            } else {
                CDFReader read= ReaderFactory.getReader( ffile.toString() );
                for ( String n : read.getVariableNames() ) {
                    namesRev.put( n,null );
                    //TODO: there's a problem here, where it puts in a set of timetags for each variable.  https://sourceforge.net/p/autoplot/bugs/2514/
                }
                logger.log(Level.FINE, "call cdf= new CDFWriter( {0}, false )", ffile.toString() );
                cdf = new CDFWriter( ffile.toString(), read.rowMajority() ); // read in the old file first

            }

            String name1= params.get( "arg_0" );

            if ( name1!=null ) {
                names.put(data,name1);
                namesRev.put(name1,data);
            }

            logger.log( Level.FINE, "handling {0}", nameFor(data)); // allocate a good name

            QDataSet dep0 = (QDataSet) data.property(QDataSet.DEPEND_0);
            String dep0name=null;
            if ( insert ) dep0=null;
            
            if ( dep0 != null ) {
                if ( !append ) {
                    String name= nameFor(dep0);
                    dep0name= name;
                    Map<String,String> params1= new HashMap<>();
                    params1.put( "timeType",params.get("timeType") );
                    addVariableRankN( cdf, dep0, name, true, params1, mon.getSubtaskMonitor("dep0") );
                } else {
                    String name = (String) dep0.property(QDataSet.NAME);
                    if ( !namesRev.containsKey(name) ) { 
                        name= nameFor(dep0);
                        dep0name= name;
                        Map<String,String> params1= new HashMap<>();
                        params1.put( "timeType",params.get("timeType") );
                        try {
                            addVariableRankN( cdf, dep0, name, true, params1, mon.getSubtaskMonitor("dep0") );
                        } catch ( Exception e ) {
                            logger.fine("CDF Exception, presumably because the variable already exists.");
                        }
                    } else {
                        dep0name= name; // https://sourceforge.net/p/autoplot/bugs/2514/ use the variable if it has the right name.
                    }
                }
            }

            QDataSet dep1 = (QDataSet) data.property(QDataSet.DEPEND_1);
            if ( insert ) dep1=null;
            
            if (dep1 != null) {
                if ( !append ) {
                    String name= nameFor(dep1);
                    if ( dep1.rank()==1 ) {
                        addVariableRank1NoVary( cdf, dep1, name, true, new HashMap<String,String>(), 
                                mon.getSubtaskMonitor("dep1") );
                    } else {
                        addVariableRankN( cdf, dep1, name, true, new HashMap<String,String>(), mon.getSubtaskMonitor("dep1") );
                    }
                } else {
                    String name = (String) dep1.property(QDataSet.NAME);
                    if ( !namesRev.containsKey(name) ) {  
                        name= nameFor(dep1);
                        Map<String,String> params1= new HashMap<>();
                        try {
                            if ( dep1.rank()==1 ) {
                                addVariableRank1NoVary( cdf, dep1, name, true, params1, mon.getSubtaskMonitor("dep1") );
                            } else {
                                addVariableRankN( cdf, dep1, name, true, params1, mon.getSubtaskMonitor("dep1") );
                            }
                        } catch ( Exception e ) {
                            logger.fine("CDF Exception, presumably because the variable already exists.");
                        }                
                    } else {
                        names.put(dep1,name);
                    }
                }
            }

            QDataSet dep2 = (QDataSet) data.property(QDataSet.DEPEND_2);
            if ( insert ) dep2=null;

            if (dep2 != null) {
                if ( !append ) {
                    String name= nameFor(dep2);
                    if ( dep2.rank()==1 ) {
                        addVariableRank1NoVary( cdf, dep2, name, true, new HashMap<String,String>(), 
                                mon.getSubtaskMonitor("dep2") );
                    } else {
                        addVariableRankN( cdf, dep2, name, true, new HashMap<String,String>(), mon.getSubtaskMonitor("dep2") );
                    }
                } else {
                    String name= (String) dep2.property(QDataSet.NAME);
                    if ( !namesRev.containsKey(name) ) { 
                        name= nameFor(dep2);
                        Map<String,String> params1= new HashMap<>();
                        try {
                            if ( dep2.rank()==1 ) {
                                addVariableRank1NoVary( cdf, dep2, name, true, params1, mon.getSubtaskMonitor("dep2") );
                            } else {
                                addVariableRankN( cdf, dep2, name, true, params1, mon.getSubtaskMonitor("dep2") );
                            }
                        } catch ( Exception e ) {
                            logger.fine("CDF Exception, presumably because the variable already exists.");
                        }                
                    } else {
                        names.put(dep2,name); 
                    }
                }
            }

            QDataSet dep3 = (QDataSet) data.property(QDataSet.DEPEND_3);
            if ( insert ) dep3=null;

            if (dep3 != null) {
                if ( !append ) {
                    String name= nameFor(dep3);
                    if ( dep3.rank()==1 ) {
                        addVariableRank1NoVary( cdf, dep3, name, true, new HashMap<String,String>(), 
                                mon.getSubtaskMonitor("dep3") );
                    } else {
                        addVariableRankN( cdf, dep3, name, true, new HashMap<String,String>(), mon.getSubtaskMonitor("dep3") );
                    }
                } else {
                    String name= (String) dep2.property(QDataSet.NAME);
                    if ( !namesRev.containsKey(name) ) { 
                        name= nameFor(dep3);                                        
                        Map<String,String> params1= new HashMap<>();
                        try {
                            if ( dep3.rank()==1 ) {
                                addVariableRank1NoVary( cdf, dep3, name, true, params1, mon.getSubtaskMonitor("dep3") );
                            } else {
                                addVariableRankN( cdf, dep3, name, true, params1, mon.getSubtaskMonitor("dep3") );
                            }
                        } catch ( Exception e ) {
                            logger.fine("CDF Exception, presumably because the variable already exists.");
                        }                
                    } else {
                        names.put(dep3,name); 
                    }
                }
            }
            
            QDataSet bds= (QDataSet) data.property(QDataSet.BUNDLE_1);
            if ( bds != null && insert==false ) {
                if ( !append && data.rank()==2 ) {
                    if ( dep1==null ) {
                        logger.fine("writing bundled datasets to CDF separately.");
                    } else {
                        String name= nameFor(bds);
                        if ( bds.rank()==1 || ( bds.rank()==2 && Schemes.isBundleDescriptor(bds) ) ) {
                            addVariableRank1NoVary( cdf, bds, name, true, new HashMap<String,String>(), 
                                    mon.getSubtaskMonitor("bundle1") );
                        }
                    }
                } else {
                    String name= nameFor(bds);
                    Map<String,String> params1= new HashMap<>();
                    try {
                        addVariableRank1NoVary( cdf, bds, name, true, params1, mon.getSubtaskMonitor("bundle1") );
                    } catch ( Exception e ) {
                        logger.fine("CDF Exception, presumably because the variable already exists.");
                    }                
                }
            }

            if ( bds!=null && dep1==null && "T".equals(params.get("bundle")) ) {
                for ( int i=0; i<bds.length(); i++ ) {
                    QDataSet data1= Ops.unbundle( data, i ) ;
                    addVariableRankN( cdf, data1, nameFor(data1), false, params, mon.getSubtaskMonitor("bundle") );
                    if ( dep0!=null ) cdf.addVariableAttributeEntry( nameFor(data1), "DEPEND_0", CDFDataType.CHAR, dep0name );
                }
                
            } else if ( data.rank()==3 && data.property(QDataSet.BUNDLE_2)!=null && "T".equals(params.get("bundle")) ) {
                int n= data.length(0,0);
                for ( int i=0; i<n; i++ ) {
                    QDataSet data1= Ops.slice2( data, i );
                    addVariableRankN( cdf, data1, nameFor(data1), false, params, mon.getSubtaskMonitor("rank3") );
                    if ( dep0!=null ) cdf.addVariableAttributeEntry( nameFor(data1), "DEPEND_0", CDFDataType.CHAR, dep0name );
                }
                
            } else {
                addVariableRankN( cdf, data, nameFor(data), false, params, mon.getSubtaskMonitor("bundle1") );

                try {
                    if ( dep0!=null ) cdf.addVariableAttributeEntry( nameFor(data), "DEPEND_0", CDFDataType.CHAR, dep0name );
                    if ( dep1!=null ) cdf.addVariableAttributeEntry( nameFor(data), "DEPEND_1", CDFDataType.CHAR, nameFor(dep1) );
                    if ( dep2!=null ) cdf.addVariableAttributeEntry( nameFor(data), "DEPEND_2", CDFDataType.CHAR, nameFor(dep2) );
                    if ( dep3!=null ) cdf.addVariableAttributeEntry( nameFor(data), "DEPEND_3", CDFDataType.CHAR, nameFor(dep3) );
                    if ( bds!=null )  cdf.addVariableAttributeEntry( nameFor(data), "LABL_PTR_1", CDFDataType.CHAR, nameFor(bds) );
                } catch ( CDFException.WriterError ex ) {
                    logger.log( Level.WARNING, ex.getMessage() , ex );
                }
            }

            mon.setProgressMessage("writing file");
            if ( !( append || insert ) ) {
                if ( ffile.exists() ) {
                    CdfDataSource.cdfCacheReset();
                    File tempFile= File.createTempFile( "deleteme",".cdf");
                    if ( !ffile.renameTo( tempFile) ) {
                        if ( !ffile.delete() ) {
                            logger.log(Level.WARNING, "file {0} cannot be deleted", ffile );
                        } else {
                            if ( ffile.exists() ) {
                                logger.log(Level.WARNING, "file {0} cannot be renamed", ffile);
                            }
                        }
                    } 
                    write( cdf, ffile.toString() );
                    if ( tempFile.exists() && !tempFile.delete() ) {
                        logger.log(Level.WARNING, "file {0} cannot be deleted", tempFile);
                    }
                } else {
                    write( cdf, ffile.toString() );
                }
            } else if ( insert ) {
                CdfDataSource.cdfCacheReset();
                cdf.write( ffile.toString() );
                
            } else {
                write( cdf, ffile.toString() );
            }
        
        } finally {
            mon.finished();
        }
        
    }

    private static void addVariableRank1NoVary( CDFWriter cdf, QDataSet ds, String name, boolean isSupport, 
            Map<String,String> params, org.das2.util.monitor.ProgressMonitor mon ) throws Exception {
        Units units = (Units) ds.property(QDataSet.UNITS);
        CDFDataType type = CDFDataType.DOUBLE;

        UnitsConverter uc = UnitsConverter.IDENTITY;

        if (units != null && UnitsUtil.isTimeLocation(units)) {
            type = CDFDataType.EPOCH;
            uc = units.getConverter(Units.cdfEpoch);
        }

        if ( ds.rank()==1 ) {
            //cdf.defineNRVVariable( name, type, new int[0], 0 );
            //cdf.createVariable( name, type, new int[0] );
            
            Object array= CdfDataSourceFormat.datasetToArray( ds, uc, type, mon );
            logger.log(Level.FINE, "call cdf.addNRVVariable( {0},{1},{2})", 
                    new Object[]{name, logName(type), logName( new int[] { ds.length() } ), logName(array) });
            cdf.addNRVVariable( name, type, new int[] { ds.length() }, array );
        } else if ( Schemes.isBundleDescriptor(ds) ) {
            String[] array= new String[ ds.length() ];
            String[] ss= DataSetOps.bundleNames(ds);
            int dim=0; // max number of characters
            for ( int i=0; i<ds.length(); i++ ) {
                String s= (String)ds.property( QDataSet.LABEL, i );
                if ( s==null ) s= (String)ds.property( QDataSet.NAME, i );
                if ( s==null ) s= ss[i];
                array[i]= s;
                int l= s.length();
                dim= dim<l ? l : dim;
            }
            logger.log(Level.FINE, "call cdf.addNRVVariable( {0},{1},{2})", 
                    new Object[]{name, logName(type), logName( new int[] { ds.length() } ), logName(array) });
            
            cdf.addNRVVariable( name, CDFDataType.CHAR, new int[] { ds.length() }, dim, array );
            
        } else {
            throw new IllegalArgumentException("not supported!");
            
        }
        copyMetadata( cdf, units, name, type, isSupport, ds);
        
    }

    /**
     * see UIntDataSet in BufferDataSet
     * @param d the double to encode
     * @return the unsigned integer bit equivalent.
     */
    private static int encodeUINT4( double d ) {
        return (int)( d > 2147483648. ? d - 4294967296. : d );
    }

    private static short encodeUINT2( double d ) {
        return (short)( d > 32768 ? d - 65536 : d );
    }
    
    private static byte encodeUINT1( double d ) {
        return (byte)( d > 128 ? d - 256 : d );
    }
    
    /**
     * convert the rank 1 dataset to a buffer.
     * @param ds rank 1 dataset
     * @param uc units converter to convert the type.
     * @param type type code, such as CDF_DOUBLE indicating how the data should be converted.
     * @return buffer of this type.
     */
    private static ByteBuffer doIt1Nio( QDataSet ds, UnitsConverter uc, CDFDataType type ) {
        ByteBuffer export;
        QubeDataSetIterator iter = new QubeDataSetIterator(ds);
        if ( type==CDFDataType.DOUBLE || type==CDFDataType.EPOCH ) {
            ByteBuffer buf= ByteBuffer.allocate( ds.length()*8 );
            buf.order( ByteOrder.LITTLE_ENDIAN );
            while (iter.hasNext()) {
                iter.next();
                buf.putDouble( uc.convert(iter.getValue(ds) ) );
            }
            export= buf;
        } else if ( type==CDFDataType.TT2000 ) {
            ByteBuffer buf= ByteBuffer.allocate( ds.length()*8 );
            buf.order( ByteOrder.LITTLE_ENDIAN );
            while (iter.hasNext()) {
                iter.next();
                buf.putLong( (long)uc.convert(iter.getValue(ds) ) );
            }
            export= buf;
            
        } else if ( type==CDFDataType.FLOAT ) {
            ByteBuffer buf= ByteBuffer.allocate( ds.length()*4 );
            buf.order( ByteOrder.LITTLE_ENDIAN );
            while (iter.hasNext()) {
                iter.next();
                buf.putFloat( (float)uc.convert(iter.getValue(ds) ) );
            }
            export= buf;

        } else if ( type==CDFDataType.INT4 ) {
            ByteBuffer buf= ByteBuffer.allocate( ds.length()*4 );
            buf.order( ByteOrder.LITTLE_ENDIAN );
            while (iter.hasNext()) {
                iter.next();
                buf.putInt( (int)uc.convert(iter.getValue(ds) ) );
            }
            export= buf;

        } else if ( type==CDFDataType.INT2 ) {
            ByteBuffer buf= ByteBuffer.allocate( ds.length()*2 );
            buf.order( ByteOrder.LITTLE_ENDIAN );
            while (iter.hasNext()) {
                iter.next();
                buf.putShort( (short)uc.convert(iter.getValue(ds) ) );
            }
            export= buf;

        } else if ( type==CDFDataType.INT1 ) {
            ByteBuffer buf= ByteBuffer.allocate( ds.length()*1 );
            //buf.order( ByteOrder.LITTLE_ENDIAN );
            while (iter.hasNext()) {
                iter.next();
                buf.put( (byte)uc.convert(iter.getValue(ds) ) );
            }
            export= buf;

        } else if ( type==CDFDataType.UINT4 ) {
            ByteBuffer buf= ByteBuffer.allocate( ds.length()*4 );
            buf.order( ByteOrder.LITTLE_ENDIAN );
            while (iter.hasNext()) {
                iter.next();
                buf.putInt( encodeUINT4( uc.convert(iter.getValue(ds) ) ) );
            }
            export= buf;

        } else if ( type==CDFDataType.UINT2 ) {
            ByteBuffer buf= ByteBuffer.allocate( ds.length()*2 );
            buf.order( ByteOrder.LITTLE_ENDIAN );
            while (iter.hasNext()) {
                iter.next();
                buf.putShort(encodeUINT2( uc.convert(iter.getValue(ds) ) ) );
            }
            export= buf;

        } else if ( type==CDFDataType.UINT1 ) {
            ByteBuffer buf= ByteBuffer.allocate( ds.length()*1 );
            //buf.order( ByteOrder.LITTLE_ENDIAN );
            while (iter.hasNext()) {
                iter.next();
                buf.put( encodeUINT1( uc.convert(iter.getValue(ds) ) ) );
            }
            export= buf;
        } else if ( type==CDFDataType.CHAR ) {
            int maxLenth= 1;
            ByteBuffer buf= ByteBuffer.allocate( ds.length()*maxLenth );
            for ( int i=0; i<ds.length(); i++ ) {
                buf.put( (byte)ds.slice(i).svalue().charAt(0) );
            }
            export= buf;

        } else {
            throw new IllegalArgumentException("not supported: "+type);
        }
        export.flip();
        return export;

    }
    
    /**
     * CDF library needs array in double or triple arrays.  
     * 
     * @param ds the dataset.
     * @param uc UnitsConverter in case we need to handle times.
     * @param type the data type.
     * @return a ByteBuffer containing the data.
     */
    private static ByteBuffer dataSetToNioArray( QDataSet ds, UnitsConverter uc, CDFDataType type, ProgressMonitor mon ){
        switch (ds.rank()) {
            case 1:
                return doIt1Nio( ds, uc, type );
            case 2:
                throw new UnsupportedOperationException("not implemented");
            case 3:
                throw new UnsupportedOperationException("not implemented");
            case 4:
                throw new UnsupportedOperationException("not implemented");
            default:
                throw new IllegalArgumentException("rank 0 not supported");
        }
        
    }
    
    /**
     * convert the rank 1 dataset to a native array.
     * @param ds rank 1 dataset
     * @param uc units converter to convert the type.
     * @param type type code, such as CDF_DOUBLE indicating how the data should be converted.
     * @return array of this type.
     */
    private static Object doIt1( QDataSet ds, UnitsConverter uc, CDFDataType type ) {
        Object export;
        QubeDataSetIterator iter = new QubeDataSetIterator(ds);
        if ( type==CDFDataType.DOUBLE || type==CDFDataType.EPOCH ) {
            double[] dexport= new double[ ds.length() ];
            int i = 0;
            while (iter.hasNext()) {
                iter.next();
                dexport[i++] = uc.convert(iter.getValue(ds));
            }
            export= dexport;
       } else if ( type==CDFDataType.TT2000 ) {
            long[] dexport= new long[ ds.length() ];
            int i = 0;
            while (iter.hasNext()) {
                iter.next();
                dexport[i++] = (long)uc.convert(iter.getValue(ds));
            }
            export= dexport;
            
        } else if ( type==CDFDataType.FLOAT ) {
            float[] fexport= new float[ ds.length() ];
            int i = 0;
            while (iter.hasNext()) {
                iter.next();
                fexport[i++] = (float)uc.convert(iter.getValue(ds));
            }
            export= fexport;

        } else if ( type==CDFDataType.INT4 ) {
            int[] bexport= new int[ ds.length() ];
            int i = 0;
            while (iter.hasNext()) {
                iter.next();
                bexport[i++] = (int)uc.convert(iter.getValue(ds));
            }
            export= bexport;

        } else if ( type==CDFDataType.INT2 ) {
            short[] bexport= new short[ ds.length() ];
            int i = 0;
            while (iter.hasNext()) {
                iter.next();
                bexport[i++] = (short)uc.convert(iter.getValue(ds));
            }
            export= bexport;

        } else if ( type==CDFDataType.INT1 ) {
            byte[] bexport= new byte[ ds.length() ];
            int i = 0;
            while (iter.hasNext()) {
                iter.next();
                bexport[i++] = (byte)uc.convert(iter.getValue(ds));
            }
            export= bexport;

        } else if ( type==CDFDataType.CHAR ) {
            String[] s= new String[ ds.length() ];
            for ( int i=0; i<ds.length(); i++ ) {
                s[i]= ds.slice(i).svalue();
            }
            export= s;
        
        } else if ( type==CDFDataType.UINT1 ) {
            short[] bexport= new short[ ds.length() ];
            int i = 0;
            while (iter.hasNext()) {
                iter.next();
                bexport[i++] = (short)uc.convert(iter.getValue(ds));
            }
            export= bexport;
            
        } else if ( type==CDFDataType.UINT2 ) {
            int[] bexport= new int[ ds.length() ];
            int i = 0;
            while (iter.hasNext()) {
                iter.next();
                bexport[i++] = (short)uc.convert(iter.getValue(ds));
            }
            export= bexport;

        } else if ( type==CDFDataType.UINT4 ) {
            long[] bexport= new long[ ds.length() ];
            int i = 0;
            while (iter.hasNext()) {
                iter.next();
                bexport[i++] = (short)uc.convert(iter.getValue(ds));
            }
            export= bexport;

        } else {
            throw new IllegalArgumentException("not supported: "+type);
        }
        return export;

    }

    /**
     * convert the rank 2 dataset to a native array.
     * @param ds rank 2 dataset
     * @param uc units converter to convert the type.
     * @param type type code, such as CDF_DOUBLE indicating how the data should be converted.
     * @param mon monitor
     * @return array of this type.
     */
    private static Object doIt2( QDataSet ds, UnitsConverter uc, CDFDataType type, ProgressMonitor mon ) {
        Object export;
        QubeDataSetIterator iter = new QubeDataSetIterator(ds);
        iter.setMonitor(mon);
        int elements= ds.length()*ds.length(0);
        if ( type==CDFDataType.DOUBLE || type==CDFDataType.EPOCH ) {
            double[] dexport= new double[ elements ];
            int i = 0;
            while (iter.hasNext()) {
                iter.next();
                dexport[i++] = uc.convert(iter.getValue(ds));
            }
            export= dexport;
       } else if ( type==CDFDataType.TT2000 ) {
            long[] dexport= new long[ elements ];
            int i = 0;
            while (iter.hasNext()) {
                iter.next();
                dexport[i++] = (long)uc.convert(iter.getValue(ds));
            }
            export= dexport;
            
        } else if ( type==CDFDataType.FLOAT ) {
            float[] fexport= new float[ elements ];
            int i = 0;
            while (iter.hasNext()) {
                iter.next();
                fexport[i++] = (float)uc.convert(iter.getValue(ds));
            }
            export= fexport;

        } else if ( type==CDFDataType.INT4 ) {
            int[] bexport= new int[ elements ];
            int i = 0;
            while (iter.hasNext()) {
                iter.next();
                bexport[i++] = (int)uc.convert(iter.getValue(ds));
            }
            export= bexport;

        } else if ( type==CDFDataType.INT2 ) {
            short[] bexport= new short[ elements ];
            int i = 0;
            while (iter.hasNext()) {
                iter.next();
                bexport[i++] = (short)uc.convert(iter.getValue(ds));
            }
            export= bexport;

        } else if ( type==CDFDataType.INT1 ) {
            byte[] bexport= new byte[ elements ];
            int i = 0;
            while (iter.hasNext()) {
                iter.next();
                bexport[i++] = (byte)uc.convert(iter.getValue(ds));
            }
            export= bexport;

        } else if ( type==CDFDataType.CHAR ) {
            String[] s= new String[ elements ];
            for ( int i=0; i<ds.length(); i++ ) {
                s[i]= ds.slice(i).svalue();
            }
            export= s;
        
        } else if ( type==CDFDataType.UINT1 ) {
            short[] bexport= new short[ elements ];
            int i = 0;
            while (iter.hasNext()) {
                iter.next();
                bexport[i++] = (short)uc.convert(iter.getValue(ds));
            }
            export= bexport;
            
        } else if ( type==CDFDataType.UINT2 ) {
            int[] bexport= new int[ elements ];
            int i = 0;
            while (iter.hasNext()) {
                iter.next();
                bexport[i++] = (short)uc.convert(iter.getValue(ds));
            }
            export= bexport;

        } else if ( type==CDFDataType.UINT4 ) {
            long[] bexport= new long[ elements ];
            int i = 0;
            while (iter.hasNext()) {
                iter.next();
                bexport[i++] = (short)uc.convert(iter.getValue(ds));
            }
            export= bexport;

        } else {
            throw new IllegalArgumentException("not supported: "+type);
        }
        return export;

    }
    
    /**
     * CDF library needs array in double or triple arrays.  
     * 
     * @param ds the dataset.
     * @param uc UnitsConverter in case we need to handle times.
     * @param itype the CDF data type, for example 21=float
     * @param mon a progress monitor
     * @return a 1,2,3,4-d array of double,long,float,int,short,byte.
     */
    public static Object datasetToArray( QDataSet ds, UnitsConverter uc, int itype, ProgressMonitor mon ){
        CDFDataType type= SupportedTypes.cdfType(itype);
        return CdfDataSourceFormat.datasetToArray(ds, uc, type, mon);
    }
    
    /**
     * CDF library needs array in double or triple arrays.  
     * 
     * @param ds the dataset.
     * @param uc UnitsConverter in case we need to handle times.
     * @param type the data type.
     * @param mon a progress monitor
     * @return a 1,2,3,4-d array of double,long,float,int,short,byte.
     */
    public static Object datasetToArray( QDataSet ds, UnitsConverter uc, CDFDataType type, ProgressMonitor mon ){
        Object oexport;
        if ( uc==null ) uc= UnitsConverter.IDENTITY;
        if ( mon==null ) mon= new NullProgressMonitor();
        switch (ds.rank()) {
            case 1:
                return doIt1( ds, uc, type );
            case 2:
                return doIt2( ds, uc, type, mon );
            case 3:
                if ( type==CDFDataType.DOUBLE ) {
                    oexport= new double[ds.length()][][];
                } else if ( type==CDFDataType.TT2000 ) {
                    oexport= new long[ds.length()][][];
                } else if ( type==CDFDataType.FLOAT ) {
                    oexport= new float[ds.length()][][];
                } else if ( type==CDFDataType.INT4 ) {
                    oexport= new int[ds.length()][][];
                } else if ( type==CDFDataType.INT2 ) {
                    oexport= new short[ds.length()][][];
                } else if ( type==CDFDataType.INT1 ) {
                    oexport= new byte[ds.length()][][];
                } else {
                    throw new IllegalArgumentException("type not supported"+type);
                }
                mon.setTaskSize(ds.length());
                mon.started();
                for ( int i=0; i<ds.length(); i++ ) {
                    mon.setTaskProgress(i);
                    Array.set(oexport, i, CdfDataSourceFormat.datasetToArray( ds.slice(i), uc, type, null ) );
                }
                mon.finished();
                return oexport;
            case 4:
                if ( type==CDFDataType.DOUBLE ) {
                    oexport= new double[ds.length()][][][];
                } else if ( type==CDFDataType.TT2000 ) {
                    oexport= new long[ds.length()][][][];
                } else if ( type==CDFDataType.FLOAT ) {
                    oexport= new float[ds.length()][][][];
                } else if ( type==CDFDataType.INT4 ) {
                    oexport= new int[ds.length()][][][];
                } else if ( type==CDFDataType.INT2 ) {
                    oexport= new short[ds.length()][][][];
                } else if ( type==CDFDataType.INT1 ) {
                    oexport= new byte[ds.length()][][][];
                } else {
                    throw new IllegalArgumentException("type not supported"+type);
                }
                mon.setTaskSize(ds.length());
                mon.started();
                for ( int i=0; i<ds.length(); i++ ) {
                    mon.setTaskProgress(i);
                    Array.set(oexport, i, CdfDataSourceFormat.datasetToArray( ds.slice(i), uc, type, null ) );
                }
                mon.finished();
                return oexport;
            default:
                throw new IllegalArgumentException("rank 0 not supported");
        }
        
    }

    private static void addVariableRankN( CDFWriter cdf, QDataSet ds, String name, boolean isSupport, 
            Map<String,String> params, org.das2.util.monitor.ProgressMonitor mon) throws Exception {
        Units units = (Units) ds.property(QDataSet.UNITS);
        CDFDataType type = CDFDataType.DOUBLE;
        
        String sinsert=params.get("insert");
        boolean insert= sinsert!=null && sinsert.startsWith("T");
        
        if ( insert ) {
            type = cdf.getVariableType(name);
        } else {

            String t= params.get("type");
            if ( t!=null ) {
                switch (t) {
                    case "float":
                        type= CDFDataType.FLOAT;
                        break;
                    case "byte":
                        type= CDFDataType.INT1;
                        break;
                    case "int1":
                        type= CDFDataType.INT1;
                        break;
                    case "int2":
                        type= CDFDataType.INT2;
                        break;
                    case "int4":
                        type= CDFDataType.INT4;
                        break;
                    case "uint1":
                        type= CDFDataType.UINT1;
                        break;
                    case "uint2":
                        type= CDFDataType.UINT2;
                        break;
                    case "uint4":
                        type= CDFDataType.UINT4;
                        break;
                    case "double":
                        type= CDFDataType.DOUBLE;
                        break;
                    case "string":
                        type= CDFDataType.CHAR;
                        break;

                    default:
                        logger.log(Level.WARNING, "unsupported type, using double: {0}", t);
                        break;
                }
            } else {
                if ( ds.rank()<3 ) {
                    type= CDFDataType.DOUBLE;
                } else {
                    type= CDFDataType.FLOAT;
                }
            }
        }
        
        boolean compressed= "T".equals( params.get("compressed") );

        UnitsConverter uc = UnitsConverter.IDENTITY;

        if (units != null && UnitsUtil.isTimeLocation(units)) {
            boolean tt2000= !( "epoch".equals( params.get("timeType") ) );
            if ( tt2000 ) {                
                type = CDFDataType.TT2000;
                uc = units.getConverter(Units.cdfTT2000);
                units= Units.cdfTT2000;
            } else {
                type = CDFDataType.EPOCH;
                uc = units.getConverter(Units.cdfEpoch);
                units= Units.cdfEpoch;
            }
        }

        if ( ds.rank()==0 ) {
            throw new IllegalArgumentException("rank 0 data not supported");
        }
        
        if ( ds.rank()>4 ) {
            throw new IllegalArgumentException("high rank data not supported");
        }
        
            
        if ( compressed ) {
            if ( ds.rank()==1 ) {
                logger.log(Level.FINE, "call cdf.defineCompressedVariable( {0}, {1}, {2} )", 
                        new Object[] { name, logName(type), logName(new int[0]) } );
                cdf.defineCompressedVariable( name, type, new int[0] );
                addData( cdf, name, dataSetToNioArray( ds, uc, type, mon ) ); //TODO: I think I need to compress the channel.
            } else { 
                switch (ds.rank()) {
                    case 2:
                        defineCompressedVariable( cdf, name, type, new int[] { ds.length(0) } );
                        break;
                    case 3:
                        defineCompressedVariable( cdf, name, type, new int[] { ds.length(0),ds.length(0,0) } );
                        break;
                    case 4:
                        defineCompressedVariable( cdf, name, type, new int[] { ds.length(0),ds.length(0,0),ds.length(0,0,0) } );
                        break;
                    default:
                        break;
                }
                Object o= CdfDataSourceFormat.datasetToArray( ds, uc, type, mon );
                addData( cdf, name, o );
            }
            
        } else {            
            if ( ds.rank()==1 ) {
                if ( !insert ) {
                    defineVariable( cdf, name, type, new int[0] );
                    addData( cdf, name, dataSetToNioArray( ds, uc, type, mon ) );
                } else {
                    addData( cdf, name, doIt1( ds, uc, type ) );
                }
                
            } else { // this branch doesn't use dataSetToNioArray
                if ( !insert ) {
                    switch (ds.rank()) {
                        case 2:
                            defineVariable( cdf, name, type, new int[] { ds.length(0) } );
                            break;
                        case 3:
                            defineVariable( cdf, name, type, new int[] { ds.length(0),ds.length(0,0) } );
                            break;
                        case 4:
                            defineVariable( cdf, name, type, new int[] { ds.length(0),ds.length(0,0),ds.length(0,0,0) } );
                            break;
                        default:
                            break;
                    }
                }
                addData(cdf, name, CdfDataSourceFormat.datasetToArray( ds, uc, type, mon ) );
            }
        }

        if ( !insert ) {
            copyMetadata( cdf, units, name, type, isSupport, ds );
        }
        
    }
    
    /**
     * return expressions so example testing codes can be written
     * @param o
     * @return 
     */
    private static String logName( Object o ) {
        if ( o.getClass().isArray() ) {
            StringBuilder s= new StringBuilder(o.getClass().getComponentType().toString()+"[");
            s.append( Array.getLength(o));
            if ( Array.getLength(o)>0 ) {
                o= Array.get(o,0);
                while ( o.getClass().isArray() ) {
                    s.append(",").append(Array.getLength(o));
                    o= Array.get(o,0);
                }
            }
            s.append("]");
            return s.toString();
        } else if ( o instanceof String ) {
            return "\"" + o + "\"";
        } else if ( o instanceof CDFDataType ) {
            return "CDFDataType=" + ((CDFDataType)o).getValue();
        } else {
            return o.toString();
        }
    }
    
    private static void write( CDFWriter cdf, String name ) throws IOException {
        logger.log(Level.FINE, "call cdf.write({0})", new Object[] { logName(name) } );
        try {
            CdfDataSource.cdfCacheReset();
            cdf.write( name );
        } catch ( FileNotFoundException ex ){
            logger.log(Level.WARNING, "first attempt to write \"{0}\" fails, try again for good measure", name);
            CdfDataSource.cdfCacheReset();
            System.gc();
            try {
                Thread.sleep(1000);
                System.gc();
                Thread.sleep(1000);
                System.gc();
            } catch (InterruptedException ex1) {
                logger.log(Level.SEVERE, null, ex1);
            }
            cdf.write( name );
        }
    }
    
    private static void defineCompressedVariable( CDFWriter cdf, String name, CDFDataType type, int[] dims )  throws Exception {
        logger.log(Level.FINE, "call cdf.defineCompressedVariable({0},{1},{2})", 
                new Object[] { logName(name), logName(type), logName(dims) } );
        cdf.defineCompressedVariable( name, type, dims );
    }
    
    private static void defineVariable( CDFWriter cdf, String name, CDFDataType type, int[] dims )  throws Exception {
        logger.log(Level.FINE, "call cdf.defineVariable({0},{1},{2})", 
                new Object[] { logName(name), logName(type), logName(dims) } );
        cdf.defineVariable( name, type, dims );
    }
    
    private static void addData( CDFWriter cdf, String name, Object d ) throws Exception {
        logger.log(Level.FINE, "call cdf.addData({0},{1})", new Object[] { logName(name), logName(d) } );
        cdf.addData( name, d );
    }

    private static void addVariableAttributeEntry( CDFWriter cdf, String varName, String attrName, CDFDataType type, Object o ) 
            throws CDFException.WriterError {
        logger.log( Level.FINE, "call cdf.addVariableAttributeEntry( {0}, {1}, {2}, {3} )",  
                new Object[] { logName(varName), logName(attrName), logName(type), logName( o ) } );
        if ( type==CDFDataType.CHAR && o.toString().length()==0 ) { 
            o= " ";
        }
        cdf.addVariableAttributeEntry( varName, attrName, type, o );
    }
    
    /**
     * copy metadata for the variable.
     * @param units Units object to identify time types.
     * @param name the variable name
     * @param ds the dataset containing metadata.
     * @throws Exception 
     */
    private static void copyMetadata( CDFWriter cdf, Units units, String name, CDFDataType type, boolean isSupport, QDataSet ds ) 
            throws Exception {
        
        if ( units!=null ) {
            if (units == Units.cdfEpoch) {
                addVariableAttributeEntry( cdf, name, "UNITS", CDFDataType.CHAR, "ms" );
            } else if ( units==Units.cdfTT2000 ) {
                addVariableAttributeEntry( cdf, name, "UNITS", CDFDataType.CHAR, "ns" );
            } else {
                addVariableAttributeEntry( cdf, name, "UNITS", CDFDataType.CHAR, units.toString() );
            }
        } else {
            addVariableAttributeEntry( cdf, name, "UNITS", CDFDataType.CHAR, " " );
        }
        
        String label = (String) ds.property(QDataSet.LABEL);
        if (label != null && label.length()>0 ) {
            if ( units!=null && label.endsWith("("+units+")") ) {
                label= label.substring(0,label.length()-units.toString().length()-2);
            }
            addVariableAttributeEntry( cdf, name,"LABLAXIS", CDFDataType.CHAR, label);
        }
        String title = (String) ds.property(QDataSet.TITLE);
        if (title != null && title.length()>0 ) {
            addVariableAttributeEntry( cdf, name,"CATDESC", CDFDataType.CHAR, title);
        }
        
        String description = (String) ds.property(QDataSet.DESCRIPTION);
        if (description != null && description.length()>0 ) {
            addVariableAttributeEntry( cdf, name,"VAR_NOTES", CDFDataType.CHAR, description);
        }
        
        Number vmax= (Number) ds.property( QDataSet.VALID_MAX );
        Number vmin= (Number) ds.property( QDataSet.VALID_MIN );
        if ( vmax!=null || vmin !=null ) {
            if ( units==Units.cdfEpoch ) {
                //UnitsConverter uc= ((Units)ds.property(QDataSet.UNITS)).getConverter(units);
                //if ( vmax==null ) vmax= 1e38; else vmax= uc.convert(vmax);
                //if ( vmin==null ) vmin= -1e38; else vmin= uc.convert(vmin);
                //cdf.addVariableAttributeEntry( name, "VALIDMIN", CDFDataType.DOUBLE, vmin.doubleValue() );
                //cdf.addVariableAttributeEntry( name, "VALIDMAX", CDFDataType.DOUBLE, vmax.doubleValue() );
            } else if ( units==Units.cdfTT2000 ) {
                if ( vmax!=null && vmin !=null ) {
                    cdf.addVariableAttributeEntry( name, "VALIDMIN", CDFDataType.TT2000, new long[] { vmin.longValue() } );
                    cdf.addVariableAttributeEntry( name, "VALIDMAX", CDFDataType.TT2000, new long[] { vmax.longValue() } );
                }
            } else {
                if ( vmax==null ) vmax= 1e38;
                if ( vmin==null ) vmin= -1e38;
                cdf.addVariableAttributeEntry( name, "VALIDMIN", type, new double[] { vmin.doubleValue() } );
                cdf.addVariableAttributeEntry( name, "VALIDMAX", type, new double[] { vmax.doubleValue() } );
            }
        }
        Number fillval= (Number) ds.property( QDataSet.FILL_VALUE );
        if ( fillval!=null ) {
            if ( units==Units.cdfEpoch ) {
                
            } else if ( units==Units.cdfTT2000 ) {
                //TODO: use long access, if available.
                cdf.addVariableAttributeEntry( name, "FILLVAL", CDFDataType.TT2000, new long[] { fillval.longValue() } ); 
            } else {
                cdf.addVariableAttributeEntry( name,"FILLVAL", type, new double[] { fillval.doubleValue() });
            }
        } else {
            //cdf.addVariableAttributeEntry( name,"FILLVAL",CDFDataType.DOUBLE,-1e31);
        }
        Number smax= (Number) ds.property( QDataSet.TYPICAL_MAX );
        Number smin= (Number) ds.property( QDataSet.TYPICAL_MIN );
        if ( smax!=null || smin !=null ) {
            if ( units==Units.cdfEpoch ) {
                //UnitsConverter uc= ((Units)ds.property(QDataSet.UNITS)).getConverter(units);
                //if ( smax==null ) smax= 1e38; else smax= uc.convert(smax);
                //if ( smin==null ) smin= -1e38; else smin= uc.convert(smin);
                //cdf.addVariableAttributeEntry( name,"SCALEMIN", CDFDataType.DOUBLE, smin.doubleValue() );
                //cdf.addVariableAttributeEntry( name,"SCALEMAX", CDFDataType.DOUBLE, smax.doubleValue() );
            } else if ( units==Units.cdfTT2000 ) {
                if ( smax==null ) smax= Units.cdfTT2000.parse("1958-01-01T00:00").doubleValue( Units.cdfTT2000);
                if ( smin==null ) smin= Units.cdfTT2000.parse("2058-01-01T00:00").doubleValue( Units.cdfTT2000);
                cdf.addVariableAttributeEntry( name, "SCALEMIN", CDFDataType.TT2000, new long[] { smin.longValue() } );
                cdf.addVariableAttributeEntry( name, "SCALEMAX", CDFDataType.TT2000, new long[] { smax.longValue() } );
            } else {
                if ( smax==null ) smax= 1e38;
                if ( smin==null ) smin= -1e38;
                cdf.addVariableAttributeEntry( name,"SCALEMIN", type, new double[] { smin.doubleValue() } );
                cdf.addVariableAttributeEntry( name,"SCALEMAX", type, new double[] { smax.doubleValue() } );
            }
        }
        String scaleTyp= (String) ds.property(QDataSet.SCALE_TYPE);
        if ( scaleTyp!=null ) {
            addVariableAttributeEntry( cdf, name,"SCALETYP",CDFDataType.CHAR,scaleTyp);
        }

        String format= (String) ds.property( QDataSet.FORMAT );
        if ( format!=null && format.trim().length()>0 ) {
            addVariableAttributeEntry( cdf, name,"FORMAT",CDFDataType.CHAR,format);
        }

        String displayType= (String)ds.property( QDataSet.RENDER_TYPE );
        if ( displayType==null || displayType.length()==0 ) {
            displayType= DataSourceUtil.guessRenderType(ds);
        }
        switch (displayType) {
            case "nnSpectrogram":
            case "spectrogram":
                displayType= "spectrogram";
                break;
            case "image":
                displayType= "image";
                break;
            case "series":
            case "scatter":
            case "hugeScatter":
                displayType= "time_series";
                break;
            default:
                break;
        }
        addVariableAttributeEntry( cdf, name,"DISPLAY_TYPE", CDFDataType.CHAR, displayType );
        
        addVariableAttributeEntry( cdf, name,"VAR_TYPE", CDFDataType.CHAR, isSupport ? "support_data" : "data" );
        
    }

    @Override
    public boolean canFormat(QDataSet ds) {
        return ! ( ds.rank()==0  || SemanticOps.isJoin(ds) );
    }

    @Override
    public String getDescription() {
        return "NASA Common Data Format";
    }
    
}
