
package org.autoplot.netCDF;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.ArrayDataSet;
import org.das2.qds.DDataSet;
import org.das2.qds.DataSetIterator;
import org.das2.qds.DataSetOps;
import org.das2.qds.DataSetUtil;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.QubeDataSetIterator;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.AbstractDataSourceFormat;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;
import ucar.nc2.write.NetcdfFileFormat;
import ucar.nc2.write.NetcdfFormatWriter;

/**
 * Format HDF5 files using the NetCDF library.  These files do not work with Matlab, and this needs to be explored more.
 * 
 * @author jbf
 */
public class HDF5DataSourceFormat extends AbstractDataSourceFormat {

    Map<QDataSet,String> names= new HashMap();
    private static final Logger logger= LoggerManager.getLogger("apdss.netcdf");
    
    private synchronized String nameFor(QDataSet dep0) {
        String name= names.get(dep0);

        if ( name!=null ) {
            return name;
        }
        
        name = (String) dep0.property(QDataSet.NAME);
        Units units = (Units) dep0.property(QDataSet.UNITS);
        if (name == null) {
            if ( units!=null && UnitsUtil.isTimeLocation(units)) {
                name = "Epoch";
            } else {
                name = "Variable_" + names.size();
            }
        }

        if ( names.containsValue(name) ) {
            name= name+"_"+names.size(); // safety.  We should really never get here for other reasons.
        }
        
        names.put(dep0, name);

        return name;
    }

    private synchronized DataType typeFor( QDataSet dep0, String suggest ) {
        Units units = (Units) dep0.property(QDataSet.UNITS);
        if ( units!=null && UnitsUtil.isTimeLocation(units)) {
            return DataType.DOUBLE;
        } else {
            switch (suggest) {
                case "double":
                    return DataType.DOUBLE;
                case "float":
                    return DataType.FLOAT;
                case "long":
                    return DataType.LONG;
                case "int":
                    return DataType.INT;
                case "short":
                    return DataType.SHORT;
                default:
                    return DataType.DOUBLE;
            }
        }

    }

    private static Object getProperty( QDataSet src, String name, Object deft ) {
        Object o= src.property(name);
        if ( o==null ) return deft; else return o;
    }

    private void copy( NetcdfFile in, NetcdfFormatWriter.Builder out ) {
        
        for ( Dimension d : in.getDimensions() ) {
            logger.log(Level.FINER, "out.addDimension({0})", d.getName());
            out.getRootGroup().addDimension( d );
        }
        
        for ( Variable v : in.getVariables() ) {
            logger.log(Level.FINER, "out.addVariable({0})", v.getShortName());
            out.addVariable( v.getShortName(), v.getDataType(), v.getDimensions() );
            names.put( DDataSet.create( new int[0] ), v.getShortName() );
        }
        
    }
    
