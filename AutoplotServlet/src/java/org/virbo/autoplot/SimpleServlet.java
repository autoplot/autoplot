/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import java.awt.Font;
import org.das2.util.DasPNGConstants;
import org.das2.util.DasPNGEncoder;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.das2.graph.DasColumn;
import org.das2.util.AboutUtil;
import org.das2.util.awt.GraphicsOutput;
import org.virbo.datasource.DataSetSelectorSupport;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;

/**
 *
 * @author jbf
 */
public class SimpleServlet extends HttpServlet {

    /** 
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     * @param request servlet request
     * @param response servlet response
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        try {


            String arg0 = request.getParameter("url");
            String vap = request.getParameter("vap");
            int width = ServletUtil.getIntParameter(request, "width", 700);
            int height = ServletUtil.getIntParameter(request, "height", 400);
            String format = ServletUtil.getStringParameter(request, "format", "image/png");
            String font = ServletUtil.getStringParameter(request, "font", "");
            String column= ServletUtil.getStringParameter(request, "column", "");
            String row= ServletUtil.getStringParameter(request, "row", "");

            OutputStream out = response.getOutputStream();

            if (arg0.equals("about:plugins")) {
                response.setContentType("text/html");
                out.write(DataSetSelectorSupport.getPluginsText().getBytes());
                out.close();
                return;
            } else if (arg0.equals("about:autoplot")) {
                response.setContentType("text/html");
                String s = AboutUtil.getAboutHtml();
                out.write(s.getBytes());
                out.close();
                return;
            } else {
                response.setContentType(format);
            }

            System.setProperty("java.awt.headless", "true");

            ApplicationModel appmodel = new ApplicationModel();

            if ( "true".equals(request.getParameter("autolayout")) ) {
                appmodel.setAutolayout(true);
            } else {
                if ( !row.equals("") ) ServletUtil.setDevicePosition( appmodel.plot.getRow(), row );
                if ( !column.equals("") ) ServletUtil.setDevicePosition( appmodel.plot.getColumn(), column );
            }

            if ( !font.equals("") ) appmodel.canvas.setFont(Font.decode(font));
            
            appmodel.getCanvas().setSize(width, height);
            appmodel.getCanvas().revalidate();
            appmodel.getCanvas().setPrintingTag("");

            if (vap != null) appmodel.doOpen(new File(vap));

            DataSource dsource;
            try {
                dsource = DataSetURL.getDataSource(arg0);
            } catch (NullPointerException ex) {
                throw new RuntimeException("No such data source: ", ex);
            } catch (Exception ex) {
                throw ex;
            }

            appmodel.setDataSource(dsource);

            if (format.equals("image/png")) {

                Image image = appmodel.canvas.getImage(width, height);

                DasPNGEncoder encoder = new DasPNGEncoder();
                encoder.addText(DasPNGConstants.KEYWORD_CREATION_TIME, new Date().toString());
                try {
                    encoder.write((BufferedImage) image, out);
                } catch (IOException ioe) {
                } finally {
                    try {
                        out.close();
                    } catch (IOException ioe) {
                        throw new RuntimeException(ioe);
                    }
                }
            } else if (format.equals("application/x-pdf")) {
                appmodel.canvas.prepareForOutput(width, height);
                
                GraphicsOutput go = new org.das2.util.awt.PdfGraphicsOutput();
                
                appmodel.canvas.writeToGraphicsOutput( out, go );
                
            } else if ( format.equals("image/svg") ) {
                appmodel.canvas.prepareForOutput(width, height);
                
                GraphicsOutput go = new org.das2.util.awt.SvgGraphicsOutput();
                
                appmodel.canvas.writeToGraphicsOutput( out, go );
                
            } else {
                throw new IllegalArgumentException("format must be image/png, application/x-pdf, or image/svg");
                
            }
                    

            out.close();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }


    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /** 
     * Handles the HTTP <code>GET</code> method.
     * @param request servlet request
     * @param response servlet response
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /** 
     * Handles the HTTP <code>POST</code> method.
     * @param request servlet request
     * @param response servlet response
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /** 
     * Returns a short description of the servlet.
     */
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>
}
