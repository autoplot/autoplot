
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
    private static final Logger logger= Logger.getLogger("hapi");    
    protected static JSONObject getInfo( String id ) throws JSONException, IllegalArgumentException, IOException {
        JSONObject jo= new JSONObject();
        jo.put("HAPI","1.0");
        jo.put("createdAt",String.format("%tFT%<tRZ",Calendar.getInstance(TimeZone.getTimeZone("Z"))));
        
        if ( id.equals("Iowa City Conditions") ) {
            jo.put( "startDate", "2012-05-25T00:00Z" );
            jo.put( "stopDate", String.format("%tFT%<tRZ", Calendar.getInstance(TimeZone.getTimeZone("Z"))) );
        } else if ( id.equals("Iowa City Forecast") ) {
            jo.put( "startDate", "2012-05-25T00:00Z" );
            jo.put( "stopDate", String.format("%tFT%<tRZ", Calendar.getInstance(TimeZone.getTimeZone("Z"))) );
        } else if ( id.equals("0B000800408DD710.nostream") ) {
            jo.put( "startDate", "2012-01-09T00:00Z" );
            jo.put( "stopDate", String.format("%tFT%<tRZ", Calendar.getInstance(TimeZone.getTimeZone("Z"))) );            
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

        if ( id.equals("SpectrogramRank2") ) { 
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
            parameter.put( "x_about","http://jfaden.net/HapiServerDemo/about/about.html#SpectrogramRank2");
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
            parameter.put( "longDescription", "<html><p>This returns 27 channels, where each of the seven segments has "
                    + "three measurements, and there are two reference measurements in the centers of the segments. <ul> "
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
