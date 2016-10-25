
package org.autoplot.hapiserver;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.autoplot.bufferdataset.BufferDataSet;
import org.das2.datum.Units;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.qstream.AsciiTimeTransferType;
import org.virbo.qstream.TransferType;

/**
 * Format to doubles.
 * @author jbf
 */
public class BinaryDataFormatter implements DataFormatter {
    
    private static final String FORMAT_BINARY="binary";
    
    TransferType[] transferTypes;
    ByteBuffer b;
    int bufferSize;
    
    public BinaryDataFormatter( ) {
    }
        
    @Override
    public void initialize( JSONObject info, OutputStream out, QDataSet record) {
        try {
            transferTypes= new TransferType[record.length()];
            bufferSize= 0;
            
            int totalFields= 0;
            JSONArray parameters= info.getJSONArray("parameters");
            for ( int i=0; i<parameters.length(); i++ ) {
                JSONObject parameter= parameters.getJSONObject(i);
                TransferType tt;
                final String stype = parameter.getString("type");
                if ( stype.equals("isotime") ) {
                    tt= AsciiTimeTransferType.getForName( "time"+parameter.getInt("length"), Collections.singletonMap(QDataSet.UNITS,(Object)Units.us2000) );
                } else {
                    tt= TransferType.getForName(stype, null );
                }
                int nfields;
                if ( parameter.has("size") ) {
                    nfields= DataSetUtil.product( (int[])parameter.get("size") );
                } else {
                    nfields= 1;
                }
                for ( int j=0; j<nfields; j++ ) {
                    transferTypes[totalFields+j]= tt;
                }
                totalFields+= nfields;
                bufferSize+= nfields * tt.sizeBytes();
            }

            b= TransferType.allocate( bufferSize, ByteOrder.LITTLE_ENDIAN );
            
        } catch (JSONException ex) {
            Logger.getLogger(BinaryDataFormatter.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    @Override
    public void sendRecord( OutputStream out, QDataSet record ) throws IOException {

        for ( int i=0; i<record.length(); i++ ) {
            QDataSet d= record.slice(i);
            transferTypes[i].write( d.value(), b );
        }
        byte[] bytes= b.array();
        
        out.write( bytes );
        
        b.flip();
                
    }
    
    /**
     * perform any final operations to the stream.  This 
     * DOES NOT close the stream!
     * @param out 
     */
    @Override
    public void finalize( OutputStream out ) {
        
    } 
    
}
