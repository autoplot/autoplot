/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.cdf;

import gov.nasa.gsfc.spdf.cdfj.CDFDataType;
import gov.nasa.gsfc.spdf.cdfj.CDFWriter;
import java.lang.reflect.Array;
import org.virbo.datasource.DataSourceUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsConverter;
import org.das2.datum.UnitsUtil;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.QubeDataSetIterator;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.DataSourceFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.LoggerManager;
import org.virbo.dataset.SemanticOps;

/**
 * Format the QDataSet into CDF tables, using Nand Lal's library.
 *
 * @author jbf
 */
public class CdfDataSourceFormat implements DataSourceFormat {

    CDFWriter cdf;
    //Object depend_0, depend_1, depend_2;
    //Object unitsAttr, lablAxisAttr, catdescAttr, validmaxAttr, validminAttr, fillvalAttr, scalemaxAttr, scaleminAttr;
    //Object formatAttr, displayTypeAttr;

    Map<QDataSet,String> names;
    Map<String,QDataSet> seman;

    private static final Logger logger= LoggerManager.getLogger("apdss.cdfj");
    
    public CdfDataSourceFormat() {
        names= new HashMap<QDataSet,String>();
        seman= new HashMap<String,QDataSet>();
    }

    private synchronized String nameFor(QDataSet dep0) {
        String name= names.get(dep0);
        
        if ( name==null ) name = (String) dep0.property(QDataSet.NAME);
        
        Units units = (Units) dep0.property(QDataSet.UNITS);
        if (name == null) {
            if ( units!=null && UnitsUtil.isTimeLocation(units)) {
                name = "Epoch";
            } else {
                name = "Variable_" + names.size();
            }
        }
        
        names.put(dep0, name);
        seman.put(name, dep0);
        
        return name;
    }

    public void formatData( String uri, QDataSet data, ProgressMonitor mon) throws Exception {

        URISplit split= URISplit.parse( uri );
        java.util.Map<String, String> params= URISplit.parseParams( split.params );

        File file= new File( split.resourceUri );
        boolean append= "T".equals( params.get("append") ) ;
        
        if ( ! append ) {
            if ( file.exists() && !file.delete() ) {
                throw new IllegalArgumentException("Unable to delete file"+file);
            }
            logger.log(Level.FINE, "create CDF file {0}", file);
            cdf = new CDFWriter( false );

        } else {
            cdf = new CDFWriter( file.toURI().toURL() );
            
        }
        
        String name1= params.get( "arg_0" );

        if ( name1!=null ) {
            names.put(data,name1);
            seman.put(name1,data);
        }
        
        nameFor(data); // allocate a good name

        QDataSet dep0 = (QDataSet) data.property(QDataSet.DEPEND_0);

        if ( dep0 != null ) {
            if ( !append ) {
                String name= nameFor(dep0);
                Map<String,String> params1= new HashMap<String,String>();
                params1.put( "timeType",params.get("timeType") );
                addVariableRankN( dep0, name, params1, mon );
            }
        }
        
        QDataSet dep1 = (QDataSet) data.property(QDataSet.DEPEND_1);

        if (dep1 != null) {
            if ( !append ) {
                String name= nameFor(dep1);
                addVariableRank1NoVary(dep1, name, new HashMap<String,String>(), new NullProgressMonitor() );
            }
        }

        QDataSet dep2 = (QDataSet) data.property(QDataSet.DEPEND_2);

        if (dep2 != null) {
            if ( !append ) {
                String name= nameFor(dep2);
                addVariableRank1NoVary(dep2, name, new HashMap<String,String>(), new NullProgressMonitor() );
            }
        }

        addVariableRankN(data, nameFor(data), params, mon );
        
        try {
            if ( dep0!=null ) cdf.addVariableAttributeEntry( nameFor(data), "DEPEND_0", CDFDataType.CHAR, nameFor(dep0) );
            if ( dep1!=null ) cdf.addVariableAttributeEntry( nameFor(data), "DEPEND_1", CDFDataType.CHAR, nameFor(dep1) );
            if ( dep2!=null ) cdf.addVariableAttributeEntry( nameFor(data), "DEPEND_2", CDFDataType.CHAR, nameFor(dep2) );
        } catch ( Exception ex ) {
            logger.log( Level.WARNING, ex.getMessage() , ex );
        }
            
        cdf.write( file.toString() );
        
    }

