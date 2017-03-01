
package org.autoplot.hapiserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Info servlet returns information about parameters.
 * @author jbf
 */
public class InfoServlet extends HttpServlet {
    private static final Logger logger= Logger.getLogger("hapi");    
    protected static JSONObject getInfo( String id ) throws JSONException, IllegalArgumentException, IOException {
        JSONObject jo= new JSONObject();
        jo.put("HAPI",Util.hapiVersion());
        jo.put("createdAt",String.format("%tFT%<tRZ",Calendar.getInstance(TimeZone.getTimeZone("Z"))));
        
        if ( !HapiServerSupport.getCatalogIds().contains(id) ){
            throw new IllegalArgumentException("invalid parameter id: \""+id+"\" is not known.");
        }
        
        JSONArray parameters= new JSONArray();
        JSONObject parameter;
        parameter= new JSONObject();
        parameter.put( "name", "Time" );
        parameter.put( "type", "isotime" );
        parameter.put( "length", 24 );
        parameters.put( 0, parameter );

        File infoFileHome= new File( Util.getHapiHome(), "info" );
        File infoFile= new File( infoFileHome, id+".json" );
        StringBuilder builder= new StringBuilder();
        try ( BufferedReader in= new BufferedReader( new FileReader( infoFile ) ) ) {
            String line= in.readLine();
            while ( line!=null ) {
                builder.append(line);
                line= in.readLine();
            }
        }
        JSONObject o= new JSONObject(builder.toString());
        JSONArray parametersRead= o.getJSONArray("parameters");
        for ( int i=1; i<parametersRead.length(); i++ ) {
            parameters.put( i, parametersRead.getJSONObject(i) );
        }
        // support local features like "now-P3D", which are not hapi features.
        if ( o.has("startDate") && o.has("stopDate") ) { 
            String startDate= o.getString("startDate");
            String stopDate= o.getString("stopDate");
            DatumRange tr= DatumRangeUtil.parseTimeRangeValid( startDate+"/"+stopDate );
            jo.put( "startDate", tr.min().toString() );
            jo.put( "stopDate", tr.max().toString() );
        } else {
            if ( o.has("startDate") ) {
                jo.put( "startDate", o.get("startDate") );
            } else {
                logger.warning("non-conformant server needs to have startDate");
            }
            if ( o.has("stopDate") ) {
                jo.put( "stopDate", o.get("stopDate") );
            } else {
                logger.warning("non-conformant server needs to have stopDate");
            }
        }      
        
        jo.put("parameters",parameters);
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
        
        String id= request.getParameter("id");
        
        if ( id==null ) throw new ServletException("required parameter 'id' is missing from request");
        
        response.setContentType("application/json;charset=UTF-8");        
        try (PrintWriter out = response.getWriter()) {
           
           JSONObject jo= getInfo( id );
           String s= jo.toString(4);
           out.write(s);
            
        } catch ( JSONException | IllegalArgumentException ex ) {
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
