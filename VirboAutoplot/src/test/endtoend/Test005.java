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
import org.virbo.autoplot.ScriptContext;
import org.virbo.autoplot.dom.Axis;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSetURL.CompletionResult;

/**
 *
 * @author jbf
 */
public class Test005 {

    public static void main(String[] args) throws InterruptedException, IOException, Exception {
        try {
            ScriptContext.setCanvasSize(800, 600);
            ScriptContext.getDocumentModel().getOptions().setAutolayout(false);
            ScriptContext.getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");

            ScriptContext.plot("http://cdaweb.gsfc.nasa.gov/cgi-bin/opendap/nph-dods/istp_public/data/genesis/3dl2_gim/2003/genesis_3dl2_gim_20030501_v01.cdf.dds?Proton_Density");
            ScriptContext.writeToPng("test005_demo1.png");

            {
                ScriptContext.plot("http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX&timerange=20000109");
                Axis axis = ScriptContext.getDocumentModel().getPlots(0).getXaxis();
                axis.setRange(DatumRangeUtil.rescale(axis.getRange(), -1, 2));
                ScriptContext.writeToPng("test005_demo2.png");
            }

            {
                String suri = "ftp://ftp.virbo.org/LANL/LANL1991/SOPA+ESP/H0/LANL_1991_080_H0_SOPA_ESP_19920308_V01.cdf?";
                List<CompletionResult> completionResult = DataSetURL.getCompletions(suri, suri.length(), new NullProgressMonitor());
                PrintWriter out = new PrintWriter("test005_demo3.txt");
                for (CompletionResult l : completionResult) {
                    out.println(l.completion);
                }
                out.close();
            }
            ScriptContext.plot("http://cdaweb.gsfc.nasa.gov/cgi-bin/opendap/nph-dods/istp_public/data/genesis/3dl2_gim/2003/genesis_3dl2_gim_20030501_v01.cdf.dds?Proton_Density");
            ScriptContext.writeToPng("test005_demo1_r.png");
            ScriptContext.plot("http://autoplot.org/data/autoplot.xls?column=A");
            ScriptContext.writeToPng("test005_demo4.png");
            ScriptContext.plot("http://autoplot.org/data/autoplot.cdf?BGSM");
            ScriptContext.writeToPng("test005_demo5.png");
            ScriptContext.plot("http://autoplot.org/data/autoplot.xls?column=A");   // must delete extra panels from BGSM
            ScriptContext.writeToPng("test005_demo4_r.png");
            ScriptContext.plot("http://autoplot.org/data/autoplot.xml");
            ScriptContext.writeToPng("test005_demo6.png");
            ScriptContext.plot("ftp://nssdcftp.gsfc.nasa.gov/spacecraft_data/omni/omni2_1963.dat");
            ScriptContext.writeToPng("test005_demo7.png");
            ScriptContext.plot("ftp://nssdcftp.gsfc.nasa.gov/spacecraft_data/omni/omni2_$Y.dat?timerange=1963-1965");
            ScriptContext.writeToPng("test005_demo8.png");
            ScriptContext.plot("vap:ftp://nssdcftp.gsfc.nasa.gov/spacecraft_data/omni/omni2_$Y.dat?column=field17&timeFormat=$Y+$j+$H&time=field0&validMax=999&timerange=1972");
            ScriptContext.writeToPng("test005_demo9.png");
            ScriptContext.plot("http://autoplot.org/data/autoplot.ncml");
            ScriptContext.writeToPng("test005_demo10.png");
            {
                String[] list = org.virbo.jythonsupport.Util.list("http://cdaweb.gsfc.nasa.gov/istp_public/data/");
                PrintWriter out = new PrintWriter("test005_demo11.txt");
                for (String l : list) {
                    out.println(l);
                }
                out.close();
            }
            ScriptContext.plot("http://autoplot.org/data/hsi_qlimg_5050601_001.fits");
            ScriptContext.getDocumentModel().getDataSourceFilters(0).setSliceIndex(2);
            ScriptContext.getDocumentModel().getPlots(0).getZaxis().setRange(DatumRange.newDatumRange(-20e4, 20e4, Units.dimensionless));

            ScriptContext.writeToPng("test005_demo12.png");
            ScriptContext.plot("http://autoplot.org/data/hsi_fsimg_5050612_001.fits");
            ScriptContext.writeToPng("test005_demo13.png");
            ScriptContext.plot("vap:http://goes.ngdc.noaa.gov/data/avg/$Y/A105$y$m.TXT?skip=23&timeFormat=$y$m$d+$H$M&column=E1&time=YYMMDD&validMax=32000&timerange=Dec+2004");
            ScriptContext.writeToPng("test005_demo14.png");

            System.exit(0);  // TODO: something is firing up the event thread
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
