/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.IOException;
import java.util.List;
import org.autoplot.ScriptContext2023;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.EnumerationUnits;
import org.das2.system.DasLogger;
import org.autoplot.dom.Application;
import org.autoplot.dom.PlotElement;
import org.autoplot.dom.Plot;
import static org.autoplot.ScriptContext2023.*;
import org.autoplot.dom.ApplicationController;
import org.autoplot.dom.Axis;
import org.autoplot.dom.BindingModel;
import org.autoplot.layout.LayoutConstants;
import org.das2.qds.DataSetUtil;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;

/**
 * build up a dom programmatically to test dom operators.
 * @author jbf
 */
public class Test018 {
    private static ScriptContext2023 scriptContext= ScriptContext2023.getInstance();
    static long t0 = System.currentTimeMillis();

    public static void xxx(String id) {
        System.err.println("-- timer -- " + id + " --: " + (System.currentTimeMillis() - t0));
        t0 = System.currentTimeMillis();
    }

    /**
     * try to zoom in and then show context overview
     * @param testId
     * @throws IOException
     * @throws InterruptedException
     */
    private static void test3(String testId) throws IOException, InterruptedException {
        System.err.println("=== test018 test3 ===");
//        
//        System.err.println("turn up logging");
//        Logger logger= DasLogger.getLogger(DasLogger.GRAPHICS_LOG);
//        logger.setLevel( Level.ALL );
//        logger.addHandler( new GraphicalLogHandler() );
//        
//        logger.fine("can you hear me?");
//        
        scriptContext.reset();
        Application dom = scriptContext.getDocumentModel();
        dom.getController().reset(); 
        
        dom.getDataSourceFilters(0).setUri("file:///home/jbf/ct/hudson/data.backup/qds/series/hyd_%Y%m%d.qds?timerange=2000-01-01 through 2000-01-12");
        
        scriptContext.getApplicationModel().waitUntilIdle(); // wait for child plot elements to be created.
        Axis xaxis= dom.getPlots(0).getXaxis();

        ApplicationController controller= dom.getController();
        Plot domPlot= dom.getPlots(0);

        Plot that = dom.getController().copyPlotAndPlotElements( domPlot, null, false, false);
        controller.bind(domPlot.getZaxis(), Axis.PROP_RANGE, that.getZaxis(), Axis.PROP_RANGE);
        controller.bind(domPlot.getZaxis(), Axis.PROP_LOG, that.getZaxis(), Axis.PROP_LOG);
        controller.bind(domPlot.getZaxis(), Axis.PROP_LABEL, that.getZaxis(), Axis.PROP_LABEL);
        controller.addConnector(domPlot, that);
        that.getController().resetZoom(true, true, false);

        that.getZaxis().setRange( DatumRange.newDatumRange( 1e4, 1e8, that.getZaxis().getRange().getUnits() ) ); //TODO: why does this autorange so poorly?

        xaxis.setRange( DatumRangeUtil.rescale( xaxis.getRange(), 0.2, 0.5 ) );

        System.err.println( "Before writeToPng:" );
        System.err.println( "xaxis.getRange()="+xaxis.getRange() );
        System.err.println( "cacheImageValid="+domPlot.getController().getDasPlot().isCacheImageValid() );
        System.err.println( "canvas isPendingChanges="+domPlot.getController().getDasPlot().getCanvas().isPendingChanges() );
        System.err.println( "canvas isDirty="+domPlot.getController().getDasPlot().getCanvas().isDirty() );
        scriptContext.writeToPng(testId + "_003.png");
        System.err.println( "After writeToPng:" );
        System.err.println( "xaxis.getRange()="+xaxis.getRange() );
        System.err.println( "cacheImageValid="+domPlot.getController().getDasPlot().isCacheImageValid() );
        System.err.println( "canvas isPendingChanges="+domPlot.getController().getDasPlot().getCanvas().isPendingChanges() );
        System.err.println( "canvas isDirty="+domPlot.getController().getDasPlot().getCanvas().isDirty() );
        
        //logger.setLevel( Level.INFO );
        
    }

