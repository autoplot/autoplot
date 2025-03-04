
package org.autoplot.servlet;

import org.autoplot.RenderType;
import org.autoplot.ApplicationModel;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.logging.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.output.TeeOutputStream;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.graph.DasCanvas;
import org.das2.graph.Painter;
import org.das2.graph.Renderer;
import org.das2.system.DasLogger;
import org.das2.util.AboutUtil;
import org.das2.util.FileUtil;
import org.das2.util.TimerConsoleFormatter;
import org.das2.util.awt.GraphicsOutput;
import org.das2.util.monitor.NullProgressMonitor;
import org.autoplot.dom.Application;
import org.autoplot.dom.Axis;
import org.autoplot.dom.DataSourceFilter;
import org.autoplot.dom.Plot;
import org.autoplot.dom.PlotElement;
import org.autoplot.state.StatePersistence;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.DataSetSelectorSupport;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.DataSourceFactory;
import org.autoplot.datasource.FileSystemUtil;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.capability.TimeSeriesBrowse;
import org.autoplot.datasource.jython.JythonDataSourceFactory;
import org.das2.qds.ops.Ops;
import org.das2.util.ColorUtil;
import org.das2.util.TimingConsoleFormatter;

/**
 * SimpleServlet produces PNG,PDF, and SVG products for
 * .vap files and Autoplot URIs.  A simple set of controls is provided
 * to tweak layout when automatic settings are not satisfactory.
 * 
 * If the URI is not whitelisted, then it will be logged.  If the 
 * URI or VAP is blacklisted, then it will throw an exception.
 * 
 * Some known instances:<ul>
 * <li>http://jfaden.net/AutoplotServlet/
 * </ul>
 * 
 * @author jbf
 */
public class SimpleServlet extends HttpServlet {

    private static final Logger logger= Logger.getLogger("autoplot.servlet" );

    static FileHandler handler;

