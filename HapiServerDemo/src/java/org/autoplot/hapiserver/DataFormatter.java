
package org.autoplot.hapiserver;

import java.io.IOException;
import java.io.OutputStream;
import org.json.JSONObject;
import org.das2.qds.QDataSet;

/**
 *
 * @author jbf
 */
public interface DataFormatter {
        
    /**
     * configure the format.
     * @param info JSON info describing the records.
     * @param out
     * @param record rank 1 bundle
     * @throws java.io.IOException
     */
    public void initialize( JSONObject info, OutputStream out, QDataSet record) throws IOException;
    
    public void sendRecord( OutputStream out, QDataSet record ) throws IOException;
    
    /**
     * perform any final operations to the stream.  This 
     * DOES NOT close the stream!
     * @param out 
     * @throws java.io.IOException 
     */
    public void finalize( OutputStream out )  throws IOException;
    
}
