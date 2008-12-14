/*
 * NetCDFDataSourceFactory.java
 *
 * Created on May 14, 2007, 11:27 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.netCDF;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.MetadataModel;
import org.virbo.datasource.URLSplit;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.ncml.NcMLReader;

/**
 *
 * @author jbf
 */
public class NetCDFDataSourceFactory implements DataSourceFactory {
    
    /** Creates a new instance of NetCDFDataSourceFactory */
    public NetCDFDataSourceFactory() {
    }
    
    public DataSource getDataSource(URL url) throws IOException {
        return new NetCDFDataSource( url );
    }
    
    
    public List<CompletionContext> getCompletions( CompletionContext cc ,org.das2.util.monitor.ProgressMonitor mon ) throws IOException {
        List<CompletionContext> result= new ArrayList<CompletionContext>();
        
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            String file= cc.resource.toString();
            
            NetcdfDataset dataset= getDataSet( file );
            List<Variable> vars= (List<Variable>)dataset.getVariables();
            
            for ( int j=0; j<vars.size();j++ ) {
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, vars.get(j).getName(), this, "arg_0" , null, null )    );
            }
        }
        
        return result;
        
    }
    
    public MetadataModel getMetadataModel(URL url) {
        return MetadataModel.createNullModel();
    }
    
    private NetcdfDataset getDataSet( String resource ) throws IOException {
        if ( resource.endsWith(".ncml") ) {
            return NcMLReader.readNcML( resource, null );
        
        } else {
            NetcdfFile f= NetcdfFile.open( resource );
            NetcdfDataset dataset= new NetcdfDataset( f );
            return dataset;
        }
        
    }
    
    
    public boolean reject( String surl, ProgressMonitor mon ) {
        try {
            URLSplit split = URLSplit.parse( surl );
            Map params= URLSplit.parseParams( split.params );
            
            NetcdfDataset dataset= getDataSet( split.file );
            
            int depCount=0; // number of dependent variables--If there's just one, then we needn't identify it
            List<Variable> vars= (List<Variable>)dataset.getVariables();
            
            String lookfor= (String)params.get("arg_0");
            boolean haveIt= false;
            
            for ( int j=0; j<vars.size();j++ ) {
                Variable v= vars.get(j);
                List l= v.getDimension(0).getCoordinateVariables();
                if ( l.size()>1 ) throw new IllegalArgumentException("Huh?");
                for ( int i=0; i<l.size(); i++ ) {
                    Variable dv= (Variable) l.get(0);
                    if ( dv!=v ) {
                        depCount++;
                    }
                }
                if ( v.getName().equals(lookfor) ) haveIt= true;
            }
            
            if ( depCount==1 ) {
                return false;
            } else {
                return !haveIt;
            }
            
        } catch ( IOException e ) {
            throw new RuntimeException(e);
        }
    }
    
    
}
