/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.test;

import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.graph.DasCanvas;
import org.das2.graph.SpectrogramRenderer;
import org.autoplot.RenderType;
import org.autoplot.ScriptContext;
import org.autoplot.dom.Application;
import org.autoplot.dom.PlotElementController;
import org.das2.util.LoggerManager;
//import vatest.endtoend.Test009;

/**
 * Introduce test to keep track of rendering performance.
 * @author jbf
 */
public class RendererPerformance {
    
    private static final Logger logger= LoggerManager.getLogger("RendererPerformance");
    
    private static final ScriptContext scriptContext= ScriptContext.getInstance();
    
    private static void waitForPaint( final DasCanvas c ) throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait( new Runnable() {
            @Override
            public void run() {
                c.repaint();
            }
        } );
    }
            
    private static void setUp( String uri ) {
        System.err.println("uri: "+uri);
        scriptContext.plot( uri );
    }
    
    private static void stressIt( ) {
        
        Application dom= scriptContext.getDocumentModel();
        DasCanvas c= dom.getController().getCanvas().getController().getDasCanvas();
        
        PlotElementController pec= dom.getPlotElements(0).getController();
        
        try {
            scriptContext.waitUntilIdle();
            
            DatumRange r1= dom.getController().getPlot().getXaxis().getRange();
            DatumRange r2= DatumRangeUtil.rescale(r1,"1%,101%");

            pec.getRenderer().resetCounters();

            int nstep=40;
            System.err.println("nstep: "+nstep);
            for ( int i=0; i<nstep; i++ ) {
                dom.getController().getPlot().getXaxis().setRange( r2 );
                c.waitUntilIdle();
                waitForPaint(c);
                dom.getController().getPlot().getXaxis().setRange( r1 );
                c.waitUntilIdle();
                waitForPaint(c);
            }      
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (ParseException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        System.err.println( "update: " + pec.getRenderer().getUpdateCount() );
        System.err.println( "render: "+pec.getRenderer().getRenderCount() );

    }
            
    private static void performance() {
        
        int nn;
        long t0;

        Application dom= scriptContext.getDocumentModel();
        scriptContext.createGui();

        System.err.println("---------------------");
        nn= 400;    
        setUp( String.format( "vap+inline:ripples(%d,%d)", nn,nn ) );
        dom.getPlotElements(0).setRenderType(RenderType.contour);
        t0= System.currentTimeMillis();
        stressIt();
        System.err.println("performance.contour (ms): "+(System.currentTimeMillis()-t0) );
        
        System.err.println("---------------------");
        nn= 400;    
        setUp( String.format( "vap+inline:n=%d&t=linspace(0,4*PI,n)&t,t*(sin(t)+randn(n)/10)", nn ) );
        t0= System.currentTimeMillis();
        stressIt();
        System.err.println("performance.series (ms): "+(System.currentTimeMillis()-t0) );
            
        System.err.println("---------------------");
        nn= 400000;    
        setUp( String.format( "vap+inline:n=%d&t=linspace(0,4*PI,n)&t,t*(sin(t)+randn(n)/10)", nn ) );
        dom.getPlotElements(0).setRenderType(RenderType.series);
        t0= System.currentTimeMillis();
        stressIt();
        System.err.println("performance.huge.series (ms): "+(System.currentTimeMillis()-t0) );
        
        System.err.println("---------------------");
        nn= 100;    
        setUp( String.format( "vap+inline:ripplesWaveformTimeSeries(%d)", nn ) );
        t0= System.currentTimeMillis();
        stressIt();
        System.err.println("performance.waveform.series (ms): "+(System.currentTimeMillis()-t0));

        System.err.println("---------------------");
        nn= 1000;    
        setUp( String.format( "vap+inline:ripplesWaveformTimeSeries(%d)", nn ) );
        t0= System.currentTimeMillis();
        stressIt();
        System.err.println("performance.waveform.huge.series (ms): "+(System.currentTimeMillis()-t0));
        
        System.err.println("---------------------");
        nn= 100;    
        setUp( String.format( "vap+inline:ripplesWaveformTimeSeries(%d)", nn ) );
        dom.getPlotElements(0).setRenderType(RenderType.series);
        t0= System.currentTimeMillis();
        stressIt();
        System.err.println("performance.waveform.series (ms): "+(System.currentTimeMillis()-t0));

        System.err.println("---------------------");
        nn= 1000;    
        setUp( String.format( "vap+inline:ripplesWaveformTimeSeries(%d)", nn ) );
        dom.getPlotElements(0).setRenderType(RenderType.series);
        t0= System.currentTimeMillis();
        stressIt();
        System.err.println("performance.waveform.huge.series (ms): "+(System.currentTimeMillis()-t0));

        System.err.println("---------------------");
        nn= 400000;    
        setUp( String.format( "vap+inline:n=%d&t=linspace(0,4*PI,n)&t,t*(sin(t)+randn(n)/10)", nn ) );
        dom.getPlotElements(0).setRenderType(RenderType.hugeScatter);
        t0= System.currentTimeMillis();
        stressIt();
        System.err.println("performance.hugescatter (ms): "+(System.currentTimeMillis()-t0));
        
        System.err.println("---------------------");
        nn= 100;
        setUp( String.format( "vap+inline:ripples(%d,%d)", nn, nn ) );
        dom.getPlotElements(0).getStyle().setRebinMethod( SpectrogramRenderer.RebinnerEnum.binAverage );
        t0= System.currentTimeMillis();
        stressIt();
        System.err.println("performance.spectrogram (ms): "+(System.currentTimeMillis()-t0));

        System.err.println("---------------------");
        nn= 100;
        setUp( String.format( "vap+inline:ripples(%d,%d)", nn, nn ) );
        dom.getPlotElements(0).getStyle().setRebinMethod( SpectrogramRenderer.RebinnerEnum.lanlNearestNeighbor );
        t0= System.currentTimeMillis();
        stressIt();
        System.err.println("performance.lanlNN.spectrogram (ms): "+(System.currentTimeMillis()-t0));
        
    }
    
    public static void main( String[] args ) {
        performance();
        System.exit(0);
    }
    
        
}
