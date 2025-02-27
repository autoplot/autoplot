
package org.autoplot.ascii;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URI;
import org.autoplot.datasource.AbstractDataSource;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.OdlParser;
import org.das2.util.monitor.ProgressMonitor;
import org.json.JSONObject;

/**
 * Odl parser, introduced to handle STS files from the Juno Mag team.
 * @author jbf
 */
public class OdlDataSource extends AbstractDataSource {

    public OdlDataSource(URI uri) {
        super(uri);
    }

    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {

        String param = getParam("arg_0","");

        File ff = getFile( resourceURI, mon.getSubtaskMonitor("download file") );

        JSONObject record = new JSONObject();
        BufferedReader reader = new BufferedReader(new FileReader(ff));

        String ss = OdlParser.readOdl(reader,record);

        String format = OdlParser.getFormat(record);
        
        QDataSet ds = OdlParser.readStream( reader,record,mon.getSubtaskMonitor("read stream") );
        if ( param.equals("") ) {
            String[] nn= OdlParser.getNames( record, "", true, null);
            String lastVector="";
            for ( String n : nn ) {
                if ( n.indexOf('.')==-1 ) lastVector= n;
            }
        }
        
        QDataSet result = OdlParser.getDataSet(record,ds,param);
        result= Ops.putProperty( result, QDataSet.NAME, param );
        
        return result;
    }

}
