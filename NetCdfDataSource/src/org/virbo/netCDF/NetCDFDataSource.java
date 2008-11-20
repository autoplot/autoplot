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
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.QubeDataSetIterator;
import org.virbo.dataset.WritableDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.URLSplit;
import org.virbo.dsutil.TransposeRankNDataSet;
import ucar.nc2.Attribute;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.ncml.NcMLReader;

/**
 *
 * @author jbf
 */
public class NetCDFDataSource extends AbstractDataSource {
    
    Variable variable;
    String sMyUrl;
    String svariable;
    
    /** Creates a new instance of NetCDFDataSource */
    public NetCDFDataSource( URL url ) throws IOException {
        super(url);
        parseUrl();
        
    }
    
    private void parseUrl() {
        String surl= url.toString();
        int i= surl.lastIndexOf('?');
        URL myUrl;
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
            i= surl.indexOf('?');
            String params= surl.substring( i+1 );
            
            
            Map p= getParams();
            if ( p.containsKey( "id" ) ) {
                svariable= (String) p.get( "id" );
            } else {
                svariable= (String) p.get("arg_0"); // legacy support
            }
        }
    }
    
    public QDataSet getDataSet( ProgressMonitor mon) throws IOException {
        mon.started();
        readData(mon);
        NetCdfVarDataSet result= new NetCdfVarDataSet( variable );
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

        File file= getFile(mon);
        
        NetcdfDataset dataset=null;
        
        if ( sMyUrl.endsWith(".ncml" ) ) {
            String kl= URLSplit.maybeAddFile( file.toString() ); // kludge for readNcML, which doesn't like drive letters.
            dataset= NcMLReader.readNcML( kl, null );
        } else {
            NetcdfFile f= NetcdfFile.open( file.toString() );
            dataset= new NetcdfDataset( f );
        }
        
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
                if ( v.getName().equals( svariable ) ) {
                    variable= v;
                }
            }
        }
    }
    
    public static DataSourceFactory getFactory() {
        return new NetCDFDataSourceFactory();
    }
    
    public Map<String,Object> getMetaData( ProgressMonitor mon ) throws Exception {
        readData( mon );
        List attr= variable.getAttributes();
        
        if ( attr==null ) return null; // transient state
        
        Map<String,Object> result= new LinkedHashMap<String, Object>();
        for( int i=0; i<attr.size(); i++ ) {
            Attribute at= (Attribute) attr.get(i);
            result.put( at.getName(), at.getStringValue() );
        }
        
        return result;
        
    }
    
}