    private void addVariableRank1NoVary( QDataSet ds, String name, Map<String,String> params, org.das2.util.monitor.ProgressMonitor mon ) throws Exception {
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
            
            Object array= dataSetToArray( ds, uc, type, mon );
            cdf.addNRVVariable( name, type, new int[] { ds.length() }, array );

        } else {
            throw new IllegalArgumentException("not supported!");
            
        }
        copyMetadata(units, name, ds);
        
    }

    /**
     * convert the rank 1 dataset to a buffer.
     * @param ds rank 1 dataset
     * @param uc units converter to convert the type.
     * @param type type code, such as CDF_DOUBLE indicating how the data should be converted.
     * @return buffer of this type.
     */
    private ByteBuffer doIt1Nio( QDataSet ds, UnitsConverter uc, CDFDataType type ) {
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

        } else {
            throw new IllegalArgumentException("not supported: "+type);
        }
        export.flip();
        return export;

    }
    
    /**
     * cdf library needs array in double or triple arrays.  
     * 
     * @param ds the dataset.
     * @param uc UnitsConverter in case we need to handle times.
     * @param type the data type.
     * @return a ByteBuffer containing the data.
     */
    private ByteBuffer dataSetToNioArray( QDataSet ds, UnitsConverter uc, CDFDataType type, ProgressMonitor mon ){
        if ( ds.rank()==1 ) {
            return doIt1Nio( ds, uc, type );
            
        } else if ( ds.rank()==2 ) {

            throw new UnsupportedOperationException("not implemented");

        } else if ( ds.rank()==3 ) {

            throw new UnsupportedOperationException("not implemented");
            
        } else if ( ds.rank()==4 ) {

            throw new UnsupportedOperationException("not implemented");
            
        } else {
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
    private Object doIt1( QDataSet ds, UnitsConverter uc, CDFDataType type ) {
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

        } else {
            throw new IllegalArgumentException("not supported: "+type);
        }
        return export;

    }
    
    /**
     * cdf library needs array in double or triple arrays.  
     * 
     * @param ds the dataset.
     * @param uc UnitsConverter in case we need to handle times.
     * @param type the data type.
     * @return a 1,2,3,4-d array of double,long,float,int,short,byte.
     */
    private Object dataSetToArray( QDataSet ds, UnitsConverter uc, CDFDataType type, ProgressMonitor mon ){
        Object oexport;

        if ( ds.rank()==1 ) {
            return doIt1( ds, uc, type );
            
        } else if ( ds.rank()==2 ) {

            if ( type==CDFDataType.DOUBLE ) {
                oexport= new double[ds.length()][];
            } else if ( type==CDFDataType.TT2000 ) {
                oexport= new long[ds.length()][];
            } else if ( type==CDFDataType.FLOAT ) {
                oexport= new float[ds.length()][];
            } else if ( type==CDFDataType.INT4 ) {
                oexport= new int[ds.length()][];
            } else if ( type==CDFDataType.INT2 ) {
                oexport= new short[ds.length()][];
            } else if ( type==CDFDataType.INT1 ) {
                oexport= new byte[ds.length()][];
            } else {
                throw new IllegalArgumentException("type not supported: "+type);
            }
            for ( int i=0; i<ds.length(); i++ ) {
                Array.set( oexport, i, dataSetToArray( ds.slice(i), uc, type, mon ) );
            }
            return oexport;
        } else if ( ds.rank()==3 ) {

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
            for ( int i=0; i<ds.length(); i++ ) {
                Array.set( oexport, i, dataSetToArray( ds.slice(i), uc, type, mon ) );
            }
            return oexport;
        } else if ( ds.rank()==4 ) {

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
            for ( int i=0; i<ds.length(); i++ ) {
                Array.set( oexport, i, dataSetToArray( ds.slice(i), uc, type, mon ) );
            }
            return oexport;
        } else {
            throw new IllegalArgumentException("rank 0 not supported");
        }
        
    }

    private void addVariableRankN(QDataSet ds, String name, Map<String,String> params, org.das2.util.monitor.ProgressMonitor mon) throws Exception {
        Units units = (Units) ds.property(QDataSet.UNITS);
        CDFDataType type = CDFDataType.DOUBLE;

        String t= params.get("type");
        if ( t!=null ) {
            if ( t.equals("float") ) {
                type= CDFDataType.FLOAT;
            } else if ( t.equals("byte")) {
                type= CDFDataType.INT1;
            } else if ( t.equals("int2")) {
                type= CDFDataType.INT2;
            } else if ( t.equals("int4")) {
                type= CDFDataType.INT4;
            } else if ( t.equals("double")) {
                type= CDFDataType.DOUBLE;
            }
        } else {
            if ( ds.rank()<3 ) {
                type= CDFDataType.DOUBLE;
            } else {
                type= CDFDataType.FLOAT;
            }
        }

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
        
            
        if ( ds.rank()==1 ) {
            cdf.defineVariable( name, type, new int[0] );
            //cdf.createVariable( name, type, new int[0] );
            cdf.addData( name, dataSetToNioArray( ds, uc, type, mon ) );

        } else if ( ds.rank()==2 ) {
            cdf.defineVariable( name, type, new int[] { ds.length(0) } );
            cdf.addData( name, dataSetToArray( ds, uc, type, mon ) );

        } else if ( ds.rank()==3 ) {
            cdf.defineVariable( name, type, new int[] { ds.length(0),ds.length(0,0) } );
            cdf.addData( name, dataSetToArray( ds, uc, type, mon ) );

        } else if ( ds.rank()==4 ) {
            cdf.defineVariable( name, type, new int[] { ds.length(0),ds.length(0,0),ds.length(0,0,0) } );
            cdf.addData( name, dataSetToArray( ds, uc, type, mon ) );

        }

        copyMetadata( units, name, ds );
        
    }

    /**
     * copy metadata for the variable.
     * @param units Units object to identify time types.
     * @param name the variable name
     * @param ds the dataset containing metadata.
     * @throws Exception 
     */
    private void copyMetadata( Units units, String name, QDataSet ds ) throws Exception {
        
        if ( units!=null ) {
            if (units != Units.cdfEpoch) {
                cdf.addVariableAttributeEntry( name, "UNITS", CDFDataType.CHAR, units.toString() );
            } else {
                cdf.addVariableAttributeEntry( name, "UNITS", CDFDataType.CHAR, "ms" );
            }
        }
        String label = (String) ds.property(QDataSet.LABEL);
        if (label != null && label.length()>0 ) {
            if ( units!=null && label.endsWith("("+units+")") ) {
                label= label.substring(0,label.length()-units.toString().length()-2);
            }
            cdf.addVariableAttributeEntry( name,"LABLAXIS", CDFDataType.CHAR, label);
        }
        String title = (String) ds.property(QDataSet.TITLE);
        if (title != null && title.length()>0 ) {
            cdf.addVariableAttributeEntry( name,"CATDESC", CDFDataType.CHAR, title);
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
            } else {
                //if ( vmax==null ) vmax= 1e38;
                //if ( vmin==null ) vmin= -1e38;
                //cdf.addVariableAttributeEntry( name, "VALIDMIN", CDFDataType.DOUBLE, new Double[] { vmin.doubleValue() } );
                //cdf.addVariableAttributeEntry( name, "VALIDMAX", CDFDataType.DOUBLE, new Double[] { vmax.doubleValue() } );
            }
        }
        Number fillval= (Number) ds.property( QDataSet.FILL_VALUE );
        if ( fillval!=null ) {
            //cdf.addVariableAttributeEntry( name,"FILLVAL",CDFDataType.DOUBLE,fillval.doubleValue());
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
            } else {
                //if ( smax==null ) smax= 1e38;
                //if ( smin==null ) smin= -1e38;
                //cdf.addVariableAttributeEntry( name,"SCALEMIN", CDFDataType.DOUBLE, smin.doubleValue() );
                //cdf.addVariableAttributeEntry( name,"SCALEMAX", CDFDataType.DOUBLE, smax.doubleValue() );
            }
        }
        String scaleTyp= (String) ds.property(QDataSet.SCALE_TYPE);
        if ( scaleTyp!=null ) {
            cdf.addVariableAttributeEntry( name,"SCALETYP",CDFDataType.CHAR,scaleTyp);
        }

        String format= (String) ds.property( QDataSet.FORMAT );
        if ( format!=null ) {
            cdf.addVariableAttributeEntry( name,"FORMAT",CDFDataType.CHAR,format);
        }

        String displayType= (String)ds.property( QDataSet.RENDER_TYPE );
        if ( displayType==null || displayType.length()==0 ) {
            displayType= DataSourceUtil.guessRenderType(ds);
        }
        if ( displayType.equals("nnSpectrogram") || displayType.equals("spectrogram") ) {
            displayType= "spectrogram";
        } else if ( displayType.equals("image")) {
            displayType= "image";
        } else if ( displayType.equals("series") || displayType.equals("scatter") || displayType.equals("hugeScatter") ) {
            displayType= "time_series";
        }
        cdf.addVariableAttributeEntry( name,"DISPLAY_TYPE", CDFDataType.CHAR, displayType );

    }

    public boolean canFormat(QDataSet ds) {
        return ! ( ds.rank()==0  || SemanticOps.isJoin(ds) );
    }

    public String getDescription() {
        return "NASA Common Data Format";
    }
    
}
