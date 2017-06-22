/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.wav;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;

/**
 *
 * @author jbf
 */
public class WavDataSourceFactory extends AbstractDataSourceFactory {

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new WavDataSource2(uri);
    }

    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc,org.das2.util.monitor.ProgressMonitor mon) throws IOException, UnsupportedAudioFileException  {
        List<CompletionContext> result= new ArrayList<CompletionContext>();
        if ( cc.context.equals(CompletionContext.CONTEXT_PARAMETER_NAME ) ) {
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "offset=", "offset in seconds" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "length=", "length in seconds" ) );
            result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_NAME, "channel=", "channel number" ) );
        } else if ( cc.context.equals(CompletionContext.CONTEXT_PARAMETER_VALUE ) ) {
            String paramName= CompletionContext.get( CompletionContext.CONTEXT_PARAMETER_NAME, cc );
            if ( paramName.equals("channel") ) {
                int channels= getChannels(cc.resourceURI, mon);
                for ( int i=0; i<channels; i++ ) {
                    result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, ""+i ) );
                }
            } else {
                result.add( new CompletionContext( CompletionContext.CONTEXT_PARAMETER_VALUE, "<double>" ) );
            }
        }
        return result;
    }

    private int getChannels( URI uri, ProgressMonitor mon  ) throws IOException, UnsupportedAudioFileException {
        File wavFile = DataSetURI.getFile(uri, mon);

        AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(wavFile);
        AudioFormat audioFormat = fileFormat.getFormat();

        return audioFormat.getChannels();
    }
   
}
