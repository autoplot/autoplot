/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.pdsppi;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSourceFactory;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.URISplit;
import org.virbo.spase.VOTableReader;
import org.xml.sax.SAXException;

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
        
        String url= "http://ppi.pds.nasa.gov/ditdos/write?f=vo&id=pds://"+id;
        read= new VOTableReader();            
        mon.setProgressMessage("downloading data");
        File f= DataSetURI.downloadResourceAsTempFile( new URL(url), 3600, mon );
        mon.setProgressMessage("reading data");
        QDataSet ds= read.readHeader( f.toString(), mon );

        List<CompletionContext> ccresult= new ArrayList<CompletionContext>();
        for ( int i=0; i<ds.length(); i++ ) {
            String n= (String) ds.property( QDataSet.NAME, i );
            String l= (String) ds.property( QDataSet.LABEL, i );
            String t= (String) ds.property( QDataSet.TITLE, i );
            CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, n, this, n, l, t, true );
            ccresult.add(cc1);
        }
        return ccresult;
    }
    
    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        if ( cc.context==CompletionContext.CONTEXT_PARAMETER_NAME ) {
            List<CompletionContext> ccresult= new ArrayList<CompletionContext>(10);
            ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "id=", "id=", "table id" ) );
            ccresult.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "ds=", "ds=", "dataset within a table" ) );
            return ccresult;
        } else if ( cc.context==CompletionContext.CONTEXT_PARAMETER_VALUE ) {
            String param= CompletionContext.get( CompletionContext.CONTEXT_PARAMETER_NAME, cc );
            if ( param.equals("ds") ) {
                String id= "PPI/GO-J-MAG-3-RDR-HIGHRES-V1.0/DATA/SURVEY/HIGH_RES/ORB01_PSX_SYS3";
                return getDataSetCompletions( id, mon );
            } else if ( param.equals("id") ) {
                List<CompletionContext> ccresult= new ArrayList<CompletionContext>();
                ArrayList<String> keys= new ArrayList();
                keys.add("MESSMAGDATA_3001/DATA/SCIENCE_DATA/RTN/2009/AUG/MAGRTNSCIAVG09213_05_V05");
                keys.add("GOMW_5004/DATA/MAG/SATELLITES/EUROPA/ORB25_EUR_EPHIO");
                keys.add("PPI/VG_1502/DATA/MAG/HG_1_92S_I");
                for ( String key: keys ) {
                    CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, key, this, null, key, key, true  );
                    ccresult.add(cc1);
                }
                return ccresult;
            }
        }
        return new ArrayList<CompletionContext>() {};
    }

    @Override
    public <T> T getCapability(Class<T> clazz) {
        return null;
    }


    @Override
    public boolean reject(String surl, List<String> problems, ProgressMonitor mon) {
        URISplit split= URISplit.parse(surl);
        Map<String,String> params= URISplit.parseParams(split.params);

        if ( !( params.containsKey("ds") && params.containsKey("id") ) ) return true;
        
        return false;
    }

}
