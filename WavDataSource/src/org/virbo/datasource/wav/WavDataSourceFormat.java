/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.datasource.wav;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;
import java.util.Map;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.spi.AudioFileWriter;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.binarydatasource.BufferDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.QubeDataSetIterator;
import org.virbo.datasource.datasource.DataSourceFormat;

/**
 * Format data to binary file.
 * @author jbf
 */
public class WavDataSourceFormat implements DataSourceFormat {

    private ByteBuffer formatRank1(QDataSet data, ProgressMonitor mon, Map<String, String> params) {

        QDataSet dep0 = null;
        
        String type = params.get("type");
        if (type == null) {
            type = "double";
        }

        int dep0Len = (dep0 == null ? 0 : 1);
        int typeSize = BufferDataSet.byteCount(type);
        int recSize = typeSize * (dep0Len + 1);
        int size = data.length() * recSize;

        ByteBuffer result = ByteBuffer.allocate(size);
        result.order("big".equals(params.get("byteOrder")) ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

        BufferDataSet ddata = BufferDataSet.makeDataSet(1,
                recSize, dep0Len * typeSize,
                data.length(), 1, 1,
                result, type);

        QubeDataSetIterator it = new QubeDataSetIterator(data);

        while (it.hasNext()) {
            it.next();
            it.putValue(ddata, it.getValue(data));
        }

        if (dep0 != null) {
            BufferDataSet ddep0 = BufferDataSet.makeDataSet(1,
                    recSize, 0 * typeSize,
                    data.length(), 1, 1,
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

    public void formatData(File url, java.util.Map<String, String> params, QDataSet data, ProgressMonitor mon) throws IOException {

        AudioFormat outDataFormat = new AudioFormat((float) 8000.0, (int) 16, (int) 1, true, false);

        Map<String, String> params2 = new HashMap<String, String>();
        params2.put("type", "short");
        params2.put("byteOrder","little");

        ByteBuffer buf = formatRank1(data, new NullProgressMonitor(), params2);

        AudioInputStream inFileAIS = new AudioInputStream( newInputStream(buf), outDataFormat, buf.capacity() );

        File outFile= url;
        
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
