/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.hapiserver;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Info servlet returns information about parameters.
 * @author jbf
 */
public class InfoServlet extends HttpServlet {

    protected static JSONObject getInfo( String id ) throws JSONException, IllegalArgumentException {
        JSONObject jo= new JSONObject();
        jo.put("HAPI","1.0");
        jo.put("Created at",String.format("%tFT%<tRZ",Calendar.getInstance(TimeZone.getTimeZone("Z"))));
        
        boolean haveEntry= false;
        JSONObject catalog= CatalogServlet.getCatalog();
        JSONArray catalogEntries= catalog.getJSONArray("catalog");
        for ( int i=0; i<catalogEntries.length(); i++ ) {
            if ( catalogEntries.getJSONObject(i).getString("id").equals(id) ) haveEntry=true;
        }
        if ( !haveEntry ) {
            throw new IllegalArgumentException("invalid parameter id: \""+id+"\" is not known.");
        }
        JSONArray parameters= new JSONArray();
        JSONObject parameter;
        parameter= new JSONObject();
        parameter.put( "name", "Time" );
        parameter.put( "type", "isotime" );
        parameter.put( "length", 24 );
        parameters.put( 0, parameter );

        if ( id.equals("Iowa City Conditions") ) {
            parameter= new JSONObject();
            parameter.put( "name", "Temperature" );
            parameter.put( "type", "float" );
            parameter.put( "units", "deg F" );
            parameter.put( "fill", "-1e31" );
            parameter.put( "description", "Iowa City Air Temperature" );
            parameters.put( 1, parameter );
            parameter= new JSONObject();
            parameter.put( "name", "Humidity" );
            parameter.put( "type", "float" );
            parameter.put( "units", "percent" );
            parameter.put( "fill", "-1e31" );
            parameter.put( "description", "Iowa City Relative Humidity" );
            parameters.put( 2, parameter );
            parameter= new JSONObject();
            parameter.put( "name", "Pressure" );
            parameter.put( "type", "float" );
            parameter.put( "fill", "-1e31" );
            parameter.put( "description", "Iowa City Barometric Pressure" );
            parameters.put( 3, parameter );
                        
        } else {
            parameter= new JSONObject();
            parameter.put( "name", "Temperature" );
            parameter.put( "type", "float" );
            parameter.put( "units", "deg F" );
            parameter.put( "fill", "-1e31" );
            parameter.put( "description", "temperature at sensor " + id );
            parameters.put( 1, parameter );
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
        PrintWriter out = response.getWriter();
        try {
           
           JSONObject jo= getInfo( id );
           String s= jo.toString(4);
           out.write(s);
            
        } catch ( JSONException ex ) {
            throw new ServletException(ex);
        } catch ( IllegalArgumentException ex ) {
            throw new ServletException(ex);
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
