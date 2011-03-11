/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.IOException;
import java.util.List;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.Units;
import org.das2.graph.SpectrogramRenderer;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.PlotElement;
import org.virbo.autoplot.dom.Plot;
import static org.virbo.autoplot.ScriptContext.*;
import org.virbo.autoplot.dom.ApplicationController;
import org.virbo.autoplot.dom.Axis;
import org.virbo.dataset.SemanticOps;

/**
 * build up a dom programmatically to test dom operators.
 * @author jbf
 */
public class Test018 {

    static long t0 = System.currentTimeMillis();

    public static void xxx(String id) {
        System.err.println("-- timer -- " + id + " --: " + (System.currentTimeMillis() - t0));
        t0 = System.currentTimeMillis();
    }

    public static void main(String[] args) {

        boolean exit= true;

        try {

            if ( exit==false ) {
                createGui(); // start up interactive window
            }

            String testId= "test018";

            setCanvasSize(600, 600);
            getDocumentModel().getOptions().setAutolayout(false);
            getDocumentModel().getOptions().setCanvasFont("sans-8");
            getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");


            test1( testId );
            test2( testId );

        } catch ( Exception ex ) {
            ex.printStackTrace();
            System.exit(1);
        }

        if ( exit ) {
            System.exit(0);  // TODO: something is firing up the event thread
        }
    }

    /**
     * try to zoom in and then show context overview
     * @param testId
     * @throws IOException
     * @throws InterruptedException
     */
    private static void test2(String testId) throws IOException, InterruptedException {
        
        Application dom = getDocumentModel();
        dom.getController().reset(); 
        
        dom.getDataSourceFilters(0).setUri("file:///home/jbf/ct/hudson/data.backup/qds/series/hyd_%Y%m%d.qds?timerange=2000-01-01 through 2000-01-12");
        
        getApplicationModel().waitUntilIdle(false); // wait for child plot elements to be created.
        Axis xaxis= dom.getPlots(0).getXaxis();

        ApplicationController controller= dom.getController();
        Plot domPlot= dom.getPlots(0);

        System.err.println( domPlot.getXaxis().getRange() );
        
        Plot that = dom.getController().copyPlotAndPlotElements( domPlot, null, false, false);
        controller.bind(domPlot.getZaxis(), Axis.PROP_RANGE, that.getZaxis(), Axis.PROP_RANGE);
        controller.bind(domPlot.getZaxis(), Axis.PROP_LOG, that.getZaxis(), Axis.PROP_LOG);
        controller.bind(domPlot.getZaxis(), Axis.PROP_LABEL, that.getZaxis(), Axis.PROP_LABEL);
        controller.addConnector(domPlot, that);
        that.getController().resetZoom(true, true, false);

        that.getZaxis().setRange( DatumRange.newDatumRange( 1e4, 1e8, that.getZaxis().getRange().getUnits() ) ); //TODO: why does this autorange so poorly?

        getApplicationModel().waitUntilIdle(false); // TODO: look into why this is necessary.

        xaxis.setRange( DatumRangeUtil.rescale( xaxis.getRange(), 0.2, 0.5 ) );
        getApplicationModel().waitUntilIdle(false); 

        SpectrogramRenderer r= (SpectrogramRenderer) domPlot.getController().getDasPlot().getRenderer(0);
        System.err.println( "spectrogram memento state= " + r.getXmemento() );
        
        writeToPng(testId + "_003.png");

    }

    private static void test1(String testId) throws IOException, InterruptedException {
        Application dom = getDocumentModel();

        dom.getDataSourceFilters(0).setUri("vap:file:///home/jbf/ct/hudson/data.backup/cdf/ac_k0_mfi_20080602_v01.cdf?BGSEc");
        dom.getPlots(0).getYaxis().setRange(new DatumRange(-10, 10, SemanticOps.lookupUnits("nT")));
        dom.getController().waitUntilIdle(); // wait for child plot elements to be created.
        writeToPng(testId + "_000.png");
        dom.getController().copyPlotAndPlotElements(dom.getPlots(0), null, true, false);
        writeToPng(testId + "_001.png");
        dom.getController().setPlot(dom.getPlots(1));
        List<Plot> plots = dom.getController().addPlots(1, 3);
        for (int i = 0; i < plots.size(); i++) {
            Plot pl = plots.get(i);
            PlotElement p = dom.getController().getPlotElementsFor(pl).get(0);
            p.setComponent(String.format("B%s GSE", (char) ('x'+i)));
            p.setDataSourceFilterId("data_0");
            pl.setTitle("");
            pl.getYaxis().setLabel(p.getComponent());
        }
        writeToPng(testId + "_002.png");
    }
    

}
