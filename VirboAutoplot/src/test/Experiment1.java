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
    
    private static double speed;
    
    public static void main( String[] args ) throws InterruptedException {
        int size = 3;
        double[] speedArray2 = new double[size];
        double[] speedArray4 = new double[size];
        for( int i = 0; i< size ; i = i + 1) {
            doTwoThreads();
            speedArray2[i] = speed;
        }
        for( int i = 0; i< size ; i = i + 1) {
            doFourThreads();
            speedArray4[i] = speed;
        }
        for( int i = 0; i< size ; i = i + 1) {
            System.err.println("speedArray2[" + i + "]=" + speedArray2[i] + "         speedArray4[" + i + "]=" + speedArray4[i]);
        }
    }

    private static void doTwoThreads() throws InterruptedException {
        //ScriptContext.createGui();
        final QDataSet ds= Ops.ripplesWaveformTimeSeries(20000);

        final ProgressMonitor mon0= getMonitor("original task");
        long t0 = System.currentTimeMillis();
        QDataSet out= Ops.fftPower(ds, 512, mon0 );
        
        while (!mon0.isFinished()) {
            Thread.sleep(200);
        }
        long time = System.currentTimeMillis() - t0;
        System.err.println("Time for original task: " + time);
        
        ScriptContext.setLayout(3,1);
        
        ScriptContext.plot( 0, out );
        
        final ProgressMonitor mon1= getMonitor("task 1");
        final ProgressMonitor mon2= getMonitor("task 2");
                
        Runnable run1= new Runnable() {
            @Override
            public void run() {
                try {
                    QDataSet out1= Ops.fftPower(ds.trim(0,10000), 512, mon1 );
                    ScriptContext.plot( 1, out1 );
                } catch ( Exception ex) {
                    ex.printStackTrace();
                }
            }
        };
        
        Runnable run2= new Runnable() {
            @Override
            public void run() {
                try {
                    QDataSet out2= Ops.fftPower(ds.trim(10000,20000), 512, mon2 );
                    ScriptContext.plot( 2, out2 );
                } catch ( Exception ex ) {
                    ex.printStackTrace();
                }
            }
        };
        
        t0 = System.currentTimeMillis();
        new Thread(run1).start();
        
        new Thread(run2).start();
        
        while( !mon1.isFinished() && !mon2.isFinished() ) {
            Thread.sleep(200);
        }
        long time1 = System.currentTimeMillis() - t0;
        speed = ((double) time) / time1;
        //System.err.println("Time for threaded task: " + time1);
        //System.err.println(speed + "x faster");
        //System.err.println("-------------------------------------------");
    }
    
    private static void doFourThreads() throws InterruptedException {
        //ScriptContext.createGui();
        final QDataSet ds= Ops.ripplesWaveformTimeSeries(20000);

        final ProgressMonitor mon0= getMonitor("original task");
        long t0 = System.currentTimeMillis();
        QDataSet out= Ops.fftPower(ds, 512, mon0 );
        
        while (!mon0.isFinished()) {
            Thread.sleep(200);
        }
        long time = System.currentTimeMillis() - t0;
        System.err.println("Time for original task: " + time);
        
        ScriptContext.setLayout(3,1);
        
        ScriptContext.plot( 0, out );
        
        final ProgressMonitor mon1= getMonitor("task 1");
        final ProgressMonitor mon2= getMonitor("task 2");
        final ProgressMonitor mon3= getMonitor("task 3");
        final ProgressMonitor mon4= getMonitor("task 5");
                
        Runnable run1= new Runnable() {
            @Override
            public void run() {
                try {
                    QDataSet out1= Ops.fftPower(ds.trim(0,5000), 512, mon1 );
                    ScriptContext.plot( 1, out1 );
                } catch ( Exception ex) {
                    ex.printStackTrace();
                }
            }
        };
        
        Runnable run2= new Runnable() {
            @Override
            public void run() {
                try {
                    QDataSet out2= Ops.fftPower(ds.trim(5000,10000), 512, mon2 );
                    ScriptContext.plot( 2, out2 );
                } catch ( Exception ex ) {
                    ex.printStackTrace();
                }
            }
        };
        
        Runnable run3= new Runnable() {
            @Override
            public void run() {
                try {
                    QDataSet out3= Ops.fftPower(ds.trim(5000,10000), 512, mon3 );
                    ScriptContext.plot( 3, out3 );
                } catch ( Exception ex ) {
                    ex.printStackTrace();
                }
            }
        };
        
        Runnable run4= new Runnable() {
            @Override
            public void run() {
                try {
                    QDataSet out4= Ops.fftPower(ds.trim(5000,10000), 512, mon4 );
                    ScriptContext.plot( 4, out4 );
                } catch ( Exception ex ) {
                    ex.printStackTrace();
                }
            }
        };
        
        t0 = System.currentTimeMillis();
        new Thread(run1).start();
        new Thread(run2).start();
        new Thread(run3).start();
        new Thread(run4).start();
        
        while( !mon1.isFinished() && !mon2.isFinished() && !mon3.isFinished() && !mon4.isFinished() ) {
            Thread.sleep(200);
        }
        long time1 = System.currentTimeMillis() - t0;
        speed = ((double) time) / time1;
        //System.err.println("Time for threaded task: " + time1);
        //System.err.println(speed + "x faster");
        //System.err.println("-------------------------------------------");
    }
}
