
package org.autoplot.hapiserver;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.datum.Units;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.das2.qds.ops.Ops;
import org.das2.qstream.AsciiTimeTransferType;
import org.das2.qstream.TransferType;

/**
 * Format to binary types.  Note that TransferTypes use doubles to communicate,
 * so floating point numbers may not format precisely.
 * @author jbf
 */
public class BinaryDataFormatter implements DataFormatter {
  
    private static final Logger logger= Logger.getLogger("hapi");    
    
    TransferType[] transferTypes;
    double[] fill;
    ByteBuffer b;
    int bufferSize;
    int sentRecordCount;
    
    public BinaryDataFormatter( ) {
    }
        
    @Override
    public void initialize( JSONObject info, OutputStream out, QDataSet record) {
        try {
            transferTypes= new TransferType[record.length()];
            fill= new double[record.length()];
            
            bufferSize= 0;
            
            int totalFields= 0;
            JSONArray parameters= info.getJSONArray("parameters");
            for ( int i=0; i<parameters.length(); i++ ) {
                JSONObject parameter= parameters.getJSONObject(i);
                TransferType tt;
                final String stype = parameter.getString("type");
                double fl=-1;
                Units u= SemanticOps.getUnits(record.slice(i));
                switch (stype) {
                    case "isotime":
                        {
                            if ( !parameter.has("length") ) throw new RuntimeException("required tag length is missing");
                            final int len= parameter.getInt("length");
                            final TransferType delegate= AsciiTimeTransferType.getForName( "time"+(len), Collections.singletonMap(QDataSet.UNITS,(Object)u) );
                            tt= new TransferType() {
                                @Override
                                public void write(double d, ByteBuffer buffer) {
                                    delegate.write(d, buffer);
                                    buffer.put( len-1, (byte)'Z' ); // delegate doesn't put in Z for time24.
                                }
                                @Override
                                public double read(ByteBuffer buffer) {
                                    throw new UnsupportedOperationException("Not supported.");
                                }
                                @Override
                                public int sizeBytes() {
                                    return len;
                                }
                                @Override
                                public boolean isAscii() {
                                    return false;
                                }
                                @Override
                                public String name() {
                                    return "string"+len;
                                }
                            };      break;
                        }
                    case "string":
                        {
                            if ( !parameter.has("length") ) throw new RuntimeException("required tag length is missing"); 
                            final int len= parameter.getInt("length");
                            final Units units= u;
                            final byte[] zeros= new byte[len];
                            for ( int i2=0; i2<zeros.length; i2++ ) zeros[i2]= 0;
                            tt= new TransferType() {
                                @Override
                                public void write(double d, ByteBuffer buffer) {
                                    String s= units.createDatum(d).toString();
                                    byte[] bytes= s.getBytes( Charset.forName("UTF-8") );
                                    if ( bytes.length==len ) {
                                        buffer.put( bytes );
                                    } else if ( bytes.length<len ) {
                                        buffer.put( bytes, 0, bytes.length );
                                        buffer.put( zeros, bytes.length, len-bytes.length );
                                    } else {
                                        bytes= Util.trimUTF8( bytes, len );
                                        buffer.put( bytes, 0, bytes.length );
                                        buffer.put( zeros, bytes.length, len-bytes.length );
                                    }
                                }
                                @Override
                                public double read(ByteBuffer buffer) {
                                    throw new UnsupportedOperationException("Not supported.");
                                }
                                @Override
                                public int sizeBytes() {
                                    return len;
                                }
                                @Override
                                public boolean isAscii() {
                                    return false;
                                }
                                @Override
                                public String name() {
                                    return "string"+len;
                                }
                            };      break;
                        }
                    case "double":
                    case "integer":
                        tt= TransferType.getForName(stype, Collections.singletonMap(QDataSet.UNITS,(Object)u) );
                        fl= Double.parseDouble( parameter.getString("fill") );
                        break;
                    default:
                    throw new IllegalArgumentException("server is misconfigured, using unsupported type: "+stype );
                }
                int nfields;
                if ( parameter.has("size") ) {
                    JSONArray ja= (JSONArray)parameter.get("size");
                    int prod= 1;
                    for ( int j=0; j<ja.length(); j++ ) {
                        prod*= ja.getInt(j);
                    }
                    nfields= prod;
                } else {
                    nfields= 1;
                }
                for ( int j=0; j<nfields; j++ ) {
                    transferTypes[totalFields+j]= tt;
                    fill[totalFields+j]= fl;
                }
                totalFields+= nfields;
                bufferSize+= nfields * tt.sizeBytes();
            }

            b= TransferType.allocate( bufferSize, ByteOrder.LITTLE_ENDIAN );
            
            sentRecordCount=0;
            
        } catch (JSONException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
    
    @Override
    public void sendRecord( OutputStream out, QDataSet record ) throws IOException {

        for ( int i=0; i<record.length(); i++ ) {
            QDataSet d= record.slice(i);
            if ( i>0 && Ops.valid(d).value()==0 ) {
                transferTypes[i].write( fill[i], b );
            } else {
                transferTypes[i].write( d.value(), b );
            }
        }
        byte[] bytes= b.array();
        
        if ( sentRecordCount==0 ) {
            if ( logger.isLoggable(Level.FINE)  ) {
                StringBuilder sbuf;
                sbuf = new StringBuilder();
                int nf= Math.min(80,bytes.length);
                for ( int i=0; i<nf; i++ ) {
                    sbuf.append( String.format( "%2d ", i ) );
                }
                logger.fine( sbuf.toString() );
                sbuf = new StringBuilder();
                for ( int i=0; i<nf; i++ ) {
                    sbuf.append( String.format( "%02x ", bytes[i] ) );
                }
                logger.fine( sbuf.toString() );
            }
        }
        out.write( bytes );
        
        b.flip();
        
        sentRecordCount++;
                
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
