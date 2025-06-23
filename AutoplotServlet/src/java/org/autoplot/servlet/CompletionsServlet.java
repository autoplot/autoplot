
package org.autoplot.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.autoplot.ScriptContext2023;
import org.autoplot.datasource.URISplit;

/**
 * Servlet to provide completions and will be used to bridge the thin client launcher with
 * the thin client fully-qualified URI.
 * @author jbf
 */
public class CompletionsServlet extends HttpServlet {

    private static final Logger logger= Logger.getLogger("autoplot.servlet" );
        
    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        String uri= request.getParameter("uri");
        String alt= request.getParameter("alt");
        
        if ( "1".equals(alt) ) {
            response.setContentType("text/html;charset=UTF-8");
        } else {
            response.setContentType("text/plain;charset=UTF-8");
        }
        
        PrintWriter out = response.getWriter();

        if ( "1".equals(alt) ) {
            out.println("<html><head></head>");
        }
        
        if ( uri==null ) throw new IllegalArgumentException("uri parameter not specified");
        URISplit split= URISplit.parse(uri); // make it canonical
                    
        boolean whiteListed= false;
        List<String> whiteList= ServletUtil.getWhiteList();
        for ( String s: whiteList ) {
            if ( Pattern.matches( s, uri ) ) {
                whiteListed= true;
                logger.fine("uri is whitelisted");
            }
        }

        if ( split.scheme.startsWith("file") && !whiteListed ) {
            throw new IllegalArgumentException("URI cannot be a local file: "+uri);
        }
        
        if ( !uri.endsWith("&") ) uri= uri+"&";  //kludge for Dan's server, which doesn't include the final ampersand.  This should probably be removed.
        try {
            ScriptContext2023 scriptContext= new ScriptContext2023();
            String[] result= scriptContext.getCompletions(uri);
            for ( String r: result ) {
                if ( "1".equals(alt) ) {
                    out.println("<a href='thin/zoom/demo.jsp?uri="+r+"'>"+r+"</a><br>\n");
                } else {
                    out.println(r);
                }
            }
            if ( "1".equals(alt) ) {
                out.println("</html>");
            }
                    
        } catch ( Exception ex ) {
            out.println("(Error: "+ex.getMessage()+")");
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
