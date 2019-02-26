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
        <h1>This is a HAPI Server.</h1>  More information about this type of server is found at <a href="https://github.com/hapi-server/data-specification" target="_blank">GitHub</a>.
        This implementation of the HAPI server uses Autoplot URIs to load data, more information about Autoplot can be found <a href="http://autoplot.org" target="_blank">here</a>.

        <br>Run HAPI server <a href="http://tsds.org/verify-hapi/?url=http://jfaden.net/HapiServerDemo/hapi">verifier</a>.
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
        
        <h3>Some example requests:</h3>
        <a href="catalog">Catalog</a> <i>Show the catalog of available data sets.</i><br>
        <a href="capabilities">Capabilities</a> <i>Capabilities of the server. For example, can it use binary streams to transfer data?</i><br>
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
            } catch ( JSONException ex ) {
                out.print("<br><br><b>Something has gone wrong, see logs or send an email to faden at cottagesystems.com</b>");
                out.println("<br>"+out.toString());
            }
        %>
        <br><br>
        <%
            long l= org.das2.qds.RecordIterator.TIME_STAMP; // load RecordIterator class first, or we'll get a negative time.
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
            <li>2017-05-08: bugfix with binary, where the size used internally is now an array of ints, was probably not expected.</li>
            <li>2017-05-24: copy x_meta and resourceURI from templates.</li>
            <li>2017-06-07: support now-P1D/now for example time range.</li>
            <li>2017-06-08: catalog and capabilities responses can be cached.</li>
            <li>2017-06-09: bug in info response, where time was hard-coded and not getting changes in static file.</li>
            <li>2017-06-19: Bob's verifier caught that time lengths assumed that the string need not be null terminated.  doubles used for return types.</li>
            <li>2017-06-20: Bob's verifier caught that streaming data sources were not trimmed to request time.</li>
            <li>2017-06-21: support for P1D/lastday added to DasCoreDatum, so that sample times are not always changing.</li>
            <li>2017-06-28: return 404 when ID is bad, instead of empty response.  Bugfix, where streaming datasources would output an extra record.  Bugfix, subset parameters in info request.  Thanks, Bob!</li>
            <li>2017-08-14: add experimental caching mechanism, where HOME/hapi/cache can contain daily cache files.  Cache is stored in .gzip form.</li>
            <li>2017-08-23: failed release was using old version, where format=binary would return ascii files from the cache.  ascii would not properly subset.</li>
            <li>2017-11-06: put in new catch-all code on the landing page, to aid in debugging.
            <li>2017-12-01: allow modification date to be "lastday" meaning the dataset was updated at midnight
            <li>2017-12-02: various improvements to logging, and new class for monitoring output stream idle added.  More improvements.
            <li>2017-12-03: correct check for localhost.
            <li>2018-02-13: add message to error message for info response.  Update version declarations to HAPI 2.0.
            <li>2018-10-23: correct invalid id response, thanks Bob's verifier.
            <li>2019-02-26: correction to where two spectrograms could be served (specBins.2).
        </ul>
        </small>
    </body>
</html>
