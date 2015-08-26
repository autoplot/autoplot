/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.flakey;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author faden@cottagesystems.com
 */
public class SlowFile extends HttpServlet {

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
        
        String id= request.getParameter("id");
        
        if ( id==null ) {
            id= request.getPathInfo();
        }
        
        if ( id.endsWith("/") ) {
            id= "index.jsp";
        } else {
            response.setContentType("application/x-das2stream");
        }
        
        
        String surl=  "file:/tmp/FlakeyServer/";
        
        URL url;
        if ( id.startsWith("/") ) {
            url= new URL( surl + "data"+id );
        } else {
            url= new URL( surl + "data/"+id);
        }
        
        int i=0;
        try ( InputStream in= url.openStream(); OutputStream out=response.getOutputStream() ) {
            int c= in.read();
            while ( c>-1 ) {
                out.write(c);
                i++;
                if ( i==2000 || i==24000 ) {
                    try {
                        System.err.println("artificial 10 second hang at byte offset "+i);
                        Thread.sleep(10000); // simulate hang
                    } catch (InterruptedException ex) {
                        Logger.getLogger(SlowFile.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                c= in.read();
            }
        }
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String id= request.getParameter("id");
        
        if ( id==null ) {
            id= request.getPathInfo();
        }
        
        if ( id.endsWith("/") ) {
            id= "index.jsp";
        } else {
            response.setContentType("application/x-das2stream");
        }
        
        
        String surl=  "file:/tmp/FlakeyServer/";
        
        URL url;
        if ( id.startsWith("/") ) {
            url= new URL( surl + "data"+id );
        } else {
            url= new URL( surl + "data/"+id);
        }
        
        File f= new File( "/tmp/FlakeyServer/" );
        if ( id.startsWith("/") ) {
            f= new File( f, "data"+id );
        } else {
            f= new File( f, "data/"+id);
        }
        
        
        response.addHeader("Last-Modified", new Date(f.lastModified()).toGMTString() );
        response.addHeader("Content-Length", String.valueOf( f.length() ) );
        
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
