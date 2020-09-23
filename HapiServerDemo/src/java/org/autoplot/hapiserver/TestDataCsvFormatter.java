
package org.autoplot.hapiserver;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;
import org.das2.util.filesystem.HtmlUtil;
import org.das2.util.monitor.CancelledOperationException;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author jbf
 */
public class TestDataCsvFormatter {
    public static void main( String[] args ) throws MalformedURLException, IOException, CancelledOperationException, JSONException {
        DataFormatter gg= new CsvDataFormatter();
        String info= HtmlUtil.readToString(new URL("http://localhost:8084/HapiServerDemo/hapi/info?id=poolTemperature"));
        JSONObject joInfo= new JSONObject(info);
        QDataSet ds= Ops.linspace(0,10,100);
        ds= Ops.putProperty( ds, QDataSet.VALID_MIN, 2 );
        QDataSet tt= Ops.linspace("2020-09-23T00:00Z","2020-09-23T12:00Z", 100);
        ds= Ops.bundle( tt, ds );
        gg.initialize( joInfo, System.out, ds.slice(0) );
        for ( int i=0; i<ds.length(); i++ ) {
            gg.sendRecord( System.out, ds.slice(i) );
        }
        
    }
}
