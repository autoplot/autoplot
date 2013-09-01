/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author jbf
 */
public class SetLogLevel extends HttpServlet {
   
    /** 
    * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
    * @param request servlet request
    * @param response servlet response
    */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        
        PrintWriter out= response.getWriter();
        
        try {
            
            String logger= request.getParameter("logger");
            String level= request.getParameter("level");
            String handler= request.getParameter("handler");
            
            if ( logger==null ) {
                out.println("<html>");
                out.println("<head>");
                out.println("<title>Servlet SetLogLevel</title>");  
                out.println("</head>");
                out.println("<body>");
                out.println(".../SetLogLevel?logger=autoplot.servlet&level=FINE&handler=T");
            } else {

                Handler[] hh= Logger.getLogger(logger).getHandlers();
                Level lev= Level.parse(level);            
                Logger l= Logger.getLogger(logger);
                l.setLevel( lev );
                Logger.getLogger(logger).log(lev, "reset to "+level);

                out.println("<html>");
                out.println("<head>");
                out.println("<title>Servlet SetLogLevel</title>");  
                out.println("</head>");
                out.println("<body>");

                out.println(""+l +" @ "+l.getLevel()+"<br>" );
            
                out.println("handlers:<br>");
                for ( Handler h: hh ) {
                    if ( handler!=null && lev!=null ) h.setLevel(lev);
                    out.println("  "+h+" @ "+h.getLevel()+"<br>");
                }
                if ( hh.length==0 ) {
                    out.println("  (no handlers)");
                }
                
            }
            
            out.println("</body>");
            out.println("</html>");
            
            
        } catch ( Exception e ) {
            throw new RuntimeException(e);
        } finally {
            out.close();
            
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
