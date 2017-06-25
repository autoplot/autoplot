package org.autoplot.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Alternative to the CGI/Perl based solutions for dynamically creating the JNLP to
 * launch into a specific Autoplot configuration.
 * 
 * This uses the following rules:
 * <li>version parameter indicates the version of Autoplot to run.
 * <li>max-heap-size parameter indicates the heap size to use, 1G is the default to support 32 bit machines.
 * <li>URI or URL parameter is the Autoplot URI or vap file.  Everything following this considered to be part of the URI, so no further servlet parameters can be specified.
 * <li>open parameter is also an alias for URI, but I'm not sure if it is used. 
 * <li>If the parameter appears to be a URI, then it and everything following it is considered to be part of the URI.
 * 
 * This allows the following URLs, see http://autoplot.org/hudson/job/autoplot-test-jnlp-server/:
 *  http://autoplot.org/autoplot.jnlp
 *  http://autoplot.org/autoplot.jnlp?version=devel
 *  http://autoplot.org/autoplot.jnlp?http://autoplot.org/data.txt
 *  http://autoplot.org/autoplot.jnlp?version=hudson&URI=http://autoplot.org/data.txt
 *  http://autoplot.org/autoplot.jnlp?version=hudson&http://autoplot.org/data.txt
 *  http://autoplot.org/autoplot.jnlp?version=hudson&samp=true
 *  http://autoplot.org/autoplot.jnlp?version=hudson&samp=true
 *  http://autoplot.org/autoplot.jnlp?version=hudson&vap+cdaweb:filter=polar
 *  http://autoplot.org/autoplot.jnlp?vap+bin:http://www-pw.physics.uiowa.edu/voyager/data/pra/v1790205?reportOffset=yes&open=rank2=6:262&recLength=528&type=ushort&byteOrder=big
 *  http://autoplot.org/autoplot.jnlp?max-heap-size=4G'
 * 
 * @author jbf
 */
public class JnlpServlet extends HttpServlet {

    /**
     * Processes requests for both HTTP
     * <code>GET</code> and
     * <code>POST</code> methods.
     *
     * 
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/x-java-jnlp-file;charset=UTF-8");
        //response.setContentType("text/xml;charset=UTF-8");
        
        response.setHeader("Content_Disposition", "attachment;filename=\"autoplot.jnlp\"");
        response.setHeader("cache-control","no-cache, no-store, must-revalidate");
        
        PrintWriter out = response.getWriter();
        try {
            
            // find the index of the URI.
            String queryString= request.getQueryString();
            if ( queryString==null ) queryString= "";
            int isplit= findURIIndex( queryString );
            String myParams= isplit>0 ? queryString.substring(0,isplit) : "";
            String suri= queryString.substring(isplit);
            
            String version= "latest";
            String maxHeapSize= null;
            
            
            String[] ss= myParams.split("&");
            for ( String s: ss ) {
                int i= s.indexOf("=");
                if ( i>-1 ) {
                    String n= s.substring(0,i);
                    String v= s.substring(i+1);
                    if ( n.equals("version") ) {
                        version=v ;
                    } else if ( n.equals("max-heap-size") ) {
                        maxHeapSize= v;
                    }
                }
            }
            
            String codebase= "http://autoplot.org/jnlp/"+version;
                    
            URL jnlpUrl= JnlpServlet.class.getResource("/autoplot.jnlp");
            
            InputStream in = jnlpUrl.openStream();
            BufferedReader read= new BufferedReader( new InputStreamReader(in) );
            String s= read.readLine();
            while ( s!=null ) {
                s= s.replaceAll("\\#\\{codebase\\}",codebase);
                if ( suri!=null && s.contains("--nop" ) ) { // redundant check for debugging
                    s= s.replaceAll("--nop",suri);
                }
                if ( maxHeapSize!=null && s.contains("max-heap-size") ) { // redundant check for debugging
                    s= s.replaceAll( "max-heap-size=\"1024m\"", String.format("max-heap-size=\"%s\"", maxHeapSize ) );
                }
                out.println(s);
                s= read.readLine();
            }
        } finally {            
            out.close();
        }
    }
    
    /**
     * return the index of the first character of the URI.
     * @param params
     * @return the index of the URI, or params.length() if no URI is found.
     */
    private int findURIIndex( String params ) {
        int i=0;
        String[] ss= params.split("&");
        for ( String paramval: ss ) {
            int ie= paramval.indexOf("=");
            if ( ie==-1 ) {
                return i; // no equals, it must be a URI, right?  :/
            } else {
                String param= paramval.substring(0,ie);
                if ( param.contains(":") || param.contains("/") ) {
                        return i;
                } else {
                    if ( param.equals("URI") || param.equals("URL" )) {
                        return i+4;
                    } else if ( param.equals("open") ) {
                        return i+5;
                    }
                }
            }
            i= i + 1 + paramval.length();
        }
        return params.length();
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP
     * <code>GET</code> method.
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
     * Handles the HTTP
     * <code>POST</code> method.
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
