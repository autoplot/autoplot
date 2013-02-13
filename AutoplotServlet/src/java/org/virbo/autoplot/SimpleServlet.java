/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.logging.Logger;
import org.das2.util.DasPNGConstants;
import org.das2.util.DasPNGEncoder;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.graph.DasCanvas;
import org.das2.graph.Painter;
import org.das2.system.DasLogger;
import org.das2.util.AboutUtil;
import org.das2.util.TimerConsoleFormatter;
import org.das2.util.awt.GraphicsOutput;
import org.das2.util.monitor.NullProgressMonitor;
import org.python.util.PythonInterpreter;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.Axis;
import org.virbo.autoplot.dom.Plot;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetSelectorSupport;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceRegistry;
import org.virbo.datasource.capability.TimeSeriesBrowse;
import org.virbo.dsops.Ops;

/**
 *
 * @author jbf
 */
public class SimpleServlet extends HttpServlet {

    private static final Logger logger= Logger.getLogger("autoplot.servlet" );
    private static final String version= "v20130125.1705";

    static FileHandler handler;

    private static void addHandlers(long requestId) {
        try {
            FileHandler h = new FileHandler("/tmp/testservlet/log" + requestId + ".txt");
            TimerConsoleFormatter form = new TimerConsoleFormatter();
            form.setResetMessage("getImage");
            h.setFormatter(form);
            h.setLevel(Level.ALL);
            DasLogger.addHandlerToAll(h);
            if (handler != null) handler.close();
            handler = h;
        } catch (IOException ex) {
            Logger.getLogger(SimpleServlet.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SecurityException ex) {
            Logger.getLogger(SimpleServlet.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /** 
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     * @param request servlet request
     * @param response servlet response
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        logger.fine( version );

        logger.fine("=======================");

        //register java cdf as .cdf handler
        if ( DataSourceRegistry.getInstance().getSource("cdf")==null ) {
            DataSourceRegistry.getInstance().registerExtension( "org.virbo.cdf.CdfJavaDataSourceFactory", "cdf", "cdf files read by the Java CDF reader" );
        }

        long t0 = System.currentTimeMillis();
        String suniq = request.getParameter("requestId");
        long uniq;
        if (suniq == null) {
            uniq = (long) (Math.random() * 100);
        } else {
            uniq = Long.parseLong(suniq);
            addHandlers(uniq);
        }

        String debug = request.getParameter("debug");
        if ( debug==null ) debug= "true";

        logit("-- new request " + uniq, t0, uniq, debug);
        try {

            String surl = request.getParameter("url");
            String suri = request.getParameter("uri");
            if ( suri!=null ) surl= suri;
            String process = ServletUtil.getStringParameter(request, "process", "");
            String vap = request.getParameter("vap");
            String script = ServletUtil.getStringParameter(request, "script", "");
            int width = ServletUtil.getIntParameter(request, "width", -1);
            int height = ServletUtil.getIntParameter(request, "height", -1);
            String scanvasAspect = ServletUtil.getStringParameter(request, "canvas.aspect", "");
            String format = ServletUtil.getStringParameter(request, "format", "image/png");
            String font = ServletUtil.getStringParameter(request, "font", "");
            String column = ServletUtil.getStringParameter(request, "column", "");
            String row = ServletUtil.getStringParameter(request, "row", "");
            String srenderType = ServletUtil.getStringParameter(request, "renderType", "");
            String stimeRange = ServletUtil.getStringParameter(request, "timeRange", "");
            String scolor = ServletUtil.getStringParameter(request, "color", "");
            String sfillColor = ServletUtil.getStringParameter(request, "fillColor", "");
            String sforegroundColor = ServletUtil.getStringParameter(request, "foregroundColor", "");
            String sbackgroundColor = ServletUtil.getStringParameter(request, "backgroundColor", "");
            String title = ServletUtil.getStringParameter(request, "plot.title", "");
            String xlabel = ServletUtil.getStringParameter(request, "plot.xaxis.label", "");
            String xrange = ServletUtil.getStringParameter(request, "plot.xaxis.range", "");
            String xlog = ServletUtil.getStringParameter(request, "plot.xaxis.log", "");
            String xdrawTickLabels = ServletUtil.getStringParameter(request, "plot.xaxis.drawTickLabels", "");
            String ylabel = ServletUtil.getStringParameter(request, "plot.yaxis.label", "");
            String yrange = ServletUtil.getStringParameter(request, "plot.yaxis.range", "");
            String ylog = ServletUtil.getStringParameter(request, "plot.yaxis.log", "");
            String ydrawTickLabels = ServletUtil.getStringParameter(request, "plot.yaxis.drawTickLabels", "");
            String zlabel = ServletUtil.getStringParameter(request, "plot.zaxis.label", "");
            String zrange = ServletUtil.getStringParameter(request, "plot.zaxis.range", "");
            String zlog = ServletUtil.getStringParameter(request, "plot.zaxis.log", "");
            String zdrawTickLabels = ServletUtil.getStringParameter(request, "plot.zaxis.drawTickLabels", "");
            String grid= ServletUtil.getStringParameter( request, "drawGrid", "" );

            if (debug != null && !debug.equals("false")) {
                for (Enumeration en = request.getParameterNames(); en.hasMoreElements();) {
                    String n = (String) en.nextElement();
                    logger.log( Level.FINER, "{0}: {1}", new Object[]{n, Arrays.asList(request.getParameterValues(n))});
                }
            }

            if (srenderType.equals("fill_to_zero")) {
                srenderType = "fillToZero";
            }

            OutputStream out = response.getOutputStream();

            // To support load balancing, insert the actual host that resolved the request
            response.setHeader( "X-Served-By", java.net.InetAddress.getLocalHost().getCanonicalHostName() );
            response.setHeader( "X-Server-Version", version );
            if ( surl!=null ) {
                response.setHeader( "X-Autoplot-URI", surl );
            }

            if (vap != null) {
                response.setContentType(format);
            } else if ( surl==null ) {
                response.setContentType("text/html");
                response.setStatus(400);
                out.write(("Either vap= or url= needs to be specified:<br>"+request.getRequestURI()+"?"+request.getQueryString()).getBytes());
                out.close();
                return;
            } else if (surl.equals("about:plugins")) {
                response.setContentType("text/html");
                out.write(DataSetSelectorSupport.getPluginsText().getBytes());
                out.close();
                return;
            } else if (surl.equals("about:autoplot")) {
                response.setContentType("text/html");
                String s = AboutUtil.getAboutHtml();
                s = s.substring(0, s.length() - 7);
                s = s + "<br><br>servlet version="+version+"<br></html>";
                out.write(s.getBytes());
                out.close();
                return;
            } else {
                response.setContentType(format);
            }

            logit("get parameters", t0, uniq, debug);

            System.setProperty("java.awt.headless", "true");

            ApplicationModel appmodel = new ApplicationModel();
            appmodel.addDasPeersToAppAndWait();

            logit("create application model", t0, uniq, debug);

            Application dom = appmodel.getDocumentModel();

            if ("true".equals(request.getParameter("autolayout"))) {
                dom.getOptions().setAutolayout(true);
            } else {
                dom.getOptions().setAutolayout(false);
                if (!row.equals("")) {
                    dom.getController().getCanvas().getController().setRow(row);
                } else {
                    //dom.getController().getCanvas().getController().setRow(row);
                }
                if (!column.equals("")) {
                    dom.getController().getCanvas().getController().setColumn(column);
                } else {
                    dom.getController().getCanvas().getController().setColumn("10em,100%-10em");
                }
                dom.getCanvases(0).getRows(0).setTop("0%");
                dom.getCanvases(0).getRows(0).setBottom("100%");
            }

            if (!font.equals(""))
                appmodel.getCanvas().setBaseFont(Font.decode(font));


            // do dimensions
            if ("".equals(scanvasAspect)) {
                if (width == -1) width = 700;
                if (height == -1) height = 400;
            } else {
                double aspect = Units.dimensionless.parse(scanvasAspect).doubleValue(Units.dimensionless);
                if (width == -1 && height != -1)
                    width = (int) (height * aspect);
                if (height == -1 && width != -1)
                    height = (int) (width / aspect);
            }
            if (vap == null) {
                dom.getController().getCanvas().setWidth(width);
                dom.getController().getCanvas().setHeight(height);
                DasCanvas c = dom.getController().getCanvas().getController().getDasCanvas();
                c.prepareForOutput(width, height); // KLUDGE, resize all components for TimeSeriesBrowse
            }

            logit("set canvas parameters", t0, uniq, debug);

            if (vap != null) {
                appmodel.resetDataSetSourceURL( vap, new NullProgressMonitor() );
                //appmodel.doOpen(new File(vap));
                logit("opened vap", t0, uniq, debug);
                width = appmodel.dom.getCanvases(0).getWidth();
                height = appmodel.dom.getCanvases(0).getHeight();
                DasCanvas c = dom.getController().getCanvas().getController().getDasCanvas();
                c.prepareForOutput(width, height); // KLUDGE, resize all components for TimeSeriesBrowse
            }

            if (surl != null && !"".equals(surl)) {
                DataSource dsource;
                try {
                    dsource = DataSetURI.getDataSource(surl);
                } catch (NullPointerException ex) {
                    throw new RuntimeException("No such data source: ", ex);
                } catch (Exception ex) {
                    throw ex;
                }
                logit("got data source", t0, uniq, debug);

                DatumRange timeRange = null;
                if (!stimeRange.equals("")) {
                    timeRange = DatumRangeUtil.parseTimeRangeValid(stimeRange);
                    TimeSeriesBrowse tsb = dsource.getCapability(TimeSeriesBrowse.class);
                    if (tsb != null) {
                        tsb.setTimeRange(timeRange);
                        logit("timeSeriesBrowse got data source", t0, uniq, debug);
                    }
                }

                if (!process.equals("")) {
                    QDataSet r = dsource.getDataSet(new NullProgressMonitor());
                    logit("done with read", t0, uniq, debug);
                    if (process.equals("histogram")) {
                        appmodel.setDataSet(Ops.histogram(r, 100));
                    } else if (process.equals("magnitude(fft)")) {
                        r = Ops.magnitude(Ops.fft(r));
                        appmodel.setDataSet(r);
                    } else if (process.equals("nop")) {
                        appmodel.setDataSet(r);
                    }
                    logit("done with process", t0, uniq, debug);
                } else {
                    appmodel.setDataSource(dsource);
                    logit("done with setDataSource", t0, uniq, debug);
                }

                if (!stimeRange.equals("")) {
                    appmodel.waitUntilIdle(true);
                    if (UnitsUtil.isTimeLocation(dom.getTimeRange().getUnits())) {
                        dom.setTimeRange(timeRange);
                    }
                    logit("done with setTimeRange", t0, uniq, debug);
                }

            }

            // wait for autoranging, etc.
            dom.getController().waitUntilIdle();
            
            // axis settings
            Plot p = dom.getController().getPlot();

            if (!title.equals("")) p.setTitle(title);

            Axis axis = p.getXaxis();
            if (!xlabel.equals("")) axis.setLabel(xlabel);
            if (!xrange.equals("")) {
                Units u = axis.getController().getDasAxis().getUnits();
                DatumRange newRange = DatumRangeUtil.parseDatumRange(xrange, u);
                axis.setRange(newRange);
            }
            if (!xlog.equals("")) axis.setLog("true".equals(xlog));
            if (!xdrawTickLabels.equals(""))
                axis.setDrawTickLabels("true".equals(xdrawTickLabels));

            axis = p.getYaxis();
            if (!ylabel.equals("")) axis.setLabel(ylabel);
            if (!yrange.equals("")) {
                Units u = axis.getController().getDasAxis().getUnits();
                DatumRange newRange = DatumRangeUtil.parseDatumRange(yrange, u);
                axis.setRange(newRange);
            }
            if (!ylog.equals("")) axis.setLog("true".equals(ylog));
            if (!ydrawTickLabels.equals(""))
                axis.setDrawTickLabels("true".equals(ydrawTickLabels));

            axis = p.getZaxis();
            if (!zlabel.equals("")) axis.setLabel(zlabel);
            if (!zrange.equals("")) {
                Units u = axis.getController().getDasAxis().getUnits();
                DatumRange newRange = DatumRangeUtil.parseDatumRange(zrange, u);
                axis.setRange(newRange);
            }
            if (!zlog.equals("")) axis.setLog("true".equals(zlog));
            if (!zdrawTickLabels.equals(""))
                axis.setDrawTickLabels("true".equals(zdrawTickLabels));



            if (!srenderType.equals("")) {
                RenderType renderType = RenderType.valueOf(srenderType);
                dom.getController().getPlotElement().setRenderType(renderType);
            }

            if (!scolor.equals("")) {
                dom.getController().getPlotElement().getStyle().setColor(Color.decode(scolor));
            }

            if (!sfillColor.equals("")) {
                dom.getController().getPlotElement().getStyle().setFillColor(Color.decode(sfillColor));
            }
            if (!sforegroundColor.equals("")) {
                dom.getOptions().setForeground(Color.decode(sforegroundColor));
            }
            if (!sbackgroundColor.equals("")) {
                dom.getOptions().setBackground(Color.decode(sbackgroundColor));
            }

            if ( !grid.equals("") ) {
                dom.getOptions().setDrawGrid( grid.equals("true") );
            }

            logit("done with setStyle", t0, uniq, debug);
            if (!script.equals("")) {
                URL url = new URL(request.getRequestURL().toString());
                URL scriptUrl = new URL(url, script);

                //can't do anything more until script context is not static
                PythonInterpreter interp = JythonUtil.createInterpreter(true, false);

                interp.execfile(AutoplotUI.class.getResource("appContextImports.py").openStream(), "appContextImports.py");

                logit("done with script", t0, uniq, debug);
            }

            if ( false ) { // force a change in the output, useful for testing.
                dom.getController().getCanvas().getController().getDasCanvas().addTopDecorator( new Painter() {
                    public void paint(Graphics2D g) {
                        g.setFont( Font.decode("sans-30") );
                        g.drawString( "testSimpleServlet", 300, 300 );
                    }
                });
            }

            dom.getController().waitUntilIdle();

            logger.log( Level.FINER, "getDataSet: {0}", dom.getPlotElements(0).getController().getRenderer().getDataSet());
            logger.log( Level.FINER, "bounds: {0}", dom.getPlots(0).getXaxis().getController().getDasAxis().getBounds());

            if (format.equals("image/png")) {
                logit("waiting for image", t0, uniq, debug);
                Image image = appmodel.canvas.getImage(width, height);
                logit("got image", t0, uniq, debug);

                DasPNGEncoder encoder = new DasPNGEncoder();
                encoder.addText(DasPNGConstants.KEYWORD_CREATION_TIME, new Date().toString());
                try {
                    encoder.write((BufferedImage) image, out);
                } catch (IOException ioe) {
                } finally {
                    try {
                        out.close();
                    } catch (IOException ioe) {
                        throw new RuntimeException(ioe);
                    }
                }
            } else if (format.equals("application/pdf")) {
                logit("do prepareForOutput", t0, uniq, debug);
                appmodel.canvas.prepareForOutput(width, height);
                logit("done with prepareForOutput", t0, uniq, debug);
                GraphicsOutput go = new org.das2.util.awt.PdfGraphicsOutput();

                appmodel.canvas.writeToGraphicsOutput(out, go);
                logit("done with write to output", t0, uniq, debug);
            } else if (format.equals("image/svg+xml")) {
                logit("do prepareForOutput...", t0, uniq, debug);
                appmodel.canvas.prepareForOutput(width, height);
                logit("done with prepareForOutput", t0, uniq, debug);
                GraphicsOutput go = new org.das2.util.awt.SvgGraphicsOutput();

                appmodel.canvas.writeToGraphicsOutput(out, go);
                logit("done with write to output", t0, uniq, debug);
            } else {
                throw new ServletException("format must be image/png, application/pdf, or image/svg+xml");
            }


            out.close();
            logit("done with request", t0, uniq, debug);

        } catch (Exception e) {
            logger.log( Level.WARNING, null, e );
            throw new ServletException(e);
        }


    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /** 
     * Handles the HTTP <code>GET</code> method.
     * @param request servlet request
     * @param response servlet response
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /** 
     * Handles the HTTP <code>POST</code> method.
     * @param request servlet request
     * @param response servlet response
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /** 
     * Returns a short description of the servlet.
     */
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

    private void logit(String string, long t0, long id, String debug) {
        logger.log( ( ( debug!=null && !debug.equals("false") ) ? Level.INFO : Level.FINER ), String.format( "##%d# %s: %d\n", id, string, (System.currentTimeMillis() - t0) ) );
    }
}
