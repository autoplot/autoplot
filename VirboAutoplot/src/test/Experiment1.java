/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.io.IOException;
import java.util.ArrayList;
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
        int size = 15;
        double[] speedArray2 = new double[size];
        double[] speedArray4 = new double[size];
        double[] speedArrayMulti = new double[size];
        double[] speedArray8 = new double[size];
        
        //for( int i = 0; i< size ; i = i + 1) {
          //  doTwoThreads();
            //speedArray2[i] = speed;
            //System.err.print("Four threads:" + i);
        //}
        for( int i = 0; i< size ; i = i + 1) {
            doEightThreads();
            speedArray8[i] = speed;
            //System.err.print("Four threads:" + i);
        }
        for( int i = 0; i< size ; i = i + 1) {
            fftMultiThread(8);
            speedArrayMulti[i] = speed;
            //System.err.print("Four threads:" + i);
        }
        //for( int i = 0; i< size ; i = i + 1) {
          //  doEightThreads();
            //speedArray8[i] = speed;
            //System.err.print("Four threads:" + i);
        //}
        for( int i = 0; i< size ; i = i + 1) {
            System.err.println("speedArray8[" + i + "]=" + speedArray8[i]+ "     speedArrayMulti[" + i + "]=" + speedArrayMulti[i]);
            //System.err.println("speedArray2[" + i + "]=" + speedArray2[i] + "     speedArray4[" + i + "]=" + speedArray4[i]);
            //System.err.println("speedArray4[" + i + "]=" + speedArray4[i] + "     speedArray8[" + i + "]=" + speedArray8[i]);
           // System.err.println("speedArray2[" + i + "]=" + speedArray2[i] + "     speedArray4[" + i + "]=" + speedArray4[i] + "     speedArray8[" + i + "]=" + speedArray8[i]);
        }
        //QDataSet timesTwo = Ops.dataset(speedArray2);
        //QDataSet timesFour = Ops.dataset(speedArray4);
        QDataSet timesMulti = Ops.dataset(speedArrayMulti);
        QDataSet timesEight = Ops.dataset(speedArray8);
        
        ScriptContext.setLayout(4,1);
        //ScriptContext.createGui();
        //ScriptContext.plot(0, timesTwo);
        //ScriptContext.plot(1, timesFour);
        ScriptContext.plot(2, timesMulti);
        ScriptContext.plot(3, timesEight);
        
        try {
            ScriptContext.writeToPng("/tmp/Experiment1.png");
        } catch (IOException ex) {
            Logger.getLogger(Experiment1.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }

    private static void doTwoThreads() throws InterruptedException {
        //ScriptContext.createGui();
        final QDataSet ds= Ops.ripplesWaveformTimeSeries(DATASET_SIZE);

        final ProgressMonitor mon0= getMonitor("original task");
        long t0 = System.currentTimeMillis();
        QDataSet out= Ops.fftPower(ds, 512, mon0 );
        
        while (!mon0.isFinished()) {
            Thread.sleep(200);
        }
        long time = System.currentTimeMillis() - t0;
        //System.err.println("Time for original task: " + time);
        
        ScriptContext.setLayout(3,1);
        
        ScriptContext.plot( 0, out );
        
        final ProgressMonitor mon1= getMonitor("task 1");
        final ProgressMonitor mon2= getMonitor("task 2");
                
        Runnable run1= new Runnable() {
            @Override
            public void run() {
                try {
                    QDataSet out1= Ops.fftPower(ds.trim(0,DATASET_SIZE/2), 512, mon1 );
                    //ScriptContext.plot( 1, out1 );
                } catch ( Exception ex) {
                    ex.printStackTrace();
                }
            }
        };
        
        Runnable run2= new Runnable() {
            @Override
            public void run() {
                try {
                    QDataSet out2= Ops.fftPower(ds.trim(DATASET_SIZE/2, DATASET_SIZE), 512, mon2 );
                    //ScriptContext.plot( 2, out2 );
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
    }
    private static final int DATASET_SIZE = 10000;
    
    private static ArrayList<Runnable> run = new ArrayList<Runnable>();
    private static ArrayList<ProgressMonitor> mon = new ArrayList<ProgressMonitor>();
    
    private static void fftMultiThread(int threads) throws InterruptedException {
        run.clear();
        mon.clear();
        
        final QDataSet ds= Ops.ripplesWaveformTimeSeries(DATASET_SIZE);

        final QDataSet[] out = new QDataSet[threads+1];
        ProgressMonitor temp =  getMonitor("original task");
        mon.add(temp);
        long t0 = System.currentTimeMillis();
        out[0]= Ops.fftPower(ds, 512, mon.get(0) );
        
        while (!mon.get(0).isFinished()) {
            Thread.sleep(200);
        }
        long time = System.currentTimeMillis() - t0;
        
        ScriptContext.setLayout(threads+1,1);
        ScriptContext.plot( 0, out[0] );
       
        for( int i = 0; i< threads ; i = i + 1) {
            temp= getMonitor("task" + (i+1));
            mon.add(temp);
            final int j = i+1;
            final int thr = threads;
            Runnable run0= new Runnable() {
            @Override
            public void run() {
                try {
                    out[j]= Ops.fftPower(ds.trim(((j-1)*DATASET_SIZE)/thr,(j*DATASET_SIZE)/thr), 512, mon.get(j) );
                    ScriptContext.plot( j, out[j] );
                } catch ( Exception ex) {
                    ex.printStackTrace();
                }
            }
            };
            run.add(run0);
            if (i==0) {
                t0 = System.currentTimeMillis();
            }
            new Thread(run.get(i)).start();
        }
        
        while( !mon.get(threads - 1).isFinished() && !mon.get(threads).isFinished()  ) {
            Thread.sleep(200);
        }
        long time1 = System.currentTimeMillis() - t0;
        speed = ((double) time) / time1;
        
        try {
            ScriptContext.writeToPng("/tmp/Experiment1_graphs.png");
        } catch (IOException ex) {
            Logger.getLogger(Experiment1.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private static void doFourThreads() throws InterruptedException {
        //ScriptContext.createGui();
        final QDataSet ds= Ops.ripplesWaveformTimeSeries(DATASET_SIZE);

        final ProgressMonitor mon0= getMonitor("original task");
        long t0 = System.currentTimeMillis();
        QDataSet out = Ops.fftPower(ds, 512, mon0 );
       
        while (!mon0.isFinished()) {
            Thread.sleep(200);
        }
        long time = System.currentTimeMillis() - t0;
        //System.err.println("Time for original task: " + time);
        
        ScriptContext.setLayout(5,1);
        
        //ScriptContext.plot( 0, out );
       
      
        
        final ProgressMonitor mon1= getMonitor("task 1");
        final ProgressMonitor mon2= getMonitor("task 2");
        final ProgressMonitor mon3= getMonitor("task 3");
        final ProgressMonitor mon4= getMonitor("task 4");
              
       
        Runnable run1= new Runnable() {
            @Override
            public void run() {
                try {
                    QDataSet out1= Ops.fftPower(ds.trim(0,DATASET_SIZE/4), 512, mon1 );
                    //ScriptContext.plot( 1, out1 );
                } catch ( Exception ex) {
                    ex.printStackTrace();
                }
            }
        };
        
        Runnable run2= new Runnable() {
            @Override
            public void run() {
                try {
                    QDataSet out2= Ops.fftPower(ds.trim(DATASET_SIZE/4,(DATASET_SIZE*2)/4), 512, mon2 );
                    //ScriptContext.plot( 2, out2 );
                } catch ( Exception ex ) {
                    ex.printStackTrace();
                }
            }
        };
        
        Runnable run3= new Runnable() {
            @Override
            public void run() {
                try {
                    QDataSet out3= Ops.fftPower(ds.trim((DATASET_SIZE*2)/4,(DATASET_SIZE*3)/4), 512, mon3 );
                    //ScriptContext.plot( 3, out3 );
                } catch ( Exception ex ) {
                    ex.printStackTrace();
                }
            }
        };
        
        Runnable run4= new Runnable() {
            @Override
            public void run() {
                try {
                    QDataSet out4= Ops.fftPower(ds.trim((DATASET_SIZE*3)/4, DATASET_SIZE), 512, mon4 );
                    //ScriptContext.plot( 4, out4 );
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
    }
    private static void doEightThreads() throws InterruptedException {
        //ScriptContext.createGui();
        final QDataSet ds= Ops.ripplesWaveformTimeSeries(DATASET_SIZE);

        final ProgressMonitor mon0= getMonitor("original task");
        long t0 = System.currentTimeMillis();
        QDataSet out= Ops.fftPower(ds, 512, mon0 );
        
        while (!mon0.isFinished()) {
            Thread.sleep(200);
        }
        long time = System.currentTimeMillis() - t0;
        //System.err.println("Time for original task: " + time);
        
        ScriptContext.setLayout(5,1);
        
        //ScriptContext.plot( 0, out );
        
        final ProgressMonitor mon1= getMonitor("task 1");
        final ProgressMonitor mon2= getMonitor("task 2");
        final ProgressMonitor mon3= getMonitor("task 3");
        final ProgressMonitor mon4= getMonitor("task 4");
        final ProgressMonitor mon5= getMonitor("task 5");
        final ProgressMonitor mon6= getMonitor("task 6");
        final ProgressMonitor mon7= getMonitor("task 7");
        final ProgressMonitor mon8= getMonitor("task 8");
                
        Runnable run1= new Runnable() {
            @Override
            public void run() {
                try {
                    QDataSet out1= Ops.fftPower(ds.trim(0,(DATASET_SIZE*1)/8), 512, mon1 );
                    //ScriptContext.plot( 1, out1 );
                } catch ( Exception ex) {
                    ex.printStackTrace();
                }
            }
        };
        
        Runnable run2= new Runnable() {
            @Override
            public void run() {
                try {
                    QDataSet out2= Ops.fftPower(ds.trim((DATASET_SIZE*1)/8,(DATASET_SIZE*2)/8), 512, mon2 );
                    //ScriptContext.plot( 2, out2 );
                } catch ( Exception ex ) {
                    ex.printStackTrace();
                }
            }
        };
        
        Runnable run3= new Runnable() {
            @Override
            public void run() {
                try {
                    QDataSet out3= Ops.fftPower(ds.trim((DATASET_SIZE*2)/8,(DATASET_SIZE*3)/8), 512, mon3 );
                    //ScriptContext.plot( 3, out3 );
                } catch ( Exception ex ) {
                    ex.printStackTrace();
                }
            }
        };
        
        Runnable run4= new Runnable() {
            @Override
            public void run() {
                try {
                    QDataSet out4= Ops.fftPower(ds.trim((DATASET_SIZE*3)/8,(DATASET_SIZE*4)/8), 512, mon4 );
                    //ScriptContext.plot( 4, out4 );
                } catch ( Exception ex ) {
                    ex.printStackTrace();
                }
            }
        };
        
        Runnable run5= new Runnable() {
            @Override
            public void run() {
                try {
                    QDataSet out5= Ops.fftPower(ds.trim((DATASET_SIZE*4)/8,(DATASET_SIZE*5)/8), 512, mon5 );
                    //ScriptContext.plot( 5, out5 );
                } catch ( Exception ex ) {
                    ex.printStackTrace();
                }
            }
        };Runnable run6= new Runnable() {
            @Override
            public void run() {
                try {
                    QDataSet out6= Ops.fftPower(ds.trim((DATASET_SIZE*5)/8,(DATASET_SIZE*6)/8), 512, mon6 );
                    //ScriptContext.plot( 6, out6 );
                } catch ( Exception ex ) {
                    ex.printStackTrace();
                }
            }
        };Runnable run7= new Runnable() {
            @Override
            public void run() {
                try {
                    QDataSet out7= Ops.fftPower(ds.trim((DATASET_SIZE*6)/8,(DATASET_SIZE*7)/8), 512, mon7 );
                    //ScriptContext.plot( 7, out7 );
                } catch ( Exception ex ) {
                    ex.printStackTrace();
                }
            }
        };Runnable run8= new Runnable() {
            @Override
            public void run() {
                try {
                    QDataSet out8= Ops.fftPower(ds.trim((DATASET_SIZE*7)/8, DATASET_SIZE), 512, mon8 );
                    //ScriptContext.plot( 8, out8 );
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
        new Thread(run5).start();
        new Thread(run6).start();
        new Thread(run7).start();
        new Thread(run8).start();
        
        while( !mon1.isFinished() && !mon2.isFinished() && !mon3.isFinished() && !mon4.isFinished() ) {
            Thread.sleep(200);
        }
        long time1 = System.currentTimeMillis() - t0;
        speed = ((double) time) / time1;
    }
}
