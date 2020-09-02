/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.pdsppi;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFactory;
import org.autoplot.datasource.URISplit;
import org.autoplot.spase.VOTableReader;

/**
 * PDS/PPI node factory.  Examples include:
 * vap+pdsppi:id=PPI/GOMW_5004/DATA/MAG/SATELLITES/EUROPA/ORB25_EUR_EPHIO
 * vap+pdsppi:id=PPI/GO-J-MAG-3-RDR-HIGHRES-V1.0/DATA/SURVEY/HIGH_RES/ORB01_PSX_SYS3&ds=B-FIELD%20MAGNITUDE
 * @author jbf
 */
public class PDSPPIDataSourceFactory extends AbstractDataSourceFactory implements DataSourceFactory {

    protected static final Logger logger= LoggerManager.getLogger("apdss.pdsppi");

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new PDSPPIDataSource(uri);
    }

    private List<CompletionContext> getDataSetCompletions( String id, ProgressMonitor mon  ) throws Exception { 
        VOTableReader read;  
        
        String url= PDSPPIDB.PDSPPI + "ditdos/write?f=vo&id=pds://"+id;
        read= new VOTableReader();            
        mon.setProgressMessage("downloading data");
        logger.log(Level.FINE, "getDataSetCompletions {0}", url);
        File f= DataSetURI.downloadResourceAsTempFile( new URL(url), 3600, mon );
        mon.setProgressMessage("reading data");
        QDataSet ds= read.readHeader( f.toString(), mon.getSubtaskMonitor("reading data") );

        List<CompletionContext> ccresult= new ArrayList<>();
        for ( int i=0; i<ds.length(); i++ ) {
            //String n= (String) ds.property( QDataSet.NAME, i );
            String l= (String) ds.property( QDataSet.LABEL, i );
            String t= (String) ds.property( QDataSet.TITLE, i );
            CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, l, this, l, l, t, true );
            ccresult.add(cc1);
        }
        return ccresult;
    }
    
    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            List<CompletionContext> ccresult= new ArrayList<>(10);
            ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "id=", "id=", "table id" ) );
            ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "param=", "param=", "dataset within a table" ) );
            return ccresult;
        } else if ( cc.context==CompletionContext.CONTEXT_PARAMETER_VALUE ) {
            String param= CompletionContext.get( CompletionContext.CONTEXT_PARAMETER_NAME, cc );
            if ( param.equals("param") ) {
                URISplit split= URISplit.parse(cc.surl);
                Map<String,String> pp= URISplit.parseParams(split.params);
                String id= pp.get("id");
                if ( id==null ) {
                    return Collections.singletonList( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "", "", "(Select id first)" ) );
                } else {
                    return getDataSetCompletions( id, mon );
                }
            } else if ( param.equals("sc") ) {
                String[] scs= PDSPPIDB.getInstance().getSpacecraft();
                List<CompletionContext> ccresult= new ArrayList<>();
                for ( String sc: scs) {
                    CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, sc, this, null, sc, sc, false  );
                    ccresult.add(cc1);
                }
                return ccresult;
            } else if ( param.equals("id") ) { // TODO: There should be a way to get what's been specified already.
                String u= cc.surl.substring(0,cc.surlpos);
                URISplit split= URISplit.parse(u);
                Map<String,String> params= URISplit.parseParams(split.params);
                String sc= params.get("sc");                
                if ( sc!=null ) {
                    sc= sc.replaceAll("\\+"," ");
                    String[] ids= PDSPPIDB.getInstance().getIds("sc="+sc,"PPI/");
                    List<CompletionContext> ccresult= new ArrayList<>();
                    String id1= params.get("id");
                    if ( id1!=null ) {
                        String[] ss= id1.split("/",-2);
                        if ( ss.length==2 ) {
                            id1= ""; // just do completions on root part.
                        }
                    }
                    if ( id1!=null && id1.length()>0 ) {
                        String file=null;
                        String iid= null;
                        for ( String id : ids ) {
                            if ( id1.startsWith(id) ) {
                                file= id1.substring(id.length());
                                iid= id;
                            }
                        }
                        if ( file!=null ) {
                            FileSystem fs= new PDSPPIFileSystem(PDSPPIDB.removeExtraSlashes(iid));
                            int i= file.lastIndexOf("/");
                            file= file.substring(0,i+1);
                            String[] ff= fs.listDirectory(file,mon);
                            for ( String ff1 : ff ) {
                                String theid= iid+file+ff1;
                                CompletionContext cc1;
                                if ( PDSPPIDB.isPlottable(theid) ) {
                                    int dotpos= theid.lastIndexOf("."); // pop off the extension
                                    theid= theid.substring(0,dotpos);
                                    cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, theid, this, null, theid, theid, true  );
                                    ccresult.add(cc1);
                                } else {
                                    if ( theid.endsWith("/") ) {
                                        cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, theid, this, null, theid, theid, false );
                                        ccresult.add(cc1);
                                    }
                                }
                            }
                            return ccresult;
                        } else {
                            return Collections.singletonList(  new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "", "error", "error" ) );
                        }
                    } else {
                        for ( String id : ids ) {
                            if ( id.contains("\t") ) {
                                logger.log(Level.FINE, "tab in id from PDSPPIDB.getInstance().getIds(sc={0})", sc);
                                continue;
                            }
                            CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, id+"/", this, null, id+"/", id+"/", false  );
                            ccresult.add(cc1);
                        }
                    }
                    return ccresult;
                } else {
                    return Collections.singletonList(  new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "", "enter sc first", "sc constraint needed" ) );
                }
            }
        }
        return new ArrayList<CompletionContext>() {};
    }

    @Override
    public <T> T getCapability(Class<T> clazz) {
        return null;
//        if ( clazz==TimeSeriesBrowse.class ) {
//            return (T) new PDSPPITimeSeriesBrowse();
//        } else {
//            return null;
//        }
    }


    @Override
    public boolean reject(String surl, List<String> problems, ProgressMonitor mon) {
        URISplit split= URISplit.parse(surl);
        Map<String,String> params= URISplit.parseParams(split.params);

        String param= params.get("param");
        if ( param==null ) {
            param= params.get("ds");
        }
        if ( param==null ) problems.add("missing param");
        
        String id= params.get("id");
        if ( id==null ) problems.add("missing id");
        
        return ( param==null || id==null );
        
    }

    @Override
    public boolean supportsDiscovery() {
        return true;
    }

    @Override
    public boolean isFileResource() {
        return false;
    }
    
}
