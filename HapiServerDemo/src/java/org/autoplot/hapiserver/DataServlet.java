package org.autoplot.hapiserver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.das2.datum.Datum;
import org.autoplot.datasource.RecordIterator;
import org.das2.datum.DatumRange;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.das2.qds.DataSetUtil;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;

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
        
        String HAPI_SERVER_HOME= getServletContext().getInitParameter("HAPI_SERVER_HOME");
        Util.setHapiHome( new File( HAPI_SERVER_HOME ) );
            
        Map<String,String[]> params= new HashMap<>( request.getParameterMap() );
        String id= getParam( params,"id",null,"The identifier for the resource.", null );
        String timeMin= getParam( params, "time.min", null, "The earliest value of time to include in the response.", null );
        String timeMax= getParam( params, "time.max", null, "The latest value of time to include in the response.", null );
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
        
        if ( !( HapiServerSupport.getCatalogIds().contains(id) ) ) {
            Util.raiseBadId(id, response, response.getWriter() );
            return;
        }
        
        boolean allowStream= !stream.equals("false");

        OutputStream out = response.getOutputStream();
                
        File[] dataFiles= null;
        
        try {
            dsiter= checkAutoplotSource( id, dr, allowStream );
            if ( dsiter==null ) {
                File dataFileHome= new File( Util.getHapiHome(), "data" );
                File dataFile= new File( dataFileHome, id+".csv" );
                if ( dataFile.exists() ) {
                    dataFiles= new File[] { dataFile };
                } else {
                    if ( id.equals("0B000800408DD710.noStream") ) {
                        dsiter= new RecordIterator( "file:/home/jbf/public_html/1wire/data/$Y/$m/$d/0B000800408DD710.$Y$m$d.d2s", dr, false ); // allow Autoplot to select
                    } else {
                        throw new IllegalArgumentException("bad id: "+id+", does not exist: "+dataFile );
                    }
                }
            }
        } catch ( Exception ex ) {
            ex.printStackTrace();
            throw new IllegalArgumentException("Exception thrown by data read", ex);
        }
        
        try {
            if ( dsiter!=null ) dsiter.constrainDepend0(dr);
        } catch ( IllegalArgumentException ex ) {
            response.setHeader( "X-WARNING", "data is not monotonic in time, sending everything." );
        }
        
        if ( format.equals("binary") ) {
            if ( include.equals("header") ) throw new IllegalArgumentException("header cannot be sent with binary");  //TODO: check this
        }
        
        JSONObject jo;
        
        try {

            jo= InfoServlet.getInfo( id );

            if ( !parameters.equals("") ) {
                jo= Util.subsetParams( jo, parameters );
                if ( dsiter!=null ) {
                    int[] indexMap= (int[])jo.get("__indexmap__");
                    dsiter.resortFields( indexMap );
                }
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
            
            if ( dataFiles!=null ) {
                for ( File dataFile : dataFiles ) {
                    cachedDataCsv( out, dataFile, dr, parameters );
                }
                return;
            }
            
        } catch (JSONException ex) {
            throw new ServletException(ex);
        }

        
        try {
            assert dsiter!=null;
            if ( dsiter.hasNext() ) {
                            
                QDataSet first= dsiter.next();
            
                dataFormatter.initialize( jo, out, first );
        
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
        Map<String,String[]> params= new HashMap<>( request.getParameterMap() );
        String id= getParam( params,"id",null,"The identifier for the resource.", null );
        String timeMin= getParam( params, "time.min", "", "The earliest value of time to include in the response.", null );
        String timeMax= getParam( params, "time.max", "", "The latest value of time to include in the response.", null );
        String parameters= getParam( params, "parameters", "", "The comma separated list of parameters to include in the response ", null );
        String include= getParam( params, "include", "", "include header at the top", Pattern.compile("(|header)") );
        String format= getParam( params, "format", "", "The desired format for the data stream.", Pattern.compile("(|csv|binary)") );
        
        String key= request.getParameter("key"); // key is authorization, not authentication
        if ( !Util.keyCanModify(id,key) ) {
            throw new ServletException("need key to modify items in catalog");
        }

        if ( !include.equals("") ) throw new IllegalArgumentException("include cannot be used");
        if ( !parameters.equals("") ) throw new IllegalArgumentException("parameters cannot be used");
        if ( !format.equals("") ) throw new IllegalArgumentException("format cannot be used");
        if ( !timeMin.equals("") ) throw new IllegalArgumentException("time.min cannot be used");
        if ( !timeMax.equals("") ) throw new IllegalArgumentException("time.max cannot be used");
            
        File dataFileHome= new File( Util.getHapiHome(), "data" );
        File dataFile= new File( dataFileHome, id+".csv" );
        
        try ( BufferedReader r = new BufferedReader( new InputStreamReader( request.getInputStream() ) ); BufferedWriter fout= new BufferedWriter( new FileWriter(dataFile) ) ) { //TODO: merge
            String s;
            while ( (s=r.readLine())!=null ) {
                fout.write(s);
                fout.write("\n");
            }
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

    private RecordIterator checkAutoplotSource(String id, DatumRange dr, boolean allowStream) throws IOException, JSONException, Exception {
        File configFile= new File( new File( Util.getHapiHome().toString(), "info" ), id+".json" );
        if ( !configFile.exists() ) {
            return null;
        }
        JSONObject o= HapiServerSupport.readJSON(configFile);
        if ( o.has("uri") ) {
            String suri= o.getString("uri");
            RecordIterator dsiter= new RecordIterator( suri, dr, allowStream ); 
            return dsiter;
        } else {
            return null;
        }
    }

    /**
     * we have the csv pre-calculated, so just read from it.
     * Note the output stream is closed here!
     * @param out
     * @param dataFile
     * @param dr
     * @param parameters
     * @throws FileNotFoundException
     * @throws IOException 
     * 
     */
    private void cachedDataCsv(OutputStream out, File dataFile, DatumRange dr, String parameters) throws FileNotFoundException, IOException {
        try ( BufferedReader reader= new BufferedReader( new FileReader(dataFile) ); 
              BufferedWriter writer= new BufferedWriter( new OutputStreamWriter(out) ) ) {
            String line= reader.readLine();
            while ( line!=null ) {
                int i= line.indexOf(",");
                try {
                    Datum t= TimeUtil.create(line.substring(0,i));
                    if ( dr.contains(t) ) {
                        writer.write(line);
                        writer.newLine();
                    }
                } catch (ParseException ex) {
                    Logger.getLogger(DataServlet.class.getName()).log(Level.SEVERE, null, ex);
                }
                line= reader.readLine();
            }
        }
    }

    
}
