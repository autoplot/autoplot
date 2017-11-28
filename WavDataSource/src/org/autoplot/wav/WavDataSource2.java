/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.wav;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioSystem;
import org.das2.datum.Units;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.binarydatasource.BinaryDataSource;
import org.das2.qds.buffer.BufferDataSet;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.TagGenDataSet;
import org.autoplot.datasource.AbstractDataSource;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.URISplit;

/**
 * This version of the DataSource works by wrapping BinaryDataSource.  It
 * reads in the wav header, then creates a URI for the BinaryDataSource.
 * @author jbf
 */
public class WavDataSource2 extends AbstractDataSource {

    public WavDataSource2(URI uri) {
        super(uri);
    }

    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {
        File wavFile = DataSetURI.getFile(this.resourceURI, mon);

        AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(wavFile);
        AudioFormat audioFormat = fileFormat.getFormat();

        // See http://www.topherlee.com/software/pcm-tut-wavformat.html which says the header is 44 bytes.
        int headerLength= 44;

        int frameSize = audioFormat.getFrameSize();
        int frameCount = (int) ( ( wavFile.length() - headerLength ) / frameSize );
        int bits = audioFormat.getSampleSizeInBits();
        int frameOffset=0;

        if ( params.get("offset")!=null ) {
            double offsetSeconds= Double.parseDouble( params.get( "offset" ) );
            frameOffset= (int) Math.floor( offsetSeconds * audioFormat.getSampleRate() );
            frameCount-= frameOffset;
        }

        if ( params.get("length")!=null ) {
            double lengthSeconds= Double.parseDouble( params.get( "length" ) );
            int frameCountLimit= (int) Math.floor( lengthSeconds * audioFormat.getSampleRate() );
            frameCount= Math.min( frameCount, frameCountLimit );
        }

        int channel;
        if ( params.get("channel")!=null ) {
            channel= Integer.parseInt(params.get("channel"));
        } else {
            channel= 0;
        }

        int byteOffset= headerLength + frameOffset * frameSize;
        int byteLength= frameCount * frameSize;
        String byteOrder= audioFormat.isBigEndian() ? "big" : "little";
        String type=null;

        switch (bits) {
            case 32:
                type= "int";
                break;
            case 24:
                type= "int24";
                break;
            case 16:
                type= "short";
                break;
            case 8:
                type= "byte";
                break;
            default:
                throw new IllegalArgumentException("number of bits not supported: "+bits );
        }

        if ( audioFormat.getEncoding()==Encoding.PCM_UNSIGNED ) {
            type = "u" + type;
        }

        Map<String,String> lparams= new HashMap<>();
        lparams.put( "byteOffset", ""+byteOffset );
        lparams.put( "byteLength", ""+byteLength );
        lparams.put( "recLength", ""+ frameSize );
        lparams.put( "recOffset", ""+ ( channel*bits/8) );
        lparams.put( "type", type );
        lparams.put( "byteOrder", byteOrder );

        URL lurl= new URL( ""+wavFile.toURI().toURL() + "?" + URISplit.formatParams(lparams) );

        BinaryDataSource bds= new BinaryDataSource( lurl.toURI() );
        MutablePropertyDataSet result= (BufferDataSet) bds.getDataSet( new NullProgressMonitor() );
        
        MutablePropertyDataSet timeTags= new TagGenDataSet( frameCount, 1./audioFormat.getSampleRate(), 0., Units.seconds );
        result.putProperty( QDataSet.DEPEND_0, timeTags );

        return result;

    }

    @Override
    public Map<String,Object> getMetadata(ProgressMonitor mon) throws Exception {
        File f= getFile(resourceURI,mon);
        AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(f);
        AudioFormat audioFormat= fileFormat.getFormat();
        Map<String,Object> properties= new HashMap( audioFormat.properties() );
        properties.put( "encoding", audioFormat.getEncoding() );
        properties.put( "endianness", audioFormat.isBigEndian()? "bigEndian" : "littleEndian" );
        properties.put( "channels", audioFormat.getChannels() );
        properties.put( "frame rate", audioFormat.getFrameRate() );
        properties.put( "bits", audioFormat.getSampleSizeInBits() );
        return properties;
    }

}
