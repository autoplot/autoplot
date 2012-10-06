/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.Units;
import org.das2.util.monitor.NullProgressMonitor;
import static org.virbo.autoplot.ScriptContext.*;
import org.virbo.autoplot.dom.Axis;
import org.virbo.cdfdatasource.CdfFileDataSourceFactory;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSetURI.CompletionResult;

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
            CdfFileDataSourceFactory.loadCdfLibraries();

            setCanvasSize(800, 600);
            getDocumentModel().getOptions().setAutolayout(false);
            getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");


            xxx("init");

            plot("http://cdaweb.gsfc.nasa.gov/opendap/hyrax/genesis/gim/3dl2_gim/2003/genesis_3dl2_gim_20030501_v01.cdf.dds?Proton_Density");
            writeToPng("test005_demo1.png");

            xxx("demo1");

            {
                plot("http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hydra/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX&timerange=20000109");
                Axis axis = getDocumentModel().getPlots(0).getXaxis();
                axis.setRange(DatumRangeUtil.rescale(axis.getRange(), -1, 2));
                writeToPng("test005_demo2.png");
            }

            xxx("demo2");

            {
                String suri = "ftp://ftp.virbo.org/LANL/LANL1991/SOPA+ESP/H0/LANL_1991_080_H0_SOPA_ESP_19920308_V01.cdf?";
                List<CompletionResult> completionResult = DataSetURI.getCompletions(suri, suri.length(), new NullProgressMonitor());
                PrintWriter out = new PrintWriter("test005_demo3.txt");
                for (CompletionResult l : completionResult) {
                    out.println(l.completion);
                }
                out.close();
            }
            xxx("demo3");

            plot("http://cdaweb.gsfc.nasa.gov/opendap/hyrax/genesis/gim/3dl2_gim/2003/genesis_3dl2_gim_20030501_v01.cdf.dds?Proton_Density");
            writeToPng("test005_demo1_r.png");
            xxx("demo1 return");

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
            String omniSrc= "ftp://nssdcftp.gsfc.nasa.gov/spacecraft_data/omni/";
            //String omniSrc= "file:/home/jbf/ct/hudson/data.backup/dat/";
            plot( omniSrc + "/omni2_1963.dat");
            writeToPng("test005_demo7.png");
            xxx("demo7");
            plot( omniSrc + "/omni2_$Y.dat?timerange=1963-1965");
            writeToPng("test005_demo8.png");
            xxx("demo8");
            plot( omniSrc + "omni2_$Y.dat?column=field17&timeFormat=$Y+$j+$H&time=field0&validMax=999&timerange=1972");
            writeToPng("test005_demo9.png");
            xxx("demo9");
            
            plot("http://autoplot.org/data/autoplot.ncml");
            writeToPng("test005_demo10.png");
            xxx("demo10");

            {
                String[] list = org.virbo.jythonsupport.Util.listDirectory("http://cdaweb.gsfc.nasa.gov/istp_public/data/");
                PrintWriter out = new PrintWriter("test005_demo11.txt");
                for (String l : list) {
                    out.println(l);
                }
                out.close();
            }
            xxx("demo11");
            {
            plot("http://autoplot.org/data/hsi_qlimg_5050601_001.fits");
            getDocumentModel().getDataSourceFilters(0).setFilters("|slice0(2)");
            getDocumentModel().getPlotElements(0).setComponent("");
            getDocumentModel().getPlots(0).getZaxis().setRange(DatumRange.newDatumRange(-20e4, 20e4, Units.dimensionless));
            writeToPng("test005_demo12.png");
            }
            xxx("demo12");

            plot("http://autoplot.org/data/hsi_fsimg_5050612_001.fits");
            writeToPng("test005_demo13.png");

            xxx("demo13");

            plot("vap:http://goes.ngdc.noaa.gov/data/avg/$Y/A105$y$m.TXT?skip=23&timeFormat=$y$m$d+$H$M&column=E1&time=YYMMDD&validMax=32000&timerange=Dec+2004");
            writeToPng("test005_demo14.png");
            xxx("demo14");

            System.exit(0);  // TODO: something is firing up the event thread
        } catch ( Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
