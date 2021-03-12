<%-- 
    Document   : index
    Created on : Sep 17, 2016, 6:42:16 AM
    Author     : jbf
--%>

<%@page import="java.net.URLEncoder"%>
<%@page import="org.json.JSONException"%>
<%@page import="java.util.Enumeration"%>
<%@page import="java.io.File"%>
<%@page import="org.das2.datum.DatumRange"%>
<%@page import="org.json.JSONObject"%>
<%@page import="org.json.JSONArray"%>
<%@page import="org.autoplot.hapiserver.HapiServerSupport"%>
<%@page import="org.autoplot.hapiserver.Util"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>HAPI Server JSP Demo</title>
    </head>
    <body>
        <h1>This is a HAPI Server.</h1>  More information about this type of server is found at <a href="https://github.com/hapi-server/data-specification" target="_blank">GitHub</a>.
        This implementation of the HAPI server uses Autoplot URIs to load data, more information about Autoplot can be found <a href="http://autoplot.org" target="_blank">here</a>.

        <%
            String me= "https://jfaden.net/HapiServerDemo/hapi";
            %>
            <br>The HAPI server <a href="http://hapi-server.org/verify?url=<%=me%>">verifier</a> will test this HAPI server for correctness.
        <%
            Util.maybeInitialize( getServletContext() );
            if ( Util.getHapiHome()==null ) {
                String HAPI_SERVER_HOME= getServletContext().getInitParameter("HAPI_SERVER_HOME");
                Util.setHapiHome( new File( HAPI_SERVER_HOME ) );
            }
            
            String ip = request.getRemoteAddr();
            if (ip.equals("127.0.0.1")) {
                Enumeration<String> hh= request.getHeaders("X-Forwarded-For");
                if ( hh.hasMoreElements() ) {
                    ip = hh.nextElement();
                }
            }
            if ( ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1") ) {
                String s= request.getRequestURI();
                int i= s.indexOf("/",1);
                s= s.substring(0,i);
                out.println( String.format( "<br>This is run from localhost, set logging with <a href='%s/SetLogLevel'>SetLogLevel</a>. ", s ));
                out.println( "Requests from localhost will have performance monitored, which can degrade performance.<br><br>");
            }
            %>

        Click on sparkline graphics to see the example time range.        
        <h3>Some example requests:</h3>
        <a href="catalog">Catalog</a> <i>Show the catalog of available data sets.</i><br>
        <a href="capabilities">Capabilities</a> <i>Capabilities of the server. For example, can it use binary streams to transfer data?</i><br>
        <a href="about">About</a> <i>More about this server, like contact info.</i><br>
        <br>
        <%
            try {
            String HAPI_SERVER_HOME= getServletContext().getInitParameter("HAPI_SERVER_HOME");
            Util.setHapiHome( new File( HAPI_SERVER_HOME ) );
            
            JSONArray dss= HapiServerSupport.getCatalog();
            for ( int i=0; i<dss.length(); i++ ) {
                JSONObject ds= dss.getJSONObject(i);
                
                String id= ds.getString("id");
                String title= "";
                if ( ds.has("title") ) {
                    title= ds.getString("title");
                    if ( title.length()==0 ) {
                        title= id;
                    } else {
                        if ( !title.equals(id) ) {
                            title= id + ": "+ title;
                        }
                    }
                } else {
                    title= id;
                }
                
                File infoFile= new File( new File( Util.getHapiHome(), "info" ), id+".json" );
                JSONObject info= HapiServerSupport.readJSON( infoFile );
                
                DatumRange availableRange= HapiServerSupport.getRange(info);
                DatumRange exampleRange= HapiServerSupport.getExampleRange(info);
                if ( exampleRange!=null ) {
                    title= title+ "<em> (available "+availableRange + ", example range "+exampleRange + " shown)</em>";
                }

                String exampleTimeRange= exampleRange==null ? null : String.format( "time.min=%s&time.max=%s", exampleRange.min().toString(), exampleRange.max().toString() ); 
                out.println( String.format( "<p style=\"background-color: #e0e0e0;\">%s</p>", title ) );
                if ( exampleRange!=null ) {
                    out.println( String.format("[<a href=\"info?id=%s\">Info</a>] [<a href=\"data?id=%s&%s\">Data</a>]", 
                        ds.getString("id"), ds.getString("id"), exampleTimeRange ) );
                } else {
                    out.println( String.format("[<a href=\"info?id=%s\">Info</a>] [Data]", 
                        ds.getString("id"), ds.getString("id") ) );
                }
                
                String autoplotServer= "https://jfaden.net/AutoplotServlet";
                //String autoplotServer= "http://localhost:8084/AutoplotServlet";
                
                out.println(" ");
                JSONArray parameters= info.getJSONArray("parameters");
                for ( int j=0; j<parameters.length(); j++ ) {
                    if ( j>0 ) out.print("  ");
                    try {
                        String pname= parameters.getJSONObject(j).getString("name");
                        out.print( String.format( "<a href=\"data?id=%s&parameters=%s&%s\">%s</a>", ds.getString("id"), pname, exampleTimeRange, pname ) );
                        if ( j>0 ) { //sparklines
                            //     vap  +hapi  :https      ://jfaden.net  /HapiServerDemo  /hapi  ?id=?parameters=Temperature
                            //?url=vap%2Bhapi%3Ahttps%3A%2F%2Fjfaden.net%2FHapiServerDemo%2Fhapi%3Fid%3DpoolTemperature%26timerange%3D2020-08-06&format=image%2Fpng&width=70&height=20&column=0%2C100%25&row=0%2C100%25&timeRange=2003-mar&renderType=&color=%23000000&symbolSize=&fillColor=%23aaaaff&foregroundColor=%23000000&backgroundColor=none
                            StringBuilder sb= new StringBuilder();
                            sb.append("uri=");
                            StringBuilder ub= new StringBuilder();
                            ub.append("vap+hapi:"+me);
                            ub.append("?");
                            ub.append("id="+id);
                            ub.append("&parameters="+pname);
                            ub.append("&timerange="+exampleRange.toString().replaceAll(" ","+") );
                            sb.append( URLEncoder.encode(ub.toString()) );
                            sb.append("&format=image%2Fpng");
                            sb.append("&width=70");
                            sb.append("&height=16");
                            sb.append("&row=0%2C100%25");
                            sb.append("&column=0%2C100%25");
                            sb.append("&timerange="+URLEncoder.encode(exampleRange.toString()) );
                            out.print( "<a href='"+autoplotServer+"/thin/zoom/demo.jsp?"+sb.toString()+"' target='top'>");
                            out.print( "<img src='"+autoplotServer+"/SimpleServlet?"+sb.toString()+"'>" );
                            out.print( "</a>");
                            //out.print( "<img src=\"http://localhost:8084/AutoplotServlet/SimpleServlet?"+sb.toString()+"\">" );                        
                        }

                    } catch ( JSONException ex ) {
                        out.print( "???" );
                    }
                }
                
            }
            } catch ( JSONException ex ) {
                out.print("<br><br><b>Something has gone wrong, see logs or send an email to faden at cottagesystems.com</b>");
                out.println("<br>"+out.toString());
            }
        %>
        <br><br>
        <%
            long l= org.das2.qds.RecordIterator.TIME_STAMP; // load RecordIterator class first, or we'll get a negative time.
        %>
        <small>deployed <%= Util.getDurationForHumans( System.currentTimeMillis() - l ) %> ago</small>
        <br>
        <br>
        <a href='versions.html'>version <%= Util.serverVersion() %></a><br>
        HAPI protocol version <%= Util.hapiVersion() %>
        </small>
        <br>
    </body>
</html>
