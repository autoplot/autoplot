/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource.wav;

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
import org.virbo.binarydatasource.BinaryDataSource;
import org.virbo.binarydatasource.BufferDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.TagGenDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.URISplit;

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

        // leave 64 bytes for the header.  The wav file format is much more complex than this.  See http://www.sonicspot.com/guide/wavefiles.html
        int headerLength= 64;

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

        if ( audioFormat.getEncoding()== Encoding.PCM_SIGNED ) {
            if ( bits==32 ) {
                type= "int";
            } else if ( bits==16 ) {
                type= "short";
            } else if ( bits==8 ) {
                type= "byte";
            }
        } else {
            if ( bits==32 ) {
                type= "uint";
            } else if ( bits==16 ) {
                type= "ushort";
            } else if ( bits==8 ) {
                type= "ubyte";
            }
        }

        Map<String,String> params= new HashMap<String,String>();
        params.put( "byteOffset", ""+byteOffset );
        params.put( "byteLength", ""+byteLength );
        params.put( "recLength", ""+ frameSize );
        params.put( "recOffset", ""+ ( channel*bits/8) );
        params.put( "type", type );
        params.put( "byteOrder", byteOrder );

        URL lurl= new URL( ""+wavFile.toURI().toURL() + "?" + URISplit.formatParams(params) );

        BinaryDataSource bds= new BinaryDataSource( lurl.toURI() );
        MutablePropertyDataSet result= (BufferDataSet) bds.getDataSet( new NullProgressMonitor() );

        MutablePropertyDataSet timeTags= new TagGenDataSet( frameCount, 1./audioFormat.getSampleRate(), 0., Units.seconds );
        result.putProperty( QDataSet.DEPEND_0, timeTags );

        return result;

    }

    @Override
    public Map<String,Object> getMetadata(ProgressMonitor mon) throws Exception {
        AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(resourceURI.toURL());
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