    private Dimension getDimension( NetcdfFile ncfile, String name ) {
                
        try {
            logger.log(Level.FINER, "ncfile.getDimensions() (looking for {0})", name);
            for ( Dimension d: ncfile.getDimensions() ) {
                if ( d.getName().equals(name) ) {
                    return d;
                }
            }
        } catch ( NullPointerException ex ) {
            
        }
        return null;
    }
            
            
    @Override
    public void formatData(String uri, QDataSet data, ProgressMonitor mon) throws Exception {

        setUri(uri);
        maybeMkdirs();

        String typeSuggest= getParam( "type", "double" );
        
        File file= new File( getResourceURI().toURL().getFile() );        
        NetcdfFormatWriter.Builder ncfile;
        NetcdfFile oldfile;
        
        List<Dimension> dims= new ArrayList();  //TODO: rank2 DEPEND_1.
        Map<String,Dimension> dim= new HashMap<>();
        Map<String,Dimension[]> dimss= new HashMap<>();
        
        String name1= getParam( "arg_0", getParam( "name", null ) );
        
        if ( name1!=null ) {
            names.put(data,name1);
        }
        
        String doDep= getParam("doDep", "");
        if ( doDep.length()>0 && doDep.toUpperCase().charAt(0)=='F' ) {
            MutablePropertyDataSet mpds= DataSetOps.makePropertiesMutable(data);
            mpds.putProperty( QDataSet.DEPEND_0, null );
            mpds.putProperty( QDataSet.DEPEND_1, null );
            mpds.putProperty( QDataSet.DEPEND_2, null );
            mpds.putProperty( QDataSet.DEPEND_3, null );
            mpds.putProperty( QDataSet.BUNDLE_1, null );
            data= mpds;
        }
        
        // append is here as a placeholder and is not implemented!
        boolean append= "T".equals( getParam("append","F") ) ;
        
        String tempFileName= file.toString() + ".temp";
        
        if ( ! append ) {
            if ( file.exists() && !file.delete() ) {
                throw new IllegalArgumentException("Unable to delete file"+file);
            }
            logger.log(Level.FINE, "create HDF5 file {0}", file);
            logger.log(Level.FINER, "NetcdfFileWriteable.createNew( {0}, true )", tempFileName);
            ncfile= NetcdfFormatWriter.createNewNetcdf4(NetcdfFileFormat.NETCDF4_CLASSIC, tempFileName,null);
            oldfile= null;
            
        } else {
            //throw new IllegalArgumentException("append is not supported"); // this is more complex than expected.
            //ncfile= NetcdfFileWriteable.openExisting( file.toString(), true );
            logger.log(Level.FINER, "oldfile= NetcdfFile.open( {0} );", file.toString());
            
            oldfile= NetcdfFile.open( file.toString() );
            //oldfile= NetcdfFileWriteable.openExisting( file.toString(),true );
            
            logger.log(Level.FINER, "ncfile=NetcdfFileWriteable.createNew( {0}, true )", tempFileName);
            ncfile= NetcdfFormatWriter.createNewNetcdf4(NetcdfFileFormat.NETCDF4_CLASSIC, tempFileName,null);
            
            copy( oldfile, ncfile );
            
            for ( Dimension d: oldfile.getDimensions() ) {
                dim.put( d.getName(), d );
            }
            dims.addAll( oldfile.getDimensions() );
            
        }

        int[] qube= DataSetUtil.qubeDims(data);
        if ( qube==null ) {
            throw new IllegalArgumentException("data is not a qube");
        }

        for ( int i=0; i<data.rank(); i++ ) {
            String namei= "dim"+i;
            QDataSet depi= (QDataSet) data.property("DEPEND_"+i);
            if ( depi!=null ) {
                namei= nameFor(depi); // allocate the name
            } 
            
            if ( !append ) {
                Dimension d= dim.get(namei);
                if ( d==null ) {
                    logger.log(Level.FINER, "ncfile.addDimension({0},{1})", new Object[]{namei, DataSetUtil.toString(qube)});
                    d= ncfile.addDimension( namei, qube[i] );
                }
                dim.put( d.getName(), d );
                dims.add( d );
                if ( depi!=null ) {
                    if ( depi.rank()==2 ) {
                        dimss.put( namei, new Dimension[] { dims.get(0), d } );
                    } else {
                        dimss.put( namei, new Dimension[] { d } );
                    }
                }
            } else {
                //TODO: append under-implemented
            }
        }
        
        for ( int i=0; i<data.rank(); i++ ) {
            String namei;
            QDataSet depi= (QDataSet) data.property("DEPEND_"+i);
            if ( depi!=null && depi!=data ) {
                namei= nameFor(depi);
                Units u= SemanticOps.getUnits(depi);
                String typeSuggest1= UnitsUtil.isTimeLocation(u) ? "double" : typeSuggest;
            
                defineVariableOne( ncfile, depi, typeSuggest1, dimss.get(namei) );
            }
        }
        
        defineVariableOne( ncfile, data, typeSuggest, dims.toArray(new Dimension[dims.size()]) );      
        
        // unfortunately it looks like I can't have both files open at once.  
        Map<String,Array> dataStore= new LinkedHashMap<>();
         
        NetcdfFormatWriter writer;
        
        if ( append ) {
            assert oldfile!=null;
            logger.log(Level.FINER,"oldFile.getVariables()");
            for ( Variable v : oldfile.getVariables() ) {
                logger.log(Level.FINER, "v.read()" );
                Array a= v.read();
                logger.log(Level.FINE, "a={0}", a);
                dataStore.put( v.getFullName(), a );
            }
            
            oldfile.close();
            
            for ( Entry<String,Array> var: dataStore.entrySet() ) {
                Array array= var.getValue();
                ncfile.addVariable( var.getKey(), array.getDataType(), dims );
                //ncfile.write( var.getKey(), array );
            }
            writer= ncfile.build();
            
        } else {
            writer= ncfile.build();
        }
        
                
        for ( int i=0; i<data.rank(); i++ ) {
            
            QDataSet depi= (QDataSet) data.property("DEPEND_"+i);
            if ( depi!=null ) {
                nameFor(depi); // allocate the name
                Units u= SemanticOps.getUnits(depi);
                String typeSuggest1= UnitsUtil.isTimeLocation(u) ? "double" : typeSuggest;
                
                formatDataOne(writer, depi, typeSuggest1 );
            }
            
        }
        
        formatDataOne( writer, data, typeSuggest );
                        
        logger.log(Level.FINER, "ncfile.flush()" );
        writer.flush();
        
        logger.log(Level.FINER, "ncfile.close()" );
        writer.close();
        
        if ( ! new File( tempFileName ).renameTo( file ) ) {
            throw new IOException("unable to rename file "+tempFileName );
        }
        
    }

