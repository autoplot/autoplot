
package org.autoplot.hapiserver;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.das2.util.FileUtil;

/**
 * Generate the HAPI server capabilities
 * @author jbf
 */
public class AboutServlet extends HttpServlet {
    
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
                
        response.setHeader("Access-Control-Allow-Origin", "* " );
        response.setHeader("Access-Control-Allow-Methods","GET" );
        response.setHeader("Access-Control-Allow-Headers","Content-Type" );
        
        File aboutFile= new File( Util.getHapiHome(), "about.json" );
        if ( aboutFile.exists() ) {
            String s= FileUtil.readFileToString(aboutFile);
            if ( !Util.validateJSON(s) ) {
                throw new ServletException("Internal error, JSON file for capabilities does not parse.");
            }
            logger.log(Level.FINE, "using cached about file {0}", aboutFile);
            Util.transfer( new ByteArrayInputStream(s.getBytes("UTF-8")), response.getOutputStream() );
        } else {
            synchronized ( this ) {
                if ( !aboutFile.exists() ) { // double-check in synchronized block
                    InputStream in= AboutServlet.class.getResourceAsStream("/templates/about.json");
                    File tmpFile= File.createTempFile( "aboutServlet", ".json" );
                    Util.transfer( in, new FileOutputStream(tmpFile) );
                    tmpFile.renameTo(aboutFile);
                    logger.log(Level.FINE, "wrote cached about file {0}", aboutFile);
                }
                logger.log(Level.FINE, "using cached about file {0}", aboutFile);
                Util.transfer( new FileInputStream(aboutFile), response.getOutputStream() );
            }
        }
    }

}
