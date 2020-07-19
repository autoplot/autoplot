
package org.autoplot.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Secret function for adjusting the log level!
 * .../SetLogLevel?logger=autoplot.servlet&level=FINE&handler=T
 * @author jbf
 */
public class SetLogLevel extends HttpServlet {
   
    /** 
    * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
    * @param request servlet request
    * @param response servlet response
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
    */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        
        try (PrintWriter out = response.getWriter()) {
            
            String logger= request.getParameter("logger");
            String level= request.getParameter("level");
            String handler= request.getParameter("handler");
            String format= request.getParameter("format");
            
            String remoteAddr= request.getRemoteAddr();
            if ( !remoteAddr.equals("127.0.0.1" ) ) {
                out.println("<html>");
                out.println("<head>");
                out.println("<title>Servlet SetLogLevel</title>");  
                out.println("</head>");
                out.println("<body>");
                out.println("<p>.../SetLogLevel must be called from 127.0.0.1, was called from "+remoteAddr+"<br>");
                out.println("</code>");
                
            } else {
                if ( logger==null ) {
                    out.println("<html>");
                    out.println("<head>");
                    out.println("<title>Servlet SetLogLevel</title>");  
                    out.println("</head>");
                    out.println("<body>");
                    out.println("<p>.../SetLogLevel?logger=autoplot.servlet&level=FINE&handler=T<p><code><small>");
                    out.println("  logger  the logger name, autoplot.servlet is used in this servlet<br>");
                    out.println("  level   the level, FINE or FINER is used in this servlet<br>");
                    out.println("  handler if T then reset and report the handler levels as well<br>");
                    out.println("  format  =1 for single line to millisecond.<br>");
                    out.println("</code>");
                } else {

                    Handler[] hh= Logger.getLogger(logger).getHandlers();
                    Level lev= Level.parse(level);            
                    Logger l= Logger.getLogger(logger);
                    l.setLevel( lev );
                    Logger.getLogger(logger).log(lev, "reset to {0}", level);

                    out.println("<html>");
                    out.println("<head>");
                    out.println("<title>Servlet SetLogLevel</title>");  
                    out.println("</head>");
                    out.println("<body>");

                    out.println("<b>Loggers:</b><br>");
                    out.println(""+l +" @ "+l.getLevel()+"<br>" );

                    if ( hh.length==0 ) {
                        Logger.getLogger(logger).addHandler( new ConsoleHandler() );
                        hh= Logger.getLogger(logger).getHandlers();
                        out.println("<p>Added ConsoleHandler</p>"); 
                    }

                    out.println("<b>Handlers:</b><br>");
                    for ( Handler h: hh ) {
                        if ( handler!=null && lev!=null ) h.setLevel(lev);
                        out.println("  "+h+" @ "+h.getLevel()+"<br>");
                        if ( format!=null ) {
                            if ( format.equals("1") ) {
                                final DateFormat df = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss.SSS");
                                h.setFormatter( new SimpleFormatter() {
                                    @Override
                                    public synchronized String format(LogRecord record) {
                                        StringBuilder builder = new StringBuilder(1000);
                                        builder.append(df.format(new Date(record.getMillis()))).append(" - ");
                                        builder.append("[").append(record.getSourceClassName()).append(".");
                                        builder.append(record.getSourceMethodName()).append("] - ");
                                        builder.append("[").append(record.getLevel()).append("] - ");
                                        builder.append(formatMessage(record));
                                        builder.append("\n");
                                        return builder.toString();
                                    }
                                });
                            }
                        }
                    }
                    if ( hh.length==0 ) {
                        out.println("  (no handlers)");
                    }

                }
            }
            
            out.println("</body>");
            out.println("</html>");
            
            
        } catch ( IllegalArgumentException | SecurityException e ) {
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

}
