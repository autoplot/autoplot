
package org.autoplot.json;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import org.autoplot.datasource.AbstractDataSource;
import org.autoplot.datasource.URISplit;
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
        final String dep0= getParam("depend0",null);
        int ikey=-1;
        int idep0=-1;
        Class expecting=null;
        DataSetBuilder build= new DataSetBuilder(2, 100, 1 + (dep0==null ? 0 : 1) );
        try ( InputStream ins= new FileInputStream(f) ) {
            JSONJIterator iter= new JSONJIterator( ins );
            if ( iter.hasNext() ) {
                Object ob= iter.next();
                if ( ob instanceof JSONArray ) {
                    JSONArray job= (JSONArray)ob;
                    ikey= Integer.parseInt(key);
                    idep0= Integer.parseInt(dep0);
                    Datum d= Ops.datum(job.get(ikey));
                    if ( dep0==null ) {
                        build.nextRecord( new Object[] { d } );
                    } else {
                        Datum d0= Ops.datum(job.get(idep0));
                        build.nextRecord( new Object[] { d0, d } );
                    }
                } else if ( ob instanceof JSONObject ) {
                    JSONObject job= (JSONObject)ob;
                    Datum d= Ops.datum(job.get(key));
                    if ( dep0==null ) {
                        build.nextRecord( new Object[] { d } );
                    } else {
                        Datum d0= Ops.datum(job.get(dep0));
                        build.nextRecord( new Object[] { d0, d } );
                    }
                }
                expecting= ob.getClass();
            }
            while ( iter.hasNext() ) {
                Object ob= iter.next();
                if ( ob instanceof JSONArray && expecting==ob.getClass() ) {
                    JSONArray job= (JSONArray)ob;
                    if ( dep0==null ) {
                        build.nextRecord( job.get(ikey) );
                    } else {
                        build.nextRecord( new Object[] { job.get(idep0), job.get(ikey) } );
                    }
                } else if ( ob instanceof JSONObject && expecting==ob.getClass() ) {
                    if ( dep0==null ) {
                        JSONObject job= (JSONObject)ob;
                        build.nextRecord( job.get(key) );   
                    } else {
                        JSONObject job= (JSONObject)ob;
                        build.nextRecord( job.get(dep0), job.get(key) );
                    }
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
