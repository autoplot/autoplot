
package test.endtoend;

import java.io.PrintWriter;
import org.autoplot.AutoplotUI;
import org.autoplot.AutoplotUtil;
import org.das2.datum.DatumRangeUtil;
import static org.autoplot.ScriptContext.*;
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
            
            Column mc= getDocumentModel().getCanvases(0).getMarginColumn();
            System.err.println("margin column: "+ mc.getId() + " " + mc.getLeft() + " " + mc.getRight() );
            
            AutoplotUtil.disableCertificates();
            System.err.println( AutoplotUI.SYSPROP_AUTOPLOT_DISABLE_CERTS + ": " + System.getProperty( AutoplotUI.SYSPROP_AUTOPLOT_DISABLE_CERTS ) );
            
            setCanvasSize(800, 600);
            getDocumentModel().getOptions().setAutolayout(false);
            getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");
            getDocumentModel().getCanvases(0).getMarginColumn().setLeft("+6em");
            getDocumentModel().getCanvases(0).getMarginRow().setTop("2em");
            getDocumentModel().getCanvases(0).getMarginRow().setBottom("100%-3em");

            xxx("init");

            //plot("http://cdaweb.gsfc.nasa.gov/opendap/hyrax/genesis/gim/3dl2_gim/2003/genesis_3dl2_gim_20030501_v01.cdf.dds?Proton_Density");
            //#writeToPng("test005_demo1.png");

            //xxx("demo1");

            {
                plot("https://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hydra/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX&timerange=20000109");
                Axis axis = getDocumentModel().getPlots(0).getXaxis();
                axis.setRange(DatumRangeUtil.rescale(axis.getRange(), -1, 2));
                writeToPng("test005_demo2.png");
                mc= getDocumentModel().getCanvases(0).getMarginColumn();
                System.err.println("margin column: "+ mc.getId() + " " + mc.getLeft() + " " + mc.getRight() );
            }

            xxx("demo3");

            //OFFLINE MODE vvv
            //https://satdat.ngdc.noaa.gov started sending 429 (Too many requests), so I'll test offline mode.
            System.err.println("*** MANUALLY SETTING OFFLINE MODE ***");
            FileSystem.settings().setOffline( true );
            FileSystem.reset();
            
            plot("https://satdat.ngdc.noaa.gov/sem/goes/data/avg/$Y/$m/goes10/csv/g10_eps_5m_$Y$m$d_$(Y,end)$m$d.csv?column=field1&depend0=field0&skip=456&timerange=Dec+2004");
            
            Thread.sleep(300); // We shouldn't have to do this.
            
            writeToPng("test005_demo14.png");
            xxx("demo14");
            
            FileSystem.settings().setOffline( false );
            FileSystem.reset();
            //OFFLINE MODE ^^^
            
            //plot("http://cdaweb.gsfc.nasa.gov/opendap/hyrax/genesis/gim/3dl2_gim/2003/genesis_3dl2_gim_20030501_v01.cdf.dds?Proton_Density");
            //writeToPng("test005_demo1_r.png");
            //xxx("demo1 return");

            plot("http://autoplot.org/data/autoplot.xls?column=A");
            writeToPng("test005_demo4.png");
            xxx("demo4");
            plot("http://autoplot.org/data/autoplot.cdf?BGSM");
            writeToPng("test005_demo5.png");
            xxx("demo5");
            plot("http://autoplot.org/data/autoplot.xls?column=A");   // must delete extra plot elements from BGSM
            writeToPng("test005_demo4_r.png");
            xxx("demo4 return");
            plot("http://autoplot.org/data/autoplot.xml");
            writeToPng("test005_demo6.png");
            xxx("demo6");
            String omniSrc= "https://cdaweb.gsfc.nasa.gov/pub/data/omni/low_res_omni/";
            //String omniSrc= "file:/home/jbf/ct/hudson/data.backup/dat/";
            plot( omniSrc + "/omni2_1963.dat");
            writeToPng("test005_demo7.png");
            xxx("demo7");
            //plot( omniSrc + "/omni2_$Y.dat?timerange=1963-1965");
            //writeToPng("test005_demo8.png");
            //xxx("demo8");
            plot( omniSrc + "omni2_$Y.dat?column=field17&timeFormat=$Y+$j+$H&time=field0&validMax=999&timerange=1972");
            writeToPng("test005_demo9.png");
            xxx("demo9");
            
            plot("http://autoplot.org/data/autoplot.ncml");
            writeToPng("test005_demo10.png");
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
 
            plot("http://autoplot.org/data/hsi_fsimg_5050612_001.fits");
            writeToPng("test005_demo13.png");

            System.err.println( getDocumentModel().getDataSourceFilters(0).getFilters() );
            System.err.println( getDocumentModel().getDataSourceFilters(0).getController().getDataSet() );
            System.err.println( getDocumentModel().getDataSourceFilters(0).getController().getFillDataSet() );
            System.err.println( getDocumentModel().getPlotElements(0).getController().getDataSet() );
            System.err.println( getDocumentModel().getPlotElements(0).getController().getRenderer().getDataSet() );
            System.err.println( getDocumentModel().getPlotElements(0).getPlotDefaults().getZaxis().getRange() );
            System.err.println( getDocumentModel().getPlots(0).getZaxis().getRange() );
            
            xxx("demo13");
            
            plot("http://autoplot.org/data/hsi_qlimg_5050601_001.fits");  // note this is not what happens, but it's still an interesting test.
            getDocumentModel().getDataSourceFilters(0).setFilters("|slice0(2)");
            Thread.sleep(1000); // We shouldn't have to do this.
            getDocumentModel().getPlotElements(0).setComponent("");
            Thread.sleep(1000); // it's probably because the app isn't locked properly.
            writeToPng("test005_demo12.png");

            xxx("demo12");

            //TODO: why does this not reset with the plot command below?  This only occurs in testing server.
            getDocumentModel().getDataSourceFilters(0).setFilters("");
            
            System.exit(0);  // TODO: something is firing up the event thread
        } catch ( Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