    private static void addHandlers(long requestId) {
        try {
            FileHandler h = new FileHandler("/tmp/apservlet/log" + requestId + ".txt");
            TimerConsoleFormatter form = new TimerConsoleFormatter();
            form.setResetMessage("getImage");
            h.setFormatter(form);
            h.setLevel(Level.ALL);
            DasLogger.addHandlerToAll(h);
            if (handler != null) handler.close();
            handler = h;
        } catch (IOException | SecurityException ex) {
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
                    } catch (IllegalArgumentException | URISyntaxException ex) {
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
    
    private static void copyStream( InputStream source, OutputStream target ) throws IOException {
        byte[] buf = new byte[8192];
        int length;
        while ((length = source.read(buf)) > 0) {
            target.write(buf, 0, length);
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

        File cacheFile= null;
        File metaCacheFile= null;
        File logFile= null; // logging information from when file was created.
        
        FileInputStream fin= null;
        
        String qs= request.getQueryString();
        String cacheControl= request.getHeader("Cache-Control");
        
        String usecache= request.getParameter("usecache");

        synchronized ( this ) { // if a cached response is available, then use it.
            if ( ServletInfo.isCaching() && qs!=null ) {
                String format = ServletUtil.getStringParameter(request, "format", "image/png");
                String hash= request.getQueryString();
                hash= String.format( "%04d", Math.abs( hash.hashCode() % 10000 ) );
                File s= ServletInfo.getCacheDirectory();
                logFile= new File( s, hash+".log" );
                
                if ( format.equals("image/png") ) {
                    if ( !s.exists() ) {
                        if ( !s.mkdirs() ) {
                            throw new RuntimeException("Unable to make cache: "+s);
                        }
                    }
                    cacheFile= new File( s, hash + ".png" );
                    metaCacheFile= new File( s, hash + ".txt" );

                    if ( cacheFile.exists() && !( "no-cache".equals(cacheControl) || "false".equals(usecache) ) ) {
                        byte[] bb= Files.readAllBytes(metaCacheFile.toPath());
                        String qs0= new String( bb );
                        if ( qs0.equals(qs) ) {
                            cacheFile.setLastModified( new Date().getTime() );
                            String host= java.net.InetAddress.getLocalHost().getCanonicalHostName();
                            response.setHeader( "X-Served-By", host );
                            response.setHeader( "X-Server-Version", ServletInfo.version );
                            response.setHeader( "X-Autoplot-cache", "yep" );
                            response.setHeader( "X-Autoplot-cache-filename", cacheFile.getName() );

                            fin= new FileInputStream( cacheFile );

                        } else {
                            logger.finer( "cache slot occupied by another image, overwriting.");
                        }

                    }
                }
            }
        }
        
        if ( fin!=null ) {
            try ( OutputStream outs= response.getOutputStream() ) {
                copyStream( fin, outs );
            }
            fin.close();
            return;
        }
        
        if ( logFile==null ) {
            throw new NullPointerException("logfile should be set");
        }
        
        
        Handler h;
        try {
            h= new FileHandler(String.valueOf(logFile));
        } catch ( Exception ex ) {
            throw new RuntimeException(ex);
        }
        h.setFormatter( new TimingConsoleFormatter() );
        logger.addHandler(h);
        logger.setLevel( Level.ALL );
        h.setLevel( Level.ALL );
        
        //logger.setLevel(Level.FINE);
        
        logger.finer(ServletInfo.version);

        logger.fine("=======================");

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
        //debug= "TRUE";

        logit("-- new request " + uniq + " --", t0, uniq, debug);
        try {

            String suri = request.getParameter("uri");
            if ( suri==null ) {
               suri = request.getParameter("url");
            }
            if ( suri!=null && suri.startsWith("vap ") ) {  // pluses are interpretted as spaces when they are parameters in URLs.  These should have been escaped, but legacy operations require we handle this.
                suri= suri.replaceAll(" ","+");
            }
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
            String ssymbolSize = ServletUtil.getStringParameter(request, "symbolSize", "");
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
            String grid= ServletUtil.getStringParameter( request, "drawGrid", "" ); // T or true
            String stamp= ServletUtil.getStringParameter( request, "stamp", "false" );  // print a stamp for debugging.  If not false, the value is printed in blue along with a timestamp.

            for (Enumeration en = request.getParameterNames(); en.hasMoreElements();) {
                String n = (String) en.nextElement();
                logger.log( Level.FINE, "{0}: {1}", new Object[]{n, Arrays.asList(request.getParameterValues(n))});
            }

            if (srenderType.equals("fill_to_zero")) {
                srenderType = "fillToZero";
            }

            if ( suri!=null ) logger.log(Level.FINE, "suri={0}", suri);
            if ( vap!=null ) logger.log(Level.FINE, "vap={0}", vap);
            if ( id!=null ) logger.log(Level.FINE, "id={0}", id);
            
            // allow URI=vapfile
            if ( vap==null && suri!=null ) { 
                if ( suri.contains(".vap") || suri.contains(".vap?") ) {
                    vap= suri;
                    suri= null;
                }
            }
            
            // To support load balancing, insert the actual host that resolved the request
            String host= java.net.InetAddress.getLocalHost().getCanonicalHostName();
            response.setHeader( "X-Served-By", host );
            response.setHeader("X-Server-Version", ServletInfo.version);
            if ( suri!=null ) {
                response.setHeader( "X-Autoplot-URI", suri );
            }
            if ( id!=null ) {
                response.setHeader( "X-Autoplot-ID", id );
            }
            
            // id lookups.  The file id.txt is a flat file with hash comments,
            // with each record containing a regular expression with groups, 
            // then a map with group ids.
            if ( id!=null ) {
                suri= null;
                Map<String,String> ids= ServletUtil.getIdMap();
                for ( Entry<String,String> e : ids.entrySet() ) {
                    Pattern p= Pattern.compile(e.getKey());
                    Matcher m= p.matcher(id);
                    if ( m.matches() ) {
                        suri= e.getValue();
                        for ( int i=1; i<m.groupCount()+1; i++ ) {
                            String r= m.group(i);
                            if ( r.contains("..") ) {
                                throw new IllegalArgumentException(".. (up directory) is not allowed in id.");
                            }
                            suri= suri.replaceAll( "\\$"+i, r ); // I know there's a better way to do this.
                        }
                        if ( suri.contains("..") ) {
                            throw new IllegalArgumentException(".. (up directory) is not allowed in the result of id: "+suri);
                        }
                    }
                }
                if ( suri==null ) {
                    throw new IllegalArgumentException("unable to resolve id="+id);
                }
            }
            
            boolean whiteListed= false;
            if ( suri!=null ) {
                whiteListed= ServletUtil.isWhitelisted(suri);
                if ( !whiteListed ) {
                    logger.log(Level.FINE, "uri is not whitelisted: {0}", suri);                    
                    ServletUtil.dumpWhitelistToLogger(Level.FINE);
                    if ( ServletUtil.isBlacklisted(suri) ) {
                        throw new IllegalArgumentException("uri is blacklisted: {0}"+ vap);
                    }
                }
            }
            if ( vap!=null ) {
                whiteListed= ServletUtil.isWhitelisted(vap);
                if ( !whiteListed ) {
                    logger.log(Level.FINE, "vap is not whitelisted: {0}", vap);
                    ServletUtil.dumpWhitelistToLogger(Level.FINE);
                    if ( ServletUtil.isBlacklisted(vap) ) {
                        throw new IllegalArgumentException("vap is blacklisted: {0}"+ vap);
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
            response.setHeader( "Expires", httpDateFormat.format( new Date( System.currentTimeMillis()+10000 ) ) );

            if (vap != null) {
                response.setContentType(format);
            } else if ( suri==null ) {
                try (OutputStream out = response.getOutputStream()) {
                    response.setContentType("text/html");
                    response.setStatus(400);
                    out.write(("Either vap= or url= needs to be specified:<br>"+request.getRequestURI()+"?"+request.getQueryString()).getBytes());
                }
                return;
            } else if (suri.equals("about:plugins")) {
                try (OutputStream out = response.getOutputStream()) {
                    response.setContentType("text/html");
                    out.write(DataSetSelectorSupport.getPluginsText().getBytes());
                }
                return;
            } else if (suri.equals("about:autoplot")) {
                try (OutputStream out = response.getOutputStream()) {
                    response.setContentType("text/html");
                    String s = AboutUtil.getAboutHtml();
                    s = s.substring(0, s.length() - 7);
                    s = s + "<br>";
                    s = s + "hapiServerCache="+ System.getProperty( "hapiServerCache" ) + "<br>";
                    s = s + "cdawebHttps=" + System.getProperty( "cdawebHttps" ) + "<br>";
                    s = s + "enableReferenceCache=" + System.getProperty( "enableReferenceCache" ) + "<br>";
                    s = s + "<br><br>servlet version="+ServletInfo.version+"<br>";
                    s = s + "</html>";
                    out.write(s.getBytes());
                }
                return;
            } else {
                response.setContentType(format);
            }

            logit("surl: "+suri,t0, uniq, debug );
            
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
                    if ( vapHasLocalReferences( openable ) && !ServletUtil.isWhitelisted( vap ) ) {
                        throw new IllegalArgumentException("remote .vap file has local references");
                    }
                }
                URISplit split= URISplit.parse(vap);
                if (split.params != null) {
                    LinkedHashMap<String, String> params = URISplit.parseParams(split.params);
                    if ( params.containsKey("timerange") && !params.containsKey("timeRange") ) {
                        params.put("timeRange", params.remove("timerange") );
                    }
                    params.put("PWD",split.path);
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
                    appmodel.doOpenVap(openable, params);
                } else {
                    LinkedHashMap<String, String> params = new LinkedHashMap();
                    params.put("PWD",split.path);
                    if ( stimeRange.trim().length()>0 ) {
                        params.put( "timeRange", stimeRange );
                    }                   
                    appmodel.doOpenVap(openable, params);
                }
                logit("opened vap", t0, uniq, debug);
                width = appmodel.getDom().getCanvases(0).getWidth();
                height = appmodel.getDom().getCanvases(0).getHeight();
                DasCanvas c = dom.getController().getCanvas().getController().getDasCanvas();
                c.prepareForOutput(width, height); // KLUDGE, resize all components for TimeSeriesBrowse
            }

            if (suri != null && !"".equals(suri)) {

                File data= new File( ServletUtil.getServletHome(), "data" );
                if ( !data.exists() ) {
                    if ( !data.mkdirs() ) {
                        throw new IllegalArgumentException("Unable to make servlet data directory");
                    }
                }
                suri= URISplit.makeAbsolute( data.getAbsolutePath(), suri );
                
                URISplit split = URISplit.parse(suri);                

                if ( id==null ) { // id!=null indicates that the surl was generated within the server.
                    if ( whiteListed ) {
                        
                    } else {
                        if ( FileSystemUtil.isLocalResource(suri) ) {
                            File p= new File(data.getAbsolutePath());
                            File f= new File(split.file.substring(7));
                            if ( FileUtil.isParent( p, f ) ) {
                                logger.log(Level.FINE, "file within autoplot_data/server/data folder is allowed");
                                logger.log(Level.FINE, "{0}", suri);
                            } else {
                                // See http://autoplot.org/developer.servletSecurity for more info.
                                logger.log(Level.FINE, "{0}", suri);
                                throw new IllegalArgumentException("local resources cannot be served, except via local vap file.  ");
                                
                            }
                        } else {
                            if ( split.file!=null && split.file.contains("jyds") || ( split.vapScheme!=null && split.vapScheme.equals("jyds") ) ) {
                                File sd= ServletUtil.getServletHome();
                                File ff= new File( sd, "whitelist.txt" );
                                logger.log(Level.FINE, "non-local .jyds scripts are not allowed.");
                                logger.log(Level.FINE, "{0}", suri);
                                throw new IllegalArgumentException("non-local .jyds scripts are not allowed.  Administrators may wish to whitelist this data, see "+ff+", which does not include a match for "+suri); //TODO: this server file reference should be removed.
                            }
                        }

                        if ( split.vapScheme!=null && split.vapScheme.equals("vap+inline") && split.surl.contains("getDataSet") ) { // this list could go on forever...
                            throw new IllegalArgumentException("vap+inline URI cannot contain getDataSet.");
                        }
                    }
                }
                                
                DataSource dsource;
                try {
                    dsource = DataSetURI.getDataSource(suri);
                    
                    DatumRange timeRange;
                    if (!stimeRange.equals("")) {
                        timeRange = DatumRangeUtil.parseTimeRange(stimeRange);
                        TimeSeriesBrowse tsb = dsource.getCapability(TimeSeriesBrowse.class);
                        if (tsb != null) {
                            tsb.setURI(suri);
                            tsb.setTimeRange(timeRange);
                            logit("timeSeriesBrowse got data source", t0, uniq, debug);
                            suri= tsb.getURI();
                        }
                    }
                    response.setHeader( "X-Autoplot-TSB-URI", suri );
                    DataSourceFactory dsf= DataSetURI.getDataSourceFactory( DataSetURI.getURI(suri),new NullProgressMonitor());
                    List<String> problems= new ArrayList<>(1);
                    if ( dsf.reject(suri, problems, new NullProgressMonitor() )) {
                        if ( problems.isEmpty() ) {
                            // no explanation provided.
                            throw new IllegalArgumentException("URI was rejected: "+suri );
                        } else if ( problems.size()==1 ) {
                            throw new IllegalArgumentException("URI was rejected: "+problems.get(0) );
                        } else {
                            throw new IllegalArgumentException("URI was rejected: "+problems.get(0) + " and "+(problems.size()-1) + "more" );
                        }
                    }
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
                    switch (process) {
                        case "histogram":
                            appmodel.setDataSet(Ops.histogram(r, 100));
                            break;
                        case "magnitude(fft)":
                            r = Ops.magnitude(Ops.fft(r));
                            QDataSet tt= (QDataSet)r.property(QDataSet.DEPEND_0);
                            QDataSet s= Ops.sort(tt);
                            r= Ops.applyIndex(r, s);
                            appmodel.setDataSet(r);
                            break;
                        case "nop":
                            appmodel.setDataSet(r);
                            break;
                    }
                    logit("done with process", t0, uniq, debug);
                } else {
                    appmodel.setDataSource(dsource);
                    logit("done with setDataSource", t0, uniq, debug);
                }

                if (!stimeRange.equals("")) {
                    appmodel.waitUntilIdle();
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

            if (!title.equals("")) {
                Font f= appmodel.getCanvas().getBaseFont();
                BufferedImage im= new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d= im.createGraphics();
                g2d.setFont(f);
                FontRenderContext frc= g2d.getFontRenderContext();
                int plotWidth= p.getController().getColumn().getController().getDasColumn().getWidth();
                plotWidth-=10; // room for ellipsis
                Rectangle2D rect= f.getStringBounds(title,frc);
                boolean shortened= false;
                while ( rect.getWidth()>plotWidth && title.length()>10 ) {
                    if ( rect.getWidth()-plotWidth>f.getSize()*10 ) {
                        title= title.substring(0,title.length()-10);
                    } else {
                        title= title.substring(0,title.length()-1);
                    }
                    rect= f.getStringBounds(title,frc);
                    shortened= true;
                }
                if ( shortened ) title+="...";
                p.setTitle(title);
            }

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

            if ( !ssymbolSize.equals("") ) { 
                dom.getController().getPlotElement().getStyle().setSymbolSize( Double.parseDouble( ssymbolSize ) );
            }
            if ( scolor.equals("") ) {
                scolor= sforegroundColor;
            }
            if (!scolor.equals("")) {
                String[] scolors= scolor.split("[,;]"); // allow for comma-delimited list.
                if ( scolors.length==1 ) {
                    dom.getController().getPlotElement().getStyle().setColor(ColorUtil.decodeColor(scolor));
                    for ( PlotElement pe: dom.getPlotElements() ) { // bug where Bob saw red
                        pe.getStyle().setColor(ColorUtil.decodeColor(scolor));
                    }
                } else {
                    dom.getController().getPlotElement().getStyle().setColor(ColorUtil.decodeColor(scolors[0]));
                    int i=0;
                    for ( PlotElement pe: dom.getPlotElements() ) { 
                        if ( pe.isActive() ) {
                            pe.getStyle().setColor(ColorUtil.decodeColor(scolors[i]));
                            if ( i<scolors.length-1 ) i+=1;
                        }
                    }
                }
            }

            if (!sfillColor.equals("")) {
                String[] sfillColors= sfillColor.split("[,;]"); // allow for comma-delimited list.
                if ( sfillColors.length==1 ) {
                    dom.getController().getPlotElement().getStyle().setFillColor(ColorUtil.decodeColor(sfillColor));
                    for ( PlotElement pe: dom.getPlotElements() ) { // bug where Bob saw red
                        pe.getStyle().setFillColor(ColorUtil.decodeColor(sfillColor));
                    }
                } else {
                    dom.getController().getPlotElement().getStyle().setFillColor(ColorUtil.decodeColor(sfillColors[0]));
                    int i=0;
                    for ( PlotElement pe: dom.getPlotElements() ) { 
                        if ( pe.isActive() ) {
                            pe.getStyle().setFillColor(ColorUtil.decodeColor(sfillColors[i]));
                            if ( i<sfillColors.length-1 ) i+=1;
                        }
                    }
                }
            }
            if (!sforegroundColor.equals("")) {
                dom.getOptions().setForeground(ColorUtil.decodeColor(sforegroundColor));
            }
            if (!sbackgroundColor.equals("")) {
                if ( sbackgroundColor.equals("none") ) {
                    dom.getOptions().setBackground(new Color( 255,0,0,0 ) ); // transparent
                } else {
                    dom.getOptions().setBackground(ColorUtil.decodeColor(sbackgroundColor));
                }
            }

            if ( !grid.equals("") ) {
                boolean setGrid= grid.equals("true") || grid.equals("T");
                dom.getOptions().setDrawGrid( setGrid );
            }

            logit("done with setStyle", t0, uniq, debug);
            
            if ( !stamp.equals("false") ) { // force a change in the output, useful for testing.
                final String fstamp= stamp;
                final Font ffont= Font.decode("sans-4-italic");
                final String fhost= host;
                dom.getController().getCanvas().getController().getDasCanvas().addTopDecorator(new Painter() {
                    @Override
                    public void paint(Graphics2D g) {
                        g.setFont( ffont );
                        g.setColor( Color.BLUE );
                        g.drawString(""+fstamp+" "+ fhost + " " + TimeUtil.now().toString() + " version: "+ServletInfo.version, 0, 10 );
                    }
                });
            }

            dom.getController().waitUntilIdle();

            logger.log( Level.FINER, "getDataSet: {0}", dom.getPlotElements(0).getController().getRenderer().getDataSet());
            logger.log( Level.FINER, "bounds: {0}", dom.getPlots(0).getXaxis().getController().getDasAxis().getBounds());

            // look for errors on the canvas.
            Exception e= null;
            for ( PlotElement pe: dom.getPlotElements() ) {
                Renderer r= pe.getController().getRenderer();
                if ( r.isActive() ) {
                    Exception lastException= r.getLastException();
                    if (  lastException!=null  ) {
                        e= lastException;
                    }
                }
            }
            
            if ( e!=null ) {
                String message= e.getLocalizedMessage();
                if ( message==null ) message=  e.toString();
                response.setHeader( "X-Autoplot-Exception", message ); 
                //response.setStatus( 400 );
            }
            
            response.setHeader( "X-Autoplot-vaptimer-ms",  String.valueOf( System.currentTimeMillis()-t0 ) );
            
            OutputStream out = response.getOutputStream();
            
            ByteArrayOutputStream baos= null;
            
            try {
                
                if ( cacheFile!=null ) {
                    baos= new ByteArrayOutputStream( 100000 );
                    out= new TeeOutputStream( out, baos );
                }
                
                switch (format) {
                    case "image/png":
                        logger.log(Level.FINE, "time to create image: {0} ms", ( System.currentTimeMillis()-t0 ));
                        try {
                            appmodel.getCanvas().writeToPng( out, width, height );
                            
                        } catch (IOException ioe) {
                            logger.log( Level.SEVERE, ioe.toString(), ioe );
                            
                        } finally {
                            try {
                                out.close();
                            } catch (IOException ioe) {
                                throw new RuntimeException(ioe);
                            }
                        }   
                        if ( !"false".equals(debug) ) {
                            logit("debug means vap file written to /tmp/apserver.vap", t0, uniq, debug);
                            StatePersistence.saveState( new File( "/tmp/apserver.vap" ), dom );
                        }
                        break;
                        
                    case "application/pdf":  
                        logit("do prepareForOutput", t0, uniq, debug);
                        appmodel.getCanvas().prepareForOutput(width, height);
                        logit("done with prepareForOutput", t0, uniq, debug);
                        GraphicsOutput go = new org.das2.util.awt.PdfGraphicsOutput();
                        appmodel.getCanvas().writeToGraphicsOutput(out, go);
                        logit("done with write to output", t0, uniq, debug);
                        break;
                        
                    case "image/svg+xml":
                        logit("do prepareForOutput...", t0, uniq, debug);
                        appmodel.getCanvas().prepareForOutput(width, height);
                        logit("done with prepareForOutput", t0, uniq, debug);
                        GraphicsOutput gos = new org.das2.util.awt.SvgGraphicsOutput();
                        appmodel.getCanvas().writeToGraphicsOutput(out, gos);
                        logit("done with write to output", t0, uniq, debug);
                        break;
                        
                    default:
                        throw new ServletException("format must be image/png, application/pdf, or image/svg+xml");
                }
            } finally { 
                if ( out!=null ) {
                    out.close();
                    
                    synchronized ( this ) {
                        if ( baos!=null && cacheFile!=null ) {
                            byte[] buf= baos.toByteArray();
                            try ( ByteArrayInputStream bais= new ByteArrayInputStream(buf) ) {
                                Files.copy( bais, cacheFile.toPath() );
                            }
                            byte[] metaBytes= qs.getBytes();
                            assert metaCacheFile!=null;
                            Files.write( metaCacheFile.toPath(), metaBytes );
                            logger.removeHandler(h);
                            h.flush();
                            h.close();
                        }
                    }
                }
            }
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
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /** 
     * Returns a short description of the servlet.
     * @return a short description of the servlet.
     */
    @Override
    public String getServletInfo() {
        return "Autoplot Simple Servlet";
    }// </editor-fold>

    private void logit(String string, long t0, long id, String debug) {
        boolean flushHandlers= true;
        if ( debug!=null && !debug.equals("false") ) {
            if ( logger.isLoggable(Level.FINE) ) {
                logger.log( Level.FINE, String.format( "##%d# %s: %d\n", id, string, (System.currentTimeMillis() - t0) ) );
                if ( flushHandlers ) {
                    for ( Handler h: logger.getHandlers() ) {
                        h.flush();
                    }   
                }
            }
            //System.err.println( String.format( "##%d# %s: %d\n", id, string, (System.currentTimeMillis() - t0) ) );
        } else {
            if ( logger.isLoggable(Level.FINER) ) {
                logger.log( Level.FINER, String.format( "##%d# %s: %d\n", id, string, (System.currentTimeMillis() - t0) ) );
                if ( flushHandlers ) {
                    for ( Handler h: logger.getHandlers() ) {
                        h.flush();
                    }
                }
            }
        }
    }
}
