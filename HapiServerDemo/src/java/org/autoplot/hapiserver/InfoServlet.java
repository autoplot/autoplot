
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
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;

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
            jo.put( "startDate", "2012-05-25T00:00Z" );
            jo.put( "stopDate", String.format("%tFT%<tRZ", Calendar.getInstance(TimeZone.getTimeZone("Z"))) );
        } else if ( id.equals("Iowa City Forecast") ) {
            jo.put( "startDate", "2012-05-25T00:00Z" );
            jo.put( "stopDate", String.format("%tFT%<tRZ", Calendar.getInstance(TimeZone.getTimeZone("Z"))) );
        } else if ( id.equals("0B000800408DD710") ) {
            jo.put( "startDate", "2012-01-09T00:00Z" );
            jo.put( "stopDate", String.format("%tFT%<tRZ", Calendar.getInstance(TimeZone.getTimeZone("Z"))) );            
        } else if ( id.equals("0B000800408DD710.nostream") ) {
            jo.put( "startDate", "2012-01-09T00:00Z" );
            jo.put( "stopDate", String.format("%tFT%<tRZ", Calendar.getInstance(TimeZone.getTimeZone("Z"))) );            
        } else if ( id.equals("8500080044259C10") ) {
            jo.put( "startDate", "2012-01-09T00:00Z" );
            jo.put( "stopDate", String.format("%tFT%<tRZ", Calendar.getInstance(TimeZone.getTimeZone("Z"))) );            
        } else if ( id.equals("610008002FE00410") ) {
            jo.put( "startDate", "2012-01-09T00:00Z" );
            jo.put( "stopDate", String.format("%tFT%<tRZ", Calendar.getInstance(TimeZone.getTimeZone("Z"))) );            
            jo.put( "label", "attic" );
        } else if ( id.equals("AC00080040250510") ) {
            jo.put( "startDate", "2012-01-09T00:00Z" );
            jo.put( "stopDate", String.format("%tFT%<tRZ", Calendar.getInstance(TimeZone.getTimeZone("Z"))) );            
        } else if ( id.equals("Spectrum") ) {
            jo.put( "startDate", "2012-08-30T00:00Z" );
            jo.put( "sampleStartDate", "2016-01-01T00:00Z" );
            jo.put( "sampleEndDate", "2016-01-02T00:00Z" );            
        } else if ( id.equals("PowerWheel") ) {
            jo.put( "startDate",  "2016-07-28T00:00Z"  );
            jo.put( "sampleStartDate", "2016-07-28T00:00Z" );
            jo.put( "sampleEndDate", "2016-07-29T00:00Z" );
            jo.put( "about","http://jfaden.net/HapiServerDemo/about/about.html#wheelThingy");
            jo.put( "DOI", "10.1007/s12145-010-0053-4" );
        } else if ( id.equals("PowerOnesDigitSegments") ) {
            jo.put( "startDate",  "2016-07-28T00:00Z"  );
            jo.put( "sampleStartDate", "2016-07-28T00:00Z" );
            jo.put( "sampleEndDate", "2016-07-29T00:00Z" );
        }
        
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

        if ( id.equals("Iowa City Conditions") ) {
            parameter= new JSONObject();
            parameter.put( "name", "Temperature" );
            parameter.put( "type", "double" );
            parameter.put( "units", "deg F" );
            parameter.put( "fill", "-1e31" );
            parameter.put( "description", "Air Temperature" );
			parameters.put( 1, parameter );
            parameter= new JSONObject();
            parameter.put( "name", "WindSpeed" );
            parameter.put( "type", "double" );
            parameter.put( "units", "mph" );
            parameter.put( "fill", "-1e31" );
            parameter.put( "description", "Wind Speed" );
            parameters.put( 2, parameter );
            parameter= new JSONObject();
            parameter.put( "name", "DewPoint" );
            parameter.put( "type", "double" );
            parameter.put( "units", "deg F" );
            parameter.put( "fill", "-1e31" );
            parameter.put( "description", "Dew Point" );
            parameters.put( 3, parameter );
            parameter= new JSONObject();
            parameter.put( "name", "Humidity" );
            parameter.put( "type", "double" );
            parameter.put( "units", "percent" );
            parameter.put( "fill", "-1e31" );
            parameter.put( "description", "Relative Humidity" );
            parameters.put( 4, parameter );
            parameter= new JSONObject();
            parameter.put( "name", "WindChill" );
            parameter.put( "type", "double" );
            parameter.put( "units", "deg F" );
            parameter.put( "fill", "-1e31" );
            parameter.put( "description", "Wind Chill" );
            parameters.put( 5, parameter );
            parameter= new JSONObject();
            parameter.put( "name", "HeatIndex" );
            parameter.put( "type", "double" );
            parameter.put( "units", "deg F" );
            parameter.put( "fill", "-1e31" );
            parameter.put( "description", "Heat Index" );
            parameters.put( 6, parameter );
            parameter= new JSONObject();
            parameter.put( "name", "Altimeter" );
            parameter.put( "type", "double" );
            parameter.put( "fill", "-1e31" );
            parameter.put( "description", "Altimeter" );
            parameters.put( 7, parameter );
            parameter= new JSONObject();
            parameter.put( "name", "Pressure" );
            parameter.put( "type", "double" );
            parameter.put( "fill", "-1e31" );
            parameter.put( "description", "Barometric Pressure" );
            parameters.put( 8, parameter );
            parameter= new JSONObject();
            parameter.put( "name", "Precip" );
            parameter.put( "type", "double" );
            parameter.put( "fill", "-1e31" );
            parameter.put( "description", "Precipitation" );
            parameters.put( 9, parameter );
        } else if ( id.equals("Iowa City Forecast") ) {
            parameter= new JSONObject();
            parameter.put( "name", "Temperature" );
            parameter.put( "type", "double" );
            parameter.put( "units", "deg F" );
            parameter.put( "fill", "-1e31" );
            parameter.put( "description", "Air Temperature" );
            parameters.put( 1, parameter );
                 
            parameter= new JSONObject();
            parameter.put( "name", "DewPoint" );
            parameter.put( "type", "double" );
            parameter.put( "units", "deg F" );
            parameter.put( "fill", "-1e31" );
            parameter.put( "description", "Dew Point" );
            parameters.put( 2, parameter );

            parameter= new JSONObject();
            parameter.put( "name", "PrecipProbabily" );
            parameter.put( "type", "double" );
            parameter.put( "units", "%" );
            parameter.put( "fill", "-1e31" );
            parameter.put( "description", "Probability of Precipitation" );
            parameters.put( 3, parameter );

            parameter= new JSONObject();
            parameter.put( "name", "WindSpeed" );
            parameter.put( "type", "double" );
            parameter.put( "units", "mph" );
            parameter.put( "fill", "-1e31" );
            parameter.put( "description", "Wind Speed" );
            parameters.put( 4, parameter );

            parameter= new JSONObject();
            parameter.put( "name", "WindDirection" );
            parameter.put( "type", "double" );
            parameter.put( "units", "degrees" );
            parameter.put( "fill", "-1e31" );
            parameter.put( "description", "Wind Direction" );
            parameters.put( 5, parameter );

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
        } else if ( id.equals("PowerWheel") ) {
            parameter= new JSONObject();
            parameter.put( "name", "PowerWheel" );
            parameter.put( "type", "float" );
            parameter.put( "units", "greyscale" );
            JSONObject bins= new JSONObject();
            bins.put( "units", "" );
            double[] ens= new double[] { 241.0, 241.8, 242.6, 243.4, 244.2, 245.0, 245.8, 246.6, 247.4, 248.2, 249.0, 249.8, 250.6, 251.4, 252.2, 253.0, 253.8, 254.6, 255.4, 256.2, 257.0 };
            JSONArray values= new JSONArray();
            for ( int i=0; i<ens.length; i++ ) {
                JSONObject en= new JSONObject();
                en.put("center",ens[i]);
                values.put( i,en );
            }
            bins.put( "values", values );
            bins.put( "name", "horizontal position" );
            bins.put( "units", "pixels" );
            parameter.put( "bins", bins );
            parameter.put( "size", new int[] { ens.length } );
            parameter.put( "description", "The little wheel thingy that spins around." );
            parameters.put( 1, parameter );
        } else if ( id.equals("SpectrogramRank2") ) {
            parameter= new JSONObject();
            parameter.put( "name", "SpectrogramRank2" );
            parameter.put( "type", "float" );
            parameter.put( "units", "greyscale" );
            parameter.put( "size", new int[] { 7,3 } );   
            JSONArray binsArray= new JSONArray();
            JSONObject bins= new JSONObject();
            double[] ens1= new double[] { .1,.2,.3,.4,.5,.6,.7 };
            JSONArray values= new JSONArray();
            for ( int i=0; i<ens1.length; i++ ) {
                JSONObject en= new JSONObject();
                en.put("center",ens1[i]);
                values.put( i,en );
            }
            bins.put( "values", values );
            bins.put( "name", "energy" );
            bins.put( "units", "sampleNumber" );
            binsArray.put(0,bins);
            bins= new JSONObject();
            double[] ens2= new double[] { 10,20,30 };
            values= new JSONArray();
            for ( int i=0; i<ens2.length; i++ ) {
                JSONObject en= new JSONObject();
                en.put("center",ens2[i]);
                values.put( i,en );
            }
            bins.put( "values", values );
            bins.put( "name", "cell" );
            bins.put( "units", "cellNumber" );
            binsArray.put(1,bins);
            parameter.put( "bins", binsArray );
            parameter.put( "description", "Example of rank 2 data." );
            parameters.put( 1, parameter );

        } else if ( id.equals("PowerOnesDigitSegments") ) {
            parameter= new JSONObject();
            parameter.put( "name", "PowerOnesDigitSegments" );
            parameter.put( "type", "float" );
            parameter.put( "units", "greyscale" );
            JSONObject bins= new JSONObject();
            bins.put( "units", "" );
            QDataSet ens= Ops.findgen(27);
            JSONArray values= new JSONArray();
            for ( int i=0; i<ens.length(); i++ ) {
                JSONObject en= new JSONObject();
                en.put("center",ens.value(i));
                values.put( i,en );
            }
            bins.put( "values", values );
            bins.put( "name", "segmentNumber" );
            bins.put( "units", "" ); 
            parameter.put( "bins", bins );
            parameter.put( "size", new int[] { ens.length() } );
            parameter.put( "description", "Each of the seven segments of the ones digit seven segment display" );
            parameter.put( "longDescription", "<html><p>This returns 27 channels, where each of the seven segments has three measurements, "
                    + "and there are two reference measurements in the centers of the segments. <ul> "
                    + "<li>Channels 0,1,2 are in the center of the top four segments, "
                    + "<li>Channels 24,25,26 are in the center of the bottom four segments."
                    + "<li>Channels 3,4,5 are the upper segment, typically labelled A"
                    + "<li>Channels 6,7,8 are the upper segment, typically labelled B"
                    + "<li>Channels 9,10,11 are the upper segment, typically labelled C"
                    + "<li>Channels 12,13,14 are the upper segment, typically labelled D"
                    + "<li>Channels 15,16,17 are the upper segment, typically labelled E"
                    + "<li>Channels 18,19,20 are the upper segment, typically labelled F"
                    + "<li>Channels 21,22,23 are the upper segment, typically labelled G"
                    + "</ul></html>"
            );
            parameters.put( 1, parameter );
                             
        } else if ( Character.isDigit( id.charAt(0) ) ) {
            parameter= new JSONObject();
            parameter.put( "name", "Temperature" );
            parameter.put( "type", "float" );
            parameter.put( "units", "deg F" );
            parameter.put( "fill", "-1e31" );
            if ( id.equals("610008002FE00410")) {
                parameter.put( "description", "temperature in attic" );
            } else if ( id.equals("0B000800408DD710")) {
                parameter.put( "description", "temperature in garage, car" );
            } else if ( id.equals("8500080044259C10")) {
                parameter.put( "description", "temperature in garage, far" );
            } else if ( id.equals("AC00080040250510")) {
                parameter.put( "description", "temperature at thermostate" );
            } else {
                parameter.put( "description", "temperature at sensor " + id );
            }
            parameters.put( 1, parameter );
        } else {
            throw new IllegalArgumentException("unknown id: " +id );
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
