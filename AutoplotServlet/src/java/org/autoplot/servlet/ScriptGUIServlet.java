
package org.autoplot.servlet;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.net.UnknownHostException;
import java.nio.file.Paths;
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
import org.autoplot.datasource.DataSourceUtil;
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
import org.python.core.PySystemState;
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
    
    static final Logger timelogger;
    
    static {
        timelogger= Logger.getLogger("autoplot.servlet.script.gui.timing");
        timelogger.setLevel(Level.FINE);
        try {
            // %h is user.home
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
            response.sendRedirect( "ScriptGUIServletPick" );
            return;
        }
        
        if ( !ServletUtil.isWhitelisted(script) ) {
            throw new IllegalArgumentException("script must come from whitelisted host, contact " +ServletUtil.getServletContact() + " to see if script could be run: "+script );
        }
        
        String scriptURI= script;
        
        URISplit split= URISplit.parse(script);
        String pwd= split.path;
        String name= split.file.substring(split.path.length());
                
        File scriptFile= DataSetURI.getFile( script, new NullProgressMonitor() );
        script= FileUtil.readFileToString(scriptFile);            
        
        String key= request.getParameter("key");
        
        if ( request.getParameter("img")!=null ) {
            writeOutputImage( key, scriptURI, response, script, name, aaparams, pwd);
        } else if ( request.getParameter("text")!=null ) {
            writeOutputText( key, scriptURI, response, script, name, aaparams, pwd);
        } else {
            writeParametersForm( response, pwd, script, ssparams, name, request, scriptURI, sparams);
            
        }
    }

    private void writeOutputImage( String key, String scriptURI, 
            HttpServletResponse response, 
            String script, 
            String name, 
            String[] aaparams, 
            String pwd) throws IOException, UnknownHostException {
        // now run the script
        
        File keyFile= getKeyFile( key,".png" );
        
        while ( !keyFile.exists() ) {
            Thread.yield();
        }
        
        // To support load balancing, insert the actual host that resolved the request
        response.setHeader( "X-Served-By", java.net.InetAddress.getLocalHost().getCanonicalHostName() );
        
        try ( InputStream ins= new FileInputStream(keyFile); OutputStream out = response.getOutputStream() ) {
            byte[] buf= new byte[60000];
            int i;
            while ( (i=ins.read(buf))>-1 ) {
                out.write( buf, 0, i );
            }
        }
    }
    
    private void writeOutputText( String key, String scriptURI, 
            HttpServletResponse response, 
            String script, 
            String name, 
            String[] aaparams, 
            String pwd) throws IOException, UnknownHostException {
        // now run the script
        
        File keyFile= getKeyFile( key,".txt" );
        
        while ( !keyFile.exists() ) {
            Thread.yield();
        }
        
        response.setHeader( "X-Served-By", java.net.InetAddress.getLocalHost().getCanonicalHostName() );
        
        try ( InputStream ins= new FileInputStream(keyFile); OutputStream out = response.getOutputStream() ) {
            byte[] buf= new byte[60000];
            int i;
            while ( (i=ins.read(buf))>-1 ) {
                out.write( buf, 0, i );
            }
        }
        
    }
        
    private void  writeOutputs( String key, String scriptURI, 
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
        Application dom= model.getDom();
        
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
        
        ByteArrayOutputStream baos= new ByteArrayOutputStream();
        
        runScript( dom,
                new ByteArrayInputStream(script.getBytes("UTF-8")),
                baos,
                name,
                aaparams,
                pwd );
        timelogger.log(Level.FINE, "end runScript {0} ({1}ms)", new Object[]{name, System.currentTimeMillis()-t0});
                
        try (OutputStream out = response.getOutputStream()) {
            byte[] buf= new byte[60000];
            out.write( baos.toByteArray() );
            try { los1.close(); } catch ( IOException ex ) {}
            try { los2.close(); } catch ( IOException ex ) {}
        }
    }
    
    /**
     * copy of JythonUtil.runScript allows the stdout to be gathered.
     * @param dom
     * @param in
     * @param out
     * @param name
     * @param argv
     * @param pwd
     * @throws IOException 
     */
    private void runScript( Application dom, InputStream in, OutputStream out, String name, String[] argv, String pwd ) 
            throws IOException {
        if ( argv==null ) argv= new String[] {};
        
        String[] pyInitArgv= new String[ argv.length+1 ];
        pyInitArgv[0]= name;
        System.arraycopy(argv, 0, pyInitArgv, 1, argv.length);
        
        PySystemState.initialize( PySystemState.getBaseProperties(), null, pyInitArgv ); // legacy support sys.argv. now we use getParam
        
        PythonInterpreter interp = JythonUtil.createInterpreter(true, false, dom, new NullProgressMonitor() );
        if ( pwd!=null ) {
            pwd= URISplit.format( URISplit.parse(pwd) ); // sanity check against injections
            interp.exec("PWD='"+pwd+"'");// JythonRefactory okay
        }

        interp.exec("import autoplot2017 as autoplot");// JythonRefactory okay
        interp.setOut(out);
        
        int iargv=1;  // skip the zeroth one, it is the name of the script
        for (String s : argv ) {
            int ieq= s.indexOf('=');
            if ( ieq>0 ) {
                String snam= s.substring(0,ieq).trim();
                if ( DataSourceUtil.isJavaIdentifier(snam) ) {
                    String sval= s.substring(ieq+1).trim();
//                    if ( snam.equals("resourceURI") ) {  // check to see if pwd can be inserted
//                        URISplit split= URISplit.parse(sval);
//                        if ( split.path==null ) {
//                            sval= pwd + sval;
//                        }
//                    }
                    interp.exec("autoplot.params['" + snam + "']='" + sval+"'");// JythonRefactory okay
                } else {
                    if ( snam.startsWith("-") ) {
                        System.err.println("\n!!! Script arguments should not start with -, they should be name=value");
                    }
                    System.err.println("bad parameter: "+ snam);
                }
            } else {
                interp.exec("autoplot.params['arg_" + iargv + "']='" + s +"'" );// JythonRefactory okay
                iargv++;
            }
        }
        
        if ( name==null ) {
            interp.execfile(JythonRefactory.fixImports(in));
        } else {
            interp.execfile(JythonRefactory.fixImports(in,name),name);
        }

    }
    
    private File getKeyFile( String key, String ext ) {
        File keyhome=  new File( ServletUtil.getServletHome().getAbsolutePath(), "key" );
        if ( !keyhome.exists() ) {
            if ( !keyhome.mkdirs() ) {
                throw new IllegalArgumentException("Unable to make key file folder");
            }
        }
        return new File( keyhome, key+ ext );
    }
    
    private void startScript( String key, String scriptURI, 
            String script, 
            String name, 
            String[] aaparams, 
            String pwd ) throws IOException {
        
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
        Application dom= model.getDom();
        
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
                
        //TODO: this limits to one user!
        LoggingOutputStream los2= new LoggingOutputStream( Logger.getLogger("autoplot.servlet.scriptservlet"), Level.INFO );
        //ScriptContext._setOutputStream( los2 );
        
        script= JythonRefactory.fixImports(script);
        
        ScriptContext.setApplicationModel(model); // why must I do this???
        
        script= "def showMessageDialog(msg): \n    pass\n" + script;
        
        long t0= System.currentTimeMillis();
        timelogger.log(Level.FINE, "begin runScript {0}", name);
        
        File keyFile= getKeyFile( key, ".txt.t" );
        
        try ( OutputStream baos= new FileOutputStream( keyFile ) ) {    
            runScript( dom,
                new ByteArrayInputStream(script.getBytes("UTF-8")),
                baos,
                name,
                aaparams,
                pwd );
        }
        
        File imageKeyFile =  getKeyFile( key, ".png.t" );
        try ( FileOutputStream out= new FileOutputStream(imageKeyFile) ) {
            writeToPng( dom, out );
        }
        if ( !imageKeyFile.renameTo( getKeyFile( key, ".png" ) ) ) {
            throw new IllegalArgumentException("unable to rename file (.png)");
        }
        if ( !keyFile.renameTo( getKeyFile( key, ".txt" ) ) ) {
            throw new IllegalArgumentException("unable to rename file (.txt)");
        }
        
        timelogger.log(Level.FINE, "end runScript {0} ({1}ms)", new Object[]{name, System.currentTimeMillis()-t0});
        
    }
    
        
    private void writeParametersForm( HttpServletResponse response, 
            String pwd, 
            String script, 
            Map<String, String> ssparams, 
            String name, 
            HttpServletRequest request, 
            String scriptURI, 
            String sparams) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        
        String key= String.format( "%06d", (int)( Math.random() * 100000 ) );
        
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
                out.println("<h1>"+pwd + name+"</h1>");
                if ( sd.getDescription().length()>0 ) {
                    out.println("<p>"+sd.getDescription() +"</p>");
                }
            }
            out.println("<table><tr>");
            out.println("<td valign='top'>");
            out.println("<form action='ScriptGUIServlet'>");
            for ( Param p: sd.getParams() ) {
                p.doc= p.doc.trim();
                Object currentValue= p.value == null ? p.deft : p.value;
                String andDoc= p.doc.length()>0 ? ( ", <em>"+ p.doc +"</em>" ) : "";
                boolean isCheckBox= p.enums!=null && p.enums.size()==2 && 
                        ( ( p.enums.contains("T") && p.enums.contains("F") )
                        ||  ( p.enums.contains(0) && p.enums.contains(1) ) );
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
                    if ( p.examples!=null && p.examples.size()>0 ) {
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
                    out.println("<input name='"+p.name+"' value='"+s+"'></input><br>");
                    out.println("<script language='javascript' src='util.js'></script>");
                    out.println("<button title='Previous interval' onclick='scanPrev()'>&lt;&lt; PREV</button>");
                    out.println("<button title='Next interval' onclick='scanNext()'>NEXT &gt;&gt;</button>");
                    //out.println("<script language='javascript'>addScanButtons(dom.getElementById('"+p.name+"'),null,null)</script>");
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
            
            out.println("<input type='hidden' name='script' value='"+scriptURI+"'>\n");
            out.println("<input type='submit' value='Submit'>\n");
            out.println("</form>\n");
            out.println("<br>\n");
            out.println("Console Output:<br>\n");
            out.println( "<iframe id='stdoutp' src='ScriptGUIServlet?text=1&key="+key+sparams+"'></iframe>\n" );
            out.println( "</td>\n");
            out.println( "<td valign='top'>\n");
            out.println( "<div border=1></div>\n");
            out.println( "<img src='ScriptGUIServlet?img=1&key="+key+sparams+"' alt='image'>\n" );
            out.println( "</td>\n");
            out.println( "</tr>\n");
            out.println( "</table>\n");
            out.println( "<hr>\n" );
            out.println("Running script <a href="+scriptURI+">"+scriptURI+"</a>");
            out.println("Pick <a href='ScriptGUIServletPick'>another</a>...\n");
            out.println("<br><small>key="+key+"</small>");
            out.println("</body>");
            out.close();
            
            String[] ss= sparams.substring(1).split("\\&");
            
            startScript( key, scriptURI, script, name, ss, pwd );
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
