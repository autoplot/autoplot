/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import edu.uiowa.physics.pw.das.util.DasPNGConstants;
import edu.uiowa.physics.pw.das.util.DasPNGEncoder;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;

/**
 *
 * @author jbf
 */
public class SimpleServlet extends HttpServlet {
   
    /** 
    * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
    * @param request servlet request
    * @param response servlet response
    */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        
        response.setContentType("image/png");
        
        try {
            OutputStream out = response.getOutputStream();
            
            String arg0= request.getParameter("url");
            String vap= request.getParameter("vap");
            
            System.setProperty("java.awt.headless","true");
            
            ApplicationModel appmodel= new ApplicationModel();
            //appmodel.setAutolayout(true);
            //appmodel.canvas.setFont( Font.decode("sans-6" ) );
            appmodel.canvas.setSize( 700, 400 );
            appmodel.canvas.revalidate();
            appmodel.canvas.setPrintingTag("");
            
            if ( vap!=null ) appmodel.doOpen( new File(vap) );
            
            DataSource dsource= DataSetURL.getDataSource( arg0 );
            
            appmodel.setDataSource(dsource);
            
            Image image= appmodel.canvas.getImage( 700, 400 );
            
            DasPNGEncoder encoder = new DasPNGEncoder();
            encoder.addText(DasPNGConstants.KEYWORD_CREATION_TIME, new Date().toString());
            try {
                encoder.write((BufferedImage)image, out);
            } catch (IOException ioe) {} finally {
                try { out.close(); } catch (IOException ioe) { throw new RuntimeException(ioe); }
            }
            
            out.close();
            
        } catch ( Exception e ) {
            e.printStackTrace();
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
