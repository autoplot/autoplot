/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.components.DasProgressPanel;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.autoplot.ScriptContext;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.examples.Schemes;
import org.virbo.dsops.Ops;

/**
 *
 * @author mmclouth
 */
public class Experiment1 {
    
    private static ProgressMonitor getMonitor(String label) {
        DasProgressPanel p= DasProgressPanel.createFramed(label);
        p.setVisible(true);
        return p;
        
    }
    
    public static void main( String[] args ) throws InterruptedException {
        ScriptContext.createGui();
        final QDataSet ds= Ops.ripplesWaveformTimeSeries(40000);

        QDataSet out= Ops.fftPower(ds, 512, getMonitor("original task") );
        
        ScriptContext.plot( 1, out );
        
        final ProgressMonitor mon1= getMonitor("task 1");
        final ProgressMonitor mon2= getMonitor("task 2");
                
        Runnable run1= new Runnable() {
            @Override
            public void run() {
                try {
                    QDataSet out1= Ops.fftPower(ds.trim(0,10), 512, mon1 );
                    ScriptContext.plot( 2, out1 );
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        };
        
        Runnable run2= new Runnable() {
            @Override
            public void run() {
                try {
                    QDataSet out2= Ops.fftPower(ds.trim(10,20), 512, mon2 );
                    ScriptContext.plot( 3, out2 );
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        };
        
        new Thread(run1).start();
        
        new Thread(run2).start();
        
        while( !mon1.isFinished() && !mon2.isFinished() ) {
            Thread.sleep(200);
        }
        
        System.err.println("here");
        
    }
}
