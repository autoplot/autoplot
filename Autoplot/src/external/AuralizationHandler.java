
package external;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.autoplot.wav.WavDataSource2;
import org.das2.graph.Auralizor;
import org.das2.qds.QDataSet;
import org.das2.util.monitor.NullProgressMonitor;

/**
 * experiment to see if auralization is useful with log messages.  This will make
 * one of two sounds when HEAD or GET is found within the log message.
 * @author jbf
 */
public class AuralizationHandler extends Handler {

    Map<Pattern,QDataSet> sounds;
    
    public AuralizationHandler() throws Exception {
        sounds= new LinkedHashMap<>();
           
    }
    
    /**
     * auralize the sound when the pattern is encountered.
     * @param p the pattern, for example Pattern.compile("HEAD.*")
     * @param s the sound, for example getDataSet("http://jfaden.net/~jbf/autoplot/data/wav/dinkq.wav")
     */
    public void addSound( Pattern p, QDataSet s ) {
        sounds.put( p, s );
    }
    
    
    @Override
    public void publish(LogRecord record) {
        //String s= record.getMessage();
                
        QDataSet buf=null;
        for ( Entry<Pattern,QDataSet> e: sounds.entrySet() ) {
            if ( e.getKey().matcher(record.getMessage()).matches() ) {
                buf= e.getValue();
                break;
            }
        }
        if ( buf==null ) return;
        
        final Auralizor auralizor= new Auralizor( buf );
        
        auralizor.setScale(false);
        Runnable run= new Runnable() {
            public void run() {
                auralizor.playSound();
            }
        };
        new Thread(run).start();
        
    }

    @Override
    public void flush() {
        
    }

    @Override
    public void close() throws SecurityException {
        
    }
    
}
