/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import org.virbo.autoplot.*;
import java.applet.AppletContext;
import java.applet.AppletStub;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.JApplet;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.UnitsUtil;
import org.das2.util.filesystem.FileObject;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.NullProgressMonitor;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetSelector;
import org.virbo.datasource.DataSetSelectorSupport;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceRegistry;
import org.virbo.datasource.capability.TimeSeriesBrowse;

/**
 *
 * @author jbf
 */
public class AutoplotApplet extends JApplet {

    ApplicationModel model;
    static Logger logger = Logger.getLogger("virbo.autoplot.applet");

    private String getStringParameter( String name, String deft) {
        String result = getParameter(name);
        if (result == null) return deft;
        else return result;
    }

    private int getIntParameter(Object ignore, String name, int deft) {
        String result = getParameter(name);
        if (result == null) return deft;
        else return Integer.parseInt(result);
    }

    @Override
    public void init() {
        super.init();
        
        System.err.println("THIS IS APPLET 1.0.0");

        model = new ApplicationModel();
        
        setLayout(new BorderLayout());
        add(model.getCanvas(), BorderLayout.CENTER);

    }

    @Override
    public void start() {
        
        System.err.println("THIS IS APPLET 1.0.0");
        
        super.start();
        try {
            System.err.println(DataSetURL.getDataSource("/home/jbf/foo.qds"));
            System.err.println("Formatters: "+DataSourceRegistry.getInstance().getFormatterExtensions());
        } catch (Exception ex) {
            Logger.getLogger(AutoplotApplet.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        ApplicationModel appmodel = model;
        
        final DataSetSelector select = new DataSetSelector();
        Object request = null;
        int width = getIntParameter(request, "width", 700);
        int height = getIntParameter(request, "height", 400);
        String font = getStringParameter( "font", "");
        String column = getStringParameter( "column", "");
        String row = getStringParameter( "row", "");
        String scolor = getStringParameter( "color", "");
        String sfillColor = getStringParameter( "fillColor", "");
        String sforegroundColor = getStringParameter( "foregroundColor", "");
        String sbackgroundColor = getStringParameter( "backgroundColor", "");

        if ("true".equals(getParameter("autolayout"))) {
            appmodel.setAutolayout(true);
        } else {
            if (!row.equals("")) try {
                    AutoplotUtil.setDevicePosition(appmodel.plot.getRow(), row);
                } catch (ParseException ex) {
                    Logger.getLogger(AutoplotApplet.class.getName()).log(Level.SEVERE, null, ex);
                }
            if (!column.equals("")) try {
                    AutoplotUtil.setDevicePosition(appmodel.plot.getColumn(), column);
                } catch (ParseException ex) {
                    Logger.getLogger(AutoplotApplet.class.getName()).log(Level.SEVERE, null, ex);
                }
        }

        if (!font.equals("")) appmodel.canvas.setBaseFont(Font.decode(font));

        appmodel.getCanvas().setSize(width, height);
        appmodel.getCanvas().revalidate();
        appmodel.getCanvas().setPrintingTag("");

        //if (vap != null) appmodel.doOpen(new File(vap));


        if ( scolor!=null && !scolor.equals("")) {
            appmodel.seriesRend.setColor(Color.decode(scolor));
        }

        if ( sfillColor!=null && !sfillColor.equals("")) {
            appmodel.seriesRend.setFillColor(Color.decode(sfillColor));
        }
        if ( sforegroundColor!=null && !sforegroundColor.equals("")) {
            appmodel.canvas.setForeground(Color.decode(sforegroundColor));
        }
        if ( sbackgroundColor!=null && !sbackgroundColor.equals("")) {
            appmodel.canvas.setBackground(Color.decode(sbackgroundColor));
        }



        if (getCodeBase() != null) select.setValue(getCodeBase().toString());
        select.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                System.err.println("actionPerformed()");
                System.err.println("  " + select.getValue());
                setDataSetURL(select.getValue());
            }
        });

        add(select, BorderLayout.NORTH);


    // createAppletTester();
    //Logger.getLogger("").setLevel( Level.WARNING );
        
        String srenderType = getStringParameter( "renderType", "");
        String stimeRange = getStringParameter( "timeRange", "");
        String surl = getParameter("url");
        String process = getStringParameter( "process", "");
        //String vap = getParameter("vap");
        String script = getStringParameter( "script", "");
                
