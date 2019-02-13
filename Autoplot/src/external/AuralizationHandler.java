
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
 * experiment to see
 * @author jbf
 */
public class AuralizationHandler extends Handler {

    QDataSet ds;
    Map<Pattern,QDataSet> sounds;
    
    public AuralizationHandler() throws Exception {
        try {
            
            sounds= new LinkedHashMap<>();
            sounds.put( Pattern.compile("HEAD.*"), 
                    new WavDataSource2( new URI("file:///home/jbf/fun/sounds/dinkq.wav") ).getDataSet(new NullProgressMonitor() ) );
            sounds.put( Pattern.compile("GET.*"), 
                    new WavDataSource2( new URI("file:///home/jbf/fun/sounds/ding2.wav") ).getDataSet(new NullProgressMonitor() ) );
           
        } catch (URISyntaxException ex) {
            Logger.getLogger(AuralizationHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
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
