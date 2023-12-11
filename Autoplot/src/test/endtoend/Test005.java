
package test.endtoend;

import java.io.PrintWriter;
import org.autoplot.AutoplotUI;
import org.autoplot.AutoplotUtil;
import org.autoplot.ScriptContext;
import org.autoplot.dom.Application;
import org.das2.datum.DatumRangeUtil;
import org.autoplot.dom.Axis;
import org.autoplot.dom.Column;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystem;

/**
 * Test Autoplot's demo bookmarks
 * @author jbf
 */
public class Test005 {

    static long t0= System.currentTimeMillis();

    public static void xxx(String id) {
        System.err.println("-- timer -- " + id + " --: "+ ( System.currentTimeMillis()-t0) );
        t0= System.currentTimeMillis();
    }

    public static void main(String[] args)  {
        try {

            LoggerManager.readConfiguration("/home/jbf/autoplot_data/config/logging.properties");
            
            ScriptContext scriptContext= ScriptContext.getInstance();
            Application dom= scriptContext.getDocumentModel();
            
            Column mc= dom.getCanvases(0).getMarginColumn();
            System.err.println("margin column: "+ mc.getId() + " " + mc.getLeft() + " " + mc.getRight() );
            
            AutoplotUtil.disableCertificates();
            System.err.println( AutoplotUI.SYSPROP_AUTOPLOT_DISABLE_CERTS + ": " + System.getProperty( AutoplotUI.SYSPROP_AUTOPLOT_DISABLE_CERTS ) );
            
            scriptContext.setCanvasSize(800, 600);
            dom.getOptions().setAutolayout(false);
            dom.getCanvases(0).getMarginColumn().setRight("100%-10em");
            dom.getCanvases(0).getMarginColumn().setLeft("+6em");
            dom.getCanvases(0).getMarginRow().setTop("2em");
            dom.getCanvases(0).getMarginRow().setBottom("100%-3em");

            xxx("init");

            //plot("http://cdaweb.gsfc.nasa.gov/opendap/hyrax/genesis/gim/3dl2_gim/2003/genesis_3dl2_gim_20030501_v01.cdf.dds?Proton_Density");
            //#writeToPng("test005_demo1.png");

            //xxx("demo1");

            {
                scriptContext.plot("https://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hydra/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX&timerange=20000109");
                Axis axis = dom.getPlots(0).getXaxis();
                axis.setRange(DatumRangeUtil.rescale(axis.getRange(), -1, 2));
                scriptContext.writeToPng("test005_demo2.png");
                mc= dom.getCanvases(0).getMarginColumn();
                System.err.println("margin column: "+ mc.getId() + " " + mc.getLeft() + " " + mc.getRight() );
            }

            xxx("demo3");

            //OFFLINE MODE vvv
            //https://satdat.ngdc.noaa.gov started sending 429 (Too many requests), so I'll test offline mode.
            System.err.println("*** MANUALLY SETTING OFFLINE MODE ***");
            FileSystem.settings().setOffline( true );
            FileSystem.reset();
            
            scriptContext.plot("https://satdat.ngdc.noaa.gov/sem/goes/data/avg/$Y/$m/goes10/csv/g10_eps_5m_$Y$m$d_$(Y,end)$m$d.csv?column=field1&depend0=field0&skip=456&timerange=Dec+2004");
            
            Thread.sleep(300); // We shouldn't have to do this.
            
            scriptContext.writeToPng("test005_demo14.png");
            xxx("demo14");
            
            FileSystem.settings().setOffline( false );
            FileSystem.reset();
            //OFFLINE MODE ^^^
            
            //plot("http://cdaweb.gsfc.nasa.gov/opendap/hyrax/genesis/gim/3dl2_gim/2003/genesis_3dl2_gim_20030501_v01.cdf.dds?Proton_Density");
            //writeToPng("test005_demo1_r.png");
            //xxx("demo1 return");

            scriptContext.plot("http://autoplot.org/data/autoplot.xls?column=A");
            scriptContext.writeToPng("test005_demo4.png");
            xxx("demo4");
            scriptContext.plot("http://autoplot.org/data/autoplot.cdf?BGSM");
            scriptContext.writeToPng("test005_demo5.png");
            xxx("demo5");
            scriptContext.plot("http://autoplot.org/data/autoplot.xls?column=A");   // must delete extra plot elements from BGSM
            scriptContext.writeToPng("test005_demo4_r.png");
            xxx("demo4 return");
            scriptContext.plot("http://autoplot.org/data/autoplot.xml");
            scriptContext.writeToPng("test005_demo6.png");
            xxx("demo6");
            String omniSrc= "https://cdaweb.gsfc.nasa.gov/pub/data/omni/low_res_omni/";
            //String omniSrc= "file:/home/jbf/ct/hudson/data.backup/dat/";
            scriptContext.plot( omniSrc + "/omni2_1963.dat");
            scriptContext.writeToPng("test005_demo7.png");
            xxx("demo7");
            //plot( omniSrc + "/omni2_$Y.dat?timerange=1963-1965");
            //writeToPng("test005_demo8.png");
            //xxx("demo8");
            scriptContext.plot( omniSrc + "omni2_$Y.dat?column=field17&timeFormat=$Y+$j+$H&time=field0&validMax=999&timerange=1972");
            scriptContext.writeToPng("test005_demo9.png");
            xxx("demo9");
            
            scriptContext.plot("http://autoplot.org/data/autoplot.ncml");
            scriptContext.writeToPng("test005_demo10.png");
            xxx("demo10");

            {
                String[] list = org.autoplot.jythonsupport.Util.listDirectory("http://cdaweb.gsfc.nasa.gov/istp_public/data/");
                try (PrintWriter out = new PrintWriter("test005_demo11.txt")) {
                    for (String l : list) {
                        out.println(l);
                    }
                }
            }
            xxx("demo11");
 
            scriptContext.plot("http://autoplot.org/data/hsi_fsimg_5050612_001.fits");
            scriptContext.writeToPng("test005_demo13.png");

            System.err.println( dom.getDataSourceFilters(0).getFilters() );
            System.err.println( dom.getDataSourceFilters(0).getController().getDataSet() );
            System.err.println( dom.getDataSourceFilters(0).getController().getFillDataSet() );
            System.err.println( dom.getPlotElements(0).getController().getDataSet() );
            System.err.println( dom.getPlotElements(0).getController().getRenderer().getDataSet() );
            System.err.println( dom.getPlotElements(0).getPlotDefaults().getZaxis().getRange() );
            System.err.println( dom.getPlots(0).getZaxis().getRange() );
            
            xxx("demo13");
            
            scriptContext.plot("http://autoplot.org/data/hsi_qlimg_5050601_001.fits");  // note this is not what happens, but it's still an interesting test.
            dom.getDataSourceFilters(0).setFilters("|slice0(2)");
            Thread.sleep(1000); // We shouldn't have to do this.
            dom.getPlotElements(0).setComponent("");
            Thread.sleep(1000); // it's probably because the app isn't locked properly.
            scriptContext.writeToPng("test005_demo12.png");

            xxx("demo12");

            //TODO: why does this not reset with the plot command below?  This only occurs in testing server.
            dom.getDataSourceFilters(0).setFilters("");
            
            System.exit(0);  // TODO: something is firing up the event thread
        } catch ( Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
