/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.python.util.PythonInterpreter;
import org.virbo.autoplot.scriptconsole.LoggingOutputStream;

/**
 *
 * @author jbf
 */
public class ScriptServlet extends HttpServlet {
   
    /** 
    * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
    * @param request servlet request
    * @param response servlet response
    */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        try {

            ApplicationModel appmodel = new ApplicationModel();
            appmodel.addDasPeersToAppAndWait();

            String script= request.getParameter("script");
            
            if ( script==null ) {
                script= "setCanvasSize( 400, 400 )\n"+
                    "setDataSourceURL( 'http://www.sarahandjeremy.net/jeremy/1wire/data/2008/0B000800408DD710.20080117.d2s' )\n"+
                    "setTitle( 'Garage 20080117' )\n" +
                    "response.setContentType('image/png')\n" + 
                    "out = response.getOutputStream()\n" + 
                    "writeToPng( out )\n";
            }
            
            // do minimal taint checking.
            String[] ss= script.split("\n");
             
            for ( int i=0; i<ss.length; i++ ) {
                if ( ss[i].contains("import") ) throw new IllegalArgumentException("imports not allowed for security");
                if ( ss[i].contains("eval") ) throw new IllegalArgumentException("eval not allowed for security");
            }

            PythonInterpreter interp = JythonUtil.createInterpreter( true, true );
            interp.setOut( new LoggingOutputStream( Logger.getLogger("virbo.scriptservlet"), Level.INFO ) );
            
            interp.set( "response", response );
            
            ScriptContext._setOutputStream( new LoggingOutputStream( Logger.getLogger("virbo.scriptservlet"), Level.INFO ) ); 
            
            throw new IllegalArgumentException("server-side scripting disabled");
            //interp.exec(script);
        } catch ( Exception ex ) {
            ex.printStackTrace();
        } finally { 
            
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
