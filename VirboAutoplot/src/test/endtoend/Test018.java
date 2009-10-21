/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.IOException;
import java.util.List;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.Panel;
import org.virbo.autoplot.dom.Plot;
import static org.virbo.autoplot.ScriptContext.*;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.metatree.MetadataUtil;

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

    public static void main(String[] args) throws InterruptedException, IOException, Exception {

        String testId= "test018";

        setCanvasSize(600, 600);
        getDocumentModel().getOptions().setAutolayout(false);
        getDocumentModel().getOptions().setCanvasFont("sans-8");
        getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");

        Application dom= getDocumentModel();

        dom.getDataSourceFilters(0).setUri("vap:http://cdaweb.gsfc.nasa.gov/istp_public/data/ace/mfi/2008/ac_k0_mfi_20080602_v01.cdf?BGSEc");
        dom.getPlots(0).getYaxis().setRange( new DatumRange(-10,10,SemanticOps.lookupUnits("nT") ) );
        dom.getController().waitUntilIdle();  // wait for child panels to be created.

        writeToPng( testId + "_000.png");

        dom.getController().copyPlotAndPanels( dom.getPlots(0), null, true, false );

        writeToPng( testId + "_001.png");

        dom.getController().setPlot( dom.getPlots(1) );
        
        List<Plot> plots= dom.getController().addPlots( 1, 3 );

        for ( int i=0; i<plots.size(); i++ ) {
            Plot pl= plots.get(i);
            Panel p= dom.getController().getPanelsFor(pl).get(0);
            p.setComponent( String.format( "B%s GSE", (char)('x'+i) ) );
            p.setDataSourceFilterId("data_0");
            pl.setTitle("");
            pl.getYaxis().setLabel( p.getComponent() );
        }
    
        writeToPng( testId + "_002.png");


        System.exit(0);  // TODO: something is firing up the event thread
    }
    

}
