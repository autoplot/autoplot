/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.netCDF;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.ArrayDataSet;
import org.virbo.dataset.DataSetIterator;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.QubeDataSetIterator;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.AbstractDataSourceFormat;
import org.virbo.dsops.Ops;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFileWriteable;
import ucar.nc2.Variable;

/**
 * Format HDF5 files using the NetCDF library.  These files do not work with Matlab.
 * 
 * @author jbf
 */
public class HDF5DataSourceFormat extends AbstractDataSourceFormat {

    Map<QDataSet,String> names= new HashMap();
    private static final Logger logger= LoggerManager.getLogger("apdss.cdfj");
    
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
            if ( suggest.equals("double") ) {
                return DataType.DOUBLE;
            } else if ( suggest.equals("float") ) {
                return DataType.FLOAT;
            } else {
                return DataType.DOUBLE;
            }
        }

    }

    private static Object getProperty( QDataSet src, String name, Object deft ) {
        Object o= src.property(name);
        if ( o==null ) return deft; else return o;
    }

    @Override
    public void formatData(String uri, QDataSet data, ProgressMonitor mon) throws Exception {

        setUri(uri);

        String typeSuggest= getParam( "type", "double" );
        
        File file= new File( getResourceURI().toURL().getFile() );
        NetcdfFileWriteable ncfile;
        
        // append is here as a placeholder and is not implemented!
        boolean append= "T".equals( getParam("append","F") ) ;
        
        if ( ! append ) {
            if ( file.exists() && !file.delete() ) {
                throw new IllegalArgumentException("Unable to delete file"+file);
            }
            logger.log(Level.FINE, "create HDF5 file {0}", file);
            ncfile= NetcdfFileWriteable.createNew( file.toString(), true );

        } else {
            throw new IllegalArgumentException("append is not supported"); // this is more complex than expected.
            //ncfile= NetcdfFileWriteable.openExisting( file.toString(), true );
            
        }

        int[] qube= DataSetUtil.qubeDims(data);
        if ( qube==null ) {
            throw new IllegalArgumentException("data is not a qube");
        }

        List<Dimension> dims= new ArrayList();  //TODO: rank2 DEPEND_1.
        Map<String,Dimension[]> dimss= new HashMap<String, Dimension[]>();
        for ( int i=0; i<data.rank(); i++ ) {
            String namei= "dim"+i;
            QDataSet depi= (QDataSet) data.property("DEPEND_"+i);
            if ( depi!=null ) {
                namei= nameFor(depi); // allocate the name
            } 
            Dimension d= ncfile.addDimension( namei, qube[i] );
            dims.add( d );
            if ( depi!=null ) {
                if ( depi.rank()==2 ) {
                    dimss.put( namei, new Dimension[] { dims.get(0), d } );
                } else {
                    dimss.put( namei, new Dimension[] { d } );
                }
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
        
        
        if ( append ) {
            // I wonder how this would be done
        } else {
            ncfile.create();
        }        
        
        for ( int i=0; i<data.rank(); i++ ) {
            
            QDataSet depi= (QDataSet) data.property("DEPEND_"+i);
            if ( depi!=null ) {
                nameFor(depi); // allocate the name
            }
            Units u= SemanticOps.getUnits(depi);
            String typeSuggest1= UnitsUtil.isTimeLocation(u) ? "double" : typeSuggest;
            
            formatDataOne(ncfile, depi, typeSuggest1 );
            
        }
        
        formatDataOne(ncfile, data, typeSuggest );

        ncfile.finish();

        ncfile.close();
        
    }

    private void defineVariableOne( NetcdfFileWriteable ncfile, QDataSet data, String typeSuggest, Dimension[] dims ) {
        String varName= nameFor(data);
        
        Variable var= ncfile.addVariable( varName, typeFor(data,typeSuggest), dims );

        double fill;
        Number nfill= (Number)data.property(QDataSet.FILL_VALUE);
        if ( nfill==null ) {
            fill= -1e38;
        } else {
            fill= nfill.doubleValue();
        }
        
        String meta= getParam( "metadata", "" );
        if ( meta.equals("istp") ) {
            var.addAttribute( new Attribute("FIELDNAM", varName ) );
            var.addAttribute( new Attribute("UNITS", SemanticOps.getUnits(data).toString() ) );
            var.addAttribute( new Attribute("VAR_TYPE", "data" ) );
            var.addAttribute( new Attribute("FILLVAL",  fill ) );
            var.addAttribute( new Attribute("VALIDMIN", (Double) getProperty( data, QDataSet.VALID_MIN, -1e38 ) ) );
            var.addAttribute( new Attribute("VALIDMAX", (Double) getProperty( data, QDataSet.VALID_MAX, 1e38 ) ) );
            if ( data.property(QDataSet.TYPICAL_MIN)!=null ) {
                var.addAttribute( new Attribute("SCALEMIN", (Double) getProperty( data, QDataSet.TYPICAL_MIN, -1e38 ) ) ); // -1e38 will not be used
            }
            if ( data.property(QDataSet.TYPICAL_MAX)!=null ) {
                var.addAttribute( new Attribute("SCALEMAX", (Double) getProperty( data, QDataSet.TYPICAL_MAX, 1e38 ) ) ); // -1e38 will not be used
            }
            if ( data.property(QDataSet.SCALE_TYPE)!=null ) {
                var.addAttribute( new Attribute("SCALETYP", (String) getProperty( data, QDataSet.SCALE_TYPE, "linear" ) ) );
            }
            if ( data.property(QDataSet.TITLE)!=null ) {
                var.addAttribute( new Attribute("CATDESC", (String) getProperty( data, QDataSet.TITLE, "" ) ) );
            }
            if ( data.property(QDataSet.LABEL)!=null ) {
                var.addAttribute( new Attribute("LABLAXIS", (String) getProperty( data, QDataSet.LABEL, "" ) ) );
            }
        } else {
            var.addAttribute( new Attribute("_FillValue", fill ) );
            if ( UnitsUtil.isTimeLocation( SemanticOps.getUnits(data) ) ) {
                //data= Ops.putProperty( Ops.convertUnitsTo( data, Units.cdfTT2000 ), QDataSet.UNITS, null ); // data should really be converted to account for leap seconds.
                //data= Ops.divide( data, 1e9 );
                Units u= SemanticOps.getUnits(data);
                String unitsStr= u.getOffsetUnits().toString() + " " + u.getBasis().getDescription();
                var.addAttribute( new Attribute("units",unitsStr));
            }
        }
        
    }
    
    private void formatDataOne( NetcdfFileWriteable ncfile, QDataSet data, String typeSuggest) throws IllegalArgumentException, InvalidRangeException, IOException {

        String varName= nameFor(data);
        
        int[] qube= DataSetUtil.qubeDims(data);
        if ( qube==null ) {
            throw new IllegalArgumentException("data is not a qube");
        }

        ArrayDataSet ads= ArrayDataSet.copy(data);

        Array ddata= Array.factory( typeFor(data,typeSuggest), qube );
        DataSetIterator it= new QubeDataSetIterator(ads);
        int i=0;
        while ( it.hasNext() ) {
            it.next();
            ddata.setDouble( i, it.getValue(ads) );
            i++;
        }
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
