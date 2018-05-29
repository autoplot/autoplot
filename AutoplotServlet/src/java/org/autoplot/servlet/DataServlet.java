/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.servlet;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.autoplot.AutoplotDataServer;
import static org.autoplot.servlet.SimpleServlet.handler;
import org.das2.system.DasLogger;
import org.das2.util.TimerConsoleFormatter;
import org.das2.util.monitor.NullProgressMonitor;

/**
 * DataServlet wraps the command-line data server, so that start-up time is 
 * not an issue and memory caching is used.
 * @author jbf
 */
public class DataServlet extends HttpServlet {
    
     private static final Logger logger= Logger.getLogger("autoplot.servlet" );
     public static final String version= "v20180526.1255";

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
        } catch (IOException | SecurityException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
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
        
         try (OutputStream outs = response.getOutputStream()) {
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
             String suri = request.getParameter("uri");
             if ( suri==null ) {
                 suri = request.getParameter("url");
             }
             String stimeRange = ServletUtil.getStringParameter(request, "timeRange", "");
             String step= request.getParameter("timeStep");
             if ( step==null ) {
                 step= "86400s";
             }
             String format= request.getParameter("format");
             if ( format==null ) {
                 format="hapi-csv";
             }
             
             ServletUtil.SecurityResponse check= ServletUtil.checkSecurity( response, null, suri, null );
             ServletUtil.securityCheckPart2(check);
             
             Set outEmpty= new HashSet<>();
             AutoplotDataServer.doService( stimeRange, suri, step, true, format,
                     new PrintStream( outs ),
                     true, outEmpty, new NullProgressMonitor() );
         } catch (Exception ex) {
             Logger.getLogger(DataServlet.class.getName()).log(Level.SEVERE, null, ex);
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
