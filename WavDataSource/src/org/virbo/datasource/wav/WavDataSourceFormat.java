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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.das2.datum.LoggerManager;
import org.das2.datum.Units;
import org.das2.datum.UnitsConverter;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.bufferdataset.BufferDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.QubeDataSetIterator;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.DataSourceFormat;
import org.virbo.dsops.Ops;
import org.virbo.dsutil.DataSetBuilder;

/**
 * Format data to binary wav file.  The wav file format contains metadata in the first bytes, 
 * and then the data as interleaved channels.  Java AudioFormat is used to format the header,
 * and the BinaryDataSource is used to format the rest of the wav file.
 * @author jbf
 */
public class WavDataSourceFormat implements DataSourceFormat {

    private static final Logger logger= LoggerManager.getLogger("apdss.wav");
    
    private ByteBuffer formatRank1(QDataSet data, ProgressMonitor mon, Map<String, String> params) {

        String type = params.get("type");
        boolean doscale= !"F".equals( params.get("scale") );
                
        QDataSet extent= Ops.extent(data);
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
        
        double shift= 0;
        boolean unsigned= type.startsWith("u");
        long typeOrdinals= (int)Math.pow(2,8*typeSize);
        int limit= (int)( typeOrdinals / ( unsigned ? 1 : 2 ) );
        if ( extent.value(1)>limit ) {
            if ( ( extent.value(1)-extent.value(0) ) < typeOrdinals ) {
                if ( extent.value(0)>0 ) {
                    shift= typeOrdinals / 2;
                } else {
                    shift= ( extent.value(1)+extent.value(0) ) / 2;
                }
            } else {
                if ( !doscale ) throw new IllegalArgumentException("data extent is too great: "+extent);
            }
        }

        double scale= 1.0;
        if ( doscale ) {
            shift= ( extent.value(1)+extent.value(0) ) / 2;
            if ( ( extent.value(1)-extent.value(0) )>0 ) {
                scale= typeOrdinals / ( extent.value(1)-extent.value(0) );
            }
        }

        QubeDataSetIterator it = new QubeDataSetIterator(data);
        while (it.hasNext()) {
            it.next();
            it.putValue(ddata, scale * ( it.getValue(data)-shift ) );
        }

        return result;
    }

