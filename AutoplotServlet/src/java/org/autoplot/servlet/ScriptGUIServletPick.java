/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.servlet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
    
    private static boolean isWhiteListed( String uri ) throws IOException {
        boolean whiteListed= false;
        List<String> whiteList= ServletUtil.getWhiteList();
        for ( String s: whiteList ) {
            if ( Pattern.matches( s, uri ) ) {
                whiteListed= true;
                logger.fine("uri is whitelisted");
            }
        }
        return whiteListed;
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
            out.println("Enter script from a whitelisted source, such as https://github.com/autoplot/dev/");
            out.println("<form action='ScriptGUIServlet'>");
            out.println("  <input name='script' value='https://github.com/autoplot/dev/blob/master/demos/2019/20190726/demoParams.jy' size='80' type='text'><br>");
            out.println("  <input type='submit' value='Submit'>");
            out.println("</form>");
            out.println("Recently run scripts:<br>");
            File scriptLogFile= getLog();
            if ( scriptLogFile.exists() ) {
                LinkedHashSet<String> scripts= new LinkedHashSet<>();
                try ( BufferedReader r= new BufferedReader( new FileReader(scriptLogFile) ) ) {
                    String s= r.readLine();
                    while ( s!=null ) {
                        String[] ss= s.split("\t");
                        String script= ss[2];
                        if ( isWhiteListed(script) ) {
                            scripts.remove(script);
                            scripts.add(script);
                        }
                        s= r.readLine();     
                    }
                } catch ( IOException ex ) {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
                out.println("<ul>\n");
                
                List<String> scriptsList= new ArrayList(scripts);
                Collections.reverse( scriptsList );
                
                int limit=20;
                int i=0;
                for ( String s: scriptsList ) {
                    out.print("<li>");
                    out.print("<a href='ScriptGUIServlet?script="+s+"'>");
                    out.print(s);
                    out.println("</li>");
                    i++;
                    if ( i==limit ) break;
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
