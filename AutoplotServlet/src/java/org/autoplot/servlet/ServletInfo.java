/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.servlet;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.das2.util.AboutUtil;
import static org.autoplot.servlet.SimpleServlet.version;

/**
 *
 * @author faden@cottagesystems.com
 */
public class ServletInfo extends HttpServlet {

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
        PrintWriter out = response.getWriter();
        try {
            /* TODO output your page here. You may use following sample code. */
            out.println("<!DOCTYPE html>");
            out.println("<html>");
            out.println("<head>");
            out.println("<title>Autoplot Servlet ServletInfo</title>");            
            out.println("</head>");
            out.println("<body>");
            out.println("<h1>Servlet ServletInfo at " + request.getContextPath() + "</h1>");
            
            String s = AboutUtil.getAboutHtml();
            s = s.substring(0, s.length() - 7);
            out.println(s);
                    
            File sd= ServletUtil.getServletHome();
            File ff= new File( sd, "whitelist.txt" );
            out.println("<h2>Whitelist File</h2>\n");
            out.println("Whitelist File: "+ff+"<br>");
            out.println("<ul>");
            List<String> ss= ServletUtil.getWhiteList();
            for ( String s1: ss ) {
                out.println("<li>"+s1+"\n");
            }
            if ( ss.isEmpty() ) {
                out.println("<li>(whitelist is empty)\n");
            }
            out.println("</ul>");
            out.println("<h2>Other Info</h2>\n");
            InetAddress localhost = java.net.InetAddress.getLocalHost();
            String hostName = localhost.getHostName();
            out.println("hostname: "+hostName);
            out.println("<br>user.name: "+ System.getProperty("user.name") + "\n"); // TODO: security concerns
            out.println("<br>java.home: "+ System.getProperty("java.home") + "\n"); // TODO: security concerns
            out.println("<p>PWD: "+ ( new File(".").getAbsolutePath() ) +"\n" );
            out.println("<br>Servlet Home: "+ServletUtil.getServletHome() + "\n"); // TODO: security concerns
            out.println("<br>servlet version="+version+"<br>");
            
            out.println("</p></body>");
            out.println("</html>");
        } finally {
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
