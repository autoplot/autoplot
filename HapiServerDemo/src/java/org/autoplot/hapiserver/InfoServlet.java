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
import java.util.TimeZone;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author jbf
 */
public class InfoServlet extends HttpServlet {

    protected static void doHeader(PrintWriter out, String id, String parameterNames ) throws JSONException {
        JSONObject jo= new JSONObject();
        jo.put("HAPI","1.0");
        jo.put("Created at",String.format("%tFT%<tRZ",Calendar.getInstance(TimeZone.getTimeZone("Z"))));
        JSONArray parameters= new JSONArray();
        JSONObject parameter;
        parameter= new JSONObject();
        parameter.put( "name", "Time" );
        parameter.put( "type", "isotime" );
        parameter.put( "length", 24 );
        parameters.put( 0, parameter );

        parameter= new JSONObject();
        parameter.put( "name", "Temperature" );
        parameter.put( "type", "float" );
        parameter.put( "units", "deg F" );
        parameter.put( "fill", "1e31" );
        parameter.put( "description", "temperature at sensor" + id );
        parameters.put( 1, parameter );

        jo.put("parameters",parameters);
        jo.write(out);

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
            JSONObject jo= new JSONObject();
            jo.put("HAPI","1.0");
            jo.put("Created at",String.format("%tFT%<tRZ",Calendar.getInstance(TimeZone.getTimeZone("Z"))));
            JSONArray parameters= new JSONArray();
            JSONObject parameter;
            parameter= new JSONObject();
            parameter.put( "name", "Time" );
            parameter.put( "type", "ISOTIME" );
            parameter.put( "length", 24 );
            parameters.put( 0, parameter );
            
            parameter= new JSONObject();
            parameter.put( "name", "Temperature" );
            parameter.put( "type", "float" );
            parameter.put( "units", "deg F" );
            parameter.put( "description", "temperature at sensor" + id );
            parameters.put( 1, parameter );
            
            jo.put("parameters",parameters);
            jo.write(out);
            
        } catch ( JSONException ex ) {
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
