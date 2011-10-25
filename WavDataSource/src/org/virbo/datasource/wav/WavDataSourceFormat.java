/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.datasource.wav;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.das2.datum.Units;
import org.das2.datum.UnitsConverter;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.binarydatasource.BufferDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.QubeDataSetIterator;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.DataSourceFormat;

/**
 * Format data to binary file.
 * @author jbf
 */
public class WavDataSourceFormat implements DataSourceFormat {

    private ByteBuffer formatRank1(QDataSet data, ProgressMonitor mon, Map<String, String> params) {

        QDataSet dep0 = null;
        
        String type = params.get("type");

        int dep0Len = 0; //(dep0 == null ? 0 : 1);
        int typeSize = BufferDataSet.byteCount(type);
        int recSize = typeSize * (dep0Len + 1);
        int size = data.length() * recSize;

        ByteBuffer result = ByteBuffer.allocate(size);
        result.order("big".equals(params.get("byteOrder")) ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

        BufferDataSet ddata = BufferDataSet.makeDataSet(1,
                recSize, dep0Len * typeSize,
                data.length(), 1, 1, 1,
                result, type);

        QubeDataSetIterator it = new QubeDataSetIterator(data);

        while (it.hasNext()) {
            it.next();
            it.putValue(ddata, it.getValue(data));
        }

        if (dep0 != null) {
            BufferDataSet ddep0 = BufferDataSet.makeDataSet(1,
                    recSize, 0 * typeSize,
                    data.length(), 1, 1, 1,
                    result, type);
            it = new QubeDataSetIterator(dep0);

            while (it.hasNext()) {
                it.next();
                it.putValue(ddep0, it.getValue(dep0));
            }
        }

        return result;
    }

    /**
     * @param data
     * @param mon
     * @param params
     * @return
     */
    private ByteBuffer formatRank2(QDataSet data, ProgressMonitor mon, Map<String, String> params) {

        String type = params.get("type");

        int dep0Len = 0;
        int typeSize = BufferDataSet.byteCount(type);
        int recSize = typeSize * (dep0Len + 1);
        int channels= data.length(0);
        int size = data.length() * recSize * channels;

        ByteBuffer result = ByteBuffer.allocate(size);
        result.order("big".equals(params.get("byteOrder")) ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

        BufferDataSet ddata = BufferDataSet.makeDataSet( 2,
                recSize, dep0Len * typeSize,
                data.length(), data.length(0), 1, 1,
                result, type);

        QubeDataSetIterator it = new QubeDataSetIterator(data);

        while (it.hasNext()) {
            it.next();
            it.putValue(ddata, it.getValue(data));
        }

        return result;
    }

    /**
     * Returns an input stream for a ByteBuffer.
     *The read() methods use the relative ByteBuffer get() methods.
     * from http://www.exampledepot.com/egs/java.nio/Buffer2Stream.html
     */
    public static InputStream newInputStream(final ByteBuffer buf) {
        return new InputStream() {

            public synchronized int read() throws IOException {
                if (!buf.hasRemaining()) {
                    return -1;
                }
                return buf.get();
            }

            public synchronized int read(byte[] bytes, int off, int len) throws IOException {
                // Read only what's left
                len = Math.min(len, buf.remaining());
                buf.get(bytes, off, len);
                return len;
            }
        };
    }

    public void formatData( String uri, QDataSet data, ProgressMonitor mon) throws IOException {

        URISplit split= URISplit.parse(uri);

        QDataSet dep0= (QDataSet) data.property( QDataSet.DEPEND_0 );

        float samplesPerSecond= 8000.0f;


        if ( dep0!=null && dep0.length()>0 ) {
            Units u= (Units) dep0.property( QDataSet.UNITS ) ;
            if ( u==null ) {
                u= Units.dimensionless;
            } else {
                u= u.getOffsetUnits();  // allow for datasets with timetags.
            }

            UnitsConverter uc= u.getConverter( Units.seconds );
            double periodSeconds= uc.convert( dep0.value(1) - dep0.value(0) );

            samplesPerSecond= (float) Math.round( 1 / periodSeconds );
        }

        AudioFormat outDataFormat;
        if ( data.rank()==1 ) {
            outDataFormat= new AudioFormat((float) samplesPerSecond, (int) 16, (int) 1, true, false);
        } else if ( data.rank()==2 ) {
            outDataFormat= new AudioFormat((float) samplesPerSecond, (int) 16, (int) data.length(0), true, false);
        } else {
            throw new IllegalArgumentException("only rank 1 and rank 2 datasets supported");
        }

        Map<String, String> params2 = new HashMap<String, String>();
        params2.put("type", "short");
        params2.put("byteOrder","little");

        ByteBuffer buf;
        if ( data.rank()==1 ) {
            buf= formatRank1(data, new NullProgressMonitor(), params2);
        } else if ( data.rank()==2 ) {
            buf= formatRank2(data, new NullProgressMonitor(), params2);
        } else {
            throw new IllegalArgumentException("only rank 1 and rank 2 datasets supported");
        }

        AudioInputStream inFileAIS = new AudioInputStream( newInputStream(buf), outDataFormat, buf.capacity() );

        File outFile=  new File( split.resourceUri );
        
        if (AudioSystem.isFileTypeSupported(
                AudioFileFormat.Type.WAVE, inFileAIS)) {
            // inFileAIS can be converted to AIFF.
            // so write the AudioInputStream to the
            // output file.
            int i= AudioSystem.write( inFileAIS, AudioFileFormat.Type.WAVE, outFile );

            inFileAIS.close();
            return; // All done now
        } else {
            throw new IllegalArgumentException("System doesn't support format to WAVE");
        }

    }
}
