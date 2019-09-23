/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.servlet;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringBufferInputStream;
import java.io.StringReader;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.autoplot.JythonUtil;
import org.autoplot.ScriptContext;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.URISplit;
import org.autoplot.jythonsupport.JythonRefactory;
import org.autoplot.jythonsupport.JythonUtil.Param;
import org.autoplot.jythonsupport.ui.Util;
import org.autoplot.scriptconsole.LoggingOutputStream;
import org.das2.util.FileUtil;
import org.das2.util.monitor.NullProgressMonitor;
import org.python.util.PythonInterpreter;

/**
 *
 * @author jbf
 */
public class ScriptGUIServlet extends HttpServlet {

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
 
        String script= request.getParameter("script");
        Map params= request.getParameterMap();
        Map<String,String> ssparams= new LinkedHashMap<>();
        ArrayList<String> slparams= new ArrayList<>();
        StringBuilder sbparams= new StringBuilder();
        for ( Object o: params.entrySet() ) {
            Entry e= (Entry)o;
            String value= Array.get(e.getValue(),0).toString();
            ssparams.put( e.getKey().toString(), value );
            slparams.add( e.getKey().toString() + "=" + value );
            sbparams.append("&").append(e.getKey().toString()).append("=").append(value);
        }
        String sparams= sbparams.toString();
        String[] aaparams= slparams.toArray(new String[slparams.size()]);
        
        if ( script==null ) {
            script= "https://github.com/autoplot/dev/blob/master/demos/2019/20190726/demoParams.jy";
        }
        
        URISplit split= URISplit.parse(script);
        String pwd= split.path;
        String name= split.file.substring(split.path.length());
                
        File scriptFile= DataSetURI.getFile( script, new NullProgressMonitor() );
        script= FileUtil.readFileToString(scriptFile);            
        
        if ( request.getParameter("img")!=null ) {
            // now run the script
            ScriptContext.getDocumentModel().getOptions().setAutolayout(false);

            PythonInterpreter interp = JythonUtil.createInterpreter( true, true );
            interp.set("java",null);
            interp.set("org",null);
            interp.set("getFile",null);
            interp.set("dom",ScriptContext.getDocumentModel());
            interp.set("downloadResourceAsTempFile",null);

            LoggingOutputStream los1= new LoggingOutputStream( Logger.getLogger("autoplot.servlet.scriptservlet"), Level.INFO );
            interp.setOut( los1 );
            
            interp.set( "response", response );

            // To support load balancing, insert the actual host that resolved the request
            response.setHeader( "X-Served-By", java.net.InetAddress.getLocalHost().getCanonicalHostName() );
            
            //TODO: this limits to one user!
            LoggingOutputStream los2= new LoggingOutputStream( Logger.getLogger("autoplot.servlet.scriptservlet"), Level.INFO ); 
            ScriptContext._setOutputStream( los2 ); 
            
            script= JythonRefactory.fixImports(script);
            interp.exec(script);
            
            JythonUtil.runScript( ScriptContext.getDocumentModel(), new ByteArrayInputStream(script.getBytes("UTF-8")), name, aaparams, pwd );
            
            try (OutputStream out = response.getOutputStream()) {
                ScriptContext.writeToPng(out);
                try { los1.close(); } catch ( IOException ex ) {}
                try { los2.close(); } catch ( IOException ex ) {}
            }
        } else {
            response.setContentType("text/html;charset=UTF-8");
        
            Map<String,Param> parms= Util.getParams( null, script, ssparams, new NullProgressMonitor() );
        
            try (PrintWriter out = response.getWriter()) {
                /* TODO output your page here. You may use following sample code. */
                out.println("<!DOCTYPE html>");
                out.println("<html>");
                out.println("<head>");
                out.println("<title>Servlet ScriptGUIServlet</title>");            
                out.println("</head>");
                out.println("<body>");
                out.println("<h1>Servlet ScriptGUIServlet at " + request.getContextPath() + "</h1>");
                out.println("<table><tr>");
                out.println("<td>");
                out.println("<form action='ScriptGUIServlet'>");
                for ( Entry<String,Param> pe: parms.entrySet() ) {
                    Param p= pe.getValue();
                    out.println(""+p.name +", <em>" + p.doc +"</em><br>");
                    if ( p.enums!=null ) {
                        if ( p.enums.size()==2 && p.enums.contains("T") && p.enums.contains("F") ) {
                            if ( "T".equals(p.value) ) {
                                out.println("<input type='checkbox' name='"+p.name+"' checked>"+p.name + ", " + p.doc);
                            } else if ( "on".equals(p.value) ) {
                                out.println("<input type='checkbox' name='"+p.name+"' checked>"+p.name + ", " + p.doc);
                                sparams= sparams.replace(p.name+"=on", p.name+"=T");
                            } else {
                                out.println("<input type='checkbox' name='"+p.name+"'>"+p.name + ", " + p.doc);
                            }
                        } else {
                            out.println("<select name='"+p.name+"'>");
                            for ( Object s: p.enums ) {
                                if ( s.equals(p.value) ) {
                                    out.println("<option value='"+s+"' selected>"+s+"</input>");
                                } else {
                                    out.println("<option value='"+s+"'>"+s+"</input>");
                                }
                            }
                            out.println("</select>");
                        }
                    } else if ( (p.type=='F') || (p.type=='A') ) {
                        Object s= (p.value!=null) ? p.value : p.deft;
                        out.println("<input name='"+p.name+"' value='"+s+"'></input>");
                    } 
                    out.println("<br><br>");
                }
                if ( parms.isEmpty() ) {
                    out.println("script has no parameters.");
                }
                out.println("<input type='submit' value='Submit'>");
                out.println("</form>");
                out.println( "</td>");
                out.println( "<td>");
                out.println( "<img src='ScriptGUIServlet?img=1"+sparams+"' alt='image'>" );
                out.println( "</td>");
                out.println( "</tr>");
                out.println( "</table>");
                out.println("</body>");
                out.close();
                
            }
            
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
