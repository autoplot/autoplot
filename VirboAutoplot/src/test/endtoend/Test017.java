/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.virbo.autoplot.ScriptContext.*;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;
import org.virbo.jythonsupport.Util;

/**
 * giant list of URIs for testing.  These are generally URIs that caused problems in the past, so
 * they represent corner cases that may cause problems in the future.
 * @author jbf
 */
public class Test017 {

    static long t0 = System.currentTimeMillis();

    public static void xxx(String id) {
        System.err.println("timer: in " + (System.currentTimeMillis() - t0) + "ms finished " + id  );
        t0 = System.currentTimeMillis();
    }

    public static void main(String[] args)  {

        try {

        int count;

        setCanvasSize(750, 300);
        getDocumentModel().getOptions().setAutolayout(false);
        getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");
        getDocumentModel().getCanvases(0).getMarginColumn().setLeft("5em");
        getDocumentModel().getCanvases(0).getMarginRow().setTop("2em");
        getDocumentModel().getCanvases(0).getMarginRow().setBottom("100%-2em");

        xxx("start");

        ThreadPoolExecutor exec= new ThreadPoolExecutor(12,12,3600,TimeUnit.SECONDS, new SynchronousQueue<Runnable>() );

        for (String s : uris) {

            count= Integer.parseInt( s.substring(0,4).trim() );
            s= s.substring(4);

            String label = String.format("test017_%03d", count);

            try {
                
                if (s.startsWith("CC ")) {
                    String[] list = org.virbo.jythonsupport.Util.listDirectory(s.substring(3));
                    PrintWriter out = new PrintWriter( label+".txt" );
                    for (String l : list) {
                        out.println(l);
                    }
                    out.close();
                } else if (s.contains("file:/") && !s.contains("/home/jbf/ct/hudson") ) {
                    // we'll just skip these odd local file references for now.
                    System.err.println("skipping local " + s);
                } else {
                    doTest( s, label,exec );

                }
            } catch (Exception ex) {
                PrintWriter pw = new PrintWriter(label + ".error");
                pw.println(s);
                pw.println("");
                ex.printStackTrace(pw);

                pw.close();

                ex.printStackTrace();
                
            }

            xxx( label + ": "+ s );

        }
        } catch ( Exception ex ) {
            ex.printStackTrace();
            System.exit(1);
        }
        System.exit(0);  // TODO: something is firing up the event thread
    }
    