    private static void test1(String testId) throws IOException, InterruptedException {
        System.err.println("=== test018 test1 ===");
        scriptContext.reset();
        Application dom = scriptContext.getDocumentModel();

        dom.getDataSourceFilters(0).setUri("file:///home/jbf/ct/hudson/data.backup/cdf/ac_k0_mfi_20080602_v01.cdf?BGSEc");
        dom.getPlots(0).getYaxis().setRange(new DatumRange(-10, 10, SemanticOps.lookupUnits("nT")));
        dom.getController().waitUntilIdle(); // wait for child plot elements to be created.
        scriptContext.writeToPng(testId + "_000.png");
        dom.getController().copyPlotAndPlotElements(dom.getPlots(0), null, true, false);
        scriptContext.writeToPng(testId + "_001.png");
        dom.getController().setPlot(dom.getPlots(1));
        List<Plot> plots = dom.getController().addPlots(1, 3,LayoutConstants.BELOW);
        for (int i = 0; i < plots.size(); i++) {
            Plot pl = plots.get(i);
            PlotElement p = dom.getController().getPlotElementsFor(pl).get(0);
            p.setComponent(String.format("B%s GSE", (char) ('x'+i)));
            p.setDataSourceFilterId("data_0");
            pl.setTitle("");
            pl.getYaxis().setLabel(p.getComponent());
        }
        scriptContext.writeToPng(testId + "_002.png");
    }

    /**
     * this shows a bug where switching array-of-vector URIs would result in 12 plot elements instead of 4.
     * @param testId
     * @throws IOException
     * @throws InterruptedException
     */
    public static void test4( String testId ) throws IOException, InterruptedException {
        System.err.println("=== test018 test4 ===");
        scriptContext.reset();
        scriptContext.setCanvasSize(400,300);

        System.err.println("+++++++++++++++++++++++++");
        // this transition works fine
        scriptContext.plot(0,"http://autoplot.org/data/autoplot.cdf?BGSM");
        scriptContext.waitUntilIdle();
        scriptContext.plot(0,"http://autoplot.org/data/autoplot.cdf?BGSEc");
        scriptContext.waitUntilIdle();
        scriptContext.writeToPng(testId + "_004a.png");

        System.err.println("+++++++++++++++++++++++++");
        scriptContext.plot(0,"vap+cdfj:file:///home/jbf/ct/hudson/data.backup/cdf/lon/thb_l2_esa_20080907_v01.cdf?thb_peef_velocity_dsl");
        scriptContext.waitUntilIdle();
        scriptContext.plot(0,"vap+cdf:file:///home/jbf/ct/hudson/data.backup/cdf/lon/thb_l2_esa_20080907_v01.cdf?thb_peef_velocity_dsl");
        scriptContext.waitUntilIdle();
        scriptContext.writeToPng(testId + "_004.png");
        System.err.println("+++++++++++++++++++++++++");
    }

    public static void test5( String testId ) throws InterruptedException, IOException {
        System.err.println("=== test018 test5 ===");
        scriptContext.reset();
        Application dom = scriptContext.getDocumentModel();
        scriptContext.plot( "vap+cdf:http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hydra/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX&timerange=20000109" );
        dom.getController().getPlot().getXaxis().setRange( dom.getController().getPlot().getXaxis().getRange().next() );
        scriptContext.plot( 1, "vap+cdf:http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hydra/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ION_DIFFERENTIAL_ENERGY_FLUX&timerange=20000110" );
        dom.getController().getPlot().getXaxis().setRange( dom.getController().getPlot().getXaxis().getRange().next() ); // are they bound?
        scriptContext.writeToPng(testId + "_005.png");
    }


    /**
     * check for bindings.  Currently this will bind to the timerange, and after 2012-04-17 it does not reset the timerange.  Soon
     * this should leave it unbound, or the gui will present the user the choice.
     * @param testId
     * @throws Exception
     */
    public static void test6( String testId ) throws Exception {
        System.err.println("=== test018 test6 ===");
        scriptContext.reset();
        scriptContext.plot( "http://autoplot.org/vap/swpc.vap?timeRange=2012-04-07+through+2012-04-17" );
        scriptContext.waitUntilIdle();
        scriptContext.plot( 0, "vap+tsds:http://tsds.net/cgi-bin/get.cgi?StartDate=19910101&EndDate=20041231&ext=bin&out=tsml&ppd=24&filter=mean&param1=Augsburg_ULF-1-v1" );
        scriptContext.writeToPng(testId + "_006.png");
    }

    private static Plot docKludge( int position, String mes ) throws InterruptedException {
        EnumerationUnits eu= new EnumerationUnits("default");
        Datum d= eu.createDatum(mes);
        QDataSet ds= DataSetUtil.asDataSet(d);
        scriptContext.plot( position, ds );
        scriptContext.waitUntilIdle();
        ApplicationController ac= scriptContext.getDocumentModel().getController();
        Plot p= ac.getFirstPlotFor( scriptContext.getDocumentModel().getDataSourceFilters(position) );
        //p.getXaxis().setVisible(false);
        //p.getYaxis().setVisible(false);
        return p;
    }

    private static String bindStr( Application dom ) {
        StringBuilder b= new StringBuilder();
        for ( BindingModel bm: dom.getBindings() ) {
            if ( bm.toString().contains("colortable") ) {
                // don't mention this.
            } else {
                b.append(bm.toString()).append("!c");
            }
        }
        return b.toString();
    }

