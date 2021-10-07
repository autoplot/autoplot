/*
 * NetCDFDataSourceFactory.java
 *
 * Created on May 14, 2007, 11:27 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.autoplot.netCDF;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFactory;
import org.autoplot.datasource.HtmlResponseIOException;
import org.autoplot.datasource.MetadataModel;
import org.autoplot.datasource.URISplit;
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
    
    @Override
    public DataSource getDataSource(URI uri) throws IOException {
        return new NetCDFDataSource( uri );
    }
    
    public Map<String,CompletionContext> getParams( URI ncFile, ProgressMonitor mon ) throws IOException {
        File file= DataSetURI.getFile( ncFile, mon );

        NetcdfDataset dataset= getDataSet( file.toURI().toURL() );
        List<Variable> vars= (List<Variable>)dataset.getVariables();

        LinkedHashMap<String,CompletionContext> result= new LinkedHashMap<>();
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
                    result.put( v2.getName(), new CompletionContext( 
                        CompletionContext.CONTEXT_PARAMETER_NAME,
                        v2.getName(), this, "arg_0",
                        v2.getNameAndDimensions(), v2.getDescription(), true ) );
                }
            } else {
                boolean isFormattedTime= v.getDataType()==DataType.CHAR && v.getRank()==2 && v.getShape(1)>=14 && v.getShape(1)<=30;
                if ( !isFormattedTime && !v.getDataType().isNumeric() ) continue;
                result.put( v.getName(), new CompletionContext(
                        CompletionContext.CONTEXT_PARAMETER_NAME,
                        v.getName(), this, "arg_0",
                        v.getNameAndDimensions(), v.getDescription(), true ) );
            }
        }
        dataset.close();
        return result;

    }
    
    @Override
    public List<CompletionContext> getCompletions( CompletionContext cc ,org.das2.util.monitor.ProgressMonitor mon ) throws IOException {
        List<CompletionContext> result= new ArrayList<>();
        
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            Map<String,CompletionContext> result1= getParams( cc.resourceURI, mon );
            
            for ( Entry<String,CompletionContext> r: result1.entrySet() ) {
                result.add( r.getValue() );
            }

            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "units=", "override the file units"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "xunits=", "override the units for x"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "where=",
                    "add constraint by another field's value"));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "validMin=",
                    "values less than this value are treated as fill."));
            result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "validMax=",
                    "values greater than this value are treated as fill."));

        } else if ( cc.context==CompletionContext.CONTEXT_PARAMETER_VALUE ) {
            String paramName= CompletionContext.get( CompletionContext.CONTEXT_PARAMETER_NAME, cc );
            if (paramName.equals("fill")) {
                return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<double>"));
            } else if (paramName.equals("validMin")) {
                return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<double>"));
            } else if (paramName.equals("validMax")) {
                return Collections.singletonList(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "<double>"));            
            } else if (paramName.equals("where")) { // TODO: a fun project would be to make completions for this that look in the file...
                if ( cc.completable.contains(".") ) {
                    int i= cc.completable.lastIndexOf(".");
                    String s= cc.completable.substring(0,i);
                    if ( s.length()>0 ) {
                        result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, s + ".eq(0)" ) ) ;
                        result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, s + ".ne(0)" ) ) ;
                        result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, s + ".gt(0)" ) ) ;
                        result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, s + ".lt(0)" ) ) ;
                        result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, s + ".within(0+to+10)" ) ) ;
                    }
                } else {
                    Map<String,CompletionContext> result1= getParams( cc.resourceURI, mon );
                    for ( Entry<String,CompletionContext> r: result1.entrySet() ) {
                        String s= r.getKey();
                        result.add( new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, s+".eq(0)","where parameter is equal to zero"));
                    }       
                }
                return result;
            }
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
    
    /**
     * return the number of records of the variable.  The file will be 
     * downloaded if it is not available.  This was introduced because
     * we had a huge file where we needed to read in so many blocks at a time,
     * instead of the entire file.
     * @param surl the URI, including the variable to read.
     * @param mon the monitor
     * @return the number of records.
     * @throws IOException 
     */
    public int getNumberOfRecords( String surl, ProgressMonitor mon ) throws IOException {
        URISplit split = URISplit.parse( surl );
        Map params= URISplit.parseParams( split.params );

        File file= DataSetURI.getFile( surl, mon ); // check for non-ncml.  We always download now because ncml can be slow.
        String svariable= (String)params.get("arg_0");
        
        NetcdfDataset dataset= getDataSet( file.toURI().toURL() );
        
        for ( Variable v : dataset.getVariables() ) {
            if ( v.getName().replaceAll(" ", "+").equals(svariable) ) {
                return v.getDimension(0).getLength();
            }
        }
        throw new IllegalArgumentException("variable name must be specified");
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
            
            if ( svariable==null ) {
                svariable= (String)params.get( NetCDFDataSource.PARAM_Y );
            }

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
