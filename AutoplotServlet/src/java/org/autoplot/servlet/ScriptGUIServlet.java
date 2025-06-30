
package org.autoplot.servlet;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.net.UnknownHostException;
import java.nio.file.Paths;
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
import javax.imageio.ImageIO;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.autoplot.ApplicationModel;
import org.autoplot.JythonUtil;
import org.autoplot.ScriptContext2023;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.datasource.URISplit;
import org.autoplot.dom.Application;
import org.autoplot.jythonsupport.JythonRefactory;
import org.autoplot.jythonsupport.Param;
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
import org.das2.util.awt.GraphicsOutput;
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

    /**
     * milliseconds within which script must run
     */
    private static final int TIMEOUT_SCRIPT = 50000;
    
    static {
        timelogger= Logger.getLogger("autoplot.servlet.script.gui.timing");
        timelogger.setLevel(Level.FINE);
        try {
            // %h is user.home
            String home= System.getProperty("user.home"); //TODO: verify this new code which makes the log directory
            File hf= Paths.get( home, "log", "tomcat" ).toFile();
            if ( !hf.exists() ) {
                if ( !hf.mkdirs() ) {
                    throw new RuntimeException("Unable to make log directory: "+hf);
                }
            }
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
        } catch (IOException | SecurityException ex) {
            Logger.getLogger(ScriptGUIServlet.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    File logfile= new File( "/home/jbf/log/ScriptGUIServlet.log" );

    @Override
    public void init(ServletConfig config) throws ServletException {
        System.setProperty("java.awt.headless", "true");
        super.init(config); 
    }
    
    
    /**
     * write out the current canvas to stdout.  This is introduced to support servers.
     * TODO: this has issues with the size.  See writeToPng(filename).
     * @param dom
     * @param out the OutputStream accepting the data, which is not closed.
     * @throws java.io.IOException
     */
    private static void writeToPng( Application dom, String suri, BufferedImage orig, OutputStream out) throws IOException {
        dom.getController().getScriptContext().waitUntilIdle();

        DasCanvas c = dom.getController().getApplicationModel().getCanvas();
        int width= dom.getCanvases(0).getWidth();
        int height= dom.getCanvases(0).getHeight();

        BufferedImage image = c.getImage(width,height);

        if ( image.equals(orig) ) {
            BufferedImage im= new BufferedImage( 320, 200, BufferedImage.TYPE_INT_ARGB );
            Graphics g= im.getGraphics();
            g.setColor( Color.WHITE );
            g.fillRect( 0, 0, width, height );
            g.setColor( Color.GRAY );
            g.drawRect( 0, 0, width-1, height-1 );
            g.drawString( "(canvas not used)", 10, 100 );
            image= im;
        }
        
        DasPNGEncoder encoder = new DasPNGEncoder();
        encoder.addText(DasPNGConstants.KEYWORD_CREATION_TIME, new Date().toString());
        encoder.addText(DasPNGConstants.KEYWORD_SOFTWARE, "Autoplot" );
        encoder.addText(DasPNGConstants.KEYWORD_PLOT_INFO, c.getImageMetadata() ); 
        encoder.addText("ScriptURI",suri);

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
        
        if ( script==null ) {
            response.sendRedirect( "ScriptGUIServletPick" );
            return;
        }
        
        if ( !ServletUtil.isWhitelisted(script) ) {
            throw new IllegalArgumentException("script must come from whitelisted host, contact " +ServletUtil.getServletContact() 
                    + " to see if script could be run: "+script );
        }
        
        String mode;
        if ( request.getParameter("img")!=null ) {
            mode= "img";
        } else if ( "Print SVG".equals(request.getParameter("printsvg")) ) {
            mode= "svg";
        } else if ( "Print PDF".equals( request.getParameter("printpdf")) ) {
            mode= "pdf";
        } else if ( request.getParameter("text")!=null ) {
            mode= "text";
        } else {
            mode= "init";
        }
        
        long t0= System.currentTimeMillis();
        String key= request.getParameter("key");
        if ( key==null ) key="";
        
        logger.log( Level.INFO, "enter ScriptGUIServlet: {0} {1}", new Object[] { mode, key } );
        
        Map params= request.getParameterMap();
        Map<String,String> ssparams= new LinkedHashMap<>();
        StringBuilder sbparams= new StringBuilder();
        for ( Object o: params.entrySet() ) {
            Entry e= (Entry)o;
            String value= Array.get(e.getValue(),0).toString();
            ssparams.put( e.getKey().toString(), value );
            sbparams.append("&").append(e.getKey().toString()).append("=").append(value);
        }
        String sparams= sbparams.toString();

        String scriptURI= script;
        
        URISplit split= URISplit.parse(script);
        String pwd= split.path;
        String name= split.file.substring(split.path.length());
                
        File scriptFile= DataSetURI.getFile( script, new NullProgressMonitor() );
        script= FileUtil.readFileToString(scriptFile);            
        
        if ( request.getParameter("img")!=null ) {
            
            if ( !( getKeyFile( key,".png.t" ).exists() || getKeyFile( key,".png" ).exists() ) ) {
                throw new IOException( "invalid key: "+key );
            } 
            writeOutputImage( key, response );

        } else if ( "Print SVG".equals( request.getParameter("printsvg") ) ) {
            String[] ss= new String[ssparams.size()];
            int i=0;
            for ( Entry<String,String> e: ssparams.entrySet() ) {
                ss[i] = e.getKey() + "=" + e.getValue();
                i++;
            }
            
            printScript( response, key, scriptURI, script, name, ss, pwd, "svg" );

        } else if ( "Print PDF".equals( request.getParameter("printpdf") ) ) {
            String[] ss= new String[ssparams.size()];
            int i=0;
            for ( Entry<String,String> e: ssparams.entrySet() ) {
                ss[i] = e.getKey() + "=" + e.getValue();
                i++;
            }
            
            printScript( response, key, scriptURI, script, name, ss, pwd, "pdf" );
            
            
        } else if ( request.getParameter("text")!=null ) {
            
            if ( !( getKeyFile( key,".txt.t" ).exists() || getKeyFile( key,".txt" ).exists() ) ) {
                throw new IOException( "invalid key: "+key );
            } 
            writeOutputText( key, response );
            
        } else {

            // key= String.format( "%06d", (int)( Math.random() * 100000 ) );
            key = String.format( "%010d", Math.abs(sparams.hashCode()) );

            writeParametersForm(key, response, pwd, script, ssparams, name, scriptURI, sparams);
            
            String[] ss= new String[ssparams.size()];
            int i=0;
            for ( Entry<String,String> e: ssparams.entrySet() ) {
                ss[i] = e.getKey() + "=" + e.getValue();
                i++;
            }
            
            boolean runScript= true;
            
            File existingImageFile = getKeyFile( key, ".png" );
            if ( existingImageFile.exists() ) {
                long ageMillis= t0 - existingImageFile.lastModified();
                if ( ageMillis<60000 ) {
                    runScript= false;
                }
            }
            
            if ( runScript ) {
                existingImageFile = getKeyFile( key, ".png.t" );
                if ( existingImageFile.exists() ) {
                    long ageMillis= t0 - existingImageFile.lastModified();
                    if ( ageMillis<60000 ) {
                        runScript= false;
                    }
                }
            }
            
            if ( runScript ) {
                // create empty new files for placeholders.
                File textKeyFile= getKeyFile( key, ".txt.t" );
                if ( textKeyFile.exists() ) {
                    if ( !textKeyFile.delete() ) {
                        throw new IllegalArgumentException("unable to delete temporary key file.  Please refresh to try again.");
                    }
                }
                if ( !textKeyFile.createNewFile() ) throw new IllegalArgumentException("unable to create file: "+textKeyFile);
                File imageKeyFile =  getKeyFile( key, ".png.t" );
                if ( imageKeyFile.exists() ) {
                    if ( !imageKeyFile.delete() ) {
                        throw new IllegalArgumentException("unable to delete temporary image file.  Please refresh to try again.");
                    }
                }
                if ( !imageKeyFile.createNewFile() ) throw new IllegalArgumentException("unable to create file: "+imageKeyFile);
        
                startScript( request, key, scriptURI, script, name, ss, pwd );
            }
            
        }
        
        logger.log( Level.INFO, "exit ScriptGUIServlet: {0} {1} {2}",
                new Object[] { mode, key, String.format("%.2f", (System.currentTimeMillis()-t0)/1000. ) } );
        
    }

    /**
     * park this thread and wait for the image to be output by the other thread.  
     * @param key
     * @param response
     * @throws IOException
     * @throws UnknownHostException 
     */
    private void writeOutputImage( String key, HttpServletResponse response ) throws IOException, UnknownHostException {
        
        File keyFile= getKeyFile( key,".png" );
        
        long t0= System.currentTimeMillis();
        while ( !keyFile.exists() ) {
            if ( ( System.currentTimeMillis()-t0 )> TIMEOUT_SCRIPT ) {
                throw new IOException( String.format( "timeout, process takes longer than %d seconds", TIMEOUT_SCRIPT/1000 ) );
            }
            Thread.yield();
        }
        
        // To support load balancing, insert the actual host that resolved the request
        response.setHeader( "X-Served-By", java.net.InetAddress.getLocalHost().getCanonicalHostName() );
        response.setHeader( "X-ScriptGUIServlet-Key", key );
        
        try ( InputStream ins= new FileInputStream(keyFile); OutputStream out = response.getOutputStream() ) {
            byte[] buf= new byte[48000];
            int i;
            while ( (i=ins.read(buf))>-1 ) {
                out.write( buf, 0, i );
            }
        }
    }
    
    /**
     * park this thread and wait for the text to be output by the other thread.
     * @param key
     * @param response
     * @throws IOException
     * @throws UnknownHostException 
     */
    private void writeOutputText( String key, HttpServletResponse response ) throws IOException, UnknownHostException {
        
        File keyFile= getKeyFile( key,".txt" );
        
        long t0= System.currentTimeMillis();
        while ( !keyFile.exists() ) {
            if ( ( System.currentTimeMillis()-t0 )> TIMEOUT_SCRIPT ) {
                throw new IOException( String.format( "timeout, process takes longer than %d seconds", TIMEOUT_SCRIPT/1000 ) );
            }
            Thread.yield();
        }
        
        response.setHeader( "X-Served-By", java.net.InetAddress.getLocalHost().getCanonicalHostName() );
        response.setHeader( "X-ScriptGUIServlet-Key", key );
        
        try ( InputStream ins= new FileInputStream(keyFile); OutputStream out = response.getOutputStream() ) {
            byte[] buf= new byte[48000];
            int i;
            while ( (i=ins.read(buf))>-1 ) {
                out.write( buf, 0, i );
            }
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
        
        PySystemState.initialize( PySystemState.getBaseProperties(), null, pyInitArgv ); 
                // legacy support sys.argv. now we use getParam
        
        PythonInterpreter interp = JythonUtil.createInterpreter(true, false, dom, new NullProgressMonitor() );
        if ( pwd!=null ) {
            pwd= URISplit.format( URISplit.parse(pwd) ); // sanity check against injections
            interp.exec("PWD='"+pwd+"'");// JythonRefactory okay
        }

        interp.exec("import autoplot2023 as autoplot");// JythonRefactory okay
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
    
    private void printScript( HttpServletResponse response, String key, String scriptURI, String script, 
            String name, String[] aaparams, String pwd, String format ) throws IOException {
        try {
            File scriptLogArea= new File( ServletUtil.getServletHome(), "log" );
            if ( !scriptLogArea.exists() ) {
                if ( !scriptLogArea.mkdirs() ) {
                    logger.warning("unable to make log area");
                }
            }
            File scriptLogFile= new File( scriptLogArea, "ScriptGUIServlet.log" );
            Datum n= TimeUtil.now();
            TimeParser tp= TimeParser.create( TimeParser.TIMEFORMAT_Z );
            String s= tp.format( n ) + "\t" + key + "\t" + scriptURI ;
            
            try ( PrintWriter w= new PrintWriter( new FileWriter( scriptLogFile, scriptLogFile.exists() ) ) ) {
                w.println(s);
            }
            
            if ( !"true".equals(System.getProperty("java.awt.headless")) ) {
                throw new IllegalArgumentException("java.awt.headless must be set to true");
            }
            
            org.autoplot.jythonsupport.JythonUtil.ScriptDescriptor sd0= 
                    org.autoplot.jythonsupport.JythonUtil.describeScript( null, script, null );
            aaparams= interpretAsParams( sd0, aaparams );
            
            logger.fine("add fonts, which must be done with headless mode");
            //System.getProperty("java.awt.headless");
            org.autoplot.Util.addFonts(); // PROBLEMS
            
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
            
            script= JythonRefactory.fixImports(script);
            
            ScriptContext2023 scriptContext= dom.getController().getScriptContext();
            scriptContext.setApplicationModel(model); // why must I do this???
            
            script= "def showMessageDialog(msg): \n    pass\n" + script;
            
            long t0= System.currentTimeMillis();
            timelogger.log(Level.FINE, "begin runScript {0}", name);
            
            File consoleKeyFile= getKeyFile( key, ".txt.t" );
                        
            try ( OutputStream baos= new FileOutputStream( consoleKeyFile, true ) ) {
                runScript( dom,
                        new ByteArrayInputStream(script.getBytes("UTF-8")),
                        baos,
                        name,
                        aaparams,
                        pwd );
            } catch ( Exception ex ) {
                try ( PrintWriter write= new PrintWriter( new FileWriter( consoleKeyFile, true ) ) ) {
                    ex.printStackTrace(write);
                }
            }
            
            long elapsedTime= System.currentTimeMillis()-t0;
            timelogger.log(Level.FINE, "end printScript {0} ({1}ms)", new Object[]{name, elapsedTime });
            
            scriptContext.waitUntilIdle();
            
            if ( format.equals("svg") ) {
                Class goClass = Class.forName("org.das2.util.awt.SvgGraphicsOutput");
                GraphicsOutput go = (GraphicsOutput) goClass.newInstance();
                
                DasCanvas c = dom.getController().getApplicationModel().getCanvas();

                c.writeToGraphicsOutput( response.getOutputStream(), go );
                
            } else {
                Class goClass = Class.forName( "org.das2.util.awt.PdfGraphicsOutput" );
                GraphicsOutput go = (GraphicsOutput) goClass.newInstance();
                
                DasCanvas c = dom.getController().getApplicationModel().getCanvas();

                c.writeToGraphicsOutput( response.getOutputStream(), go );
                
            }
            
            
        } catch (InstantiationException ex) {
            Logger.getLogger(ScriptGUIServlet.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(ScriptGUIServlet.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(ScriptGUIServlet.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    
    private synchronized void startScript( HttpServletRequest request, String key, String scriptURI, String script, String name, String[] aaparams, String pwd) throws IOException {
        
        File scriptLogArea= new File( ServletUtil.getServletHome(), "log" );
        if ( !scriptLogArea.exists() ) {
            if ( !scriptLogArea.mkdirs() ) {
                logger.warning("unable to make log area");
            }
        }            
        File scriptLogFile= new File( scriptLogArea, "ScriptGUIServlet.log" );
        Datum n= TimeUtil.now();
        TimeParser tp= TimeParser.create( TimeParser.TIMEFORMAT_Z );
        String s= tp.format( n ) + "\t" + key + "\t" + scriptURI ;

        try ( PrintWriter w= new PrintWriter( new FileWriter( scriptLogFile, scriptLogFile.exists() ) ) ) {
            w.println(s);
        }
        
        if ( !"true".equals(System.getProperty("java.awt.headless")) ) {
            throw new IllegalArgumentException("java.awt.headless must be set to true");
        }
        
        logger.fine("add fonts, which must be done with headless mode");
        //System.getProperty("java.awt.headless");
        org.autoplot.Util.addFonts(); // PROBLEMS
        
        ApplicationModel model = new ApplicationModel();
        model.setExceptionHandler( new DumpRteExceptionHandler() );
        model.addDasPeersToAppAndWait();
        Application dom= model.getDom();
        
        logger.log(Level.FINE, "dom: {0}", dom);
        logger.log(Level.FINE, "dom options: {0}", dom.getOptions());
        
        dom.getOptions().setAutolayout(false);
        
        ScriptContext2023 scriptContext= new ScriptContext2023();
        dom.getController().setScriptContext( scriptContext );
        
        if ( !scriptContext.isModelInitialized() ) {
            scriptContext.setApplicationModel(model);
        }
        //PythonInterpreter interp = JythonUtil.createInterpreter( true, true );
        //interp.set("java",null);
        //interp.set("org",null);
        //interp.set("getFile",null);
        //interp.set("dom",dom);
        //interp.set("downloadResourceAsTempFile",null);
        
        //LoggingOutputStream los1= new LoggingOutputStream( Logger.getLogger("autoplot.servlet.scriptservlet"), Level.INFO );
        //interp.setOut( los1 );
                        
        script= JythonRefactory.fixImports(script);
        
        
        script= "def showMessageDialog(msg): \n    pass\n" + script;
        
        long t0= System.currentTimeMillis();
        timelogger.log(Level.FINE, "begin runScript {0}", name);
        
        File consoleKeyFile= getKeyFile( key, ".txt.t" );
        
        DasCanvas c = dom.getController().getApplicationModel().getCanvas();
        int width= dom.getCanvases(0).getWidth();
        int height= dom.getCanvases(0).getHeight();

        BufferedImage baseImage = c.getImage(width,height);
        
        boolean success= false;
        
        try ( OutputStream baos= new FileOutputStream( consoleKeyFile, true ) ) {
            baos.write( ("running script..."+name+"\n").getBytes() );
            runScript( dom,
                new ByteArrayInputStream(script.getBytes("UTF-8")),
                baos,
                name,
                aaparams,
                pwd );
            success= true;
        } catch ( Exception ex ) {
            try ( PrintWriter write= new PrintWriter( new FileWriter( consoleKeyFile, true ) ) ) {
                ex.printStackTrace(write);
            }
        }

        if ( success ) {
            File imageKeyFile =  getKeyFile( key, ".png.t" );
            try ( FileOutputStream out= new FileOutputStream( imageKeyFile, true ) ) {
                Map<String,Object> params= new LinkedHashMap<>();
                for ( int i=0; i<aaparams.length; i++ ) {
                    String p= aaparams[i];
                    if ( p.startsWith("script=") ) continue;
                    int ieq= p.indexOf("=");
                    if ( ieq>-1 ) params.put( p.substring(0,ieq), p.substring(ieq+1) );
                }
                String suri= URISplit.format( "script", scriptURI, params );
                writeToPng(dom, suri, baseImage, out );
            }
            if ( !imageKeyFile.renameTo( getKeyFile( key, ".png" ) ) ) {
                throw new IllegalArgumentException("unable to rename file (.png)");
            }
            if ( !consoleKeyFile.renameTo( getKeyFile( key, ".txt" ) ) ) {
                throw new IllegalArgumentException("unable to rename file (.txt)");
            }
            
        } else {
            // clean up after ourselves...
            File imageKeyFile =  getKeyFile( key, ".png.t" );
            BufferedImage im= new BufferedImage( 400, 400, BufferedImage.TYPE_INT_ARGB );
            Graphics2D g= (Graphics2D) im.getGraphics();
            g.setColor( Color.LIGHT_GRAY );
            g.fillRoundRect( 50,50,300,300,16,16 );
            g.dispose();
            ImageIO.write( im, "png", getKeyFile( key, ".png" ) );
            boolean throwException=false;
            if ( ! imageKeyFile.delete() ) {
                throwException= true;
            } 
            if ( !consoleKeyFile.renameTo( getKeyFile( key, ".txt" ) ) ) {
                throw new IllegalArgumentException("unable to rename file (.txt)");
            }
            if ( throwException ) {
                throw new IOException("unable to delete png or console output, someone will have to deal with this...");
            }
        }
        
        long elapsedTime= System.currentTimeMillis()-t0;
        File keyLogFile= getKeyFile( key, ".stats" );
        try ( PrintStream outs= new PrintStream( new FileOutputStream( keyLogFile ) ) ) {
            outs.println( "ExecutionTimeMs: "+ elapsedTime );
            outs.println( "Script: "+ scriptURI + "?"+ String.join("&", aaparams) );
            outs.println( "ClientId: "+ SecurityUtil.clientId(request) );
            outs.println( "UserAgent: " + request.getHeader("User-Agent") );
        }
        
        timelogger.log(Level.FINE, "end runScript {0} ({1}ms)", new Object[]{name, elapsedTime });
        
    }
    
    private static boolean isBoolean( Param p ) {
        if ( p.enums==null || p.enums.size()!=2 ) return false;
        if ( p.type=='A' && p.enums.size()==2 && p.enums.contains("T") && p.enums.contains("F") ) return true;
        if ( p.type=='F' && p.enums.size()==2 && p.enums.contains(0) && p.enums.contains(1) ) return true;
        return false;
    }

    /**
     * go through and convert "on" to "1", etc.  Note doubles will still be Strings, URIs will still be Strings, etc.
     * ssparams will be modified and a copy is returned.
     * @param sd0
     * @param ssparams
     * @return the map is returned as a string-object version for convenience.
     */
    private static Map<String,Object> interpretAsParams( org.autoplot.jythonsupport.JythonUtil.ScriptDescriptor sd0, 
            Map<String,String> ssparams ) {
        Map<String,Object> result= new LinkedHashMap<>();
        // go through and convert "on" to 1, etc.
        for ( Param p: sd0.getParams() ) {
            String svalue= ssparams.get(p.name);
            if ( isBoolean(p) ) {
                if ( svalue!=null ) {
                    if ( svalue.equals("on") ) {
                        if ( p.type=='F' ) {
                            result.put( p.name, "1" );
                            ssparams.put( p.name, "1" );
                        } else {
                            result.put( p.name, "T" );
                            ssparams.put( p.name, "T" );
                        }
                    } else {
                        if ( p.type=='F' ) {
                            result.put( p.name, "0" );
                            ssparams.put( p.name, "0" );
                        } else {
                            result.put( p.name, "F" );
                            ssparams.put( p.name, "F" );
                        }
                    }
                } 
            } else {
                result.put( p.name, svalue );
            }
        }
        return result;
    }
    
    /**
     * go through and convert "on" to "1", etc.  Note doubles will still be Strings, URIs will still be Strings, etc.
     * ssparams will be modified and a copy is returned.
     * @param sd0
     * @param ssparams
     * @return the map is returned as a string-object version for convenience.
     */
    private static String[] interpretAsParams( org.autoplot.jythonsupport.JythonUtil.ScriptDescriptor sd0, 
            String[] ssparams ) {
        for ( int i=0; i<ssparams.length; i++ ) {
            if ( ssparams[i].endsWith("=on") ) {
                ssparams[i]= ssparams[i].substring(0,ssparams[i].length()-3) + "=1";
            } else if (ssparams[i].endsWith("=off") ) {
                ssparams[i]= ssparams[i].substring(0,ssparams[i].length()-3) + "=0";
            }
        }
        //TODO: much
        return ssparams;
    }

    private void writeParametersForm( String key, HttpServletResponse response, 
            String pwd, 
            String script, 
            Map<String, String> ssparams, 
            String name, 
            String scriptURI, 
            String sparams) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
                
        Map<String,Object> env= new HashMap<>();
        env.put( "PWD", pwd );
        
        long t0= System.currentTimeMillis();
        timelogger.log(Level.FINE, "begin describeScript {0}", name);
        org.autoplot.jythonsupport.JythonUtil.ScriptDescriptor sd0= 
            org.autoplot.jythonsupport.JythonUtil.describeScript( env, script, null );
        
        Map<String,Object> params= interpretAsParams( sd0, ssparams );
        
        org.autoplot.jythonsupport.JythonUtil.ScriptDescriptor sd= 
            org.autoplot.jythonsupport.JythonUtil.describeScript( env, script, ssparams );
        timelogger.log(Level.FINE, "end describeScript {0} ({1}ms)", new Object[]{name, System.currentTimeMillis()-t0});
        
        String uri= URISplit.format( "vap+jy", scriptURI, params );
                
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html>");
            out.println("<head>");
            out.println("<title>"+name+"</title>");
            out.println("<script src=\"ScriptGUIServlet.js\"></script>");
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
                boolean isCheckBox= isBoolean(p);
                if ( !isCheckBox ) {
                    out.println(""+p.name + andDoc +"<br>");
                } else {
                    out.println("<label>");
                }
                if ( p.enums!=null ) {
                    if ( isCheckBox ) {
                        if ( "T".equals(currentValue) ) {
                            out.println("<input type='checkbox' name='"+p.name+"' checked>"+p.name + andDoc );
                        } else if ( currentValue.equals(1) ) {
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
                    out.println("<script language='javascript' src='sprintf.js'></script>");
                    out.println("<script language='javascript' src='TimeUtil.js'></script>");
                    out.println("<script language='javascript' src='util.js'></script>");
                    out.println("<input name='"+p.name+"' value='"+s+"' size='34'></input>");
                    out.println("<button title='update' onclick='updateInterval("+p.name+")' hidden=1> UP</button><br>");
                    out.println("<button title='Previous interval' onclick='previousInterval("+p.name+")'>&lt;&lt; PREV</button>");
                    out.println("<button title='Next interval' onclick='nextInterval("+p.name+")'>NEXT &gt;&gt;</button>");
                    //out.println("<script language='javascript'>addScanButtons(dom.getElementById('"+p.name+"'),null,null)</script>");
                } else {
                    //TODO: GUIs for URIs and other parameters.
                    Object s= (p.value!=null) ? p.value : p.deft;
                    out.println("<input name='"+p.name+"' value='"+s+"'></input>");
                }
                if ( isCheckBox ) {
                    out.println("</label>");
                }
                out.println("<br><br>");
            }
            if ( sd.getParams().isEmpty() ) {
                out.println("script has no parameters.");
                out.println("<br><br>");
            }
            
            out.println("<input type='hidden' name='script' value='"+scriptURI+"'>\n");
            out.println("<input type='submit' value='Submit'>\n");
            out.println("<hr>\n");
            out.println("<input type='submit' value='Print SVG' name='printsvg' value='svg'>\n");
            out.println("<input type='submit' value='Print PDF' name='printpdf' value='pdf'>\n");
            out.println("</form>\n");
            out.println( "</td>\n");
            out.println( "<td valign='top'>\n");
            out.println( "<div border=1></div>\n");
            out.println("<img id='outputImage' src='ScriptGUIServlet?img=1&key="+key+sparams+"' alt='image' width='800' height='500'>\n" );
            out.println( "</td>\n");
            out.println( "</tr>\n");
            out.println( "</table>\n");
            out.println("<br>\n");
            out.println("Console Output:<br>\n");
            out.println("<iframe id='stdoutp' src='ScriptGUIServlet?text=1&key="+key+sparams+"' width='800'></iframe>\n" );
            out.println( "<hr>\n" );
            out.println("Running script <a href="+scriptURI+">"+scriptURI+"</a>");
            out.println("Pick <a href='ScriptGUIServletPick'>another</a>...\n");
            out.println("<br><small>key="+key+" "+uri+"</small> ");
            out.println("<script>clearImageSizeWhenLoaded(document.getElementById('outputImage'));</script>");
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
