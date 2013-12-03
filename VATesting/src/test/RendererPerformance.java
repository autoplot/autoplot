/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.graph.DasCanvas;
import org.das2.graph.SpectrogramRenderer;
import org.das2.system.DasLogger;
import org.das2.util.LoggerManager;
import org.virbo.autoplot.RenderType;
import org.virbo.autoplot.ScriptContext;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.PlotElementController;
import test.endtoend.Test009;

/**
 *
 * @author jbf
 */
public class RendererPerformance {
    
    private static void waitForPaint( final DasCanvas c ) throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait( new Runnable() {
            public void run() {
                c.repaint();
            }
        } );
    }
            
    private static void setUp( String uri ) {
        try {
            ScriptContext.plot( uri );
        } catch (InterruptedException ex) {
            Logger.getLogger(RendererPerformance.class.getName()).log(Level.SEVERE, null, ex);
        }   
    }
    
    private static void stressIt( ) {
        
        Application dom= ScriptContext.getDocumentModel();
        DasCanvas c= dom.getController().getCanvas().getController().getDasCanvas();
        
        PlotElementController pec= dom.getPlotElements(0).getController();
        
        try {
            ScriptContext.waitUntilIdle();
            
            DatumRange r1= dom.getController().getPlot().getXaxis().getRange();
            DatumRange r2= DatumRangeUtil.rescale(r1,"1%,101%");

            pec.getRenderer().resetCounters();

            int nstep=40;
            for ( int i=0; i<nstep; i++ ) {
                dom.getController().getPlot().getXaxis().setRange( r2 );
                c.waitUntilIdle();
                waitForPaint(c);
                dom.getController().getPlot().getXaxis().setRange( r1 );
                c.waitUntilIdle();
                waitForPaint(c);
            }      
        } catch (InterruptedException ex) {
            Logger.getLogger(Test009.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ParseException ex) {
            Logger.getLogger(Test009.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            Logger.getLogger(RendererPerformance.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.err.println( " update: " + pec.getRenderer().getUpdateCount() +" render: "+pec.getRenderer().getRenderCount() );

    }
            
    private static void performance() {
        // test series renderer performance
        
        int nn;
        
        LoggerManager.setEnableTimers(true);

        Application dom= ScriptContext.getDocumentModel();
        ScriptContext.createGui();

        nn= 400;    
        setUp( String.format( "vap+inline:n=%d&t=linspace(0,4*PI,n)&t,t*(sin(t)+randn(n)/10)", nn ) );
        LoggerManager.resetTimer("series renderer performance");    
        stressIt();
        LoggerManager.markTime("performance.series");
            
        nn= 400000;    
        setUp( String.format( "vap+inline:n=%d&t=linspace(0,4*PI,n)&t,t*(sin(t)+randn(n)/10)", nn ) );
        dom.getPlotElements(0).setRenderType(RenderType.series);
        LoggerManager.resetTimer("huge series");    
        stressIt();
        LoggerManager.markTime("performance.huge.series");
        
        nn= 400000;    
        setUp( String.format( "vap+inline:n=%d&t=linspace(0,4*PI,n)&t,t*(sin(t)+randn(n)/10)", nn ) );
        dom.getPlotElements(0).setRenderType(RenderType.hugeScatter);
        LoggerManager.resetTimer("huge scatter");    
        stressIt();
        LoggerManager.markTime("performance.hugescatter");
        
        nn= 100;
        setUp( String.format( "vap+inline:ripples(%d,%d)", nn, nn ) );
        dom.getPlotElements(0).getStyle().setRebinMethod( SpectrogramRenderer.RebinnerEnum.binAverage );
        LoggerManager.resetTimer("spectrogram renderer performance");    
        stressIt();
        LoggerManager.markTime("performance.spectrogram");

        nn= 100;
        setUp( String.format( "vap+inline:ripples(%d,%d)", nn, nn ) );
        dom.getPlotElements(0).getStyle().setRebinMethod( SpectrogramRenderer.RebinnerEnum.lanlNearestNeighbor );
        LoggerManager.resetTimer("spectrogram lanlNN renderer performance");    
        stressIt();
        LoggerManager.markTime("performance.lanlNN.spectrogram");
        
    }
    
    public static void main( String[] args ) {
        performance();
        System.exit(0);
    }
    
        
}
