/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.io.File;
import java.util.logging.Logger;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.graph.DasCanvas;
import org.das2.graph.Painter;
import org.das2.system.DasLogger;
import org.das2.util.AboutUtil;
import org.das2.util.FileUtil;
import org.das2.util.TimerConsoleFormatter;
import org.das2.util.awt.GraphicsOutput;
import org.das2.util.monitor.NullProgressMonitor;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.Axis;
import org.virbo.autoplot.dom.DataSourceFilter;
import org.virbo.autoplot.dom.Plot;
import org.virbo.autoplot.state.StatePersistence;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetSelectorSupport;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.DataSourceRegistry;
import org.virbo.datasource.FileSystemUtil;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.capability.TimeSeriesBrowse;
import org.virbo.datasource.jython.JythonDataSourceFactory;
import org.virbo.dsops.Ops;

/**
 * SimpleServlet produces PNG,PDF, and SVG products for
 * .vap files and Autoplot URIs.  A simple set of controls is provided
 * to tweak layout when automatic settings are not satisfactory.
 * 
 * @author jbf
 */
public class SimpleServlet extends HttpServlet {

    private static final Logger logger= Logger.getLogger("autoplot.servlet" );
    public static final String version= "v20150107.1100";

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
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        } catch (SecurityException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }
    
    /**
     * return true if the .vap file contains any references to local resources.
     * This checks .jyds references to see that they also do not contain local 
     * references.
     * @param vap vap file
     * @return true if the file has local references.
     */
    private static boolean vapHasLocalReferences( File vap ) {
        try {
            Application app= (Application) StatePersistence.restoreState(vap);
            DataSourceFilter[] dsfs= app.getDataSourceFilters();
            for (DataSourceFilter dsf : dsfs) {
                URI uri = DataSetURI.toUri(dsf.getUri());
                if (FileSystemUtil.isLocalResource(dsf.getUri())) {
                    logger.log( Level.FINE, "vap contains local reference: {0}", uri );
                    return true;
                } else {
                    try {
                        DataSourceFactory dssf= DataSetURI.getDataSourceFactory( uri, new NullProgressMonitor() );
                        if ( dssf instanceof JythonDataSourceFactory ) {
                            if ( JythonDataSourceFactory.jydsHasLocalReferences(uri) ) {
                                return true;
                            }
                        }
                    } catch (IllegalArgumentException ex) {
                        Logger.getLogger(SimpleServlet.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (URISyntaxException ex) {
                        Logger.getLogger(SimpleServlet.class.getName()).log(Level.SEVERE, null, ex);
                    }                    
                }
            }
            Plot[] plots= app.getPlots();
            for ( int i=0; i<plots.length; i++ ) {
                if ( FileSystemUtil.isLocalResource( plots[i].getTicksURI() ) ) {
                    logger.log( Level.FINE, "vap contains local reference: {0}", dsfs[i].getUri());
                    return true;
                }
            }
            return false;
        } catch (IOException ex) {
            return false;
        }
        
    }
    
    /** 
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException
     * @throws IOException
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        //logger.setLevel(Level.FINE);
        
        logger.finer( version );

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
        if ( debug==null ) debug= "false";

        logit("-- new request " + uniq + " --", t0, uniq, debug);
        try {

            String surl = request.getParameter("url");
            String suri = request.getParameter("uri");
            if ( suri!=null ) surl= suri;
            String id= request.getParameter("id"); // lookup local URIs in table id.txt
            String process = ServletUtil.getStringParameter(request, "process", "");
            String vap = request.getParameter("vap");
            int width = ServletUtil.getIntParameter(request, "width", -1);
            int height = ServletUtil.getIntParameter(request, "height", -1);
            String scanvasAspect = ServletUtil.getStringParameter(request, "canvas.aspect", "");
            String format = ServletUtil.getStringParameter(request, "format", "image/png");
            String font = ServletUtil.getStringParameter(request, "font", "");
            String column = ServletUtil.getStringParameter(request, "column", "");
            String row = ServletUtil.getStringParameter(request, "row", "");
            String srenderType = ServletUtil.getStringParameter(request, "renderType", "");
            String stimeRange = ServletUtil.getStringParameter(request, "timeRange", "");
            if ( stimeRange.length()==0 ) stimeRange= ServletUtil.getStringParameter(request, "timerange", "");
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
            String stamp= ServletUtil.getStringParameter( request, "stamp", "false" );  // print a stamp for debugging.  If not false, the value is printed in blue along with a timestamp.

            for (Enumeration en = request.getParameterNames(); en.hasMoreElements();) {
                String n = (String) en.nextElement();
                logger.log( Level.FINE, "{0}: {1}", new Object[]{n, Arrays.asList(request.getParameterValues(n))});
            }

            if (srenderType.equals("fill_to_zero")) {
                srenderType = "fillToZero";
            }

            OutputStream out = response.getOutputStream();

            // To support load balancing, insert the actual host that resolved the request
            String host= java.net.InetAddress.getLocalHost().getCanonicalHostName();
            response.setHeader( "X-Served-By", host );
            response.setHeader( "X-Server-Version", version );
            if ( surl!=null ) {
                response.setHeader( "X-Autoplot-URI", surl );
            }
            if ( id!=null ) {
                response.setHeader( "X-Autoplot-ID", id );
            }
            
            // id lookups.  The file id.txt is a flat file with hash comments,
            // with each record containing a regular expression with groups, 
            // then a map with group ids.
            if ( id!=null ) {
                surl= null;
                Map<String,String> ids= ServletUtil.getIdMap();
                for ( Entry<String,String> e : ids.entrySet() ) {
                    Pattern p= Pattern.compile(e.getKey());
                    Matcher m= p.matcher(id);
                    if ( m.matches() ) {
                        surl= e.getValue();
                        for ( int i=1; i<m.groupCount()+1; i++ ) {
                            String r= m.group(i);
                            if ( r.contains("..") ) {
                                throw new IllegalArgumentException(".. (up directory) is not allowed in id.");
                            }
                            surl= surl.replaceAll( "\\$"+i, r ); // I know there's a better way to do this.
                        }
                        if ( surl.contains("..") ) {
                            throw new IllegalArgumentException(".. (up directory) is not allowed in the result of id: "+surl);
                        }
                    }
                }
                if ( surl==null ) {
                    throw new IllegalArgumentException("unable to resolve id="+id);
                }
            }
            
            boolean whiteListed= false;
            if ( surl!=null ) {
                List<String> whiteList= ServletUtil.getWhiteList();
                for ( String s: whiteList ) {
                    if ( Pattern.matches( s, surl ) ) {
                        whiteListed= true;
                        logger.fine("uri is whitelisted");
                    }
                }
            }
            if ( vap!=null ) {
                List<String> whiteList= ServletUtil.getWhiteList();
                for ( String s: whiteList ) {
                    if ( Pattern.matches( s, vap ) ) {
                        whiteListed= true;
                        logger.fine("vap is whitelisted");
                    }
                }
                //TODO: there may be a request that the URIs within the vap are 
                //verified to be whitelisted.  This is not done.
            }
                
            // Allow a little caching.  See https://devcenter.heroku.com/articles/increasing-application-performance-with-http-cache-headers
            // public means multiple browsers can use the same cache, maybe useful for workshops and seems harmless.
            // max-age means the result is valid for the next 10 seconds.  
            response.setHeader( "Cache-Control", "public, max-age=10" );  
            DateFormat httpDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            httpDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            response.setHeader( "Expires", httpDateFormat.format( new Date( System.currentTimeMillis()+600000 ) ) );

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
                boolean isLocalVap= FileSystemUtil.isLocalResource(vap);
                logger.log(Level.FINER, "vap isLocalVap={0}", isLocalVap);
                File openable = DataSetURI.getFile(vap,new NullProgressMonitor());
                if ( !isLocalVap ) {
                    if ( vapHasLocalReferences( openable ) ) {
                        throw new IllegalArgumentException("remote .vap file has local references");
                    }
                }
                URISplit split= URISplit.parse(vap);
                if (split.params != null) {
                    LinkedHashMap<String, String> params = URISplit.parseParams(split.params);
                    if ( params.containsKey("timerange") && !params.containsKey("timeRange") ) {
                        params.put("timeRange", params.remove("timerange") );
                    }
                    if ( isLocalVap ) params.put("PWD",split.path);
                    if ( stimeRange.trim().length()>0 ) {
                        params.put( "timeRange", stimeRange );
                    }
                    if ( !isLocalVap ) {
                        LinkedHashMap<String, String> params1 = new LinkedHashMap();
                        if ( params.get("timeRange")!=null ) {
                            params1.put( "timeRange", params.get("timeRange") );
                        }
                        params= params1;
                    }
                    appmodel.doOpen(openable, params);
                } else {
                    LinkedHashMap<String, String> params = new LinkedHashMap();
                    if ( isLocalVap ) params.put("PWD",split.path);
                    if ( stimeRange.trim().length()>0 ) {
                        params.put( "timeRange", stimeRange );
                    }                   
                    appmodel.doOpen(openable, params);
                }
                logit("opened vap", t0, uniq, debug);
                width = appmodel.dom.getCanvases(0).getWidth();
                height = appmodel.dom.getCanvases(0).getHeight();
                DasCanvas c = dom.getController().getCanvas().getController().getDasCanvas();
                c.prepareForOutput(width, height); // KLUDGE, resize all components for TimeSeriesBrowse
            }

            if (surl != null && !"".equals(surl)) {

                File data= new File( ServletUtil.getServletHome(), "data" );
                if ( !data.exists() ) {
                    if ( !data.mkdirs() ) {
                        throw new IllegalArgumentException("Unable to make servlet data directory");
                    }
                }
                surl= URISplit.makeAbsolute( data.getAbsolutePath(), surl );
                
                URISplit split = URISplit.parse(surl);                

                if ( id==null ) { // id!=null indicates that the surl was generated within the server.
                    if ( whiteListed ) {
                        
                    } else {
                        if ( FileSystemUtil.isLocalResource(surl) ) {
                            File p= new File(data.getAbsolutePath());
                            File f= new File(split.file.substring(7));
                            if ( FileUtil.isParent( p, f ) ) {
                                logger.fine("file within autoplot_data/server/data folder is allowed");
                            } else {
                                // See http://autoplot.org/developer.servletSecurity for more info.
                                throw new IllegalArgumentException("local resources cannot be served, except via local vap file.  ");
                            }
                        } else {
                            if ( split.file!=null && split.file.contains("jyds") || ( split.vapScheme!=null && split.vapScheme.equals("jyds") ) ) {
                                throw new IllegalArgumentException("non-local .jyds scripts are not allowed.  Administrators may wish to whitelist this data, see HOME/autoplot_data/server/whitelist.txt.");
                            }
                        }

                        if ( split.vapScheme!=null && split.vapScheme.equals("vap+inline") && split.surl.contains("getDataSet") ) { // this list could go on forever...
                            throw new IllegalArgumentException("vap+inline URI cannot contain getDataSet.");
                        }
                    }
                }
                                
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
            
            if ( !stamp.equals("false") ) { // force a change in the output, useful for testing.
                final String fstamp= stamp;
                final Font ffont= Font.decode("sans-4-italic");
                final String fhost= host;
                dom.getController().getCanvas().getController().getDasCanvas().addTopDecorator( new Painter() {
                    public void paint(Graphics2D g) {
                        g.setFont( ffont );
                        g.setColor( Color.BLUE );
                        g.drawString( ""+fstamp+" "+ fhost + " " + TimeUtil.now().toString(), 0, 10 );
                    }
                });
            }

            dom.getController().waitUntilIdle();

            logger.log( Level.FINER, "getDataSet: {0}", dom.getPlotElements(0).getController().getRenderer().getDataSet());
            logger.log( Level.FINER, "bounds: {0}", dom.getPlots(0).getXaxis().getController().getDasAxis().getBounds());

            if (format.equals("image/png")) {
                                
                try {
                    appmodel.canvas.writeToPng( out, width, height );
                    
                } catch (IOException ioe) {
                    logger.log( Level.SEVERE, ioe.toString(), ioe );
                    
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
            logger.log( Level.WARNING, e.getMessage(), e );
            throw new ServletException(e);
        }


    }

    @Override
    public void destroy() {
        super.destroy();
        logger.warning("SimpleServlet uses no resources.");
    }
    
    

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /** 
     * Handles the HTTP <code>GET</code> method.
     * @param request servlet request
     * @param response servlet response
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /** 
     * Handles the HTTP <code>POST</code> method.
     * @param request servlet request
     * @param response servlet response
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /** 
     * Returns a short description of the servlet.
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

    private void logit(String string, long t0, long id, String debug) {
        logger.log( ( ( debug!=null && !debug.equals("false") ) ? Level.FINE : Level.FINER ), String.format( "##%d# %s: %d\n", id, string, (System.currentTimeMillis() - t0) ) );
    }
}
