/*
 * NetCDFDataSource.java
 *
 * Created on April 4, 2007, 7:03 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.netCDF;

import org.das2.util.monitor.ProgressMonitor;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.MetadataModel;
import org.virbo.dsutil.TransposeRankNDataSet;
import org.virbo.metatree.IstpMetadataModel;
import ucar.nc2.Attribute;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.ncml.NcMLReader;

/**
 * Read Data from NetCDF and HDF5 files.
 * @author jbf
 */
public class NetCDFDataSource extends AbstractDataSource {
    
    Variable variable;
    String sMyUrl;
    String svariable;
    NetcdfDataset ncfile;

/*    static {
        try {
            NetcdfFile.registerIOProvider("org.virbo.netCDF.APIOServiceProvider");
        } catch (IllegalAccessException ex) {
            Logger.getLogger(NetCDFDataSource.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            Logger.getLogger(NetCDFDataSource.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(NetCDFDataSource.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
*/
    /** Creates a new instance of NetCDFDataSource */
    public NetCDFDataSource( URI uri ) throws IOException {
        super(uri);
        parseUrl();
        
    }
    
    private void parseUrl() {
        String surl= DataSetURI.fromUri(uri);
        int i= surl.lastIndexOf('?');
        if ( i>-1 ) {
            sMyUrl= surl.substring(0, i);
        } else {
            sMyUrl= surl;
        }
        
        if ( i==-1 ) {
            svariable= null;
            return;
            
        } else {
            // get the variable            
            Map p= getParams();
            if ( p.containsKey( "id" ) ) {
                svariable= (String) p.get( "id" );
            } else {
                svariable= (String) p.get("arg_0"); // legacy support
                svariable= svariable.replaceAll(" ","+");
            }
        }
    }
    
    public QDataSet getDataSet( ProgressMonitor mon) throws IOException {
        mon.started();
        readData( mon );
        NetCdfVarDataSet result= NetCdfVarDataSet.create( variable , ncfile, mon );
        QDataSet qresult= checkLatLon(result);
        mon.finished();
        return qresult;
        
    }
    
    
    /**
     * check for lat and lon tags, transpose if lat come before lon.
     * @param v
     */
    private QDataSet checkLatLon( NetCdfVarDataSet v ) {
        int lat=-1;
        int lon=-1;
        for ( int i=0; i<v.rank();i++ ) {
            QDataSet dep= (QDataSet) v.property( "DEPEND_"+i );
            if ( dep!=null ) {
                String name= (String) dep.property("NAME");
                if ( "lon".equals(name) ) lon=i;
                if ( "lat".equals(name) ) lat=i;
            }
        }
        if ( lat>-1 && lon>-1 && lat<lon ) {
            int[] order= new int[v.rank()];
            for ( int i=0;i<v.rank(); i++) order[i]= i;
            int t= order[lat];
            order[lat]= order[lon];
            order[lon]= t;
            QDataSet transpose= new TransposeRankNDataSet( v, order );
            return transpose;
        } else {
            return v;
        }
    }
    
    private synchronized void readData( ProgressMonitor mon ) throws IOException {

        String location;
        boolean makeLocal= true;
        if ( makeLocal ) {
            File file= getFile(mon);
            location= file.toURI().toURL().toString();
        } else {
            location= DataSetURI.fromUri(resourceURI);
        }
        
        NetcdfDataset dataset=null;

        mon.started();
        if ( sMyUrl.endsWith(".ncml" ) ) {
            dataset= NcMLReader.readNcML( location, null );
        } else {
            NetcdfFile f= NetcdfFile.open( location );
            dataset= new NetcdfDataset( f );
        }

        ncfile= dataset;
        
        List<Variable> variables= (List<Variable>)dataset.getVariables();
        
        if ( svariable==null ) {
            for ( int i=0; i<variables.size(); i++ ) {
                Variable v= variables.get(i);
                if ( !v.getDimension(0).getName().equals(v.getName()) ) {
                    variable= v;
                    break;
                }
            }
        } else {
            for ( int i=0; i<variables.size(); i++ ) {
                Variable v= variables.get(i);
                if ( v.getName().replaceAll(" ", "+").equals( svariable ) ) { //TODO: verify this, it's probably going to cause problems now.
                    variable= v;
                }
            }
        }
        mon.finished();
    }
    
    public static DataSourceFactory getFactory() {
        return new NetCDFDataSourceFactory();
    }
    
    @Override
    public Map<String,Object> getMetadata( ProgressMonitor mon ) throws Exception {
        mon.started();
        mon.setProgressMessage("reading metadata");
        readData( mon );
        List attr= variable.getAttributes();
        
        if ( attr==null ) return null; // transient state

        Map<String,Object> result= new LinkedHashMap<String, Object>();
        for( int i=0; i<attr.size(); i++ ) {
            Attribute at= (Attribute) attr.get(i);
            result.put( at.getName(), at.getStringValue() );
        }
        mon.finished();
        return result;
        
    }

    @Override
    public MetadataModel getMetadataModel() {
        List attr= variable.getAttributes();
        if ( attr==null ) return null; // transient state
        for( int i=0; i<attr.size(); i++ ) {
            Attribute at= (Attribute) attr.get(i);
            if ( at.getName().equals("VAR_TYPE") ) {
                return new IstpMetadataModel();
            }
        }
        return MetadataModel.createNullModel();
    }


    
}
