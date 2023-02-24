/*
 * CdfFileDataSourceFactory.java
 *
 * Created on July 23, 2007, 8:29 PM
 */

package org.autoplot.cdf;

import gov.nasa.gsfc.spdf.cdfj.CDFReader;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFactory;
import org.autoplot.datasource.URISplit;

/**
 * Read data from CDF files using the Java reader provided by NASA/Goddard.
 * @author jbf
 */
public class CdfJavaDataSourceFactory extends AbstractDataSourceFactory implements DataSourceFactory {
    
    private static final Logger logger = Logger.getLogger("apdss.cdf");
    
    /** Creates a new instance of CdfFileDataSourceFactory */
    public CdfJavaDataSourceFactory() {
	
    }    
    
    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new org.autoplot.cdf.CdfDataSource( uri ); // still issues: http://www.sarahandjeremy.net:8080/hudson/job/autoplot-test100/4987/
    }
    
    
    public String editPanel(String surl) throws Exception {
        return surl;
    }
    
    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, org.das2.util.monitor.ProgressMonitor mon) throws Exception {
        if ( cc.context.equals(CompletionContext.CONTEXT_PARAMETER_NAME) ) {
            logger.log(Level.FINE, "getCompletions {0}", cc.resourceURI);
            File cdfFile= DataSetURI.getFile( cc.resourceURI, mon );
            String fileName= cdfFile.toString();
            //if ( System.getProperty("os.name").startsWith("Windows") ) fileName= CdfUtil.win95Name( cdfFile );
            
            logger.log(Level.FINEST, "opening cdf file {0}", fileName);

            CDFReader cdf;

            cdf = CdfDataSource.getCdfFile(fileName);

            logger.finest("inspect cdf for plottable parameters");
            Map<String,String> result= org.autoplot.cdf.CdfUtil.getPlottable( cdf, false, 4 );
            
            logger.finest("close cdf");
            //cdf.close();
            
            List<CompletionContext> ccresult= new ArrayList<>();
            for ( java.util.Map.Entry<String,String> e:result.entrySet() ) {
                String key= e.getKey();
                CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, key, this, "arg_0", e.getValue(), null, true );
                ccresult.add(cc1);
            }

            ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "interpMeta=", "control interpretation of metadata"));
            ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "replaceLabels=", "use DEPEND data to label channels"));
            ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "doDep=", "control dependencies between variables"));
            ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "where=", "only return variables where the condition is true"));            
            ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "loadErrors=", "load errors as well."));            
            return ccresult;
            
        } else if ( cc.context==CompletionContext.CONTEXT_PARAMETER_VALUE ) {
            String parmname= CompletionContext.get( CompletionContext.CONTEXT_PARAMETER_NAME, cc );
            if ( parmname.equals("id") ) {
                String file= CompletionContext.get( CompletionContext.CONTEXT_FILE, cc );
                
                File cdfFile= DataSetURI.getFile( DataSetURI.getURL(file), mon );
                DataSetURI.checkLength(cdfFile);
                String fileName= cdfFile.toString();
                //if ( System.getProperty("os.name").startsWith("Windows") ) fileName= CdfUtil.win95Name( cdfFile );
                
                CDFReader cdf;

                cdf = CdfDataSource.getCdfFile(fileName);

                Map<String,String> result= org.autoplot.cdf.CdfUtil.getPlottable( cdf, false, 4 );
                //cdf.close();
                
                List<CompletionContext> ccresult= new ArrayList<>();
                for ( Entry<String,String> ent:result.entrySet() ) {
                    String key= ent.getKey();
                    CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, key, this, null, key, ent.getValue(), true  );
                    ccresult.add(cc1);
                }
                
                return ccresult;
            } else if ( parmname.equals("interpMeta") ) {
                return Arrays.asList(
                        new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "yes", "use metadata (default)" ),
                        new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "no", "inhibit use of metadata" ) );
            } else if ( parmname.equals("doDep") ) {
                return Arrays.asList(
                        new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "yes", "use dependency tags (default)" ),
                        new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "no", "inhibit use of dependency tags" ) );
            } else if ( parmname.equals("replaceLabels") ) {
                return Arrays.asList(
                        new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "T", "use DEPEND data for labels" ),
                        new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "F", "normal behavior uses LABL_PTR (default)" ) );
            } else if ( parmname.equals("loadErrors") ) {
                return Arrays.asList(
                        new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "T", "use DELTA_MINUS_VAR and DELTA_PLUS_VAR for errors" ),
                        new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "F", "do not show errors, even if available (default)" ) );
            } else if ( parmname.equals("where") ) {

                List<CompletionContext> ccresult= new ArrayList<>();
                if ( cc.completable.contains(".") ) {
                    int i= cc.completable.lastIndexOf(".");
                    String s= cc.completable.substring(0,i);
                    if ( s.length()>0 ) {
                        ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, s + ".eq(0)" ) ) ;
                        ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, s + ".ne(0)" ) ) ;
                        ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, s + ".gt(0)" ) ) ;
                        ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, s + ".lt(0)" ) ) ;
                        ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, s + ".within(0+to+10)" ) ) ;
                    }
                } else {
                    String file= CompletionContext.get( CompletionContext.CONTEXT_FILE, cc );
                    
                    File cdfFile= DataSetURI.getFile( DataSetURI.getURL(file), mon );
                    DataSetURI.checkLength(cdfFile);

                    String fileName= cdfFile.toString();
                
                    CDFReader cdf;
                    cdf = org.autoplot.cdf.CdfDataSource.getCdfFile(fileName);

                    Map<String,String> result= org.autoplot.cdf.CdfUtil.getPlottable( cdf, false, 2 );
                    
                    for ( Map.Entry<String,String> e:result.entrySet() ) {
                        String key= e.getKey();
                        CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, key+".eq(0)", this, null, key+".eq(0)", e.getValue(), true  );
                        ccresult.add(cc1);
                    }
                    
                }
                return ccresult;
            } else if (  parmname.equals("X") ) {
                String file= CompletionContext.get( CompletionContext.CONTEXT_FILE, cc );
                
                File cdfFile= DataSetURI.getFile( DataSetURI.getURL(file), mon );
                DataSetURI.checkLength(cdfFile);
                String fileName= cdfFile.toString();
                //if ( System.getProperty("os.name").startsWith("Windows") ) fileName= CdfUtil.win95Name( cdfFile );
                
                CDFReader cdf;

                cdf = CdfDataSource.getCdfFile(fileName);

                LinkedHashMap<String,String> pp= URISplit.parseParams(cc.params);
                String param0= pp.get("arg_0");
                LinkedHashMap<String, CdfUtil.CdfVariableDescription> cdfParameterInfo= org.autoplot.cdf.CdfUtil.getPlottable( cdf, null );
                LinkedHashMap<String, CdfUtil.CdfVariableDescription> compatible= 
                        CdfJavaDataSourceEditorPanel.getCompatible( cdfParameterInfo, param0, 
                                param0.trim().length()>0 ? CdfJavaDataSourceEditorPanel.X_PARAMETER : "" );
                //cdf.close();
                
                List<CompletionContext> ccresult= new ArrayList<>();
                for ( Entry<String,CdfUtil.CdfVariableDescription> ent:compatible.entrySet() ) {
                    String key= ent.getKey();
                    CompletionContext cc1= new CompletionContext( 
                            CompletionContext.CONTEXT_PARAMETER_VALUE, key, this, null, key, ent.getValue().description, true  );
                    ccresult.add(cc1);
                }
                
                return ccresult;
                
            } else if (  parmname.equals("Y") ) {
                String file= CompletionContext.get( CompletionContext.CONTEXT_FILE, cc );
                
                File cdfFile= DataSetURI.getFile( DataSetURI.getURL(file), mon );
                DataSetURI.checkLength(cdfFile);
                String fileName= cdfFile.toString();
                //if ( System.getProperty("os.name").startsWith("Windows") ) fileName= CdfUtil.win95Name( cdfFile );
                
                CDFReader cdf;
                LinkedHashMap<String,String> pp= URISplit.parseParams(cc.params);
                String param0= pp.get("arg_0");
                
                cdf = CdfDataSource.getCdfFile(fileName);

                LinkedHashMap<String, CdfUtil.CdfVariableDescription> cdfParameterInfo= org.autoplot.cdf.CdfUtil.getPlottable( cdf, null );
                LinkedHashMap<String, CdfUtil.CdfVariableDescription> compatible= 
                        CdfJavaDataSourceEditorPanel.getCompatible( cdfParameterInfo, param0, 
                                param0.trim().length()>0 ? CdfJavaDataSourceEditorPanel.Y_PARAMETER : "" );
                //cdf.close();
                
                List<CompletionContext> ccresult= new ArrayList<>();
                for ( Entry<String,CdfUtil.CdfVariableDescription> ent:compatible.entrySet() ) {
                    String key= ent.getKey();
                    CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, key, this, null, key, 
                            ent.getValue().description, true  );
                    ccresult.add(cc1);
                }
                
                return ccresult;
            } else {
                return Collections.emptyList();
            }
            
        } else {
            return Collections.emptyList();
        }
    }
    
    @Override
    public boolean reject( String surl, List<String> problems, ProgressMonitor mon ) {
        try {
            if (!surl.contains("?") || surl.indexOf("?") == surl.length() - 1) {
                return true;
            }
            URISplit split = URISplit.parse(surl);
            Map<String,String> args= URISplit.parseParams( split.params );
            String param= args.get("arg_0");
            if ( param==null ) {
                param= args.get("id");
                if ( param==null ) {
                    return true;
                }
            }
            String slice1= args.get("slice1");
            if ( slice1!=null ) {
                try {
                    Integer.parseInt(slice1);
                } catch ( NumberFormatException ex ) {
                    problems.add("misformatted slice");
                    return true;
                }
            }              
            File file = DataSetURI.getFile(split.resourceUri, mon);
            if (!file.isFile()) {
                return true;
            } else {
                CDFReader cdf;
                cdf = CdfDataSource.getCdfFile( file.getPath() );

                Map<String,String> result= org.autoplot.cdf.CdfUtil.getPlottable( cdf, false, 4 );
                //cdf.close();
                int i= param.indexOf("[");
                if ( i>-1 ) {
                    param= param.substring(0,i);
                }
                
                if ( param.contains(";") ) {
                    String[] ss= param.split(";",-2);
                    boolean reject= false;
                    for ( String s: ss ){
                        if ( !result.containsKey(s) ) reject= true;
                    }
                    return reject;
                } else {
                    return ! result.containsKey(param);
                }
            }
        } catch (Exception ex) {
            logger.log( Level.SEVERE, surl, ex );
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "NASA Common Data Format files";
    }
    
}
