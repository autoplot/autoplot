/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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

/**
 * Servlet takes a set of URIs and returns a vap v1.08 that will display them.
 * @author jbf
 */
public class CdawebVapServlet extends HttpServlet {

    private String getUriParam( HttpServletRequest request, String p ) {
        String uri= request.getParameter( p );
        if ( uri==null ) return null;
        if ( uri.startsWith("vap " ) ) { // vap+inline URI encoding
            throw new IllegalArgumentException("Escape the pluses with %2B: "+uri);
        }
        return uri;
    }
    
    /**
     * Given a list of URIs, return a vap.
     * <code>GET</code> and
     * <code>POST</code> methods.
     *
     * @param request servlet request, containing list of URI-encoded, ampersand delimited, URIs. data0=, data1=, etc. and timeRange=iso8601
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/x-autoplot-vap+xml;charset=UTF-8");
        
        String tt= new SimpleDateFormat("yyyyMMdd_HHmmss").format( new Date() );
        response.setHeader("Content-Disposition","inline; filename=\"default_"+tt+".vap\"" );
        response.setHeader("Access-Control-Allow-Origin","*");
        response.setHeader("Access-Control-Allow-Methods","GET");
        
        PrintWriter out = response.getWriter();
        
        Map params= new HashMap(request.getParameterMap());
        
        // verify that data0 is not in there twice.
        String qs= request.getQueryString();
        int idata0= qs.indexOf("data0=");
        if ( idata0==-1 ) {
            throw new IllegalArgumentException("at least data0= must be specified");
        }
        idata0= qs.indexOf("data0=",idata0+6);
        if ( idata0!=-1 ) {
            throw new IllegalArgumentException("data0 appears to be specified twice");
        }
        
        LinkedHashMap<String,String> uris= new LinkedHashMap();
        int first= 0;
        String uri= getUriParam( request,"data"+first );
        if ( uri!=null ) params.remove("data"+first);
        if ( uri==null ) { // allow data1 to be the first one.
            first= 1;
            uri= getUriParam( request,"data"+first );
            if ( uri!=null ) params.remove("data"+first);
        }
        while ( uri!=null ) {
            uris.put( "data_"+first, uri );
            first++;
            uri= getUriParam( request,"data"+first );
            if ( uri!=null ) params.remove("data"+first);
        }
        
        //uris.put("data_1","vap+inline:timegen('2014-01-17','60s',1440),ripples(1440)");
        //uris.put("data_2","vap+inline:timegen('2014-01-17','60s',1440),rand(1440)+ripples(1440)*100");
        
        //String timeRange= "2014-01-16 23:00 to 2014-01-18 01:00";
        String timeRange= request.getParameter("timeRange");
        if ( timeRange!=null ) params.remove("timeRange");
        
        if ( timeRange==null ) {
            timeRange= request.getParameter("timerange");
            if ( timeRange!=null ) params.remove("timerange");
        }
        
        if ( timeRange==null ) {
            throw new IllegalArgumentException("timeRange must be specified");
        }
        
        if ( !params.isEmpty() ) {
            StringBuilder b= new StringBuilder();
            Set es= params.entrySet();
            int count=0;
            for ( Iterator it = es.iterator(); it.hasNext(); ) { 
                Entry e = (Entry) it.next();
                b.append(" ");
                Object v= e.getValue();
                if ( v.getClass().isArray() ) {
                    for ( int jj= 0; jj<Array.getLength(v); jj++ ) {
                        b.append(" ");
                        b.append(e.getKey()).append("=").append( Array.get(v,jj) );
                        count++;
                    }
                } else {
                    b.append(e.getKey()).append("=").append( e.getValue() );
                    count++;
                }
            }
            String note= ". Note %26 should be used to escape the ampersands in Autoplot URIs.";
            if ( count>1 ) {
                throw new IllegalArgumentException("unrecognized parameters ("+count+"): "+b.substring(1) + note);
            } else {
                throw new IllegalArgumentException("unrecognized parameter: "+b.substring(1)+ note);
            }
        }
        
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc= docBuilder.newDocument();
            Element vap= doc.createElement("vap");
            doc.appendChild(vap);            
            vap.setAttribute("appVersionTag","");
            vap.setAttribute("domVersion","1.08");
            
            Element app= doc.createElement("Application");
            app.setAttribute("id","app_1");
            
            vap.appendChild(app);

            Element bindings= createArray( doc, "BindingModel", uris.size(), "bindings" );
            for ( int i=0; i<uris.size(); i++ ) {
                Element b1= doc.createElement("property");
                b1.setAttribute("default","null");
                b1.setAttribute("type","propertyBinding");
                b1.setAttribute("value","app_1.timeRange to xaxis_"+(i+1)+".range (app_1)");
                bindings.appendChild(b1);
            }
            app.appendChild(bindings);
            
            Element canvas= createCanvasAndLayout(doc);
            app.appendChild(canvas);
            
            Element connectorClass= createArray( doc, "Connector", 0, "connectors" );
            app.appendChild(connectorClass);
                        
            Element dss= createDataSourceFilters(doc,uris);
            app.appendChild(dss);

            Map<String,PlotDescriptor> plotDescriptors= new LinkedHashMap<String, PlotDescriptor>();
            for ( int i=0; i<uris.size(); i++ ) {
                plotDescriptors.put("plot_"+(i+1),new PlotDescriptor());
            }
            Element plots= createPlots(doc,plotDescriptors);
            app.appendChild(plots);
            
            Map<String,Map<String,String>> plotss= new LinkedHashMap<String,Map<String,String>>();
            Map<String,String> aplot;
            int i=0;
            for ( Entry<String,String> uriEntry: uris.entrySet() ) {
                aplot= new HashMap<String,String>();
                aplot.put("dataSourceFilterId", uriEntry.getKey() );
                aplot.put("plotId","plot_"+(i+1));
                plotss.put( "plotElement_"+(i+1), aplot );
                i++;
            }

            Element plotElements= createPlotElements(doc,plotss);
            app.appendChild(plotElements);
            
            // add the timeRange to the file.
            Element trp= doc.createElement( "property" );
            trp.setAttribute("name", "timeRange");
            trp.setAttribute("type", "datumRange");
            Element tr= doc.createElement("datumRange");
            tr.setAttribute("units","us2000");
            tr.setAttribute("value",timeRange );
            trp.appendChild(tr);
            app.appendChild(trp);
            
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
        plots= createArray(doc, "PlotElement", pplotss.size(), "plotElements" );
        for ( Entry<String,Map<String,String>> aplotss: pplotss.entrySet()  ) {
            plots.appendChild( createPlotElement( doc, aplotss.getKey(), aplotss.getValue() ) );
        }
        return plots;
    }
    
     private Element createPlotElement( Document doc, String id, Map<String,String> pe ) {
        Element plotElement= doc.createElement("PlotElement");
                        
        addProperty( doc, plotElement, "autoComponent", "Boolean", "true" );
        addProperty( doc, plotElement, "autoLabel", "Boolean", "true" );
        addProperty( doc, plotElement, "autoRenderType", "Boolean", "true" );
        addProperty( doc, plotElement, "plotId", "String", pe.get("plotId") );
        addProperty( doc, plotElement, "dataSourceFilterId", "String", pe.get("dataSourceFilterId") );
        plotElement.setAttribute("id", id );
        return plotElement;
    }
     
    public static class PlotDescriptor {
        String xmin, xmax;
        String ymin, ymax;
        String zmin, zmax;
        boolean xlog,ylog,zlog;
        String xlabel, ylabel, zlabel;
    }
    
    /**
     * 
     * @param doc
     * @param plotDescriptors map of plot_INT to properties.
     * @return 
     */
    private Element createPlots( Document doc, Map<String,PlotDescriptor> plotDescriptors ) {
        Element plots;
        plots= createArray(doc, "Plot", plotDescriptors.size(), "plots" ); // TODO: assumes one plot element for each plot.
        for ( Entry<String,PlotDescriptor> plot: plotDescriptors.entrySet()  ) {
            int i= plot.getKey().indexOf("_");
            int iid= Integer.valueOf(plot.getKey().substring(i+1));
            Element plotE= createPlot( doc, plot.getKey(), iid, plot.getValue() );
            plots.appendChild( plotE );
        }
        return plots;
    }
    
