package org.autoplot.hapiserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.virbo.datasource.RecordIterator;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;

/**
 *
 * @author jbf
 */
@WebServlet(urlPatterns = {"/DataServlet"})
public class DataServlet extends HttpServlet {

    private String getParam( Map<String,String[]> request, String name, String deft, String doc, Pattern constraints ) {
        String[] vs= request.remove(name);
        String v;
        if ( vs==null ) {
            v= deft;
        } else {
            v= vs[0];
        }
        if ( v==null ) v= deft;
        if ( constraints!=null ) {
            if ( !constraints.matcher(v).matches() ) {
                throw new IllegalArgumentException("parameter "+name+"="+v +" doesn't match pattern");
            }
        }
        if ( v==null ) throw new IllegalArgumentException("required parameter "+name+" is needed");
        return v;
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
        
        Map<String,String[]> params= new HashMap<String, String[]>( request.getParameterMap() );
        String id= getParam( params,"id",null,"The identifier for the resource.", null );
        String timeMin= getParam( params, "time.min", null, "The smallest value of time to include in the response.", null );
        String timeMax= getParam( params, "time.max", null, "The largest value of time to include in the response.", null );
        String parameters= getParam( params, "parameters", "", "The comma separated list of parameters to include in the response ", null );
        String include= getParam( params, "include", "", "include header at the top", Pattern.compile("(|header)") );
        String format= getParam( params, "format", "", "The desired format for the data stream.", Pattern.compile("(|csv|binary)") );
        String stream= getParam( params, "stream", "true", "allow/disallow streaming.", Pattern.compile("(|true|false)") );
        
        if ( !params.isEmpty() ) {
            throw new ServletException("unrecognized parameters: "+params);
        }
        
        DataFormatter dataFormatter;
        if ( format.equals("binary") ) {
            response.setContentType("application/binary");
            dataFormatter= new BinaryDataFormatter();
            response.setHeader("Content-disposition", "attachment; filename="+ Ops.safeName(id) + "_"+timeMin+ "_"+timeMax + ".bin" );
        } else {
            response.setContentType("text/csv;charset=UTF-8");  
            dataFormatter= new CsvDataFormatter();
            response.setHeader("Content-disposition", "attachment; filename="+ Ops.safeName(id) + "_"+timeMin+ "_"+timeMax + ".csv" ); 
        }

        DatumRange dr;
        try {
            dr = new DatumRange( Units.cdfTT2000.parse(timeMin), Units.cdfTT2000.parse(timeMax) );
        } catch (ParseException ex) {
            throw new IllegalArgumentException(ex);
        }

        RecordIterator dsiter;
        
        if ( !( HapiServerSupport.getCatalog().contains(id) ) ) {
            throw new IllegalArgumentException("id not recognized");
        }
        
        boolean allowStream= !stream.equals("false");

        try {
            if ( id.equals("Iowa City Conditions") ) { // TODO: 
                dsiter= new RecordIterator( "vap+jyds:file:///home/jbf/public_html/1wire/ictemp/readTemperaturesMulti.jyds", dr, allowStream );
            } else if ( id.equals("Spectrum") ) {
                dsiter= new RecordIterator( "vap+cdaweb:ds=RBSP-A_HFR-SPECTRA_EMFISIS-L2&id=HFR_Spectra", dr , allowStream);
            } else if ( id.equals("PowerWheel") ) {
                dsiter= new RecordIterator( "file:/home/jbf/ct/autoplot/rfe/529/powerWheel.jyds?", dr, allowStream );
            } else if ( id.equals("PowerOnesDigitSegments") ) {
                dsiter= new RecordIterator( "file:/home/jbf/ct/autoplot/rfe/529/powerOnes.jyds?", dr, allowStream );
            } else if ( id.equals("0B000800408DD710.noStream") ) {
                dsiter= new RecordIterator( "file:/home/jbf/public_html/1wire/data/$Y/$m/$d/0B000800408DD710.$Y$m$d.d2s", dr, false ); // allow Autoplot to select
            } else {
                dsiter= new RecordIterator( "file:/home/jbf/public_html/1wire/data/$Y/$m/$d/"+id+".$Y$m$d.d2s", dr, allowStream );
            }
        } catch ( Exception ex ) {
            ex.printStackTrace();
            throw new IllegalArgumentException("Exception thrown by data read", ex);
        }
        
        dsiter.constrainDepend0(dr);
        
        OutputStream out = response.getOutputStream();
        
        if ( format.equals("binary") ) {
            if ( include.equals("header") ) throw new IllegalArgumentException("header cannot be sent with binary");
        }
        
        try {

            JSONObject jo= InfoServlet.getInfo( id );

            if ( !parameters.equals("") ) {
                String[] pps= parameters.split(",");
                Map<String,Integer> map= new HashMap();
                JSONArray jsonParameters= jo.getJSONArray("parameters");
                for ( int i=0; i<jsonParameters.length(); i++ ) {
                    map.put( jsonParameters.getJSONObject(i).getString("name"), i ); // really--should name/id are two names for the same thing...
                }
                JSONArray newParameters= new JSONArray();
                int[] indexMap= new int[pps.length];
                for ( int ip=0; ip<pps.length; ip++ ) {
                    int i= map.get(pps[ip]);
                    indexMap[ip]= i;
                    newParameters.put( ip, jsonParameters.get(i) );
                }
                dsiter.resortFields( indexMap );
                jsonParameters= newParameters;
                jo.put( "parameters", jsonParameters );
            }

            if ( include.equals("header") ) {
                ByteArrayOutputStream boas= new ByteArrayOutputStream(10000);
                PrintWriter pw= new PrintWriter(boas);
                
                pw.write( jo.toString(4) );
                pw.close();
                boas.close();
                String[] ss= boas.toString("UTF-8").split("\n");
                for ( String s: ss ) {
                    out.write( "# ".getBytes("UTF-8") );
                    out.write( s.getBytes("UTF-8") );
                    out.write( (char)10 );
                }
            }
        } catch (JSONException ex) {
            throw new ServletException(ex);
        }

        
        try {

            if ( dsiter.hasNext() ) {
                            
                QDataSet first= dsiter.next();
            
                dataFormatter.initialize( out, first );
        
                dataFormatter.sendRecord( out, first );
                while ( dsiter.hasNext() ) {
                    dataFormatter.sendRecord( out, dsiter.next() );
                }
            }
            
            dataFormatter.finalize(out);
            
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
