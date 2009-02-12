/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import java.applet.AppletContext;
import java.applet.AppletStub;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
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
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.util.AboutUtil;
import org.das2.util.filesystem.FileObject;
import org.das2.util.filesystem.FileSystem;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.Axis;
import org.virbo.autoplot.dom.Plot;
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
    Application dom;
    static Logger logger = Logger.getLogger("virbo.autoplot.applet");

    private String getStringParameter(String name, String deft) {
        String result = getParameter(name);
        if (result == null) {
            return deft;
        } else {
            return result;
        }
    }

    private int getIntParameter(String name, int deft) {
        String result = getParameter(name);
        if (result == null) {
            return deft;
        } else {
            return Integer.parseInt(result);
        }
    }

    @Override
    public void init() {
        super.init();

        System.err.println("AutoplotApplet 20090209.3");

        model = new ApplicationModel();

        setLayout(new BorderLayout());
        add(model.getCanvas(), BorderLayout.CENTER);
        validate();

    }

    @Override
    public void start() {

        super.start();
        try {
            System.err.println("Formatters: " + DataSourceRegistry.getInstance().getFormatterExtensions());
        } catch (Exception ex) {
            Logger.getLogger(AutoplotApplet.class.getName()).log(Level.SEVERE, null, ex);
        }

        ApplicationModel appmodel = model;
        dom = model.getDocumentModel();

        Object request = null;
        int width = getIntParameter("width", 700);
        int height = getIntParameter("height", 400);
        String font = getStringParameter("font", "");
        String column = getStringParameter("column", "");
        String row = getStringParameter("row", "");
        String scolor = getStringParameter("color", "");
        String srenderType = getStringParameter("renderType", "");
        String stimeRange = getStringParameter("timeRange", "");
        String sfillColor = getStringParameter("fillColor", "");
        String sforegroundColor = getStringParameter("foregroundColor", "");
        String sbackgroundColor = getStringParameter("backgroundColor", "");
        String title = getStringParameter("plot.title", "");
        String xlabel = getStringParameter("plot.xaxis.label", "");
        String xrange = getStringParameter("plot.xaxis.range", "");
        String xlog = getStringParameter("plot.xaxis.log", "");
        String xdrawTickLabels = getStringParameter("plot.xaxis.drawTickLabels", "");
        String ylabel = getStringParameter("plot.yaxis.label", "");
        String yrange = getStringParameter("plot.yaxis.range", "");
        String ylog = getStringParameter("plot.yaxis.log", "");
        String ydrawTickLabels = getStringParameter("plot.yaxis.drawTickLabels", "");
        String zlabel = getStringParameter("plot.zaxis.label", "");
        String zrange = getStringParameter("plot.zaxis.range", "");
        String zlog = getStringParameter("plot.zaxis.log", "");
        String zdrawTickLabels = getStringParameter("plot.zaxis.drawTickLabels", "");

        dom.getOptions().setAutolayout("true".equals(getParameter("autolayout")));
        if (!dom.getOptions().isAutolayout()) {
            if (!row.equals("")) {
                dom.getController().getCanvas().setRow(row);
            }
            if (!column.equals("")) {
                dom.getController().getCanvas().setColumn(column);
            }
        }

        if (!font.equals("")) {
            appmodel.canvas.setBaseFont(Font.decode(font));
        }

        appmodel.getCanvas().setSize(width, height);
        appmodel.getCanvas().revalidate();
        appmodel.getCanvas().setPrintingTag("");

        //if (vap != null) appmodel.doOpen(new File(vap));

        if (sforegroundColor != null && !sforegroundColor.equals("")) {
            appmodel.canvas.setForeground(Color.decode(sforegroundColor));
        }
        if (sbackgroundColor != null && !sbackgroundColor.equals("")) {
            appmodel.canvas.setBackground(Color.decode(sbackgroundColor));
        }


        if (getParameter("select") != null) {
            final DataSetSelector select = new DataSetSelector();

            if (getCodeBase() != null) {
                select.setValue(getCodeBase().toString());
            }
            select.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    System.err.println("actionPerformed()");
                    System.err.println("  " + select.getValue());
                    setDataSetURL(select.getValue());
                }
            });

            add(select, BorderLayout.NORTH);
        }


        // createAppletTester();
        //Logger.getLogger("").setLevel( Level.WARNING );

        String surl = getParameter("url");
        String process = getStringParameter("process", "");
        //String vap = getParameter("vap");
        String script = getStringParameter("script", "");

        if (surl != null && !surl.equals("")) {
            DataSource dsource;
            try {
                dsource = DataSetURL.getDataSource(surl);
            } catch (NullPointerException ex) {
                throw new RuntimeException("No such data source: ", ex);
            } catch (Exception ex) {
                ex.printStackTrace();
                dsource = null;
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

            if (stimeRange != null && !stimeRange.equals("")) {
                try {
                    appmodel.waitUntilIdle(true);
                } catch (InterruptedException ex) {
                    Logger.getLogger(AutoplotApplet.class.getName()).log(Level.SEVERE, null, ex);
                }
                if (UnitsUtil.isTimeLocation(dom.getTimeRange().getUnits())) {
                    dom.setTimeRange(timeRange);
                }
            }

        }


        // axis settings
        Plot p = dom.getController().getPlot();

        if (!title.equals("")) {
            p.setTitle(title);
        }

        Axis axis = p.getXaxis();
        if (!xlabel.equals("")) {
            axis.setLabel(xlabel);
        }
        if (!xrange.equals("")) {
            try {
                Units u = axis.getController().getDasAxis().getUnits();
                DatumRange newRange = DatumRangeUtil.parseDatumRange(xrange, u);
                axis.setRange(newRange);
            } catch (ParseException ex) {
                Logger.getLogger(AutoplotApplet.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        if (!xlog.equals("")) {
            axis.setLog("true".equals(xlog));
        }
        if (!xdrawTickLabels.equals("")) {
            axis.setDrawTickLabels("true".equals(xdrawTickLabels));
        }

        axis = p.getYaxis();
        if (!ylabel.equals("")) {
            axis.setLabel(ylabel);
        }
        if (!yrange.equals("")) {
            try {
                Units u = axis.getController().getDasAxis().getUnits();
                DatumRange newRange = DatumRangeUtil.parseDatumRange(yrange, u);
                axis.setRange(newRange);
            } catch (ParseException ex) {
                Logger.getLogger(AutoplotApplet.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        if (!ylog.equals("")) {
            axis.setLog("true".equals(ylog));
        }
        if (!ydrawTickLabels.equals("")) {
            axis.setDrawTickLabels("true".equals(ydrawTickLabels));
        }

        axis = p.getZaxis();
        if (!zlabel.equals("")) {
            axis.setLabel(zlabel);
        }
        if (!zrange.equals("")) {
            try {
                Units u = axis.getController().getDasAxis().getUnits();
                DatumRange newRange = DatumRangeUtil.parseDatumRange(zrange, u);
                axis.setRange(newRange);
            } catch (ParseException ex) {
                Logger.getLogger(AutoplotApplet.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        if (!zlog.equals("")) {
            axis.setLog("true".equals(zlog));
        }
        if (!zdrawTickLabels.equals("")) {
            axis.setDrawTickLabels("true".equals(zdrawTickLabels));
        }


        if (srenderType != null && !srenderType.equals("")) {
            ApplicationModel.RenderType renderType = ApplicationModel.RenderType.valueOf(srenderType);
            dom.getController().getPanel().setRenderType(renderType);
        }

        if (!srenderType.equals("")) {
            ApplicationModel.RenderType renderType = ApplicationModel.RenderType.valueOf(srenderType);
            dom.getController().getPanel().setRenderType(renderType);
        }

        if (!scolor.equals("")) {
            dom.getController().getPanel().getStyle().setColor(Color.decode(scolor));
        }

        if (!sfillColor.equals("")) {
            dom.getController().getPanel().getStyle().setFillColor(Color.decode(sfillColor));
        }
        if (!sforegroundColor.equals("")) {
            dom.getOptions().setForeground(Color.decode(sforegroundColor));
        }
        if (!sbackgroundColor.equals("")) {
            dom.getOptions().setBackground(Color.decode(sbackgroundColor));
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
                        return;

                    } else if (surl.equals("about:autoplot")) {

                        try {
                            StringBuffer buffy = new StringBuffer();
                            URL aboutHtml = ApplicationModel.class.getResource("aboutAutoplot.html");

                            BufferedReader reader = new BufferedReader(new InputStreamReader(aboutHtml.openStream()));
                            String s = reader.readLine();
                            while (s != null) {
                                buffy.append(s + "");
                                s = reader.readLine();
                            }
                            reader.close();

                            buffy.append("    <h2>Build Information:</h2>");
                            buffy.append("<ul>");
                            buffy.append("<li>release tag: " + AboutUtil.getReleaseTag() + "</li>");

                            List<String> bi = Util.getBuildInfos();
                            for (String ss : bi) {
                                buffy.append("    <li>" + ss + "");
                            }
                            buffy.append("<ul>    </p></html>");

                            JOptionPane.showMessageDialog(AutoplotApplet.this, buffy.toString());
                            return;

                        } catch (IOException iOException) {
                            iOException.printStackTrace();
                        }
                    }
                    System.err.println(surl);
                    model.setDataSourceURL(surl);
                }
            });
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    protected String timeRange;
    public static final String PROP_TIMERANGE = "timeRange";

    public String getTimeRange() {
        return dom.getTimeRange().toString();
    }

    public void setTimeRange(final String timeRange) {
        Runnable run = new Runnable() {

            public void run() {
                try {
                    String oldv = getTimeRange();
                    dom.getController().getPlot().getController().getDasPlot().getXAxis().setDatumRange(DatumRangeUtil.parseTimeRangeValid(timeRange));
                    //dom.setTimeRange(DatumRangeUtil.parseTimeRangeValid(timeRange));
                    propertyChangeSupport.firePropertyChange(PROP_TIMERANGE, oldv, timeRange);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        SwingUtilities.invokeLater(run);
    }
    
    protected String font;
    public static final String PROP_FONT = "canvasFont";

    public String getCanvasFont() {
        return model.getCanvas().getBaseFont().toString();
    }

    public void setCanvasFont(String font) {
        try {
            String oldFont = getCanvasFont();
            model.getCanvas().setBaseFont(Font.decode(font));
            propertyChangeSupport.firePropertyChange(PROP_FONT, oldFont, font);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
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
