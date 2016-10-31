<%-- 
    Document   : index
    Created on : Sep 17, 2016, 6:42:16 AM
    Author     : jbf
--%>

<%@page import="org.autoplot.hapiserver.Util"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>HAPI Server JSP Demo</title>
    </head>
    <body>
        <h1>This is a HAPI Server.</h1>
        
        <h3>Some example requests:</h3>
        <a href="catalog">Catalog</a> <i>Show the catalog of available data sets.</i><br>
        <a href="capabilities">Capabilities</a> <i>Capabilities of the server. For example, can it use binary streams to transfer data?</i><br>
        <a href="info?id=0B000800408DD710">Info</a> <i>Information about a data set, showing what parameters are contained within.</i><br>
        <a href="data?id=0B000800408DD710&time.min=2016-01-01&time.max=2016-01-05">Data</a> <i>Example data transfer request.</i><br>
        <a href="data?id=0B000800408DD710&time.min=2016-01-01&time.max=2016-01-05&format=binary">Data Binary</a> <i>Example data request, for binary.</i><br>
        <a href="data?id=0B000800408DD710&time.min=2016-01-01&time.max=2016-10-01">Data (10 months)</a> <i>Example data request, showing long requests.</i><br>
        <a href="data?id=0B000800408DD710&time.min=2016-01-01&time.max=2016-01-05&include=header">Data w/header</a> <i>Data request, requesting header as well.</i><br>
        <a href="data?id=Iowa+City+Conditions&time.min=2016-01-01&time.max=2016-01-05&include=header">Data w/multiple parameters</a> <i>Request for data with multiple parameters.</i><br>
        <a href="data?id=Iowa+City+Conditions&time.min=2016-01-01&time.max=2016-01-05&include=header&parameters=Time,Humidity">Data w/subset</a> <i>Request subset of the parameters.</i><br>
        
        <%
            long l= org.virbo.dataset.RecordIterator.TIME_STAMP; // load RecordIterator class first, or we'll get a negative time.
        %>
        <br><small>deployed <%= Util.getDurationForHumans( System.currentTimeMillis() - org.virbo.dataset.RecordIterator.TIME_STAMP ) %> ago</small>
        
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
                
        </ul>
        </small>
    </body>
</html>