    private void defineVariableOne( NetcdfFormatWriter.Builder ncfile, QDataSet data, String typeSuggest, Dimension[] dims ) {
        String varName= nameFor(data);
        
        DataType t= typeFor(data,typeSuggest);
        logger.log(Level.FINER, "ncfile.addVariable({0},{1},<dims>)", new Object[]{varName, t});
        Variable.Builder vbuilder= ncfile.addVariable( varName, t, Arrays.asList(dims) );

        double fill;
        Number nfill= (Number)data.property(QDataSet.FILL_VALUE);
        if ( nfill==null ) {
            fill= -1e38;
        } else {
            fill= nfill.doubleValue();
        }
        
        String meta= getParam( "metadata", "" );
        if ( meta.equals("istp") ) {
            logger.finer("adding ISTP metadata");
            vbuilder.addAttribute( new Attribute("FIELDNAM", varName ) );
            vbuilder.addAttribute( new Attribute("UNITS", SemanticOps.getUnits(data).toString() ) );
            vbuilder.addAttribute( new Attribute("VAR_TYPE", "data" ) );
            vbuilder.addAttribute( new Attribute("FILLVAL",  fill ) );
            vbuilder.addAttribute( new Attribute("VALIDMIN", (Double) getProperty( data, QDataSet.VALID_MIN, -1e38 ) ) );
            vbuilder.addAttribute( new Attribute("VALIDMAX", (Double) getProperty( data, QDataSet.VALID_MAX, 1e38 ) ) );
            if ( data.property(QDataSet.TYPICAL_MIN)!=null ) {
                vbuilder.addAttribute( new Attribute("SCALEMIN", (Double) getProperty( data, QDataSet.TYPICAL_MIN, -1e38 ) ) ); // -1e38 will not be used
            }
            if ( data.property(QDataSet.TYPICAL_MAX)!=null ) {
                vbuilder.addAttribute( new Attribute("SCALEMAX", (Double) getProperty( data, QDataSet.TYPICAL_MAX, 1e38 ) ) ); // -1e38 will not be used
            }
            if ( data.property(QDataSet.SCALE_TYPE)!=null ) {
                vbuilder.addAttribute( new Attribute("SCALETYP", (String) getProperty( data, QDataSet.SCALE_TYPE, "linear" ) ) );
            }
            if ( data.property(QDataSet.TITLE)!=null ) {
                vbuilder.addAttribute( new Attribute("CATDESC", (String) getProperty( data, QDataSet.TITLE, "" ) ) );
            }
            if ( data.property(QDataSet.LABEL)!=null ) {
                vbuilder.addAttribute( new Attribute("LABLAXIS", (String) getProperty( data, QDataSet.LABEL, "" ) ) );
            }
        } else {
            vbuilder.addAttribute( new Attribute("_FillValue", fill ) );
            if ( UnitsUtil.isTimeLocation( SemanticOps.getUnits(data) ) ) {
                //data= Ops.putProperty( Ops.convertUnitsTo( data, Units.cdfTT2000 ), QDataSet.UNITS, null ); // data should really be converted to account for leap seconds.
                //data= Ops.divide( data, 1e9 );
                Units u= SemanticOps.getUnits(data);
                String unitsStr= u.getOffsetUnits().toString() + " " + u.getBasis().getDescription();
                vbuilder.addAttribute( new Attribute("units",unitsStr));
            }
        }
        
    }
    
    private void formatDataOne( NetcdfFormatWriter ncfile, QDataSet data, String typeSuggest) throws IllegalArgumentException, InvalidRangeException, IOException {

        String varName= nameFor(data);
        
        int[] qube= DataSetUtil.qubeDims(data);
        if ( qube==null ) {
            throw new IllegalArgumentException("data is not a qube");
        }

        ArrayDataSet ads= ArrayDataSet.copy(data);

        DataType dataType= typeFor(data,typeSuggest);
        logger.log(Level.FINER, "ddata= Array.factory( {0}, qube );", dataType );
        Array ddata= Array.factory( dataType, qube );
        DataSetIterator it= new QubeDataSetIterator(ads);
        int i=0;
        while ( it.hasNext() ) {
            it.next();
            ddata.setDouble( i, it.getValue(ads) );
            i++;
        }
        logger.log(Level.FINER, "ncfile.write({0},ddata)", varName);
        ncfile.write( varName, ddata );
    }

//    public static void main( String[] args ) throws Exception {
//        QDataSet out= Ops.rand(100);
//        new HDF5DataSourceFormat().formatData("file:///home/jbf/foo.nc", out, new NullProgressMonitor() );
//    }

    @Override
    public boolean canFormat(QDataSet ds) {
        int[] qube= DataSetUtil.qubeDims(ds);
        return qube!=null;
    }

    @Override
    public String getDescription() {
        return "HDF5";
    }
}
