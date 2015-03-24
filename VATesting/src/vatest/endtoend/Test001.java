/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package vatest.endtoend;

import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.lang.System.err;
import static java.lang.System.exit;
import org.das2.util.monitor.NullProgressMonitor;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import static org.virbo.dsops.Ops.*;
import static org.virbo.autoplot.ScriptContext.*;
import static org.virbo.dataset.QDataSet.DEPEND_0;
import static org.virbo.dataset.QDataSet.DEPEND_1;
import static org.virbo.dataset.QDataSet.SCALE_TYPE;
import static org.virbo.dsops.Ops.FFTFilterType.Hanning;
import static org.virbo.jythonsupport.Util.getDataSet;
import static vatest.endtoend.VATestSupport.TEST_DATA;

/**
 * Test Autoplot
 * @author jbf
 */
public class Test001 {
    public static void main(String[] args)  {
        try {
            getDocumentModel().getOptions().setAutolayout(false);
            getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");
            
            long t0;
            
            err.println( "--- test001_001 ---" );
            t0= currentTimeMillis();
            QDataSet ds= getDataSet( "http://www.autoplot.org/data/fireworks.wav" );
            plot( 0, ds );
            plot( 1, fftWindow( ds, 512 ) );
            setCanvasSize( 800, 600 );
            writeToPng( "test001_001.png" );
            err.println(format("test001_001 completed in %d ms", currentTimeMillis()-t0 ) );
            
            err.println( "--- test001_004 ---" );
            t0= currentTimeMillis();
            ds= getDataSet( "http://www.autoplot.org/data/fireworks.wav" );
            plot( 0, ds );
            plot(1, fftPower(ds, windowFunction(Hanning, 1024 ), 2, new NullProgressMonitor() ) );
            setCanvasSize( 800, 600 );
            writeToPng( "test001_004.png" );
            err.println(format("test001_004 completed in %d ms", currentTimeMillis()-t0 ) );            
                  
            
            err.println( "--- test001_002 ---" );
            t0= currentTimeMillis();
            ds= getDataSet(TEST_DATA+"xls/2008-lion and tiger summary.xls?sheet=Samantha+tiger+lp+lofreq&firstRow=53&column=Complex_Modulus&depend0=Frequency" );
            plot( 0, "xaxis should be log", ds );

            MutablePropertyDataSet mds= (MutablePropertyDataSet) findgen(50,50);
            MutablePropertyDataSet xds= (MutablePropertyDataSet) exp10( linspace( 0, 2, 50 ) );
            xds.putProperty(SCALE_TYPE, "log" );
            MutablePropertyDataSet yds= (MutablePropertyDataSet) exp10( linspace( 0, 5, 50 ) );
            yds.putProperty(SCALE_TYPE, "log" );
            mds.putProperty(DEPEND_0, xds );
            mds.putProperty(DEPEND_1, yds );
            plot( 1, "log-log spectrogram", mds );

            setCanvasSize( 800, 600 );
            writeToPng( "test001_002.png" );
            err.println(format("test001_002 completed in %d ms", currentTimeMillis()-t0 ) );            

            reset();
            err.println( "--- test001_003 ---" );
            plot(TEST_DATA+"xls/2008-lion and tiger summary.xls?sheet=Samantha+tiger+lp+lofreq&firstRow=53&column=Complex_Modulus&depend0=Frequency" );
            // this causes bad things as of 2009-08-12.
            plot(TEST_DATA+"qds/hist2.qds" );
            writeToPng( "test001_003.png" );
            err.println(format("test001_003 completed in %d ms", currentTimeMillis()-t0 ) );

            exit(0);  // TODO: something is firing up the event thread
        } catch ( Exception ex ) {
            ex.printStackTrace();
            exit(1);
        }
    }
}
