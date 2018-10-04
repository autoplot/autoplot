
package test.endtoend;

import java.io.File;
//import java.io.FileInputStream;
//import java.io.FileNotFoundException;
//import java.io.IOException;
//import java.util.logging.Level;
//import java.util.logging.LogManager;
//import java.util.logging.Logger;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.qds.MutablePropertyDataSet;
import org.das2.qds.QDataSet;
import static org.das2.qds.ops.Ops.*;
import static org.autoplot.ScriptContext.*;
import org.autoplot.datasource.AutoplotSettings;
import org.das2.qds.ops.Ops;
import org.autoplot.jythonsupport.Util;
//import org.das2.util.LoggerManager;

/**
 * Test Autoplot
 * @author jbf
 */
public class Test001 {
    public static void main(String[] args)  {
        try {
            
//            try {
//                LogManager.getLogManager().readConfiguration( new FileInputStream("/home/jbf/autoplot_data/config/logging.properties") );
//                LoggerManager.getLogger("autoplot.script").fine("Hello");
//            } catch (FileNotFoundException ex) {
//                Logger.getLogger(Test001.class.getName()).log(Level.SEVERE, null, ex);
//            } catch (IOException | SecurityException ex) {
//                Logger.getLogger(Test001.class.getName()).log(Level.SEVERE, null, ex);
//            }
//            
            System.err.println("pwd: "+(new File(".")).getCanonicalPath());
            System.err.println("autoplot_data: "+AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA) );
            System.err.println("fscache: "+AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_FSCACHE) );
                    
            getDocumentModel().getOptions().setAutolayout(false);
            getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");
            
            long t0;
            QDataSet ds;
               
            System.err.println( "--- test001_001 ---" );
            t0= System.currentTimeMillis();
            ds= Util.getDataSet( "http://autoplot.org/data/fireworks.wav" );
            plot( 0, ds );
            plot( 1, fftWindow( ds, 512 ) );
            setCanvasSize( 800, 600 );
            writeToPng( "test001_001.png" );
            System.err.println( String.format( "test001_001 completed in %d ms", System.currentTimeMillis()-t0 ) );
            
            System.err.println( "--- test001_004 ---" );
            t0= System.currentTimeMillis();
            ds= Util.getDataSet( "http://autoplot.org/data/fireworks.wav" );
            plot( 0, ds );
            plot( 1, fftPower( ds, Ops.windowFunction( FFTFilterType.Hanning, 1024 ), 2, new NullProgressMonitor() ) );
            setCanvasSize( 800, 600 );
            writeToPng( "test001_004.png" );
            System.err.println( String.format( "test001_004 completed in %d ms", System.currentTimeMillis()-t0 ) );            
                  
            
            System.err.println( "--- test001_002 ---" );
            t0= System.currentTimeMillis();
            ds= Util.getDataSet( TestSupport.TEST_DATA+"xls/2008-lion and tiger summary.xls?sheet=Samantha+tiger+lp+lofreq&firstRow=53&column=Complex_Modulus&depend0=Frequency" );
            plot( 0, "xaxis should be log", ds );

            MutablePropertyDataSet mds= (MutablePropertyDataSet) findgen(50,50);
            MutablePropertyDataSet xds= (MutablePropertyDataSet) exp10( linspace( 0, 2, 50 ) );
            xds.putProperty( QDataSet.SCALE_TYPE, "log" );
            MutablePropertyDataSet yds= (MutablePropertyDataSet) exp10( linspace( 0, 5, 50 ) );
            yds.putProperty( QDataSet.SCALE_TYPE, "log" );
            mds.putProperty( QDataSet.DEPEND_0, xds );
            mds.putProperty( QDataSet.DEPEND_1, yds );
            plot( 1, "log-log spectrogram", mds );

            setCanvasSize( 800, 600 );
            writeToPng( "test001_002.png" );
            System.err.println( String.format( "test001_002 completed in %d ms", System.currentTimeMillis()-t0 ) );            

            reset();
            System.err.println( "--- test001_003 ---" );
            plot( TestSupport.TEST_DATA+"xls/2008-lion and tiger summary.xls?sheet=Samantha+tiger+lp+lofreq&firstRow=53&column=Complex_Modulus&depend0=Frequency" );
            // this causes bad things as of 2009-08-12.
            plot( TestSupport.TEST_DATA+"qds/hist2.qds" );
            writeToPng( "test001_003.png" );
            System.err.println( String.format( "test001_003 completed in %d ms", System.currentTimeMillis()-t0 ) );

            System.exit(0);  // TODO: something is firing up the event thread
        } catch ( Exception ex ) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