    static String[] uris = new String[]{
        //[edit] 1 Tsds
        "001 vap+tsds:http://timeseries.org/get.cgi?StartDate=19890101&EndDate=19890101&ext=bin&out=tsml&ppd=1440&param1=SourceAcronym_Subset3-1-v0",
        "002 vap+tsds:http://timeseries.org/get.cgi?StartDate=19950101&EndDate=19950104&ext=bin&out=tsml&ppd=1440&param1=OMNI_OMNIHR-22-v0",
        //(this loads:
        "003 vap+tsds:http://timeseries.org/get.cgi?StartDate=19950101&EndDate=19950109&ppd=144&out=tsml&param1=OMNI_OMNIHR-22-v0",
        "004 vap+tsds:http://timeseries.org/get.cgi?StartDate=19950101&EndDate=19950104&ext=bin&out=tsml&ppd=144&param1=OMNI_OMNIHR-22-v0",
        "005 vap+tsds:http://timeseries.org/OMNI_OMNIHR-22-v0-to_19950101-tf_19950104-ppd_144-filter_0-ext_bin.bin",
        //[edit] 2 Das2Server

        //Autoplot gets confused about the escaping. "vap+das2server" turns into "vap das2server" and the das2Server file part is removed. This probably has something to do with its TimeSeriesBrowse capability.

        "006 vap+das2server:http://www-wbd.physics.uiowa.edu/das/das2Server?dataset=das2_1/cluster/wbd/r_wbd&start_time=2007-04-17T08:40Z&end_time=2007-04-17T08:50Z&spacecraft=c1&mode=DSN&antenna=Any&frequencyOffset=Any&fftSize=1024",
        //Fails to use log for z-axis:

        "007 vap+das2server:http://www-wbd.physics.uiowa.edu/das/das2Server?dataset=das2_1/cluster/wbd/r_wbd&start_time=2007-04-17T08:40Z&end_time=2007-04-17T08:50Z&spacecraft=c1&mode=DSN&antenna=Ey&frequencyOffset=Any&fftSize=1024",
        //[edit] 3 CDF

        "008 http://cdaweb.gsfc.nasa.gov/istp_public/data/canopus/mari_mag/1994/cn_k0_mari_19940122_v01.cdf?Epoch",
        "009 http://cdaweb.gsfc.nasa.gov/istp_public/data/canopus/bars/%Y/cn_k0_bars_%Y%m%d_v...cdf?E_vel&timerange=1993-01-02+through+1993-01-14",
        "010 CC ftp://cdaweb.gsfc.nasa.gov/pub/data/imp/imp8/mag_15sec/1973/i8_15sec_mag_19731030_v02.cdf",
        //No data is drawn:
        "011 ftp://cdaweb.gsfc.nasa.gov/pub/data/themis/tha/l2/fgm/2007/tha_l2_fgm_20070224_v01.cdf?tha_fgh_gse",
        //IndexOutOfBoundsException:
        "012 http://cdaweb.gsfc.nasa.gov/istp_public/data/cluster/c2/pp/cis/2003/c2_pp_cis_20030104_v02.cdf?N_p__C2_PP_CIS",
        //Suspect problem identifying valid data: "" +
        "013 http://cdaweb.gsfc.nasa.gov/istp_public/data/cluster/c2/pp/fgm/2003/c2_pp_fgm_20030114_v01.cdf?Epoch__C2_PP_FGM",
        //Fails to guess cadence:
        "014 ftp://cdaweb.gsfc.nasa.gov/pub/data/imp/imp8/mag/mag_15sec_cdaweb/2000/i8_15sec_mag_20000101_v03.cdf?F1_Average_B_15s",
        //Strange message:

        //java.lang.RuntimeException: java.lang.IllegalArgumentException: not supported: Lo E PD
        //at org.virbo.autoplot.ApplicationModel.resetDataSetSourceURL(ApplicationModel.java:249)

        "015 ftp://cdaweb.gsfc.nasa.gov/pub/data/lanl/97_spa/2005/l7_k0_spa_20050405_v01.cdf?spa_p_dens",
        //This is described in bug https://sourceforge.net/tracker2/index.php?func=detail&aid=2620088&group_id=199733&atid=970682",

        //No data is displayed:
        "016 ftp://cdaweb.gsfc.nasa.gov/pub/data/themis/tha/l2/fgm/2007/tha_l2_fgm_20070224_v01.cdf?tha_fgh_gse", // This is corrected and will be released soon. The problem was the "COMPONENT_0" conventions used for Themis lead to the timetags being interpretted as invalid.

        //Vectors plotted as spectrogram:
        "017 ftp://cdaweb.gsfc.nasa.gov/pub/data/geotail/def_or/1995/ge_or_def_19950101_v02.cdf?GSE_POS",
        //Works fine, but nicely demonstrates AutoHistogram's robust statistics and the potential to indentify fill values automatically:
        "018 ftp://cdaweb.gsfc.nasa.gov/pub/data/geotail/mgf/1998/ge_k0_mgf_19980102_v01.cdf?IB",
        //[edit] 4 OpenDAP

        //Rank 2 spectrogram over OpenDAP:
        "019 http://cdaweb.gsfc.nasa.gov/cgi-bin/opendap/nph-dods/istp_public/data/polar/hyd_h0/2002/po_h0_hyd_20020110_v01.cdf.html?ELECTRON_DIFFERENTIAL_ENERGY_FLUX",
        //[edit] 5 FITS

        //This fails because negative CADENCE and MONOTONIC=true.

        //"020 vap:http://www.astro.princeton.edu/~frei/Gcat_htm/Catalog/Fits/n4013_lR.fits",
        //[edit] 6 ASCII

        //Autoplot doesn't download this URL:

        "021 vap+bin:ftp://ftp.nmh.ac.uk/wdc/obsdata/hourval/single_year/1909/clh1909.wdc",
        //java.lang.IllegalArgumentException: unable to identify time format for 1990-11-05T16:31:00.000Z

        "022 vap+dat:http://www.igpp.ucla.edu/cache2/GOMA_3001/DATA/SUMMARY/E1_SUMM_GSE_GSM.TAB?timeFormat=ISO8601&column=field1",
        //This demonstrates fractional day of year:

        "023 vap+dat:http://wind.nasa.gov/swe_apbimax/wi_swe_fc_apbimax.1995005.txt?comment=;&column=21&timeFormat=$Y+$j&time=field0",
        //"Fill string is recognized, -1e31 is inserted, but this is not marked as fill:"

        "024 vap+dat:http://goes.ngdc.noaa.gov/data/avg/$Y/A105$y$m.TXT?skip=23&timeFormat=$y$m$d+$H$M&column=E1&time=YYMMDD&fill=32700&timerange=Dec+2004",
        //I'd expect this to read in the column as a rank 1 dataset:

//        "025 http://www-pw.physics.uiowa.edu/~jbf/L1times.2.dat?fixedColumns=29-35",
        //And this gets a null pointer exception in AsciiParser.getFieldIndex line 1024:

//        "026 http://www-pw.physics.uiowa.edu/~jbf/L1times.2.dat?fixedColumns=0-24,29-35&column=field1",
        //Very large with $b and ${skip}:

        "027 http://vho.nasa.gov/mission/soho/celias_pm_30sec/2003.txt",
        //High resolution OMNI data:

        "028 vap+dat:ftp://nssdcftp.gsfc.nasa.gov/spacecraft_data/omni/high_res_omni/monthly_1min/omni_min200101.asc?time=field0&column=field14&timeFormat=$Y+$j+$H+$M&validMax=9999",
        //Comment parameter used:

        "029 http://wind.nasa.gov/swe_apbimax/wi_swe_fc_apbimax.2001017.txt?column=field2&comment=;&time=field0&timeFormat=$Y+$j",
        //"Value must not be negative". No feedback on line number:

        "030 http://vho.nasa.gov/mission/soho/celias_pm_30sec/1998.txt?time=YY&column=GSE_X&timeFormat=$y+$b+$d+$(ignore):$H:$M:$S",
        //From VHO:

        "031 ftp://nis-ftp.lanl.gov/pub/projects/genesis/3dmom/gim-3dl2-2002-01_v02.txt?skip=68&time=field0&timeFormat=$Y+$j+$H+$M+$S&column=field8&fill=-9999.0",
        //[edit] 7 Excel

        //German umlaut is not handled when creating column name:


//comment because of SVN.  Need to get from wiki to get umlaut.        "032 file:/media/mini/data.backup/examples/xls/2.25_carbopol_summary.xls?firstRow=51&sheet=125 um hifreq 2&depend0=Frequenz&column=Komplexe_Viskosit<umlautA>t",
        //[edit] 8 Aggregation

        //Here is aggregation with CDF subsampling. This has issues. It doesn't reload when I change the parameter.

        "033 http://cdaweb.gsfc.nasa.gov/istp_public/data/omni/hro_5min/%Y/omni_hro_5min_%Y%m%d_v...cdf?HR[::100]&timerange=1995+to+2000",
        //No feedback when using with openDAP:

        "034 http://cdaweb.gsfc.nasa.gov/cgi-bin/opendap/nph-dods/istp_public/data/polar/vis/%Y/po_k0_vis_%Y%m%d_v...cdf.html?Epoch&timerange=2001",
        //"Fails to Identify as aggregation:"

        "035 vap+txt:file:///opt/project/galileo/data/lrsudr/g7/eden/pws$y$j.data?timeRange=1997-049",
        //Though "Dec 2004" is requested, "Nov 2004 through Jan 2005" is loaded:

        "036 vap+dat:http://goes.ngdc.noaa.gov/data/avg/$Y/A105$y$m.TXT?skip=23&timeFormat=$y$m$d+$H$M&column=E1&time=YYMMDD&fill=32700&timerange=Dec+2004",
        //[edit] 9 File System Completions

        //fails:

        "037 CC ftp://ftp.nmh.ac.uk/wdc/obsdata/hourval/single_year/",
        //fails to read zip, but does read locally:

        "038 http://www-pw.physics.uiowa.edu/helios/data1/data/average/a7510-12.zip/av751229.dat?depend0=field0&rank2=1:",
        //perhaps one day this will work:

        "040 http://www-pw.physics.uiowa.edu/helios/data1/data/average/a$y$m-...zip/av$y$m$d.dat?rank2=1:&time=field0&timerange=1975-oct",
        //[edit] 10 Data Source Completions
        //[edit] 11 URIs with difficult CADENCE

        //The Time parameter is irregular:

        "041 file:///media/mini/data.backup/examples/xls/2008-lion%20and%20tiger%20summary.xls?sheet=Samantha+tiger+lp+lofreq&column=Elastic_Modulus&firstRow=53&depend0=Time",
        //[edit] 12 Issues with URIs

        //It would be nice to support plus notation with excel spreadsheets. Also, this shows an issue with the excel data source which

        "042 file:///Documents%20and%20Settings/sklemuk/Desktop/UCSF%20Voice%20Conference%202008/Product%20Summary.xls?sheet=nist%20lo&column=H",
        //This works:
        "043 file:///c:/Documents+and+Settings/jbf/Desktop/Product+Summary.xls?sheet=nist+lo&column=H",
        //<message>go file:///Documents%20and%20Settings/sklemuk/Desktop/UCSF%20Voice%20Conference%202008/Product%20Summary.xls?sheet=nist%20lo&column=H</message>
        //<message>java.lang.NullPointerException
        "044 file:///Documents%20and%20Settings/sklemuk/Desktop/UCSF%20Voice%20Conference%202008/Product%20Summary.xls?sheet=nist%20lo&column=H",
        //at org.virbo.excel.ExcelSpreadsheetDataSource.getDataSet(ExcelSpreadsheetDataSource.java:89) at org.virbo.autoplot.ApplicationModel.loadDataSet(ApplicationModel.java:1322) at org.virbo.autoplot.ApplicationModel.updateImmediately(ApplicationModel.java:1260) at org.virbo.autoplot.ApplicationModel.access$600(ApplicationModel.java:112) at org.virbo.autoplot.ApplicationModel$8.run(ApplicationModel.java:1293) at org.das2.system.RequestProcessor$Runner.run(RequestProcessor.java:201) at java.lang.Thread.run(Unknown Source)
        //[edit] 13 Miscellaneous URIs

        //Demonstrates problem with AutoHistogram:

        "045 http://goes.ngdc.noaa.gov/data/avg/2004/A1050402.TXT",
        //[edit] 14 VAPs in the wild

        //VAP files are Autoplot configuration files, an xml version of the DOM tree. I'd expect these to be very fragile right now, but I'll try to support them:

        //From VMO, the data here contains the search date, but the time axis is not properly located:

        //"046 http://vmo.nasa.gov/vxotmp/vap/VMO/Granule/OMNI/PT1H/omni2_1994.vap",

        //vaps with modifiers and recent ISO8601 parsing.
        "050 file:/home/jbf/ct/hudson/vap/cdaweb_ace.vap?timerange=2010-10-20+12:00+to+18:00",
        "051 file:/home/jbf/ct/hudson/vap/cdaweb_ace.vap?timerange=2010-10-20T12:00/2010-10-20T18:00",
        "052 file:/home/jbf/ct/hudson/vap/cdaweb_ace.vap?timerange=2010-10-20T12:00/PT6H",

        // these are known to cause faults
        "100 file:///home/jbf/ct/hudson/data/csv/pw/2011_08_23T20.hrs_rec.coeff.csv?depend0=SCET&column=Pkt%20%26%20Src%20ID", //No Datum exists for this ordinal: -1

    };

