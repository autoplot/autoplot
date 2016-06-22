/*
 * NetCDFDataSourceFactory.java
 *
 * Created on May 14, 2007, 11:27 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.netCDF;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.AbstractDataSourceFactory;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.HtmlResponseIOException;
import org.virbo.datasource.MetadataModel;
import org.virbo.datasource.URISplit;
import ucar.ma2.DataType;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Structure;
import ucar.nc2.Variable;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.ncml.NcMLReader;

/**
 * Factory for NetCDF and HDF5 data sources.
 * @author jbf
 */
public class NetCDFDataSourceFactory extends AbstractDataSourceFactory implements DataSourceFactory {
    
    private static final Logger logger= LoggerManager.getLogger("apdss.netcdf");
    
    /** Creates a new instance of NetCDFDataSourceFactory */
    public NetCDFDataSourceFactory() {
    }
    
    public DataSource getDataSource(URI uri) throws IOException {
        return new NetCDFDataSource( uri );
    }
    
    @Override
    public List<CompletionContext> getCompletions( CompletionContext cc ,org.das2.util.monitor.ProgressMonitor mon ) throws IOException {
        List<CompletionContext> result= new ArrayList<CompletionContext>();
        
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            File file= DataSetURI.getFile( cc.resourceURI, mon );
            
            NetcdfDataset dataset= getDataSet( file.toURI().toURL() );
            List<Variable> vars= (List<Variable>)dataset.getVariables();
            
            for ( int j=0; j<vars.size();j++ ) {
                Variable v= vars.get(j);
                if ( v.getDimensions().isEmpty() ) continue;
                if ( v instanceof Structure ) {
                    for ( Variable v2: ((Structure) v).getVariables() ) {
                        if ( !v2.getDataType().isNumeric() ) continue;
                        StringBuilder description= new StringBuilder( v2.getName()+"[" );
                        for ( int k=0; k<v2.getDimensions().size(); k++ ) {
                            Dimension d= v2.getDimension(k);
                            if ( k>0 ) description.append(",");
                            try {
                                String n= d.getName();
                                if ( n!=null && !n.equals(v2.getName()) ) {
                                    description.append(d.getName()).append("=");
                                }
                                description.append(d.getLength());
                            } catch ( NullPointerException ex ) {
                                throw ex;
                            }
                        }
                        description.append("]");
                        
                        ///parameters.put( v2.getName(), description.toString() );
                        result.add( new CompletionContext( 
                            CompletionContext.CONTEXT_PARAMETER_NAME,
                            v2.getName(), this, "arg_0",
                            v2.getNameAndDimensions(), v2.getDescription(), true ) );
                    }
                    
                } else {
                    boolean isFormattedTime= v.getDataType()==DataType.CHAR && v.getRank()==2 && v.getShape(1)>=14 && v.getShape(1)<=30;
                    if ( !isFormattedTime && !v.getDataType().isNumeric() ) continue;
                    result.add( new CompletionContext(
                            CompletionContext.CONTEXT_PARAMETER_NAME,
                            v.getName(), this, "arg_0",
                            v.getNameAndDimensions(), v.getDescription(), true ) );
                }
            }
            dataset.close();
            
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "units="));

        }
        
        return result;
        
    }
    
    public MetadataModel getMetadataModel(URL url) {
        return MetadataModel.createNullModel();
    }

    protected static void checkMatlab( String resource ) throws IOException {
        if ( resource.startsWith("file:/") ) {
            BufferedReader r= new BufferedReader( new FileReader( new URL(resource).getFile() ) );
            try {
                String magic= r.readLine();
                if ( magic!=null && magic.contains("MATLAB") && !magic.contains("HDF5") ) {
                    throw new IllegalArgumentException("Matlab file is not an HDF5 file.  Use Matlab 7.3 or greater, and save with -v7.3");
                }
            } finally {
                r.close();
            }
        }
    }
    /**
     * @param resource
     * @return
     * @throws IOException
     */
    private NetcdfDataset getDataSet( URL resourceURL ) throws IOException {
        String resource;
        if ( resourceURL.getProtocol().equals("file") ) {
            try {
                resource= new File( resourceURL.toURI() ).toString();
            } catch (URISyntaxException ex) {
                logger.log(Level.SEVERE, null, ex);
                resource= resourceURL.toString(); // do what we did before.
            }
        } else {
            resource= resourceURL.toString();
        }
        
        if ( resource.endsWith(".ncml") ) {
            return NcMLReader.readNcML( resource, null );
        
        } else {
            checkMatlab( resource );
            NetcdfFile f= NetcdfFile.open( resource );
            NetcdfDataset dataset= new NetcdfDataset( f );
            return dataset;
        }
        
    }
    
    
    @Override
    public boolean reject( String surl, List<String> problems, ProgressMonitor mon ) {
        try {
            URISplit split = URISplit.parse( surl );
            Map params= URISplit.parseParams( split.params );

            File file= DataSetURI.getFile( surl, mon ); // check for non-ncml.  We always download now because ncml can be slow.

            NetcdfDataset dataset= getDataSet( file.toURI().toURL() );

            int depCount=0; // number of dependent variables--If there's just one, then we needn't identify it
            List<Variable> vars= (List<Variable>)dataset.getVariables();
            
            String svariable= (String)params.get("arg_0");
            

            if ( svariable!=null ) {
                int ic= svariable.indexOf("[");
                if ( ic>-1 ) {
                    svariable= svariable.substring(0,ic);
                }
                if ( svariable!=null ) svariable= svariable.replaceAll(" ", "+");  // change space back to plus
            }

            boolean haveIt= false;
            
            for ( int j=0; j<vars.size();j++ ) {
                Variable v= vars.get(j);
                if ( v.getDimensions().isEmpty() ) continue;
                if ( v instanceof Structure ) {
                    for ( Variable v2: ((Structure) v).getVariables() ) {
                        if ( !v2.getDataType().isNumeric() ) continue;
                        if ( v2.getName().replaceAll(" ","+").equals( svariable) ) haveIt= true;
                    }
                                        
                } else {
                    List l= v.getDimension(0).getCoordinateVariables();
                    if ( l.size()>1 ) throw new IllegalArgumentException("Huh?");
                    for ( int i=0; i<l.size(); i++ ) {
                        Variable dv= (Variable) l.get(0);
                        if ( dv!=v ) {
                            depCount++;
                        }
                    }
                    if ( v.getName().replaceAll(" ", "+").equals(svariable) ) haveIt= true;
                }
            }

            dataset.close();
            
            if ( depCount==1 ) {
                return false;
            } else {
                return !haveIt;
            }
        } catch ( HtmlResponseIOException e ) {
            return false;
        } catch ( IOException e ) {
            return false;
        }
    }

    @Override
    public <T> T getCapability(Class<T> clazz) {
        return null;
    }
    
    
}
