/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.pdsppi;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.CompletionContext;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.URISplit;

/**
 * PDS/PPI node factory.  Examples include:
 * vap+pdsppi:id=pds://PPI/GOMW_5004/DATA/MAG/SATELLITES/EUROPA/ORB25_EUR_EPHIO
 * @author jbf
 */
public class PDSPPIDataSourceFactory implements DataSourceFactory {

    protected static final Logger logger= LoggerManager.getLogger("apdss.pdsppi");

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new PDSPPIDataSource(uri);
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
                // parse label file.
                List<CompletionContext> ccresult= new ArrayList<CompletionContext>();
                CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "dummy", this, null, "dummy", "dummy", true  );
                ccresult.add(cc1);
                return ccresult;
            } else if ( param.equals("id") ) {
                List<CompletionContext> ccresult= new ArrayList<CompletionContext>();
                ArrayList<String> keys= new ArrayList();
                keys.add("pds://PPI/MESSMAGDATA_3001/DATA/SCIENCE_DATA/RTN/2009/AUG/MAGRTNSCIAVG09213_05_V05");
                keys.add("pds://PPI/GOMW_5004/DATA/MAG/SATELLITES/EUROPA/ORB25_EUR_EPHIO");
                keys.add("pds://PPI/VG_1502/DATA/MAG/HG_1_92S_I");
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

        if ( !( params.containsKey("ds") && params.containsKey("id" )&& params.containsKey("timerange") ) ) return true;
        
        return false;
    }

}
