/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.logging.Logger;
import org.das2.util.DasPNGConstants;
import org.das2.util.DasPNGEncoder;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
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
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.capability.TimeSeriesBrowse;
import org.virbo.dsops.Ops;

/**
 *
 * @author jbf
 */
public class SimpleServlet extends HttpServlet {

    static FileHandler handler;
    
    private static void addHandlers( long requestId ) {
        try {
            FileHandler h = new FileHandler("/tmp/testservlet/log" + requestId + ".txt");
            TimerConsoleFormatter form = new TimerConsoleFormatter();
            form.setResetMessage("getImage");
            h.setFormatter(form);
            h.setLevel(Level.ALL);
            DasLogger.addHandlerToAll(h);
            if ( handler!=null ) handler.close();
            handler= h;
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

        long t0= System.currentTimeMillis();
        String suniq= request.getParameter("requestId");
        long uniq;
        if ( suniq==null ) {
            uniq= (long)(Math.random()*100);
        } else {
            uniq= Long.parseLong(suniq);
            addHandlers(uniq);
        }
        
        logit("-- new request "+uniq, t0,uniq);
        try {

            String surl = request.getParameter("url");
            String process = ServletUtil.getStringParameter(request, "process", "");
            String vap = request.getParameter("vap");
            String script = ServletUtil.getStringParameter(request, "script", "");
            int width = ServletUtil.getIntParameter(request, "width", -1);
            int height = ServletUtil.getIntParameter(request, "height", -1);
            String scanvasAspect= ServletUtil.getStringParameter(request, "canvas.aspect", "");
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
            String title = ServletUtil.getStringParameter(request, "plot.title", "" );
            String xlabel= ServletUtil.getStringParameter(request, "plot.xaxis.label", "" );
            String xrange= ServletUtil.getStringParameter(request, "plot.xaxis.range", "" );
            String xlog= ServletUtil.getStringParameter(request, "plot.xaxis.log", "" );
            String xdrawTickLabels= ServletUtil.getStringParameter(request, "plot.xaxis.drawTickLabels", "" );
            String ylabel= ServletUtil.getStringParameter(request, "plot.yaxis.label", "" );
            String yrange= ServletUtil.getStringParameter(request, "plot.yaxis.range", "" );
            String ylog= ServletUtil.getStringParameter(request, "plot.yaxis.log", "" );
            String ydrawTickLabels= ServletUtil.getStringParameter(request, "plot.yaxis.drawTickLabels", "" );
            String zlabel= ServletUtil.getStringParameter(request, "plot.zaxis.label", "" );
            String zrange= ServletUtil.getStringParameter(request, "plot.zaxis.range", "" );
            String zlog= ServletUtil.getStringParameter(request, "plot.zaxis.log", "" );
            String zdrawTickLabels= ServletUtil.getStringParameter(request, "plot.zaxis.drawTickLabels", "" );
            
            

            OutputStream out = response.getOutputStream();

            if (surl.equals("about:plugins")) {
                response.setContentType("text/html");
                out.write(DataSetSelectorSupport.getPluginsText().getBytes());
                out.close();
                return;
            } else if (surl.equals("about:autoplot")) {
                response.setContentType("text/html");
                String s = AboutUtil.getAboutHtml();
                s = s.substring(0, s.length() - 7);
                s = s + "<br><br>servlet version=20081029_1009<br></html>";
                out.write(s.getBytes());
                out.close();
                return;
            } else {
                response.setContentType(format);
            }

            logit("get parameters",t0,uniq);
            
            System.setProperty("java.awt.headless", "true");

            ApplicationModel appmodel = new ApplicationModel();

            logit("create application model",t0,uniq);

            Application dom= appmodel.getDocumentModel();

            if ("true".equals(request.getParameter("autolayout"))) {
                dom.getOptions().setAutolayout(true);
            } else {
                dom.getOptions().setAutolayout(false);
                if (!row.equals("")) dom.getController().getCanvas().setRow(row);
                if (!column.equals("")) dom.getController().getCanvas().setColumn(column);
            }

            if (!font.equals("")) appmodel.getCanvas().setBaseFont(Font.decode(font));


            // do dimensions
            if ( "".equals(scanvasAspect) ) {
                if ( width==-1 ) width= 700;
                if ( height==-1 ) height= 400;
            } else {
                double aspect= Units.dimensionless.parse(scanvasAspect).doubleValue(Units.dimensionless);
                if ( width==-1 && height!=-1 ) width= (int)( height * aspect );
                if ( height==-1 && width!=-1 ) height= (int)( width / aspect );
            }
            dom.getController().getCanvas().setSize( new Dimension( width, height) );

            logit("set canvas parameters",t0,uniq);
            
            if (vap != null) {
                appmodel.doOpen(new File(vap));
                logit("opened vap",t0,uniq);
            }

            if (!surl.equals("")) {
                DataSource dsource;
                try {
                    dsource = DataSetURL.getDataSource(surl);
                } catch (NullPointerException ex) {
                    throw new RuntimeException("No such data source: ", ex);
                } catch (Exception ex) {
                    throw ex;
                }
                logit("got data source",t0,uniq);
                
                DatumRange timeRange=null;
                if (!stimeRange.equals("")) {
                    timeRange = DatumRangeUtil.parseTimeRangeValid(stimeRange);
                    TimeSeriesBrowse tsb = dsource.getCapability(TimeSeriesBrowse.class);
                    if (tsb != null) {
                        tsb.setTimeRange(timeRange);
                        logit("timeSeriesBrowse got data source",t0,uniq);
                    }
                }

                if (!process.equals("")) {
                    QDataSet r = dsource.getDataSet(new NullProgressMonitor());
                    logit("done with read",t0,uniq);
                    if (process.equals("histogram")) {
                        appmodel.setDataSet( Ops.histogram(r, 100 ) );
                    } else if ( process.equals("magnitude(fft)") ) {
                        r= Ops.magnitude(Ops.fft(r));
                        appmodel.setDataSet( r );
                    } else if ( process.equals("nop") ) {
                        appmodel.setDataSet( r );
                    }
                    logit("done with process",t0,uniq);
                } else {
                    appmodel.setDataSource(dsource);
                    logit("done with setDataSource",t0,uniq);
                }
                
                if (!stimeRange.equals("") ) {
                    appmodel.waitUntilIdle(true);
                    if ( UnitsUtil.isTimeLocation( dom.getTimeRange().getUnits() ) ) {
                        dom.setTimeRange(timeRange);
                    }
                    logit("done with setTimeRange",t0,uniq);
                }

            }

            // axis settings
            Plot p= dom.getController().getPlot();

            if ( !title.equals("") )  p.setTitle(title);

            Axis axis = p.getXaxis();
            if ( !xlabel.equals("") )  axis.setLabel(xlabel);
            if ( !xrange.equals("") )  {
                Units u= axis.getController().getDasAxis().getUnits();
                DatumRange newRange= DatumRangeUtil.parseDatumRange(xrange, u);
                axis.setRange(newRange);
            }
            if ( !xlog.equals("") )  axis.setLog("true".equals(xlog) );
            if ( !xdrawTickLabels.equals("") )  axis.setDrawTickLabels("true".equals(xdrawTickLabels));
            
            axis = p.getYaxis();
            if ( !ylabel.equals("") )  axis.setLabel(ylabel);
            if ( !yrange.equals("") )  {
                Units u= axis.getController().getDasAxis().getUnits();
                DatumRange newRange= DatumRangeUtil.parseDatumRange(yrange, u);
                axis.setRange(newRange);
            }
            if ( !ylog.equals("") )  axis.setLog("true".equals(ylog) );
            if ( !ydrawTickLabels.equals("") )  axis.setDrawTickLabels("true".equals(ydrawTickLabels));

            axis = p.getZaxis();
            if ( !zlabel.equals("") )  axis.setLabel(zlabel);
            if ( !zrange.equals("") )  {
                Units u= axis.getController().getDasAxis().getUnits();
                DatumRange newRange= DatumRangeUtil.parseDatumRange(zrange, u);
                axis.setRange(newRange);
            }
            if ( !zlog.equals("") )  axis.setLog("true".equals(zlog) );
            if ( !zdrawTickLabels.equals("") )  axis.setDrawTickLabels("true".equals(zdrawTickLabels));



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

            logit("done with setStyle",t0,uniq);
            if (!script.equals("")) {
                URL url = new URL(request.getRequestURL().toString());
                URL scriptUrl = new URL(url, script);

                System.err.println(scriptUrl);
                //can't do anything more until script context is not static
                PythonInterpreter interp = JythonUtil.createInterpreter(true, false);

                interp.execfile(AutoPlotUI.class.getResource("appContextImports.py").openStream(), "appContextImports.py");

                logit("done with script",t0,uniq);
            }
            
            dom.getController().waitUntilIdle();

            System.err.println( dom.getController().getPlot().getController().getDasPlot().getRow() );

            if (format.equals("image/png")) {
                logit("waiting for image",t0,uniq);
                Image image = appmodel.canvas.getImage(width, height);
                logit("got image",t0,uniq);
                
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
                logit("do prepareForOutput",t0,uniq);
                appmodel.canvas.prepareForOutput(width, height);
                logit("done with prepareForOutput",t0,uniq);
                GraphicsOutput go = new org.das2.util.awt.PdfGraphicsOutput();

                appmodel.canvas.writeToGraphicsOutput(out, go);
                logit("done with write to output",t0,uniq);
            } else if (format.equals("image/svg+xml")) {
                logit("do prepareForOutput...",t0,uniq);
                appmodel.canvas.prepareForOutput(width, height);
                logit("done with prepareForOutput",t0,uniq);
                GraphicsOutput go = new org.das2.util.awt.SvgGraphicsOutput();

                appmodel.canvas.writeToGraphicsOutput(out, go);
                logit("done with write to output",t0,uniq);
            } else {
                throw new IllegalArgumentException("format must be image/png, application/pdf, or image/svg+xml");

            }

            
            out.close();
            logit( "done with request",t0,uniq);
            
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
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

    private void logit(String string, long t0,long id) {
        System.err.printf("##%d# %s: %d\n", id, string, ( System.currentTimeMillis()-t0) );
    }
}
