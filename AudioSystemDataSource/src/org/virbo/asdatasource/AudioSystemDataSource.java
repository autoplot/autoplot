/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.asdatasource;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.binarydatasource.BufferDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.capability.Updating;
import org.virbo.dsops.Ops;

/**
 * provide a dataset by grabbing data from the desktop's audio system.
 * @author jbf
 */
public class AudioSystemDataSource extends AbstractDataSource implements Updating {
    public static final double LEN_SECONDS = 1.0;
    public static final int SAMPLE_RATE = 8000; // SAMPLE_RATE * SAMPLE_LENGTH_SEC should be multiple of BUFSIZE

    public AudioSystemDataSource( URI uri ) {
        super(uri);
        String sspec= (String) getParams().get("spec");
        if ( sspec!=null ) spec= Integer.parseInt(sspec);
        addCability( Updating.class, this );
    }
    
    ByteBuffer dataBuffer;
    ReadableByteChannel audioChannel;
    int nsamples;
    int spec=-1;

    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {

        double lenSeconds = LEN_SECONDS;
        nsamples = (int) (lenSeconds * SAMPLE_RATE);
        int len = nsamples * 4;
        int nchannels= 1;
        int bitsPerSample= 16;
        int frameSize= bitsPerSample / ( nchannels * 8 );

        dataBuffer = ByteBuffer.allocateDirect(len);

        TargetDataLine targetDataLine;
        AudioInputStream audioInputStream;

        AudioFormat audioFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                SAMPLE_RATE, bitsPerSample, nchannels, frameSize, SAMPLE_RATE, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);

        targetDataLine = (TargetDataLine) AudioSystem.getLine(info);
        targetDataLine.open(audioFormat);

        audioInputStream = new AudioInputStream(targetDataLine);

        targetDataLine.start();

        audioChannel = Channels.newChannel(audioInputStream);

        mon.setTaskSize(len);
        mon.started();
        dataBuffer.limit( 2048 );
        fillBuffer(mon);

        mon.finished();

        dataBuffer.order( ByteOrder.LITTLE_ENDIAN );

        //startUpdateTimer();
        
        QDataSet ds= BufferDataSet.makeDataSet( 1, 2, 0, nsamples, 1, 1, 1, dataBuffer, BufferDataSet.SHORT );
        if ( spec>-1 ) {
            ds= Ops.fftWindow(ds, spec);
        }
        return ds;
    }

    PropertyChangeSupport pcs= new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    private void fillBuffer(ProgressMonitor mon) throws IllegalArgumentException, IOException {
        System.err.println("fillBuffer"+dataBuffer);
        while (dataBuffer.position() < dataBuffer.capacity()) {
            audioChannel.read(dataBuffer);
            dataBuffer.limit(Math.min(dataBuffer.position() + 2048, dataBuffer.capacity()));
            mon.setTaskProgress(dataBuffer.position());
        }
    }

    //TODO: add updating capability to replace function of code below.
//
//    private void shiftBuffer() throws IOException {
//        System.err.println("shiftBuffer"+dataBuffer);
//        dataBuffer.position( dataBuffer.capacity()/2 );
//        dataBuffer.compact();
//        fillBuffer( new NullProgressMonitor() );
//    }
//
//    private void startUpdateTimer() {
//        Runnable run= new Runnable() {
//            public void run() {
//                while ( true ) {
//                    try {
//                        shiftBuffer();
//                        QDataSet ds= BufferDataSet.makeDataSet( 1, 2, 0, nsamples, 1, 1, dataBuffer, BufferDataSet.SHORT );
//                        if ( spec>-1 ) {
//                           ds= Ops.fftWindow(ds, spec);
//                        }
//                        pcs.firePropertyChange(Updating.PROP_DATASET, null, ds );
//                    } catch ( IOException ex ) {
//                        Logger.getLogger(AudioSystemDataSource.class.getName()).log(Level.SEVERE, null, ex);
//                    }
//                }
//            }
//        };
//        new Thread( run, "updater for " + getURI() ).start();
//    }

}
