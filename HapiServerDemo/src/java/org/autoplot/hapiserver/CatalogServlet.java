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
import org.das2.datum.TimeParser;
import org.das2.datum.TimeUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Servlet for the dataset catalog.  
 * @see https://github.com/hapi-server/data-specification/blob/master/hapi-2.0.0/HAPI-data-access-spec-2.0.0.md#catalog
 * @author jbf
 */
public class CatalogServlet extends HttpServlet {

    private static final Logger logger= Logger.getLogger("hapi");    
    
    private static final String deployedAt= TimeParser.create( TimeParser.TIMEFORMAT_Z ).format( TimeUtil.now() );
    /**
     * return the JSONObject for the catalog.
     * @return
     * @throws JSONException 
     */
    public static JSONObject getCatalog() throws JSONException, IOException {
        JSONObject jo= new JSONObject();
        jo.put("HAPI",Util.hapiVersion());
        jo.put("x_deployedAt", deployedAt );
        JSONArray catalog= HapiServerSupport.getCatalog();
        jo.put("catalog",catalog);
                
        JSONObject status= new JSONObject();
        status.put( "code", 1200 );
        status.put( "message", "OK request successful");
        jo.put( "status", status );
        
        return jo;
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
                
        response.setHeader("Access-Control-Allow-Origin", "* " );
        response.setHeader("Access-Control-Allow-Methods","GET" );
        response.setHeader("Access-Control-Allow-Headers","Content-Type" );
        
        File catalogFile= new File( Util.getHapiHome(), "catalog.json" );
        if ( catalogFile.exists() ) {
            logger.log(Level.FINE, "using cached catalog file {0}", catalogFile);
            Util.transfer( new FileInputStream(catalogFile), response.getOutputStream() );
            return;
        }
        try (PrintWriter out = response.getWriter()) {
            JSONObject jo= getCatalog();
            out.write( jo.toString(4) );
            
        } catch ( JSONException ex ) {
            throw new ServletException(ex);
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
