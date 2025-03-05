
package org.autoplot.json;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import org.autoplot.datasource.AbstractDataSource;
import org.das2.datum.Datum;
import org.das2.datum.Units;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.DataSetBuilder;
import org.das2.util.monitor.ProgressMonitor;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author jbf
 */
public class JSONDataSource extends AbstractDataSource {

    public JSONDataSource(URI uri) {
        super(uri);
    }

    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {
        File f= getFile(mon);
        String key= getParam("arg_0", "0");
        int ikey;
        Class expecting=null;
        DataSetBuilder build= new DataSetBuilder(2,100,1);
        try ( InputStream ins= new FileInputStream(f) ) {
            JSONJIterator iter= new JSONJIterator( ins );
            if ( iter.hasNext() ) {
                Object ob= iter.next();
                if ( ob instanceof JSONArray ) {
                    JSONArray job= (JSONArray)ob;
                    ikey= Integer.parseInt(key);
                    Datum d= Ops.datum(job.get(ikey));
                    build.nextRecord( new Object[] { d } );
                } else if ( ob instanceof JSONObject ) {
                    JSONObject job= (JSONObject)ob;
                    Datum d= Ops.datum(job.get(key));
                    build.nextRecord( new Object[] { d } );
                }
                expecting= ob.getClass();
            }
            while ( iter.hasNext() ) {
                Object ob= iter.next();
                if ( ob instanceof JSONArray && expecting==ob.getClass() ) {
                    JSONArray job= (JSONArray)ob;
                    ikey= Integer.parseInt(key);
                    build.nextRecord( job.get(ikey) );
                } else if ( ob instanceof JSONObject  && expecting==ob.getClass() ) {
                    JSONObject job= (JSONObject)ob;
                    build.nextRecord( job.get(key) );   
                }
            }
        }
        QDataSet result= build.getDataSet();
        if ( result.length(0)==1 ) {
            return Ops.unbundle(result,0);
        } else {
            return result;
        }
    }
    
}
