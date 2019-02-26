
package org.autoplot.hapiserver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.logging.Level;
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
 * @see https://github.com/hapi-server/data-specification/blob/master/hapi-2.0.0/HAPI-data-access-spec-2.0.0.md#info
 * @author jbf
 */
public class InfoServlet extends HttpServlet {
    
    private static final Logger logger= Logger.getLogger("hapi");    
    
    /**
     * 
     * @param id the identifier
     * @return the JSON object
     * @throws JSONException
     * @throws IllegalArgumentException if the id is not defined.
     * @throws IOException 
     */
    protected static JSONObject getInfo( String id ) throws JSONException, IllegalArgumentException, IOException {
        
        if ( !HapiServerSupport.getCatalogIds().contains(id) ){
            throw new IllegalArgumentException("invalid parameter id: \""+id+"\" is not known.");
        }
        
        JSONArray parameters= new JSONArray();

        File infoFileHome= new File( Util.getHapiHome(), "info" );
        File infoFile= new File( infoFileHome, id+".json" );
        
        JSONObject o= HapiServerSupport.readJSON(infoFile);
        
        o.put("HAPI",Util.hapiVersion());
        o.put("x_createdAt",String.format("%tFT%<tRZ",Calendar.getInstance(TimeZone.getTimeZone("Z"))));
        
        JSONArray parametersRead= o.getJSONArray("parameters");
        for ( int i=0; i<parametersRead.length(); i++ ) {
            JSONObject jo1= parametersRead.getJSONObject(i);
            parameters.put( i,jo1  );
            if ( !jo1.has("fill") ) {
                logger.log(Level.WARNING, "required parameter fill is missing from parameter {0}", i);
            }
        }
        
        if ( o.has("modificationDate") ) { // allow modification date to be "lasthour"
            String modificationDate= o.getString("modificationDate");
            DatumRange tr= DatumRangeUtil.parseTimeRangeValid( modificationDate+"/now" );
            o.put( "modificationDate", tr.min().toString() );
        }
        
        // support local features like "now-P3D", which are not hapi features.
        if ( o.has("startDate") && o.has("stopDate") ) { 
            String startDate= o.getString("startDate");
            String stopDate= o.getString("stopDate");
            DatumRange tr= DatumRangeUtil.parseTimeRangeValid( startDate+"/"+stopDate );
            o.put( "startDate", tr.min().toString() );
            o.put( "stopDate", tr.max().toString() );
        } else {
            if ( !o.has("startDate") ) {
                logger.warning("non-conformant server needs to have startDate");
            }
            if ( !o.has("stopDate") ) {
                logger.warning("non-conformant server needs to have stopDate");
            }
        }
        
        if ( o.has("sampleStartDate") && o.has("sampleStopDate") ) { 
            String startDate= o.getString("sampleStartDate");
            String stopDate= o.getString("sampleStopDate");
            DatumRange tr= DatumRangeUtil.parseTimeRangeValid( startDate+"/"+stopDate );
            o.put( "sampleStartDate", tr.min().toString() );
            o.put( "sampleStopDate", tr.max().toString() );
        }
        
        JSONObject status= new JSONObject();
        status.put( "code", 1200 );
        status.put( "message", "OK request successful");
                
        o.put( "status", status );
        o.put("x_infoVersion__", "20171201.1" );
        return o;

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
        
        String HAPI_SERVER_HOME= getServletContext().getInitParameter("HAPI_SERVER_HOME");
        Util.setHapiHome( new File( HAPI_SERVER_HOME ) );
            
        String id= request.getParameter("id");
        
        logger.log(Level.FINE, "info request for {0}", id);
        
        if ( id==null ) throw new ServletException("required parameter 'id' is missing from request");
        
        response.setContentType("application/json;charset=UTF-8");        
        
        response.setHeader("Access-Control-Allow-Origin", "* " );
        response.setHeader("Access-Control-Allow-Methods","GET" );
        response.setHeader("Access-Control-Allow-Headers","Content-Type" );
        
        try (PrintWriter out = response.getWriter()) {
           try {
               JSONObject jo= getInfo( id );
               String parameters= request.getParameter("parameters");
               if ( parameters!=null) {
                   jo= Util.subsetParams(jo,parameters);
               }
               String s= jo.toString(4);
               out.write(s);
           } catch ( IllegalArgumentException ex ) {
                Util.raiseBadId(id, response, out);
           }
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


    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost( req, resp );
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

        String id= request.getParameter("id");
        
        if ( id==null ) throw new ServletException("required parameter 'id' is missing from request");
        
        String key= request.getParameter("key"); // key is authorization, not authentication
        
        if ( !Util.keyCanCreate(id,key) ) {
            throw new ServletException("need key to add to catalog");
        }
        
        File dataFileHome= new File( Util.getHapiHome(), "info" );
        File dataFile= new File( dataFileHome, id+".json" );
        
        ByteArrayOutputStream out= new ByteArrayOutputStream(2000);
        
        try ( BufferedReader r = new BufferedReader( new InputStreamReader( request.getInputStream() ) ); BufferedWriter fout= new BufferedWriter( new OutputStreamWriter(out) ) ) {
            String s;
            while ( (s=r.readLine())!=null ) {
                fout.write(s);
                fout.write("\n");
            }
        }

        String enc= request.getCharacterEncoding();
        if ( enc==null ) enc= "UTF-8";
        String json= out.toString( enc );
        
        try {
            //verify that it is valid JSON
            JSONObject jo= new JSONObject(json);
        } catch (JSONException ex) {
            throw new ServletException(ex);
        }
        
        try ( PrintWriter lout= new PrintWriter(dataFile) ) {
            lout.write(json);
        }
        
        File catalogFile= new File( Util.getHapiHome(), "catalog.json" );
        try {        
            JSONObject catalog= HapiServerSupport.readJSON(catalogFile);
            JSONArray catalogArray= catalog.getJSONArray("catalog");
            int index= -1;
            for ( int i=0; i<catalogArray.length(); i++ ) {
                if ( catalogArray.getJSONObject(i).get("id").equals(id) ) {
                    index= i;
                }
            }
            if ( index==-1 ) {
                JSONObject item= new JSONObject();
                item.put("id", id );
                catalogArray.put( catalogArray.length(), item );
                File tempCatalogFile= new File( Util.getHapiHome(), "catalog.json.t" );
                try ( FileWriter w=new FileWriter( tempCatalogFile ) ) {
                    String s= catalog.toString(4);
                    w.write(s);
                }
                
                tempCatalogFile.renameTo(catalogFile);
            }
            
        } catch (FileNotFoundException ex) {
            Logger.getLogger(InfoServlet.class.getName()).log(Level.SEVERE, null, ex);
        } catch (JSONException ex) {
            Logger.getLogger(InfoServlet.class.getName()).log(Level.SEVERE, null, ex);
        }
                
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
