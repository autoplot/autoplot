
package org.autoplot.servlet;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.autoplot.ApplicationModel;
import org.autoplot.JythonUtil;
import org.autoplot.ScriptContext;
import static org.autoplot.ScriptContext.waitUntilIdle;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.URISplit;
import org.autoplot.dom.Application;
import org.autoplot.jythonsupport.JythonRefactory;
import org.autoplot.jythonsupport.JythonUtil.Param;
import org.autoplot.scriptconsole.DumpRteExceptionHandler;
import org.autoplot.scriptconsole.LoggingOutputStream;
import org.das2.datum.Datum;
import org.das2.datum.TimeParser;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.graph.DasCanvas;
import org.das2.util.DasPNGConstants;
import org.das2.util.DasPNGEncoder;
import org.das2.util.FileUtil;
import org.das2.util.monitor.NullProgressMonitor;
import org.python.util.PythonInterpreter;

/**
 * Run a script on the server side, and produce a client-side GUI for the 
 * getParam calls.
 * Note this has logging installed which is set up in code, which is bad practice.  This should be 
 * removed should this be used in production!
 * @author jbf
 */
public class ScriptGUIServlet extends HttpServlet {

    static final Logger logger= Logger.getLogger("autoplot.servlet.scriptgui");
    
    static Logger timelogger;
    
