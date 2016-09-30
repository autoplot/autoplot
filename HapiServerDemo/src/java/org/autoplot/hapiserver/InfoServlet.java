
package org.autoplot.hapiserver;

import java.io.IOException;
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
 * Info servlet returns information about parameters.
 * @author jbf
 */
public class InfoServlet extends HttpServlet {

    protected static JSONObject getInfo( String id ) throws JSONException, IllegalArgumentException {
        JSONObject jo= new JSONObject();
        jo.put("HAPI","1.0");
        jo.put("createdAt",String.format("%tFT%<tRZ",Calendar.getInstance(TimeZone.getTimeZone("Z"))));
        
        if ( id.equals("Iowa City Conditions") ) {
            jo.put( "firstDate", "2012-05-25T00:00Z" );
            jo.put( "lastDate", String.format("%tFT%<tRZ", Calendar.getInstance(TimeZone.getTimeZone("Z"))) );
        } else if ( id.equals("0B000800408DD710") ) {
            jo.put( "firstDate", "2012-01-09T00:00Z" );
            jo.put( "lastDate", String.format("%tFT%<tRZ", Calendar.getInstance(TimeZone.getTimeZone("Z"))) );            
        } else if ( id.equals("8500080044259C10") ) {
            jo.put( "firstDate", "2012-01-09T00:00Z" );
            jo.put( "lastDate", String.format("%tFT%<tRZ", Calendar.getInstance(TimeZone.getTimeZone("Z"))) );            
        } else if ( id.equals("610008002FE00410") ) {
            jo.put( "firstDate", "2012-01-09T00:00Z" );
            jo.put( "lastDate", String.format("%tFT%<tRZ", Calendar.getInstance(TimeZone.getTimeZone("Z"))) );            
        } else if ( id.equals("AC00080040250510") ) {
            jo.put( "firstDate", "2012-01-09T00:00Z" );
            jo.put( "lastDate", String.format("%tFT%<tRZ", Calendar.getInstance(TimeZone.getTimeZone("Z"))) );            
        }
        
        if ( !HapiServerSupport.getCatalog().contains(id) ){
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
            parameter.put( "description", "Air Temperature" );
            parameters.put( 1, parameter );
            parameter= new JSONObject();
            parameter.put( "name", "DewPoint" );
            parameter.put( "type", "float" );
            parameter.put( "units", "deg F" );
            parameter.put( "fill", "-1e31" );
            parameter.put( "description", "Dew Point" );
            parameters.put( 2, parameter );
            parameter= new JSONObject();
            parameter.put( "name", "Humidity" );
            parameter.put( "type", "float" );
            parameter.put( "units", "percent" );
            parameter.put( "fill", "-1e31" );
            parameter.put( "description", "Relative Humidity" );
            parameters.put( 3, parameter );
            parameter= new JSONObject();
            parameter.put( "name", "WindChill" );
            parameter.put( "type", "float" );
            parameter.put( "units", "deg F" );
            parameter.put( "fill", "-1e31" );
            parameter.put( "description", "Wind Chill" );
            parameters.put( 4, parameter );
            parameter= new JSONObject();
            parameter.put( "name", "HeatIndex" );
            parameter.put( "type", "float" );
            parameter.put( "units", "deg F" );
            parameter.put( "fill", "-1e31" );
            parameter.put( "description", "Heat Index" );
            parameters.put( 5, parameter );
            parameter= new JSONObject();
            parameter.put( "name", "Altimeter" );
            parameter.put( "type", "float" );
            parameter.put( "fill", "-1e31" );
            parameter.put( "description", "Altimeter" );
            parameters.put( 6, parameter );
            parameter= new JSONObject();
            parameter.put( "name", "Pressure" );
            parameter.put( "type", "float" );
            parameter.put( "fill", "-1e31" );
            parameter.put( "description", "Barometric Pressure" );
            parameters.put( 7, parameter );
            parameter= new JSONObject();
            parameter.put( "name", "Precip" );
            parameter.put( "type", "float" );
            parameter.put( "fill", "-1e31" );
            parameter.put( "description", "Precipitation" );
            parameters.put( 8, parameter );
                 
        } else if ( id.equals("Spectrum") ) {
            parameter= new JSONObject();
            parameter.put( "name", "Spectra" );
            parameter.put( "type", "float" );
            parameter.put( "units", "V^2 m^-2 Hz^-1" );
            parameter.put( "fill", "-1e31" );
            JSONObject bins= new JSONObject();
            bins.put( "units", "eV" );
            double[] ens= new double[] { 10000.0,10500.0,11000.0,11500.0,12100.0,12700.0,13300.0,14000.0,14700.0,15400.0,16200.0,17000.0,17800.0,18700.0,19600.0,20500.0,21500.0,22600.0,23700.0,24900.0,26100.0,27400.0,28700.0,30100.0,31600.0,33200.0,34800.0,36500.0,38300.0,40200.0,42200.0,44200.0,46400.0,48700.0,51100.0,53600.0,56200.0,59000.0,61900.0,64900.0,68100.0,71500.0,75000.0,78700.0,82500.0,86600.0,90900.0,95300.0,1.00000E5,1.05000E5,1.10000E5,1.15000E5,1.21000E5,1.27000E5,1.33000E5,1.40000E5,1.47000E5,1.54000E5,1.62000E5,1.69000E5,1.78000E5,1.87000E5,1.96000E5,2.05000E5,2.15000E5,2.26000E5,2.37000E5,2.49000E5,2.61000E5,2.74000E5,2.87000E5,3.01000E5,3.16000E5,3.32000E5,3.48000E5,3.65000E5,3.83000E5,4.02000E5,4.22000E5,4.42000E5,4.64000E5,4.87000E5 };
            JSONArray values= new JSONArray();
            for ( int i=0; i<ens.length; i++ ) {
                JSONObject en= new JSONObject();
                en.put("center",ens[i]);
                values.put( i,en );
            }
            bins.put( "values", values );
            bins.put( "name", "frequencies" );
            bins.put( "units", "Hz" );
            parameter.put( "bins", bins );
            parameter.put( "size", new int[] { ens.length } );
            parameter.put( "description", "spectrogram example that is not for publication." );
            parameters.put( 1, parameter );
        
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
