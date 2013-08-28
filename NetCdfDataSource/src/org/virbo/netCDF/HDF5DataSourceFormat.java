/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.netCDF;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.ArrayDataSet;
import org.virbo.dataset.DataSetIterator;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.QubeDataSetIterator;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.AbstractDataSourceFormat;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFileWriteable;
import ucar.nc2.Variable;

/**
 *
 * @author jbf
 */
public class HDF5DataSourceFormat extends AbstractDataSourceFormat {

    Map<QDataSet,String> names= new HashMap();

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

    public void formatData(String uri, QDataSet data, ProgressMonitor mon) throws Exception {

        setUri(uri);

        String typeSuggest= getParam( "type", "double" );

        NetcdfFileWriteable ncfile= NetcdfFileWriteable.createNew( getResourceURI().toURL().getFile(), true );

        String varName= nameFor(data);

        int[] qube= DataSetUtil.qubeDims(data);
        if ( qube==null ) {
            throw new IllegalArgumentException("data is not a qube");
        }

        List<Dimension> dims= new ArrayList();
        for ( int i=0; i<data.rank(); i++ ) {
            String namei= "dim"+i;
            QDataSet depi= (QDataSet) data.property("DEPEND_"+i);
            if ( depi!=null ) {
                namei= nameFor(depi);
            }
            Dimension d= ncfile.addDimension( namei, qube[i] );
            dims.add( d );
            
        }

        Variable var= ncfile.addVariable( varName, typeFor(data,typeSuggest), dims );

        String meta= getParam( "metadata", "" );
        if ( meta.equals("istp") ) {
            var.addAttribute( new Attribute("UNITS", SemanticOps.getUnits(data).toString() ) );
            var.addAttribute( new Attribute("VAR_TYPE", "data" ) );
            var.addAttribute( new Attribute("VALIDMIN", (Double) getProperty( data, QDataSet.VALID_MIN, -1e38 ) ) );
            var.addAttribute( new Attribute("VALIDMAX", (Double) getProperty( data, QDataSet.VALID_MAX, 1e38 ) ) );
            var.addAttribute( new Attribute("FILLVAL",  (Double) getProperty( data, QDataSet.FILL_VALUE, -1e31 ) ) );
        }

        ncfile.create();

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

        ncfile.finish();

        ncfile.close();
        
    }

//    public static void main( String[] args ) throws Exception {
//        QDataSet out= Ops.rand(100);
//        new HDF5DataSourceFormat().formatData("file:///home/jbf/foo.nc", out, new NullProgressMonitor() );
//    }

    public boolean canFormat(QDataSet ds) {
        return true;
    }

    public String getDescription() {
        return "HDF5";
    }
}