    static {
        timelogger= Logger.getLogger("autoplot.servlet.script.gui.timing");
        timelogger.setLevel(Level.FINE);
        try {
            final Handler h= new FileHandler( "%h/log/tomcat/scriptGuiTiming.%g.log" ) {
                @Override
                public synchronized void publish(LogRecord record) {
                    super.publish(record);
                    flush();
                }
            };
            Formatter f= new Formatter() {
                @Override
                public String format(LogRecord record) {
                    String msg= formatMessage(record);
                    return Units.t1970.createDatum(record.getMillis()).toString() + ":" + msg + "\n";
                }
            };
            h.setFormatter( f );
            timelogger.addHandler(h);
        } catch (IOException ex) {
            Logger.getLogger(ScriptGUIServlet.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SecurityException ex) {
            Logger.getLogger(ScriptGUIServlet.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    File logfile= new File( "/home/jbf/log/ScriptGUIServlet.log" );
    
    /**
     * write out the current canvas to stdout.  This is introduced to support servers.
     * TODO: this has issues with the size.  See writeToPng(filename).
     * @param dom
     * @param out the OutputStream accepting the data, which is not closed.
     * @throws java.io.IOException
     */
    public static void writeToPng( Application dom, OutputStream out) throws IOException {
        waitUntilIdle();

        DasCanvas c = dom.getController().getApplicationModel().getCanvas();
        int width= dom.getCanvases(0).getWidth();
        int height= dom.getCanvases(0).getHeight();

        BufferedImage image = c.getImage(width,height);

        DasPNGEncoder encoder = new DasPNGEncoder();
        encoder.addText(DasPNGConstants.KEYWORD_CREATION_TIME, new Date().toString());
        encoder.addText(DasPNGConstants.KEYWORD_SOFTWARE, "Autoplot" );
        encoder.addText(DasPNGConstants.KEYWORD_PLOT_INFO, c.getImageMetadata() );        

        encoder.write( image, out);

    }
    
    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
 
        String script= request.getParameter("script");
        Map params= request.getParameterMap();
        Map<String,String> ssparams= new LinkedHashMap<>();
        ArrayList<String> slparams= new ArrayList<>();
        StringBuilder sbparams= new StringBuilder();
        for ( Object o: params.entrySet() ) {
            Entry e= (Entry)o;
            String value= Array.get(e.getValue(),0).toString();
            ssparams.put( e.getKey().toString(), value );
            slparams.add( e.getKey().toString() + "=" + value );
            sbparams.append("&").append(e.getKey().toString()).append("=").append(value);
        }
        String sparams= sbparams.toString();
        String[] aaparams= slparams.toArray(new String[slparams.size()]);
        
        if ( script==null ) {
            script= "https://github.com/autoplot/dev/blob/master/demos/2019/20190726/demoParams.jy";
        }
        
        if ( !ServletUtil.isWhitelisted(script) ) {
            throw new IllegalArgumentException("script must come from whitelisted host, contact " +ServletUtil.getServletContact() );
        }
        
        String scriptURI= script;
        
        URISplit split= URISplit.parse(script);
        String pwd= split.path;
        String name= split.file.substring(split.path.length());
                
        File scriptFile= DataSetURI.getFile( script, new NullProgressMonitor() );
        script= FileUtil.readFileToString(scriptFile);            
        
        if ( request.getParameter("img")!=null ) {
            writeOutputImage(scriptURI, response, script, name, aaparams, pwd);
            
        } else {
            writeParametersForm(response, pwd, script, ssparams, name, request, scriptURI, sparams);
            
        }
    }

    private void writeOutputImage(String scriptURI, 
            HttpServletResponse response, 
            String script, 
            String name, 
            String[] aaparams, 
            String pwd) throws IOException, UnknownHostException {
        // now run the script
        
        File scriptLogArea= new File( ServletUtil.getServletHome(), "log" );
        if ( !scriptLogArea.exists() ) {
            if ( !scriptLogArea.mkdirs() ) {
                logger.warning("unable to make log area");
            }
        }            
        File scriptLogFile= new File( scriptLogArea, "ScriptGUIServlet.log" );
        Datum n= TimeUtil.now();
        TimeParser tp= TimeParser.create( TimeParser.TIMEFORMAT_Z );
        String s= tp.format( n ) + "\t" + scriptURI;

        try ( PrintWriter w= new PrintWriter( new FileWriter( scriptLogFile, scriptLogFile.exists() ) ) ) {
            w.println(s);
        }
        
        org.autoplot.Util.addFonts();
        
        ApplicationModel model = new ApplicationModel();
        model.setExceptionHandler( new DumpRteExceptionHandler() );
        model.addDasPeersToAppAndWait();
        Application dom= model.getDocumentModel();
        
        logger.log(Level.FINE, "dom: {0}", dom);
        logger.log(Level.FINE, "dom options: {0}", dom.getOptions());
        
        dom.getOptions().setAutolayout(false);
        
        PythonInterpreter interp = JythonUtil.createInterpreter( true, true );
        interp.set("java",null);
        interp.set("org",null);
        interp.set("getFile",null);
        interp.set("dom",dom);
        interp.set("downloadResourceAsTempFile",null);
        
        LoggingOutputStream los1= new LoggingOutputStream( Logger.getLogger("autoplot.servlet.scriptservlet"), Level.INFO );
        interp.setOut( los1 );
        
        interp.set( "response", response );
        
        // To support load balancing, insert the actual host that resolved the request
        response.setHeader( "X-Served-By", java.net.InetAddress.getLocalHost().getCanonicalHostName() );
        
        //TODO: this limits to one user!
        LoggingOutputStream los2= new LoggingOutputStream( Logger.getLogger("autoplot.servlet.scriptservlet"), Level.INFO );
        //ScriptContext._setOutputStream( los2 );
        
        script= JythonRefactory.fixImports(script);
        
        ScriptContext.setApplicationModel(model); // why must I do this???
        
        script= "def showMessageDialog(msg): \n    pass\n" + script;
        
        long t0= System.currentTimeMillis();
        timelogger.log(Level.FINE, "begin runScript {0}", name);
        JythonUtil.runScript( dom,
                new ByteArrayInputStream(script.getBytes("UTF-8")),
                name,
                aaparams,
                pwd );
        timelogger.log(Level.FINE, "end runScript {0} ({1}ms)", new Object[]{name, System.currentTimeMillis()-t0});
                
        try (OutputStream out = response.getOutputStream()) {
            writeToPng(dom,out);
            try { los1.close(); } catch ( IOException ex ) {}
            try { los2.close(); } catch ( IOException ex ) {}
        }
    }
    
    /**
     * write the text generated by the script and reported with setMessageDialog.
     * @param scriptURI
     * @param response
     * @param script
     * @param name
     * @param aaparams
     * @param pwd
     * @throws IOException
     * @throws UnknownHostException 
     */
    private void writeOutputShowMessage( String scriptURI, 
            HttpServletResponse response, 
            String script, 
            String name, 
            String[] aaparams, 
            String pwd) throws IOException, UnknownHostException {
        // now run the script
        
        File scriptLogArea= new File( ServletUtil.getServletHome(), "log" );
        if ( !scriptLogArea.exists() ) {
            if ( !scriptLogArea.mkdirs() ) {
                logger.warning("unable to make log area");
            }
        }            
        File scriptLogFile= new File( scriptLogArea, "ScriptGUIServlet.log" );
        Datum n= TimeUtil.now();
        TimeParser tp= TimeParser.create( TimeParser.TIMEFORMAT_Z );
        String s= tp.format( n ) + "\t" + scriptURI;

        try ( PrintWriter w= new PrintWriter( new FileWriter( scriptLogFile, scriptLogFile.exists() ) ) ) {
            w.println(s);
        }
        
        org.autoplot.Util.addFonts();
        
        ApplicationModel model = new ApplicationModel();
        model.setExceptionHandler( new DumpRteExceptionHandler() );
        model.addDasPeersToAppAndWait();
        Application dom= model.getDocumentModel();
        
        logger.log(Level.FINE, "dom: {0}", dom);
        logger.log(Level.FINE, "dom options: {0}", dom.getOptions());
        
        dom.getOptions().setAutolayout(false);
        
        PythonInterpreter interp = JythonUtil.createInterpreter( true, true );
        interp.set("java",null);
        interp.set("org",null);
        interp.set("getFile",null);
        interp.set("dom",dom);
        interp.set("downloadResourceAsTempFile",null);
        
        LoggingOutputStream los1= new LoggingOutputStream( Logger.getLogger("autoplot.servlet.scriptservlet"), Level.INFO );
        interp.setOut( los1 );
        
        interp.set( "response", response );
        
        // To support load balancing, insert the actual host that resolved the request
        response.setHeader( "X-Served-By", java.net.InetAddress.getLocalHost().getCanonicalHostName() );
        
        //TODO: this limits to one user!
        LoggingOutputStream los2= new LoggingOutputStream( Logger.getLogger("autoplot.servlet.scriptservlet"), Level.INFO );
        //ScriptContext._setOutputStream( los2 );
        
        script= JythonRefactory.fixImports(script);
        
        ScriptContext.setApplicationModel(model); // why must I do this???
        
        script= "def showMessageDialog(msg): \n    print response\n    response.write(msg)\n\n" + script;
        
        JythonUtil.runScript( dom,
                new ByteArrayInputStream(script.getBytes("UTF-8")),
                name,
                aaparams,
                pwd );
        
        
    }
    
    private void writeParametersForm(HttpServletResponse response, String pwd, String script, Map<String, String> ssparams, String name, HttpServletRequest request, String scriptURI, String sparams) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        
        Map<String,Object> env= new HashMap<>();
        env.put( "PWD", pwd );
        
        long t0= System.currentTimeMillis();
        timelogger.log(Level.FINE, "begin describeScript {0}", name);
        org.autoplot.jythonsupport.JythonUtil.ScriptDescriptor sd= 
            org.autoplot.jythonsupport.JythonUtil.describeScript( env, script, ssparams );
        timelogger.log(Level.FINE, "end describeScript {0} ({1}ms)", new Object[]{name, System.currentTimeMillis()-t0});
        
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html>");
            out.println("<head>");
            out.println("<title>"+name+"</title>");
            out.println("</head>");
            out.println("<body>");
            if ( sd.getTitle().length()>0 ) {
                out.println("<h1>"+sd.getTitle()+"</h1>");
                if ( sd.getDescription().length()>0 ) {
                    out.println("<p>"+sd.getDescription() +"</p>");
                }
            } else {
                out.println("<h1>Servlet ScriptGUIServlet at " + request.getContextPath() + "</h1>");
            }
            out.println("<table><tr>");
            out.println("<td valign='top'>");
            out.println("<form action='ScriptGUIServlet'>");
            for ( Param p: sd.getParams() ) {
                p.doc= p.doc.trim();
                Object currentValue= p.value == null ? p.deft : p.value;
                String andDoc= p.doc.length()>0 ? ( ", <em>"+ p.doc +"</em>" ) : "";
                boolean isCheckBox= p.enums!=null && p.enums.size()==2 && p.enums.contains("T") && p.enums.contains("F");
                if ( !isCheckBox ) {
                    out.println(""+p.name + andDoc +"<br>");
                }
                if ( p.enums!=null ) {
                    if ( isCheckBox ) {
                        if ( "T".equals(currentValue) ) {
                            out.println("<input type='checkbox' name='"+p.name+"' checked>"+p.name + andDoc );
                        } else if ( "on".equals(currentValue) ) {
                            out.println("<input type='checkbox' name='"+p.name+"' checked>"+p.name + andDoc );
                            sparams= sparams.replace(p.name+"=on", p.name+"=T");
                        } else {
                            out.println("<input type='checkbox' name='"+p.name+"'>"+p.name + andDoc );
                        }
                    } else {
                        out.println("<select name='"+p.name+"'>");
                        for ( Object s: p.enums ) {
                            if ( s.equals(currentValue) ) {
                                out.println("<option value='"+s+"' selected>"+s+"</input>");
                            } else {
                                out.println("<option value='"+s+"'>"+s+"</input>");
                            }
                        }
                        out.println("</select>");
                    }
                } else if ( (p.type=='F') || (p.type=='A') ) {
                    Object s= (p.value!=null) ? p.value : p.deft;
                    if ( p.examples.size()>0 ) {
                        out.println("<input name='"+p.name+"' value='"+s+"' list='examples"+p.name+"'></input>");
                        out.println("<datalist id='examples"+p.name+"'>");
                        for ( Object ex: p.examples ) {
                            out.println("<option value='"+ex+"'>"+ex+"</option>");
                        }
                        out.println("</datalist>");
                    } else {
                        out.println("<input name='"+p.name+"' value='"+s+"'></input>");
                    }
                } else if ( p.type=='T' ) {
                    //TODO: nice timerange GUI
                    Object s= (p.value!=null) ? p.value : p.deft;
                    out.println("<input name='"+p.name+"' value='"+s+"'></input>");
                } else {
                    //TODO: GUIs for URIs and other parameters.
                    Object s= (p.value!=null) ? p.value : p.deft;
                    out.println("<input name='"+p.name+"' value='"+s+"'></input>");
                }
                out.println("<br><br>");
            }
            if ( sd.getParams().isEmpty() ) {
                out.println("script has no parameters.");
                out.println("<br><br>");
            }
            
            out.println("<input type='hidden' name='script' value='"+scriptURI+"'>");
            out.println("<input type='submit' value='Submit'>");
            out.println("</form>");
            out.println( "</td>");
            out.println( "<td valign='top'>");
            out.println( "<div border=1></div>");
            out.println( "<img src='ScriptGUIServlet?img=1"+sparams+"' alt='image'>" );
            out.println( "<div src='ScriptGUIServlet?text=1"+sparams+"' alt='image'>" );
            out.println( "</td>");
            out.println( "</tr>");
            out.println( "</table>");
            out.println( "<hr>" );
            out.println("Running script <a href="+scriptURI+">"+scriptURI+"</a>");
            out.println("Pick <a href='ScriptGUIServletPick'>another</a>...\n");
            out.println("</body>");
            out.close();
            
        }
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

}
