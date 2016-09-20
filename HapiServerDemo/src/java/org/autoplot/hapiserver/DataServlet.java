package org.autoplot.hapiserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.virbo.datasource.RecordIterator;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.json.JSONException;
import org.json.JSONObject;
import org.virbo.dataset.QDataSet;

/**
 *
 * @author jbf
 */
@WebServlet(urlPatterns = {"/DataServlet"})
public class DataServlet extends HttpServlet {

    private String getParam( HttpServletRequest request, String name, String deft, String doc, Pattern constraints ) {
        String v= request.getParameter(name);
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
        
        String id= getParam( request,"id",null,"The identifier for the resource.", null );
        String timeMin= getParam( request, "time.min", null, "The smallest value of time to include in the response.", null );
        String timeMax= getParam( request, "time.max", null, "The largest value of time to include in the response.", null );
        String parameters= getParam( request, "parameters", "", "The comma separated list of parameters to include in the response ", null );
        String include= getParam( request, "include", "", "include header at the top", Pattern.compile("(|header)") );
        String format= getParam( request, "format", "", "The desired format for the data stream.", Pattern.compile("(|csv|binary)") );
        
        DataFormatter dataFormatter;
        if ( format.equals("binary") ) {
            response.setContentType("application/binary");
            dataFormatter= new BinaryDataFormatter();
        } else {
            response.setContentType("text/csv;charset=UTF-8");  
            dataFormatter= new CsvDataFormatter();
        }

        DatumRange dr;
        try {
            dr = new DatumRange( Units.cdfTT2000.parse(timeMin), Units.cdfTT2000.parse(timeMax) );
        } catch (ParseException ex) {
            throw new IllegalArgumentException(ex);
        }

        RecordIterator dsiter;
        
        if ( id.equals("Iowa City Conditions") ) { // TODO: 
            dsiter= new RecordIterator( "vap+jyds:file:///home/jbf/public_html/1wire/ictemp/readTemperaturesMulti.jyds", dr );
        } else {
            dsiter= new RecordIterator( "file:/home/jbf/public_html/1wire/data/$Y/$m/$d/"+id+".$Y$m$d.d2s", dr );
        }
        
        dsiter.constrainDepend0(dr);
                
        OutputStream out = response.getOutputStream();
        
        if ( include.equals("header") ) {
            if ( format.equals("binary") ) {
                throw new IllegalArgumentException("header cannot be sent with binary");
            }
            try {
                ByteArrayOutputStream boas= new ByteArrayOutputStream(10000);
                PrintWriter pw= new PrintWriter(boas);
                JSONObject jo= InfoServlet.getInfo( id, parameters ); //TODO: BUG
                jo.write(pw);
                pw.close();
                boas.close();
                out.write( boas.toByteArray() );
                out.write((char)10);
            } catch (JSONException ex) {
                throw new ServletException(ex);
            }
        }
        
        try {

            QDataSet first= dsiter.next();
            
            dataFormatter.initialize( out, first );
        
            dataFormatter.sendRecord( out, first );
            while ( dsiter.hasNext() ) {
                dataFormatter.sendRecord( out, dsiter.next() );
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
