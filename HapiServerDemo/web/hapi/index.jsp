<%-- 
    Document   : index
    Created on : Sep 17, 2016, 6:42:16 AM
    Author     : jbf
--%>

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
        <h1>This is a HAPI Server.</h1>  More information about this type of server is found at <a href="https://github.com/hapi-server/data-specification" target="_blank">github</a>.
        This implementation of the HAPI server uses Autoplot URIs to load data, more information about Autoplot can be found <a href="http://autoplot.org" target="_blank">here</a>
        
        <h3>Some example requests:</h3>
        <a href="catalog">Catalog</a> <i>Show the catalog of available data sets.</i><br>
        <a href="capabilities">Capabilities</a> <i>Capabilities of the server. For example, can it use binary streams to transfer data?</i><br>
        <br>
        <%

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
                
                DatumRange exampleRange= HapiServerSupport.getExampleRange(info);

                out.println( String.format( "<p style=\"background-color: #e0e0e0;\">%s</p>", title ) );
                if ( exampleRange!=null ) {
                    out.println( String.format("<a href=\"info?id=%s\">Info</a> <a href=\"data?id=%s&time.min=%s&time.max=%s\">Data</a>", 
                        ds.getString("id"), ds.getString("id"), exampleRange.min().toString(), exampleRange.max().toString() ) );
                } else {
                    out.println( String.format("<a href=\"info?id=%s\">Info</a> Data", 
                        ds.getString("id"), ds.getString("id") ) );
                }
                
                out.println(" ");
                JSONArray parameters= info.getJSONArray("parameters");
                for ( int j=0; j<parameters.length(); j++ ) {
                    if ( j>0 ) out.print(", ");
                    try {
                        out.print( parameters.getJSONObject(j).getString("name") );
                    } catch ( JSONException ex ) {
                        out.print( "???" );
                    }
                }
                
            }
        %>
        <br><br>
        <%
            long l= org.virbo.dataset.RecordIterator.TIME_STAMP; // load RecordIterator class first, or we'll get a negative time.
        %>
        <br><small>deployed <%= Util.getDurationForHumans( System.currentTimeMillis() - l ) %> ago</small>
        
        <small>
        <ul>
            <li>2016-09-21: bugfix: parameters supported when include is not set.</li>
            <li>2016-09-26: more parameters in current conditions.</li>
            <li>2016-09-29: time ranges.</li>
            <li>2016-09-30: add spectrogram example.</li>
            <li>2016-10-03: correctly handle /hapi request, which redirects to /index.jsp.</li>
            <li>2016-10-04: add sample time range.</li>
            <li>2016-10-05: add power meter image spectrograms.</li>
            <li>2016-10-09: digits spectrogram is 27 channel spectrogram.</li>
            <li>2016-10-11: put in HAPI extension longDescription.</li>
            <li>2016-10-13: finish off support for streaming.</li>
            <li>2016-10-14: add a noStream version of one of the datasets, so that Autoplot can be used to compare.</li>
            <li>2016-10-16: add capabilities page.  Properly handle empty datasets from readers.</li>
            <li>2016-10-25: add support for binary transfer</li>
            <li>2016-10-29: bugfix: binary assumed that times were in us2000.</li>
            <li>2016-10-31: bugfix: running app under different user showed that pylisting.txt was not available.</li>
            <li>2016-11-10: add titles to each item</li>
            <li>2016-11-11: bugfix: properly handle no granules of data found.  Add forecast, which includes non-monotonic data.</li>
            <li>2016-11-15: add rain to forecast</li>
            <li>2016-11-21: new capabilities scheme</li>
            <li>2016-11-23: corrections to bugs found by Scott, <a href='https://sourceforge.net/p/autoplot/bugs/1717/'>1717</a>, like parameters=Spectra</li>
            <li>2017-01-04: add support for DOI and SPASE references in extra info.</li>
            <li>2017-01-08: add wind speed</li>
            <li>2017-01-10: use startDate and stopDate instead of firstDate and lastDate as decided on 2017-01-10 telecon.</li>
            <li>2017-02-03: add http link in info, to show support for this.</li>
            <li>2017-02-03: add demonstration ds for rank 3 data</li>   
            <li>2017-02-07: use bins array instead of bins1, bins2.</li>
            <li>2017-02-13: use x_about instead of about.</li>
            <li>2017-02-21: work towards make the server externally configurable. </li>
            <li>2017-02-28: tweak the connection time for CDAWeb web services, add setLogLevel servlet. </li>
            <li>2017-03-04: use web.xml to set the initial location of the servlet data.</li>
            <li>2017-03-05: recent changes to support time-varying DEPEND_1 broke old codes and there was not sufficient testing to catch the mistake.</li>
            <li>2017-03-06: fix silly mistakes in untested changes.  More silly mistakes.</li>
            <li>2017-03-15: allow data to come from csv files in data directory.</li>
            <li>2017-03-22: add experimental upload data capability.</li>
            <li>2017-03-28: finally support nominal data.</li>
            <li>2017-03-29: bugfix with include=header when cached file is used.</li>
        </ul>
        </small>
    </body>
</html>
