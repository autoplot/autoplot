
package org.autoplot.servlet;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.autoplot.ApplicationModel;
import org.autoplot.AutoplotUtil;
import org.autoplot.dom.Application;

/**
 * servlet for reporting version information.  This should be 
 * updated manually before adding and releasing new features.
 * @author faden@cottagesystems.com
 */
public class ServletInfo extends HttpServlet {

    private static final long birthMilli= System.currentTimeMillis();
    public static final String version = "v20191220.1050";
    
    public static long getAgeMillis() {
        return System.currentTimeMillis() - birthMilli;
    }
            
    /**
     * return the duration in a easily-human-consumable form.
     * @param dt the duration in milliseconds.
     * @return a duration like "2.6 hours"
     */
    public static String getDurationForHumans( long dt ) {
        if ( dt<2*1000 ) {
            return dt+" milliseconds";
        } else if ( dt<2*60000 ) {
            return String.format( Locale.US, "%.1f",dt/1000.)+" seconds";
        } else if ( dt<2*3600000 ) {
            return String.format( Locale.US, "%.1f",dt/60000.)+" minutes";
        } else if ( dt<2*86400000 ) {
            return String.format( Locale.US, "%.1f",dt/3600000.)+" hours";
        } else {
            return String.format( Locale.US, "%.1f",dt/86400000.)+" days";
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
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            /* TODO output your page here. You may use following sample code. */
            out.println("<!DOCTYPE html>");
            out.println("<html>");
            out.println("<head>");
            out.println("<title>Autoplot Servlet ServletInfo</title>");            
            out.println("</head>");
            out.println("<body>");
            out.println("<h1>Servlet ServletInfo at " + request.getContextPath() + "</h1>");
            
            boolean isHostPrivileged= false;
            try {
                SecurityUtil.checkAllowed(request);
                isHostPrivileged= true;
            } catch ( SecurityException ex ) {
            }
            
            if ( !isHostPrivileged ) {
                out.println("<h2>You do not have access to see all information.\n</h2>");
            }            
            
            if ( isHostPrivileged ) {
                String st= AutoplotUtil.getAboutAutoplotHtml();
                out.println(st);
            } else {
                
            }

            out.println("<h2>Options</h2>");
            ApplicationModel appmodel = new ApplicationModel();
            Application dom = appmodel.getDocumentModel();
            out.println("displayLogLevel: "+dom.getOptions().getDisplayLogLevel());
            out.println("<br>printingLogLevel: "+dom.getOptions().getPrintingLogLevel());
            out.println("<br>printingTag: "+dom.getOptions().getPrintingTag());
            
            out.println("<h2>Servlet Info</h2>");
            out.println("servlet version: "+version+"<br>");
            out.println("servlet uptime: "+getDurationForHumans(getAgeMillis())+"<br>");
            if ( isHostPrivileged ) {
                out.println("<br>user.name: "+ System.getProperty("user.name") + "\n"); // TODO: security concerns
                out.println("<br>java.home: "+ System.getProperty("java.home") + "\n"); // TODO: security concerns
                out.println("<br>user.home: "+ System.getProperty("user.home") + "\n"); // TODO: security concerns

                out.println("<p>PWD: "+ ( new File(".").getAbsolutePath() ) +"\n" );
                out.println("<br>Servlet Home: "+ServletUtil.getServletHome() + "\n"); // TODO: security concerns
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
                
            } 
            out.println("Contact Info: "+ServletUtil.getServletContact()+"</sm>");
            out.println("</p></body>");
            out.println("</html>");
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
