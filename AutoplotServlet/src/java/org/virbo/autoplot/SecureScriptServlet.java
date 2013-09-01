/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.python.util.PythonInterpreter;

/**
 * This provides a generic method for adding a function to the server via Jython scripts.  Introduced
 * to support wildcard-de-globing, this could be used for a number of different ways.
 * 
 * Unlike the ScriptServlet, this requires a reference to a script that already exists on the server, instead of 
 * running possibly malicious updated code.
 *
 * http://localhost:8084/AutoplotServlet/ScriptServlet2?resourceURI=http://autoplot.org/data/versioning/data_$Y_$m_$d_v$v.qds&timerange=2010-03
 *
 * @author jbf
 */
public class SecureScriptServlet extends HttpServlet {
   
    private static final Logger logger= Logger.getLogger("autoplot.servlet");
    
    private static final String SCRIPT_FILE_REGEX = "[a-zA-Z_][a-zA-Z0-9_]*\\.jy";
    
    private static String maybeQuoteString(String sval) {
        boolean isNumber= false;
        try {
            Double.parseDouble(sval); 
        } catch ( NumberFormatException ex ) {
            isNumber= false;
        }

        if ( sval.length()>0 && !isNumber && !sval.equals("True") && !sval.equals("False") ) {
            if ( !( sval.startsWith("'") && sval.endsWith("'") ) ) {
                sval= String.format( "'%s'", sval );
            }
        }
        return sval;

    }

    /**
     * there are a couple of places were we set the Python interpreter params.  Start to introduce a standard code for this.
     * @param parms
     * @param interp
     */
    private static void setParams( Map<String,String> parms, PythonInterpreter interp, boolean reset ) {
        if ( reset ) interp.exec("params=dict()");
        for ( Entry<String,String> e: parms.entrySet() ) {
            String s= e.getKey();
            if (!s.equals("arg_0") && !s.equals("script") ) {
                String sval= e.getValue();

                sval= maybeQuoteString( sval );
                interp.exec("params['" + s + "']=" + sval);
            }
        }
    }

    /** 
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        response.setContentType("text/plain;charset=UTF-8");
        response.setHeader( "X-Served-By", java.net.InetAddress.getLocalHost().getCanonicalHostName() );

        long t0= System.currentTimeMillis();
        
        Pattern scriptFilePattern= Pattern.compile(SCRIPT_FILE_REGEX);
                
        PrintWriter out = response.getWriter();
        try {

            String scriptFile= request.getParameter("scriptFile");
            if ( scriptFile==null ) {
                throw new ServletException("scriptFile parameter not specified");
            }

            // make sure these symbols are imported, otherwise there will be problems with imports.py.
            Object o;
            o= new org.das2.dataset.AverageTableRebinner();
            o= org.das2.graph.SymColor.black;
            // end, make sure these symbols are imported, otherwise there will be problems with imports.py

            // limit security by ensuring the scriptFile parameter doesn't contain slashes, which might allow it to access
            // other parts of the server.
            if ( !scriptFilePattern.matcher(scriptFile).matches() ) {
                throw new ServletException("scriptFile must match "+SCRIPT_FILE_REGEX );
            }

            String file= getServletContext().getRealPath( scriptFile );

            PythonInterpreter interp = JythonUtil.createInterpreter( true, true );

            interp.setOut( out );

            interp.set( "response", response );

            Map<String,String> m= new HashMap();
            request.getParameterMap();
            Enumeration e= request.getParameterNames(); // request.getParameterMap() returns string arrays!
            while ( e.hasMoreElements() ) {
                String k= (String)e.nextElement();
                if ( !k.equals("scriptFile") ) {
                    String v= request.getParameter(k);
                    if ( v.equals("on") ) v= "T"; // kludge for checkbox
                    m.put( k,v );
                }
            }
            setParams( m, interp, true );

            interp.execfile( new FileInputStream(file) );
            
        } catch ( RuntimeException ex ) {
            ex.printStackTrace(out);
            ex.printStackTrace();
            throw ex;
        } catch ( ServletException ex ) {
            ex.printStackTrace(out);
            ex.printStackTrace();
            throw ex;
        } catch ( IOException ex ) {
            ex.printStackTrace(out);
            ex.printStackTrace();
            throw ex;
        } finally { 
            out.close();
            logger.log(Level.FINE, "time to process SecureScriptServlet: {0}", ( System.currentTimeMillis()-t0 ));
        }
    } 

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /** 
     * Handles the HTTP <code>GET</code> method.
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
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

}
