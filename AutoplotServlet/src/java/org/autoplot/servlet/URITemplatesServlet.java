
package org.autoplot.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.Collections;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.TimeParser;
import org.das2.datum.Units;
import org.das2.fsm.FileStorageModel;
import org.das2.util.filesystem.FileSystem;

/**
 * URI Templates servlet implements the hybrid generate/parse method
 * for resolving URIs.
 * @author jbf
 */
public class URITemplatesServlet extends HttpServlet {

    private boolean supportsGenerate( String s ) {
        return !(s.contains("$v") || s.contains("$(v") ||
                s.contains("$x") || s.contains("$(x") ||
                s.contains(";sparse") || s.contains(",sparse"));  // TODO: make canonical!!!
    } 
    
    private int doParse( DatumRange trdr, String root, String template, PrintWriter out ) throws IOException {
        
        int count= 0;
        
        FileSystem fs= FileSystem.create( root );
        FileStorageModel fsm= FileStorageModel.create( fs, template );
        fsm.setContext( trdr );

        String[] names= fsm.getNamesFor( trdr );

        for ( String n : names ) {
            out.printf(  "<tr>" );
            DatumRange tr= fsm.getRangeFor( n );
            String v;
            try {
                v= fsm.getField("v",n);
            } catch ( Exception ex ) {
                v= "N/A";
            }
            out.printf(  "<td>"+root + "/" + n + "</td><td>"+tr.toString() + "</td><td>" + v +"</td>\n" );
            out.printf(  "</tr>" );
            count= count+1;
        }
        return count;
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
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            /* TODO output your page here. You may use following sample code. */
            out.println("<!DOCTYPE html>");
            out.println("<html>");
            out.println("<head>");
            out.println("<title>URI Templates</title>");            
            out.println("</head>");
            out.println("<body>");
            
            String uri= request.getParameter("resourceURI");
            String timerange= request.getParameter("timerange");
            
            if ( (timerange.split(" ") ).length==10 ) {  // make it easier to use Jon V.'s document "uri_template_test_cases.txt"
                String[] trs= timerange.split(" ");
                timerange= String.format( "%s-%s-%sT%s:%s/%s-%s-%sT%s:%s", (Object[]) trs );
            }
            
            long t0= System.currentTimeMillis();
            
            DatumRange drtr;
            try {
                drtr= DatumRangeUtil.parseTimeRange(timerange);
            } catch ( ParseException ex ) {
                throw new RuntimeException( "unable to parse timerange",ex );
            }
        
            String[] ss= uri.split("/");
            
            StringBuilder generateUriBuilder= new StringBuilder(ss[0]);
            
            int i=1;
            while ( i<ss.length && supportsGenerate(ss[i]) ) {
                generateUriBuilder.append("/").append(ss[i]);
                i++;
            }
                    
            String generateUri= generateUriBuilder.toString();
            
            String parseUri;
            if ( generateUri.length()==uri.length() ) {
                parseUri= "";
            } else {
                parseUri= uri.substring(generateUri.length()+1);
            }
            
            TimeParser tp= TimeParser.create(generateUri);
            
            int i1= generateUri.indexOf("$(enum;");
            
            String[] enums;
            String id;
            
            if ( i1>-1 ) {
                int ix= generateUri.indexOf("$(enum",i1+6);
                if (ix>-1 ) {
                    throw new IllegalArgumentException( "Template can only contain one $(enum)." );
                } else {
                    TimeParser.EnumFieldHandler fh= (TimeParser.EnumFieldHandler) tp.getFieldHandlerByCode("enum");
                    enums= fh.getValues();
                    id= fh.getId();
                }
            } else {
                enums= new String[] { "" };
                id= "";
            }
            
            String st= tp.format( drtr.min(), null, Collections.singletonMap( id, enums[0] ) );
            DatumRange dr;
            try {
                dr = tp.parse( st,null ).getTimeRange();
            } catch (ParseException ex) {
                throw new RuntimeException("unable to parse timerange",ex);
            }

            out.printf( "<h1>Hybrid generate/parse result</h1>");
            out.printf( "<h3>%s</h3>\n", uri );
            out.printf( "<p>search limited to %s</p>\n", timerange );

            if ( parseUri.length()==0 ) {
                out.printf( "<p>generation used for entire URI</p>\n" );
            } else {
                out.printf( "<p>generation used for " + generateUri + ", parsing for " +parseUri + " </p>\n" );
            }
            out.printf( "<table border=1>\n" );
            out.printf( "<tr><td>Filename</td><td>Time Range</td><td>Version</td></tr>" );

            int count= 0;
            
            boolean isLink= generateUri.startsWith("https://") || generateUri.startsWith("http://");
            
            Datum stop= drtr.max().subtract( Datum.create(500,Units.ns ) ); //2010-03-01T00:00:00/10  http://data.org/data_$Y_$j_$H$M$S.$(subsec;places=1) would have extra because of roundoff.
            while ( count<=10000 && dr.min().lt( stop ) ) {
                for ( String enum1 : enums ) {
                    st= tp.format( dr.min(), dr.max(), Collections.singletonMap( id, enum1 ) ); 
                    String lst;
                    if ( isLink ) {
                        lst= "<a href=\""+st+"\">" + st + "</a>";
                    } else {
                        lst= st;
                    }
                    if ( parseUri.length()==0 ) {
                        out.printf(  "<tr><td>"+ lst + "</td><td>"+dr + "</td><td>N/A</td><tr>\n" );
                        count++;
                    } else {
                        count+= doParse( drtr, st, parseUri, out );
                    }

                    if ( count>10000 ) {
                        out.printf( "<tr><td></td><td>Search limited to 10000 results.</td></tr>" );
                        break;
                    }
                    dr= dr.next();
                }
            }
            
            out.println( "</table>");
            
            long dt= System.currentTimeMillis() - t0;
            
            out.printf("%d results calculated in %d milliseconds.\n",count,dt);
            out.println("<br><small>v20250327.1</small>");
            out.println("</body>");
            out.println("</html>");
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
