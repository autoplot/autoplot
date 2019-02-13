
package external;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;
import org.das2.graph.Auralizor;
import org.das2.qds.QDataSet;

/**
 * experiment to see if auralization is useful with log messages.  This will make
 * one of two sounds when HEAD or GET is found within the log message.
 * <pre>
 * from org.das2.util import LoggerManager
 * t= LoggerManager.getLogger('das2.url')
 * from external import AuralizationHandler
 * handler= AuralizationHandler()
 * handler.addSound( 'HEAD.*', getDataSet( 'http://jfaden.net/~jbf/autoplot/data/wav/dinkq.wav' ) )
 * handler.addSound( 'GET.*', getDataSet( 'http://jfaden.net/~jbf/autoplot/data/wav/ding2.wav' ) )
 * t.addHandler( handler )
 * from java.util.logging import Level
 * t.setLevel( Level.FINER )
 * t.info('GET test:')
 * sleep(2000)
 * getDataSet( 'http://jfaden.net/~jbf/autoplot/data/events/colorEvents.txt' )
 * </pre>
 * 
 * @author jbf
 */
public class AuralizationHandler extends Handler {

    Map<Pattern,QDataSet> sounds;
    
    public AuralizationHandler() throws Exception {
        sounds= new LinkedHashMap<>();
           
    }
    
    /**
     * auralize the sound when the pattern is encountered.
     * @param regex the regex, for example 'HEAD.*'
     * @param s the sound, for example getDataSet("http://jfaden.net/~jbf/autoplot/data/wav/dinkq.wav")
     */
    public void addSound( String regex, QDataSet s ) {
        sounds.put( Pattern.compile(regex), s );
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
