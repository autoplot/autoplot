
package org.autoplot.servlet;

import org.autoplot.JythonUtil;
import org.autoplot.ApplicationModel;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.autoplot.ScriptContext2023;
import org.autoplot.jythonsupport.JythonRefactory;
import org.das2.datum.TimeUtil;
import org.das2.datum.format.TimeDatumFormatter;
import org.python.util.PythonInterpreter;
import org.autoplot.scriptconsole.LoggingOutputStream;
import org.das2.util.monitor.NullProgressMonitor;

/**
 * Allow the clients to run scripts on the server.
 * For now, these are logged for security to the folder /tmp/autoplotservlet (or AUTOPLOT_SERVLET_HOME).
 * AUTOPLOT_SERVLET_HOME/allowhosts contains a list of allowed clients.  
 * See script.jsp for more information.
 *
 * @author jbf
 */
public class ScriptServlet extends HttpServlet {
   
    /** 
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     * @param request servlet request
     * @param response servlet response
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        try {

            ApplicationModel appmodel = new ApplicationModel();
            appmodel.addDasPeersToAppAndWait();

            String script= request.getParameter("script");
            if ( script==null ) {
                script= "setCanvasSize( 600, 400 )\n"+
                    "setDataSourceURL( 'http://www.sarahandjeremy.net/jeremy/1wire/data/2008/0B000800408DD710.20080117.d2s' )\n"+
                    "setTitle( 'Garage 20080117' )\n" +
                    "response.setContentType('image/png')\n" + 
                    "out = response.getOutputStream()\n" + 
                    "writeToPng( out )\n";
            }
            
            // do minimal taint checking.
            String[] ss= script.split("\n");
             
            for (String s : ss) {
                if (s.contains("import")) throw new IllegalArgumentException("imports not allowed for security");
                if (s.contains("eval")) throw new IllegalArgumentException("eval not allowed for security");
                if (s.contains("formatDataSet")) throw new IllegalArgumentException("formatDataSet not allowed for security");
            }

            String ts= new TimeDatumFormatter("%Y%m%dT%H%M%S.%{milli}").format( TimeUtil.now() );
            String who= request.getRemoteAddr();
            if ( who.equals("0:0:0:0:0:0:0:1") ) who= "localhost";
            if ( who.equals("127.0.0.1") ) who= "localhost";

            String home= System.getProperty( "AUTOPLOT_SERVLET_HOME" );
            if ( home==null ) home= "/tmp/autoplotservlet/";

            home= new File(home).getCanonicalPath();

            home= home + File.separator; 

            new File(home).mkdirs();

            File hostsallow= new File( home + "allowhosts" );

            synchronized ( ScriptServlet.class ) {
                if ( !hostsallow.exists() ) {
                    PrintWriter write= new PrintWriter( new FileWriter( hostsallow ) );
                    write.println("# Initially, only clients from the localhost can connect.  List the allowed clients, one per line.");
                    write.println("# Globs like 192.168.0.* may be used.");
                    write.println("localhost");
                    write.close();
                }
            }

            if ( hostsallow.exists() ) {
                boolean reject= true;
                BufferedReader r= new BufferedReader( new FileReader( hostsallow ) );
                String h= r.readLine();
                while ( h!=null ) {
                    int i= h.indexOf("#");
                    if (i>-1 ) h= h.substring(0,i);
                    if ( h.trim().length()==0 ) {
                        h= r.readLine();
                        continue;
                    }
                    h= h.replaceAll("\\*","\\.\\*");

                    if ( Pattern.matches( h, who ) ) {
                        reject= false;
                    }

                    h= r.readLine();
                }

                if ( reject ) {
                    response.sendError(403, hostsallow + " does not permit host=\""+who+"\"");
                    return;
                }
            } else {
                response.sendError(403, hostsallow + " file does not exist: "+hostsallow );
            }

            File f= new File( home + ts+"."+who+".jy" );
            FileWriter w= new FileWriter(f);
            w.append(script);
            w.close();

            ScriptContext2023 scriptContext= new ScriptContext2023();
            
            PythonInterpreter interp = JythonUtil.createInterpreter( true, true, scriptContext.getDocumentModel(), new NullProgressMonitor() );
            interp.set("java",null);
            interp.set("org",null);
            interp.set("getFile",null);
            interp.set("downloadResourceAsTempFile",null);
            
            interp.get("setCanvasSize");

            LoggingOutputStream los1= new LoggingOutputStream( Logger.getLogger("autoplot.servlet.scriptservlet"), Level.INFO );
            interp.setOut( los1 );
            
            interp.set( "response", response );

            // To support load balancing, insert the actual host that resolved the request
            response.setHeader( "X-Served-By", java.net.InetAddress.getLocalHost().getCanonicalHostName() );
                        
            LoggingOutputStream los2= new LoggingOutputStream( Logger.getLogger("autoplot.servlet.scriptservlet"), Level.INFO ); 
            scriptContext._setOutputStream( los2 ); 
            
            script= JythonRefactory.fixImports(script);
            interp.exec(script);
            
            try { los1.close(); } catch ( IOException ex ) {}
            try { los2.close(); } catch ( IOException ex ) {}
            
        } catch ( Exception ex ) {
            response.sendError(403, ex.toString() );
        } finally { 
            
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
        return "Autoplot ScriptServlet";
    }// </editor-fold>

}
