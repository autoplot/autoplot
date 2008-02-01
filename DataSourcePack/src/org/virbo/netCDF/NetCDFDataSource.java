/*
 * NetCDFDataSource.java
 *
 * Created on April 4, 2007, 7:03 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.netCDF;

import edu.uiowa.physics.pw.das.util.DasProgressMonitor;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.tree.TreeModel;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.metatree.NameValueTreeModel;
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
    
    public QDataSet getDataSet( DasProgressMonitor mon) throws IOException {
        readData(mon);
        QDataSet result= new NetCdfVarDataSet( variable );
        return result;
    }
    
    private synchronized void readData( DasProgressMonitor mon ) throws IOException {

        File file= getFile(mon);
        
        NetcdfDataset dataset=null;
        
        if ( sMyUrl.endsWith(".ncml" ) ) {
            String kl= DataSetURL.maybeAddFile( file.toString() ); // kludge for readNcML, which doesn't like drive letters.
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
    
    public boolean asynchronousLoad() {
        return true;
    }
    
    public static DataSourceFactory getFactory() {
        return new NetCDFDataSourceFactory();
    }
    
    public TreeModel getMetaData( DasProgressMonitor mon ) throws Exception {
        readData( mon );
        List attr= variable.getAttributes();
        
        if ( attr==null ) return null; // transient state
        
        List names= new ArrayList();
        List values= new ArrayList();
        
        for( int i=0; i<attr.size(); i++ ) {
            Attribute at= (Attribute) attr.get(i);
            names.add( at.getName() );
            values.add( at.getStringValue() );
        }
        
        return NameValueTreeModel.create( "variable attributes", names, values );
        
    }
    
}
