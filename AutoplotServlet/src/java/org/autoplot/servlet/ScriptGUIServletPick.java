/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.servlet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.das2.datum.Datum;
import org.das2.datum.TimeParser;
import org.das2.datum.TimeUtil;

/**
 *
 * @author jbf
 */
public class ScriptGUIServletPick extends HttpServlet {
    
    static final Logger logger= Logger.getLogger("autoplot.servlet.scriptgui");
    
    private static File getLog() {
        File scriptLogArea= new File( ServletUtil.getServletHome(), "log" );
            if ( !scriptLogArea.exists() ) {
                if ( !scriptLogArea.mkdirs() ) {
                    logger.warning("unable to make log area");
                }
            }
            File scriptLogFile= new File( scriptLogArea, "ScriptGUIServlet.log" );
            return scriptLogFile;
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
        response.setContentType("text/html;charset=UTF-8");
        //String script= request.getParameter("script");
        try (PrintWriter out = response.getWriter()) {
            out.println("<body>");
            out.println("Recent Scripts:<br>");
            File scriptLogFile= getLog();
            if ( scriptLogFile.exists() ) {
                HashSet<String> scripts= new LinkedHashSet<>();
                try ( BufferedReader r= new BufferedReader( new FileReader(scriptLogFile) ) ) {
                    String s= r.readLine();
                    while ( s!=null ) {
                        String script= s.substring(25);
                        scripts.remove(script);
                        scripts.add(script);
                        s= r.readLine();
                    }
                } catch ( IOException ex ) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
                out.println("<ul>\n");
                for ( String s: scripts ) {
                    out.print("<li>");
                    out.print("<a href='ScriptGUIServlet?script="+s+"'>");
                    out.print(s);
                    out.println("</li>");
                }
                out.println("</ul>\n");
            }
            out.println("</body></html>");
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