    /**
     * format the data in the waveform scheme: rank 2, DEPEND_0 is the waveform packet timetags, 
     * DEPEND_1 is the timetag offsets.
     * @param data rank 2 waveform
     * @param mon
     * @param params
     * @return 
     */
    private ByteBuffer formatRank2Waveform(QDataSet data, ProgressMonitor mon, Map<String, String> params) {

        String type = params.get("type");
        boolean doscale= !"F".equals( params.get("scale") );
        
        if ( !DataSetUtil.isQube(data) ) {
            throw new IllegalArgumentException("data must be qube");
        }
        
        // cull records that are not monotonically increasing
        QDataSet dep0= (QDataSet) data.property(QDataSet.DEPEND_0);
        if ( dep0!=null ) {
            DataSetBuilder b= new DataSetBuilder(1,dep0.length());
            double t0= dep0.value(0);
            b.putValue(b.getLength(),0);
            for ( int i=1; i<dep0.length(); i++ ) {
                if ( dep0.value(i)>t0 ) {
                    b.putValue(b.getLength(),i);
                    t0= dep0.value(i);
                }
            }
            QDataSet r= b.getDataSet();
            if ( r.length()<dep0.length() ) {
                logger.warning("timetags are not monotonic");
                data= DataSetOps.applyIndex( data, 0, r, false );
            }
        }
        
        
        QDataSet extent= Ops.extent(data);
        int dep0Len = 0; //(dep0 == null ? 0 : 1);
        int typeSize = BufferDataSet.byteCount(type);
        int recSize = typeSize * (dep0Len + 1);
        int size = data.length() * data.length(0) * recSize;

        ByteBuffer result = ByteBuffer.allocate(size);
        result.order("big".equals(params.get("byteOrder")) ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

        BufferDataSet ddata = BufferDataSet.makeDataSet(1,
                recSize, dep0Len * typeSize,
                data.length() * data.length(0), 1, 1, 1,
                result, type);

        double shift= 0; // shift is essentially the D/C part.
        boolean unsigned= type.startsWith("u");
        long typeOrdinals= (int)Math.pow(2,8*typeSize);
        int limit= (int)( typeOrdinals / ( unsigned ? 1 : 2 ) );
        if ( extent.value(1)>limit ) {
            if ( ( extent.value(1)-extent.value(0) ) < typeOrdinals ) {
                if ( extent.value(0)>0 ) {
                    shift= typeOrdinals / 2;
                } else {
                    shift= ( extent.value(1)+extent.value(0) ) / 2;
                }
            } else {
                if ( !doscale ) throw new IllegalArgumentException("data extent is too great: "+extent);
            }
        }

        double scale= 1.0;
        if ( doscale ) {
            shift= ( extent.value(1)+extent.value(0) ) / 2;
            if ( ( extent.value(1)-extent.value(0) )>0 ) {
                scale= typeOrdinals / ( extent.value(1)-extent.value(0) );
            }
        }

        QubeDataSetIterator it = new QubeDataSetIterator(data);
        QubeDataSetIterator it2= new QubeDataSetIterator(ddata);
        while (it.hasNext()) {
            it.next();
            it2.next();
            it2.putValue( ddata, scale * ( it.getValue(data)-shift ) );
        }

        return result;
    }

    /**
     * format rank 2 bundle of waveforms.  E.g. stereo[time,2] or quadraphonic[time,4]
     * @param data
     * @param mon
     * @param params
     * @return
     */
    private ByteBuffer formatRank2(QDataSet data, ProgressMonitor mon, Map<String, String> params) {

        String type = params.get("type");
        boolean doscale= !"F".equals( params.get("scale") );
        
        QDataSet extent= Ops.extent(data);
        int dep0Len = 0;
        int typeSize = BufferDataSet.byteCount(type);
        int channels= data.length(0);
        int recSize = typeSize * channels;
        int size = data.length() * recSize;

        ByteBuffer result = ByteBuffer.allocate(size);
        result.order("big".equals(params.get("byteOrder")) ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

        BufferDataSet ddata = BufferDataSet.makeDataSet( 2,
                recSize, dep0Len * typeSize,
                data.length(), data.length(0), 1, 1,
                result, type);

        QubeDataSetIterator it = new QubeDataSetIterator(data);

        double shift= 0; // shift is essentially the D/C part.
        boolean unsigned= type.startsWith("u");
        long typeOrdinals= (int)Math.pow(2,8*typeSize);
        int limit= (int)( typeOrdinals / ( unsigned ? 1 : 2 ) );
        if ( extent.value(1)>limit ) {
            if ( ( extent.value(1)-extent.value(0) ) < typeOrdinals ) {
                if ( extent.value(0)>0 ) {
                    shift= typeOrdinals / 2;
                } else {
                    shift= ( extent.value(1)+extent.value(0) ) / 2;
                }
            } else {
                if ( !doscale ) throw new IllegalArgumentException("data extent is too great: "+extent);
            }
        }
        
        double scale= 1.0;
        if ( doscale ) {
            shift= 0; // TODO: this is inconsistent with other branches.
            if ( ( extent.value(1)-extent.value(0) )>0 ) {
                scale= typeOrdinals / ( extent.value(1)-extent.value(0) );
            }
        }
        
        while (it.hasNext()) {
            it.next();
            it.putValue( ddata,  scale * ( it.getValue(data)-shift ) );
        }

        return result;
    }

    /**
     * Returns an input stream for a ByteBuffer.
     * The read() methods use the relative ByteBuffer get() methods.
     * from http://www.exampledepot.com/egs/java.nio/Buffer2Stream.html
     * @param buf the buffer
     * @return the InputStream.
     */
    private static InputStream newInputStream(final ByteBuffer buf) {
        return new InputStream() {

            public synchronized int read() throws IOException {
                if (!buf.hasRemaining()) {
                    return -1;
                }
                return buf.get();
            }

            @Override
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


        if ( SemanticOps.isRank2Waveform(data) ) {
            QDataSet dep1= (QDataSet) data.property(QDataSet.DEPEND_1);
            if ( dep1==null || dep1.length()<2 ) {
                throw new IllegalArgumentException("dep1 length must be at least 2");
            }
            Units u= (Units) dep1.property( QDataSet.UNITS ) ;
            if ( u==null ) {
                u= Units.dimensionless;
            }
            UnitsConverter uc= u.getConverter( Units.seconds );
            double periodSeconds= uc.convert( dep1.value(1) - dep1.value(0) );

            samplesPerSecond= (float) Math.round( 1/periodSeconds );

        } else {
            if ( dep0!=null && dep0.length()>1 ) {
                Units u= (Units) dep0.property( QDataSet.UNITS ) ;
                if ( u==null ) {
                    u= Units.dimensionless;
                } else {
                    u= u.getOffsetUnits();  // allow for datasets with timetags.
                }

                UnitsConverter uc= u.getConverter( Units.seconds );
                double periodSeconds= uc.convert( dep0.value(1) - dep0.value(0) );

                samplesPerSecond= (float) Math.round( 1 / periodSeconds );
            } else {
                throw new IllegalArgumentException("dep0 length must be at least 2");
            }
        }
        
        int channels= 1;

        switch (data.rank()) {
            case 1:
                break;
            case 2:
                if ( SemanticOps.isRank2Waveform(data) ) {
                    // rank 2 waveforms cannot be used to produce stereo.
                } else {
                    channels= (int) data.length(0);
                }   break;
            default:
                throw new IllegalArgumentException("only rank 1 and rank 2 datasets supported");
        }

        Map<String, String> params2 = new HashMap<>();
        params2.put("type", "short"); // only short and ushort.  short is default.
        params2.put("byteOrder","little");

        int bytesPerField;
        
        params2.putAll( URISplit.parseParams( split.params ) );

        Set<String> allowedTypes= new HashSet<>();    
        allowedTypes.add("ushort");
        allowedTypes.add("short");
        allowedTypes.add("int");
        allowedTypes.add("int24");
        
        String type= params2.get("type");
        
        if ( !allowedTypes.contains(type) ) {
            throw new IllegalArgumentException("type must be one of: "+allowedTypes );
        }
        
        bytesPerField= BufferDataSet.byteCount(type);
        
        boolean signed;
        signed = !type.startsWith("u");
        
        boolean bigEndian= params2.get("byteOrder").equals("big");

        String stimeScale= params2.get("timeScale");
        double timeScale;
        if ( stimeScale!=null ) {
            timeScale= Double.parseDouble(stimeScale);
            samplesPerSecond= (float)( samplesPerSecond*timeScale );
        }
                
        AudioFormat outDataFormat;
        outDataFormat= new AudioFormat((float) samplesPerSecond, (int) bytesPerField*8, channels, signed, bigEndian );


        ByteBuffer buf;
        switch (data.rank()) {
            case 1:
                buf= formatRank1(data, new NullProgressMonitor(), params2);
                break;
            case 2:
                if ( SemanticOps.isRank2Waveform(data) ) {
                    buf= formatRank2Waveform( data, new NullProgressMonitor(), params2 );
                } else {
                    buf= formatRank2(data, new NullProgressMonitor(), params2);
                }   break;
            default:
                throw new IllegalArgumentException("only rank 1 and rank 2 datasets supported");
        }

        AudioInputStream inFileAIS = new AudioInputStream( newInputStream(buf), outDataFormat, buf.capacity()/(bytesPerField*channels) );

        File outFile=  new File( split.resourceUri );
        
        if ( AudioSystem.isFileTypeSupported( AudioFileFormat.Type.WAVE, inFileAIS) ) {
            // inFileAIS can be converted to AIFF.
            // so write the AudioInputStream to the
            // output file.
            int i= AudioSystem.write( inFileAIS, AudioFileFormat.Type.WAVE, outFile );
            logger.log( Level.FINE, "{0} bytes written to file.", i);
            inFileAIS.close();

        } else {
            throw new IllegalArgumentException("System doesn't support format to WAVE");
        }

    }

    public boolean canFormat(QDataSet ds) {
        return ds.rank()==1 || ( ds.rank()==2 && ( SemanticOps.isRank2Waveform(ds) || ds.length(0)<16 ) ); //16 channels
    }

    public String getDescription() {
        return "WAVE audio";
    }
}
