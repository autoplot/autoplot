
package org.autoplot.ascii;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.das2.qds.util.OdlParser;
import org.das2.util.monitor.ProgressMonitor;
import org.json.JSONObject;

/**
 * Reads ODL files, in particular those with sts extension from Juno Mag team.
 * @author jbf
 */
public class OdlDataSourceFactory  extends AbstractDataSourceFactory {

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new OdlDataSource(uri);
    }

    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        if ( cc.context.equals(CompletionContext.CONTEXT_PARAMETER_NAME) ) {    
            
            File ff= DataSetURI.getFile( cc.resourceURI, mon );
            
            JSONObject record = new JSONObject();
            BufferedReader reader = new BufferedReader(new FileReader(ff));

            String ss = OdlParser.readOdl(reader,record);
        
            String[] nn= OdlParser.getNames( record, "", true, null);
            
            List<CompletionContext> ccresult= new ArrayList<>();
            for ( String s: nn ) {
                String key= s;
                CompletionContext cc1= new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, key, this, "arg_0", s, null, true );
                ccresult.add(cc1);
            }
            
            return ccresult;
        } else {
            return super.getCompletions( cc, mon );
        }
    }

}