    public static void test7_bindings( String testId ) throws Exception {
        Application dom= scriptContext.getDocumentModel();

        System.err.println("=== test018 007_1 ========================");
        scriptContext.reset();
        scriptContext.plot( "vap+cdaweb:ds=AC_H2_SWE&id=Np&timerange=1998-03-01" );
        scriptContext.waitUntilIdle();
        scriptContext.plot( 1, "vap+cdaweb:ds=AC_H2_SWE&id=Vp&timerange=1998-03-01" );
        dom.setTimeRange( dom.getTimeRange().next() );
        docKludge( 2, "These should be bound!c"+bindStr(dom) );
        System.err.println(bindStr(dom).replaceAll("!c","\n"));
        scriptContext.writeToPng(testId + "_007_1.png");

        System.err.println("=== test018 007_2 ========================");
        scriptContext.reset();
        scriptContext.plot( "vap+cdaweb:ds=AC_H2_SWE&id=Np&timerange=1998-03-01" );
        scriptContext.waitUntilIdle();
        scriptContext.plot( 1, "vap+cdaweb:ds=AC_H2_SWE&id=Vp&timerange=1998-03-01" );
        scriptContext.plot( "vap+cdaweb:ds=AC_H2_SWE&id=Np&timerange=1998-03-02" );
        docKludge( 2, "These should be bound!c"+bindStr(dom) );
        System.err.println(bindStr(dom).replaceAll("!c","\n"));
        scriptContext.writeToPng(testId + "_007_2.png");

        System.err.println("=== test018 007_3 ========================");
        scriptContext.reset();
        scriptContext.plot( "vap+cdaweb:ds=AC_H2_SWE&id=Np&timerange=1998-03-01" );
        scriptContext.waitUntilIdle();
        scriptContext.plot( 1, "vap+cdaweb:ds=AC_H2_SWE&id=Np&timerange=1999-04-05" );
        docKludge( 2, "These should not be bound!c"+bindStr(dom) );
        System.err.println(bindStr(dom).replaceAll("!c","\n"));
        scriptContext.writeToPng(testId + "_007_3.png"); // this should disengage from timerange

        System.err.println("=== test018 007_4 ========================");
        scriptContext.reset();
        scriptContext.plot( "vap+cdaweb:ds=AC_H2_SWE&id=Np&timerange=1998-03-01" );
        scriptContext.waitUntilIdle();
        scriptContext.plot( 1, "vap+cdaweb:ds=AC_H2_SWE&id=Vp&timerange=1998-03-01" );
        scriptContext.plot( "vap+cdaweb:ds=AC_H2_SWE&id=Np&timerange=1998-04-05" );
        docKludge( 2, "These should be bound!c"+bindStr(dom));
        System.err.println(bindStr(dom).replaceAll("!c","\n"));
        scriptContext.writeToPng(testId + "_007_4.png");
        System.err.println("==============================");
    }


    public static void main(String[] args) {

        boolean exit= true;
        
        DasLogger.setUpHandler("mini");
//        try {
//            LogManager.getLogManager().readConfiguration( new FileInputStream("/home/jbf/autoplot_data/config/logging.properties") );
//        } catch (FileNotFoundException ex) {
//            Logger.getLogger(Test018.class.getName()).log(Level.SEVERE, null, ex);
//        } catch (IOException | SecurityException ex) {
//            Logger.getLogger(Test018.class.getName()).log(Level.SEVERE, null, ex);
//        }
        
        try {

            if ( exit==false ) {
                scriptContext.createGui(); // start up interactive window
            }

            String testId= "test018";
            scriptContext.getDocumentModel().getOptions().setAutolayout(false);
            
            // screen size must be default or image is too large and compare hangs.
            test7_bindings( testId ); // verify bindings logic described in http://autoplot.org/developer.timerangeBinding(?)
            
            scriptContext.setCanvasSize(600, 600);

            scriptContext.getDocumentModel().getOptions().setAutolayout(false);
            scriptContext.getDocumentModel().getOptions().setCanvasFont("sans-8");
            scriptContext.getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");

            test1( testId );
            //no test2, to straighten out the numbering
            //DasPlot.testSentinal="break";
            test3( testId );
            test4( testId );
            test5( testId );
            //test6( testId ); // test out binding TEST DISABLED WHILE TSDS is DOWN

            //test7_bindings( testId ); // verify bindings logic described in http://autoplot.org/developer.timerangeBinding(?)

        } catch ( Exception ex ) {
            ex.printStackTrace();
            System.exit(1);
        }

        if ( exit ) {
            System.exit(0);  // TODO: something is firing up the event thread
        }
    }

}