        if ( surl!=null && !surl.equals("")) {
            DataSource dsource;
            try {
                System.err.println("THIS IS APPLET 1.0.0");
                
                dsource = DataSetURL.getDataSource(surl);
            } catch (NullPointerException ex) {
                throw new RuntimeException("No such data source: ", ex);
            } catch (Exception ex) {
                ex.printStackTrace();
                dsource= null;
            }

            DatumRange timeRange = null;
            if (!stimeRange.equals("")) {
                timeRange = DatumRangeUtil.parseTimeRangeValid(stimeRange);
                TimeSeriesBrowse tsb = dsource.getCapability(TimeSeriesBrowse.class);
                if (tsb != null) {
                    tsb.setTimeRange(timeRange);
                }
            }

            appmodel.setDataSource(dsource);

            if (stimeRange!=null && !stimeRange.equals("")) {
                try {
                    appmodel.waitUntilIdle(true);
                } catch (InterruptedException ex) {
                    Logger.getLogger(AutoplotApplet.class.getName()).log(Level.SEVERE, null, ex);
                }
                if (UnitsUtil.isTimeLocation(appmodel.getPlot().getXAxis().getUnits())) {
                    appmodel.getPlot().getXAxis().setDatumRange(timeRange);
                }
            }

        }

        if ( srenderType!=null && !srenderType.equals("")) {
            ApplicationModel.RenderType renderType = ApplicationModel.RenderType.valueOf(srenderType);
            appmodel.setRenderType(renderType);
        }
        
        surl = getParameter("dataSetURL");
        if (surl != null) {
            setDataSetURL(surl);
        }
    }

    private void createAppletTester() {
        JFrame frame = new JFrame();
        JButton button = new JButton(new AbstractAction("pushme") {

            public void actionPerformed(ActionEvent e) {
                URL url = getCodeBase();
                String surl = "" + url.toString() + "Capture_00158.jpg?channel=red";
                System.err.println("************************************************");
                System.err.println("************************************************");
                System.err.println(surl);
                System.err.println("************************************************");
                System.err.println("************************************************");
                setDataSetURL(surl);
            // setDataSetURL("file:/media/mini/data.backup/examples/jpg/Capture_00158.jpg?channel=red");
            // testDownload();
            }
        });
        frame.getContentPane().add(button);
        frame.pack();
        frame.setVisible(true);
    }

    private void testDownload() {
        try {
            FileSystem fs = FileSystem.create(new URL("http://www.das2.org/wiki/data/"));
            String[] files = fs.listDirectory("/");
            FileObject fo = fs.getFileObject("afile.dat");

            BufferedReader r = new BufferedReader(new InputStreamReader(fo.getInputStream()));

            String s = r.readLine();
            while (s != null) {
                System.err.println(s);
                s = r.readLine();
            }
        } catch (IOException ex) {
            Logger.getLogger(AutoplotApplet.class.getName()).log(Level.SEVERE, null, ex);
        }



    }

    public void setDataSetURL(final String surl) {
        try {
            logger.info(surl);
            System.err.println("***************");
            SwingUtilities.invokeLater(new Runnable() {

                public void run() {
                    if (surl.equals("about:plugins")) {
                        String text = DataSetSelectorSupport.getPluginsText();
                        JOptionPane.showMessageDialog(AutoplotApplet.this, text);
                    }
                    System.err.println(surl);
                    model.setDataSourceURL(surl);
                }
            });
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("autoplot applet");
        AppletStub stub = new AppletStub() {

            Map<String, String> params = new HashMap<String, String>();
            

            {
                //"tsds.http://timeseries.org/get.cgi?StartDate=19890101&EndDate=19890101&ext=bin&out=tsml&ppd=1440&param1=SourceAcronym_Subset3-1-v0";
                params.put("dataSetURL", "http://www.sarahandjeremy.net/jeremy/1wire/data/2008/0B000800408DD710.20080106.d2s");
                params.put("column", "5em,100%-10em");
                params.put("font", "sans-8");
                params.put("row", "3em,100%-3em");
                params.put("renderType", "fill_to_zero");
                params.put("color", "#0000ff");
                params.put("fillColor", "#aaaaff");
                params.put("foregroundColor", "#ffffff");
                params.put("backgroundColor", "#000000");

            }

            public boolean isActive() {
                return true;
            }

            public URL getDocumentBase() {
                return null;
            }

            public URL getCodeBase() {
                return null;
            }

            public String getParameter(String name) {
                return params.get(name);
            }

            public AppletContext getAppletContext() {
                return null;
            }

            public void appletResize(int width, int height) {
            }
        };

        AutoplotApplet applet = new AutoplotApplet();
        applet.setStub(stub);

        Dimension size = new Dimension(400, 300);
        applet.setPreferredSize(size);
        frame.getContentPane().add(applet);
        frame.pack();
        applet.init();
        applet.start();
        frame.setVisible(true);
    }
}
