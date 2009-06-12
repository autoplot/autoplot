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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.MalformedURLException;
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
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.das2.components.propertyeditor.PropertyEditor;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.graph.DasCanvas;
import org.das2.util.AboutUtil;
import org.das2.util.filesystem.FileObject;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.Axis;
import org.virbo.autoplot.dom.Diff;
import org.virbo.autoplot.dom.DomUtil;
import org.virbo.autoplot.dom.Plot;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetSelector;
import org.virbo.datasource.DataSetSelectorSupport;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceRegistry;
import org.virbo.datasource.capability.TimeSeriesBrowse;
import org.virbo.dsutil.AsciiParser;
import org.virbo.qstream.SerializeDelegate;
import org.virbo.qstream.SerializeRegistry;

/**
 *
 * @author jbf
 */
public class AutoplotApplet extends JApplet {

    ApplicationModel model;
    Application dom;
    boolean initializing = true;
    static Logger logger = Logger.getLogger("virbo.autoplot.applet");
    String statusCallback;
    String timeCallback;
    ProgressMonitor loadInitialMonitor;
    long t0 = System.currentTimeMillis();
    public static final String VERSION = "20090610.2";
    private Image splashImage;

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

    private void setInitializationStatus(String val) {
        if (!statusCallback.equals("")) {
            try {
                getAppletContext().showDocument(new URL("javascript:" + statusCallback + "(\"" + val + "\")"));
            } catch (MalformedURLException ex) {
                Logger.getLogger(AutoplotApplet.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void timeCallback(String val) {
        if (!timeCallback.equals("")) {
            try {
                getAppletContext().showDocument(new URL("javascript:" + timeCallback + "(\"" + val + "\")"));
            } catch (MalformedURLException ex) {
                Logger.getLogger(AutoplotApplet.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void drawString(Graphics g, String s, int x, int y) {
        Color c0 = g.getColor();
        g.setColor(Color.WHITE);
        g.drawString(s, x - 1, y - 1);
        g.setColor(c0);
        g.drawString(s, x, y);
    }

    private ProgressMonitor myMon() {
        return new NullProgressMonitor() {

            @Override
            public void setTaskProgress(long position) throws IllegalArgumentException {
                super.setTaskProgress(position);
                repaint();
            }

            @Override
            public void setTaskSize(long taskSize) {
                super.setTaskSize(taskSize);
                repaint();
            }
        };
    }

    public void paint( Graphics g ) {
        //super.paint(g);
        paintComponent(g);
    }
    
    public void paintComponent(Graphics g1) {
        //System.err.println( "init="+initializing+ " " +this.dom.getController().getCanvas().getController().getDasCanvas().isVisible() + "  " +
        //        ""+ this.dom.getController().getCanvas().getController().getDasCanvas().getBackground() );

        Graphics2D g= (Graphics2D)g1;
        if (initializing) {
            
            super.paint(g);
            g.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );

            int leftJust= 70;
            int em= g.getFontMetrics().getHeight();
            if (splashImage != null) {
                if (!g.drawImage(splashImage, 0, 0, this)) {
                    drawString(g, "loading splash", leftJust, getHeight() / 2 - em);
                }
            }
            drawString(g, "initializing...", leftJust, getHeight() / 2);

            if (loadInitialMonitor != null) {
                Color c0= g.getColor();
                g.setColor( new Color( 0, 0, 255, 200  ) );
                long size = loadInitialMonitor.getTaskSize();
                long pos = loadInitialMonitor.getTaskProgress();
                int x0 = leftJust;
                int y0 = getHeight() / 2 + em/2;
                int w = 100;
                int h = 5;
                if (size == -1) {
                    long t = System.currentTimeMillis() % 2000;
                    int x= (int) (t * w / 2000);
                    int x1= (int) (t * w / 2000 ) + h*2;
                    int ww= x1-x;
                    g.fillRect(x0 + x, y0, Math.min( w-x, x1-x ), h );
                    Timer timer= new Timer( 100, new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            repaint();
                        }
                    } );
                    timer.setRepeats(false);
                    timer.restart();

                } else {
                    if ( pos>size ) pos= size;
                    g.fillRect(x0, y0, (int) (pos * w / size ), h);
                }
                g.setColor(c0);
                g.drawRect( x0, y0, w, h );
            }
        } else {
            super.paint(g);
        }
    }

    @Override
    public void init() {
        super.init();

        String fontParam = getParameter("font");
        if ( fontParam!=null ) {
            Font f= Font.decode(fontParam);
            f= f.deriveFont( f.getSize2D()+2 );
            setFont( f );
        }


        loadInitialMonitor = myMon();
        String si = getStringParameter("splashImage", "");
        if (!si.equals("")) {
            this.splashImage = getImage(getDocumentBase(), si);
            repaint();
        }

        initializing = true;
        System.err.println("init AutoplotApplet " + VERSION + " @ " + (System.currentTimeMillis() - t0) + " msec");

        System.err.println("done init AutoplotApplet " + VERSION + " @ " + (System.currentTimeMillis() - t0) + " msec");

        repaint();

    }

    @Override
    public void destroy() {
        super.destroy();
    }

    @Override
    public void stop() {
        remove(model.getCanvas());
        this.model = null;
    }

    @Override
    public void start() {
        System.err.println("start AutoplotApplet " + VERSION + " @ " + (System.currentTimeMillis() - t0) + " msec");
        super.start();
        
        model = new ApplicationModel();
        model.setApplet(true);
        model.dom.getOptions().setAutolayout(false);

        System.err.println("ApplicationModel created @ " + (System.currentTimeMillis() - t0) + " msec");

        model.addDasPeersToApp();

        System.err.println("done addDasPeersToApp @ " + (System.currentTimeMillis() - t0) + " msec");

        try {
            System.err.println("Formatters: " + DataSourceRegistry.getInstance().getFormatterExtensions());
        } catch (Exception ex) {
            Logger.getLogger(AutoplotApplet.class.getName()).log(Level.SEVERE, null, ex);
        }

        ApplicationModel appmodel = model;
        //dom = (Application) model.getDocumentModel().copy();
        dom = model.getDocumentModel();

        String debug= getParameter("debug");
        if ( debug!=null && !debug.equals("true") ) {
            //TODO:  print all parameters
        }

        int width = getIntParameter("width", 700);
        int height = getIntParameter("height", 400);
        String fontParam = getStringParameter("font", "");
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
        statusCallback = getStringParameter("statusCallback", "");
        timeCallback = getStringParameter("timeCallback", "");

        if ( srenderType.equals("fill_to_zero") ) {
            srenderType= "fillToZero";
        }

        setInitializationStatus("readParameters");
        System.err.println("done readParameters @ " + (System.currentTimeMillis() - t0) + " msec");

        //     appmodel.getCanvas().setVisible(false);
        appmodel.getCanvas().setSize(width, height);
        appmodel.getCanvas().revalidate();
        appmodel.getCanvas().setPrintingTag("");

        dom.getOptions().setAutolayout("true".equals(getParameter("autolayout")));
        if (!dom.getOptions().isAutolayout()) {
            if (!row.equals("")) {
                dom.getController().getCanvas().getController().setRow(row);
            }
            if (!column.equals("")) {
                dom.getController().getCanvas().getController().setColumn(column);
            }
            dom.getCanvases(0).getRows(0).setTop("0%");
            dom.getCanvases(0).getRows(0).setBottom("100%");
        }

        if (!fontParam.equals("")) {
            appmodel.canvas.setBaseFont(Font.decode(fontParam));
        }

        JMenuItem item = new JMenuItem(new AbstractAction("Edit DOM") {

            public void actionPerformed(ActionEvent e) {
                new PropertyEditor(dom).showDialog(AutoplotApplet.this);
            }
        });
        dom.getPlots(0).getController().getDasPlot().getDasMouseInputAdapter().addMenuItem(item);

        /*        item= new JMenuItem( new AbstractAction( "Execute DOM command..." ) {
        public void actionPerformed(ActionEvent e) {
        String command= JOptionPane.showInputDialog("enter command prop=val");
        if ( command==null ) return;
        String[] ss= command.split("=");
        setDomNode(ss[0], ss[1]);
        }
        });
        dom.getPlots(0).getController().getDasPlot().getDasMouseInputAdapter().addMenuItem(item); */

        //if (vap != null) appmodel.doOpen(new File(vap));

        if (sforegroundColor != null && !sforegroundColor.equals("")) {
            appmodel.canvas.setForeground(Color.decode(sforegroundColor));
        }
        if (sbackgroundColor != null && !sbackgroundColor.equals("")) {
            appmodel.canvas.setBackground(Color.decode(sbackgroundColor));
        }

        getContentPane().setLayout(new BorderLayout());

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

            getContentPane().add(select, BorderLayout.NORTH);

        }

        System.err.println("done set parameters @ " + (System.currentTimeMillis() - t0) + " msec");

        // createAppletTester();
        //Logger.getLogger("").setLevel( Level.WARNING );

        String surl = getParameter("url");
        String process = getStringParameter("process", "");
        //String vap = getParameter("vap");
        String script = getStringParameter("script", "");

        if ( surl==null ) {
            surl= getParameter("dataSetURL");
        }
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

            QDataSet ds;
            try {
                ds = dsource==null ? null : dsource.getDataSet(loadInitialMonitor);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }

            appmodel.setDataSource(dsource);

            setInitializationStatus("dataSourceSet");

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
            setInitializationStatus("dataSetLoaded");
        }

        System.err.println("done dataSetLoaded @ " + (System.currentTimeMillis() - t0) + " msec");

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
            try {
                RenderType renderType = RenderType.valueOf(srenderType);
                dom.getController().getPanel().setRenderType(renderType);
            } catch (IllegalArgumentException ex) {
                ex.printStackTrace();
            }
        }

        System.err.println("done setRenderType @ " + (System.currentTimeMillis() - t0) + " msec");

        if (!scolor.equals("")) {
            try {
                dom.getController().getPanel().getStyle().setColor(Color.decode(scolor));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        if (!sfillColor.equals("")) {
            try {
                dom.getController().getPanel().getStyle().setFillColor(Color.decode(sfillColor));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        if (!sforegroundColor.equals("")) {
            try {
                dom.getOptions().setForeground(Color.decode(sforegroundColor));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        if (!sbackgroundColor.equals("")) {
            try {
                dom.getOptions().setBackground(Color.decode(sbackgroundColor));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        surl = getParameter("dataSetURL");
        if (surl != null) {
            if (surl.startsWith("about:")) {
                setDataSetURL(surl);
            } else {
                //dom.getDataSourceFilters(0).setUri(surl);
            }
        }

        getContentPane().add(model.getCanvas(), BorderLayout.CENTER);

        System.err.println("done add to applet @ " + (System.currentTimeMillis() - t0) + " msec");

        validate();

        System.err.println("done applet.validate @ " + (System.currentTimeMillis() - t0) + " msec");

        repaint();
        appmodel.getCanvas().setVisible(true);
        initializing = false;

        repaint();
        System.err.println("ready @ " + (System.currentTimeMillis() - t0) + " msec");
        setInitializationStatus("ready");

        dom.getController().getPlot().getXaxis().addPropertyChangeListener(Axis.PROP_RANGE, new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                timeCallback(String.valueOf(evt.getNewValue()));
            }
        });

        System.err.println("done start AutoplotApplet " + VERSION + " @ " + (System.currentTimeMillis() - t0) + " msec");
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
                    firePropertyChange(PROP_TIMERANGE, oldv, timeRange);
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

    public void setCanvasFont(final String font) {
        Runnable run = new Runnable() {

            public void run() {
                try {
                    String oldFont = getCanvasFont();
                    model.getCanvas().setBaseFont(Font.decode(font));
                    model.getCanvas().repaint();
                    firePropertyChange(PROP_FONT, oldFont, font);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        SwingUtilities.invokeLater(run);
    }

    public void dumpDom() {
        List<Diff> diffs = new Application().diffs(dom);
        for (Diff d : diffs) {
            System.err.println(d);
        }
    }

    public void printDomNode(final String node) {
        Runnable run = new Runnable() {

            public void run() {
                try {
                    Object o = DomUtil.getPropertyValue(dom, node);
                    System.err.println("dom." + node + "=" + o);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };

        SwingUtilities.invokeLater(run);
    }

    public void setDomNode(final String node, final String sval) {
        Runnable run = new Runnable() {

            public void run() {
                try {

                    Class c = DomUtil.getPropertyType(dom, node);

                    SerializeDelegate sd = SerializeRegistry.getDelegate(c);
                    if (sd == null) {
                        System.err.println("unable to find serialize delegate for " + c.getCanonicalName());
                        return;
                    }
                    Object val = sd.parse(sd.typeId(c), sval);

                    DomUtil.setPropertyValue(dom, node, val);

                    System.err.println("dom." + node + "=" + DomUtil.getPropertyValue(dom, node));
                } catch (ParseException ex) {
                    ex.printStackTrace();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };
        SwingUtilities.invokeLater(run);
    }

    /**
     * reset the plot zoom to the initial settings.
     */
    public void resetZoom() {
        Runnable run = new Runnable() {

            public void run() {
                dom.getController().getPlot().getController().resetZoom(true, true, true);
            }
        };
        SwingUtilities.invokeLater(run);
    }

    /**
     * plot the data in the string.
     * @param sdata
     */
    public void plotData(final String fsdata) {
        Runnable run = new Runnable() {

            public void run() {
                try {
                    String sdata = fsdata;
                    AsciiParser p = new AsciiParser();
                    int i = sdata.indexOf(";");
                    if (i != -1) {
                        sdata = sdata.replaceAll(";", "\n");
                        i = sdata.indexOf("\n");
                    }
                    p.guessDelimParser(sdata.substring(0, i));
                    DasCanvas c = dom.getController().getCanvas().getController().getDasCanvas();
                    QDataSet data = p.readStream(new StringReader(sdata), dom.getController().getMonitorFactory().getMonitor(c, "reading data", "reading data"));
                    MutablePropertyDataSet y = DataSetOps.slice1(data, 1);
                    y.putProperty(QDataSet.DEPEND_0, DataSetOps.slice1(data, 0));
                    model.setDataSet(y);
                } catch (IOException ex) {
                    Logger.getLogger(AutoplotApplet.class.getName()).log(Level.SEVERE, null, ex);
                }

            }
        };
        SwingUtilities.invokeLater(run);
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