    private Element createPlot( Document doc, String id, int iid, PlotDescriptor pd ) {
        Element plot= doc.createElement("Plot");
        addProperty( doc, plot, "autoLabel", "Boolean", "true" );
        addProperty( doc, plot, "xaxis", "DomNode", createAxis( doc, "xaxis_"+iid, iid, pd.xmin, pd.xmax, pd.xlog, pd.xlabel ) ); //TODO: Why not Axis instead of DomNode
        addProperty( doc, plot, "yaxis", "DomNode", createAxis( doc, "yaxis_"+iid, iid, pd.ymin, pd.ymax, pd.ylog, pd.ylabel ) );
        addProperty( doc, plot, "zaxis", "DomNode", createAxis( doc, "zaxis_"+iid, iid, pd.zmin, pd.zmax, pd.zlog, pd.zlabel ) );
        plot.setAttribute("id", id );
        return plot;
    }
    
    private Element createAxis( Document doc, String id, int iid, String min, String max, boolean log, String label ) {
        Element axis= doc.createElement("Axis");
        if ( min==null ) {
            addProperty( doc, axis, "autoRange", "Boolean", "true" );
            addProperty( doc, axis, "autoLabel", "Boolean", "true" );
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
        axis.setAttribute( "id", id );
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
        addProperty( doc, canvas, "width", "Integer", "722" );
        
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
