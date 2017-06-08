
package org.autoplot.hapiserver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Generate the HAPI server capabilities
 * @author jbf
 */
public class CapabilitiesServlet extends HttpServlet {
    
    private static final Logger logger= Logger.getLogger("hapi");    
    
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
        response.setContentType("application/json;charset=UTF-8");
        File capFile= new File( Util.getHapiHome(), "capabilities.json" );
        if ( capFile.exists() ) {
            logger.log(Level.FINE, "using cached capabilities file {0}", capFile);
            Util.transfer( new FileInputStream(capFile), response.getOutputStream() );
            return;
        }
        try (PrintWriter out = response.getWriter()) {
            JSONObject jo= new JSONObject();
            jo.put("HAPI",Util.hapiVersion());
            JSONArray outputFormats= new JSONArray();
            outputFormats.put( 0, "csv" );
            outputFormats.put( 1, "binary" );
            jo.put( "outputFormats", outputFormats );
            out.write( jo.toString(4) );
            JSONObject status= new JSONObject();
            status.put( "code", 1200 );
            status.put( "message", "OK request successful");
            jo.put( "status", status );
            
        } catch ( JSONException ex ) {
            throw new ServletException(ex);
        }
    }

}
