
package org.tsds.datasource;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 *
 * @author jbf
 */
public class SpeedTest {

    private static int empty( InputStream in ) throws IOException {
        byte[] buf= new byte[2048];
        int total= 0;
        int bytes= in.read(buf);
        while ( bytes!=-1 ) {
            total+=bytes;
            bytes= in.read(buf);
        }
        return total;
    }
    public static void main(String[] args) throws MalformedURLException, IOException {
        String[] urls = new String[]{
            "http://timeseries.org/get.cgi?StartDate=20030301&EndDate=20030331&ppd=1&ext=bin&out=tsml&param1=OMNI_OMNIHR-26-v0",
            "http://timeseries.org/OMNI_OMNIHR-26-v0-to_20030301-tf_20030331-ppd_1-filter_0-ext_bin.bin",
            "http://timeseries.org/OMNI_OMNIHR-26-v0-to_20030301-tf_20030331-ppd_1-filter_2-ext_bin.bin",
            "http://timeseries.org/OMNI_OMNIHR-26-v0-to_20030301-tf_20030331-ppd_1-filter_3-ext_bin.bin",
        };
        for (int i = 0; i < urls.length; i++) {
            URL url = new URL(urls[i]);
            System.err.println(url);
            HttpURLConnection cc= (HttpURLConnection) url.openConnection();
            cc.connect();
            System.err.println("  "+empty(cc.getInputStream()) );
        }
        
        for (int i = 0; i < urls.length; i++) {
            URL url = new URL(urls[i]);
            System.err.println("2: " + url);
            //HttpClient client= HttpClient.New(url);
            //client.afterConnect();
            //System.err.println("  "+empty(client.getInputStream()) );
        }
    }
}
