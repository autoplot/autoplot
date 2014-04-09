/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

/**
 *
 * @author jbf
 */
public class CdawebVapServlet extends HttpServlet {

    /**
     * Given a list of URIs, return a vap.
     * <code>GET</code> and
     * <code>POST</code> methods.
     *
     * @param request servlet request, containing list of URI-encoded, ampersand delimited, URIs.
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc= docBuilder.newDocument();
            Element vap= doc.createElement("vap");
            doc.appendChild(vap);            
            vap.setAttribute("appVersionTag","");
            vap.setAttribute("domVersion","1.07");
            
            Element app= doc.createElement("Application");
            app.setAttribute("id","app_0");
            
            vap.appendChild(app);

            Element bindings= createArray( doc, "BindingModel", 0, "bindings" );
            app.appendChild(bindings);
            
            Element canvas= createCanvasAndLayout(doc);
            app.appendChild(canvas);
            
            Element connectorClass= createArray( doc, "Connector", 0, "connectors" );
            app.appendChild(connectorClass);
                        
            Map<String,String> uris= new HashMap();
            uris.put("data_0","vap+inline:ripples(20)");
            uris.put("data_1","vap+inline:ripples(20,20)");
            Element dss= createDataSourceFilters(doc,uris);
            app.appendChild(dss);

            Map<String,PlotDescriptor> plotDescriptors= new HashMap<String, PlotDescriptor>();
            plotDescriptors.put("plot_0",new PlotDescriptor());
            plotDescriptors.put("plot_1",new PlotDescriptor());            
            Element plots= createPlots(doc,plotDescriptors);
            app.appendChild(plots);
            
            Map<String,Map<String,String>> plotss= new HashMap<String,Map<String,String>>();
            Map<String,String> aplot;
            aplot= new HashMap<String,String>();
            aplot.put("dataSourceFilterId","data_0");
            aplot.put("plotId","plot_0");
            plotss.put( "plotElement_0", aplot );
            aplot= new HashMap<String,String>();
            aplot.put("dataSourceFilterId","data_1");
            aplot.put("plotId","plot_1");
            plotss.put( "plotElement_1", aplot );
            Element plotElements= createPlotElements(doc,plotss);
            app.appendChild(plotElements);
            
            //write the content into xml file
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            DOMSource source = new DOMSource(doc);

            StreamResult result =  new StreamResult( out );
            transformer.transform(source, result);
            
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(CdawebVapServlet.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TransformerConfigurationException ex) {
            Logger.getLogger(CdawebVapServlet.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TransformerException ex) {
            Logger.getLogger(CdawebVapServlet.class.getName()).log(Level.SEVERE, null, ex);
        } finally {            
            out.close();
        }
    }
    
    private Element createArray( Document doc, String clasname, int len, String name ) {
        Element array= doc.createElement( "property" );
        array.setAttribute("class", clasname );
        array.setAttribute("length",String.valueOf(len) );
        array.setAttribute("name",name);
        return array;
    }

    
    private Element createPlotElements( Document doc, Map<String,Map<String,String>> pplotss ) {
        Element plots;
        plots= createArray(doc, "PlotElement", 2, "plotElements" );
        for ( Entry<String,Map<String,String>> aplotss: pplotss.entrySet()  ) {
            plots.appendChild( createPlotElement( doc, aplotss.getValue() ) );
        }
        return plots;
    }
    
     private Element createPlotElement( Document doc, Map<String,String> pe ) {
        Element plotElement= doc.createElement("PlotElement");
        addProperty( doc, plotElement, "plotId", "String", pe.get("plotId") );
        addProperty( doc, plotElement, "dataSourceFilterId", "String", pe.get("dataSourceFilterId") );
        //plotElement.setAttribute("id", id );
        return plotElement;
    }
     
    public static class PlotDescriptor {
        String xmin, xmax;
        String ymin, ymax;
        String zmin, zmax;
        boolean xlog,ylog,zlog;
        String xlabel, ylabel, zlabel;
    }
    
    private Element createPlots( Document doc, Map<String,PlotDescriptor> plotDescriptors ) {
        Element plots;
        plots= createArray(doc, "Plot", 2, "plots" );
        for ( Entry<String,PlotDescriptor> plot: plotDescriptors.entrySet()  ) {
            plots.appendChild( createPlot( doc, plot.getKey(), plot.getValue() ) );
        }
        return plots;
    }
    
    private Element createPlot( Document doc, String id, PlotDescriptor pd ) {
        Element plot= doc.createElement("Plot");
        addProperty( doc, plot, "xaxis", "Axis", createAxis( doc, pd.xmin, pd.xmax, pd.xlog, pd.xlabel ) );
        addProperty( doc, plot, "yaxis", "Axis", createAxis( doc, pd.ymin, pd.ymax, pd.ylog, pd.ylabel ) );
        addProperty( doc, plot, "zaxis", "Axis", createAxis( doc, pd.zmin, pd.zmax, pd.zlog, pd.zlabel ) );
        plot.setAttribute("id", id );
        return plot;
    }
    
    private Element createAxis( Document doc, String min, String max, boolean log, String label ) {
        Element axis= doc.createElement("Axis");
        if ( min==null ) {
            addProperty( doc, axis, "autoRange", "Boolean", "true" );
        } else {
            addProperty( doc, axis, "log", "Boolean", String.valueOf(log) );
            if ( min.contains("T") ) {
                addProperty( doc, axis, "range", "datumRange", min+"/"+max );
            } else {
                addProperty( doc, axis, "range", "datumRange", min+" to "+max );
            } 
            addProperty( doc, axis, "log", "Boolean", String.valueOf(log) );
            addProperty( doc, axis, "label", "String", label );
        }
        return axis;
    }
    
    private Element createDataSourceFilters( Document doc, Map<String,String> uris ) {
        Element dss;
        dss= createArray(doc, "DataSourceFilter", uris.size(), "dataSourceFilters" );
        for ( Entry<String,String> uri: uris.entrySet()  ) {
            dss.appendChild( createDsf( doc, uri.getKey(), uri.getValue() ) );
        }
        return dss;
    }
    
    private Element createDsf( Document doc, String id, String uri ) {
        Element dsf= doc.createElement("DataSourceFilter");
        dsf.setAttribute("id", id );
        addProperty( doc, dsf, "uri", "String", uri );
        return dsf;
    }
    
    private Element createCanvasAndLayout( Document doc ) {
        Element canvases;
        canvases= createArray(doc, "Canvas", 1, "canvases" );

        Element canvas= doc.createElement("Canvas");
        canvas.setAttribute("id","canvas_0");

        canvases.appendChild(canvas);

        Element columns= createArray( doc, "Column", 0, "columns" );
        canvas.appendChild( columns );
        addProperty( doc, canvas, "height", "Integer", "604" );
        
        Element column= addColumn( doc, "marginColumn_0", "", "+7.0em", "100.00%-7.0em" );
        addProperty( doc, canvas, "marginColumn", "DomNode", column );

        Element row= addRow( doc, "marginRow_0", "", "2.0em", "100%-1.0em" );
        addProperty( doc, canvas, "marginRow", "DomNode", row );

        Element prop;
        prop= doc.createElement("property");
        prop.setAttribute("class", "Row");
        prop.setAttribute("length", "2");
        prop.setAttribute("name","rows");                    

        Element row1= addRow( doc, "row1", "marginRow_0", "+2.0em", "50%-2.0em" );
        prop.appendChild(row1);

        Element row2= addRow( doc, "row2", "marginRow_0", "50%+2.0em", "100%-2.0em" );
        prop.appendChild(row2);

        canvas.appendChild(prop);

        addProperty( doc, canvas, "width", "Integer", "722" );
        
        return canvases;
    }
    
    private void addProperty( Document doc, Element canvas, String name, String type, String value ) {
        Element cprop= doc.createElement("property");
        cprop.setAttribute("name",name);
        cprop.setAttribute("type",type);
        cprop.setAttribute("value",value);
        canvas.appendChild(cprop);
    }

    private void addProperty( Document doc, Element canvas, String name, String type, Element p ) {
        Element cprop= doc.createElement("property");
        cprop.setAttribute("name",name);
        cprop.setAttribute("type",type);
        canvas.appendChild(cprop);
        cprop.appendChild(p);
    }
            
    private Element addColumn( Document doc, String name, String parent, String left, String right ) {
        Element column= doc.createElement("Column");
        column.setAttribute("id",name);

        Element cprop= doc.createElement("property");
        cprop.setAttribute("default", "2em" );
        cprop.setAttribute("type", "String" );
        cprop.setAttribute("name","left");
        cprop.setAttribute("value",left);
        column.appendChild(cprop);
        
        cprop= doc.createElement("property");
        cprop.setAttribute("type", "String" );
        cprop.setAttribute("name","parent");
        cprop.setAttribute("value",parent);
        column.appendChild(cprop);
        
        cprop= doc.createElement("property");
        cprop.setAttribute("default", "100%-3em" );
        cprop.setAttribute("type", "String" );
        cprop.setAttribute("name","right");
        cprop.setAttribute("value",right);
        column.appendChild(cprop);
        return column;
    }

    private Element addRow( Document doc, String name, String parent, String top, String bottom ) {
        Element row= doc.createElement("Row");
        row.setAttribute("id",name);

        Element cprop;
        cprop= doc.createElement("property");
        cprop.setAttribute("type", "String" );
        cprop.setAttribute("name","parent");
        cprop.setAttribute("value",parent);
        row.appendChild(cprop);
        
        cprop= doc.createElement("property");
        cprop.setAttribute("default", "+2em" );
        cprop.setAttribute("type", "String" );
        cprop.setAttribute("name","top");
        cprop.setAttribute("value",top);
        row.appendChild(cprop);
        
        cprop= doc.createElement("property");
        cprop.setAttribute("default", "100%-2.0em" );
        cprop.setAttribute("type", "String" );
        cprop.setAttribute("name","bottom");
        cprop.setAttribute("value",bottom);
        row.appendChild(cprop);
        return row;
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