    private static Runnable getRunnable( final String uri, final String id ) {
        Runnable run= new Runnable() {
            public void run()  {
                try {
                    QDataSet ds;
                    ds = Util.getDataSet(uri);
                    MutablePropertyDataSet hist = (MutablePropertyDataSet) Ops.autoHistogram(ds);
                    hist.putProperty(QDataSet.TITLE, uri);
                    hist.putProperty(QDataSet.LABEL, id);
                    formatDataSet(hist, id + ".qds");
                    QDataSet dep0 = (QDataSet) ds.property(QDataSet.DEPEND_0);
                    if (dep0 != null) {
                        MutablePropertyDataSet hist2 = (MutablePropertyDataSet) Ops.autoHistogram(dep0);
                        formatDataSet(hist2, id + ".dep0.qds");
                    } else {
                        PrintWriter pw = new PrintWriter(id + ".dep0.qds");
                        pw.println("no dep0");
                        pw.close();
                    }
                    plot(ds);
                    setCanvasSize(750, 300);
                    int i = uri.lastIndexOf("/");
                    setTitle(uri.substring(i + 1));
                    writeToPng(id + ".png");

                } catch (Exception ex) {
                    TestSupport.logger.log(Level.SEVERE, null, ex);
                }
            }
        };
        return run;
    }

    private static void doTest( final String uri, final String id, ThreadPoolExecutor exec ) throws IOException, InterruptedException, Exception {

        System.err.printf( "== %s ==\n", id );
        System.err.printf( "uri: %s\n", uri );
        
        Runnable run= getRunnable( uri, id );
        int timeoutSeconds= 180;

        Future f=null;
        while (true) {
            try {
                f = exec.submit(run, "Success!");
                break;
            }
            catch (RejectedExecutionException ex) {
                if (exec.isShutdown()) break;
                System.err.println("Thread pool is full. Retrying...");
                Thread.sleep(100);
            }
        }

        if ( f!=null && "Success!".equals(f.get(  timeoutSeconds, TimeUnit.SECONDS ) ) ) { //findbugs wrong
            System.err.println("okay!");
        } else {
            PrintWriter pw = new PrintWriter(id + ".error");
            pw.println(uri);
            pw.println("\ntimeout in "+timeoutSeconds+" seconds.");

            pw.close();
        }


    }

}
